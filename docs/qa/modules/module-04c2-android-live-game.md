# Module 04C-2 — Android Live Game and Reconnection Verification

Disposition: **PARTIAL — core live Android and Admin draw flow passed; restart, network-recovery, and terminal-state UI cases remain unverified**

Scope: Module 04C-2 only. No Module 04D/04E work or production financial posting was started.

## Isolated environment and fixtures

The current-source Spring Boot runtime used the `test` profile on `127.0.0.1:8082`, with datasource and Flyway credentials from the guarded `.env.test` configuration. The active schema was verified as `bingo_qa_test`. The draw scheduler was disabled in this runtime for controlled Admin-triggered requests; server selection, due-time checks, transaction persistence, and broadcast remained enabled. Android used `http://10.0.2.2:8082`.

The guarded fixture setup created synthetic ADMIN user #371, PLAYER #372, room #35, and OPEN session #30. After a first run against a stale packaged backend, that runtime generated 45 unaudited scheduled rows in session #30; those rows stayed in `bingo_qa_test`. The current-source runtime produced one audited Admin draw at sequence 46 in that session. The [session #30 reconciliation](../evidence/module-04c2-session30-stale-reconciliation.txt) records that history. These rows were not used for the clean reconciliation below.

Session #31 (`QA Module 04C2 Clean Android Session`) was created in room #35 through the isolated Admin API and transitioned to STARTED. The Chromium Admin interface then requested two automatic draws without a request body. The player app opened session #31, observed both ordered events, and refreshed the authoritative history. The clean database reconciliation confirms session state DRAWING, draws at sequences 1–2, two distinct in-range values, two matching `BINGO_NUMBER_DRAWN` audit rows, and zero tickets, claims, entitlements, or player wallet transactions. See [clean session reconciliation](../evidence/module-04c2-clean-session-reconciliation.txt).

The app never purchased a ticket, submitted a claim, or invoked a draw mutation. The fixture contains no financial credit. The persistent `bingo_qa` snapshot taken before the run and the read-only snapshot after it compare byte-for-byte equal; see [before](../evidence/module-04c2-database-before.txt) and [after](../evidence/module-04c2-database-after.txt).

## Defect repaired

The real browser showed that the Admin session detail page requested `/api/v1/sessions/{id}`, a player-only endpoint. ADMIN users received an access error and the live draw controls did not appear. The Admin API helper now requests `/api/v1/admin/sessions/{id}`; an API regression asserts that path. The Admin page then loaded the isolated live session, sent bodyless draw requests, and displayed the server-selected results. Admin frontend tests passed 84/84, lint and build passed, and the targeted API regression passed 8/8.

The player lobby also lacked a production entry to `SessionDetailScreen`. The lobby now exposes a Live Game button for STARTED/DRAWING sessions. A widget regression verifies active-session navigation and the OPEN-state affordance. The real Android integration run on emulator-5554 passed on clean session #31: login, lobby/room navigation, live-screen entry, status and history display, ordered events, and refresh. Flutter tests passed 30/30 and `flutter analyze` reported no issues. The saved Android capture is a separate live-screen visual from session #30 (46 displayed draws) and is [here](../evidence/module-04c2-android-session30-live.png); the clean session #31 Android journey passed in the integration runner. Admin live, returned result, and clean-session captures are [here](../evidence/module-04c2-admin-live-session.png), [here](../evidence/module-04c2-admin-draw-result.png), and [here](../evidence/module-04c2-admin-clean-session.png).

## Retry and authorization findings

The Admin button is disabled while its mutation is pending, and the API helper sends no draw payload. The backend endpoint has no request identifier or idempotency key. The existing backend suite covers role denial and same-instant serialization; an HTTP retry arriving at a later due interval can request a distinct subsequent draw. Product/API owners still need to define retry behavior. No automatic client retry was found for the draw POST.

Player and agent draw attempts are denied by the existing backend role tests; SUPPORT and finance roles are also excluded, including combined roles. Caller-selected-number bodies are rejected by existing backend integration coverage. These are API-level checks; the Android client exposes no draw action or draw mutation method.

## Verification and remaining cases

The focused lifecycle/draw/WebSocket/OpenAPI results are in the [focused summary](../evidence/module-04c2-backend-focused-summary.txt), and the full backend regression is in the [full summary](../evidence/module-04c2-backend-full-summary.txt). The Android integration runner passed 1/1 on session #31; the full Flutter suite passed 30/30 and analyzer was clean. Admin frontend regression passed 84/84 with lint/build clean; Chromium exercised the actual draw UI twice on session #31, with zero-byte POST bodies and matching server/DB sequence values.

The network was not interrupted during the Android journey, the app process was not force-stopped and restored during the live session, and cancellation/completion was not driven through the UI. A15, A16, A14, and A19 therefore remain NOT RUN. Module 04C-2 is PARTIAL. The broader HTTP retry contract remains an open Product/API decision.

See the [A01–A20 matrix](module-04c2-test-matrix.md), [Module 04C report](module-04c-game-session-draws.md), and [Module 04C-1 report](module-04c1-draw-integrity.md). Module 04B remains PARTIAL, Module 02 remains INCOMPLETE, and Module 03 remains COMPLETE FOR DEFINED QA SCOPE.
