$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Import-Module (Join-Path $PSScriptRoot 'AndroidTestBackendPolicy.psm1') -Force

$validJdbcUrl = 'jdbc:mysql://127.0.0.1:3306/bingo_qa_test?createDatabaseIfNotExist=false&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$validIdentity = [pscustomobject]@{
    profile = 'android-test'
    database = 'bingo_qa_test'
    jdbcHost = '127.0.0.1'
    jdbcPort = 3306
    flyway = '22'
    drawScheduler = 'disabled'
    financialPostingAndSimulation = 'disabled'
    processId = 20588
    backendUrl = 'http://127.0.0.1:8082'
    emulatorBackendUrl = 'http://10.0.2.2:8082'
    sourceCommit = '0123456789abcdef0123456789abcdef01234567'
    artifactSha256 = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
    sourceSha256 = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
    adminOrigin = 'http://127.0.0.1:5174'
}
$validRuntimeLine = 'QA_TEST_RUNTIME_IDENTITY pid=20588 profiles=android-test database=bingo_qa_test jdbcHost=127.0.0.1 jdbcPort=3306 flyway=22 commit=0123456789abcdef0123456789abcdef01234567 artifactSha256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef sourceSha256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
$validGuardsLine = 'QA_TEST_RUNTIME_GUARDS drawScheduler=disabled signupRewardPosting=disabled depositSimulation=disabled adjustmentSimulation=disabled agentFloatSimulation=disabled adminCorsOrigin=http://127.0.0.1:5174'

function Assert-Rejected([string]$Name, [scriptblock]$Action) {
    try {
        & $Action | Out-Null
    } catch {
        return
    }
    throw "Unsafe Agent Android E2E policy input was accepted: $Name."
}

$environment = Assert-AgentAndroidE2EEnvironment `
    -EnvironmentName 'android-test' `
    -BackendUrl 'http://127.0.0.1:8082' `
    -EmulatorBackendUrl 'http://10.0.2.2:8082' `
    -JdbcUrl $validJdbcUrl `
    -MysqlUsername 'bingo_qa_test_runner' `
    -MysqlPassword 'synthetic-nonempty-value'
if ($environment.Database -ne 'bingo_qa_test') { throw 'Valid isolated environment regression failed.' }

