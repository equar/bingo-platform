# Module 02D — Financial security closure

## Status

**TECHNICALLY VERIFIED — PRODUCTION FINANCIAL POSTING BLOCKED.** The guarded backend suite, migrated QA database, Chromium workflow, and read-only reconciliation passed. Production deposits and manual wallet adjustments remain blocked pending Finance approval. Do not start Module 02E or Module 03.

The persistent QA schema is at Flyway V20; the isolated `bingo_qa_test` suite also migrated through V20. The QA application ran with explicit local-only simulation flags for synthetic E2E; production configuration remains disabled.

## Unsafe root cause

Before this change, `WalletService.approveDeposit` approved a `PENDING` claim based on the admin review request, directly increased `Wallet.availableBalance`, and wrote a wallet transaction. There was no independent settlement evidence or matched verification record. `WalletService.adjustPlayerWallet` similarly performed a direct admin balance adjustment in one step.

The direct methods now return controlled conflicts and do not update claims, wallets, journals, or wallet transactions. The supported deposit approval route delegates to `DepositFinancialService`, which only accepts a verified claim. The legacy wallet-adjustment route is disabled in favor of the separate request/review workflow.

Other wallet-credit paths were inspected: `SignupRewardService` grants only the promotional balance under a versioned admin policy and a unique grant; game prize credits are tied to the supported prize-claim flow; wallet and agent transfers use their existing transaction flows. The configured `PaymentProvider` supplies payment instructions but does not confirm settlement. None of these paths can approve a deposit claim or bypass the verified-deposit service.

## Controls implemented in source

### Player deposits

- Deposit submission still creates an uncredited `PENDING` claim. A supplied receipt or admin note is not payment confirmation.
- Finance verification references an existing bank statement entry and reuses Module 02B's receiving accounts, statement entries, duplicate bank-reference constraint, and verification-attempt records. Deposit and agent-float journals remain separate products.
- Additive V20 marks receiving accounts as player-deposit eligible only through an explicit `player_deposits_enabled` flag, false by default. V19 remains checksum-compatible with its already-applied QA version. A float-only account cannot verify a player deposit unless an admin explicitly enables the separate purpose; this does not enable production credits.
- A verifier must be active and hold `FINANCE_VERIFIER`; an approver must be active and hold `FINANCE_APPROVER`. A player cannot verify their own claim, and the verifier and approver must be different identities.
- Verification checks that the receiving account is active, the statement source is trusted, the transaction reference matches, the currency is ETB, the cleared amount equals the submitted cash amount, the transaction date is not in the future, and settlement state is `SETTLED`. Reversals, unsettled transactions, and mismatches become verification exceptions, not credits.
- Approval re-reads the statement evidence and requires it to remain matched/current. Claim locking, unique journal constraints, and the approval idempotency key protect retries and concurrent approval. Approval and posting share a database transaction.
- Simulated posting produces two equal ETB journal lines and a `DEPOSIT_CREDIT` wallet transaction containing before/after balances. The journal identifies verifier, approver, posting reference, and simulation state. Failure rolls back the claim, journal, transaction, and balance together.

### Wallet adjustments

- An active finance verifier submits a nonzero ETB amount, reason, and supporting reference. A different active finance approver must approve or reject it.
- Approval locks the request and wallet; the unique request journal prevents a second posting. Negative changes are rejected if they would make the available balance negative.
- The adjustment journal, wallet transaction, request decision, and wallet balance update are in the same transaction. Maker/checker identities and review reason are persisted and covered by the service audit aspect.
- The prior one-step adjustment endpoint is disabled. The new workflow is not a production posting authorization.

### Local simulation and production restrictions

