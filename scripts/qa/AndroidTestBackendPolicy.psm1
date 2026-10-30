Set-StrictMode -Version Latest

if (-not ('BingoQaProcessCommandLineProbe' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class BingoQaProcessCommandLineProbe {
  [StructLayout(LayoutKind.Sequential)] struct UnicodeString { public ushort Length; public ushort MaximumLength; public IntPtr Buffer; }
  [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr OpenProcess(uint access, bool inherit, int processId);
  [DllImport("kernel32.dll", SetLastError=true)] static extern bool CloseHandle(IntPtr handle);
  [DllImport("ntdll.dll")] static extern int NtQueryInformationProcess(IntPtr process, int infoClass, IntPtr info, uint length, out uint returned);
  public static bool Matches(int pid, string jar, string resourceUri) {
    IntPtr handle=OpenProcess(0x1000,false,pid);
    if(handle==IntPtr.Zero) return false;
    try {
      IntPtr buffer=Marshal.AllocHGlobal(65536);
      try {
        uint returned;
        int status=NtQueryInformationProcess(handle,60,buffer,65536,out returned);
        if(status!=0) return false;
        UnicodeString value=Marshal.PtrToStructure<UnicodeString>(buffer);
        string command=Marshal.PtrToStringUni(value.Buffer,value.Length/2);
        bool jarMatch=command.Contains("-jar \""+jar+"\"") || command.Contains("-jar "+jar);
        bool profileMatch=command.Contains("--spring.profiles.active=android-test");
        bool configMatch=command.Contains("--spring.config.additional-location="+resourceUri);
        return jarMatch && profileMatch && configMatch;
      } finally { Marshal.FreeHGlobal(buffer); }
    } finally { CloseHandle(handle); }
  }
}
'@
}

function ConvertTo-UpperHexString {
    [CmdletBinding()]
    param([Parameter(Mandatory)][byte[]]$Bytes)

    # Matches Convert.ToHexString while remaining available in Windows PowerShell 5.1.
    return [System.BitConverter]::ToString($Bytes).Replace('-', '')
}

function Get-QAScenarioStatusesFromOutput {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string[]]$OutputLines,
        [Parameter(Mandatory)][string[]]$ScenarioNames
    )

    $statuses = @{}
    foreach ($name in $ScenarioNames) { $statuses[$name] = 'NOT RUN' }
    foreach ($line in $OutputLines) {
        $marker = [regex]::Match($line, 'QA_SCENARIO_(START|PASS):([a-z0-9-]+)')
        if (-not $marker.Success) { continue }
        $name = $marker.Groups[2].Value
        if (-not $statuses.ContainsKey($name)) { continue }
        if ($marker.Groups[1].Value -eq 'PASS') { $statuses[$name] = 'PASS' }
        elseif ($statuses[$name] -ne 'PASS') { $statuses[$name] = 'FAIL' }
    }
    return $statuses
}

function Test-AndroidTestProcessCommandLine {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][int]$ProcessId,
        [Parameter(Mandatory)][string]$JarPath,
        [Parameter(Mandatory)][string]$TestResourcesUri
    )

    try {
        return [BingoQaProcessCommandLineProbe]::Matches($ProcessId, $JarPath, $TestResourcesUri)
    } catch {
        return $false
    }
}

function Get-AndroidTestBackendSourceFingerprint {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$RepositoryRoot)

    $backendRoot = Join-Path $RepositoryRoot 'bingo-backend'
    $sourceRoot = Join-Path $backendRoot 'src\main'
    $pomPath = Join-Path $backendRoot 'pom.xml'
    if (-not (Test-Path -LiteralPath $sourceRoot) -or -not (Test-Path -LiteralPath $pomPath)) {
        throw 'Backend source fingerprint inputs are incomplete.'
    }

    $files = @(Get-ChildItem -LiteralPath $sourceRoot -File -Recurse) + @(Get-Item -LiteralPath $pomPath)
    $entries = foreach ($file in $files | Sort-Object FullName) {
        $relativePath = $file.FullName.Substring($backendRoot.Length).TrimStart('\').Replace('\', '/')
        $fileHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        "$relativePath`n$fileHash"
    }
    $manifest = [string]::Join("`n", $entries)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ConvertTo-UpperHexString -Bytes $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($manifest))
    } finally {
        $sha.Dispose()
    }
}

