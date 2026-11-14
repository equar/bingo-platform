# Local Bingo QA schema upgrade and recovery runbook (V22 → V40)

**Review status (2026-11-13): not authorized or executed.** This replaces the former V23-only procedure. The original guide was insufficient for this checkout; all V23-only commands are retired. This document is the single maintained procedure for the local functional QA schema. No backup, restore, schema creation, grant change, or migration has been run as part of this review.

## Verified environment

- Read-only connection to `127.0.0.1:3306/bingo_qa_test` returned database `bingo_qa_test`, account `bingo_qa_test_runner@127.0.0.1`, host `EQUAR-DELL-XPS15`, port `3306`; latest successful Flyway row is V22 (`V22__prevent_duplicate_room_sessions.sql`). MySQL client/server are 8.0.36.
- The schema contains 40 InnoDB base tables, no routines/events/triggers, and about 12 users, 17 sessions, 4 rooms, 0 tickets, 0 entitlements, and one legacy finance-role assignment. These are read-only counts; `information_schema.tables.table_rows` is approximate. Largest reported table was `audit_log_entries` (504 approximate rows, 393216 data bytes). This is small local QA state, not a runtime estimate for production.
- Read-only migration preflight found no duplicate user phone values, no duplicate `(role, phone)` candidates, no duplicate `(ticket_id, prize_type)` entitlements, no duplicate room names, and no duplicate signup-grant players. `user_roles.normalized_phone` does not exist yet at V22, so the role/phone check joined through `users`.
- The configured account has only `USAGE` globally and `ALL PRIVILEGES` on `bingo_qa_test.*`. It cannot create/use a new database. It lacks global `PROCESS`; its process-list view cannot establish a complete writer freeze.
- `scripts/qa/Start-AndroidTestBackend.ps1` hard-pins `127.0.0.1:3306/bingo_qa_test`, requires Windows identity `EQUAR-DELL-XPS1\16414` and profile `C:\Users\16414`, rejects the Codex sandbox identity, refuses a port-8080 listener, requires current Flyway V40 and checks scheduler/financial safeguards. It does not auto-migrate a V22 database.

## Migration review V23–V40

| Version | Change and data effect | Preconditions and risk |
|---|---|---|
| V23 | Adds room privacy and stored generated public name; replaces room-name uniqueness with public-name uniqueness; adds visibility index and four private-game/challenge/leaderboard tables. No existing-row DML. | Existing duplicate public room names would block the unique key. DDL commits independently; partial application is possible. Current duplicate-name count is 0. |
| V24 | Adds nullable `users.preferred_language`; no backfill. | Low data risk; `ALTER users` metadata lock. |
| V25 | Adds verification-review fields/FK/index and review table. Existing users default to `NOT_REVIEWED`. | FK requires valid reviewer IDs on future writes; table/index DDL can wait on metadata locks. |
| V26 | Makes `users.normalized_phone` nullable. | Alters a widely used table; online behavior/lock duration depends on MySQL settings and table size. |
| V27 | Drops global phone unique key; adds `user_roles.normalized_phone`, backfills it from users, adds `(role, normalized_phone)` unique key, and creates three phone-sync triggers. | Data transform; duplicate role/phone values block unique key. Requires `TRIGGER` plus `ALTER`, `INDEX`, and DML privileges. Trigger creation and ALTER take metadata locks. Existing globally unique users can share phone across distinct roles after this change. Current joined preflight found no conflicting pairs. |
| V28–V29 | Adds nullable audit-actor display-name fields to rooms and users. | No backfill; `ALTER` metadata locks. |
| V30 | Copies `FINANCE_VERIFIER` assignments to `SUPPORT` and `FINANCE_APPROVER` to `ADMIN` using `INSERT IGNORE`, then deletes old role rows. | Deliberate authorization migration; review affected identities and resulting access before migration. One legacy finance assignment exists in QA. It is not a financial balance/journal migration, but changes permissions. Preserve before/after role mapping. |
| V31 | Conditionally adds activation-email status if absent; sets non-`PENDING` users to `NOT_SENT`. | Data update across users; inspect old values and preserve a before/after summary. Conditional SQL must be tested on the V22 schema. |
| V32–V34 | Adds activation sent/status timestamps and password-reset email status/timestamp; default `NOT_REQUESTED`. | No explicit backfill beyond defaults; `ALTER users` locks. |
| V35 | Generalizes signup rewards: adds policy/grant fields, changes grant FK/indexes, adds campaign/player uniqueness and generated signup-player uniqueness. | Constraint migration can fail on duplicate `(policy_id, player_id)` or duplicate signup grants per player; existing values receive `SIGNUP`. Review `awarded_by_user_id` FK and nullable generated-column uniqueness semantics. QA preflight found no duplicate players in grants. |
| V36 | Adds session rules/funding/claim-window fields defaulted to `LEGACY`/NULL and unique `(ticket_id, prize_type)` on entitlements. | Economic uniqueness constraint must not hide or delete historical duplicate awards. Current duplicate count is 0. Review all session defaults and entitlements. |
| V37 | Conditionally removes `ck_deposit_journal_separation`. | Relaxes a financial-journal constraint; no row rewrite. Confirm the application invariant and require Finance/DBA review. |
| V38 | Creates app-settings, announcements, and announcement-read tables, inserts settings singleton id 1. | New persistent content tables; FK/index DDL. Seed row is intentional. |
| V39 | Drops `bingo_sessions.rules_version`, introduced by V36. | Destructive column removal; after V39, rollback requires restoring the V22 backup or an explicitly engineered forward repair. Do not downgrade application/schema independently. |
| V40 | Adds campaign credit thresholds; adds referral codes and referral attribution to users, backfilling existing users to `BINGO<user id>`; adds grant event keys, backfilling legacy grants to `PLAYER-<player id>`, and replaces campaign/player plus global signup-player uniqueness with campaign/event uniqueness. | Data rewrite on `users` and `signup_reward_grants`, plus unique-index and FK DDL. V35's per-campaign/player key guarantees each legacy `PLAYER-<id>` backfill is unique within its policy. Check for duplicate/referral-code collisions, null grant beneficiaries, and unexpected existing event keys; preserve row counts and compare wallet and grant amounts. V40 app code is required because older code does not populate the new non-null referral-code field or implement these campaign triggers. The known local QA schemas are V22 and V26; production/deployment migration history was not inspected. Before rollout, confirm V40 was not applied elsewhere; if it was, do not alter its deployed checksum—prepare a forward V41 correction instead. |

