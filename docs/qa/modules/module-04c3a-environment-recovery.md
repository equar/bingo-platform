# Module 04C-3A — Restore Isolated QA Backend Environment

Disposition: **COMPLETE — isolated backend restored and verified; no gameplay tests run**

Scope: environment recovery only. Module 04C-3 remains PARTIAL. No 04D/04E work or financial/gameplay operation was started.

## Configuration and database isolation

The existing ignored `bingo-backend/.env.test` was found; no credential file was created or changed. `.gitignore` rule `**/.env.test` ignores it. The file supplies `BINGO_TEST_MYSQL_URL`, `BINGO_TEST_MYSQL_USERNAME`, and `BINGO_TEST_MYSQL_PASSWORD`; none of their secret values are recorded here. The JDBC target is `127.0.0.1:3306/bingo_qa_test`.

Spring does not automatically load `.env.test`. The variables were loaded into the launching PowerShell process using the existing QA environment procedure. The test profile resource was explicitly added from `bingo-backend/src/test/resources/`; the running process received no secrets in its command line. No `.env.qa` credentials or persistent schema were used.

Before startup, a read-only MySQL connection using the test account returned catalog `bingo_qa_test`, server `127.0.0.1:3306`, MySQL `8.0.36`, and account `bingo_qa_test_runner@127.0.0.1`. Its grants were `USAGE ON *.*` and `ALL PRIVILEGES ON bingo_qa_test.*`; no persistent schema privileges were present. The isolated application connected only to that local catalog. Persistent `bingo_qa` was not queried or connected by the backend.

Flyway is enabled with `clean-disabled=true` and `validate-on-migrate=true`. Before startup, the database was at V22 and the source’s latest migration is V22. Startup validated all 22 migrations and reported `bingo_qa_test` at V22, up to date, with no migration applied.

## Build and runtime identity

- Git HEAD: `2f8a0f9e8c26db1cd5bd5a8ee55c88a187d8ae26`.
- Working tree: modified; the full status at build time is preserved in [working-tree evidence](../evidence/module-04c3a-working-tree-status.txt). No changes were committed.
- Build: Maven 3.9.6, Java 25.0.2, Spring Boot 4.1.1; `package -DskipTests` completed successfully. No tests were run.
- Build/artifact time: `2026-10-25T11:38:37Z`.
- Artifact: `bingo-backend/target/bingo-backend-0.0.1-SNAPSHOT.jar`, 69,742,846 bytes.
- SHA-256: `8FA15AC5267FC5038A03FFF699798F05663EF14B9E2EA07B14ABFEE2885DAA9B`.
- Runtime PID: `32844`; executable: `C:\Program Files\Java\jdk-25.0.2\bin\java.exe`; launched with the artifact above.
- Listener: PID 32844 on `127.0.0.1:8082`.
- Active profile: `test`; active datasource identity from the test-only startup diagnostic: `bingo_qa_test`, JDBC host `127.0.0.1`, port `3306`, Flyway V22. The diagnostic includes the build commit and JAR checksum and fails startup for a nonlocal/wrong schema or enabled safety flags. It is not an HTTP endpoint. Future QA runtime launches must explicitly enable it with `APP_QA_TEST_RUNTIME_IDENTITY_REPORTING_ENABLED=true` and provide its build identity values; standard Maven tests leave the opt-in disabled.

## Safety and connectivity checks

The test profile defaults financial simulation/posting flags to `true`. For this environment runtime, signup-reward test posting, deposit simulation, adjustment simulation, and agent-float simulation were explicitly overridden to `false`. The startup diagnostic verifies all four are disabled. Automatic draw scheduling and startup recovery were also disabled to prevent background activity in retained sessions. Production configuration was not changed.

- Host `GET /actuator/health`: HTTP 200, `UP`.
- Emulator `emulator-5554` read-only `GET http://10.0.2.2:8082/actuator/health`: HTTP 200, `UP`.
- `GET /v3/api-docs`: succeeded. `POST /api/v1/admin/sessions/{id}/draws` has no request body in the current OpenAPI contract. The route was not called.
- No app was launched. No session, draw, ticket, claim, wallet, or financial operation was created. Flyway made no migration; the draw scheduler was disabled. Sessions #30 and #31 were not accessed or modified.

Detailed sanitized checks are in [environment evidence](../evidence/module-04c3a-environment-recovery.txt). No passwords, tokens, or secret values are recorded.

## Handoff

The local isolated backend is available on `127.0.0.1:8082` for the separately scoped 04C-3 Android recovery work. This report does not close 04C-3: A14, A15, A16, and A19 have not been run. Module 04C and Module 04B remain PARTIAL; Module 02 remains INCOMPLETE; Module 03 remains COMPLETE FOR DEFINED QA SCOPE.
