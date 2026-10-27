# Remaining Functional E2E Execution

**Historical disposition as of the initial 2026-10-26 assessment: BLOCKED — no new real E2E scenario could be started safely.**  
**Assessment date:** 2026-10-26. This report records only newly selected scenarios and the safety preflight disposition. It does not repeat previously passing L5 journeys or replace real E2E with unit/widget tests.

## Selected scenarios

Five incomplete P0/P1 scenarios were selected from the existing 39-domain matrix. Each failed the common safety gate before API, UI, or database execution.

| Requirement / domain | Priority and workflow | Existing coverage | New real E2E result | UI evidence | API evidence | MySQL/reconciliation evidence | Defect / remaining blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F12 / D15 | P0 — Player ticket purchase and ownership | Backend purchase and wallet tests exist; Android preview exists, but purchase UI is absent. Purchase is financially stateful. | **BLOCKED** | None; Android app was not launched. | None; backend was not started or called. | None in this run. Existing 04D-4 grant preflight found destructive privileges. | Purchase UI is absent; wallet debit and Finance approval are prerequisites. An approved funded fixture is unavailable. |
| F14 / D16 | P0 — Automatic draws, uniqueness, and retry behavior | Scoped Admin/Android/DB draw journeys exist; retry/idempotency remains unresolved in 04C. | **BLOCKED** | None; no emulator journey. | None; backend was not started or called. | None in this run. | Runtime account fails least-privilege gate. Draw retry contract still needs Product/API decision; no eligible active session was verified. |
| F10, F16 / D17 | P0 — Server card generation and winning-pattern validation | Pure `BingoGameRulesTest` and `BingoPatternEvaluatorTest` evidence exists; this is not real persistence/UI evidence. | **BLOCKED** | None; no eligible ticket could be rendered in the app. | None. | None in this run. | Requires a safely owned persisted ticket. No approved nonfinancial fixture writer exists; normal purchase creates financial writes. |
| F17, F18 / D18–D19 | P0 — Claim eligibility and duplicate-claim prevention | Service/unit and guard tests exist; real claim persistence, duplicate/concurrent E2E remain unverified. | **BLOCKED** | None; Android/Admin were not launched. | None. | None in this run. | `bingo_qa_test_runner` was previously verified with `ALL PRIVILEGES ON bingo_qa_test.*`; Module 04D-5B has design only, not an approved/implemented fixture. CLOSED eligibility remains a Product decision. |
| F08, F13 / D13–D14 | P1 — Session scheduling and lifecycle | Admin session and Android terminal/recovery scenarios have scoped historical coverage; schedule policy and broader transitions remain incomplete. | **BLOCKED** | None; no Admin Chromium or Android journey. | None. | None in this run. | Same failed DB safety gate. Timezone, DST, overlap, recurrence, and past-time policies remain unresolved. |

## Safety preflight and stop decision

- Existing read-only preflight evidence from Module 04D-4 identifies `bingo_qa_test_runner@127.0.0.1` on `bingo_qa_test` with `ALL PRIVILEGES ON bingo_qa_test.*`, including destructive DDL. That account fails the established runtime safety gate. See [04D-4 preflight evidence](../evidence/module-04d4-preflight.txt).
- The configured ignored `bingo-backend/.env.qa` target was checked without printing secrets: it points to `bingo_qa` using `bingo_qa_app`, not the requested isolated `bingo_qa_test` runtime identity. It was not used. No current authorized DBA connection was available for a fresh grant check or grant change.
- The MySQL service is running, but service availability does not establish an authorized isolated connection or safe grants. No MySQL query or database connection was made in this execution batch; there is no before/after database snapshot to report.
- The existing report says no approved nonfinancial owned-ticket fixture path exists. No ticket, user, session, draw, or claim fixture was created.
- An `adb devices -l` preflight failed before listing devices: ADB reported `Cannot mkdir '\.android': Permission denied`. The emulator was not confirmed available to this execution identity. No Android app was launched. The retained diagnostic is [ADB preflight log](../evidence/module-06a2/adb-devices.txt).
- No QA Spring Boot backend was started; no API calls, Chromium flows, financial operations, migrations, or database writes occurred. No unrelated-record reconciliation was attempted because no database connection was made.

Because every applicable real E2E scenario requires the verified isolated database and this runtime account fails the documented gate, execution stopped before UI/API/database actions. The existing grant evidence was not reinterpreted as authorization. No mocks or supplemental tests are counted as new E2E results.

