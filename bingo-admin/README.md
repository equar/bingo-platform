# bingo-admin

React 18, TypeScript, and Vite admin console for the Bingo platform.

## Configuration

Set `VITE_API_BASE_URL` before running the app. Vite embeds this public API origin into the built bundle; it is configuration, not a secret. Do not put credentials, tokens, or private backend addresses in `VITE_*` variables.

For local development, put the backend URL in `.env.local`:

Example:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

For production, provide the public HTTPS origin at build/deployment time, for example `VITE_API_BASE_URL=https://bingo.example.com`. The same-origin Nginx deployment serves the SPA and proxies `/api/` and `/ws`.

## Development checks

Use Node.js 22.13 or newer and npm:

```bash
npm ci
npm run lint
npm run test:coverage
npm run build
```

## Admin session security

The current backend issues bearer tokens, and the admin client stores the session in `localStorage`. This keeps the existing API contract but means an XSS flaw could expose the active token. Keep dependencies patched, use the deployment Content Security Policy, avoid unsafe HTML rendering, and never log tokens. Migrating to `HttpOnly` cookies requires coordinated backend session and CSRF design and is intentionally not attempted here.

## Backend contract dependencies

This UI is wired to the backend OpenAPI contract and expects these admin routes to be available:

- `POST /api/v1/admin/login`
- `GET /api/v1/admin/dashboard/metrics`
- `GET /api/v1/admin/players` and `GET /api/v1/admin/players/{id}`
- `GET /api/v1/admin/rooms`, `POST /api/v1/admin/rooms`, `PUT /api/v1/admin/rooms/{id}`
- `GET /api/v1/admin/sessions`, `GET /api/v1/admin/sessions/live`, `POST /api/v1/admin/rooms/{roomId}/sessions`, `PUT /api/v1/admin/sessions/{id}/state`
- `GET /api/v1/sessions/{id}`, `GET /api/v1/sessions/{id}/draws`, `POST /api/v1/admin/sessions/{id}/draws`
- `GET /api/v1/admin/win-claims`, `POST /api/v1/admin/win-claims/{id}/approve`, `POST /api/v1/admin/win-claims/{id}/reject`
- `GET /api/v1/admin/deposits`, `POST /api/v1/admin/deposits/{id}/approve`, `POST /api/v1/admin/deposits/{id}/reject`
- `POST /api/v1/admin/players/{id}/wallet-adjustments`
- `GET /api/v1/admin/audit-logs`, `GET /api/v1/admin/review-queue`

## Current contract gaps

The React admin now includes agents and call-center support screens, but a few requested backend capabilities are still not exposed by the contract:

- Agent float balance/ledger detail is not available from any admin endpoint.
- Agent payout, ticket, and commission history are not exposed to the admin user; only agent summary counts are available.
- Agent suspend does not accept a reason field, and the float-top-up review endpoint does not accept idempotency headers.
- There is no admin endpoint for a searchable float-top-up queue or commission-rule history.
- Support ticket assignee filtering and dashboard priority totals are not available as aggregate backend endpoints, so the UI derives them from the loaded page.

The session live draw page also expects the existing websocket transport used by the backend session feed.

## Playwright E2E credentials

The Playwright specs require `BINGO_E2E_ADMIN_EMAIL`, `BINGO_E2E_ADMIN_PASSWORD`, and `BINGO_E2E_PRIMARY_PHONE` from the test environment; they do not contain fallback accounts or passwords. `BINGO_E2E_BACKEND_URL` and `BINGO_E2E_ADMIN_URL` can override the local service URLs. The tracked `.env.example` contains placeholders only and is not automatically loaded by Playwright. Supply the values through your shell environment or CI secret store. Do not print secrets in CI steps, and keep traces disabled for credential-bearing runs.

Admin-created accounts receive a single-use activation link by email. The link expires after 24 hours; the recipient chooses a password during activation. The raw activation token is placed in the URL fragment, so browsers do not send it to the web server in HTTP requests.

If a credential used by an earlier E2E configuration was real or reused, rotate it before running tests or deploying.

The guarded real-Chromium workflow for the isolated `android-test` backend is documented in [Local E2E Testing](../docs/local-e2e-testing.md). The Admin session-scheduling scenario and its current evidence are tracked in [Module 06A-5](../docs/qa/modules/module-06a5-functional-gap-closure.md). Unit/Vitest results alone are not browser E2E verification.
