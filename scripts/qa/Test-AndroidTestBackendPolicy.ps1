$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'AndroidTestBackendPolicy.psm1') -Force

$sampleBytes = [System.Text.Encoding]::UTF8.GetBytes('PowerShell 5.1 SHA-256 compatibility')
$sampleHash = [System.Security.Cryptography.SHA256]::Create()
try {
    $hex = ConvertTo-UpperHexString -Bytes $sampleHash.ComputeHash($sampleBytes)
} finally {
    $sampleHash.Dispose()
}
if ($hex -cne 'E91177BBE03B81FFFCAA96170B851D8E3D99AEA7369B3260AB0721EE261FF8C4') {
    throw 'Windows PowerShell compatible uppercase SHA-256 hex conversion regression failed.'
}

$valid = Assert-AndroidTestDatasource 'jdbc:mysql://127.0.0.1:3306/bingo_qa_test?createDatabaseIfNotExist=false&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
if ($valid.Schema -ne 'bingo_qa_test' -or $valid.Port -ne 3306) { throw 'Valid datasource regression failed.' }

$wrongSchemaRejected = $false
try { Assert-AndroidTestDatasource 'jdbc:mysql://127.0.0.1:3306/bingo_qa?createDatabaseIfNotExist=false' | Out-Null }
catch { $wrongSchemaRejected = $true }
if (-not $wrongSchemaRejected) { throw 'Wrong-schema datasource was not rejected.' }

$wrongPortRejected = $false
try { Assert-AndroidTestDatasource 'jdbc:mysql://127.0.0.1:3307/bingo_qa_test?createDatabaseIfNotExist=false' | Out-Null }
catch { $wrongPortRejected = $true }
if (-not $wrongPortRejected) { throw 'Wrong-port datasource was not rejected.' }

$rootIdentity = Assert-AndroidTestMysqlIdentity "bingo_qa_test`	root@localhost`	EQUAR-DELL-XPS15`	3306" 'root'
if ($rootIdentity.CurrentUser -ne 'root@localhost') { throw 'Authorized local root identity regression failed.' }

$wrongIdentityRejected = $false
try { Assert-AndroidTestMysqlIdentity "bingo`	root@localhost`	EQUAR-DELL-XPS15`	3306" 'root' | Out-Null }
catch { $wrongIdentityRejected = $true }
if (-not $wrongIdentityRejected) { throw 'Wrong-schema MySQL identity was not rejected.' }

$wrongIdentityPortRejected = $false
try { Assert-AndroidTestMysqlIdentity "bingo_qa_test`	root@localhost`	EQUAR-DELL-XPS15`	3307" 'root' | Out-Null }
catch { $wrongIdentityPortRejected = $true }
if (-not $wrongIdentityPortRejected) { throw 'Wrong-port MySQL identity was not rejected.' }

Write-Host 'PASS: Android-test backend datasource and identity policy regressions (wrong schema/port rejected; local root accepted).'
