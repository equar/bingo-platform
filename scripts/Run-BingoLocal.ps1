<#+
.SYNOPSIS
Runs the Player Flutter app against the local Bingo backend.

.EXAMPLE
.\scripts\Run-BingoLocal.ps1 -App player -Platform android
.\scripts\Run-BingoLocal.ps1 -App player -Platform android -DeviceId emulator-5554
.\scripts\Run-BingoLocal.ps1 -App player -Platform android -CleanRebuild
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('player')]
    [string] $App,

    [Parameter(Mandatory)]
    [ValidateSet('android', 'chrome', 'windows')]
    [string] $Platform,

    [string] $DeviceId,

    [switch] $CleanRebuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$projectDirectory = Join-Path $repoRoot 'bingo-player'
$entryPoint = Join-Path $projectDirectory 'lib\main.dart'
$apiHost = if ($Platform -eq 'android') { '10.0.2.2' } else { 'localhost' }
$apiBaseUrl = "http://${apiHost}:8080"
$webSocketBaseUrl = "ws://${apiHost}:8080/ws"
$webPort = 5180

if (-not (Test-Path -LiteralPath $projectDirectory -PathType Container)) {
    throw "The $App project was not found at '$projectDirectory'."
}
if (-not (Test-Path -LiteralPath $entryPoint -PathType Leaf)) {
    throw "The Flutter entry point was not found at '$entryPoint'."
}
if ($DeviceId -and $Platform -ne 'android') {
    throw '-DeviceId is only used with -Platform android.'
}

# PowerShell processes started before a user environment change do not inherit
# it. Read the persisted setting when available, then pass it to Flutter and
# Gradle as a process-scoped override without hard-coding a developer path.
if ([string]::IsNullOrWhiteSpace($env:PUB_CACHE)) {
    $userPubCache = [Environment]::GetEnvironmentVariable('PUB_CACHE', 'User')
    if (-not [string]::IsNullOrWhiteSpace($userPubCache)) {
        $env:PUB_CACHE = $userPubCache
    }
}

