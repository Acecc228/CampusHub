#requires -Version 5.1
[CmdletBinding()]
param([ValidateSet('Bootstrap','Ready')][string]$Stage = 'Bootstrap', [string]$LegacyClientConfig)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
$c = Get-CampusHubConfig
$results = New-Object 'Collections.Generic.List[object]'
function Add-Check([string]$Name, [string]$Status, [string]$Detail) {
    $results.Add([PSCustomObject]@{ Check=$Name; Status=$Status; Detail=$Detail })
}
function Test-AccountPrivileges([string]$Credential, [string]$Account, [string]$Database, [bool]$Migration) {
    $selectDatabase = if ($Migration -or $Stage -eq 'Ready') { $Database } else { '' }
    Confirm-CampusDatabase -CredentialName $Credential -Database $selectDatabase
    $actualAccount = (Invoke-CampusSql -CredentialName $Credential -Sql 'SELECT CURRENT_USER();').Trim()
    if ($actualAccount -cne ($Account + '@127.0.0.1')) { throw 'Credential authenticates as an unexpected account.' }
    $grantee = ("'$Account'@'127.0.0.1'").Replace("'", "''")
    $query = @"
SELECT 'GLOBAL', PRIVILEGE_TYPE, IS_GRANTABLE FROM information_schema.USER_PRIVILEGES WHERE GRANTEE='$grantee'
UNION ALL SELECT CONCAT('SCHEMA:', TABLE_SCHEMA), PRIVILEGE_TYPE, IS_GRANTABLE FROM information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE='$grantee'
UNION ALL SELECT CONCAT('TABLE:', TABLE_SCHEMA, '.', TABLE_NAME), PRIVILEGE_TYPE, IS_GRANTABLE FROM information_schema.TABLE_PRIVILEGES WHERE GRANTEE='$grantee';
"@
    $lines = (Invoke-CampusSql -CredentialName $Credential -Sql $query).Trim()
    $actual = @()
    foreach ($line in ($lines -split '\r?\n')) {
        if (-not $line) { continue }
        $fields = $line.Split("`t")
        if ($fields.Count -ne 3 -or $fields[2] -ne 'NO') { throw 'Unexpected or grantable database privilege.' }
        if ($fields[0] -eq 'GLOBAL' -and $fields[1] -eq 'USAGE') { continue }
        $actual += ($fields[0] + ':' + $fields[1])
    }
    $expected = @()
    if ($Migration) {
        # SCHEMA_PRIVILEGES exposes the escaped mysql.db grant pattern.
        $grantSchema = $Database.Replace('_','\_')
        $expected = @('SELECT','INSERT','UPDATE','DELETE','CREATE','ALTER','INDEX','REFERENCES') | ForEach-Object { "SCHEMA:${grantSchema}:$_" }
    } elseif ($Stage -eq 'Ready') {
        $privileges = if ($Database -eq 'campushub') { @('SELECT') } else { @('SELECT','INSERT','UPDATE','DELETE') }
        $expected = $privileges | ForEach-Object { "TABLE:${Database}.activity_category:$_" }
    }
    if (@(Compare-Object -ReferenceObject @($expected | Sort-Object) -DifferenceObject @($actual | Sort-Object)).Count -ne 0) { throw 'Privileges differ from the approved per-schema/per-table matrix.' }
}

try {
    $legacy = Get-LegacyFingerprint
    if ($legacy.State -ne 'Running') { throw 'MySQL80 is not running.' }
    Add-Check 'Legacy service' 'PASS' 'Running; configuration read only.'
} catch { Add-Check 'Legacy service' 'FAIL' $_.Exception.Message }

if (-not (Get-CimInstance Win32_Service -Filter "Name='MySQL84CampusHub'")) {
    Add-Check 'CampusHub service' 'BLOCKED' 'Not installed. This read-only check never installs it.'
} else {
    try {
        Assert-CampusService
        $baselineFile = Join-Path $c.RuntimeRoot 'legacy-before.json'
        $saved = Get-Content -LiteralPath $baselineFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $before = @{}
        foreach ($property in $saved.PSObject.Properties) { $before[$property.Name] = $property.Value }
        Assert-LegacyUnchanged $before
        Add-Check 'Service and legacy baseline' 'PASS' 'Manual LocalService; explicit new binary/config; loopback port 3307; legacy fingerprint unchanged.'
        $accounts = @(
            @('app','campushub_app','campushub',$false),
            @('migrator','campushub_migrator','campushub',$true),
            @('test-app','campushub_test_app','campushub_test',$false),
            @('test-migrator','campushub_test_migrator','campushub_test',$true)
        )
        foreach ($account in $accounts) {
            if (-not (Test-Path -LiteralPath (Join-Path $c.Secrets ($account[0] + '.cnf')))) {
                Add-Check $account[1] 'BLOCKED' 'Bootstrap credential has not been created.'
                continue
            }
            try { Test-AccountPrivileges $account[0] $account[1] $account[2] $account[3]; Add-Check $account[1] 'PASS' 'Instance, account and exact privilege matrix verified by read-only SQL.' }
            catch { Add-Check $account[1] 'FAIL' $_.Exception.Message }
        }
    } catch { Add-Check 'Service and legacy baseline' 'FAIL' $_.Exception.Message }
}

if ($LegacyClientConfig) {
    try {
        $file = [IO.Path]::GetFullPath($LegacyClientConfig)
        Assert-NoReparsePoint $file
        if ($file.StartsWith($c.RepoRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Do not store real credentials inside the repository.' }
        $row = (Invoke-PrivateProcess -FilePath $c.LegacyClient -Arguments @("--defaults-file=$file",'--no-login-paths','--protocol=TCP','--host=127.0.0.1','--port=3306','--batch','--raw','--skip-column-names') -InputText 'SELECT VERSION(), @@port, @@datadir, 1;').Trim().Split("`t")
        if ($row.Count -ne 4 -or $row[0] -ne '8.0.44' -or $row[1] -ne '3306' -or $row[2].Replace('/','\').TrimEnd('\') -ine 'C:\ProgramData\MySQL\MySQL Server 8.0\Data' -or $row[3] -ne '1') { throw 'Unexpected original instance SQL result.' }
        Add-Check 'Legacy SQL' 'PASS' 'SELECT verified original version/port/data directory; no data changes.'
    } catch { Add-Check 'Legacy SQL' 'FAIL' $_.Exception.Message }
} else { Add-Check 'Legacy SQL' 'BLOCKED' 'Supply an existing protected client configuration outside Git; do not send passwords in chat.' }

Add-Check 'Restart isolation' 'NOT_RUN' 'Requires separately approved new-service stop/start and legacy SQL checks at both points.'
Add-Check 'Backend integration' 'NOT_RUN' 'Flyway, backend API and documentation tests require separate authorization after M1.0-B database/account bootstrap.'
$status = if (@($results | Where-Object Status -eq 'FAIL').Count) { 'FAIL' } elseif (@($results | Where-Object Status -eq 'BLOCKED').Count) { 'BLOCKED' } else { 'PASS' }
[PSCustomObject]@{ CheckedAt=(Get-Date).ToUniversalTime().ToString('o'); Stage=$Stage; Overall=$status; Results=@($results.ToArray()) } | ConvertTo-Json -Depth 5
if ($status -eq 'FAIL') { exit 1 }
if ($status -eq 'BLOCKED') { exit 2 }
