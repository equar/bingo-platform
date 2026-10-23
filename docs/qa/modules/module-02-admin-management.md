# Module 02 — Admin dashboard and management E2E

## Scope and environment

Exercise the admin dashboard and management routes in Chromium at `http://localhost:5174` against the running QA API at `http://localhost:8081`. All browser writes use retained synthetic records in `bingo_qa`; integration tests use only the guarded `bingo_qa_test` database. Do not reset either database or commit local credentials, PINs, tokens, Playwright traces, or environment files.

The suite is `bingo-admin/tests/e2e/module-02-admin-dashboard.spec.ts`. It covers dashboard metrics against API results, loading/error/retry states, responsive navigation, static and available detail routes, anonymous and non-admin access, player/agent search and detail, support-ticket assignment/replies/internal notes/status, safe disabled and expired wallet-policy configuration including validation, commission rule persistence/versioning, room/session creation and detail, and admin player restriction. Screenshots and sanitized JSON/text evidence are in `../evidence/module-02-*`.

## Results

Agent onboarding, approval, and suspension were subsequently verified in [Module 02A](module-02a-agent-lifecycle.md) with two new retained fixtures. This report remains **INCOMPLETE** because float top-up review still has no queue read API or balance/ledger view, and the remaining financial workflows were not exercised with dedicated safe fixtures. Existing QA agent identities and their profiles were preserved.

The UI/API review also found that float top-up review is by ID only and there is no review-queue listing API or current float balance/ledger API. No float review was attempted because there was no request fixture to review. Deposit, transfer, claim, entitlement, and wallet adjustment mutations were not attempted because the QA system has no dedicated business fixtures and those actions alter financial state. Their list/detail screens were checked with currently available data.

No live session was started, no draw was made, and no terminal game state was advanced. The room and session fixture remains active/DRAFT. Wallet policy records written by the test are disabled, expired, zero-value, and ETB; they cannot grant rewards or enable transfers. Commission test rules are inactive and zero basis points.

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

The agent lifecycle gap is covered separately by Module 02A. Module 02 remains **INCOMPLETE** because float review has no queue read API or float balance/ledger API. Other consequential financial decisions and game progression remain untested because no dedicated business fixtures were available. Do not report COMPLETE until all remaining mandatory workflows can be exercised safely.
