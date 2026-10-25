# Module 04B-2 — Test Matrix

| ID | Scenario | Result | Evidence / disposition |
| --- | --- | --- | --- |
| C01 | Purchase an eligible ticket in an OPEN session before actual start, even when scheduled time is in the future | PASS | MySQL integration purchase path |
| C02 | Purchase at exact scheduled cutoff | BLOCKED | Product has not defined a schedule-time cutoff or boundary inclusivity |
| C03 | Purchase 1 ms after scheduled cutoff | BLOCKED | Same unresolved Product rule; no schedule-time cutoff is implemented |
| C04 | Purchase 1 ms before scheduled cutoff | BLOCKED | Same unresolved Product rule; no schedule-time cutoff is implemented |
| C05 | Reject DRAFT session | PASS | Lifecycle validation integration coverage |
| C06 | Reject CANCELLED session | PASS | Lifecycle validation integration coverage |
| C07 | Reject closed/completed session | PASS | Lifecycle validation integration coverage |
| C08 | Reject session in inactive room | PASS | Room eligibility integration coverage |
| C09 | Reject invalid session ID | PASS | Service/API validation coverage |
| C10 | Reject unauthenticated purchase | PASS | HTTP route integration assertion |
| C11 | Reject unauthorized admin and agent roles | PASS | HTTP route integration assertions |
| C12 | Reject restricted player | PASS | Player eligibility integration coverage |
| C13 | Insufficient wallet balance causes no issuance or debit | PASS | Integration assertions include unchanged idempotency and journal counts |
| C14 | Ignore malicious client-supplied ticket price | PASS | HTTP request supplied 0.01 and replay supplied 999999.99; server session price charged |
| C15 | Duplicate request replays the original result | PASS | Idempotency integration coverage |
| C16 | Concurrent purchases cannot overspend or exceed capacity | PASS | Session/wallet lock integration coverage |
| C17 | Forced outer transaction failure rolls back the purchase | PASS | MySQL `TransactionTemplate` test flushes then throws |
| C18 | Failed transaction leaves no ticket | PASS | Rollback test verifies no ticket row |
| C19 | Failed transaction leaves no wallet debit or wallet transaction | PASS | Rollback test verifies wallet balances and transaction count |
| C20 | Denied purchase creates no financial journal | PASS | Denial assertions verify existing journal counts unchanged; a ticket-sale journal is not implemented or approved |
| C21 | Schedule/room configuration change does not mutate a session snapshot; actual start cutoff applies | PASS | Session snapshot stability and actual-start integration test |
| C22 | Persistent `bingo_qa` remains unchanged | PASS | Read-only before/after reconciliation; see evidence file |

C02–C04 are not failures of an implemented rule: the scheduled-time sale rule has not been specified. Do not close those cases by assuming the session schedule is a cutoff.

Backend full-suite totals are in [the full-run summary](../evidence/module-04b2-backend-full-summary.txt). Persistent QA comparison is in [the database reconciliation](../evidence/module-04b2-database-readonly.txt).
