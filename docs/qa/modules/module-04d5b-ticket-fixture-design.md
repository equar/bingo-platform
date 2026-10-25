# Module 04D-5B — Nonfinancial Bingo Ticket Fixture Design

Disposition: **COMPLETE FOR DESIGN — a gated, non-HTTP fixture-runner design and claim E2E reconciliation plan are documented and database-free checks pass. No fixture-writing code or endpoint was implemented, no database connection was made, and no records were created. Module 04D remains PARTIAL.**

Scope: design and database-free validation only. No migrations, MySQL queries, backend runtime, Android/Admin E2E, financial operation, commit or push occurred.

## Findings from existing code

- `BingoGameService.purchaseTicket` is the normal player purchase path. It invokes `LocalFinancialSimulationGuard`, debits the owner's wallet, generates a card, creates a ticket, and records a wallet transaction. It cannot be used for this fixture while financial posting is disabled.
- `AgentService.sellTicket` debits an agent float before `BingoGameService.createAgentTicket`, then creates a commission accrual. It is also excluded.
- `BingoCardGenerator.generate()` creates a server-side 5×5 card with column ranges B 1–15, I 16–30, N 31–45, G 46–60, O 61–75 and a free center. The generator's `validate` checks the card structure/ranges; ticket `grid_json` is the existing server-authoritative representation.
- Ticket rows reference both `bingo_sessions` and `users`; claim submission derives session from the ticket and queries by ticket ID plus authenticated owner ID. The request contains no client-supplied owner, grid, session or draw history.
- Claim submission locks the session row, rejects CANCELLED before claim persistence, evaluates server-side draws, and persists either PENDING_REVIEW or FAILED_SERVER_VALIDATION. A uniqueness constraint covers ticket/pattern/draw sequence, and the operation ID is unique. `AuditLogAspect` and `AuditLogService` capture success and failure audit events.
- Admin claim routes require ADMIN. The browser claims page renders claimant/ticket/session references, validation and status; it does not enable review actions unless a claim is server-passed and PENDING_REVIEW. Do not approve claims in the E2E plan: approval creates a prize entitlement and is outside this fixture objective.
- No existing QA fixture writer for owned Bingo tickets was found. The local `bingo_qa_test_runner` account was verified in 04D-4 as schema-scoped but overprivileged (ALL PRIVILEGES on `bingo_qa_test.*`). It must be reduced before any QA runtime or writer is run.

## Recommended architecture

Implement a **separate one-shot local QA fixture runner**, not an HTTP endpoint. Keep its source/artifact outside the production backend artifact (for example, a dedicated `bingo-qa-fixture-runner` module that depends on backend domain services). It runs as a command-line process with Spring's web application disabled, provisions one fixture run, emits non-secret identifiers, and exits. The ordinary `android-test` backend remains a separate process and exposes only existing production application routes. This keeps fixture creation out of production routing and prevents an accidentally enabled test controller.

The runner should select a dedicated synthetic PLAYER identity created through the ordinary account-registration workflow and tagged with an unmistakable `QA SYNTHETIC` display label. It must verify that identity's role/status and membership in a locally configured fixture allowlist; the command accepts a run/scenario identifier, not arbitrary player identity or arbitrary SQL. Do not reuse historical fixture IDs. Associate the run with a new unique correlation UUID.

For each run, create a dedicated room/session through existing game services and standard lifecycle transitions. Create tickets only through a narrowly scoped fixture service that:

1. Calls the production `BingoCardGenerator.generate()` and `validate()`; it never accepts a grid from a request.
2. Links the generated ticket to the selected synthetic player and that run's session, uses unique ticket/operation identifiers, and applies an explicit `QA SYNTHETIC` marker.
3. Does not call `purchaseTicket`, create wallet debits, call agent services, or call commission/settlement services. A zero-balance wallet created by ordinary registration may remain unchanged; reconciliation must show no wallet transaction or balance delta.
4. Creates provenance before or atomically with the ticket: a run manifest records UUID, purpose/scenario, synthetic player, room/session/ticket identifiers, creation timestamp, generated-card validation outcome and operator identity. Fixture audit metadata records `synthetic=true`, the same UUID, and every created identifier. Draws must continue through the normal server-selected draw service, with its existing audit behavior; client-chosen numbers are forbidden.
5. Uses normal claim endpoint, ownership validation, session lock/state validator, pattern evaluator, idempotency, repository constraints and audit path. The fixture component must not create claim rows or call claim repositories.

Current tables have no universal fixture-run marker. The implementation approval must choose and review a provenance representation before coding: either a small run-manifest migration plus audit metadata or an existing-schema audit/identifier scheme with enough information to identify every linked record. Any migration requires separate DBA approval and must be applied only after the schema safety gates pass. Do not silently add schema changes during test startup.

For fast positive claims, use more than one generated ticket across dedicated synthetic players, then advance draws only through the ordinary authorized server draw path and interval. Stop when an eligible ticket has a real matching pattern. Do not inject chosen draw values, shorten timing rules without approval, or fabricate draw history. If this cannot produce a timely valid winner, record the run as blocked rather than altering game integrity.

## Required security and runtime gates

All conditions are required; profile name alone is insufficient:

