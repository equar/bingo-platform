[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^bingo_qa_v22_v40_[0-9a-f]{32}$')][string]$Database,
    [Parameter(Mandatory)][string]$ConnectionDefaultsFile,
    [Parameter(Mandatory)][string]$EvidenceDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$backendRoot = Join-Path $repoRoot 'bingo-backend'
$testResources = Join-Path $backendRoot 'src\test\resources'
$policyModule = Join-Path $PSScriptRoot 'AndroidTestBackendPolicy.psm1'
Import-Module $policyModule -Force

$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
if ($identity.Name -match 'CodexSandboxOffline' -or $identity.Name -notmatch '\\16414$' -or
    $env:USERPROFILE -cne 'C:\Users\16414') {
    throw 'Run the isolated clone launcher from the normal EQUAR-DELL-XPS1\16414 interactive Windows session.'
}
if (-not (Test-Path -LiteralPath $ConnectionDefaultsFile -PathType Leaf)) {
    throw 'DBA-provisioned clone connection option file is missing.'
}
if (-not (Test-Path -LiteralPath $EvidenceDirectory -PathType Container)) {
    throw 'Use an existing DBA-approved protected evidence directory.'
}
if ($ConnectionDefaultsFile.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $EvidenceDirectory.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Clone credentials and runtime evidence must remain outside the repository.'
}

function Assert-PrivateOwnedPath([string]$Path, [switch]$RequireProtectedAcl) {
    $acl = Get-Acl -LiteralPath $Path
    $owner = [Security.Principal.NTAccount]::new($acl.Owner).Translate([Security.Principal.SecurityIdentifier]).Value
    if ($owner -ne $identity.User.Value -or ($RequireProtectedAcl -and -not $acl.AreAccessRulesProtected)) {
        throw 'Clone option file must be owned by the operator with inheritance disabled; evidence must be operator-owned.'
    }
    foreach ($rule in $acl.Access) {
        if ($rule.AccessControlType -ne [Security.AccessControl.AccessControlType]::Allow) { continue }
        $sid = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
        if ($sid -notin @($identity.User.Value, 'S-1-5-18')) {
            throw 'Protected clone files and evidence directory may grant access only to the operator and SYSTEM.'
        }
    }
}

Assert-PrivateOwnedPath $ConnectionDefaultsFile -RequireProtectedAcl
Assert-PrivateOwnedPath $EvidenceDirectory

$config = @{}
$inClientSection = $false
foreach ($line in Get-Content -LiteralPath $ConnectionDefaultsFile) {
    if ($line -match '^\s*\[([^\]]+)\]\s*$') {
        $inClientSection = $matches[1] -ceq 'client'
        continue
    }
    if ($inClientSection -and $line -match '^\s*(user|password|host|port)\s*=\s*(.*?)\s*$') {
        if ($config.ContainsKey($matches[1])) { throw 'Clone option file repeats a required client setting.' }
        $config[$matches[1]] = $matches[2]
    }
}
if ($config['host'] -cne '127.0.0.1' -or $config['port'] -cne '3306' -or
    $config['user'] -notmatch '^[A-Za-z0-9_]+$' -or $config['password'] -notmatch '^[A-Fa-f0-9]{40,128}$') {
    throw 'Clone option file must contain the approved local host/port, scoped username, and generated hex secret.'
}

$mysql = Get-Command mysql.exe -ErrorAction Stop
$repoMavenSettings = Join-Path $env:USERPROFILE '.m2\settings.xml'
if (-not (Test-Path -LiteralPath $repoMavenSettings -PathType Leaf)) {
    throw 'Normal user Maven settings are unavailable.'
}
$migrationVersions = Get-ChildItem -LiteralPath (Join-Path $backendRoot 'src\main\resources\db\migration') -File |
    ForEach-Object { if ($_.Name -match '^V(\d+)__') { [int]$matches[1] } }
if (($migrationVersions | Measure-Object -Maximum).Maximum -ne 40) {
    throw 'This clone launcher is pinned to source V40 and refuses to start if source migrations change.'
}
$expectedHistory = (@($migrationVersions | Sort-Object -Unique | ForEach-Object { [string]$_ }) -join ',')
if (@(Get-AndroidTestTcpListenerPids -Port 8080).Count -gt 0) {
    throw 'Refusing startup: port 8080 already has a listener. Verify it separately; do not stop unrelated processes.'
}

$previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
$env:MYSQL_PWD = $config['password']
try {
    $identityRow = & $mysql.Source --defaults-extra-file=$ConnectionDefaultsFile --protocol=TCP `
        --host=127.0.0.1 --port=3306 "--user=$($config['user'])" "--database=$Database" `
        --batch --raw --skip-column-names `
        --execute 'SELECT CONCAT(DATABASE(), CHAR(9), CURRENT_USER(), CHAR(9), @@hostname, CHAR(9), @@port)'
    if ($LASTEXITCODE -ne 0 -or @($identityRow).Count -ne 1) {
        throw 'Read-only clone database identity query failed.'
    }
    $identityParts = ([string]$identityRow[0]) -split "`t"
    if ($identityParts.Count -ne 4 -or $identityParts[0] -cne $Database -or
        $identityParts[1] -cne "$($config['user'])@127.0.0.1" -or $identityParts[2] -cne 'EQUAR-DELL-XPS15' -or
        $identityParts[3] -cne '3306') {
        throw 'Connected database, scoped account, host, or port differs from the approved clone.'
    }

    $grants = & $mysql.Source --defaults-extra-file=$ConnectionDefaultsFile --protocol=TCP `
        --host=127.0.0.1 --port=3306 "--user=$($config['user'])" "--database=$Database" `
        --batch --raw --skip-column-names --execute 'SHOW GRANTS FOR CURRENT_USER()'
    if ($LASTEXITCODE -ne 0 -or -not $grants) { throw 'Could not verify clone account grants.' }
    $allowedPrivileges = @('SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'ALTER', 'INDEX', 'REFERENCES', 'TRIGGER')
    $grantedPrivileges = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($grant in $grants) {
        $normalized = ([string]$grant).Replace('`', '').ToUpperInvariant()
        if ($normalized -match '^GRANT USAGE ON \*\.\*') { continue }
        $grantPattern = '^GRANT (?<privileges>[A-Z ,]+) ON ' + [regex]::Escape($Database.ToUpperInvariant()) + '\.\* TO '
        $grantMatch = [regex]::Match($normalized, $grantPattern)
        if (-not $grantMatch.Success -or $normalized -match 'GRANT OPTION') {
            throw 'Clone database user has a global, cross-schema, or grant-option privilege.'
        }
        foreach ($privilege in ($grantMatch.Groups['privileges'].Value -split ',\s*')) {
            if ($privilege -notin $allowedPrivileges) {
                throw 'Clone database user has a privilege outside the approved migration/runtime set.'
            }
            $null = $grantedPrivileges.Add($privilege)
        }
    }
    if (@($allowedPrivileges | Where-Object { -not $grantedPrivileges.Contains($_) }).Count -gt 0) {
        throw 'Clone database user is missing required schema-scoped privileges.'
    }

    $history = & $mysql.Source --defaults-extra-file=$ConnectionDefaultsFile --protocol=TCP `
        --host=127.0.0.1 --port=3306 "--user=$($config['user'])" "--database=$Database" `
        --batch --raw --skip-column-names `
        --execute "SELECT GROUP_CONCAT(version ORDER BY installed_rank SEPARATOR ','), SUM(success = 0) FROM flyway_schema_history"
    if ($LASTEXITCODE -ne 0 -or @($history).Count -ne 1) {
        throw 'Could not verify exact clone Flyway history.'
    }
    $historyParts = ([string]$history[0]) -split "`t", 2
    if ($historyParts.Count -ne 2 -or $historyParts[0] -cne $expectedHistory -or $historyParts[1] -cne '0') {
        throw 'Clone must have exactly the successful source migrations through V40 and no failed history. This launcher never migrates.'
    }
} finally {
    if ($null -eq $previousMysqlPwd) { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $previousMysqlPwd }
}

$runId = [guid]::NewGuid().ToString('N')
$runtimeRoot = Join-Path $env:TEMP "bingo-v40-clone-$runId"
$logRoot = Join-Path $EvidenceDirectory "bingo-v40-clone-$runId"
$identityFile = Join-Path $logRoot 'backend-identity.json'
if ((Test-Path -LiteralPath $runtimeRoot) -or (Test-Path -LiteralPath $logRoot)) {
    throw 'Refusing to reuse a backend build or evidence directory.'
}
New-Item -ItemType Directory -Path $runtimeRoot | Out-Null
New-Item -ItemType Directory -Path $logRoot | Out-Null
$logAcl = [System.Security.AccessControl.DirectorySecurity]::new()
$logAcl.SetOwner([System.Security.Principal.NTAccount]::new($identity.Name))
$logAcl.SetAccessRuleProtection($true, $false)
$logInheritance = [System.Security.AccessControl.InheritanceFlags]::ContainerInherit -bor
    [System.Security.AccessControl.InheritanceFlags]::ObjectInherit
$logAcl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new(
        $identity.User, [System.Security.AccessControl.FileSystemRights]::FullControl, $logInheritance,
        [System.Security.AccessControl.PropagationFlags]::None, [System.Security.AccessControl.AccessControlType]::Allow))
$logAcl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new(
        [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'),
        [System.Security.AccessControl.FileSystemRights]::FullControl, $logInheritance,
        [System.Security.AccessControl.PropagationFlags]::None, [System.Security.AccessControl.AccessControlType]::Allow))
Set-Acl -LiteralPath $logRoot -AclObject $logAcl
Assert-PrivateOwnedPath $logRoot
$sourceFingerprintBefore = Get-AndroidTestBackendSourceFingerprint -RepositoryRoot $repoRoot

Push-Location $backendRoot
try {
    & mvn --settings $repoMavenSettings "-Dbingo.build.directory=$runtimeRoot" -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Backend package build failed; clone backend was not started.' }
} finally { Pop-Location }

$sourceFingerprint = Get-AndroidTestBackendSourceFingerprint -RepositoryRoot $repoRoot
if ($sourceFingerprint -ne $sourceFingerprintBefore) {
    throw 'Backend source changed during build; refusing to start an unverified artifact.'
}
$jar = Get-ChildItem -LiteralPath $runtimeRoot -Filter 'bingo-backend-*.jar' -File |
    Where-Object { $_.Name -notmatch 'original|sources|javadoc' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw 'Packaged backend JAR was not found.' }
$jarHash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash
$commit = & git.exe -c safe.directory=D:/GitHub/bingo-platform -C $repoRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Could not identify the checked-out source revision.' }

$previousEnvironment = @{}
$environment = @{
    BINGO_TEST_MYSQL_URL = "jdbc:mysql://127.0.0.1:3306/$Database`?createDatabaseIfNotExist=false&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    BINGO_TEST_MYSQL_USERNAME = $config['user']
    BINGO_TEST_MYSQL_PASSWORD = $config['password']
    BINGO_QA_BUILD_COMMIT = ([string]($commit | Select-Object -First 1)).Trim()
    BINGO_QA_BUILD_SHA256 = $jarHash
    BINGO_QA_BUILD_SOURCE_SHA256 = $sourceFingerprint
    CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:5174'
}
foreach ($name in $environment.Keys) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, [string]$environment[$name], 'Process')
}
$resourceUri = ([Uri]::new(((Join-Path $backendRoot 'src\test\resources').TrimEnd('\') + '\'))).AbsoluteUri
$stdout = Join-Path $logRoot 'backend.stdout.log'
$stderr = Join-Path $logRoot 'backend.stderr.log'
    $javaArgs = @(
    '-jar', "`"$($jar.FullName)`"", "--spring.config.additional-location=$resourceUri",
    '--spring.profiles.active=android-test', '--server.address=127.0.0.1', '--server.port=8080',
    "--app.qa.test-runtime-expected-database=$Database", '--app.qa.test-runtime-identity-reporting-enabled=true',
    '--app.qa.android-test-fixtures-bootstrap-enabled=false', '--app.game.draw-scheduler-enabled=false',
    '--app.wallet.deposit-simulation-enabled=false', '--app.wallet.adjustment-simulation-enabled=false',
    '--app.agent.float-simulation-enabled=false', '--app.qa.synthetic-financial-tests-enabled=false',
    '--spring.jpa.hibernate.ddl-auto=validate', '--spring.flyway.enabled=true',
    '--spring.flyway.clean-disabled=true', '--spring.flyway.validate-on-migrate=true'
)
$process = $null
try {
    $process = Start-Process -FilePath 'java' -ArgumentList $javaArgs -WorkingDirectory $backendRoot `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
} finally {
    foreach ($name in $environment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
    $config['password'] = $null
    $environment['BINGO_TEST_MYSQL_PASSWORD'] = $null
}

$verified = $false
$runtimePattern = 'QA_TEST_RUNTIME_IDENTITY pid=' + $process.Id + ' profiles=android-test database=' +
    [regex]::Escape($Database) + ' jdbcHost=127\.0\.0\.1 jdbcPort=3306 flyway=40 '
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    Start-Sleep -Seconds 1
    if ($process.HasExited) { break }
    $captured = @()
    if (Test-Path -LiteralPath $stdout) { $captured += Get-Content -LiteralPath $stdout -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $stderr) { $captured += Get-Content -LiteralPath $stderr -ErrorAction SilentlyContinue }
    if (($captured -join "`n") -match $runtimePattern -and
        ($captured -join "`n") -match 'QA_TEST_RUNTIME_GUARDS drawScheduler=disabled signupRewardPosting=disabled depositSimulation=disabled adjustmentSimulation=disabled agentFloatSimulation=disabled') {
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3
            if ($health.status -eq 'UP') { $verified = $true; break }
        } catch { }
    }
}
if (-not $verified) {
    if ($null -ne $process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force }
    throw "Clone backend identity/health verification failed. Preserve logs at $logRoot; do not retry automatically."
}

$status = [ordered]@{
    verifiedAt = (Get-Date).ToString('o')
    profile = 'android-test'
    database = $Database
    jdbcHost = '127.0.0.1'
    jdbcPort = 3306
    flyway = 40
    backendUrl = 'http://127.0.0.1:8080'
    emulatorBackendUrl = 'http://10.0.2.2:8080'
    adminOrigin = 'http://127.0.0.1:5174'
    drawScheduler = 'disabled'
    financialPostingAndSimulation = 'disabled'
    processId = $process.Id
    processStartedAt = $process.StartTime.ToString('o')
    artifactSha256 = $jarHash
    sourceSha256 = $sourceFingerprint
    sourceCommit = ([string]($commit | Select-Object -First 1)).Trim()
}
$status | ConvertTo-Json | Set-Content -LiteralPath $identityFile -Encoding utf8
Write-Host "Verified isolated V40 clone backend at http://127.0.0.1:8080; no migration was run."
Write-Host "Sanitized identity evidence: $identityFile"
