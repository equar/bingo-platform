# Phase 6 Defect and Verification Matrix

Statuses describe evidence obtained on 2026-10-21. A PASS is limited to the scenario stated; it does not imply production readiness.

| Gap | Scenario | Status | Evidence / remaining work | Rerun command |
|---|---|---|---|---|
| G1 | MySQL 8 V6-to-V12 Flyway upgrade | PASS | Testcontainers MySQL 8.0.36; upgrade regression test passed. | `cd bingo-backend; mvn -Dtest=FlywayUpgradeRecoveryIntegrationTest test` |
| G1 | V7 constraints and migration checksum validation/repair | PASS | V7 integration test passed; upgrade test verified checksum drift detection and repair. | `cd bingo-backend; mvn -Dtest=FlywayV7MigrationIntegrationTest,FlywayUpgradeRecoveryIntegrationTest test` |
| G1 | Backup restore / full rollback drill | BLOCKED | No disposable backup/restore environment. Flyway migrations are forward-only; checksum repair is not a database rollback. | Provision isolated MySQL 8 plus a pre-upgrade backup, then run the documented backup restore procedure; retain the restore and post-restore validation logs. |
| G1 | Complete backend suite with all new tests | FAIL | `mvn clean verify`: 73 run, 1 failure, 1 error, 0 skipped. Failures are the G2 and G3 findings below. | `cd bingo-backend; mvn clean verify` |
| G2 | Authenticated real STOMP CONNECT | FAIL | Connection closes before STOMP `CONNECTED`; stack is in `AuthenticatedStompIntegrationTest.connect()` at line 190. No root cause is asserted from this observation. | `cd bingo-backend; mvn -Dtest=AuthenticatedStompIntegrationTest test` |
| G2 | Live draw order, reconnect, and replay | BLOCKED | Test does not reach draw/subscription assertions until the authenticated handshake issue is diagnosed. | After correcting the handshake in an isolated MySQL-backed test environment: `cd bingo-backend; mvn -Dtest=AuthenticatedStompIntegrationTest test` |
| G2 | Invalid JWT cannot subscribe to protected channel | BLOCKED | Real broker test did not reach subscription. Unit interceptor tests passed but are insufficient for this gate. | After handshake is operational: `cd bingo-backend; mvn -Dtest=AuthenticatedStompIntegrationTest test` |
| G3 | Anonymous sale rejected | PASS | Real HTTP endpoint returned 401. | `cd bingo-backend; mvn -Dtest=AgentTicketSaleHttpIntegrationTest#ticketSaleAndPayoutEndpointsAreIdempotentAndAccrueOnlyEligibleCommission test` |
| G3 | Agent sale, sequential same-key replay, and float movement | PASS | Same ticket returned on replay; one sale and expected balance observed. | `cd bingo-backend; mvn -Dtest=AgentTicketSaleHttpIntegrationTest#ticketSaleAndPayoutEndpointsAreIdempotentAndAccrueOnlyEligibleCommission test` |
| G3 | Payout verify/replay and insufficient-float rejection | PASS | Same payout record on replay; insufficient-funds sale did not create a ticket/accrual or debit float. | `cd bingo-backend; mvn -Dtest=AgentTicketSaleHttpIntegrationTest#ticketSaleAndPayoutEndpointsAreIdempotentAndAccrueOnlyEligibleCommission test` |
| G3 | Concurrent duplicate sale idempotency | FAIL | One concurrent same-actor/key request returns 500 due to MySQL duplicate-key violation in `idempotency_records`. | `cd bingo-backend; mvn -Dtest=AgentTicketSaleHttpIntegrationTest#concurrentDuplicateSaleReturnsTheSameResultWithoutADuplicateDebit test` |
| G4 | Enabled commission calculation, rounding, persistence, retry | PASS | 2.5% of 4.25 persisted once as 0.11. | `cd bingo-backend; mvn -Dtest=AgentTicketSaleHttpIntegrationTest#ticketSaleAndPayoutEndpointsAreIdempotentAndAccrueOnlyEligibleCommission test` |
| G4 | Disabled commission rule | PASS | Subsequent sale created no new accrual. | Same targeted command as above. |
| G4 | Concurrent duplicate commission event | FAIL | Bound to the G3 concurrency failure; concurrent same-key request returned 500. | `cd bingo-backend; mvn -Dtest=AgentTicketSaleHttpIntegrationTest#concurrentDuplicateSaleReturnsTheSameResultWithoutADuplicateDebit test` |
| G5 | Admin unit/component/API suite | PASS | 68 tests passed; coverage 62.86% statements. | `cd bingo-admin; npm run test:coverage -- --maxWorkers=1` |
| G5 | Admin lint and build | PASS | Lint and production build passed; main bundle 414.41 kB raw / 137.80 kB gzip. | `cd bingo-admin; npm run lint; npm run build` |
| G5 | Playwright test discovery | PASS | Five existing tests listed with non-secret placeholder environment variables. | Set non-secret placeholders for `BINGO_E2E_ADMIN_EMAIL`, `BINGO_E2E_ADMIN_PASSWORD`, and `BINGO_E2E_PRIMARY_PHONE`; then `cd bingo-admin; npx playwright test --list`. |
| G5 | Five real browser E2E flows | BLOCKED | No disposable credentials or isolated backend/admin environment. Existing occupied ports were not used. | Supply isolated `BINGO_E2E_BACKEND_URL`, `BINGO_E2E_ADMIN_URL`, `BINGO_E2E_ADMIN_EMAIL`, `BINGO_E2E_ADMIN_PASSWORD`, `BINGO_E2E_PRIMARY_PHONE`, then run `cd bingo-admin; npx playwright test`. |
| G6 | SMTP onboarding, email, first login, expiry and credential reuse | BLOCKED | Mailpit and an isolated backend were unavailable; no end-to-end evidence exists. | Start an isolated Mailpit: `docker run --rm --name bingo-qa-mailpit -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 axllent/mailpit:v1.27`; configure the isolated backend to use SMTP port 1025 and add the missing Mailpit-backed onboarding integration test; then run `cd bingo-backend; mvn -Dtest=AdminUserOnboardingMailpitIntegrationTest test`. The test must exercise creation, received email, first login, expiry, invalid credentials, and reuse before this gate can pass. |
| G7 | Player Flutter unit tests/analyzer | PASS | 17 tests passed; analyzer passed. | `cd bingo-app; flutter test; flutter analyze` |
| G7 | Agent Flutter unit tests/analyzer/web build | PASS | 9 tests passed; analyzer and web build passed. | `cd bingo-agent; flutter test; flutter analyze; flutter build web` |
| G7 | Android player E2E | BLOCKED | Emulator exists; isolated backend and disposable account/manifest missing. | Create ignored `bingo-app/e2e-secrets.json` from the example using secret-store values; then `cd bingo-app; flutter test integration_test/local_e2e_test.dart -d emulator-5554 --dart-define-from-file=e2e-secrets.json`. |
| G7 | iOS E2E | BLOCKED | No Xcode/iOS simulator on this Windows host. | On a supported macOS runner: `cd bingo-app && flutter test integration_test/local_e2e_test.dart -d <ios-simulator-id> --dart-define-from-file=e2e-secrets.json`. |
| G7 | Player web build | FAIL | Build command failed because the player app is not configured for web. | `cd bingo-app; flutter build web --dart-define=API_BASE_URL=https://api.example.invalid` |
| G8 | 1,000-player / 10-ticket-capacity load | BLOCKED | `k6` and a load script/environment are absent; no capacity or latency measurements were made. | After provisioning `k6`, an isolated load target, and a reviewed `bingo-backend/tests/load/bingo-platform.js` script: `k6 run --vus 1000 --duration 10m bingo-backend/tests/load/bingo-platform.js`. |
| G8 | Concurrent money-flow operations and invariants | BLOCKED | No safe load environment/harness. The one directly tested duplicate-sale concurrency case failed (G3). | Use the isolated load environment and reviewed script: `k6 run --vus 1000 --duration 10m bingo-backend/tests/load/bingo-platform.js`; rerun G3's focused concurrency test independently. |

## Findings requiring remediation before release

1. Fix and retest the agent ticket-sale idempotency race; concurrent duplicates must replay the same result without a 500, duplicate debit, or extra ticket.
2. Diagnose the live authenticated STOMP disconnect and demonstrate authorization, ordered delivery, reconnection, and replay.
3. Complete a database backup/restore rehearsal.
4. Run the blocked onboarding, browser, device, and load scenarios in isolated test infrastructure.

No production application source was changed as part of this verification pass. Only test isolation, regression coverage, Playwright failure-artifact retention, and these reports were changed.
