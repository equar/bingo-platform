# Module 06A-9 — Android Player Functional E2E

Date: 2026-10-26; latest execution: 2026-10-27
Status: **PARTIAL — authenticated Player display journeys passed; card journey remains blocked**

## Latest authorized Player Android run — 2026-10-27

**Result: PARTIAL; the authenticated safe-display journey completed on emulator-5554 against the verified isolated backend.** Backend PID 23908 listened on port 8082 with profile `android-test`, database `127.0.0.1:3306/bingo_qa_test`, Flyway V22, and health UP. The MySQL runner account was scoped to that schema. Scheduler and financial posting/simulation remained disabled.

| Check / scenario | Status | Result |
| --- | --- | --- |
| Android-test backend identity and safety controls | PASS | Runtime profile, datasource/schema, Flyway V22, health, schema-only account scope, and disabled scheduler/financial controls were verified by the guarded runner. |
| Player Android authentication and validation | PASS | The real emulator UI authenticated the retained ACTIVE Player; required-field and credential validation completed. |
| Lobby, room/session, price/status and read-only ticket preview | PASS | The UI displayed retained active room #38 and OPEN session #47 at 1.00 ETB. The ticket preview was informational only. |
| Live-game view and logout/persistence | PASS | Live-game UI opened with purchase disabled; logout and persisted session behavior passed. Flutter integration exit code was 0. |
| Player card assignment/purchase | BLOCKED | No financially approved Player card exists. The test reported the blocker in UI/evidence; no ticket row, purchase, or wallet mutation was attempted. |
| Database/history/financial reconciliation | PASS | Session #47 was created through the normal Admin API and retained. Existing room/session/ticket records and wallet balances were unchanged; no test ticket was inserted. |
| Flutter Player analysis and unit tests | PASS | Analysis passed; unit tests passed 32/32. |
| Maven backend tests | BLOCKED | No distinct disposable Maven database/configuration was available. Maven was not pointed at `bingo_qa_test`. |

Evidence: [run summary](../evidence/module-06a9/QA_M06A9_20261027_124227_686.json), [fixture](../evidence/module-06a9/QA_M06A9_20261027_124227_686-fixture.json), [database before](../evidence/module-06a9/QA_M06A9_20261027_124227_686-database-before.json), [database after](../evidence/module-06a9/QA_M06A9_20261027_124227_686-database-after.json), and [sanitized Flutter log](../evidence/module-06a9/QA_M06A9_20261027_124227_686-flutter-integration.log).

## Player unit verification and fixture inventory — 2026-10-27

The unnecessary `Widget` cast at line 118 was removed; targeted Player analysis passed and `flutter test` passed 32/32. The earlier read-only fixture inventory is preserved in [preflight evidence](../evidence/module-06a9/QA_M06A9_20261027_fixture-preflight.json).

## Initial authorized preflight attempt — 2026-10-27 (historical; superseded by the run above)

**Status: BLOCKED before fixture provisioning and authenticated UI execution.** Fixture creation was authorized, but the required Android-test backend is not running on port 8082. The healthy backend on port 8080 has no verified runtime profile/database identity. Read-only MySQL checks against the configured connection confirmed `bingo_qa_test` on loopback with schema-scoped grants, but cannot prove the absent backend is connected to it. Scheduler and financial controls are disabled in test-profile configuration, but runtime controls remain unverified. No fixture or account was changed, and no UI test was launched.

| Check / scenario | Status | Result |
| --- | --- | --- |
| Android-test backend identity and health | BLOCKED | Required port 8082 has no listener. Port 8080 health is UP, but it is not verified as the isolated Android-test backend. |
| Configured MySQL identity and grant scope | PASS — READ ONLY | Target `127.0.0.1:3306/bingo_qa_test` and schema-only grants verified using ignored local configuration. No write query was run. |
| Runtime scheduler/financial flags | BLOCKED | Source test-profile configuration sets scheduler and financial posting/simulation flags disabled; live runtime values were unavailable. |
| Backend/Agent launcher policy tests | PASS | Both non-mutating policy regression scripts passed; the actual guarded launchers were not run. |
| Fixture provisioning and Agent approval | NOT RUN | Stopped before any mutation because the live backend identity gate failed. |
| Player Android login, validation, lobby/session/card, logout and persistence | BLOCKED | No approved Android-test backend; no authenticated Player journey executed. |
| Agent Android phone/PIN login, pending/approved behavior and non-financial workflows | BLOCKED | No approved Android-test backend; no authenticated Agent journey executed. |
| Admin Chromium workflows and resulting mobile state | BLOCKED | No approved Android-test backend; no authenticated Admin action or normal Agent approval executed. |
| Financial scenarios | BLOCKED — NOT ATTEMPTED | Financial posting/simulation was not enabled, and no prohibited financial path was approached. |
| Maven tests | BLOCKED | Separate disposable Maven DB/configuration was unavailable; Maven tests were not pointed at `bingo_qa_test`. |

See [authorized-run preflight evidence](../evidence/module-06a9/QA_M06A9_20261027_authorized-run-preflight.json). No fixtures were created and no existing database data was changed or deleted.

## Earlier verification result — 2026-10-26 (historical)

**Status: BLOCKED before authenticated Player UI execution.** Run `QA_M06A9_20261026_202153_463` stopped before login because the configured local Player credential did not resolve to exactly one ACTIVE PLAYER in `bingo_qa_test`. No API login, fixture creation, or Android test launch occurred. The exact blocker is recorded in the [run report](../evidence/module-06a9/QA_M06A9_20261026_202153_463.json).

