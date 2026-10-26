# Module 06A-7 — Local Android and Backend E2E Setup

Date: 2026-10-26

## Results

| Area | Status | Evidence / disposition |
| --- | --- | --- |
| Existing MySQL installation and `bingo_qa_test` | PASS — prior read-only verification | Existing sanitized evidence records MySQL 8.0.36 on port 3306, `bingo_qa_test`, Flyway V22, and the prior runner account. No schema, grants, or data were changed in this module. |
| Local MySQL root identity | NOT RUN | Owner authorizes local root. This sandbox did not authenticate as root. The launcher accepts root and prompts securely when `-MySqlUsername root` differs from `.env.test`; password values were neither read to output nor persisted. |
| Datasource policy regression | PASS | `scripts/qa/Test-AndroidTestBackendPolicy.ps1` accepts the approved endpoint/root identity and rejects the wrong schema and wrong port. |
| Spring profile configuration | PASS — static configuration | Launcher supplies `spring.profiles.active=android-test` and loads `src/test/resources`; profile datasource uses `BINGO_TEST_MYSQL_URL`, `BINGO_TEST_MYSQL_USERNAME`, and `BINGO_TEST_MYSQL_PASSWORD`; Hibernate validates; Flyway clean is disabled and validate-on-migrate is enabled. Draw scheduler and all financial posting/simulation flags are explicitly false. |
| Flyway compatibility | NOT RUN against current root connection | Launcher performs a read-only latest-version/success query and refuses startup unless the version equals the highest source migration. No migration runs on mismatch. Prior `bingo_qa_test` evidence has Flyway V22. |
| Backend startup on 8082 | BLOCKED in this execution | The launcher refuses the restricted `CodexSandboxOffline` identity. It was not started here. Existing backend on 8080 was not queried, changed, or stopped. |
| Backend health and runtime identity | NOT RUN | No process was started, so 8082 health and identity are not claimed. On startup, the script requires both the runtime identity log and `/actuator/health` UP before recording PID/evidence. |
| Android emulator and Flutter device | PASS — owner-reported normal terminal verification | The user confirms emulator-5554 and Flutter device detection from normal VS Code PowerShell. They were not re-probed in the restricted sandbox. |
| Flutter app launch and player workflows | NOT RUN | No app was launched. Login, lobby, OPEN-session navigation, price/currency, ticket ownership, purchase-disabled state, and logout have no new runtime evidence. No financial operation or ticket fixture was attempted. |
| Flutter tests/analyzer after code changes | NOT RUN | Previously reported 10/10 tests and clean analyzer predate these launcher/config changes. Run the commands below from normal VS Code PowerShell. |
| PowerShell launcher syntax | PASS | All `scripts/qa/*.ps1` scripts parse successfully. |

## Changes made

- `Start-AndroidTestBackend.ps1` now accepts configurable local MySQL usernames and `-MySqlUsername root`. If the override differs from ignored `.env.test`, it prompts with `Read-Host -AsSecureString`. It never writes the prompted password to `.env.test` or evidence. The existing `.env.test` username is not changed.
- Datasource validation is centralized in `AndroidTestBackendPolicy.psm1`: exact schema `bingo_qa_test`, host `127.0.0.1`, port `3306`, and `createDatabaseIfNotExist=false` are required. MySQL identity uses raw tab-separated columns and validates `DATABASE()`, `CURRENT_USER()`, and `@@port`.
- `root@localhost` (and local `root@127.0.0.1`) is accepted only when configured username is root. Grant-scope checks remain for non-root accounts; the owner-authorized local root exception skips that rejection. Production policies were not changed.
- Password environment variables are removed after preflight and after child process launch. Runtime evidence includes the selected username and authenticated identity, but no password.
- `application-android-test.yml` now explicitly disables the draw scheduler in addition to its existing isolated datasource, Hibernate validate, Flyway clean-disabled, and financial-off settings.
- Player launcher passes `API_BASE_URL=http://10.0.2.2:8082` and `BINGO_PLAYER_PURCHASES_ENABLED=false`. Existing debug-only Android manifest allows cleartext HTTP; the main/release manifest remains unchanged.
- Added dependency-free datasource/identity regression checks, including wrong schema, wrong port, and approved local root cases.

## Regression results

Executed from this repository in the sandbox:

```text
PASS: Android-test backend datasource and identity policy regressions (wrong schema/port rejected; local root accepted).
PASS: PowerShell parser for all QA scripts.
```

No backend package build, Spring Boot process, Android build, Flutter tests/analyzer, MySQL root connection, login, or real Android E2E was executed here.

## Exact normal-user PowerShell commands

From normal VS Code PowerShell running as `EQUAR-DELL-XPS1\16414`:

```powershell
Set-Location D:\GitHub\bingo-platform
.\scripts\qa\Start-AndroidTestBackend.ps1 -Port 8082 -MySqlUsername root
Invoke-RestMethod http://127.0.0.1:8082/actuator/health | ConvertTo-Json
Get-Content .\docs\qa\evidence\module-06a5\backend-identity-preflight.json
```

The launcher securely prompts for the root password because the current ignored `.env.test` uses a different username. Expected evidence must show `profile=android-test`, `database=bingo_qa_test`, `jdbcHost=127.0.0.1`, `jdbcPort=3306`, Flyway V22 (or an exact current source match), scheduler disabled, financial posting disabled, PID, and artifact hash. Health must show `UP`. Do not proceed if identity, migration, or health verification fails.

Then launch the Android player:

```powershell
Set-Location D:\GitHub\bingo-platform
.\scripts\qa\Run-PlayerAgainstIsolatedBackend.ps1
```

The script rechecks emulator-5554 and Flutter detection and runs Flutter with the isolated emulator URL and purchases disabled. It does not create tickets or fixtures. To run changed-code regressions and static analysis:

```powershell
Set-Location D:\GitHub\bingo-platform
.\scripts\qa\Test-AndroidTestBackendPolicy.ps1
Set-Location .\bingo-app
flutter test test/core/app_config_test.dart test/core/api_client_test.dart test/features/lobby/lobby_screen_test.dart
flutter analyze
```

## Remaining E2E disposition

Application startup, API connectivity, login/auth errors, lobby/session listing, OPEN navigation, price/currency, existing ticket ownership, purchase-disabled state, and logout are all **NOT RUN** in this module. The credentials for existing player accounts were not available in this restricted process, and no prompt was made in chat. Claim and settlement flows remain out of scope. Six historical DRAWING sessions remain untouched and scheduler stays disabled. No purchases, wallet debits, schema creation, grant changes, destructive tests, commits, or pushes occurred.
