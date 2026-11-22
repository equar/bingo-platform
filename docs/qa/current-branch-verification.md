# Current branch QA verification

## Scope and execution policy

This plan covers the Admin React application, Flutter Player application, and supporting Spring Boot backend. It excludes agent application/functionality, live services, deployments, and Git operations. Financial integration testing is permitted only with the guarded isolated MySQL 8 schema `bingo_qa_maven_test`, using the credentials and identity checks required by `MySqlIntegrationTest`; no normal `bingo` datasource or persistent QA schema may be used.

The present environment did not provide `BINGO_MAVEN_MYSQL_URL`, `BINGO_MAVEN_MYSQL_USERNAME`, or `BINGO_MAVEN_MYSQL_PASSWORD`. The MySQL integration suite was not started. Flutter and Dart executables were present, but `flutter --version` and `dart --version` each remained silent and hung for over 40 seconds; they were interrupted. No Flutter tests or builds are claimed as run.

## QA matrix

| Area / checks | Result | Evidence | Remaining release risk |
|---|---|---|---|
| Backend Java compilation | PASS | `mvn -f bingo-backend/pom.xml -DskipTests compile` — BUILD SUCCESS (Java 25) | Does not exercise persistence or runtime wiring. |
| Backend unit tests | PASS | `mvn -f bingo-backend/pom.xml test "-Dtest=*,!*IntegrationTest"` — 202 tests, 0 failures, 0 errors, 0 skipped | Mock/unit evidence only; no MySQL behavior verified. |
| Backend MySQL integration tests | BLOCKED | Guard requires the three `BINGO_MAVEN_MYSQL_*` environment variables; all were unset. No attempt was made to connect to MySQL or weaken the guard. | Deposits, withdrawals/refunds, transfers, tickets, claims/settlement, idempotency, uniqueness, lock/race behavior and migration compatibility still need the approved isolated MySQL 8 run. |
| Admin lint | PASS | `npm run lint` completed without diagnostics. | — |
| Admin unit tests | PASS | `npm test -- --run` — 41 files, 235 tests passed. | Component tests do not replace backend authorization checks. |
| Admin production build | PASS | `npm run build` — TypeScript and Vite succeeded; 1,116 modules transformed. | — |
| Playwright discovery | PASS | `npm run e2e:list` — 70 tests in 25 files discovered without requiring live credentials. | Discovery does not verify browser behavior. |
| Admin mock browser tests | PASS (selected) | Chromium: dashboard mock empty/error/large amount cases (3), dashboard English/Amharic case (1), responsive localization (1): 5 passed. The localization spec was corrected to target the language selector uniquely and to assert the actual uppercase `PENDING` option. | Full suite not run; live credential/backend suites remain unverified. |
| Flutter `wallet_api.dart`, `game_api.dart`, `auth_api.dart` direct client tests | BLOCKED | Added `bingo-player/test/api_clients_test.dart` covering request paths/payloads, idempotency headers, response parsing, draw cursor, logout and safe transport error propagation. Flutter CLI could not initialize, so these tests are not executed evidence. | Must run `flutter test test/api_clients_test.dart` once the SDK responds. |
| Flutter analyze and unit/widget suite | BLOCKED | Flutter CLI hung before printing its version; no analyze/test result. | Player UI and localization regressions are unverified. |
| Flutter integration tests | BLOCKED | Requires responsive Flutter tooling and an available app/device plus approved backend/test credentials; none verified in this run. | Live deposit/withdrawal/transfer/ticket/claim recovery remains unverified from Player. |
| Android, Chrome, Windows builds | BLOCKED | Flutter CLI did not initialize. `adb`, Chrome and MSBuild availability was not established independently. | Platform packaging and device/browser startup remain unverified. |

## Focused domain evidence

| Domain | Mock/unit evidence | Real-backend evidence |
|---|---|---|
| Deposits and withdrawals/refunds | Admin unit suite includes UI coverage; Flutter direct-client tests have been added but not run. | BLOCKED by absent approved MySQL configuration. |
| Wallet transfer and ledger consistency | Backend unit suite passed; Flutter direct-client transfer request/error test added but not run. | BLOCKED. |
| Ticket purchase, live draws and claims | Backend game unit tests ran as part of the 202 passing tests; Flutter API client purchase/draw tests added but not run. | BLOCKED. |
| Prize entitlement settlement and authorization | Backend unit suite included game/security authorization tests; Admin component suite passed. | Settlement ledger, duplicate prevention, and payout safeguards need guarded integration execution. |
| Localization | Admin English/Amharic browser case and responsive localization browser case passed. | Flutter localization tests not run. |
| Failure recovery | Admin mocked dashboard error/retry case passed; wallet client error propagation test added but not run. | Financial retry/idempotency behavior remains unverified against MySQL. |

## Commands to complete blocked checks

After provisioning an approved local MySQL 8 disposable schema and restricted `bingo_qa_maven_runner` account exactly as required by the repository guard, set the required `BINGO_MAVEN_MYSQL_*` environment values and run the backend integration-test command documented by the repository. Do not override or remove `MavenTestDatabasePolicy` checks. Run Flutter after resolving the SDK initialization issue:

```powershell
cd bingo-player
flutter analyze
flutter test
flutter test test/api_clients_test.dart
flutter test integration_test
flutter build appbundle
flutter build web
flutter build windows
```

Platform builds/tests should be marked unavailable when their SDKs, devices, or runners are absent, not treated as passes. Re-run live Admin Playwright suites only against an approved disposable browser-test environment with its explicitly configured credentials.
