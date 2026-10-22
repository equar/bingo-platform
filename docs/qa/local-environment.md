# Local QA Environment

This setup uses the existing Windows MySQL 8 installation and keeps QA data in a dedicated `bingo_qa` schema. The QA backend listens on port `8081`, separate from the normal local backend on `8080`.

## Current machine inventory

- MySQL service: `MySQL80`, running automatically.
- MySQL endpoint: `127.0.0.1:3306`; server version: `8.0.36`.
- QA schema: `bingo_qa`, MySQL 8.0.36, `utf8mb4_0900_ai_ci`; Flyway schema version 14, 30 tables.
- Read-only QA account check: 0 users and 0 agents are currently provisioned; latest Flyway migration is version 14 and successful.
- QA DB users: `bingo_qa_app` has only `SELECT`, `INSERT`, `UPDATE`, and `DELETE` on `bingo_qa.*`. `bingo_qa_migrator` has those plus `CREATE`, `ALTER`, `INDEX`, and `REFERENCES` on `bingo_qa.*`. Neither has global grants. Random passwords and the QA JWT secret are in ignored `bingo-backend/.env.qa`; no secrets are recorded here.
- Normal backend configuration defaults to schema `bingo` and Hibernate `update`. It is currently stopped; QA scripts use port 8081 and do not use that schema.
- Existing development admin: `http://localhost:5173/login`, HTTP 200; its ignored `.env.local` still points at port 8080, where no backend is currently running.
- QA admin: `http://localhost:5174/login`, HTTP 200 during setup. It runs Vite QA mode using ignored `bingo-admin/.env.qa`, which points only to port 8081.
- Public website: `http://localhost:4321/`, HTTP 200 during setup.
- QA backend: `http://localhost:8081`; running with the `qa` profile. `/actuator/health` returns `UP`; CORS preflight from `http://localhost:5174` returns HTTP 200.
- Node.js: `v22.12.0`; the admin README asks for `22.13` or newer.
- Playwright: `1.64.1`; Chromium and headless Chromium are present in the local Playwright cache.
- Flutter SDK: `C:\src\flutter`, stable 3.47.7, Dart 3.13.5; both app manifests and lockfiles require Flutter 3.41.0 / Dart 3.11.0 or newer. The SDK already meets those requirements. Flutter commands need permission to write the SDK cache outside the repository in this environment.
- Android SDK: `C:\Users\16414\AppData\Local\Android\Sdk`; SDK platform 36.0.0, emulator 37.2.12, and AVD `Medium_Phone_API_37.0`. An Android 17 / API 37 emulator is running as `emulator-5554`. `ANDROID_HOME` and `ANDROID_SDK_ROOT` are set process-locally for QA commands, and `platform-tools` / `emulator` are added to the process PATH. `adb devices -l` and `flutter devices` then detect the emulator. `10.0.2.2` responds from the emulator.

## QA database

`bingo_qa` was created after explicit confirmation, then the current migrations were applied. Two local-only database users are scoped to this schema:

- `bingo_qa_app`: `SELECT`, `INSERT`, `UPDATE`, and `DELETE` on `bingo_qa.*` for application runtime.
- `bingo_qa_migrator`: the same data permissions plus `CREATE`, `ALTER`, `INDEX`, and `REFERENCES` on `bingo_qa.*` for Flyway migrations. No global privileges are needed.

Generate separate random passwords on the machine and place them only in the ignored `bingo-backend/.env.qa` file. Do not reuse the credentials from `.env` or grant either account access to `bingo` or other schemas. The example template is [bingo-backend/.env.qa.example](../../bingo-backend/.env.qa.example).

## Backend

For a local QA run:

1. The current ignored `bingo-backend/.env.qa` is configured. For a new local workspace, copy `.env.qa.example` to `.env.qa` and set unique private values; never overwrite an existing credentials file blindly.
2. Use the documented backend prerequisites (Java 25 and Maven 3.9+) and run from `bingo-backend`:

   ```powershell
   mvn spring-boot:run -Dspring-boot.run.profiles=qa
   ```

3. Verify `http://localhost:8081/actuator/health` reports `UP`, and check startup output for Flyway validation and migration against `bingo_qa`.

The `qa` profile explicitly disables database auto-creation, sets Hibernate to `validate`, disables Flyway clean/baseline, and uses separate runtime and migration credentials. `.env.qa` points only to the dedicated schema. The database was created after explicit confirmation; the existing `bingo` schema was not used or modified by QA setup.

The QA profile's one-time account bootstrap is disabled by default. For this local setup it was enabled for a single startup using `APP_QA_BOOTSTRAP_ENABLED=true` and `BINGO_QA_BOOTSTRAP_CONFIRMATION=bingo_qa`; it independently checked the JDBC catalog and required zero existing users and agents before writing. It used the app's BCrypt password/PIN encoders, agent approval service, and player registration service. Credentials and test numbers are in ignored `.env.qa`; only the account names, non-secret IDs, roles, and statuses are in [test-data-registry.md](test-data-registry.md). After the bootstrap, the backend was restarted with the default disabled setting. The bootstrap refuses to start if enabled against an already populated QA account schema. Player suspension has no supported player service and was skipped.

## Isolated integration-test database

The separate `bingo_qa_test` database was created on the same local MySQL 8.0.36 instance. The test account `bingo_qa_test_runner` has `ALL PRIVILEGES` only on `bingo_qa_test.*`; its global grant is `USAGE` only. MySQL denied this account access to both `bingo` and `bingo_qa`. The password and JDBC URL are in ignored `bingo-backend/.env.test`; use [`.env.test.example`](../../bingo-backend/.env.test.example) to configure another local machine.

