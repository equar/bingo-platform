# Module 04C — Game Session Lifecycle and Number Draw Engine

Disposition: **PARTIAL — lifecycle/draw behavior and automatic admin trigger verified; Android live journey remains blocked**  
Scope: Module 04C only. Module 04D claims and Module 04E settlement were not started. No production financial posting was enabled.

## Existing lifecycle and rules

The persisted state machine is `DRAFT → OPEN → STARTED → DRAWING → CLOSED → SETTLED`; `CANCELLED` is reachable from DRAFT, OPEN, STARTED, DRAWING, or CLOSED. `SETTLED` and `CANCELLED` are terminal. There are no PAUSED or RESUMED states or endpoints. Session start is an authorized admin state update; `startedAt` is recorded on the server. A repeated `STARTED` transition is currently rejected as `INVALID_SESSION_STATE_TRANSITION`; no API retry/idempotency contract for that PUT was found.

The supported live engine is automatic. `GameLiveDrawScheduler` runs at `app.game.draw-interval` (default 10 seconds) and checks due `STARTED`/`DRAWING` sessions. On startup, recovery checks the same persisted sessions. The server builds the 1–75 pool, excludes persisted prior numbers, selects from remaining values with `SecureRandom`, records sequence/session/operation ID/server timestamp, transitions STARTED to DRAWING, and closes the session after the 75th number. The session row is pessimistically locked and draw rows have unique constraints for session/sequence, session/number, and operation ID. Broadcast events are delivered after transaction commit; REST history is ordered by sequence and can be replayed after a sequence.

## Defects, security, and fairness findings

No runtime defect was reproduced in the automatic scheduler's range, uniqueness, sequencing, locking, transaction, or recovery paths. The implementation work in this module adds regression coverage around lifecycle authorization, rollback, cancellation, recovery, and persistence; it does not change game rules or financial behavior.

Added regression coverage for admin-only session start and draw operations, unauthorized player/agent/anonymous attempts, successful lifecycle audit entries, rejected repeated start, cancellation and invalid-session draw rejection, and persisted draw range/session/timestamp properties. Existing integration cases cover competing draw workers, complete 75-number exhaustion without duplicates, ordered REST history, and WebSocket authorization/broadcast.

Module 04C-1 removed client-selected numbers from the admin draw route and screen. The bodyless `POST /api/v1/admin/sessions/{id}/draws` requests one server-generated draw only when the session is due; legacy bodies are rejected. The same `SecureRandom` pool selection, session lock, interval, transaction, and after-commit broadcast protect scheduler and admin-triggered draws. Admin triggers reject SUPPORT and finance roles even when those roles are combined with ADMIN. Every committed draw now has a `BINGO_NUMBER_DRAWN` audit row with session target, operation ID, sequence, number, and source; admin-triggered events also retain the operator as actor. Concurrent same-instant triggers produce one draw. Independent fairness/regulatory certification is not claimed.

No new pause/resume behavior was added because the state model has no pause/resume states. Same-state start retry semantics remain undefined. Ticket purchase/claims/settlement are outside this module; no persistent ticket, draw, claim, entitlement, wallet, or journal was created.

## Player live-game coverage

The Flutter live screen already refreshes the session snapshot and draw history from the authenticated API, subscribes to authenticated STOMP draw events, orders incoming events by sequence, ignores duplicate sequences, retrieves missing history when a sequence gap appears, refreshes after reconnect/resume, and displays session status and called numbers. Its transport has no draw mutation method. Unit/widget tests passed, but an Android live-session journey was not possible: persistent `bingo_qa` has no OPEN/STARTED/DRAWING session and the QA backend is not listening on port 8081. No DRAFT or historical session was changed to manufacture a live game. The emulator is connected as `emulator-5554`; Android live E2E is BLOCKED pending an eligible QA session and an available QA backend. See [Android results](../evidence/module-04c-android-results.txt).

## Verification

- Focused MySQL backend lifecycle/draw/WebSocket suite: **30/30 passed** on local guarded `bingo_qa_test`; see [summary](../evidence/module-04c-backend-focused-summary.txt) and sanitized [log](../evidence/module-04c-backend-focused.log).
- Module 04C-1 final focused suite, including the OpenAPI contract: **32/32 passed**; full backend regression: **159/159 passed** on local guarded `bingo_qa_test`. See the [04C-1 focused summary](../evidence/module-04c1-backend-focused-summary.txt), [full summary](../evidence/module-04c1-backend-full-summary.txt), and sanitized [log](../evidence/module-04c1-backend-full.log).
- Admin frontend regression: **84/84 passed**, lint and build passed. See the [04C-1 admin summary](../evidence/module-04c1-admin-summary.txt).
- Flutter unit/widget suite: **29/29 passed**; analyzer clean. See [summary](../evidence/module-04c-flutter-summary.txt).
- Android live-game journey: **BLOCKED**, as described above; no journey was fabricated.
- Persistent `bingo_qa`: the before/after SELECT-only snapshots are compared in [database reconciliation](../evidence/module-04c-database-readonly.txt); SQL is [here](../evidence/module-04c-database-readonly.sql).

See the full [L01–L12 and D01–D16 matrix](module-04c-test-matrix.md) and the [04C-1 report](module-04c1-draw-integrity.md) with its [I01–I20 matrix](module-04c1-test-matrix.md). Module 04C remains PARTIAL because a live Android session could not be tested. Module 04B remains PARTIAL, Module 02 remains INCOMPLETE, and Module 03 remains COMPLETE FOR DEFINED QA SCOPE.
