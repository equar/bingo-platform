# Local `bingo_qa_test` Backup, Restore, and V23 Runbook

**Status: prepared for review only.** No database, backup, restore, grant, or migration operation in this runbook has been executed. Do not execute it until the approval at the end is granted and the prerequisites are met.

Run the procedure blocks from the repository root in one PowerShell 7 session so the unique run ID, evidence paths, and helper functions remain in scope.

## Findings and scope

- The local source is `127.0.0.1:3306/bingo_qa_test`, currently at successful Flyway V22; checked-out backend source includes V23. The guarded QA launcher correctly refuses to start this mismatch. This is a legitimate pending migration, not a profile-selection error.
- V23 adds `bingo_rooms.private_room` (default `false`), a stored generated `public_name` for public rooms, a public-name unique constraint, and a visibility index. It removes the former unique index directly on `bingo_rooms.name`. It creates `private_games`, `private_game_members`, `player_challenge_claims`, and `leaderboard_profiles`.
- V23 contains no `INSERT`, `UPDATE`, or `DELETE` statements and does not reference financial tables. Existing rooms therefore retain their stored fields; they acquire `private_room=false` and generated public names. Existing public duplicate names, if any, would prevent the new unique key from being created and must be investigated, not edited to force success. New tables should be empty after migration.
- These are MySQL DDL statements, which can commit independently. A failure midway can leave schema changes without a successful V23 Flyway history row. Do not retry blindly, repair Flyway history, run `clean`, or manually reverse DDL.
- Installed local MySQL server/client tools are 8.0.36: `mysql.exe`, `mysqldump.exe`, `mysqlpump.exe` (deprecated), and `mysqlsh.exe`. Use `mysqldump.exe`; `mydumper`/`myloader` are unavailable.
- The effective `bingo_qa_test_runner` grant is global `USAGE` plus `ALL PRIVILEGES` only on `bingo_qa_test.*`. It cannot create or access a separately named verification schema. The owner-authorized local-root exception is limited to the Android backend targeting `bingo_qa_test`; it does not authorize using root for the verification backend or another schema.
- The source runner has no global `PROCESS` privilege, so its view of `information_schema.processlist` cannot prove that all other users' connections are gone. A DBA must perform the read-only connection check using an already-authorized identity with process-list visibility; do not request or grant new privileges as part of this preparation.
- The current schema has 40 InnoDB tables, including `flyway_schema_history`; no routines, events, or triggers were found. The existing QA account can read and dump the source schema, but a separately provisioned DBA/verification identity is required to create and restore a copy and run V23 against it. Do not change grants as part of this preparation.
- The migration profile uses Flyway, Hibernate `validate`, `clean-disabled=true`, and disables scheduler, fixture bootstrap, and financial simulation/posting controls.

## Approval and safety boundaries

The maintained [guarded E2E runbook](platform-e2e-runbook.md) prohibits
Flyway `clean` against persistent data, requires the exact isolated target,
and does not authorize migration or guard bypass. The
[current blockers](qa-blockers-and-approvals.md) require DBA provisioning and
verification for destructive Maven tests. Neither guide approves this V23
backup/restore rehearsal or migration.

This runbook is prepared for review only. Execute no backup, restore, schema
creation, account/grant change, or V23 migration until the specific owner and
DBA approvals in the procedure are obtained. The local root exception described
in the E2E runbook applies only to the guarded Android test backend targeting
`bingo_qa_test`; it does not authorize access to a verification schema or
change the required separately scoped verification identity.

## Preconditions

1. Written owner approval for the source backup and restore rehearsal; written DBA authorization to create a uniquely named disposable schema and provision an identity scoped only to that schema.
2. A restricted, encrypted local backup location with enough free space for the full logical dump and evidence. Do not use a shared or unencrypted location. The examples below use `$backupRoot`; set it to the approved location before proceeding.
3. PowerShell 7 or newer, MySQL 8.0.36 and `mysqldump.exe`/`mysql.exe`; the source account from ignored `bingo-backend\.env.test`; and DBA-provisioned read-only process-list and disposable-schema credentials. Never print or place credentials in command-line arguments, logs, manifests, or repository files.
4. A DBA must authorize a one-time database-creator identity with `CREATE` permission to create the exact fresh verification schema, then provision a separate account scoped only to that schema. The clone account needs the object-restore privileges required by the dump (`CREATE`, `ALTER`, `INDEX`, `REFERENCES`, DML, and—if any routines/events/triggers are present—those object-specific privileges), Flyway history DML, and `CREATE TEMPORARY TABLES` for the probes. The source runner's schema-scoped grant is not sufficient. Do not point the backend at a disposable schema using the local-root exception.
5. A verified stop/write-freeze window. Stop only backend and test processes started for this task. Stop the Android app/test runner if this task owns it. Do not stop or kill processes by broad name. If another writer or an unrecognized client is connected to `bingo_qa_test`, stop and investigate; do not proceed. This is an operational freeze, not a MySQL global `read_only` change.
6. Preserve financial posting/simulation, schedulers, and fixture bootstrap as disabled. No business API calls, fixture writes, synthetic tickets, wallet/float/prize operations, or external payment actions are part of this runbook.

## Procedure — execute only after approval

### 1. Initialize a unique evidence directory and inspect the pinned target

The following only reads configuration/metadata and creates a new local evidence directory; it does not create or alter a database. Refuse an existing run directory instead of reusing it.

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path '.').Path
$backendRoot = Join-Path $repoRoot 'bingo-backend'
$policyModule = Join-Path $repoRoot 'scripts\qa\AndroidTestBackendPolicy.psm1'
$envFile = Join-Path $backendRoot '.env.test'
$mysql = Get-Command mysql.exe -ErrorAction Stop
$mysqldump = Get-Command mysqldump.exe -ErrorAction Stop