| Check / scenario | Status | Result |
| --- | --- | --- |
| Live backend and database identity | PASS | Backend PID 20588 reports profile `android-test`, schema `bingo_qa_test`, `127.0.0.1:3306`, Flyway V22, and disabled scheduler/financial posting. Health and DB health are UP. |
| Android emulator discovery | PASS | `emulator-5554` is available, but no app or test APK was launched in this run. |
| Player credential preflight | BLOCKED | The configured local account does not resolve to exactly one ACTIVE PLAYER; no login was attempted. |
| Login, validation, lobby refresh, room price/currency, logout, and protected-state behavior | NOT RUN | These require the authorized Player identity and an actual Android UI run. |
| OPEN-session/card-display flow and persistence | NOT RUN | No Admin credential or fixture setup was performed; no session or ticket fixture was created. Purchase remained disabled. |
| Authenticated Admin Chromium actions and resulting Player state | BLOCKED | Admin E2E credentials were unavailable; no administrative action was attempted. |
| Database reconciliation | PASS — READ ONLY | The guarded Agent preflight baseline and final read-only query agree for users, Agent profiles/floats/requests/transactions/journals/journal lines, wallets, and wallet transactions. No mutation occurred. |
| Flutter Player unit tests | PASS | 32 tests passed. |
| Flutter Player analysis | FAIL — STATIC LINT | One unnecessary cast remains at `integration_test/module_06a9_player_e2e_test.dart:118`; no runtime failure was observed. |
| PowerShell launcher and safety checks | PASS | Launcher parsing and the backend identity/policy regression checks passed; no unsafe database target was contacted. |
| Backend Maven suite | NOT RUN | `.env.maven-test` was unavailable; Maven tests were not run against `bingo_qa_test`. |
| Financial controls | PASS — PRESERVED | Backend scheduler and simulated posting remained disabled. The Player purchase scenario was not reached; no purchase or other finance operation was attempted. |

Historical device evidence remains in its original reports. This current attempt does not replace or extend those PASS results.

## Prior offline-context preflight (historical; not the current result)

The following rows preserve an earlier attempt that ran under `CodexSandboxOffline`. Its ADB profile-permission blocker was specific to that execution context; the current attempt reached the Player fixture-validation gate instead.

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

## Prepared executable coverage (not executed in the current run)

Added [the guarded normal-user runner](../../../scripts/qa/Invoke-AndroidPlayerFunctionalE2E.ps1) and [the real Flutter integration scenario](../../../bingo-app/integration_test/module_06a9_player_e2e_test.dart). The scenario uses the actual `BingoApp`, real Android instrumentation, real Spring Boot API, and existing QA player credentials. It tests form validation; one invalid credential response followed by valid authentication; lobby load and refresh; existing room price/currency; optional OPEN-session navigation, purchase-disabled UI, and return/reopen; then logout and unauthenticated state.

The PowerShell runner refuses `CodexSandboxOffline`; verifies the saved backend profile/schema/flags, health and listener PID, MySQL runtime schema/port, existing active PLAYER and ADMIN identities, emulator and installed Player package; and snapshots read-only QA data. It securely prompts for player phone/PIN and admin email/password, validates both credentials without recording tokens, creates a unique OPEN session through the real Admin API, and inserts one valid test-owned card display fixture with a null financial funding snapshot. The generated fixture IDs are written to a per-run manifest and the persistent QA test-data registry. Flutter runs with `API_BASE_URL=http://10.0.2.2:8082` and `BINGO_PLAYER_PURCHASES_ENABLED=false`; its secret define file is ACL protected and deleted after use. No new player is registered because the current positive signup reward policy could trigger financial grant behavior.

The before/after snapshots contain aggregate table counts, room price/currency and active state, session identity/state/price/currency, ticket identity/ownership/operation/grid/funding-snapshot presence, and a SHA-256 digest of wallet balances. Reconciliation allows exactly one run-owned session and ticket; it verifies every retained session and ticket is unchanged, wallet balances and finance-table counts are unchanged, and the UI-displayed prices match MySQL. It saves a JSON manifest, fixture record, snapshots, and sanitized integration log under `docs/qa/evidence/module-06a9/`. Test-owned fixtures are retained and registered; no cleanup deletes historical or test records.

## Next action to unblock

After an authorized existing ACTIVE Player identity and Admin E2E credentials are made available for the isolated schema, rerun from normal VS Code PowerShell as `EQUAR-DELL-XPS1\16414` while the verified backend remains healthy:

```powershell
Set-Location D:\GitHub\bingo-platform
.\scripts\qa\Invoke-AndroidPlayerFunctionalE2E.ps1
```

The runner uses existing authorized QA credentials without putting secrets in command lines or evidence. Expected output is a manifest path under `docs/qa/evidence/module-06a9/`, a retained fixture manifest, before/after DB snapshots, a sanitized integration log, and per-check results. If ADB or a safety gate fails before fixture creation, it writes a `BLOCKED` manifest and exits nonzero. Do not create replacement accounts or fixtures merely to bypass the current blocker.

## Protection status

Only `bingo_qa_test` is allowed. Existing `DRAWING` sessions remain untouched. The scheduler and financial posting remain disabled; purchase is disabled in the app. No ticket, claim, account, session, schema, grant, or financial record was created or modified by this run. A future authorized runner invocation may create one uniquely named session and one nonfinancial ticket/card display fixture and retain them after all credential and identity checks pass. Port 8080, commits, and pushes were not touched.
