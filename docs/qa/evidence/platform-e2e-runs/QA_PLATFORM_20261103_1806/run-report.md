# Platform QA Run Report

- **Run ID:** `QA_PLATFORM_20261103_1806`
- **Date:** 2026-11-03 (local time)
- **Verdict:** **NOT READY — real platform E2E is BLOCKED**
- **Scope:** Current workspace source at `9e2a4d5cf8280cfed723a5ec3c0ca0d060fa2e0c`; no commit, push, deploy, database migration, reset, deletion, or financial posting was performed.

## Environment and isolation preflight

| Check | Result |
|---|---|
| Backend source revision | `9e2a4d5cf8280cfed723a5ec3c0ca0d060fa2e0c`; backend worktree clean. |
| Backend source fingerprint | `865C16669CF6459B213DC0F04D317427EEAA7DF891225D2FC5F2FBF6FE580050` from `Get-AndroidTestBackendSourceFingerprint`. |
| Source Flyway maximum | V23. |
| Local MySQL | `MySQL80` running; listener observed on loopback `127.0.0.1:3306`. |
| Configured test target | `bingo_qa_test` via the ignored `.env.test`; configured account name `bingo_qa_test_runner`. The guarded launcher validated the current datasource identity and scoped-grant rules before the version check. Passwords and credential values were not read into evidence or printed. |
| Current schema Flyway | V22, latest migration reported successful by the guarded preflight. |
| Active backend / profile | No backend listener on 8082; no active profile/runtime identity. The intended launcher profile is `android-test`, but it did not start. |
| API endpoints | Intended host endpoint `http://127.0.0.1:8082`, emulator endpoint `http://10.0.2.2:8082`; neither had an active backend listener. |
| Admin Vite | No listener on 5174 or 5176. |
| Runtime flags | No active runtime flags exist because no backend started. The guarded launcher configures scheduler, signup reward, deposit/adjustment/float simulation, and synthetic financial testing as disabled. |
| Android | `emulator-5554` detected by the guarded launcher and Flutter. |

The [fresh preflight evidence](preflight.log) confirms the exact blocker: **database Flyway V22 does not match backend source V23**. Startup stopped before packaging or launching. The saved `module-06a5/backend-identity-preflight.json` is dated 2026-11-02 and references a different source commit; it is historical and was not used as current runtime evidence. No database migration or write was attempted.

## Execution results

### Database-free verification

| Application | Result | Exact evidence |
|---|---:|---|
| Admin Vitest | PASS — 31 files, 115 tests | [`admin-vitest.log`](admin-vitest.log) |
| Admin ESLint | PASS | [`admin-eslint.log`](admin-eslint.log) |
| Admin TypeScript + Vite production build | PASS; non-fatal bundle warning for a 648.90 kB chunk over the 500 kB threshold | [`admin-build.log`](admin-build.log) |
| Agent Flutter tests | PASS — 26 tests | [`agent-flutter-tests.log`](agent-flutter-tests.log) |
| Agent Flutter analyze | PASS — no issues | [`agent-flutter-analyze.log`](agent-flutter-analyze.log) |
| Player Flutter tests | PASS — 55 tests | [`player-flutter-tests.log`](player-flutter-tests.log) |
| Player Flutter analyze | PASS — no issues | [`player-flutter-analyze.log`](player-flutter-analyze.log) |
| Agent mocked Android smoke test | PASS — 1 test; mocked repository, no backend or database | [`agent-mocked-android-smoke.log`](agent-mocked-android-smoke.log) |
| Player mocked Android deposit smoke test | PASS — 1 test; local form only, no backend or database | [`player-mocked-android-smoke.log`](player-mocked-android-smoke.log) |
| Agent and Player debug runtime | PASS — APK build/launch and Dart hot restart succeeded; no Dart runtime errors; not E2E | [`device-runtime-checks.md`](device-runtime-checks.md) |

The first Player mocked Android attempt exposed a **test-harness issue**, not a product failure: the test sent the keyboard `done` action while the amount field still had focus, so the form correctly advanced focus instead of submitting. The test now focuses the reference field before submitting and passes on rerun.

### Real E2E and data protection

- Admin Chromium, Player Android against Spring, Agent Android against Spring, real API state assertions, MySQL before/after reconciliation, and cross-client workflows: **BLOCKED / NOT RUN** by the V22/V23 preflight gate.
- The current schema was not migrated. Financial posting, ticket purchase, deposit/transfer/float posting, rewards, settlement, and external payment were not enabled or attempted.
- No QA fixture, business row, screenshot, or database before/after snapshot was created in this run. No current-run database write, reset, deletion, or test-data cleanup occurred.
- Historical QA results are linked per scenario in the [matrix](../../../platform-e2e-scenario-matrix.md); they are not current-run passes.

## Performance and coverage limitations

