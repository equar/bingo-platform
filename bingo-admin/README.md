# Bingo Admin

React 18, TypeScript, and Vite console for authorized administration and
support workflows.

## Configuration and development

Use Node.js 22.13+ and npm. Set `VITE_API_BASE_URL` to the public API origin
(not an API path); Vite embeds it in the frontend bundle, so it is configuration
and never a secret. For local development, set it in ignored `.env.local`:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

Run:

```bash
npm ci
npm run dev
npm test
npm run lint
npm run build
```

The same-origin production Nginx deployment serves the SPA and proxies `/api/`
and `/ws`. Production configuration uses the public HTTPS API origin.

## Security and API contract

The client uses bearer tokens in `localStorage` under the current API
contract; an XSS flaw could expose the active token. Keep dependencies patched,
avoid unsafe HTML rendering, follow the deployment Content Security Policy,
and never log tokens. Switching to `HttpOnly` cookies requires coordinated
backend session and CSRF changes.

Admin account creation uses a single-use email activation link that expires
after 24 hours; the recipient chooses the password. The raw activation token is
in the URL fragment and is not sent to the web server in HTTP requests.

Player verification is an Admin-only manual review tracker. It stores review
outcomes and an append-only history, but does not collect identity documents,
call a verification provider, or restrict play, deposits, or withdrawals.
Perform identity checks through an approved external process and do not enter
document numbers or other sensitive identity data in this console. Support
accounts do not have access to the verification queue.

The checked-in backend [OpenAPI contract](../bingo-backend/openapi.json) is the
authoritative route reference. See the [backend guide](../bingo-backend/README.md)
for API and WebSocket details and the [guarded E2E runbook](../docs/qa/platform-e2e-runbook.md)
for real Chromium execution. Component tests alone are not browser E2E.