if (-not (Test-Path -LiteralPath $envFile)) { throw 'Ignored .env.test is missing.' }
Import-Module $policyModule -Force

$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Za-z0-9_]+)=(.*)\s*$') {
        $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
    }
}
foreach ($key in @('BINGO_TEST_MYSQL_URL', 'BINGO_TEST_MYSQL_USERNAME', 'BINGO_TEST_MYSQL_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([string]$settings[$key])) {
        throw "Missing isolated source setting: $key"
    }
}
$null = Assert-AndroidTestDatasource -JdbcUrl $settings.BINGO_TEST_MYSQL_URL
if ($settings.BINGO_TEST_MYSQL_URL -notmatch '^jdbc:mysql://127\.0\.0\.1:3306/bingo_qa_test(?:\?|$)') {
    throw 'Source URL is not the fixed local bingo_qa_test target.'
}

$backupRoot = Read-Host 'Enter the approved encrypted backup directory (no existing backup will be overwritten)'
if (-not (Test-Path -LiteralPath $backupRoot -PathType Container)) {
    throw 'The approved backup directory must already exist.'
}
$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ') + '_' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$runDir = Join-Path $backupRoot "bingo_qa_test_v23_$runId"
if (Test-Path -LiteralPath $runDir) { throw 'Run directory already exists; choose a fresh run ID.' }
New-Item -ItemType Directory -Path $runDir | Out-Null

$sourceUser = [string]$settings.BINGO_TEST_MYSQL_USERNAME
$sourcePassword = [string]$settings.BINGO_TEST_MYSQL_PASSWORD
$processCheck = Get-Credential -Message 'Enter an already-authorized DBA identity with read-only process-list visibility'
$processCheckUser = $processCheck.UserName
$processCheckPassword = $processCheck.GetNetworkCredential().Password
if ($processCheckUser -notmatch '^[A-Za-z0-9_]+$') { throw 'Unsupported DBA username format.' }
$env:MYSQL_PWD = $sourcePassword
try {
    $identity = & $mysql.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$sourceUser" --database=bingo_qa_test --batch --raw --skip-column-names --execute 'SELECT DATABASE(), CURRENT_USER(), @@hostname, @@port, VERSION()'
    if ($LASTEXITCODE -ne 0 -or $identity.Count -ne 1 -or $identity[0] -notmatch '^bingo_qa_test\t.*\t3306\t8\.0\.36') {
        throw 'Source identity/version did not match the approved local database.'
    }
    $defaults = & $mysql.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$sourceUser" --database=bingo_qa_test --batch --raw --skip-column-names --execute "SELECT default_character_set_name, default_collation_name FROM information_schema.schemata WHERE schema_name = 'bingo_qa_test'"
    if ($LASTEXITCODE -ne 0 -or $defaults.Count -ne 1) { throw 'Could not read source schema defaults.' }
    $charset, $collation = $defaults[0] -split "`t"
    if ($charset -notmatch '^[A-Za-z0-9_]+$' -or $collation -notmatch '^[A-Za-z0-9_]+$') {
        throw 'Unexpected source charset/collation identifier.'
    }
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    $sourcePassword = $null
    $settings['BINGO_TEST_MYSQL_PASSWORD'] = $null
}
$env:MYSQL_PWD = $processCheckPassword
try {
    $connections = & $mysql.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$processCheckUser" --batch --raw --skip-column-names --execute 'SELECT id, user, host, db, command, time FROM information_schema.processlist WHERE db = ''bingo_qa_test'' AND id <> CONNECTION_ID()'
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect active source connections.' }
    if ($connections.Count -gt 0) { throw 'Other source connections exist; identify/stop authorized writers and repeat preflight.' }
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    $processCheckPassword = $null
    $processCheck = $null
}

Write-Host "Evidence directory: $runDir"
Write-Host 'Source identity confirmed; credentials were not printed.'
```

### 2. Stop only task-owned writers and verify the freeze

If the backend from this task is running, validate every identity field and its listener before stopping its exact PID. Do not act on stale or mismatched evidence. The following stops only the verified PID from the existing launcher evidence; it does not use `-Force`.

```powershell
$identityPath = Join-Path $repoRoot 'docs\qa\evidence\module-06a5\backend-identity-preflight.json'
if (Test-Path -LiteralPath $identityPath) {
    if (((Get-Date).ToUniversalTime() - (Get-Item -LiteralPath $identityPath).LastWriteTimeUtc).TotalMinutes -gt 15) {
        throw 'Backend identity evidence is stale; do not stop a process from an earlier task.'
    }
    $backend = Get-Content -LiteralPath $identityPath -Raw | ConvertFrom-Json
    if ($backend.database -ne 'bingo_qa_test' -or
        $backend.jdbcHost -ne '127.0.0.1' -or [int]$backend.jdbcPort -ne 3306 -or
        $backend.drawScheduler -ne 'disabled' -or
        $backend.financialPostingAndSimulation -ne 'disabled' -or
        [int]$backend.processId -le 0) {
        throw 'Identity evidence is not an eligible task-owned backend.'
    }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$backend.processId)"
    $listenerPids = @(Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
    $runningProcess = Get-Process -Id ([int]$backend.processId) -ErrorAction SilentlyContinue
    $expectedStart = [DateTimeOffset]::Parse([string]$backend.processStartedAt).LocalDateTime
    if (-not $process -or $process.Name -ne 'java.exe' -or
        -not $process.CommandLine.Contains([string]$backend.artifactPath) -or
        -not $runningProcess -or [Math]::Abs(($runningProcess.StartTime - $expectedStart).TotalSeconds) -gt 2 -or
        $listenerPids.Count -ne 1 -or [int]$listenerPids[0] -ne [int]$backend.processId) {
        throw 'Backend process/listener does not match the saved task-owned identity; do not stop it.'
    }
    Stop-Process -Id ([int]$backend.processId)
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Process -Id ([int]$backend.processId) -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 1
    }
    if (Get-Process -Id ([int]$backend.processId) -ErrorAction SilentlyContinue) {
        throw 'Verified backend did not stop gracefully; do not force-kill or continue.'
    }
}

