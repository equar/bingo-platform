[CmdletBinding()]
param(
    [string]$AdminUrl = 'http://127.0.0.1:5174',
    [switch]$LoginReadinessOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$identityFile = Join-Path $repoRoot 'docs\qa\evidence\module-06a5\backend-identity-preflight.json'
$policyModule = Join-Path $PSScriptRoot 'AndroidTestBackendPolicy.psm1'
$adminConfig = Join-Path $repoRoot 'bingo-admin\.env.qa'
$agentConfig = Join-Path $repoRoot 'bingo-agent\e2e-secrets.json'
$testFile = Join-Path $repoRoot 'bingo-admin\tests\e2e\module-06a9-agent-approval.spec.ts'
$evidenceId = 'QA_M06A9_' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff')
$testPath = if ($LoginReadinessOnly) { 'tests/e2e/module-06a9-admin-login-readiness.spec.ts' } else { 'tests/e2e/module-06a9-agent-approval.spec.ts' }
$logSuffix = if ($LoginReadinessOnly) { 'admin-login-readiness' } else { 'admin-approval' }
$logPath = Join-Path $repoRoot "docs\qa\evidence\module-06a9\$evidenceId-$logSuffix.log"
Import-Module $policyModule -Force

if (-not (Test-Path -LiteralPath $identityFile)) {
    throw 'BLOCKED: guarded Android-test backend identity evidence is missing.'
}
$identity = Get-Content -LiteralPath $identityFile -Raw | ConvertFrom-Json
$listenerPids = @(Get-AndroidTestTcpListenerPids -Port 8082)
if ($listenerPids.Count -ne 1) { throw 'BLOCKED: exactly one verified backend listener is required on port 8082.' }
$listenerPid = [int]$listenerPids[0]
if ($listenerPid -ne [int]$identity.processId) { throw 'BLOCKED: 8082 listener does not match the saved isolated backend PID.' }
$backendProcess = Get-Process -Id $listenerPid -ErrorAction Stop
if ($backendProcess.ProcessName -ne 'java' -or [System.IO.Path]::GetFileName($backendProcess.Path) -ne 'java.exe') { throw 'BLOCKED: 8082 listener process is not Java.' }
$logDir = Join-Path $env:TEMP 'bingo-platform-module-06a5'
$runtimeLogs = ((Get-Content -LiteralPath (Join-Path $logDir 'backend.stdout.log') -ErrorAction SilentlyContinue) +
    (Get-Content -LiteralPath (Join-Path $logDir 'backend.stderr.log') -ErrorAction SilentlyContinue)) -join "`n"
$identityLine = ([regex]::Matches($runtimeLogs, '(?m)^.*QA_TEST_RUNTIME_IDENTITY .*?$') | Select-Object -Last 1).Value
$guardLine = ([regex]::Matches($runtimeLogs, '(?m)^.*QA_TEST_RUNTIME_GUARDS .*?$') | Select-Object -Last 1).Value
$health = Invoke-RestMethod -Uri 'http://127.0.0.1:8082/actuator/health' -TimeoutSec 5
$databaseHealth = if ($health.components -and $health.components.db) { [string]$health.components.db.status } else { 'DOWN' }
$jar = if ($identity.artifactPath) {
    Get-Item -LiteralPath ([string]$identity.artifactPath) -ErrorAction SilentlyContinue
} else {
    Get-ChildItem -LiteralPath (Join-Path $repoRoot 'bingo-backend\target') -Filter 'bingo-backend-*.jar' -File |
        Where-Object Name -NotMatch 'original|sources|javadoc' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}
$jarMatches = $jar -and (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash -eq $identity.artifactSha256
$resourceUri = ([Uri]::new(((Join-Path $repoRoot 'bingo-backend\src\test\resources').TrimEnd('\') + '\'))).AbsoluteUri
$jarPath = if ($jar) { [System.IO.Path]::GetFullPath($jar.FullName) } else { '' }
if (-not $jarPath -or -not (Test-AndroidTestProcessCommandLine -ProcessId $listenerPid -JarPath $jarPath -TestResourcesUri $resourceUri)) {
    throw 'BLOCKED: native process inspection could not verify the exact QA JAR and android-test launch arguments.'
}
$sourceFingerprint = Get-AndroidTestBackendSourceFingerprint -RepositoryRoot $repoRoot
$sourceMatches = $sourceFingerprint -eq $identity.sourceSha256
$identityLog = [regex]::Match($identityLine, '^(?<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3})')
$identityTime = if ($identityLog.Success) { [DateTimeOffset]::Parse($identityLog.Groups['time'].Value) } else { [DateTimeOffset]::MinValue }
$processStartedAt = [DateTimeOffset]$backendProcess.StartTime
if ($identityTime -lt $processStartedAt -or $identityTime -gt $processStartedAt.AddMinutes(5)) { throw 'BLOCKED: runtime identity log is not contemporaneous with the listener process start.' }
$null = Assert-AndroidTestBackendRuntimeIdentity `
    -BackendIdentity $identity `
    -RuntimeIdentityLine $identityLine `
    -RuntimeGuardsLine $guardLine `
    -ListenerProcessId $listenerPid `
    -BackendHealth ([string]$health.status) `
    -DatabaseHealth $databaseHealth `
    -BackendJarMatches ([bool]$jarMatches) `
    -BackendSourceMatches ([bool]$sourceMatches)

if ($AdminUrl.TrimEnd('/') -ne 'http://127.0.0.1:5174') {
    throw 'BLOCKED: Admin E2E must use the guarded local QA Vite port 5174.'
}
$adminHealth = Invoke-WebRequest -Uri $AdminUrl -TimeoutSec 5 -UseBasicParsing
if ([int]$adminHealth.StatusCode -ne 200) { throw 'BLOCKED: isolated Admin Vite server is not healthy.' }
if (-not (Test-Path -LiteralPath $adminConfig) -or -not (Test-Path -LiteralPath $agentConfig)) {
    throw 'BLOCKED: ignored local Admin or Agent configuration is missing.'
}
foreach ($path in @($adminConfig, $agentConfig)) {
    $relative = $path.Substring($repoRoot.Length).TrimStart('\').Replace('\', '/')
    & git -c safe.directory=D:/GitHub/bingo-platform -C $repoRoot check-ignore -q $relative
    if ($LASTEXITCODE -ne 0) { throw 'BLOCKED: E2E credentials must remain in ignored local configuration.' }
}

$adminSettings = @{}
foreach ($line in Get-Content -LiteralPath $adminConfig) {
    if ($line -match '^\s*([A-Za-z0-9_]+)=(.*)\s*$') {
        $adminSettings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
    }
}
$agentSettings = Get-Content -LiteralPath $agentConfig -Raw | ConvertFrom-Json
$email = [string]$adminSettings['BINGO_E2E_ADMIN_EMAIL']
$password = [string]$adminSettings['BINGO_E2E_ADMIN_PASSWORD']
$candidatePhone = [string]$agentSettings.BINGO_QA_APPROVED_AGENT_PHONE
$pendingPhone = [string]$agentSettings.BINGO_QA_PENDING_AGENT_PHONE
if (-not $email -or -not $password -or -not $candidatePhone -or -not $pendingPhone) {
    throw 'BLOCKED: Admin approval E2E local credentials are incomplete.'
}

$env:BINGO_E2E_ADMIN_EMAIL = $email
$env:BINGO_E2E_ADMIN_PASSWORD = $password
$env:BINGO_E2E_ADMIN_URL = $AdminUrl.TrimEnd('/')
$env:BINGO_E2E_BACKEND_URL = 'http://127.0.0.1:8082'
$env:BINGO_QA_APPROVED_AGENT_PHONE = $candidatePhone
$env:BINGO_QA_PENDING_AGENT_PHONE = $pendingPhone
New-Item -ItemType Directory -Path (Split-Path -Parent $logPath) -Force | Out-Null
try {
    Push-Location (Join-Path $repoRoot 'bingo-admin')
    try {
        $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
        $arguments = @('run', 'e2e', '--', '--project=chromium', $testPath)
        $output = & $npm @arguments 2>&1
        $exitCode = $LASTEXITCODE
        foreach ($entry in $output) {
            $line = [string]$entry
            foreach ($sensitive in @($email, $password, $candidatePhone, $pendingPhone,
                    [string]$agentSettings.BINGO_QA_APPROVED_AGENT_PIN,
                    [string]$agentSettings.BINGO_QA_PENDING_AGENT_PIN)) {
                if (-not [string]::IsNullOrEmpty($sensitive)) { $line = $line.Replace($sensitive, '[REDACTED]') }
            }
            $line
            Add-Content -LiteralPath $logPath -Value $line
        }
        if ($exitCode -ne 0) { throw "Admin Chromium Agent-approval E2E failed with exit code $exitCode." }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($key in @(
        'BINGO_E2E_ADMIN_EMAIL', 'BINGO_E2E_ADMIN_PASSWORD',
        'BINGO_E2E_ADMIN_URL', 'BINGO_E2E_BACKEND_URL',
        'BINGO_QA_APPROVED_AGENT_PHONE', 'BINGO_QA_PENDING_AGENT_PHONE'
    )) { Remove-Item -Path "Env:\$key" -ErrorAction SilentlyContinue }
    $password = $null
    $adminSettings['BINGO_E2E_ADMIN_PASSWORD'] = $null
}

if ($LoginReadinessOnly) {
    Write-Host "PASS: Admin Chromium login readiness and retained Agent state check; sanitized evidence: $logPath"
} else {
    Write-Host "PASS: Admin Chromium normal approval flow; sanitized evidence: $logPath"
}
