[CmdletBinding()]
param(
    [string]$BackendUrl,
    [string]$AdminUrl = 'http://127.0.0.1:5174'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$statusFile = Join-Path $repoRoot 'docs\qa\evidence\module-06a5\backend-identity-preflight.json'
$adminRoot = Join-Path $repoRoot 'bingo-admin'
if (-not (Test-Path -LiteralPath $statusFile)) {
    throw 'Run Start-AndroidTestBackend.ps1 first and verify its sanitized datasource identity evidence.'
}
$status = Get-Content -LiteralPath $statusFile -Raw | ConvertFrom-Json
if ($status.profile -ne 'android-test' -or $status.database -ne 'bingo_qa_test' -or $status.jdbcHost -ne '127.0.0.1') {
    throw 'Admin startup refused: backend identity evidence is not for the isolated local E2E backend.'
}
if ([string]::IsNullOrWhiteSpace($BackendUrl)) { $BackendUrl = [string]$status.backendUrl }
if ($BackendUrl.TrimEnd('/') -ne ([string]$status.backendUrl).TrimEnd('/')) {
    throw 'Admin startup refused: React Admin must target the exact backend URL recorded by the verified isolated backend process.'
}
$expectedAdminUrl = 'http://127.0.0.1:5174'
if ($AdminUrl.TrimEnd('/') -ne $expectedAdminUrl) {
    throw 'Admin startup refused: this QA Vite instance is bound to 127.0.0.1:5174.'
}
$health = Invoke-RestMethod -Uri "$BackendUrl/actuator/health" -TimeoutSec 5
if ($health.status -ne 'UP') { throw 'Isolated E2E backend health is not UP.' }
if (-not (Test-Path -LiteralPath (Join-Path $adminRoot 'node_modules'))) {
    throw 'Admin node_modules is missing; install dependencies as a separate setup action before starting E2E.'
}

# Vite's process environment overrides .env.qa, so React Admin and Playwright
# point at the same android-test backend. No credentials are placed in VITE_*.
$env:VITE_API_BASE_URL = $BackendUrl.TrimEnd('/')
$env:BINGO_E2E_BACKEND_URL = $BackendUrl.TrimEnd('/')
$env:BINGO_E2E_ADMIN_URL = $AdminUrl.TrimEnd('/')
Write-Host "React Admin API target: $($env:VITE_API_BASE_URL) (android-test / bingo_qa_test)"
Write-Host 'Starting Vite in the foreground. Stop this terminal with Ctrl+C when finished.'
Push-Location $adminRoot
try {
    & npm run dev -- --mode qa --host 127.0.0.1 --port 5174 --strictPort
    if ($LASTEXITCODE -ne 0) { throw 'React Admin dev server exited with an error.' }
} finally {
    Pop-Location
}
