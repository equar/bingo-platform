# Module 04C-3B — Android Live-Game Recovery and Terminal-State Verification

Disposition: **PARTIAL — requested Android scenarios pass; historical-session preservation precondition failed before fixture creation**

Scope: 04C-3B only. No 04D/04E work, ticket purchases, settlement, wallet posting, or production financial posting.

## Environment and isolation

Revalidated the local current-source backend before any C3B mutation: PID 32844, JAR SHA-256 `8FA15AC5267FC5038A03FFF699798F05663EF14B9E2EA07B14ABFEE2885DAA9B`, HEAD `2f8a0f9e8c26db1cd5bd5a8ee55c88a187d8ae26`, Spring `test`, local MySQL `bingo_qa_test`, Flyway V22, scheduler disabled, and all financial simulation/posting flags disabled. MySQL grants were schema-scoped. Emulator `emulator-5554` used `http://10.0.2.2:8082`; its health GET returned HTTP 200. Flutter integration runs explicitly targeted that URL and synthetic C3B credentials. The persistent app secrets/config pointing to port 8081 were not used.

Before fixture creation, the read-only test-schema check found V22, two users, zero rooms/sessions/draws/tickets/wallet transactions, one audit row, one wallet, and no claims or entitlements. Sessions #30 and #31 did not exist in the database, but the auto-increment sequence had advanced to those values. First inserts therefore received #30 and #31. This preservation conflict was disclosed immediately; these new rows were not subsequently changed. Prior C2 evidence files remain present, but this run cannot claim the original #30/#31 database rows were preserved. The full pre-check note is [here](../evidence/module-04c3b-isolated-database-before.txt).

Fixtures were created through authorized application/test workflows: synthetic users and rooms, session start transitions, then one server-selected draw per session. IDs #30/#31 (room #35) were the colliding initial inserts. Terminal sessions #32/#33 (room #35) and fresh Android sessions #34/#35 (room #36) were used for the scenarios. All are left as-is. No SQL state edits were made.

## Android scenario results

All scenarios ran on the connected Android emulator and the app’s actual Flutter UI. Each session history showed one persisted sequence and number, and the ticket purchase action was absent.

| Scenario | Result | Evidence |
| --- | --- | --- |
| A14 — force-stop and relaunch | PASS | On session #34, the first run logged in and opened the live screen; ADB force-stopped only the app process (no data clear). Relaunch restored stored auth without login and loaded the same draw count/history. [Before](../evidence/module-04c3b-restart-before.png) · [after](../evidence/module-04c3b-restart-after.png) |
| A15 — offline error and cached draw history | PASS | On session #35, airplane mode interrupted connectivity while the app process remained live. The UI displayed a real `WebSocketChannelException` / `SocketException` and `Reconnecting...`, with `DRAWING`, one draw, and the same called number visible. [Offline screen](../evidence/module-04c3b-network-offline.png) |
| A16 — same-process reconnection | PASS | Restored emulator Wi-Fi/data, verified backend health via emulator raw GET, then the integration driver refreshed in the same process. Connection returned to `Live`; count/history remained one. [Recovered screen](../evidence/module-04c3b-network-recovered.png) |
| A19a — supported completion terminal (`CLOSED`) | PASS for supported state | App first opened session #32 in `DRAWING`; Admin API transitioned it to `CLOSED`; subsequent bodyless Admin draw POST returned HTTP 409; player refresh displayed terminal `CLOSED`/`Finished` and retained its draw. [Screenshot](../evidence/module-04c3b-terminal-closed.png) |
| A19b — cancellation terminal | PASS | App first opened session #33 in `DRAWING`; Admin API transitioned it to `CANCELLED`; subsequent bodyless Admin draw POST returned HTTP 409; player refresh displayed terminal `CANCELLED`/`Finished` and retained its draw. [Screenshot](../evidence/module-04c3b-terminal-cancelled.png) |

The product has no separate `COMPLETED` enum/state. `CLOSED` is the supported closure/completion terminal in this lifecycle; a distinct `COMPLETED` state cannot be tested without a product/backend capability change. `CANCELLED` is independently supported and passed.

The focused fixture authorization check returned 403 for PLAYER, AGENT, SUPPORT, and FINANCE roles on a live session. Focused backend lifecycle/fixture tests passed 3/3. Flutter game tests passed 11/11 and analyzer reported no issues. The first driver attempt exposed a viewport issue for the live-session button; the driver was corrected to scroll it into view. The first network assertion expected a `Disconnected` label, while the app showed `Reconnecting...` alongside the actual socket exception. The assertion was corrected and the complete offline/recovery scenario passed. No product code change was needed for those driver corrections. Marker and test summaries are recorded in [Android results](../evidence/module-04c3b-android-results.txt).

## Database and financial reconciliation

Read-only after snapshot: V22; 7 users; 2 synthetic rooms; 6 sessions; 6 draws; 6 matching `BINGO_NUMBER_DRAWN` audit records; zero tickets; zero claims; zero entitlements; zero wallet transactions. Each session #30–#35 has exactly one draw at sequence 1 and one matching draw audit row. The four C3B Admin role-denial results were HTTP 403; the two authorized post-terminal draw attempts were HTTP 409 and did not add draws. Details: [isolated before](../evidence/module-04c3b-isolated-database-before.txt), [isolated after](../evidence/module-04c3b-isolated-database-after.txt).

Persistent `bingo_qa` was queried with the existing SELECT-only reconciliation script. The before/after outputs are byte-identical (15,081 bytes each): V22; 33 rooms; 18 sessions; zero tickets, draws, claims, entitlements; 19 wallet transactions; existing journal counts and Agent #3 float unchanged. [Before](../evidence/module-04c3b-persistent-database-before.txt) · [after](../evidence/module-04c3b-persistent-database-after.txt) · [SQL](../evidence/module-04c-database-readonly.sql).

## Open issue and disposition

The Android recovery and supported terminal-state scenarios passed. However, because the pre-run database already lacked historical #30/#31 rows and the first fixtures received those IDs from the advanced sequence, this run fails the requested preservation precondition. The two new records are retained, not rewritten or removed. The original evidence files are retained, and the conflict is recorded in the test-data registry. Module 04C and this module remain **PARTIAL**; do not represent session IDs #30/#31 in this run as the historical C2 records.

Module 04B remains PARTIAL, Module 02 remains INCOMPLETE, and Module 03 remains COMPLETE FOR DEFINED QA SCOPE. No commit or push was made.
