ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN temporary_password_expires_at TIMESTAMP(6) NULL;
