ALTER TABLE users
    ADD COLUMN account_activation_token_hash VARCHAR(64) NULL,
    ADD COLUMN account_activation_expires_at TIMESTAMP(6) NULL,
    ADD UNIQUE INDEX uk_users_account_activation_token_hash (account_activation_token_hash);
