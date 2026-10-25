# Module 04A — Player Game Lobby and Room Discovery

## Disposition

**COMPLETE.** The player lobby, room detail/session display, and Android offline-to-online Retry recovery are verified on emulator-5554. The app-only network failure displayed the generic error; after independent emulator-to-backend connectivity confirmation, tapping Retry in the same app screen reloaded rooms and cleared the error. Existing room/session details remained accurate. G13, G23 and G24 pass in the [test matrix](module-04a-test-matrix.md). Flutter lobby regression is 6/6 and analyzer is clean; read-only persistent database snapshots match.

Module 04 overall remains INCOMPLETE. Modules 04B–04F were not started. No ticket purchase, reservation, prize settlement, wallet posting, or other financial mutation was performed.

## Existing model and behavior

The source of truth is the Module 02E game-administration implementation and current API/entity model. A `BingoRoom` has an active flag, name/description, ETB ticket price and currency, timezone/schedule rule, session capacity, and configured patterns/prizes. A `GameSession` belongs to a room and snapshots ticket price, currency, and capacity. Observed session states include DRAFT and terminal CANCELLED; the session enum also includes OPEN, STARTED, DRAWING, CLOSED, and SETTLED. Room schedule rules are displayed as stored; no recurrence, DST, or automated scheduling semantics were inferred.

The player lobby lists active rooms. Room details show the room identity, description, price, schedule rule and timezone, capacity, active status, patterns, and sessions. Session details show state, scheduled timestamp in UTC, price/currency, capacity, and patterns. CANCELLED, CLOSED, and SETTLED states are labeled “Not playable.” Historical sessions remain visible in the room’s session list with their state; the lobby does not label them as upcoming. The lobby has an empty state, refresh action, pull-to-refresh, and retry UI.

Player endpoints return dedicated safe response DTOs. Sensitive administrative prize configuration and ticket-limit/closing controls are omitted. There is no player-count/available-seat field, no listing pagination contract, and no reservation operation in the lobby. Accordingly, availability count and pagination were not claimed or synthesized. Session purchase remains reachable only through the separate live-game flow and is not linked from these read-only lobby details.

## Defects fixed

- Public room/session reads had insufficient player-only access control. Player lobby operations now require the PLAYER role; unauthenticated callers receive 401 and other roles receive 403.
- No player endpoint listed a room’s sessions. Added `GET /api/v1/rooms/{id}/sessions` and safe player room/session DTOs.
- Inactive rooms and sessions under inactive rooms could be fetched directly. Player detail and list operations now apply active-room visibility server-side and return controlled 404 responses for hidden/missing IDs.
- A suspended account’s still-valid JWT could pass the request filter because account lock state was not checked. The filter now requires an unlocked principal.
- The lobby’s prior placeholder did not provide usable discovery/details and could lead toward a live purchase flow. It now presents read-only room/session information without a purchase action.

Regression coverage includes role/authentication, suspended token, visibility, invalid IDs and sensitive-field checks in backend integration tests; lobby, terminal-state, empty-state and retry behavior in Flutter tests.

## Verification

| Area | Result |
| --- | --- |
| Focused backend integration | 15/15 passed, 0 failures/errors/skips, `bingo_qa_test` |
| Full backend suite | 146/146 passed, 0 failures/errors/skips, `bingo_qa_test` |
| Flutter analyzer | Clean, no issues |
| Flutter unit/widget suite | 26/26 passed |
| Android room/detail/session journey | Passed on emulator-5554; real QA API at `10.0.2.2:8081`; read-only session details and refresh shown |
| Android process restart/session restore | Saved player session restored without clearing app data |
| Android network recovery | Offline error captured; restored emulator route independently confirmed; actual Retry recovered lobby and room/session details in the same process (G13 PASS; post-failure refresh now shows a non-actionable loading state) |
| Persistent database | SELECT-only before/after snapshots match; 33 rooms, 18 sessions, zero tickets/draws/claims/entitlements, 19 wallet transactions |

No new persistent fixture was created. Player #181, room #6 and the associated existing session were reused. The QA browsing flow did not create tickets or wallet transactions. `bingo_qa` received only SELECT queries for reconciliation; backend destructive/integration tests used the guarded `bingo_qa_test` database. Ordinary authentication telemetry may change with login and was not treated as game/financial state.

## Missing product requirements and remaining verification

- The product has no contract for lobby pagination or player availability/seat counts; those values are not exposed by the current API.
- Session recurrence/timezone/DST execution semantics and what qualifies as an “upcoming” session are not specified. This UI reports stored schedule values and terminal states without inventing a scheduler.
- No remaining Module 04A verification blocker. A duplicate Retry availability defect during refresh was fixed by showing loading instead of the stale error state; regression G24 covers it. Retry, refresh and room/session discovery remain read-only. The lobby provider does not supply a Dio cancellation token if disposed mid-request; cancellation is not exposed by this screen and no failure was reproduced.

## Evidence

- [Test matrix](module-04a-test-matrix.md)
- [Android room/session detail screenshot](../evidence/module-04a-room-session-details.png)
- [Android network error screenshot](../evidence/module-04a-network-error-retry.png)
- [Android results](../evidence/module-04a-android-results.txt)
- [Backend results](../evidence/module-04a-backend-results.txt)
- [Flutter results](../evidence/module-04a-flutter-results.txt)
- [Read-only reconciliation SQL](../evidence/module-04a-database-readonly.sql), [before](../evidence/module-04a-database-before.txt), and [after](../evidence/module-04a-database-after.txt)

- [Module 04A-R Android network recovery report](module-04a-r-network-recovery.md)
- [Offline error](../evidence/module-04a-r-offline-error.png), [Retry pending/loading](../evidence/module-04a-r-retry-pending.png), [recovered lobby](../evidence/module-04a-r-recovered-lobby.png), and [recovered room/session](../evidence/module-04a-r-recovered-room-session.png)
- [Module 04A-R Android results](../evidence/module-04a-r-android-results.txt), [Flutter results](../evidence/module-04a-r-flutter-results.txt), and [database reconciliation](../evidence/module-04a-r-database-after.txt)



