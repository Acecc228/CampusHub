#requires -Version 5.1
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$module = Join-Path $repo 'scripts\lib\CampusHub.Backend.psm1'
$results = New-Object 'Collections.Generic.List[object]'
function Check([string]$Name, [scriptblock]$Action) {
    try { & $Action; $results.Add([pscustomobject]@{Check=$Name;Status='PASS'}) }
    catch { $results.Add([pscustomobject]@{Check=$Name;Status='FAIL';ErrorType=$_.Exception.GetBaseException().GetType().Name}) }
}
function Reject([scriptblock]$Action) {
    $rejected = $false
    try { & $Action } catch { $rejected = $_.Exception.Message -like 'BackendGuard:*' }
    if (-not $rejected) { throw 'Expected guard rejection.' }
}
if (Test-Path -LiteralPath $module) { Import-Module $module -Force }
Check 'Empty development database requires no baseline' {
    if ((Get-MigrationAction -Database campushub -Tables @()) -ne 'Migrate') { throw 'Wrong action.' }
}
Check 'Existing test probe requires explicit baseline option' {
    Reject { Get-MigrationAction -Database campushub_test -Tables @('m10b_unicode_probe') }
}
Check 'Explicit baseline only admits known test inventory' {
    if ((Get-MigrationAction -Database campushub_test -Tables @('m10b_unicode_probe') -InitializeTestHistory) -ne 'BaselineThenMigrate') { throw 'Wrong action.' }
}
Check 'Existing valid history migrates without baseline' {
    if ((Get-MigrationAction -Database campushub_test -Tables @('m10b_unicode_probe','flyway_schema_history','activity_category')) -ne 'Migrate') { throw 'Wrong action.' }
}
Check 'M1.1 inventory permits incremental validation with history' {
    if ((Get-MigrationAction -Database campushub -Tables @('activity_category','flyway_schema_history','app_user','app_role','app_user_role')) -ne 'Migrate') { throw 'Wrong action.' }
}
Check 'M1.1 tables without history cannot be adopted' {
    Reject { Get-MigrationAction -Database campushub -Tables @('app_user') }
}
Check 'M1.2 inventory permits migration and rejects historyless activity' {
    if ((Get-MigrationAction -Database campushub -Tables @('activity','activity_category','flyway_schema_history','app_user','app_role','app_user_role')) -ne 'Migrate') { throw 'Wrong action.' }
    Reject { Get-MigrationAction -Database campushub -Tables @('activity') }
}
Check 'Activity grants default to preview' {
    $result = & (Join-Path $repo 'scripts\Grant-ActivityAccess.ps1')
    if ($result.Mode -ne 'PREVIEW') { throw 'Expected preview.' }
}
Check 'M1.3 inventory requires history and permits incremental migration' {
    if ((Get-MigrationAction -Database campushub -Tables @('activity_registration','activity','flyway_schema_history')) -ne 'Migrate') { throw 'Wrong action.' }
    Reject { Get-MigrationAction -Database campushub -Tables @('activity_registration') }
}
Check 'Registration grants default to preview and only grant three table privileges' {
    $result = & (Join-Path $repo 'scripts\Grant-RegistrationAccess.ps1')
    if ($result.Mode -ne 'PREVIEW') { throw 'Expected preview.' }
    $sql = [IO.File]::ReadAllText((Join-Path $repo 'infra\mysql\sql\07-grant-registration.sql'))
    $expected = "GRANT SELECT, INSERT, UPDATE ON campushub.activity_registration TO 'campushub_app'@'127.0.0.1';`nGRANT SELECT, INSERT, UPDATE ON campushub_test.activity_registration TO 'campushub_test_app'@'127.0.0.1';"
    if ($sql.Replace("`r`n","`n").Trim() -cne $expected) { throw 'Unexpected registration grants.' }
}
foreach ($case in @(
    @{Database='mysql';Tables=@()},
    @{Database='campushub';Tables=@('activity_category')},
    @{Database='campushub_test';Tables=@('m10b_unicode_probe','unexpected')},
    @{Database='campushub_test';Tables=@()},
    @{Database='campushub';Tables=@('m10b_unicode_probe')}
)) {
    Check ('Reject conflicting inventory: ' + $case.Database + '/' + ($case.Tables -join ',')) {
        Reject { Get-MigrationAction @case -InitializeTestHistory }
    }
}
Check 'Credential parser requires exact user and generated secret format' {
    $fixture = "[client]`nuser=campushub_app`npassword=`"$('a' * 64)`"`nhost=127.0.0.1`nport=3307`nprotocol=TCP`nssl-mode=REQUIRED`n"
    $value = Read-BackendCredentialText -Text $fixture -ExpectedUser campushub_app
    if ($value.User -ne 'campushub_app' -or $value.Password.Length -ne 64) { throw 'Invalid parsed fixture.' }
    Reject { Read-BackendCredentialText -Text $fixture -ExpectedUser campushub_migrator }
    Reject { Read-BackendCredentialText -Text ($fixture + 'password=duplicate') -ExpectedUser campushub_app }
    Reject { Read-BackendCredentialText -Text ($fixture.Replace('port=3307','port=3306')) -ExpectedUser campushub_app }
}
Check 'Child environment removes inherited migration and datasource secrets' {
    $envs = @{CAMPUSHUB_AUTH_DEMOENABLED='true';SERVER_ADDRESS='0.0.0.0';FLYWAY_PASSWORD='fixture';CH_DB_PASSWORD='fixture';SPRING_DATASOURCE_PASSWORD='fixture';SPRINGDOC_APIDOCS_ENABLED='true';SPRINGDOC_SWAGGERUI_ENABLED='true';PATH='keep'}
    Clear-BackendDatabaseEnvironment -Environment $envs
    if ($envs.Count -ne 1 -or $envs.PATH -ne 'keep') { throw 'Credential environment leaked.' }
}
foreach ($script in @('Invoke-BackendMigration.ps1','Invoke-Backend.ps1')) {
    Check ('Default preview: ' + $script) {
        $result = & (Join-Path $repo ('scripts\' + $script))
        if ($result.Mode -ne 'PREVIEW') { throw 'Expected preview.' }
    }
}
Check 'Demo configuration rejects incomplete or unexpected role inventory' {
    Assert-DemoRoleInventory 'ADMIN,ORGANIZER,STUDENT'
    Reject { Assert-DemoRoleInventory 'ORGANIZER,STUDENT' }
    Reject { Assert-DemoRoleInventory 'ADMIN,OTHER,ORGANIZER,STUDENT' }
}
foreach ($script in @('Set-DemoAccounts.ps1','Grant-AuthenticationAccess.ps1','Test-BackendDatabase.ps1','Test-BackendPackage.ps1')) {
    Check ('Default preview: ' + $script) {
        $result=& (Join-Path $repo ('scripts\'+$script))
        if ($result.Mode -ne 'PREVIEW') { throw 'Expected preview.' }
    }
}
Check 'Production launcher refuses demo switch before any writes' {
    $rejected=$false
    try { & (Join-Path $repo 'scripts\Invoke-Backend.ps1') -Action Run -Profile prod -EnableDemoLogin } catch { $rejected=$_.Exception.Message -eq 'Demo login switch requires Run with dev profile.' }
    if (-not $rejected) { throw 'Expected launcher rejection.' }
}
$failed = @($results | Where-Object Status -eq FAIL).Count
[pscustomobject]@{Suite='M1.0-C protection conditions (no database writes)';Passed=@($results | Where-Object Status -eq PASS).Count;Failed=$failed;Results=$results.ToArray()} | ConvertTo-Json -Depth 5
if ($failed) { exit 1 }
exit 0