Assert-Rejected 'missing environment' {
    Assert-AgentAndroidE2EEnvironment -EnvironmentName '' -BackendUrl 'http://127.0.0.1:8082' -EmulatorBackendUrl 'http://10.0.2.2:8082' -JdbcUrl $validJdbcUrl -MysqlUsername 'runner' -MysqlPassword 'synthetic'
}
Assert-Rejected 'wrong environment' {
    Assert-AgentAndroidE2EEnvironment -EnvironmentName 'qa' -BackendUrl 'http://127.0.0.1:8082' -EmulatorBackendUrl 'http://10.0.2.2:8082' -JdbcUrl $validJdbcUrl -MysqlUsername 'runner' -MysqlPassword 'synthetic'
}
Assert-Rejected 'missing datasource' {
    Assert-AgentAndroidE2EEnvironment -EnvironmentName 'android-test' -BackendUrl 'http://127.0.0.1:8082' -EmulatorBackendUrl 'http://10.0.2.2:8082' -JdbcUrl '' -MysqlUsername 'runner' -MysqlPassword 'synthetic'
}
Assert-Rejected 'unsafe schema' {
    Assert-AgentAndroidE2EEnvironment -EnvironmentName 'android-test' -BackendUrl 'http://127.0.0.1:8082' -EmulatorBackendUrl 'http://10.0.2.2:8082' -JdbcUrl 'jdbc:mysql://127.0.0.1:3306/bingo?createDatabaseIfNotExist=false' -MysqlUsername 'runner' -MysqlPassword 'synthetic'
}
Assert-Rejected 'unsafe database host' {
    Assert-AgentAndroidE2EEnvironment -EnvironmentName 'android-test' -BackendUrl 'http://127.0.0.1:8082' -EmulatorBackendUrl 'http://10.0.2.2:8082' -JdbcUrl 'jdbc:mysql://localhost:3306/bingo_qa_test?createDatabaseIfNotExist=false' -MysqlUsername 'runner' -MysqlPassword 'synthetic'
}
Assert-Rejected 'unsafe database port' {
    Assert-AgentAndroidE2EEnvironment -EnvironmentName 'android-test' -BackendUrl 'http://127.0.0.1:8082' -EmulatorBackendUrl 'http://10.0.2.2:8082' -JdbcUrl 'jdbc:mysql://127.0.0.1:3307/bingo_qa_test?createDatabaseIfNotExist=false' -MysqlUsername 'runner' -MysqlPassword 'synthetic'
}
Assert-Rejected 'database auto-creation enabled' {
    Assert-AgentAndroidE2EEnvironment -EnvironmentName 'android-test' -BackendUrl 'http://127.0.0.1:8082' -EmulatorBackendUrl 'http://10.0.2.2:8082' -JdbcUrl 'jdbc:mysql://127.0.0.1:3306/bingo_qa_test?createDatabaseIfNotExist=true' -MysqlUsername 'runner' -MysqlPassword 'synthetic'
}
Assert-Rejected 'missing backend URL' {
    Assert-AgentAndroidE2EEnvironment -EnvironmentName 'android-test' -BackendUrl '' -EmulatorBackendUrl 'http://10.0.2.2:8082' -JdbcUrl $validJdbcUrl -MysqlUsername 'runner' -MysqlPassword 'synthetic'
}
Assert-Rejected 'missing MySQL username' {
    Assert-AgentAndroidE2EEnvironment -EnvironmentName 'android-test' -BackendUrl 'http://127.0.0.1:8082' -EmulatorBackendUrl 'http://10.0.2.2:8082' -JdbcUrl $validJdbcUrl -MysqlUsername '' -MysqlPassword 'synthetic'
}
Assert-Rejected 'missing MySQL password' {
    Assert-AgentAndroidE2EEnvironment -EnvironmentName 'android-test' -BackendUrl 'http://127.0.0.1:8082' -EmulatorBackendUrl 'http://10.0.2.2:8082' -JdbcUrl $validJdbcUrl -MysqlUsername 'runner' -MysqlPassword ''
}

$runtime = Assert-AndroidTestBackendRuntimeIdentity `
    -BackendIdentity $validIdentity `
    -RuntimeIdentityLine $validRuntimeLine `
    -RuntimeGuardsLine $validGuardsLine `
    -ListenerProcessId 20588 `
    -BackendHealth 'UP' `
    -DatabaseHealth 'UP' `
    -BackendJarMatches $true `
    -BackendSourceMatches $true
if ($runtime.Database -ne 'bingo_qa_test') { throw 'Valid runtime identity regression failed.' }

function Assert-RuntimeRejected([string]$Name, [scriptblock]$Action) {
    Assert-Rejected $Name $Action
}

