#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
$c = Get-CampusHubConfig
if (-not $Apply) {
    [pscustomobject]@{Mode='PREVIEW';Target='127.0.0.1:3307/campushub_test';Actions=@('Read authenticated identity, schema, account, TLS and credential ACL metadata.', 'Verify access denial using read-only queries.', 'Create m10b_unicode_probe only in campushub_test; preserve one Unicode evidence row; verify transaction rollback.', 'Fail on any existing probe table; never drop, clean, grant or retry bootstrap.')}
    return
}
$results = New-Object 'Collections.Generic.List[object]'
$step = 'Instance and legacy baseline'
function Pass([string]$Name, [string]$Detail) {
    $results.Add([pscustomobject]@{Check=$Name;Status='PASS';Detail=$Detail})
}
function Expect-Denied([string]$Credential, [string]$Database, [string]$Sql, [string]$Code) {
    $denied = $false
    try { $null = Invoke-CampusSql -CredentialName $Credential -Database $Database -Sql $Sql }
    catch { $denied = $_.Exception.Message -match ('MYSQL_' + $Code + '_') }
    if (-not $denied) { throw 'Expected MySQL denial code was not observed.' }
}
try {
    $legacyBefore = Get-LegacyFingerprint
    Assert-CampusService
    Confirm-CampusDatabase -CredentialName root
    $saved = Get-Content -LiteralPath (Join-Path $c.RuntimeRoot 'legacy-before.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $baseline = @{}
    foreach ($p in $saved.PSObject.Properties) { $baseline[$p.Name] = $p.Value }
    Assert-LegacyUnchanged $baseline
    Pass $step 'Authenticated root@localhost; 8.4.11, port 3307, approved data directory and partial_revokes=OFF; MySQL80 baseline unchanged.'

    $step = 'Schema encoding and empty development schema'
    $actual = (Invoke-CampusSql root "SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('campushub','campushub_test') ORDER BY SCHEMA_NAME;").Trim()
    $expected = "campushub`tutf8mb4`tutf8mb4_0900_ai_ci`ncampushub_test`tutf8mb4`tutf8mb4_0900_ai_ci"
    if (($actual -replace "`r",'') -cne $expected) { throw 'Unexpected schema encoding.' }
    if ((Invoke-CampusSql root "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='campushub';").Trim() -ne '0') { throw 'Development schema is not empty.' }
    Pass $step 'Both databases use utf8mb4 / utf8mb4_0900_ai_ci; campushub has no tables.'

    $step = 'Account host and authentication settings'
    $rows = (Invoke-CampusSql root "SELECT User, Host, plugin, password_expired, account_locked FROM mysql.user WHERE User IN ('campushub_app','campushub_migrator','campushub_test_app','campushub_test_migrator') ORDER BY User;").Trim() -split '\r?\n'
    $users = @('campushub_app','campushub_migrator','campushub_test_app','campushub_test_migrator')
    if ($rows.Count -ne 4) { throw 'Unexpected account count.' }
    for ($i=0; $i -lt 4; $i++) {
        if ($rows[$i] -cne ($users[$i] + "`t127.0.0.1`tcaching_sha2_password`tN`tN")) { throw 'Unexpected account authentication settings.' }
    }
    Pass $step 'Exactly four approved accounts, all @127.0.0.1, caching_sha2_password, non-expired and unlocked.'

    $step = 'Credential ACL and independent passwords'
    Assert-PrivateCredentialDirectory
    $owner = (Get-Acl -LiteralPath $c.RepoRoot).Owner
    $sid = (New-Object Security.Principal.NTAccount($owner)).Translate([Security.Principal.SecurityIdentifier]).Value
    $allowed = @($sid,'S-1-5-18','S-1-5-32-544')
    $passwords = @()
    foreach ($name in @('root-initial','root','app','migrator','test-app','test-migrator')) {
        $path = Join-Path $c.Secrets ($name + '.cnf')
        Assert-NoReparsePoint $path
        foreach ($rule in (Get-Acl -LiteralPath $path).GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])) {
            if ($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Value -notin $allowed) { throw 'Unexpected credential file ACL.' }
        }
        if ($name -ne 'root-initial') {
            $text = [IO.File]::ReadAllText($path)
            if ($text -cnotmatch '(?m)^password="([a-f0-9]{64})"$') { throw 'Unexpected generated secret format.' }
            $passwords += $Matches[1]
        }
    }
    if (@($passwords | Select-Object -Unique).Count -ne 5) { throw 'Passwords are not independent.' }
    $passwords = $null; $text = $null; $Matches.Clear()
    Pass $step 'Six protected files outside Git; five independent 256-bit generated secrets; no additional allowed Windows identities.'

    $step = 'Initial root password invalidated'
    Expect-Denied root-initial '' 'SELECT 1;' '1045'
    Pass $step 'Old bootstrap root credential rejected with MySQL 1045.'

    $step = 'Encrypted authenticated connections'
    foreach ($name in @('root','app','migrator','test-app','test-migrator')) {
        Confirm-CampusDatabase -CredentialName $name
        $cipher = (Invoke-CampusSql $name "SHOW SESSION STATUS LIKE 'Ssl_cipher';").Trim().Split("`t")
        if ($cipher.Count -ne 2 -or -not $cipher[1]) { throw 'Session is not encrypted.' }
    }
    Pass $step 'All five current credentials authenticate to the approved instance over TLS.'

    $step = 'Migration accounts reject cross-schema access'
    Expect-Denied migrator campushub_test 'SELECT 1;' '1044'
    Expect-Denied test-migrator campushub 'SELECT 1;' '1044'
    Pass $step 'Both cross-schema connection attempts denied with MySQL 1044; no cross-schema writes attempted.'

    $step = 'Bootstrap application accounts reject schema access'
    foreach ($name in @('app','test-app')) {
        foreach ($database in @('campushub','campushub_test')) { Expect-Denied $name $database 'SELECT 1;' '1044' }
    }
    Pass $step 'Both application accounts authenticate but cannot select either database; USAGE-only policy preserved.'

    $step = 'Unicode test target and conflict guard'
    Confirm-CampusDatabase -CredentialName test-migrator -Database campushub_test
    $count = (Invoke-CampusSql test-migrator "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='campushub_test' AND TABLE_NAME='m10b_unicode_probe';" campushub_test).Trim()
    if ($count -ne '0') { throw 'Probe table already exists; no automatic reuse, cleanup or retry.' }
    Pass $step 'Only campushub_test on port 3307 can receive fixture writes; table name is unused.'

    $step = 'Unicode committed round trip'
    # Chinese, snowman, emoji, a supplementary CJK character and combining accent.
    # ASCII source remains executable with Windows PowerShell 5.1 UTF-8 handling.
    $value = [string][char]0x6821 + [char]0x56ED + [char]0x6D3B + [char]0x52A8 + '|' + [char]0x2603 + '|' + [char]::ConvertFromUtf32(0x1F600) + '|' + [char]::ConvertFromUtf32(0x20000) + '|e' + [char]0x0301
    $sql = "CREATE TABLE m10b_unicode_probe (id INT PRIMARY KEY, payload VARCHAR(100) NOT NULL) ENGINE=InnoDB; START TRANSACTION; INSERT INTO m10b_unicode_probe (id,payload) VALUES (1,'$value'); COMMIT;"
    $null = Invoke-CampusSql test-migrator $sql campushub_test
    $row = (Invoke-CampusSql test-migrator 'SELECT payload, HEX(payload) FROM m10b_unicode_probe WHERE id=1;' campushub_test).TrimEnd("`r","`n").Split("`t")
    $hex = ([BitConverter]::ToString([Text.Encoding]::UTF8.GetBytes($value))).Replace('-','')
    if ($row.Count -ne 2 -or $row[0] -cne $value -or $row[1] -cne $hex) { throw 'Unicode round trip mismatch.' }
    Pass $step 'Committed and read through a new real MySQL connection; text and exact UTF-8 bytes match. One evidence row retained in campushub_test.m10b_unicode_probe.'

    $step = 'Transaction rollback in test schema'
    $null = Invoke-CampusSql test-migrator "START TRANSACTION; INSERT INTO m10b_unicode_probe (id,payload) VALUES (2,'rollback-only'); ROLLBACK;" campushub_test
    $ids = (Invoke-CampusSql test-migrator 'SELECT GROUP_CONCAT(id ORDER BY id) FROM m10b_unicode_probe;' campushub_test).Trim()
    if ($ids -cne '1') { throw 'Rollback did not preserve exactly the committed evidence row.' }
    Pass $step 'A new connection sees id=1 only; uncommitted id=2 was rolled back.'

    $step = 'Test application has no fixture table access'
    Expect-Denied test-app '' 'SELECT payload FROM campushub_test.m10b_unicode_probe;' '1142'
    Pass $step 'Existing test table SELECT denied with MySQL 1142; no temporary extra grant was used.'

    $step = 'Final MySQL80 baseline'
    Assert-LegacyUnchanged $legacyBefore
    Assert-LegacyUnchanged $baseline
    Pass $step 'Service state, command, account, startup mode and configuration hash remain unchanged.'
} catch {
    $results.Add([pscustomobject]@{Check=$step;Status='FAIL';Detail='Stopped at first failure. No cleanup or automatic retry.';ErrorType=$_.Exception.GetBaseException().GetType().Name;Line=$_.InvocationInfo.ScriptLineNumber;RawErrorWithheld=$true})
}
$failed = @($results | Where-Object Status -eq 'FAIL').Count
$status = if ($failed) { 'FAIL' } else { 'PASS' }
[pscustomobject]@{CheckedAtUtc=[DateTime]::UtcNow.ToString('o');Stage='M1.0-B real database verification';Status=$status;Passed=@($results | Where-Object Status -eq 'PASS').Count;Failed=$failed;Results=$results.ToArray();Flyway='NOT_RUN';Backend='NOT_RUN';LegacySql='NOT_RUN: no connection to the legacy instance'} | ConvertTo-Json -Depth 6
if ($failed) { exit 1 }
exit 0
