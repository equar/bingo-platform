# Bingo Platform Production-Readiness Report

**Execution date:** 2026-10-22
**Baseline:** October 20, 2026 End-to-End QA Report
**Branch:** `muse-code-review`

> **Independent audit update:** See [final-independent-qa-review.md](./final-independent-qa-review.md) for the D1–D16 matrix, follow-up code/tests, and current release decision. It confirms **NO-GO**. D4/D5 passed, and a clean backend build from a temporary copy passed 85 tests and packaging, including G6 DB/Mailpit onboarding and the OpenAPI contract check. An isolated MySQL dump/drop/restore rehearsal also passed.

## Release decision

**NO-GO.** The concurrent agent-sale idempotency failure and authenticated STOMP failure from the initial Phase 6 run have been fixed and now pass regression tests. Release remains blocked because the required 1,000-player load test, complete Playwright flow, device E2E flows, production backup/recovery validation, production CloudFront verification, and continuous operational monitoring were not completed.

The configured capacity of 1,000 players and maximum of 10 tickets per player are configuration values, not measured capacity evidence.

## Environment

- Windows 11; Java 25.0.2; Maven 3.9.6; Docker CLI connected to server 29.9.2.
- Backend integration tests used Testcontainers MySQL 8.0.36 with Flyway enabled.
- Node 22.12.0; Playwright 1.64.1; Chromium installed.
- Flutter 3.47.7 / Dart 3.13.5; Android emulator `emulator-5554` (Android API 37) visible.
- No iOS simulator/Xcode on this Windows host; `k6` and `adb` are not on `PATH`.
- Ports 3306, 8080, and 5173 were already occupied by processes not started for this run. They were not modified or used for financial E2E tests. Ports 1025, 8025, and 13306 were initially free and reserved for disposable loopback-only QA containers.
- No E2E credentials were available in the process environment. Credentials and tokens were not printed or stored.
- During the 2026-10-22 follow-up, the Java Testcontainers named-pipe strategy could not reach the Docker engine, although the Docker CLI could. Tests used disposable loopback-only Docker CLI containers: MySQL 8.0.36 on port 13306 and Mailpit on ports 1025/8025. Existing `kirshi-*` containers and the service on port 3306 were not touched.
- A clean `mvn clean verify` from a temporary copy of current backend source passed all 85 tests and completed packaging. The existing workspace JAR remained held by a separate running Java process, which was left untouched.

## Gap results

### G1 — MySQL 8 and Flyway

| Scenario | Status | Evidence |
|---|---|---|
| Clean/upgrade migration execution on MySQL 8 | PASS | The backend suite passed on isolated MySQL 8.0.36, upgrading V6 through V13. `FlywayV7MigrationIntegrationTest` also verified the V7 constraints. |
| Flyway history checksum verification/repair | PASS | The upgrade test checked stored checksums, detected deliberately altered history, ran Flyway `repair()`, and validated the repaired history. |
| Full backend suite against MySQL | PASS | Clean build from a temporary source copy: 85 tests, 0 failures, 0 errors, 0 skipped, and successful packaging against isolated MySQL/Mailpit. |
| Database backup restore / rollback recovery rehearsal | PARTIAL | A disposable MySQL dump/drop/restore recovered Flyway V13 and all 30 tables. Production backup retention, recovery objectives, and operator restore procedures were not verified. |

### G2 — Live WebSocket draw verification

| Scenario | Status | Evidence |
|---|---|---|
| Authenticated STOMP connection to the live backend | PASS | The integration test receives `CONNECTED` after configuring bearer-JWT authentication rather than HTTP-session CSRF on STOMP CONNECT. |
| Ordered draw delivery, reconnect, and missed-sequence replay | PASS | The real client received sequence 1, replay API returned missed sequence 2 after disconnect, and the reconnected client received sequence 3 in order. |
| Invalid-token denial on the live broker | PASS | The same test verifies a malformed JWT cannot establish a STOMP session. |

### G3 — Agent ticket sales and payouts

| Scenario | Status | Evidence |
|---|---|---|
| Anonymous ticket sale authorization | PASS | The HTTP integration test received 401 for an anonymous sale. |
| Normal agent sale, sequential idempotency replay, and wallet float movement | PASS | The test verified a 201 sale and replay returning the same ticket, one ticket persisted, and the expected float balance. |
| Payout verification and replay | PASS | The test verified 200 responses, the same payout record on replay, and the expected available/clearing balances. |
| Insufficient-float rejection | PASS | Sale returned a 4xx without creating another ticket or commission accrual, and the available balance remained unchanged. |
| Concurrent duplicate sale using the same actor/key | PASS | Database-level actor locking and a locking idempotency-record lookup serialize retries through transaction commit. Both requests now replay the same ticket, with one ticket, one float debit, and one commission accrual. |

