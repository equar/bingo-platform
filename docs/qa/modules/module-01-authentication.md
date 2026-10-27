# Module 01 — Authentication and Authorization

Date: 2026-10-23; latest verification: 2026-10-27
Status: **PARTIAL — authenticated Player, Agent, and Admin journeys verified with documented scope gaps**

Historical evidence: the two then-outstanding authentication scenarios passed. A11 used a valid expired HMAC-signed JWT in the real admin browser. P17 registered a dedicated player in the real Android app, set the player to PENDING through the admin console, and verified sign-in and protected access were denied. The original five QA users and both agent profiles retain their previous state. The full backend suite passed after the status-management change. No unresolved critical or high authentication defect was found in that scope. Module 02 was not started.

## Player analysis and fixture inventory — 2026-10-27

Player Flutter analysis passed with no issues and `flutter test` passed 32/32. The earlier read-only fixture inventory is preserved in [preflight evidence](../evidence/module-06a9/QA_M06A9_20261027_fixture-preflight.json).

## Latest authorized E2E run — 2026-10-27

**Result: authenticated Android and Chromium journeys ran against the verified isolated test backend.** Backend PID 23908 listened on port 8082 with profile `android-test`, database `127.0.0.1:3306/bingo_qa_test`, Flyway V22, and health UP. The connected MySQL identity was schema-scoped to `bingo_qa_test`; scheduler and financial posting/simulation controls remained disabled. The separate service on port 8080 was left untouched.

| Surface / scenario | Status | Result |
| --- | --- | --- |
| Synthetic fixtures | PASS | Retained Player user 378, Admin user 379, approval candidate user 380 / Agent 27, and pending user 381 / Agent 28. Agent 27 was approved through the normal Admin UI; Agent 28 remains pending. Credentials are only in ignored local configuration. |
| Player Android authentication and safe UI journeys | PASS — SCOPED | Real emulator login, validation, lobby/session display, room price/status, read-only ticket preview, live-game view with purchase disabled, logout, and persistence passed. See [Player run report](../evidence/module-06a9/QA_M06A9_20261027_124227_686.json) and [sanitized integration log](../evidence/module-06a9/QA_M06A9_20261027_124227_686-flutter-integration.log). |
| Player card assignment/purchase | BLOCKED | No financially approved Player card fixture exists. No ticket was inserted and no purchase or wallet posting was attempted. The retained nonfinancial session is #47 in room #38. |
| Agent Android authentication and read-only workflow | PASS — SCOPED | Real emulator test passed approved phone/PIN login, pending-Agent rejection, invalid credentials and throttling, session restore/expiry/logout, and authenticated Support ticket listing. Protected financial/account counts were unchanged. Cross-role authorization restrictions and other Agent workflows were NOT RUN. See [Agent run summary](../evidence/module-01-agent-authentication/QA_M01_AGENT_20261027_125207_750/run-summary.json) and [integration log](../evidence/module-01-agent-authentication/QA_M01_AGENT_20261027_125207_750/flutter-integration.log). |
| Admin Chromium authenticated approval | PASS | Normal Admin UI approval passed 1/1 on retry; Agent 27 became APPROVED and Agent 28 remained PENDING. The evidence log preserves the earlier failed attempt and successful retry. See [approval result](../evidence/module-06a9/QA_M06A9_20261027_admin-approval.json) and [run log](../evidence/module-06a9/QA_M06A9_20261027_admin-approval.log). |
| Financial posting and prohibited scenarios | BLOCKED — NOT ATTEMPTED | Scheduler and financial posting/simulation remained disabled. No ticket purchase, float funding, payout, settlement, or other financial posting was attempted. |
| Maven test suite | BLOCKED | No separate disposable Maven database/configuration was available; Maven was not pointed at `bingo_qa_test`. |

