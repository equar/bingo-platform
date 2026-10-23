# QA Module Status

Status values: COMPLETE, PASS, FAIL, BLOCKED, NOT RUN, or a scoped qualification when technical verification and production readiness differ. A module is COMPLETE only when all mandatory scenarios have passed.

| Module | Status | Notes |
| --- | --- | --- |
| 00 — Local QA environment | COMPLETE | MySQL, backend, admin and public website, Flutter toolchain, API 37 emulator, APK launches, and persistent QA accounts are verified. Database-backed tests use guarded bingo_qa_test; persistent bingo_qa accounts were preserved. Latest complete backend regression run: 105/105 PASS. See [Module 00](modules/module-00-environment.md). |
| 01 — Authentication and authorization | COMPLETE | Prior admin Chromium suite: 7/7 PASS. A11 valid expired signed JWT in the actual admin browser: PASS. P17 restricted player flow through registration, admin status management, and Android login: PASS. Player/agent Android authentication and process-restart checks: PASS. Latest full backend suite: 105/105 PASS. QA_PLAYER_003 is retained as PENDING; the original five users and two agent profiles are unchanged. See [Module 01](modules/module-01-authentication.md). |

| 02 — Admin dashboard and management | INCOMPLETE | Module 02E room configuration and selected session controls are partially verified. Claim browser workflows, ticket-bearing cancellation/refunds, session progression/draw and approved prize settlement remain unverified or blocked. Module 02D production deposit/adjustment posting remains blocked; other consequential admin operations remain. See [Module 02](modules/module-02-admin-management.md), [route matrix](modules/module-02-admin-test-matrix.md), [02C closure matrix](modules/module-02c-closure-matrix.md), [02C report](modules/module-02c-admin-closure.md), [02A](modules/module-02a-agent-lifecycle.md), [02B](modules/module-02b-agent-float.md), [02D](modules/module-02d-admin-operations.md), and [02E](modules/module-02e-game-admin.md). |
| 02A — Agent onboarding, approval and suspension | PASS | Real Chromium roster/approval/suspension 3/3; Flutter Android onboarding, pending denial, approved login (two agents), and suspended login passed on emulator-5554; backend focused regression 24/24; read-only `bingo_qa` confirms new records and V15. Original agent profiles remain unchanged. See [Module 02A](modules/module-02a-agent-lifecycle.md). |
| 02B — Agent float management | TECHNICALLY VERIFIED — PRODUCTION FUNDING BLOCKED | Chromium approval/reconciliation E2E passed 1/1; Android posted-state E2E passed; frontend 75/75 with lint/build; backend 105/105 against `bingo_qa_test`; read-only `bingo_qa` confirms V18, independent verifier/approver, two balanced synthetic journals and two ETB 25.50 float credits (ETB 51.00 cumulative). Synthetic QA posting is not real settlement; production remains disabled pending finance approvals and bank integration. Module 02 overall remains INCOMPLETE. See [Module 02B](modules/module-02b-agent-float.md). |
| 02D — Player, wallet, deposit and support admin operations | TECHNICALLY VERIFIED — PRODUCTION FINANCIAL POSTING BLOCKED | QA and isolated `bingo_qa_test` migrated to V20 without changing V19's checksum; full backend suite 110/110, updated wallet integration 9/9, production-simulation guard 2/2, frontend 78/78 with lint/build, and real Chromium D01–D20 1/1 passed. Read-only reconciliation confirms original claims 1–7 and Agent #3 float/journals preserved; synthetic postings are balanced and idempotent. Finance/accounting/evidence/reversal policy approvals remain required before production credits or adjustments. Module 02 overall remains INCOMPLETE; no later module started. See [Module 02D financial security report](modules/module-02d-financial-security.md), [admin operations report](modules/module-02d-admin-operations.md), and [test matrix](modules/module-02d-test-matrix.md). |
| 02E — Game administration and operational controls | PARTIAL | Room configuration concurrency/validation, DRAFT scheduling, inactive-room protection, ticket-free cancellation, audit persistence, and fail-closed prize settlement guard are covered. Claim browser workflows lack a safe QA fixture; draw/session progression, ticket-bearing cancellation/refunds and approved payout policy remain unverified. See [Module 02E report](modules/module-02e-game-admin.md) and [test matrix](modules/module-02e-test-matrix.md). Module 02 remains INCOMPLETE. |

## Isolated integration-test database

The separate bingo_qa_test database was created on the same local MySQL 8.0.36 instance. The test account bingo_qa_test_runner has ALL PRIVILEGES only on bingo_qa_test.*; its global grant is USAGE only. MySQL denied this account access to both bingo and bingo_qa. The password and JDBC URL are in ignored bingo-backend/.env.test; use [.env.test.example](../../bingo-backend/.env.test.example) to configure another local machine.