No instrumented before/after request-count, render/rebuild, latency, or memory baseline was captured. The database blocker prevented representative real-client network profiling, so no performance improvement is claimed. Component/unit checks verify local action eligibility and validation behavior but do not establish end-to-end request counts or screen rebuild cost.

The Agent and Player cold debug launches emitted skipped-frame warnings during initial startup (311/96 Agent and 289/107 Player frames in the two reported bursts). These were debug builds and are not enough to diagnose production performance; profile and compare steady-state flows after the real backend gate is resolved.

The [scenario matrix](../../../platform-e2e-scenario-matrix.md) inventories 32 functional/performance scenarios: **0 current real E2E PASS, 31 BLOCKED, 1 NOT RUN**. The repository's 2026-10-25 inventory documents 39 domains and historical evidence, with material financial, claim, purchase, concurrency, and operational gaps still open.

## Changed-file inventory

The client action-validation/error/localization work from the preceding audit remains uncommitted and is included in this workspace. No backend source or dependency manifest was changed.

- Admin: `bingo-admin/src/adminUiTranslations.ts`; `bingo-admin/src/i18n.ts`; `bingo-admin/src/features/auth/AdminAccountActivationPage.tsx` and `.test.tsx`; `bingo-admin/src/features/profile/ChangePasswordPage.tsx` and `.test.tsx`.
- Agent: `bingo-agent/lib/l10n/app_en.arb`; `bingo-agent/lib/l10n/app_am.arb`; `bingo-agent/lib/src/core/validation/ethiopian_phone.dart`; `bingo-agent/lib/src/features/auth/presentation/auth_screen.dart`; `bingo-agent/lib/src/features/payouts/presentation/payouts_screen.dart`; `bingo-agent/lib/src/features/players/presentation/players_screen.dart`; `bingo-agent/lib/src/features/tickets/presentation/ticket_sale_screen.dart`; `bingo-agent/lib/src/features/wallet/presentation/wallet_topup_screen.dart`; `bingo-agent/test/core/validation/ethiopian_phone_test.dart`; `bingo-agent/test/features/action_form_validation_test.dart`; `bingo-agent/integration_test/action_mock_smoke_test.dart`.
- Player: `bingo-app/lib/l10n/app_en.arb`; `bingo-app/lib/l10n/app_am.arb`; `bingo-app/lib/src/features/auth/presentation/auth_screen.dart`; `bingo-app/lib/src/features/community/presentation/daily_challenges_screen.dart`; `bingo-app/lib/src/features/community/presentation/leaderboard_screen.dart`; `bingo-app/lib/src/features/community/presentation/private_games_screen.dart`; `bingo-app/lib/src/features/wallet/presentation/deposit_dialog.dart`; `bingo-app/test/features/community/community_safe_errors_test.dart`; `bingo-app/test/features/community/private_games_screen_test.dart`; `bingo-app/test/features/wallet/deposit_dialog_test.dart`; `bingo-app/integration_test/action_mock_smoke_test.dart`.
- QA deliverables: `docs/qa/platform-e2e-runbook.md`; `docs/qa/platform-e2e-scenario-matrix.md`; and the run's logs/reports under `docs/qa/evidence/platform-e2e-runs/QA_PLATFORM_20261103_1806/`.

## Release assessment

**NOT READY / not fully tested.** The current environment did not satisfy the source/schema prerequisite, so no real backend-dependent critical scenario was executed. Passing component tests, analyzers, builds, and mocked Android UI tests are supporting evidence only. Re-run the guarded stages in the [runbook](../../../platform-e2e-runbook.md) only after the V23 schema prerequisite is resolved through an explicitly authorized process and the financial test environment is separately approved.

## Evidence index

- Current backend/MySQL gate: [`preflight.log`](preflight.log).
- Admin tests/lint/build: [`admin-vitest.log`](admin-vitest.log), [`admin-eslint.log`](admin-eslint.log), [`admin-build.log`](admin-build.log).
- Agent component tests/analyzer: [`agent-flutter-tests.log`](agent-flutter-tests.log), [`agent-flutter-analyze.log`](agent-flutter-analyze.log).
- Player component tests/analyzer: [`player-flutter-tests.log`](player-flutter-tests.log), [`player-flutter-analyze.log`](player-flutter-analyze.log).
- Mock-only on-device UI: [`agent-mocked-android-smoke.log`](agent-mocked-android-smoke.log), [`player-mocked-android-smoke.log`](player-mocked-android-smoke.log).
- Debug launch/restart: [`device-runtime-checks.md`](device-runtime-checks.md).
- Scenario-level prior evidence and current status: [scenario matrix](../../../platform-e2e-scenario-matrix.md).
- Repeatable, guarded commands: [runbook](../../../platform-e2e-runbook.md).
- Current blockers and fixes: [`defect-report.md`](defect-report.md).
