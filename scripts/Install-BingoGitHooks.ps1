[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = (git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or -not $repoRoot) { throw 'Run the installer inside a Bingo Platform clone.' }
$configuredPath = git config --path --get core.hooksPath 2>$null
if ($LASTEXITCODE -eq 0 -and $configuredPath) {
    $hooksPath = [string]$configuredPath
    if (-not [IO.Path]::IsPathRooted($hooksPath)) { $hooksPath = Join-Path $repoRoot $hooksPath }
    Write-Host "Using the existing core.hooksPath: $hooksPath"
} else {
    $hooksPath = (git rev-parse --git-path hooks).Trim()
    if (-not [IO.Path]::IsPathRooted($hooksPath)) { $hooksPath = Join-Path $repoRoot $hooksPath }
    Write-Host "No core.hooksPath is configured; using Git's hooks directory: $hooksPath"
}

New-Item -ItemType Directory -Path $hooksPath -Force | Out-Null
$versionedHooks = Join-Path $repoRoot '.githooks'
foreach ($name in @('pre-commit', 'pre-push')) {
    $target = Join-Path $hooksPath $name
    $backup = "$target.bingo-platform-original"
    $source = Join-Path $versionedHooks $name
    if ([IO.Path]::GetFullPath($target).Equals([IO.Path]::GetFullPath($source), [StringComparison]::OrdinalIgnoreCase)) {
        Write-Host "core.hooksPath already points to the versioned .githooks directory; keeping $name in place."
        continue
    }
    if (Test-Path -LiteralPath $target -PathType Leaf) {
        $targetHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
        $sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
        if ($targetHash -ne $sourceHash) {
            $preserved = $false
            if (-not (Test-Path -LiteralPath $backup -PathType Leaf)) {
                Copy-Item -LiteralPath $target -Destination $backup
                Write-Host "Preserved existing $name hook at $backup; the installed shim will run it first."
                $preserved = $true
            } else {
                $alreadyPreserved = @(Get-ChildItem -LiteralPath $hooksPath -File -Filter "$name.bingo-platform-original*") |
                    Where-Object { (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash -eq $targetHash }
                if (-not $alreadyPreserved) {
                    $additionalBackup = "$target.bingo-platform-original-$(Get-Date -Format 'yyyyMMddHHmmssfff')"
                    Copy-Item -LiteralPath $target -Destination $additionalBackup
                    Write-Host "Preserved an additional existing $name hook at $additionalBackup; the installed shim will run it first."
                    $preserved = $true
                }
            }
            if (-not $preserved) { Write-Host "Existing $name hook is already preserved; refreshing the Bingo wrapper." }
        }
    }
    Copy-Item -LiteralPath $source -Destination $target -Force
}

Write-Host 'Bingo pre-commit and pre-push checks are installed. Run scripts/Install-BingoGitHooks.ps1 again to refresh them.'
