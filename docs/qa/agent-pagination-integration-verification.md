# Agent roster pagination integration verification

This focused verification exercises `GET /api/v1/admin/agents` against a newly
provisioned, disposable MySQL schema. It uses synthetic Agent rows created in
the test transaction and rolls them back. It does not inherit the broad Agent
service integration test cleanup, run Flyway `clean()`, or enable financial
simulation.

## DBA provisioning

The DBA must first confirm that both exact names below are unused, then run
[`agent-pagination-test-dba-setup.sql`](scripts/agent-pagination-test-dba-setup.sql)
with a MySQL 8 account allowed to create local schemas, local accounts, and a
schema-scoped grant:

- Schema: `bingo_qa_agent_pagination_test`
- Account: `bingo_qa_agent_pagination_runner` at `localhost`
- Grant: `ALL PRIVILEGES` on that schema only

The SQL asks MySQL to generate a random password. The DBA must deliver that
value through the approved secret channel. Do not put it in SQL, source,
command arguments, shell history, or evidence. The script intentionally fails
if either name already exists; it does not alter or clean an existing schema.

## Local test identity and guard

After DBA provisioning, place the connection values in the ignored
`bingo-backend/.env.maven-agent-pagination` file (create it locally):

```text
BINGO_MAVEN_AGENT_PAGINATION_TESTS_ENABLED=true
BINGO_MAVEN_AGENT_PAGINATION_MYSQL_URL=jdbc:mysql://127.0.0.1:3306/bingo_qa_agent_pagination_test?createDatabaseIfNotExist=false&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
BINGO_MAVEN_AGENT_PAGINATION_MYSQL_USERNAME=bingo_qa_agent_pagination_runner
BINGO_MAVEN_AGENT_PAGINATION_MYSQL_PASSWORD=<store DBA-issued secret locally>
BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED=false
```

The focused test base independently requires the explicit opt-in and exact
loopback host, port, schema, account, and no-create URL setting. It confirms
the actual catalog and `CURRENT_USER()`, rejects grants outside the disposable
schema, verifies access is denied to `bingo`, `bingo_qa`, `bingo_qa_test`, and
`bingo_qa_maven_test`, and accepts only an empty schema or one already
migrated successfully through V28. It refuses to run if the
synthetic-financial opt-in is true.
JUnit skips the class unless `BINGO_MAVEN_AGENT_PAGINATION_TESTS_ENABLED=true`,
so the general Maven suite does not initialize or connect to this dedicated
database by default.
The existing `MySqlIntegrationTest` guard remains unchanged and continues to
bind the broad Maven suite to `bingo_qa_maven_test` and
`bingo_qa_maven_runner`.

Load the ignored file into the current PowerShell process without displaying
the values, then run only the focused class:

```powershell
foreach ($line in Get-Content .\bingo-backend\.env.maven-agent-pagination) {
    if ($line -match '^\s*(BINGO_MAVEN_AGENT_PAGINATION_TESTS_ENABLED|BINGO_MAVEN_AGENT_PAGINATION_MYSQL_URL|BINGO_MAVEN_AGENT_PAGINATION_MYSQL_USERNAME|BINGO_MAVEN_AGENT_PAGINATION_MYSQL_PASSWORD|BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED)=(.*)\s*$') {
        Set-Item -Path "Env:\$($matches[1])" -Value $matches[2].Trim().Trim('"').Trim("'")
    }
}
Push-Location .\bingo-backend
mvn --settings "$env:USERPROFILE\.m2\settings.xml" -Dtest=AgentRosterPaginationIntegrationTest test
Pop-Location
```

The backend's test profile runs Flyway automatically on startup. On the first
run, the guard requires an empty schema and database creation is disabled, so
Flyway migrates that new schema from version 0 through checked-in V28;
Hibernate then validates the resulting schema. Later focused runs require the
same schema to have successful V28 history. Do not run the full Maven suite
against this target: existing tests include broad cleanup and a Flyway recovery
test that calls `clean()`.

## Coverage prepared

The focused HTTP test covers database-backed case-insensitive query filtering,
status filtering, page boundaries, total elements/pages, and descending
`created_at` plus `id` ordering for tied timestamps. It uses three matching
synthetic rows and checks that the page-level ticket, float-transaction,
top-up, and payout counts each execute as one grouped SQL query. It also checks
the returned synthetic float transaction count, anonymous denial, SUPPORT
denial, and invalid page size. The test class is transactional and explicitly
rolls back each method.

Verification remains pending until the DBA has provisioned the new empty
schema/account, the generated secret is configured locally, and the guarded
focused command passes. No provisioning or database migration is authorized
by this preparation.
