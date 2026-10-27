# Module 06A-8 — Real Android Player E2E

Date: 2026-10-26

## Execution summary

**Module status: PARTIAL — backend identity and health verified; real Android workflows NOT RUN in this Codex execution context.** No new E2E PASS is claimed. No source changes, API mutations, database writes, purchases, claims, fixtures, or scheduler actions were performed.

| Scenario / check | Status | Evidence and result |
| --- | --- | --- |
| Saved QA backend identity | PASS — saved evidence | `backend-identity-preflight.json` specifies profile `android-test`, schema `bingo_qa_test`, MySQL `127.0.0.1:3306`, Flyway V22, draw scheduler disabled, financial posting/simulation disabled, and PID 20588. |
| Live backend health | PASS | `http://127.0.0.1:8082/actuator/health` returned `UP`, with application, database, liveness, and readiness components UP. |
| Port 8082 process correlation | PASS | Read-only `netstat -ano -p tcp` showed `0.0.0.0:8082 LISTENING` under PID 20588; that PID is `java.exe`, matching the saved identity record. Port 8080 was not queried or touched. |
| Emulator / Flutter device detection in this run | NOT RUN | This restricted Codex execution context cannot perform Android tooling. The owner’s prior normal VS Code PowerShell verification of `emulator-5554` and Flutter 3.47.7 is retained as historical evidence, not counted as a new run. |
| Flutter build, install, and app launch | NOT RUN | No Android command was executed here. |
| API connectivity from the app | NOT RUN | Host health is verified, but no Android app request to `10.0.2.2:8082` was observed. |
| Valid/invalid login, auth state, logout | NOT RUN | No credentials were read or printed and no UI session was run. |
| Lobby, loading/empty/error states, refresh, session status and price/currency | NOT RUN | No UI session or DB reconciliation was run. |
| OPEN-session detail navigation | NOT RUN | No Android UI execution. Existing session rows were not changed. |
| Purchase disabled / no wallet debit | NOT RUN — no purchase attempted | The launcher supplies `BINGO_PLAYER_PURCHASES_ENABLED=false`; this configuration was inspected, not demonstrated in a running app. No financial request was made. |
| Existing ticket/card/gameplay display | NOT RUN | No ticket or card UI was opened; no fixture was created. |
| Database before/after reconciliation | NOT RUN | No database connection or SQL query was made in this run. Since no API or UI mutations were initiated, this is not presented as database evidence. |
| Requested Flutter tests and analysis | NOT RUN | They require the normal Windows user toolchain in this environment; earlier sandbox attempts stalled before output. No test count or analyzer result is inferred from prior runs. |
| Defects fixed | None | No new functional failure was observed because no app workflow ran. |

## Execution boundary

The host-side isolated backend passed the live health and listener-to-PID checks. The saved runtime identity is consistent with that PID and required safety flags. This verifies the backend endpoint/process but does not independently query MySQL at runtime in this module. No backend restart was needed or performed.

Android execution remains on the normal Windows account because the restricted Codex process cannot reliably access the Android profile/tooling. This is the already-established execution boundary; no ADB troubleshooting, server restart, or process termination was attempted. The launch script was reviewed and passes the exact saved isolated backend URL to Flutter, checks health and device visibility, and forces purchases off.

## Exact next command in normal VS Code PowerShell

The backend is already healthy on port 8082 under the saved PID. Do not run the backend starter while this process remains healthy and identity evidence matches. In the normal VS Code PowerShell terminal as `EQUAR-DELL-XPS1\16414`, run:

```powershell
Set-Location D:\GitHub\bingo-platform
.\scripts\qa\Run-PlayerAgainstIsolatedBackend.ps1
```

Expected launcher evidence: health UP; connected `emulator-5554` from both ADB and Flutter; Flutter debug build/install/start; API define `http://10.0.2.2:8082`; purchase flag false. Then manually enter an existing authorized QA player account in the emulator UI (do not place credentials in shell arguments or tracked files). Capture sanitized screenshots/logs and record actual outcomes for login, invalid login, lobby refresh, an existing OPEN session detail/back/reopen, purchase-unavailable behavior, and logout. Do not attempt a purchase, claim, ticket fixture, or change a session status.

After the UI run, use the project’s approved read-only MySQL connection to capture before/after counts or checksums for sessions, tickets, claims, wallets, wallet transactions, and draws. Do not print credentials or sensitive account data. For changed-code regression checks, run from normal VS Code PowerShell:

```powershell
Set-Location D:\GitHub\bingo-platform\bingo-app
flutter test test/core/app_config_test.dart test/core/api_client_test.dart test/features/lobby/lobby_screen_test.dart
flutter analyze
```

Record raw sanitized outputs before upgrading any scenario status. Unit/widget tests do not count as Android E2E.

## Safety and remaining work

`bingo_qa_test` remains the only permitted database for this isolated backend. The scheduler and financial posting remain disabled. No historical records, including the retained DRAWING sessions, were modified. No schema, grants, or persistent QA records were changed. No commit or push was made.

The module cannot be marked COMPLETE until Android launch/API reachability, authorized login/logout, lobby and eligible OPEN-session navigation, purchase-disabled behavior, read-only database reconciliation, targeted Flutter regression tests, and static analysis have genuine runtime evidence.
