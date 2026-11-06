-- Account-level UI language preference. NULL means no explicit preference and
-- resolves to English; existing rows are intentionally left untouched.
ALTER TABLE users
    ADD COLUMN preferred_language VARCHAR(8) NULL;
