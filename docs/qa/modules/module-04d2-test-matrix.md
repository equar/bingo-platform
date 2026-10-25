# Module 04D-2 — Claim Lifecycle Security and E2E Matrix

PASS results are explicitly limited to the executed unit/controller/analyzer scope. Database-backed and UI E2E scenarios are not inferred from unit tests.

## Backend MySQL scenarios

| ID | Scenario | Status | Evidence / reason |
| --- | --- | --- | --- |
| B01 | Valid player claim persists correctly | BLOCKED | Local MySQL exists, but the dedicated Maven schema/account is not provisioned and no authorized test grant is verified. |
| B02 | Nonwinning pattern is handled correctly | BLOCKED | Production evaluator unit tests pass; claim-row persistence semantics need MySQL assertion. |
| B03 | Claim on CANCELLED session is rejected | BLOCKED | Service-unit guard passes; local MySQL is available, but schema/account access and authoritative row/transaction behavior are not verified. |
| B04 | Another player's ticket is rejected | BLOCKED | Ownership query is present; DB/API integration not run. |
| B05 | Unauthenticated request is denied | BLOCKED | Endpoint contract is unit-inspected; HTTP security chain not run. |
| B06 | Agent, support and finance roles are denied | BLOCKED | PLAYER-only endpoint contract checked; HTTP role tests not run. |
| B07 | Duplicate claim does not create duplicate records | BLOCKED | Idempotency and unique constraints remain unverified on isolated MySQL. |
| B08 | Concurrent duplicate requests remain consistent | BLOCKED | No MySQL concurrency run. |
| B09 | Invalid ticket/session relationship is rejected | N/A | API has no session field; server derives session from owner-verified ticket. DB relationship constraints were not independently tested. |
| B10 | Server verifies actual MySQL draw history | BLOCKED | Pure evaluator is tested; MySQL draw sourcing not exercised. |
| B11 | Claim audit is persisted correctly | BLOCKED | Aspect failure/success boundary inspected; no audit-row query. |
| B12 | Failed claim transaction rolls back | BLOCKED | No database transaction test. |
| B13 | Restart preserves claim history | BLOCKED | No persisted claim or restart test. |
| B14 | No wallet or settlement posting occurs | BLOCKED | Cancellation unit path made no wallet/entitlement repository calls, but required MySQL before/after assertions were not run. At the 04D-2 review, Maven `test` profile flags were true; 04D-3 changed defaults to false and added guarded disposable-schema opt-in. Runtime DB preflight remains unverified. |

## Android emulator scenarios

| ID | Scenario | Status | Evidence / reason |
| --- | --- | --- | --- |
| A01 | Player logs in | BLOCKED | E2E preflight stopped because test DB/account isolation is unverified. |
| A02 | Player opens eligible game | BLOCKED | No approved eligible ticket/session fixture was established. |
| A03 | Actual card and draw numbers display | BLOCKED | No real E2E session/ticket fixture. |
| A04 | Player submits valid claim | BLOCKED | Requires a valid owned ticket; no approved paid-ticket fixture path. |
| A05 | Backend validates claim against MySQL | BLOCKED | No isolated database account/schema. |
| A06 | Android displays authoritative result | BLOCKED | No claim submission was made. |
| A07 | Claim and audit match MySQL | BLOCKED | No claim/audit rows created or queried. |
| A08 | Duplicate submission handled safely | BLOCKED | No app-to-DB submission. Flutter controller retry is unit-tested separately. |
| A09 | Invalid claim displays suitable error | BLOCKED | No Android claim response. |
| A10 | App restart restores claim history | BLOCKED | No persisted claim fixture. |
| A11 | Offline submission controlled | BLOCKED | Real Android network scenario not run. |
| A12 | Network recovery creates no duplicate | BLOCKED | Real Android/API/MySQL recovery not run. |

## Admin and regression

| Check | Status | Result |
| --- | --- | --- |
| Authorized Admin sees submitted claims | BLOCKED | No E2E claim fixture or isolated backend verification. |
| Claim status/references/audit match MySQL | BLOCKED | No DB query. |
| Unauthorized roles cannot review claims | BLOCKED | Admin HTTP/browser security tests not run. |
| No settlement/wallet posting in Admin E2E | BLOCKED | No Admin E2E ran; no operation was invoked by this work. Actual data reconciliation unavailable. |
| Focused Java unit tests | PASS | 15 run, 0 failures, 0 errors, 0 skipped across four named classes. |
| Static API contract | PASS | Claim DTO, route, PLAYER guard, and Idempotency-Key inspected by executed unit test. |
| Focused Flutter claim test | PASS | 1/1; retry preserves idempotency key after simulated transport failure. |
| Flutter analyzer | PASS | No issues found. |
| Real MySQL integration / reconciliation | BLOCKED | Maven schema unprovisioned and no authorized DBA/test account available. |
| Real Android / Chromium E2E | BLOCKED | DB isolation and approved ticket fixture preconditions unmet. |
| `git diff --check` | NOT RUN | To be recorded after documentation and code review. |
