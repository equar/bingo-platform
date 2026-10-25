# Module 04C-4 — Automatic Draw Retry and Audit Assessment

Disposition: **PARTIAL — current semantics and integrity are established from code and prior evidence; dedicated live MySQL retry/concurrency verification was blocked before test execution**

Scope: Module 04C-4 only. No public API contract was added and no production financial posting was enabled.

## Current retry behavior

`POST /api/v1/admin/sessions/{id}/draws` has no request body or request identifier in the current OpenAPI contract. The Admin UI disables its button while the request is pending and does not automatically retry, but a transport failure can leave the caller unsure whether the server committed.

The backend runs each trigger in a transaction, locks the session row pessimistically, checks the last draw timestamp against the configured interval, chooses an unused number, writes the draw and its audit entry, and publishes only after commit. Two simultaneous requests for the same due instant serialize; a later request inside the interval returns `DRAW_NOT_AVAILABLE` (HTTP 409). A retry after the next interval is due is a new operation and can create the next sequence/different number. Thus an immediate repeat does not duplicate a committed draw, but the endpoint is **not request-idempotent**: the original response cannot be recovered by replaying the same operation identity.

The committed test class `DrawRetrySafetyIntegrationTest` models a lost response, immediate retry, retry after the interval, same-instant contention, audit matching, unique sequence/number, and timestamp equality. The only authorized execution attempt in this audit failed its isolation guard before connecting because the local `.env.test` account is rejected by MySQL; results are therefore **NOT VERIFIED in this run**, not a pass. Existing Module 04C evidence remains: concurrent same-instant calls yield one draw, persisted draws have unique session/sequence and session/number constraints, current-source draws have matching audit events, and the 45 stale-backend draws lacked audits.

## Integrity assessment

The service assigns monotonically ordered sequence numbers, generates operation UUIDs and server timestamps truncated to database precision, excludes prior numbers in the session, and records `BINGO_NUMBER_DRAWN` in the same transaction with session target, operation ID, sequence, number and source. Database unique constraints cover session/sequence, session/number and operation ID. Audit is append-oriented through the ordinary API; no normal draw replacement endpoint was found. Prior C2 and C3B reconciliations show matching current-source draw/audit pairs. The original session #30 contained 45 unaudited stale-runtime draws and one audited current-source draw. That stale packaged backend is the documented explanation for the audit gap; no missing audit rows were fabricated or retroactively repaired.

## Recommendation and decision required

Adopt an explicit request-idempotency contract before treating a lost response as safely retryable:

- Require a client-generated UUID per intended draw operation and persist it atomically with the draw result.
- Enforce a unique key scoped to the operation (at minimum session plus request ID; specify actor/route scope deliberately).
- On replay of the same key and same request, return the original result, including its operation ID, sequence, number, and authoritative draw timestamp. A different key represents a new operation and still obeys due-time/session rules.
- Define behavior when the same key is reused with a different session or request payload, expiration/retention, authorization on replay, and concurrent insert conflict handling.
- Keep database uniqueness as the concurrency authority; test response-loss replay and racing duplicate keys on isolated MySQL.

This changes the public API contract and Admin client behavior. Product/API approval is required before implementation. No existing approved draw idempotency contract was found, and none is claimed implemented. Existing bodyless callers would require a compatibility/migration decision.

## Test result

`mvn -Dmaven.repo.local=C:\Users\16414\.m2\repository -Dtest=DrawRetrySafetyIntegrationTest test` selected only the isolated append-only test class. Surefire reported 2 tests, 0 failures, 2 errors: class initialization failed because MySQL rejected `bingo_test` credentials in `MySqlIntegrationTest` before the tests connected. No draw fixtures were created. Do not run the class until the approved isolated account is restored and its access guard passes. The destructive Flyway recovery class and broad integration suite were not run.

Module 04C remains PARTIAL: request idempotency is unresolved pending Product/API approval and a successful isolated DB verification. Module 04C-4 is not complete.

### 04C-5 recovery status

The focused test still cannot run safely: the 04C-4 test account was rejected, and no MySQL administrator credentials are available to create the separate `bingo_qa_maven_test` schema and scoped test account. C5 stopped before the destructive test suite. C5A prepared the code-side guard and datasource separation, but neither is runtime-verified. The existing R05 test also sleeps across the configured interval; deterministic time control is needed before claiming the requested retry regression is suitable. See [C5 isolation report](module-04c5-database-isolation.md), [C5A recovery plan](module-04c5a-mysql-recovery-plan.md), [draw retry test report](module-04c5-draw-retry-tests.md), and [gate evidence](../evidence/module-04c5-isolation-gate.txt). Product/API approval for request-idempotency remains open.
