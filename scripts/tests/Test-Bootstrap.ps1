#requires -Version 5.1
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$source = [IO.File]::ReadAllText((Join-Path $repo 'scripts\Initialize-Database.ps1'))
# Exercise the actual log-reader statements without running bootstrap SQL.
$start = $source.IndexOf('$temporary =')
$end = $source.IndexOf("Write-ClientCredential -Name 'root-initial'")
if ($start -lt 0 -or $end -le $start) { throw 'Cannot isolate the bootstrap log reader.' }
$reader = [scriptblock]::Create($source.Substring($start, $end - $start) + '; $initialPassword')
$fixtureRoot = Join-Path $repo ('.local\tests\bootstrap-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $fixtureRoot
$results = New-Object 'Collections.Generic.List[object]'
function Check([string]$Name, [scriptblock]$Action) {
    try { & $Action; $results.Add([pscustomobject]@{Check=$Name;Status='PASS'}) }
    catch { $results.Add([pscustomobject]@{Check=$Name;Status='FAIL';ErrorType=$_.Exception.GetBaseException().GetType().Name}) }
}
function Read-Fixture([string]$Text) {
    $log = Join-Path $fixtureRoot ([guid]::NewGuid().ToString('N') + '.txt')
    [IO.File]::WriteAllText($log, $Text, (New-Object Text.UTF8Encoding($false)))
    # Simulate the running server holding its log open for writing.
    $writer = [IO.File]::Open($log, [IO.FileMode]::Open, [IO.FileAccess]::Write, [IO.FileShare]::ReadWrite)
    try { & $reader } finally { $writer.Dispose() }
}
Check 'Read bootstrap record while server holds a write handle' {
    $actual = Read-Fixture 'fixture: A temporary password is generated for root@localhost: fixture-only-value'
    if ($actual -cne 'fixture-only-value') { throw 'Fixture value mismatch.' }
}
foreach ($case in @(
    @{Name='Reject missing bootstrap record';Text='fixture without a record'},
    @{Name='Reject ambiguous bootstrap records';Text="A temporary password is generated for root@localhost: one`nA temporary password is generated for root@localhost: two"}
)) {
    Check $case.Name {
        $rejected = $false
        try { $null = Read-Fixture $case.Text } catch { $rejected = $_.Exception.Message -like 'Expected exactly one initialization password record.*' }
        if (-not $rejected) { throw 'Expected record-count rejection was not observed.' }
    }
}
Check 'Early environment failure returns sanitized JSON before database access' {
    $output = & {
        # Isolate only the external environment; the real script handles failure.
        function Import-Module { param($Name, [switch]$Force) }
        function Get-CampusHubConfig { return @{RepoRoot=$repo} }
        function Get-LegacyFingerprint { throw 'fixture-private-error' }
        function Assert-CampusService { throw 'Database gate must not be reached.' }
        & (Join-Path $repo 'scripts\Test-DatabaseBootstrap.ps1') -Apply
    }
    $report = $output | ConvertFrom-Json
    if ($report.Status -ne 'FAIL' -or $report.Failed -ne 1 -or $report.Passed -ne 0 -or ($output -join '') -match 'fixture-private-error') { throw 'Expected sanitized FAIL report.' }
}
$failed = @($results | Where-Object Status -eq 'FAIL').Count
[pscustomobject]@{Suite='Bootstrap guards (non-sensitive fixtures; no database)';Passed=@($results | Where-Object Status -eq 'PASS').Count;Failed=$failed;Results=$results.ToArray()} | ConvertTo-Json -Depth 4
if ($failed) { exit 1 }
exit 0