# Stop/close only Flutter, browser, or test processes that this run started.
# Do not stop an unrelated app, emulator, MySQL service, or process by name.
```

Repeat the read-only connection query from step 1 after the task-owned backend/test clients stop. If any unknown source connection remains, do not dump until its owner confirms the write freeze. Do not use `SET GLOBAL read_only`, modify grants, or change any startup guard.

### 3. Capture the full source backup and checksum

The dump includes all schema objects, data, and `flyway_schema_history`; `--no-tablespaces` avoids a global PROCESS requirement, and the source schema is InnoDB. This reads `bingo_qa_test` and creates only new files in `$runDir`.

```powershell
$dumpPath = Join-Path $runDir 'bingo_qa_test-full.sql'
$schemaPath = Join-Path $runDir 'bingo_qa_test-schema.sql'
$manifestPath = Join-Path $runDir 'backup-manifest.txt'
foreach ($path in @($dumpPath, $schemaPath, $manifestPath)) {
    if (Test-Path -LiteralPath $path) { throw "Refusing to overwrite: $path" }
}

$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Za-z0-9_]+)=(.*)\s*$') {
        $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
    }
}
$sourceUser = [string]$settings.BINGO_TEST_MYSQL_USERNAME
$env:MYSQL_PWD = [string]$settings.BINGO_TEST_MYSQL_PASSWORD
try {
    & $mysqldump.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$sourceUser" `
        --single-transaction --skip-lock-tables --skip-add-locks --no-tablespaces --set-gtid-purged=OFF `
        --default-character-set=utf8mb4 --hex-blob --routines --events --triggers `
        --skip-add-drop-table --result-file="$dumpPath" bingo_qa_test
    if ($LASTEXITCODE -ne 0) { throw 'Full source backup failed; stop and preserve partial output.' }

    & $mysqldump.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$sourceUser" `
        --single-transaction --skip-lock-tables --skip-add-locks --no-tablespaces --set-gtid-purged=OFF `
        --default-character-set=utf8mb4 --hex-blob --no-data --routines --events --triggers `
        --skip-add-drop-table --result-file="$schemaPath" bingo_qa_test
    if ($LASTEXITCODE -ne 0) { throw 'Schema-only capture failed; do not proceed to restore.' }
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    $settings['BINGO_TEST_MYSQL_PASSWORD'] = $null
}

foreach ($path in @($dumpPath, $schemaPath)) {
    $file = Get-Item -LiteralPath $path
    if ($file.Length -le 0) { throw "Empty backup artifact: $path" }
}
$dumpHash = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash
$schemaHash = (Get-FileHash -LiteralPath $schemaPath -Algorithm SHA256).Hash
$verificationSummary = Join-Path $runDir 'v23-verification-summary.txt'
if (Test-Path -LiteralPath $verificationSummary) { throw 'Refusing to overwrite verification summary.' }
@(
    "createdUtc=$((Get-Date).ToUniversalTime().ToString('o'))"
    "source=127.0.0.1:3306/bingo_qa_test"
    "mysqlClient=$((& $mysql.Source --version) -join ' ')"
    "mysqldump=$((& $mysqldump.Source --version) -join ' ')"
    "dumpBytes=$((Get-Item -LiteralPath $dumpPath).Length)"
    "dumpSha256=$dumpHash"
    "schemaBytes=$((Get-Item -LiteralPath $schemaPath).Length)"
    "schemaSha256=$schemaHash"
) | Set-Content -LiteralPath $manifestPath -Encoding ascii
Get-Content -LiteralPath $manifestPath
```

Do not continue if either command fails, either file is empty, the hash is not recorded, or the approved backup location is not protected. Preserve the dump and manifest; do not rerun over them.

### 4. Create a separately named disposable verification schema and restore

**DATABASE EFFECT: CREATE** one new disposable schema. The command refuses an existing name. It never uses `bingo`, `bingo_qa`, or an existing verification schema. The DBA-provisioned credential is needed; the local runner and local-root exception are not authorized for this target.

```powershell
$verifyDb = "bingo_qa_test_v23_verify_$runId".ToLowerInvariant().Replace('-', '_')
if ($verifyDb -notmatch '^bingo_qa_test_v23_verify_[a-z0-9_]+$' -or $verifyDb.Length -gt 64) {
    throw 'Verification schema name is invalid.'
}

