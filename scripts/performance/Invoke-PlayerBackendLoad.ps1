[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet(100, 250, 500, 1000)]
    [int]$Players,
    [ValidateSet('read', 'purchase', 'purchase-retry', 'claim', 'claim-retry', 'settle-race', 'expired-jwt')]
    [string]$Mode = 'read',
    [ValidatePattern('^\d+[sm]$')]
    [string]$Duration = '5m',
    [string]$RunId = '',
    [string[]]$ContainerNames = @()
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$scriptPath = Join-Path $repoRoot 'performance/player-backend/k6/game-load.js'
$approval = [Environment]::GetEnvironmentVariable('DISPOSABLE_TARGET_APPROVED')
$approvedOrigin = [Environment]::GetEnvironmentVariable('APPROVED_ORIGIN')
$apiBase = [Environment]::GetEnvironmentVariable('API_BASE_URL')
$approvedMaxVus = [int]([Environment]::GetEnvironmentVariable('APPROVED_MAX_VUS'))
$approvedMaxRps = [int]([Environment]::GetEnvironmentVariable('APPROVED_MAX_RPS'))
$approvedMaxDbConnections = [int]([Environment]::GetEnvironmentVariable('APPROVED_MAX_DB_CONNECTIONS'))
$fixtures = [Environment]::GetEnvironmentVariable('PLAYER_FIXTURES_FILE')
$resultsRoot = [Environment]::GetEnvironmentVariable('PERF_RESULTS_DIR')
$approvedK6Version = [Environment]::GetEnvironmentVariable('APPROVED_K6_VERSION')
$expectedSchema = [Environment]::GetEnvironmentVariable('EXPECTED_DISPOSABLE_SCHEMA')
$expectedMysqlServerUuid = [Environment]::GetEnvironmentVariable('EXPECTED_DISPOSABLE_MYSQL_SERVER_UUID')
$targetGuardToken = [Environment]::GetEnvironmentVariable('BINGO_PERF_GUARD_TOKEN')

if ($approval -ne 'YES') { throw 'Set DISPOSABLE_TARGET_APPROVED=YES only after independently verifying a disposable environment.' }
if ([string]::IsNullOrWhiteSpace($apiBase) -or [string]::IsNullOrWhiteSpace($approvedOrigin)) { throw 'API_BASE_URL and APPROVED_ORIGIN are required.' }
if ([Uri]::new($apiBase).GetLeftPart([UriPartial]::Authority) -ne $approvedOrigin.TrimEnd('/')) { throw 'API_BASE_URL origin must exactly match APPROVED_ORIGIN.' }
if ($approvedMaxVus -lt $Players -or $approvedMaxVus -gt 1000) { throw 'APPROVED_MAX_VUS must be within the reviewed ceiling and at least the requested player count.' }
if ($approvedMaxRps -lt 1) { throw 'APPROVED_MAX_RPS must be explicitly set to an approved request-rate ceiling.' }
if ($approvedMaxDbConnections -lt 1) { throw 'APPROVED_MAX_DB_CONNECTIONS must be the explicit approved database connection ceiling.' }
if ($expectedSchema -notmatch '^bingo_perf_[a-z0-9_]{1,48}$') { throw 'EXPECTED_DISPOSABLE_SCHEMA must be a dedicated bingo_perf_<run> catalog.' }
if ($expectedMysqlServerUuid -notmatch '^[0-9a-fA-F-]{36}$') { throw 'EXPECTED_DISPOSABLE_MYSQL_SERVER_UUID must be the reviewed disposable MySQL server UUID.' }
if ([string]::IsNullOrWhiteSpace($targetGuardToken) -or $targetGuardToken.Length -lt 32) { throw 'BINGO_PERF_GUARD_TOKEN must be a random performance-only secret of at least 32 characters.' }
$durationMatch = [regex]::Match($Duration, '^(\d+)(s|m)$')
$durationSeconds = [int]$durationMatch.Groups[1].Value * $(if ($durationMatch.Groups[2].Value -eq 'm') { 60 } else { 1 })
if ($durationSeconds -lt 30 -or $durationSeconds -gt 1800) { throw 'Duration must be between 30 seconds and 30 minutes.' }
if (-not (Test-Path -LiteralPath $fixtures -PathType Leaf)) { throw 'PLAYER_FIXTURES_FILE must name an existing out-of-repository fixture file.' }
if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) { throw 'k6 is required; install a pinned version from the approved tooling source.' }
if ([string]::IsNullOrWhiteSpace($approvedK6Version)) { throw 'APPROVED_K6_VERSION must pin the reviewed k6 release.' }
if ([string]::IsNullOrWhiteSpace($RunId)) { $RunId = 'perf-' + (Get-Date -Format 'yyMMddHHmm') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6) }
if ($RunId -notmatch '^[a-zA-Z0-9_-]{6,24}$') { throw 'RunId must be 6-24 letters, digits, underscores, or hyphens.' }
if ([string]::IsNullOrWhiteSpace($resultsRoot)) { $resultsRoot = Join-Path $env:TEMP 'bingo-player-performance' }
$resultsRoot = [System.IO.Path]::GetFullPath($resultsRoot)
$repoPrefix = $repoRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
if ($resultsRoot.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase) -or $resultsRoot -eq $repoRoot) {
    throw 'PERF_RESULTS_DIR must be outside the source repository to avoid retaining tokens or operational output in source control.'
}

