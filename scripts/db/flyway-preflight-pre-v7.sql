-- READ ONLY. Run against a schema stopped at V6 or earlier, before V7.
-- Any non-zero count is a blocker for automatic V7 upgrade; do not infer
-- operation IDs or actor identities and do not delete these financial records.
SELECT version, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;

SELECT 'PAYOUT_VERIFICATION_ROWS_NEED_REVIEW_BEFORE_V7' AS check_name, COUNT(*) AS row_count
FROM payout_verification_records;

SELECT 'LEGACY_IDEMPOTENCY_ROWS_CANNOT_BE_ASSIGNED_ACTORS_AUTOMATICALLY' AS check_name,
       COUNT(*) AS row_count
FROM idempotency_records;

SELECT 'LOG_BIN_TRUST_FUNCTION_CREATORS' AS check_name,
       @@global.log_bin_trust_function_creators AS enabled
;
