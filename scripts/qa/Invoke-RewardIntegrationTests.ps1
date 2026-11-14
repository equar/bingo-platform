param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$backendRoot = Join-Path $repoRoot 'bingo-backend'
$environmentFile = Join-Path $backendRoot '.env.maven-test'
$settingsFile = Join-Path $env:USERPROFILE '.m2\settings.xml'
$requiredSchemaVersion = '40'

if (-not (Test-Path -LiteralPath $environmentFile -PathType Leaf)) {
    throw 'Missing bingo-backend/.env.maven-test. Copy the approved example and populate it through the DBA secret-handling procedure.'
}
if (-not (Test-Path -LiteralPath $settingsFile -PathType Leaf)) {
    throw 'The normal Windows user Maven settings file was not found.'
}

$activeSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$profileOwner = [Security.Principal.NTAccount]::new((Get-Acl -LiteralPath $env:USERPROFILE).Owner)
$profileOwnerSid = $profileOwner.Translate([Security.Principal.SecurityIdentifier]).Value
if ($activeSid -ne $profileOwnerSid) {
    throw 'Run this integration-test launcher from the normal Windows user session that owns the approved QA secret file and Maven repository.'
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $environmentFile) {
    if ($line -match '^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$') {
        $values[$Matches[1]] = $Matches[2].Trim('"').Trim("'")
    }
}
$required = @('BINGO_MAVEN_MYSQL_URL', 'BINGO_MAVEN_MYSQL_USERNAME', 'BINGO_MAVEN_MYSQL_PASSWORD')
foreach ($name in $required) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "The approved integration-test configuration is missing $name."
    }
}

$target = [Uri]($values['BINGO_MAVEN_MYSQL_URL'] -replace '^jdbc:', '')
$targetSchema = $target.AbsolutePath.Trim('/')
if ($target.Host -notin @('127.0.0.1', 'localhost') -or $target.Port -ne 3306 -or
    $targetSchema -cne 'bingo_qa_maven_test' -or
    $values['BINGO_MAVEN_MYSQL_USERNAME'] -cne 'bingo_qa_maven_runner' -or
    $values['BINGO_MAVEN_MYSQL_URL'] -match '(?i)createDatabaseIfNotExist\s*=\s*true') {
    throw 'Refusing Maven tests: the configured target is outside the approved local disposable MySQL schema.'
}
# This switch blocks synthetic deposit-posting scenarios; it does not disable
# SignupRewardPostingIntegrationTest or skip its reward assertions. That suite
# inserts controlled ledger rows directly and remains guarded by the V40 preflight.
if (-not $values.ContainsKey('BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED') -or
    $values['BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED'] -notmatch '^(?i:false|0|no)$') {
    throw 'Refusing campaign integration tests unless synthetic financial posting is explicitly disabled.'
}
# This switch blocks simulated bank/deposit posting. The reward suite still
# executes its assertions using isolated, direct posted-ledger test fixtures;
# it contains no JUnit skip/assumption gate on this setting.

$mysql = Get-Command mysql.exe -ErrorAction SilentlyContinue
if (-not $mysql) {
    throw 'mysql.exe is required for the read-only database identity and schema-version preflight.'
}

# The fixtures commit in independent request/service transactions. Serialize
# launcher runs on this machine because they intentionally reuse fixed phones.
$lockPath = Join-Path $env:TEMP 'bingo-reward-integration-tests.lock'
try {
    $lock = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::OpenOrCreate,
        [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
} catch {
    throw 'Another reward integration launcher appears to be running; wait for it to finish before retrying.'
}

try {
    $previousMysqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    $env:MYSQL_PWD = $values['BINGO_MAVEN_MYSQL_PASSWORD']
    try {
        $preflightSql = @'
SELECT DATABASE(), CURRENT_USER(), @@port,
       (SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1),
       (SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0)
FROM flyway_schema_history
LIMIT 1
'@
        $preflight = & $mysql.Source --protocol=TCP --host=127.0.0.1 --port=3306 `
            "--user=$($values['BINGO_MAVEN_MYSQL_USERNAME'])" `
            "--database=$targetSchema" --batch --skip-column-names "--execute=$preflightSql" 2>$null
        if ($LASTEXITCODE -ne 0 -or @($preflight).Count -ne 1) {
            throw 'Read-only database preflight failed; refusing to start integration tests.'
        }
        $identity = ([string]$preflight[0]).Split([char]9)
        if ($identity.Count -ne 5 -or $identity[0] -cne 'bingo_qa_maven_test' -or
            $identity[1] -cne 'bingo_qa_maven_runner@127.0.0.1' -or $identity[2] -cne '3306' -or
            $identity[3] -cne $requiredSchemaVersion -or $identity[4] -cne '0') {
            throw 'Database identity or migration history does not match the approved V40 disposable test schema; refusing writes.'
        }
    } finally {
        if ($null -eq $previousMysqlPwd) {
            Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $previousMysqlPwd
        }
    }

    $environmentNames = $required + @('BINGO_MAVEN_SYNTHETIC_FINANCIAL_TESTS_ENABLED')
    $previousEnvironment = @{}
    foreach ($name in $environmentNames) {
        $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($values.ContainsKey($name)) {
            [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
        }
    }

    Push-Location $backendRoot
    try {
        & mvn --settings $settingsFile '-Dspring.flyway.enabled=false' `
            '-Dtest=SignupRewardPostingIntegrationTest' test
        if ($LASTEXITCODE -ne 0) {
            throw "Reward integration tests failed with exit code $LASTEXITCODE. No schema migration was enabled."
        }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($name in $environmentNames) {
        if ($null -eq $previousEnvironment[$name]) {
            Remove-Item "Env:\$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
        }
    }
    $lock.Dispose()
}
