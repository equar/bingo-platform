[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Write-Error 'The standalone Agent Flutter Android client was retired. Historical QA reports remain under docs/qa/evidence; backend and Admin Agent workflows are maintained separately.'
exit 2
