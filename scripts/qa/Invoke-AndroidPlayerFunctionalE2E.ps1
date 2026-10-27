[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
if ($identity.Name -match 'CodexSandboxOffline') {
    throw 'BLOCKED: run from normal VS Code PowerShell under EQUAR-DELL-XPS1\16414; no Android or database test was started.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$statusPath = Join-Path $repoRoot 'docs\qa\evidence\module-06a5\backend-identity-preflight.json'
$envPath = Join-Path $repoRoot 'bingo-backend\.env.test'
$evidenceDir = Join-Path $repoRoot 'docs\qa\evidence\module-06a9'
$appRoot = Join-Path $repoRoot 'bingo-app'
$policyModule = Join-Path $PSScriptRoot 'AndroidTestBackendPolicy.psm1'
$sdk = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$flutter = 'C:\src\flutter\bin\flutter.bat'
$mysql = $null
$runId = 'QA_M06A9_' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff')
$manifestPath = Join-Path $evidenceDir "$runId.json"
$beforePath = Join-Path $evidenceDir "$runId-database-before.json"
$afterPath = Join-Path $evidenceDir "$runId-database-after.json"
$flutterLogPath = Join-Path $evidenceDir "$runId-flutter-integration.log"
$secretPath = $null
$secretDir = $null
$processExit = 1
$snapshotBefore = $null
$accountBefore = $null
$accountAfter = $null
$testOutput = @()
$backend = $null
$securePin = $null
$secureAdminPassword = $null
$settings = $null
$mysqlUser = $null
$mysqlPassword = $null
$playerId = 0L
$createdSessionId = 0L
$createdTicketId = 0L
$ticketCode = $null
$fixturePath = Join-Path $evidenceDir "$runId-fixture.json"
$adminToken = $null
$pin = $null
$adminPassword = $null
$startedAt = (Get-Date).ToString('o')
Import-Module $policyModule -Force

function Read-EnvFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw 'The approved local .env.test file is missing.' }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*([A-Za-z0-9_]+)=(.*)\s*$') {
            $values[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
        }
    }
    return $values
}

function Invoke-QaMysql([string]$Sql) {
    $env:MYSQL_PWD = $mysqlPassword
    try {
        $result = $Sql | & $mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user=$mysqlUser --database=bingo_qa_test --batch --raw --skip-column-names
        if ($LASTEXITCODE -ne 0) { throw 'Read-only MySQL query failed; SQL output was suppressed.' }
        return ,@($result | ForEach-Object { [string]$_ })
    } finally { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
}

function Invoke-QaFixtureSql([string]$Sql) {
    $env:MYSQL_PWD = $mysqlPassword
    try {
        $result = $Sql | & $mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user=$mysqlUser --database=bingo_qa_test --batch --raw --skip-column-names
        if ($LASTEXITCODE -ne 0) { throw 'Approved synthetic fixture utility failed; SQL text was suppressed.' }
        return ,@($result | ForEach-Object { [string]$_ })
    } finally { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
}

function Get-ReadOnlySnapshot([long]$ExcludeWalletOwnerId = 0) {
    $tables = Invoke-QaMysql "SELECT table_name FROM information_schema.tables WHERE table_schema='bingo_qa_test' AND table_type='BASE TABLE' ORDER BY table_name;"
    $counts = @()
    foreach ($table in $tables) {
        if ($table -notmatch '^[A-Za-z0-9_]+$') { throw 'Unexpected table name in isolated schema metadata.' }
        $count = Invoke-QaMysql "SELECT COUNT(*) FROM ``$table``;"
        $counts += [ordered]@{ table = $table; rowCount = [long]$count[0] }
    }
    $sessionRows = Invoke-QaMysql 'SELECT CONCAT_WS(''|'',id,room_id,session_name,state,ticket_price_snapshot,currency_snapshot) FROM bingo_sessions ORDER BY id;'
    $roomRows = Invoke-QaMysql 'SELECT CONCAT_WS(''|'',id,active,ticket_price,currency) FROM bingo_rooms ORDER BY id;'
    if ($ExcludeWalletOwnerId -gt 0) {
        $walletRows = Invoke-QaMysql "SELECT CONCAT_WS('|',id,owner_id,currency,available_balance,promotional_balance,version) FROM wallets WHERE owner_id<>$ExcludeWalletOwnerId ORDER BY id;"
    } else {
        $walletRows = Invoke-QaMysql "SELECT CONCAT_WS('|',id,owner_id,currency,available_balance,promotional_balance,version) FROM wallets ORDER BY id;"
    }
    $ticketRows = Invoke-QaMysql "SELECT CONCAT_WS('|',id,session_id,owner_id,public_ticket_code,operation_id,grid_json,IF(wallet_funding_snapshot_json IS NULL,'NO_FUNDING_SNAPSHOT','HAS_FUNDING_SNAPSHOT')) FROM bingo_tickets ORDER BY id;"
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $walletHash = [Convert]::ToHexString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($walletRows -join "`n"))))
    } finally { $sha.Dispose() }
    return [ordered]@{
        capturedAt = (Get-Date).ToString('o')
        target = '127.0.0.1:3306/bingo_qa_test'
        tableCounts = $counts
        roomStates = $roomRows
        sessionStates = $sessionRows
        ticketRows = $ticketRows
        walletBalancesSha256 = $walletHash
    }
}

