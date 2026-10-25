# Module 04C-3 — Test Matrix

This matrix combines the initial safe environment-gate result with the later 04C-3B execution. The initial attempt ran no gameplay operations; the recovered test environment and scenario results are in [04C-3A](module-04c3a-environment-recovery.md) and [04C-3B](module-04c3b-live-game-verification.md).

| ID | Scenario | Result | Evidence / notes |
| --- | --- | --- | --- |
| ENV01 | Initial 04C-3 backend gate | BLOCKED, then resolved | Initial attempt missed the ignored `bingo-backend/.env.test`; it made no gameplay request. 04C-3A restored and verified isolated backend identity. |
| A14 | Force-stop and relaunch; restore auth and history | PASS in 04C-3B | Session #34; Android storage was not cleared. |
| A15 | Emulator offline transport error and retained history | PASS in 04C-3B | Session #35; SocketException and reconnecting UI captured. |
| A16 | Same-process network restoration and history refresh | PASS in 04C-3B | Backend reachability independently verified; history remained unchanged. |
| A19a | Supported closure/completion terminal and further-draw denial | PASS in 04C-3B | `CLOSED` UI and HTTP 409 verified. A distinct `COMPLETED` state is not defined by the product. |
| A19b | Cancellation terminal and further-draw denial | PASS in 04C-3B | `CANCELLED` UI and HTTP 409 verified. |
| DB01 | Preserve historical session IDs before fixture work | DEVIATION | Pre-run SELECT showed session IDs #30/#31 absent, with sequence advanced. First new fixtures received those IDs; rows left untouched after collision disclosure. |
| DB02 | Isolated fixture reconciliation | PASS | Six sessions each have one draw and matching audit; no tickets, claims, entitlements, or wallet transactions. |
| DB03 | Persistent `bingo_qa` before/after | PASS | SELECT-only snapshots are byte-identical. |

The 04C-3B scenario tests pass, but module status remains PARTIAL because the run's precondition to preserve historical #30/#31 database rows could not be met. Prior C2 evidence files remain preserved.
