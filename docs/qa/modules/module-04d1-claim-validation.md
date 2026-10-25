# Module 04D-1 — Bingo Claim Rules, Authorization and Nonfinancial Validation

Disposition: **PARTIAL — pattern logic was executed during 04D-1; Module 04D-2 later recovered focused Java/Flutter validation and added the cancelled-session guard. Real database-backed claim integration remains unverified.**

Scope: Module 04D-1 only. No gameplay tests, MySQL administration, database-backed mutation, settlement, wallet credit, or financial posting was performed. Module 04D remains PARTIAL.

## Claim architecture and implemented behavior

- `POST /api/v1/tickets/{id}/win-claims` is the player submission endpoint. It requires `PLAYER`, a non-null `claimedPattern`, and `Idempotency-Key`. The request contains no session, ticket grid, or draw-history field.
- `BingoGameService.submitWinClaim` requires a player principal and looks up the ticket by both ticket ID and owner ID; a ticket owned by another player is returned as `TICKET_NOT_FOUND`. The session comes from that ticket. Current called numbers and draw sequence are loaded server-side.
- `BingoPatternEvaluator` is the production pure rules implementation. Supported patterns are `LINE`, `TWO_LINES`, and `FULL_HOUSE`; lines include rows, columns, and both diagonals, and the center zero is free. No other pattern is inferred or added.
- A matching submission is persisted as `PENDING_REVIEW`; a nonmatching submission is persisted as `FAILED_SERVER_VALIDATION` with a server code/message. The latter is a failed claim record, not an entitlement.
- Idempotency is scoped to the player/request and a database existence check rejects an existing ticket/pattern/draw-sequence claim. Schema uniqueness also covers ticket/pattern/draw sequence and operation ID. Concurrency and durable replay behavior were not exercised here.
- Admin claim routes are class-restricted to `ADMIN`. Review cannot override a failed server validation or re-review terminal `APPROVED`/`REJECTED` claims. Approval creates a prize entitlement; that is not settlement or wallet credit. The entitlement settlement service currently fails closed with `PRIZE_SETTLEMENT_BLOCKED`.
- `AuditLogAspect` covers public feature service methods with mutating prefixes, including `submitWinClaim`, and records success/failure audit events. Claim-specific audit persistence and metadata were not runtime-verified.

## Findings and open rules

**Module 04D-2 update:** the prior CANCELLED-session guard gap has been fixed. The service now locks and validates the authoritative session state before claim creation. Focused database-free service tests cover cancellation rejection, no claim save/award or wallet collaborator calls, and preservation of STARTED/CLOSED behavior. Real MySQL serialization and persisted audit verification remain untested. See [Module 04D-2 report](module-04d2-claim-e2e.md) and [matrix](module-04d2-test-matrix.md).

**Closed-session policy:** the submission method also has no state gate for CLOSED sessions. No source-level claim eligibility rule distinguishes CLOSED from STARTED, and the supplied scope says to follow the existing eligibility rule. Product must define whether claims remain eligible after closure before behavior can be changed or C14 can pass.

Claim review creates a nonfinancial entitlement with configured prize data, but payout calculation/settlement policy and financial posting are outside scope. Do not interpret entitlement approval as an award paid to a wallet.

## Flutter and admin review

The player screen lists session-configured patterns, disables submit while submitting, without a selected ticket/pattern, or for a terminal session, shows a spinner, and displays success/error text. Without a ticket, it shows the existing contract-gap message. `LiveGameController` preserves the same pending idempotency key after a failed network request and clears it after success or pattern/ticket selection changes; authentication errors expire the local session. The UI button blocks ordinary repeated taps, but `submitClaim()` itself does not reject a second direct invocation while `isSubmittingClaim` is true. Controller/UI tests did not execute in this run, so these are source-review findings only.

The admin page supports claim search/filter, displays pattern, validation, status, sequence and claimant/ticket/session IDs, and allows approve/reject only for server-passed pending claims. These browser actions were not executed.

## Database-free validation

The added JUnit test class `BingoPatternEvaluatorTest` targets the production evaluator. At the end of 04D-1, Maven could not run it: the first invocation was blocked by an unwritable default `C:\.m2\repository`; an explicit local repository invocation reached compilation but failed reading/resolving a cached Spring Boot JAR. Module 04D-2 later recovered selected unit execution with a forked compiler; see its report and evidence.

As a database-free fallback, `javac` compiled only the production `BingoPatternType`, `BingoCard`, `BingoPatternEvaluator` sources and a temporary assertion harness; `java` executed six assertions successfully: LINE complete/missing, TWO_LINES complete/insufficient, and FULL_HOUSE complete/missing (including the free center). No mock or test-only rule implementation was used. This validates the pure pattern evaluator only, not the service, authorization, repositories, audit persistence, or HTTP behavior. Harness/source output is in [pure logic evidence](../evidence/module-04d1-pure-logic.txt).

At the time of Module 04D-1, Flutter validation could not start. Module 04D-2 recovered the SDK invocation using a command-scoped Git safe-directory setting and tool-approved access to the SDK cache; the claim controller retry test passed 1/1 and `flutter analyze` reported no issues. No claim mutation was sent to persistent or test QA.

## Matrix disposition and remaining blockers

Scenario-level outcomes are in the [test matrix](module-04d1-test-matrix.md). Runtime HTTP authorization, ticket ownership, idempotency/concurrency, audit persistence, and full Android claim flow remain unverified. CANCELLED rejection and Flutter retry received focused unit/controller coverage in Module 04D-2, but database row locking and audit persistence still need integration verification. No QA database or financial records were accessed or modified by these modules; no independent before/after database reconciliation was performed.

Module 04C stays PARTIAL, Module 04B stays PARTIAL, Module 02 stays INCOMPLETE, and Module 03 stays COMPLETE FOR DEFINED QA SCOPE. No Module 04E work was started. No commit or push was made.
