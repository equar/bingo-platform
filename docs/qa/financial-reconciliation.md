# Financial Reconciliation Evidence

**Execution date:** 2026-10-22  
**Status:** **BLOCKED — full reconciliation not executed**

## Safety boundary

No production/shared financial service or live-money account was used. Backend money-flow integration tests ran against disposable, loopback-bound MySQL 8.0.36. These tests validate selected transactions and invariants, not a complete account/session reconciliation.

## Verified focused invariants

| Scenario | Result | Evidence |
|---|---|---|
| Same-key agent ticket sale replay | PASS | Replay returned the original ticket without a duplicate ticket, float debit, or commission accrual. |
| Insufficient agent float | PASS | Rejected sale created no ticket/accrual and did not reduce the available float. |
| Commission calculation and persistence | PASS | The enabled 2.5% rule on a 4.25 sale persisted 0.11 after rounding; replay did not duplicate the accrual. |
| Disabled commission rule | PASS | A sale after disabling the rule created no new commission accrual. |
| Concurrent same-key sale requests | PASS | Both requests received the same result; one ticket, one debit, and one commission accrual were verified. |
| Payout replay | PASS | Replayed payout returned the existing result without duplicating the operation. |
| Admin-created account activation | PASS | Activation changed the password, consumed the token, rejected replay, and allowed subsequent login. This is account security evidence, not a money-ledger reconciliation. |
| End-to-end reconciliation across deposits, approvals, settlements, transfers, winnings, and ledger snapshots | NOT RUN | No approved integrated scenario/environment was available to reconcile all source and destination balances before and after a complete lifecycle. |

## Required before release

Run the complete workflow only in an approved disposable environment using synthetic users and a reviewed dataset. Capture immutable before/after ledger snapshots and transaction identifiers; reconcile deposits, approvals, ticket sales, payouts, commissions, player transfers, and session winnings. Verify that totals balance across available/pending buckets, failed operations produce no partial writes, retries are idempotent, and the configured 10-ticket limit holds under concurrency.

Retain sanitized test reports and database/ledger assertions without exposing credentials, tokens, personal data, or live financial information. The requested 1,000-player concurrency run is tracked separately in [load-test-results.md](./load-test-results.md).

**Release gate:** No complete financial reconciliation evidence exists; this gate remains **NO-GO**.
