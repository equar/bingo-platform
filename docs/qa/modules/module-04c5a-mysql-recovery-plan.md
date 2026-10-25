# Module 04C-5A — MySQL Credential and Isolation Recovery Plan

Disposition: **PREPARATION PARTIAL — datasource separation, fail-fast guard, and DBA script are prepared; user approval has been received, but database authentication/rotation remain blocked by unavailable DBA access. A prior tool output exposed the ignored local credential, so the no-disclosure completion condition was not met.**

Scope: Non-destructive preparation only. No MySQL administration statement, database connection, test suite, Flyway clean, gameplay operation, or financial posting was run in 04C-5A.

## Proven authentication and environment findings

- The local MySQL 8.0 service (`MySQL80`) is running with automatic startup. `mysql.exe`, `mysqladmin.exe`, and `mysqld.exe` are installed.
- The prior 04C-4 failed connection used the `test` profile and the local URL `127.0.0.1:3306/bingo_qa_test`, with configured username `bingo_test`. MySQL's error reported the attempted principal as `bingo_test` from `localhost` and returned access denied before JDBC established a connection. This proves authentication failure. It does **not** distinguish an incorrect password from a missing/mismatched MySQL `user@host` row.
- Because authentication failed, current schema grants/privileges cannot be read; privilege failure is not established. No repeated password attempts were made. No current MySQL account-host inspection was possible.
- `.env.test` is ignored local configuration and is not loaded automatically by Spring. The 04C-4 command explicitly loaded its values into that one child process. Maven's guarded test base reads process environment variables and overrides Spring datasource properties. Spring process environment values take precedence over YAML placeholders.
- Before C5A, Maven tests and the controlled Android backend both used `BINGO_TEST_MYSQL_*`; the Flyway recovery class directly called `clean()`. The full Maven lifecycle can therefore destroy `bingo_qa_test` if run with that local datasource.
- Current process checks found no `BINGO_*`, `MYSQL_*`, or Spring datasource variables. No root/admin credential is available. The `.env.qa` account is for persistent QA and is not used for test setup.

## Credential exposure scope and rotation

The local `.env.test` password was accidentally displayed in a prior Codex tool output. Treat it as compromised. Its value is omitted from this report, SQL, and evidence.

Non-displaying search results:

- No exact match was found in tracked repository text files, saved QA evidence, backend build/log files, or the targeted local QA/work directories.
- No exact match was found in the PowerShell PSReadLine history file or the inspected VS Code workspace/Copilot Chat storage.
- The current Codex conversation does contain the earlier tool output. The agent cannot edit or remove the hosted conversation record. No other Copilot/VS Code storage location was confirmed.
- The local credential was compared in memory with the separate static CI test value; they do not match. The same short account name is used by ephemeral MySQL services in the PR and deployment workflows, but those services have separate databases and a different configured credential. Rotation of the local account does not require changing that CI value.
- The ignored local `.env.test` is the only local configuration identified as carrying the exposed exact value. `.env.test.example` contains a placeholder, not that credential.

Do not reuse the exposed value. After an administrator approves and verifies the exact local MySQL account host row, rotate that row using a newly generated local-only secret. Update only the ignored local `.env.test` entry and any local process launch configuration that explicitly sources it. Keep the rotated value out of command lines, terminal output, logs, evidence, Git, and CI. Invalidate/replace any process environment loaded from the old file. If the administrator discovers an additional account or service using the same value, rotate that configuration separately; current file/log scans did not identify one.

## Prepared database separation and source changes

| Use | Schema | Profile/variables | Access design |
| --- | --- | --- | --- |
| Persistent QA | `bingo_qa` | QA runtime configuration | No Maven test grants; never reset. |
| Android/Admin QA | `bingo_qa_test` | `android-test`, `BINGO_TEST_MYSQL_*` | The rotated Android account is restricted to this schema and DML/read access; no access to Maven schema. |
| Disposable Maven tests | `bingo_qa_maven_test` | `test`, `BINGO_MAVEN_MYSQL_*` | `bingo_qa_maven_runner`, scoped to this disposable schema with DDL/DML needed for migrations and destructive fixtures; no access to either QA schema. |

Prepared in source/configuration:

- `MySqlIntegrationTest` now requires local host/port 3306, exact catalog `bingo_qa_maven_test`, exact Maven runner name, localhost/loopback `CURRENT_USER()`, and database auto-creation disabled in the JDBC URL.
- Before exposing Spring datasource properties, the guard checks for denied access to `bingo`, `bingo_qa`, and `bingo_qa_test`, and rejects any `SHOW GRANTS` entry outside the disposable schema (except global `USAGE`). All current `@SpringBootTest` integration classes inherit this base; the Flyway clean test therefore hits its static guard before calling `clean()`.
- `application-test.yml` now uses only `BINGO_MAVEN_MYSQL_*`. A separate `application-android-test.yml` uses `BINGO_TEST_MYSQL_*`, so the local Android backend must run with profile `android-test`.
- `.env.maven-test.example` documents the dedicated Maven variables, and `.env.maven-test` is ignored. The README and both CI workflow service definitions are routed to ephemeral `bingo_qa_maven_test` with the Maven account name.

These are source-reviewed changes only. They were not compiled or executed against MySQL. CI has not been run. The local Maven schema/account is not provisioned, so no functional isolation claim is made yet.

## Administrator review script and setup

Review [the DBA SQL script](../scripts/module-04c5a-dba-setup.sql) before execution. It has no `DROP DATABASE`, no global grant, no production credential, and uses placeholders for every secret and uncertain account host. It includes:

1. Read-only checks for MySQL server, existing schema names, and exact user/host rows.
2. A rotation statement for the confirmed local Android test account row.
3. Optional privilege narrowing of that exact Android account to `bingo_qa_test` DML only.
4. Creation of `bingo_qa_maven_test`, a new local Maven runner, and schema-scoped DDL/DML privileges.
5. `SHOW GRANTS` and authenticated-account verification instructions.

The DBA must first verify account host rows and grants, confirm the local account is not shared outside Android QA, and approve the SQL. Run it manually only after filling placeholders with unique local secrets. The Maven identity must have no global privileges and no grant outside `bingo_qa_maven_test`; the Android identity must have no grant to `bingo_qa_maven_test`. `bingo_qa` remains unavailable to either test identity.

## Remaining blockers and completion

- DBA account access and credential rotation are pending; user approval for the listed local changes has been received.
- The dedicated local schema/account and restricted Android account are not provisioned.
- CI service definitions were edited but not run. The local Spring profile split and guard were not tested.
- The previous output disclosure means this report cannot claim that no credential was disclosed. Treat the credential as exposed and rotate it before reuse.
- Module 04C remains PARTIAL. Module 04C-5 remains BLOCKED; no R01–R10 test ran. Request idempotency remains a separate Product/API decision.

See [C5 diagnostics evidence](../evidence/module-04c5a-diagnostics.txt), [C5 isolation report](module-04c5-database-isolation.md), and [C5 retry test report](module-04c5-draw-retry-tests.md).
