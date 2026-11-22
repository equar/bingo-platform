# Withdrawal reference reservation and legacy reconciliation

V56 adds a historical reference reservation ledger. V57 adds canonical
reservations in separate tables and switches runtime completion to that ledger.
V54 and V56 remain unchanged. The runtime canonical form is uppercase ASCII
letters and digits after removing punctuation and whitespace. The V57 MySQL
backfill applies the same transformation to all completed raw references and
all V56 reservations before application code uses that form. Historical
withdrawal rows, raw references, V54 values, and V56 reservation/source rows
are not rewritten or deleted. V57 links every completed row with a usable
reference in `withdrawal_transfer_reference_reservation_sources_v57`.

V54 could retain only the oldest normalized reference from a collision group;
other completed rows could have `normalized_transfer_reference = NULL`. V56
reconstructs reservations from all completed raw references. V57 then merges
punctuation/whitespace variants into the canonical key, again deriving from all
completed rows and carrying forward reservation-only keys. Any canonical key
with multiple legacy sources is `AMBIGUOUS_LEGACY`, has one source row per
historical withdrawal, and remains reserved pending manual reconciliation.

Completed BANK rows with a blank bank name are reserved in provider scope `*`.
The application checks that wildcard scope for every provider, so an unknown
legacy provider cannot make the reference reusable. A completed row with no
external reference is retained in `withdrawal_transfer_reference_migration_issues`
with `COMPLETED_WITHOUT_REFERENCE`; no external value can safely be inferred.

## Read-only migration review

After V57 on a disposable clone, inspect unresolved records using a read-only
database account:

```sql
SELECT r.id, r.provider_key, r.normalized_reference, r.legacy_source_count,
       s.withdrawal_request_id, w.transfer_reference, w.destination_type,
       w.bank_name, w.reviewed_by, w.reviewed_at, w.payment_evidence
FROM withdrawal_transfer_reference_reservations_v57 r
JOIN withdrawal_transfer_reference_reservation_sources_v57 s ON s.reservation_id = r.id
JOIN wallet_withdrawal_requests w ON w.id = s.withdrawal_request_id
WHERE r.reservation_status = 'AMBIGUOUS_LEGACY'
ORDER BY r.provider_key, r.normalized_reference, s.withdrawal_request_id;

SELECT issue.withdrawal_request_id, issue.issue_code, w.reviewed_by, w.reviewed_at,
       w.transfer_reference, w.payment_evidence
FROM withdrawal_transfer_reference_migration_issues issue
JOIN wallet_withdrawal_requests w ON w.id = issue.withdrawal_request_id
ORDER BY issue.withdrawal_request_id;
```

For each ambiguous reference, Finance and Operations must compare the original
request rows with independent provider/bank records and existing payout
evidence, document which historical payments can be established, and retain
the approval and evidence in the organization’s controlled reconciliation
record. DBA records should identify the reservation ID and all source request
IDs. Never delete a withdrawal, change a raw transfer reference, or clear a
reservation to make a later completion pass. If evidence is inconclusive, keep
the reservation blocked and escalate it; a new withdrawal must use a genuinely
new external transfer reference.

V56 deliberately does not auto-resolve ambiguous records. Completion returns
`TRANSFER_REFERENCE_RECONCILIATION_REQUIRED` for an ambiguous historical
reservation and `TRANSFER_REFERENCE_ALREADY_USED` for an established one.
V57 keeps the same responses. New canonical reservations and source links are
written in the same transaction as withdrawal completion. The unique
provider/reference key arbitrates concurrent completions; a database collision
is returned as HTTP 409 with `TRANSFER_REFERENCE_ALREADY_USED`.

## Upgrade and rollback

Run migration upgrade tests only through the guarded disposable Maven MySQL 8
schema. Test V55-to-current with duplicate and distinct-provider references,
an unknown BANK provider, and a completed request without a reference. Separately
exercise current completion, duplicate reuse, and concurrent completion in the
guarded integration suite. Do not clean, reset, or migrate persistent QA or
production databases as part of this procedure.

Rollback is forward-only for financial data. The schema is additive, but
rolling the application back to a build that ignores the V56/V57 reservation
ledgers would remove this protection while V54's nullable legacy rows remain.
If rollback is needed, keep all reservation/source/issue tables and deploy a
compatible forward fix; do not drop them or restore a backup over a live schema
without an approved recovery plan.
