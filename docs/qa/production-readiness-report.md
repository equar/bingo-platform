# Bingo Platform Production-Readiness Report

**Execution date:** 2026-10-21
**Baseline:** October 20, 2026 End-to-End QA Report
**Branch:** `muse-code-review`

## Release decision

**NO-GO.** Phase 6 produced positive verification for MySQL/Flyway migration paths, the normal agent sale/payout/commission flow, and existing admin/mobile unit checks. Release is blocked by a reproducible concurrent idempotency failure and a real authenticated STOMP connection failure. The required 1,000-player load test, onboarding-mail flow, complete Playwright flow, and device E2E flows were not run in an isolated environment.

The configured capacity of 1,000 players and maximum of 10 tickets per player are configuration values, not measured capacity evidence.

## Environment

- Windows 11; Java 25.0.2; Maven 3.9.6; Docker 28.3.3.
- Backend integration tests used Testcontainers MySQL 8.0.36 with Flyway enabled.
- Node 22.12.0; Playwright 1.64.1; Chromium installed.
- Flutter 3.47.7 / Dart 3.13.5; Android emulator `emulator-5554` (Android API 37) visible.
- No iOS simulator/Xcode on this Windows host; `k6` and `adb` are not on `PATH`.
- Ports 3306, 8080, and 5173 were already occupied by processes not started for this run. They were not modified or used for financial E2E tests. Mailpit ports 1025 and 8025 were not listening.
- No E2E credentials were available in the process environment. Credentials and tokens were not printed or stored.

## Gap results

### G1 — MySQL 8 and Flyway

| Scenario | Status | Evidence |
|---|---|---|
| Clean/upgrade migration execution on MySQL 8 | PASS | `mvn -Dtest=FlywayUpgradeRecoveryIntegrationTest test` passed (1/1) on MySQL 8.0.36, upgrading V6 through V12. `FlywayV7MigrationIntegrationTest` passed in the full suite and verified the V7 constraints. |
| Flyway history checksum verification/repair | PASS | The upgrade test checked stored checksums, detected deliberately altered history, ran Flyway `repair()`, and validated the repaired history. |
| Full backend suite against MySQL | FAIL | Final `mvn clean verify`: 73 tests, 1 failure, 1 error, 0 skipped. The two release-blocking tests are detailed under G2/G3. |
| Database backup restore / rollback recovery rehearsal | BLOCKED | The test verifies forward migration and Flyway history repair; it does not restore a production-like backup. The migration chain is forward-only and no disposable backup/restore rehearsal environment was provided. |

### G2 — Live WebSocket draw verification

| Scenario | Status | Evidence |
|---|---|---|
| Authenticated STOMP connection to the live backend | FAIL | `AuthenticatedStompIntegrationTest` fails before receiving `CONNECTED`: `ConnectionLostException: Connection closed` in `connect()` at test line 190. |
| Ordered draw delivery, reconnect, and missed-sequence replay | BLOCKED | Assertions were not reached because the authenticated handshake closed. |
| Invalid-token denial on the live broker | BLOCKED | The live authenticated connection failed before subscription checks could run. Existing interceptor unit tests passed in the backend suite but are not a substitute for broker-level proof. |

### G3 — Agent ticket sales and payouts

| Scenario | Status | Evidence |
|---|---|---|
| Anonymous ticket sale authorization | PASS | The HTTP integration test received 401 for an anonymous sale. |
| Normal agent sale, sequential idempotency replay, and wallet float movement | PASS | The test verified a 201 sale and replay returning the same ticket, one ticket persisted, and the expected float balance. |
| Payout verification and replay | PASS | The test verified 200 responses, the same payout record on replay, and the expected available/clearing balances. |
| Insufficient-float rejection | PASS | Sale returned a 4xx without creating another ticket or commission accrual, and the available balance remained unchanged. |
| Concurrent duplicate sale using the same actor/key | FAIL | Concurrent requests return 500 for one request. MySQL reports a duplicate key on `idempotency_records.uk_idempotency_records_actor_key` instead of replaying the prior result. This is an observed money-flow/idempotency defect; production code was not changed during this verification task. |

### G4 — Commission accrual

| Scenario | Status | Evidence |
|---|---|---|
| Enabled rule, persistence, rounding, and sequential retry | PASS | HTTP integration flow persisted one accrual for a 4.25 sale at 2.5%, rounded to 0.11; replay did not duplicate the sale/accrual. |
| Disabled commission rule | PASS | A subsequent sale after disabling the rule created no additional accrual. |
| Duplicate concurrent sale/event | FAIL | The concurrent idempotency failure in G3 means a concurrent retry is not proven safe and can return HTTP 500. |

### G5 — Admin Playwright

| Scenario | Status | Evidence |
|---|---|---|
| Existing admin unit/component/API tests | PASS | `npm run test:coverage -- --maxWorkers=1`: 68 tests passed in 25 files; statement coverage 62.86%. |
| Admin lint and production build | PASS | `npm run lint` and `npm run build` passed. Production main bundle measured 414.41 kB raw / 137.80 kB gzip. |
| Enumeration of existing browser scenarios | PASS | `npx playwright test --list` with non-secret placeholder environment values listed all five tests across two files. |
| Execution of five browser E2E scenarios and failure artifacts | BLOCKED | Actual credentials were unavailable and existing local backend/admin ports belonged to processes outside this run. No financial E2E flow was sent to those services. Playwright now retains traces, screenshots, and video on failure; no artifacts exist because the E2E tests were not run. |

### G6 — Admin-created user onboarding

| Scenario | Status | Evidence |
|---|---|---|
| SMTP delivery through Mailpit, first login, expiry/reuse behavior | BLOCKED | Mailpit was not running, no isolated backend/mail configuration was supplied, and no Mailpit-backed onboarding E2E was available to execute. Existing unit tests do not establish delivery or first-login behavior. The exact test rerun prerequisite is documented in [defect-remediation-matrix.md](./defect-remediation-matrix.md). |

### G7 — Mobile device verification

| Scenario | Status | Evidence |
|---|---|---|
| Player/agent Flutter unit tests and analysis | PASS | Player: `flutter test` (17 passed), `flutter analyze` passed. Agent: `flutter test` (9 passed), `flutter analyze` passed. |
| Agent web build | PASS | `flutter build web` passed. |
| Player Android end-to-end flows | BLOCKED | An Android emulator was available, but the isolated backend and disposable E2E credentials/manifest were not. No live financial flow was run. |
| Agent Android end-to-end operations | BLOCKED | No isolated backend, disposable credentials, or agent device integration scenario was available for the real operations requested. |
| iOS simulator verification | BLOCKED | Xcode and an iOS simulator are unavailable on this Windows host. |
| Player web build | FAIL | `flutter build web --dart-define=API_BASE_URL=https://api.example.invalid` failed because the player app is not configured for web. This is not evidence of failure for its supported Android/iOS targets. |

### G8 — Load and concurrency

| Scenario | Status | Evidence |
|---|---|---|
| 1,000 concurrent players / 10-ticket limit under load | BLOCKED | `k6` is unavailable, no load-test harness was present, and no isolated load environment was supplied. Capacity and the 10-ticket enforcement behavior under concurrency were not measured. |
| Concurrent purchases, deposits, approvals, settlements, and transfers | BLOCKED | No safe isolated environment/load harness was available. The targeted same-key concurrent agent sale test did run and failed as described in G3. |
| Throughput, latency, failure rate, contention, and financial invariants at capacity | BLOCKED | No load measurements were made; no capacity claims are made. |

## Test commands

Commands run in this phase included:

```text
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