function Convert-SecureStringToPlainText([Security.SecureString]$Value) {
    $secretPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPtr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPtr) }
}

function Get-AccountStatus([string]$NormalizedPhone) {
    # Output contains only aggregate status/role flags; no phone, ID, or hash.
    $rows = Invoke-QaMysql "SELECT CONCAT(COUNT(*),'|',COALESCE(MAX(status),'MISSING'),'|',COALESCE((SELECT COUNT(*) FROM user_roles r JOIN users u2 ON u2.id=r.user_id WHERE u2.normalized_phone='$NormalizedPhone' AND r.role='PLAYER'),0)) FROM users WHERE normalized_phone='$NormalizedPhone';"
    if ($rows.Count -ne 1) { throw 'Player account status query returned an unexpected shape.' }
    $parts = $rows[0].Split('|')
    if ($parts.Count -ne 3) { throw 'Player account status query was not parseable.' }
    return [ordered]@{ matchingAccounts = [int]$parts[0]; status = $parts[1]; hasPlayerRole = ([int]$parts[2] -gt 0) }
}

function Get-AdminStatus([string]$Email) {
    $escapedEmail = $Email.Replace("'", "''")
    $rows = Invoke-QaMysql "SELECT CONCAT(COUNT(DISTINCT u.id),'|',COALESCE(MAX(u.status),'MISSING'),'|',COALESCE(SUM(r.role='ADMIN'),0)) FROM users u LEFT JOIN user_roles r ON r.user_id=u.id WHERE LOWER(u.email)=LOWER('$escapedEmail');"
    if ($rows.Count -ne 1) { throw 'Admin account status query returned an unexpected shape.' }
    $parts = $rows[0].Split('|')
    if ($parts.Count -ne 3) { throw 'Admin account status query was not parseable.' }
    return [ordered]@{ matchingAccounts = [int]$parts[0]; status = $parts[1]; hasAdminRole = ([int]$parts[2] -gt 0) }
}

