[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('PreCommit', 'PrePush', 'Manual')]
    [string]$Mode
)

$ErrorActionPreference = 'Stop'
$repoRoot = (git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or -not $repoRoot) { throw 'Run this check inside the Bingo Platform Git repository.' }
$runner = Join-Path $repoRoot 'scripts/ci/run-component-checks.mjs'

function Invoke-Git([string[]]$Arguments) {
    $output = & git @Arguments
    if ($LASTEXITCODE -ne 0) { throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE." }
    return $output
}

function Get-ChangedPaths([string]$Base, [string]$Head) {
    $range = "$Base..$Head"
    $raw = & git diff --name-only -z $range 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) { return [pscustomobject]@{ Success = $false; Paths = @(); Error = ($raw -join [Environment]::NewLine) } }
    $paths = @(([string]($raw -join '') -split "`0") | Where-Object { $_ })
    return [pscustomobject]@{ Success = $true; Paths = $paths; Error = $null }
}

function Get-TreePaths([string]$Commit) {
    $raw = & git ls-tree -r --name-only -z $Commit
    if ($LASTEXITCODE -ne 0) { throw "Unable to inspect outgoing commit $Commit." }
    $paths = @(([string]$raw -split "`0") | Where-Object { $_ })
    Write-Host "Inspected $($paths.Count) tracked paths in outgoing commit $Commit."
    return $paths
}

function Get-Components([string[]]$Paths) {
    $components = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($file in $Paths) {
        if ($file -match '^bingo-admin/') { $null = $components.Add('admin') }
        if ($file -match '^bingo-backend/') { $null = $components.Add('backend') }
        if ($file -match '^bingo-website/') { $null = $components.Add('website') }
        if ($file -match '^bingo-player/') { $null = $components.Add('player') }
        if ($file -match '^bingo-agent/') { $null = $components.Add('agent') }
        if ($file -match '^deployment/(aws|lightsail)/|^.github/workflows/validate-lightsail-infrastructure.yml$') { $null = $components.Add('infrastructure') }
        if ($file -match '^.github/workflows/pr-validation-pipeline.yml$') { @('backend','admin','website') | ForEach-Object { $null = $components.Add($_) } }
        if ($file -match '^.github/workflows/bingo-player-mobile-ci.yml$') { @('player','agent') | ForEach-Object { $null = $components.Add($_) } }
        if ($file -match '^.github/workflows/admin-browser-e2e.yml$') { $null = $components.Add('admin') }
        if ($file -match '^.github/workflows/deploy.yml$') { @('backend','admin','website','infrastructure') | ForEach-Object { $null = $components.Add($_) } }
        if ($file -match '^scripts/ci/') { @('backend','admin','website','player','agent','infrastructure') | ForEach-Object { $null = $components.Add($_) } }
        if ($file -match '^deployment/aws/(detect-deployment-changes|validate-config)\.py$') { @('backend','admin','website','infrastructure') | ForEach-Object { $null = $components.Add($_) } }
        if ($file -match '^(\.editorconfig|\.gitattributes|\.gitignore|package\.json|package-lock\.json|pom\.xml|\.mvn/|mvnw|mvnw\.cmd)$') { @('backend','admin','website','player','agent') | ForEach-Object { $null = $components.Add($_) } }
        if ($file -match '^\.github/workflows/') {
            $known = $file -match '^(\.github/workflows/(pr-validation-pipeline|bingo-player-mobile-ci|admin-browser-e2e|deploy|validate-lightsail-infrastructure)\.yml)$'
            if (-not $known) { @('backend','admin','website','player','agent','infrastructure') | ForEach-Object { $null = $components.Add($_) } }
        }
    }
    return @($components | Sort-Object)
}

