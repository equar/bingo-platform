# Module 02H — Wallet transfer administration and coverage closure

## Scope and disposition

This work covers Module 02H only. The admin detail view and backend detail response are implemented and regression tested with isolated `bingo_qa_test` records. Persistent Chromium transfer-detail scenarios remain **BLOCKED**: `bingo_qa` contains no transfer or transfer-ledger rows, and all five retained transfer policies are disabled, expired, and have zero transfer limits. No existing wallet was funded, no policy was enabled, and no SQL writes were made. The overall Module 02 status remains **INCOMPLETE**.

## Existing transfer model

Players initiate transfers through the existing player-to-player API. An admin can list/search/filter transfers at `GET /api/v1/admin/wallet/transfers` and retrieve a single record at `GET /api/v1/admin/wallet/transfers/{id}`. The transfer record carries sender/recipient identity snapshots, amount, currency, status, operation ID, balance snapshots, creation/completion timestamps, and failure reason. The operation ID is the transaction reference.

There is no separate transfer journal table. Each successful transfer produces two entries in the existing wallet transaction ledger, with `reference_type=WALLET_TRANSFER`, the transfer ID as `reference_id`, and the same operation ID: one debit and one credit. Admin detail now returns these related ledger entries alongside the transfer. No new transaction type or posting rule was added. Transfer history has no separate audit-history UI; mutations are audited by the existing service audit aspect.

## Implementation and defect

`GET /api/v1/admin/wallet/transfers/{id}` now returns transfer metadata and related ledger entries. The page uses that detail endpoint after selecting a row, shows the paired entries, and presents generic error messages without server internals. New DTOs contain only the transfer fields and ledger references needed by the admin view.

The first detail regression exposed an audit aspect naming collision: the read method `transferDetail` began with the mutating prefix `transfer`, so the aspect attempted to insert an audit record inside a read-only transaction and the endpoint returned HTTP 500. Renaming it to `getTransferDetail` corrected the false mutation classification. A controlled unknown-ID response is 404 with `TRANSFER_NOT_FOUND`.

## Executed evidence

- Frontend: **82/82** unit tests pass; lint and production build pass.
- Real Chromium at `http://localhost:5174`: **4/4** tests pass. These cover admin list/search/status filter/refresh with the retained empty dataset, invalid-ID error safety, and player and phone-plus-PIN agent denial. Screenshots: [empty transfer list](../evidence/module-02h-empty-transfer-list.png), [invalid ID](../evidence/module-02h-invalid-transfer-error.png), [player denial](../evidence/module-02h-player-transfer-access-denied.png), and [agent denial](../evidence/module-02h-agent-transfer-access-denied.png). The browser tests verify live backend status codes and read-only database snapshots before/after.
- Backend focused transfer integration: **1/1 PASS** against `bingo_qa_test` (43.30 s). It covers list filters, detail response, ledger references and timestamps, idempotent replay, insufficient-balance rollback and failure audit, admin-only access, and concurrent overspend (one success, one 409; balances reconcile). Full backend suite: **130/130 PASS**; wallet integration class **10/10**, OpenAPI contract **1/1**. See [test results](../evidence/module-02h-test-results.txt).
- Persistent database: read-only verification found `0` transfers, `0` wallet transactions referencing `WALLET_TRANSFER`, `117` wallets, and five transfer policies. Each policy is disabled, expired, and has zero single-transfer and daily outgoing limits. The browser run confirmed transfer, ledger, wallet balance, and policy snapshots were unchanged. See [database reconciliation](../evidence/module-02h-database-reconciliation.txt).
- Android: **NOT RUN**. No mobile behavior changed; the existing agent login remains phone number and PIN only.

## Fixture and financial safety

The isolated integration fixture creates its own synthetic player identities and test balance using the application/test setup in `bingo_qa_test`. It does not modify persistent QA data. The first parallel MockMvc attempt exposed a failure-audit transaction-order defect: the aspect inserted the failure record before the transfer transaction released its locks, causing a MySQL lock timeout. Failed-operation audits are now recorded in an after-completion callback, after the business transaction releases its locks; successful-operation audit ordering is unchanged. The regression asserts that the insufficient-balance failure is audited after rollback. The concurrent retry passed: two ETB 40 requests from ETB 70 yielded one success and one 409, with sender/recipient balances ending ETB 30/70 and exactly two transfers/four ledger entries.

Production posting remains disabled. No synthetic QA posting is represented as bank settlement. Transfer fees, commissions, limits, or settlement behavior were not changed.

## Remaining blockers and deferrals

- Persistent transfer details, sender/recipient, amount/currency, status, reference, timestamps, positive search/filter results, and refresh (W02–W09 and W14): **BLOCKED** by absence of a safe retained fixture and inactive transfer policy. Empty-list search/filter behavior was exercised in Chromium, and matching search/filter behavior passed against isolated fixtures. Creating a persistent transfer would require a safe source balance and approved active policy; none was available. Existing player balances were not used.
- Persistent browser pagination with multiple transfers (W10): **NOT RUN** because the retained list is empty. Pagination is covered at the isolated API and frontend component level.
- Schedule recurrence/timezone/DST/past-time semantics: **BLOCKED** pending Product decision.
- Prize settlement: **BLOCKED** pending Finance/Product payout, accounting, and compliance rules; the operation remains fail-closed.
- Production financial posting: **BLOCKED** pending Finance, accounting, bank integration, and applicable regulatory approval. QA simulation does not establish production readiness.
- Live session progression, draw, claim creation/review, and prize entitlement lifecycle remain **DEFERRED to Module 03**, which owns gameplay/ticket integration. No Module 03 work was started.

## Closure recommendation

The admin detail capability is implemented and its isolated contract is covered, but the persistent Chromium detail scenarios remain blocked by lack of a safe retained fixture. Module 02H is therefore **PARTIAL**, and Module 02 remains **INCOMPLETE**. The historical 44-row matrix counts are preserved in [the master coverage matrix](module-02f-master-coverage.md); Module 02H dispositions are in the [test matrix](module-02h-test-matrix.md).