### G4 — Commission accrual

| Scenario | Status | Evidence |
|---|---|---|
| Enabled rule, persistence, rounding, and sequential retry | PASS | HTTP integration flow persisted one accrual for a 4.25 sale at 2.5%, rounded to 0.11; replay did not duplicate the sale/accrual. |
| Disabled commission rule | PASS | A subsequent sale after disabling the rule created no additional accrual. |
| Duplicate concurrent sale/event | PASS | The concurrent same-key sale regression confirms one persisted ticket/accrual and the expected float balance after both requests complete. |

### G5 — Admin Playwright

| Scenario | Status | Evidence |
|---|---|---|
| Existing admin unit/component/API tests | PASS | `npm run test:coverage -- --maxWorkers=1`: 70 tests passed in 25 files; statements 63.72%, branches 61.81%, functions 50.69%, lines 63.56%. |
| Admin lint and production build | PASS | `npm run lint` and `npm run build` passed. Production main bundle measured 414.52 kB raw / 137.81 kB gzip. |
| Enumeration of existing browser scenarios | PASS | `npx playwright test --list` with non-secret placeholder environment values listed all five tests across two files. |
| Execution of five browser E2E scenarios and failure artifacts | BLOCKED | Actual credentials were unavailable and existing local backend/admin ports belonged to processes outside this run. No financial E2E flow was sent to those services. Playwright now retains traces, screenshots, and video on failure; no artifacts exist because the E2E tests were not run. |

### G6 — Admin-created user onboarding

| Scenario | Status | Evidence |
|---|---|---|
| SMTP delivery through Mailpit, first login, expiry/reuse behavior | PASS | Isolated MySQL/Mailpit integration tests verified actual SMTP delivery, first login, forced password change, 24-hour expiry, and temporary-password invalidation/reuse. |

### G7 — Mobile device verification

| Scenario | Status | Evidence |
|---|---|---|
| Player/agent Flutter unit tests and analysis | PASS | Player: `flutter test` (20 passed), `flutter analyze` passed. Agent: `flutter test` (10 passed), `flutter analyze` passed. |
| Agent web build | PASS | `flutter build web` passed. |
| Player Android end-to-end flows | BLOCKED | An Android emulator was available, but the isolated backend and disposable E2E credentials/manifest were not. No live financial flow was run. |
| Agent Android end-to-end operations | BLOCKED | No isolated backend, disposable credentials, or agent device integration scenario was available for the real operations requested. |
| iOS simulator verification | BLOCKED | Xcode and an iOS simulator are unavailable on this Windows host. |
| Player web build | FAIL | `flutter build web --dart-define=API_BASE_URL=https://api.example.invalid` failed because the player app is not configured for web. This is not evidence of failure for its supported Android/iOS targets. |

### G8 — Load and concurrency

| Scenario | Status | Evidence |
|---|---|---|
| 1,000 concurrent players / 10-ticket limit under load | BLOCKED | `k6` is unavailable, no load-test harness was present, and no isolated load environment was supplied. Capacity and the 10-ticket enforcement behavior under concurrency were not measured. |
| Concurrent purchases, deposits, approvals, settlements, and transfers | BLOCKED | The targeted same-key concurrent agent sale test now passes as described in G3. Other concurrent money-flow scenarios remain untested because no safe isolated load environment/harness was available. |
| Throughput, latency, failure rate, contention, and financial invariants at capacity | BLOCKED | No load measurements were made; no capacity claims are made. |

## Test commands

Commands run in this phase included:

```text
mvn -Dtest=AgentTicketSaleHttpIntegrationTest#concurrentDuplicateSaleReturnsTheSameResultWithoutADuplicateDebit test
mvn -Dtest=AuthenticatedStompIntegrationTest test
mvn clean verify
mvn -Dtest=FlywayUpgradeRecoveryIntegrationTest test
mvn -Dtest=AgentTicketSaleHttpIntegrationTest test
mvn -Dtest=AuthenticatedStompIntegrationTest test
npm run test:coverage -- --maxWorkers=1
npm run lint
npm run build
npx playwright test --list
flutter test
flutter analyze
flutter build web
flutter build web --dart-define=API_BASE_URL=https://api.example.invalid
```

For command outcomes and rerun instructions per gate, see [defect-remediation-matrix.md](./defect-remediation-matrix.md), [security-validation.md](./security-validation.md), [load-test-results.md](./load-test-results.md), and [release-checklist.md](./release-checklist.md).
