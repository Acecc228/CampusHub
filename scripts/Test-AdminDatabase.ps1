#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
if (-not $Apply) { [pscustomobject]@{Mode='PREVIEW';Action='Read-only database evidence; writes sanitized verification JSON only'}; return }
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
Assert-BackendProbe
$c=Get-CampusHubConfig
$fixture=Get-Content -LiteralPath (Join-Path $c.RepoRoot 'docs\verification\m1.4-a-fixture.json') -Raw | ConvertFrom-Json
$activityId=[long]$fixture.createdId
if($activityId -le 0 -or $fixture.database -cne 'campushub_test'){throw 'Invalid fixture evidence'}
$persisted=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql ('SELECT id,status,version,capacity,registered_count FROM activity WHERE id='+$activityId+';')).Trim()
$registration=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql ('SELECT activity_id,status,version FROM activity_registration WHERE activity_id='+$activityId+';')).Trim()
$inconsistent=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql "SELECT COUNT(*) FROM activity a WHERE a.registered_count<>(SELECT COUNT(*) FROM activity_registration r WHERE r.activity_id=a.id AND r.status='CONFIRMED');").Trim()
$duplicates=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql 'SELECT COUNT(*) FROM (SELECT activity_id,user_id FROM activity_registration GROUP BY activity_id,user_id HAVING COUNT(*)>1) d;').Trim()
$testCount=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql "SELECT COUNT(*) FROM activity WHERE title LIKE 'M14A%';").Trim()
$devCount=(Invoke-CampusSql -CredentialName app -Database campushub -Sql 'SELECT (SELECT COUNT(*) FROM activity),(SELECT COUNT(*) FROM activity_registration),(SELECT COUNT(*) FROM app_user);').Trim()
if(-not $persisted -or -not $registration -or $inconsistent -cne '0' -or $duplicates -cne '0' -or $devCount -cne "0`t0`t3"){throw 'Database evidence mismatch'}
$report=[pscustomobject]@{Status='PASS';Database='campushub_test';Address='127.0.0.1:3307';FixtureId=$activityId;PersistedActivity=$persisted;PersistedRegistration=$registration;CountMismatches=0;DuplicateRegistrations=0;RetainedM14AActivities=[int]$testCount;DevActivityCount=0;DevRegistrationCount=0;DevOriginalUsers=3;OriginalProbe='PASS';DatabaseMutation='NONE';MySQL80='No connection or mutation'}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $c.RepoRoot 'docs\verification\m1.4-a-database.json') -Encoding UTF8
$report