- Deposit and adjustment simulation switches default to false; production explicitly sets both false.
- Simulated posting requires `qa` or `test`, an explicit enable flag, local MySQL on port 3306, and schema `bingo_qa` or `bingo_qa_test`. The guard rejects the `prod` profile and nonlocal or unexpected schemas.
- Simulated deposit journal accounts are `QA_BANK_CLEARING_ASSET` (debit) and `PLAYER_WALLET_LIABILITY` (credit). These are proposed accounts only; Finance has not approved this accounting treatment.
- Non-synthetic production posting is rejected. Deposits whose submitted cash amount differs from the calculated wallet credit are also blocked until an approved FX/fee balancing-account policy exists.
- Adjustment posting remains blocked by default pending an approved policy and chart-of-accounts treatment.

## Decisions still required from Finance

1. Approve or replace the deposit accounting treatment (proposed: debit bank cash/clearing, credit player wallet liability); define rounding, fees, FX/conversion, settlement date, and reconciliation ownership.
2. Approve the authoritative bank-record source/import, receiving-account ownership and activation procedure, evidence retention, duplicate handling, and reversal/chargeback process.
3. Approve adjustment roles, limits, maker/checker policy, supporting evidence requirements, accounting accounts, and whether any production posting is permitted.
4. Confirm reconciliation procedures for matching each bank transaction, journal, wallet transaction, and balance.

Until decisions are recorded and implemented, do not enable either simulation flag in production or treat synthetic evidence/journals as real settlement or revenue.

## Current verification evidence

| Check | Result |
|---|---|
| Flyway migration | PASS — QA and `bingo_qa_test` at V20; V19 checksum preserved; V20 applied additively |
| Backend full suite | **110/110 PASS**, 0 failures, 0 errors, 0 skipped, isolated to `bingo_qa_test` |
| Wallet integration regression | **9/9 PASS** — includes duplicate statement reference and amount-mismatch cases; no unverified posting |
| Production-simulation guard regression | **2/2 PASS** — production profile rejects enabled simulation; disabled posting fails before database access |
| Frontend unit tests | **78/78 PASS** |
| Frontend lint and production build | PASS |
| Chromium Module 02D E2E | **1/1 PASS**, run suffix `69021617`; D01–D20 exercised with no page errors, console errors, or failed requests |
| Read-only QA reconciliation | PASS — original claims 1–7 unchanged and uncredited; deposit/adjustment journals, wallet transactions, actors, balances, and Agent #3 float reconciled |

### Persistent QA financial evidence

All business data below was verified with read-only SQL after the browser test. No SQL data writes, resets, or deletions were performed. QA V20 was applied by Flyway; all fixtures were created through supported application workflows. Failed/interrupted E2E iterations were retained rather than cleaned up. The ignored local `bingo-backend/.env.qa` explicitly enables float-statement, deposit, and adjustment simulations for this QA run. Production configuration keeps deposit and adjustment simulations false, and the runtime guard rejects enabled simulation outside local QA/test or on any schema other than `bingo_qa`/`bingo_qa_test`.

#### Earlier retained financial runs

| Deposit claim | State / evidence | Posting and wallet reconciliation |
|---|---|---|
| #8 | `PENDING`; interrupted attempt; no verifier | No journal or wallet transaction; player balance 0.00 ETB |
| #9 | `APPROVED`; statement #3, verification #3; verifier #92, approver #93 | Deposit journal #1, wallet transaction #12; ETB 2.50, balance 0.00 → 2.50 |
| #10 | `APPROVED`; statement #5, verification #4; verifier #101, approver #102 | Deposit journal #2, wallet transaction #14; ETB 2.50, balance 0.00 → 2.50 |
| #11 | `VERIFICATION_EXCEPTION`; statement #7, verification #5; amount mismatch ETB 3.00 vs 2.99 | No journal or wallet transaction; player balance 0.00 ETB |
| #12 | `REJECTED`; no verification | No journal or wallet transaction; player balance 0.00 ETB |
| #13 | `APPROVED`; statement #8, verification #6; verifier #101, approver #102; concurrent retry | Deposit journal #3, wallet transaction #15; ETB 4.25, balance 0.00 → 4.25 |