Player Flutter analysis and unit tests passed (32/32). Agent Flutter analysis passed and unit tests passed (12/12). The Agent Android integration run exited 0; its report now maps only exercised scenarios to PASS and leaves cross-role checks NOT RUN. Admin Vitest passed 84/84, lint/build passed, and Playwright discovery listed 57 tests across 17 files. The authenticated approval E2E passed 1/1 on retry; it was not repeated after the final test-helper error-path hardening because the create-only candidate is already approved. The Agent report and screenshot inventory are in the run evidence above. Retained fixture identities and purposes are recorded in the [test-data registry](../test-data-registry.md).

## Initial authorized preflight attempt — 2026-10-27 (historical; superseded by the verified run above)

**Result: BLOCKED before fixture provisioning and authenticated UI.** Fixture setup and the normal Agent approval workflow were authorized, but the required live-runtime preflight did not pass. Port 8082 has no listener. Port 8080 is healthy, but its process command line does not declare the `android-test` profile and no runtime identity proves its datasource or safety flags. The launcher was not started.

| Gate / scenario | Status | Result |
| --- | --- | --- |
| Actual backend profile/database identity | BLOCKED | No backend listens on required `127.0.0.1:8082`; the healthy process on 8080 has no verified runtime identity. |
| Configured MySQL target and account grants | PASS — READ ONLY | Using ignored `.env.test`, a read-only identity query confirmed `bingo_qa_test` at `127.0.0.1:3306`; grants are confined to that schema. This does not verify a backend-connected identity. |
| Runtime scheduler and financial controls | BLOCKED | `application-android-test.yml` and launcher arguments configure them disabled, but actual runtime flags cannot be verified without the approved backend process. |
| Launcher policy regression tests | PASS | Both backend identity and Agent E2E policy scripts passed. These are policy tests; actual launchers were not run. |
| Fixture setup / Agent approval | NOT RUN | No provisioning, credential reset, role change, approval, or database write was attempted because the live-runtime gate failed. |
| Player Android authentication and journeys | BLOCKED | Approved backend was unavailable; no Android UI journey ran. |
| Agent Android authentication and workflows | BLOCKED | Approved backend was unavailable; no Android UI journey ran. |
| Admin Chromium workflows and resulting mobile state | BLOCKED | Approved backend was unavailable; no authenticated browser workflow or Agent approval ran. |
| Maven backend tests | BLOCKED | `.env.maven-test` and the required Maven database environment variables are absent; no disposable Maven test database was verified. |

No fixtures were created, and no existing data was modified or deleted. See [authorized-run preflight evidence](../evidence/module-06a9/QA_M06A9_20261027_authorized-run-preflight.json). Historical PASS results below remain limited to their recorded scopes.

## Earlier verification attempt — 2026-10-26 (historical)

**Result: BLOCKED before authenticated UI execution.** The previously recorded PASS results remain historical and are not claimed as fresh verification against the isolated `bingo_qa_test` schema.

| Surface | Current result | Evidence / blocker |
| --- | --- | --- |
| Backend runtime and MySQL identity | PASS | Live backend PID 20588 reports profile `android-test`, schema `bingo_qa_test`, loopback MySQL port 3306, Flyway V22, and disabled scheduler/financial posting. The authenticated MySQL account is schema-scoped to `bingo_qa_test`; health and DB health are UP. |
| Protected data reconciliation | PASS — READ ONLY | Counts for users, Agent profiles/floats/requests/transactions/journals/journal lines, wallets, and wallet transactions match the Agent preflight baseline. No test data or financial records were created. |
| Player Android authentication and functional UI | BLOCKED | The guarded runner stopped before login because the configured local Player credential does not resolve to exactly one ACTIVE PLAYER in `bingo_qa_test`. No account, session, or ticket fixture was created. See [Player run report](../evidence/module-06a9/QA_M06A9_20261026_202153_463.json) and [Module 06A-9](module-06a9-android-player-functional-e2e.md). |
| Agent Android phone/PIN authentication | BLOCKED | The guarded launcher verified the isolated backend but found no exact approved and pending Agent fixtures in `bingo_qa_test`; login and fixture creation were not attempted. See [Agent run report](../evidence/module-01-agent-authentication/QA_M01_AGENT_20261026_201829_695/run-summary.json). |
| Agent primary workflow, validation, and persistence | BLOCKED | No approved Agent fixture was available for Android UI execution. Float top-up/approval and any financial posting were not attempted; their separate approval and posting safeguards remain in force. |
| Admin Chromium administrative workflow | BLOCKED | No authorized Admin E2E credentials were available. Authenticated administrative actions and resulting Android state changes were not run. Admin unit tests (84/84), lint, and production build passed; these do not replace Chromium E2E. |
| Backend regression suite | NOT RUN | The Maven test environment file `.env.maven-test` was unavailable; no Maven suite was run against `bingo_qa_test`. |

