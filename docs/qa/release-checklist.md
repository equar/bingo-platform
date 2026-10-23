# Production Release Checklist

**Release decision:** **NO-GO**
**Assessment date:** 2026-10-22

> The latest execution and unresolved gates are recorded in [qa-execution-plan-and-results.md](./qa-execution-plan-and-results.md) and [release-blockers.md](./release-blockers.md). An isolated clean backend verification passed all 88 tests and packaged the executable JAR. Release remains **NO-GO**.

| Release gate | Status | Evidence / required action |
|---|---|---|
| MySQL 8 clean migration and V7 constraints | PASS | Testcontainers MySQL 8.0.36 tests passed. |
| Upgrade from representative prior schema | PASS | The current V6-to-V14 Flyway upgrade regression passed. |
| Flyway checksum drift and repair validation | PASS | Upgrade regression test detected drift, repaired history, and validated it. |
| Backup restore / rollback recovery rehearsal | PARTIAL | Earlier isolated restore covered V13 and 30 tables; V14 has not yet been included in a restore test. Confirm production backup retention, recovery objectives, and operator runbook. |
| Complete backend verification suite | PASS | `mvn -q clean verify` from an isolated copy of current source: 88 tests, 0 failures/errors/skips and executable JAR produced against isolated MySQL/Mailpit. The workspace JAR remains held by an unrelated process; that process was not stopped. |
| Concurrent money-flow idempotency | PASS | Focused same-key concurrent sale test verifies replayed results, one ticket, one float debit, and one commission accrual. |
| Authenticated live WebSocket authorization and replay | PASS | Real STOMP CONNECT, ordered draws, disconnect/replay/reconnect, and malformed-token rejection pass. |
| Commission accrual and disabled-rule behavior | PASS | Normal flow verified amount/rounding, persistence, replay, disabled rule, and one accrual for concurrent retries. |
| Admin unit tests, lint, and production build | PASS | 73 tests passed; lint and production build passed. |
| Admin browser E2E suite | PARTIAL | One targeted sessions-management test passed (exit code 0) against isolated services; four other discovered tests remain unexecuted on the release candidate. |
| Onboarding email and first-login workflow | PASS | Mailpit test verified password-free email, 24-hour single-use activation link, activation, replay rejection, and subsequent login. |
| Player and agent mobile device E2E | PARTIAL | Android player setup/deposit/registration and support journeys passed against isolated synthetic services; each phase run exited 0 with one selected journey active and four phase cases returning early. Purchase/settlement/claim, agent-device flows, and iOS remain open. Unit tests and agent web build passed. |
| 1,000-player capacity, 10-ticket cap, and load metrics | BLOCKED | No `k6` script/environment; capacity is unmeasured. |
| Security validation | PARTIAL | Datasource hardening and password-free account activation are implemented and tested. Fastlane is pinned to 2.241.1; its transitive dependency lock and CI validation, production CA trust, proxy deployment, and external security validation remain open. See [security-validation.md](./security-validation.md). |
| Load evidence | BLOCKED | No load metrics; see [load-test-results.md](./load-test-results.md). |
| Production readiness | FAIL | Mandatory database, money-flow, security, live delivery, and capacity evidence is incomplete. |

## Required before a GO decision

1. Repeat backup restore validation through V14 and confirm production retention, recovery objectives, and operator recovery runbook.
2. Execute the remaining four admin browser E2E scenarios with disposable credentials on the release candidate. Keep trace/screenshot/video capture disabled unless sanitized artifacts are required.
3. Retain the passing G6 Mailpit/MySQL integration results on the release candidate and confirm production SMTP delivery configuration with the mail operator.
4. Execute Android purchase/settlement/claim and agent E2E tests; run iOS tests on a supported macOS runner if iOS is in the release scope.
5. Provision a reviewed load harness and isolated environment; measure capacity and money-flow invariants through the requested 1,000-player target.
6. Retain the isolated clean backend verification evidence and rerun it on the exact release candidate; rerun mobile/admin/security checks and all blocked E2E/load gates on the release candidate.
7. Lock Fastlane's transitive gem dependencies and verify the pinned 2.241.1 installation/build steps in CI.

**No production-readiness GO is granted by this report.**
