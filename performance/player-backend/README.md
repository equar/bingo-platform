# Player and backend performance/resilience suite

This suite exercises only synthetic PLAYER accounts, the Player REST/WebSocket APIs, and (in the explicit Admin settlement race) the wallet prize-entitlement path. It contains no Agent endpoints or Agent fixture logic. It never creates accounts, sessions, tickets, claims, entitlements, or deletes test data. Financial cases are opt-in and require fresh fixtures in an isolated disposable schema.

## Hard target and resource gates

`k6/game-load.js` and `scripts/performance/Invoke-PlayerBackendLoad.ps1` both require all of the following before starting:

- `DISPOSABLE_TARGET_APPROVED=YES`, set only after an operator independently confirms that the backend and its database are disposable and isolated.
- `API_BASE_URL` must be an origin only and exactly match `APPROVED_ORIGIN`; targets must use loopback or an RFC1918 IPv4 literal. DNS names and public hosts are rejected to avoid unsafe name resolution.
- `APPROVED_MAX_VUS`, `APPROVED_MAX_RPS`, and `APPROVED_MAX_DB_CONNECTIONS` are explicitly set to approved ceilings. The runner accepts only 100, 250, 500, or 1000 concurrent players and refuses more than 1000 VUs. k6 applies the approved RPS cap; the backend startup guard and preflight check the Hikari maximum against the approved database connection ceiling.
- `APPROVED_K6_VERSION` must exactly identify the release installed from the approved official source; the runner refuses a version mismatch.
- `EXPECTED_DISPOSABLE_SCHEMA` and `EXPECTED_DISPOSABLE_MYSQL_SERVER_UUID` must exactly identify the reviewed disposable schema and MySQL server. `BINGO_PERF_GUARD_TOKEN` must be a random secret of at least 32 characters, supplied only to the isolated backend profile and local k6 runner.
- A unique 6-24 character `RUN_ID`, a pre-provisioned `SESSION_ID`, an authenticated `WS_URL` ending in `/ws`, and an out-of-repository `PLAYER_FIXTURES_FILE` containing at least the selected number of synthetic players. Set `WS_ORIGIN` to an origin already allowed by the disposable backend's WebSocket configuration; the default is `APPROVED_ORIGIN`.
- `EXPECTED_SESSION_STATE` must match the fixture; read/claim/settlement runs require `DRAWING`, purchase runs require `OPEN` with enough unsold capacity.
- Mutation modes additionally require `SYNTHETIC_FINANCIAL_MUTATIONS_APPROVED=YES`; claim modes require `VALID_CLAIM_FIXTURES_APPROVED=YES`; prize settlement concurrency requires `SYNTHETIC_PRIZE_SETTLEMENT_APPROVED=YES`.

The backend must run with the isolated `performance-test` Spring profile. That profile disables Flyway and SQL initialization, requires Hibernate `validate`, and uses only `BINGO_PERF_MYSQL_*` settings. Set `BINGO_PERF_DB_POOL_MAX` no higher than `BINGO_PERF_APPROVED_MAX_DB_CONNECTIONS`; the runner's `APPROVED_MAX_DB_CONNECTIONS` must equal that backend approved ceiling. Its startup guard checks the live connected catalog, MySQL port and server UUID, `bingo_perf_runner` account, schema-only grants, and actual Hikari maximum against the configured approved ceiling before the application can accept traffic or start scheduled work. The Admin-path identity endpoint exists only in this profile and requires the performance-only guard token; k6 verifies the exact schema, server UUID, and connection ceiling before starting VUs. A normal `dev` backend has neither that endpoint nor this profile.

Pre-provision and migrate a dedicated `bingo_perf_<run>` disposable schema through an independently reviewed DBA/migration procedure before starting the performance profile; no script here creates or cleans a schema. The backend profile has Flyway disabled deliberately. Do not use `bingo`, `bingo_qa`, shared QA, production, or credentials with cross-schema grants. The backend Maven integration-test schema is separate and its `Flyway.clean()` recovery test means it must also be treated as disposable.

