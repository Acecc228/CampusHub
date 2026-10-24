#requires -Version 5.1
[CmdletBinding()]
param(
    [switch]$Apply,
    [ValidateRange(1,2147483647)][int]$ActivityId,
    [ValidatePattern('^[a-z0-9-]{1,40}$')][string]$Checkpoint,
    [ValidateSet('PUBLISHED','UNPUBLISHED')][string]$ExpectedActivityStatus,
    [ValidateRange(0,2147483647)][int]$ExpectedCount,
    [ValidateSet('NONE','CONFIRMED','CANCELLED')][string]$ExpectedRegistrationStatus,
    [ValidateRange(0,2147483647)][int]$ExpectedRegistrationVersion,
    [switch]$Final
)
$ErrorActionPreference='Stop'
if(-not $Apply){[pscustomobject]@{Mode='PREVIEW';Action='Read-only M1.4-C-01 test database checkpoint; save sanitized evidence';Database='campushub_test';Address='127.0.0.1:3307'};return}
if(-not $ActivityId -or -not $Checkpoint -or -not $ExpectedActivityStatus -or -not $ExpectedRegistrationStatus){throw 'Explicit checkpoint and expected state required'}
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
$activity=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql ('SELECT id,status,version,capacity,registered_count,creator_id FROM activity WHERE id='+$ActivityId+" AND title LIKE 'M14C01-%';")).Trim().Split("`t")
if($activity.Count -ne 6){throw 'Expected isolated M14C01 activity'}
$rows=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql ('SELECT id,activity_id,user_id,status,version FROM activity_registration WHERE activity_id='+$ActivityId+' ORDER BY id;')).Trim()
$registrations=@()
if($rows){$registrations=@($rows -split '\r?\n' | ForEach-Object {$v=$_ -split "`t";[pscustomobject]@{Id=[long]$v[0];ActivityId=[long]$v[1];UserId=[long]$v[2];Status=$v[3];Version=[long]$v[4]}})}
$confirmed=@($registrations | Where-Object Status -eq 'CONFIRMED').Count
if($activity[1] -cne $ExpectedActivityStatus -or [int]$activity[4] -ne $ExpectedCount -or $confirmed -ne $ExpectedCount){throw 'Activity count/status mismatch'}
if($ExpectedRegistrationStatus -eq 'NONE'){
    if($registrations.Count -ne 0){throw 'Unexpected registration'}
}else{
    if($registrations.Count -ne 1 -or $registrations[0].Status -cne $ExpectedRegistrationStatus -or $registrations[0].Version -ne $ExpectedRegistrationVersion){throw 'Registration state/version mismatch'}
}
$result=[ordered]@{Status='PASS';Checkpoint=$Checkpoint;At=[DateTime]::UtcNow.ToString('o');Database='campushub_test';Address='127.0.0.1:3307';Credential='test-app';Activity=[ordered]@{Id=[long]$activity[0];Status=$activity[1];Version=[long]$activity[2];Capacity=[int]$activity[3];RegisteredCount=[int]$activity[4];CreatorId=[long]$activity[5]};Registrations=$registrations;ConfirmedRows=$confirmed;DatabaseMutation='NONE'}
if($Final){
    Assert-BackendProbe
    $mismatches=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql "SELECT COUNT(*) FROM activity a WHERE a.registered_count<>(SELECT COUNT(*) FROM activity_registration r WHERE r.activity_id=a.id AND r.status='CONFIRMED');").Trim()
    $duplicates=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql 'SELECT COUNT(*) FROM (SELECT activity_id,user_id FROM activity_registration GROUP BY activity_id,user_id HAVING COUNT(*)>1) d;').Trim()
    $dev=(Invoke-CampusSql -CredentialName app -Database campushub -Sql 'SELECT (SELECT COUNT(*) FROM activity),(SELECT COUNT(*) FROM activity_registration),(SELECT COUNT(*) FROM app_user);').Trim()
    if($mismatches -cne '0' -or $duplicates -cne '0' -or $dev -cne "0`t0`t3"){throw 'Final database invariant mismatch'}
    $result.Final=[ordered]@{CountMismatches=0;Duplicates=0;OriginalProbe='PASS';DevActivities=0;DevRegistrations=0;DevUsers=3;MySQL80='No connection or mutation'}
}
$directory=Join-Path $PSScriptRoot '../docs/verification/m1.4-c-01'
if(-not(Test-Path -LiteralPath $directory -PathType Container)){throw 'Evidence directory must already exist'}
$destination=Join-Path $directory ('sql-'+$Checkpoint+'.json')
if(Test-Path -LiteralPath $destination){throw 'Evidence already exists; choose a new checkpoint, do not overwrite'}
$result | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $destination -Encoding UTF8
[pscustomobject]@{Status='PASS';Checkpoint=$Checkpoint;ActivityId=$ActivityId;RegisteredCount=$ExpectedCount;RegistrationStatus=$ExpectedRegistrationStatus;RegistrationVersion=$ExpectedRegistrationVersion}
