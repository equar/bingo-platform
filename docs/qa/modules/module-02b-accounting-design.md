# Module 02B accounting design

## Architecture findings

- `agent_floats` is the operational source for an agent's available and clearing float (`DECIMAL(19,2)`). Existing sale and payout services lock this row before updating it.
- `agent_float_transactions` is an immutable, single-agent movement history with before/after snapshots and a unique operation ID. It is not a general ledger and cannot demonstrate balanced platform accounting by itself.
- `wallets` and `wallet_transactions` track player wallet movements. They are not a bank/float journal and must not be repurposed for agent top-ups.
- Commission rules/accruals are separate and remain unchanged.
- No double-entry journal, receiving-bank registry, independent bank-verification record, or finance-specific role exists in the inspected implementation.

## QA-only simulated posting model

The requested conceptual funding treatment is represented only in the isolated, explicit QA simulation: debit a synthetic bank-clearing asset account and credit an agent prepaid-float liability account by the same ETB amount and posting reference. The entry is marked `SIMULATED`; it is not bank evidence, revenue, or production settlement. Production funding remains disabled while finance approves the real account mapping and treatment.

`agent_floats` remains the agent-facing operational balance. A successful simulated posting updates that balance and appends its existing float transaction in the same database transaction as the two immutable journal lines and request transition. A new journal is required because the current float/wallet histories cannot enforce balanced accounting. It has a unique posting reference, unique source request, currency, simulation marker, and append-only debit/credit lines. A database check/unique constraints plus a balance validation in the transaction enforce equal debit and credit totals.

The posting transaction locks the request and agent float, confirms verification and approval eligibility, confirms the agent is still active/approved, inserts one journal and its two lines, updates the balance once, adds the float transaction, and transitions the request to posted. Any exception rolls the transaction back. Unique constraints on request, operation, and simulated bank statement reference make retries and duplicate verification fail closed.

## Production decision still required

Finance must approve the actual bank and liability chart-of-account IDs, recognition timing, source evidence retention, settlement/reversal treatment, fees and amount variance treatment, refund controls, and daily reconciliation owner. Production profile cannot enable simulated posting, and no successful production funding is represented by this QA journal design.

## Configuration boundary

Simulation requires all of: the explicit simulation flag, the `qa` Spring profile, and a connected catalog exactly equal to `bingo_qa` or `bingo_qa_test`. It is false by default. The service checks the active JDBC catalog at runtime before allowing simulation. Production profiles reject the flag at startup. Synthetic statement fixtures are explicitly labeled and never use uploaded agent receipt content as independent evidence.
