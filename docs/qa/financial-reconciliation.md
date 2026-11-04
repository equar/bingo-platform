# Financial Controls and Reconciliation

**Release state:** NO-GO for production financial posting and complete
reconciliation. Synthetic QA evidence is not bank evidence, real settlement,
or release certification.

## Ledger boundaries

- `agent_floats` and `agent_float_transactions` are the Agent operational
  balance/history, not a general ledger. Float transactions retain movement
  and before/after snapshots; they cannot alone prove balanced platform
  accounting.
- `wallets` and `wallet_transactions` track player wallet state. Do not use
  them as a bank-statement or Agent-float ledger.
- Commission rules/accruals are separate from float and wallet entries.
- Deposit, Agent-float, wallet-adjustment, transfer, ticket-sale, payout, and
  reversal flows require distinct references and reconciliation. Never infer
  that one transaction type authorizes another.

## Deposits and wallet adjustments

- A deposit submission is an uncredited claim. A player receipt, provider
  instructions, or Admin note is not proof of settlement.
- The claim keeps the conversion-rate snapshot used at submission; later
  configuration changes must not rewrite it. Telebirr references are handled
  as manual claims; no provider settlement integration is implied.
- Verification requires trusted statement evidence matched to the eligible
  receiving account, unique bank reference, ETB currency, exact submitted cash
  amount, nonfuture transaction date, and `SETTLED` state. Reversed, unsettled,
  duplicate, wrong-account, and mismatched entries become exceptions, not
  credits.
- An active `FINANCE_VERIFIER` verifies; a different active
  `FINANCE_APPROVER` approves. Self-verification, self-approval, and player
  verification are denied. Approval rechecks evidence and commits the claim,
  journal, wallet transaction, and balance update atomically with idempotent
  retry/concurrency protection.
- A wallet adjustment requires a nonzero ETB amount, reason, supporting
  reference, distinct maker/checker identities, and a negative-balance guard.
  The legacy one-step adjustment path is disabled. Neither workflow is
  production-authorized by its implementation or QA simulation.

## Agent float funding and payouts

- An Agent's reference, note, or uploaded receipt is an unverified claim. Float
  funding requires independent trusted bank evidence and separate verification
  and approval. Synthetic statement rows only test controls in an explicitly
  enabled local QA environment.
- QA funding simulation uses a balanced journal and updates the Agent's
  operational float in the same transaction. The proposed bank-clearing and
  float-liability accounts are not Finance-approved production accounting.
- Agent ticket sales debit float and may accrue commission. They cannot be
  used as nonfinancial ticket fixtures.
- The Admin prize-entitlement settlement endpoint fails closed with
  `PRIZE_SETTLEMENT_BLOCKED`; it does not credit a player wallet. A separate
  `AGENT_CASH` path lets an approved Agent verify/disburse a pending entitlement
  using PIN and idempotency controls, debit float, and record settlement. This
  implementation is not production authorization. One entitlement must never
  be paid through both Agent cash and a player-wallet credit.

## Environment and approval boundaries

- Deposit, adjustment, float, and other financial simulation/posting flags are
  false by default and disabled in production. Never enable them to satisfy
  coverage in persistent QA or production.
- QA simulation is limited to explicit opt-in, approved local QA/test profiles,
  and verified local schemas. Synthetic journals are labeled and are not
  revenue, bank receipt, or real settlement.
- Production funding, deposits, wallet adjustments, transfers, ticket sales,
  rewards, commissions, prizes, external payments, and payout require
  documented Finance/accounting, Product, Security, and Operations approvals
  appropriate to that flow. See [current blockers](qa-blockers-and-approvals.md).

## Required reconciliation before release

Use synthetic accounts and a separately approved disposable environment.
Capture immutable, read-only before/after snapshots and reconcile each source
record, approval, journal line, wallet/float transaction, balance, and audit
event. Verify exact-once replay, concurrency, rollback/no partial writes,
currency/rounding, reversal treatment, and unchanged unrelated financial and
historical data. The configured 10-ticket limit must be measured under
concurrency; a setting alone is not evidence.

No complete end-to-end financial lifecycle or 1,000-player capacity run has
been demonstrated. Do not infer a production GO decision from component tests,
synthetic runs, or historical PASS results.
