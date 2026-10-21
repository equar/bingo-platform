# bingo-admin

React 18, TypeScript, and Vite admin console for the Bingo platform.

## Configuration

Set `VITE_API_BASE_URL` before running the app. The admin shell reads the value at startup and uses it for every backend request.

For local development, put the backend URL in `.env.local`:

Example:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## Backend contract dependencies

This UI is wired to the backend OpenAPI contract and expects these admin routes to be available:

- `POST /api/v1/auth/login`
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

If a credential used by an earlier E2E configuration was real or reused, rotate it before running tests or deploying.
