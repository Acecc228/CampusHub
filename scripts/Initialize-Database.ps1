#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
$c = Get-CampusHubConfig
$sqlRoot = Join-Path $c.RepoRoot 'infra\mysql\sql'
if (-not $Apply) {
    [PSCustomObject]@{ Mode='PREVIEW'; Target='127.0.0.1:3307'; Actions=@('Verify the new Windows service and listener.', 'Read temporary root password privately from the NEW log; rotate only the NEW root account.', 'Create campushub and campushub_test; create four distinct accounts.', 'Grant only schema-scoped migration privileges; app accounts remain without table access until Flyway creates tables.', 'Save separate protected client credential files outside Git; never emit passwords.'); SqlFiles=@('01-create-databases.sql','02-create-accounts.sql.template','03-grant-migration.sql') }
    return
}
Assert-CampusService
$before = Get-LegacyFingerprint
if (@(Get-ChildItem -LiteralPath $c.Secrets -Force).Count -ne 0) { throw 'Bootstrap credentials already exist. Stop for manual inspection; no automatic reset or resume.' }
$log = Join-Path $c.RuntimeRoot 'logs\mysql84.err'
Assert-NoReparsePoint $log
$temporary = @()
# The running server keeps a write handle open; share reads and writes without
# changing the log, service or ACL. Never emit its sensitive contents.
$stream = [IO.File]::Open($log, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
$reader = New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8)
try {
    while ($null -ne ($line = $reader.ReadLine())) {
        if ($line -match 'A temporary password is generated for root@localhost: ') { $temporary += $line }
    }
} finally { $reader.Dispose(); $stream.Dispose() }
if ($temporary.Count -ne 1) { throw 'Expected exactly one initialization password record. Stop for manual inspection.' }
$initialPassword = ($temporary[0] -split 'root@localhost: ', 2)[1].Trim()
Write-ClientCredential -Name 'root-initial' -User 'root' -Password $initialPassword
$rootPassword = New-DatabaseSecret
# Save before changing root so a later error cannot lose the new credential.
Write-ClientCredential -Name 'root' -User 'root' -Password $rootPassword
$null = Invoke-CampusSql -CredentialName 'root-initial' -ExpiredPassword -Sql "ALTER USER CURRENT_USER() IDENTIFIED BY '$rootPassword';"
$initialPassword = $null
$temporary = $null
$line = $null
$rootPassword = $null
# Root rotation is necessary before SQL on an expired bootstrap account. Service,
# binary/config paths and owning process were verified before this first write.
Confirm-CampusDatabase -CredentialName 'root'
$existing = (Invoke-CampusSql -CredentialName 'root' -Sql "SELECT (SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('campushub','campushub_test')) + (SELECT COUNT(*) FROM mysql.user WHERE user IN ('campushub_app','campushub_migrator','campushub_test_app','campushub_test_migrator'));").Trim()
if ($existing -ne '0') { throw 'Target schemas/accounts already exist. No automatic reuse, alteration or cleanup.' }
$passwords = @{
    APP_PASSWORD = New-DatabaseSecret
    MIGRATOR_PASSWORD = New-DatabaseSecret
    TEST_APP_PASSWORD = New-DatabaseSecret
    TEST_MIGRATOR_PASSWORD = New-DatabaseSecret
}
Write-ClientCredential -Name 'app' -User 'campushub_app' -Password $passwords.APP_PASSWORD
Write-ClientCredential -Name 'migrator' -User 'campushub_migrator' -Password $passwords.MIGRATOR_PASSWORD
Write-ClientCredential -Name 'test-app' -User 'campushub_test_app' -Password $passwords.TEST_APP_PASSWORD
Write-ClientCredential -Name 'test-migrator' -User 'campushub_test_migrator' -Password $passwords.TEST_MIGRATOR_PASSWORD
$null = Invoke-CampusSql -CredentialName 'root' -Sql ([IO.File]::ReadAllText((Join-Path $sqlRoot '01-create-databases.sql')))
$accountSql = Expand-AccountSql ([IO.File]::ReadAllText((Join-Path $sqlRoot '02-create-accounts.sql.template'))) $passwords
$null = Invoke-CampusSql -CredentialName 'root' -Sql $accountSql
$accountSql = $null
$passwords = $null
$null = Invoke-CampusSql -CredentialName 'root' -Sql ([IO.File]::ReadAllText((Join-Path $sqlRoot '03-grant-migration.sql')))
Confirm-CampusDatabase -CredentialName 'migrator' -Database 'campushub'
Confirm-CampusDatabase -CredentialName 'test-migrator' -Database 'campushub_test'
Assert-LegacyUnchanged $before
[PSCustomObject]@{ Status='PASS'; Stage='M1.0-B Database/account bootstrap'; BusinessTables='NOT_RUN: require separately authorized Flyway migration'; AppGrants='NOT_RUN: after migration'; LegacySql='NOT_RUN' }
