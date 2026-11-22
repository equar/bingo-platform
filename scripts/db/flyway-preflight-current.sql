-- READ ONLY. Run against a schema that has completed V57 (current latest at
-- review time). This script returns counts only; it does not expose row data.
SELECT version, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;

SELECT 'FAILED_FLYWAY_MIGRATIONS' AS check_name, COUNT(*) AS row_count
FROM flyway_schema_history WHERE success = 0
UNION ALL
SELECT 'IDEMPOTENCY_NULL_ACTOR', COUNT(*)
FROM idempotency_records WHERE actor_id IS NULL
UNION ALL
SELECT 'IDEMPOTENCY_ORPHAN_ACTOR', COUNT(*)
FROM idempotency_records idem
LEFT JOIN users actor ON actor.id = idem.actor_id
WHERE idem.actor_id IS NOT NULL AND actor.id IS NULL
UNION ALL
SELECT 'PAYOUT_VERIFICATION_ROWS', COUNT(*) FROM payout_verification_records
UNION ALL
SELECT 'PAYOUT_VERIFICATION_NULL_OR_EMPTY_OPERATION_ID', COUNT(*)
FROM payout_verification_records
WHERE operation_id IS NULL OR TRIM(operation_id) = ''
UNION ALL
SELECT 'PAYOUT_VERIFICATION_DUPLICATE_OPERATION_ID_GROUPS', COUNT(*)
FROM (
    SELECT operation_id
    FROM payout_verification_records
    GROUP BY operation_id
    HAVING COUNT(*) > 1
) duplicate_operations
UNION ALL
SELECT 'WITHDRAWAL_COMPLETED_WITHOUT_RAW_REFERENCE', COUNT(*)
FROM wallet_withdrawal_requests
WHERE status = 'COMPLETED' AND (transfer_reference IS NULL OR TRIM(transfer_reference) = '')
UNION ALL
SELECT 'WITHDRAWAL_COMPLETED_REFERENCE_WITHOUT_V57_SOURCE', COUNT(*)
FROM wallet_withdrawal_requests withdrawal
LEFT JOIN withdrawal_transfer_reference_reservation_sources_v57 ref_source
  ON ref_source.withdrawal_request_id = withdrawal.id
WHERE withdrawal.status = 'COMPLETED'
  AND withdrawal.transfer_reference IS NOT NULL
  AND REGEXP_REPLACE(UPPER(TRIM(withdrawal.transfer_reference)), '[^A-Z0-9]', '') <> ''
  AND ref_source.id IS NULL
UNION ALL
SELECT 'V57_AMBIGUOUS_REFERENCE_RESERVATIONS', COUNT(*)
FROM withdrawal_transfer_reference_reservations_v57
WHERE reservation_status = 'AMBIGUOUS_LEGACY'
UNION ALL
SELECT 'WITHDRAWAL_REFERENCE_RECONCILIATION_ISSUES', COUNT(*)
FROM withdrawal_transfer_reference_migration_issues;

-- Distinct provider/reference collisions after the runtime canonicalization.
-- Every returned group needs a retention decision; V57 keeps the key reserved.
SELECT COUNT(*) AS historical_canonical_collision_groups
FROM (
    SELECT CASE
               WHEN destination_type = 'BANK' AND NULLIF(TRIM(bank_name), '') IS NULL THEN '*'
               WHEN destination_type = 'BANK' THEN UPPER(TRIM(bank_name))
               ELSE 'TELEBIRR'
           END AS provider_key,
           REGEXP_REPLACE(UPPER(TRIM(transfer_reference)), '[^A-Z0-9]', '') AS canonical_reference,
           COUNT(*) AS source_count
    FROM wallet_withdrawal_requests
    WHERE status = 'COMPLETED'
      AND transfer_reference IS NOT NULL
      AND REGEXP_REPLACE(UPPER(TRIM(transfer_reference)), '[^A-Z0-9]', '') <> ''
    GROUP BY provider_key, canonical_reference
    HAVING COUNT(*) > 1
) conflicts;

-- Schema invariants expected after V55/V57. Each count should equal one.
SELECT 'ACTOR_ID_NOT_NULL' AS check_name, COUNT(*) AS constraint_count
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'idempotency_records'
  AND column_name = 'actor_id' AND is_nullable = 'NO'
UNION ALL
SELECT 'ACTOR_USER_FOREIGN_KEY', COUNT(*)
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE() AND table_name = 'idempotency_records'
  AND column_name = 'actor_id' AND referenced_table_name = 'users'
  AND referenced_column_name = 'id'
UNION ALL
SELECT 'ACTOR_SCOPED_KEY_UNIQUE_INDEX', COUNT(*)
FROM (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'idempotency_records'
      AND non_unique = 0
    GROUP BY index_name
    HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'actor_id,idempotency_key'
) actor_key
UNION ALL
SELECT 'PAYOUT_OPERATION_ID_NOT_NULL', COUNT(*)
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'payout_verification_records'
  AND column_name = 'operation_id' AND is_nullable = 'NO'
UNION ALL
SELECT 'PAYOUT_OPERATION_ID_UNIQUE_INDEX', COUNT(*)
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'payout_verification_records'
  AND index_name = 'uk_payout_verification_records_operation_id' AND non_unique = 0
UNION ALL
SELECT 'V54_PROVIDER_REFERENCE_UNIQUE_INDEX', COUNT(*)
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'wallet_withdrawal_requests'
  AND index_name = 'uk_withdrawal_provider_transfer_reference' AND non_unique = 0
UNION ALL
SELECT 'V57_PROVIDER_REFERENCE_UNIQUE_INDEX', COUNT(*)
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'withdrawal_transfer_reference_reservations_v57'
  AND index_name = 'uk_withdrawal_transfer_reference_reservation_v57' AND non_unique = 0
UNION ALL
SELECT 'V56_PROVIDER_REFERENCE_RESERVATION_TABLE', COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'withdrawal_transfer_reference_reservations'
UNION ALL
SELECT 'V57_REFERENCE_RECONCILIATION_ISSUE_TABLE', COUNT(*)
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'withdrawal_transfer_reference_migration_issues'
UNION ALL
SELECT 'V57_WITHDRAWAL_SOURCE_UNIQUE_INDEX', COUNT(*)
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'withdrawal_transfer_reference_reservation_sources_v57'
  AND index_name = 'uk_withdrawal_reference_source_request_v57' AND non_unique = 0
UNION ALL
SELECT 'V57_WITHDRAWAL_SOURCE_REQUEST_FOREIGN_KEY', COUNT(*)
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE()
  AND table_name = 'withdrawal_transfer_reference_reservation_sources_v57'
  AND column_name = 'withdrawal_request_id'
  AND referenced_table_name = 'wallet_withdrawal_requests'
  AND referenced_column_name = 'id';

SELECT 'LOG_BIN_TRUST_FUNCTION_CREATORS' AS check_name,
       @@global.log_bin_trust_function_creators AS enabled;

-- Deposit accounting journal double-entry reconciliation. A non-zero result is
-- a blocker requiring financial investigation, not a migration auto-repair.
SELECT COUNT(*) AS unbalanced_deposit_journals
FROM (
    SELECT journal.id
    FROM deposit_accounting_journals journal
    LEFT JOIN deposit_accounting_journal_lines line ON line.journal_id = journal.id
    GROUP BY journal.id
    HAVING COALESCE(SUM(line.debit_amount), 0) <> COALESCE(SUM(line.credit_amount), 0)
) unbalanced;
