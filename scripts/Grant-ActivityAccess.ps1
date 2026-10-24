#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
if (-not $Apply) { [pscustomobject]@{Mode='PREVIEW';Instance='127.0.0.1:3307';Action='Grant activity table privileges only; no DDL'}; return }
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
$c=Get-CampusHubConfig
Assert-CampusService
Confirm-CampusDatabase -CredentialName root
foreach ($database in @('campushub','campushub_test')) {
    Confirm-CampusDatabase -CredentialName root -Database $database
    $count=(Invoke-CampusSql -CredentialName root -Database $database -Sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version='3' AND type='SQL' AND success=1;").Trim()
    if ($count -cne '1') { throw 'Successful V3 migration required in both databases before grants.' }
}
$null=Invoke-CampusSql -CredentialName root -Database campushub -Sql ([IO.File]::ReadAllText((Join-Path $c.RepoRoot 'infra\mysql\sql\06-grant-activity.sql')))
[pscustomobject]@{Status='PASS';Stage='Activity table grants'}
