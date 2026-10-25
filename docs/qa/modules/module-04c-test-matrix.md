# Module 04C — Test Matrix

## Session lifecycle

| ID | Scenario | Result | Notes |
| --- | --- | --- | --- |
| L01 | Create a valid session in isolated QA | PASS | MySQL integration fixtures create room/session rows on `bingo_qa_test` |
| L02 | DRAFT session cannot draw | PASS | State guard exercised in lifecycle regression; no draw row is persisted |
| L03 | Authorized admin starts a session | PASS | HTTP state update records server `startedAt` |
| L04 | Unauthorized session start denied | PASS | Anonymous 401; player and agent 403 |
| L05 | Invalid state transition denied | PASS | Existing lifecycle transition regression returns conflict |
| L06 | Session start retry is idempotent where required | BLOCKED | Current repeated STARTED PUT is rejected; no explicit Product/API retry contract was found |
| L07 | Pause follows existing design | N/A | No PAUSED state or pause operation exists |
| L08 | Resume follows existing design | N/A | No PAUSED state or resume operation exists |
| L09 | Cancellation prevents further draws | PASS | Draw scheduler returns empty and persists no row after cancellation |
| L10 | Completed session rejects further draws | PASS | 75th draw closes session; later scheduler attempt persists nothing |
| L11 | Session restart/recovery preserves authoritative history | PASS | Startup recovery hook advances a persisted STARTED session; ordered REST history replay verified |
| L12 | Lifecycle audit events are correct | PASS | Lifecycle transitions and scheduler/admin-automatic draws have integration coverage for audit persistence; failed operations do not create draw audit rows |

## Number draw engine

| ID | Scenario | Result | Notes |
| --- | --- | --- | --- |
| D01 | Draw returns a valid number | PASS | Automatic SecureRandom engine completed 75 draws |
| D02 | Draw is in configured Bingo range | PASS | Existing 1–75 range; regression asserts every generated value is within range |
| D03 | No duplicate number within a session | PASS | Full pool exhaustion test observed 75 unique numbers; DB also enforces uniqueness |
| D04 | Draw order is sequential | PASS | REST history returns increasing sequence numbers |
| D05 | Draw is associated with correct session | PASS | Regression verifies persisted events link to the fixture session |
| D06 | Draw persistence is atomic | PASS | Session transition and draw persistence share the transaction; rollback regression verifies no partial state |
| D07 | Concurrent draw requests cannot duplicate a sequence | PASS | Competing workers persist exactly one event for one due instant |
| D08 | Retry does not create an unintended additional draw | PARTIAL | Concurrent same-instant triggers persist one draw, but the request has no idempotency key; a later HTTP retry can trigger a subsequent due draw and retry semantics are unspecified |
| D09 | Maximum draw count is enforced | PASS | Exactly 75 numbers then CLOSED; no 76th draw |
| D10 | Draw after cancellation is rejected | PASS | No draw row after CANCELLED |
| D11 | Draw after completion is rejected | PASS | No draw row after CLOSED |
| D12 | Invalid session draw is rejected safely | PASS | Missing session returns no scheduled draw and no database row |
| D13 | Unauthorized role cannot trigger draws | PASS | Anonymous 401; player and agent 403 on admin draw route |
| D14 | Recovery restores prior draw history | PASS | Startup recovery and REST replay use persisted ordered events |
| D15 | Draw timestamp uses authoritative server time | PASS | Scheduler persists the server-supplied `Instant`; production caller supplies `Instant.now()` |
| D16 | Draw history matches MySQL | PASS | Integration compares persisted rows with ordered replay; persistent QA reconciliation also compares all existing draw rows |

## Randomness, player display, and environment

| Scenario | Result | Notes |
| --- | --- | --- |
| Automatic generation uses a nontrivial production seed | PASS | `SecureRandom`; no deterministic production seed configured |
| Client/player input cannot control automatic selection | PASS | Player/agent cannot call draw mutation routes; automatic scheduler accepts no number input |
| Administrator-selected numbers are disallowed | PASS | 04C-1 makes the route bodyless; legacy number bodies are rejected and the UI exposes only “Draw next number” |
| Flutter draw timeline orders/deduplicates/recover gaps | PASS | Flutter controller/timeline unit tests |
| Android live-game access, ordered history, and refresh | PASS | Real emulator E2E passed on isolated session #31; see [04C-2 matrix](module-04c2-test-matrix.md) |
| Android force-stop restart, network interruption/recovery, terminal-state UI | NOT RUN | These remain 04C-2 cases A14–A16 and A19 |
| Persistent QA data unchanged | PASS | Before/after SELECT-only comparisons; see [reconciliation](../evidence/module-04c-database-readonly.txt) |

Module 04C remains PARTIAL while L06, D08's HTTP retry contract, and 04C-2 cases A14–A16/A19 are unresolved. See [Module 04C-2](module-04c2-android-live-game.md) for the Android/Admin journey and Module 04C-1 for route, role, audit, UI and same-instant concurrency coverage.
