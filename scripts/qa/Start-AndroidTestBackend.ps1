[CmdletBinding()]
param(
    [int]$Port = 8082,
    [string]$MySqlUsername,
    [switch]$ProvisionAndroidTestFixtures
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'bingo-backend'
$envFile = Join-Path $backendRoot '.env.test'
$testResources = Join-Path $backendRoot 'src\test\resources'
$evidenceDir = Join-Path $repoRoot 'docs\qa\evidence\module-06a5'
$identityFile = Join-Path $evidenceDir 'backend-identity-preflight.json'
$logDir = Join-Path $env:TEMP 'bingo-platform-module-06a5'
$policyModule = Join-Path $PSScriptRoot 'AndroidTestBackendPolicy.psm1'
Import-Module $policyModule -Force

$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
if ($identity.Name -match 'CodexSandboxOffline') {
    throw 'Run this script from the normal VS Code PowerShell terminal under your Windows user account.'
}
if ($identity.Name -notmatch '\\16414$' -or $env:USERPROFILE -cne 'C:\Users\16414') {
    throw 'Run this launcher as EQUAR-DELL-XPS1\16414 with USERPROFILE C:\Users\16414.'
}
if ($Port -ne 8082) { throw 'The isolated Android-test backend must use port 8082.' }
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Ignored bingo-backend/.env.test is missing.' }
if (-not (Test-Path -LiteralPath $testResources)) { throw 'Backend test profile resources are missing.' }
$existingListeners = @(Get-AndroidTestTcpListenerPids -Port $Port)
if ($existingListeners.Count -gt 0) {
    throw "Refusing startup: port $Port already has a listener. Verify its identity and stop only the intended process first."
}

# Android SDK configuration is derived from the interactive Windows profile.
$sdk = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
$env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
$env:Path = "$sdk\platform-tools;$sdk\emulator;C:\src\flutter\bin;$env:Path"

$adb = Join-Path $sdk 'platform-tools\adb.exe'
$flutter = 'C:\src\flutter\bin\flutter.bat'
if (Test-Path -LiteralPath $adb) {
    $adbList = & $adb devices -l 2>&1
    if (($adbList -join "`n") -match '(?m)^emulator-5554\s+device\b') {
        Write-Host 'Android emulator-5554: detected.'
    } else {
        Write-Warning 'Android emulator-5554 was not detected. Admin-only checks may still run.'
    }
} else {
    Write-Warning 'Android SDK platform-tools was not found. Admin-only checks may still run.'
}
if (Test-Path -LiteralPath $flutter) {
    $flutterList = & $flutter devices 2>&1
    if (($flutterList -join "`n") -match 'emulator-5554') {
        Write-Host 'Flutter emulator-5554: detected.'
    } else {
        Write-Warning 'Flutter did not report emulator-5554. Admin-only checks may still run.'
    }
}

# Load the isolated test connection values without displaying their contents.
$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*([A-Za-z0-9_]+)=(.*)\s*$') {
        $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
    }
}
foreach ($key in @('BINGO_TEST_MYSQL_URL', 'BINGO_TEST_MYSQL_USERNAME')) {
    if (-not $settings.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($settings[$key])) {
        throw "Required isolated database setting is missing: $key"
    }
}
$null = Assert-AndroidTestDatasource -JdbcUrl $settings.BINGO_TEST_MYSQL_URL

$configuredUsername = [string]$settings.BINGO_TEST_MYSQL_USERNAME
if (-not [string]::IsNullOrWhiteSpace($MySqlUsername)) {
    $mysqlUser = $MySqlUsername.Trim()
} else {
    $mysqlUser = $configuredUsername
}
if ($mysqlUser -notmatch '^[A-Za-z0-9_]+$') { throw 'MySQL username contains unsupported characters.' }

$mysqlPassword = [string]$settings['BINGO_TEST_MYSQL_PASSWORD']
if ((-not [string]::IsNullOrWhiteSpace($MySqlUsername) -and $mysqlUser -cne $configuredUsername) -or
    [string]::IsNullOrWhiteSpace($mysqlPassword)) {
    $securePassword = Read-Host "Enter the local MySQL password for $mysqlUser" -AsSecureString
    $passwordPointer = [IntPtr]::Zero
    try {
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        $mysqlPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    } finally {
        if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
        $securePassword.Dispose()
    }
}
if ([string]::IsNullOrWhiteSpace($mysqlPassword)) {
    throw 'MySQL password is missing; set it in ignored .env.test or select another username and enter it securely.'
}

