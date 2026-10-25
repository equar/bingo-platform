# Module 04D-3 — Safety Gate Test Matrix

All passing tests in this matrix are database-free. No MySQL database or Spring integration context was used.

| ID | Guard / check | Status | Evidence |
| --- | --- | --- | --- |
| G01 | Ordinary Maven test profile defaults all financial simulation/posting flags off | PASS | YAML defaults resolve from an unset opt-in to false; config reviewed. |
| G02 | Explicit synthetic financial opt-in is required | PASS | Guard unit test rejects enabled posting when master opt-in is false, with no datasource access. |
| G03 | Posting enabled in production is rejected | PASS | Guard unit test rejects `prod` profile before datasource access. |
| G04 | Posting enabled in `qa` or `android-test` is rejected | PASS | Shared profile guard rejects both profiles; Android config explicitly disables master and feature switches. |
| G05 | Financial posting targeting `bingo_qa` is rejected | PASS | Guard unit test rejects persistent schema through datasource URL before connecting. |
| G06 | Financial posting targeting `bingo_qa_test` is rejected | PASS | Guard unit test rejects Android/Admin QA schema before connecting. |
| G07 | Maven destructive tests require the disposable schema/account | PASS | Pure policy accepts only localhost/127.0.0.1:3306, `bingo_qa_maven_test`, `bingo_qa_maven_runner`; 4 policy tests pass. |
| G08 | Maven destructive tests reject DB auto-creation | PASS | Pure policy test rejects `createDatabaseIfNotExist=true`. |
| G09 | Test-profile datasource mismatch is rejected before connection | PASS | Guard validates URL/user configuration and shared MySQL integration base runs policy before JDBC connection. |
| G10 | Ticket purchase posting requires explicit isolated-test opt-in | PASS | Guard test rejects ticket-posting request without opt-in, before datasource access. |
| G11 | Signup-reward, deposit, adjustment, and float use shared opt-in/database guard | PASS | Source review confirms each positive path delegates to `LocalFinancialSimulationGuard`; guard tests pass. |
| G12 | Prize settlement remains disabled | PASS | `settleEntitlement` remains unconditional `PRIZE_SETTLEMENT_BLOCKED`; no settlement config switch exists. |
| G13 | External payment network is disabled/absent | PASS | Payment provider is configuration-only and returns instructions; no network client in this payment path. |
| G14 | Focused Java regression set | PASS | 26 run, 0 failures/errors/skipped. |
| G15 | Focused Flutter claim test | PASS | 1/1 passed. |
| G16 | Flutter analyzer | PASS | No issues found. |
| G17 | `git diff --check` | PASS | Exit code 0; untracked files also scanned for trailing whitespace. |
| G18 | Runtime DB grants/authentication and schema isolation | BLOCKED | Local MySQL exists, but dedicated Maven schema/account is not provisioned; no authorized DBA credential. No connection attempted. |
| G19 | Android/Admin DB isolation and claim fixture | BLOCKED | `bingo_qa_test` grants are unverified and there is no approved already-purchased ticket fixture path. |
| G20 | Real claim integration/E2E | BLOCKED | Requires the database provisioning and approved fixture decisions above; Module 04D remains PARTIAL. |

## Commands

- Focused Maven test selection: `LocalFinancialSimulationGuardTest,MavenTestDatabasePolicyTest,WinClaimSessionSecurityTest,BingoPatternEvaluatorTest,BingoGameRulesTest,GameSessionStateTest` only.
- Flutter: `flutter test test/features/game/live_game_controller_test.dart` and `flutter analyze`.
- No DB-backed Maven suite, Android claim mutation, Admin claim action, settlement, or wallet posting was run.