$dbCreator = Get-Credential -Message 'Enter the DBA-authorized one-time database-creator credential'
$dbCreatorUser = $dbCreator.UserName
$dbCreatorPassword = $dbCreator.GetNetworkCredential().Password
if ($dbCreatorUser -notmatch '^[A-Za-z0-9_]+$') { throw 'Unsupported database-creator username format.' }
$env:MYSQL_PWD = $dbCreatorPassword
try {
    $exists = & $mysql.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$dbCreatorUser" `
        --batch --raw --skip-column-names --execute "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = '$verifyDb'"
    if ($LASTEXITCODE -ne 0 -or $exists.Count -ne 1 -or $exists[0] -ne '0') {
        throw 'Verification schema already exists or could not be checked; refusing to reuse it.'
    }

    # DATABASE: CREATE. The only interpolated schema is the new name above.
    & $mysql.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$dbCreatorUser" `
        --execute "CREATE DATABASE ``$verifyDb`` CHARACTER SET $charset COLLATE $collation"
    if ($LASTEXITCODE -ne 0) { throw 'Disposable schema creation failed.' }
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    $dbCreatorPassword = $null
    $dbCreator = $null
}

$verifyCredential = Get-Credential -Message 'Enter the DBA-provisioned account scoped only to the new verification schema'
$verifyUser = $verifyCredential.UserName
$verifyPassword = $verifyCredential.GetNetworkCredential().Password
if ($verifyUser -notmatch '^[A-Za-z0-9_]+$') { throw 'Unsupported disposable-schema username format.' }
$env:MYSQL_PWD = $verifyPassword
try {
    $targetCheck = & $mysql.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$verifyUser" `
        --database=$verifyDb --batch --raw --skip-column-names --execute 'SELECT DATABASE()'
    if ($LASTEXITCODE -ne 0 -or $targetCheck.Count -ne 1 -or $targetCheck[0] -ne $verifyDb) {
        throw 'Disposable identity is not pinned to the new verification schema.'
    }
    # Restore a plain single-schema dump to the explicitly selected new database.
    # No --databases option is used, so the dump has no source USE/CREATE DATABASE directive.
    $restoreCmd = '"{0}" --protocol=TCP --host=127.0.0.1 --port=3306 --user="{1}" --database="{2}" < "{3}"' -f `
        $mysql.Source, $verifyUser, $verifyDb, $dumpPath
    & $env:ComSpec /d /c $restoreCmd
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed; preserve the disposable schema and all diagnostics.' }
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}
```

If the installed shell does not pass the `cmd.exe` redirection exactly as shown, stop and have the DBA review an equivalent streaming restore command before execution. Do not improvise a restore target.

### 5. Compare the restored V22 copy before migration

Reload credentials securely; do not echo either password. Run the following comparisons and save only sanitized counts/hashes and Flyway metadata under the unique `$runDir`.

```powershell
$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Za-z0-9_]+)=(.*)\s*$') {
        $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
    }
}
$sourceUser = [string]$settings.BINGO_TEST_MYSQL_USERNAME
$sourcePassword = [string]$settings.BINGO_TEST_MYSQL_PASSWORD
$verifyCredential = Get-Credential -Message 'Enter the DBA-provisioned account scoped only to the verification schema'
$verifyUser = $verifyCredential.UserName
$verifyPassword = $verifyCredential.GetNetworkCredential().Password
if ($verifyUser -notmatch '^[A-Za-z0-9_]+$') { throw 'Unsupported disposable-schema username format.' }

function Invoke-LocalMysql {
    param([string]$User, [string]$Password, [string]$Database, [string]$Sql)
    $previousPassword = $env:MYSQL_PWD
    $env:MYSQL_PWD = $Password
    try {
        $result = & $mysql.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$User" `
            "--database=$Database" --batch --raw --skip-column-names --execute $Sql
        if ($LASTEXITCODE -ne 0) { throw "Read-only query failed for $Database." }
        return $result
    } finally {
        if ($null -eq $previousPassword) { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
        else { $env:MYSQL_PWD = $previousPassword }
    }
}

# Use the runner credential for source reads and the DBA-provisioned credential for the copy.
$sourceTables = @(Invoke-LocalMysql $sourceUser $sourcePassword 'bingo_qa_test' `
    'SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = ''BASE TABLE'' ORDER BY table_name')
$copyTables = @(Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb `
    'SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = ''BASE TABLE'' ORDER BY table_name')
if (Compare-Object $sourceTables $copyTables) { throw 'Restored base-table names differ from source.' }

