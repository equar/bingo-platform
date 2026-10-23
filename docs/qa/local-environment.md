# Local QA Environment

This setup uses the existing Windows MySQL 8 installation and keeps QA data in a dedicated `bingo_qa` schema. The QA backend listens on port `8081`, separate from the normal local backend on `8080`.

## Current machine inventory

- MySQL service: `MySQL80`, running automatically.
- MySQL endpoint: `127.0.0.1:3306`; server version: `8.0.36`.
- QA schema: `bingo_qa`, MySQL 8.0.36, `utf8mb4_0900_ai_ci`; Flyway schema version 14, 30 tables.
- Read-only QA account check: six users and two agent profiles are provisioned; latest Flyway migration is version 14 and successful. The five original users are unchanged; QA_PLAYER_003 is the retained PENDING Module 01 fixture. See [test-data-registry.md](test-data-registry.md) for non-secret identities and statuses.
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

The original adb error was caused by the execution identity, not a missing SDK or broken AVD. The sandbox SID had no registered Windows profile. Although USERPROFILE referred to the account profile, Windows SHGetFolderPath returned FILE_NOT_FOUND for the sandbox identity, so adb tried to create \.android at the filesystem root. Setting HOME and Android home variables could not repair that Windows profile lookup. Running adb and Flutter under the Windows account that owns the existing profile fixed it. No system-wide environment variables were changed and no profile, SDK, or AVD was recreated.

The existing Medium_Phone_API_37.0 emulator is API 37, detected as emulator-5554 (sdk_gphone64_x86_64). adb version 37.0.1-15733141 starts cleanly, Flutter detects the device, and /data had 7.0 GB free. The apps reached the QA backend through http://10.0.2.2:8081; successful existing-account login verified device-to-host connectivity.

Set Android paths in the same PowerShell process that runs adb/Flutter, using the current Windows account:

    $sdk = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    $env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
    $env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
    $env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
    $env:Path = "$sdk\platform-tools;$sdk\emulator;C:\src\flutter\bin;$env:Path"
    adb kill-server
    adb start-server
    adb devices -l
    flutter devices

Both apps require API_BASE_URL. For the emulator use http://10.0.2.2:8081; this value is supplied by ignored app-local e2e-secrets.json define files. There is no hardcoded API fallback. Debug manifests allow local HTTP; profile/release builds continue to require HTTPS.

Authentication-only UI suites are under bingo-app/integration_test/module_01_authentication_test.dart and bingo-agent/integration_test/module_01_authentication_test.dart. The host drivers in each app’s test_driver directory export screenshots. Run from the app folder:

    flutter drive --keep-app-running --driver=test_driver/module_01_authentication.dart --target=integration_test/module_01_authentication_test.dart -d emulator-5554 --dart-define-from-file=e2e-secrets.json

For the process-restart check, first run adb shell am force-stop with the app package, then run the same command with target integration_test/module_01_session_restore_test.dart. The keep-app-running option preserves the installed package and its secure-storage data for the follow-up launch. Credentials remain in ignored local files.

Player and agent authentication E2E and separate process-restart checks passed. The player flow covered login, validation, normalization, throttling, invalid/expired token handling, offline recovery, secure storage, and logout. The agent flow covered approved and pending accounts, throttling, invalid/expired sessions, offline recovery, secure storage, and logout. P17 created QA_PLAYER_003 through Flutter registration, set it to PENDING with the audited admin status workflow, and verified login rejection in the Android UI. Final flutter analyze passed for both apps. Screenshots, sanitized logs, and the read-only bingo_qa account-state verification are under docs/qa/evidence.

No app E2E submitted a game, wallet-transfer, or deposit operation. The authorized P17 registration created its normal wallet/signup reward and the new player remains PENDING. The original five users and two agent profiles were not deleted or changed. The full backend suite ran separately against guarded bingo_qa_test and passed 94 tests. Authentication attempts, status-change audit entries, and hashed rate-limit buckets remain in bingo_qa.
## Data retention

Keep QA accounts and business records between modules. Use names such as `QA_PLAYER_001`, `QA_AGENT_001`, and `QA_SESSION_001`, synthetic phone numbers accepted by application validation, and unique references/idempotency keys. Add every persistent record to [test-data-registry.md](test-data-registry.md). Scenarios requiring initial states must create new uniquely identified records; never delete records or reverse terminal states to reuse them.
