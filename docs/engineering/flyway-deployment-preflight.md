# Flyway deployment preflight

**Reviewed migrations:** V7, V54, V55, V56, V57 (latest in this checkout).
**Purpose:** read-only deployment checks and the guarded test procedure. These checks never infer actor mappings, rewrite references, or delete financial records.

## Migration findings

- **V7:** adds nullable `idempotency_records.actor_id` and a unique actor/key index, then adds `payout_verification_records.operation_id NOT NULL` and uniqueness. On a V6 schema, populated payout records have no valid operation IDs to backfill; MySQL may reject the ALTER or materialize an invalid duplicate empty value. Existing idempotency rows receive no actor identity. V7 is historical and must not be edited. Before V7, run `scripts/db/flyway-preflight-pre-v7.sql`. Any payout rows require an approved lossless operation-ID remediation; any idempotency rows require an approved retention/replay policy and real actor mapping. Do not fabricate actors or discard rows.
- **V54:** normalizes completed transfer references with trim and uppercase. When legacy references collide, it keeps one value in the unique-indexed columns and sets the other normalized values to `NULL`; MySQL unique indexes allow multiple `NULL`s. Raw withdrawal rows remain, but those references were not all reserved by V54 alone.
- **V55:** makes `actor_id` non-null and adds an FK to `users`. Null and orphan actors block the migration. There is no safe mapping inferable from the key or response body alone; retain those rows until an owner approves reconciliation/retention.
- **V56:** creates a provider/reference reservation ledger and links historical completed withdrawal rows, including references V54 nulled. Missing references are preserved in a migration-issue queue. Collisions are marked ambiguous and remain reserved.
- **V57 (latest):** builds canonical reservation keys by removing punctuation/whitespace and uppercasing. Canonical collisions remain reserved as ambiguous; unnormalizable and missing references remain in the reconciliation queue. Its historical tables and audit links should not be removed as part of remediation.

## Read-only preflight

Run the pre-V7 SQL only against a schema at V6 or earlier. Run the current SQL only after V57. Use a read-only database account and a protected MySQL option file; do not put passwords in command-line arguments, checked-in files, or logs. The scripts return counts and migration metadata, not financial row details.

```sh
mysql --defaults-extra-file=/secure/path/read-only.cnf --database=bingo < scripts/db/flyway-preflight-current.sql
```

Use the matching isolated test schema when rehearsing; never repoint these checks at the regular local `bingo` datasource for upgrade testing. A preflight is evidence for triage, not authorization to automatically repair data. Stop deployment if the schema version is outside the selected script's stated range, a required table is missing, Flyway has failed migrations, V55 reports null/orphan actors, payout operation IDs are invalid, V57 historical links are missing, constraints are absent, references need reconciliation, or deposit journals are unbalanced.

V27 creates MySQL triggers. On a binary-logged MySQL server, a schema-scoped user without `SUPER`/`SYSTEM_VARIABLES_ADMIN` cannot create them when `log_bin_trust_function_creators` is disabled. Check that variable before an upgrade; do not weaken a live server's global setting to make a test pass. The isolated MySQL test instance must be provisioned with an approved setting or equivalent narrowly scoped administrative migration identity.

For a target before V7, any payout verification rows are a blocker until the business/data owner approves a lossless assignment of unique operation IDs. Do not assign an idempotency actor without authoritative evidence. For V55 failures, preserve null/orphan rows and coordinate an explicit data-retention and remediation decision before retrying the migration.

## Guarded MySQL 8 verification

Integration tests inherit `MySqlIntegrationTest`, which verifies the exact loopback `bingo_qa_maven_test` catalog, dedicated `bingo_qa_maven_runner` identity, local MySQL port, no access to `bingo`, `bingo_qa`, or `bingo_qa_test`, and grants limited to the disposable schema. The upgrade test cleans only that guarded schema. Keep these checks enabled; do not set the CI disposable override locally or point `BINGO_MAVEN_*` at `bingo`.

Run through `scripts/ci/run-component-checks.mjs backend` so the local Maven environment is loaded and prevalidated. The suite includes fresh and V6-to-latest migration paths, a V13-to-latest populated-schema path, existing idempotency/deposit/withdrawal concurrency checks, double-entry journal assertions, and withdrawal reservation migration checks. MySQL is mandatory; H2 and mocks are not substitutes.

Dump/restore rehearsal must use a separately confirmed disposable MySQL schema and restore the dump only to that same schema. Validate schema history/checksums and critical row counts before and after restore. Do not use a production or shared `bingo` schema. If `mysqldump`/`mysql` are unavailable or the protected disposable target cannot be verified, report this part blocked rather than claiming a rehearsal.

## Execution evidence (2026-11-22)

The existing Maven guard accepted the loopback `bingo_qa_maven_test` schema and verified MySQL 8.0, the dedicated account, and schema-only grants. `mvn -DskipTests test-compile` passed. The guarded `mvn verify` attempted 399 tests: 0 assertion failures, 178 errors, and 3 skipped. Fresh/current and V13 upgrade paths stopped at historical V27, which failed creating its first trigger with MySQL error 1419: binary logging is enabled, `log_bin_trust_function_creators` is disabled, and the account has no global `SUPER` privilege. The runner then reported Flyway validation failures for contexts sharing that failed schema. No account grants or server variables were changed.

The corrected focused V13-to-current test was also attempted and stopped at the same V27 trigger prerequisite before reaching V54-V57. The new dump/restore case could not run because it first builds the complete current schema and the same migration barrier prevents that. Consequently no pass is claimed for the V13-to-V57 upgrade, concurrent financial integration suite, ledger reconciliation integration checks, or dump/restore rehearsal on this host. These require an approved disposable MySQL 8 instance configured for V27 trigger creation (or an approved migration role) while retaining the strict schema guard. The disposable migration test schema is cleaned by the migration test class after execution.
