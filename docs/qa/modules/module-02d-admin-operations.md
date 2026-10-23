# Module 02D — Admin operations verification report

## Result

**Module 02D is TECHNICALLY VERIFIED — PRODUCTION FINANCIAL POSTING BLOCKED.** The additive migration, full isolated backend suite, Chromium D01–D20, and read-only QA reconciliation passed. Production deposit credits and manual wallet adjustments remain disabled pending Finance approval.

Module 02 overall remains INCOMPLETE. This work did not start Module 02E, 02F, or 03.

QA is at Flyway V20. V19's already-applied checksum was preserved and V20 added the receiving-account eligibility flag without `flyway repair`. The backend suite used only restricted `bingo_qa_test`; QA business fixtures were created through the application, and no SQL data writes or resets were performed.

## Implemented operations inspected

- `/players` and `/players/:id`: player search/filter/pagination, detail, status transitions, wallet balance/history, and wallet-adjustment request submission/history.
- Wallet admin functionality is embedded in player detail. A finance verifier submits a reasoned request with a supporting reference; a different finance approver may review it. The request history displays reason, supporting reference, and checker note. The legacy one-step adjustment endpoint fails with a controlled conflict. QA-only simulated posting is disabled by default and was explicitly enabled for the local QA run.
- `/deposits`: list/search/filter/pagination, bank-statement verification, approval, and rejection. Verification is protected by a `FINANCE_VERIFIER` endpoint role and service check; approval requires a current matched statement, verifier/approver separation, and a local-only simulation gate. A review note alone cannot authorize a credit.
- Deposit review rows show claim details; there is no separate deposit detail route.
- `/support/tickets` and detail: search/filter/pagination, assignment, public reply, private note, and supported status changes.
- Admin access is enforced server side. Anonymous, player, and agent attempts against protected admin actions were denied in tested cases.

## Defects fixed

1. **Wallet adjustment preflight rejected by CORS.** `Idempotency-Key` was missing from allowed request headers. Added it to the CORS allowlist and added an `AuthSecurityIntegrationTest` preflight regression. The browser confirmed adjustment POST succeeds after the change.
2. **Support tickets accepted an arbitrary user as assignee.** `SupportService.assignTicket` now accepts only active admin/support roles supported by the UI; assigning a player returns `SUPPORT_ASSIGNEE_REQUIRED`. Added a `SupportServiceTest` regression.
3. **Single-step wallet adjustments bypassed maker-checker.** The legacy endpoint now rejects direct posting; request submission and separate checker review write a unique journal and wallet transaction atomically when explicit local simulation is enabled.
4. **Deposit verification role was not constrained at the route.** The verification endpoint now requires `FINANCE_VERIFIER` at the security boundary, in addition to the service-level role check.
5. **Posting inspection was accidentally audited as a mutation.** The read method is named `getPosting`, avoiding the audit aspect's `post*` mutation prefix and preventing read-only transaction failures. The posting endpoint now returns its financial record successfully.
6. **Adjustment request evidence was hidden in the player history.** The UI now renders the maker reason, supporting reference, and checker review note.

## Financial security implementation

- `WalletService.approveDeposit` no longer credits claims. The supported approval route requires `VERIFIED`; it rechecks current bank statement evidence and receiving-account activity, rejects stale/reversed/mismatched evidence, enforces active finance roles and separate verifier/approver identities, then posts an ETB-only balanced simulated journal and wallet transaction in one transaction.
- Verification reuses the existing receiving bank account, statement entry, duplicate-reference, and verification-attempt tables. Synthetic evidence is accepted only in local QA/test when explicitly enabled. Statement evidence and mismatch outcomes are retained; receipts and review notes are not evidence.
- V20 adds a separate `player_deposits_enabled` receiving-account flag, default false; float-only accounts cannot be used as player-deposit evidence. V19 remains immutable and matches the checksum already applied to QA.
- Synthetic deposit journals debit `QA_BANK_CLEARING_ASSET` and credit `PLAYER_WALLET_LIABILITY`. This is a proposed accounting shape only; Finance has not approved it. Non-simulated production posting is rejected, and ETB deposits whose submitted amount differs from the wallet credit are blocked pending a balancing-account policy.
- Wallet adjustment requests require a finance-verifier maker, distinct finance-approver checker, reason, reference, and nonzero ETB amount. Negative posting checks available funds. Request approval, journal lines, wallet transaction, and wallet balance update are transactional and protected by a request row lock and unique journal constraint.
- Both simulation switches are false by default and explicitly false in production. `LocalFinancialSimulationGuard` permits simulated posting only in `qa`/`test`, against local MySQL port 3306 and schema `bingo_qa` or `bingo_qa_test`; production-profile simulation configuration is rejected.
- The immutable journal/transaction, verifier, approver, maker, checker, and audit records were verified against QA. Synthetic receiving-account and statement rows are explicitly labeled and do not represent real settlement.

