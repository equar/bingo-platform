# Bingo Platform

Bingo Platform is a multi-app bingo service: a Spring Boot backend, React
admin console, Flutter player and agent apps, and an Astro public website. The
backend is the source of truth for authentication, business rules, persistence,
and live game events.

## Prerequisites

- Java 25+ and Maven 3.9+
- Node.js 22.22.0 and npm for Admin and website checks (version pinned in `.node-version`)
- Flutter 3.41.0+ and Dart 3.11.0+ for Player and Agent
- Visual Studio 2022 Build Tools with **Desktop development with C++** and a Windows 10/11 SDK for Windows desktop builds
- Android Studio and an Android SDK for Android emulators
- Google Chrome for browser runs
- MySQL 8 for local backend development

## Local quick start

Start MySQL, then the backend, followed by the clients you need. Run each
command from the directory shown:

From the repository root, start the normal local backend:

```powershell
.\scripts\Start-BingoBackendLocal.ps1
```

This launcher explicitly selects the `dev` profile and MySQL schema `bingo`;
it passes the datasource URL as a Spring command-line argument so ambient
datasource environment overrides cannot redirect local startup. It does not
start or alter MySQL itself. Before startup, follow the
[backend guide](bingo-backend/README.md) to create the ignored `.env` file and
configure `JWT_SECRET`.

From `bingo-admin`:

```bash
npm ci
npm run dev
```

Set `VITE_API_BASE_URL` as described in the [Admin guide](bingo-admin/README.md).

Start Player or Agent on a selected platform from the repository root:

```powershell
.\scripts\Run-BingoLocal.ps1 -App player -Platform android
.\scripts\Run-BingoLocal.ps1 -App player -Platform chrome
.\scripts\Run-BingoLocal.ps1 -App player -Platform windows
.\scripts\Run-BingoLocal.ps1 -App agent -Platform android
.\scripts\Run-BingoLocal.ps1 -App agent -Platform chrome
.\scripts\Run-BingoLocal.ps1 -App agent -Platform windows
```

For Android development on Windows, keep the Pub package cache on the same
drive as this checkout when possible. The following commands are for normal
Windows PowerShell (not CMD). Run them as the Windows developer account so
the user-level setting persists:

```powershell
$env:PUB_CACHE = 'D:\DevCaches\Pub'
New-Item -ItemType Directory -Force $env:PUB_CACHE | Out-Null
[Environment]::SetEnvironmentVariable('PUB_CACHE', $env:PUB_CACHE, 'User')
```

If using CMD instead, use CMD syntax and switch drives with `cd /d`:

```bat
setx PUB_CACHE "D:\DevCaches\Pub"
set "PUB_CACHE=D:\DevCaches\Pub"
cd /d D:\GitHub\bingo-platform\bingo-player
flutter pub get
cd /d D:\GitHub\bingo-platform\bingo-agent
flutter pub get
```

In PowerShell, use `Set-Location` (or `cd`) and set `$env:PUB_CACHE`; do not
paste the PowerShell commands into CMD. Both apps' package configurations
should resolve hosted and Git dependencies under `D:\DevCaches\Pub`.

Set supported Android paths in the same PowerShell session if Android tools
report a `.android` path at the drive root:

```powershell
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
$env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
```

## Local Git checks on Windows

Install this clone's Git hooks from PowerShell:

```powershell
.\scripts\Install-BingoGitHooks.ps1
```

The installer uses an existing `core.hooksPath` when configured and preserves
existing pre-commit/pre-push hooks as chained backups. Re-run it to refresh the
wrappers. Pre-commit checks staged Admin JavaScript/TypeScript lint and staged
Player/Agent Dart formatting against the staged blob, without formatting or
staging files. Pre-push checks outgoing commits in an isolated Git worktree;
first pushes with no known base conservatively check every CI-covered component
in the outgoing tree. Failures show the component, command, output, and rerun
command, and block the commit or push.

Run validation manually:

```powershell
.\scripts\hooks\Invoke-BingoGitChecks.ps1 -Mode Manual
```

The same entry point is available in VS Code under **Tasks: Run Task → Verify
before push**. It validates committed changes against the configured upstream,
or the whole current commit when no upstream is available.

