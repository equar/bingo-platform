# Live-game player fixes — November 11, 2026

## Confirmed production findings

Sessions 8 and 9 were LINE-only, EARLIEST_SPLIT_V1 games with three tickets and a fixed 10 ETB LINE prize. Both reached 75 calls. The scheduler previously stopped only when its number pool was exhausted. Once every number was called, every card was marked; that does not establish earliest-winner eligibility.

The player screen loaded session state initially but did not refresh it as draws arrived. Opening an OPEN game could therefore leave the screen in its pregame layout throughout play, hiding the replacement call panel and disabling Claim. Initial REST loading also depended on successfully connecting the live socket.

## Changes

- Always render the bounded current-number/recent-five-calls panel, including its waiting state before play. Full history remains in a bottom sheet.
- Load session, tickets, claims and call history before connecting the socket. Closed games load without requiring a live connection.
- Refresh the authoritative session snapshot every five seconds while the screen is active, including when socket connectivity fails. Stop polling on closure, pause, authentication expiry and disposal.
- Automatically close EARLIEST_SPLIT_V1 games when every configured pattern has an earliest winner, under the session row lock. LINE-only games stop on their winning LINE call. Games that explicitly include FULL_HOUSE continue until that configured pattern is complete.
- Keep existing earliest-completion eligibility and deterministic splitting of each fixed prize pool. Later-completing tickets remain ineligible even if the historical game continued to 75 calls.
- Correct the merged game-rules migration filename from V32 to V36. Its bytes match the existing production V36 exactly; all 36 migration files in the packaged backend match production. No schema changes were introduced by this release.

## Verification and rollout

- Flutter: 128 tests passed; five optional screenshot tests skipped. Focused Dart analysis passed. Layout checks cover growing call history, bottom-sheet history, narrow screens, large text, and English/Amharic.
- Backend: 31 focused tests passed against the isolated disposable MySQL test environment, including authenticated WebSocket replay, draw retry safety, earliest-winner/tie accounting, and automatic LINE-only closure followed by a successful closed-game claim.
- Updated and launched all six running Android emulators: Pixel 6, 7, 8, 8 Pro, 9, and 9 Pro XL. Installation retained existing app data and signing compatibility. Updated the booted iPhone SE simulator too.
- All installed apps target https://api.bingo.bakaroo.com.
- Production had no active games before the backend restart. The deployed JAR SHA-256 is `f1a679c9a22c8ef7ede2c7a142be704df03a96fd41fdc6e83c60b7fd89428c92`. The previous JAR was retained, and startup health passed with automatic rollback available on failure.
- Existing sessions and call history were preserved. No production claims, purchases, wallet adjustments or prize payments were performed for verification.

Production currently allows a 24-hour post-game claim window for these games. Payouts remain disabled by existing configuration; accepting/reviewing a claim and paying an approved prize are separate operations.

## Follow-up: repeat claims and focused play screen

The player requested hiding the empty call panel before play, superseding the waiting-panel behavior above. OPEN/DRAFT games now show the purchase summary and tickets. Once play starts, the call panel, expanding ticket grid and compact Claim controls fill the viewport. Details and claim history are available through the information button. Short viewports and large accessibility text retain scrolling as needed.

A repeat claim now opens a small localized “Your claim has already been submitted.” dialog with OK. Accepted responses are remembered independently of claim-history refreshes; duplicate API responses are handled as an explicit outcome rather than a generic error. Failed validations can still be retried after additional calls. The first accepted submission displays its confirmation snackbar.

Verification: 132 app tests passed (five optional screenshot tests skipped), including duplicate API responses, repeat taps without a second submission, pregame hiding, full-height play and details access. Focused static analysis passed. Phone/tablet layouts and the dialog were rendered with real fonts and visually inspected. No backend change or production financial mutation is required for this follow-up.

## Follow-up: server-confirmed winner eligibility

The six-player LINE session 10 did have a winner: ticket 9 completed the reverse diagonal on call 29 (number 73). The diagonal was 73, 58, FREE, 19, 2. Its existing claim was PENDING_REVIEW. The earlier UI did not explicitly announce this win and enabled Claim based on session state alone.

Ticket responses now include authenticated, server-computed `eligibleClaimPatterns`. The player enables Claim only when the selected ticket is eligible for the selected pattern and the claim window is open. It displays an explicit winning-ticket message. Eligibility refreshes when a live call arrives, with periodic API polling retained for recovery. Missing eligibility metadata defaults to disabled, rather than guessing a winner from marked cells.

The backend rejects unmatched and late-completing claims with a business error before saving a claim. An older API response that records a failed validation is also treated as a failure by the player instead of showing “Claim submitted.” Existing valid claims and prize rules are preserved; the LINE rule continues to include horizontal, vertical and diagonal lines.

Verification: 133 app tests passed (five optional screenshot tests skipped), and 45 backend/API tests passed. These cover disabled pre-win controls, positive winner eligibility, losing/late-ticket rejection without new claim rows, legacy failed-response handling, financial prize invariants, and the regenerated OpenAPI contract. Focused Dart analysis passed. No database migration is required.

## Follow-up: distinct ticket header and winner celebration

The BINGO header now uses navy with gold letters, distinct from both called-number and latest-number marking colors. Server-confirmed winners automatically see a navy/gold celebration dialog containing winning-ticket count, per-ticket winnings, exact totals, and a won/owned ticket count. OK stays pinned below the scrollable results so it remains accessible with large text and on short screens. Repeated API polling does not reopen an acknowledged celebration; genuinely new ticket/pattern wins can trigger another celebration.

The ticket API adds `winningPrizeAmounts` and `prizeCurrency`. Prize shares use the same server-side cent-allocation routine as financial settlement. The app aggregates integer minor units and includes winners beyond the selected ticket page. It does not estimate prize pools or assume every player receives the advertised full prize.

Verification: 138 app tests passed (five optional screenshot tests skipped); 22 focused backend/API tests passed, including exact 5.01/5.00 tie-share metadata for a 10.01 prize pool. Tests cover 3 winning tickets out of 5, correct 600 ETB aggregation, acknowledgment without repeat popups, winning tickets beyond page one, distinct header/marking colors, and large-text English/Amharic dialog controls. Static analysis passed. Rendered phone/tablet layouts and the celebration were visually inspected. No schema migration or production financial mutation is required.
