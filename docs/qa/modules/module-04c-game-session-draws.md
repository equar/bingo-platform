# Module 04C — Game Session Lifecycle and Number Draw Engine

Disposition: **PARTIAL — lifecycle/draw behavior and Android recovery verified; historical-data preservation deviation remains**
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

Module 04C-2 created a dedicated isolated `bingo_qa_test` session and completed a real Android journey on emulator-5554. The Admin browser triggered two server-selected draws on a clean isolated session; MySQL sequence and audit rows reconcile. The initial 04C-3 attempt stopped at the environment gate, which 04C-3A subsequently resolved. In 04C-3B, Android force-stop/relaunch, offline error and same-process network recovery, CLOSED terminal, and CANCELLED terminal scenarios all passed. The pre-fixture read-only check did not find the historical #30/#31 rows; the first new fixtures consequently received those IDs. The collision was disclosed, and the new rows have been left untouched, but the requested historical DB-preservation condition could not be satisfied. See the [04C-2 report](module-04c2-android-live-game.md), [04C-3B report](module-04c3b-live-game-verification.md), [A01–A20 matrix](module-04c2-test-matrix.md), [04C-3B evidence matrix](module-04c3b-test-matrix.md), and [isolated DB reconciliation](../evidence/module-04c3b-isolated-database-after.txt). Persistent `bingo_qa` before/after snapshots for 04C-3B are byte-identical.

## Verification

- Focused MySQL backend lifecycle/draw/WebSocket suite: **30/30 passed** on local guarded `bingo_qa_test`; see [summary](../evidence/module-04c-backend-focused-summary.txt) and sanitized [log](../evidence/module-04c-backend-focused.log).
- Module 04C-1 final focused suite, including the OpenAPI contract: **32/32 passed**; full backend regression: **159/159 passed** on local guarded `bingo_qa_test`. See the [04C-1 focused summary](../evidence/module-04c1-backend-focused-summary.txt), [full summary](../evidence/module-04c1-backend-full-summary.txt), and sanitized [log](../evidence/module-04c1-backend-full.log).
- Admin frontend regression: **84/84 passed**, lint and build passed. See the [04C-1 admin summary](../evidence/module-04c1-admin-summary.txt).
- Flutter unit/widget suite: **29/29 passed**; analyzer clean. See [summary](../evidence/module-04c-flutter-summary.txt).
- Android recovery and terminal journey: **five Android flows passed** in 04C-3B (A14–A16, CLOSED, CANCELLED); historical-row preservation deviation remains.
- Persistent `bingo_qa`: the before/after SELECT-only snapshots are compared in [database reconciliation](../evidence/module-04c-database-readonly.txt); SQL is [here](../evidence/module-04c-database-readonly.sql).

Module 04C-2 Android, Admin browser, and clean-session results are in the [04C-2 report](module-04c2-android-live-game.md) and [A01–A20 matrix](module-04c2-test-matrix.md). Module 04C remains PARTIAL because the historical session rows #30/#31 were absent before 04C-3B and the first fixtures received those IDs; the separate Admin draw retry/idempotency question also remains open. Supported Android recovery and terminal cases A14–A16/A19 pass in [04C-3B](module-04c3b-live-game-verification.md). See the full [L01–L12 and D01–D16 matrix](module-04c-test-matrix.md) and the [04C-1 report](module-04c1-draw-integrity.md) with its [I01–I20 matrix](module-04c1-test-matrix.md). Module 04B remains PARTIAL, Module 02 remains INCOMPLETE, and Module 03 remains COMPLETE FOR DEFINED QA SCOPE.

The initial 04C-3 attempt did not run backend, Flutter, Android, or database tests because it missed the guarded test datasource and identifiable isolated runtime; no mutation occurred in that initial attempt. 04C-3B later ran supported scenarios. The initial gate and later recovery are recorded in the [04C-3 report](module-04c3-live-game-recovery.md), [04C-3 matrix](module-04c3-test-matrix.md), and [04C-3B matrix](module-04c3b-test-matrix.md). Module 04B remains PARTIAL, Module 02 remains INCOMPLETE, and Module 03 remains COMPLETE FOR DEFINED QA SCOPE.

The separate 04C-3A task restored the test backend; 04C-3B then passed A14–A16/A19 on Android and confirmed the persistent schema remained byte-identical. The pre-run absence of historical session rows #30/#31 means the overall module remains PARTIAL. See [04C-3A report](module-04c3a-environment-recovery.md) and [04C-3B report](module-04c3b-live-game-verification.md).