All versions contain MySQL DDL, so Flyway cannot roll back the entire chain atomically. V27 and V30 perform data-changing SQL; V31 updates existing users; V37 removes a financial check; V39 drops a column; V40 backfills referral/event keys and replaces a grant uniqueness key. Compatibility is forward-only for the current source: run the complete migration before starting the current V40 application. Do not serve old binaries against a partially upgraded schema. V23, V26–V29, V31–V36, V38–V40 alter tables/indexes; expect metadata-lock waits and, depending on MySQL's chosen algorithm and table size, table-copy/rebuild work. A writer freeze is required. Do not promise zero downtime or infer production runtimes from this small QA schema.

### Required privileges

For the V22→V40 clone migration, DBA-provision a user scoped only to the uniquely named clone with the privileges required by these exact statements: `SELECT`, `INSERT`, `UPDATE`, `DELETE` for migration DML/Flyway history; `CREATE`, `ALTER`, `INDEX`, and `REFERENCES` for tables, columns, indexes, keys, and FKs; and `TRIGGER` for V27. No migration creates routines/events. Grant `CREATE DATABASE` only to a one-time DBA provisioning identity, never to the runtime/migration account. A DBA identity with authorized process-list visibility must perform the read-only writer/process check (`PROCESS` is not granted to the QA runner). The source backup needs source `SELECT` and metadata access; DBA must confirm the installed `mysqldump` requirements. Do not broaden the existing runner grant.

## Approval gate and deployment effects

Exact repository instruction in [`qa-blockers-and-approvals.md`](qa-blockers-and-approvals.md), “Local functional E2E schema”: **“Owner/DBA must approve and review an isolated backup/migration rehearsal covering V22→V40, and verify the runbook before migration. Then launch only through the guarded script as the normal Windows user. Do not migrate, clean, or bypass the guard.”** User approval for the isolated rehearsal was recorded on 2026-11-13. DBA verification of this runbook, clone provisioning, and scoped account setup remain required before any database write.

**User approval for an isolated V22→V40 clone rehearsal was received on 2026-11-13. No disposable schema is provisioned yet.** The runner cannot create one and the local-root exception is limited to the guarded launcher on `bingo_qa_test`. DBA provisioning is still required for a one-time clone creator, clone-scoped migration account, protected encrypted evidence location, and writer/process preflight. A fresh synthetic V40 database may support feature acceptance only after separate approval and a separately reviewed guarded launcher/config; it does not verify V22→V40. Never repoint production/dev settings or modify the existing V22 schema.

