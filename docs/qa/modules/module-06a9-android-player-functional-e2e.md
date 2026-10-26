# Module 06A-9 — Android Player Functional E2E

Date: 2026-10-26

## Result

**Status: PARTIAL — real Android execution BLOCKED in this Codex process; the normal-user runner now creates one run-owned OPEN session and nonfinancial card display fixture before executing the UI journey.** No real login, lobby, OPEN-session, or purchase-disabled E2E PASS is claimed. Credentials have not been queried or recorded. This Codex turn made no MySQL or API mutation, ticket purchase, claim, or scheduler action.

| Check / scenario | Status | Result |
| --- | --- | --- |
| Isolated backend health | PASS | Fresh `http://127.0.0.1:8082/actuator/health` response was UP, including `db=UP`. |
| Port 8082 process identity | PASS | `netstat` shows `0.0.0.0:8082 LISTENING` under PID 20588; the process is `java.exe`, matching the saved Module 06A-5 identity record. Port 8080 was not queried or touched. |
| Saved backend safety identity | PASS — saved evidence | Profile `android-test`; DB `bingo_qa_test`; `127.0.0.1:3306`; Flyway V22; scheduler disabled; financial posting/simulation disabled. See [identity evidence](../evidence/module-06a5/backend-identity-preflight.json). |
| Actual MySQL identity and schema | PASS — READ ONLY | The approved `.env.test` connection returned `bingo_qa_test`, port 3306, Flyway V22 and 40 base tables. Existing game tables support session snapshots, ticket grids and nullable `wallet_funding_snapshot_json`; no schema migration is needed for the requested UI journey. |
| QA fixture inventory | PASS — READ ONLY | Existing active rooms #35–38 are available; no OPEN sessions existed. Six retained sessions are DRAWING, #32 is CLOSED and #33 is CANCELLED; all are preserved. Ticket, claim and wallet-transaction counts were zero. One active PLAYER exists. An enabled positive signup-reward policy means the runner must reuse that player rather than register a new one. |
| Android tool availability | BLOCKED | `whoami` returned `equar-dell-xps1\codexsandboxoffline`; ADB failed with `Cannot mkdir '\.android': Permission denied`. This is the known execution boundary. No ADB server restart, emulator action, or process termination was attempted. |
| Installed app / Android UI execution | NOT RUN | No app or test APK was launched in this run. Owner’s report that the app is running on `emulator-5554` is not counted as new evidence. |
| Required-field and invalid-input validation | NOT RUN | Implemented in the real-device integration scenario; not executed. |
| Invalid credentials and valid login | NOT RUN | Integration test exercises one `999999` rejection, then valid credentials supplied through a secure normal-terminal prompt; not executed. |
| Authenticated lobby, refresh, room price/currency | NOT RUN | Integration scenario and before/after DB snapshots are prepared; no UI/database reconciliation evidence yet. |
| OPEN-session navigation / purchase disabled | NOT RUN | Existing OPEN session inventory was empty, so the runner now requests a uniquely named OPEN session through the Admin API and inserts one test-owned ticket card solely for the ownership/card UI. Purchase remains disabled; the fixture has no wallet funding snapshot or ledger effect. Neither fixture has been created in this Codex process. |
| Logout / protected screen after logout | NOT RUN | Included in the prepared real Android test; not executed. |
| Owned ticket/card presentation | NOT RUN | The runner is prepared to create one controlled display-only ticket fixture; no fixture has been created in this Codex process. |
| Financial/business data reconciliation | NOT RUN | Runner captures before/after table counts, all room/session/ticket identities, price/currency, and wallet-balance hash. It permits exactly the test-owned session and ticket count increases and verifies every prior session/ticket remains unchanged. |
| Flutter regression tests / analysis | BLOCKED | `flutter analyze` was started in this process but produced no output for about 50 seconds and was interrupted. No Dart analysis or Flutter regression test PASS is claimed. Earlier owner-reported 10/10 results are historical only. |
| PowerShell syntax | PASS | Parser accepted the updated runner. This validates script syntax only, not E2E behavior. |
| Functional defects fixed | None | No runtime behavior was observed failing, so no product code fix was warranted. |

## Prepared executable coverage

Added [the guarded normal-user runner](../../../scripts/qa/Invoke-AndroidPlayerFunctionalE2E.ps1) and [the real Flutter integration scenario](../../../bingo-app/integration_test/module_06a9_player_e2e_test.dart). The scenario uses the actual `BingoApp`, real Android instrumentation, real Spring Boot API, and existing QA player credentials. It tests form validation; one invalid credential response followed by valid authentication; lobby load and refresh; existing room price/currency; optional OPEN-session navigation, purchase-disabled UI, and return/reopen; then logout and unauthenticated state.

The PowerShell runner refuses `CodexSandboxOffline`; verifies the saved backend profile/schema/flags, health and listener PID, MySQL runtime schema/port, existing active PLAYER and ADMIN identities, emulator and installed Player package; and snapshots read-only QA data. It securely prompts for player phone/PIN and admin email/password, validates both credentials without recording tokens, creates a unique OPEN session through the real Admin API, and inserts one valid test-owned card display fixture with a null financial funding snapshot. The generated fixture IDs are written to a per-run manifest and the persistent QA test-data registry. Flutter runs with `API_BASE_URL=http://10.0.2.2:8082` and `BINGO_PLAYER_PURCHASES_ENABLED=false`; its secret define file is ACL protected and deleted after use. No new player is registered because the current positive signup reward policy could trigger financial grant behavior.

The before/after snapshots contain aggregate table counts, room price/currency and active state, session identity/state/price/currency, ticket identity/ownership/operation/grid/funding-snapshot presence, and a SHA-256 digest of wallet balances. Reconciliation allows exactly one run-owned session and ticket; it verifies every retained session and ticket is unchanged, wallet balances and finance-table counts are unchanged, and the UI-displayed prices match MySQL. It saves a JSON manifest, fixture record, snapshots, and sanitized integration log under `docs/qa/evidence/module-06a9/`. Test-owned fixtures are retained and registered; no cleanup deletes historical or test records.

## Exact next action

Run once from normal VS Code PowerShell as `EQUAR-DELL-XPS1\16414`, while the verified backend remains healthy:

```powershell
Set-Location D:\GitHub\bingo-platform
.\scripts\qa\Invoke-AndroidPlayerFunctionalE2E.ps1
```

The runner prompts for an existing authorized QA player phone/PIN and active QA administrator email/password without putting secrets in the command line or evidence. Expected output is a manifest path under `docs/qa/evidence/module-06a9/`, a retained fixture manifest, before/after DB snapshots, a sanitized integration log, and per-check results. If ADB or a safety gate fails before fixture creation, it writes a `BLOCKED` manifest and exits nonzero. Review any created fixture IDs and evidence before changing scenario status.

## Protection status

Only `bingo_qa_test` is allowed. Existing `DRAWING` sessions remain untouched. The scheduler and financial posting remain disabled; purchase is disabled in the app. No ticket, claim, account, session, schema, or grant was created or modified by this Codex run. A normal-user runner invocation is authorized to create one uniquely named session and one nonfinancial ticket/card display fixture and retain them. Port 8080, commits, and pushes were not touched.
