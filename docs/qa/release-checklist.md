# Production Release Checklist

**Release decision:** **NO-GO**
**Assessment date:** 2026-10-22

> The current independent review is [final-independent-qa-review.md](./final-independent-qa-review.md). Release remains **NO-GO**. A clean backend build from a temporary copy passed all 85 tests and completed packaging against isolated MySQL/Mailpit containers, including G6 onboarding and OpenAPI. An isolated MySQL dump/drop/restore rehearsal passed; production-specific and live operational gates remain open.

| Release gate | Status | Evidence / required action |
|---|---|---|
| MySQL 8 clean migration and V7 constraints | PASS | Testcontainers MySQL 8.0.36 tests passed. |
| Upgrade from representative prior schema | PASS | V6-to-V13 upgrade test passed. |
| Flyway checksum drift and repair validation | PASS | Upgrade regression test detected drift, repaired history, and validated it. |
| Backup restore / rollback recovery rehearsal | PASS (isolated restore) | A disposable MySQL dump/drop/restore recovered Flyway V13 and all 30 tables. Confirm production backup retention, recovery objectives, and operator runbook before release. |
| Complete backend verification suite | PASS | `mvn clean verify` from a temporary copy of current backend source: 85 tests, 0 failures/errors/skips, successful packaging against isolated MySQL 8.0.36/Mailpit. |
| Concurrent money-flow idempotency | PASS | Focused same-key concurrent sale test verifies replayed results, one ticket, one float debit, and one commission accrual. |
| Authenticated live WebSocket authorization and replay | PASS | Real STOMP CONNECT, ordered draws, disconnect/replay/reconnect, and malformed-token rejection pass. |
| Commission accrual and disabled-rule behavior | PASS | Normal flow verified amount/rounding, persistence, replay, disabled rule, and one accrual for concurrent retries. |
| Admin unit tests, lint, and production build | PASS | 70 tests passed; coverage 63.72% statements, 61.81% branches, 50.69% functions, 63.56% lines; lint/build passed. |
| Admin browser E2E suite | BLOCKED | Five tests enumerated, but execution requires isolated service URLs and disposable credentials. |
| Onboarding email and first-login workflow | PASS | Mailpit SMTP delivery, first login, forced password change, 24-hour expiry, and temporary-password invalidation/reuse checks passed against isolated services. |
| Player and agent mobile device E2E | BLOCKED | No isolated E2E backend/accounts; iOS runner unavailable. Unit tests and agent web build passed. |
| 1,000-player capacity, 10-ticket cap, and load metrics | BLOCKED | No `k6` script/environment; capacity is unmeasured. |
| Security validation | PASS (tested scope) | Focused D4 failure-only throttling/trusted-proxy tests and D5 issued-JWT Actuator role checks passed; Phase 6 concurrency and STOMP checks pass. Independent security review, proxy deployment confirmation, and updated full backend verification remain required. See [security-validation.md](./security-validation.md). |
| Load evidence | BLOCKED | No load metrics; see [load-test-results.md](./load-test-results.md). |
| Production readiness | FAIL | Mandatory database, money-flow, security, live delivery, and capacity evidence is incomplete. |

## Required before a GO decision

1. Validate production backup retention, recovery objectives, and operator recovery runbook; the isolated MySQL restore rehearsal passed.
2. Execute the five admin browser E2E scenarios with disposable credentials and retain failure artifacts.
3. Retain the passing G6 Mailpit/MySQL integration results on the release candidate and confirm production SMTP delivery configuration with the mail operator.
4. Execute Android player/agent E2E tests; run iOS tests on a supported macOS runner if iOS is in the release scope.
5. Provision a reviewed load harness and isolated environment; measure capacity and money-flow invariants through the requested 1,000-player target.
6. Retain the passing `mvn clean verify` and isolated MySQL/Mailpit evidence for the release candidate; rerun relevant Flutter checks, admin tests/lint/build, security checks, and all blocked E2E/load gates on the release candidate.

**No production-readiness GO is granted by this report.**
