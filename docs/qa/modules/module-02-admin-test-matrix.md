# Module 02 — Admin route and API test matrix

All admin pages are protected by the ADMIN role. The actual app route map is defined in `bingo-admin/src/routes/AppRouter.tsx`; this matrix distinguishes exercised pages from consequential mutations that were intentionally not made without a dedicated fixture.

| Route / area | Main API family | Coverage | Notes |
| --- | --- | --- | --- |
| `/login`, `/activate-account` | `/api/v1/admin/login`, `/api/v1/admin/activate` | Render/access boundary | Activation requires a captured email; SMTP catcher is unavailable. |
| `/dashboard` | `/api/v1/admin/dashboard/metrics`, `/api/v1/admin/sessions/live`, `/api/v1/admin/review-queue` | PASS | Metrics compared with API, refresh, loading/error/retry, responsive view. |
| `/admin-users` | `/api/v1/admin/users` GET/POST/PUT, `/api/v1/admin/users/{id}/agent-pin` | Route render | No admin-user creation; activation requires email delivery and no dedicated fixture. |
| `/rooms` | `/api/v1/admin/rooms` GET/POST/PUT | PASS | Created and searched a synthetic active room. |
| `/sessions`, `/sessions/{id}` | `/api/v1/admin/sessions`, `/api/v1/admin/rooms/{roomId}/sessions`, session detail/state/draw APIs | PASS (read/create); NOT RUN (state/draw) | New session remains DRAFT; no draw or state transition. |
| `/players`, `/players/{id}` | `/api/v1/admin/players`, detail, wallet/deposit/support/ticket/claim/activity reads | PASS | Search, PENDING filter/detail, dedicated status transition to PENDING. No wallet/deposit mutation. |
| `/claims` | `/api/v1/admin/win-claims` GET/detail/approve/reject | Route/list render | No claim fixture; no approve/reject attempt. |
| `/prize-entitlements` | `/api/v1/admin/prize-entitlements` GET/settle | Route/list render | No entitlement fixture; settlement not attempted. |
| `/deposits` | `/api/v1/admin/deposits` GET/approve/reject | Route/list render | No pending deposit fixture; no financial decision. |
| `/agents`, `/agents/{id}` | `/api/v1/admin/agents`, detail, approve, suspend, commission rules, float review and balance/history | PASS for documented 02A/02B cases; route render reconfirmed in 02C | Lifecycle covered by [Module 02A](module-02a-agent-lifecycle.md); synthetic funding flow, separate verifier/approver, journals and reconciliation covered by [Module 02B](module-02b-agent-float.md). Production funding remains blocked. |
| `/wallet/signup-reward` | signup reward current/history/grants/save APIs | PASS | Saved disabled, expired, zero ETB settings; invalid currency returned 400 and UI displayed error. |
| `/wallet/transfer-policy` | transfer policy current/history/save APIs | PASS | Saved disabled, expired, zero limits; no actual transfer executed. |
| `/wallet/transfers` | transfer list/detail | Route/list render | No transfer fixture. |
| `/support/tickets`, `/support/tickets/{id}` | support list/detail/assign/messages/notes/status | PASS | Synthetic ticket; public reply, private note, assignment, WAITING_FOR_CUSTOMER. XSS payload rendered inert. |
| `/audit-logs` | audit list/detail | Route render/read | Audit trail also checked for the player restriction and support mutations. |
| `/profile`, `/change-password` | `/api/v1/me`, `/api/v1/admin/me/password` | Route render | Password was not changed. |
| `/`, `/management`, `/wallet`, unknown path | redirects to dashboard/default policy route | PASS | Redirect behavior checked in Chromium. |
| All admin API families | role-based security | PASS for covered routes; not every state-changing endpoint individually exercised | Anonymous receives 401; player and agent reads/mutations are denied 403 in prior browser/backend regressions. See [Module 02C closure matrix](module-02c-closure-matrix.md) for route-specific action gaps. |

The route matrix is a summary. The [Module 02C closure matrix](module-02c-closure-matrix.md) inventories each implemented route and controller operation, distinguishes render/read from state-changing coverage, and records remaining risks and final statuses. Module 02C real Chromium results and the read-only QA database cross-check are documented in [the closure report](module-02c-admin-closure.md).

## Backend and UI details

Dashboard metrics now count active PLAYER accounts only; pending accounts are excluded. `AdminReadServiceTest` and `AuthSecurityIntegrationTest` cover that behavior and lazy agent profile reads. Commission rule saves are versioned; GET `/api/v1/admin/agents/commission-rules/current` provides the persisted current version. Inactive latest versions stop new commission accruals while preserving history.

The E2E suite is explicitly limited to `module-02-admin-dashboard.spec.ts`; legacy suites may alter or terminate shared business fixtures and are not part of this module run. Evidence names and retained identifiers are catalogued in `docs/qa/test-data-registry.md`.
