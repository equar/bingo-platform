<#+
.SYNOPSIS
Runs the Player or Agent Flutter app against the local Bingo backend.

.EXAMPLE
.\scripts\Run-BingoLocal.ps1 -App player -Platform android
.\scripts\Run-BingoLocal.ps1 -App agent -Platform chrome
.\scripts\Run-BingoLocal.ps1 -App player -Platform android -DeviceId emulator-5554
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('player', 'agent')]
    [string] $App,

    [Parameter(Mandatory)]
    [ValidateSet('android', 'chrome', 'windows')]
    [string] $Platform,

    [string] $DeviceId
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$projectDirectory = Join-Path $repoRoot $(if ($App -eq 'player') { 'bingo-app' } else { 'bingo-agent' })
$entryPoint = Join-Path $projectDirectory 'lib\main.dart'
$apiHost = if ($Platform -eq 'android') { '10.0.2.2' } else { 'localhost' }
$apiBaseUrl = "http://${apiHost}:8080"
$webSocketBaseUrl = "ws://${apiHost}:8080/ws"
$webPort = if ($App -eq 'player') { 5180 } else { 5181 }

if (-not (Test-Path -LiteralPath $projectDirectory -PathType Container)) {
    throw "The $App project was not found at '$projectDirectory'."
}
if (-not (Test-Path -LiteralPath $entryPoint -PathType Leaf)) {
    throw "The Flutter entry point was not found at '$entryPoint'."
}
if ($DeviceId -and $Platform -ne 'android') {
    throw '-DeviceId is only used with -Platform android.'
}

$flutterCommand = Get-Command flutter -ErrorAction SilentlyContinue
if ($null -eq $flutterCommand) {
    throw 'Flutter was not found on PATH. Install Flutter 3.41 or newer, add its bin directory to PATH, then open a new terminal.'
}
$flutter = $flutterCommand.Source

function Get-FlutterJson {
    param([Parameter(Mandatory)][string[]] $Arguments)

    $output = & $flutter @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        $details = ($output | Out-String).Trim()
        throw "Flutter could not enumerate devices. $details`nCheck Flutter and Android SDK setup with 'flutter doctor -v'."
    }

    try {
        return ,(@(($output | Out-String) | ConvertFrom-Json))
    }
    catch {
        throw "Flutter returned invalid device information. Run 'flutter devices' and check the Flutter installation."
    }
}

function Get-AndroidDevices {
    $allDevices = Get-FlutterJson -Arguments @('devices', '--machine')
    return @($allDevices | Where-Object {
        $_.targetPlatform -like 'android*' -or $_.id -like 'emulator-*'
    })
}

function Get-ConfiguredEmulators {
    return @(Get-FlutterJson -Arguments @('emulators', '--machine'))
}

function Select-FromList {
    param(
        [Parameter(Mandatory)][object[]] $Items,
        [Parameter(Mandatory)][string] $Prompt,
        [Parameter(Mandatory)][scriptblock] $Label
    )

    for ($index = 0; $index -lt $Items.Count; $index++) {
        Write-Host ("  [{0}] {1}" -f ($index + 1), (& $Label $Items[$index]))
    }
    $selection = Read-Host $Prompt
    $number = 0
    if (-not [int]::TryParse($selection, [ref] $number) -or $number -lt 1 -or $number -gt $Items.Count) {
        throw 'The selection was not valid. Run the launcher again and choose a listed number.'
    }
    return $Items[$number - 1]
}

function Wait-ForAndroidDevice {
    param(
        [string] $ExpectedId,
        [string] $ExpectedName,
        [string[]] $ExistingDeviceIds = @()
    )

    $description = if ($ExpectedName) { $ExpectedName } else { $ExpectedId }
    Write-Host "Waiting for Android device '$description' to finish starting..."
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        Start-Sleep -Seconds 2
        $devices = Get-AndroidDevices
        $device = $devices | Where-Object {
            $_.id -eq $ExpectedId -or
            ($ExpectedName -and $_.name -eq $ExpectedName) -or
            $_.id -notin $ExistingDeviceIds
        } | Select-Object -First 1
        if ($null -ne $device) {
            return [string] $device.id
        }
    }
    throw "Android device '$description' did not become ready. Start it from Android Studio Device Manager and retry."
}

