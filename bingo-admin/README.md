# bingo-admin

React 18, TypeScript, and Vite admin console for the Bingo platform.

## Configuration

Set `VITE_API_BASE_URL` before running the app. The admin shell reads the value at startup and uses it for every backend request.

Example:

```bash
VITE_API_BASE_URL=https://api.example.com
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

The session live draw page also expects the existing websocket transport used by the backend session feed.
