# Game administration and production operations policy

## Game mutation authorization

The Admin game API requires the `ADMIN` role at the HTTP security matcher, controller method, and `BingoGameService` Admin entry point. The service also requires an enabled, active Admin principal before starting a room or session mutation. Support remains authorized only for the explicitly scoped support workflows; it cannot create or edit rooms/sessions, change session state, or trigger a draw. Existing state-transition checks, row locks, funding checks, and cancellation/refund behavior remain in the game service. The automatic draw scheduler operates through its scheduler service path without an HTTP principal, so the Admin-only check does not block scheduled draws.

Private-game host operations keep their existing Player authorization and business-state checks. Scheduled room cadence continues to create future sessions through its internal repository path.

## API documentation

Springdoc API schema and Swagger UI remain enabled by default for local developer use. The `prod` profile disables both endpoints (`springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false`). Production ingress should not publish these paths as alternate static files.

## Actuator

Only `/actuator/health` is exposed over HTTP, for deployment health probes. It is publicly reachable but returns only overall status: component names and details are hidden. Environment, beans, configuration properties, and other diagnostic endpoints are not exposed over HTTP in production. Operational diagnostics should use the platform's authenticated logs/metrics facilities rather than enabling broad Actuator exposure.

## HTTP and WebSocket

REST routes use stateless JWT authentication and role matchers. The `/ws` HTTP route permits the WebSocket handshake; that does not grant application access. STOMP `CONNECT` requires a valid bearer token with current token-version and active Player-session checks. `SUBSCRIBE` is limited to the supported session destinations and session access is checked against the caller's role and private-game membership. Client origins remain constrained by the configured WebSocket allowed-origin list.
