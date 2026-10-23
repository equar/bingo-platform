# Bingo Platform Production-Readiness Report

**Execution date:** 2026-10-22
**Baseline:** October 20, 2026 End-to-End QA Report
**Branch:** `muse-code-review`

> **Current execution update:** See [qa-execution-plan-and-results.md](./qa-execution-plan-and-results.md), [security-validation.md](./security-validation.md), and [release-blockers.md](./release-blockers.md). A clean `mvn -q clean verify` from an isolated copy of current backend source passed 88 tests and produced the executable JAR. Activation, Mailpit, and OpenAPI checks passed. The prior isolated database restore stopped at V13, before the V14 activation migration. Release remains **NO-GO**.

## Release decision

**NO-GO.** The concurrent agent-sale idempotency and authenticated STOMP failures have regression coverage. The production datasource and account-onboarding security findings were remediated and tested; Fastlane is version-pinned, but its transitive dependency lock and CI validation remain open. One targeted admin browser journey and two Android player journeys pass against isolated synthetic services, but the remaining browser/device journeys are incomplete. Release is also blocked by the required 1,000-player load test, V14 backup/restore and production recovery validation, legal pages marked as drafts, production CloudFront verification, and continuous operational monitoring.

The configured capacity of 1,000 players and maximum of 10 tickets per player are configuration values, not measured capacity evidence.

## Environment

- Windows 11; Java 25.0.2; Maven 3.9.6; Docker CLI connected to server 29.9.2.
- Backend database tests used isolated, loopback-bound MySQL 8.0.36/Mailpit CLI containers with Flyway enabled because Testcontainers could not discover Docker from the Java process.
- Node 22.12.0; Playwright 1.64.1; Chromium installed.
- Flutter 3.47.7 / Dart 3.13.5; Android emulator `emulator-5554` (Android API 37) visible.
- No iOS simulator/Xcode on this Windows host; `k6` and `adb` are not on `PATH`.
- Ports 3306, 8080, and 5173 were already occupied by processes not started for this run. They were not modified or used for financial E2E tests. Ports 1025, 8025, and 13306 were initially free and reserved for disposable loopback-only QA containers.
- The original execution had no E2E credentials in the process environment. The follow-up created synthetic credentials in a temporary directory outside the repository; credentials and tokens were not printed or retained in reports.
- During the 2026-10-22 follow-up, the Java Testcontainers named-pipe strategy could not reach the Docker engine, although the Docker CLI could. Tests used disposable loopback-only Docker CLI containers: MySQL 8.0.36 on port 13306 and Mailpit on ports 1025/8025. Existing `kirshi-*` containers and the service on port 3306 were not touched.
- A clean `mvn -q clean verify` from an isolated copy of current `src`, `pom.xml`, and `openapi.json` passed 88 tests (0 failures/errors/skips) and packaged the executable JAR. The workspace's own JAR remains locked by an unrelated process, which was left untouched.
- The isolated browser/mobile follow-up used MySQL on loopback port 23306, backend `127.0.0.1:18080`, and admin UI `127.0.0.1:15173`; the existing listeners on ports 3306, 8080, and 5173 were not used. Temporary Android emulator port forwarding was removed after each run.

## Gap results

### G1 — MySQL 8 and Flyway

| Scenario | Status | Evidence |
|---|---|---|
| Clean/upgrade migration execution on MySQL 8 | PASS | Current MySQL 8.0.36 upgrade regression migrated V6 through V14. `FlywayV7MigrationIntegrationTest` also verifies the V7 constraints. |
| Flyway history checksum verification/repair | PASS | The upgrade test checked stored checksums, detected deliberately altered history, ran Flyway `repair()`, and validated the repaired history. |
| Full backend verification against MySQL | PASS | Clean isolated-source `mvn -q clean verify`: 88 tests, 0 failures, 0 errors, 0 skipped, and executable JAR packaging completed. |
| Database backup restore / rollback recovery rehearsal | PARTIAL | A prior disposable dump/drop/restore recovered V13 and 30 tables. V14 was introduced afterward and has not been included in a restore rehearsal; production recovery procedures remain unverified. |

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
| Existing admin unit/component/API tests | PASS | `npm test`: 73 tests passed in 26 files, including activation-page tests. |
| Admin lint and production build | PASS | `npm run lint` and `npm run build` passed. Production main bundle measured 414.52 kB raw / 137.81 kB gzip. |
| Enumeration of existing browser scenarios | PASS | `npx playwright test --list` with non-secret placeholder environment values listed all five tests across two files. |
| Targeted sessions-management browser E2E | PASS (partial suite) | `sessions-admin.spec.ts`: 1 passed, 0 failed, exit code 0 against isolated backend/admin services. The test now handles paginated room results. The other four discovered browser tests remain unrun; Playwright artifacts are disabled by default. |

### G6 — Admin-created user onboarding

| Scenario | Status | Evidence |
|---|---|---|
| SMTP delivery through Mailpit and activation | PASS | Isolated MySQL/Mailpit integration verified email delivery without a password, 24-hour single-use activation, rejection of token replay, and successful login after activation. |

### G7 — Mobile device verification

| Scenario | Status | Evidence |
|---|---|---|
| Player/agent Flutter unit tests and analysis | PASS | Player: `flutter test` (20 passed), `flutter analyze` passed, including the integration-test changes. Agent: `flutter test` (10 passed), `flutter analyze` passed. |
| Agent web build | PASS | `flutter build web` passed. |
| Player Android end-to-end flows | PASS (partial journeys) | On `emulator-5554`, setup/deposit/registration and support journeys passed against isolated synthetic services. Each phase run exited 0 and reported 5 passed/0 failed/0 skipped; only the selected journey executed, while four phase cases returned early. No live payment provider was used. Purchase, settlement, claim, and post-settlement verification remain unrun. |
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
