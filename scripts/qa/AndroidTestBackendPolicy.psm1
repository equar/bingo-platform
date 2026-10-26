Set-StrictMode -Version Latest

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

Export-ModuleMember -Function Assert-AndroidTestDatasource, Assert-AndroidTestMysqlIdentity