The standard `& .\scripts\qa\Start-AndroidTestBackend.ps1` runs against `bingo_qa_test`, runs Flyway at startup, and then performs Hibernate `validate`. It requires the owner's interactive PowerShell identity `EQUAR-DELL-XPS1\16414` and `USERPROFILE=C:\Users\16414`; it refuses the sandbox identity and V22 schema. Do not use it to migrate the existing V22 database. After DBA provisions and migrates the approved clone, `scripts/qa/Start-AndroidTestBackendV40Clone.ps1` can serve it on loopback port 8080. That launcher requires a protected clone-only MySQL option file and evidence directory, verifies V40 and exact schema-scoped grants, uses Hibernate validation, and verifies all financial/scheduler safeguards. It never runs Flyway migrations. This launcher is for Swagger/runtime validation only; authenticated E2E acceptance still requires a separate clone-aware runner and authoritative-record reconciliation. Do not start the clone with an unguarded raw Java command.

After V22→V40 migration assertions and DBA review pass, run this from the owner's normal interactive PowerShell session to serve Swagger from the clone:

```powershell
& .\scripts\qa\Start-AndroidTestBackendV40Clone.ps1 `
  -Database $verifyDb `
  -ConnectionDefaultsFile $verifyDefaults `
  -EvidenceDirectory $runDir
