# QA Module Status

Status values: `PASS`, `FAIL`, `BLOCKED`, `NOT RUN`. A pass means the action was actually executed.

| Module | Status | Notes |
| --- | --- | --- |
| 00 — Local QA environment | COMPLETE | MySQL, backend, admin and public website, Flutter toolchain, Android emulator, both APK launches, Flutter tests, persistent QA accounts, and all 88 backend tests are verified. Database-backed tests use guarded `bingo_qa_test`; persistent `bingo_qa` remained intact. See [Module 00](modules/module-00-environment.md). |
| 01 — Business functionality | NOT RUN | Explicitly deferred until Module 00 is ready and requested. |

Do not start Module 01 until Module 00 is marked PASS and the user requests it.
