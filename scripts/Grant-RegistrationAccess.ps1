#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
if (-not $Apply) {
    [pscustomobject]@{Mode='PREVIEW';Instance='127.0.0.1:3307';Action='Only SELECT INSERT UPDATE on each application account registration table'}
    return
}
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
$c=Get-CampusHubConfig
Assert-CampusService
$legacy=Get-LegacyFingerprint
Confirm-CampusDatabase -CredentialName root
$snapshots=New-Object 'Collections.Generic.List[object]'
foreach ($database in @('campushub','campushub_test')) {
    $name=if ($database -eq 'campushub') { 'app' } else { 'test-app' }
    $user=if ($database -eq 'campushub') { 'campushub_app' } else { 'campushub_test_app' }
    Confirm-CampusDatabase -CredentialName $name -Database $database
    $account='`'+$user+'`@`127.0.0.1`'
    $expected=@('GRANT USAGE ON *.* TO '+$account)
    foreach ($table in @('activity_category','app_user','app_role','app_user_role','activity')) {
        $permissions=if ($name -eq 'test-app' -and $table -ne 'app_role') { 'SELECT, INSERT, UPDATE, DELETE' }
            elseif ($table -eq 'activity') { 'SELECT, INSERT, UPDATE' } else { 'SELECT' }
        $expected+=('GRANT '+$permissions+' ON `'+$database+'`.`'+$table+'` TO '+$account)
    }
    $addition='GRANT SELECT, INSERT, UPDATE ON `'+$database+'`.`activity_registration` TO '+$account
    $actual=@((Invoke-CampusSql -CredentialName $name -Database $database -Sql 'SHOW GRANTS;').Trim() -split '\r?\n')
    $withoutRegistration=@($actual | Where-Object { $_ -cne $addition })
    if ((($withoutRegistration | Sort-Object) -join "`n") -cne (($expected | Sort-Object) -join "`n")) {
        throw 'Unexpected current privileges. No grants performed.'
    }
    $history=(Invoke-CampusSql -CredentialName root -Database $database -Sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version='4' AND type='SQL' AND success=1;").Trim()
    if ($history -cne '1') { throw 'Successful V4 required before grants.' }
    $columns=(Invoke-CampusSql -CredentialName root -Database $database -Sql "SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='activity_registration';").Trim()
    if ($columns -cne 'id,activity_id,user_id,status,version,created_at,updated_at') { throw 'Unexpected registration table.' }
    $engine=(Invoke-CampusSql -CredentialName root -Database $database -Sql "SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='activity_registration';").Trim()
    if ($engine -cne 'InnoDB') { throw 'Expected InnoDB registration table.' }
    $snapshots.Add([pscustomobject]@{Database=$database;Credential=$name;Before=$actual;Expected=($expected+@($addition));After=@()})
}
try {
    # Both account inventories are checked before the first authorized grant.
    $sql=[IO.File]::ReadAllText((Join-Path $c.RepoRoot 'infra\mysql\sql\07-grant-registration.sql'))
    $expectedSql="GRANT SELECT, INSERT, UPDATE ON campushub.activity_registration TO 'campushub_app'@'127.0.0.1';`nGRANT SELECT, INSERT, UPDATE ON campushub_test.activity_registration TO 'campushub_test_app'@'127.0.0.1';"
    if ($sql.Replace("`r`n","`n").Trim() -cne $expectedSql) { throw 'Grant SQL exceeds authorization.' }
    $null=Invoke-CampusSql -CredentialName root -Database campushub -Sql $sql
    foreach ($snapshot in $snapshots) {
        $snapshot.After=@((Invoke-CampusSql -CredentialName $snapshot.Credential -Database $snapshot.Database -Sql 'SHOW GRANTS;').Trim() -split '\r?\n')
        if ((($snapshot.After | Sort-Object) -join "`n") -cne (($snapshot.Expected | Sort-Object) -join "`n")) { throw 'Post-grant matrix differs.' }
    }
    [pscustomobject]@{Status='PASS';CheckedAtUtc=[DateTime]::UtcNow.ToString('o');Accounts=$snapshots.ToArray()} |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $c.RepoRoot 'docs\verification\m1.3-grants.json') -Encoding UTF8
    [pscustomobject]@{Status='PASS';Stage='Exact registration table grants; other privileges unchanged'}
} finally { Assert-LegacyUnchanged $legacy }
