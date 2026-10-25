# Module 04B-2 — Ticket Sale Cutoff and Purchase Integrity

Disposition: **PARTIAL — integrity controls verified in isolated MySQL QA tests; sale schedule semantics and financial posting policy remain blocked**  
Scope: purchase cutoff, eligibility, idempotency, concurrency, rollback, and financial posting guard. Module 04C was not started.

## Cutoff semantics

The implemented cutoff is the actual transition to `STARTED`. The transition records `startedAt`; a purchase after that timestamp is rejected with `SESSION_ALREADY_STARTED` when the global `APP_GAME_TICKET_SALES_CLOSE_ON_SESSION_START` setting or the session's persisted `ticketSalesCloseOnSessionStart` snapshot is true. The effective check is fail-closed if either flag is true. The global setting defaults to true.

`scheduledStartAt` is not itself a sale cutoff. The current product requirements do not define whether an `OPEN` session becomes unavailable at its scheduled time, or the exact inclusive/exclusive boundary. C02–C04 therefore remain BLOCKED pending a Product decision. No schedule-based cutoff or clock injection was introduced. The precedence intended between the global property and per-session snapshot also needs Product clarification; the current OR behavior is conservative.

## Purchase integrity

Purchase uses one transaction and locks the session before capacity checks. The service derives price from the server-side session snapshot; request-supplied prices are ignored. Eligibility checks cover lifecycle state, active room, player status/role, capacity, player limits, and wallet balance. Wallet debit, ticket issuance, wallet transaction, and idempotency record commit or roll back together. Session serialization protects capacity; wallet locking protects balances across sessions. An identical idempotency key/request replays its stored response, while a conflicting request hash is rejected.

Production posting is disabled by `LocalFinancialSimulationGuard`: purchase is permitted only with the `test` profile against local MySQL schema `bingo_qa_test` on port 3306. The guard rejects production/QA profiles and other database targets. Tests use the real MySQL test schema; persistent `bingo_qa` is read-only and is not a purchase target.

No Finance-approved ticket-sale journal mapping or revenue, prize-pool, commission, tax, cancellation, or refund policy was found. The current service writes no ticket-sale accounting journal. Production purchase posting remains disabled until Finance and Product policies are approved.

## Verification

The final focused integration and guard tests and full backend suite results are recorded in [the focused summary](../evidence/module-04b2-backend-focused-summary.txt) and [the full-run summary](../evidence/module-04b2-backend-full-summary.txt), with sanitized logs alongside them. The purchase tests cover actual-start denial, server-side price, role/access rejection, idempotent replay, insufficient funds, concurrent overspend prevention, and forced outer-transaction rollback.

The before/after `bingo_qa` reconciliation is read-only and is recorded in [the database comparison](../evidence/module-04b2-database-readonly.txt). SQL used for both snapshots is [here](../evidence/module-04b2-database-readonly.sql). The exact C01–C22 disposition is in the [test matrix](module-04b2-test-matrix.md).

Module 04B remains PARTIAL. Module 02 remains INCOMPLETE and Module 03 remains COMPLETE FOR DEFINED QA SCOPE.