The fixture file is JSON outside source control, for example:

```json
[
  {"playerId": 910001, "accessToken": "<short-lived synthetic PLAYER JWT>", "initialBalance": "500.00", "ticketPrice": "10.00"},
  {"playerId": 910002, "accessToken": "<short-lived synthetic PLAYER JWT>", "initialBalance": "500.00", "ticketPrice": "10.00"}
]
```

Use one distinct account and wallet per VU for ordinary purchases. Keep tokens short lived, file permissions restricted, and delete them after the run under the disposable environment's approved cleanup policy. Never paste tokens into a shell transcript, source tree, or report. Claim fixture rows must add a prevalidated `ticketId` and `claimedPattern`; tickets must represent legitimate eligible synthetic winners at the chosen draw sequence. A settlement race requires `SETTLEMENT_ADMIN_TOKEN`, `ENTITLEMENT_ID`, and `SETTLEMENT_REFERENCE` for one pending `PLAYER_WALLET` entitlement with payouts enabled. Do not use cash entitlements.

## Scenarios

Run one size at a time, increasing only when the prior run remains within the approved CPU, memory, database connection, and RPS ceilings. Read-only mode holds concurrent authenticated players, reads session and ordered REST draw replay, and opens authenticated STOMP subscriptions. Mutation modes execute exactly one mutation iteration per VU; a duplicate/retry mode intentionally sends concurrent same-actor/same-key requests to test idempotency. The settlement race uses only a wallet entitlement and the existing Admin endpoint; it does not invoke or imitate Agent payouts.

| Mode | Traffic and invariants |
|---|---|
| `read` | Concurrent authorized player session reads, `GET /api/v1/sessions/{id}/draws`, authenticated STOMP `/ws` CONNECT and `/topic/session/{id}/draws` subscriptions; checks ordered unique replay and WebSocket duplicate sequence delivery. Repeats for `DURATION` (default `5m`). |
| `purchase` | One unique purchase per synthetic player with a unique run-scoped idempotency key; captures wallet balances before/after and fails if the available-balance debit differs from the fixture ticket price. |
| `purchase-retry` | Concurrent same-player, same-key purchase; checks all successful responses identify one ticket, then database reconciliation must show one debit. Use `RETRY_ACTOR_INDEX`. |
| `claim` | One prevalidated valid claim per fixture player. Use for simultaneous competing eligible winners only when that exact game/pattern/draw fixture is approved. Reconcile claims and entitlements in MySQL. |
| `claim-retry` | Concurrent same-player, same-key valid claim; checks one claim response ID. Use a newly prepared eligible fixture and `RETRY_ACTOR_INDEX`. |
| `settle-race` | Concurrent requests from the configured Admin to settle one pending Player-wallet entitlement with the same reference. Checks settled status/path/reference; SQL reconciliation checks one wallet credit. Requires the separate settlement approval flag and Admin token. |
| `expired-jwt` | Uses a separately supplied expired synthetic PLAYER JWT; REST must reject it and STOMP must never return `CONNECTED`. Requires `EXPIRED_PLAYER_JWT`. |

The harness records HTTP request duration (median/p50, p90, p95, p99, max), WebSocket initial connect and reconnect duration, draw delivery delay, request/error rates, checks, iterations, throughput, and invariant counters in k6's JSON event stream and summary. Draw delivery latency is `client receive time - server drawnAt`, so synchronize client and server clocks (NTP) and record observed clock offset. REST replay order and socket duplicate detection are also written to counters. Database and host resources are gathered out of process as described below.

## Running a bounded scenario

Install a pinned, approved k6 release from the official distribution channel and confirm the target/resource preflight first. Supply environment variables through a protected local environment, never by editing this repository. Example PowerShell invocation (replace values only with approved disposable environment values):

