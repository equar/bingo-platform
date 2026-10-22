# Module 00 — Local QA Environment

Date: 2026-10-22

## Results

| Check | Status | Evidence / notes |
| --- | --- | --- |
| Existing MySQL service and version | PASS | `MySQL80` is running; read-only MySQL query returned 8.0.36 on `127.0.0.1:3306`. |
| Dedicated QA database and restricted users | PASS | Created `bingo_qa` after explicit approval. Runtime and Flyway users are scoped to `bingo_qa.*` with separate credentials held in ignored `.env.qa`. Current read-only query confirms five users, two agent profiles, and latest successful migration V14. |
| QA Spring profile | PASS | `application-qa.yml` uses `bingo_qa`, separate migration/runtime credentials, Flyway enabled and protected, Hibernate `validate`, and no create-database URL option. |
| QA backend startup / Flyway / health | PASS | Current source packaged with tests skipped; QA backend started on port 8081. Flyway validated and applied 14 migrations to v14 (30 tables); health is `UP`. QA admin CORS preflight returned HTTP 200. |
| Admin browser availability | PASS | Existing dev `/login` on port 5173 and isolated QA `/login` on port 5174 returned HTTP 200; QA Vite mode points to port 8081. Playwright 1.64.1 and Chromium binaries are installed. |
| Public website availability | PASS | Local Astro server started at `http://localhost:4321/`; `/` returned HTTP 200. |
| Playwright browser smoke | PASS | Chromium opened the isolated QA admin and public website and captured [admin login](../evidence/module-00-admin-login.png) and [public website](../evidence/module-00-website-home.png) screenshots. E2E business scenarios were not executed. |
| Flutter / Dart toolchain | PASS | `C:\src\flutter` stable 3.47.7 / Dart 3.13.5 meets both apps' minimums (Flutter 3.41.0 / Dart 3.11.0). `flutter doctor -v` reports Android toolchain healthy; only the unused Windows desktop toolchain lacks Visual Studio. |
| Android SDK / adb / emulator | PASS | Process-local `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and PATH fixed discovery. `adb devices -l` sees `emulator-5554`; `flutter devices` sees Android 17 / API 37. Emulator `GET /actuator/health` to `10.0.2.2:8081` returned HTTP 200 / `UP`. |
| Flutter pub / analyze / unit-widget tests | PASS | `flutter pub get` and `flutter analyze` passed for both apps. `flutter test`: player 20 passed; agent 10 passed. |
| Player / agent APK build and launch | PASS | Both debug APKs built x64 with `--dart-define=API_BASE_URL=http://10.0.2.2:8081`, installed, and reached the foreground without Flutter exception logs. Captured [player login](../evidence/module-00-player-login.png) and [agent login](../evidence/module-00-agent-login.png). No auth request/business E2E was sent. |
| Agent Android project setup | PASS | Added Android platform scaffolding because no Android directory existed. Fixed `API_BASE_URL` lookup to use a Dart compile-time constant. Added internet permission to both app manifests; cleartext HTTP is enabled only in debug manifests. |
| Persistent QA accounts | PASS | Five accounts are present: admin user 1, approved agent user 2 / agent 1, pending agent user 3 / agent 2, and players users 4 and 5. The guarded one-time bootstrap checked catalog `bingo_qa`, required zero existing users/agents, and used app BCrypt encoders plus agent approval and player registration services. Admin, approved-agent, and player login API requests passed. The user authorized choosing numbers; the five distinct values are recorded in the registry. Player suspension is not supported and was not fabricated. |
| Earlier read-only contract check | PASS | Before `bingo_qa_test` was created, `OpenApiContractIntegrationTest` ran once against `bingo_qa`; it only GETs `/v3/api-docs` and did not change users or agents. The final full backend suite now uses only `bingo_qa_test`. |
| Isolated database for backend tests | PASS | Created `bingo_qa_test` on local MySQL 8.0.36. `bingo_qa_test_runner@127.0.0.1` has only schema-scoped privileges on `bingo_qa_test.*`; direct access checks to `bingo` and `bingo_qa` were denied. Credentials are in ignored `.env.test`. |
| Integration-test database guard and Flyway | PASS | `MySqlIntegrationTest` rejects non-local URLs, any database except `bingo_qa_test`, wrong catalogs, missing environment configuration, or test credentials that can access `bingo`/`bingo_qa`. Testcontainers fallback/dependency was removed. First alphabetically, `A00FlywayUpgradeRecoveryIntegrationTest` cleaned only `bingo_qa_test`, migrated V1–V14 from V6, and repaired checksum drift. |
| Complete backend test suite | PASS | `mvn ... test`: 88 tests passed, 0 failed, 0 errors, 0 skipped. Includes migrations, repositories, authentication, wallet transaction/ledger snapshots, agent, bingo game, idempotency, support, and API tests. This codebase has no standalone ledger module; financial entries are recorded as wallet transactions. The initial run had one Mailpit connection failure (no external SMTP service on port 1025); the activation integration test now uses a capturing in-process mail sender and passed on targeted and full reruns. |
| Persistent QA database isolation | PASS | After the suite, `bingo_qa` still has five users, two agent profiles, and successful Flyway version 14. Admin, approved-agent, and player credentials still authenticate. The test account's MySQL grants and application guard prevent test cleanup from reaching either persistent schema. |
| Backend unit test | PASS | `AuthenticationClientAddressResolverTest`: 3 passed. |

