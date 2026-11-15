CREATE TABLE wallet_withdrawal_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    operation_id VARCHAR(36) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    destination_type VARCHAR(12) NOT NULL,
    account_name VARCHAR(120) NOT NULL,
    phone_number VARCHAR(30),
    bank_name VARCHAR(100),
    account_number VARCHAR(80),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP(6),
    transfer_reference VARCHAR(120),
    review_note VARCHAR(500),
    CONSTRAINT uk_wallet_withdrawal_operation UNIQUE (operation_id),
    CONSTRAINT fk_withdrawal_player FOREIGN KEY (player_id) REFERENCES users (id),
    CONSTRAINT fk_withdrawal_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id),
    CONSTRAINT ck_withdrawal_amount CHECK (amount > 0),
    CONSTRAINT ck_withdrawal_status CHECK (status IN ('PENDING','COMPLETED','REJECTED')),
    CONSTRAINT ck_withdrawal_destination_type CHECK (destination_type IN ('TELEBIRR','BANK')),
    CONSTRAINT ck_withdrawal_destination_details CHECK (
      (destination_type = 'TELEBIRR' AND phone_number IS NOT NULL) OR
      (destination_type = 'BANK' AND bank_name IS NOT NULL AND account_number IS NOT NULL)
    ),
    INDEX idx_withdrawals_status_created (status, created_at DESC),
    INDEX idx_withdrawals_player_created (player_id, created_at DESC)
);
