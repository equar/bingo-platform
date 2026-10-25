# Module 04C-1 — Draw Integrity and Admin Number-Selection Security

Disposition: **TECHNICALLY VERIFIED — Chromium live-session E2E blocked by unavailable QA API and active-session fixture**  
Scope: server-generated ordinary draws, admin-triggered automatic draws, role enforcement, audit, and admin UI. Module 04D claims and Module 04E settlement were not started. Financial posting remains disabled.

## Design and implementation

The admin draw endpoint is now bodyless: `POST /api/v1/admin/sessions/{id}/draws` asks the server to draw the next number. Any request body is rejected with `CALLER_SELECTED_DRAW_NOT_ALLOWED`, so the former `numberDrawn` input is not part of the normal draw protocol. The server retains its `SecureRandom` selection from the remaining values in the 1–75 pool.

Admin-triggered draws use the scheduler's due-time rule, pessimistic session lock, unique sequence/number constraints, transaction, and after-commit WebSocket broadcast. Repeated concurrent triggers at the same due instant can commit at most one draw. The operator action does not bypass the draw interval. Missing, cancelled, closed, and otherwise ineligible sessions fail without creating a draw.

The UI prevents another click while a request is pending, and concurrent same-instant requests serialize. The endpoint has no idempotency key: a transport retry arriving at a later due interval may request the next draw. The broader retry contract remains unresolved (Module 04C matrix D08).

The controller and service restrict this operation to ADMIN users and explicitly exclude SUPPORT, FINANCE_VERIFIER, and FINANCE_APPROVER, including users with combined roles. Scheduler-generated draws remain automatic. Each committed draw writes a `BINGO_NUMBER_DRAWN` audit event in the same transaction with the game session target, operation ID, sequence number, number, and source. Admin-triggered draws include the operator actor; scheduler events identify the scheduler source and have no user actor.

The admin session page no longer accepts a number. Eligible administrators see “Draw next number”; the button disables while the request is pending, and the response displays the number selected by the server. The API client sends no request body.

## Verification

The focused backend suite passed **32/32**, including the OpenAPI contract. The full backend regression passed **159/159**. These suites cover authorized and denied roles, unauthenticated access, legacy request-body rejection, valid server selection, audit persistence, transaction rollback, ineligible sessions, and concurrent triggers. The full admin frontend suite passed **84/84**; lint and production build passed. Admin tests cover the bodyless API request, removal of number entry, display of the returned draw, and pending-request protection. See the [focused backend summary](../evidence/module-04c1-backend-focused-summary.txt), [full backend summary](../evidence/module-04c1-backend-full-summary.txt), [OpenAPI summary](../evidence/module-04c1-openapi-summary.txt), and [admin summary](../evidence/module-04c1-admin-summary.txt).

Persistent `bingo_qa` is not used to create or trigger a draw. Database verification is SELECT-only and the game, wallet, journal, and Agent #3 float snapshots match before and after; see the [database reconciliation](../evidence/module-04c1-database-readonly.txt). A real Chromium live-game E2E remains blocked because no eligible persistent live session exists and the QA backend is unavailable on port 8081; see the [browser result](../evidence/module-04c1-chromium-results.txt). Existing 04C Flutter unit/widget coverage remains applicable.

This change removes caller-selected numbers in the ordinary admin draw path. It does not provide independent randomness certification, seed attestation, or regulatory approval. No 04D or 04E workflow was started.

The existing `bingo-admin/tests/e2e/local-e2e.spec.ts` still sends chosen numbers to build a deterministic claim/settlement scenario. It is incompatible with this stricter route and was not run because it targets persistent QA state. Claim E2E needs a safe isolated deterministic fixture in the deferred Module 04D work; the ordinary draw endpoint must remain server-selected.

See the [I01–I20 test matrix](module-04c1-test-matrix.md), [Module 04C report](module-04c-game-session-draws.md), and [status page](../module-status.md).