- Exact local profiles: `android-test` plus a fixture-runner-only profile, with `dev`, `qa`, `prod`, and Maven `test` profiles rejected. The fixture-runner process must disable the web server.
- A false-by-default fixture property plus a separate explicit local approval capability supplied through the local secret environment. Validate it without logging it and compare in constant time. Approval is tied to a documented Product/Security authorization and one invocation/run UUID.
- The verified local JDBC target must be `127.0.0.1:3306/bingo_qa_test`, database creation disabled, current catalog exact, and authenticated account exact/local. Reject `bingo`, `bingo_qa`, `bingo_qa_maven_test`, remote hosts, unexpected accounts, CI identities and wildcard hosts.
- Grant inspection must allow only global USAGE and the exact minimum schema privileges required. For fixture creation and claim E2E, prefer SELECT/INSERT/UPDATE only; no CREATE, ALTER, DROP, DELETE, GRANT OPTION, global privilege or other schema grant. If actual app behavior requires a privilege outside that list, stop for DBA/security review.
- Require Hibernate `validate`, Flyway clean disabled, no database auto-create, an already-current and valid Flyway version, and no pending migration. Fixture execution must not migrate or clean.
- Verify all simulation/posting flags false, settlement hard-blocked, and no real payment/bank/mail client enabled. Fixture/test services are unavailable in production builds/profiles. Do not allow synthetic financial opt-in as a substitute.
- Require a dedicated fixture operator identity and synthetic PLAYER allowlist. The fixture run request cannot supply arbitrary player/ticket IDs, card contents, draw numbers or status. Record operator, run UUID, action and result in audit evidence.
- Keep fixture network surface local and one-shot; no public controller or browser-visible fixture route. Do not expose fixture credentials or approval material in logs, screenshots, evidence, Git, or API responses.

The 04D-4 grant result means DBA work is a hard prerequisite: remove the existing account's schema-wide ALL PRIVILEGES and verify the reduced grant set and denied schemas before any runner is implemented or started.

## Provenance and cleanup

Every fixture run has a fresh UUID. The manifest and fixture audit metadata carry it; the synthetic player/session labels identify QA data; ticket, draw, claim and audit rows are linked through their normal foreign keys and the manifest's entity IDs. Do not claim a base row contains a run UUID unless an approved migration adds that column. Evidence stores IDs and run UUID, never credentials. Capture the pre-run read-only snapshot before invoking the writer.

Do not auto-delete fixtures at runner exit, use `Flyway clean`, or delete broad user/session ranges. Retain runs as labeled QA data until evidence is archived and a Product/DBA-approved retention policy authorizes cleanup. If cleanup is approved later, require a separate run-UUID-scoped cleanup tool and independent pre/post reconciliation; the runtime fixture account should not receive DELETE privilege.

## Approvals and prerequisites

- **Product approval:** synthetic nonfinancial tickets are eligible for claims in the chosen session state; fixture player/session labeling; acceptable draw cadence; CLOSED claim behavior stays unchanged until the separate Product decision is recorded.
- **Security approval:** one-shot runner boundary, local approval capability, operator identity, production artifact exclusion, least-privilege policy, audit metadata and retention/cleanup model.
- **DBA approval/action:** correct the overprivileged `bingo_qa_test` account or provision a dedicated least-privilege local runner account; preserve `bingo_qa`, deny all cross-schema access, verify grant output and current account; approve any manifest migration separately.
- **QA prerequisites:** current backend build, Android emulator/API routing, Admin Chromium environment, dedicated synthetic accounts and credentials in ignored secure local storage, read-only baseline/reconciliation scripts, and an approved test run plan.

## Implementation steps after design approvals

1. Implement the pure fixture policy and tests first; require exact multi-factor config, URL/catalog/account/grant allowlist, all financial switches off, schema current, no pending migrations, and production/profile rejection.
2. Add the separate non-web fixture runner and service using the existing repositories/domain/card generator. Add run manifest/audit provenance. Keep it out of production packaging.
3. Add database-free tests for safe config acceptance/rejection, generated-card invariants, no wallet/agent/commission collaborators, and no claim bypass. Add isolated persistence tests only after DBA authorization and account grants pass.
4. Review production artifact contents and route mappings to prove no fixture route/service is available in production.
5. After the existing schema/account grant issue is fixed, start the normal Android QA backend with migration-safe configuration, verify runtime identity/current grants, execute the fixture runner once, capture read-only baseline, then proceed to the E2E matrix.

## Database-free verification in this design module

The existing `BingoGameRulesTest.generatedCardsRespectGridRules` is the pure card/fixture validation check. Focused claim security tests verify ticket ownership and CANCELLED ordering with mocks; the pattern tests use the production evaluator; financial and database-target guards verify rejection without JDBC. Static inspection confirms ticket purchase and agent sale remain financial paths and the normal claim path owns eligibility. Exact run results are in [design evidence](../evidence/module-04d5b-design-checks.txt).

This is a design deliverable only. No fixture writer, schema migration, database access, Android/Admin test, or Module 04E work was performed. Module 04D remains PARTIAL; Module 04C and 04B remain PARTIAL; Module 02 remains INCOMPLETE; Module 03 remains COMPLETE FOR DEFINED QA SCOPE.
