# Module 02 — Admin dashboard and management E2E

## Scope and environment

Exercise the admin dashboard and management routes in Chromium at `http://localhost:5174` against the running QA API at `http://localhost:8081`. All browser writes use retained synthetic records in `bingo_qa`; integration tests use only the guarded `bingo_qa_test` database. Do not reset either database or commit local credentials, PINs, tokens, Playwright traces, or environment files.

The suite is `bingo-admin/tests/e2e/module-02-admin-dashboard.spec.ts`. It covers dashboard metrics against API results, loading/error/retry states, responsive navigation, static and available detail routes, anonymous and non-admin access, player/agent search and detail, support-ticket assignment/replies/internal notes/status, safe disabled and expired wallet-policy configuration including validation, commission rule persistence/versioning, room/session creation and detail, and admin player restriction. Screenshots and sanitized JSON/text evidence are in `../evidence/module-02-*`.

## Results

Agent onboarding, approval, and suspension were subsequently verified in [Module 02A](module-02a-agent-lifecycle.md). The synthetic bank verification, separate finance roles, posting, journal integrity, and reconciliation workflow was completed in [Module 02B](module-02b-agent-float.md). Production float funding remains blocked pending real bank settlement and approved accounting controls.

Module 02 remains **INCOMPLETE**. [Module 02C](module-02c-admin-closure.md) confirms all implemented admin routes render and independently matches dashboard data against read-only `bingo_qa` queries. However, no deposit, claim, or entitlement fixtures exist; their financial decision/settlement actions and wallet adjustments remain unverified. Admin user/finance-role changes and password change are also incomplete. These gaps are listed route by route in the [Module 02C closure matrix](module-02c-closure-matrix.md); rendering a page is not counted as action coverage.

No live session was started, no draw was made, and no terminal game state was advanced. Session progression, draw, claim generation, and prize settlement are deferred to Module 03. Wallet policy records written by earlier Module 02 tests are disabled, expired, zero-value ETB; commission test rules are inactive and zero basis points.

## Product fixes made during QA

- Dashboard active-player count now excludes pending/inactive player accounts.
- Agent roster/detail reads are transactional, preventing lazy-profile failures after the persistence session closes.
- The admin shell uses a temporary drawer and menu button on narrow viewports.
- Wallet policy validation errors are shown in the page.
- Commission rules now have a current-rule read endpoint; each save adds an immutable version and the page hydrates the persisted current rule after refresh. Only the latest version controls new accruals; an inactive latest rule disables accruals.

## Coverage and evidence

See [the detailed route/API matrix](module-02-admin-test-matrix.md), [persistent record registry](../test-data-registry.md), and evidence files with the `module-02-` prefix. The test writes are retained in `bingo_qa` and include synthetic support tickets, a room/session, inactive policy versions, inactive commission rule versions, and a pending player. Original users 1–5 and both original agent profiles must remain unchanged; Module 01 player 6 remains PENDING. Keep every newly created record registered.

## Final execution — 2026-10-23

- Chromium module suite: **12/12 passed** on the real QA admin and API. This includes the corrected port-5174 WebSocket origin and all available detail routes; the browser reported no page or console errors.
- Frontend: **76/76 unit tests passed**, production build passed, and lint passed.
- Backend focused regression: **15/15 passed** across `AdminReadServiceTest` and `AuthSecurityIntegrationTest`, using guarded `bingo_qa_test` only. This covers active-player metrics, agent profile reads, commission version persistence, and role denial.
- Dashboard evidence was refreshed after the complete suite. API metrics reported 2 active players, 1 active agent, 5 open support tickets, 0 financial reviews, 0 active sessions, and zero player/agent float balances. The dashboard values were checked against the API response.
- Read-only `bingo_qa` verification confirmed the ten users and retained records in [the database snapshot](../evidence/module-02-database-verify.txt). Original users 1–5, Module 01 user 6, and both agent profiles remain unchanged. New users 7–10 are PENDING with zero ETB wallets; the four new session fixtures remain DRAFT. All reward, transfer, and commission configuration rows are inactive/expired/zero-valued.

The agent lifecycle and synthetic float-funding areas are covered by Modules 02A and 02B. Module 02 remains **INCOMPLETE** because deposit decisions, wallet adjustments, claims and prize settlement, privileged admin-user changes, and other state-changing workflows remain unverified. Full game progression is deferred to Module 03. Do not report COMPLETE until all remaining mandatory workflows are verified safely.

## Module 02C closure addendum — 2026-10-23

- Real QA Chromium closure sweep: **2/2 passed**, covering 16 desktop routes and six representative pages at 390px; no page or console errors. Anonymous metrics were 401 and authenticated admin metrics 200.
- Dashboard metrics matched read-only `bingo_qa` aggregates: 2 active players, 2 approved agents, 5 open support tickets, 0 pending deposit/float/claim reviews, 0 active sessions, player wallet ETB 0.00, and agent float ETB 51.00 available / ETB 0.00 clearing.
- Frontend regression: **75/75**; lint and production build passed. Focused backend regression on `bingo_qa_test`: **17/17**. No Module 02C writes were made to persistent QA data.
- Full route/action status is in [module-02c-closure-matrix.md](module-02c-closure-matrix.md); execution and database evidence are in [module-02c-admin-closure.md](module-02c-admin-closure.md) and `docs/qa/evidence/module-02c-*`.