## Current verification results

- Full backend suite: **110/110 PASS** against `bingo_qa_test`; zero failures, errors, or skipped tests. The updated `WalletIntegrationTest` passed **9/9**, including duplicate-evidence and amount-mismatch denials. The production-simulation guard regression passed **2/2**. Flyway V20 migration compatibility passed.
- Frontend: **78/78 PASS**, lint PASS, production build PASS.
- Real Chromium E2E: **1/1 PASS**, run `69021617`, covering D01–D20; no page errors, console errors, or failed requests.
- Read-only SQL: V20 present; original claims 1–7 remain unchanged and uncredited. Latest synthetic deposits #16 and #19 have balanced journals and matching wallet transactions; #14–15 are pending/uncredited, #17 is an amount exception without credit, and #18 is rejected/uncredited.
- Reconciliation recorded latest deposit journals #4–5 and wallet transactions #18–19; adjustment requests #7–10 are pending/unposted, and #11–12 have distinct maker/checker postings in journals #5–6 and wallet transactions #16–17. Audit entries identify the verifier, approver, maker, and checker.
- Agent #3's ETB 51.00 available float, ETB 0.00 clearing balance, and two balanced float journals remain unchanged.
- Detailed persistent identifiers and scenario-level results are in [the financial-security report](module-02d-financial-security.md), [the test matrix](module-02d-test-matrix.md), and [the test-data registry](../test-data-registry.md).

## Historical baseline execution results (before current financial changes)

- Historical Chromium admin E2E: **1/1 passed** before the financial-security changes. It covered the player, wallet, deposit rejection, and support workflows. The current post-change E2E result and sanitized identifiers are recorded above and in [current execution evidence](../evidence/module-02d-execution.json).
- Backend baseline: focused audit/wallet regression **11/11 passed** and full suite **108/108 passed** on `bingo_qa_test` before this financial-security change.
- Frontend baseline: **75/75 unit tests passed** with lint and production build before this change.
- QA database: `bingo_qa` was queried read-only. It contains 44 retained synthetic Module 02D players (IDs 17–60), seven synthetic deposit claims (two pending and uncredited; five rejected and uncredited), and 30 synthetic support tickets. Eight players each have one ETB 1.25 wallet adjustment transaction. No SQL writes, resets, or deletions were issued. The five original support tickets remain `WAITING_FOR_CUSTOMER`, assigned to admin user 1. Agent profiles 1–4, Agent 3's ETB 51.00 available / ETB 0.00 clearing float, all five float requests, and both balanced journals remain as previously recorded.
- Synthetic data is intentionally retained; the registry identifies all created accounts, their terminal states, wallet adjustments, deposit claims, and support tickets. Failed/interrupted browser attempts that had already performed writes are included as retained records.
- No Android test was run because Module 02D changes and tests admin web/backend workflows only.

## Deposit approval blocker

The configured provider supplies payment instructions, not settlement confirmation. The new service only accepts bank-statement records from the shared evidence model; production posting additionally remains disabled. Finance must approve the accounting treatment, bank evidence source/import and reconciliation policy, duplicate/reversal handling, and wallet-adjustment accounts/limits before production credits can be enabled. Original claims #1–7 remain unchanged and uncredited. Local synthetic QA postings are not real settlement; do not treat receipts, screenshots, or notes as evidence.

## Evidence and remaining work

The current evidence is under `docs/qa/evidence/module-02d-*`. See [the financial-security report](module-02d-financial-security.md), [the Module 02D matrix](module-02d-test-matrix.md), [read-only database reconciliation](../evidence/module-02d-database-verify.txt), and [the persistent test-data registry](../test-data-registry.md). Technical verification is complete. Production posting remains blocked pending the Finance decisions listed above. Module 02 overall remains INCOMPLETE; do not start later modules.
