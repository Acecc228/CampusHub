#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
if (-not $Apply) { [pscustomobject]@{Mode='PREVIEW';Instance='127.0.0.1:3307';Action='Grant authentication table privileges only; no DDL'}; return }
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
$c=Get-CampusHubConfig
Assert-CampusService
Confirm-CampusDatabase -CredentialName root
$count=(Invoke-CampusSql -CredentialName root -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ('campushub','campushub_test') AND table_name IN ('app_user','app_role','app_user_role') AND table_type='BASE TABLE';").Trim()
if ($count -cne '6') { throw 'Authentication tables must already exist in both databases.' }
$null=Invoke-CampusSql -CredentialName root -Sql ([IO.File]::ReadAllText((Join-Path $c.RepoRoot 'infra\mysql\sql\05-grant-authentication.sql')))
[pscustomobject]@{Status='PASS';Stage='Authentication table grants'}
