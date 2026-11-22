ALTER TABLE wallet_withdrawal_requests
    ADD COLUMN normalized_transfer_reference VARCHAR(120) NULL,
    ADD COLUMN transfer_provider_key VARCHAR(100) NULL,
    ADD COLUMN payment_evidence VARCHAR(1000) NULL;

-- Keep every historical raw reference. When legacy completed requests collide after
-- trim/case normalization, reserve the reference on the oldest row and leave the
-- newer ambiguous rows unindexed so the migration completes without rewriting history.
UPDATE wallet_withdrawal_requests w
JOIN (
    SELECT CASE WHEN destination_type = 'BANK' THEN UPPER(TRIM(bank_name)) ELSE 'TELEBIRR' END AS provider_key,
           UPPER(TRIM(transfer_reference)) AS normalized_reference, MIN(id) AS keep_id
    FROM wallet_withdrawal_requests
    WHERE status = 'COMPLETED' AND transfer_reference IS NOT NULL AND TRIM(transfer_reference) <> ''
    GROUP BY CASE WHEN destination_type = 'BANK' THEN UPPER(TRIM(bank_name)) ELSE 'TELEBIRR' END,
             UPPER(TRIM(transfer_reference))
) refs ON refs.provider_key = CASE WHEN w.destination_type = 'BANK' THEN UPPER(TRIM(w.bank_name)) ELSE 'TELEBIRR' END
   AND refs.normalized_reference = UPPER(TRIM(w.transfer_reference))
SET w.transfer_provider_key = CASE WHEN w.destination_type = 'BANK' THEN UPPER(TRIM(w.bank_name)) ELSE 'TELEBIRR' END,
    w.normalized_transfer_reference = CASE WHEN w.id = refs.keep_id THEN refs.normalized_reference ELSE NULL END
WHERE w.status = 'COMPLETED' AND w.transfer_reference IS NOT NULL AND TRIM(w.transfer_reference) <> '';

CREATE UNIQUE INDEX uk_withdrawal_provider_transfer_reference
    ON wallet_withdrawal_requests (transfer_provider_key, normalized_transfer_reference);
