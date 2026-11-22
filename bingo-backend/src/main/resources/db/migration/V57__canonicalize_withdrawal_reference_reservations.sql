-- Canonicalize external withdrawal references without rewriting the historical
-- reservation ledger. Punctuation and whitespace do not distinguish references.
-- V54/V56 tables remain intact as the audit source; runtime reservations move to
-- these canonical tables only after this backfill succeeds.
CREATE TABLE withdrawal_transfer_reference_reservations_v57 (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider_key VARCHAR(100) NOT NULL,
    normalized_reference VARCHAR(120) NOT NULL,
    reservation_status VARCHAR(24) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    legacy_source_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_withdrawal_transfer_reference_reservation_v57
        UNIQUE (provider_key, normalized_reference),
    CONSTRAINT ck_withdrawal_reference_reservation_status_v57
        CHECK (reservation_status IN ('RESERVED', 'AMBIGUOUS_LEGACY')),
    CONSTRAINT ck_withdrawal_reference_reservation_source_v57
        CHECK (source_type IN ('LEGACY', 'COMPLETION'))
) ENGINE=InnoDB;

CREATE TABLE withdrawal_transfer_reference_reservation_sources_v57 (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    withdrawal_request_id BIGINT NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_withdrawal_reference_source_request_v57 UNIQUE (withdrawal_request_id),
    CONSTRAINT fk_withdrawal_reference_source_reservation_v57
        FOREIGN KEY (reservation_id) REFERENCES withdrawal_transfer_reference_reservations_v57 (id),
    CONSTRAINT fk_withdrawal_reference_source_request_v57
        FOREIGN KEY (withdrawal_request_id) REFERENCES wallet_withdrawal_requests (id),
    CONSTRAINT ck_withdrawal_reference_source_type_v57
        CHECK (source_type IN ('LEGACY', 'COMPLETION')),
    INDEX idx_withdrawal_reference_source_reservation_v57 (reservation_id)
) ENGINE=InnoDB;

-- The union retains reservations that do not currently have a source link while
-- also deriving the canonical key directly from every completed financial row.
INSERT INTO withdrawal_transfer_reference_reservations_v57
    (provider_key, normalized_reference, reservation_status, source_type, legacy_source_count, created_at)
SELECT merged.provider_key,
       merged.canonical_reference,
       CASE WHEN SUM(merged.request_count) > 1
                      OR SUM(merged.prior_reservation_count) > 1
                      OR MAX(merged.was_ambiguous) = 1
                      OR merged.provider_key = '*'
            THEN 'AMBIGUOUS_LEGACY' ELSE 'RESERVED' END,
       'LEGACY',
       GREATEST(SUM(merged.request_count), MAX(merged.prior_source_count)),
       CURRENT_TIMESTAMP(6)
FROM (
    SELECT CASE
               WHEN request.destination_type = 'BANK' AND NULLIF(TRIM(request.bank_name), '') IS NULL THEN '*'
               WHEN request.destination_type = 'BANK' THEN UPPER(TRIM(request.bank_name))
               ELSE 'TELEBIRR'
           END AS provider_key,
           REGEXP_REPLACE(UPPER(TRIM(request.transfer_reference)), '[^A-Z0-9]', '') AS canonical_reference,
           1 AS request_count,
           0 AS was_ambiguous,
           0 AS prior_source_count,
           0 AS prior_reservation_count
    FROM wallet_withdrawal_requests request
    WHERE request.status = 'COMPLETED'
      AND request.transfer_reference IS NOT NULL
      AND REGEXP_REPLACE(UPPER(TRIM(request.transfer_reference)), '[^A-Z0-9]', '') <> ''
    UNION ALL
    SELECT reservation.provider_key,
           REGEXP_REPLACE(UPPER(TRIM(reservation.normalized_reference)), '[^A-Z0-9]', '') AS canonical_reference,
           0 AS request_count,
           CASE WHEN reservation.reservation_status = 'AMBIGUOUS_LEGACY' THEN 1 ELSE 0 END AS was_ambiguous,
           reservation.legacy_source_count AS prior_source_count,
           1 AS prior_reservation_count
    FROM withdrawal_transfer_reference_reservations reservation
    WHERE REGEXP_REPLACE(UPPER(TRIM(reservation.normalized_reference)), '[^A-Z0-9]', '') <> ''
) merged
GROUP BY merged.provider_key, merged.canonical_reference;

-- Preserve direct audit links from every completed historical request. Canonical
-- collisions are intentionally marked ambiguous above and stay reserved pending
-- explicit reconciliation; no financial record or legacy audit row is removed.
INSERT INTO withdrawal_transfer_reference_reservation_sources_v57
    (reservation_id, withdrawal_request_id, source_type, created_at)
SELECT reservation.id, request.id, 'LEGACY', CURRENT_TIMESTAMP(6)
FROM wallet_withdrawal_requests request
JOIN withdrawal_transfer_reference_reservations_v57 reservation
  ON reservation.provider_key = CASE
         WHEN request.destination_type = 'BANK' AND NULLIF(TRIM(request.bank_name), '') IS NULL THEN '*'
         WHEN request.destination_type = 'BANK' THEN UPPER(TRIM(request.bank_name))
         ELSE 'TELEBIRR'
     END
 AND reservation.normalized_reference = REGEXP_REPLACE(UPPER(TRIM(request.transfer_reference)), '[^A-Z0-9]', '')
WHERE request.status = 'COMPLETED'
  AND request.transfer_reference IS NOT NULL
  AND REGEXP_REPLACE(UPPER(TRIM(request.transfer_reference)), '[^A-Z0-9]', '') <> '';

-- Completed references containing no ASCII letters or digits cannot be safely
-- normalized. Keep them visible in the existing reconciliation queue.
INSERT INTO withdrawal_transfer_reference_migration_issues
    (withdrawal_request_id, issue_code, created_at)
SELECT request.id, 'REFERENCE_EMPTY_AFTER_NORMALIZATION', CURRENT_TIMESTAMP(6)
FROM wallet_withdrawal_requests request
WHERE request.status = 'COMPLETED'
  AND request.transfer_reference IS NOT NULL
  AND REGEXP_REPLACE(UPPER(TRIM(request.transfer_reference)), '[^A-Z0-9]', '') = ''
ON DUPLICATE KEY UPDATE issue_code = VALUES(issue_code);
