# Sunset Bingo release acceptance preflight

**Run date:** 2026-11-18  
**Result:** BLOCKED before application or database execution  
**State changes:** None. No account, wallet, payment, game, claim, payout,
schema, or deployment state was changed.

## Release candidates and environment

No single release candidate was available to test. The checkout is at
`3fe1bd3731913144cefe78bd7f91d648b7447d6e` on `admin-ui-enhancement`, with
uncommitted Player, Admin, and backend changes. Source version labels are
Player `0.1.2+24`, Admin `0.1.0`, and backend `0.0.1-SNAPSHOT`; these do not
identify a coordinated release candidate.

The local process environment did not expose a staging target in the inspected
QA/staging URL variable names, and no listener was found on the local app,
backend, Mailpit, or MySQL QA ports inspected (5173, 5174, 8080, 8081, 8082,
8025, 3306, and 13306). No staging endpoint, isolated database identity,
synthetic account set, or sandbox payment integration was supplied for this
run. Tool discovery found `k6` and `adb` unavailable. The checked-in QA runbook
is not staging authorization and explicitly forbids persistent-DB financial
tests.

Existing artifacts are not acceptable release candidates: Player output is a
debug APK only (`app-debug.apk`, SHA-256
`51A032BCBC0AF63E4966288D004560E8A43EEE86E51253DA55184F995DDEE5A7`); Admin
`dist` is timestamped before the current session-storage edits; backend JAR
`bingo-backend-0.0.1-SNAPSHOT.jar` (SHA-256
`88222BCD8BE8C461CE0841879A6539729388FC59A793337E42107670884192D7`) predates
current backend edits. No signed Player release AAB/APK or coordinated build
manifest was present.

## Acceptance scenarios

| Scenario | Expected result | Actual result | Environment / release | Supporting evidence |
|---|---|---|---|---|
| Flyway on a clean MySQL database | All candidate migrations apply and validate; application starts against the isolated schema. | **BLOCKED** — no isolated staging MySQL identity or clean target supplied; no connection or migration attempted. | No verified staging; no coordinated release candidate. | This checkout contains uncommitted migration V52; historical QA docs describe older V22/V40 schemas and do not authorize migration. `docs/qa/platform-e2e-runbook.md` database boundaries. |
| Flyway from a supported prior schema with synthetic data | Upgrade preserves supported synthetic records, validates history/checksums, and passes application startup. | **BLOCKED** — supported source schema and isolated copy not provisioned for this candidate. | No verified staging; no coordinated release candidate. | `docs/qa/qa-blockers-and-approvals.md` requires a separately approved clone and clone-scoped account. No database state was read or changed in this run. |
| Register, sandbox fund, ticket purchase, game entry, valid claim, Admin review, payout, ledger reconciliation | Each operation succeeds once; claim/payout and wallet ledger balance to expected synthetic amounts with audit records. | **BLOCKED** — no staging accounts or sandbox payment integration; no application journey run. The repository documents Admin prize settlement as fail-closed with `PRIZE_SETTLEMENT_BLOCKED`. | No verified staging; source labels only, no release artifacts. | `docs/qa/financial-reconciliation.md` and `docs/qa/qa-blockers-and-approvals.md` payout/reconciliation gates. |
| Failed and duplicate payment callbacks | Failed callbacks do not credit; duplicate callbacks are idempotent; one accepted payment yields one credit. | **BLOCKED** — no sandbox provider or staging callback endpoint supplied. | No verified staging; no release candidate. | `docs/qa/financial-reconciliation.md` requires verified source evidence and exact-once posting; no synthetic callback was sent. |
| Purchase retry after connection loss | Same logical purchase resolves to one server purchase and ledger debit; client recovers the authoritative outcome. | **BLOCKED** — no release Player app or staging backend available. | Local debug APK only; no matching release candidate. | Candidate artifact absent; no request was sent. |
| Concurrent purchases and claims | Contending operations preserve capacity, ownership, exact-once posting, and one valid settlement outcome. | **BLOCKED** — no isolated load target or authorized synthetic fixtures; not run. | No verified staging; `k6` unavailable. | `docs/qa/release-blockers.md` defines a 1,000-player/10-ticket target; existing integration coverage is not this run's staging evidence. |
| Rejected claims | Invalid, ineligible, wrong-owner, duplicate, or late claims are rejected without payout or partial ledger writes. | **BLOCKED** — no approved owned-ticket claim fixture, staging database, or Admin account. | No verified staging; no release candidate. | `docs/qa/qa-blockers-and-approvals.md` records unresolved claim fixture and policy constraints. |
| Restart recovery | Restart restores authoritative state and safely resumes/recovers in-flight purchase, payment, claim, and payout operations. | **BLOCKED** — no staging service or run-specific persisted operations to restart and reconcile. | No verified staging; no release candidate. | No state-changing step was performed. |
| Peak-window load | Meet approved throughput/latency/error targets while financial invariants hold. | **BLOCKED** — repository target is 1,000 players with 10-ticket capacity, but measured latency/throughput/error SLOs and a load environment are absent; `k6` is unavailable. | No load environment; no release candidate. | `docs/qa/release-blockers.md` and `docs/qa/qa-blockers-and-approvals.md` capacity gates. |
| Backup restore and rollback | Restore the candidate schema/data to an isolated recovery target, validate Flyway and balances, and demonstrate rollback/recovery timing. | **BLOCKED** — no approved recovery target, backup, or current-schema rehearsal. | No verified staging; current source contains V52 while older restore evidence covers earlier versions. | `docs/qa/defect-remediation-matrix.md`; no backup was requested or accessed. |
| Pause purchases/payouts and revoke Admin sessions | Operators can stop new purchases/payouts and invalidate privileged sessions, then verify denial and recovery. | **BLOCKED** — no staging operator access or runtime flags/controls verified. Current backend working-tree revocation edits are not in a tested release artifact; configured payout control defaults false and settlement is documented blocked. | No verified staging; no coordinated release candidate. | `application.yml`, current uncommitted backend source, and `docs/qa/financial-reconciliation.md`; no runtime configuration changed. |

## Preflight actions and disposition

Read-only actions in this attempt: inspect Git identity/status and source
version labels; inspect existing artifact paths and hashes; inspect QA
runbooks/blockers; check relevant local listening ports and tool availability.
No credentials were read or printed. No database connection, migration,
fixture setup, build, deployment, payment callback, or financial operation was
attempted.

**Disposition: NO ACCEPTANCE / BLOCKED.** Resume only after a coordinated,
immutable Player/Admin/backend candidate is identified, the owner supplies the
authorized staging URL and access path, DBA confirms an isolated MySQL target
and supported schema copy, Finance/Product approve the synthetic funding and
payout plan, the sandbox payment integration and dedicated test accounts are
available, and Operations supplies load/recovery targets and pause/revoke
procedures. Capture run-specific before/after ledger and schema evidence without
credentials or personal data.
