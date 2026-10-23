# Module 02 — Admin route and API test matrix

Most admin pages and all financial/game control APIs require ADMIN or an assigned finance role. Support pages are available to ADMIN and SUPPORT accounts; SUPPORT is restricted to support/profile/password routes. The actual app route map is defined in `bingo-admin/src/routes/AppRouter.tsx`; this matrix distinguishes exercised pages from consequential mutations that were intentionally not made without a dedicated fixture.

| Route / area | Main API family | Coverage | Notes |
| --- | --- | --- | --- |
| `/login`, `/activate-account` | `/api/v1/admin/login`, `/api/v1/admin/activate` | PASS — Module 02F | Synthetic ADMIN and SUPPORT activation links captured via the local QA SMTP catcher; login and single-use password setup verified in real Chromium. |
| `/dashboard` | `/api/v1/admin/dashboard/metrics`, `/api/v1/admin/sessions/live`, `/api/v1/admin/review-queue` | PASS | Metrics compared with API, refresh, loading/error/retry, responsive view. |
| `/admin-users` | `/api/v1/admin/users` GET/POST/PUT, `/api/v1/admin/users/{id}/finance-roles` | PASS for ADMIN/SUPPORT create, activation, suspension, role transition, and finance-role revocation — Modules 02F/02G | Synthetic role transition and finance revoke use the admin UI; finance grants remain API-only and Product confirmation is still open. See [02G matrix](module-02g-test-matrix.md). |
| `/rooms` | `/api/v1/admin/rooms` GET/POST/PUT | PASS — Module 02E | Synthetic room create/edit, version increment, stale/invalid update rejection, audit and refresh persistence. |
| `/sessions`, `/sessions/{id}` | `/api/v1/admin/sessions`, `/api/v1/admin/rooms/{roomId}/sessions`, session detail/state/draw APIs | PARTIAL — Module 02E | DRAFT create/snapshot/refresh and ticket-free DRAFT cancellation verified. No live start/draw/finish or ticket-bearing cancellation/refund. |
| `/players`, `/players/{id}` | `/api/v1/admin/players`, detail, wallet/deposit/support/ticket/claim/activity reads | PASS | Search, PENDING filter/detail, dedicated status transition to PENDING. No wallet/deposit mutation. |
| `/claims` | `/api/v1/admin/win-claims` GET/detail/approve/reject | PARTIAL | No persistent claim fixture, so browser claim review/list-detail workflows were not exercised. Backend tests cover server-rejected claims and duplicate review protection. |
| `/prize-entitlements` | `/api/v1/admin/prize-entitlements` GET/settle | PARTIAL — fail closed | Browser warning and API 409 guard verified; no QA entitlement exists. Settlement remains blocked pending approved payout/accounting controls. |
| `/deposits` | `/api/v1/admin/deposits` GET/approve/reject | Route/list render | No pending deposit fixture; no financial decision. |
| `/agents`, `/agents/{id}` | `/api/v1/admin/agents`, detail, approve, suspend, `POST /api/v1/admin/users/{userId}/agent-pin`, commission rules, float review and balance/history | PASS for documented 02A/02B cases and masked agent PIN set — Modules 02A/02B/02G | PIN response/audit redaction, one-way hash, authorization, invalidation, and persistence covered. Lifecycle is in [Module 02A](module-02a-agent-lifecycle.md); funding and reconciliation are in [Module 02B](module-02b-agent-float.md). Production funding remains blocked. |
| `/wallet/signup-reward` | signup reward current/history/grants/save APIs | PASS | Saved disabled, expired, zero ETB settings; invalid currency returned 400 and UI displayed error. |
| `/wallet/transfer-policy` | transfer policy current/history/save APIs | PASS | Saved disabled, expired, zero limits; no actual transfer executed. |
| `/wallet/transfers` | transfer list/detail | Route/list render | No transfer fixture. |
| `/support/tickets`, `/support/tickets/{id}` | support list/detail/assign/messages/notes/status and eligible-assignee list | PASS for ADMIN and SUPPORT workflow — Modules 02D/02F/02G | Dedicated SUPPORT tickets covered assignment, public reply, close with resolution summary, rejection of unauthorized mutation, refresh persistence, audit, and cross-user read denial. Original tickets 1–5 remain unchanged. |
| `/audit-logs` | audit list/detail | PASS for tested lifecycle audit actions | Audit trail checked for player restriction, support mutations, and Module 02F admin/support suspensions; 17 retained status-update rows reconciled. Detail/redaction edge cases remain untested. |
| `/profile`, `/change-password` | `/api/v1/me`, `/api/v1/admin/me/password` | PASS — Module 02F | Synthetic admin positive password update, old-password denial, mismatch and incorrect current password tested. |
| `/`, `/management`, `/wallet`, unknown path | redirects to dashboard/default policy route | PASS | Redirect behavior checked in Chromium. |
| All admin API families | role-based security | PASS for covered routes; not every state-changing endpoint individually exercised | Anonymous receives 401; player and agent reads/mutations are denied 403 in prior browser/backend regressions. See [Module 02C closure matrix](module-02c-closure-matrix.md) for route-specific action gaps. |

The route matrix is a summary. The [Module 02C closure matrix](module-02c-closure-matrix.md) inventories previous route coverage. The [Module 02F master matrix](module-02f-master-coverage.md) retains historical coverage and records the Module 02G follow-up. Wallet-transfer detail and policy blockers remain open; Module 02 is incomplete.

Module 02E detailed case-level outcomes, blocked game/claim scenarios and retained synthetic room/session data are in the [Module 02E test matrix](module-02e-test-matrix.md) and [Module 02E report](module-02e-game-admin.md). Current final admin coverage and open actions are in the [Module 02F master matrix](module-02f-master-coverage.md). Module 02 is not complete.

## Backend and UI details

Dashboard metrics now count active PLAYER accounts only; pending accounts are excluded. `AdminReadServiceTest` and `AuthSecurityIntegrationTest` cover that behavior and lazy agent profile reads. Commission rule saves are versioned; GET `/api/v1/admin/agents/commission-rules/current` provides the persisted current version. Inactive latest versions stop new commission accruals while preserving history.

The E2E suite is explicitly limited to `module-02-admin-dashboard.spec.ts`; legacy suites may alter or terminate shared business fixtures and are not part of this module run. Evidence names and retained identifiers are catalogued in `docs/qa/test-data-registry.md`.