## Result counts

- Previously incomplete scenarios selected: **5**
- New real E2E passed: **0**
- Failed after execution: **0**
- Blocked before execution: **5**
- Confirmed functional defects fixed: **0**
- Domains upgraded to L5: **0**

The master E2E coverage matrix and test-data registry were not changed because this batch produced no new business-flow or reconciliation evidence. Module statuses remain unchanged; Module 04E remains NOT STARTED. No commit or push was made.

## Remaining priority and next batch

P0 work remaining includes ticket ownership/purchase, real card/pattern evaluation, draw retry/concurrency, and claim eligibility/duplicate persistence. Purchase/transfer/settlement cases also remain subject to financial approvals. P1 work includes session scheduling/lifecycle under still-open Product rules and the incomplete agent ticket-sale-to-player-ticket journey, which has float/commission effects.

There is no currently executable real E2E batch. After the DBA/security grant gate is resolved and the Product/Security/DBA-approved nonfinancial ticket-fixture path is implemented, the next high-value batch is the owned-ticket → draw → claim/duplicate-claim journey through Android, the real API, and Admin Chromium where applicable, with read-only MySQL/audit reconciliation and proof that wallet/settlement data stayed unchanged. Draw-retry scenarios additionally require the Product/API retry contract. Financial purchase and agent-sale scenarios remain deferred until their approved isolated funding controls exist.

## Module 06A-3 follow-up

The five selected scenarios remain **BLOCKED**. ADB diagnostics confirmed the server and emulator TCP listeners were already present, but the ADB client fails during Windows home-directory initialization (`Cannot mkdir '\.android': Permission denied`) under the unregistered `codexsandboxoffline` profile. Profile-derived environment overrides did not fix it. Dart 3.13.5 runs; bounded Flutter `--version`, `devices`, and `doctor -v` commands timed out without output. No active server was restarted and no unrelated process was terminated. See [Module 06A-3 report](module-06a3-e2e-unblock-and-execution.md) and its [diagnostic logs](../evidence/module-06a3/).

The DBA grant gate and owned-ticket fixture approvals remain unchanged. No API, MySQL, Android UI, or Admin UI workflow ran, so there are no new business-flow results or reconciliation evidence and the master coverage matrix/test-data registry remain unchanged. Module 04E remains NOT STARTED.

## Module 06A-4 follow-up — 2026-10-26

ADB was found at the installed SDK path, but `adb version` and `adb devices -l` both failed during ADB home initialization with `Cannot mkdir '\.android': Permission denied`. The device list was not produced and the emulator was not confirmed to this execution identity. The combined preflight did not produce a Flutter device list; the prior bounded Flutter probes timed out as documented above. No ADB server restart, emulator action, or process termination was performed. Run the profile-derived commands from a normal VS Code terminal under the Windows account that owns the registered Android profile:

```powershell
$sdk = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
$env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
$env:Path = "$sdk\platform-tools;$sdk\emulator;C:\src\flutter\bin;$env:Path"
Get-Command adb
adb version
adb devices -l
flutter devices
```

Database grants were not freshly queried because no authorized DBA connection is available. The retained Module 04D-4 preflight identifies `bingo_qa_test_runner@127.0.0.1` with `ALL PRIVILEGES ON bingo_qa_test.*`, which remains an unsafe runtime grant under the mandatory policy. `.env.qa` remains prohibited because it targets persistent `bingo_qa`; neither that configuration nor any database connection was used in this run. All five scenarios remain BLOCKED, with no real E2E attempt, no DB reconciliation, no defects fixed, and no matrix or registry changes. See [Module 06A-4 results](module-06a4-android-e2e-results.md). Module 04E remains NOT STARTED.

## Local QA authorization update and resumption preparation — 2026-10-26

The project owner explicitly accepted `ALL PRIVILEGES` scoped to local `bingo_qa_test` for local testing, prohibited grant changes, and confirmed this exception does not apply to production. The privilege concern recorded in earlier assessments is therefore **ACCEPTED FOR LOCAL TESTING**, not a current E2E blocker. Historical outcomes above remain accurate for their original execution time. The isolated schema target, no-cross-schema-grant check, disabled financial controls, preservation rules, and prohibition on destructive Maven runs against `bingo` or `bingo_qa_test` remain in force.

