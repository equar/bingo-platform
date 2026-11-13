[CmdletBinding()]
param(
    [switch]$ShowEffectiveConfig
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendRoot = Join-Path $repoRoot 'bingo-backend'
$settingsFile = Join-Path $env:USERPROFILE '.m2\settings.xml'
$localDatasourceUrl = 'jdbc:mysql://localhost:3306/bingo?createDatabaseIfNotExist=false&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$localDatasourceArgument = "--spring.datasource.url=$localDatasourceUrl"

if ($ShowEffectiveConfig) {
    Write-Output 'profile=dev mysqlHost=localhost mysqlPort=3306 schema=bingo'
    return
}

if (-not (Test-Path -LiteralPath (Join-Path $backendRoot 'pom.xml') -PathType Leaf)) {
    throw 'The Bingo backend Maven project was not found.'
}

$maven = Get-Command mvn -ErrorAction SilentlyContinue
if ($null -eq $maven) {
    throw 'Maven was not found on PATH. Install Maven 3.9+ and open a new terminal.'
}

$mavenArguments = @()
if (Test-Path -LiteralPath $settingsFile -PathType Leaf) {
    $mavenArguments += @('--settings', $settingsFile)
}
$mavenArguments += @(
    '-Dspring-boot.run.profiles=dev',
    "-Dspring-boot.run.arguments=$localDatasourceArgument",
    'spring-boot:run'
)

Write-Host 'Starting the local Bingo backend with profile=dev, schema=bingo, port=8080.'
Push-Location $backendRoot
try {
    & $maven.Source @mavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven backend startup exited with code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