$unsafeIdentity = $validIdentity.PSObject.Copy()
$unsafeIdentity.profile = 'dev'
Assert-RuntimeRejected 'wrong runtime profile' {
    Assert-AndroidTestBackendRuntimeIdentity -BackendIdentity $unsafeIdentity -RuntimeIdentityLine $validRuntimeLine -RuntimeGuardsLine $validGuardsLine -ListenerProcessId 20588 -BackendHealth 'UP' -DatabaseHealth 'UP' -BackendJarMatches $true -BackendSourceMatches $true
}
$unsafeIdentity = $validIdentity.PSObject.Copy()
$unsafeIdentity.database = 'bingo'
Assert-RuntimeRejected 'wrong runtime schema' {
    Assert-AndroidTestBackendRuntimeIdentity -BackendIdentity $unsafeIdentity -RuntimeIdentityLine $validRuntimeLine -RuntimeGuardsLine $validGuardsLine -ListenerProcessId 20588 -BackendHealth 'UP' -DatabaseHealth 'UP' -BackendJarMatches $true -BackendSourceMatches $true
}
$unsafeIdentity = $validIdentity.PSObject.Copy()
$unsafeIdentity.financialPostingAndSimulation = 'enabled'
Assert-RuntimeRejected 'financial controls enabled' {
    Assert-AndroidTestBackendRuntimeIdentity -BackendIdentity $unsafeIdentity -RuntimeIdentityLine $validRuntimeLine -RuntimeGuardsLine $validGuardsLine -ListenerProcessId 20588 -BackendHealth 'UP' -DatabaseHealth 'UP' -BackendJarMatches $true -BackendSourceMatches $true
}
Assert-RuntimeRejected 'listener PID mismatch' {
    Assert-AndroidTestBackendRuntimeIdentity -BackendIdentity $validIdentity -RuntimeIdentityLine $validRuntimeLine -RuntimeGuardsLine $validGuardsLine -ListenerProcessId 1234 -BackendHealth 'UP' -DatabaseHealth 'UP' -BackendJarMatches $true -BackendSourceMatches $true
}
Assert-RuntimeRejected 'backend health unavailable' {
    Assert-AndroidTestBackendRuntimeIdentity -BackendIdentity $validIdentity -RuntimeIdentityLine $validRuntimeLine -RuntimeGuardsLine $validGuardsLine -ListenerProcessId 20588 -BackendHealth 'DOWN' -DatabaseHealth 'UP' -BackendJarMatches $true -BackendSourceMatches $true
}
Assert-RuntimeRejected 'database health unavailable' {
    Assert-AndroidTestBackendRuntimeIdentity -BackendIdentity $validIdentity -RuntimeIdentityLine $validRuntimeLine -RuntimeGuardsLine $validGuardsLine -ListenerProcessId 20588 -BackendHealth 'UP' -DatabaseHealth 'DOWN' -BackendJarMatches $true -BackendSourceMatches $true
}
Assert-RuntimeRejected 'runtime identity missing' {
    Assert-AndroidTestBackendRuntimeIdentity -BackendIdentity $validIdentity -RuntimeIdentityLine '' -RuntimeGuardsLine $validGuardsLine -ListenerProcessId 20588 -BackendHealth 'UP' -DatabaseHealth 'UP' -BackendJarMatches $true -BackendSourceMatches $true
}
Assert-RuntimeRejected 'runtime guards missing' {
    Assert-AndroidTestBackendRuntimeIdentity -BackendIdentity $validIdentity -RuntimeIdentityLine $validRuntimeLine -RuntimeGuardsLine '' -ListenerProcessId 20588 -BackendHealth 'UP' -DatabaseHealth 'UP' -BackendJarMatches $true -BackendSourceMatches $true
}
Assert-RuntimeRejected 'backend artifact mismatch' {
    Assert-AndroidTestBackendRuntimeIdentity -BackendIdentity $validIdentity -RuntimeIdentityLine $validRuntimeLine -RuntimeGuardsLine $validGuardsLine -ListenerProcessId 20588 -BackendHealth 'UP' -DatabaseHealth 'UP' -BackendJarMatches $false -BackendSourceMatches $true
}
Assert-RuntimeRejected 'backend source mismatch' {
    Assert-AndroidTestBackendRuntimeIdentity -BackendIdentity $validIdentity -RuntimeIdentityLine $validRuntimeLine -RuntimeGuardsLine $validGuardsLine -ListenerProcessId 20588 -BackendHealth 'UP' -DatabaseHealth 'UP' -BackendJarMatches $true -BackendSourceMatches $false
}

Write-Host 'PASS: Agent Android E2E policy checks reject missing/unsafe configuration and unverified runtime identities. Tests perform no MySQL, HTTP, emulator, or file-target operations.'
