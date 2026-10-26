# Archived Initial Implementation Plan

> Historical planning draft retained for context. It describes an empty repository and proposes PostgreSQL; the current repository is implemented and uses MySQL. Do not use this document as an implementation or migration guide.

## Current Workspace State

- The repository currently contains no tracked source files in the working tree.
- No AGENTS.md instructions were present in the workspace.
- This plan is therefore based on the requested target architecture and requirements, not on existing application code.

## Target Projects

- `bingo-backend`: Spring Boot 4.1.1, Java 25, Maven, PostgreSQL, Flyway.
- `bingo-app`: Flutter player app for Android and iOS.
- `bingo-admin`: React 18, TypeScript, Vite, MUI, TanStack Query, Axios.
- `bingo-agent`: Flutter counter-service agent app for Android, iOS, and web.

## Recorded Existing Functionality

- No implementable product functionality is currently visible in the workspace.
- No API contracts, database schema, UI flows, or application logic could be verified from source because the repo is empty.
- As a result, all functionality below should be treated as planned scope rather than a delta against inspected code.

## Missing Functionality To Build

- Server-authoritative bingo game rules and ticket lifecycle.
- Draw management, calling, and deterministic session state.
- Ticket generation, purchase closure on session start, and capacity enforcement.
- Win validation for LINE, TWO_LINES, and FULL_HOUSE.
- Prize configuration, prize entitlement creation, and one-time settlement.
- Wallets, balances, immutable transaction records, and agent float.
- Manual telebirr reference submission and admin verification workflow.
- Agent management, commissions, and support operations.
- Player and agent localized UX in English and Amharic.

## Dependencies And Build Order

1. Backend domain model and persistence first, because all other apps depend on authoritative APIs.
2. Backend authentication, roles, and money-movement rules next, because wallet and settlement behavior must be consistent before UI work.
3. Admin workflows after backend APIs stabilize, because administration depends on verification and management endpoints.
4. Player app and agent app after the game and wallet contracts stabilize, because both clients consume the same authoritative game and finance APIs.
5. Reporting, support tooling, and operational hardening last.

## Phased Checklist

### Phase 0 - Repository Foundation

- Create the four application folders if they do not already exist.
- Establish shared conventions for API naming, localization keys, and money/rounding rules.
- Add baseline README and environment documentation for local development.

### Phase 1 - Backend Domain Core

- Define PostgreSQL schema and Flyway migrations for users, roles, wallets, wallet ledger, sessions, tickets, draws, called numbers, prizes, prize entitlements, settlements, deposits, telebirr claims, agents, commissions, and support notes.
- Implement server-side bingo pattern validation for 75-ball boards.
- Implement authoritative session state transitions: draft, open, started, drawing, closed, settled, cancelled.
- Implement ticket issuance with immutable ownership and session capacity checks.
- Implement draw generation and call tracking from backend only.
- Implement win detection for LINE, TWO_LINES, and FULL_HOUSE.
- Implement prize entitlement creation on approval and exactly-once settlement consumption.
- Implement wallet ledger posting with immutable transaction records.
- Implement agent float accounting and commission accrual.
- Implement manual telebirr claim submission and admin verification state machine.

### Phase 2 - Backend APIs And Security

- Publish REST APIs for player, agent, and admin use cases.
- Add authentication and authorization for player, agent, admin, and support roles.
- Add idempotency protection for financial operations and ticket purchases.
- Add audit trails for all balance-changing actions.
- Add validation for localization-sensitive error messages and client-facing enums.

### Phase 3 - Admin Console

- Build admin dashboards for sessions, tickets, draws, winners, wallets, telebirr claims, commissions, and support cases.
- Add manual verification flows for telebirr references.
- Add controls for prize configuration, session setup, capacity, ticket limits, and commission rules.
- Add agent management screens and support tooling.

### Phase 4 - Player Flutter App

- Build player onboarding, login, wallet view, session browser, ticket purchase, ticket history, and winnings view.
- Add localized English and Amharic UI.
- Add in-app submission flow for telebirr payment references.
- Add server-sourced draw playback and ticket status screens.

### Phase 5 - Agent Flutter App

- Build agent onboarding and operational dashboard for float, ticket sales, deposits, commissions, and settlement status.
- Add localized English and Amharic UI.
- Add web-compatible layouts for counter-service use.
- Add support entry points for customer assistance and claim follow-up.

### Phase 6 - Quality, Operations, And Hardening

- Add backend tests for validation, settlement, and ledger invariants.
- Add contract tests for APIs consumed by the three clients.
- Add monitoring, alerting, and administrative audit views.
- Add seed data and environment-specific configuration examples.
- Verify all money-moving paths are exactly-once and audit-safe.

## Implementation Dependencies By Domain

- Bingo rules and ticket validation depend on the backend schema, session state machine, and board model.
- Win settlement depends on draw tracking, prize configuration, and entitlement records.
- Wallet balances depend on immutable ledger entries, not on mutable balance fields alone.
- Telebirr claims depend on separate cash-evidence records and must not be conflated with game credits.
- Commission payouts depend on agent management, settlement rules, and the same exact-once entitlement model.

## Delivery Notes

- Keep cash evidence and game credits separate at the data model level.
- Make every financial conversion rate explicit and snapshot it at claim time.
- Do not introduce telebirr API integration; verification stays manual.
- Do not allow a single prize entitlement to be both wallet-credited and cash-paid.
- Prefer additive, auditable changes over in-place mutation.
