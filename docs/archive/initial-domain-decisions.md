# Archived Initial Domain Decisions

> Historical product/design intent retained for context, not a statement of current runtime behavior or approval to enable financial operations. Current implementation constraints, QA gates, and production restrictions are documented in the [QA policy](../qa/mandatory-real-e2e-testing-policy.md), [financial reconciliation](../qa/financial-reconciliation.md), and [release checklist](../qa/release-checklist.md).

## Scope

This document resolves the requested specification gaps with explicit defaults so implementation can proceed without additional design churn.

## Bingo Format

- Game format: 75-ball bingo.
- Board layout: 5 columns by 5 rows.
- Column ranges:
  - B: 1-15
  - I: 16-30
  - N: 31-45
  - G: 46-60
  - O: 61-75
- Center cell: FREE.
- Ticket validation must reject boards that violate these ranges or the center-free rule.

## Winning Patterns

- LINE means any completed row, column, or diagonal.
- TWO_LINES means any two distinct completed lines.
- FULL_HOUSE means every non-free cell on the ticket has been called.
- The backend is the sole authority for win validation.

## Session And Ticket Rules

- Ticket purchase closes when a session starts.
- Session capacity semantics: capacity is the maximum number of tickets allowed for a session. Once the cap is reached, no additional tickets may be issued, and purchase attempts must fail cleanly.
- Maximum tickets per player default: 10 per session.
- Both `maxTicketsPerPlayer` and `sessionCapacity` must be configurable per deployment, with environment defaults applied when not overridden.

## Prize Configuration

- Configured prize types:
  - LINE
  - TWO_LINES
  - FULL_HOUSE
- Default payout medium for game prizes: PLAYER_WALLET.
- Default settlement path for agent commissions and other agent-earned cash payouts: AGENT_CASH.
- Multiple winners are allowed by default for all configured prizes unless a specific session overrides the rule.
- Prize amount, winner multiplicity, and settlement medium must be session-configurable.

## Financial Rule

- A prize entitlement must have exactly one settlement path: PLAYER_WALLET or AGENT_CASH.
- Approval creates a prize entitlement record.
- Settlement consumes that entitlement exactly once.
- The system must never credit a wallet and also pay the same entitlement in cash.
- Settlement attempts on an already-consumed entitlement must be rejected as duplicates.

## Wallets And Ledgering

- Player wallets are distinct from agent float accounts.
- Wallet balances are derived from immutable transaction records.
- Cash-payment evidence is stored separately from game credits.
- Deposit claims must snapshot the conversion rate at the time of claim submission.
- Any later rate changes must not alter the historical claim record or the derived accounting outcome.

## Telebirr Handling

- Manual telebirr reference submission is required.
- Admins verify telebirr claims manually.
- No telebirr API integration is part of the current scope.
- Telebirr claim records must store evidence, reviewer decision, and decision timestamp.

## Localization

- English and Amharic are required in both Flutter apps.
- The backend should expose stable message keys and enum codes so clients can localize independently.

## Configurable Defaults

- `maxTicketsPerPlayer`: 10
- `sessionCapacity`: deployment-specific, recommended default 1,000 tickets per session until operational load data suggests otherwise
- `allowMultipleWinnersPerPrize`: true
- `defaultPrizeSettlementPathForPlayerWins`: PLAYER_WALLET
- `defaultPrizeSettlementPathForAgentEarnedCash`: AGENT_CASH
- `ticketSalesCloseOnSessionStart`: true

## Implementation Constraints

- All draw, win, balance, and commission decisions must remain server-authoritative.
- Clients may display state, but they must not calculate final outcomes.
- All financial actions must be auditable and idempotent.

## Blocking Questions

No genuinely blocking questions remain after applying the defaults above.

The only items that may need later product-owner tuning are operational defaults such as session capacity and per-prize payout amounts, but they are not blockers for starting implementation.