function Invoke-Check([string]$WorkingTree, [string[]]$Components) {
    if ($Components.Count -eq 0) { Write-Host 'No CI-covered component paths changed; no component checks required.'; return }
    $runnerPath = Join-Path $WorkingTree 'scripts/ci/run-component-checks.mjs'
    foreach ($component in $Components) {
        $targets = if ($component -eq 'player') { @('bingo-player') } elseif ($component -eq 'agent') { @('bingo-agent') } else { @('') }
        foreach ($target in $targets) {
            $label = if ($target) { "$component ($target)" } else { $component }
            $commandArgs = @($runnerPath, $component)
            $previousMavenEnvironment = @{}
            if ($component -eq 'backend') {
                foreach ($key in @('BINGO_MAVEN_MYSQL_URL','BINGO_MAVEN_MYSQL_USERNAME','BINGO_MAVEN_MYSQL_PASSWORD')) {
                    $previousMavenEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
                }
                $testEnvironmentFile = Join-Path $repoRoot 'bingo-backend/.env.maven-test'
                if (Test-Path -LiteralPath $testEnvironmentFile -PathType Leaf) {
                    foreach ($entry in Get-Content -LiteralPath $testEnvironmentFile) {
                        if ($entry -match '^\s*(BINGO_MAVEN_MYSQL_URL|BINGO_MAVEN_MYSQL_USERNAME|BINGO_MAVEN_MYSQL_PASSWORD)\s*=\s*(.*?)\s*$') {
                            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim('"').Trim("'"), 'Process')
                        }
                    }
                }
            }
            if ($component -in @('admin','website')) {
                $folder = if ($component -eq 'admin') { 'bingo-admin' } else { 'bingo-website' }
                Write-Host "`n[$label] npm ci"
                & npm ci --prefix (Join-Path $WorkingTree $folder)
                if ($LASTEXITCODE -ne 0) { throw "[$label] command 'npm ci' failed with exit code $LASTEXITCODE. Rerun: cd $folder; npm ci" }
            }
            if ($component -in @('player','agent')) { $commandArgs += @('--app', $target) }
            if ($component -eq 'infrastructure' -and -not (Get-Command cfn-lint -ErrorAction SilentlyContinue)) {
                Write-Host '[infrastructure] SKIPPED: cfn-lint is missing; install with python -m pip install cfn-lint.'
            }
            Write-Host "`n[$label] node $($commandArgs -join ' ')"
            try {
                & node @commandArgs
                if ($LASTEXITCODE -ne 0) { throw "[$label] command 'node $($commandArgs -join ' ')' failed with exit code $LASTEXITCODE. Rerun: node scripts/ci/run-component-checks.mjs $component$(if ($target) { " --app $target" })" }
            } finally {
                foreach ($key in $previousMavenEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $previousMavenEnvironment[$key], 'Process') }
            }
        }
    }
}

