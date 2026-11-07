-- DBA-only provisioning for focused Agent roster pagination integration tests.
-- Run only after confirming these exact schema and account names do not exist.
-- MySQL generates the password and returns it to the DBA; transfer it through
-- the approved secret channel. Do not save it in this repository or command history.

CREATE DATABASE bingo_qa_agent_pagination_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'bingo_qa_agent_pagination_runner'@'localhost'
    IDENTIFIED BY RANDOM PASSWORD;

GRANT ALL PRIVILEGES ON bingo_qa_agent_pagination_test.*
    TO 'bingo_qa_agent_pagination_runner'@'localhost';

-- No FLUSH PRIVILEGES is required for CREATE USER / GRANT.
-- The account receives no global privilege and no grant on another schema.
