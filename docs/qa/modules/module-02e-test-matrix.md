# Module 02E — Game administration test matrix

Status key: PASS means executed and passed; PARTIAL means only the stated portion was exercised; BLOCKED means a prerequisite policy/fixture is absent; NOT RUN means not executed. Browser coverage is real Chromium against the local QA app/backend; integration coverage uses only `bingo_qa_test`.

## Rooms and sessions

| ID | Scenario | Result | Evidence/notes |
| --- | --- | --- | --- |
| G01 | Create room with valid configuration | PASS | Chromium, response and persistent refresh |
| G02 | Anonymous room access/mutation | PASS | 401 on read and write |
| G03 | Edit room configuration | PASS | Chromium; price and updated version persisted |
| G04 | Reject invalid/minor-unit precision | PASS | Invalid zero price rejected; backend verifies sub-cent precision |
| G05 | Stale room version | PASS | 409 `ROOM_VERSION_CONFLICT`; latest config preserved |
| G06 | Concurrent room edits | PASS | Backend integration; optimistic conflict prevents silent overwrite |
| G07 | Room audit and refresh persistence | PASS | Browser audit query and reload |
| G08 | Inactive room cannot be scheduled | PASS | API returns 409 `ROOM_INACTIVE`; no session created |
| G09 | Duplicate same-room/schedule creation race | PASS | Backend integration; one insert and one 409 conflict |
| G10 | Historic session snapshots survive room edits | PASS | Backend integration |
| S01 | Required session fields | PASS | Backend rejects missing name/start time |
| S02 | Create DRAFT session through admin UI | PASS | Real Chromium; response and refresh |
| S03 | Initial state guard | PASS | Only DRAFT/OPEN are allowed; invalid operational state rejected |
| S04 | Rule/pricing snapshot creation | PASS | DRAFT response included expected ticket price, currency and pattern snapshot |
| S05 | Schedule grammar, recurrence, timezone/DST and past-time policy | BLOCKED | Approved interpretation is not documented; only future synthetic timestamps used |
| S06 | Session state transitions | PARTIAL | Ticket-free DRAFT → CANCELLED via browser; backend validates transitions. Start/draw/finish not exercised |
| S07 | Unauthorized session/room operations | PASS | Anonymous browser/API denied; backend verifies non-admin state changes denied |
| S08 | Session persistence after refresh | PASS | Browser reload retains DRAFT state |
| S09 | Cancel ticket-free DRAFT | PASS | Browser response, audit and refresh |
| S10 | Cancel session with tickets and refund/void handling | NOT RUN | No approved cancellation/refund policy or safe fixture |
| S11 | Duplicate session schedule | PASS | Database unique constraint plus service-level conflict translation |
| S12 | Concurrent creation race | PASS | Backend integration; one persisted session |

## Tickets, draws, claims and prizes

| ID | Scenario | Result | Evidence/notes |
| --- | --- | --- | --- |
| T01 | Session ticket price/capacity/pattern configuration | PARTIAL | Session snapshot creation checked; no QA ticket purchase in browser |
| T02 | Ticket sale limits and lifecycle enforcement | PARTIAL | Existing backend regressions cover capacity/state; no real QA sale flow in this run |
| T03 | Number draw and operational controls | NOT RUN | No persistent draw was made; gameplay/draw verification belongs to approved gameplay scope |
| T04 | Winner and prize calculation | BLOCKED | No approved payout/prize calculation policy; QA has no ticket/draw fixture |
| T05 | Claim submission and server verification | PARTIAL | Isolated integration test covers server-rejected claim; QA DB has no claims |
| T06 | Admin claim list/detail/approve/reject browser workflow | NOT RUN | No safe persisted claim fixture; browser workflow remains unverified |
| T07 | Claim duplicate/review protections | PASS | Backend integration checks duplicate review and disallows overriding server rejection |
| T08 | Prize entitlement creation | PARTIAL | Valid claim/entitlement path exists in isolated backend test; no entitlement was written to persistent QA |
| T09 | Prize settlement | BLOCKED | UI disabled; API returns 409 `PRIZE_SETTLEMENT_BLOCKED`; no wallet credit; finance controls not approved |
| T10 | Money precision | PASS | Room, session and prize amount validation enforces two decimal places |
| T11 | Auditability | PARTIAL | Room/session browser audit checked; claim/settlement audit browser flow is not run |

## Execution summary

Chromium final result: **6/6 passed** against the healthy QA app/backend, with no page or console errors. An earlier run had 5 passing tests and one transient login failure during a QA backend restart (`ERR_CONNECTION_REFUSED`); that run was superseded. See [final Chromium log](../evidence/module-02e-chromium-final.txt) and the four screenshots in the evidence directory.

Backend full-suite result: **125/125 passed, 0 failures, 0 errors, 0 skipped** against `bingo_qa_test`. The focused Flyway/game regression run also passes after duplicate-key translation recognizes both the historical `uk_bingo_sessions_room_start` and current `uk_bingo_sessions_room_schedule` constraint names.

Frontend: 78/78 unit tests passed; lint and production build passed after Module 02E UI changes. Flutter/Android: not run because no mobile code or mobile behavior changed in this module.

Persistent QA DB verification used SELECT statements only: schema V22; duplicate room/schedule groups 0; win claims, entitlements, tickets and draws 0; synthetic game room/session records are retained. Agent #3 remains APPROVED with ETB 51.00 available, ETB 0.00 clearing, two float transactions and two journals. See [database evidence](../evidence/module-02e-database-verify.txt) and [test-data registry](../test-data-registry.md).

See [Module 02E report](module-02e-game-admin.md) for implementation details, limits and module disposition.