MySqlIntegrationTest requires BINGO_TEST_MYSQL_URL, username, and password. Before a Spring integration context starts it checks for a local MySQL URL on port 3306 whose database path is exactly /bingo_qa_test, confirms the connected catalog, and verifies that the test user is denied access to bingo and bingo_qa. Missing settings, another host/database, or access to either persistent schema fail closed. There is no implicit Testcontainers fallback. Surefire runs A00FlywayUpgradeRecoveryIntegrationTest first; it cleans only bingo_qa_test, tests migration from V6 through the current migration and checksum repair, and leaves the schema migrated at the current version for the rest of the suite.

For the current workspace, run from bingo-backend after loading the ignored file into the current PowerShell process:

    $testEnv = @{}
    Get-Content .env.test | ForEach-Object {
      if ($_ -match '^([^#=]+)=(.*)$') { $key = $matches[1]; $value = $matches[2]; $testEnv[$key] = $value }
    }
    $env:BINGO_TEST_MYSQL_URL = $testEnv['BINGO_TEST_MYSQL_URL']
    $env:BINGO_TEST_MYSQL_USERNAME = $testEnv['BINGO_TEST_MYSQL_USERNAME']
    $env:BINGO_TEST_MYSQL_PASSWORD = $testEnv['BINGO_TEST_MYSQL_PASSWORD']
    mvn -s ../.tmp/maven-settings.xml '-Dmaven.repo.local=../.tmp/m2' test
    Remove-Item Env:\BINGO_TEST_MYSQL_URL, Env:\BINGO_TEST_MYSQL_USERNAME, Env:\BINGO_TEST_MYSQL_PASSWORD

Do not point these variables at bingo or bingo_qa. The admin activation integration test captures its outgoing message in-process, so the backend suite does not require an external SMTP catcher.

Environment variables are listed in .env.qa.example. BINGO_QA_JWT_SECRET must be a unique random secret of at least 32 UTF-8 bytes. SMTP is directed to local 127.0.0.1:1025; account-email flows remain unavailable until a local SMTP catcher is installed and started.

## Browser QA

Admin and website URLs can be set with BINGO_E2E_ADMIN_URL and BINGO_E2E_WEBSITE_URL; the QA backend URL is BINGO_E2E_BACKEND_URL. Defaults for reusable helpers and Playwright are http://localhost:5174, http://localhost:4321, and http://localhost:8081. The QA admin can be started from bingo-admin with npm run dev -- --mode qa --host 0.0.0.0 --port 5174; its ignored .env.qa is copied from .env.qa.example. Admin E2E credentials must be supplied as BINGO_E2E_ADMIN_EMAIL and BINGO_E2E_ADMIN_PASSWORD; player credentials use BINGO_E2E_PRIMARY_PHONE and BINGO_E2E_PRIMARY_PASSWORD. Do not place credentials in tracked files or VITE_* variables.

bingo-admin/tests/e2e/support/qaHelpers.ts provides admin login, navigation, screenshots, HTTP response checks, and failure artifacts. Playwright defaults and E2E manifest URLs target the isolated QA admin/backend ports. Existing specs can create permanent business records; they have not been run as part of Module 0. QA admin/player credentials are available in ignored bingo-backend/.env.qa for local E2E setup; never commit or print them.

## Mobile QA

The emulator reaches the Windows-hosted QA backend at http://10.0.2.2:8081. The existing API 37 AVD is Medium_Phone_API_37.0 and adb identifies it as emulator-5554. Run adb and Flutter in the Windows account that owns the Android user profile; this execution environment's synthetic sandbox SID has no Windows profile registration and causes adb to resolve an empty profile path. Do not hardcode a profile path.

For each process, derive the SDK and Android configuration directories from USERPROFILE:

    $sdk = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    $env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
    $env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
    $env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
    $env:Path = "$sdk\platform-tools;$sdk\emulator;C:\src\flutter\bin;$env:Path"

The authentication-only suites are under bingo-app/integration_test and bingo-agent/integration_test. Use the ignored e2e-secrets.json define files and explicitly pass API_BASE_URL. The integration driver writes named screenshots to docs/qa/evidence. For session persistence, flutter drive --keep-app-running preserves app data; force-stop the package before the separate session-restore suite.

The final player and agent auth-only E2E suites passed on emulator-5554. P17 additionally registered a dedicated player through the Flutter UI and verified its PENDING sign-in rejection on the emulator; the player remains in that state. Screenshots and sanitized logs are under docs/qa/evidence. The player app allows local HTTP only in debug mode; production builds still require HTTPS. The app manifests retain internet permission for HTTPS production endpoints. The agent Android scaffold was generated earlier because its repository had no android/ directory despite its README listing Android support.

Final Flutter analysis passed for both apps, and the relevant player Android integration tests passed. The current full backend suite has 94 passing tests. Keep QA accounts and business records between modules. Do not delete/reset records or change terminal states to reuse them.

## Data retention

Keep QA accounts and business records between modules. Use names such as QA_PLAYER_001, QA_AGENT_001, and QA_SESSION_001, synthetic phone numbers accepted by application validation, and unique references/idempotency keys. Add every persistent record to [test-data-registry.md](test-data-registry.md). Scenarios requiring initial states must create new uniquely identified records; never delete records or reverse terminal states to reuse them.
