# Module 04C-4 — Test Data Integrity and Fixture Preservation

Disposition: **PARTIAL — lifecycle hazard is proven and documented; historical disappearance root cause remains UNKNOWN; verification test was blocked by stale local credentials**

Scope: Module 04C-4 only. No 04D/04E work, production financial posting, persistent QA writes, or destructive database command was performed.

## Historical session findings

The 04C-2 evidence identifies the original isolated session #30 in `bingo_qa_test` with `started_at` `2026-10-25 10:50:02.003485`. It had 46 draws: sequences 1–45 came from a stale packaged backend and had no matching draw audit records; sequence 46 was the current-source Admin draw. The reconciliation captured all 46 draw rows and one matching audit. The original clean session #31 had `started_at` `2026-10-25 11:08:01.814826`, with sequences 1–2 and two matching audit events. These evidence files do not record either original `created_at` value, so those creation timestamps are unavailable from the retained evidence. Neither session had tickets, claims, entitlements, or player wallet transactions.

The 04C-3B pre-fixture snapshot later found zero sessions and zero draws in `bingo_qa_test`, while the sequence had advanced. Its first fixtures reused numeric IDs #30 and #31, created at `2026-10-25 12:02:57.502342` and `.583118`. The new rows each have one draw and one audit; they are not the originals. No original rows were present to compare at that point. The 04C-3B evidence records the reused rows and was captured before 04C-4.

The cause of the original rows' disappearance is **UNKNOWN**. The checked-in evidence establishes absence, not the event that caused it. The current repository contains a proven destruction path: `A00FlywayUpgradeRecoveryIntegrationTest` explicitly enables Flyway clean and calls `current.clean()` before rebuilding `bingo_qa_test`. Surefire's default test lifecycle includes this test, and the PR workflow runs `mvn -q verify`. In that workflow the DB is a dedicated MySQL service provisioned for the job, so its clean is scoped to that ephemeral service. Running the same Maven lifecycle locally with credentials pointed at shared `bingo_qa_test` would clean the shared schema. Broad integration fixtures also delete all game sessions, draw events, rooms, users and audit rows in their setup hooks; these can erase retained rows if run locally against the shared schema. No execution log ties those mechanisms to the historical disappearance. No datasource mismatch, manual action, or migration event is proven as its cause. Normal test-profile Flyway configuration has clean disabled; a programmatic `clean()` path bypasses that safeguard.

## Database lifecycle and preservation controls

`bingo_qa` is persistent QA and must remain unchanged. `bingo_qa_test` is shared disposable integration QA, not a historical archive. Test-profile properties use `ddl-auto: validate`, Flyway `clean-disabled: true`, and `validate-on-migrate: true`. The integration guard requires local MySQL `127.0.0.1`/`localhost:3306/bingo_qa_test` and rejects credentials that can access `bingo` or `bingo_qa`. Those controls do not make the schema durable: the dedicated Flyway recovery test overrides clean protection in code and destructive fixture suites issue broad deletes.

Controls for each future evidence-producing run:

1. Give each fixture a run UUID/correlation ID and unique human-readable name; record immutable DB primary keys, creation timestamps, and the source commit/runtime identity.
2. Capture sanitized read-only before and after snapshots keyed by run ID, not numeric IDs alone. Include session, draw sequence/number/operation ID/drawn timestamp, and linked audit event IDs/timestamps/metadata.
3. Export the evidence files before any broad test suite, preserve test output and exact command, and record fixture counts and financial-table zero checks.
4. Recreate deterministic fixtures through application/test setup with documented expected values; never treat reused auto-increment IDs as identity.
5. Label destructive suites and cleanup hooks with ownership and target scope. The Flyway clean test and broad fixture-reset suites may run only against a fresh CI/service schema or a newly provisioned disposable local schema; they must not be run against shared `bingo_qa_test` evidence.

No permanent retention promise is made for `bingo_qa_test`. If historical test records must survive, establish a separately governed, access-controlled non-disposable QA evidence environment with backups and approved retention. Do not convert `bingo_qa_test` into that environment implicitly.

## Verification attempt and persistent QA

The explicitly selected `DrawRetrySafetyIntegrationTest` did not execute its test bodies. Its static isolation guard rejected the credentials currently stored in `bingo-backend/.env.test` (`bingo_test`), and MySQL returned access denied before any connection or fixture write. The preexisting C4 isolated snapshot remained the last known snapshot; no after snapshot could be taken through the rejected credentials. The suite was not rerun with guessed or weakened credentials. A first Maven invocation also failed before test execution because its default local repository resolved to unwritable `C:\.m2`; Maven was then pointed to the existing user cache.

The C4 persistent `bingo_qa` before snapshot is preserved at [persistent before snapshot](../evidence/module-04c4-persistent-database-before.txt). No persistent connection or mutation was made in this audit. This is not a new before/after equality proof for this run; the existing C2/C3B byte-identical comparisons remain the prior evidence.

See [04C-2 original session reconciliation](../evidence/module-04c2-session30-stale-reconciliation.txt), [04C-2 clean session reconciliation](../evidence/module-04c2-clean-session-reconciliation.txt), and [04C-3B pre-fixture snapshot](../evidence/module-04c4-isolated-database-before.txt).

### 04C-5 follow-up

The proposed three-way split is `bingo_qa` (persistent), `bingo_qa_test` (Android/Admin fixtures), and `bingo_qa_maven_test` (disposable destructive backend integration). C5 found the ignored local `.env.test` targets `bingo_qa_test` with account `bingo_test`; the previous MySQL attempt was rejected before connection. The current account host restrictions and grants cannot be confirmed. No local DBA credentials were available to provision the dedicated test schema/users or restore the Android runtime identity. C5 stopped before mutation; C5A later prepared source/config guards and CI routing but could not verify them against MySQL. See [database isolation report](module-04c5-database-isolation.md), [C5A recovery plan](module-04c5a-mysql-recovery-plan.md), [gate evidence](../evidence/module-04c5-isolation-gate.txt), and [C5 retry test report](module-04c5-draw-retry-tests.md). The credential value is omitted; rotate the local test account secret before reuse.
