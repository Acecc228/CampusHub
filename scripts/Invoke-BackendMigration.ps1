#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('campushub','campushub_test')][string]$Database='campushub_test',
    [ValidateSet('Migrate','Validate')][string]$Operation='Migrate',
    [switch]$InitializeTestHistory,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
if (-not $Apply) {
    [pscustomobject]@{Mode='PREVIEW';Database=$Database;Host='127.0.0.1';Port=3307;Operation=$Operation;InitializeTestHistory=[bool]$InitializeTestHistory}
    return
}
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
Assert-CampusService
$before = Get-LegacyFingerprint
$credentialName = if ($Database -eq 'campushub') { 'migrator' } else { 'test-migrator' }
Confirm-CampusDatabase -CredentialName $credentialName -Database $Database
$inventory = Invoke-CampusSql -CredentialName $credentialName -Database $Database -Sql 'SHOW TABLES;'
$tables = @($inventory -split '\r?\n' | Where-Object { $_ })
if ($Database -eq 'campushub_test') { Assert-BackendProbe }
$action = Get-MigrationAction -Database $Database -Tables $tables -InitializeTestHistory:$InitializeTestHistory
if ($Operation -eq 'Validate' -and ('flyway_schema_history' -notin $tables -or $InitializeTestHistory)) { throw 'BackendGuard: validate requires existing history, without baseline.' }
$credential = Get-BackendCredential $credentialName
$environment = @{
    FLYWAY_URL=('jdbc:mysql://127.0.0.1:3307/' + $Database + '?sslMode=REQUIRED&characterEncoding=UTF-8&connectionTimeZone=UTC')
    FLYWAY_USER=$credential.User;FLYWAY_PASSWORD=$credential.Password;FLYWAY_SCHEMA=$Database
}
try {
    if ($action -eq 'BaselineThenMigrate') { Invoke-BackendMaven -Goals @('-Pmigration','flyway:baseline') -Environment $environment -Label ($Database+'-baseline0') }
    if ($Operation -eq 'Migrate') { Invoke-BackendMaven -Goals @('-Pmigration','flyway:migrate') -Environment $environment -Label ($Database+'-migrate') }
    Invoke-BackendMaven -Goals @('-Pmigration','flyway:validate') -Environment $environment -Label ($Database+'-validate')
    if ($Database -eq 'campushub_test') { Assert-BackendProbe }
} finally { Assert-LegacyUnchanged $before }
