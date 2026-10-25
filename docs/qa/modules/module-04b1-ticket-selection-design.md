# Module 04B-1 — Ticket Format Preview and Selection Design

Date: 2026-10-25  
Status: **COMPLETE FOR THE EXISTING RANDOM-AT-PURCHASE MODEL**  
Scope: safe, read-only player ticket information. No paid purchase or financial posting is enabled.

## Established ticket model

The implemented contract is **B — tickets are generated automatically during purchase**. `POST /api/v1/sessions/{id}/tickets` accepts a session ID and idempotency key; it accepts no card ID, numbers, or client price. `BingoGameService` calls `BingoCardGenerator` during the purchase transaction and creates the ticket for the authenticated player. `Ticket` persists the generated grid and owner. The server validates a 5×5, 75-ball card: columns B/I/N/G/O use 1–15, 16–30, 31–45, 46–60, and 61–75; the center square is free (`0`).

This is not a pre-generated card catalog and not a finite shared inventory. A generated card is assigned to one purchaser only after the purchase succeeds. Existing “My tickets” behavior selects already purchased cards for gameplay; it does not select a card before purchase. The session price is a server snapshot, and the client cannot supply or override it.

## Reservation decision

Temporary reservations are unnecessary for this model. There is no unowned card in a shared inventory for a player to hold, and no chosen card ID that competing purchasers could claim. A reservation table, expiry, cancellation, and reservation API would add a separate product lifecycle without protecting an existing resource. No reservation feature was added.

If Product later wants players to choose particular numbered cards, that changes the model to a finite shared inventory. Product must then decide whether to pre-generate inventory, how to define card identity/uniqueness, and whether holds expire and can be cancelled. Those choices would determine whether a reservation lifecycle is necessary. They are outside this module.

## Implemented nonfinancial experience

Room session details now link to **Ticket information**. The screen refreshes authenticated player session and room GET endpoints and displays the current server-defined session price, session state, configured win patterns, remaining seats computed from the server ticket count/capacity, and the established card column ranges/free-center rule. It explicitly says this is a format description, not an assigned ticket; no card can be selected or reserved there. Inactive rooms, non-OPEN sessions, and full sessions show their respective eligibility state. Purchase remains visibly disabled in this QA preview, which states that it will create no ticket and debit no wallet.

The screen has loading, empty pattern, retryable error, refresh, and re-entry behavior. It submits no POST request and persists no local card choice. Server authorization and eligibility checks on the existing purchase endpoint are unchanged; visibility in the preview is informational and does not guarantee that a later purchase will pass account, player-limit, wallet, or session checks.

## Security and financial boundaries

- Player room/session GET endpoints require PLAYER authorization; inactive rooms and invalid session IDs return not found. Suspended accounts are rejected by the authentication filter.
- Price and capacity are taken from the server response. No wallet amount or ticket identity is sent by the preview client.
- The preview performs GET requests only. There is no ticket creation, reservation, wallet debit, or financial posting path on this screen.
- Existing server purchase checks remain authoritative for session state, active room, capacity/player limits, price, and available wallet balance. Regression tests cover restricted-player denial, invalid IDs, inactive room and ineligible state denial, and no mutation after rejected/insufficient-funds purchases.
- Production ticket-sale accounting policy remains undefined; this nonfinancial module does not enable or change posting.

## Verification

- Focused `BingoGameIntegrationTest` against guarded MySQL `bingo_qa_test`: **18/18 passed**; see [summary](../evidence/module-04b1-backend-focused-summary.txt) and [sanitized log](../evidence/module-04b1-backend-focused.log).
- Full backend suite: **150/150 passed**; see [summary](../evidence/module-04b1-backend-full-summary.txt) and [sanitized log](../evidence/module-04b1-backend-full.log).
- Flutter targeted lobby/preview widget tests: **8/8 passed**; full player suite: **29/29 passed**; analyzer clean. See [Flutter results](../evidence/module-04b1-flutter-results.txt).
- Real Android emulator E2E: **2/2 journeys passed**; see [Android evidence](../evidence/module-04b1-android-results.txt) and [screenshot](../evidence/module-04b1-android-ticket-preview.png). Read-only `bingo_qa` before/after reconciliation: [SQL](../evidence/module-04b1-database-readonly.sql), [output](../evidence/module-04b1-database-readonly.txt).

## Product decisions still outside this scope

The current implementation is unambiguous, so no Product decision was needed to deliver a format preview. If Product wants specific card selection in the future, it must explicitly approve a different inventory model and decide whether reservations are required. Ticket-sale accounting journal treatment and scheduled-time sale cutoff remain existing Module 04B policy gaps; Module 04B stays PARTIAL.

No persistent QA sessions were changed to make a session eligible. Module 02 remains INCOMPLETE. Module 03 remains COMPLETE FOR DEFINED QA SCOPE. No Module 04C work was started; no commit or push was made.
