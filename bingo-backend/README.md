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

Run the normal local backend from the repository root:

```powershell
.\scripts\Start-BingoBackendLocal.ps1
```

The launcher pins the local `dev` profile to `localhost:3306/bingo` even when
`SPRING_DATASOURCE_URL` is set in the shell or imported `.env` file. QA and test
databases remain available only through their explicit profiles and guarded
launchers. To inspect the launcher's resolved target without starting Spring
or connecting to MySQL, run
`.\scripts\Start-BingoBackendLocal.ps1 -ShowEffectiveConfig` from the
repository root.

### Local email capture with Mailpit (Windows)

From the repository root, install the pinned official Mailpit v1.33.1 binary
once, then start the local SMTP sink before starting the backend:

```powershell
.\scripts\Mailpit.ps1 -Action Install
.\scripts\Mailpit.ps1 -Action Start
```

Mailpit listens only on `127.0.0.1:1025` for SMTP and
`127.0.0.1:8025` for its inbox. Open <http://localhost:8025> to inspect
messages. Stop it with `.\scripts\Mailpit.ps1 -Action Stop`; check its state
with `.\scripts\Mailpit.ps1 -Action Status`.

The `dev` profile uses `DEV_SMTP_HOST=localhost`, `DEV_SMTP_PORT=1025`,
disabled SMTP authentication, STARTTLS, and SSL, and 5000 ms connection/read/
write timeouts. `APP_EMAIL_FROM` defaults to `no-reply@bingo.local` and can be
overridden in the ignored `.env`. No Gmail credentials are needed. Mailpit
stores captured messages locally under
`%LOCALAPPDATA%\BingoPlatform\Mailpit`; it does not relay or forward them, so
they are never delivered to real inboxes. The backend's existing local
workflow runs on the host; a future containerized backend would need a
network-reachable Mailpit service hostname rather than `localhost`.

If Mailpit cannot start, run `.\scripts\Mailpit.ps1 -Action Status` and check
whether another process owns ports 1025 or 8025. If the backend cannot connect,
confirm it is running with the `dev` profile and that Mailpit is listening on
1025. SMTP delivery failures are logged with the user ID and exception type
only; message bodies, recipients, and activation/reset links are not logged.

The local datasource defaults to `jdbc:mysql://localhost:3306/bingo` and may
create the development database. Development account seeding is disabled by
default; enabling it requires unique `DEV_ADMIN_*`, `DEV_AGENT_*`, and
`DEV_PLAYER_*` values in the ignored `.env`. Optional `DEV_PLAYER_TWO_*` and
`DEV_PLAYER_THREE_*` settings seed two additional players when each phone and
six-digit PIN pair is configured. Never use these development
settings for QA or production.

Admin/support accounts require a valid phone number. The backend sends a
single-use activation link that expires after 24 hours; the recipient chooses
the password. Activation emails are sent asynchronously after account
creation commits. If delivery fails, the account remains created with pending
activation and the failure is logged by the backend.
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

## Troubleshooting and application logs

The backend writes logs to the console (`stdout`/`stderr`) and does not create
application log files. Running `mvn spring-boot:run` prints them in that
terminal; a local background launcher may redirect them to its own files.
Production runs under systemd with `StandardOutput=journal` and
`StandardError=journal`; use `journalctl -u bingo-backend` to read logs. Log
rotation and retention are provided by journald/host operations, not the app.
Keep deployment retention aligned with the service's operational and privacy
requirements.

Local logs use readable timestamped lines. The `prod` profile emits structured
Logstash-compatible JSON to the console. Both formats include service,
environment, severity, and correlation ID. Request records add operation,
outcome, method, sanitized route, status, and duration. Successful API reads
and writes are INFO so ordinary business requests are searchable at the
production default level; preflight OPTIONS requests remain DEBUG. Committed
service mutations are INFO; rejected requests and recoverable conflicts are
WARN; unexpected failures are ERROR. Health polling and individual WebSocket
frames are not logged. INFO request logging increases production log volume;
route and status fields are bounded and exclude query strings and bodies.

Configure levels with `LOG_LEVEL_ROOT` and `LOG_LEVEL_APP` (both default to
`INFO`). Hibernate SQL and bind-value logging default to `OFF`; the optional
`LOG_LEVEL_HIBERNATE_SQL` and `LOG_LEVEL_HIBERNATE_BIND` settings are intended
for short-lived local diagnostics and must remain off in production. Do not
enable SQL/bind logging or DEBUG/TRACE in production. Never put credentials,
tokens, or request bodies in a log-level override or support bundle.

