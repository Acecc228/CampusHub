#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
if(-not $Apply){[pscustomobject]@{Mode='PREVIEW';Action='C-01 targeted real HTTP regression; retain isolated C02 fixture';Database='campushub_test';Address='127.0.0.1:3307'};return}
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
Assert-CampusService
Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
Assert-PrivateCredentialDirectory
$listeners=@(Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 8080 -State Listen -ErrorAction Stop)
if($listeners.Count -ne 1){throw 'Expected dedicated test backend'}
$process=Get-CimInstance Win32_Process -Filter ('ProcessId='+$listeners[0].OwningProcess)
if($process.CommandLine -notmatch '--campushub.database-purpose=test' -or $process.CommandLine -notmatch 'campushub-backend-0.1.0-SNAPSHOT.jar'){throw 'Refusing HTTP writes to an unverified backend'}
$before=Get-LegacyFingerprint
$oldApply=$env:CH_C02_APPLY
try {
    $env:CH_C02_APPLY='true'
    & node (Join-Path $PSScriptRoot '../miniprogram/tests/c02-real-api.cjs')
    if($LASTEXITCODE -ne 0){throw 'C02 client regression failed; see sanitized report'}
} finally { $env:CH_C02_APPLY=$oldApply; Assert-LegacyUnchanged $before }
