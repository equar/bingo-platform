[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
if ($identity.Name -match 'CodexSandboxOffline') {
    throw 'Run this scenario from the normal VS Code PowerShell terminal. No E2E was started.'
}
if ([string]::IsNullOrWhiteSpace($env:BINGO_E2E_ADMIN_EMAIL) -or [string]::IsNullOrWhiteSpace($env:BINGO_E2E_ADMIN_PASSWORD)) {
    throw 'Set the approved local Admin E2E credentials in this PowerShell process. Values will not be printed or written to files.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Import-Module (Join-Path $PSScriptRoot 'AndroidTestBackendPolicy.psm1') -Force
$statusPath = Join-Path $repoRoot 'docs\qa\evidence\module-06a5\backend-identity-preflight.json'
$backendRoot = Join-Path $repoRoot 'bingo-backend'
$adminRoot = Join-Path $repoRoot 'bingo-admin'
$envFile = Join-Path $backendRoot '.env.test'
$evidenceDir = Join-Path $repoRoot 'docs\qa\evidence\module-06a5'
$tempLogDir = Join-Path $env:TEMP 'bingo-platform-module-06a5'
if (-not (Test-Path -LiteralPath $statusPath)) { throw 'Start and verify the isolated android-test backend first.' }
$backendStatus = Get-Content -LiteralPath $statusPath -Raw | ConvertFrom-Json
if ($backendStatus.profile -ne 'android-test' -or $backendStatus.database -ne 'bingo_qa_test' -or
    $backendStatus.backendUrl -ne 'http://127.0.0.1:8082' -or $backendStatus.drawScheduler -ne 'disabled' -or
    $backendStatus.financialPostingAndSimulation -ne 'disabled') {
    throw 'Refusing E2E: the verified backend must be local android-test on bingo_qa_test with scheduler and financial paths disabled.'
}
$health = Invoke-RestMethod -Uri "$($backendStatus.backendUrl)/actuator/health" -TimeoutSec 5
if ($health.status -ne 'UP') { throw 'Isolated backend health is not UP.' }
if (-not (Test-Path -LiteralPath (Join-Path $adminRoot 'node_modules\.bin\playwright.cmd'))) {
    throw 'Local Playwright dependencies are missing; no package installation was attempted.'
}
if (-not (Test-Path -LiteralPath (Join-Path $adminRoot 'node_modules\vite\bin\vite.js'))) {
    throw 'Local Vite dependencies are missing; no package installation was attempted.'
}

$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Za-z0-9_]+)=(.*)\s*$') {
        $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
    }
}
if ($settings.BINGO_TEST_MYSQL_URL -notmatch '^jdbc:mysql://127\.0\.0\.1:3306/bingo_qa_test\?') {
    throw 'Refusing E2E reconciliation: .env.test does not target the approved local schema.'
}
$mysqlUser = $settings['BINGO_TEST_MYSQL_USERNAME']
$mysql = (Get-Command mysql.exe -ErrorAction Stop).Source
$runId = 'QA_M06A5_' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff')
$manifestPath = Join-Path $evidenceDir "$runId.json"
$beforePath = Join-Path $evidenceDir "$runId-database-before.json"
$afterPath = Join-Path $evidenceDir "$runId-database-after.json"
$playwrightLog = Join-Path $evidenceDir "$runId-playwright.log"
$viteLogDir = Join-Path $tempLogDir $runId
$null = New-Item -ItemType Directory -Path $evidenceDir -Force
$null = New-Item -ItemType Directory -Path $viteLogDir -Force

