# Module 06A-5 — Functional Gap Closure and Executable E2E

Date: 2026-10-26

## 1. Confirmed implementation gaps

- **Ticket purchase:** the Android app already lists rooms/sessions, shows ticket price/capacity/patterns, provides a random-card preview, calls the idempotent purchase API from live detail, and displays server-returned owned tickets/cards. The actual entry defect was that OPEN sessions could not enter live detail, while the buy control lived there; lobby navigation exposed only STARTED/DRAWING. The ticket preview CTA remains intentionally disabled. Card choice/reservation is not part of the server contract: cards are generated server-side on purchase. An explicit price confirmation dialog is now implemented behind `BINGO_PLAYER_PURCHASES_ENABLED`, which defaults false. The backend `TicketPurchasePostingGuard` also rejects posting outside the explicitly opted-in disposable Maven test profile; the android-test profile cannot debit a wallet.
- **Automatic draws:** scheduler and restart recovery are implemented. `GameLiveDrawScheduler` is property-gated, calls recovery on application ready, and advances due STARTED/DRAWING sessions. Draw writes lock the session, derive a sequence under that lock, exclude previously drawn numbers, write audit data, and publish events. Schema uniqueness constraints cover session/sequence, session/number, and operation ID. Existing `DrawRetrySafetyIntegrationTest` covers lost-response retry and concurrent requests. No implementation defect was confirmed from source review.
- **Six retained DRAWING rows:** the read-only snapshot identifies four rows named for Module 04C-3B restart/network scenarios and two Module 04C-4 retry/concurrency scenarios. Those integration fixtures start sessions and deliberately preserve append-only evidence; the draw retry tests disable the scheduler and leave their successful sessions DRAWING. These are retained historical test fixtures, not evidence of a scheduler-created transition defect. No row was changed. Keep the scheduler disabled against this shared database.
- **Session scheduling/lifecycle:** Admin create and state-transition UI/API support creating OPEN sessions and transitioning to STARTED/CANCELLED. The prepared Chromium scenario creates a unique `QA_M06A5_*` session in an existing room, then transitions that new row. It does not overwrite or clean up historical data.
- **Cards/patterns and claims:** Flutter renders server-owned ticket cards and exposes configured patterns. Backend validation is authoritative; claim submission is player-only and idempotent, with unique constraints against duplicate ticket/pattern/draw-snapshot claims. Existing `BingoGameIntegrationTest` covers invalid pattern persistence, duplicate claims, and another player's ticket returning `TICKET_NOT_FOUND`; `WinClaimSessionSecurityTest` covers CANCELLED rejection and leaves CLOSED behavior undecided. `BingoPatternEvaluatorTest` covers valid/incomplete patterns. These are not Android E2E. No fixture-driven Android claim test was added because the test needs an approved owned ticket and controlled draw history; no such approval/data exists. CLOSED claim eligibility remains a Product decision.

## 2. Code changes

- `AppConfig` adds `BINGO_PLAYER_PURCHASES_ENABLED`, false by default. The purchase API maps server error codes/messages rather than exposing a generic Dio exception, preserving useful feedback for insufficient balance, closed/expired sales, and network failures.
- Lobby routes OPEN sessions to live detail, making their ticket information and gated purchase control reachable.
- The purchase action displays price/currency and requires confirmation when the explicit UI flag is enabled. No backend debit, accounting, or authorization logic changed; backend guard remains authoritative.
- Added regressions in the lobby test and `test/core/app_config_test.dart` for OPEN routing and the purchase-off default, plus `test/core/api_client_test.dart` for preserving purchase error details.
- Reviewed and repaired the PowerShell Admin E2E runner's audit-max query interpolation. The runner records before/after snapshots and reconciles session, audit, ticket, draw, claim, room, and wallet state.

## 3. Tests and results

| Check | Result | Detail |
| --- | --- | --- |
| Flutter focused widget tests | **NOT RUN** | `flutter test` produced no output while initializing under the restricted Codex identity; the invocation was interrupted. Run in normal VS Code PowerShell. |
| Backend pure unit tests (`GameSessionStateTest`, `BingoPatternEvaluatorTest`) | **BLOCKED** | Maven failed before test discovery because it could not create `C:\.m2\repository`. No integration suite was started. |
| PowerShell parser | **PASS** | All four `scripts/qa/*.ps1` files parse successfully. |
| Git whitespace check | **PASS** | `git -c safe.directory=D:/GitHub/bingo-platform diff --check`. |
| Android, backend, Admin Chromium, real MySQL E2E | **NOT RUN** | This sandbox cannot run the normal-user Android tooling; no Admin credentials are configured here. No DB mutation occurred. |

Flutter focused command for normal VS Code PowerShell:

```powershell
Set-Location D:\GitHub\bingo-platform\bingo-app
flutter test test/core/app_config_test.dart test/features/lobby/lobby_screen_test.dart
```

## 4. Real E2E evidence

There are **no new real E2E PASS/FAIL results**. Existing real QA evidence remains preserved. The local database remains `bingo_qa_test`; all six historical DRAWING sessions, other QA rows, and grants were left unchanged. Financial posting stayed disabled.

## 5. Product decisions still open

- Ticket purchase/cutoff policy, confirmation wording/price rules, and approval to enable purchases for an isolated local QA run.
- Draw retry semantics for clients that cannot provide an idempotency key. The current scheduled draw cadence is not a request replay contract.
- Whether claims are permitted in CLOSED sessions.
- Product-supported custom card selection/reservation, if desired; current API generates cards randomly on the server.

## 6. Execution blockers

- Normal-user Android tooling is available only from the user's VS Code PowerShell terminal; the Codex sandbox cannot initialize it.
- Admin credentials are not configured in this execution process. The session E2E runner reads them from process environment and never passes them to Vite or writes them to evidence.
- Ticket/claim real E2E needs explicitly approved synthetic owned-ticket/player fixtures. No such fixtures were created.
- Do not start automatic draw scheduling against `bingo_qa_test` while historical DRAWING sessions remain there.

## 7. Next executable commands

For the actual Android/Flutter regression in normal VS Code PowerShell:

```powershell
Set-Location D:\GitHub\bingo-platform\bingo-app
flutter test test/core/app_config_test.dart test/features/lobby/lobby_screen_test.dart
```

For the prepared real Admin Chromium session lifecycle and MySQL reconciliation, from the repository root in that same normal-user terminal:

```powershell
Set-Location D:\GitHub\bingo-platform
.\scripts\qa\Start-AndroidTestBackend.ps1
$adminCredential = Get-Credential -Message 'Local QA Admin E2E account'
$env:BINGO_E2E_ADMIN_EMAIL = $adminCredential.UserName
$env:BINGO_E2E_ADMIN_PASSWORD = $adminCredential.GetNetworkCredential().Password
.\scripts\qa\Invoke-AdminSessionSchedulingE2E.ps1
Remove-Item Env:\BINGO_E2E_ADMIN_EMAIL, Env:\BINGO_E2E_ADMIN_PASSWORD -ErrorAction SilentlyContinue
Remove-Variable adminCredential -ErrorAction SilentlyContinue
```

Expected new evidence on successful Chromium execution: a unique `QA_M06A5_*.json` manifest, `*-database-before.json`, `*-database-after.json`, `*-reconciliation.json`, before/after screenshots, and Playwright/Vite logs under `docs/qa/evidence/module-06a5/`. PASS requires Chromium success plus all reconciliation checks true; script preparation alone is not PASS.

Module 04E remains **NOT STARTED**. No schema or grants were changed, no historical data was modified, and no commit or push was made.
