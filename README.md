# Bingo Platform

Bingo Platform is a multi-app bingo service: a Spring Boot backend, React
admin console, Flutter player and agent apps, and an Astro public website. The
backend is the source of truth for authentication, business rules, persistence,
and live game events.

## Prerequisites

- Java 25+ and Maven 3.9+
- Node.js 22.13+ and npm for the Admin app
- Flutter 3.41.0+ and Dart 3.11.0+ for the mobile apps
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

From `bingo-app` or `bingo-agent`:

```bash
flutter pub get
flutter gen-l10n
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

The Flutter URL above is for an Android emulator. Use
`http://localhost:8080` for local desktop development. Always provide
`API_BASE_URL`; local HTTP is debug-only, while profile and release builds
require HTTPS.

The backend requires a private `JWT_SECRET` in the ignored
`bingo-backend/.env`; development account seeding is disabled by default.
Local MySQL defaults are for disposable development only. Never point tests or
recovery operations at production, shared QA, or persistent test data. Follow
migration-specific recovery guidance instead of dropping or cleaning a
database.

## Documentation

- [Documentation index](docs/README.md)
- [QA policy, current status, and evidence](docs/qa/README.md)
- [Local E2E procedures and database isolation](docs/local-e2e-testing.md)
- [Deployment and release](deployment/README.md)
- Component setup: [backend](bingo-backend/README.md), [Admin](bingo-admin/README.md),
  [Player](bingo-app/README.md), [Agent](bingo-agent/README.md), and
  [website](bingo-website/README.md)
