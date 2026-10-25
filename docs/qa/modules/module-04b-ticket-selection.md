# Module 04B — Player Ticket Selection, Reservation, and Purchase Boundaries

Date: 2026-10-25  
Disposition: **PARTIAL — backend guards verified; Android and several product capabilities remain unverified or unsupported**  
Scope: Module 04B only. Module 04C onward was not started. No production financial posting was enabled.

## Existing model

The player app has no pre-purchase ticket inventory or ticket-selection screen. The live session screen offers **Purchase ticket** for an `OPEN` session. `POST /api/v1/sessions/{id}/tickets` creates a new ticket immediately; the server generates a random 5×5 card and assigns ownership to the authenticated player. The app’s “My tickets” chips select among tickets the player already owns for that session. That is post-purchase card viewing, not selection of an available ticket before purchase.

`BingoCardGenerator` uses the existing 75-ball layout: columns B/I/N/G/O map to 1–15, 16–30, 31–45, 46–60, and 61–75; the center cell is free (`0`). Each ticket is generated server-side and stored with its owner, session, public code, operation ID, grid, purchase time, and wallet-funding snapshot. No player-provided card or ticket identifier reaches purchase.

The room/session detail displays ticket price/currency and configured capacity. The session response includes capacity and ticket count; a separate live-game model derives available seats client-side, but the room/session detail does not display that value. The live purchase screen itself does not display ticket price next to its purchase action. The session screen displays a selectable list/grid only after tickets exist. There is no pre-purchase availability list or ticket/card hold API.

## Reservation support

The actual model is server-generated random cards at purchase time, not a set of pre-generated cards in a shared finite inventory. No unowned card exists before purchase to reserve. Under this model a separate reservation lifecycle is unnecessary; Module 04B-1 documented that decision and did not add reservation data or endpoints. If Product later asks players to choose specific cards from a finite catalog, inventory identity/uniqueness and reservation expiry/cancellation become explicit design decisions before implementation.

## Session and purchase boundaries

Ticket price is taken from the server’s session price snapshot; the request has no price or ticket-ID input. A purchase requires PLAYER authentication, checks the session and room, capacity, per-player limit, currency consistency, account state through the authentication filter, and available wallet balance. A transaction debits the wallet, issues the ticket, records a `TICKET_PURCHASE_DEBIT` wallet transaction and funding snapshot, and runs through the idempotency service in one transactional path. Session row locking serializes purchases against capacity; the wallet row is now pessimistically locked so same-player purchases across different sessions serialize before balance checks. The transaction rolls back together on failure.

The player purchase path records the wallet transaction and financial audit event, but does not create a separate double-entry accounting journal or account lines. No approved ticket-sale journal mapping was found in the inspected game/wallet design. That accounting treatment needs Finance/Product definition before production financial posting.

Lifecycle review found and fixed two confirmed defects. DRAFT was not rejected by `validateTicketSaleOpen`, and purchase did not recheck an inactive room after a session had been created. DRAFT and terminal sessions now return `SESSION_NOT_OPEN`; an inactive room returns `ROOM_INACTIVE`. Regression coverage verifies both denials leave tickets, wallet balances, and wallet transactions unchanged. A third regression proves concurrent purchases in separate sessions cannot spend the same player balance twice.

The existing `ticket-sales-close-on-session-start` server policy controls whether an already-started session can still sell; no new timing rule was invented. The product documentation does not define how `scheduledStartAt` independently gates an OPEN session, so the extra scheduling-boundary requirement remains a product-policy question. An inactive room cannot be scheduled for a new session under existing code.

## Verification and evidence

Module 04B-1 added a read-only ticket-format information route from room session details. It displays the server price snapshot, session/capacity availability, configured patterns, and the existing card ranges/free-center rule. It states that a random card is generated only after purchase and that the displayed format is not an assigned ticket. Purchase is disabled in this QA screen. See the [04B-1 design](module-04b1-ticket-selection-design.md), [matrix](module-04b1-test-matrix.md), and [Android evidence](../evidence/module-04b1-android-results.txt).

- Focused MySQL integration class: **18/18 passed**, 0 failures/errors/skips, against guarded local `bingo_qa_test` at schema V22. Includes positive purchase in isolated synthetic financial fixtures, idempotent replay, price/balance/ledger behavior, ticket ownership denial, purchase audit event, capacity concurrency, insufficient-funds rollback, suspended-player/DRAFT/cancelled/completed/inactive-room denial, and cross-session wallet concurrency regressions. See [focused backend results](../evidence/module-04b-backend-focused-summary.txt) and the [sanitized command log](../evidence/module-04b-backend-focused.log).
- Full backend suite: **150/150 passed**, 0 failures/errors/skips, real MySQL `bingo_qa_test` at V22. See [full backend results](../evidence/module-04b-backend-full-summary.txt) and [sanitized command log](../evidence/module-04b-backend-full.log).
- Flutter focused game controller/reconnect tests: **5/5 passed**. Full Flutter player suite: **27/27 passed**. Flutter analyzer: **clean, no issues found**. No Flutter source changed in this module. See [Flutter results](../evidence/module-04b-flutter-results.txt).
- Android: real emulator journey **1/1 passed** through login, lobby, room #6, existing DRAFT session #5 details, and refresh. The screenshot shows ETB 3.25, capacity 10, LINE pattern, and DRAFT state. The lobby does not link to ticket selection/purchase, so the prohibited purchase could not be attempted through the app. See the [session screenshot](../evidence/module-04b-android-session-details.png) and [Android results](../evidence/module-04b-android-results.txt).
- Persistent `bingo_qa` was queried using a read-only transaction. It remains 33 rooms, 18 sessions, 0 tickets, 0 draws, 0 claims, 0 entitlements, and 19 wallet transactions; Agent #3 remains ETB 51.00 available / ETB 0.00 clearing with two float transactions. All 33 room rows, 18 session rows, and the four preserved player-wallet rows exactly match the Module 04A after snapshot. See the [read-only SQL](../evidence/module-04b-database-readonly.sql) and [database output](../evidence/module-04b-database-readonly.txt).
- No persistent paid ticket or wallet mutation was attempted. Positive financial integration ran only in `bingo_qa_test`; no production posting policy was enabled.

## Remaining blockers

Module 04B remains PARTIAL. Module 04B-2 verifies purchase integrity in isolated MySQL tests and adds a production posting guard. Actual card selection is unsupported by the existing generated-at-purchase model; reservations are unnecessary unless Product approves a future finite selectable inventory. Ticket-sale accounting journal treatment and independent scheduled-time sale cutoff remain policy gaps. Android purchase remains disabled. See [04B-2 cutoff and integrity report](module-04b2-ticket-sale-cutoff.md) and [matrix](module-04b2-test-matrix.md).

Module 02 remains INCOMPLETE, Module 03 remains COMPLETE FOR DEFINED QA SCOPE, and Module 04 remains INCOMPLETE. No work beyond Module 04B was started.

## References

- [Module 04B test matrix](module-04b-test-matrix.md)
- [Module 04B-1 ticket information design](module-04b1-ticket-selection-design.md)
- [Module 04B-1 test matrix](module-04b1-test-matrix.md)
- [Module 04A player lobby](module-04a-game-lobby.md)
- [Module 02E game administration](module-02e-game-admin.md)
