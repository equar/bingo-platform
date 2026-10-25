-- PREPARATION ONLY. This script has not been executed.
-- Requires explicit DBA approval and verified local account host values.
-- Do not substitute production credentials. Do not run against a production server.
-- No database deletion command is present. The Maven account is scoped to one disposable schema.
-- This file is read-only as checked in: every mutating statement below is commented out.
-- After DBA review, uncomment only approved statements, replace placeholders, then execute individually.

-- Read-only preflight. Review account names/hosts/grants before any statement below.
SELECT @@hostname AS mysql_hostname, @@port AS mysql_port, CURRENT_USER() AS administrator_account;
SELECT SCHEMA_NAME
FROM information_schema.schemata
WHERE SCHEMA_NAME IN ('bingo_qa', 'bingo_qa_test', 'bingo_qa_maven_test');
SELECT User, Host
FROM mysql.user
WHERE User IN ('bingo_test', 'bingo_qa_test_runner', 'bingo_qa_maven_runner')
ORDER BY User, Host;

-- Run SHOW GRANTS separately for each exact account/host returned by the preflight.
-- Do not continue if bingo_qa_maven_test already exists unexpectedly, if the local test
-- identity is used by another local system, or if the account has unknown shared grants.
-- Example review commands (replace <verified-host>):
-- SHOW GRANTS FOR 'bingo_test'@'<verified-host>';
-- SHOW GRANTS FOR 'bingo_qa_test_runner'@'<verified-host>';

-- ROTATE the credential currently configured by local bingo-backend/.env.test.
-- Replace <verified-host> only with a host row proven by the preflight.
-- Replace the placeholder with a new random value, then update ignored .env.test locally.
-- This local username's credential does not match the separate ephemeral CI literal.
-- Do not run if this local account is shared with another host/service.
-- ALTER USER 'bingo_test'@'<verified-host>' IDENTIFIED BY '<new-random-android-test-secret>';

-- OPTIONAL: narrow the verified Android account row to controlled QA only.
-- Review every existing grant first; this removes privileges assigned to this exact account.
-- Do not continue if it is used outside local Android/Admin QA.
-- REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'bingo_test'@'<verified-host>';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON bingo_qa_test.* TO 'bingo_test'@'<verified-host>';

-- Create a dedicated disposable Maven schema and local-only account.
-- Confirm the schema is absent before running. Repeat account statements only for verified
-- localhost/127.0.0.1 host rows required by this MySQL installation.
-- CREATE DATABASE bingo_qa_maven_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- CREATE USER 'bingo_qa_maven_runner'@'<verified-local-host>' IDENTIFIED BY '<new-random-maven-secret>';
-- GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
--     ON bingo_qa_maven_test.* TO 'bingo_qa_maven_runner'@'<verified-local-host>';

-- Read-only grant verification; run after provisioning and again after rotation.
SHOW GRANTS FOR 'bingo_qa_maven_runner'@'<verified-local-host>';
SHOW GRANTS FOR 'bingo_test'@'<verified-host>';

-- From a fresh Maven-account connection, verify identity/target and grants:
-- SELECT DATABASE() AS active_schema, CURRENT_USER() AS authenticated_account,
--        @@hostname AS mysql_hostname, @@port AS mysql_port;
-- Expected: bingo_qa_maven_test, bingo_qa_maven_runner@localhost or @127.0.0.1, port 3306.
-- Under that account, reads from bingo_qa and bingo_qa_test must fail with access denied.
-- From a fresh Android-account connection, reads from bingo_qa_maven_test must fail.
-- SHOW GRANTS must show no global grants beyond USAGE and no grants outside the assigned schema.