try {
    if (-not (Test-Path -LiteralPath $statusPath)) { throw 'BLOCKED: isolated backend identity evidence is missing.' }
    $backend = Get-Content -LiteralPath $statusPath -Raw | ConvertFrom-Json
    if ($backend.profile -ne 'android-test' -or $backend.database -ne 'bingo_qa_test' -or
        $backend.backendUrl -ne 'http://127.0.0.1:8082' -or $backend.emulatorBackendUrl -ne 'http://10.0.2.2:8082' -or
        $backend.jdbcHost -ne '127.0.0.1' -or $backend.jdbcPort -ne 3306 -or $backend.drawScheduler -ne 'disabled' -or
        $backend.financialPostingAndSimulation -ne 'disabled') {
        throw 'BLOCKED: saved backend identity or no-financial/no-scheduler gates do not match the approved target.'
    }
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8082/actuator/health' -TimeoutSec 5
    if ($health.status -ne 'UP' -or $health.components.db.status -ne 'UP') { throw 'BLOCKED: isolated backend/database health is not UP.' }
    $listener = netstat -ano -p tcp | Select-String ':8082\s+0\.0\.0\.0:0\s+LISTENING\s+(\d+)' | Select-Object -First 1
    if (-not $listener -or [int]$listener.Matches[0].Groups[1].Value -ne [int]$backend.processId) {
        throw 'BLOCKED: port 8082 listener PID does not match saved isolated backend PID.'
    }
    $backendProcess = Get-Process -Id ([int]$backend.processId) -ErrorAction Stop
    if ($backendProcess.ProcessName -ne 'java') { throw 'BLOCKED: saved backend PID is not a Java process.' }
    if (-not (Test-Path -LiteralPath $adb) -or -not (Test-Path -LiteralPath $flutter)) { throw 'BLOCKED: Android SDK or Flutter executable is missing.' }
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    $env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
    $env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
    $env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
    $env:Path = "$sdk\platform-tools;$sdk\emulator;C:\src\flutter\bin;$env:Path"
    $adbDevices = & $adb devices -l 2>$null
    if ($LASTEXITCODE -ne 0 -or ($adbDevices -join "`n") -notmatch '(?m)^emulator-5554\s+device\b') { throw 'BLOCKED: emulator-5554 is not available.' }
    $installedPackages = & $adb -s emulator-5554 shell pm list packages -3 2>$null
    if ($LASTEXITCODE -ne 0 -or ($installedPackages -join "`n") -notmatch '(?m)^package:com\.example\.bingo_app$') {
        throw 'BLOCKED: the Bingo Player package is not installed on emulator-5554.'
    }
    $flutterDevices = & $flutter devices 2>$null
    if ($LASTEXITCODE -ne 0 -or ($flutterDevices -join "`n") -notmatch 'emulator-5554') { throw 'BLOCKED: Flutter does not detect emulator-5554.' }

    $settings = Read-EnvFile $envPath
    $mysqlUser = [string]$settings['BINGO_TEST_MYSQL_USERNAME']
    $mysqlPassword = [string]$settings['BINGO_TEST_MYSQL_PASSWORD']
    $mysql = (Get-Command mysql.exe -ErrorAction Stop).Source
    $null = Assert-AgentAndroidE2EEnvironment `
        -EnvironmentName 'android-test' `
        -BackendUrl ([string]$backend.backendUrl) `
        -EmulatorBackendUrl ([string]$backend.emulatorBackendUrl) `
        -JdbcUrl ([string]$settings['BINGO_TEST_MYSQL_URL']) `
        -MysqlUsername $mysqlUser `
        -MysqlPassword $mysqlPassword

    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$backend.processId)" -ErrorAction Stop
    $jarMatch = [regex]::Match([string]$process.CommandLine, '-jar\s+"?(?<jar>[^"\s]+\.jar)"?')
    if (-not $jarMatch.Success -or -not (Test-Path -LiteralPath $jarMatch.Groups['jar'].Value)) {
        throw 'BLOCKED: running backend JAR path could not be verified.'
    }
    $jarPath = (Resolve-Path -LiteralPath $jarMatch.Groups['jar'].Value).Path
    $backendTarget = (Resolve-Path -LiteralPath (Join-Path $repoRoot 'bingo-backend\target')).Path
    if (-not $jarPath.StartsWith($backendTarget, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'BLOCKED: running backend JAR is outside the repository backend target directory.'
    }
    $actualJarHash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
    $resourceUri = ([Uri]::new(((Join-Path $repoRoot 'bingo-backend\src\test\resources').TrimEnd('\') + '\'))).AbsoluteUri
    if ([string]$process.CommandLine -notlike "*--spring.config.additional-location=$resourceUri*" -or
        [string]$process.CommandLine -notlike '*--spring.profiles.active=android-test*') {
        throw 'BLOCKED: running backend command line does not identify the isolated test configuration and profile.'
    }
    $git = Get-Command git.exe -ErrorAction Stop
    & $git.Source -C $repoRoot diff --quiet ([string]$backend.sourceCommit) HEAD -- bingo-backend/src/main bingo-backend/pom.xml
    $sourceMatches = $LASTEXITCODE -eq 0
    $localSourceChanges = & $git.Source -C $repoRoot status --short -- bingo-backend/src/main bingo-backend/pom.xml
    $sourceMatches = $sourceMatches -and -not $localSourceChanges

    $runtimeLogDir = Join-Path $env:TEMP 'bingo-platform-module-06a5'
    $runtimeIdentityLine = @(
        Select-String -Path @(
            (Join-Path $runtimeLogDir 'backend.stdout.log'),
            (Join-Path $runtimeLogDir 'backend.stderr.log')
        ) -Pattern ('QA_TEST_RUNTIME_IDENTITY pid=' + [int]$backend.processId + ' ') -ErrorAction SilentlyContinue
    ) | Select-Object -Last 1
    $runtimeGuardsLine = @(
        Select-String -Path @(
            (Join-Path $runtimeLogDir 'backend.stdout.log'),
            (Join-Path $runtimeLogDir 'backend.stderr.log')
        ) -Pattern 'QA_TEST_RUNTIME_GUARDS .*' -ErrorAction SilentlyContinue
    ) | Select-Object -Last 1
    $runtime = Assert-AndroidTestBackendRuntimeIdentity `
        -BackendIdentity $backend `
        -RuntimeIdentityLine ([string]$runtimeIdentityLine.Line) `
        -RuntimeGuardsLine ([string]$runtimeGuardsLine.Line) `
        -ListenerProcessId ([int]$listener.Matches[0].Groups[1].Value) `
        -BackendHealth ([string]$health.status) `
        -DatabaseHealth ([string]$health.components.db.status) `
        -BackendJarMatches ($actualJarHash -eq [string]$backend.artifactSha256) `
        -BackendSourceMatches $sourceMatches

    $dbIdentityRow = Invoke-QaMysql 'SELECT CONCAT(DATABASE(),CHAR(9),CURRENT_USER(),CHAR(9),@@hostname,CHAR(9),@@port);'
    $mysqlIdentity = Assert-AndroidTestMysqlIdentity -IdentityRow $dbIdentityRow[0] -ConfiguredUsername $mysqlUser
    if ($mysqlIdentity.Database -ne $runtime.Database -or $mysqlIdentity.Port -ne $runtime.MysqlPort) {
        throw 'BLOCKED: direct MySQL identity does not match the running backend runtime identity.'
    }
    $dbIdentity = @($mysqlIdentity.Database, $mysqlIdentity.CurrentUser, [string]$mysqlIdentity.Port)

    $phone = Read-Host 'Existing authorized QA player phone (not echoed in evidence)'
    $securePin = Read-Host 'Existing QA player six-digit PIN' -AsSecureString
    if ($phone -notmatch '^\+?[0-9\s().-]+$') { throw 'BLOCKED: player phone format is invalid.' }
    $phoneDigits = $phone.Trim() -replace '[\s\-().]', ''
    if ($phoneDigits.StartsWith('+')) { $phoneDigits = $phoneDigits.Substring(1) }
    if ($phoneDigits.Length -eq 12 -and $phoneDigits.StartsWith('251')) { $normalizedPhone = $phoneDigits }
    elseif ($phoneDigits.Length -eq 10 -and $phoneDigits.StartsWith('0')) { $normalizedPhone = '251' + $phoneDigits.Substring(1) }
    elseif ($phoneDigits.Length -eq 9) { $normalizedPhone = '251' + $phoneDigits }
    else { throw 'BLOCKED: player phone is not a supported Ethiopian mobile format.' }
    if ($normalizedPhone -notmatch '^251[79][0-9]{8}$') { throw 'BLOCKED: player phone is not a supported Ethiopian mobile format.' }

    $accountBefore = Get-AccountStatus $normalizedPhone
    if ($accountBefore.matchingAccounts -ne 1 -or $accountBefore.status -ne 'ACTIVE' -or -not $accountBefore.hasPlayerRole) {
        throw 'BLOCKED: provided QA account is not exactly one ACTIVE PLAYER; no login was attempted.'
    }
    $playerIdRows = Invoke-QaMysql "SELECT id FROM users WHERE normalized_phone='$normalizedPhone' AND status='ACTIVE';"
    if ($playerIdRows.Count -ne 1 -or $playerIdRows[0] -notmatch '^\d+$') { throw 'BLOCKED: active QA player identity could not be resolved safely.' }
    $playerId = [long]$playerIdRows[0]

    $adminEmail = Read-Host 'Existing authorized QA administrator email'
    $secureAdminPassword = Read-Host 'Existing QA administrator password' -AsSecureString
    if ($adminEmail -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') { throw 'BLOCKED: administrator email format is invalid.' }
    $adminBefore = Get-AdminStatus $adminEmail
    if ($adminBefore.matchingAccounts -ne 1 -or $adminBefore.status -ne 'ACTIVE' -or -not $adminBefore.hasAdminRole) {
        throw 'BLOCKED: provided account is not exactly one ACTIVE ADMIN; no session was created.'
    }
    $null = New-Item -ItemType Directory -Path $evidenceDir -Force
    $snapshotBefore = Get-ReadOnlySnapshot
    $snapshotBefore | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $beforePath -Encoding utf8

    $pin = Convert-SecureStringToPlainText $securePin
    if ($pin -notmatch '^[0-9]{6}$') { throw 'BLOCKED: supplied QA PIN must contain six digits.' }
    $adminPassword = Convert-SecureStringToPlainText $secureAdminPassword
    if ($adminPassword.Length -lt 8 -or $adminPassword.Length -gt 72) { throw 'BLOCKED: supplied QA administrator password length is invalid.' }

    # Verify both supplied credentials without writing them to logs or evidence.
    $playerLogin = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8082/api/v1/auth/login' -ContentType 'application/json' -Body (@{ phoneNumber = $phone; password = $pin } | ConvertTo-Json -Compress) -TimeoutSec 15
    if (-not $playerLogin.accessToken -or $playerLogin.user.status -ne 'ACTIVE' -or $playerLogin.user.roles -notcontains 'PLAYER') { throw 'BLOCKED: player credential preflight did not return an active PLAYER.' }
    $playerLogin = $null
    $adminLogin = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8082/api/v1/admin/login' -ContentType 'application/json' -Body (@{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json -Compress) -TimeoutSec 15
    if (-not $adminLogin.accessToken) { throw 'BLOCKED: administrator credential preflight did not return an access token.' }
    $adminToken = [string]$adminLogin.accessToken
    $adminLogin = $null

    $activeRooms = @($snapshotBefore.roomStates | Where-Object { $_ -match '^\d+\|1\|' } | Select-Object -First 1)
    if ($activeRooms.Count -ne 1) { throw 'BLOCKED: no active QA room is available for a test-owned session.' }
    $activeRoomParts = $activeRooms[0].Split('|')
    if ($activeRoomParts.Count -lt 4) { throw 'BLOCKED: active QA room metadata is incomplete.' }
    $roomId = [long]$activeRoomParts[0]
    $roomPrice = [decimal]$activeRoomParts[2]
    $roomCurrency = $activeRoomParts[3]
    $sessionName = "QA_E2E_06A9_$($runId.Substring(10))"
    $scheduledStart = [DateTime]::UtcNow.AddDays(2).ToString('o')
    $sessionRequest = @{ sessionName = $sessionName; scheduledStartAt = $scheduledStart; state = 'OPEN'; ticketPrice = $roomPrice; currency = $roomCurrency }
    $sessionResponse = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8082/api/v1/admin/rooms/$roomId/sessions" -Headers @{ Authorization = "Bearer $adminToken" } -ContentType 'application/json' -Body ($sessionRequest | ConvertTo-Json -Compress) -TimeoutSec 20
    if ($sessionResponse.state -ne 'OPEN' -or [long]$sessionResponse.roomId -ne $roomId -or [decimal]$sessionResponse.ticketPrice -ne $roomPrice -or $sessionResponse.currency -ne $roomCurrency) {
        throw 'FAIL: Admin API did not return the expected isolated OPEN session snapshot.'
    }
    $createdSessionId = [long]$sessionResponse.id
    if ($createdSessionId -le 0) { throw 'FAIL: Admin API returned no session identifier.' }
    $sessionResponse = $null
    $adminToken = $null

    # This row is a UI-only card display fixture. It has no wallet funding snapshot,
    # wallet debit, purchase API call, claim, or financial ledger entry.
    $ticketCode = 'QA06A9' + [guid]::NewGuid().ToString('N').Substring(0, 16).ToUpperInvariant()
    $operationId = [guid]::NewGuid().ToString()
    $gridJson = '[[1,16,31,46,61],[2,17,32,47,62],[3,18,0,48,63],[4,19,34,49,64],[5,20,35,50,65]]'
    $fixtureSql = "START TRANSACTION; INSERT INTO bingo_tickets(session_id,owner_id,public_ticket_code,operation_id,grid_json,purchased_at,wallet_funding_snapshot_json) VALUES($createdSessionId,$playerId,'$ticketCode','$operationId','$gridJson',UTC_TIMESTAMP(6),NULL); SELECT LAST_INSERT_ID(); COMMIT;"
    $ticketIdResult = Invoke-QaFixtureSql $fixtureSql
    if ($ticketIdResult.Count -lt 1 -or $ticketIdResult[-1] -notmatch '^\d+$') { throw 'FAIL: controlled ticket display fixture insert returned no ticket identifier.' }
    $createdTicketId = [long]$ticketIdResult[-1]
    $fixtureManifest = [ordered]@{
        runId = $runId; fixtureType = 'nonfinancial-card-display-only'; createdAt = (Get-Date).ToString('o')
        database = 'bingo_qa_test'; roomId = $roomId; sessionId = $createdSessionId; sessionName = $sessionName
        playerDatabaseId = $playerId; ticketId = $createdTicketId; publicTicketCode = $ticketCode; operationId = $operationId
        cardGrid = $gridJson; walletFundingSnapshot = $null; walletDebit = $false; financialLedger = 'unchanged'
        note = 'Test-owned records are retained. No purchase API, wallet mutation, claim, settlement, scheduler, or external transfer was invoked.'
    }
    $fixtureManifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $fixturePath -Encoding utf8
    $registryPath = Join-Path $repoRoot 'docs\qa\test-data-registry.md'
    if (-not (Test-Path -LiteralPath $registryPath)) { throw 'BLOCKED: persistent QA test-data registry is missing.' }
    if (-not (Select-String -LiteralPath $registryPath -SimpleMatch $runId -Quiet)) {
        Add-Content -LiteralPath $registryPath -Value "`n- Module 06A-9 ${runId}: active room #$roomId, OPEN session #$createdSessionId ($sessionName), player database ID #$playerId, card-display ticket #$createdTicketId ($ticketCode). Nonfinancial fixture; no wallet debit or ledger entry. Retain all records. See [run fixture evidence](evidence/module-06a9/${runId}-fixture.json)."
    }

    $secretDir = Join-Path $env:TEMP $runId
    $null = New-Item -ItemType Directory -Path $secretDir -Force
    $acl = Get-Acl -LiteralPath $secretDir
    $acl.SetAccessRuleProtection($true, $false)
    $acl.Access | ForEach-Object { $null = $acl.RemoveAccessRule($_) }
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new($identity.User, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $secretDir -AclObject $acl
    $secretPath = Join-Path $secretDir 'e2e-secrets.json'
    $secretJson = @{ BINGO_E2E_PRIMARY_PHONE = $phone; BINGO_E2E_PRIMARY_PIN = $pin; BINGO_E2E_TICKET_CODE = $ticketCode; BINGO_E2E_SESSION_ID = [string]$createdSessionId } | ConvertTo-Json -Compress
    [System.IO.File]::WriteAllText($secretPath, $secretJson, [System.Text.UTF8Encoding]::new($false))
    $pin = $null
    $securePin.Dispose()

    $null = New-Item -ItemType Directory -Path $evidenceDir -Force
    $env:API_BASE_URL = 'http://10.0.2.2:8082'
    Push-Location $appRoot
    try {
        & $flutter test integration_test/module_06a9_player_e2e_test.dart -d emulator-5554 `
            "--dart-define-from-file=$secretPath" `
            '--dart-define=API_BASE_URL=http://10.0.2.2:8082' `
            '--dart-define=BINGO_PLAYER_PURCHASES_ENABLED=false' 2>&1 |
            ForEach-Object {
                $line = [string]$_
                foreach ($sensitive in @($phone, $phoneDigits, $normalizedPhone, $adminEmail, $pin, $adminPassword)) {
                    if (-not [string]::IsNullOrEmpty($sensitive)) { $line = $line.Replace($sensitive, '[REDACTED]') }
                }
                $line
            } | Tee-Object -FilePath $flutterLogPath
        $processExit = $LASTEXITCODE
        $testOutput = @(Get-Content -LiteralPath $flutterLogPath -ErrorAction SilentlyContinue)
    } finally { Pop-Location }

    $snapshotAfter = Get-ReadOnlySnapshot
    $snapshotAfter | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $afterPath -Encoding utf8
    $accountAfter = Get-AccountStatus $normalizedPhone
    $beforeCounts = @{}; foreach ($item in $snapshotBefore.tableCounts) { $beforeCounts[$item.table] = $item.rowCount }
    $afterCounts = @{}; foreach ($item in $snapshotAfter.tableCounts) { $afterCounts[$item.table] = $item.rowCount }
    $businessTables = @('bingo_rooms','bingo_sessions','bingo_tickets','bingo_draw_events','bingo_win_claims','bingo_prize_entitlements','wallets','wallet_transactions','deposit_accounting_journals','deposit_accounting_journal_lines','wallet_adjustment_accounting_journals','wallet_adjustment_accounting_journal_lines','float_accounting_journals','float_accounting_journal_lines')
    $businessTableCountsUnchanged = $true
    foreach ($table in $businessTables) {
        if ($beforeCounts.ContainsKey($table) -or $afterCounts.ContainsKey($table)) {
            $expectedDelta = if ($table -eq 'bingo_sessions' -and $createdSessionId -gt 0) { 1 } elseif ($table -eq 'bingo_tickets' -and $createdTicketId -gt 0) { 1 } else { 0 }
            if (($afterCounts[$table] - $beforeCounts[$table]) -ne $expectedDelta) { $businessTableCountsUnchanged = $false }
        }
    }
    $oldSessionsBefore = @($snapshotBefore.sessionStates)
    $oldSessionsAfter = @($snapshotAfter.sessionStates | Where-Object { $_ -notmatch "^$createdSessionId\|" })
    $oldTicketsBefore = @($snapshotBefore.ticketRows)
    $oldTicketsAfter = @($snapshotAfter.ticketRows | Where-Object { $_ -notmatch "^$createdTicketId\|" })
    $newSessionRow = @($snapshotAfter.sessionStates | Where-Object { $_ -match "^$createdSessionId\|" }) | Select-Object -First 1
    $newTicketRow = @($snapshotAfter.ticketRows | Where-Object { $_ -match "^$createdTicketId\|" }) | Select-Object -First 1
    $checks = [ordered]@{
        backendHealthUp = $true
        backendPidMatchesListener = $true
        isolatedDatabaseIdentity = $true
        activePlayerConfirmedBefore = ($accountBefore.matchingAccounts -eq 1 -and $accountBefore.status -eq 'ACTIVE' -and $accountBefore.hasPlayerRole)
        playerStatusUnchanged = ($accountBefore.status -eq $accountAfter.status -and $accountBefore.matchingAccounts -eq $accountAfter.matchingAccounts -and $accountBefore.hasPlayerRole -eq $accountAfter.hasPlayerRole)
        businessTableCountsUnchanged = $businessTableCountsUnchanged
        roomStatesUnchanged = (($snapshotBefore.roomStates -join "`n") -eq ($snapshotAfter.roomStates -join "`n"))
        retainedSessionStatesUnchanged = (($oldSessionsBefore -join "`n") -eq ($oldSessionsAfter -join "`n"))
        retainedTicketRowsUnchanged = (($oldTicketsBefore -join "`n") -eq ($oldTicketsAfter -join "`n"))
        testSessionReconciled = ($newSessionRow -and $newSessionRow -match "^$createdSessionId\|$roomId\|$([regex]::Escape($sessionName))\|OPEN\|$([regex]::Escape([string]$roomPrice))\|$roomCurrency$")
        testTicketReconciled = ($newTicketRow -and $newTicketRow -match "^$createdTicketId\|$createdSessionId\|$playerId\|$ticketCode\|[0-9a-fA-F-]{36}\|$([regex]::Escape($gridJson))\|NO_FUNDING_SNAPSHOT$")
        walletBalancesUnchanged = ($snapshotBefore.walletBalancesSha256 -eq $snapshotAfter.walletBalancesSha256)
        integrationTestPassed = ($processExit -eq 0)
        displayedRoomPriceReconciled = $false
        displayedOpenSessionReconciled = $false
        openSessionDataBlockReported = (($testOutput -join "`n") -match 'BLOCKED_BY_DATA: no existing OPEN session')
    }
    foreach ($line in $testOutput) {
        if ($line -match 'E2E_UI_ROOM\|id=(\d+)\|Price:\s*([0-9]+(?:\.[0-9]+)?)\s+([A-Z]{3})') {
            $uiRoomId = $matches[1]; $uiPrice = [decimal]$matches[2]; $uiCurrency = $matches[3]
            $dbRoom = $snapshotBefore.roomStates | Where-Object { $_ -match "^$uiRoomId\|" } | Select-Object -First 1
            if ($dbRoom) { $dbParts = $dbRoom.Split('|'); if ([decimal]$dbParts[2] -eq $uiPrice -and $dbParts[3] -eq $uiCurrency) { $checks.displayedRoomPriceReconciled = $true } }
        }
        if ($line -match 'E2E_UI_OPEN_SESSION\|id=(\d+)\|status=OPEN\|Price:\s*([0-9]+(?:\.[0-9]+)?)\s+([A-Z]{3})') {
            $uiSessionId = $matches[1]; $uiPrice = [decimal]$matches[2]; $uiCurrency = $matches[3]
            $dbSession = $snapshotAfter.sessionStates | Where-Object { $_ -match "^$uiSessionId\|" } | Select-Object -First 1
            if ($dbSession) { $dbParts = $dbSession.Split('|'); if ($dbParts[3] -eq 'OPEN' -and [decimal]$dbParts[4] -eq $uiPrice -and $dbParts[5] -eq $uiCurrency) { $checks.displayedOpenSessionReconciled = $true } }
        }
    }
    $status = if ($processExit -eq 0 -and $checks.businessTableCountsUnchanged -and $checks.retainedSessionStatesUnchanged -and $checks.retainedTicketRowsUnchanged -and $checks.testSessionReconciled -and $checks.testTicketReconciled -and $checks.walletBalancesUnchanged -and $checks.playerStatusUnchanged -and $checks.displayedRoomPriceReconciled -and $checks.displayedOpenSessionReconciled) { 'PASS' } else { 'FAIL' }
    $manifest = [ordered]@{
        runId = $runId
        startedAt = $startedAt
        completedAt = (Get-Date).ToString('o')
        overall = $status
        target = [ordered]@{ backend = 'http://127.0.0.1:8082'; emulatorBackend = 'http://10.0.2.2:8082'; profile = $backend.profile; database = $dbIdentity[0]; mysqlIdentity = $dbIdentity[1]; mysqlPort = [int]$dbIdentity[2]; backendPid = [int]$backend.processId; emulator = 'emulator-5554'; purchaseFlag = $false }
        playerAccount = $accountBefore
        checks = $checks
        flutterExitCode = $processExit
        artifacts = [ordered]@{ before = $beforePath; after = $afterPath; flutterLog = $flutterLogPath; fixture = $fixturePath }
        note = 'No credential, phone, token, or account identifier is written to this manifest.'
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    Write-Host "Module 06A-9 $status. Evidence manifest: $manifestPath"
    if ($status -ne 'PASS') { exit 1 }
} catch {
    $reason = $_.Exception.Message
    $classification = if ($reason -match '^BLOCKED:') { 'BLOCKED' } else { 'FAIL' }
    $failureManifest = [ordered]@{
        runId = $runId
        startedAt = $startedAt
        completedAt = (Get-Date).ToString('o')
        overall = $classification
        reason = $reason
        backendPid = if ($backend) { $backend.processId } else { $null }
        createdSessionId = if ($createdSessionId -gt 0) { $createdSessionId } else { $null }
        createdTicketId = if ($createdTicketId -gt 0) { $createdTicketId } else { $null }
        artifacts = [ordered]@{ before = if (Test-Path $beforePath) { $beforePath } else { $null }; after = if (Test-Path $afterPath) { $afterPath } else { $null }; flutterLog = if (Test-Path $flutterLogPath) { $flutterLogPath } else { $null }; fixture = if (Test-Path $fixturePath) { $fixturePath } else { $null } }
    }
    $null = New-Item -ItemType Directory -Path $evidenceDir -Force
    $failureManifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    Write-Error "${classification}: $reason`nEvidence manifest: $manifestPath"
    exit 1
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    Remove-Item Env:\API_BASE_URL -ErrorAction SilentlyContinue
    if ($secretDir -and (Test-Path -LiteralPath $secretDir)) { Remove-Item -LiteralPath $secretDir -Recurse -Force }
    if ($securePin) { $securePin.Dispose() }
    if ($secureAdminPassword) { $secureAdminPassword.Dispose() }
    $pin = $null
    $adminPassword = $null
    $adminToken = $null
    $phone = $null
    $normalizedPhone = $null
    $settings = $null
    $mysqlPassword = $null
    $mysqlUser = $null
}
