# OpenAPI Integration

The backend now checks in a generated contract at [../openapi.json](../openapi.json). Treat that file as the contract source for client generation and mocked API fixtures.

## Source Of Truth

- In local development, the live contract is available at `http://localhost:8080/v3/api-docs`.
- After backend API changes, refresh [../openapi.json](../openapi.json) from the live endpoint and commit the updated file with the backend change.

## Frontend Usage

- `bingo-admin`: point code generation or typed client import at [../openapi.json](../openapi.json); use the `/api/v1/admin/**` and `/api/v1/admin/support/**` operations.
- `bingo-agent`: point code generation or typed client import at [../openapi.json](../openapi.json); use the `/api/v1/agent/**`, `/api/v1/support/**`, and shared auth/game/wallet operations.
- `bingo-app`: point code generation or typed client import at [../openapi.json](../openapi.json); use the public auth/game/wallet/support operations.

## Endpoint Coverage Notes

- The contract includes the new dashboard, player, session, agent, audit-log, and support-ticket APIs needed by the admin and call-center workflows.
- Internal support notes are intentionally excluded from player and agent responses.
- WebSocket live-draw usage is documented in [websocket.md](websocket.md).