The Admin, website, backend, Player, Agent, and infrastructure checks are
shared with their GitHub Actions workflows. Backend integration tests run
locally only when `bingo-backend/.env.maven-test` has the approved loopback
`bingo_qa_maven_test` URL and scoped `bingo_qa_maven_runner` account; the
backend test guard verifies database identity and grants before tests run.
Without that isolated configuration, local checks compile/package the backend
and report that database tests remain in CI. The rendered Nginx check, GitHub
deployment/release steps requiring credentials or permissions, and the second
Flutter version in the mobile CI matrix remain CI-only.

The recent mobile Actions failure was the Player localization test for missing
Tigrinya message keys. The local Player/Agent check runs `flutter test`, so the
same localization test failure blocks the push when it is present in the
outgoing commit; the matrix's other Flutter SDK version remains CI-only.

Persist those Android values with the same user-level API when running as the
normal Windows developer account:

```powershell
foreach ($name in 'ANDROID_HOME','ANDROID_SDK_ROOT','ANDROID_USER_HOME','ANDROID_EMULATOR_HOME','ANDROID_AVD_HOME') {
    [Environment]::SetEnvironmentVariable($name, (Get-Item "Env:$name").Value, 'User')
}
```

After changing `PUB_CACHE`, run `flutter pub get` once in both apps. The
launcher checks generated package configuration against `pubspec.yaml`,
`pubspec.lock`, optional `pubspec_overrides.yaml`, and the selected Pub cache;
it resolves dependencies only when one is stale or missing. Keep the launcher
terminal open: `r` hot reloads and `R` hot
restarts. Use a full stop and relaunch after native Android/Gradle changes.
For a one-app generated-state recovery, pass `-CleanRebuild`; normal launches
keep build and Gradle caches.

The launcher resolves paths from its own location, so it can be run from any
working directory. It uses an ADB reverse tunnel to `127.0.0.1:8080` from
an Android emulator and
`localhost:8080` from Chrome and Windows. Player live draws connect to `/ws`;
the API origin is the backend root at port 8080 and API routes use `/api/v1`.
Player and Agent use separate Chrome ports (5180 and 5181), so both can run at
once. Android uses the currently connected device when there is one, prompts
when several are connected, and starts the only configured emulator when none
is running. Pass `-DeviceId <id>` to select a particular Android device or
configured emulator.

You can launch the same six targets from **Run and Debug** in VS Code using the
`Bingo Player — Android emulator`, `Bingo Player — Chrome`, `Bingo Player — Windows`,
`Bingo Agent — Android emulator`, `Bingo Agent — Chrome`, and
`Bingo Agent — Windows` configurations. Select the Android emulator in the
VS Code device picker; the configurations do not pin a machine-specific ID.

Start the local backend and MySQL first, following the [backend guide](bingo-backend/README.md).
Its `dev` profile allows only the configured local client origins; production
CORS and WebSocket origin rules remain separate. Development seeding is off by
default. Local test accounts must be configured using the documented
`DEV_PLAYER_*` and `DEV_AGENT_*` environment entries or created through the
supported local workflow; keep credentials in the ignored backend `.env` and
do not copy them into source or launch settings.

Each platform launch is a separate app instance with its own local session
storage. Switching platform does not move an in-progress game or workflow.
The development URLs use HTTP/WS only in debug mode; profile and release
configurations require HTTPS/WSS and reject loopback endpoints.

The backend requires a private `JWT_SECRET` in the ignored
`bingo-backend/.env`; development account seeding is disabled by default.
Local MySQL defaults are for disposable development only. Never point tests or
recovery operations at production, shared QA, or persistent test data. Follow
migration-specific recovery guidance instead of dropping or cleaning a
database.

## Branch promotion policy

Promote changes through `feature/*` or `fix/*` -> `develop` -> `release` ->
`main`. Hotfixes follow the same flow; do not bypass an intermediate branch.
Any source branch may target `develop`, including `release` or `main` when
synchronizing changes back. Only this repository's `develop` branch may target
`release`, and only this repository's `release` branch may target `main`; fork
branches with those names are not valid promotion sources. Changes to
`develop`, `release`, and `main` must go through pull requests; direct pushes,
force pushes, and branch deletion are blocked, including for administrators.

## Documentation

- [Documentation index](docs/README.md)
- [QA policy, current status, and evidence](docs/qa/README.md)
- [Guarded E2E procedures and database isolation](docs/qa/platform-e2e-runbook.md)
- [Deployment and release](deployment/README.md)
- Component setup: [backend](bingo-backend/README.md), [Admin](bingo-admin/README.md),
  [Player](bingo-player/README.md), [Agent](bingo-agent/README.md), and
  [website](bingo-website/README.md)