```

The guarded command binds only to `127.0.0.1:8080`; the existing `bingo_qa_test` schema remains unchanged.

## Proposed V22→V40 clone rehearsal — user approved; DBA provisioning pending

1. Record the written user approval for a consistent source backup/restore rehearsal. Before any write, the DBA must approve and provision one specifically named clone, a one-time creator, a clone-scoped migration account, process visibility, and a protected encrypted evidence location. Confirm an operational write freeze and no task-owned QA backend/client writers. Do not stop unrelated processes.
2. Initialize the target and run the read-only identity/history preflight in one PowerShell session. The operator supplies the already approved restricted encrypted evidence path. This reads only ignored `.env.test` connection values and never prints the password:

   ```powershell
   $repoRoot = (Resolve-Path '.').Path
   $runToken = [guid]::NewGuid().ToString('N')
   $runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ') + '_' + $runToken.Substring(0,8)
   $approvedEncryptedRoot = Read-Host 'Approved restricted encrypted evidence directory'
   if (-not (Test-Path -LiteralPath $approvedEncryptedRoot -PathType Container)) { throw 'Evidence directory must already exist.' }
   $approvedSecretRoot = Read-Host 'Approved ACL-restricted directory for MySQL option files'
   if (-not (Test-Path -LiteralPath $approvedSecretRoot -PathType Container)) { throw 'Protected credential directory must already exist.' }
   $runDir = Join-Path $approvedEncryptedRoot "bingo_v22_v40_$runId"
   if (Test-Path -LiteralPath $runDir) { throw 'Refuse reused run directory.' }
   New-Item -ItemType Directory -Path $runDir | Out-Null
   $envFile = Join-Path $repoRoot 'bingo-backend\.env.test'
   $testSettings = @{}
   foreach ($line in Get-Content -LiteralPath $envFile) {
       if ($line -match '^\s*([A-Za-z0-9_]+)=(.*)\s*$') { $testSettings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'") }
   }
   $sourceUrl = $testSettings['BINGO_TEST_MYSQL_URL']
   $sourceUser = $testSettings['BINGO_TEST_MYSQL_USERNAME']
   $sourcePassword = $testSettings['BINGO_TEST_MYSQL_PASSWORD']
   if ($sourceUrl -notmatch '^jdbc:mysql://127\.0\.0\.1:3306/bingo_qa_test(?:\?|$)' -or
       [string]::IsNullOrWhiteSpace($sourceUser) -or [string]::IsNullOrWhiteSpace($sourcePassword)) {
       throw 'Ignored QA datasource configuration is missing or not the pinned local database.'
   }
   $mysqlExe = (Get-Command mysql.exe -ErrorAction Stop).Source
   $env:MYSQL_PWD = $sourcePassword
   try {
       $identity = & $mysqlExe --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$sourceUser" `
           --database=bingo_qa_test --batch --raw --skip-column-names `
           --execute 'SELECT CONCAT(DATABASE(), CHAR(9), CURRENT_USER(), CHAR(9), @@hostname, CHAR(9), @@port)'
       if ($LASTEXITCODE -ne 0 -or [string]($identity | Select-Object -First 1) -ne "bingo_qa_test`t${sourceUser}@127.0.0.1`tEQUAR-DELL-XPS15`t3306") {
           throw 'Source identity does not match the reviewed local QA target.'
       }
       $history = & $mysqlExe --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$sourceUser" `
           --database=bingo_qa_test --batch --raw --skip-column-names `
           --execute 'SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1'
       if ($LASTEXITCODE -ne 0 -or [string]($history | Select-Object -First 1) -ne "22`t1") {
           throw 'Source Flyway history is not the reviewed successful V22 state.'
       }
   } finally { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
   ```

   If database host/account/port or Flyway state changed, stop and have the owner/DBA review this guide again. Do not adapt the target silently.
3. Capture a full logical dump and SHA-256 without printing credential values. The approved encrypted directory must have sufficient space; refuse existing paths. Suggested commands, after approved writer freeze and secure credential setup, are:

   ```powershell
   $backup = Join-Path $approvedEncryptedRoot "bingo_qa_test_v22_$runId.sql"
   if (Test-Path -LiteralPath $backup) { throw 'Refuse overwrite' }
   $env:MYSQL_PWD = $sourcePassword # loaded silently from the ignored .env.test
   & mysqldump.exe --protocol=TCP --host=127.0.0.1 --port=3306 `
     --user=$sourceUser --single-transaction --skip-lock-tables `
     --no-tablespaces --set-gtid-purged=OFF --hex-blob `
     --default-character-set=utf8mb4 bingo_qa_test `
     --result-file=$backup
   if ($LASTEXITCODE -ne 0) { throw 'Backup failed; preserve partial file and stop.' }
   Remove-Item Env:\MYSQL_PWD
   Get-FileHash -LiteralPath $backup -Algorithm SHA256
   ```

   The backup is sensitive account/financial QA data: encrypt and restrict it; never place it in the repo or shared location. `--single-transaction` is consistent only for transactional tables and does not make concurrent DDL safe; the approved writer freeze remains mandatory. Include schema, triggers, routines/events inventory, Flyway history, and verify the backup is restorable before continuing.
4. DBA creates a new unpredictable, run-specific disposable database and a separate least-privilege migration account scoped only to it. Never reuse an existing schema. The source schema default is verified as `utf8mb4/utf8mb4_0900_ai_ci`. Use a protected DBA option file (not a password argument) and a strict target name; these commands are proposals for review and must not be run until approval:

   ```powershell
   $verifyDb = "bingo_qa_v22_v40_$runToken"
   if ($verifyDb -notmatch '^bingo_qa_v22_v40_[0-9a-f]{32}$') { throw 'Unsafe target name' }
   $creatorDefaults = Join-Path $approvedSecretRoot 'qa-clone-creator.cnf'
   if (-not (Test-Path -LiteralPath $creatorDefaults -PathType Leaf)) { throw 'Missing DBA protected option file' }
   $mysqlExe = (Get-Command mysql.exe -ErrorAction Stop).Source
   $createSql = 'CREATE DATABASE `' + $verifyDb + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'
   & $mysqlExe "--defaults-extra-file=$creatorDefaults" --protocol=TCP --host=127.0.0.1 --port=3306 "--execute=$createSql"
   if ($LASTEXITCODE -ne 0) { throw 'Clone creation failed; stop and preserve DBA output.' }
   ```

   The DBA then provisions a unique clone-only user at host `127.0.0.1` with `SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, TRIGGER` on that exact schema. No global grant or `GRANT OPTION`; `CREATE DATABASE` stays with the one-time creator. Store the clone user's connection in a second protected MySQL option file with ACLs limited to the operator. Its format is `[client]` followed by `user=...`, `password=...`, `host=127.0.0.1`, and `port=3306`. Never put credentials in this guide, command-line arguments, or evidence.

   Restore using the clone-scoped option file. Refuse unsafe shell metacharacters in protected paths and refuse any existing output files. This streams the dump instead of loading it into PowerShell memory:

   ```powershell
   $verifyDefaults = Join-Path $approvedSecretRoot 'qa-clone-migrator.cnf'
   if (-not (Test-Path -LiteralPath $verifyDefaults -PathType Leaf)) { throw 'Missing clone-scoped MySQL option file.' }
   $cloneSettings = @{}
   foreach ($line in Get-Content -LiteralPath $verifyDefaults) {
       if ($line -match '^\s*(user|password)\s*=\s*(.*?)\s*$') { $cloneSettings[$matches[1]] = $matches[2] }
   }
   $verifyUser = $cloneSettings['user']
   $verifyPassword = $cloneSettings['password']
   if ([string]::IsNullOrWhiteSpace($verifyUser) -or [string]::IsNullOrWhiteSpace($verifyPassword) -or $verifyUser -notmatch '^[A-Za-z0-9_]+$') { throw 'Clone connection settings are incomplete.' }
   $restoreLog = Join-Path $runDir 'clone-restore.stderr.log'
   $restoreOut = Join-Path $runDir 'clone-restore.stdout.log'
   foreach ($path in @($verifyDefaults,$backup,$restoreLog,$restoreOut)) {
       if ($path -match '[&|<>^%!"]') { throw 'Use protected paths without cmd metacharacters.' }
   }
   foreach ($path in @($restoreLog,$restoreOut)) { if (Test-Path -LiteralPath $path) { throw 'Refuse overwrite.' } }
   $restoreCommand = '"{0}" --defaults-extra-file="{1}" --protocol=TCP --host=127.0.0.1 --port=3306 --database="{2}" < "{3}" 1> "{4}" 2> "{5}"' -f `
       $mysqlExe,$verifyDefaults,$verifyDb,$backup,$restoreOut,$restoreLog
   & $env:ComSpec /d /s /c $restoreCommand
   if ($LASTEXITCODE -ne 0) { throw 'Clone restore failed; preserve the partial clone and stop.' }
   ```

   Compare exact ordered Flyway history (successful V22), table/column/index/FK definitions, exact per-table row counts, and deterministic data fingerprints before migration. Preserve protected evidence only; do not include account values or secrets in logs.
5. Build the checkout once and run Flyway against only the verified clone. The test profile has scheduler, simulation, fixture bootstrap, and synthetic financial testing disabled. Never use the standard port-8080 launcher for the clone or expose it beyond loopback. The following exact command template uses only environment variables for the datasource password and refuses reused output paths:

   ```powershell
   $backendRoot = Join-Path $repoRoot 'bingo-backend'
   $testResources = Join-Path $backendRoot 'src\test\resources'
   $buildDir = Join-Path $env:TEMP "bingo-v22-v40-build-$runId"
   if (Test-Path -LiteralPath $buildDir) { throw 'Refuse reused build directory.' }
   New-Item -ItemType Directory -Path $buildDir | Out-Null
   Push-Location $backendRoot
   try { & mvn "-Dbingo.build.directory=$buildDir" -DskipTests package; if ($LASTEXITCODE -ne 0) { throw 'Build failed before migration.' } }
   finally { Pop-Location }
   $jar = Get-ChildItem $buildDir -Filter 'bingo-backend-*.jar' -File |
       Where-Object { $_.Name -notmatch 'original|sources|javadoc' } |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
   if (-not $jar) { throw 'Packaged backend not found.' }
   $env:BINGO_TEST_MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/$verifyDb"
   $env:BINGO_TEST_MYSQL_USERNAME = $verifyUser
   $env:BINGO_TEST_MYSQL_PASSWORD = $verifyPassword
   $resourceUri = ([Uri]::new(($testResources.TrimEnd('\') + '\'))).AbsoluteUri
   $stdout = Join-Path $runDir 'v22-v40.stdout.log'
   $stderr = Join-Path $runDir 'v22-v40.stderr.log'
   foreach ($path in @($stdout,$stderr)) { if (Test-Path -LiteralPath $path) { throw 'Refuse overwrite.' } }
   $javaArgs = @('-jar',"`"$($jar.FullName)`"", "--spring.config.additional-location=$resourceUri",
       '--spring.profiles.active=android-test','--server.address=127.0.0.1','--server.port=0',
       '--app.qa.test-runtime-identity-reporting-enabled=false','--app.game.draw-scheduler-enabled=false',
       '--app.wallet.deposit-simulation-enabled=false','--app.wallet.adjustment-simulation-enabled=false',
       '--app.agent.float-simulation-enabled=false','--app.qa.synthetic-financial-tests-enabled=false',
       '--app.qa.android-test-fixtures-bootstrap-enabled=false','--spring.jpa.hibernate.ddl-auto=validate',
       '--spring.flyway.clean-disabled=true','--spring.flyway.validate-on-migrate=true')
   $migrationProcess = Start-Process java -ArgumentList $javaArgs -WorkingDirectory $backendRoot `
       -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
   # Poll read-only clone Flyway history; do not retry or start another process on timeout.
   $env:MYSQL_PWD = $verifyPassword
   $deadline = (Get-Date).AddMinutes(5)
   $atV40 = $false
   while ((Get-Date) -lt $deadline -and -not $migrationProcess.HasExited) {
       $version = & $mysqlExe "--defaults-extra-file=$verifyDefaults" --protocol=TCP --host=127.0.0.1 `
           --port=3306 "--user=$verifyUser" "--database=$verifyDb" --batch --raw --skip-column-names `
           --execute 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1'
       if ($LASTEXITCODE -eq 0 -and [string]($version | Select-Object -First 1) -eq '40') { $atV40=$true; break }
       Start-Sleep -Seconds 2
   }
   Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
   Remove-Item Env:\BINGO_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue
   if (-not $atV40) { throw 'Migration not confirmed at V40; preserve clone and logs. Do not retry.' }
   $started = $false
   $startDeadline = (Get-Date).AddMinutes(2)
   while ((Get-Date) -lt $startDeadline -and -not $migrationProcess.HasExited) {
       $startupText = (@(Get-Content -LiteralPath $stdout -Raw -ErrorAction SilentlyContinue) +
           @(Get-Content -LiteralPath $stderr -Raw -ErrorAction SilentlyContinue)) -join "`n"
       if ($startupText -match '(?m)Started .+ in .+ seconds' -and
           $startupText -notmatch 'APPLICATION FAILED TO START|Application run failed') { $started = $true; break }
       Start-Sleep -Seconds 2
   }
   if (-not $started) { throw 'Flyway V40 is recorded but app startup/Hibernate validation was not confirmed; preserve clone and logs.' }
   Remove-Item Env:\BINGO_TEST_MYSQL_USERNAME,Env:\BINGO_TEST_MYSQL_URL -ErrorAction SilentlyContinue
   ```

   Require the application log to contain successful startup and Hibernate validation as well as Flyway V40. Stop only the exact `migrationProcess` after capturing health/log evidence; preserve the clone until DBA signs off. The 5-minute bound is a stop condition, not a runtime promise.
6. Require startup success, exactly successful Flyway versions through V40, Hibernate validation, 48 base tables (40 at V22 plus 4 at V23, 1 at V25, and 3 at V38), and read-only postchecks: V23's four tables and room privacy semantics; V27 phone trigger definitions and role/phone uniqueness; V30 expected role mapping with no legacy finance roles; V35 uniqueness/index/FKs and intentional defaults; V36 no duplicate entitlements and expected session defaults; V37 check absence; V38 settings singleton and empty announcements/read tables; V39 absence of `rules_version`; V40 referral-code uniqueness/non-null checks, null referral attribution for legacy rows, and trigger-reference uniqueness/backfill checks. Compare all unaffected source data fingerprints. Explicitly reconcile V27/V30/V31 changes and V38's intentional singleton. Verify wallets, transactions, float, journals, prize entitlements, tickets, and all unrelated rows are unchanged. Have DBA review the before/after report.
7. On any error, timeout, partial Flyway history, or startup/validation failure: stop only the exact process started by this task; preserve original backup/hash, failed clone, logs, and exact DB identity. Do not run `flyway repair`, `clean`, retry, manual DDL, rollback, or drop. DBA reviews. A retry requires a newly named clone restored from the unchanged V22 backup. Clone deletion requires a separate approval and exact identity check.
8. Keep the existing `bingo_qa_test` at V22. Do not migrate, restore over, clean, or repoint it. After the V22→V40 rehearsal passes, the same isolated V40 clone may be used for feature acceptance only after the clone-specific guarded runtime path is reviewed and approved, the backend is started from the owner's normal interactive Windows session, and the required synthetic purchase/claim execution approvals are in place. If clone migration partially applies, preserve that clone and backup for DBA review; any retry uses a newly named clone restored from the unchanged V22 backup.

## Separate Maven campaign-test schema paths — prepared, not authorized

The Maven-only `bingo_qa_maven_test` is a separate disposable schema, read-only
verified at V26 on 2026-11-13. Its test account cannot create schemas. The exact
repository gate in [`qa-blockers-and-approvals.md`](qa-blockers-and-approvals.md),
“Maven integration schema”, says: **“DBA/Product approval is still required
before any V26→V40 migration rehearsal or fresh-schema reset.”** These commands
are review material only. Do not run them until that approval names the source,
clone, operator, and protected evidence directory. Never migrate the source
`bingo_qa_maven_test` in place.

### V26 → V40 clone rehearsal

1. Obtain a written writer freeze, DBA approval for a one-time creator and
   clone-scoped migrator, and protected encrypted evidence/option-file paths.
   Use the normal user's secret-handling procedure to make source and clone
   MySQL option files; they must not be stored in this repository or emitted
   in logs. Use a fresh run token and refuse existing paths:

   ```powershell
   $runToken = [guid]::NewGuid().ToString('N')
   $sourceDb = 'bingo_qa_maven_test'
   $cloneDb = "bingo_qa_maven_v26_v40_$runToken"
   if ($cloneDb -notmatch '^bingo_qa_maven_v26_v40_[0-9a-f]{32}$') { throw 'Unsafe clone name' }
   $sourceDefaults = Join-Path $approvedSecretRoot 'maven-test-source.cnf'
   $cloneDefaults = Join-Path $approvedSecretRoot 'maven-test-clone.cnf'
   $backup = Join-Path $approvedEncryptedRoot "bingo_qa_maven_test_v26_$runToken.sql"
   foreach ($path in @($sourceDefaults,$cloneDefaults)) {
       if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw 'Missing protected option file' }
   }
   if (Test-Path -LiteralPath $backup) { throw 'Refuse overwrite' }
   $mysqlExe = (Get-Command mysql.exe -ErrorAction Stop).Source
   $dumpExe = (Get-Command mysqldump.exe -ErrorAction Stop).Source
   $sourceIdentity = & $mysqlExe "--defaults-extra-file=$sourceDefaults" --protocol=TCP `
       --host=127.0.0.1 --port=3306 --database=$sourceDb --batch --skip-column-names `
       --execute "SELECT DATABASE(), CURRENT_USER(), @@port, (SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1), (SELECT COUNT(*) FROM flyway_schema_history WHERE success=0) FROM flyway_schema_history LIMIT 1"
   if ($LASTEXITCODE -ne 0 -or [string]($sourceIdentity -join '') -notmatch '^bingo_qa_maven_test\tbingo_qa_maven_runner@127\.0\.0\.1\t3306\t26\t0$') {
       throw 'Source identity/history is not the approved V26 Maven test schema; stop.'
   }
   & $dumpExe "--defaults-extra-file=$sourceDefaults" --protocol=TCP --host=127.0.0.1 `
       --port=3306 --single-transaction --skip-lock-tables --no-tablespaces `
       --set-gtid-purged=OFF --hex-blob --default-character-set=utf8mb4 `
       "--result-file=$backup" $sourceDb
   if ($LASTEXITCODE -ne 0) { throw 'Backup failed; preserve partial output and stop.' }
   Get-FileHash -LiteralPath $backup -Algorithm SHA256
   ```

   The DBA creates `$cloneDb` using the V22 rehearsal's strict `CREATE DATABASE`
   pattern and provisions `$cloneDefaults` with only
   `SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, TRIGGER`
   on that exact schema. Restore the dump there with the reviewed step 4
   protected-option-file redirection command. Before migration, compare source
   and clone database/account identity, V26 Flyway history, table definitions,
   exact per-table row counts, and deterministic data fingerprints. Keep the
   original backup/hash immutable.
