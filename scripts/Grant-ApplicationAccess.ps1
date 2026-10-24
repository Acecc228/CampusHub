#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
$c = Get-CampusHubConfig
if (-not $Apply) {
    [PSCustomObject]@{ Mode='PREVIEW'; Prerequisite='Approved Flyway migrations have created activity_category in both schemas.'; Development='campushub_app: SELECT on campushub.activity_category'; Test='campushub_test_app: SELECT/INSERT/UPDATE/DELETE on campushub_test.activity_category'; MigrationHistory='No app privileges'; SqlFile='infra/mysql/sql/04-grant-application.sql' }
    return
}
Assert-CampusService
Confirm-CampusDatabase -CredentialName 'root'
$count = (Invoke-CampusSql -CredentialName 'root' -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('campushub','campushub_test') AND table_name='activity_category' AND table_type='BASE TABLE';").Trim()
if ($count -ne '2') { throw 'Both business tables must exist after verified Flyway migrations. No tables are created by this script.' }
$null = Invoke-CampusSql -CredentialName 'root' -Sql ([IO.File]::ReadAllText((Join-Path $c.RepoRoot 'infra\mysql\sql\04-grant-application.sql')))
Confirm-CampusDatabase -CredentialName 'app' -Database 'campushub'
Confirm-CampusDatabase -CredentialName 'test-app' -Database 'campushub_test'
[PSCustomObject]@{ Status='PASS'; Stage='Application table grants'; NegativePrivilegeTests='NOT_RUN: require M1.0-B integration tests' }