$fixtureSettings = @{}
$git = Get-Command git.exe -ErrorAction Stop
if ($ProvisionAndroidTestFixtures) {
    $appSecretsPath = Join-Path $repoRoot 'bingo-app\e2e-secrets.json'
    $agentSecretsPath = Join-Path $repoRoot 'bingo-agent\e2e-secrets.json'
    $adminSecretsPath = Join-Path $repoRoot 'bingo-admin\.env.qa'
    foreach ($path in @($appSecretsPath, $agentSecretsPath, $adminSecretsPath)) {
        if (-not (Test-Path -LiteralPath $path)) {
            throw 'Fixture provisioning requires existing ignored local Player, Agent, and Admin configuration files.'
        }
        $relativePath = $path.Substring($repoRoot.Length).TrimStart('\').Replace('\', '/')
        & $git.Source -C $repoRoot check-ignore -q $relativePath
        if ($LASTEXITCODE -ne 0) {
            throw 'Fixture provisioning refused because a local credential file is not ignored by Git.'
        }
    }

    try {
        $fixtureSettings.Player = Get-Content -LiteralPath $appSecretsPath -Raw | ConvertFrom-Json
        $fixtureSettings.Agent = Get-Content -LiteralPath $agentSecretsPath -Raw | ConvertFrom-Json
    } catch {
        throw 'Fixture provisioning local Player or Agent configuration is invalid.'
    }
    $adminSettings = @{}
    foreach ($line in Get-Content -LiteralPath $adminSecretsPath) {
        if ($line -match '^\s*([A-Za-z0-9_]+)=(.*)\s*$') {
            $adminSettings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
        }
    }
    $fixtureSettings.Admin = $adminSettings
    foreach ($key in @('BINGO_QA_PLAYER_001_PHONE', 'BINGO_QA_PLAYER_001_PIN')) {
        if ([string]::IsNullOrWhiteSpace([string]$fixtureSettings.Player.$key)) {
            throw 'Fixture provisioning local Player configuration is incomplete.'
        }
    }
    foreach ($key in @(
        'BINGO_QA_APPROVED_AGENT_PHONE', 'BINGO_QA_APPROVED_AGENT_PIN',
        'BINGO_QA_PENDING_AGENT_PHONE', 'BINGO_QA_PENDING_AGENT_PIN'
    )) {
        if ([string]::IsNullOrWhiteSpace([string]$fixtureSettings.Agent.$key)) {
            throw 'Fixture provisioning local Agent configuration is incomplete.'
        }
    }
    foreach ($key in @('BINGO_ANDROID_TEST_ADMIN_PHONE', 'BINGO_E2E_ADMIN_EMAIL', 'BINGO_E2E_ADMIN_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([string]$fixtureSettings.Admin[$key])) {
            throw 'Fixture provisioning local Admin configuration is incomplete.'
        }
    }
}

$mysqlCommand = Get-Command mysql.exe -ErrorAction Stop
$mysqlIdentity = $null
$env:MYSQL_PWD = $mysqlPassword
try {
    $identityRow = & $mysqlCommand.Source --protocol=TCP --host=127.0.0.1 --port=3306 --user=$mysqlUser --database=bingo_qa_test --batch --raw --skip-column-names --execute 'SELECT DATABASE(), CURRENT_USER(), @@hostname, @@port'
    if ($LASTEXITCODE -ne 0) { throw 'Read-only MySQL identity query failed.' }
    $mysqlIdentity = Assert-AndroidTestMysqlIdentity -IdentityRow ([string]($identityRow | Select-Object -First 1)) -ConfiguredUsername $mysqlUser

    if ($mysqlUser -cne 'root') {
        $grants = & $mysqlCommand.Source --protocol=TCP --host=127.0.0.1 --port=3306 --user=$mysqlUser --database=bingo_qa_test --batch --raw --skip-column-names --execute 'SHOW GRANTS FOR CURRENT_USER()'
        if ($LASTEXITCODE -ne 0 -or -not $grants) { throw 'Read-only MySQL grant query failed.' }
        foreach ($grant in $grants) {
            $normalized = $grant.Replace('`', '').ToUpperInvariant()
            if ($normalized -match '^GRANT USAGE ON \*\.\*') { continue }
            if ($normalized -match ' ON BINGO_QA_TEST\.\* ' -and $normalized -notmatch 'GRANT OPTION') { continue }
            throw 'Refusing startup: non-root database account has a global or cross-schema grant.'
        }
    }

    $flywayRow = & $mysqlCommand.Source --protocol=TCP --host=127.0.0.1 --port=3306 --user=$mysqlUser --database=bingo_qa_test --batch --raw --skip-column-names --execute 'SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1'
    if ($LASTEXITCODE -ne 0 -or -not $flywayRow) { throw 'Could not read the current Flyway schema version.' }
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
}

$flywayParts = ([string]($flywayRow | Select-Object -First 1)) -split "`t"
if ($flywayParts.Count -ne 2 -or $flywayParts[1] -ne '1') { throw 'Latest Flyway migration is not successful.' }
$dbFlyway = $flywayParts[0]
$migrationVersions = Get-ChildItem -LiteralPath (Join-Path $backendRoot 'src\main\resources\db\migration') -File |
    ForEach-Object { if ($_.Name -match '^V(\d+)__') { [int]$matches[1] } }
$sourceFlyway = ($migrationVersions | Measure-Object -Maximum).Maximum
if ([int]$dbFlyway -ne [int]$sourceFlyway) {
    throw "Refusing startup: database Flyway V$dbFlyway does not match source V$sourceFlyway; do not auto-migrate during E2E preflight."
}

$commit = & $git.Source -c safe.directory=D:/GitHub/bingo-platform -C $repoRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Could not identify the checked-out source revision.' }

New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$buildDirectory = Join-Path $logDir ("backend-build-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $buildDirectory -Force | Out-Null
$sourceShaBeforeBuild = Get-AndroidTestBackendSourceFingerprint -RepositoryRoot $repoRoot
Push-Location $backendRoot
try {
    & mvn "-Dbingo.build.directory=$buildDirectory" -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Backend package build failed. No tests were run.' }
} finally {
    Pop-Location
}
$sourceSha256 = Get-AndroidTestBackendSourceFingerprint -RepositoryRoot $repoRoot
if ($sourceSha256 -ne $sourceShaBeforeBuild) {
    throw 'Backend source changed during packaging; refusing to start an artifact with an unverified source fingerprint.'
}

$jar = Get-ChildItem -LiteralPath $buildDirectory -Filter 'bingo-backend-*.jar' -File |
    Where-Object { $_.Name -notmatch 'original|sources|javadoc' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw 'Packaged backend JAR was not found.' }
$jarHash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash
$resourceUri = ([Uri]::new(($testResources.TrimEnd('\') + '\'))).AbsoluteUri

$env:BINGO_TEST_MYSQL_URL = $settings.BINGO_TEST_MYSQL_URL
$env:BINGO_TEST_MYSQL_USERNAME = $mysqlUser
$env:BINGO_TEST_MYSQL_PASSWORD = $mysqlPassword
$env:BINGO_QA_BUILD_COMMIT = ($commit | Select-Object -First 1).Trim()
$env:BINGO_QA_BUILD_SHA256 = $jarHash
$env:BINGO_QA_BUILD_SOURCE_SHA256 = $sourceSha256
if ($ProvisionAndroidTestFixtures) {
    $env:BINGO_ANDROID_TEST_FIXTURES_CONFIRMATION = 'bingo_qa_test'
    $env:BINGO_QA_PLAYER_001_PHONE = [string]$fixtureSettings.Player.BINGO_QA_PLAYER_001_PHONE
    $env:BINGO_QA_PLAYER_001_PIN = [string]$fixtureSettings.Player.BINGO_QA_PLAYER_001_PIN
    $env:BINGO_ANDROID_TEST_ADMIN_PHONE = [string]$fixtureSettings.Admin['BINGO_ANDROID_TEST_ADMIN_PHONE']
    $env:BINGO_E2E_ADMIN_EMAIL = [string]$fixtureSettings.Admin['BINGO_E2E_ADMIN_EMAIL']
    $env:BINGO_E2E_ADMIN_PASSWORD = [string]$fixtureSettings.Admin['BINGO_E2E_ADMIN_PASSWORD']
    foreach ($key in @(
        'BINGO_QA_APPROVED_AGENT_PHONE', 'BINGO_QA_APPROVED_AGENT_PIN',
        'BINGO_QA_PENDING_AGENT_PHONE', 'BINGO_QA_PENDING_AGENT_PIN'
    )) {
        Set-Item -Path "Env:\$key" -Value ([string]$fixtureSettings.Agent.$key)
    }
}

$stdoutLog = Join-Path $logDir 'backend.stdout.log'
$stderrLog = Join-Path $logDir 'backend.stderr.log'
$args = @(
    '-jar', "`"$($jar.FullName)`"",
    "--spring.config.additional-location=$resourceUri",
    '--spring.profiles.active=android-test',
    "--server.port=$Port",
    '--app.qa.test-runtime-identity-reporting-enabled=true',
    '--app.game.draw-scheduler-enabled=false',
    '--app.wallet.signup-reward-test-posting-enabled=false',
    '--app.wallet.deposit-simulation-enabled=false',
    '--app.wallet.adjustment-simulation-enabled=false',
    '--app.agent.float-simulation-enabled=false',
    '--app.qa.synthetic-financial-tests-enabled=false',
    '--CORS_ALLOWED_ORIGINS=http://127.0.0.1:5174',
    "--app.qa.android-test-fixtures-bootstrap-enabled=$($ProvisionAndroidTestFixtures.IsPresent.ToString().ToLowerInvariant())",
    '--spring.jpa.hibernate.ddl-auto=validate',
    '--spring.flyway.clean-disabled=true',
    '--spring.flyway.validate-on-migrate=true'
)
try {
    $process = Start-Process -FilePath 'java' -ArgumentList $args -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
} finally {
    Remove-Item Env:\BINGO_TEST_MYSQL_PASSWORD -ErrorAction SilentlyContinue
    foreach ($key in @(
        'BINGO_ANDROID_TEST_FIXTURES_CONFIRMATION', 'BINGO_QA_PLAYER_001_PHONE',
        'BINGO_QA_PLAYER_001_PIN', 'BINGO_ANDROID_TEST_ADMIN_PHONE',
        'BINGO_E2E_ADMIN_EMAIL', 'BINGO_E2E_ADMIN_PASSWORD',
        'BINGO_QA_BUILD_SOURCE_SHA256',
        'BINGO_QA_APPROVED_AGENT_PHONE', 'BINGO_QA_APPROVED_AGENT_PIN',
        'BINGO_QA_PENDING_AGENT_PHONE', 'BINGO_QA_PENDING_AGENT_PIN'
    )) {
        Remove-Item -Path "Env:\$key" -ErrorAction SilentlyContinue
    }
    $mysqlPassword = $null
    $settings['BINGO_TEST_MYSQL_PASSWORD'] = $null
}

$identityPattern = 'QA_TEST_RUNTIME_IDENTITY .*profiles=android-test database=bingo_qa_test jdbcHost=127\.0\.0\.1 jdbcPort=3306 flyway=' + [regex]::Escape([string]$dbFlyway) + ' '
$verified = $false
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) { break }
    $captured = @()
    if (Test-Path -LiteralPath $stdoutLog) { $captured += Get-Content -LiteralPath $stdoutLog -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $stderrLog) { $captured += Get-Content -LiteralPath $stderrLog -ErrorAction SilentlyContinue }
    if (($captured -join "`n") -match $identityPattern) {
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 3
            if ($health.status -eq 'UP') { $verified = $true; break }
        } catch { }
    }
}

if (-not $verified) {
    if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
    throw "Backend identity/health verification failed. Logs are in $logDir; do not run E2E until reviewed."
}

$status = [ordered]@{
    verifiedAt = (Get-Date).ToString('o')
    profile = 'android-test'
    database = 'bingo_qa_test'
    jdbcHost = '127.0.0.1'
    jdbcPort = 3306
    flyway = [int]$dbFlyway
    mysqlUsername = $mysqlUser
    mysqlAuthenticatedIdentity = $mysqlIdentity.CurrentUser
    backendUrl = "http://127.0.0.1:$Port"
    emulatorBackendUrl = "http://10.0.2.2:$Port"
    adminOrigin = 'http://127.0.0.1:5174'
    drawScheduler = 'disabled'
    financialPostingAndSimulation = 'disabled'
    processId = $process.Id
    processStartedAt = $process.StartTime.ToString('o')
    javaExecutable = (Get-Process -Id $process.Id -ErrorAction Stop).Path
    artifactPath = $jar.FullName
    sourceCommit = $env:BINGO_QA_BUILD_COMMIT
    artifactSha256 = $jarHash
    sourceSha256 = $sourceSha256
}
$status | ConvertTo-Json | Set-Content -LiteralPath $identityFile -Encoding utf8
Write-Host "Verified QA backend: profile=android-test database=bingo_qa_test flyway=V$dbFlyway scheduler=disabled financial-posting=disabled"
Write-Host "Backend PID: $($process.Id). Sanitized identity evidence: $identityFile"