```powershell
$env:DISPOSABLE_TARGET_APPROVED = 'YES'
$env:APPROVED_ORIGIN = 'http://127.0.0.1:8080'
$env:API_BASE_URL = $env:APPROVED_ORIGIN
$env:WS_URL = 'ws://127.0.0.1:8080/ws'
$env:WS_ORIGIN = 'http://localhost:5173'
$env:SESSION_ID = '<pre-provisioned-disposable-session-id>'
$env:PLAYER_FIXTURES_FILE = '<restricted-path-outside-repository>/players.json'
$env:APPROVED_MAX_VUS = '<approved-ceiling-no-greater-than-1000>'
$env:APPROVED_MAX_RPS = '<approved-request-rate-ceiling>'
$env:APPROVED_MAX_DB_CONNECTIONS = '<approved-total-backend-pool-ceiling>'
$env:APPROVED_K6_VERSION = '<reviewed-pinned-k6-version>'
$env:EXPECTED_DISPOSABLE_SCHEMA = 'bingo_perf_<run-id-with-underscores>'
$env:EXPECTED_DISPOSABLE_MYSQL_SERVER_UUID = '<reviewed-mysql-server-uuid>'
$env:BINGO_PERF_GUARD_TOKEN = '<random-performance-only-secret-of-at-least-32-characters>'
$env:EXPECTED_SESSION_STATE = 'DRAWING'
./scripts/performance/Invoke-PlayerBackendLoad.ps1 -Players 100 -Mode read -Duration 5m
```

Repeat with `-Players 250`, `500`, then `1000` only after reviewing the previous result, monitoring alarms, and obtaining/confirming that the approved resource ceiling covers the next step. The script does not automatically scale up. Repeat financial modes only on freshly reviewed synthetic fixtures and with their explicit opt-in variables.

Artifacts default to `%TEMP%/bingo-player-performance/<RUN_ID>/`; set `PERF_RESULTS_DIR` to another restricted path outside the repository if required. The runner captures k6 summary/event JSON, a nonsecret run manifest, and five-second host plus selected-container CPU/memory samples. Treat these as sensitive operational data: review for tokens/personal information before sharing, retain only the approved duration, then securely remove them. The fixture file and credentials are not copied into artifacts intentionally. Set `BINGO_PERF_BUILD_REVISION` to the reviewed build identifier; the runner does not invoke Git.

## Resilience and chaos procedure

Do these separately from baseline load. Record run ID, UTC start/end, tested failure, recovery action, app version, database identity, and all fixture IDs. Never inject faults without disposable-target confirmation and an operator watching system health.

1. **Packet loss/latency:** use a dedicated Linux disposable load/backend host. Apply `tc netem` only to the isolated test container/namespace interface, first with a small reviewed delay and loss rate, then remove the qdisc in a `finally`/cleanup trap. Record the exact interface, `tc qdisc show`, before/after packet counters, and prove cleanup. Do not use host-wide netem on a shared machine. Compare draw gaps, stale sequence counts, reconnect time, and claim/request errors.
2. **Network switching:** on a dedicated Android emulator, record `adb devices -l`; disable/re-enable the emulator's Wi-Fi or switch its configured network while a live session is open. Capture `adb logcat` filtered to the Player process, observed STOMP lifecycle, last received sequence, replay cursor and recovered sequence range. Do not disable connectivity on a shared physical device.
3. **Background/foreground:** on the same emulator, background the Player during an active draw sequence, leave it backgrounded across multiple draws, then foreground it. Verify app lifecycle handling, token-expiry behavior, WebSocket reconnect and ordered REST replay without duplicate UI events. Capture a screen recording/logcat and reconcile every observed sequence with the database.
4. **Expired/replaced JWT:** use `MODE=expired-jwt` with an intentionally expired synthetic token or a dedicated integration fixture. Separately test replacement/revocation using the existing auth session integration test in the disposable backend. Confirm REST 401/403, no authenticated STOMP `CONNECTED`/`MESSAGE`, and no reconnect loop with the stale token.
5. **Backend restart:** while read-only load is active, an operator restarts only the disposable backend instance using its approved orchestrator. Keep MySQL running. Measure error window, recovery, STOMP reconnect and REST replay from the last observed sequence. Do not restart a shared or production service.
6. **Database contention:** operate only in the disposable MySQL 8 schema. Increase purchase/read concurrency by approved step while recording Hikari active/idle/pending/max connections and MySQL `Threads_connected`, running statements, lock waits, deadlocks, and InnoDB row-lock time. Exercise same-session ticket sales and valid simultaneous claims only with independently validated fixtures. Stop at the first approved resource ceiling; do not tune production-like pool limits during a run.
7. **Competing valid winners:** prepare a session with multiple independently verified tickets that become eligible on the same server draw. Submit claims concurrently in `claim` mode. Verify all authorized tied winners follow the existing rule, entitlement cardinality is one per claim, payout policy is respected, and repeat settlement requests credit each Player wallet only once. No scenario may modify the draw number or fabricate eligibility.

