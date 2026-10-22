# Production Release Checklist

**Release decision:** **NO-GO**
**Assessment date:** 2026-10-21

> The current independent review is [final-independent-qa-review.md](./final-independent-qa-review.md). Release remains **NO-GO**. D4 proxy-aware throttling changes are in progress and must pass focused and full backend tests before this gate can be reconsidered.

| Release gate | Status | Evidence / required action |
|---|---|---|
| MySQL 8 clean migration and V7 constraints | PASS | Testcontainers MySQL 8.0.36 tests passed. |
| Upgrade from representative prior schema | PASS | V6-to-V12 upgrade test passed. |
| Flyway checksum drift and repair validation | PASS | Upgrade regression test detected drift, repaired history, and validated it. |
| Backup restore / rollback recovery rehearsal | BLOCKED | Execute against an isolated backup/restore environment; Flyway itself is forward-only. |
| Complete backend verification suite | PASS | `mvn clean verify`: 73 tests, 0 failures, 0 errors, 0 skipped on MySQL 8.0.36. |
| Concurrent money-flow idempotency | PASS | Focused same-key concurrent sale test verifies replayed results, one ticket, one float debit, and one commission accrual. |
| Authenticated live WebSocket authorization and replay | PASS | Real STOMP CONNECT, ordered draws, disconnect/replay/reconnect, and malformed-token rejection pass. |
| Commission accrual and disabled-rule behavior | PASS | Normal flow verified amount/rounding, persistence, replay, disabled rule, and one accrual for concurrent retries. |
| Admin unit tests, lint, and production build | PASS | 68 tests passed; lint/build passed. |
| Admin browser E2E suite | BLOCKED | Five tests enumerated, but execution requires isolated service URLs and disposable credentials. |
| Onboarding email and first-login workflow | BLOCKED | Mailpit-backed integration scenario not executed. |
| Player and agent mobile device E2E | BLOCKED | No isolated E2E backend/accounts; iOS runner unavailable. Unit tests and agent web build passed. |
| 1,000-player capacity, 10-ticket cap, and load metrics | BLOCKED | No `k6` script/environment; capacity is unmeasured. |
| Security validation | PASS (tested scope) | The Phase 6 concurrency and STOMP checks pass; independent security review and earlier-phase control verification remain required. See [security-validation.md](./security-validation.md). |
| Load evidence | BLOCKED | No load metrics; see [load-test-results.md](./load-test-results.md). |
| Production readiness | FAIL | Mandatory database, money-flow, security, live delivery, and capacity evidence is incomplete. |

## Required before a GO decision

1. Complete the isolated MySQL backup/restore recovery rehearsal.
2. Execute the five admin browser E2E scenarios with disposable credentials and retain failure artifacts.
3. Verify onboarding email and first-login/expiry/reuse behavior using Mailpit.
4. Execute Android player/agent E2E tests; run iOS tests on a supported macOS runner if iOS is in the release scope.
5. Provision a reviewed load harness and isolated environment; measure capacity and money-flow invariants through the requested 1,000-player target.
6. Rerun `mvn clean verify`, relevant Flutter checks, admin tests/lint/build, security checks, and all blocked E2E/load gates on the release candidate.

**No production-readiness GO is granted by this report.**
