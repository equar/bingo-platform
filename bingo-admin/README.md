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

## Navigation and interaction conventions

| Context | Convention |
| --- | --- |
| Navigation and page names | Use the route's staff-facing entity name consistently in the sidebar, page heading, breadcrumb, and browser title. Keep entity distinctions such as room, session, ticket, claim, deposit, and transfer. |
| Create and edit | Use **Create [entity]** for new records, **Save changes** for edits, and **Cancel** to abandon unsubmitted form changes. |
| Record navigation | Use links for destinations and buttons for actions. Detail pages link back to their parent list; nested routes keep the parent active in the sidebar. |
| Search and results | Keep search and filters with their result list; keep pagination below the table. Preserve server pagination and export semantics. |
| List state | Keep non-sensitive filters, page, and page size in the URL and carry them into detail/editor links when a record route exists. Keep free-text searches (which may contain names, email addresses, or phone numbers) and the controls for modal-based queues in tab session storage; clear both on sign-out. URL state is enabled for sessions, players, agents, support tickets, rooms, and player verification. Audit logs, claims, deposits, prize entitlements, admin users, wallet transfers, and session tickets retain their controls in tab session storage. |
| Dialogs | Use **Close** for read-only dialogs and **Cancel** for an unsubmitted decision. Name consequential actions and affected records in confirmation copy. |
| Action wording | Use explicit business verbs such as approve, reject, suspend, activate, reconcile, and settle. Never show success until the API confirms the action. |
| Access | Hide unavailable actions according to the existing role capabilities. Backend authorization remains authoritative; do not infer or introduce permissions from UI visibility. |
| Language | Add shared labels and page-specific copy to `adminUiTranslations.ts` and keep English keys in `i18n.ts`; preserve equivalent meaning in English and Amharic. |

Review and verification dialogs are intentional exceptions to page-based detail/edit flows where the existing workflow must keep queue context visible. Financial actions retain their precise business names and confirmation steps instead of generic save or submit labels. List filters should be represented in URL query parameters when a page already supports deep links; never put credentials, tokens, or other sensitive data in a URL.
