# Module 02E — Game Administration and Operational Controls

## Scope and disposition

This report covers room configuration, session scheduling/state controls, game auditability, claims and prize entitlement controls. It does not start Module 02F or Module 03. Module 02 remains INCOMPLETE.

**Disposition: PARTIAL.** Room configuration concurrency and validation, DRAFT session creation, inactive-room protection, ticket-free DRAFT cancellation, and the prize-settlement safety block were exercised. The module is not complete: there is no approved production prize payout/accounting policy, the persistent QA database has no safe claim/ticket fixture for browser review, and real session progression/draw and ticket-bearing cancellation/refund behaviors were not exercised. Do not interpret synthetic QA data or a UI/API guard as authorization to post a real prize.

## Implementation and defects addressed

- Room updates carry an optimistic version. Stale/missing versions fail instead of silently overwriting concurrent configuration, and successful saves return the incremented version.
- Room ticket prices, session ticket prices and prize amounts are validated to ETB minor-unit precision. Sub-cent values are rejected.
- Inactive rooms cannot receive sessions. A new session can only start as DRAFT or OPEN; operational states cannot be selected at creation.
- Database uniqueness prevents duplicate room/start schedules. Duplicate insert races return `409 SESSION_ALREADY_EXISTS`, including databases with either historical constraint name.
- Historic session snapshots remain stable when the source room is edited.
- Prize entitlement settlement is disabled at both the API and UI until approved payout evidence, authorization, and accounting controls exist. An attempt returns `409 PRIZE_SETTLEMENT_BLOCKED`; it creates no wallet credit. This is a fail-closed safeguard, not a settlement workflow.
- Optimistic locking failures map to `409 CONCURRENT_UPDATE_CONFLICT`.

## Actual verification

| Verification | Result | Evidence |
| --- | --- | --- |
| Chromium admin E2E | **6/6 passed** | [Chromium run log](../evidence/module-02e-chromium-final.txt) and screenshots below |
| Backend integration suite | **125/125 passed; 0 failures, errors or skips** | Full Maven suite; guarded `.env.test` URL targeted `bingo_qa_test` only |
| Admin frontend tests, lint, build | 78/78 tests; lint and production build passed | Prior completed run after the Module 02E UI changes |
| Flutter Android | Not run; no mobile client behavior or code is in Module 02E scope | — |
| Persistent QA database | Read-only SELECT verification; Flyway V22, duplicate schedules 0, claims/entitlements/tickets/draws 0; Agent #3 float/journals unchanged | [Database verification](../evidence/module-02e-database-verify.txt) |

An earlier Chromium attempt ran while the QA backend was being restarted by Maven compilation: 5/6 passed and the first test failed because the backend connection was refused. The final rerun after health returned UP passed 6/6 with no browser page or console errors. Failed/interrupted attempts may have created synthetic rooms/sessions; all are retained and listed in the test-data registry. No persistent data was deleted or reset.

### Covered outcomes

- Browser-created room configuration persisted after reload; edit, version increment, audit event, stale-version conflict and invalid-price rejection were checked.
- A synthetic DRAFT session was created through the admin form with room pricing/rule snapshots and survived refresh.
- Backend rejected scheduling a session for an inactive room.
- A ticket-free synthetic DRAFT session was cancelled through the browser; the response, audit event and persisted state were checked after refresh.
- Anonymous admin game reads and writes were denied.
- Prize entitlement listing rendered the settlement warning; the settlement endpoint failed closed. QA had no entitlement rows to settle.
- Backend integration cases cover required fields, invalid initial states, role enforcement, snapshot stability, configuration concurrency, duplicate schedule concurrency, rejected claims, duplicate claim review and blocked payout without wallet credit.

### Not verified / remaining gaps

- **Claims browser workflows:** no safe persisted claim fixture exists in `bingo_qa`; list filters, detail evidence, approval/rejection and browser audit review were not run end to end. Backend tests verify rejected server validation cannot be overridden and duplicate review is rejected. A valid claim can create an entitlement in isolated tests, but payout remains blocked.
- **Actual prize calculation and payment:** no approved prize policy or reconciled accounting/payout flow was supplied. No persistent claim or entitlement was created. Settlement remains blocked.
- **Live session progression:** no real QA session was opened/started, paused/resumed, drawn, closed or finished. Pause/resume are not supported by the present state model. Manual draw correctness, deterministic/replay behavior and actual winner selection remain outside this verification and require the approved gameplay scope.
- **Ticket-bearing cancellation/refund:** browser cancellation was limited to a DRAFT session with zero tickets. Refund/void rules and race behavior for ticket sales versus cancellation were not tested.
- **Schedule semantics:** room schedule values were stored; rules for timezone/DST, recurrence, past timestamps, overlapping sessions, and automated start behavior were not defined and were not certified.
- **Financial safety:** Module 02D's real-money deposit/adjustment posting remains blocked. This report does not authorize wallet or bank settlement.

## Data and safety

The persistent QA database was only queried with SELECT statements for verification. All E2E-created room/session records, including interrupted-run records, remain in `bingo_qa`; see [test-data-registry.md](../test-data-registry.md). The existing shared agents, original approved/pending agent profiles, player accounts, wallets and financial journals were not changed. Integration tests target `bingo_qa_test`, never `bingo_qa`.

## Files and evidence

- [Module 02E test matrix](module-02e-test-matrix.md)
- [Module 02 overall route matrix](module-02-admin-test-matrix.md)
- [QA test-data registry](../test-data-registry.md)
- Browser screenshots: `module-02e-room-updated.png`, `module-02e-draft-session-created.png`, `module-02e-draft-session-cancelled.png`, and `module-02e-prize-settlement-blocked.png` under `docs/qa/evidence/`.