function Resolve-AndroidDevice {
    $devices = Get-AndroidDevices
    if ($DeviceId) {
        $connected = $devices | Where-Object id -eq $DeviceId | Select-Object -First 1
        if ($null -ne $connected) {
            return [string] $connected.id
        }

        $emulators = Get-ConfiguredEmulators
        $emulator = $emulators | Where-Object { $_.id -eq $DeviceId -or $_.name -eq $DeviceId } | Select-Object -First 1
        if ($null -eq $emulator) {
            $available = @($devices | ForEach-Object id) -join ', '
            if (-not $available) { $available = 'none connected' }
            throw "Device '$DeviceId' is not connected or a configured emulator. Android devices: $available. Run 'flutter emulators' to list emulator IDs."
        }
        $existingIds = @($devices | ForEach-Object { [string] $_.id })
        & $flutter emulators --launch $emulator.id
        if ($LASTEXITCODE -ne 0) {
            throw "Could not start emulator '$($emulator.id)'. Open it from Android Studio Device Manager and retry."
        }
        return Wait-ForAndroidDevice -ExpectedId ([string] $emulator.id) -ExpectedName ([string] $emulator.name) -ExistingDeviceIds $existingIds
    }

    if ($devices.Count -eq 1) {
        return [string] $devices[0].id
    }
    if ($devices.Count -gt 1) {
        $device = Select-FromList -Items $devices -Prompt 'Select an Android device number' -Label {
            param($item) "{0} ({1})" -f $item.name, $item.id
        }
        return [string] $device.id
    }

    $emulators = Get-ConfiguredEmulators
    if ($emulators.Count -eq 0) {
        throw "No Android device or emulator is available. Start a device from Android Studio Device Manager, or create an emulator, then run 'flutter doctor -v'."
    }
    if ($emulators.Count -eq 1) {
        $emulator = $emulators[0]
    }
    else {
        Write-Host 'No Android device is running. Configured emulators:'
        $emulator = Select-FromList -Items $emulators -Prompt 'Select an emulator to start' -Label {
            param($item) "{0} ({1})" -f $item.name, $item.id
        }
    }

    Write-Host "Starting emulator '$($emulator.name)'..."
    & $flutter emulators --launch $emulator.id
    if ($LASTEXITCODE -ne 0) {
        throw "Could not start emulator '$($emulator.id)'. Start it from Android Studio Device Manager and retry."
    }
    return Wait-ForAndroidDevice -ExpectedId ([string] $emulator.id) -ExpectedName ([string] $emulator.name)
}

function Test-LocalBackend {
    try {
        $response = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 3 -UseBasicParsing -SkipHttpErrorCheck
        return $response.StatusCode -eq 200
    }
    catch {
        return $false
    }
}

if ($Platform -eq 'chrome') {
    if ($env:CHROME_EXECUTABLE) {
        if (-not (Test-Path -LiteralPath $env:CHROME_EXECUTABLE -PathType Leaf)) {
            throw "CHROME_EXECUTABLE does not point to a file: '$env:CHROME_EXECUTABLE'."
        }
        $chromePaths = @($env:CHROME_EXECUTABLE)
    }
    else {
        $chromePaths = @(
            (Join-Path ${env:ProgramFiles} 'Google\Chrome\Application\chrome.exe'),
            (Join-Path ${env:ProgramFiles(x86)} 'Google\Chrome\Application\chrome.exe'),
            (Join-Path $env:LOCALAPPDATA 'Google\Chrome\Application\chrome.exe')
        ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) }
    }
    if (-not $chromePaths) {
        throw 'Chrome was not found. Install Google Chrome or set CHROME_EXECUTABLE to its executable path.'
    }
    $env:CHROME_EXECUTABLE = $chromePaths[0]
}

if ($Platform -eq 'windows') {
    $vsWhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    $hasCppTools = $false
    if (Test-Path -LiteralPath $vsWhere) {
        $install = & $vsWhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
        $hasCppTools = [bool] $install
    }
    if (-not $hasCppTools -and -not (Get-Command cl.exe -ErrorAction SilentlyContinue)) {
        throw 'Windows desktop builds require Visual Studio 2022 Build Tools with the Desktop development with C++ workload and a Windows 10/11 SDK.'
    }
    $flutterConfig = (& $flutter config --list 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $flutterConfig -notmatch 'enable-windows-desktop:\s*true') {
        throw "Enable Flutter Windows desktop support with 'flutter config --enable-windows-desktop', then retry."
    }
}

$device = switch ($Platform) {
    'android' { Resolve-AndroidDevice }
    'chrome' { 'chrome' }
    'windows' { 'windows' }
}

if ($Platform -eq 'chrome') {
    $portListener = [System.Net.Sockets.TcpClient]::new()
    $portOccupied = $false
    try {
        $portListener.Connect('localhost', $webPort)
        $portOccupied = $portListener.Connected
    }
    catch {
        $portOccupied = $false
    }
    finally {
        $portListener.Dispose()
    }
    if ($portOccupied) {
        throw "Port $webPort is already in use. Stop the app using that port or choose the other app's separate Chrome launch. No processes were stopped."
    }
}

Write-Host "App:     Bingo $($App.Substring(0, 1).ToUpperInvariant())$($App.Substring(1))"
Write-Host "Platform: $Platform"
if ($Platform -eq 'android') { Write-Host "Device:   $device" }
Write-Host "Backend:  $apiBaseUrl"
if ($App -eq 'player') { Write-Host "WebSocket: $webSocketBaseUrl" }
if (Test-LocalBackend) {
    Write-Host 'Backend health: UP'
}
else {
    Write-Warning "Backend health check failed. Start MySQL and run 'mvn spring-boot:run -Dspring-boot.run.profiles=dev' from bingo-backend."
}

$arguments = @(
    'run', '--debug', '-d', $device,
    "--dart-define=API_BASE_URL=$apiBaseUrl"
)
if ($App -eq 'player') {
    $arguments += "--dart-define=WS_BASE_URL=$webSocketBaseUrl"
}
if ($Platform -eq 'chrome') {
    $arguments += @('--web-hostname=localhost', "--web-port=$webPort")
}

Push-Location $projectDirectory
$runExitCode = 0
try {
    & $flutter @arguments
    $runExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}
if ($runExitCode -ne 0) {
    throw "Flutter exited with code $runExitCode. Check the Flutter output above for the build or device error."
}
