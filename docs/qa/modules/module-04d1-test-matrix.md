# Module 04D-1 — Claim Validation Matrix

Statuses describe this module's evidence. PASS is reserved for executed checks; source review by itself does not establish runtime security.

| ID | Scenario | Status | Evidence / limitation |
| --- | --- | --- | --- |
| C01 | Authorized player submits a valid claim | BLOCKED | Service path inspected; no HTTP/service runtime or database fixture available. |
| C02 | Player cannot claim another player's ticket | BLOCKED | Ownership-scoped repository lookup is present; no runtime authorization test. |
| C03 | Agent cannot submit player claims | BLOCKED | Endpoint requires PLAYER; no security-filter runtime test. |
| C04 | Support cannot submit player claims | BLOCKED | Endpoint requires PLAYER; no security-filter runtime test. |
| C05 | Finance cannot submit player claims | BLOCKED | Endpoint requires PLAYER; no security-filter runtime test. |
| C06 | Unauthenticated claim is denied | BLOCKED | Security annotation is present; no HTTP security test executed. |
| C07 | Claim without a valid ticket is rejected | BLOCKED | Owner-scoped lookup returns TICKET_NOT_FOUND; no runtime test. |
| C08 | Claim for a different session is rejected | N/A | Request has no session identifier; session is derived from the ticket. A distinct client-supplied session cannot be mismatched in the current API contract. |
| C09 | Claim before a valid winning pattern is rejected | BLOCKED | Pure evaluator rejects a nonmatching pattern. Service behavior persists FAILED_SERVER_VALIDATION; integration behavior not executed. |
| C10 | Claim with insufficient drawn numbers is rejected | BLOCKED | Pure evaluator missing-number cases pass, but server draw sourcing/service persistence was not executed. |
| C11 | Duplicate claim is handled safely | BLOCKED | Idempotency, service existence check, and unique constraints inspected; database replay not executed. |
| C12 | Concurrent duplicate claims cannot create multiple awards | BLOCKED | Unique keys exist in source/schema; no concurrency test or MySQL available. |
| C13 | Cancelled session rejects claims | FAIL | `submitWinClaim` has no session-state check before claim save. This is a source finding; runtime not executed. |
| C14 | Closed session follows existing claim eligibility rules | BLOCKED | No explicit eligibility rule/state gate found. Product decision required; no runtime execution. |
| C15 | Server verifies the winning pattern independently | BLOCKED | Service calls the production evaluator using DB-loaded numbers, but server/service runtime was not exercised. Production evaluator's six deterministic assertion harness checks PASS; this is narrower than C15. |
| C16 | Client cannot choose or modify authoritative draw history | BLOCKED | DTO has only `claimedPattern`; service obtains current sequence/numbers server-side. No HTTP tampering test executed. |
| C17 | Invalid claim requests do not create awards | BLOCKED | Nonmatching case creates a FAILED_SERVER_VALIDATION claim record; entitlement creation occurs only in separate admin approval. Runtime/DB assertion not executed. |
| C18 | Valid claim produces appropriate nonfinancial audit evidence | BLOCKED | Generic audit aspect covers `submitWinClaim`; persisted event and metadata not verified. |
| C19 | Network retry behavior is documented | NOT RUN | Source shows pending idempotency key retained after failure; targeted Flutter controller test and offline UI behavior did not execute. |
| C20 | No wallet credit or prize settlement occurs during this module | PASS | Module ran only pure Java source compilation/assertions and source inspections. No application endpoints, settlement operations, wallet operations, or database mutations were invoked. |

## Executed pure logic checks

The production `BingoPatternEvaluator` was compiled and exercised directly using deterministic card/draw input. Six assertions passed: LINE complete and missing, TWO_LINES complete and insufficient, and FULL_HOUSE complete and missing. Full JUnit/Surefire execution did not occur because the local Maven dependency cache could not be read. See [evidence](../evidence/module-04d1-pure-logic.txt).

## Deferred checks

Do not run DB-backed claim tests until the isolated `bingo_qa_maven_test` account/schema is provisioned and authenticated. Later coverage must include HTTP role denials, foreign ownership, missing/invalid tickets, duplicate replay and concurrent uniqueness, terminal session rules after Product defines CLOSED behavior, claim/audit/entitlement persistence, and Flutter retry/UI behavior. Keep settlement and wallet posting disabled.
