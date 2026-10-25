# Module 04D-2 — Real Database Integration and End-to-End Claim Validation

Disposition: **PARTIAL — the CANCELLED-session guard is implemented and focused Java/Flutter tests pass. Real MySQL, Android, and Admin claim E2E remain BLOCKED because the isolated Maven schema/account are not provisioned and no authorized `bingo_qa_test` grant/fixture path has been verified.**

Scope: Module 04D-2 only. No claim mutation, gameplay test, settlement, wallet credit, or financial posting was attempted. `bingo_qa` and `bingo_qa_test` were not accessed. No 04E work was started.

## Defect and change

Before this change, `BingoGameService.submitWinClaim` verified player role and ticket ownership, then evaluated and saved the claim without checking the session lifecycle state.

The service now derives the session ID from the owned ticket and, inside the idempotent supplier and service transaction, loads the session using `GameSessionRepository.findByIdForUpdate`. `WinClaimSessionValidator` rejects authoritative `CANCELLED` with HTTP 409 domain code `SESSION_CANCELLED`; a null/invalid state returns `SESSION_STATE_INVALID`. This occurs before duplicate/draw evaluation, claim save, or any award/financial collaborator.

Session state changes in `updateSessionState` already use the same `findByIdForUpdate` row lock. Therefore, under MySQL/InnoDB and the existing transactional service boundary, a new claim and cancellation serialize on the same session row: whichever locks first completes its state decision first. If cancellation commits first, the new claim sees CANCELLED and is rejected. If claim submission locks first, that claim may finish before the later cancellation. This is an atomic ordering of the decisions, not a guarantee that cancellation retroactively invalidates an already accepted claim. The concurrency behavior still requires real MySQL verification; no such test ran here.

Ticket ownership remains checked before session locking. The endpoint remains PLAYER-only and requires the same idempotency header. The request DTO still contains only `claimedPattern`, so the client cannot supply status, session identity, card contents, or draw history. CLOSED is not rejected by the new validator and retains prior behavior pending Product's eligibility decision. The supported terminal states are CLOSED and CANCELLED; no COMPLETED state was added.

## Rejection side effects and audit boundary

Focused production-service unit tests execute the idempotent supplier with an authoritative CANCELLED session and assert `winClaimRepository.save` is never called, as well as no interaction with draw, entitlement, wallet, or wallet-transaction repositories. This verifies the rejection path does not create a claim/award or initiate posting within the tested service path.

`AuditLogAspect` wraps public service methods including `submitWinClaim`: a thrown domain conflict is handled by its failure-audit path, not its success-audit path. The aspect and audit row are not part of the isolated unit test. Persisted failure audit evidence and transaction rollback behavior require database-backed verification and remain BLOCKED.

Nonmatching patterns retain existing semantics: the service persists a `FAILED_SERVER_VALIDATION` claim record, with no entitlement. That differs from cancellation rejection, which exits before a claim row is created. Admin approval creates a prize entitlement, while settlement remains fail-closed; this module did not approve claims or invoke settlement.

## Test execution and recovery

### Java

The local Maven cache's `spring-boot-starter-jackson-4.1.1.jar` passes `jar tf` and SHA-256 inspection, so the archive is readable and structurally valid. Maven Central connectivity check to `repo.maven.apache.org:443` failed; the test was run offline from the existing cache. In-process Javac emitted `AccessDeniedException` while resolving the real path of that cached JAR during ZipFileSystem cleanup. Setting `-Dmaven.compiler.fork=true` let Maven proceed through compilation and Surefire without deleting or rewriting the Maven cache. The compiler diagnostic still appears, but the final selected-test build exits successfully.

Focused command selected only non-Spring-context unit classes: `WinClaimSessionSecurityTest`, `BingoPatternEvaluatorTest`, `BingoGameRulesTest`, and `GameSessionStateTest`. Surefire result: **15 tests, 0 failures, 0 errors, 0 skipped** (6 claim/security service unit tests, 3 pattern evaluator tests, 4 game rules tests, 2 session-state tests). The integration suite and any `MySqlIntegrationTest` subclass were not selected. Static endpoint contract checks are included in the claim unit test.

### Flutter

Flutter initially appeared hung because `C:\src\flutter` is outside the writable workspace: its batch wrapper could not acquire/write the SDK cache lock. A Git safe-directory check also rejected the SDK checkout under the sandbox identity. No stale Dart process or SDK lock deletion was needed. Using a command-scoped `safe.directory=C:/src/flutter` and the tool-approved elevated run recovered the CLI without changing global Git configuration.

- Focused `live_game_controller_test.dart`: **1/1 PASS**; it verifies same idempotency key is reused after a failed submit.
- `flutter analyze`: **No issues found** (61.2 seconds).
- Flutter reported 23 dependency updates available but none were applied. No application claim was sent.

### Database and E2E

The local MySQL server is present (the prior 04C-5B diagnostics observed the MySQL80 service), but `bingo_qa_maven_test` remains unprovisioned. Prior Module 04C-5B evidence records no authorized DBA login, so Maven schema/account grants and authentication are not verified. The `bingo_qa_test` runtime account/grants are also not currently verified. Consequently the preflight requirements—active datasource, grant isolation, environment identity and before/after SQL snapshots—cannot be satisfied, and no DB connection was attempted. At the time of this 04D-2 review, the Maven `test` profile set signup-reward posting, deposit simulation, adjustment simulation, and agent-float simulation flags to `true`; no Spring context was started and no posting was executed. Module 04D-3 changed these defaults to false and added a separate explicit opt-in constrained to the disposable Maven schema/account. Runtime DB access and grant verification remain blocked until DBA provisioning is complete. The Android test profile continues to keep these switches false.

No approved isolated fixture path for an already-paid ticket was identified. With financial posting disabled, the test must not bypass the purchase guard or synthesize a paid ticket by SQL. Backend B01–B13, Android A01–A12, Admin browser claim review, persistence/restart, concurrency, and audit assertions are BLOCKED. The Module 04C recovery/DBA blocker is the database dependency. Module 02 financial safeguards remain unchanged; no settlement or wallet posting is enabled.

Exact Java output, Flutter results, cache/toolchain diagnosis, and E2E access disposition are recorded in [Module 04D-2 evidence](../evidence/module-04d2-execution.txt). Scenario-level results are in the [test matrix](module-04d2-test-matrix.md).

## Remaining decisions and blockers

- Product must define CLOSED-session claim eligibility before changing or approving that behavior.
- DBA provisioning and successful authentication/grant verification are required before backend database integration tests.
- An approved nonfinancial ticket fixture path or an approved isolated purchase mechanism is required before a valid-claim E2E; no payment safeguard may be bypassed.
- MySQL verification is still needed for cancellation/claim lock ordering, audit persistence on rejected claims, claim persistence/restart, and before/after no-posting reconciliation. Confirm all Maven test profile financial simulation/posting switches are disabled for that run.
- Admin claim UI and real Android claim history remain unverified end to end.

Module 04D remains PARTIAL pending real claim integration/E2E. Module 04C remains PARTIAL, Module 04B remains PARTIAL, Module 02 remains INCOMPLETE, and Module 03 remains COMPLETE FOR DEFINED QA SCOPE. No commit or push was made.
