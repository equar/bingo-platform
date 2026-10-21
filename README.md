# Bingo Platform

Bingo Platform is a multi-app system for running and operating a bingo product.
This repository contains the backend service plus three client apps:

- `bingo-backend` - Spring Boot API, authentication, Flyway migrations, WebSocket feed, and OpenAPI contract
- `bingo-admin` - React/Vite admin console for operations and support workflows
- `bingo-app` - Flutter player app for gameplay and live draws
- `bingo-agent` - Flutter agent scaffold for counter-service workflows

## Local Prerequisites

Before starting the apps locally, make sure you have:

- Java 25 or newer for the backend
- Maven 3.9+ or a compatible Maven installation
- Node.js 18+ and npm
- Flutter 3.22+ for the mobile apps
- MySQL 8 running locally

The backend uses local MySQL by default and does not require Docker.
It connects to `jdbc:mysql://localhost:3306/bingo` and expects the local MySQL user to be able to create the database if it does not already exist.

## Start Order

Start the backend first, then the frontends.

1. Start `bingo-backend`
2. Start `bingo-admin`
3. Start `bingo-app`
4. Start `bingo-agent` only when you need to work on the agent scaffold

## Backend

The backend is the source of truth for authentication, business rules, persistence, and realtime updates.

From `bingo-backend`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The backend requires a private JWT signing secret for every profile. For local work, copy
`bingo-backend/.env.example` to the untracked `bingo-backend/.env` file and replace its
placeholder `JWT_SECRET` with a randomly generated value before starting the backend.
The `dev` profile does not seed accounts unless explicitly enabled and supplied with
private development credentials.
If Flyway reports a checksum or migration error on your local machine, the easiest recovery is usually to drop the local `bingo` database and start the backend again so migrations can be applied from scratch.

When creating new admin or support accounts from the console, the backend generates the password, enforces a 10-digit phone number, and emails the password to the new user. Make sure SMTP is configured before using that flow locally.

Useful backend notes:

- Default local datasource credentials are `root` / `root`
- The backend exposes OpenAPI from the checked-in contract at `bingo-backend/openapi.json`
- Live game updates use the backend WebSocket/STOMP transport

## Admin Console

The admin app is a React 18 + TypeScript + Vite frontend.

From `bingo-admin`:

```bash
npm install
npm run dev
```

Before starting it, set the backend base URL in `.env.local`:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

The admin UI expects the backend to be running first.

## Player App

The player app is a Flutter client for players.

From `bingo-app`:

```bash
flutter pub get
flutter gen-l10n
flutter run --dart-define=API_BASE_URL=http://localhost:8080
```

The app reads the backend URL only from `--dart-define=API_BASE_URL=...` and stops at startup if the value is missing.
For live draws, it connects to the backend WebSocket endpoint and uses the same host as the REST API.

## Agent App

`bingo-agent` is a Flutter scaffold for agent workflows.

From `bingo-agent`:

```bash
flutter pub get
flutter gen-l10n
flutter run
```

This module is currently a scaffold, so check its own README before wiring it into a specific workflow.

## API And Contract Notes

- The backend contract is defined by `bingo-backend/openapi.json`
- The frontend apps are expected to follow that contract when calling admin, auth, session, and support endpoints
- WebSocket consumers should keep the backend running so live draw traffic and session events are available

## Troubleshooting

- If the backend fails during Flyway startup, verify that MySQL is running and that the local `bingo` database is clean
- If a prior local migration failed, drop the database and restart the backend with the `dev` profile
- If the admin app cannot talk to the backend, confirm `VITE_API_BASE_URL` points to `http://localhost:8080`
- If the Flutter player app exits immediately, confirm `API_BASE_URL` was provided with `--dart-define`

## Related Docs

- [Backend README](bingo-backend/README.md)
- [Admin README](bingo-admin/README.md)
- [Player app README](bingo-app/README.md)
- [Agent README](bingo-agent/README.md)