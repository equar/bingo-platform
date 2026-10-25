# Module 04C-5B — DBA Provisioning and Isolation Verification

Disposition: **BLOCKED — explicit approval received, but no authorized MySQL administrator login is available; no SQL was executed**

Scope: Module 04C-5B only. No gameplay tests, Maven tests, Flyway clean, production account changes, financial posting, or database writes.

## Approval and SQL review

The user explicitly approved all listed local MySQL changes in the approval reply for this module. The approved scope was:

- Create `bingo_qa_maven_test` and local account `bingo_qa_maven_runner` with schema-scoped DDL/DML privileges only on that schema.
- Rotate the local `bingo_test@<verified-host>` credential currently used by `.env.test` and replace it with a new local-only secret.
- Narrow that verified Android test account to DML access on `bingo_qa_test` after reviewing its existing grants.
- Verify schema-scoped grants and cross-schema denials.

The prepared [DBA SQL template](../scripts/module-04c5a-dba-setup.sql) was reviewed. It uses explicit schema-scoped Maven privileges, contains no global grant or database-drop command, contains no production account, uses placeholders for secrets/hosts, and preserves rows in both existing QA schemas. The optional `REVOKE` affects the exact local account's privileges only; it does not delete QA data. All mutating SQL statements remain commented in the checked-in script.

The `bingo_test` account name also appears in the isolated PR/deployment MySQL service configuration. The local exposed credential does not match the separate ephemeral CI credential, so local rotation will not change CI access. Before rotation, the DBA must still confirm the exact local `user@host` row and ensure it is not shared by another local service.

## Administrator access gate

Non-destructive checks found:

- Local MySQL service `MySQL80` is available and was previously observed running.
- No MySQL/database administrator variables are set in the current process.
- No user `.my.cnf` or `.mylogin.cnf` file was found. `mysql_config_editor` is installed, but no configured login path was available to use.
- The repository has a persistent-QA application account configuration and historical test account documentation, but neither is an authorized DBA credential. They were not tried.
- The prior application test login was rejected before connection. Current account rows, host restrictions, and grants remain unknown.

No alternate login was attempted, no password was guessed, and no database connection was made. The administrator should open an authenticated local MySQL administrative session using the approved secret store/Workbench credential, verify `@@hostname` and `@@port` identify the intended local server, inspect the precise user/host rows and `SHOW GRANTS`, then apply the approved statements from the SQL template individually. Do not paste the password into chat or add it to tracked files.

## Configuration review

Source inspection confirms the prepared configuration:

- Maven integration config uses `BINGO_MAVEN_MYSQL_*` and requires the exact disposable schema `bingo_qa_maven_test`, local loopback host/port, expected Maven account, local account host (CI wildcard only behind the explicit disposable-CI flag), disabled database auto-creation, and grants scoped only to the disposable schema.
- The integration guard rejects access to `bingo`, `bingo_qa`, and `bingo_qa_test` before Spring test context initialization. The Flyway recovery test extends the guarded base, so it must pass this gate before `clean()` can run.
- Android backend config is separated into profile `android-test` and uses `BINGO_TEST_MYSQL_*` for `bingo_qa_test`.
- The `android-test` and production profiles have signup reward, deposit, adjustment, and agent-float simulation/posting flags set to `false`. Maven test profile defaults remain test-only settings; no Maven tests were run. No production profile or financial controls were changed.
- PR and deployment workflow MySQL services target their own ephemeral `bingo_qa_maven_test` schema. Workflow changes have not been executed.

These findings are source/config inspection only. No connection-level grant denial or configuration guard test has been run.

## Provisioning and verification results

| Requirement | Result |
| --- | --- |
| User approval | PASS — explicit approval for all listed local changes received. |
| DBA authentication | BLOCKED — no authorized admin login available. |
| Maven schema/account provisioning | NOT RUN. |
| Local credential rotation | NOT RUN; old exposed credential must not be restored. |
| Android account grant narrowing | NOT RUN. |
| Maven account authentication/grants | NOT VERIFIED. |
| Maven denied access to `bingo_qa` and `bingo_qa_test` | NOT VERIFIED against MySQL. |
| Android denied access to Maven schema | NOT VERIFIED against MySQL. |
| Persistent and Android QA before/after comparison | NOT RUN; there was no database connection and no writes. |
| Configuration guard tests | NOT RUN. |
| Full Maven/gameplay tests | NOT RUN, as required for this module. |

Rollback plan for a later approved DBA session: capture current grants first; if provisioning fails, lock or revoke the new Maven account while preserving the newly created schema for inspection. Do not restore the exposed local password. Restore any Android account grants only from the captured pre-change grant output and only after the DBA reviews the impact. No rollback action was needed in this run.

Module 04C-5B is **BLOCKED**, not complete. The approved SQL must be applied by an authorized DBA before the access checks and non-destructive guard tests can be run. Module 04C remains PARTIAL; request-idempotency remains unresolved.

See [access-gate evidence](../evidence/module-04c5b-admin-access-gate.txt), [04C-5A recovery plan](module-04c5a-mysql-recovery-plan.md), and [approved SQL template](../scripts/module-04c5a-dba-setup.sql).