2. Reuse the packaged-app Flyway command in V22 rehearsal step 5, substituting
   only `$cloneDb`, `$cloneDefaults`, a run-specific build/log path, and the
   V26→V40 labels. Keep Hibernate `validate`, Flyway clean disabled, schedulers,
   simulations, fixture bootstrap, and synthetic financial processing off.
   The current V40 app applies migrations only to the verified clone. Require
   successful history through V40, Hibernate startup/validation, and the
   read-only assertions below before considering the rehearsal passed.
3. On timeout, failed migration, unexpected version, or startup error, preserve
   the source, backup, partially migrated clone, and sanitized logs. Stop only
   the exact Java process started by this rehearsal. Do not repair, clean,
   manually edit history, retry, or drop the clone. A retry requires a newly
   named clone restored from the unchanged V26 backup and fresh DBA approval.

### Empty fresh V40 schema validation

This validates migration creation from empty state; it is not upgrade evidence.
After separate written DBA/Product approval, use a fresh unique name, the
one-time creator account, and a migrator scoped only to that new schema. Do not
reset either existing QA schema:

```powershell
$runToken = [guid]::NewGuid().ToString('N')
$freshDb = "bingo_qa_reward_fresh_$runToken"
if ($freshDb -notmatch '^bingo_qa_reward_fresh_[0-9a-f]{32}$') { throw 'Unsafe schema name' }
$creatorDefaults = Join-Path $approvedSecretRoot 'reward-fresh-creator.cnf'
$freshDefaults = Join-Path $approvedSecretRoot 'reward-fresh-migrator.cnf'
if (-not (Test-Path -LiteralPath $creatorDefaults) -or -not (Test-Path -LiteralPath $freshDefaults)) { throw 'Missing protected DBA option file' }
$mysqlExe = (Get-Command mysql.exe -ErrorAction Stop).Source
$createSql = "CREATE DATABASE ``$freshDb`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
& $mysqlExe "--defaults-extra-file=$creatorDefaults" --protocol=TCP --host=127.0.0.1 `
    --port=3306 "--execute=$createSql"
if ($LASTEXITCODE -ne 0) { throw 'Fresh schema creation failed; preserve DBA output and stop.' }
```

