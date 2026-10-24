#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
if(-not $Apply){[pscustomobject]@{Mode='PREVIEW';Action='Read-only final M1.4-B MySQL evidence; writes sanitized JSON only'};return}
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
Assert-BackendProbe
$inconsistent=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql "SELECT COUNT(*) FROM activity a WHERE a.registered_count<>(SELECT COUNT(*) FROM activity_registration r WHERE r.activity_id=a.id AND r.status='CONFIRMED');").Trim()
$duplicates=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql 'SELECT COUNT(*) FROM (SELECT activity_id,user_id FROM activity_registration GROUP BY activity_id,user_id HAVING COUNT(*)>1) d;').Trim()
$testCount=(Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql "SELECT COUNT(*) FROM activity WHERE title LIKE 'M14B-%' OR summary LIKE '%M14B-%';").Trim()
$devCount=(Invoke-CampusSql -CredentialName app -Database campushub -Sql 'SELECT (SELECT COUNT(*) FROM activity),(SELECT COUNT(*) FROM activity_registration),(SELECT COUNT(*) FROM app_user);').Trim()
if($inconsistent -cne '0' -or $duplicates -cne '0' -or $devCount -cne "0`t0`t3"){throw 'Database invariant mismatch'}
$fixture=Get-Content -LiteralPath (Join-Path $PSScriptRoot '../docs/verification/m1.4-b-real-api.json') -Raw | ConvertFrom-Json
if($fixture.database -cne 'campushub_test'){throw 'Invalid fixture evidence'}
$ids=@($fixture.activityIds | ForEach-Object { [long]$_ })
if(-not $ids.Count -or @($ids | Where-Object {$_ -le 0}).Count){throw 'Invalid fixture IDs'}
$rows=Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql ('SELECT a.id,a.status,a.capacity,a.registered_count,r.status,r.version FROM activity a LEFT JOIN activity_registration r ON r.activity_id=a.id WHERE a.id IN ('+($ids -join ',')+') ORDER BY a.id,r.id;')
$report=[pscustomobject]@{Status='PASS';Database='campushub_test';Address='127.0.0.1:3307';CountMismatches=0;DuplicateRegistrations=0;RetainedM14BActivities=[int]$testCount;DevActivityCount=0;DevRegistrationCount=0;DevOriginalUsers=3;OriginalProbe='PASS';FixtureState=$rows;DatabaseMutation='NONE';MySQL80='No connection or mutation'}
$report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $PSScriptRoot '../docs/verification/m1.4-b-database.json') -Encoding UTF8
[pscustomobject]@{Status='PASS';CountMismatches=0;DuplicateRegistrations=0;OriginalProbe='PASS';Dev='0 activities / 0 registrations / 3 users'}
