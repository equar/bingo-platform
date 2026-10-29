[CmdletBinding()]
param(
    [AllowNull()][string]$EnvironmentName
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'bingo-backend'
$agentRoot = Join-Path $repoRoot 'bingo-agent'
$envPath = Join-Path $backendRoot '.env.test'
$secretsPath = Join-Path $agentRoot 'e2e-secrets.json'
$statusPath = Join-Path $repoRoot 'docs\qa\evidence\module-06a5\backend-identity-preflight.json'
$logDir = Join-Path $env:TEMP 'bingo-platform-module-06a5'
$policyModule = Join-Path $PSScriptRoot 'AndroidTestBackendPolicy.psm1'
$evidenceRoot = Join-Path $repoRoot 'docs\qa\evidence\module-01-agent-authentication'
$runId = 'QA_M01_AGENT_' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff')
$runEvidenceDir = Join-Path $evidenceRoot $runId
$reportPath = Join-Path $runEvidenceDir 'run-summary.json'
$flutterLogPath = Join-Path $runEvidenceDir 'flutter-integration.log'
$secretDir = $null
$secretFile = $null
$screenshotBackupDir = $null
$mysqlPassword = $null
$settings = $null
$fixturePhones = @()
$screenshotNames = @(
    'module-01-agent-login.png',
    'module-01-agent-network-error.png',
    'module-01-agent-authenticated.png',
    'module-01-agent-pending-rejected.png',
    'module-01-agent-expired-session.png',
    'module-01-agent-session-persisted.png',
    'module-01-agent-support.png',
    'module-01-agent-dashboard.png',
    'module-01-agent-float-readonly.png',
    'module-01-agent-player-lookup-validation.png',
    'module-01-agent-process-restart.png'
)
$screenshotOriginals = @{}
$screenshotCopiesReady = $false
$failureMessage = $null
$testExitCode = $null
$testStarted = $false
$startedAt = (Get-Date).ToString('o')

$report = [ordered]@{
    runId = $runId
    startedAt = $startedAt
    stage = 'execution-identity'
    status = 'BLOCKED'
    environment = $EnvironmentName
    test = 'bingo-agent/integration_test/module_01_authentication_test.dart'
    backend = $null
    mysql = $null
    androidDevice = 'emulator-5554'
    financialPosting = 'disabled'
    agentFixtures = [ordered]@{ approved = $false; pending = $false }
    databaseCountsBefore = $null
    databaseCountsAfter = $null
    protectedCountsUnchanged = $null
    testExitCode = $null
    processRestartExitCode = $null
    overallExitCode = $null
    processRestartBlocker = $null
    scenarios = @(
        [ordered]@{ name = 'Approved Agent phone/PIN login in Android UI'; status = 'NOT RUN' }
        [ordered]@{ name = 'Pending Agent login denied in Android UI'; status = 'NOT RUN' }
        [ordered]@{ name = 'Invalid credentials and login throttling'; status = 'NOT RUN' }
        [ordered]@{ name = 'Session restore, expiry, and logout'; status = 'NOT RUN' }
        [ordered]@{ name = 'Cross-role authorization restrictions'; status = 'NOT RUN' }
        [ordered]@{ name = 'Authenticated Agent Support ticket list (read-only)'; status = 'NOT RUN' }
        [ordered]@{ name = 'Authenticated Agent dashboard, Player lookup validation, and read-only operational screens'; status = 'NOT RUN' }
        [ordered]@{ name = 'Android process force-stop/relaunch session restore and logout'; status = 'NOT RUN' }
    )
    screenshots = @()
    failureType = $null
    failureLine = $null
    failureDetail = $null
    blocker = $null
}

$scenarioMarkerIndexes = @{
    'agent-approved-login' = 0
    'agent-pending-login-denied' = 1
    'agent-invalid-credentials-throttling' = 2
    'agent-session-restore-expiry-logout' = 3
    'agent-cross-role-restrictions' = 4
    'agent-support-readonly' = 5
    'agent-dashboard-lookup-operational-readonly' = 6
    'agent-process-restart-session-restore-logout' = 7
}

function Read-EnvFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { throw 'BLOCKED: isolated backend .env.test is missing.' }
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
        $result = $Sql | & $script:mysqlCommand --protocol=TCP --host=127.0.0.1 --port=3306 "--user=$script:mysqlUser" --database=bingo_qa_test --batch --raw --skip-column-names
        if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: read-only isolated MySQL query failed.' }
        return ,@($result | ForEach-Object { [string]$_ })
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Get-DatabaseCounts {
    $rows = Invoke-QaMysql @'
SELECT CONCAT('users|',COUNT(*)) FROM users;
SELECT CONCAT('bingo_agents|',COUNT(*)) FROM bingo_agents;
SELECT CONCAT('agent_floats|',COUNT(*)) FROM agent_floats;
SELECT CONCAT('agent_float_top_up_requests|',COUNT(*)) FROM agent_float_top_up_requests;
SELECT CONCAT('agent_float_transactions|',COUNT(*)) FROM agent_float_transactions;
SELECT CONCAT('float_accounting_journals|',COUNT(*)) FROM float_accounting_journals;
SELECT CONCAT('float_accounting_journal_lines|',COUNT(*)) FROM float_accounting_journal_lines;
SELECT CONCAT('wallets|',COUNT(*)) FROM wallets;
SELECT CONCAT('wallet_transactions|',COUNT(*)) FROM wallet_transactions;
'@
    $counts = [ordered]@{}
    foreach ($row in $rows) {
        $parts = $row -split '\|', 2
        if ($parts.Count -ne 2 -or $parts[1] -notmatch '^\d+$') { throw 'BLOCKED: database count snapshot had an unexpected shape.' }
        $counts[$parts[0]] = [long]$parts[1]
    }
    return $counts
}

function ConvertTo-NormalizedAgentPhone([string]$Phone) {
    if ($Phone -notmatch '^\+?[0-9\s().-]+$') { throw 'BLOCKED: an authorized Agent test phone has an unsupported format.' }
    $digits = $Phone.Trim() -replace '[\s\-().]', ''
    if ($digits.StartsWith('+')) { $digits = $digits.Substring(1) }
    if ($digits.Length -eq 12 -and $digits.StartsWith('251')) { $normalized = $digits }
    elseif ($digits.Length -eq 10 -and $digits.StartsWith('0')) { $normalized = '251' + $digits.Substring(1) }
    elseif ($digits.Length -eq 9) { $normalized = '251' + $digits }
    else { throw 'BLOCKED: an authorized Agent test phone is not a supported Ethiopian mobile format.' }
    if ($normalized -notmatch '^251[79][0-9]{8}$') { throw 'BLOCKED: an authorized Agent test phone is not a supported Ethiopian mobile format.' }
    return $normalized
}

function Set-PrivateDirectoryAcl([string]$Path, [System.Security.Principal.SecurityIdentifier]$UserSid) {
    $acl = Get-Acl -LiteralPath $Path
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($accessRule in @($acl.Access)) { $null = $acl.RemoveAccessRule($accessRule) }
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new(
        $UserSid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'
    )
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $acl
}

function Save-RunScreenshots {
    $savedPaths = @()
    $sourceDir = Join-Path $repoRoot 'docs\qa\evidence'
    $destinationDir = Join-Path $runEvidenceDir 'screenshots'
    $null = New-Item -ItemType Directory -Path $destinationDir -Force

    foreach ($name in $screenshotNames) {
        $sourcePath = Join-Path $sourceDir $name
        if (Test-Path -LiteralPath $sourcePath) {
            $destinationPath = Join-Path $destinationDir $name
            Copy-Item -LiteralPath $sourcePath -Destination $destinationPath -Force
            $savedPaths += "screenshots/$name"
        }
        if ($screenshotOriginals[$name]) {
            $backupPath = Join-Path $screenshotBackupDir $name
            if (Test-Path -LiteralPath $backupPath) {
                Copy-Item -LiteralPath $backupPath -Destination $sourcePath -Force
            }
        } elseif (Test-Path -LiteralPath $sourcePath) {
            Remove-Item -LiteralPath $sourcePath -Force
        }
    }
    return $savedPaths
}

try {
    Import-Module $policyModule -Force
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
    if ($identity.Name -match 'CodexSandboxOffline') {
        throw 'BLOCKED: run this launcher from the normal Windows user profile; no Android or database test was started.'
    }
    if ($identity.Name -notmatch '\\16414$' -or $env:USERPROFILE -cne 'C:\Users\16414') {
        throw 'BLOCKED: run this launcher as EQUAR-DELL-XPS1\16414; no Android or database test was started.'
    }

    $report.stage = 'environment-validation'
    $settings = Read-EnvFile $envPath
    foreach ($key in @('BINGO_TEST_MYSQL_URL', 'BINGO_TEST_MYSQL_USERNAME', 'BINGO_TEST_MYSQL_PASSWORD')) {
        if (-not $settings.ContainsKey($key)) { throw "BLOCKED: required isolated setting is missing: $key." }
    }
    $environment = Assert-AgentAndroidE2EEnvironment `
        -EnvironmentName $EnvironmentName `
        -BackendUrl 'http://127.0.0.1:8082' `
        -EmulatorBackendUrl 'http://10.0.2.2:8082' `
        -JdbcUrl ([string]$settings.BINGO_TEST_MYSQL_URL) `
        -MysqlUsername ([string]$settings.BINGO_TEST_MYSQL_USERNAME) `
        -MysqlPassword ([string]$settings.BINGO_TEST_MYSQL_PASSWORD)
    $mysqlPassword = [string]$settings.BINGO_TEST_MYSQL_PASSWORD
    $script:mysqlUser = [string]$settings.BINGO_TEST_MYSQL_USERNAME
    $script:mysqlCommand = (Get-Command mysql.exe -ErrorAction Stop).Source

    $report.stage = 'backend-runtime-validation'
    if (-not (Test-Path -LiteralPath $statusPath)) { throw 'BLOCKED: saved isolated backend identity evidence is missing.' }
    $backend = Get-Content -LiteralPath $statusPath -Raw | ConvertFrom-Json
    if ([string]$backend.backendUrl -ne $environment.BackendUrl) { throw 'BLOCKED: saved backend URL does not match the approved local target.' }

    $backendPid = [int]$backend.processId
    $listenerPids = @(Get-AndroidTestTcpListenerPids -Port 8082)
    if ($listenerPids.Count -ne 1) { throw 'BLOCKED: exactly one listener must be verified on isolated backend port 8082.' }
    $listenerPid = [int]$listenerPids[0]
    $process = Get-Process -Id $backendPid -ErrorAction Stop
    if ($process.ProcessName -ne 'java' -or $listenerPid -ne $backendPid) {
        throw 'BLOCKED: isolated backend listener PID is not the saved Java process.'
    }
    $buildRoot = [System.IO.Path]::GetFullPath((Join-Path $env:TEMP 'bingo-platform-module-06a5') + '\')
    $jarPath = [System.IO.Path]::GetFullPath([string]$backend.artifactPath)
    if (-not $jarPath.StartsWith($buildRoot, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $jarPath) -or -not $process.Path -or
        [System.IO.Path]::GetFileName($process.Path) -ne 'java.exe') {
        throw 'BLOCKED: saved QA artifact or Java process identity is outside the isolated launch target.'
    }
    $actualJarHash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
    $resourceUri = ([Uri]::new(((Join-Path $backendRoot 'src\test\resources').TrimEnd('\') + '\'))).AbsoluteUri
    if (-not (Test-AndroidTestProcessCommandLine -ProcessId $backendPid -JarPath $jarPath -TestResourcesUri $resourceUri)) {
        throw 'BLOCKED: native process inspection could not verify the exact QA JAR and android-test launch arguments.'
    }
    $sourceFingerprint = Get-AndroidTestBackendSourceFingerprint -RepositoryRoot $repoRoot
    $sourceMatches = $sourceFingerprint -eq [string]$backend.sourceSha256

    $stdoutLog = Join-Path $logDir 'backend.stdout.log'
    $stderrLog = Join-Path $logDir 'backend.stderr.log'
    $runtimeIdentityLine = @(
        Select-String -Path @($stdoutLog, $stderrLog) -Pattern ('QA_TEST_RUNTIME_IDENTITY pid=' + $backendPid + ' ') -ErrorAction SilentlyContinue
    ) | Select-Object -Last 1
    $runtimeGuardsLine = @(
        Select-String -Path @($stdoutLog, $stderrLog) -Pattern ('QA_TEST_RUNTIME_GUARDS .*') -ErrorAction SilentlyContinue
    ) | Select-Object -Last 1
    $runtimeTimeMatch = [regex]::Match([string]$runtimeIdentityLine.Line, '^(?<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3})')
    $runtimeTime = if ($runtimeTimeMatch.Success) { [DateTimeOffset]::Parse($runtimeTimeMatch.Groups['time'].Value) } else { [DateTimeOffset]::MinValue }
    $processStartedAt = [DateTimeOffset]$process.StartTime
    if ($runtimeTime -lt $processStartedAt -or $runtimeTime -gt $processStartedAt.AddMinutes(5)) {
        throw 'BLOCKED: backend identity log is not contemporaneous with the listener process start.'
    }
    $health = Invoke-RestMethod -Uri "$($environment.BackendUrl)/actuator/health" -TimeoutSec 5
    $backendRuntime = Assert-AndroidTestBackendRuntimeIdentity `
        -BackendIdentity $backend `
        -RuntimeIdentityLine ([string]$runtimeIdentityLine.Line) `
        -RuntimeGuardsLine ([string]$runtimeGuardsLine.Line) `
        -ListenerProcessId $listenerPid `
        -BackendHealth ([string]$health.status) `
        -DatabaseHealth ([string]$health.components.db.status) `
        -BackendJarMatches ($actualJarHash -eq [string]$backend.artifactSha256) `
        -BackendSourceMatches $sourceMatches
    $report.backend = $backendRuntime

    $report.stage = 'mysql-identity-validation'
    $identityRow = Invoke-QaMysql 'SELECT CONCAT(DATABASE(),CHAR(9),CURRENT_USER(),CHAR(9),@@hostname,CHAR(9),@@port);'
    $mysqlIdentity = Assert-AndroidTestMysqlIdentity -IdentityRow $identityRow[0] -ConfiguredUsername $script:mysqlUser
    if ($mysqlIdentity.Port -ne 3306) { throw 'BLOCKED: direct MySQL identity is not on port 3306.' }
    $report.databaseCountsBefore = Get-DatabaseCounts
    if ($script:mysqlUser -cne 'root') {
        $grants = Invoke-QaMysql 'SHOW GRANTS FOR CURRENT_USER();'
        if ($grants.Count -eq 0) { throw 'BLOCKED: isolated MySQL account has no verifiable grants.' }
        foreach ($grant in $grants) {
            $normalizedGrant = $grant.Replace('`', '').ToUpperInvariant()
            if ($normalizedGrant -match '^GRANT USAGE ON \*\.\*') { continue }
            if ($normalizedGrant -match ' ON BINGO_QA_TEST\.\* ' -and $normalizedGrant -notmatch 'GRANT OPTION') { continue }
            throw 'BLOCKED: isolated MySQL account has a global, cross-schema, or grant-option privilege.'
        }
    }
    $flywayRow = Invoke-QaMysql 'SELECT CONCAT(version,CHAR(9),success) FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;'
    $flywayParts = $flywayRow[0] -split "`t"
    if ($flywayParts.Count -ne 2 -or $flywayParts[1] -ne '1' -or $flywayParts[0] -ne [string]$backend.flyway) {
        throw 'BLOCKED: MySQL Flyway version does not match the running backend identity.'
    }
    $report.mysql = [ordered]@{
        database = $mysqlIdentity.Database
        authenticatedUser = $mysqlIdentity.CurrentUser
        serverHost = $mysqlIdentity.ServerHost
        port = $mysqlIdentity.Port
        flyway = $flywayParts[0]
        accountScope = 'bingo_qa_test only'
    }

    $report.stage = 'agent-fixture-validation'
    if (-not (Test-Path -LiteralPath $secretsPath)) {
        throw 'BLOCKED: ignored local Agent E2E credential file is missing; no credentials were requested or logged.'
    }
    try { $secrets = Get-Content -LiteralPath $secretsPath -Raw | ConvertFrom-Json }
    catch { throw 'BLOCKED: ignored local Agent E2E credential file is invalid; no secret values were logged.' }
    if ($null -eq $secrets) { throw 'BLOCKED: ignored local Agent E2E credential file is empty.' }
    $fixturePhones = @(
        [string]$secrets.BINGO_QA_APPROVED_AGENT_PHONE,
        [string]$secrets.BINGO_QA_APPROVED_AGENT_PIN,
        [string]$secrets.BINGO_QA_PENDING_AGENT_PHONE,
        [string]$secrets.BINGO_QA_PENDING_AGENT_PIN
    )
    foreach ($key in @(
        'BINGO_QA_APPROVED_AGENT_PHONE', 'BINGO_QA_APPROVED_AGENT_PIN',
        'BINGO_QA_PENDING_AGENT_PHONE', 'BINGO_QA_PENDING_AGENT_PIN'
    )) {
        if ([string]::IsNullOrWhiteSpace([string]$secrets.$key)) {
            throw 'BLOCKED: local Agent E2E fixture credentials are incomplete.'
        }
    }
    if ([string]$secrets.BINGO_QA_APPROVED_AGENT_PIN -notmatch '^\d{4,8}$' -or
        [string]$secrets.BINGO_QA_PENDING_AGENT_PIN -notmatch '^\d{4,8}$') {
        throw 'BLOCKED: local Agent E2E PIN values do not meet the supported format.'
    }
    $approvedPhone = ConvertTo-NormalizedAgentPhone ([string]$secrets.BINGO_QA_APPROVED_AGENT_PHONE)
    $pendingPhone = ConvertTo-NormalizedAgentPhone ([string]$secrets.BINGO_QA_PENDING_AGENT_PHONE)
    $fixturePhones += @($approvedPhone, $pendingPhone)
    if ($approvedPhone -eq $pendingPhone) { throw 'BLOCKED: approved and pending Agent fixtures must be distinct accounts.' }

    $fixtureRows = Invoke-QaMysql @"
SELECT CONCAT('APPROVED|',COUNT(*)) FROM users u JOIN bingo_agents a ON a.user_id=u.id WHERE u.normalized_phone='$approvedPhone' AND u.status='ACTIVE' AND a.status='APPROVED';
SELECT CONCAT('PENDING|',COUNT(*)) FROM users u JOIN bingo_agents a ON a.user_id=u.id WHERE u.normalized_phone='$pendingPhone' AND u.status='PENDING' AND a.status='PENDING';
"@
    $fixtureStatus = @{}
    foreach ($row in $fixtureRows) {
        $parts = $row -split '\|', 2
        if ($parts.Count -ne 2 -or $parts[1] -notmatch '^\d+$') { throw 'BLOCKED: Agent fixture status query returned an unexpected shape.' }
        $fixtureStatus[$parts[0]] = [int]$parts[1]
    }
    $report.agentFixtures.approved = ($fixtureStatus['APPROVED'] -eq 1)
    $report.agentFixtures.pending = ($fixtureStatus['PENDING'] -eq 1)
    if (-not $report.agentFixtures.approved -or -not $report.agentFixtures.pending) {
        throw 'BLOCKED: isolated bingo_qa_test does not contain the exact approved and pending Agent fixtures from the local credential file; no login or fixture creation was attempted.'
    }

    $report.stage = 'android-device-preflight'
    $sdk = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'
    $adb = Join-Path $sdk 'platform-tools\adb.exe'
    $flutter = 'C:\src\flutter\bin\flutter.bat'
    if (-not (Test-Path -LiteralPath $adb) -or -not (Test-Path -LiteralPath $flutter)) {
        throw 'BLOCKED: Android SDK or Flutter executable is missing.'
    }
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    $env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
    $env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
    $env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
    $env:Path = "$sdk\platform-tools;$sdk\emulator;C:\src\flutter\bin;$env:Path"
    $adbDevices = & $adb devices -l 2>$null
    if ($LASTEXITCODE -ne 0 -or ($adbDevices -join "`n") -notmatch '(?m)^emulator-5554\s+device\b') {
        throw 'BLOCKED: emulator-5554 is not available.'
    }
    $flutterDevices = & $flutter devices 2>$null
    if ($LASTEXITCODE -ne 0 -or ($flutterDevices -join "`n") -notmatch 'emulator-5554') {
        throw 'BLOCKED: Flutter does not detect emulator-5554.'
    }

    $secretDir = Join-Path $env:TEMP $runId
    $null = New-Item -ItemType Directory -Path $secretDir -Force
    Set-PrivateDirectoryAcl -Path $secretDir -UserSid $identity.User
    $secretFile = Join-Path $secretDir 'e2e-secrets.json'
    $defines = @{}
    foreach ($property in $secrets.PSObject.Properties) { $defines[$property.Name] = $property.Value }
    $defines['API_BASE_URL'] = 'http://10.0.2.2:8082'
    [System.IO.File]::WriteAllText(
        $secretFile,
        ($defines | ConvertTo-Json -Compress),
        [System.Text.UTF8Encoding]::new($false)
    )

    $screenshotBackupDir = Join-Path $env:TEMP ($runId + '-screenshots')
    $null = New-Item -ItemType Directory -Path $screenshotBackupDir -Force
    $legacyEvidenceDir = Join-Path $repoRoot 'docs\qa\evidence'
    foreach ($name in $screenshotNames) {
        $legacyPath = Join-Path $legacyEvidenceDir $name
        $screenshotOriginals[$name] = Test-Path -LiteralPath $legacyPath
        if ($screenshotOriginals[$name]) {
            Copy-Item -LiteralPath $legacyPath -Destination (Join-Path $screenshotBackupDir $name) -Force
        }
    }
    $screenshotCopiesReady = $true

    $report.stage = 'android-integration-test'
    $report.status = 'RUNNING'
    $testStarted = $true
    Push-Location $agentRoot
    try {
        $mainFlutterOutput = & $flutter drive `
            --driver=test_driver/module_01_authentication.dart `
            --target=integration_test/module_01_authentication_test.dart `
            -d emulator-5554 `
            --keep-app-running `
            "--dart-define-from-file=$secretFile" 2>&1
        $mainExitCode = $LASTEXITCODE
        $flutterOutput = @($mainFlutterOutput)
        $restartOutput = @()
        $restartExitCode = $null
        $restartPrerequisite = ($mainFlutterOutput -join "`n") -match 'QA_AGENT_RESTART_PREREQUISITE:authenticated-session-persisted'
        if ($mainExitCode -eq 0 -and $restartPrerequisite) {
            $forceStopOutput = & $adb -s emulator-5554 shell am force-stop com.bakaroo.bingo.bingo_agent 2>&1
            $forceStopExitCode = $LASTEXITCODE
            if ($forceStopExitCode -eq 0) {
                $restartOutput = & $flutter drive `
                    --driver=test_driver/module_01_authentication.dart `
                    --target=integration_test/module_01_session_restore_test.dart `
                    -d emulator-5554 `
                    --keep-app-running `
                    "--dart-define-from-file=$secretFile" 2>&1
                $restartExitCode = $LASTEXITCODE
                $flutterOutput += $forceStopOutput
                $flutterOutput += $restartOutput
            } else {
                $flutterOutput += $forceStopOutput
                $report.processRestartBlocker = "NOT RUN: ADB force-stop failed with exit code $forceStopExitCode."
            }
        } elseif ($mainExitCode -ne 0) {
            $report.processRestartBlocker = 'NOT RUN: main authentication suite did not complete successfully, so the authenticated-session prerequisite was not accepted.'
        } else {
            $report.processRestartBlocker = 'NOT RUN: main suite did not emit the authenticated-session prerequisite marker.'
        }
        $report.processRestartExitCode = $restartExitCode
        $report.testExitCode = $mainExitCode
        $testExitCode = if ($mainExitCode -eq 0 -and $restartExitCode -eq 0) { 0 } else { 1 }
        $report.overallExitCode = $testExitCode
    } finally {
        Pop-Location
    }

    $safeOutput = foreach ($line in $flutterOutput) {
        $text = [string]$line
        foreach ($secretValue in $fixturePhones) {
            if (-not [string]::IsNullOrEmpty($secretValue)) { $text = $text.Replace($secretValue, '[REDACTED]') }
        }
        $text
    }
    $null = New-Item -ItemType Directory -Path $runEvidenceDir -Force
    Set-Content -LiteralPath $flutterLogPath -Value $safeOutput -Encoding utf8
    $scenarioResults = Get-QAScenarioStatusesFromOutput `
        -OutputLines (@($mainFlutterOutput) + @($restartOutput)) `
        -ScenarioNames @($scenarioMarkerIndexes.Keys)
    foreach ($name in $scenarioMarkerIndexes.Keys) {
        $report.scenarios[$scenarioMarkerIndexes[$name]].status = $scenarioResults[$name]
    }
    if ($testExitCode -eq 0) {
        $report.status = 'PASS'
        $report.blocker = $null
    } elseif ($mainExitCode -ne 0) {
        $report.status = 'FAIL'
        $report.blocker = 'Agent authentication integration suite failed; scenario markers identify completed and incomplete scenarios in the sanitized log.'
    } elseif ($restartExitCode -ne 0) {
        $report.status = 'FAIL'
        $report.blocker = 'Agent process-restart integration suite failed; see the process-restart scenario and sanitized log.'
    } else {
        $report.status = 'BLOCKED'
        $report.blocker = $report.processRestartBlocker
    }
    $report.stage = 'database-reconciliation'
    $report.databaseCountsAfter = Get-DatabaseCounts
    $protectedTables = @(
        'users', 'bingo_agents', 'agent_floats', 'agent_float_top_up_requests',
        'agent_float_transactions', 'float_accounting_journals',
        'float_accounting_journal_lines', 'wallets', 'wallet_transactions'
    )
    $report.protectedCountsUnchanged = $true
    foreach ($table in $protectedTables) {
        if ($report.databaseCountsBefore[$table] -ne $report.databaseCountsAfter[$table]) {
            $report.protectedCountsUnchanged = $false
        }
    }
    if (-not $report.protectedCountsUnchanged) {
        $report.status = 'FAIL'
        $report.blocker = 'Protected user, Agent, wallet, float, or journal row counts changed during authentication-only E2E; records were not deleted or modified by teardown.'
    }
} catch {
    $errorMessage = [string]$_.Exception.Message
    if ($errorMessage -match '^(BLOCKED|FAIL):') {
        $failureMessage = $errorMessage
    } else {
        $failureMessage = 'BLOCKED: preflight failed unexpectedly; no secret values or SQL text are included in this report.'
        $report.failureType = $_.Exception.GetType().FullName
        $report.failureLine = $_.InvocationInfo.ScriptLineNumber
        $detail = [string]$_.Exception.Message
        $sensitiveValues = @($mysqlPassword) + $fixturePhones
        if ($settings) { $sensitiveValues += [string]$settings['BINGO_TEST_MYSQL_PASSWORD'] }
        foreach ($sensitiveValue in $sensitiveValues) {
            if (-not [string]::IsNullOrEmpty([string]$sensitiveValue)) {
                $detail = $detail.Replace([string]$sensitiveValue, '[REDACTED]')
            }
        }
        $report.failureDetail = $detail
    }
    $report.status = if ($failureMessage.StartsWith('FAIL:')) { 'FAIL' } else { 'BLOCKED' }
    $report.blocker = $failureMessage
} finally {
    if ($screenshotCopiesReady) {
        try { $report.screenshots = @(Save-RunScreenshots) }
        catch {
            $report.status = 'FAIL'
            $report.blocker = 'Screenshot preservation failed; inspect only the run evidence directory before another launch.'
        }
    }
    if ($testStarted -and $report.testExitCode -eq $null) { $report.status = 'FAIL' }
    if ($secretFile -and (Test-Path -LiteralPath $secretFile)) { Remove-Item -LiteralPath $secretFile -Force }
    if ($secretDir -and (Test-Path -LiteralPath $secretDir)) { Remove-Item -LiteralPath $secretDir -Force -Recurse }
    if ($screenshotBackupDir -and (Test-Path -LiteralPath $screenshotBackupDir)) { Remove-Item -LiteralPath $screenshotBackupDir -Force -Recurse }
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    $mysqlPassword = $null
    $report.finishedAt = (Get-Date).ToString('o')
    if (-not (Test-Path -LiteralPath $runEvidenceDir)) { $null = New-Item -ItemType Directory -Path $runEvidenceDir -Force }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8
}

if ($report.status -eq 'PASS') {
    Write-Host "PASS: Agent Android authentication E2E; sanitized evidence: $runEvidenceDir"
    exit 0
}
Write-Host "$($report.status): $($report.blocker) Evidence: $reportPath"
exit 1
