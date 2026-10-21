# Production Release Checklist

**Release decision:** **NO-GO**
**Assessment date:** 2026-10-21

| Release gate | Status | Evidence / required action |
|---|---|---|
| MySQL 8 clean migration and V7 constraints | PASS | Testcontainers MySQL 8.0.36 tests passed. |
| Upgrade from representative prior schema | PASS | V6-to-V12 upgrade test passed. |
| Flyway checksum drift and repair validation | PASS | Upgrade regression test detected drift, repaired history, and validated it. |
| Backup restore / rollback recovery rehearsal | BLOCKED | Execute against an isolated backup/restore environment; Flyway itself is forward-only. |
| Complete backend verification suite | FAIL | 73 tests: 1 failure and 1 error. Run `cd bingo-backend; mvn clean verify` after the two findings are resolved. |
| Concurrent money-flow idempotency | FAIL | Agent duplicate sale returns 500 on unique idempotency constraint; fix and prove no duplicate debit/ticket. |
| Authenticated live WebSocket authorization and replay | FAIL | Real STOMP connection closes before `CONNECTED`; diagnose and prove valid/invalid token behavior, order, reconnect, and replay. |
| Commission accrual and disabled-rule behavior | PASS | Normal flow verified amount/rounding, persistence, replay, and disabled rule. Concurrent retry is blocked by the G3 defect. |
| Admin unit tests, lint, and production build | PASS | 68 tests passed; lint/build passed. |
| Admin browser E2E suite | BLOCKED | Five tests enumerated, but execution requires isolated service URLs and disposable credentials. |
| Onboarding email and first-login workflow | BLOCKED | Mailpit-backed integration scenario not executed. |
| Player and agent mobile device E2E | BLOCKED | No isolated E2E backend/accounts; iOS runner unavailable. Unit tests and agent web build passed. |
| 1,000-player capacity, 10-ticket cap, and load metrics | BLOCKED | No `k6` script/environment; capacity is unmeasured. |
| Security validation | FAIL | Concurrent money-flow idempotency fails and live STOMP auth remains unverified; see [security-validation.md](./security-validation.md). |
| Load evidence | BLOCKED | No load metrics; see [load-test-results.md](./load-test-results.md). |
| Production readiness | FAIL | Mandatory database, money-flow, security, live delivery, and capacity evidence is incomplete. |

## Required before a GO decision

1. Fix and pass the concurrent agent-sale idempotency regression without duplicate financial effects.
2. Diagnose and pass real authenticated STOMP delivery, authorization, reconnect, and replay tests.
3. Complete the isolated MySQL backup/restore recovery rehearsal.
4. Execute the five admin browser E2E scenarios with disposable credentials and retain failure artifacts.
5. Verify onboarding email and first-login/expiry/reuse behavior using Mailpit.
6. Execute Android player/agent E2E tests; run iOS tests on a supported macOS runner if iOS is in the release scope.
7. Provision a reviewed load harness and isolated environment; measure capacity and money-flow invariants through the requested 1,000-player target.
8. Rerun `mvn clean verify`, relevant Flutter checks, admin tests/lint/build, security checks, and all blocked E2E/load gates on the release candidate.

**No production-readiness GO is granted by this report.**
