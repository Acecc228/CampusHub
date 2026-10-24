#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
if (-not $Apply) { [pscustomobject]@{Mode='PREVIEW';Action='Read-only grants, history, evidence and checksum-rejection verification'}; return }
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
$c = Get-CampusHubConfig
$before = Get-LegacyFingerprint
$results = New-Object 'Collections.Generic.List[object]'
function Check([string]$Name, [scriptblock]$Action) {
    try { & $Action; $results.Add([pscustomobject]@{Check=$Name;Status='PASS'}) }
    catch { $results.Add([pscustomobject]@{Check=$Name;Status='FAIL';ErrorType=$_.Exception.GetBaseException().GetType().Name}) }
}
Assert-CampusService
$matrices = @(
    @{Name='app';User='campushub_app';Database='campushub';Scope='`campushub`.`activity_category`';Permissions='SELECT'},
    @{Name='test-app';User='campushub_test_app';Database='campushub_test';Scope='`campushub_test`.`activity_category`';Permissions='SELECT, INSERT, UPDATE, DELETE'},
    @{Name='migrator';User='campushub_migrator';Database='campushub';Scope='`campushub`.*';Permissions='SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES, INDEX, ALTER'},
    @{Name='test-migrator';User='campushub_test_migrator';Database='campushub_test';Scope='`campushub\_test`.*';Permissions='SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES, INDEX, ALTER'}
)
foreach ($matrix in $matrices) {
    Check ('Exact grant matrix and identity: '+$matrix.Name) {
        Confirm-CampusDatabase -CredentialName $matrix.Name -Database $matrix.Database
        $account = '`'+$matrix.User+'`@`127.0.0.1`'
        $expected = @(('GRANT USAGE ON *.* TO '+$account), ('GRANT '+$matrix.Permissions+' ON '+$matrix.Scope+' TO '+$account))
        if ($matrix.Name -in @('app','test-app')) {
            foreach ($table in @('app_user','app_role','app_user_role')) {
                $permissions=if ($matrix.Name -eq 'test-app' -and $table -ne 'app_role') { 'SELECT, INSERT, UPDATE, DELETE' } else { 'SELECT' }
                $expected+=('GRANT '+$permissions+' ON `'+$matrix.Database+'`.`'+$table+'` TO '+$account)
            }
            $activityPermissions=if ($matrix.Name -eq 'test-app') { 'SELECT, INSERT, UPDATE, DELETE' } else { 'SELECT, INSERT, UPDATE' }
            $expected+=('GRANT '+$activityPermissions+' ON `'+$matrix.Database+'`.`activity` TO '+$account)
            $expected+=('GRANT SELECT, INSERT, UPDATE ON `'+$matrix.Database+'`.`activity_registration` TO '+$account)
        }
        $actual = @((Invoke-CampusSql -CredentialName $matrix.Name -Sql 'SHOW GRANTS;').Trim() -split '\r?\n')
        if ((($actual | Sort-Object) -join "`n") -cne (($expected | Sort-Object) -join "`n")) { throw 'Unexpected privilege matrix.' }
    }
}
foreach ($database in @('campushub','campushub_test')) {
    $migrationCredentialName = if ($database -eq 'campushub') { 'migrator' } else { 'test-migrator' }
    Check ('Migration history and seeds: '+$database) {
        $row=(Invoke-CampusSql -CredentialName $migrationCredentialName -Database $database -Sql "SELECT version,type,checksum,success FROM flyway_schema_history WHERE version='1';").Trim()
        if ($row -cne "1`tSQL`t1160888031`t1") { throw 'Unexpected migration history.' }
        $count=(Invoke-CampusSql -CredentialName $migrationCredentialName -Database $database -Sql 'SELECT COUNT(*) FROM activity_category;').Trim()
        if ($count -cne '4') { throw 'Unexpected seed count or test residue.' }
        $version=(Invoke-CampusSql -CredentialName $migrationCredentialName -Database $database -Sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version='2' AND type='SQL' AND checksum=436036048 AND success=1;").Trim()
        if ($version -cne '1') { throw 'V2 migration missing.' }
        $version=(Invoke-CampusSql -CredentialName $migrationCredentialName -Database $database -Sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version='3' AND type='SQL' AND checksum=-852546724 AND success=1;").Trim()
        if ($version -cne '1') { throw 'V3 migration missing.' }
        $version=(Invoke-CampusSql -CredentialName $migrationCredentialName -Database $database -Sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version='4' AND type='SQL' AND success=1;").Trim()
        if ($version -cne '1') { throw 'V4 migration missing.' }
        $roles=(Invoke-CampusSql -CredentialName $migrationCredentialName -Database $database -Sql "SELECT GROUP_CONCAT(code ORDER BY code) FROM app_role;").Trim()
        if ($roles -cne 'ADMIN,ORGANIZER,STUDENT') { throw 'Unexpected roles.' }
    }
}
foreach ($app in @(
    @{Name='app';Database='campushub';Other='campushub_test'},
    @{Name='test-app';Database='campushub_test';Other='campushub'}
)) {
    Check ('Registration SELECT works and DELETE ALTER cross-database are denied: '+$app.Name) {
        $null=Invoke-CampusSql -CredentialName $app.Name -Database $app.Database -Sql 'SELECT id FROM activity_registration LIMIT 0;'
        foreach ($sql in @('DELETE FROM activity_registration WHERE 1=0;',
                'ALTER TABLE activity_registration ENGINE=InnoDB;',
                ('SELECT id FROM '+$app.Other+'.activity_registration LIMIT 0;'))) {
            $denied=$false
            try { $null=Invoke-CampusSql -CredentialName $app.Name -Database $app.Database -Sql $sql }
            catch { $denied=$_.Exception.Message -match 'MYSQL_(1142_TABLE_ACCESS_DENIED|1044_DATABASE_ACCESS_DENIED)' }
            if (-not $denied) { throw 'Expected specific permission denial.' }
        }
    }
}
Check 'Original probe definition and Unicode evidence unchanged' { Assert-BackendProbe }
Check 'Flyway rejects modified migration checksum without repair' {
    Confirm-CampusDatabase -CredentialName test-migrator -Database campushub_test
    $directory=Join-Path $c.RepoRoot ('.local\checksum-probe-'+[guid]::NewGuid().ToString('N'))
    $null=New-Item -ItemType Directory -Path $directory
    $source=Join-Path $c.RepoRoot 'infra\mysql\migrations\V1__create_activity_category.sql'
    Copy-Item -LiteralPath (Join-Path $c.RepoRoot 'infra\mysql\migrations\V2__create_identity_and_roles.sql') -Destination $directory
    Copy-Item -LiteralPath (Join-Path $c.RepoRoot 'infra\mysql\migrations\V3__create_activity.sql') -Destination $directory
    Copy-Item -LiteralPath (Join-Path $c.RepoRoot 'infra\mysql\migrations\V4__create_activity_registration.sql') -Destination $directory
    $copy=Join-Path $directory 'V1__create_activity_category.sql'
    [IO.File]::WriteAllText($copy,([IO.File]::ReadAllText($source)+"`n-- Deliberate validation-only checksum probe`n"),(New-Object Text.UTF8Encoding($false)))
    $credential=Get-BackendCredential test-migrator
    $environment=@{FLYWAY_URL='jdbc:mysql://127.0.0.1:3307/campushub_test?sslMode=REQUIRED&characterEncoding=UTF-8&connectionTimeZone=UTC';FLYWAY_USER=$credential.User;FLYWAY_PASSWORD=$credential.Password;FLYWAY_SCHEMA='campushub_test'}
    $label='checksum-rejection-'+[guid]::NewGuid().ToString('N')
    $relative='../.local/'+(Split-Path $directory -Leaf)
    $rejected=$false
    try { $null=Invoke-BackendMaven -Goals @('-Pmigration','flyway:validate',('-Dflyway.locations=filesystem:'+$relative)) -Environment $environment -Label $label }
    catch { $rejected=$_.Exception.Message -like 'BackendMavenFailed:*' }
    $log=Get-ChildItem -LiteralPath (Join-Path $c.RepoRoot '.local\backend-logs') -Filter ($label+'-*.log') | Select-Object -First 1
    if (-not $rejected -or -not $log -or [IO.File]::ReadAllText($log.FullName) -notmatch 'Migration checksum mismatch for migration version 1') { throw 'Expected checksum failure was not observed.' }
    $null=Invoke-BackendMaven -Goals @('-Pmigration','flyway:validate') -Environment $environment -Label checksum-original-validation
}
Check 'MySQL80 fingerprint unchanged' { Assert-LegacyUnchanged $before }
$failed=@($results | Where-Object Status -eq FAIL).Count
$report=[pscustomobject]@{CheckedAtUtc=[DateTime]::UtcNow.ToString('o');Status=$(if ($failed) {'FAIL'} else {'PASS'});Passed=@($results | Where-Object Status -eq PASS).Count;Failed=$failed;Results=$results.ToArray()}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $c.RepoRoot 'docs\verification\m1.3-database.json') -Encoding UTF8
$report | Select-Object Status,Passed,Failed
if ($failed) { exit 1 }
exit 0
