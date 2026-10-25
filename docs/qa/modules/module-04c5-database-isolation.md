# Module 04C-5 — QA Database Isolation

Disposition: **BLOCKED — local test authentication failed and no MySQL administrator credential is available to provision the dedicated schema/users; no destructive test or database change was made**

Scope: Module 04C-5 only. No 04D/04E work, financial posting, schema mutation, or Android fixture mutation.

## Required three-database model

| Environment | Schema | Intended owner and access |
| --- | --- | --- |
| Persistent browser/mobile QA | `bingo_qa` | Persistent QA only; never reset. Not available to test users. |
| Android/Admin integration QA | `bingo_qa_test` | Controlled fixtures. Must not be a target of broad Maven suites or Flyway clean. Runtime account should have no schema/database create or drop privilege. |
| Disposable Maven integration tests | `bingo_qa_maven_test` | Dedicated schema for destructive integration tests and Flyway recovery. Only its Maven runner may perform destructive object operations; no account receives global or `bingo_qa`/`bingo_qa_test` grants. |

The new name follows the existing `bingo_qa_test` convention while explicitly distinguishing Maven's disposable database. It is a proposal only; the schema has not been created.

## Configuration and authentication diagnosis

- Before C5A, `application-test.yml` and `MySqlIntegrationTest` used `BINGO_TEST_MYSQL_*`. Spring does not load `.env.test` itself. C5A separated the profiles: `application-test.yml` and the Maven integration base use `BINGO_MAVEN_MYSQL_*`; the new `application-android-test.yml` uses `BINGO_TEST_MYSQL_*`.
- The focused 04C-4 invocation manually loaded the ignored `bingo-backend/.env.test` values. The configured target was local MySQL on `127.0.0.1:3306`, schema `bingo_qa_test`, user `bingo_test`. MySQL rejected authentication as `bingo_test` from host `localhost` before a JDBC connection was established. The failure does not establish whether the cause is password drift, missing account, or MySQL host matching.
- The checked-in `.env.test.example` documents the prior Android schema/user (`bingo_qa_test_runner`). Earlier 04C-3A evidence reports that account had schema-scoped grants, but the current ignored `.env.test` and live authentication result do not match that historical condition. No current privileges could be inspected without a valid login.
- The Maven test account, Android runtime account, and local DB administrator credentials are not available in the process environment. The `.env.qa` persistent QA application account is not a substitute and must not be reused for tests. No secret values are recorded here.
- Maven tests use the `test` Spring profile and the shared integration base now consumes only `BINGO_MAVEN_MYSQL_*`. Both CI workflows were updated to provision ephemeral `bingo_qa_maven_test` services. Android QA uses the new `android-test` profile and `BINGO_TEST_MYSQL_*`. Local provisioning/authentication for the separate Maven schema is still pending, so neither profile separation nor CI workflow behavior has been executed here.

The cause of authentication failure cannot be proven without current MySQL account/grant administration access. Do not retry the supplied password, guess credentials, or use persistent QA credentials.

## Safety gate and required setup

No destructive suite was started. The C5A preparation changes now route guarded Maven tests to `bingo_qa_maven_test`; test credentials are read only from `BINGO_MAVEN_MYSQL_*`, with no Android variable fallback. The shared base guard checks the exact URL/catalog, local host and port, expected runner name and host, no database auto-creation, only allowlisted grants, and denied access to `bingo`, `bingo_qa`, and `bingo_qa_test` before any Spring test context starts. The Flyway recovery test extends that base, so its pre-clean class initialization guard runs before it constructs Flyway or calls `clean()`. A `%` MySQL user host is permitted only when CI explicitly sets `BINGO_MAVEN_CI_DISPOSABLE=true`; URL, catalog, username, grant, and denied-schema checks still apply. The Android/Admin runtime profile is separated as `android-test`, using `BINGO_TEST_MYSQL_*`. These guards are source-reviewed only; tests were not run.

The exact administrator setup required before implementation/verification:

1. A local MySQL administrator creates `bingo_qa_maven_test` on `127.0.0.1:3306` with the repository's MySQL 8.0 collation. Keep it separate from `bingo_qa` and `bingo_qa_test`.
2. Provision a new random-secret Maven account. Since this MySQL instance has reported the client as `localhost` and prior evidence used `127.0.0.1`, inspect host resolution and create only the account host entries needed. A template for the disposable host accounts is:

   ```sql
   CREATE DATABASE bingo_qa_maven_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   CREATE USER 'bingo_qa_maven_runner'@'localhost' IDENTIFIED BY '<new-random-local-secret>';
   GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
       ON bingo_qa_maven_test.* TO 'bingo_qa_maven_runner'@'localhost';
   CREATE USER 'bingo_qa_maven_runner'@'127.0.0.1' IDENTIFIED BY '<same-new-random-local-secret>';
   GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
       ON bingo_qa_maven_test.* TO 'bingo_qa_maven_runner'@'127.0.0.1';
   ```

   Do not grant `GRANT OPTION`, global privileges, or `CREATE/DROP DATABASE`. Schema-scoped privileges must be sufficient for Flyway migration/clean while containing destructive authority to this disposable schema. If only one host form is selected, omit the other account. Verify `CURRENT_USER()` and `SHOW GRANTS` after provisioning.
3. Inspect the Android account's actual host rows with `SHOW GRANTS`. Remove broad DDL privileges from the existing `bingo_qa_test_runner` account rows and grant only the DML/read operations required by the test runtime. For each host row that exists, the intended grant shape is:

   ```sql
   REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'bingo_qa_test_runner'@'<verified-host>';
   GRANT SELECT, INSERT, UPDATE, DELETE ON bingo_qa_test.* TO 'bingo_qa_test_runner'@'<verified-host>';
   ```

   Do not run the template until `<verified-host>` is replaced with an account host shown by MySQL. If migrations are needed, run them using a separately controlled migration account, not the Android runtime account.
4. Keep credentials in ignored local env files or the CI secret store. Configure separate `BINGO_MAVEN_MYSQL_URL/USERNAME/PASSWORD` and Android QA variables. Do not put local secrets in workflow YAML. Point the CI disposable MySQL service and the Maven variables to `bingo_qa_maven_test`.
5. Review the prepared code guards before running anything: explicit destructive schema allowlist; host/port/catalog assertion before Flyway clean and fixture setup; separate datasource wiring; and configuration tests proving refusal for `bingo_qa` and `bingo_qa_test`. The Android path must reject the Maven schema. The source guard is prepared, but its tests have not run.
6. Verify `SELECT DATABASE(), CURRENT_USER(), @@hostname, @@port` and `SHOW GRANTS` for each account, then confirm the destructive guard's active schema and rejection cases. Take read-only snapshots of `bingo_qa` and `bingo_qa_test` before/after. Only then run isolated regression suites on the disposable schema.

These steps require a database administrator. Since those credentials are unavailable here, database setup and test execution stop at this gate. The source/config guard and workflow routing are prepared but remain unexecuted and untested; no account, schema, grant, or runtime authentication is claimed as repaired or verified.

See [04C-4 test attempt](../evidence/module-04c4-test-attempt.txt), [C5 read-only investigation evidence](../evidence/module-04c5-isolation-gate.txt), and [04C-5 draw retry tests](module-04c5-draw-retry-tests.md).