`MySqlIntegrationTest` requires `BINGO_TEST_MYSQL_URL`, username, and password. Before a Spring integration context starts it checks for a local MySQL URL on port 3306 whose database path is exactly `/bingo_qa_test`, confirms the connected catalog, and verifies that the test user is denied access to `bingo` and `bingo_qa`. Missing settings, another host/database, or access to either persistent schema fail closed. There is no implicit Testcontainers fallback. Surefire runs `A00FlywayUpgradeRecoveryIntegrationTest` first; it cleans only `bingo_qa_test`, tests migration from V6 through V14 and checksum repair, and leaves the schema migrated at V14 for the rest of the suite.

For the current workspace, run from `bingo-backend` after loading the ignored file into the current PowerShell process:

```powershell
$testEnv = @{}
Get-Content .env.test | ForEach-Object {
  if ($_ -match '^([^#=]+)=(.*)$') { $key = $matches[1]; $value = $matches[2]; $testEnv[$key] = $value }
}
$env:BINGO_TEST_MYSQL_URL = $testEnv['BINGO_TEST_MYSQL_URL']
$env:BINGO_TEST_MYSQL_USERNAME = $testEnv['BINGO_TEST_MYSQL_USERNAME']
$env:BINGO_TEST_MYSQL_PASSWORD = $testEnv['BINGO_TEST_MYSQL_PASSWORD']
mvn -s ../.tmp/maven-settings.xml '-Dmaven.repo.local=../.tmp/m2' test
Remove-Item Env:\BINGO_TEST_MYSQL_URL, Env:\BINGO_TEST_MYSQL_USERNAME, Env:\BINGO_TEST_MYSQL_PASSWORD
```

Do not point these variables at `bingo_qa` or `bingo`. The admin activation integration test captures its outgoing message in-process, so the backend suite does not require an external SMTP catcher.

Environment variables are listed in `.env.qa.example`. `BINGO_QA_JWT_SECRET` must be a unique random secret of at least 32 UTF-8 bytes. SMTP is directed to local `127.0.0.1:1025`; account-email flows remain unavailable until a local SMTP catcher is installed and started.

## Browser QA

Admin and website URLs can be set with `BINGO_E2E_ADMIN_URL` and `BINGO_E2E_WEBSITE_URL`; the QA backend URL is `BINGO_E2E_BACKEND_URL`. Defaults for reusable helpers and Playwright are `http://localhost:5174`, `http://localhost:4321`, and `http://localhost:8081`. The QA admin can be started from `bingo-admin` with `npm run dev -- --mode qa --host 0.0.0.0 --port 5174`; its ignored `.env.qa` is copied from `.env.qa.example`. Admin E2E credentials must be supplied as `BINGO_E2E_ADMIN_EMAIL` and `BINGO_E2E_ADMIN_PASSWORD`; player credentials use `BINGO_E2E_PRIMARY_PHONE` and `BINGO_E2E_PRIMARY_PASSWORD`. Do not place credentials in tracked files or `VITE_*` variables.

`bingo-admin/tests/e2e/support/qaHelpers.ts` provides admin login, navigation, screenshots, HTTP response checks, and failure artifacts. Playwright defaults and E2E manifest URLs target the isolated QA admin/backend ports. Existing specs can create permanent business records; they have not been run as part of Module 0. QA admin/player credentials are available in ignored `bingo-backend/.env.qa` for local E2E setup; never commit or print them.

## Mobile QA

The Android emulator reaches a Windows-hosted service using `http://10.0.2.2:8081`, passed at launch time, for example:

```powershell
flutter test integration_test/local_e2e_test.dart -d Medium_Phone_API_37.0 --dart-define=API_BASE_URL=http://10.0.2.2:8081
```

Set Android paths in the same PowerShell process before Flutter or adb commands:

```powershell
$env:ANDROID_HOME = 'C:\Users\16414\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
```

Both APKs were built for the running x64 emulator with the QA API explicitly supplied, installed, and launched:

```powershell
flutter build apk --debug --target-platform=android-x64 --dart-define=API_BASE_URL=http://10.0.2.2:8081
```

The player and agent login screenshots are [player](evidence/module-00-player-login.png) and [agent](evidence/module-00-agent-login.png). Their processes reached the foreground without Flutter exceptions. A read-only HTTP GET from the emulator to `http://10.0.2.2:8081/actuator/health` returned HTTP 200 with `status: UP`. After provisioning, admin, approved agent, and player login API requests succeeded. No business E2E action was sent. Debug manifests alone allow cleartext HTTP; the app manifests include internet permission for HTTPS production endpoints. The agent Android scaffold was generated because its repository had no `android/` directory despite its README listing Android support.

`flutter doctor -v` reports Flutter and Android toolchain healthy, detects the API 37 emulator, and lists only Visual Studio for Windows desktop as missing (irrelevant to Android). `flutter pub get`, `flutter analyze`, and `flutter test` passed for both apps. Unit/widget results: 20 player tests and 10 agent tests passed. Do not run the integration E2E command until a safe database-backed test setup is available. Helpers are under each app's `integration_test/support/` directory.

## Data retention

Keep QA accounts and business records between modules. Use names such as `QA_PLAYER_001`, `QA_AGENT_001`, and `QA_SESSION_001`, synthetic phone numbers accepted by application validation, and unique references/idempotency keys. Add every persistent record to [test-data-registry.md](test-data-registry.md). Scenarios requiring initial states must create new uniquely identified records; never delete records or reverse terminal states to reuse them.
