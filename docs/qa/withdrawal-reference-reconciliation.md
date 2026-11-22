# Withdrawal reference reservation and legacy reconciliation

V56 adds an append-only reference reservation ledger. V54 remains unchanged.
Every nonblank `COMPLETED` withdrawal reference is reserved using the same
provider scope and `UPPER(TRIM(reference))` normalization used by withdrawal
completion. Historical request rows and raw references are not rewritten or
deleted. `withdrawal_transfer_reference_reservation_sources` retains the link
from each reservation to every original withdrawal row.

V54 could retain only the oldest normalized reference from a collision group;
other completed rows could have `normalized_transfer_reference = NULL`. V56
reconstructs the reservation set from **all** completed rows' raw references,
not from the V54 normalized column. A reference occurring more than once for
one provider is inserted once as `AMBIGUOUS_LEGACY`, with one source row per
historical withdrawal. It remains reserved while the ambiguity is investigated.

Completed BANK rows with a blank bank name are reserved in provider scope `*`.
The application checks that wildcard scope for every provider, so an unknown
legacy provider cannot make the reference reusable. A completed row with no
external reference is retained in `withdrawal_transfer_reference_migration_issues`
with `COMPLETED_WITHOUT_REFERENCE`; no external value can safely be inferred.

## Read-only migration review

After V56 on a disposable clone, inspect unresolved records using a read-only
database account:

```sql
SELECT r.id, r.provider_key, r.normalized_reference, r.legacy_source_count,
       s.withdrawal_request_id, w.transfer_reference, w.destination_type,
       w.bank_name, w.reviewed_by, w.reviewed_at, w.payment_evidence
FROM withdrawal_transfer_reference_reservations r
JOIN withdrawal_transfer_reference_reservation_sources s ON s.reservation_id = r.id
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
New reservations and source links are written in the same transaction as the
withdrawal completion. The unique provider/reference key arbitrates concurrent
completions; a database collision is returned as HTTP 409 with the stable
`TRANSFER_REFERENCE_ALREADY_USED` code.

## Upgrade and rollback

Run migration upgrade tests only through the guarded `bingo_qa_maven_test`
MySQL 8 schema. Test V55-to-V56 with duplicate and distinct-provider references,
an unknown BANK provider, and a completed request without a reference. Separately
exercise current completion, duplicate reuse, and concurrent completion in the
guarded integration suite. Do not clean, reset, or migrate persistent QA or
production databases as part of this procedure.

Rollback is forward-only for financial data. The schema is additive, but
rolling the application back to a build that ignores the reservation ledger
would remove this protection while V54's nullable legacy rows remain. If a
rollback is needed, keep the V56 tables and deploy a compatible forward fix;
do not drop reservation/source/issue rows or restore a backup over a live
schema without an approved recovery plan.
