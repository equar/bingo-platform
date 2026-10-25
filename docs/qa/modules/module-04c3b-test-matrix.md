# Module 04C-3B — Test Matrix

All Android rows use the synthetic player, the isolated test backend at `10.0.2.2:8082`, and current-source sessions. See the [verification report](module-04c3b-live-game-verification.md) and [read-only snapshots](../evidence/module-04c3b-isolated-database-after.txt).

| ID | Scenario | Result | Evidence / notes |
| --- | --- | --- | --- |
| ENV01 | Revalidate backend identity, schema, flags, grants, emulator route | PASS | Current source JAR PID 32844; `bingo_qa_test`, V22; scheduler/financial flags disabled; emulator health 200. |
| DB00 | Verify historical #30/#31 before fixture work | DEVIATION | Both rows absent before writes despite advanced sequence at 30/31. Initial inserts received those IDs; collision disclosed and rows left untouched afterward. |
| A14 | Force-stop and relaunch; restore login and live draw history | PASS | Session #34; no app data clear; stored session restored; one draw unchanged. Before/after Android evidence linked in report. |
| A15 | Interrupt emulator network; capture transport error and retained history | PASS | Session #35; socket exception and `Reconnecting...`; one draw retained. |
| A16 | Restore network in same process; retry history | PASS | Health GET 200; refresh recovered to Live; same one draw. |
| A19a | Display supported completion terminal and block future draw | PASS for `CLOSED` | Session #32; Admin transition; post-terminal draw HTTP 409; UI shows CLOSED/Finished and one draw. No separate COMPLETED state exists. |
| A19b | Display cancellation terminal and block future draw | PASS | Session #33; Admin transition; post-terminal draw HTTP 409; UI shows CANCELLED/Finished and one draw. |
| SEC01 | Player/agent/support/finance roles denied draw trigger | PASS | HTTP 403 for all four roles on fresh live fixture; no added draw/audit row. |
| DB01 | Reconcile session, unique sequence/number, audit, ticket, claim, entitlement, wallet invariants | PASS | Sessions #30–35 each have sequence 1, one audit row, no tickets; no claims, entitlements, or wallet transactions. |
| DB02 | Persistent `bingo_qa` before/after comparison | PASS | Existing SELECT-only snapshot outputs byte-identical at 15,081 bytes. |
| QA01 | Flutter focused game tests / analyzer | PASS | 11/11 tests; analyzer clean. |
| QA02 | Focused backend fixture/lifecycle tests | PASS | 3/3; destructive game integration classes and full suite were not run. |

## Disposition note

Android scenarios supported by the product passed. Module remains PARTIAL because pre-run verification found the historical database sessions #30/#31 already absent and first inserts collided with their IDs; the required historical-data preservation claim cannot be made for this run. Prior C2 evidence is retained, and no further mutations were made to the collided rows.