The Player and Agent Flutter unit tests passed (32 and 12, respectively). Player `flutter analyze` reported one unnecessary cast in `integration_test/module_06a9_player_e2e_test.dart:118`; this is a static warning, not an executed UI failure. Agent analysis passed. Guard-policy checks for missing/unsafe targets and mismatched runtime identity passed without contacting the targets. No financial posting flags were enabled.

Execution context: Windows account `EQUAR-DELL-XPS1\16414`; repository root `D:\GitHub\bingo-platform`; available device `emulator-5554` (Android 17/API 37). Commands and outcomes:

- `.\scripts\qa\Invoke-AndroidPlayerFunctionalE2E.ps1` — BLOCKED before login at Player fixture validation.
- `.\scripts\qa\Invoke-AgentAndroidAuthenticationE2E.ps1 -EnvironmentName android-test` — BLOCKED before login at approved/pending Agent fixture validation.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\qa\Test-AgentAndroidE2EPolicy.ps1` — PASS; rejects missing/unsafe configuration and mismatched runtime identity without target access.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\qa\Test-AndroidTestBackendPolicy.ps1` — PASS; rejects unsafe datasource identities.
- From `bingo-app` and `bingo-agent`: `flutter test` and `flutter analyze` — unit tests passed; Player analysis reports the lint above and Agent analysis passed.
- From `bingo-admin`: `npm test`, `npm run lint`, and `npm run build` — 84 tests, lint, and production build passed. `npm run e2e` was not run because authorized Admin E2E credentials were unavailable.
- `GET http://127.0.0.1:8082/actuator/health` and read-only MySQL identity/count queries against `bingo_qa_test` — PASS; the protected counts match the Agent preflight report.

The next action is to provision or identify authorized existing fixtures/credentials in the isolated test environment, then rerun the guarded launchers and Admin Chromium workflow; do not create replacement accounts or use persistent `bingo_qa`.

## Historical test evidence — 2026-10-23

The following sections preserve results from the earlier run. They are historical evidence and do not change the BLOCKED outcome of the 2026-10-26 attempt above.

## adb root cause and emulator recovery

The original error, “Cannot mkdir '\\.android': Permission denied”, came from a Windows execution identity mismatch. The sandbox process had a synthetic SID with no Windows profile registration even though USERPROFILE pointed to C:\Users\16414. A Windows SHGetFolderPath(CSIDL_PROFILE) diagnostic returned FILE_NOT_FOUND and an empty profile directory. adb consequently resolved its configuration folder at the filesystem root. Setting HOME, ANDROID_USER_HOME, ANDROID_SDK_HOME, and related variables in that process did not change the Windows profile API result.

Running adb and Flutter under the Windows account that owns the existing profile fixed the issue. The profile’s Android configuration folder was writable, and adb used the existing SDK installation. No user profile was created, no system-wide environment setting was changed, and no AVD or SDK was deleted or recreated.

| Check | Result |
| --- | --- |
| SDK and adb | PASS — SDK resolved from Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'; adb 37.0.1-15733141 |
| adb server | PASS — kill-server, start-server, and devices -l completed without the .android error |
| Emulator | PASS — existing Medium_Phone_API_37.0; device emulator-5554, sdk_gphone64_x86_64, API 37 |
| Boot and storage | PASS — sys.boot_completed=1; /data had 7.0 GB available |
| Flutter device detection | PASS — Flutter listed emulator-5554 as Android x86_64 |
| Backend reachability | PASS — host port 8081 was listening and both apps completed real login requests through http://10.0.2.2:8081 |
| Android environment changes | PASS — process-local ANDROID_HOME, ANDROID_SDK_ROOT, ANDROID_USER_HOME, ANDROID_EMULATOR_HOME, ANDROID_AVD_HOME, and PATH were set using the current USERPROFILE |