Fresh read-only inspection confirmed `bingo_qa_test_runner@127.0.0.1`, schema `bingo_qa_test`, MySQL port 3306, Flyway V22, 40 tables, eight retained sessions (including six historical `DRAWING` sessions), zero tickets, zero claims, and zero wallet transactions. The account's only grants were global `USAGE` and `ALL PRIVILEGES` on `bingo_qa_test.*`. No rows or grants were changed. See [sanitized read-only preflight evidence](../evidence/module-06a5-local-qa-preflight.txt). Do not enable the draw scheduler against the retained `DRAWING` sessions, and do not reuse or change them for this run.

No scenario was executed in this sandbox context. Ticket purchase remains **BLOCKED** because the Android purchase UI is absent and the operation has financial effects. Card/pattern and claim/duplicate-claim remain **BLOCKED** because an eligible owned ticket requires the unapproved synthetic ticket fixture. Automatic draw/retry is **NOT RUN**: the existing `DRAWING` sessions are historical, the retry contract remains unresolved, and this environment cannot run Android UI. Session scheduling/lifecycle is **NOT RUN** pending Admin credentials and execution of the real Chromium flow against the verified isolated backend. No API writes, app launches, financial operations, or fixture creation occurred.

The owner-authorized local grant exception removes the old database-privilege gate. The normal-user runners and Chromium scenario are prepared and PowerShell syntax validation passes, but this Codex execution identity cannot run the Android tooling and has no Admin E2E credentials configured. Therefore no scenario has new PASS/FAIL runtime evidence. The session scheduling runner captures read-only database snapshots before and after, verifies unchanged historical session/room/ticket/draw/claim/wallet state, and reconciles the new session and audit rows. It has not been run. Its command is `.\scripts\qa\Start-AndroidTestBackend.ps1`, then `.\scripts\qa\Invoke-AdminSessionSchedulingE2E.ps1` from normal VS Code PowerShell after setting the approved Admin credentials in that process. The backend launcher verifies `android-test` plus `bingo_qa_test` identity, keeps scheduler and financial paths disabled, and does not execute test suites.

PowerShell launch scripts are prepared for the normal Windows user terminal: [start and verify the Android-test backend](../../../scripts/qa/Start-AndroidTestBackend.ps1), [start React Admin against that same verified backend](../../../scripts/qa/Start-AdminAgainstIsolatedBackend.ps1), and [launch the player app against it](../../../scripts/qa/Run-PlayerAgainstIsolatedBackend.ps1). The backend script checks the schema/account/grant scope and Flyway version before startup, uses `android-test`, verifies the runtime identity, disables the draw scheduler and all financial simulation/posting, and never runs tests or cleans the database. It also refuses the known `CodexSandboxOffline` identity. These scripts are preparation, not E2E evidence; every scenario above remains unpassed until its real UI/API/MySQL execution and reconciliation are captured. Module 04E remains NOT STARTED.

## Module 06A-5 source review and gap closure — 2026-10-26

Source review found the player purchase path was not wholly absent: session/price/capacity preview, server-side random card generation, purchase API, ownership/card rendering, and idempotency already exist. The confirmed gap was that lobby navigation did not open live detail for OPEN sessions, even though purchase was only offered in live detail. OPEN sessions now have that route. A price confirmation is implemented behind `BINGO_PLAYER_PURCHASES_ENABLED`, default false. The backend still rejects purchase posting outside its isolated opted-in Maven profile; financial posting remains unavailable in `android-test`.

No draw defect was confirmed. The six retained DRAWING sessions correspond by name to preserved Module 04C restart/network and retry/concurrency integration fixtures. Those tests disable the scheduler and retain rows after manual draws. Keep `app.game.draw-scheduler-enabled=false` for shared `bingo_qa_test`; no historical session was mutated. Server pattern validation, claim ownership/security/idempotency, and duplicate constraints already exist, but real Android/database claim evidence requires approved owned-ticket fixtures. CLOSED-session eligibility and retry semantics remain Product/API decisions.

Regression tests were added for OPEN-session navigation and purchase default-off behavior. **Flutter tests NOT RUN** because SDK startup stalled without output in the restricted sandbox. **Backend pure unit tests BLOCKED before discovery** because Maven could not create `C:\.m2\repository`. No database integration suite was run. PowerShell parser validation and the per-command Git whitespace check pass. No Android, Spring Boot runtime, React Admin Chromium, or real MySQL E2E was run; real E2E PASS remains **0**. See [Module 06A-5 functional gap closure](module-06a5-functional-gap-closure.md) for exact next commands and expected reconciliation artifacts.
