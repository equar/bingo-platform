# Bingo Backend

Spring Boot 4.1.1 service using Java 25, Spring Security, Spring Data JPA,
MySQL, Flyway, Bean Validation, and springdoc OpenAPI.

## Local development and configuration

Use Java 25+, Maven 3.9+, and local MySQL. Copy `.env.example` to the ignored
`.env`, replace every placeholder, and set a private `JWT_SECRET` of at least
32 UTF-8 bytes. Generate a random secret in PowerShell:

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

Run the backend from this directory:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The local datasource defaults to `jdbc:mysql://localhost:3306/bingo` and may
create the development database. Development account seeding is disabled by
default; enabling it requires unique `DEV_ADMIN_*`, `DEV_AGENT_*`, and
`DEV_PLAYER_*` values in the ignored `.env`. Never use these development
settings for QA or production.

Admin/support accounts require a valid phone number. The backend sends a
single-use activation link that expires after 24 hours; the recipient chooses
the password. The `dev` profile reads SMTP settings from `DEV_SMTP_HOST`,
`DEV_SMTP_PORT`, `DEV_SMTP_USERNAME`, and `DEV_SMTP_PASSWORD` in `.env`. For
Gmail, use an app password. Activation emails are sent asynchronously after
account creation commits. If delivery fails, the account remains created with
pending activation and the failure is logged by the backend.
Production uses Flyway and Hibernate `validate`; it does not use development
seeding. See the [production deployment guide](../deployment/README.md) for
the protected runtime environment and secret setup.

## API, OpenAPI, and live draws

The checked-in [OpenAPI contract](openapi.json) is the source for client
generation and mocked API fixtures. During local development the live contract
is at `http://localhost:8080/v3/api-docs`. After an API change, refresh
`openapi.json` from that endpoint and commit it with the backend change.
The Admin agent roster uses zero-based Spring Data pages with a default size of
20 and a maximum size of 100. Search (`query`) and status filtering are applied
by the database before pagination; results default to `createdAt DESC, id DESC`
ordering.
Admin consumes `/api/v1/admin/**` and `/api/v1/admin/support/**`; Agent consumes
`/api/v1/agent/**`, `/api/v1/support/**`, and shared auth/game/wallet
operations; Player consumes public auth/game/wallet/support operations.

The Admin-only player verification endpoints provide a manual outcome tracker:
`GET /api/v1/admin/player-verifications`,
`GET /api/v1/admin/players/{playerId}/verification/reviews`, and
`POST /api/v1/admin/players/{playerId}/verification/reviews`. They retain
status/reason history without identity documents or free-text evidence, and
verification state does not currently enforce account, gameplay, deposit, or
withdrawal restrictions. Use an approved external verification process until
a provider and jurisdiction-specific policy are configured.

Live draws use STOMP at `/ws` locally and `wss://<host>/ws` behind TLS. Send
the existing API JWT in the STOMP `CONNECT` frame's `Authorization` header
using the bearer-token form. Subscribe to
`/topic/session/{sessionId}/draws`; events include `id`, `sessionId`,
`sequenceNo`, `numberDrawn`, and `drawnAt`. The server persists each event
before delivery. Clients deduplicate by `id` or `sequenceNo` and recover missed
events with
`GET /api/v1/sessions/{sessionId}/draws?afterSequence={n}`. Clients must not
publish authoritative game events; authenticated, subscribe-only access and
the origin allowlist are enforced by the backend.

## Security and operations

- Public registration creates only `PLAYER` accounts. Admin and Agent roles
  cannot be assigned through public registration.
- Password/PIN hashes and JWT secrets are never returned by the API. Login
  attempts are rate-limited with shared MySQL-backed counters.
- Only health, environment, and bean Actuator endpoints are exposed over HTTP.
  Health responses omit diagnostics; the other endpoints require ADMIN and
  environment values are masked.
- Production datasource TLS uses `sslMode=VERIFY_IDENTITY`. The deployment
  installs the AWS CA bundle into a Java truststore without disabling hostname
  verification.
- Previous source revisions contained predictable development/E2E fallback
  credentials. Treat any value reused outside disposable local environments
  as compromised: rotate affected passwords/PINs/secrets, invalidate sessions,
  and review access logs. Removing current-source fallbacks does not remove
  secrets from Git history.

## Integration-test safeguards

Database-backed Maven integration tests use only the separately disposable
`bingo_qa_maven_test` schema and `BINGO_MAVEN_MYSQL_URL`,
`BINGO_MAVEN_MYSQL_USERNAME`, and `BINGO_MAVEN_MYSQL_PASSWORD`. The local guard
requires loopback MySQL port 3306, the exact catalog and scoped
`bingo_qa_maven_runner` account, disabled database auto-creation, and no
cross-schema grants. Synthetic financial tests additionally require the
explicit `BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED=true` opt-in in the
isolated test job.

Do not point Maven tests at `bingo`, persistent `bingo_qa`, or Android/Admin
`bingo_qa_test`. The test suite includes cleanup and a Flyway recovery test
that calls `clean()`. The disposable Maven schema/account is not provisioned
for the current local environment, so do not run database-backed Maven tests
until its target and source guard are verified. See the
[guarded E2E runbook](../docs/qa/platform-e2e-runbook.md) and
[current blockers](../docs/qa/qa-blockers-and-approvals.md).