The actual runtime commands used adb and Flutter from the owning Windows profile. API_BASE_URL was passed from ignored e2e-secrets.json; no API fallback was added and no release networking setting was weakened.

## Admin Chromium E2E

The previously recorded Chromium run remains 7/7 PASS against http://localhost:5174. It covered login, validation, rate limiting and recovery, refresh persistence, logout, protected routes, role enforcement, network recovery, and sensitive browser storage/log checks. Screenshots and Playwright artifacts remain under bingo-admin/test-results.

### A11 — valid expired JWT in the admin browser

**Expected:** The API rejects an authentic but expired JWT with HTTP 401; the browser clears the session, returns to login, and cannot restore protected access after refresh. A new login succeeds.

**Actual:** PASS. Playwright logged in through the real admin UI, used the resulting token claims and the explicitly supplied QA signing secret to create an expired HS256 token, then stored it in the browser session. The protected players API returned HTTP 401. The interceptor cleared local storage and redirected to login. Refresh and a second protected-route visit remained at login without another protected request; a fresh login returned the browser to Players and loaded the page. No token was printed or placed in tracked evidence. The isolated test issuer is test code only; no production token endpoint or signature bypass was added.

**Evidence:** [API response summary](../evidence/module-01-admin-expired-jwt-api.json), [login redirect screenshot](../evidence/module-01-admin-expired-session.png), and [reauthenticated screenshot](../evidence/module-01-admin-reauthenticated.png). The original Playwright trace was stored only under ignored local test-results and is not part of the repository.

**Defects/fixes:** No A11 product defect. The existing 401 interceptor and protected route behaved as intended. A rate-limit E2E helper was made threshold-adaptive after the QA server used its configured five-attempt default; its isolated regression passed.

## Player Android E2E

Executed the actual player Flutter integration suite on emulator-5554 with API_BASE_URL=http://10.0.2.2:8081 and the existing QA_PLAYER_001 account. The suite passed. A separate run after adb shell am force-stop com.example.bingo_app passed process-restart restoration and logout. Authentication used existing QA data; repeated failures used per-run synthetic phone identifiers and created no user records.

| ID | Scenario | Result | Evidence / notes |
| --- | --- | --- | --- |
| P01 | App startup and login screen | PASS | Login screenshot captured. |
| P02 | Registration screen opens | PASS | UI toggle exposed the display-name field; no account was submitted. |
| P03 | Existing player login | PASS | Login succeeded with the formatted +251 900 000 004 number. |
| P04 | Invalid PIN | PASS | Wrong six-digit PIN returned an auth error; retry with the correct PIN succeeded. |
| P05 | Repeated failure throttling | PASS | Six UI submissions using a synthetic identity; sixth response displayed AUTH_RATE_LIMITED. |
| P06 | Phone normalization | PASS | Formatted +251 phone authenticated against the existing 251-prefixed account. |
| P07 | Invalid phone | PASS | Malformed phone was rejected by UI validation. |
| P08 | Empty fields | PASS | Empty phone and PIN were rejected locally. |
| P09 | Incorrect PIN length | PASS | Five-digit PIN was rejected locally; no request was sent. |
| P10 | Session persistence after process restart | PASS | App was force-stopped; a fresh integration run restored the stored session. |
| P11 | Logout | PASS | Logout returned to sign-in and secure storage no longer contained the token. |
| P12 | Expired session | PASS | Expired stored session was cleared and sign-in showed the expiry message. |
| P13 | Invalid token | PASS | An invalid bearer token received 401 on the initial lobby request; the app cleared it and returned to sign-in. |
| P14 | Offline login | PASS | Test-only unavailable API endpoint produced an error on the actual login UI. |
| P15 | Network recovery | PASS | Rebuilt with the configured QA API; an invalid PIN response and then a valid login succeeded. |
| P16 | Secure token handling | PASS | Token persisted through FlutterSecureStorage, was not rendered in UI, and was removed on logout. |
| P17 | Pending/disabled player | PASS | New QA_PLAYER_003 was registered through the Android UI and set to PENDING through the admin player-status workflow. See the P17 evidence below. |