To follow one request, send an optional `X-Correlation-Id` containing 1–64
ASCII letters, digits, `.`, `_`, `:`, or `-`. The server replaces invalid IDs
with a UUID and returns the accepted/generated value in the same response
header. Search the console/journal for that exact value. Async email work
inherits the request ID; background draw sweeps use a separate generated
`job-…` correlation ID. Financial `Idempotency-Key` values are separate and
are never logged.

Sanitized examples:

```text
2026-11-11T14:04:12.218-05:00 level=INFO service=bingo-backend env=dev correlationId=req-91 "operation"="WalletService.transfer" "outcome"="committed" "operationId"="9b2…" "actorId"=42 "entityId"=881 "durationMs"=37 - Business operation committed
2026-11-11T14:04:12.220-05:00 level=WARN service=bingo-backend env=dev correlationId=req-91 "operation"="authentication" "outcome"="rejected" "errorCode"="AUTH_INVALID_CREDENTIALS" - Request rejected
2026-11-11T14:04:12.224-05:00 level=ERROR service=bingo-backend env=dev correlationId=req-92 "operation"="http_request" "outcome"="failed" "route"="/api/v1/wallet/transfers" "failureType"="DataAccessResourceFailureException" - Unhandled request failure
2026-11-11T14:04:22.000-05:00 level=ERROR service=bingo-backend env=dev correlationId=job-31f… "operation"="game_live_draw_sweep" "outcome"="failed" "job"="scheduled" "failureType"="DataAccessException" - Scheduled draw sweep failed
```

For login problems, search the request ID for `operation=authentication` or
`login_rate_limit`; logs intentionally omit the submitted identity. For
database failures, start with the request's `failureType` and original stack
trace, then check `/actuator/health` and the database/service journal without
printing connection properties. For wallet or other financial changes, find
the `Business operation committed` record and its internal `operationId`; it
is emitted only after commit. Replayed idempotent requests log `outcome=replay`
without the idempotency key. For scheduled jobs, search the generated `job-…`
correlation ID and `operation=game_live_draw_sweep`; successful sweeps are
logged only when they commit one or more draws.

The application does not rotate files or ship logs to a central service.
Production aggregation, access controls, retention, alerting, and clock
synchronization remain deployment responsibilities. Durable business audit
records remain separate from these diagnostic logs.

## Integration-test safeguards

Database-backed Maven integration tests use only the separately disposable
`bingo_qa_maven_test` schema and `BINGO_MAVEN_MYSQL_URL`,
`BINGO_MAVEN_MYSQL_USERNAME`, and `BINGO_MAVEN_MYSQL_PASSWORD`. The local guard
requires loopback MySQL port 3306, the exact catalog and scoped
`bingo_qa_maven_runner` account, disabled database auto-creation, and no
cross-schema grants. Synthetic financial tests additionally require the
explicit `BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED=true` opt-in in the
isolated test job.

Migration V27 creates MySQL triggers. When binary logging is enabled, MySQL
8.0.36 requires `log_bin_trust_function_creators=1` for these trigger
definitions. On a verified, dedicated, nonproduction local test server, a
MySQL administrator can persist the setting with:

```sql
SET PERSIST log_bin_trust_function_creators = 1;
SELECT @@global.log_bin, @@global.log_bin_trust_function_creators;
```

`SET PERSIST` requires `SYSTEM_VARIABLES_ADMIN` (or the deprecated `SUPER`)
privilege and takes effect immediately. This is a server-wide setting: confirm
the instance is not shared or production and check its bind address and firewall
rules before changing it. Do not disable binary logging or grant `SUPER` to the
Maven runner. A loopback JDBC URL alone does not prove that the MySQL server is
local-only.

Do not point Maven tests at `bingo`, persistent `bingo_qa`, or Android/Admin
`bingo_qa_test`. The test suite includes cleanup and a Flyway recovery test
that calls `clean()`. Verify the isolated target and source guard, as well as
the server-wide trigger prerequisite, before running database-backed Maven
tests. See the
[guarded E2E runbook](../docs/qa/platform-e2e-runbook.md) and
[current blockers](../docs/qa/qa-blockers-and-approvals.md).