The DBA provisions the migrator with the V26 clone's schema-scoped privileges.
Before application startup, use the scoped option file for a read-only check
that `DATABASE()` is `$freshDb`, `CURRENT_USER()` is the approved localhost
migrator, `@@port` is 3306, and `information_schema.tables` reports zero tables
in that schema. If any check differs, stop. The application command from V22
rehearsal step 5 is then pointed only at `$freshDb`. Capture sanitized
identity/history/startup evidence. Require
successful Flyway history through V40 and Hibernate validation, then verify the
expected 48 application tables, no failed Flyway rows, the campaign-event
unique index, absence of the old global signup-player unique index, non-null
unique `BINGO<id>` referral codes (zero players is valid), nullable legacy
referrer attribution, and no pre-existing grants. Preserve the empty schema
and logs until DBA review; do not use it as an upgrade clone.

### V40 migration assertions for both paths

Run only read-only SQL under the reviewed scoped account after migration:

```sql
SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) FROM users WHERE referral_code IS NULL OR referral_code = '';
SELECT COUNT(*) - COUNT(DISTINCT referral_code) FROM users;
SELECT COUNT(*) FROM signup_reward_grants WHERE trigger_reference_key IS NULL;
SELECT COUNT(*) FROM signup_reward_grants g
 WHERE g.trigger_reference_key <> CONCAT('PLAYER-', g.player_id);
SELECT COUNT(*) FROM signup_reward_grants g
 WHERE g.trigger_type = 'SIGNUP'
   AND g.trigger_reference_key <> CONCAT('PLAYER-', g.player_id);
SELECT COUNT(*) FROM (
 SELECT policy_id, trigger_reference_key FROM signup_reward_grants
 GROUP BY policy_id, trigger_reference_key HAVING COUNT(*) > 1
) collisions;
SELECT index_name, non_unique, GROUP_CONCAT(column_name ORDER BY seq_in_index)
 FROM information_schema.statistics
 WHERE table_schema = DATABASE() AND table_name = 'signup_reward_grants'
 GROUP BY index_name, non_unique;
```

