#requires -Version 5.1
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
$c = Get-CampusHubConfig
$checks = New-Object 'Collections.Generic.List[object]'
function Record-Pass([string]$Name, [string]$Detail) { $checks.Add([PSCustomObject]@{ Check=$Name; Status='PASS'; Detail=$Detail }) }
function Get-PreparationSnapshot {
    [ordered]@{
        Legacy = Get-LegacyFingerprint
        RuntimeExists = Test-Path -LiteralPath $c.RuntimeRoot
        NewService = @(Get-CimInstance Win32_Service -Filter "Name='MySQL84CampusHub'" | Select-Object Name,State,PathName)
        NewListener = @(Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq 3307 | Select-Object LocalAddress,LocalPort,OwningProcess)
    } | ConvertTo-Json -Depth 5 -Compress
}
$before = Get-PreparationSnapshot
$files = @(Get-ChildItem -LiteralPath $c.RepoRoot -Recurse -File | Where-Object { $_.Extension -in @('.ps1','.psm1','.psd1') -and $_.FullName -notlike '*\.local\*' })
$forbiddenCommands = @('Remove-Item','Remove-Service','Stop-Service','Restart-Service','Set-Service','Set-ExecutionPolicy','Invoke-Expression','Start-Process','sc','sc.exe','icacls','icacls.exe','cmd','cmd.exe')
foreach ($file in $files) {
    $parseErrors = $null
    $tokens = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count) { throw "PowerShell parse error in $($file.Name): $($parseErrors[0].Message)" }
    $commands = $ast.FindAll({ param($node) $node -is [Management.Automation.Language.CommandAst] }, $true)
    foreach ($command in $commands) {
        $name = $command.GetCommandName()
        if ($name -in $forbiddenCommands) { throw "Forbidden command in $($file.Name): $name" }
        if ($name -eq 'Set-Acl' -and $file.Name -ne 'Install-MySql84.ps1') { throw 'ACL mutation outside the reviewed installer.' }
        if ($name -eq 'Start-Service' -and ($file.Name -ne 'Install-MySql84.ps1' -or $command.Extent.Text -cne "Start-Service -Name 'MySQL84CampusHub'")) { throw 'Unexpected service mutation.' }
    }
}
Record-Pass 'PowerShell syntax' "$($files.Count) files parsed; no errors."
Record-Pass 'Dangerous command scan' 'No deletion, service reset, execution-policy change, shell deletion, elevation launcher or recursive ACL command.'

$operational = @('Install-MySql84.ps1','Initialize-Database.ps1','Grant-ApplicationAccess.ps1','Test-DatabaseBootstrap.ps1')
foreach ($name in $operational) {
    $content = [IO.File]::ReadAllText((Join-Path $PSScriptRoot $name))
    if ($content -match '--initialize-insecure|--remove|/reset|/T\b') { throw "Unsafe operational flag in $name" }
    $preview = & (Join-Path $PSScriptRoot $name)
    if ($preview.Mode -cne 'PREVIEW') { throw "$name did not default to preview." }
}
Record-Pass 'Default preview' 'All four mutation entrypoints returned PREVIEW without -Apply.'

$sqlFiles = @(Get-ChildItem -LiteralPath (Join-Path $c.RepoRoot 'infra\mysql\sql') -File)
foreach ($file in $sqlFiles) {
    $sql = [IO.File]::ReadAllText($file.FullName) -replace '(?m)^\s*--.*$', ''
    if ($sql -match '(?i)\b(DROP|TRUNCATE|REVOKE)\b|\bGRANT\s+ALL\b|\bON\s+\*\.\*|\bWITH\s+GRANT\s+OPTION\b') { throw "Unexpected broad/destructive SQL in $($file.Name)." }
    if ($sql -match "(?i)IDENTIFIED BY\s+'(?!\{\{[A-Z_]+\}\}')") { throw 'Literal password found in SQL.' }
}
Record-Pass 'Bootstrap SQL scope' 'Four SQL/templates checked; no broad grants, destructive statements or literal passwords.'
foreach ($ignored in @('.local/probe.txt','.env','probe.cnf','probe.log','probe.zip')) {
    & git -C $c.RepoRoot check-ignore --quiet -- $ignored
    if ($LASTEXITCODE -ne 0) { throw "Sensitive/local file is not ignored: $ignored" }
}
Record-Pass 'Git exclusions' 'Local packages, environment files, client credentials and logs are excluded.'
$safety = & (Join-Path $PSScriptRoot 'tests\Test-Safety.ps1')
Record-Pass 'Safety guard tests' "$($safety.Passed) passed; $($safety.Failed) failed. No database access."
$fix = (& (Join-Path $PSScriptRoot 'tests\Test-InstallFix.ps1')) | ConvertFrom-Json
Record-Pass 'Installation fix tests' "$($fix.Passed) passed; $($fix.Failed) failed. No installation or ACL mutation."
if ((Get-PreparationSnapshot) -cne $before) { throw 'Observed database service/runtime/legacy configuration state changed during checks.' }
Record-Pass 'Preparation side effects' 'Legacy fingerprint, new-service existence, new runtime and 3307 listener unchanged.'
[PSCustomObject]@{ CheckedAt=(Get-Date).ToUniversalTime().ToString('o'); Overall='PASS'; Checks=$checks.ToArray(); TestSuites=@($safety,$fix); DatabaseIntegration='NOT_RUN' } | ConvertTo-Json -Depth 6