function Invoke-QaMysql([string]$Sql) {
    $env:MYSQL_PWD = $settings['BINGO_TEST_MYSQL_PASSWORD']
    try {
        $rows = & $mysql --protocol=TCP --host=127.0.0.1 --port=3306 --user=$mysqlUser --database=bingo_qa_test --batch --skip-column-names --execute $Sql
        if ($LASTEXITCODE -ne 0) { throw 'Read-only QA MySQL query failed.' }
        return @($rows | ForEach-Object { [string]$_ })
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Get-QaSnapshot {
    $rows = Invoke-QaMysql @"
SELECT CONCAT('COUNT|rooms|',COUNT(*)) FROM bingo_rooms;
SELECT CONCAT('COUNT|sessions|',COUNT(*)) FROM bingo_sessions;
SELECT CONCAT('COUNT|tickets|',COUNT(*)) FROM bingo_tickets;
SELECT CONCAT('COUNT|draws|',COUNT(*)) FROM bingo_draw_events;
SELECT CONCAT('COUNT|claims|',COUNT(*)) FROM bingo_win_claims;
SELECT CONCAT('COUNT|wallet_transactions|',COUNT(*)) FROM wallet_transactions;
SELECT CONCAT('COUNT|audit_rows|',COUNT(*)) FROM audit_log_entries;
SELECT CONCAT('AUDIT_MAX|',COALESCE(MAX(id),0)) FROM audit_log_entries;
SELECT CONCAT('ROOM|',id,'|',active) FROM bingo_rooms ORDER BY id;
SELECT CONCAT('SESSION|',id,'|',state) FROM bingo_sessions ORDER BY id;
"@
    $walletRows = Invoke-QaMysql 'SELECT CONCAT_WS(''|'',id,owner_id,currency,available_balance,promotional_balance,version) FROM wallets ORDER BY id'
    $transactionRows = Invoke-QaMysql 'SELECT * FROM wallet_transactions ORDER BY id'
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $walletHash = ConvertTo-UpperHexString -Bytes $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($walletRows -join "`n")))
        $transactionHash = ConvertTo-UpperHexString -Bytes $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($transactionRows -join "`n")))
    } finally { $sha.Dispose() }
    return [ordered]@{
        capturedAt = (Get-Date).ToString('o')
        mysqlTarget = '127.0.0.1:3306/bingo_qa_test'
        rows = $rows
        walletBalancesSha256 = $walletHash
        walletTransactionsSha256 = $transactionHash
    }
}

$before = Get-QaSnapshot
$before | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $beforePath -Encoding utf8
$existingRoomRows = @($before.rows | Where-Object { $_ -like 'ROOM|*' })
if ($existingRoomRows.Count -eq 0) { throw 'No existing QA rooms are available; no room fixture was created.' }

$adminPortCheck = [System.Net.Sockets.TcpClient]::new()
$adminPortBusy = $false
try { $adminPortCheck.Connect('127.0.0.1', 5174); $adminPortBusy = $true } catch { } finally { $adminPortCheck.Dispose() }
if ($adminPortBusy) { throw 'Port 5174 is already occupied. Refusing to use an unverified Admin frontend.' }

$email = $env:BINGO_E2E_ADMIN_EMAIL
$password = $env:BINGO_E2E_ADMIN_PASSWORD
$env:VITE_API_BASE_URL = $backendStatus.backendUrl
$env:BINGO_E2E_BACKEND_URL = $backendStatus.backendUrl
$env:BINGO_E2E_ADMIN_URL = 'http://127.0.0.1:5174'
$env:BINGO_E2E_RUN_ID = $runId
$env:BINGO_E2E_MANIFEST_PATH = $manifestPath

# Do not pass Admin credentials to the Vite server process.
Remove-Item Env:\BINGO_E2E_ADMIN_EMAIL, Env:\BINGO_E2E_ADMIN_PASSWORD -ErrorAction SilentlyContinue
$viteProcess = $null
$playwrightExit = 1
try {
    $vite = Join-Path $adminRoot 'node_modules\vite\bin\vite.js'
    $viteProcess = Start-Process -FilePath 'node' -ArgumentList @($vite, '--mode', 'qa', '--host', '127.0.0.1', '--port', '5174', '--strictPort') `
        -WorkingDirectory $adminRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $viteLogDir 'vite.stdout.log') `
        -RedirectStandardError (Join-Path $viteLogDir 'vite.stderr.log')

    $adminReady = $false
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        Start-Sleep -Seconds 1
        if ($viteProcess.HasExited) { break }
        try {
            $response = Invoke-WebRequest -Uri 'http://127.0.0.1:5174/login' -TimeoutSec 2
            if ($response.StatusCode -eq 200) { $adminReady = $true; break }
        } catch { }
    }
    if (-not $adminReady) { throw 'Isolated Admin frontend did not become ready; no browser scenario was started.' }

    $env:BINGO_E2E_ADMIN_EMAIL = $email
    $env:BINGO_E2E_ADMIN_PASSWORD = $password
    $playwright = Join-Path $adminRoot 'node_modules\.bin\playwright.cmd'
    Push-Location $adminRoot
    try {
        & $playwright test tests/e2e/module-06a5-session-scheduling.spec.ts --project=chromium --workers=1 2>&1 |
            Tee-Object -FilePath $playwrightLog
        $playwrightExit = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    Remove-Item Env:\BINGO_E2E_ADMIN_EMAIL, Env:\BINGO_E2E_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    if ($viteProcess -and -not $viteProcess.HasExited) { Stop-Process -Id $viteProcess.Id -Force }
}

