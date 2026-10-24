# Module 03D — Test matrix

Statuses distinguish passing zero-balance/guard verification from the blocked positive financial posting path.

| ID | Scenario | Status | Evidence |
| --- | --- | --- | --- |
| B01 | Admin reviews current signup reward settings and history in Chromium | PASS | Real Chromium admin session reads settings, seven history rows, and grants; saved state is unchanged after reload. |
| B02 | Anonymous and player roles cannot access admin reward settings | PASS | Guard integration test verifies 401 anonymous and 403 player responses. |
| B03 | Disabled or zero reward policy creates a zero-balance wallet | PASS | MySQL integration coverage checks available/promotional balance, grants, and transactions. |
| B04 | Expired or future policy does not grant a reward | PASS | MySQL integration coverage checks both effective-date boundaries. |
| B05 | Positive reward is posted to persistent QA only with approved financial controls | BLOCKED | No Finance/Product posting approval or reward-specific balanced journal; persistent policy remains disabled. |
| B06 | Android registration initializes an ETB wallet with both buckets at zero | PASS | Real emulator registration for player #180 opens wallet with available/promotional ETB 0.00. |
| B07 | A player receives at most one reward grant | PASS | Unique player/operation constraints and duplicate grant regression. |
| B08 | Duplicate registration does not create a second wallet/grant | PASS | Duplicate request integration assertion returns conflict and preserves one identity/wallet. |
| B09 | Concurrent same-phone registration creates one identity/wallet/grant | PASS | MySQL integration regression: one creation, remaining requests conflict. |
| B10 | Only an active player under an effective positive policy is eligible | PASS | Service checks role, active status, enablement, amount, and effective dates; isolated tests cover inactive and date boundaries. |
| B11 | Saving a policy does not retroactively grant existing players | PASS | Policy save path only records configuration; grant invocation is tied to new-player registration. Retained #178/#179 reconcile with zero grants. |
| B12 | Policy mutation attempts produce audit evidence and history is retained | PASS | Guard integration asserts failed-save audit; existing settings history is read-only in QA. |
| B13 | A successful grant is traceable to a grant record and wallet transaction | PASS | Isolated positive grant regression verifies one grant, one `SIGNUP_REWARD_CREDIT`, and balance snapshots. |
| B14 | Reward posting has a balanced double-entry accounting journal | BLOCKED | No signup-reward journal model/table or approved accounting design exists. No journal was fabricated. |
| B15 | Promotional balance reconciles with reward transaction snapshots | PASS | Isolated positive grant regression checks before/after promotional bucket values; persistent balances remain zero. |
| B16 | Guard rejection rolls back registration side effects | PASS | Active-positive-policy registration in the disabled-posting guard test returns 409 and rolls back user, wallet, grant, and transaction. |
| B17 | Negative amount is rejected | PASS | Request validation returns 400; policy history is unchanged. |
| B18 | Invalid effective date range is rejected | PASS | Request validation returns 400 when `effectiveUntil` is not after `effectiveFrom`. |
| B19 | Unsupported currency is rejected | PASS | Only the configured ETB wallet currency is accepted; invalid currency returns 400. |
| B20 | Inactive/pending player cannot receive a grant | PASS | MySQL integration regression confirms no promotional credit, grant, or transaction. |
| B21 | Settings remain unchanged after reload | PASS | Real Chromium reload returns the same disabled, zero ETB policy. |
| B22 | Persistent QA data reconciliation is SELECT-only and preserves retained users | PASS | SQL snapshot checks policies, players #178/#179, wallets, grants, transactions, and journal availability. |

## Overall

Zero-balance wallet initialization, eligibility, validation, authorization, idempotency, rollback, audit behavior, and test-only positive grant bookkeeping are technically verified. Persistent positive reward posting and double-entry financial reconciliation remain blocked pending approved product and finance controls.
