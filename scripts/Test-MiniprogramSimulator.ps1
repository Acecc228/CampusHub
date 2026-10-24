#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply, [ValidatePattern('^[a-z0-9-]{0,24}$')][string]$VisualLabel='')
$ErrorActionPreference='Stop'
if(-not $Apply){[pscustomobject]@{Mode='PREVIEW';Action='Native WeChat simulator test; uses actual test student, may register/cancel M14B fixtures';Database='campushub_test'};return}
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
Assert-PrivateCredentialDirectory
$listeners=@(Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 8080 -State Listen -ErrorAction Stop)
if($listeners.Count -ne 1){throw 'Expected dedicated test backend'}
$process=Get-CimInstance Win32_Process -Filter ('ProcessId='+$listeners[0].OwningProcess)
if($process.CommandLine -notmatch '--campushub.database-purpose=test'){throw 'Refusing simulator writes against an unverified backend'}
$oldApply=$env:CH_MINI_APPLY
try {
    $env:CH_MINI_APPLY='true'
    if($VisualLabel){ & node (Join-Path $PSScriptRoot '../miniprogram/tests/visual.cjs') $VisualLabel }
    else { & node (Join-Path $PSScriptRoot '../miniprogram/tests/simulator.cjs') }
    if($LASTEXITCODE -ne 0){throw 'Simulator checks failed; see sanitized report'}
}
finally { $env:CH_MINI_APPLY=$oldApply }
