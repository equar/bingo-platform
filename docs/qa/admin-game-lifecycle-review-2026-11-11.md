# Admin portal and game lifecycle review

Implementation follow-up: see [the readiness implementation and activation requirements](admin-game-readiness-implementation-2026-11-11.md). The findings below record the original reviewed state.

Date: November 11, 2026. Scope: existing admin functionality, its backend enforcement, ticket purchasing, live draws, claims, entitlements, and the connected agent cash payout path. No application behavior changed.

## Verdict

**Not ready for sign-off for real-money game operations.** The current implementation has useful security and concurrency controls, but does not complete the existing payout lifecycle or enforce a non-loss-making game budget. Passing UI tests does not establish business-rule completeness.

This is a source and automated-test review. It does not certify the deployed environment, visually inspect a running portal, establish actual production configuration, or certify accounting or regulatory compliance. Findings below distinguish observed behavior from rules that need an explicit business decision.

## Findings, in priority order

### 1. Critical: no session funding gate protects the house

Source: `bingo-backend/src/main/java/com/bakaroo/bingo/backend/feature/game/service/BingoGameService.java`, `createSession` (355–409), `updateSessionState` (412–433), `createEntitlementForClaim` (772–800).

The system accepts fixed prize amounts and starts sessions without comparing collected funds to maximum prize liability. Winner caps limit the number of awards, not their affordability. A session with one cheap ticket can start with a much larger prize. Approval creates the configured full amount per winner; amounts are not divided among winners.

Ticket purchases can consume promotional balances before cash-backed balances (`purchaseTicket`, 461–500). Nominal ticket sales therefore cannot automatically be treated as fresh cash revenue. Agent commissions also accrue separately (`AgentService.createCommissionAccrual`).

Required correction within existing operations: enforce an agreed budget at start and approval, including all configured patterns and effective winner caps, commission, promotional funding, and relevant costs. Count committed entitlements, not just completed payouts. Reserve the funds supporting the obligation. Reject an unfunded start rather than relying on an operator's arithmetic.

### 2. High: the same ticket can collect the same pattern prize repeatedly

Source: `BingoGameService.submitWinClaim` (573–603), `enforcePrizeWinnerLimit` (826–840); `WinClaimRepository.existsByTicketIdAndClaimedPatternAndDrawSequenceSnapshot`; migration `V3__create_bingo_game.sql:100,125`.

Duplicate detection includes draw sequence. Once a ticket completes a pattern, the pattern remains complete on subsequent draws. The player can submit again after another draw; each claim can be approved into a separate entitlement while the session/pattern winner cap has space. Entitlement uniqueness is per claim, not per ticket/pattern.

Example: with three winners allowed, one ticket can consume all three awards through claims at sequences N, N+1, and N+2. Session locking preserves the cap but does not prevent this repetition.

Required correction: one award per ticket/pattern/session unless repeat awards are explicitly the intended published rule; enforce the invariant in the approval transaction and database. A new idempotency key must not create a second economic award.

### 3. High: winner selection follows admin approval order, not earliest completion

Source: `BingoGameService.submitWinClaim`, `reviewWinClaim` (628–657), `enforcePrizeWinnerLimit`; `GameLiveDrawService.advanceSessionDrawInTransaction`.

Claims are checked against all calls at submission time. No earliest-completion sequence is computed; drawing continues after successful claims and awards. At 75 calls every ticket completes every supported pattern. A later claimant can receive the last available award before an earlier valid claimant is reviewed.

This is incompatible with a first-to-complete rule if that is the intended bingo format. It also leaves same-call ties undefined. The observed implementation is effectively admin-selected valid claims up to a cap.

Required correction: settle the existing rule for first completion, claim deadline, same-call ties, and whether prizes are split or paid in full. Enforce that rule using authoritative draw history before approvals consume award slots. Do not use manual review order as a substitute for winner eligibility.

### 4. High: settled sessions remain financially mutable

Source: `BingoGameService.updateSessionState` (412–433), `reviewWinClaim`; `WinClaimSessionValidator.java:9–18`.

`CLOSED -> SETTLED` checks only the transition table. It does not require pending claims to be resolved or prize entitlements to be settled. The claim validator rejects CANCELLED and null, but accepts SETTLED, CLOSED, DRAFT, and OPEN. Reviews also have no final-session guard. Consequently a settled session can accept new claims and incur new liabilities.

Every terminal-state update also replaces `endedAt`, so marking an already closed session settled overwrites its game end time.

Required correction: define claim eligibility after closure, enforce its cutoff, require all obligations to be resolved before settlement, and preserve the actual end time. Post-settlement approvals must not silently reopen financial obligations.

### 5. High: payout controls differ between admin and agent routes

Source: `BingoGameService.settleEntitlement` (661–665); `bingo-admin/src/features/prizeEntitlements/PrizeEntitlementsPage.tsx:94–97,226–229`; `AgentService.verifyAndDisbursePayout` (323–376); `AgentController.java:110`.

Admin settlement always returns `PRIZE_SETTLEMENT_BLOCKED`, and the UI disables settlement pending approved evidence/accounting controls. But an approved agent with a PIN and payout code can settle a pending AGENT_CASH entitlement through `/api/v1/agent/payouts/verify`. There is no equivalent global payout block in that service method. It debits float, increases clearing, and records disbursement.

Thus the admin warning does not describe a system-wide restriction. Wallet-path entitlements lack an executable admin settlement path, while cash entitlements can move through the agent route. The cash method records an operator-supplied clearing reference; that alone does not prove physical cash delivery.

Required correction: apply the approved payout policy consistently across existing routes and show an accurate status to admins and players. Do not simply enable the disabled admin button. Verify existing cash payout evidence and accounting before treating the lifecycle as complete.

