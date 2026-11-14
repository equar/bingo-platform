# Bingo Platform

Bingo Platform is a multi-app bingo service: a Spring Boot backend, React
admin console, Flutter player and agent apps, and an Astro public website. The
backend is the source of truth for authentication, business rules, persistence,
and live game events.

## Prerequisites

- Java 25+ and Maven 3.9+
- Node.js 22.13+ and npm for the Admin app
- Flutter 3.41.0+ and Dart 3.11.0+ for Player and Agent
- Visual Studio 2022 Build Tools with **Desktop development with C++** and a Windows 10/11 SDK for Windows desktop builds
- Android Studio and an Android SDK for Android emulators
- Google Chrome for browser runs
- MySQL 8 for local backend development

## Local quick start

Start MySQL, then the backend, followed by the clients you need. Run each
command from the directory shown:

From `bingo-backend`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Before startup, follow the [backend guide](bingo-backend/README.md) to create
the ignored `.env` file and configure `JWT_SECRET`.

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

The launcher resolves paths from its own location, so it can be run from any
working directory. It uses `10.0.2.2:8080` from an Android emulator and
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