$sourceSchemaCheck = Join-Path $runDir 'source-v22-schema-check.sql'
$copySchemaCheck = Join-Path $runDir 'restored-v22-schema-check.sql'
foreach ($path in @($sourceSchemaCheck, $copySchemaCheck)) {
    if (Test-Path -LiteralPath $path) { throw "Refusing to overwrite: $path" }
}
$env:MYSQL_PWD = $sourcePassword
try {
    & $mysqldump.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$sourceUser" --no-data --no-tablespaces --set-gtid-purged=OFF --skip-lock-tables --skip-add-locks --skip-add-drop-table --skip-comments --skip-dump-date --result-file="$sourceSchemaCheck" bingo_qa_test
    if ($LASTEXITCODE -ne 0) { throw 'Source V22 schema comparison dump failed.' }
} finally { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
$env:MYSQL_PWD = $verifyPassword
try {
    & $mysqldump.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$verifyUser" --no-data --no-tablespaces --set-gtid-purged=OFF --skip-lock-tables --skip-add-locks --skip-add-drop-table --skip-comments --skip-dump-date --result-file="$copySchemaCheck" $verifyDb
    if ($LASTEXITCODE -ne 0) { throw 'Restored V22 schema comparison dump failed.' }
} finally { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
if ((Get-FileHash $sourceSchemaCheck -Algorithm SHA256).Hash -ne (Get-FileHash $copySchemaCheck -Algorithm SHA256).Hash) {
    throw 'Source/restored V22 schema hashes differ.'
}

$historySql = 'SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success FROM flyway_schema_history ORDER BY installed_rank'
$sourceHistory = @(Invoke-LocalMysql $sourceUser $sourcePassword 'bingo_qa_test' $historySql)
$copyHistory = @(Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb $historySql)
if (Compare-Object $sourceHistory $copyHistory) { throw 'Restored Flyway history differs from source.' }

function Get-ExactTableCounts([string]$User, [string]$Password, [string]$Database, [string[]]$Tables) {
    foreach ($table in $Tables) {
        if ($table -notmatch '^[A-Za-z0-9_]+$') { throw 'Unexpected table identifier.' }
        $count = Invoke-LocalMysql $User $Password $Database "SELECT COUNT(*) FROM ``$table``"
        "$table`t$($count[0])"
    }
}
$sourceCounts = @(Get-ExactTableCounts $sourceUser $sourcePassword 'bingo_qa_test' $sourceTables)
$copyCounts = @(Get-ExactTableCounts $verifyUser $verifyPassword $verifyDb $copyTables)
if (Compare-Object $sourceCounts $copyCounts) { throw 'Exact table row counts differ after restore.' }
$sourceCounts | Set-Content -LiteralPath (Join-Path $runDir 'source-v22-table-counts.txt') -Encoding ascii
```

The compare must show the exact same base tables, identical schema hash, identical ordered Flyway history with successful V22, and exact counts for every table. Stop if anything differs. The backup/restore rehearsal has not passed until all these checks pass.

### 6. Record pre-migration data fingerprints

Record a per-row hash for all preexisting `bingo_rooms` fields and a deterministic data-only dump for every other preexisting table except Flyway history. This verifies room business fields and all other records—including every financial/accounting table—remain unchanged after V23. No record values are written to the evidence files.

```powershell
$roomHashSql = @'
SELECT SHA2(CAST(JSON_ARRAY(id, name, description, ticket_price, currency, timezone,
 schedule_rule, session_capacity, max_tickets_per_player,
 ticket_sales_close_on_session_start, allow_multiple_winners_per_prize, active,
 configured_patterns_json, configured_prizes_json, created_at, updated_at, version) AS CHAR), 256)
FROM bingo_rooms ORDER BY id
'@
$sourceRoomRows = @(Invoke-LocalMysql $sourceUser $sourcePassword 'bingo_qa_test' $roomHashSql)
$copyRoomRows = @(Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb $roomHashSql)
if (Compare-Object $sourceRoomRows $copyRoomRows) { throw 'Room row fingerprints differ before V23.' }

$comparisonTables = @($sourceTables | Where-Object { $_ -notin @('bingo_rooms', 'flyway_schema_history') })
$dataBefore = Join-Path $runDir 'pre-v23-existing-data.sql'
if (Test-Path -LiteralPath $dataBefore) { throw 'Refusing to overwrite pre-migration data fingerprint.' }
$env:MYSQL_PWD = $sourcePassword
try {
    $dumpArgs = @(
        '--protocol=TCP', '--host=127.0.0.1', '--port=3306', "--user=$sourceUser",
        '--single-transaction', '--skip-lock-tables', '--skip-add-locks', '--no-tablespaces',
        '--set-gtid-purged=OFF', '--default-character-set=utf8mb4', '--hex-blob',
        '--no-create-info', '--skip-comments', '--skip-dump-date', '--skip-extended-insert',
        '--complete-insert', '--order-by-primary', "--result-file=$dataBefore",
        'bingo_qa_test'
    ) + $comparisonTables
    & $mysqldump.Source @dumpArgs
    if ($LASTEXITCODE -ne 0) { throw 'Could not capture pre-V23 data fingerprint.' }
} finally { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
$dataBeforeHash = (Get-FileHash -LiteralPath $dataBefore -Algorithm SHA256).Hash
$roomRowsHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData(
    [Text.Encoding]::UTF8.GetBytes(($sourceRoomRows -join "`n"))
))
```

Keep the backup unchanged and protected. Never edit, truncate, or reuse any backup/fingerprint file.

### 7. Apply V23 to the restored copy through Spring Boot/Flyway

**DATABASE EFFECT: MODIFY** only `$verifyDb`. Do not use `Start-AndroidTestBackend.ps1` for the V22 source schema: it intentionally pins functional E2E to `bingo_qa_test` and refuses a source/schema mismatch. This one-time rehearsal starts the current packaged application with the normal `android-test` Spring/Flyway configuration, but sets its datasource explicitly to the verified disposable copy. The test identity reporter remains disabled because it is intentionally hard-pinned to `bingo_qa_test`; do not change its source or weaken the E2E launcher. Do not expose the temporary server beyond loopback.

```powershell
$backendRoot = Join-Path $repoRoot 'bingo-backend'
$testResources = Join-Path $backendRoot 'src\test\resources'
$buildDir = Join-Path $env:TEMP "bingo-v23-review-build-$runId"
if (Test-Path -LiteralPath $buildDir) { throw 'Refusing to reuse a build directory.' }
New-Item -ItemType Directory -Path $buildDir | Out-Null
Push-Location $backendRoot
try {
    & mvn "-Dbingo.build.directory=$buildDir" -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Current-source backend packaging failed; no migration was attempted.' }
} finally { Pop-Location }
$jar = Get-ChildItem -LiteralPath $buildDir -Filter 'bingo-backend-*.jar' -File |
    Where-Object { $_.Name -notmatch 'original|sources|javadoc' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw 'Current-source backend JAR not found.' }
$resourceUri = ([Uri]::new(($testResources.TrimEnd('\') + '\'))).AbsoluteUri
$jdbcUrl = "jdbc:mysql://127.0.0.1:3306/$verifyDb"
$env:BINGO_TEST_MYSQL_URL = $jdbcUrl
$env:BINGO_TEST_MYSQL_USERNAME = $verifyUser
$env:BINGO_TEST_MYSQL_PASSWORD = $verifyPassword

$logOut = Join-Path $runDir 'v23-flyway.stdout.log'
$logErr = Join-Path $runDir 'v23-flyway.stderr.log'
foreach ($path in @($logOut, $logErr)) {
    if (Test-Path -LiteralPath $path) { throw "Refusing to overwrite: $path" }
}
$args = @(
    '-jar', "`"$($jar.FullName)`"",
    "--spring.config.additional-location=$resourceUri",
    '--spring.profiles.active=android-test',
    '--server.address=127.0.0.1',
    '--server.port=0',
    '--app.qa.test-runtime-identity-reporting-enabled=false',
    '--app.game.draw-scheduler-enabled=false',
    '--app.wallet.deposit-simulation-enabled=false',
    '--app.wallet.adjustment-simulation-enabled=false',
    '--app.agent.float-simulation-enabled=false',
    '--app.qa.synthetic-financial-tests-enabled=false',
    '--app.qa.android-test-fixtures-bootstrap-enabled=false',
    '--spring.jpa.hibernate.ddl-auto=validate',
    '--spring.flyway.clean-disabled=true',
    '--spring.flyway.validate-on-migrate=true'
)
$process = Start-Process -FilePath 'java' -ArgumentList $args -WorkingDirectory $backendRoot `
    -WindowStyle Hidden -PassThru -RedirectStandardOutput $logOut -RedirectStandardError $logErr

$deadline = (Get-Date).AddMinutes(3)
$applied = $false
while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
    $version = Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb `
        'SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1'
    if ($version.Count -eq 1 -and $version[0] -eq '23') { $applied = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $applied) {
    if (-not $process.HasExited) { Stop-Process -Id $process.Id }
    throw 'V23 was not confirmed successful; preserve this clone and logs, do not retry.'
}

$deadline = (Get-Date).AddMinutes(2)
while (-not $process.HasExited -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 1 }
if (-not $process.HasExited) { Stop-Process -Id $process.Id }
if (-not $process.WaitForExit(30000)) { throw 'Migration process did not stop; do not force-kill or continue.' }
$startupLog = @(
    if (Test-Path -LiteralPath $logOut) { Get-Content -LiteralPath $logOut -Raw }
    if (Test-Path -LiteralPath $logErr) { Get-Content -LiteralPath $logErr -Raw }
) -join "`n"
if ($startupLog -notmatch '(?m)Started .+ in .+ seconds' -or
    $startupLog -match 'APPLICATION FAILED TO START|Application run failed') {
    throw 'Flyway V23 was recorded but application startup was not verified; preserve the clone and logs.'
}
Remove-Item Env:\BINGO_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue
```

If V23 was recorded but Hibernate validation or application startup fails, treat the rehearsal as **FAIL/PARTIAL DDL**, not a pass. Preserve the failed verification database, log files, and original backup; stop and request DBA review.

### 8. Verify V23, room semantics, table counts, and unchanged records

**DATABASE EFFECT: READ ONLY**, except the two uniqueness probes, which use connection-scoped temporary tables and insert only probe rows that disappear when their MySQL connection closes. Never insert into persistent `bingo_rooms` or any financial/business table.

```powershell
$historyAfter = @(Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb $historySql)
if ($historyAfter.Count -ne ($sourceHistory.Count + 1) -or $historyAfter[-1] -notmatch "`t23`t.*`t1$") {
    throw 'Flyway V23 is not the single successful new history row.'
}

$newTables = @('private_games', 'private_game_members', 'player_challenge_claims', 'leaderboard_profiles')
foreach ($table in $newTables) {
    $count = Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb `
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$verifyDb' AND table_name = '$table' AND table_type = 'BASE TABLE'"
    if ($count[0] -ne '1') { throw "V23 table missing: $table" }
    $rows = Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb "SELECT COUNT(*) FROM ``$table``"
    if ($rows[0] -ne '0') { throw "V23 table unexpectedly contains records: $table" }
}

$oldRoomIndex = Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb `
    "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = '$verifyDb' AND table_name = 'bingo_rooms' AND index_name = 'uk_bingo_rooms_name' AND column_name = 'public_name' AND non_unique = 0"
if ($oldRoomIndex[0] -ne '1') { throw 'Public-name unique index is missing.' }
$roomColumns = Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb `
    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = '$verifyDb' AND table_name = 'bingo_rooms' AND column_name IN ('private_room', 'public_name')"
if ($roomColumns[0] -ne '2') { throw 'V23 room columns are missing.' }

$publicProbeSql = @'
CREATE TEMPORARY TABLE qa_v23_public_probe LIKE bingo_rooms;
INSERT INTO qa_v23_public_probe
 (name,ticket_price,currency,timezone,schedule_rule,session_capacity,max_tickets_per_player,
  ticket_sales_close_on_session_start,allow_multiple_winners_per_prize,active,
  configured_patterns_json,configured_prizes_json,created_at,updated_at,version,private_room)
 VALUES
 ('__QA_V23_PUBLIC_DUP__',1.00,'ETB','UTC','',100,10,1,1,0,'[]','[]',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0,FALSE);
INSERT INTO qa_v23_public_probe
 (name,ticket_price,currency,timezone,schedule_rule,session_capacity,max_tickets_per_player,
  ticket_sales_close_on_session_start,allow_multiple_winners_per_prize,active,
  configured_patterns_json,configured_prizes_json,created_at,updated_at,version,private_room)
 VALUES
 ('__QA_V23_PUBLIC_DUP__',1.00,'ETB','UTC','',100,10,1,1,0,'[]','[]',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0,FALSE);
'@
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $verifyPassword
try {
    $publicOutput = & $mysql.Source --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$verifyUser" `
        "--database=$verifyDb" --batch --raw --execute $publicProbeSql 2>&1
    $publicExit = $LASTEXITCODE
} finally {
    if ($null -eq $previousPassword) { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $previousPassword }
}
if ($publicExit -eq 0 -or ($publicOutput -join "`n") -notmatch '(?i)duplicate entry|error 1062') {
    throw 'Duplicate public room names were not rejected by the generated unique key.'
}

$privateProbeSql = @'
CREATE TEMPORARY TABLE qa_v23_private_probe LIKE bingo_rooms;
INSERT INTO qa_v23_private_probe
 (name,ticket_price,currency,timezone,schedule_rule,session_capacity,max_tickets_per_player,
  ticket_sales_close_on_session_start,allow_multiple_winners_per_prize,active,
  configured_patterns_json,configured_prizes_json,created_at,updated_at,version,private_room)
 VALUES
 ('__QA_V23_PRIVATE_DUP__',1.00,'ETB','UTC','',100,10,1,1,0,'[]','[]',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0,TRUE),
 ('__QA_V23_PRIVATE_DUP__',1.00,'ETB','UTC','',100,10,1,1,0,'[]','[]',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0,TRUE);
SELECT COUNT(*) FROM qa_v23_private_probe;
'@
$privateRows = Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb $privateProbeSql
if ($privateRows[-1] -ne '2') { throw 'Duplicate private names did not remain allowed.' }

$copyRoomRowsAfter = @(Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb $roomHashSql)
if (Compare-Object $sourceRoomRows $copyRoomRowsAfter) { throw 'Existing room business fields changed during V23.' }
$postTables = @(Invoke-LocalMysql $verifyUser $verifyPassword $verifyDb `
    'SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = ''BASE TABLE'' ORDER BY table_name')
if ($postTables.Count -ne ($sourceTables.Count + 4)) { throw 'Expected exactly four new V23 base tables.' }
$postCounts = @(Get-ExactTableCounts $verifyUser $verifyPassword $verifyDb $postTables)

$dataAfter = Join-Path $runDir 'post-v23-existing-data.sql'
if (Test-Path -LiteralPath $dataAfter) { throw 'Refusing to overwrite post-migration data fingerprint.' }
$env:MYSQL_PWD = $verifyPassword
try {
    $dumpArgs = @(
        '--protocol=TCP', '--host=127.0.0.1', '--port=3306', "--user=$verifyUser",
        '--single-transaction', '--skip-lock-tables', '--skip-add-locks', '--no-tablespaces',
        '--set-gtid-purged=OFF', '--default-character-set=utf8mb4', '--hex-blob',
        '--no-create-info', '--skip-comments', '--skip-dump-date', '--skip-extended-insert',
        '--complete-insert', '--order-by-primary', "--result-file=$dataAfter",
        $verifyDb
    ) + $comparisonTables
    & $mysqldump.Source @dumpArgs
    if ($LASTEXITCODE -ne 0) { throw 'Could not capture post-V23 data fingerprint.' }
} finally { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
$dataAfterHash = (Get-FileHash -LiteralPath $dataAfter -Algorithm SHA256).Hash
if ($dataBeforeHash -ne $dataAfterHash) { throw 'Existing non-room data differs; includes financial/accounting state.' }
if (-not (Test-Path -LiteralPath (Join-Path $runDir 'v23-flyway.stdout.log'))) {
    throw 'Migration evidence log is missing.'
}
@(
    "verificationDatabase=$verifyDb"
    "flywayVersion=$($historyAfter[-1])"
    "newTables=$($newTables -join ',')"
    "roomRows=$($sourceRoomRows.Count)"
    "roomRowsSha256=$roomRowsHash"
    "nonRoomExistingDataSha256=$dataAfterHash"
    "postMigrationTableCounts=$($postCounts -join ';')"
) | Set-Content -LiteralPath $verificationSummary -Encoding ascii
```

The migration rehearsal passes only if all checks succeed, the new tables are empty, public duplicate names fail with MySQL duplicate-key error 1062, private duplicate names succeed in the temporary probe table, existing room field hashes match, and all other preexisting row data fingerprints match. Inspect and preserve the sanitized evidence bundle; do not infer financial preservation from successful startup alone.

## Source migration remains gated

No authorized command for applying the pending migration to the existing `bingo_qa_test` was found. The current `Start-AndroidTestBackend.ps1` deliberately refuses when the database is V22 and source is V23. Do not repoint the disposable-copy startup in step 7 at `bingo_qa_test`, disable the existing launcher guard, or manually invoke Flyway against the source. After the backup/restore rehearsal passes, the owner/DBA must approve a separately reviewed one-time source-migration command that uses normal Flyway and preserves the existing guards; until that command is approved, leave the source schema at V22 and classify the source migration BLOCKED.

## Partial-DDL failure and recovery

If MySQL reports an error, the Flyway row is absent/unsuccessful, or application startup fails after V23 begins:

1. Stop only the exact migration process started in step 7; do not use `-Force`.
2. Preserve the original backup, checksum manifest, verification schema, migration stdout/stderr, and the exact database identity. Record whether V23 has a history row and which V23 objects exist using read-only queries.
3. Do not run `flyway repair`, `flyway clean`, another startup, manual `ALTER`/`DROP`, or a rollback on either schema. Do not delete the failed verification schema; retain it for DBA analysis.
4. Ask the DBA to review the failing DDL and data. A retry rehearsal must use a **newly named** verification schema restored again from the unchanged, checksummed V22 backup; never reuse a partial clone.
5. If V23 partially succeeds on `bingo_qa_test` in a separately approved future change, stop the backend and clients, preserve the live schema as-is, and classify the state BLOCKED. No in-place cleanup or restore-to-source command is authorized by this runbook. Any restoration of `bingo_qa_test` requires a separate explicit recovery approval and a verified recovery plan.

**Database removal:** This runbook contains no `DROP DATABASE` command. Failed and successful verification copies are retained. Any later removal of a disposable copy requires a separate approval, a fresh identity check of its exact name, and a separately reviewed command; never remove or overwrite `bingo_qa_test` or another existing schema.

## Player restart test prepared, not run

[`module_00_authenticated_localization_restart_test.dart`](../../bingo-app/integration_test/module_00_authenticated_localization_restart_test.dart) checks a clean existing session state before it changes anything, selects Amharic, signs in with the already-approved Player account, verifies the persisted locale and authenticated session, and leaves both intact only when `PRESERVE_PLAYER_SESSION=true`. It does not create a business record or clear app data.

[`module_01_session_restore_test.dart`](../../bingo-app/integration_test/module_01_session_restore_test.dart) now verifies Amharic preference, translated UI, valid persisted token/expiry, and active Player role after the test runner has force-stopped the app. It logs out only after those assertions pass. The force-stop/relaunch step must not use `adb uninstall`, `pm clear`, or any app-data wipe.

Do not run these commands until the source DB is V23, the existing guarded launcher verifies its identity and disabled financial controls, and the backend is healthy:

```powershell
$repoRoot = (Resolve-Path '.').Path
$flutter = 'C:\src\flutter\bin\flutter.bat'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$appRoot = Join-Path $repoRoot 'bingo-app'
$secretFile = Join-Path $appRoot 'e2e-secrets.json'
$runId = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ') + '_' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$evidenceDir = Join-Path $repoRoot 'docs\qa\evidence\user-facing-localization'
$setupLog = Join-Path $evidenceDir "player-amharic-restart-setup-$runId.log"
$restartLog = Join-Path $evidenceDir "player-amharic-restart-assertion-$runId.log"
foreach ($path in @($setupLog, $restartLog)) {
    if (Test-Path -LiteralPath $path) { throw "Refusing to overwrite evidence: $path" }
}
if (-not (Test-Path -LiteralPath $secretFile)) { throw 'Ignored Player E2E secret file is missing.' }

& (Join-Path $repoRoot 'scripts\qa\Start-AndroidTestBackend.ps1') -Port 8082
# Confirm the script's fresh evidence says V23, database=bingo_qa_test,
# scheduler=disabled, financialPostingAndSimulation=disabled; stop if not.

Push-Location $appRoot
try {
    $setupOutput = & $flutter drive `
        --driver=test_driver/module_01_authentication.dart `
        --target=integration_test/module_00_authenticated_localization_restart_test.dart `
        -d emulator-5554 --keep-app-running `
        "--dart-define-from-file=$secretFile" `
        '--dart-define=API_BASE_URL=http://10.0.2.2:8082' `
        '--dart-define=BINGO_PLAYER_PURCHASES_ENABLED=false' `
        '--dart-define=PRESERVE_PLAYER_SESSION=true' 2>&1
    $setupExit = $LASTEXITCODE
    $setupOutput | Set-Content -LiteralPath $setupLog -Encoding utf8
    if ($setupExit -ne 0) { throw 'Amharic sign-in/persistence prerequisite failed; do not force-stop/relaunch.' }

    & $adb -s emulator-5554 shell am force-stop com.bakaroo.bingo_app
    if ($LASTEXITCODE -ne 0) { throw 'Force-stop failed; process-restart assertion was not run.' }

    $restartOutput = & $flutter drive `
        --driver=test_driver/module_01_authentication.dart `
        --target=integration_test/module_01_session_restore_test.dart `
        -d emulator-5554 --keep-app-running `
        "--dart-define-from-file=$secretFile" `
        '--dart-define=API_BASE_URL=http://10.0.2.2:8082' `
        '--dart-define=PLAYER_EXPECT_LOCALE=am' 2>&1
    $restartExit = $LASTEXITCODE
    $restartOutput | Set-Content -LiteralPath $restartLog -Encoding utf8
    if ($restartExit -ne 0) { throw 'Amharic/session post-restart assertion failed.' }
} finally {
    Pop-Location
}
```

These are proposed commands only; none were run during preparation. The Android package name is `com.bakaroo.bingo_app`. `flutter drive` uses the existing integration driver and `adb force-stop`; it does not uninstall the app or clear application data. The real-backend 403 and financial-uncertainty scenarios remain separate, documented validation gaps.

## Approval request

Please approve these as **three gated operations**:

1. Create a checksummed full logical backup of only local `127.0.0.1:3306/bingo_qa_test` in an approved encrypted location, with task-owned writers stopped.
2. Provision and restore that backup to one fresh, uniquely named disposable verification schema; compare schema, Flyway history, exact table counts, room rows, and existing financial/non-room data before testing V23.
3. **Only after the restore rehearsal passes**, approve a DBA-reviewed one-time command to apply V23 to the existing `bingo_qa_test` through normal Spring Boot/Flyway, without weakening or bypassing the current E2E startup guard. The exact source command remains BLOCKED until that approved procedure is supplied.

The third operation is explicitly conditional on a successful rehearsal. This request does not authorize grant changes, a root connection to the verification schema, schema reset, record deletion, financial posting, commit, push, or deployment.