### P17 — restricted player access

**Expected:** A player in a non-ACTIVE supported account state cannot sign in or use protected APIs. Existing sessions stop working; wallet state is unchanged. Existing QA players remain untouched.

**Lifecycle inspection:** `AccountStatus` supports `ACTIVE`, `PENDING`, `SUSPENDED`, and `DISABLED`. Player authentication uses Spring Security `isEnabled`, which is true only for ACTIVE; the JWT filter reloads the user and rechecks that state on every protected request. The existing admin users API intentionally accepts only ADMIN/SUPPORT roles, and player administration was read-only, so there was no supported administrative way to apply the existing restriction to a player.

**Fix applied:** Added an admin-only player status endpoint and a status-change action on the admin player detail page. The service accepts only users whose role is exactly PLAYER, requires a nonblank reason, and the mutation is captured by the existing audit aspect. No new lifecycle states or registration rules were invented.

**Actual:** PASS. The Flutter registration UI created QA_PLAYER_003 (User 6), then the actual admin browser changed it from ACTIVE to PENDING. The admin update returned HTTP 200 and generated an audit record. The player Android UI rejected the correct PIN with `AUTH_INVALID_CREDENTIALS`, remained on sign-in, and stored no session. A token obtained while the account was ACTIVE and an anonymous wallet request both received HTTP 401 after the status change. Wallet balances remained unchanged. Backend integration tests also verified both PENDING and DISABLED deny login and invalidate existing bearer access; the feature test checks account role boundaries.

**Persistent state:** QA_PLAYER_003 is retained as PENDING in `bingo_qa`; the original five users and two agent profiles retain their roles/statuses. Its nonsecret identity and final state are in [test-data-registry.md](../test-data-registry.md). The local PIN remains only in ignored `bingo-app/.env.p17.local`.

**Evidence:** [status/API results](../evidence/module-01-p17-status-api.json), [admin screenshot](../evidence/module-01-p17-admin-player-pending.png), [registration screenshot](../evidence/module-01-p17-player-created-active.png), [Android rejection screenshot](../evidence/module-01-p17-player-pending-login-rejected.png), and [sanitized Android run log](../evidence/module-01-p17-restricted-login-run.log).

## Agent Android E2E

Executed the actual agent Flutter app on emulator-5554 with the existing approved and pending QA agent identities. The final UI suite passed. A separate run after adb shell am force-stop com.equar.bingo.bingo_agent passed process-restart restoration and logout.

| ID | Scenario | Result | Evidence / notes |
| --- | --- | --- | --- |
| G01 | App startup and login screen | PASS | Three fields and login screen rendered. |
| G02 | Approved agent login | PASS | Existing approved account reached the agent dashboard. |
| G03 | Invalid PIN | PASS | Wrong PIN was rejected and left the form available. |
| G04 | Repeated failure throttling | PASS | Six UI attempts used a per-run synthetic identity; a follow-up probe returned HTTP 429. |
| G05 | Pending agent restriction | PASS | Existing pending agent could not enter the dashboard; status was preserved. |
| G06 | Invalid phone | PASS | Malformed phone was rejected by backend authentication and shown as sign-in failure. |
| G07 | Empty fields | PASS | All three required-field validations appeared. |
| G08 | Session persistence | PASS | Secure session restored after force-stop and fresh integration launch. |
| G09 | Logout | PASS | Logout returned to sign-in and removed secure session data. |
| G10 | Expired JWT | PASS | Expired token was cleared on restore and expiry message shown. |
| G11 | Offline behavior | PASS | Unavailable endpoint showed sign-in failure without navigating to the dashboard. |
| G12 | Network recovery | PASS | Rebuilt with the QA endpoint and successfully logged in afterward. |
| G13 | Unauthorized agent operation | PASS | Unauthenticated app state exposes no protected navigation; anonymous API rejection is also covered by backend security tests. |
| G14 | Agent account status | PASS | Approved identity accepted; pending identity rejected. |

