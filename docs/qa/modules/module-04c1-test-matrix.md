# Module 04C-1 — Test Matrix

| ID | Scenario | Result | Evidence / notes |
| --- | --- | --- | --- |
| I01 | Normal admin draw request has no number field | PASS | Admin API and session page no longer accept `numberDrawn` |
| I02 | Draw endpoint accepts bodyless trigger | PASS | Backend integration exercises bodyless POST |
| I03 | Legacy client-selected number body is rejected | PASS | Backend expects `CALLER_SELECTED_DRAW_NOT_ALLOWED` |
| I04 | Admin-triggered draw is server selected | PASS | Response and persisted value are in 1–75 |
| I05 | Previously drawn values are excluded | PASS | Shared engine filters persisted values; existing exhaustion test covers uniqueness |
| I06 | Trigger requires an authorized ADMIN | PASS | Backend role matrix |
| I07 | Anonymous draw trigger is rejected | PASS | Backend HTTP security regression |
| I08 | Player and agent draw triggers are rejected | PASS | Backend role matrix |
| I09 | SUPPORT draw trigger is rejected | PASS | Explicit controller/service deny |
| I10 | FINANCE_VERIFIER draw trigger is rejected | PASS | Explicit controller/service deny, including combined ADMIN role |
| I11 | FINANCE_APPROVER draw trigger is rejected | PASS | Explicit controller/service deny, including combined ADMIN role |
| I12 | Trigger cannot draw before session is eligible | PASS | Session-state guard regression |
| I13 | Trigger cannot draw after cancellation/completion | PASS | Backend cancellation and closed-session coverage |
| I14 | Missing session fails without a draw | PASS | Backend missing-session case |
| I15 | Trigger respects the scheduled draw interval | PASS | Same due-time predicate as scheduler |
| I16 | Concurrent triggers do not duplicate a draw | PASS | Integration test runs competing requests at the same due instant |
| I17 | Draw, session transition, and audit persist atomically | PASS | Backend rollback case |
| I18 | Successful draw audit contains actor, target, source, and draw metadata | PASS | Admin audit row is asserted in integration test; scheduler source retained |
| I19 | UI displays server-returned number and blocks repeat click while pending | PASS | SessionDetailPage component tests |
| I20 | Persistent QA data remains unchanged | PASS | SELECT-only before/after snapshots; see [reconciliation](../evidence/module-04c1-database-readonly.txt) |

## Verification summary

See [backend focused results](../evidence/module-04c1-backend-focused-summary.txt), [full backend results](../evidence/module-04c1-backend-full-summary.txt), and [admin results](../evidence/module-04c1-admin-summary.txt). A real Chromium live-session journey is BLOCKED while persistent QA has no eligible active game and the QA backend port 8081 is unavailable; no persistent session was altered to create one.
