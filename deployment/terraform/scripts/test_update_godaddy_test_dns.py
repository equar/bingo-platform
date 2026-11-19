import unittest
from urllib.parse import parse_qs, urlsplit

from update_godaddy_test_dns import sync_records


ZONE = "bakaroo.com"
DOMAINS = [
    "test.bakaroo.com",
    "admin.test.bakaroo.com",
    "api.test.bakaroo.com",
]
OLD_IP = "8.8.8.8"
NEW_IP = "1.1.1.1"


class FakeGoDaddyApi:
    def __init__(self, records=()):
        self.records = {record["recordId"]: dict(record) for record in records}
        self.calls = []
        self.next_id = 1

    def __call__(self, method, url, token, payload=None):
        self.calls.append((method, url, token, payload))
        self.asserted_token = token
        parsed = urlsplit(url)
        path = parsed.path.split("/")
        if method == "GET":
            query = parse_qs(parsed.query)
            record_type = query["type"][0]
            name = query["name"][0]
            records = [
                dict(record)
                for record in self.records.values()
                if record["type"] == record_type and record["name"] == name
            ]
            return {"items": records, "totalPages": 1}
        if method == "POST":
            record = dict(payload)
            record["recordId"] = f"new-{self.next_id}"
            self.next_id += 1
            self.records[record["recordId"]] = record
            return record
        record_id = path[-1]
        if method == "PUT":
            self.records[record_id].update(payload)
            return self.records[record_id]
        raise AssertionError(f"Unexpected method: {method}")


def a_record(record_id, name, data):
    return {
        "recordId": record_id,
        "name": name,
        "type": "A",
        "data": data,
        "ttl": 600,
    }


class GoDaddyTestDnsTest(unittest.TestCase):
    def test_sync_updates_existing_a_and_creates_missing_a_records(self):
        api = FakeGoDaddyApi([a_record("existing", "test", OLD_IP)])

        sync_records("pat", ZONE, NEW_IP, DOMAINS, request=api)

        self.assertEqual(api.asserted_token, "pat")
        self.assertEqual(api.records["existing"]["data"], NEW_IP)
        self.assertEqual(
            {
                (record["name"], record["data"])
                for record in api.records.values()
                if record["type"] == "A"
            },
            {("test", NEW_IP), ("admin.test", NEW_IP), ("api.test", NEW_IP)},
        )
        self.assertEqual(sum(call[0] == "PUT" for call in api.calls), 1)
        self.assertEqual(sum(call[0] == "POST" for call in api.calls), 2)

    def test_sync_updates_existing_records_to_new_ip_without_deleting(self):
        api = FakeGoDaddyApi(
            [
                a_record(f"existing-{name}", name, OLD_IP)
                for name in ("test", "admin.test", "api.test")
            ]
        )

        sync_records("pat", ZONE, NEW_IP, DOMAINS, request=api)

        self.assertEqual(
            {record["data"] for record in api.records.values()},
            {NEW_IP},
        )
        self.assertEqual(sum(call[0] == "PUT" for call in api.calls), 3)
        self.assertFalse(any(call[0] == "DELETE" for call in api.calls))

    def test_sync_does_not_update_when_a_conflicting_cname_exists(self):
        api = FakeGoDaddyApi(
            [
                a_record("existing", "test", OLD_IP),
                {
                    "recordId": "cname",
                    "name": "admin.test",
                    "type": "CNAME",
                    "data": "old.example.com",
                    "ttl": 600,
                },
            ]
        )

        with self.assertRaisesRegex(RuntimeError, "has a CNAME"):
            sync_records("pat", ZONE, NEW_IP, DOMAINS, request=api)

        self.assertFalse(any(call[0] in ("PUT", "POST") for call in api.calls))

    def test_rejects_domains_outside_zone_before_api_calls(self):
        api = FakeGoDaddyApi()

        with self.assertRaisesRegex(ValueError, "must be under"):
            sync_records(
                "pat",
                ZONE,
                NEW_IP,
                ["test.example.com", DOMAINS[1], DOMAINS[2]],
                request=api,
            )

        self.assertEqual(api.calls, [])


if __name__ == "__main__":
    unittest.main()