$env:PLAYERS = [string]$Players
$env:MODE = $Mode
$env:RUN_ID = $RunId
$env:DURATION = $Duration
$env:TARGET_GUARD_TOKEN = $targetGuardToken
$env:APPROVED_MAX_DB_CONNECTIONS = [string]$approvedMaxDbConnections
$artifactDir = Join-Path $resultsRoot $RunId
New-Item -ItemType Directory -Path $artifactDir -Force | Out-Null
$summaryPath = Join-Path $artifactDir 'k6-summary.json'
$outputPath = Join-Path $artifactDir 'k6-events.json'
$resourcePath = Join-Path $artifactDir 'host-container-resources.jsonl'

Write-Host "Target origin: $approvedOrigin"
Write-Host "Disposable run: $RunId; mode=$Mode; concurrent players=$Players; approved VU cap=$approvedMaxVus; approved RPS cap=$approvedMaxRps; approved DB pool cap=$approvedMaxDbConnections"
Write-Host 'Fixture data is not printed. Host/container resource samples are collected every five seconds.'
if ($ContainerNames.Count -gt 0 -and -not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker container sampling requested but docker is unavailable.' }
$k6Version = (& k6 version | Out-String).Trim()
if ($k6Version -notmatch [regex]::Escape($approvedK6Version)) { throw "Installed k6 version does not match APPROVED_K6_VERSION ($approvedK6Version)." }
$manifest = [ordered]@{
    runId = $RunId
    startedAtUtc = [DateTime]::UtcNow.ToString('o')
    mode = $Mode
    players = $Players
    approvedMaxVus = $approvedMaxVus
    approvedMaxRps = $approvedMaxRps
    approvedMaxDbConnections = $approvedMaxDbConnections
    targetOrigin = $approvedOrigin
    sessionId = [Environment]::GetEnvironmentVariable('SESSION_ID')
    k6Version = $k6Version
    sampledContainerNames = @($ContainerNames)
    buildRevision = [Environment]::GetEnvironmentVariable('BINGO_PERF_BUILD_REVISION')
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $artifactDir 'run-manifest.json') -Encoding utf8
$containerNamesJson = ConvertTo-Json -InputObject @($ContainerNames) -Compress

$sampler = Start-Job -ArgumentList $resourcePath, $containerNamesJson -ScriptBlock {
    param($Path, $NamesJson)
    $Names = @($NamesJson | ConvertFrom-Json)
    while ($true) {
        $sample = [ordered]@{ sampledAtUtc = [DateTime]::UtcNow.ToString('o') }
        try {
            $sample.cpuPercent = [Math]::Round((Get-Counter '\Processor(_Total)\% Processor Time').CounterSamples[0].CookedValue, 2)
            $sample.availableMemoryMb = [Math]::Round((Get-Counter '\Memory\Available MBytes').CounterSamples[0].CookedValue, 0)
        } catch { $sample.hostCounterError = $_.Exception.Message }
        if ($Names.Count -gt 0) {
            try { $sample.containers = @(& docker stats --no-stream --format '{{json .}}' @Names | ForEach-Object { $_ | ConvertFrom-Json }) }
            catch { $sample.containerCounterError = $_.Exception.Message }
        }
        ($sample | ConvertTo-Json -Compress -Depth 5) | Add-Content -LiteralPath $Path -Encoding utf8
        Start-Sleep -Seconds 5
    }
}
$k6ExitCode = 1
try {
    & k6 run --summary-export $summaryPath --out "json=$outputPath" $scriptPath
    $k6ExitCode = $LASTEXITCODE
} finally {
    Stop-Job -Job $sampler -ErrorAction SilentlyContinue
    Receive-Job -Job $sampler -ErrorAction SilentlyContinue | Out-Null
    Remove-Job -Job $sampler -Force -ErrorAction SilentlyContinue
}
$manifest.finishedAtUtc = [DateTime]::UtcNow.ToString('o')
$manifest.k6ExitCode = $k6ExitCode
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $artifactDir 'run-manifest.json') -Encoding utf8
if ($k6ExitCode -ne 0) { throw "k6 failed with exit code $k6ExitCode. Preserve results under $artifactDir and investigate before increasing load." }
Write-Host "k6 results: $artifactDir"
