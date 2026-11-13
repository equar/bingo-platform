# Current QA Blockers and Required Approvals

**Status snapshot:** 2026-11-13. The latest guarded cross-client run stopped
before application startup; see the [scenario matrix](platform-e2e-scenario-matrix.md)
and [run report](evidence/platform-e2e-runs/QA_PLATFORM_20261103_1806/run-report.md).
This document records blockers; it grants no database, financial, or production
authorization.

| Area | Current blocker | Required resolution |
|---|---|---|
| Local functional E2E schema | Read-only preflight on 2026-11-13 confirmed local `bingo_qa_test` is at Flyway V22; this checkout contains migrations through V40. The maintained V22→V40 runbook is prepared for review only. The guarded launcher refuses startup. | Owner/DBA must approve and review an isolated backup/migration rehearsal covering V22→V40, and verify the runbook before migration. Then launch only through the guarded script as the normal Windows user. Do not migrate, clean, or bypass the guard. |
| Player Chrome renderer tests | A focused `flutter test --no-pub --platform chrome` run completed Dart web compilation, launched headless Chrome, and served the Flutter test page, but remained at `Running test suite` without a case result. | Diagnose the Chrome test-host/WebSocket handshake or in-browser suite execution, then rerun the focused test. In this environment invoke Flutter with the normal Windows user's SDK access; the sandbox identity cannot update the SDK cache and Flutter's launcher retries silently. No authenticated browser/backend acceptance has passed. |
| Maven integration schema | Read-only preflight on 2026-11-13 verified the ignored `bingo-backend/.env.maven-test` target is local `127.0.0.1:3306/bingo_qa_maven_test`, uses the dedicated `bingo_qa_maven_runner` account, has no access to `bingo`, `bingo_qa`, or `bingo_qa_test`, and has synthetic financial posting disabled. Its latest successful Flyway version is V26, while this checkout requires V40. The reward integration class disables Flyway and uses Hibernate `validate`. The launcher now checks exact local DB/account/port, failed-history count, and successful V40 before any fixture writes, serializes launcher runs, and refuses ordinary QA/production databases. Tests commit fixtures in request/service transactions; before/after cleanup is scoped to fixed synthetic phones, and an interrupted run leaves rows for the next run's cleanup. Concurrent calls use separate thread transactions; test-level rollback does not cover them. | DBA/Product approval is still required before any V26→V40 migration rehearsal or fresh-schema creation/reset. The prepared launcher refuses the current V26 schema. Obtain a separately approved V40 test schema, then run `scripts/qa/Invoke-RewardIntegrationTests.ps1` as the normal Windows user. Never point Maven tests at `bingo`, `bingo_qa`, or `bingo_qa_test`. Fresh-schema creation and V26→V40 upgrade rehearsal are separate evidence and must be reviewed/reported separately. |
| V40 deployment status | V40 is present only as an untracked migration file in this workspace and is not part of the accessible checked-in deployment history. The production workflow builds a checked-out `develop` commit, but no deployed Flyway history or host access is available here; external deployment status is therefore unknown. | Preserve V40 and its checksum. This change needs no schema/data correction, so no V41 has been prepared. If a future schema correction is required while deployment status remains unknown, create a forward V41 migration and gate its rehearsal using the DBA/Product approval above. |
| Owned-ticket claim fixture | Player purchases are available through the normal QA API path. Agent sale still debits float and can accrue commission. | Product, Security, and DBA must approve the design and local execution gates below before implementation or a claim E2E run. |
| Claim policy | CANCELLED claims are rejected; CLOSED-session eligibility and some lifecycle outcomes remain undecided. No valid owned-ticket fixture exists. | Product must define eligibility and state-transition outcomes. Claim testing must use the normal API and must not approve claims or create entitlements as a side effect. |
| Ticket purchase, scheduling, and cancellation | Player ticket purchase is enabled in QA and follows the production API path. The current purchase cutoff is transition to `STARTED`, not scheduled time; schedule timezone/DST/recurrence/overlap and ticket-bearing cancellation/refund policy remain unresolved. | Product defines timing and lifecycle behavior; Finance approves any funding, accounting, refund, or reversal treatment. |
| Draw retries | The Admin draw endpoint is bodyless and has no request-id contract. A request after a later due interval is a new draw, not a replay of the prior response. | Product/API approval of retry identity, replay, conflict, retention, and concurrency semantics before changing the contract. |
| Transfers and rewards | Positive persistent fixtures, policy limits, and zero-limit semantics lack approval. | Product/Finance define policy, funding, reversal, and reconciliation requirements; use a separately authorized isolated test environment. |
| Deposits, adjustments, float funding, Agent ticket sales, and payouts | Production posting and persistent financial scenarios are not approved. Synthetic QA results do not prove receipt or settlement of real funds. | Finance/accounting, Security, and Operations approve source evidence, roles, accounting, limits, reversals, idempotency, and an isolated execution plan. |
| Prize settlement | The Admin settlement operation fails closed with `PRIZE_SETTLEMENT_BLOCKED`. The separate Agent cash verification/disbursement path exists, but is not production authorization. | Product/Finance approve the payout destination, source evidence, accounting, compliance, one-time settlement, and reconciliation before enabling any production path. |
| Full reconciliation | No complete deposit → approval → ticket/transfer → payout → reversal lifecycle has been reconciled. | Run a reviewed synthetic workflow in an approved disposable environment with immutable before/after snapshots, balanced journals, exact-once retries, and unrelated-record checks. |
| Capacity and operations | No load test or capacity measurement has run; the configured 1,000-player limit is not validated. Monitoring/SLO, backup/restore drill, and incident-recovery evidence are incomplete. | Provision a separate disposable load environment and reviewed harness; collect latency, throughput, contention, and financial invariants. Operations must supply monitoring and recovery evidence. |
| Security and release | The 2026-10-22 security review left independent external security validation open; Fastlane transitive dependency locking/CI validation was partial, and production proxy/TLS/secret settings still require owner verification. | Revalidate these items on the release candidate, complete independent security review, and retain CI/deployment evidence before any GO decision. |
| Public website and store release | Production metadata, approved policy wording, support contacts, store listings, indexing decision, and legal/product review are not all established. | Product/legal/operations approve the public content and release state; keep unavailable links visibly unavailable rather than publishing placeholders. |

