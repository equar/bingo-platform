# Module 03D — Signup rewards and wallet initialization

## Result

**TECHNICALLY VERIFIED — PERSISTENT POSITIVE REWARD POSTING BLOCKED.** The backend regression passes 145/145 against `bingo_qa_test`. Positive grant behavior is covered only in the isolated `test` profile. The persistent `bingo_qa` configuration remains disabled, zero ETB, and expired; no persistent positive reward was posted.

Production readiness remains blocked pending explicit Product and Finance decisions about reward eligibility and amount, promotional-balance classification and redemption, expiration, liability and tax treatment, and balanced general-ledger accounting. Signup rewards currently create a grant and wallet transaction but have no signup-reward double-entry accounting journal. No financial approval or accounting treatment is inferred by this QA work.

## Implementation and controls

- New player wallets initialize with ETB 0.00 available and ETB 0.00 promotional balance.
- A grant is considered only for a new active `PLAYER`, with the latest enabled policy effective at registration and a positive amount. Existing players are not reprocessed when settings change.
- A unique grant per player and operation, plus wallet-owner uniqueness, prevents duplicate grants and wallets. Reward transaction snapshots reconcile the promotional bucket before and after posting.
- Positive policy saves and positive grant attempts fail closed by default. The test-only switch is rejected at startup outside the `test` profile and, in tests, requires a local MySQL `bingo_qa_test` schema. QA and production keep the switch false.
- Settings accept only nonnegative ETB amounts with at most two fractional digits; effective-until must be later than effective-from. The currency must match the wallet currency.
- Admin settings/history/grants endpoints require the admin role. Mutating service calls are recorded by the shared audit aspect; failed guard rejections are retained as failure audit events. Read-only views do not change reward settings.

## Verification

- Backend full regression: **145/145 PASS**, including 16 wallet integration tests and two posting-guard integration tests. Persistent and test database boundaries are documented in the test output.
- Backend focused contract test: **1/1 PASS** after refreshing `bingo-backend/openapi.json` for the request validation constraints.
- Flutter analyze: **PASS**, no issues; unit/widget tests: **21/21 PASS**. Real emulator registration and app-session restore tests: **2/2 PASS**, run without uninstall on emulator-5554. See [Flutter output](../evidence/module-03d-flutter-results.txt) and [wallet screenshot](../evidence/module-03d-android-wallet-after-restore.png).
- Admin frontend unit tests: **82/82 PASS**; lint/build pass; real Chromium settings/history review: **1/1 PASS**. It confirms anonymous settings return 401, admin settings/history/grants return 200, and the disabled zero ETB policy remains unchanged after reload. See [admin output](../evidence/module-03d-admin-results.txt), [API capture](../evidence/module-03d-admin-api-results.json), and [admin screenshot](../evidence/module-03d-signup-policy-admin-readonly.png).
- Persistent QA policy/history, grant, wallet, transaction, and retained-player checks use only SELECT statements in [database reconciliation](../evidence/module-03d-database-readonly.sql) and [captured output](../evidence/module-03d-database-readonly.txt).
- Scenario status is in the [B01–B22 matrix](module-03d-test-matrix.md).

## Persistent QA retention

The pre-existing player accounts #178 and #179 remain active with ETB 0.00 available/promotional balances, no signup grants, and no wallet transactions. Existing signup policy rows remain disabled and zero ETB. The QA E2E uses one new synthetic player, with no wallet funding. The anonymous/player authorization requests and admin settings/history reads are non-mutating. The historical user, wallet, grant, and transaction records are retained.

## Readiness boundary

Module 03D is technically verified for the approved zero-balance QA scope. Positive posting in persistent QA and production remains blocked. Module 03 overall is still in progress; Module 03E and gameplay are not part of this work.
