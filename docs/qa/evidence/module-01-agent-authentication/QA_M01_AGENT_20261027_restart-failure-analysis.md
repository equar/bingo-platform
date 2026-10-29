# Agent Android run diagnosis — 2026-10-27

Run `QA_M01_AGENT_20261027_163559_171` retained its original sanitized [Flutter log](QA_M01_AGENT_20261027_163559_171/flutter-integration.log) and [run summary](QA_M01_AGENT_20261027_163559_171/run-summary.json). The verified target in that run was PID 23908, profile `android-test`, schema `bingo_qa_test`, Flyway 22, and the summary records scheduler and financial posting/simulation controls disabled. Database protected-table counts matched before and after.

## Results from the retained log

| Scenario | Result in that run | Evidence |
| --- | --- | --- |
| Approved Agent login and authenticated home | PASS | Main integration command ends `All tests passed`; its test asserts a successful approved login and `NavigationBar`. |
| Pending Agent login denied | PASS | Main command passed its assertion that the pending Agent remained on the sign-in screen. |
| Invalid phone/PIN and synthetic login throttling | PASS | Main command passed invalid-input, repeated-failure, HTTP 429, and wrong-PIN assertions. |
| In-app session restore, expiry, and logout | PASS | Main command passed restore after rebuilding the app widget, expired-token cleanup, login persistence, and local logout checks. This is separate from process restart. |
| Cross-role API denial | PASS | Main command completed all three authenticated 403 probes. |
| Dashboard, Player lookup validation, and read-only Float UI | PASS | Main command passed the screen/validation assertions and captured the corresponding screenshots. |
| Support list UI | PASS | Main command ended `All tests passed` after the Support screen assertions. |
| Force-stop/relaunch session restore and logout | FAIL, with session restore itself successful | After ADB force-stop, the fresh process showed the authenticated home (`NavigationBar`) and the session token was nonempty. Logout then showed the sign-in form with two `TextFormField`s. The test waited for three and failed before checking that the token had been cleared. Thus restored authentication is evidenced; post-restart logout storage clearing was not verified in this attempt. |

There was one Flutter assertion failure: the restart test expected three sign-in fields and found two. The two current login fields are phone and PIN; the extra application fields only appear in Agent application mode. `AuthGate` restored an unexpired saved `AuthResponse` from secure storage and selected `HomeShell`, so this was a stale test expectation, not a timing problem or a reproduced persistence defect.

The initial authentication `flutter drive` command exited 0. The runner then invoked the process-restart command, which exited 1. Its summary incorrectly stored the combined result as `testExitCode=1` and marked all scenarios `NOT RUN` because it assigned PASS statuses only when both commands succeeded. This diagnosis does not alter that retained evidence.

## Corrections made

- Added stable `agent-home-screen`, `agent-login-screen`, and `agent-application-screen` keys. The restart test now requires the authenticated home key and a nonempty stored token after relaunch, then waits for the login screen key, checks its two phone/PIN fields, and verifies secure storage was cleared after logout.
- Added explicit scenario start/pass markers. The guarded runner maps these to PASS, FAIL, or NOT RUN, records the main and restart exit codes separately, and reports a restart blocker if the main run does not emit its authenticated-session prerequisite.
- The runner now force-stops and launches the restart test only if the main integration command exits 0 and emits `QA_AGENT_RESTART_PREREQUISITE:authenticated-session-persisted` after asserting both the authenticated home screen and stored session. Existing backend identity, datasource, MySQL grants, scheduler, and financial-control checks are unchanged.

The corrected Android E2E has not been rerun from this execution context. Retest with the guarded command in the normal interactive Windows PowerShell session.
