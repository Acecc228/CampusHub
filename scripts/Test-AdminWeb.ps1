#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
if (-not $Apply) { [pscustomobject]@{Mode='PREVIEW';Action='Real browser tests; creates retained test students, M14A activities and registrations only in campushub_test'}; return }
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
Assert-PrivateCredentialDirectory
# Check the actual backend process is the dedicated test launcher before permitting HTTP writes.
$listeners=@(Get-NetTCPConnection -LocalAddress 127.0.0.1 -LocalPort 8080 -State Listen -ErrorAction Stop)
if ($listeners.Count -ne 1) { throw 'Expected one dedicated test backend.' }
$javaProcess=Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listeners[0].OwningProcess)
if ($javaProcess.CommandLine -notmatch '--campushub.database-purpose=test' -or $javaProcess.CommandLine -notmatch 'campushub-backend-0.1.0-SNAPSHOT.jar') { throw 'Refusing tests against a backend not explicitly started for the test database.' }
$before=Get-LegacyFingerprint
$oldApply=$env:CH_E2E_APPLY
$oldStudents=$env:CH_E2E_STUDENTS
try {
    # Retained, isolated pagination fixtures. Reuse the existing test student's hash
    # inside MySQL; no password or hash is returned to PowerShell or the test report.
    $prefix='m14a_'+[Guid]::NewGuid().ToString('N').Substring(0,12)+'_'
    $students=@(1..11 | ForEach-Object { $prefix+$_ })
    $statements=New-Object 'Collections.Generic.List[string]'
    $statements.Add('START TRANSACTION;')
    foreach($name in $students){
        $statements.Add("INSERT INTO app_user(login_name,display_name,password_hash,enabled,demo_account) SELECT '$name','M1.4-A pagination student',password_hash,1,1 FROM app_user WHERE login_name='demo_student';")
        $statements.Add("INSERT INTO app_user_role(user_id,role_id) SELECT u.id,r.id FROM app_user u CROSS JOIN app_role r WHERE u.login_name='$name' AND r.code='STUDENT';")
    }
    $statements.Add('COMMIT;')
    $null=Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql ($statements -join "`n")
    $created=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql ("SELECT COUNT(*) FROM app_user u JOIN app_user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE u.login_name LIKE '$prefix%' AND r.code='STUDENT';")).Trim()
    if($created -cne '11'){throw 'Pagination fixture inventory mismatch'}
    $env:CH_E2E_STUDENTS=$students | ConvertTo-Json -Compress
    $env:CH_E2E_APPLY='true'
    Push-Location (Join-Path (Get-CampusHubConfig).RepoRoot 'admin-web')
    try { & npm.cmd run test:e2e; $testExit=$LASTEXITCODE } finally { Pop-Location }
    if ($testExit -ne 0) { throw ('Browser verification failed, exit=' + $testExit) }
} finally { $env:CH_E2E_APPLY=$oldApply; $env:CH_E2E_STUDENTS=$oldStudents; Assert-LegacyUnchanged $before }
