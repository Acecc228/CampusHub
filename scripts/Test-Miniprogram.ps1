#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
if(-not $Apply){[pscustomobject]@{Mode='PREVIEW';Action='Real HTTP client integration; retained M14B fixtures only in campushub_test';Address='127.0.0.1:3307'};return}
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
Assert-PrivateCredentialDirectory
$listeners=@(Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 8080 -State Listen -ErrorAction Stop)
if($listeners.Count -ne 1){throw 'Expected dedicated test backend'}
$process=Get-CimInstance Win32_Process -Filter ('ProcessId='+$listeners[0].OwningProcess)
if($process.CommandLine -notmatch '--campushub.database-purpose=test' -or $process.CommandLine -notmatch 'campushub-backend-0.1.0-SNAPSHOT.jar'){throw 'Refusing HTTP writes to an unverified backend'}
$before=Get-LegacyFingerprint
$oldApply=$env:CH_MINI_APPLY
$oldStudent=$env:CH_MINI_STUDENT
try {
    $student='m14b_'+[Guid]::NewGuid().ToString('N').Substring(0,12)
    $sql="START TRANSACTION; INSERT INTO app_user(login_name,display_name,password_hash,enabled,demo_account) SELECT '$student','M1.4-B test student',password_hash,1,1 FROM app_user WHERE login_name='demo_student'; INSERT INTO app_user_role(user_id,role_id) SELECT u.id,r.id FROM app_user u CROSS JOIN app_role r WHERE u.login_name='$student' AND r.code='STUDENT'; COMMIT;"
    $null=Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql $sql
    $env:CH_MINI_APPLY='true'; $env:CH_MINI_STUDENT=$student
    & node (Join-Path $PSScriptRoot '../miniprogram/tests/real-api.cjs')
    if($LASTEXITCODE -ne 0){throw 'Real API verification failed; see sanitized report'}
    $sql="SELECT 'count_consistency',COUNT(*) FROM activity a WHERE a.registered_count<>(SELECT COUNT(*) FROM activity_registration r WHERE r.activity_id=a.id AND r.status='CONFIRMED'); SELECT 'duplicates',COUNT(*) FROM (SELECT activity_id,user_id FROM activity_registration GROUP BY activity_id,user_id HAVING COUNT(*)>1) x; SELECT 'M14B_activities',COUNT(*) FROM activity WHERE title LIKE 'M14B-%';"
    $state=Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql $sql
    $state+=Invoke-CampusSql -CredentialName test-migrator -Database campushub_test -Sql "SELECT 'probe',COUNT(*) FROM m10b_unicode_probe;"
    $state | Set-Content -LiteralPath (Join-Path $PSScriptRoot '../docs/verification/m1.4-b-sql-state.txt') -Encoding UTF8
    if($state -notmatch 'count_consistency\s+0' -or $state -notmatch 'duplicates\s+0' -or $state -notmatch 'probe\s+1'){throw 'Final SQL invariant failed'}
} finally { $env:CH_MINI_APPLY=$oldApply; $env:CH_MINI_STUDENT=$oldStudent; Assert-LegacyUnchanged $before }
