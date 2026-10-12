# Live Draw WebSocket

Use the STOMP endpoint at `ws://<host>:<port>/ws` in development or `wss://<host>/ws` behind TLS.

## Authentication

Send the existing API JWT in the STOMP `CONNECT` frame as an `Authorization` header:

```text
Authorization: Bearer <access-token>
```

The backend validates the token with the same JWT rules used for HTTP requests and binds the authenticated user to the WebSocket session.

## Subscription Topic

Subscribe to live draws for a session with:

```text
/topic/session/{sessionId}/draws
```

Each message includes:

- `id`
- `sessionId`
- `sequenceNo`
- `numberDrawn`
- `drawnAt`

Messages are delivered after the draw row is persisted. Clients should deduplicate by `id` or `sequenceNo` and can recover missed updates from `GET /api/v1/sessions/{sessionId}/draws?afterSequence={n}`.

## Client Rules

- Clients must not publish authoritative game events.
- Subscribe-only access is enforced on the backend.
- If the connection drops, reconnect with the same JWT and replay from the last received sequence.