## Commands executed

- Read-only service discovery with `Get-Service` and port discovery with `netstat`.
- Read-only MySQL queries for `VERSION()` and presence of `bingo_qa`.
- Read backend Spring datasource/profile configuration and migration SQL.
- Started local website using `npm run dev -- --host 0.0.0.0 --port 4321`.
- Started the isolated QA admin using `npm run dev -- --mode qa --host 0.0.0.0 --port 5174` and an ignored `.env.qa` copied from its tracked example.
- Checked the admin login and website root with `Invoke-WebRequest` (HTTP 200 each).
- Checked Playwright with `npx playwright --version` and `npx playwright install --list`.
- Captured the isolated QA admin and website screenshots with `npx playwright screenshot`; type-checked the new Playwright helper with `npx tsc --noEmit ...` (exit 0).
- Ran `flutter --version`, `dart --version`, `flutter doctor -v`, `flutter pub get`, `flutter analyze`, and `flutter test` in both apps. Existing tests passed (20 player, 10 agent).
- Set Android paths in-process; ran `adb version`, `adb devices -l`, `flutter emulators`, and `flutter devices`.
- Generated the missing agent Android project scaffold. Built both x64 debug APKs with `--dart-define=API_BASE_URL=http://10.0.2.2:8081`, installed and launched them, inspected logs, and captured their login screenshots.
- Built the backend with tests skipped using a workspace-local Maven cache and a file mirror of the pre-existing Maven cache.
- Started the QA backend with `java -jar target/bingo-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=qa`.
- Verified health (`UP`), QA CORS preflight (HTTP 200), `bingo_qa` schema access, 30 tables, and latest successful Flyway version 14.
- Queried the persistent QA account and agent IDs/statuses, then verified the QA DB user has only schema-scoped runtime privileges.
- Added a QA-profile one-time account bootstrap, disabled by default, with schema/catalog, explicit confirmation, empty-account-schema, distinct-phone, and credential validation guards. Built with `mvn -s ../.tmp/maven-settings.xml '-Dmaven.repo.local=../.tmp/m2' -DskipTests package` (compile/package passed; tests skipped), ran the bootstrap once against `bingo_qa`, and restarted the backend without the enable/confirmation environment variables.
- Verified five users and two agent profiles with a read-only MySQL query; statuses and IDs are in [test-data-registry.md](../test-data-registry.md). Authenticated through admin, approved-agent, and player login endpoints without displaying or logging returned tokens.
- Confirmed local MySQL identity and existing schemas with the local administrator account; verified `bingo_qa_test` did not exist and that the test runner account name was not already present. Created the isolated schema and scoped test account, then verified its grants and denied access to `bingo` and `bingo_qa`.
- Ran the isolated Flyway recovery test, then the complete Maven backend suite against `bingo_qa_test`: 88 passed, 0 failed, 0 errors, 0 skipped. The initial full run exposed a missing Mailpit service; replaced the external service dependency in the admin activation integration test with an in-process message capture, reran that test successfully, and reran the full suite successfully.
- After testing, confirmed `bingo_qa` still had the same five users, two agents, and Flyway v14; authenticated admin, approved agent, and player credentials again. Confirmed the QA backend health was `UP`, admin login page returned HTTP 200, and website returned HTTP 200.
- Inspected backend test inheritance and cleanup. Did not run DB-backed tests because they use Testcontainers by default and several suites delete rows; did not run business E2E or start Module 1.
- Did not seed accounts, drop/truncate data, or change the existing `bingo` schema.

## Remaining environment notes

- Manual SMTP delivery to an external mail service was not exercised. The admin activation integration test captures generated mail locally and verifies the complete activation flow.
- Player-account suspension is not supported by an application service, so a suspended-player fixture was not created.

No Module 1 functionality testing was started.