$after = Get-QaSnapshot
$after | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $afterPath -Encoding utf8
$reconciliation = [ordered]@{
    runId = $runId
    playwrightExitCode = $playwrightExit
    databaseBefore = $beforePath
    databaseAfter = $afterPath
    manifest = $manifestPath
    auditEvidence = @()
    checks = [ordered]@{}
}

if (-not (Test-Path -LiteralPath $manifestPath)) {
    $reconciliation.checks['scenarioManifestCreated'] = $false
} else {
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $sessionId = [long]$manifest.sessionId
    $sessionRow = Invoke-QaMysql "SELECT CONCAT_WS('|',id,session_name,state,room_id) FROM bingo_sessions WHERE id=$sessionId"
    $auditMaxRow = $before.rows | Where-Object { $_ -like 'AUDIT_MAX|*' } | Select-Object -First 1
    $auditMax = [long](($auditMaxRow -split '\|')[1])
    $auditRows = Invoke-QaMysql "SELECT CONCAT_WS('|',id,action,target_type,target_id) FROM audit_log_entries WHERE id > $auditMax AND target_id=$sessionId ORDER BY id"
    $reconciliation.auditEvidence = $auditRows
    $reconciliation.checks['scenarioManifestCreated'] = $true
    $reconciliation.checks['isolatedBackendInManifest'] = ($manifest.backendBaseUrl -eq $backendStatus.backendUrl)
    $reconciliation.checks['newSessionPersistedAndCancelled'] = ($sessionRow.Count -eq 1 -and $sessionRow[0] -match '\|CANCELLED\|')
    $beforeSessions = @($before.rows | Where-Object { $_ -like 'SESSION|*' } | Sort-Object)
    $afterSessions = @($after.rows | Where-Object { $_ -like 'SESSION|*' -and $_ -notmatch "^SESSION\|$sessionId\|" } | Sort-Object)
    $reconciliation.checks['historicalSessionStatesUnchanged'] = (($beforeSessions -join "`n") -eq ($afterSessions -join "`n"))
    $reconciliation.checks['roomsUnchanged'] = ((@($before.rows | Where-Object { $_ -like 'ROOM|*' } | Sort-Object) -join "`n") -eq (@($after.rows | Where-Object { $_ -like 'ROOM|*' } | Sort-Object) -join "`n"))
    foreach ($table in @('tickets', 'draws', 'claims', 'wallet_transactions')) {
        $beforeCount = $before.rows | Where-Object { $_ -like "COUNT|$table|*" } | Select-Object -First 1
        $afterCount = $after.rows | Where-Object { $_ -like "COUNT|$table|*" } | Select-Object -First 1
        $reconciliation.checks["${table}CountUnchanged"] = ($beforeCount -eq $afterCount)
    }
    $reconciliation.checks['walletBalancesUnchanged'] = ($before.walletBalancesSha256 -eq $after.walletBalancesSha256)
    $reconciliation.checks['walletTransactionsUnchanged'] = ($before.walletTransactionsSha256 -eq $after.walletTransactionsSha256)
    $reconciliation.checks['auditRowsForNewSessionPresent'] = ($auditRows.Count -gt 0)
}

$reconciliationPath = Join-Path $evidenceDir "$runId-reconciliation.json"
$reconciliation | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reconciliationPath -Encoding utf8
$falseChecks = @($reconciliation.checks.GetEnumerator() | Where-Object { -not $_.Value })
Write-Host "Run: $runId; isolated target: bingo_qa_test; Playwright exit: $playwrightExit"
Write-Host "Read-only before/after reconciliation: $reconciliationPath"
if ($playwrightExit -ne 0 -or $falseChecks.Count -gt 0) {
    Write-Error 'Session scheduling E2E did not PASS. Preserve all created records and review the manifest, screenshots, logs, and reconciliation evidence.'
    exit 1
}
Write-Host 'PASS: real Admin Chromium session schedule/lifecycle and MySQL reconciliation.'
