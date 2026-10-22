# QA Module Status

Status values: `PASS`, `FAIL`, `BLOCKED`, `NOT RUN`. A pass means the action was actually executed.

| Module | Status | Notes |
| --- | --- | --- |
| 00 — Local QA environment | BLOCKED | MySQL, backend, browsers, Flutter toolchain, Android emulator, both APK launches, and both Flutter unit/widget suites are ready. A read-only database-backed integration test passed. Approval for a guarded QA-only bootstrap was received; provisioning still needs confirmed reserved test phone numbers. Destructive integration suites still need a separate test schema. See [Module 00](modules/module-00-environment.md). |
| 01 — Business functionality | NOT RUN | Explicitly deferred until Module 00 is ready and requested. |

Do not start Module 01 until Module 00 is marked PASS and the user requests it.