$profilePath = $env:USERPROFILE
if (-not [string]::IsNullOrWhiteSpace($profilePath) -and [System.IO.Path]::IsPathRooted($profilePath)) {
    if ([string]::IsNullOrWhiteSpace($env:ANDROID_USER_HOME)) {
        $profileAndroidHome = Join-Path $profilePath '.android'
        if (Test-Path -LiteralPath $profileAndroidHome -PathType Container) {
            $env:ANDROID_USER_HOME = $profileAndroidHome
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:ANDROID_EMULATOR_HOME) -and $env:ANDROID_USER_HOME) {
        $env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
    }
    if ([string]::IsNullOrWhiteSpace($env:ANDROID_AVD_HOME) -and $env:ANDROID_USER_HOME) {
        $env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
    }
    if ([string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
        $profileGradleHome = Join-Path $profilePath '.gradle'
        if (Test-Path -LiteralPath $profileGradleHome -PathType Container) {
            $env:GRADLE_USER_HOME = $profileGradleHome
        }
    }
}

if ($Platform -eq 'android' -and [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $localProperties = Join-Path $projectDirectory 'android\local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) {
            $sdkPath = ($sdkLine -replace '^sdk\.dir=', '').Replace('\\', '\')
            if (Test-Path -LiteralPath $sdkPath -PathType Container) {
                $env:ANDROID_HOME = $sdkPath
            }
        }
    }
}
if ($Platform -eq 'android' -and [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT) -and $env:ANDROID_HOME) {
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
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
        if ($details -match 'Cannot mkdir') {
            throw "Android tools could not access the Windows user profile (ADB reported '$details'). ANDROID_USER_HOME cannot repair this ADB profile API failure in the current shell. Run this launcher from normal Windows PowerShell under your developer account."
        }
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

$packageConfiguration = Join-Path $projectDirectory '.dart_tool\package_config.json'
$dependencyInputs = @('pubspec.yaml', 'pubspec.lock', 'pubspec_overrides.yaml') |
    ForEach-Object { Join-Path $projectDirectory $_ } |
    Where-Object { Test-Path -LiteralPath $_ -PathType Leaf }
$resolveDependencies = -not (Test-Path -LiteralPath $packageConfiguration -PathType Leaf)
if (-not $resolveDependencies) {
    $configurationTime = (Get-Item -LiteralPath $packageConfiguration).LastWriteTimeUtc
    $newerDependencyInput = $dependencyInputs | Where-Object {
        (Get-Item -LiteralPath $_).LastWriteTimeUtc -gt $configurationTime
    } | Select-Object -First 1
    if ($newerDependencyInput) {
        Write-Host "Dependency configuration is older than '$([System.IO.Path]::GetFileName($newerDependencyInput))'."
        $resolveDependencies = $true
    }
}
if (-not $resolveDependencies -and -not [string]::IsNullOrWhiteSpace($env:PUB_CACHE)) {
    try {
        $cacheRoot = [System.IO.Path]::GetFullPath($env:PUB_CACHE).TrimEnd('\', '/')
        $cachePrefix = $cacheRoot + [System.IO.Path]::DirectorySeparatorChar
        $configurationUri = [Uri] (Get-Item -LiteralPath $packageConfiguration).FullName
        $packageConfig = Get-Content -LiteralPath $packageConfiguration -Raw | ConvertFrom-Json
        foreach ($package in $packageConfig.packages) {
            $packageRootUri = [Uri]::new($configurationUri, [string] $package.rootUri)
            if (-not $packageRootUri.IsFile) { continue }
            $packageRoot = [System.IO.Path]::GetFullPath($packageRootUri.LocalPath)
            $usesPubCache = $packageRoot -match '[\\/](hosted|git)[\\/]'
            if ($usesPubCache -and -not $packageRoot.StartsWith($cachePrefix, [StringComparison]::OrdinalIgnoreCase)) {
                Write-Host "Package '$($package.name)' is configured outside PUB_CACHE."
                $resolveDependencies = $true
                break
            }
        }
    }
    catch {
        Write-Host 'Package configuration could not be validated; resolving dependencies again.'
        $resolveDependencies = $true
    }
}
if ($resolveDependencies) {
    Write-Host 'Resolving Flutter dependencies for the current manifests and PUB_CACHE.'
    Push-Location $projectDirectory
    try {
        & $flutter pub get
        if ($LASTEXITCODE -ne 0) {
            throw "Flutter pub get exited with code $LASTEXITCODE. Resolve dependencies and retry."
        }
    }
    finally {
        Pop-Location
    }
}

$device = switch ($Platform) {
    'android' { Resolve-AndroidDevice }
    'chrome' { 'chrome' }
    'windows' { 'windows' }
}

if ($Platform -eq 'android') {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -eq $adbCommand) {
        throw 'ADB was not found on PATH. Install Android platform tools, then rerun the local launcher.'
    }

    & $adbCommand.Source -s $device reverse tcp:8080 tcp:8080
    if ($LASTEXITCODE -ne 0) {
        throw "Could not forward emulator port 8080 to the local backend for '$device'. Reconnect the virtual device through ADB and retry."
    }

    $apiBaseUrl = 'http://127.0.0.1:8080'
    $webSocketBaseUrl = 'ws://127.0.0.1:8080/ws'
}

# Resolve the target before any opt-in clean so restricted or unavailable ADB
# cannot erase generated build state without a viable device to launch on.
if ($CleanRebuild) {
    Write-Host "Cleaning generated Flutter/Android build state for Bingo $App only."
    Push-Location $projectDirectory
    try {
        & $flutter clean
        if ($LASTEXITCODE -ne 0) {
            throw "Flutter clean exited with code $LASTEXITCODE. No other app was cleaned."
        }
        & $flutter pub get
        if ($LASTEXITCODE -ne 0) {
            throw "Flutter pub get exited with code $LASTEXITCODE. Run 'flutter pub get' from '$projectDirectory' and retry."
        }
    }
    finally {
        Pop-Location
    }
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
        throw "Port $webPort is already in use. Stop the process using that port before launching Player. No processes were stopped."
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
    Write-Warning "Backend health check failed. Start MySQL and run '.\scripts\Start-BingoBackendLocal.ps1' from the repository root."
}

$arguments = @(
    'run', '--debug', '--no-pub', '-d', $device,
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
