# Module 02H — Wallet transfer test matrix

| ID | Scenario | Status | Evidence / reason |
| --- | --- | --- | --- |
| W01 | Authorized admin opens transfer listing | PASS | Real Chromium; list API 200; retained empty state and unchanged DB snapshot. |
| W02 | Admin opens transfer details | BLOCKED | No safe persistent transfer fixture; isolated backend detail tested. |
| W03 | Sender and recipient are correct | BLOCKED | Persistent detail fixture unavailable; isolated backend asserts identity snapshots. |
| W04 | Amount and currency are correct | BLOCKED | Persistent detail fixture unavailable; isolated backend asserts amount and currency. |
| W05 | Transfer status is accurate | BLOCKED | Persistent detail fixture unavailable; isolated backend checks COMPLETED status. |
| W06 | Transaction reference matches backend and DB | BLOCKED | Persistent transfer/ledger rows absent; isolated backend reconciles operation ID and ledger reference. |
| W07 | Creation/completion timestamps display correctly | BLOCKED | No persistent detail row for browser verification; backend checks persisted timestamps and frontend renders available detail timestamps. |
| W08 | Search by transaction reference | BLOCKED (persistent matching result) | Chromium exercises the search request/empty result; isolated backend search checks a matching operation reference. No retained transfer is available to verify a positive browser result. |
| W09 | Status filtering | BLOCKED (persistent matching result) | Chromium exercises the COMPLETED filter and gets 200/empty state; isolated backend checks matching status. No retained transfer is available to verify a positive browser result. |
| W10 | Pagination | NOT RUN (persistent browser) | No retained rows to advance through in Chromium. Isolated API pagination and frontend component pagination are covered. |
| W11 | Invalid transfer ID returns controlled error | PASS | Real Chromium admin session receives 404 `TRANSFER_NOT_FOUND`; no internal details exposed. |
| W12 | Unauthorized access denied | PASS | Player and agent tokens receive 403 from admin transfer API. |
| W13 | Player/agent credentials cannot access admin transfer operations | PASS | Real Chromium browser context tests player token and approved agent phone+PIN token; both receive 403. |
| W14 | Refresh preserves correct transfer details | BLOCKED | No safe persistent transfer record to select and refresh; list empty-state refresh passes. |
| W15 | Financial ledger references reconcile | PASS (isolated) | Backend checks exactly paired debit/credit rows with `WALLET_TRANSFER` reference, transfer ID, operation ID, and expected balances. No dedicated journal table exists. |
| W16 | Duplicate/retried requests do not transfer twice | PASS | Isolated idempotent replay keeps one transfer/two ledger rows. Two concurrent ETB 40 requests against ETB 70 yield one 200 and one 409; balances reconcile with two transfers/four ledger rows. Insufficient balance preserves balances and records a failure audit. |
| W17 | Sensitive financial data is protected | PASS | Admin-only API; player/agent denied; detail DTO excludes credential fields and raw metadata. |
| W18 | API errors display safely | PASS | Unit regression verifies generic UI error; real Chromium validates controlled 404 and no SQL/JDBC/stack internals. |

## Execution counts

Chromium: **4/4 test cases passed**. The first case passes W01 and exercises the empty-state portions of W08/W09; a positive search/filter result remains blocked. The other three cases cover W11/W18 and player/agent denial. Frontend: **82/82 PASS**, lint and build pass. Focused/full backend counts are recorded in [Module 02H test results](../evidence/module-02h-test-results.txt). Persistent database read-only checks are in [database reconciliation](../evidence/module-02h-database-reconciliation.txt).

W02–W09 and W14 remain blocked exactly as directed when a valid source balance cannot be obtained safely; W08/W09 empty-state behavior still passed in Chromium. No retained profiles, balances, or transfer policies were changed to manufacture browser fixtures.

## Module 02I policy follow-up

02I found and fixed two backend policy-selection/enforcement defects: effective dates were not checked during transfer execution, and timestamp ties did not deterministically select the newest policy ID. The isolated regression covers disabled, expired-enabled, and zero-max rejection with no posting. This does not unblock persistent browser scenarios: no approved transfer-specific QA policy/funding method exists. See [Module 02I](module-02i-transfer-fixtures.md). The five persistent policies and wallet records remain unchanged.
