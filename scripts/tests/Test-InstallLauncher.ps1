#requires -Version 5.1
$ErrorActionPreference='Stop'
$repo=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$launcher=Join-Path $repo 'scripts\Invoke-ReviewedMySqlInstall.ps1'
if (-not (Test-Path -LiteralPath $launcher)) { throw 'FAIL: reviewed installation launcher is missing.' }
$snapshot=& (Join-Path $repo 'scripts\Test-Environment.ps1') | ConvertFrom-Json
$preview=& $launcher
if ($preview.Mode -cne 'PREVIEW' -or $preview.Status -cne 'PASS' -or $preview.SourceFilesVerified -ne 6 -or $preview.PackageSignature -cne 'VALIDSIG') { throw 'FAIL: default launcher must verify frozen source and package without installing.' }
$text=[IO.File]::ReadAllText($launcher)
foreach($forbidden in @('Bypass','Unrestricted','Set-ExecutionPolicy','Start-Process','Initialize-Database.ps1','Remove-Item','Set-Acl')) {
    if($text.Contains($forbidden)){throw 'FAIL: launcher contains an out-of-scope operation.'}
}
$tokens=$null; $errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($launcher,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'FAIL: launcher syntax invalid.'}
$applyCommands=@($ast.FindAll({param($node) $node -is [Management.Automation.Language.CommandAst] -and @($node.CommandElements | Where-Object {$_ -is [Management.Automation.Language.CommandParameterAst] -and $_.ParameterName -eq 'Apply'}).Count -gt 0},$true))
if($applyCommands.Count -ne 1 -or $applyCommands[0].Extent.Text -notmatch 'Install-MySql84\.ps1'){throw 'FAIL: expected exactly one guarded installer call.'}
$after=& (Join-Path $repo 'scripts\Test-Environment.ps1') | ConvertFrom-Json
if(($snapshot.LegacyFingerprint | ConvertTo-Json -Compress) -cne ($after.LegacyFingerprint | ConvertTo-Json -Compress) -or $snapshot.RuntimeExists -ne $after.RuntimeExists -or $snapshot.Services.Count -ne $after.Services.Count){throw 'FAIL: default launcher changed instance state.'}
[pscustomobject]@{Status='PASS';Checks=5;ApplyExecuted=$false;EffectivePolicy=(Get-ExecutionPolicy).ToString();SourceFilesVerified=$preview.SourceFilesVerified;PackageSignature=$preview.PackageSignature}
