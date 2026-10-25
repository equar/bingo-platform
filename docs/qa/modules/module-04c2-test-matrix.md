# Module 04C-2 — Test Matrix

Test profile used a local-only backend on port 8082 connected to `bingo_qa_test`. No persistent QA game or financial records were changed.

| ID | Scenario | Result | Evidence / notes |
| --- | --- | --- | --- |
| A01 | Player logs in on Android | PASS | Real emulator integration run, session #31 |
| A02 | Player opens game lobby | PASS | Real emulator integration run |
| A03 | Eligible session is visible | PASS | Session #31 was STARTED/DRAWING in room #35 |
| A04 | Player opens live-game screen | PASS | Lobby entry added for STARTED/DRAWING; integration passed |
| A05 | Current session status is accurate | PASS | Android displayed DRAWING |
| A06 | Initial draw history loads | PASS | Two persisted draws loaded on session #31 |
| A07 | Authorized Admin triggers automatic draw | PASS | Chromium Admin UI triggered both draws |
| A08 | Server chooses the number | PASS | Bodyless Admin POST; response displayed server result |
| A09 | Android displays the drawn number | PASS | Android screen displayed the ordered server history |
| A10 | Additional draw updates history | PASS | Sequence 2 appeared after the second Admin draw |
| A11 | Draw order is correct | PASS | Android and MySQL sequences 1, 2 |
| A12 | No duplicate number appears | PASS | MySQL: 2 distinct values, no out-of-range values |
| A13 | Refresh retrieves authoritative history | PASS | Android refresh and post-refresh count passed |
| A14 | App restart restores history | PASS in 04C-3B | Force-stop/relaunch without clearing storage restored the player login and one-draw history on isolated session #34. See [04C-3B report](module-04c3b-live-game-verification.md). |
| A15 | Network interruption displays controlled state | PASS in 04C-3B | Real emulator offline run showed SocketException and Reconnecting while preserving DRAWING and draw history on session #35. |
| A16 | Network restoration recovers history | PASS in 04C-3B | Same-process connectivity restoration and refresh returned to Live with the same history. |
| A17 | Player cannot trigger draws | PASS | Existing backend role integration test denies PLAYER |
| A18 | Player cannot submit a chosen number | PASS | Body-bearing legacy request rejected; no app draw mutation exists |
| A19 | Cancellation/completion updates Android UI | PASS for supported terminals in 04C-3B | CLOSED and CANCELLED refreshed to terminal UI and blocked further draws (HTTP 409). There is no distinct COMPLETED state; CLOSED is the supported completion/closure state. |
| A20 | No unauthorized financial transaction occurs | PASS | Session #31: zero tickets, claims, entitlements, and player wallet transactions |

## Admin and database checks

| Check | Result | Evidence / notes |
| --- | --- | --- |
| Authorized Admin starts/opens isolated session | PASS | Session #31 created/started in `bingo_qa_test`; live Admin view loaded |
| Draw POST has no number-selection payload | PASS | Chromium request observer recorded zero body bytes twice |
| Server result appears in Admin UI | PASS | Both Admin results appeared at sequences 1 and 2 |
| Draw button pending protection | PASS — component coverage | Existing Admin component regression verifies disabled pending state; timing window not separately observed in Chromium |
| History equals MySQL | PASS | Two ordered draw rows and two matching draw audit rows |
| Unauthorized role cannot draw | PASS — backend coverage | Existing integration matrix covers anonymous/player/agent/support/finance roles |
| Persistent `bingo_qa` unchanged | PASS | Read-only before/after snapshots are identical |
| Later transport retry may draw again | PARTIAL / OPEN | No request identifier or idempotency contract; a later due retry is a distinct command |

## Final counts

- Backend focused lifecycle/draw/WebSocket/OpenAPI: see [focused summary](../evidence/module-04c2-backend-focused-summary.txt).
- Backend full suite: see [full summary](../evidence/module-04c2-backend-full-summary.txt).
- Android integration: 1/1 passed on emulator-5554.
- Flutter unit/widget suite: 30/30 passed; analyzer clean.
- Admin frontend: 84/84 passed; lint and build passed.
- Admin Chromium: two bodyless Admin draws on clean session #31.

Module 04C-2's original scope remains PARTIAL because it did not include recovery; those shared A14–A16 and A19 cases subsequently passed in 04C-3B. Module 04C overall remains PARTIAL because the pre-run historical #30/#31 DB rows were already absent and the first 04C-3B fixtures received those IDs; see the [04C-3B report](module-04c3b-live-game-verification.md).
