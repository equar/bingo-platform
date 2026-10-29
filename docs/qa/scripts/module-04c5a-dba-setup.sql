-- Reviewed local-only provisioning for Maven integration tests.
-- Target: MySQL 8.0.36 at 127.0.0.1:3306.
-- This script preserves all existing schemas and accounts. It contains no DROP DATABASE,
-- ALTER USER, REVOKE, or statements affecting bingo, bingo_qa, or bingo_qa_test.
-- Run the read-only preflight first. Stop if bingo_qa_maven_test or the runner account
-- already exists; investigate it instead of altering or reusing it.

-- Read-only preflight: confirm this is the intended local MySQL server and inspect schema state.
SELECT @@hostname AS mysql_hostname, @@port AS mysql_port, CURRENT_USER() AS administrator_account;
SELECT SCHEMA_NAME
FROM information_schema.schemata
WHERE SCHEMA_NAME IN ('bingo', 'bingo_qa', 'bingo_qa_test', 'bingo_qa_maven_test')
ORDER BY SCHEMA_NAME;
SELECT User, Host
FROM mysql.user
WHERE User = 'bingo_qa_maven_runner'
ORDER BY Host;
SHOW GRANTS FOR CURRENT_USER();

-- Continue only when all of these are true:
-- 1. @@port is 3306 and the server is the intended local instance.
-- 2. bingo_qa_maven_test is absent; all other schemas remain untouched.
-- 3. bingo_qa_maven_runner has no existing account rows.
-- 4. The administrator has CREATE DATABASE, CREATE USER, and GRANT authority.
-- Execute each statement separately. Do not execute anything against existing schemas.

-- CREATE DATABASE bingo_qa_maven_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- CREATE USER 'bingo_qa_maven_runner'@'127.0.0.1' IDENTIFIED BY RANDOM PASSWORD;
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
--     ON bingo_qa_maven_test.* TO 'bingo_qa_maven_runner'@'127.0.0.1';

-- CREATE USER returns its generated password once in the Workbench result grid.
-- Copy it directly into the ignored bingo-backend/.env.maven-test file; never paste it
-- into chat, a tracked file, a command line, or a log. If Workbench does not return the
-- password, stop and reset this new account using an approved secret-handling method.

-- Read-only verification after provisioning:
SHOW GRANTS FOR 'bingo_qa_maven_runner'@'127.0.0.1';

-- On a fresh connection authenticated as bingo_qa_maven_runner, with the Maven schema selected:
-- SELECT DATABASE() AS active_schema, CURRENT_USER() AS authenticated_account,
--        @@hostname AS mysql_hostname, @@port AS mysql_port;
-- SHOW GRANTS FOR CURRENT_USER();
-- Expected identity: bingo_qa_maven_test, bingo_qa_maven_runner@127.0.0.1, port 3306.
-- Expected grants: global USAGE only, plus the listed privileges on bingo_qa_maven_test.*.
-- The following read-only checks MUST each fail with access denied (1044 or 1142):
-- SELECT 1 FROM bingo.flyway_schema_history LIMIT 0;
-- SELECT 1 FROM bingo_qa.flyway_schema_history LIMIT 0;
-- SELECT 1 FROM bingo_qa_test.flyway_schema_history LIMIT 0;
-- If a schema is absent, or any statement succeeds, stop; do not run Maven tests.
