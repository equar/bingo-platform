[CmdletBinding()]
param(
    [string]$BackendUrl = 'http://10.0.2.2:8082'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$statusFile = Join-Path $repoRoot 'docs\qa\evidence\module-06a5\backend-identity-preflight.json'
$sdk = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_USER_HOME = Join-Path $env:USERPROFILE '.android'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
$env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
$env:Path = "$sdk\platform-tools;$sdk\emulator;C:\src\flutter\bin;$env:Path"

if (-not (Test-Path -LiteralPath $statusFile)) {
    throw 'Run Start-AndroidTestBackend.ps1 first and verify its sanitized datasource identity evidence.'
}
$status = Get-Content -LiteralPath $statusFile -Raw | ConvertFrom-Json
if ($status.profile -ne 'android-test' -or $status.database -ne 'bingo_qa_test' -or $status.drawScheduler -ne 'disabled') {
    throw 'Android launch refused: isolated backend identity or disabled-scheduler guard is missing.'
}
if ($BackendUrl.TrimEnd('/') -ne ([string]$status.emulatorBackendUrl).TrimEnd('/')) {
    throw 'Android launch refused: API_BASE_URL must match the verified isolated backend target.'
}
$health = Invoke-RestMethod -Uri "$($status.backendUrl)/actuator/health" -TimeoutSec 5
if ($health.status -ne 'UP') { throw 'Isolated E2E backend health is not UP.' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'Android SDK adb.exe was not found.' }
$devices = & $adb devices -l
if ($LASTEXITCODE -ne 0 -or ($devices -join "`n") -notmatch '(?m)^emulator-5554\s+device\b') {
    throw 'emulator-5554 is not connected. No app was launched.'
}
$flutter = 'C:\src\flutter\bin\flutter.bat'
if (-not (Test-Path -LiteralPath $flutter)) { throw 'Flutter SDK was not found at C:\src\flutter.' }
$flutterDevices = & $flutter devices
if ($LASTEXITCODE -ne 0 -or ($flutterDevices -join "`n") -notmatch 'emulator-5554') {
    throw 'Flutter does not report emulator-5554. No app was launched.'
}

Write-Host "Launching the real player app against $BackendUrl. No ticket purchase or fixture is initiated by this command."
Push-Location (Join-Path $repoRoot 'bingo-app')
try {
    & $flutter run -d emulator-5554 --dart-define="API_BASE_URL=$BackendUrl" --dart-define=BINGO_PLAYER_PURCHASES_ENABLED=false
    if ($LASTEXITCODE -ne 0) { throw 'Flutter app launch failed.' }
} finally {
    Pop-Location
}
