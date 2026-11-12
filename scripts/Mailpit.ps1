[CmdletBinding()]
param(
    [ValidateSet('Install', 'Start', 'Stop', 'Status')]
    [string]$Action = 'Status'
)

$ErrorActionPreference = 'Stop'
$MailpitVersion = 'v1.33.1'
$InstallDirectory = Join-Path $env:LOCALAPPDATA 'BingoPlatform\bin'
$MailpitExecutable = Join-Path $InstallDirectory 'mailpit.exe'
$StateDirectory = Join-Path $env:LOCALAPPDATA 'BingoPlatform\Mailpit'
$DatabaseDirectory = Join-Path $StateDirectory 'data'
$DatabaseFile = Join-Path $DatabaseDirectory 'mailpit.db'
$ProcessIdFile = Join-Path $StateDirectory 'mailpit.pid'

function Test-LocalPort {
    param([int]$Port)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $client.Connect('127.0.0.1', $Port)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-ManagedMailpitProcess {
    if (-not (Test-Path -LiteralPath $ProcessIdFile)) {
        return $null
    }

    $processIdText = (Get-Content -LiteralPath $ProcessIdFile -Raw).Trim()
    $mailpitProcessId = 0
    if (-not [int]::TryParse($processIdText, [ref]$mailpitProcessId) -or $mailpitProcessId -le 0) {
        throw "The Mailpit PID file is invalid: $ProcessIdFile"
    }

    $process = Get-CimInstance -ClassName Win32_Process `
        -Filter "ProcessId = $mailpitProcessId" -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        Remove-Item -LiteralPath $ProcessIdFile -Force
        return $null
    }

    $actualExecutable = [System.IO.Path]::GetFullPath([string]$process.ExecutablePath)
    $expectedExecutable = [System.IO.Path]::GetFullPath($MailpitExecutable)
    if (-not [string]::Equals(
            $actualExecutable,
            $expectedExecutable,
            [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "The Mailpit PID file refers to a different process; refusing to manage PID $mailpitProcessId."
    }

    return $process
}

function Install-Mailpit {
    if (Test-Path -LiteralPath $MailpitExecutable) {
        $installedVersion = (& $MailpitExecutable version 2>&1) -join "`n"
        if ($installedVersion -notmatch [regex]::Escape($MailpitVersion.TrimStart('v'))) {
            throw "A different Mailpit version is already installed at $MailpitExecutable."
        }

        Write-Output "Mailpit $MailpitVersion is already installed at $MailpitExecutable."
        return
    }

    New-Item -ItemType Directory -Path $InstallDirectory -Force | Out-Null
    $stagingDirectory = Join-Path $env:TEMP ("bingo-mailpit-" + [guid]::NewGuid().ToString('N'))
    $archivePath = Join-Path $stagingDirectory 'mailpit.zip'
    New-Item -ItemType Directory -Path $stagingDirectory | Out-Null

    try {
        $downloadUrl = "https://github.com/axllent/mailpit/releases/download/$MailpitVersion/mailpit-windows-amd64.zip"
        Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath -UseBasicParsing
        Expand-Archive -LiteralPath $archivePath -DestinationPath $stagingDirectory

        $downloadedExecutable = Get-ChildItem -LiteralPath $stagingDirectory `
            -Filter 'mailpit.exe' -File -Recurse | Select-Object -First 1
        if ($null -eq $downloadedExecutable) {
            throw "The official Mailpit archive did not contain mailpit.exe: $downloadUrl"
        }

        Copy-Item -LiteralPath $downloadedExecutable.FullName -Destination $MailpitExecutable
        $installedVersion = (& $MailpitExecutable version 2>&1) -join "`n"
        if ($installedVersion -notmatch [regex]::Escape($MailpitVersion.TrimStart('v'))) {
            throw "The installed Mailpit binary did not report the expected version $MailpitVersion."
        }

        Write-Output "Installed Mailpit $MailpitVersion at $MailpitExecutable."
    } finally {
        if (Test-Path -LiteralPath $stagingDirectory) {
            Remove-Item -LiteralPath $stagingDirectory -Recurse -Force
        }
    }
}

function Start-Mailpit {
    if (-not (Test-Path -LiteralPath $MailpitExecutable)) {
        throw "Mailpit is not installed. Run .\scripts\Mailpit.ps1 -Action Install first."
    }

    $existingProcess = Get-ManagedMailpitProcess
    if ($null -ne $existingProcess) {
        if ((Test-LocalPort 1025) -and (Test-LocalPort 8025)) {
            Write-Output "Mailpit is already running (PID $($existingProcess.ProcessId))."
            return
        }

        throw "Managed Mailpit process $($existingProcess.ProcessId) is running but its ports are not ready. Check its log files under $StateDirectory."
    }

    foreach ($port in @(1025, 8025)) {
        if (Test-LocalPort $port) {
            throw "Port $port is already in use on 127.0.0.1; Mailpit was not started."
        }
    }

    New-Item -ItemType Directory -Path $DatabaseDirectory -Force | Out-Null
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $stdoutLog = Join-Path $StateDirectory "mailpit-$timestamp.out.log"
    $stderrLog = Join-Path $StateDirectory "mailpit-$timestamp.err.log"
    $databaseArgument = '--database="' + $DatabaseFile + '"'
    $process = Start-Process -FilePath $MailpitExecutable `
        -ArgumentList @(
            '--smtp=127.0.0.1:1025',
            '--listen=127.0.0.1:8025',
            $databaseArgument
        ) `
        -WorkingDirectory $InstallDirectory `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru
    Set-Content -LiteralPath $ProcessIdFile -Value $process.Id -NoNewline

    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        $process.Refresh()
        if ($process.HasExited) {
            Remove-Item -LiteralPath $ProcessIdFile -Force -ErrorAction SilentlyContinue
            throw "Mailpit exited during startup. Check $stderrLog and $stdoutLog."
        }

        if ((Test-LocalPort 1025) -and (Test-LocalPort 8025)) {
            Write-Output "Mailpit is listening on 127.0.0.1:1025 and 127.0.0.1:8025 (PID $($process.Id))."
            return
        }

        Start-Sleep -Milliseconds 500
    }

    throw "Mailpit did not become ready within 10 seconds. Check $stderrLog and $stdoutLog."
}

function Stop-Mailpit {
    $process = Get-ManagedMailpitProcess
    if ($null -eq $process) {
        Write-Output 'Mailpit is not running under this script.'
        return
    }

    Stop-Process -Id $process.ProcessId -ErrorAction Stop
    Remove-Item -LiteralPath $ProcessIdFile -Force -ErrorAction SilentlyContinue
    Write-Output "Stopped Mailpit (PID $($process.ProcessId))."
}

function Show-MailpitStatus {
    if (-not (Test-Path -LiteralPath $MailpitExecutable)) {
        Write-Output "Mailpit $MailpitVersion is not installed. Run .\scripts\Mailpit.ps1 -Action Install."
        return
    }

    $process = Get-ManagedMailpitProcess
    if ($null -eq $process) {
        Write-Output 'Mailpit is not running under this script.'
        return
    }

    $smtpReady = Test-LocalPort 1025
    $webReady = Test-LocalPort 8025
    Write-Output "Mailpit PID $($process.ProcessId): SMTP ready=$smtpReady, Web UI ready=$webReady."
}

switch ($Action) {
    'Install' { Install-Mailpit }
    'Start' { Start-Mailpit }
    'Stop' { Stop-Mailpit }
    'Status' { Show-MailpitStatus }
}
