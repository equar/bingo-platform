# Flyway upgrade compatibility audit

Audit date: 2026-11-22. The migration set in `bingo-backend/src/main/resources/db/migration` is contiguous from V1 through V55. No historical migration was changed.

## Findings

### V7 and populated payout verification records

This is a real upgrade risk for supported histories. V5 creates `payout_verification_records`; V7 later adds `operation_id varchar(36) not null` without a default and then adds a unique index. The repository's upgrade test explicitly supports upgrading a schema at V6. Agent payout verification is available after V5, so a V6 database can legitimately have payout rows. On MySQL, adding a required column with no value for existing rows can fail under strict mode; where the server materializes an implicit empty value, the subsequent unique index fails when more than one row exists. A single historical row may instead retain an empty identifier, which is not a valid operation ID.

V7 also alters `idempotency_records` before reaching the payout statements. MySQL DDL is not rolled back as a unit, so a failed V7 can leave those earlier statements applied while Flyway records V7 as failed. Flyway will not proceed to a later additive migration in that state. There is no safe automatic repair that can guess a payout operation's original ID. Recovery must assign fresh unique UUIDs to rows that lack an ID, finish the expected V7 schema, and mark V7 successful only after verifying every V7 effect. Do not assign fabricated IDs to idempotency actors.

### V3 and V22 duplicate room-session uniqueness

V3 already defines unique `(room_id, scheduled_start_at)` as `uk_bingo_sessions_room_start`; V22 adds the same unique key under `uk_bingo_sessions_room_schedule`. MySQL permits these two differently named indexes. The second index is redundant storage and write overhead, but on an unmodified V3 history it does not encounter duplicate data: V3 already prevents it. No data migration or recovery is required. Removing either index in a new migration would add operational risk without changing the invariant, so this audit does not add such a migration.

### Other populated-database checks

- V15 adds uniqueness to agent identity documents; pre-existing duplicate identity documents would make it fail and need reconciliation without deleting rows.
- V36 adds uniqueness for `(ticket_id, prize_type)` and explicitly intends to stop on duplicate historical awards. Those records require financial reconciliation, not automatic cleanup.
- V54 handles normalized withdrawal-reference collisions by retaining the oldest reservation and leaving ambiguous newer rows unreserved; raw historical references are preserved.
- V55 makes V7's `actor_id` non-null and adds a user FK. Legacy V2 idempotency records that remain after V7 have `actor_id = NULL`. They cannot be assigned a real actor from the schema alone, and the constraint will fail safely while any such records remain. A deployment with these rows needs an approved retention/replay remediation before proceeding; do not attach them to an arbitrary user or delete them to make the migration pass.
- V27's role-phone unique key follows V1's global phone uniqueness and backfills from that source, so that key is not expected to create collisions on an unmodified history.
- Remaining reviewed changes either add nullable/defaulted columns, add tables, or carry explicit data conversion/guard behavior. Check constraints and generated-column behavior still require the MySQL version supported by deployment; this static review does not replace running the full upgrade matrix against that version.

## Guarded V7 recovery procedure

Use only on a disposable clone or during an explicitly approved production maintenance procedure. This document does not authorize a production connection or data change. Take and verify a database backup first. Stop if the recorded failed migration is not V7, if the schema differs from the expected V7 partial state, or if the payout rows cannot be reconciled.

1. Record the MySQL version, `SHOW CREATE TABLE` for `idempotency_records` and `payout_verification_records`, the V7 row in `flyway_schema_history`, and counts of both tables. Confirm that V7's first three idempotency changes (numeric `id` primary key, nullable `actor_id`, and `uk_idempotency_records_actor_key`) are already present. If not, stop and prepare a migration-specific recovery plan; do not rerun V7 blindly.
2. Inspect all payout rows and preserve a before-recovery export. Add `operation_id varchar(36) NULL` only if the column is absent. Generate a new UUID for every null or empty value:

   ```sql
   UPDATE payout_verification_records
   SET operation_id = UUID()
   WHERE operation_id IS NULL OR TRIM(operation_id) = '';
   ```

   Then verify there are no null/empty values and `count(*) = count(distinct operation_id)`. Do not overwrite nonempty IDs.
3. Change `operation_id` to `varchar(36) NOT NULL` and add `uk_payout_verification_records_operation_id` only if it is absent. Verify the resulting column definition, index, and every preserved payout row.
4. Only after all effects of V7 match its intended schema, and after the recovery SQL and results have been independently reviewed, mark the exact failed V7 row successful in Flyway history. This is a controlled exception because `flyway repair` removes a failed row and would cause Flyway to execute the non-idempotent V7 statements again. Verify the row checksum against the repository's V7 migration, run Flyway `validate`, then run `migrate` and validate again. Keep the recovery SQL, backup reference, before/after counts, index/column checks, and Flyway output in the change record.
5. Before applying V55, count null actors and orphaned non-null actors in `idempotency_records`. If either exists, stop: V55 is expected to fail. Resolve the legacy idempotency retention/replay policy with the data owner and a reviewed, lossless remediation. Do not invent actor mappings, discard records, or bypass the FK/NOT NULL check.

## Rollback limits

Flyway/MySQL DDL is not generally transactional. A failed migration may leave earlier DDL committed. Rolling back V7's `operation_id` addition would discard newly assigned identifiers and can break the agent payout entity and operation uniqueness; do not reverse it casually. Dropping V22's redundant index is technically possible but is not needed to restore correctness. Database restore from the verified pre-change backup is the reliable rollback for a failed recovery; it also rolls back all writes made after that backup, so coordinate a write freeze and reconciliation window.

## Verification status and required matrix

The repository's `A00FlywayUpgradeRecoveryIntegrationTest` covers a fresh install, V6-to-latest upgrade, repeat migrate, and Flyway validation/checksum repair, but it does not seed nonempty V5 payout rows or legacy null-actor idempotency records. `FlywayV7MigrationIntegrationTest` verifies only the resulting schema. No real-MySQL or dump/restore result is claimed by this audit: the required `BINGO_MAVEN_MYSQL_URL`, username, and password were absent in the environment, and the running local MySQL service could not be proven to be the dedicated disposable test instance. The project test guard correctly refuses to run without that proof.

Before deployment, run the following on a dedicated disposable MySQL instance matching production's MySQL version, never on production:

1. Fresh V1-to-latest install; `flyway validate` before and after `migrate`.
2. V6 upgrade with empty and nonempty `payout_verification_records`; verify/document the expected V7 failure on the unmodified historical script, execute the guarded recovery, then validate and upgrade.
3. V6 upgrade containing multiple payout rows and V2-era idempotency rows; verify payout UUID recovery and confirm V55 stops on null actors without modifying those rows.
4. Upgrade from a representative post-V7 schema (for example V22) with populated bingo sessions; verify both named unique indexes and Flyway validation.
5. Dump the recovered database, restore to a second disposable schema, then verify row counts, operation IDs, both room-session indexes, migration checksums, and `flyway validate`.

Until these MySQL runs are available, this is an evidence-based static audit and recovery plan, not a tested migration repair.