## Backend authorization and security

The latest complete Maven suite ran against the guarded MySQL database bingo_qa_test. Result: **94 tests, 0 failures, 0 errors, 0 skipped**. This includes the player status-management and authorization regressions. The targeted AuthSecurityIntegrationTest has 11 passing tests. No Docker, Testcontainers, or H2 was used.

| Verification | Result |
| --- | --- |
| Anonymous protected API access and role boundaries | PASS |
| Invalid signature, expired JWT, and modified claims | PASS |
| Missing and malformed bearer headers | PASS |
| Protected Actuator endpoints | PASS |
| Weak/fallback JWT secret rejection | PASS |
| Client role elevation prevention | PASS |
| Authentication throttling | PASS |
| Failed authentication leaves account and wallet state unchanged | PASS |

Full suite output is under bingo-backend/target/surefire-reports. The full-suite command was mvn -s ../.tmp/maven-settings.xml '-Dmaven.repo.local=../.tmp/m2' test from bingo-backend after loading the ignored .env.test into the current PowerShell process. The fail-closed runner targeted bingo_qa_test only.

## Persistent database verification

A read-only query of bingo_qa after the scenarios confirmed 6 users, 2 agent profiles (1 APPROVED, 1 PENDING), original user IDs 1–5 and their previous states unchanged, QA_PLAYER_003/User 6 PENDING, and Flyway V14. Only the explicitly authorized new P17 player was created; no existing user/agent row or wallet balance was changed by the restriction action. Authentication attempts, hashed throttle buckets, and status-change audit records remain. The query output is [module-01-database-verify.txt](../evidence/module-01-database-verify.txt).

## Defect found and fixed

The agent login controller marked the entire auth gate as loading while waiting for the sign-in request. AuthGate disposed the form, and a failed login then called setState on the disposed form. The controller now keeps request progress local to the form; the catch handler checks mounted. The final on-device failed-login regression passed. Details are in [module-01-agent-login-dispose-defect.md](../evidence/module-01-agent-login-dispose-defect.md).

P17 also exposed the missing admin player-status management action. The new endpoint and admin UI provide a reasoned, audited status change without broadening the existing admin user API's permitted roles. Backend authentication enforcement already existed and is now covered through the status transition and stale-token regression.

Player restore also now clears an expired session from secure storage and presents the expiry message. Agent restore reads the JWT exp claim, clears expired or invalid sessions, and presents the expiry message. Neither app logs or renders the access token.

## Evidence and commands

The actual suites used:

- Player: flutter drive --keep-app-running --driver=test_driver/module_01_authentication.dart --target=integration_test/module_01_authentication_test.dart -d emulator-5554 --dart-define-from-file=e2e-secrets.json
- Player process restore: force-stop com.example.bingo_app, then flutter drive with target integration_test/module_01_session_restore_test.dart.
- Agent: same driver command from bingo-agent, with its ignored e2e-secrets.json.
- Agent process restore: force-stop com.equar.bingo.bingo_agent, then flutter drive with target integration_test/module_01_session_restore_test.dart.

Credentials remained in ignored local files. Sanitized run logs are [player](../evidence/module-01-player-run.log), [player restart](../evidence/module-01-player-session-restore-run.log), [agent](../evidence/module-01-agent-run.log), and [agent restart](../evidence/module-01-agent-session-restore-run.log). Named player and agent UI screenshots are stored beside those logs in docs/qa/evidence.

Final flutter analyze passed for bingo-app and bingo-agent. Relevant Playwright checks passed for A11, P17, and the A06/A07 rate-limit case; the PlayerDetailPage suite passed 8 tests and the admin production build passed. The P17 Android registration and restricted-login integration tests passed on emulator-5554. The full backend suite passed 94 tests. Module 02 was not started.
