# Module 04D-4 — Real Claim E2E Test Matrix

No QA backend was started because fresh MySQL checks found the `bingo_qa_test_runner` account has ALL PRIVILEGES on `bingo_qa_test.*`, including destructive DDL. No fixture was created and no gameplay or claim request was sent.

| ID | Scenario / guard | Status | Evidence |
| --- | --- | --- | --- |
| P01 | Port 8080 identity and active profile | PASS — QA TARGET REJECTED | PID 29808 is the Bingo Spring Boot application, profile `dev`, schema `bingo`; it was not used for QA. |
| P02 | QA MySQL authentication and schema | PASS | Read-only query authenticated as `bingo_qa_test_runner@127.0.0.1`; catalog `bingo_qa_test`; MySQL 8.0.36. |
| P03 | QA account denied `bingo`, `bingo_qa`, Maven schema | PASS | Read-only table enumeration denied for all three schemas. |
| P04 | No global grants | PASS | `SHOW GRANTS` reports only global `USAGE`. |
| P05 | QA account lacks destructive schema authority | FAIL — STOP GATE | Fresh `SHOW GRANTS` reports `ALL PRIVILEGES ON bingo_qa_test.*`; DDL authority is present. No backend start or fixture writes allowed. |
| P06 | Android profile safe schema settings | PASS — CONFIG ONLY | `ddl-auto=validate`, Flyway clean disabled, validate-on-migrate enabled, datasource auto-creation absent. Runtime not started. |
| P07 | Financial simulation/posting and settlement disabled | PASS — SOURCE/CONFIG | `android-test` financial switches false; settlement returns `PRIZE_SETTLEMENT_BLOCKED`. |
| P08 | External payment network inactive | PASS — SOURCE REVIEW | Configured payment provider returns instructions; no network payment client in the reviewed path. |
| P09 | Build identity, runtime profile/account and Flyway runtime guard | BLOCKED | Test runtime was not started after P05. Runtime identity reporter does not currently check current account/grants. |
| P10 | Approved safe fixture path | BLOCKED | Player purchase debits wallet; agent sale debits float and accrues commission; no nonfinancial fixture route found. |
| A01–A14 | Android claim E2E | BLOCKED / NOT RUN | No safe QA runtime or eligible synthetic ticket fixture. |
| W01–W06 | Admin Chromium claim E2E and unauthorized-role checks | BLOCKED / NOT RUN | No safe QA runtime or claim fixture. |
| DB01 | Read-only Flyway/version and cross-schema checks | PASS | V22 successful; three unrelated schemas denied; no migration or write. |
| DB02 | Claim/audit and financial before/after reconciliation | BLOCKED / NOT RUN | No fixture and no mutation. |
| U01 | Focused Java database-free tests | PASS | 26/26, 0 failures/errors/skips. |
| U02 | Flutter claim controller test | PASS | 1/1. |
| U03 | Flutter analyzer | PASS | No issues found. |
| U04 | Admin claims UI component test | PASS | 6/6 using mocked API; not browser E2E. |

## Commands

- Java: `mvn -o -Dmaven.repo.local=C:\Users\16414\.m2\repository -Dmaven.compiler.fork=true -Dtest=LocalFinancialSimulationGuardTest,MavenTestDatabasePolicyTest,WinClaimSessionSecurityTest,BingoPatternEvaluatorTest,BingoGameRulesTest,GameSessionStateTest test`
- Flutter: `flutter test test/features/game/live_game_controller_test.dart`; `flutter analyze`.
- Admin UI: `npm test -- src/features/claims/ClaimsPage.test.tsx`.
- No destructive Maven suite, backend integration API, Android emulator E2E or Chromium browser E2E ran.