Expect no failed Flyway rows, null/duplicate referral codes, null grant keys,
legacy-key mismatches or event collisions; expect the unique
`(policy_id, trigger_reference_key)` key and no unique `signup_player_id` key.
The trigger key is the database-backed award cardinality: `PLAYER-<player id>`
for signup (and legacy grants), `REFERRAL-<referred player id>` for referrals,
and `WALLET-CREDIT-<player id>` for the one wallet-credit award per campaign.
The latter key intentionally does not contain a deposit id, so additional
qualifying deposits cannot produce another grant for the same player/campaign.
On the V26 clone, reconcile all intentional V27–V40 data changes and prove
wallets, grant amounts, transactions, float, journals, tickets, entitlements,
and unrelated rows match the approved change inventory. On the empty schema,
the zero-grant assertion is expected. Preserve SQL output without secrets.

## Runtime acceptance gate after QA is available

Fresh V40 validation and V22→V40 clone rehearsal are separate evidence. Only after approved QA backend startup passes fresh health, datasource/database identity, Flyway V40, disabled-control checks, and synthetic-account authentication may authenticated gameplay testing begin. Purchase, claim-fixture, or financial actions additionally require the approvals in [`qa-blockers-and-approvals.md`](qa-blockers-and-approvals.md); this migration document grants none. Record UI and authoritative-record outcomes separately. No authenticated live acceptance has been run for this review.
