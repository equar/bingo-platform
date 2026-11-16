ALTER TABLE users
    ADD COLUMN active_player_session_id VARCHAR(36) NULL,
    ADD COLUMN active_player_session_expires_at TIMESTAMP NULL;