function Get-AndroidTestTcpListenerPids {
    [CmdletBinding()]
    param([Parameter(Mandatory)][ValidateRange(1, 65535)][int]$Port)

    $netstat = Get-Command netstat.exe -ErrorAction Stop
    $lines = & $netstat.Source -ano -p tcp 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect local TCP listeners with netstat.' }
    $pids = @(
        foreach ($line in $lines) {
            if ([string]$line -match ('^\s*TCP\s+\S+:' + $Port + '\s+\S+\s+LISTENING\s+(\d+)\s*$')) {
                [int]$matches[1]
            }
        }
    ) | Sort-Object -Unique
    return ,$pids
}

function Assert-AndroidTestDatasource {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$JdbcUrl)

    $match = [regex]::Match(
        $JdbcUrl,
        '^jdbc:mysql://(?<host>[^:/?]+)(?::(?<port>\d+))?/(?<schema>[^?]+)\?(?<query>[^#]+)$',
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if (-not $match.Success) { throw 'QA datasource URL is malformed.' }

    $port = if ($match.Groups['port'].Success) { [int]$match.Groups['port'].Value } else { 3306 }
    if ($match.Groups['host'].Value -ne '127.0.0.1') {
        throw 'QA datasource host must be 127.0.0.1.'
    }
    if ($port -ne 3306) { throw 'QA datasource port must be 3306.' }
    if ($match.Groups['schema'].Value -ne 'bingo_qa_test') {
        throw 'QA datasource schema must be bingo_qa_test.'
    }
    if ($match.Groups['query'].Value -notmatch '(?i)(?:^|&)createDatabaseIfNotExist=false(?:&|$)') {
        throw 'QA datasource must set createDatabaseIfNotExist=false.'
    }

    return [pscustomobject]@{ Host = '127.0.0.1'; Port = 3306; Schema = 'bingo_qa_test' }
}

function Assert-AndroidTestMysqlIdentity {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$IdentityRow,
        [Parameter(Mandatory)][string]$ConfiguredUsername
    )

    $parts = $IdentityRow.Trim() -split "`t", 4
    if ($parts.Count -ne 4) { throw 'MySQL identity query did not return four tab-separated columns.' }
    $account = $parts[1] -split '@', 2
    if ($account.Count -ne 2 -or $account[0] -cne $ConfiguredUsername) {
        throw 'Authenticated MySQL username does not match the configured local username.'
    }
    if ($account[1] -notin @('localhost', '127.0.0.1')) {
        throw 'Authenticated MySQL account must be a local host account.'
    }
    if ($parts[0] -cne 'bingo_qa_test') { throw 'Connected MySQL schema is not bingo_qa_test.' }
    if ([int]$parts[3] -ne 3306) { throw 'Connected MySQL server port is not 3306.' }

    return [pscustomobject]@{
        Database = $parts[0]
        CurrentUser = $parts[1]
        ServerHost = $parts[2]
        Port = [int]$parts[3]
    }
}

function Assert-AgentAndroidE2EEnvironment {
    [CmdletBinding()]
    param(
        [AllowNull()][string]$EnvironmentName,
        [AllowNull()][string]$BackendUrl,
        [AllowNull()][string]$EmulatorBackendUrl,
        [AllowNull()][string]$JdbcUrl,
        [AllowNull()][string]$MysqlUsername,
        [AllowNull()][string]$MysqlPassword
    )

    if ($EnvironmentName -cne 'android-test') {
        throw 'Agent Android E2E environment must be explicitly set to android-test.'
    }
    if ($BackendUrl -cne 'http://127.0.0.1:8082' -or
        $EmulatorBackendUrl -cne 'http://10.0.2.2:8082') {
        throw 'Agent Android E2E must target the approved local backend on port 8082.'
    }
    if ([string]::IsNullOrWhiteSpace($MysqlUsername) -or
        $MysqlUsername -notmatch '^[A-Za-z0-9_]+$' -or
        [string]::IsNullOrWhiteSpace($MysqlPassword)) {
        throw 'Agent Android E2E requires a valid isolated MySQL username and password.'
    }
    if ([string]::IsNullOrWhiteSpace($JdbcUrl)) {
        throw 'Agent Android E2E requires the isolated MySQL datasource URL.'
    }

    $target = Assert-AndroidTestDatasource -JdbcUrl $JdbcUrl
    return [pscustomobject]@{
        Environment = $EnvironmentName
        BackendUrl = $BackendUrl
        EmulatorBackendUrl = $EmulatorBackendUrl
        Database = $target.Schema
        MysqlHost = $target.Host
        MysqlPort = $target.Port
    }
}