## Nonfinancial claim-fixture constraints

Any future fixture implementation must meet all of these conditions; this
section consolidates the still-open 04D-5B design requirements:

- Use a separate one-shot, non-web QA runner that is excluded from production
  artifacts. Do not add a public controller or browser-visible fixture route.
- Select only an explicitly allowlisted synthetic PLAYER and create its
  room/session through existing game services. Accept a run/scenario identifier,
  not arbitrary user, ticket, card, session, draw, or status values.
- Generate and validate cards with the production server-side generator.
  Never call player purchase or Agent sale services; never create wallet/float
  debits, commission, settlement, or financial journals.
- Record a fresh run UUID, operator, purpose, player/session/ticket IDs, creation
  time, and card-validation result in reviewed provenance/audit metadata.
  Do not claim existing rows have a run marker unless an approved migration
  adds one.
- Use the normal claim API, owner checks, session lock/state validator, pattern
  evaluator, idempotency, uniqueness, and audit path. The fixture tool must not
  insert claims or bypass claim validation. Do not approve claims in this test.
- Draws must use the ordinary server-selected draw service and cadence. Do not
  inject numbers, fabricate history, or relax timing rules; if no winner occurs,
  report the scenario as blocked.
- Require explicit Product/Security/DBA approval, a local-only opt-in and
  operator identity, exact `127.0.0.1:3306/bingo_qa_test` verification, current
  Flyway state, Hibernate `validate`, clean/migrations disabled, and all
  financial/scheduler flags off. No database migration or grant change is
  implicitly authorized.
- Capture read-only before/after reconciliation proving balances, transactions,
  float, journals, entitlements, and unrelated retained records did not change.
  Never auto-delete fixtures; cleanup requires a separately approved,
  run-UUID-scoped procedure and retention policy.

The current local functional-E2E `ALL PRIVILEGES` exception is scoped to
`bingo_qa_test`; it does not itself approve a ticket fixture. Verify exact
runtime identity and cross-schema denial. The machine-local root exception is
only for the guarded `android-test` backend on that same fixed target and never
authorizes Maven, persistent QA, or production access.
