#requires -Version 5.1
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$module = Join-Path $repo 'scripts\lib\CampusHub.MySql.psm1'
if (-not (Test-Path -LiteralPath $module)) { throw 'FAIL: MySQL safety module is missing.' }
Import-Module $module -Force
$script:passed = 0
function Assert-Rejected([string]$Name, [scriptblock]$Action) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw "FAIL: $Name was not rejected." }
    $script:passed++
}
function Assert-Accepted([string]$Name, [scriptblock]$Action) {
    & $Action | Out-Null
    $script:passed++
}

# These exercise protection functions only, never service/database mutations.
Assert-Accepted 'contained path' { Assert-ContainedPath 'E:\CampusHubRuntime\mysql84' 'E:\CampusHubRuntime\mysql84\data' }
Assert-Rejected 'prefix lookalike' { Assert-ContainedPath 'E:\CampusHubRuntime\mysql84' 'E:\CampusHubRuntime\mysql840\data' }
Assert-Rejected 'parent traversal' { Assert-ContainedPath 'E:\CampusHubRuntime\mysql84' 'E:\CampusHubRuntime\mysql84\..\old' }
Assert-Rejected 'drive root' { Assert-ContainedPath 'E:\CampusHubRuntime\mysql84' 'E:\' }
Assert-Rejected 'old data directory' { Assert-ContainedPath 'E:\CampusHubRuntime\mysql84' 'C:\ProgramData\MySQL\MySQL Server 8.0\Data' }
Assert-Accepted 'test instance' { Assert-DatabaseIdentity -Version '8.4.11' -Port 3307 -DataDirectory 'E:/CampusHubRuntime/mysql84/data/' -Database 'campushub_test' -ExpectedDatabase 'campushub_test' }
Assert-Rejected 'old port' { Assert-DatabaseIdentity -Version '8.4.11' -Port 3306 -DataDirectory 'E:/CampusHubRuntime/mysql84/data/' -Database 'campushub_test' -ExpectedDatabase 'campushub_test' }
Assert-Rejected 'development database in test' { Assert-DatabaseIdentity -Version '8.4.11' -Port 3307 -DataDirectory 'E:/CampusHubRuntime/mysql84/data/' -Database 'campushub' -ExpectedDatabase 'campushub_test' }
Assert-Rejected 'old data instance' { Assert-DatabaseIdentity -Version '8.4.11' -Port 3307 -DataDirectory 'C:/ProgramData/MySQL/MySQL Server 8.0/Data/' -Database 'campushub_test' -ExpectedDatabase 'campushub_test' }
Assert-Rejected 'wrong version' { Assert-DatabaseIdentity -Version '8.0.44' -Port 3307 -DataDirectory 'E:/CampusHubRuntime/mysql84/data/' -Database 'campushub_test' -ExpectedDatabase 'campushub_test' }
Assert-Rejected 'unresolved SQL secret' { Expand-AccountSql 'CREATE USER u IDENTIFIED BY ''{{APP_PASSWORD}}'';' @{} }
Assert-Rejected 'SQL injection in secret' { Expand-AccountSql '{{APP_PASSWORD}}' @{ APP_PASSWORD="x'; GRANT ALL ON *.* TO u;" } }
Assert-Accepted 'generated secret rendering' { $sql=Expand-AccountSql '{{APP_PASSWORD}}' @{APP_PASSWORD=('a' * 64)}; if($sql -ne ('a' * 64)){throw 'SQL rendering mismatch.'} }

$template = [IO.File]::ReadAllText((Join-Path $repo 'infra\mysql\my.ini.template'))
Assert-Accepted 'approved server configuration' { Assert-MySqlConfiguration $template }
Assert-Rejected 'configuration points to old data' { Assert-MySqlConfiguration ($template.Replace('E:/CampusHubRuntime/mysql84/data', 'C:/ProgramData/MySQL/MySQL Server 8.0/Data')) }
Assert-Rejected 'configuration uses old port' { Assert-MySqlConfiguration ($template.Replace('port=3307', 'port=3306')) }
Assert-Rejected 'configuration adds external include' { Assert-MySqlConfiguration ($template + "`n!include C:/old.ini") }
Assert-Rejected 'configuration repeats a setting' { Assert-MySqlConfiguration ($template + "`nport=3307") }
Assert-Accepted 'exact service command' { Assert-ServiceCommandLine '"E:\CampusHubRuntime\mysql84\server\bin\mysqld.exe" --defaults-file="E:\CampusHubRuntime\mysql84\config\my.ini" MySQL84CampusHub' }
Assert-Rejected 'lookalike service configuration' { Assert-ServiceCommandLine '"E:\CampusHubRuntime\mysql84\server\bin\mysqld.exe" --defaults-file=E:\CampusHubRuntime\mysql84\config\my.ini.other MySQL84CampusHub' }
Assert-Rejected 'service overrides data directory' { Assert-ServiceCommandLine '"E:\CampusHubRuntime\mysql84\server\bin\mysqld.exe" --defaults-file=E:\CampusHubRuntime\mysql84\config\my.ini --datadir=C:\old MySQL84CampusHub' }
Assert-Accepted 'empty bootstrap privilege lists' { if (@(Compare-Object -ReferenceObject @() -DifferenceObject @()).Count -ne 0) { throw 'Empty privileges differ.' } }
Assert-Accepted 'unexpected bootstrap privilege detected' { if (@(Compare-Object -ReferenceObject @() -DifferenceObject @('CREATE')).Count -ne 1) { throw 'Unexpected privilege was not detected.' } }
Assert-Accepted 'test schema grant is literal' {
    $grant = [IO.File]::ReadAllText((Join-Path $repo 'infra\mysql\sql\03-grant-migration.sql'))
    if (-not $grant.Contains('ON `campushub\_test`.*') -or $grant.Contains('ON campushub_test.*')) { throw 'Test schema grant contains a wildcard.' }
}

Assert-Accepted 'native stdin preserves UTF-8' {
    $command = '[Console]::InputEncoding = New-Object Text.UTF8Encoding($false); [Console]::OutputEncoding = New-Object Text.UTF8Encoding($false); [Console]::Write([Console]::In.ReadToEnd())'
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
    $value = [string][char]0x6821 + [char]0x56ed + [char]0x2603
    $actual = Invoke-PrivateProcess -FilePath "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe" -Arguments @('-NoProfile','-NonInteractive','-EncodedCommand',$encoded) -InputText $value
    if ($actual -cne $value) { throw 'UTF-8 input/output changed.' }
}

[PSCustomObject]@{ Suite='Safety guards (no database)'; Status='PASS'; Passed=$script:passed; Failed=0 }
