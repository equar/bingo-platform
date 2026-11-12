# Admin game lifecycle implementation

November 11, 2026. Implements corrections from [the source review](admin-game-lifecycle-review-2026-11-11.md). No deployment or production payment was performed.

## Implemented behavior

- New sessions snapshot `EARLIEST_SPLIT_V1`, the minimum house margin, session cost reserve, and claim window.
- Each configured prize amount is a **fixed total pool**. All tickets completing that pattern on its earliest call qualify, regardless of claim/review order or the legacy winner cap. Same-call winners split the pool. Leftover cents go in ticket purchase order. Pools must provide at least one cent per possible tied ticket.
- One entitlement per ticket/pattern, protected by both session locking and a database unique constraint. Later-completing tickets fail eligibility even if their cards eventually match.
- Ticket sales close at actual start. Session overrides must contain nonempty unique patterns and exactly corresponding valid prizes.
- Start, approval, and payout enforce funding. Recognized wallet sales include only the transferable bucket debit; promotional spending contributes zero. Agent sales require the recorded float debit in the correct currency. Accrued commission remains an obligation whether paid or pending.
- `cash-backed sales >= sum(prize pools) + commission + session cost reserve + required house retention`. Retention is the configured fraction of recognized sales, rounded upward to cents. Funding applies to all potential awards, including unpaid entitlements.
- Wallet payout credits transferable funds and records the wallet transaction, actor, time, and reference in the same transaction as settlement. A retry with the same reference returns the existing settlement; a different reference cannot pay again.
- Agent cash payout uses the same payout configuration, funding, and authoritative winner/share validation, preserves existing float/clearing accounting and idempotency, validates currency, and records the paying agent. A nonblank clearing reference is required.
- A game can close manually only after every configured pattern has an eligible winner. Automatic exhaustion still closes at 75 calls. Closed sessions accept claims within their snapshotted window. SETTLED, CANCELLED, DRAFT, and OPEN reject new claims.
- Final settlement requires an expired claim window and no pending claims or entitlements. Settlement preserves the actual end timestamp. Pending claims submitted in time can still be reviewed after the deadline, before final settlement.
- Admin cancellation before the first draw refunds sold tickets transactionally. Wallet purchases return to their original transferable/promotional buckets. Agent purchases refund the **player's wallet**, while reversing sale commission; already-paid commission must be recoverable from agent float or the cancellation rolls back. Cancellation after a draw is rejected. Hosts retain the existing stricter private-game cancellation restrictions.
- The admin session screen shows pool amounts, routes, margin, funding/commission/cost/retention amounts, and the claim deadline. It refreshes authoritative session data, merges draw history, and replays on reconnect even if the final call was missed. Terminal screens use appropriate guidance. The settlement action reflects payout configuration and wallet versus cash routing.
- The owning player’s existing claim history includes the prize amount/payment state and, for enabled pending cash prizes, the collection code. Admin claim responses do not expose collection codes; other players cannot retrieve them. Codes disappear after payment.
- Player session responses and ticket preview disclose the pool/tie rule and claim window. Player claims remain available after closure until expiry. English and Amharic strings cover the revised rules. Admin colon-containing labels now render correctly instead of being interpreted as translation namespaces.

## Configuration

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `APP_GAME_MINIMUM_HOUSE_MARGIN_BPS` | `1000` | Basis points retained from recognized sales. Configurable from 1 to 9999; 1000 means 10%. Choose the operating value before creating sessions. |
| `APP_GAME_SESSION_COST_RESERVE` | `0.00` | Costs to reserve per session in its currency, beyond recorded commission and prizes. Configure realistic operating/payment costs. |
| `APP_GAME_CLAIM_WINDOW` | `PT24H` | ISO-8601 duration after game closure; must be at least one second. |
| `APP_GAME_PAYOUTS_ENABLED` | `false` | Enables implemented wallet/cash payout and funded-game refund execution. Disabled consistently across admin and agent payout routes by default. |

Margin, cost reserve, and claim window changes apply to newly created sessions. Changing configuration does not rewrite sold game terms. The payout switch is an operational runtime setting and requires an application restart in the existing deployment model.

This is a session budget invariant over recorded funding. It does not independently verify bank assets, physical cash handover, fraud, chargebacks, or costs omitted from the reserve. Those existing financial/reconciliation operations must be satisfied to make the configured margin meaningful.

## Migration and activation

V32 marks existing sessions `LEGACY` and adds rule snapshots plus uniqueness for economic awards. It does not rewrite historical amounts or decide legacy winners. Legacy sessions reject further sales, starts, approvals, and payments that require the new rule; historical records remain readable. Reconcile outstanding legacy games under their original terms before rollout. Do not relabel sold legacy sessions to bypass this protection.

Run this read-only duplicate check before applying V32:

```sql
SELECT ticket_id, prize_type, COUNT(*) AS awards
FROM bingo_prize_entitlements
GROUP BY ticket_id, prize_type
HAVING COUNT(*) > 1;
```

If any rows are returned, reconcile them before migration; the unique constraint deliberately fails instead of deleting historical obligations. Confirm no legacy live game is in flight before deployment. Back up the database using the existing deployment procedure.

The application remains undeployed. Production activation requires setting the desired margin/cost/claim-window configuration, reconciling legacy data, verifying the existing funding and payout accounting operations, and deliberately enabling payouts. The default switch must not be presented as production payout readiness.

## Verification

Completed verification:

- Full backend regression: **279 tests discovered, 276 passed, 3 optional Agent roster tests skipped**; zero failures/errors. The 12 new operational readiness integration tests cover funding, ties, repeated awards, claim expiry, settlement finality, refunds, agent commission/cash payout, payout-code ownership, and currency mismatch. Concurrent approval is covered separately.
- The three optional Agent roster tests subsequently **passed** against their separately restricted `bingo_qa_agent_pagination_test` schema with financial simulation disabled. Across both runs, all **279 distinct backend tests passed**.
- Checked-in OpenAPI compared successfully against the generated contract after regeneration, without the update switch on the final check.
- Admin: **36 files / 179 tests passed**; ESLint and production build passed.
- Playwright: the existing start/review/wallet-settlement flow passed at desktop width, with the session layout also checked at 390px. Screenshots were visually inspected. This test stubs API responses to verify the UI contract; backend integration tests separately exercise the real database behavior.
- Flutter: **5 focused live-game controller/reconnect tests passed**; analysis passed for all six modified Dart implementation files.
- `git diff --check` passed.

Tests used a dedicated Docker MySQL server and disposable schemas with restricted credentials. Test compilation used a separate Docker volume to avoid interference from the workspace's background compiler. No persistent business schema was used for test mutations, and no production migration, payment, or deployment was performed. Test credentials and detailed local logs remain in ignored local artifacts.

To regenerate the checked-in API contract in that isolated test environment:

```text
mvn -Dtest=OpenApiContractIntegrationTest -DupdateOpenApi=true test
```

Ordinary contract tests compare the generated contract without modifying the checked-in file.