The existing database-backed integration tests and the k6 load suite are different evidence. Run focused Spring integration tests under their existing MySQL identity guards; do not point Maven at a reusable schema. The k6 SQL reconciliation file is read-only and must use a read-only SQL account.

## Monitoring and correlation

- Use a private test-only monitoring path. Scrape Spring Boot/Micrometer metrics without opening Actuator to the load clients or public network. Capture request count/duration by route/status, JVM heap/nonheap, GC pauses, thread count, WebSocket sessions/open/close reasons, Hikari pool active/idle/pending/max, SQL query duration, lock waits, deadlocks, and MySQL connection count. Record sampling interval and missing metrics.
- Capture host/container CPU, working set/RSS, network bytes/drops, file descriptors/socket count, and database CPU/memory/disk I/O at one-second or five-second intervals. `docker stats --no-stream` is a spot-check, not a time series; prefer approved Prometheus/cAdvisor or orchestrator metrics. Do not exceed the approved CPU/memory/connection envelope.
- Send a unique `X-Correlation-ID` per REST operation (`<RUN_ID>-vu<id>-it<iteration>-<operation>`). Include `RUN_ID` and mode in the k6 user agent/tags. Record WebSocket run/VU identifiers in the STOMP subscription ID; correlate session ID and sequence number with backend structured logs and the draw table.
- Preserve raw k6 summary/event JSON, application logs, MySQL connection/lock snapshots, host metrics, and a redacted run manifest. Do not include bearer tokens, passwords, raw authorization headers, or unrestricted financial/player data in telemetry.

## Financial and draw reconciliation

After a mutation run, stop traffic and take a read-only snapshot. Run `mysql/reconcile-run.sql` against only the disposable schema, filling in the session ID, UTC run window, and comma-separated synthetic player IDs. Every returned duplicate/invariant query must be empty. Compare purchase debit count and sum, wallet before/after balances, response ticket/claim/entitlement identifiers, sequence-number range, and settlement credit count. Preserve a copy of each SQL statement and result with the run artifacts. A zero-row duplicate query alone does not prove completeness; reconcile expected records and amounts from the fixture manifest too.

## Backup and restore rehearsal

Before financial or destructive chaos scenarios, take a consistent encrypted backup of the disposable MySQL schema using the approved backup tool. Record schema identity, MySQL version, Flyway version, backup time, encryption/key reference (not key material), checksum, size, and operator. Keep one pre-run snapshot and one post-run snapshot only for the approved retention period (default recommendation: 7 days in access-controlled encrypted storage, unless the environment owner requires a shorter period); expire test-only JWTs immediately. Do not copy production data into the load environment.

On a separate disposable restore schema/server, rehearse restore before first high-load campaign and after changes to backup format or Flyway baseline. Verify checksum, `flyway_schema_history`, row counts and financial invariants, then run focused MySQL integration tests. Record measured RPO/RTO and data checks. Never restore over an existing shared schema. Deletion/expiry of backups follows the approved retention policy and audit log.

## Interpretation and non-goals

No capacity target has been set or met by this implementation. The suite reports measurements; the service owner must approve acceptable p50/p95/p99, throughput, error-rate, recovery-time, and resource ceilings before comparing results to a release target. A passing run means only that the declared scenario, environment and checks completed. It does not establish production capacity, external-provider behavior, agent payout behavior, or mobile behavior on every device/network.
