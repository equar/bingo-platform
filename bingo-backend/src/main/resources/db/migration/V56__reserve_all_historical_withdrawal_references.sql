-- V54 reserves only one row when legacy completed withdrawals collide after
-- normalization. Keep every historical row and reserve each distinct
-- provider/reference pair in a separate ledger so NULL normalized values on
-- older rows cannot make those references reusable.
CREATE TABLE withdrawal_transfer_reference_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider_key VARCHAR(100) NOT NULL,
    normalized_reference VARCHAR(120) NOT NULL,
    reservation_status VARCHAR(24) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    legacy_source_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_withdrawal_transfer_reference_reservation
        UNIQUE (provider_key, normalized_reference),
    CONSTRAINT ck_withdrawal_reference_reservation_status
        CHECK (reservation_status IN ('RESERVED', 'AMBIGUOUS_LEGACY')),
    CONSTRAINT ck_withdrawal_reference_reservation_source
        CHECK (source_type IN ('LEGACY', 'COMPLETION'))
) ENGINE=InnoDB;

CREATE TABLE withdrawal_transfer_reference_reservation_sources (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    withdrawal_request_id BIGINT NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_withdrawal_reference_source_request UNIQUE (withdrawal_request_id),
    CONSTRAINT fk_withdrawal_reference_source_reservation
        FOREIGN KEY (reservation_id) REFERENCES withdrawal_transfer_reference_reservations (id),
    CONSTRAINT fk_withdrawal_reference_source_request
        FOREIGN KEY (withdrawal_request_id) REFERENCES wallet_withdrawal_requests (id),
    CONSTRAINT ck_withdrawal_reference_source_type
        CHECK (source_type IN ('LEGACY', 'COMPLETION')),
    INDEX idx_withdrawal_reference_source_reservation (reservation_id)
) ENGINE=InnoDB;

-- Completed BANK records with a missing bank name cannot be safely assigned
-- to a provider. Reserve those references in the wildcard scope. The service
-- checks this scope for every provider, preventing reuse until an operator has
-- explicitly reconciled the legacy identity.
INSERT INTO withdrawal_transfer_reference_reservations
    (provider_key, normalized_reference, reservation_status, source_type, legacy_source_count, created_at)
SELECT legacy.provider_key,
       legacy.normalized_reference,
       CASE WHEN legacy.source_count > 1 OR legacy.provider_key = '*'
            THEN 'AMBIGUOUS_LEGACY' ELSE 'RESERVED' END,
       'LEGACY', legacy.source_count, CURRENT_TIMESTAMP(6)
FROM (
    SELECT CASE
               WHEN destination_type = 'BANK' AND NULLIF(TRIM(bank_name), '') IS NULL THEN '*'
               WHEN destination_type = 'BANK' THEN UPPER(TRIM(bank_name))
               ELSE 'TELEBIRR'
           END AS provider_key,
           UPPER(TRIM(transfer_reference)) AS normalized_reference,
           COUNT(*) AS source_count
    FROM wallet_withdrawal_requests
    WHERE status = 'COMPLETED'
      AND transfer_reference IS NOT NULL
      AND TRIM(transfer_reference) <> ''
    GROUP BY CASE
                 WHEN destination_type = 'BANK' AND NULLIF(TRIM(bank_name), '') IS NULL THEN '*'
                 WHEN destination_type = 'BANK' THEN UPPER(TRIM(bank_name))
                 ELSE 'TELEBIRR'
             END,
             UPPER(TRIM(transfer_reference))
) legacy;

-- Keep an immutable link from every historical completed request to the
-- provider/reference reservation, including the V54 rows whose normalized
-- column is NULL because they collided.
INSERT INTO withdrawal_transfer_reference_reservation_sources
    (reservation_id, withdrawal_request_id, source_type, created_at)
SELECT reservation.id, request.id, 'LEGACY', CURRENT_TIMESTAMP(6)
FROM wallet_withdrawal_requests request
JOIN withdrawal_transfer_reference_reservations reservation
  ON reservation.provider_key = CASE
         WHEN request.destination_type = 'BANK' AND NULLIF(TRIM(request.bank_name), '') IS NULL THEN '*'
         WHEN request.destination_type = 'BANK' THEN UPPER(TRIM(request.bank_name))
         ELSE 'TELEBIRR'
     END
 AND reservation.normalized_reference = UPPER(TRIM(request.transfer_reference))
WHERE request.status = 'COMPLETED'
  AND request.transfer_reference IS NOT NULL
  AND TRIM(request.transfer_reference) <> '';

-- A completed legacy row without a transfer reference cannot be uniquely
-- reserved by reference. Preserve it in an explicit reconciliation queue; do
-- not rewrite or remove the financial record.
CREATE TABLE withdrawal_transfer_reference_migration_issues (
    withdrawal_request_id BIGINT NOT NULL PRIMARY KEY,
    issue_code VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_withdrawal_reference_issue_request
        FOREIGN KEY (withdrawal_request_id) REFERENCES wallet_withdrawal_requests (id)
) ENGINE=InnoDB;

INSERT INTO withdrawal_transfer_reference_migration_issues
    (withdrawal_request_id, issue_code, created_at)
SELECT id, 'COMPLETED_WITHOUT_REFERENCE', CURRENT_TIMESTAMP(6)
FROM wallet_withdrawal_requests
WHERE status = 'COMPLETED'
  AND (transfer_reference IS NULL OR TRIM(transfer_reference) = '');
