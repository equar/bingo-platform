# Module 00 — Local QA Environment

Date: 2026-10-22

## Results

| Check | Status | Evidence / notes |
| --- | --- | --- |
| Existing MySQL service and version | PASS | `MySQL80` is running; read-only MySQL query returned 8.0.36 on `127.0.0.1:3306`. |
| Dedicated QA database and restricted users | PASS | Created `bingo_qa` after explicit approval. Runtime and Flyway users are scoped to `bingo_qa.*` with separate credentials held in ignored `.env.qa`. Read-only query confirmed 0 users, 0 agents, and latest successful migration V14. |
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
| Persistent QA accounts | BLOCKED | The existing dev seeder is unsafe for persistent QA because it upserts/overwrites accounts. Approval for a guarded one-time bootstrap limited to `bingo_qa` was received, but no accounts were created because reserved, non-customer Ethiopian test phone numbers have not been provided. The repo defines no reserved QA number range. |
| Read-only database-backed integration test | PASS | `OpenApiContractIntegrationTest`: 1 passed against MySQL `bingo_qa`. Flyway validated 14 migrations; schema was already at V14. This test only GETs `/v3/api-docs`; user and agent counts were 0 before and after. |
| Destructive database integration suites | BLOCKED | `MySqlIntegrationTest` uses Testcontainers unless `BINGO_TEST_MYSQL_URL` is set. Multiple suites issue `deleteAll` / DELETE statements. No separate disposable schema is configured, so those suites were not run. Docker and Testcontainers were not used. |
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
- Queried QA user/agent row counts (0/0), then ran `OpenApiContractIntegrationTest` with `BINGO_TEST_MYSQL_URL` pointed at `bingo_qa`; 1 passed, Flyway reported up-to-date, and row counts remained 0/0.
- Inspected backend test inheritance and cleanup. Did not run DB-backed tests because they use Testcontainers by default and several suites delete rows; did not run business E2E or start Module 1.
- Did not seed accounts, drop/truncate data, or change the existing `bingo` schema.

## Remaining prerequisites

1. Provide a reserved synthetic Ethiopian test phone range or already allocated QA-only numbers; the application rejects North American reserved 555 numbers. The guarded QA-only bootstrap is approved but must not guess at customer identifiers.
2. Provision admin, approved/pending agent and player accounts and keep credentials in ignored local secrets before business E2E runs.
3. Provide a disposable MySQL test schema and credentials for the suites with destructive cleanup; those suites must not target `bingo_qa`.
4. Configure a local SMTP catcher if account activation/email workflows are required.

No Module 1 functionality testing was started.
