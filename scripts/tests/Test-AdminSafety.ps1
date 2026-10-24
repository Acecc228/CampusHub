#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
if (-not $Apply) { [pscustomobject]@{Mode='PREVIEW';Action='Parse admin scripts, verify preview guards, and reject an occupied test backend port'}; return }
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$results = @()
foreach ($name in @('Invoke-AdminTestBackend.ps1','Test-AdminWeb.ps1','Test-AdminDatabase.ps1')) {
    $path = Join-Path $repo ('scripts\' + $name)
    $tokens=$null; $parseErrors=$null
    $null=[Management.Automation.Language.Parser]::ParseFile($path,[ref]$tokens,[ref]$parseErrors)
    if ($parseErrors.Count) { throw ('Parse failure: ' + $name) }
    $preview = & $path
    if ($preview.Mode -cne 'PREVIEW') { throw ('Default preview missing: ' + $name) }
    $results += [pscustomobject]@{Check=$name;Parse='PASS';DefaultPreview='PASS'}
}
$listeners=@(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction Stop)
$owners=@($listeners | Select-Object -ExpandProperty OwningProcess -Unique)
if ($owners.Count -ne 1) { throw 'Start the dedicated test backend before checking its occupied-port guard.' }
$refused=$false
try { & (Join-Path $repo 'scripts\Invoke-AdminTestBackend.ps1') -Apply }
catch { if ($_.Exception.Message -eq 'Port 8080 is already occupied; no process was stopped.') { $refused=$true } else { throw } }
if (-not $refused -or -not (Get-Process -Id $owners[0] -ErrorAction SilentlyContinue)) { throw 'Occupied-port guard failed' }
$report=[pscustomobject]@{Status='PASS';PowerShell=$PSVersionTable.PSVersion.ToString();Scripts=$results;OccupiedPortRefused='PASS';ExistingProcessPreserved='PASS';DatabaseWrites='NONE'}
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $repo 'docs\verification\m1.4-a-script-guards.json') -Encoding UTF8
$report