Earlier deposit journals #1–3 each contain two balanced ETB lines: debit `QA_BANK_CLEARING_ASSET`, credit `PLAYER_WALLET_LIABILITY`.

Requests #1–6 from earlier runs remain recorded: #1–2 pending/unposted, and #3–6 approved through different maker/checker identities. Their entries are historical, separate from the latest run below.

#### Latest Chromium run `69021617`

The run created player users #142–145, verifier #146, approver #147, additional deposit players #148–150, receiving bank account #7, and support tickets #42–47. Receiving accounts #5–7 remain active and marked player-deposit-eligible for QA synthetic tests. Player #143 remains `SUSPENDED`; the other lifecycle states and ticket #42 `CLOSED` were verified after refresh. No credentials are recorded here.

| Deposit claim | State / evidence | Posting and wallet reconciliation |
|---|---|---|
| #14–15 | `PENDING`; retained from interrupted runs before QA statement simulation was enabled | No verification, journal, or deposit-credit transaction; balances remain 0.00 ETB |
| #16 | `APPROVED`; statement #9 / verification #7; verifier #146 / approver #147 | Deposit journal #4 / wallet transaction #18; ETB 2.50, balance 0.00 → 2.50 |
| #17 | `VERIFICATION_EXCEPTION`; statement #11 / verification #8; submitted ETB 3.00 vs statement ETB 2.99 | No journal or wallet transaction; balance 0.00 ETB |
| #18 | `REJECTED`; no verification | No journal or wallet transaction; balance 0.00 ETB |
| #19 | `APPROVED`; statement #12 / verification #9; verifier #146 / approver #147; concurrent approval | Deposit journal #5 / wallet transaction #19; ETB 4.25, balance 0.00 → 4.25 |

Each latest deposit journal has two balanced ETB lines: debit `QA_BANK_CLEARING_ASSET`, credit `PLAYER_WALLET_LIABILITY`. Synthetic statements use source `SYNTHETIC_QA_BANK_STATEMENT`; they do not represent real settlement. The original claims #1–7 remain unchanged: #1–2 `PENDING` and #3–7 `REJECTED`, all with zero deposit journals and zero deposit-credit transactions.

| Adjustment request | Player | Maker / checker | State and posting |
|---|---|---|---|
| #7–10 | #109, #115, #121, #130 | #110, #116, #122, #131 / — | `PENDING`; no journal or wallet transaction |
| #11 | #139 | #140 / #141 | `APPROVED`; journal #5 / wallet transaction #16; ETB 1.25, balance 0.00 → 1.25 |
| #12 | #145 | #146 / #147 | `APPROVED`; journal #6 / wallet transaction #17; ETB 1.25, balance 0.00 → 1.25; approval replay did not post again |

Adjustment journals #5–6 balance between `WALLET_ADJUSTMENT_CLEARING` and `PLAYER_WALLET_LIABILITY`. Audit entries #1821/#1823–1824 record maker #146 and checker #147 for request #12; #1837–1839 record verifier #146 and approver #147 for deposit #16, and #1848–1850 record the same separated roles for concurrent deposit #19. Audit entry #1822 records the denied legacy single-step adjustment. Agent #3 remains `APPROVED`, with ETB 51.00 available, ETB 0.00 clearing, two float transactions, and two balanced ETB 25.50 journals (#1–2). Original support tickets #1–5 remain `WAITING_FOR_CUSTOMER`, assigned to admin #1. See [the persistent test-data registry](../test-data-registry.md), [read-only SQL evidence](../evidence/module-02d-database-verify.txt), and [Chromium execution evidence](../evidence/module-02d-execution.json).

## Production readiness restrictions

The configured payment provider supplies instructions, not authoritative bank settlement. Finance/accounting approval is still required for the deposit accounting treatment, evidence source/import, reversal/chargeback handling, reconciliation ownership, FX/fee treatment, and wallet-adjustment accounts/limits. Until those decisions are approved and implemented, both production posting paths remain blocked; synthetic journals are not real settlement or platform revenue. No later module was started.
