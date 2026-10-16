param(
  [string]$RunId = (Get-Date -Format 'yyyyMMdd-HHmmss'),
  [string]$BackendUrl = 'http://localhost:8080',
  [string]$AdminUrl = 'http://localhost:5173',
  [string]$ResultsRoot = (Join-Path $PSScriptRoot '..\test-results\local-e2e')
)

$ErrorActionPreference = 'Stop'

$workspaceRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$adminRoot = Join-Path $workspaceRoot 'bingo-admin'
$playerRoot = Join-Path $workspaceRoot 'bingo-app'
$runDir = Join-Path $ResultsRoot $RunId
$manifestPath = Join-Path $runDir 'manifest.json'

New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$env:BINGO_E2E_RUN_ID = $RunId
$env:BINGO_E2E_BACKEND_URL = $BackendUrl
$env:BINGO_E2E_ADMIN_URL = $AdminUrl
$env:BINGO_E2E_MANIFEST_PATH = $manifestPath

function Invoke-AdminPhase {
  param([Parameter(Mandatory = $true)][string]$Phase)

  Push-Location $adminRoot
  try {
    $env:BINGO_E2E_PHASE = $Phase
    & npx playwright test tests/e2e/local-e2e.spec.ts --project=chromium
  }
  finally {
    Pop-Location
  }
}

function Invoke-PlayerPhase {
  param([Parameter(Mandatory = $true)][string]$Phase)

  Push-Location $playerRoot
  try {
    & flutter test integration_test/local_e2e_test.dart `
      --dart-define=API_BASE_URL=$BackendUrl `
      --dart-define=BINGO_E2E_RUN_ID=$RunId `
      --dart-define=BINGO_E2E_MANIFEST_PATH=$manifestPath `
      --dart-define=BINGO_E2E_PHASE=$Phase
  }
  finally {
    Pop-Location
  }
}

Invoke-AdminPhase -Phase 'setup'
Invoke-PlayerPhase -Phase 'setup'
Invoke-AdminPhase -Phase 'review-deposits'
Invoke-PlayerPhase -Phase 'purchase'
Invoke-AdminPhase -Phase 'draw-session'
Invoke-PlayerPhase -Phase 'claim'
Invoke-AdminPhase -Phase 'review-claim'
Invoke-PlayerPhase -Phase 'verify'

Write-Host "Local E2E complete. Manifest: $manifestPath"
