# Module 04A — Player Lobby Test Matrix

Scope: real Android player lobby and read-only room/session discovery. `BLOCKED` denotes an incomplete mandatory verification, not a confirmed production defect. Backend integration and Flutter tests use their recorded isolated or local test setup; persistent reconciliation is SELECT-only.

| ID | Requirement | Expected result | Actual result | Android evidence | Backend evidence | Database evidence | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| G01 | Authenticated player opens lobby | PLAYER session reaches lobby | Player #181 session restored and lobby opened on emulator | Detail screenshot; Android results | 15 focused / 146 full tests | Existing player #181 retained | PASS |
| G02 | Available rooms load | Active rooms load from QA API | 25 active rooms returned; room #6 available | Room journey screenshot | Room discovery integration | `ROOMS` rows in before/after | PASS |
| G03 | Room name/details | Room identity and stored details match server | Room #6 identity, description, price, schedule, capacity, patterns displayed | `module-04a-room-session-details.png` | Safe room DTO/API integration | Room #6 row before/after | PASS |
| G04 | Room status | Displayed status reflects active flag | Active label shown; inactive rooms hidden | Room journey | Inactive room coverage | Inactive room rows preserved | PASS |
| G05 | Available sessions | Associated sessions load and state is visible | Room #6 DRAFT session shown with its stored state | Room/session screenshot | Session listing integration | Session rows before/after | PASS |
| G06 | Session schedule | Stored timestamp displayed accurately | Session instant displayed in UTC; DTO timestamp matched DB | Room/session screenshot | Schedule DTO integration | Session timestamp before/after | PASS |
| G07 | Ticket price | Display stored price/currency | ETB 3.25 shown for room/session #6 | Room/session screenshot | API DTO checks | Price snapshots before/after | PASS |
| G08 | Room capacity if supported | Show supported capacity without inventing availability | Capacity 10 shown; player-count/remaining-seat value is not supported by API | Room/session screenshot | DTO/schema checks | Capacity before/after | PASS |
| G09 | Inactive visibility policy | Inactive rooms and child sessions inaccessible | Inactive room excluded; inactive-parent direct session lookup returns controlled 404 | — | Focused security/visibility integration | Inactive rows retained | PASS |
| G10 | Cancelled session not playable | State is visibly terminal and no purchase is offered | CANCELLED is labeled “Not playable”; no purchase control | — | Session state/API coverage | Existing session state retained | PASS |
| G11 | Completed session not upcoming | CLOSED/SETTLED not represented as playable/upcoming | CLOSED and SETTLED now have widget regression coverage and terminal label; no upcoming-only feed exists | — | Session state coverage | Existing rows unchanged | PASS |
| G12 | Empty lobby | Helpful empty state appears | “No rooms available” verified in widget test | — | — | Empty state requires no DB fixture | PASS |
| G13 | Network failure and retry | Error/retry appears offline; successful retry after recovery | Android app-only network denial produced the offline error; after restoring connectivity, tapping Retry in the same live screen recovered lobby data and cleared the error/loading state. App PID unchanged | `module-04a-r-offline-error.png`; `module-04a-r-recovered-lobby.png` | Flutter lobby regression 6/6 (including pending duplicate-request guard) | SELECT-only before/after identical; no game/financial writes | PASS |
| G14 | Refresh retrieves current data | Refresh reissues server reads | Refresh action exercised during room-detail Android journey; list refresh is wired to invalidate provider | Android journey screenshot/results | Provider/API integration | Same room/session totals before/after | PASS |
| G15 | App restart restores lobby state | Force-stop without clearing data preserves authenticated session and lobby access | Existing saved session restored on emulator; lobby opened | Android results | Session/auth integration coverage | Player #181 unchanged | PASS |
| G16 | Unauthorized game data | Anonymous callers denied | 401 for unauthenticated player routes | — | Focused role/security integration | No game writes | PASS |
| G17 | Suspended player | Suspended account token denied | Request filter denies locked principal | — | Focused suspended-token regression | No user status changes | PASS |
| G18 | Agent cross-role access | Agent cannot use player-only lobby operations | 403 for AGENT credentials | — | Focused role/security integration | No game writes | PASS |
| G19 | Invalid room IDs | Controlled error without internal details | Missing room/session IDs return controlled 404/error code, no trace leakage | — | Focused invalid-ID integration | No database mutation | PASS |
| G20 | API data equals MySQL | Room/session values reconcile | Display/API values for room #6 and session #5 match SELECT snapshot | Screenshot | Focused API/database integration | Before/after SQL outputs | PASS |
| G21 | Browsing creates no ticket/wallet transaction | Read-only flow has no financial/game mutation | No purchase path used; counts remain zero tickets and 19 wallet transactions | Android read-only detail flow | Integration mutation-count assertion | Counts unchanged | PASS |
| G22 | Existing QA records remain unchanged | Preserve rooms, sessions, players, wallets and float records | Counts, player #178–181 wallets, and Agent #3 float/journal counts match | — | No test writes target persistent QA | Before/after SQL snapshots match | PASS |

## Unsupported capability notes

Room/session list endpoints do not implement pagination, so pagination behavior is not applicable to the current contract. The API does not expose occupancy or available seats; only configured session capacity is displayed. No empty-room persistent fixture was created; empty state was tested with a Flutter repository test double.


| G23 | Retry recovery details and session continuity | Recovered list and details remain accurate; authentication persists | Fixed APK recovered the lobby; opened existing room and DRAFT session with stored values; same authenticated process | `module-04a-r-recovered-room-session.png` | Flutter lobby tests 6/6; analyzer clean | Snapshot identical | PASS |



| G24 | Repeated retry while request pending | Pending retry cannot enqueue duplicate lobby loads or corrupt UI | Confirmed initial duplicate-action defect in widget regression; fixed loading state hides Retry until completion; test confirms one request and successful data | module-04a-r-retry-pending.png | Pending retry regression passes | Snapshot identical | PASS |

