#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
if (-not $Apply) {
    [pscustomobject]@{Mode='PREVIEW';Host='127.0.0.1';Port=3307;Database='campushub_test';Migration='V8';Grant='mapping SELECT INSERT UPDATE only'}
    return
}
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
Assert-CampusService
$before=Get-LegacyFingerprint
$c=Get-CampusHubConfig
try {
    Confirm-CampusDatabase -CredentialName test-migrator -Database campushub_test
    Assert-BackendProbe
    $versions=(Invoke-CampusSql -CredentialName test-migrator -Database campushub_test -Sql 'SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM flyway_schema_history WHERE success=1;').Trim()
    if ($versions -notin @('0,1,2,3,4,5,6,7','0,1,2,3,4,5,6,7,8')) { throw 'Unexpected shared test history; stop without changes.' }
    # Resolve existing M2 history privately. These files are NEVER shipped with M1.
    $resolver=Join-Path $c.RepoRoot '.local\m1-test-migrations'
    $null=New-Item -ItemType Directory -Path $resolver -Force
    Get-ChildItem -LiteralPath (Join-Path $c.RepoRoot 'infra\mysql\migrations') -Filter 'V*.sql' | ForEach-Object {
        if ($_.Name -notmatch '^V(1|2|3|4|8)__') { throw 'Unexpected M1 migration.' }
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $resolver $_.Name)
    }
    $m2='4f373ea291d4868f340c4408018d665c8cae2ccc'
    $paths=@(& git -C $c.RepoRoot ls-tree -r --name-only $m2 infra/mysql/migrations)
    if ($LASTEXITCODE -ne 0) { throw 'Protected M2 commit unavailable.' }
    foreach ($path in ($paths | Where-Object { $_ -match '/V[567]__' })) {
        $content=& git -C $c.RepoRoot show ($m2+':'+$path)
        if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve protected historical migration.' }
        [IO.File]::WriteAllText((Join-Path $resolver (Split-Path $path -Leaf)),(($content -join "`n")+"`n"),(New-Object Text.UTF8Encoding($false)))
    }
    if (@(Get-ChildItem -LiteralPath $resolver -Filter 'V*.sql').Count -ne 8) { throw 'Unexpected private resolver inventory.' }
    $credential=Get-BackendCredential test-migrator
    $environment=@{FLYWAY_URL='jdbc:mysql://127.0.0.1:3307/campushub_test?sslMode=REQUIRED&characterEncoding=UTF-8&connectionTimeZone=UTC';FLYWAY_USER=$credential.User;FLYWAY_PASSWORD=$credential.Password;FLYWAY_SCHEMA='campushub_test'}
    $location='-Dflyway.locations=filesystem:'+ $resolver.Replace('\','/')
    Invoke-BackendMaven -Goals @('-Pmigration',$location,'flyway:migrate','flyway:validate') -Environment $environment -Label m1-production-migrate
    Confirm-CampusDatabase -CredentialName root -Database campushub_test
    $null=Invoke-CampusSql -CredentialName root -Database campushub_test -Sql "GRANT SELECT,INSERT,UPDATE ON campushub_test.app_wechat_identity TO 'campushub_test_app'@'127.0.0.1';"
    Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
    $null=Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql 'SELECT COUNT(*) FROM app_wechat_identity;'
    Assert-BackendProbe
    [pscustomobject]@{Status='PASS';Migration='V8';Database='campushub_test';ProductionMigration='NOT_RUN'}
} finally { Assert-LegacyUnchanged $before }
