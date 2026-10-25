# Module 04D-3 — Claim E2E Safety Gates and Test Configuration Hardening

Disposition: **COMPLETE FOR CONFIGURATION HARDENING — ordinary test-profile financial switches now default off; synthetic posting requires explicit test-only opt-in and the disposable Maven schema/account. Database-backed claim E2E remains BLOCKED, so Module 04D remains PARTIAL.**

Scope: Module 04D-3 only. No database connection, test fixture, claim, wallet operation, settlement, or financial posting occurred. No destructive Maven test ran.

## Unsafe configuration found

At the end of Module 04D-2, `application-test.yml` set signup-reward posting, deposit simulation, adjustment simulation, and agent-float simulation to `true`. These settings supported prior positive synthetic financial integration scenarios. The financial guards also permitted `bingo_qa` or `bingo_qa_test`, while the Maven integration-test base had already moved destructive tests to `bingo_qa_maven_test`. Thus default test startup opted into financial simulation and the per-feature schema restrictions were inconsistent with Maven isolation.

Persistent `application.yml` and the Android `android-test` profile already had financial switches disabled. The prize entitlement settlement operation is independently fail-closed: `BingoGameService.settleEntitlement` always returns `PRIZE_SETTLEMENT_BLOCKED`. The configured payment provider only returns configured payment instructions; no external payment network client is used by these services.

## Changes made

- Maven `test` profile financial properties now resolve from `BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED`, defaulting to `false`. A master `app.qa.synthetic-financial-tests-enabled` property is also false by default. The Android test profile explicitly sets the master switch false. Main application configuration explicitly defaults it false.
- `LocalFinancialSimulationGuard` now requires the `test` profile only, rejects `qa`, `prod`, and `android-test`, requires the explicit master switch, and validates the datasource host, port, schema, and dedicated username before opening a connection. If an operation reaches the live verification step, it also checks the actual catalog and `CURRENT_USER()` host.
- Deposit, adjustment, ticket purchase, and signup-reward posting use that common fail-closed policy. Synthetic agent-float operations now use the same guard rather than accepting either persistent QA schema.
- `MavenTestDatabasePolicy` is a pure pre-connection policy shared by `MySqlIntegrationTest` and financial guards. It permits only local MySQL `bingo_qa_maven_test:3306` with `bingo_qa_maven_runner`; it rejects `bingo_qa`, `bingo_qa_test`, remote hosts, other ports/accounts, and `createDatabaseIfNotExist=true`. The existing integration base still authenticates and verifies current account host, grants, denied persistent-schema access, and connected catalog before Spring starts.
- The safe opt-in is `BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED=true` in the isolated Maven process, and only after the dedicated schema/account is provisioned and approved. The placeholder example defaults to false. Do not set it globally. Positive synthetic financial tests need this opt-in; ordinary tests and claim-only tests do not.
- No production credentials are read from these properties: the test profile uses only the `BINGO_MAVEN_MYSQL_*` namespace and the fixed dedicated runner username. The guard rejects any other account name before connecting.

## Verification

The focused Maven command selected only database-free classes; it did not select any `MySqlIntegrationTest` subclass or start a Spring context:

`mvn -o -Dmaven.repo.local=C:\Users\16414\.m2\repository -Dmaven.compiler.fork=true -Dtest=LocalFinancialSimulationGuardTest,MavenTestDatabasePolicyTest,WinClaimSessionSecurityTest,BingoPatternEvaluatorTest,BingoGameRulesTest,GameSessionStateTest test`

Result: **26 tests, 0 failures, 0 errors, 0 skipped**. Of these, 7 guard tests prove disabled/unsafe combinations fail without touching the mocked datasource, and 4 database-target policy tests exercise safe target acceptance and unsafe target rejection without MySQL. The other 15 are the focused claim, pattern, game-rule, and session-state regression tests from Module 04D-2.

Flutter claim controller test: **1/1 passed**. Flutter analyzer: **No issues found**. `git diff --check` is recorded in [execution evidence](../evidence/module-04d3-safety-checks.txt).

## Remaining isolation and claim blockers

- The local MySQL service exists, but `bingo_qa_maven_test` and its dedicated runner remain unprovisioned; Module 04C-5B records that no authorized DBA login was available. Runtime grant denial and authentication checks are therefore not verified.
- The Android/Admin `bingo_qa_test` account grants have not been reverified. Financial simulation/posting is disabled in that profile; do not enable it for Android fixtures.
- The Maven/Android integration settings do not supply an approved fixture for an already purchased eligible ticket. Do not bypass purchase ownership or synthesize a paid ticket. A valid-claim E2E remains blocked until Product/Finance approve a nonfinancial or isolated ticket fixture mechanism.
- CLOSED-session claim eligibility remains a Product decision. The CANCELLED guard from Module 04D-2 is preserved.
- No MySQL before/after reconciliation, Android claim workflow, Admin claim review, MySQL transaction/lock test, or audit persistence test was possible or attempted.

Module 04D-3 is **COMPLETE FOR CONFIGURATION HARDENING** only. Module 04D remains PARTIAL until real MySQL, Android, and Admin claim E2E passes. Module 04C remains PARTIAL, Module 04B remains PARTIAL, Module 02 remains INCOMPLETE, and Module 03 remains COMPLETE FOR DEFINED QA SCOPE. No Module 04E work was started. No commit or push was made.