function Assert-AndroidTestBackendRuntimeIdentity {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][pscustomobject]$BackendIdentity,
        [AllowNull()][string]$RuntimeIdentityLine,
        [AllowNull()][string]$RuntimeGuardsLine,
        [Parameter(Mandatory)][int]$ListenerProcessId,
        [AllowNull()][string]$BackendHealth,
        [AllowNull()][string]$DatabaseHealth,
        [Parameter(Mandatory)][bool]$BackendJarMatches,
        [Parameter(Mandatory)][bool]$BackendSourceMatches
    )

    foreach ($property in @(
        'profile', 'database', 'jdbcHost', 'jdbcPort', 'flyway', 'drawScheduler',
        'financialPostingAndSimulation', 'processId', 'backendUrl',
        'emulatorBackendUrl', 'sourceCommit', 'artifactSha256', 'sourceSha256', 'adminOrigin'
    )) {
        if ($null -eq $BackendIdentity.PSObject.Properties[$property]) {
            throw "Backend identity evidence is missing required field: $property."
        }
    }

    if ($BackendIdentity.profile -ne 'android-test' -or
        $BackendIdentity.database -ne 'bingo_qa_test' -or
        $BackendIdentity.jdbcHost -ne '127.0.0.1' -or
        [int]$BackendIdentity.jdbcPort -ne 3306 -or
        $BackendIdentity.drawScheduler -ne 'disabled' -or
        $BackendIdentity.financialPostingAndSimulation -ne 'disabled' -or
        $BackendIdentity.backendUrl -ne 'http://127.0.0.1:8082' -or
        $BackendIdentity.emulatorBackendUrl -ne 'http://10.0.2.2:8082' -or
        $BackendIdentity.adminOrigin -ne 'http://127.0.0.1:5174') {
        throw 'Running backend identity is not the approved isolated, no-financial Android test target.'
    }
    if ([int]$BackendIdentity.processId -le 0 -or
        $ListenerProcessId -ne [int]$BackendIdentity.processId) {
        throw 'Port 8082 listener does not match the saved backend process identity.'
    }
    if ($BackendHealth -ne 'UP' -or $DatabaseHealth -ne 'UP') {
        throw 'Running backend or its database health is not UP.'
    }
    if ($BackendIdentity.sourceCommit -notmatch '^[0-9a-fA-F]{40}$' -or
        $BackendIdentity.artifactSha256 -notmatch '^[0-9a-fA-F]{64}$' -or
        $BackendIdentity.sourceSha256 -notmatch '^[0-9a-fA-F]{64}$' -or
        -not $BackendJarMatches -or -not $BackendSourceMatches) {
        throw 'Running backend artifact or executable source does not match its verified build identity.'
    }

    $identityPattern = 'QA_TEST_RUNTIME_IDENTITY pid={0} profiles=android-test database=bingo_qa_test jdbcHost=127\.0\.0\.1 jdbcPort=3306 flyway={1} commit={2} artifactSha256={3} sourceSha256={4}' -f
        [int]$BackendIdentity.processId,
        [regex]::Escape([string]$BackendIdentity.flyway),
        [regex]::Escape([string]$BackendIdentity.sourceCommit),
        [regex]::Escape([string]$BackendIdentity.artifactSha256),
        [regex]::Escape([string]$BackendIdentity.sourceSha256)
    if ($RuntimeIdentityLine -notmatch $identityPattern) {
        throw 'Running backend startup log does not confirm its actual datasource, profile, schema version, and artifact.'
    }
    if ($RuntimeGuardsLine -notmatch 'QA_TEST_RUNTIME_GUARDS drawScheduler=disabled signupRewardPosting=disabled depositSimulation=disabled adjustmentSimulation=disabled agentFloatSimulation=disabled adminCorsOrigin=http://127\.0\.0\.1:5174') {
        throw 'Running backend startup log does not confirm disabled financial and scheduler controls.'
    }

    return [pscustomobject]@{
        ProcessId = [int]$BackendIdentity.processId
        Profile = 'android-test'
        Database = 'bingo_qa_test'
        MysqlHost = '127.0.0.1'
        MysqlPort = 3306
        Flyway = [string]$BackendIdentity.flyway
        DrawScheduler = 'disabled'
        FinancialPostingAndSimulation = 'disabled'
    }
}

Export-ModuleMember -Function ConvertTo-UpperHexString, Get-QAScenarioStatusesFromOutput, Get-AndroidTestBackendSourceFingerprint, Get-AndroidTestTcpListenerPids, Test-AndroidTestProcessCommandLine, Assert-AndroidTestDatasource, Assert-AndroidTestMysqlIdentity, Assert-AgentAndroidE2EEnvironment, Assert-AndroidTestBackendRuntimeIdentity