function Invoke-IsolatedCommit([string]$Commit, [string[]]$Components) {
    $temporaryRoot = Join-Path $env:TEMP ("bingo-push-check-" + [Guid]::NewGuid().ToString('N'))
    $temporaryRoot = [IO.Path]::GetFullPath($temporaryRoot)
    $temporaryBase = [IO.Path]::GetFullPath($env:TEMP).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $temporaryRoot.StartsWith($temporaryBase, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($temporaryRoot) -notmatch '^bingo-push-check-[0-9a-f]{32}$') {
        throw "Refusing to create a temporary checkout outside the generated temp location: $temporaryRoot"
    }
    if (Test-Path -LiteralPath $temporaryRoot) { throw "Generated worktree path already exists; refusing to reuse it: $temporaryRoot" }
    New-Item -ItemType Directory -Path $temporaryRoot -Force | Out-Null
    try {
        & git worktree add --detach $temporaryRoot $Commit
        if ($LASTEXITCODE -ne 0) { throw "Could not create isolated checkout for $Commit." }
        Invoke-Check $temporaryRoot $Components
    } finally {
        if (Test-Path -LiteralPath $temporaryRoot) {
            $resolvedTemporaryRoot = (Resolve-Path -LiteralPath $temporaryRoot).Path
            if (-not $resolvedTemporaryRoot.StartsWith($temporaryBase, [StringComparison]::OrdinalIgnoreCase) -or
                [IO.Path]::GetFileName($resolvedTemporaryRoot) -notmatch '^bingo-push-check-[0-9a-f]{32}$') {
                throw "Refusing to remove a worktree outside the generated temp location: $resolvedTemporaryRoot"
            }
            & git worktree remove --force $resolvedTemporaryRoot 2>$null | Out-Null
            if (Test-Path -LiteralPath $resolvedTemporaryRoot) {
                $remainingTemporaryRoot = (Resolve-Path -LiteralPath $resolvedTemporaryRoot).Path
                if (-not $remainingTemporaryRoot.StartsWith($temporaryBase, [StringComparison]::OrdinalIgnoreCase)) {
                    throw "Refusing recursive cleanup outside the generated temp location: $remainingTemporaryRoot"
                }
                Remove-Item -LiteralPath $remainingTemporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }
}

Push-Location $repoRoot
try {
    if ($Mode -eq 'PreCommit') {
        $runnerArgs = @($runner, 'precommit')
        & node @runnerArgs
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        exit 0
    }

    if ($Mode -eq 'Manual') {
        $head = (Invoke-Git @('rev-parse', 'HEAD')).Trim()
        $upstream = & git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}' 2>$null
        if ($LASTEXITCODE -eq 0 -and $upstream) {
            $base = & git merge-base ([string]$upstream).Trim() $head 2>$null
            if ($LASTEXITCODE -eq 0) {
                $range = Get-ChangedPaths ([string]$base).Trim() $head
                if ($range.Success) { $paths = $range.Paths }
            }
        }
        if ($null -eq $paths) {
            Write-Host 'No usable upstream range is configured; conservatively checking all components in the current committed tree.'
            $paths = Get-TreePaths $head
        }
        $components = Get-Components $paths
        Invoke-IsolatedCommit $head $components
        exit 0
    }

    $updates = [System.Collections.Generic.List[object]]::new()
    while ($null -ne ($line = [Console]::In.ReadLine())) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $fields = $line -split '\s+'
        if ($fields.Count -ge 4) { $updates.Add([pscustomobject]@{ localSha=$fields[0]; localRef=$fields[1]; remoteSha=$fields[2]; remoteRef=$fields[3] }) }
    }
    foreach ($update in $updates) {
        $localIsZero = $update.localSha -match '^0+$'
        $remoteIsZero = $update.remoteSha -match '^0+$'
        if ($localIsZero) { Write-Host "Skipping deleted ref $($update.remoteRef)."; continue }
        $base = $null
        if (-not $remoteIsZero) {
            $candidate = & git merge-base $update.remoteSha $update.localSha 2>$null
            if ($LASTEXITCODE -eq 0) { $base = ([string]$candidate).Trim() }
        } else {
            $tracking = & git for-each-ref --format='%(refname:short)' refs/remotes 2>$null
            $candidates = @($tracking | Where-Object { $_ -notmatch '/HEAD$' })
            $bestCount = [int]::MaxValue
            foreach ($ref in $candidates) {
                $candidate = & git merge-base $ref $update.localSha 2>$null
                if ($LASTEXITCODE -eq 0) {
                    $candidate = ([string]$candidate).Trim()
                    $count = (& git rev-list --count "$candidate..$($update.localSha)" 2>$null | Select-Object -First 1)
                    if ($LASTEXITCODE -eq 0 -and [int]$count -lt $bestCount) { $base = $candidate; $bestCount = [int]$count }
                }
            }
            if (-not $base) { Write-Host "No local remote-tracking base for first push of $($update.localRef); conservatively checking every CI-covered component in the outgoing tree." }
        }
        if ($base) {
            $range = Get-ChangedPaths $base $update.localSha
            if ($range.Success) {
                $paths = $range.Paths
            } else {
                Write-Host "Unable to determine outgoing diff for $($update.localRef) with 'git diff --name-only -z $base..$($update.localSha)': $($range.Error); checking the full outgoing tree."
                $paths = Get-TreePaths $update.localSha
            }
        } else {
            $paths = Get-TreePaths $update.localSha
        }
        $components = Get-Components $paths
        Write-Host "Outgoing $($update.localRef) -> $($update.remoteRef): $($components -join ', ')"
        Invoke-IsolatedCommit $update.localSha $components
    }
    exit 0
} catch {
    [Console]::Error.WriteLine("Bingo Git check failed: $($_.Exception.Message)")
    exit 1
} finally {
    Pop-Location
}
