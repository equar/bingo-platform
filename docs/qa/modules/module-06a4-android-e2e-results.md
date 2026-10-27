# Module 06A-4 — Android Connectivity and Remaining Functional E2E

**Historical status at the 2026-10-26 execution: BLOCKED.** No selected real functional E2E scenario was started. Android tooling could not initialize ADB under that execution identity, and the then-current policy treated the QA runtime grant as a blocker. The local grant concern was later accepted by the project owner; this historical result is retained without changing its execution outcome.

**Date:** 2026-10-26 (Africa/Nairobi)

## Android connectivity

| Check | Result | Evidence |
| --- | --- | --- |
| `Get-Command adb` | **PASS** — resolved `C:\Users\16414\AppData\Local\Android\Sdk\platform-tools\adb.exe` | This Module 06A-4 preflight |
| `adb version` | **BLOCKED** — ADB failed before reporting its version: `adb_utils.cpp:315 Cannot mkdir '\.android': Permission denied` | This Module 06A-4 preflight; same failure is retained in [Module 06A-3 evidence](../evidence/module-06a3/adb-version.txt) |
| `adb devices -l` | **BLOCKED** — same ADB home initialization error; no device list returned | This Module 06A-4 preflight; [prior device diagnostic](../evidence/module-06a3/adb-devices.txt) |
| Emulator `emulator-5554` | **NOT DETECTED** by this execution; no serial listing was possible | No app launch attempted |
| `flutter devices` | **NOT VERIFIED** — the combined preflight produced no device listing; prior bounded Flutter probes timed out without output | [Module 06A-3 report](module-06a3-e2e-unblock-and-execution.md) |

The installed ADB executable is discoverable, but its initialization error is consistent with the unregistered sandbox Windows profile documented in Module 06A-3. No ADB server or emulator restart was attempted, and no unrelated Dart, Flutter, or emulator process was terminated.

To verify in a normal VS Code PowerShell terminal running as the Windows account that owns the Android profile:

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

## Database safety gate

The required schema is `bingo_qa_test`. The persistent schemas `bingo` and `bingo_qa` are out of bounds; `.env.qa` targets `bingo_qa` and was not used. No database connection or mutation was made in this run.

No authorized DBA login was available for a fresh read-only grant query. The latest retained authorized preflight, [Module 04D-4 evidence](../evidence/module-04d4-preflight.txt), reports `bingo_qa_test_runner@127.0.0.1` on `bingo_qa_test` with `ALL PRIVILEGES` on the schema, including destructive privileges. This fails the established runtime safety gate. No grants or credentials were modified. Mutation-capable E2E remains stopped.

## Selected scenario results

| Scenario | Result | Execution and blocker |
| --- | --- | --- |
| E2E-01 — Ticket purchase and ownership | **BLOCKED** | Not run. Android is inaccessible; the retained runtime account grant is unsafe. Purchase UI and approved funded fixture/Finance controls are also unresolved. |
| E2E-02 — Automatic drawing and retry | **BLOCKED** | Not run. Android and DB gates fail; draw retry contract and eligible active session are not verified. |
| E2E-03 — Bingo card and pattern validation | **BLOCKED** | Not run. No approved eligible persisted-ticket fixture; DB gate fails. |
| E2E-04 — Claims and duplicate claims | **BLOCKED** | Not run. No approved nonfinancial owned-ticket fixture; DB gate fails; CLOSED eligibility remains a Product decision. |
| E2E-05 — Session scheduling and lifecycle | **BLOCKED** | Not run. Android and DB gates fail; schedule timezone, DST, overlap, recurrence, and past-time rules remain unresolved. |

No Android application, Spring Boot QA backend, Admin Chromium workflow, or database-backed E2E was launched. API and UI evidence, MySQL before/after reconciliation, and audit reconciliation are **NOT RUN**. No financial operation, fixture, or other business record was created. No functional defect was observed or fixed; no regression test was added because no failure was exercised.

## Disposition

- Real E2E scenarios attempted: **0**
- PASS: **0**; FAIL: **0**; BLOCKED: **5**; NOT RUN as execution: **5**
- MySQL reconciliation: **NOT RUN** (no authorized fresh connection and no DB changes)
- Defects fixed: **0**
- Master matrix and test-data registry: unchanged; this run produced no new business-flow evidence.
- Module 04E: **NOT STARTED**. No commit or push was made.

The Android gate can be rechecked from the normal VS Code terminal using the profile-derived commands above. The QA runtime must also pass a fresh read-only DBA grant verification and least-privilege review before mutation-capable E2E can begin. Product/Security/DBA fixture approvals and the documented unresolved policy decisions remain prerequisites for affected scenarios. See the [remaining functional E2E report](remaining-functional-e2e-execution.md) and [Module 06A-3 report](module-06a3-e2e-unblock-and-execution.md).

## Owner authorization and current disposition — 2026-10-26

The project owner accepts `ALL PRIVILEGES` scoped to local `bingo_qa_test` for local QA and explicitly prohibits changing MySQL grants. This replaces the old grant blocker for local testing only; production least-privilege requirements remain unchanged. No DBA action is required for local functional E2E on this basis.

Fresh read-only verification using the configured local test account confirmed schema `bingo_qa_test`, account `bingo_qa_test_runner@127.0.0.1`, port 3306, Flyway V22, 40 tables, eight retained sessions, and schema-scoped `ALL PRIVILEGES` plus global `USAGE`. No database or grants were changed. Six retained sessions are in `DRAWING`; the draw scheduler must stay disabled while using this shared E2E database unless a separately approved isolated session path is established.

No real UI/API E2E was executed in this turn. Purchase remains **BLOCKED** by the missing Android purchase UI and financial effects. Card/pattern and claim scenarios remain **BLOCKED** by the unapproved owned-ticket fixture. Automatic draw/retry is **NOT RUN** because the retained `DRAWING` sessions must not be advanced and retry semantics remain unresolved. Session scheduling/lifecycle is **NOT RUN** pending real Admin Chromium execution and credentials in the normal user context. The normal-user launch scripts are linked in the [remaining functional E2E report](remaining-functional-e2e-execution.md); they are preparation only, not PASS evidence.
