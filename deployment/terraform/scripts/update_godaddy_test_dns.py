#!/usr/bin/env python3
"""Synchronize the test A records in a GoDaddy DNS zone."""

import argparse
import ipaddress
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


API_ROOT = "https://api.godaddy.com/v3/domains/zones"
RECORD_TTL = 600


def api_request(method, url, token, payload=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {
        "Accept": "application/json",
        "Authorization": f"Bearer {token}",
    }
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            response_body = response.read()
    except urllib.error.HTTPError as error:
        details = error.read().decode("utf-8", "replace").strip()
        suffix = f": {details[:400]}" if details else ""
        raise RuntimeError(f"GoDaddy API returned HTTP {error.code}{suffix}") from error
    except (urllib.error.URLError, TimeoutError) as error:
        raise RuntimeError(f"Could not reach the GoDaddy DNS API: {error}") from error

    if not response_body:
        return None
    try:
        return json.loads(response_body)
    except json.JSONDecodeError as error:
        raise RuntimeError("GoDaddy DNS API returned invalid JSON.") from error


def _record_url(zone, record_type=None, record_name=None):
    url = f"{API_ROOT}/{urllib.parse.quote(zone, safe='')}/dns-records"
    if record_type is not None:
        query = urllib.parse.urlencode(
            {
                "type": record_type,
                "name": record_name,
                "page": 1,
                "pageSize": 100,
                "totalRequired": "true",
            }
        )
        return f"{url}?{query}"
    return url


def list_records(zone, record_type, record_name, token, request=api_request):
    records = []
    page = 1
    while True:
        query = urllib.parse.urlencode(
            {
                "type": record_type,
                "name": record_name,
                "page": page,
                "pageSize": 100,
                "totalRequired": "true",
            }
        )
        url = f"{API_ROOT}/{urllib.parse.quote(zone, safe='')}/dns-records?{query}"
        result = request("GET", url, token)
        if not isinstance(result, dict) or not isinstance(result.get("items"), list):
            raise RuntimeError("GoDaddy DNS API returned an invalid records page.")
        records.extend(result["items"])
        try:
            total_pages = int(result.get("totalPages", page))
        except (TypeError, ValueError) as error:
            raise RuntimeError("GoDaddy DNS API returned an invalid page count.") from error
        if page >= total_pages:
            return records
        page += 1


def _relative_record_name(domain, zone):
    normalized_domain = domain.strip().lower().rstrip(".")
    if not re.fullmatch(r"[a-z0-9]+(?:[.-][a-z0-9]+)*", normalized_domain):
        raise ValueError(f"Invalid test DNS hostname: {domain}")
    suffix = f".{zone}"
    if not normalized_domain.endswith(suffix):
        raise ValueError(f"Test DNS hostname must be under {zone}: {domain}")
    return normalized_domain[: -len(suffix)]


def _validated_inputs(zone, public_ip, domains):
    normalized_zone = zone.strip().lower().rstrip(".")
    if not re.fullmatch(r"[a-z0-9]+(?:[.-][a-z0-9]+)*", normalized_zone):
        raise ValueError("GoDaddy DNS zone is not a valid hostname.")
    try:
        address = ipaddress.ip_address(public_ip.strip())
    except ValueError as error:
        raise ValueError("The test Elastic IP is invalid.") from error
    if not isinstance(address, ipaddress.IPv4Address) or not address.is_global:
        raise ValueError("The test DNS target must be a public IPv4 address.")

    record_names = [_relative_record_name(domain, normalized_zone) for domain in domains]
    if len(set(record_names)) != len(record_names):
        raise ValueError("The three test DNS names must be distinct.")
    return normalized_zone, str(address), record_names


def sync_records(token, zone, public_ip, domains, request=api_request):
    zone, public_ip, names = _validated_inputs(zone, public_ip, domains)
    if not token.strip():
        raise ValueError("GODADDY_PAT is required to update GoDaddy DNS.")

    records_by_name = {}
    for name in names:
        a_records = list_records(zone, "A", name, token, request)
        cname_records = list_records(zone, "CNAME", name, token, request)
        aaaa_records = list_records(zone, "AAAA", name, token, request)
        if cname_records:
            raise RuntimeError(
                f"GoDaddy record {name}.{zone} has a CNAME; remove it before using an A record."
            )
        if aaaa_records:
            raise RuntimeError(
                f"GoDaddy record {name}.{zone} has an AAAA record; remove it to avoid routing IPv6 clients elsewhere."
            )
        records_by_name[name] = a_records

    for name in names:
        records = records_by_name[name]
        if records:
            for record in records:
                record_id = record.get("recordId")
                if not record_id:
                    raise RuntimeError(
                        f"GoDaddy A record for {name}.{zone} is missing its record ID."
                    )
                url = (
                    f"{API_ROOT}/{urllib.parse.quote(zone, safe='')}/dns-records/"
                    f"{urllib.parse.quote(str(record_id), safe='')}"
                )
                request(
                    "PUT",
                    url,
                    token,
                    {
                        "name": name,
                        "type": "A",
                        "data": public_ip,
                        "ttl": RECORD_TTL,
                    },
                )
        else:
            request(
                "POST",
                _record_url(zone),
                token,
                {
                    "name": name,
                    "type": "A",
                    "data": public_ip,
                    "ttl": RECORD_TTL,
                },
            )
        print(f"GoDaddy A record synced: {name}.{zone} -> {public_ip}")


def _required_environment(name):
    value = os.environ.get(name, "").strip()
    if not value:
        raise ValueError(f"{name} is required.")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("sync",))
    parser.parse_args()
    try:
        values = (
            _required_environment("GODADDY_PAT"),
            _required_environment("GODADDY_DNS_ZONE"),
            _required_environment("GODADDY_TEST_PUBLIC_IP"),
            [
                _required_environment("GODADDY_TEST_SITE_DOMAIN"),
                _required_environment("GODADDY_TEST_ADMIN_DOMAIN"),
                _required_environment("GODADDY_TEST_API_DOMAIN"),
            ],
        )
        sync_records(*values)
    except (OSError, RuntimeError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
