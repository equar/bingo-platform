$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'AndroidTestBackendPolicy.psm1') -Force

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