### 6. High: session API overrides bypass room-level prize consistency validation

Source: `BingoGameService.createSession` (390–400), `validateRoomPrizeConfiguration` (802–824); `dto/GameSessionRequest.java`.

Room creation/update validates nonempty patterns, uniqueness, and exact pattern/prize correspondence. Session creation independently accepts overridden pattern and prize lists without calling equivalent validation. Empty lists, duplicate prizes, or mismatched patterns/prizes can therefore reach session snapshots through the API even though the portal normally inherits the room configuration.

Approval selects the first matching prize configuration, making duplicate amounts ambiguous. Claims for unconfigured patterns can pass mathematical validation but fail only at approval.

Required correction: validate the final effective session snapshot regardless of whether values came from the room or request. Reject unconfigured claim patterns at submission.

### 7. Medium: no recovery path for a sold game that cannot proceed

Source: `BingoGameService.updateSessionState` (422–424); `GameSessionState.java`; `SessionDetailPage.availableTransitions`.

Cancellation is blocked once any ticket is sold. A started game can only continue or close. The existing lifecycle has no sold-session cancellation/refund path, and closing does not refund tickets. An interrupted or incorrectly configured paid session can leave customers charged without a playable or refundable game.

Given the requested scope, do not add a broad cancellation subsystem in this review. This remains an operational completeness blocker: paid games should not be treated as fully supported until the existing cancellation restriction has an approved recovery procedure and accounting treatment.

### 8. Medium: admin live session status and recovery can be stale

Source: `bingo-admin/src/features/sessions/SessionDetailPage.tsx:61–114`; `liveDrawSocket.ts`, CONNECTED handling and `onGap`.

Draw WebSocket messages update only the draw list; they do not refresh session status, draw count, or ticket count. There is no periodic session refresh. Automatic DRAWING/CLOSED transitions can leave the screen showing an obsolete state and controls until another query refresh occurs.

Replay is triggered by a subsequent MESSAGE with a sequence gap, not by reconnection itself. If the connection misses the final draw, there may be no next message to repair the history. Also, the REST history effect replaces the draw list rather than merging it, so a late history response can temporarily discard newer streamed calls.

Required correction: refresh authoritative session data on stream updates, merge replay results, and replay on reconnect even if no further draw arrives. Surface whether live information is current.

### 9. Medium: session detail does not expose the financial terms needed for operation

Source: `SessionDetailPage.tsx`, Session details card.

The page displays a prize count rather than each prize amount, payout route, and winner limit. It does not display the ticket price/currency or aggregate prize exposure. Operators cannot verify the immutable session's full financial terms from the screen used to start and close it. A closed session also gets the instruction to start before drawing, despite having no restart transition.

Required correction: show the existing snapshot values clearly and use state-appropriate guidance. Start/settle confirmation should describe the actual consequences and blockers of those actions.

## House exposure rule

For the current fixed-amount-per-winner model, maximum prize liability is:

`L = sum(prize amount × effective maximum winners)`

The effective maximum is one when multiple winners are disabled; otherwise it is the configured maximum for each pattern. Include overlapping patterns because one ticket can satisfy several patterns.

For a game to avoid an unfunded obligation, cash-backed revenue assigned to that game, plus an explicitly funded subsidy if allowed, must cover L, commissions, relevant costs, and the required retained margin. Promotional debits require explicit funding treatment. Float availability is a liquidity check, not proof of game profitability. A reserve can fund a losing game but does not make that game profitable.

Example: two prizes of 500 and 1,000, each allowing two winners, create up to 3,000 in prize obligations. Selling ten tickets at 100 produces only 1,000 in nominal sales before promotional funding, commissions, or costs. Existing code permits starting that game.

No current rule guarantees the house never loses. Protect the budget and honor declared prizes; changing draws or rejecting legitimate wins to preserve margin would not correct the funding defect. Server-side random selection should remain intact.

## Existing controls worth preserving

- Server selects random numbers using SecureRandom from remaining values, with session row locking to serialize draws and no caller-selected draw body.
- Ticket purchase uses a locked session and wallet, capacity/player limits, balance checks, transactional issuance/debit, and idempotency.
- Session financial/rule values are snapshotted, limiting the effect of later room edits on existing games.
- Claim ownership and server-side card evaluation prevent client-supplied win evidence from determining eligibility. Failed validation cannot be overridden by admin approval.
- Approval locks claim and session rows and checks the configured session/pattern award cap.
- Agent cash payout locks entitlement and float rows, checks pending status, uses idempotency, and records payout/float transactions.
- Admin endpoint role checks, review notes, confirmation dialogs, and audit infrastructure exist. These were inspected, not subjected to a full penetration test.

## Validation performed

- Admin Vitest: **35 files, 174 tests passed**.
- Selected backend unit tests: **12 passed** across BingoGameRulesTest, GameSessionStateTest, and WinClaimSessionSecurityTest.
- Selected backend integration tests: **32 initialization errors**, because `BINGO_MAVEN_MYSQL_URL` is required and absent. These did not exercise application behavior and must not be reported as product test failures or passes.
- No live-browser, production-data, actual payout, load, or deployment test performed. Existing tests do not resolve the business-rule findings above.

## Sign-off requirements for existing functionality

Reproduce and verify the corrections using isolated MySQL tests: unfunded starts; promotional funding; duplicate awards across draw sequences; competing valid winners and ties; post-close/post-settlement claims; settlement with unresolved obligations; malformed session overrides; consistent payout blocking across wallet/admin/agent routes; cancellation recovery; reconnect after the final draw. Then visually exercise the existing portal before, during, and after a complete test game, including rejection, approval, disbursement, and reconciliation.
