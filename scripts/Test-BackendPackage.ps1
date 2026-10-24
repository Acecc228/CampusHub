#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
if (-not $Apply) { [pscustomobject]@{Mode='PREVIEW';Action='Run packaged JAR in dev/prod; HTTP checks; stop only owned child processes'}; return }
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
Add-Type -AssemblyName System.Net.Http
$c = Get-CampusHubConfig
$before = Get-LegacyFingerprint
Assert-CampusService
Confirm-CampusDatabase -CredentialName app -Database campushub
$credential = Get-BackendCredential app
$results = New-Object 'Collections.Generic.List[object]'
$client = New-Object Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(5)
$jar = Join-Path $c.RepoRoot 'backend\target\campushub-backend-0.1.0-SNAPSHOT.jar'
function Get-HttpResult([string]$Url) {
    $response = $client.GetAsync($Url).GetAwaiter().GetResult()
    try { return @{Status=[int]$response.StatusCode;Body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()} }
    finally { $response.Dispose() }
}
function Post-HttpResult([string]$Url,[string]$Token,[hashtable]$Form) {
    $fields=New-Object 'Collections.Generic.Dictionary[string,string]'
    foreach ($key in $Form.Keys) { $fields.Add($key,[string]$Form[$key]) }
    $request=New-Object Net.Http.HttpRequestMessage([Net.Http.HttpMethod]::Post,$Url)
    $request.Content=New-Object Net.Http.FormUrlEncodedContent($fields)
    $request.Headers.Add('X-CSRF-TOKEN',$Token)
    try {
        $response=$client.SendAsync($request).GetAwaiter().GetResult()
        try { return @{Status=[int]$response.StatusCode;Body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()} }
        finally { $response.Dispose() }
    } finally { $request.Dispose() }
}
try {
    foreach ($profile in @('dev','prod')) {
        $demoEnabled=($profile -eq 'dev').ToString().ToLowerInvariant()
        $process = New-BackendProcess -FilePath 'D:\develop\jdk\bin\java.exe' -Arguments @('-jar',$jar,('--spring.profiles.active='+$profile),'--server.port=0',('--campushub.auth.demo-enabled='+$demoEnabled)) -Environment @{CH_DB_PASSWORD=$credential.Password}
        if ($process.StartInfo.EnvironmentVariables.ContainsKey('FLYWAY_PASSWORD') -or $process.StartInfo.EnvironmentVariables.ContainsKey('SPRINGDOC_APIDOCS_ENABLED') -or $process.StartInfo.EnvironmentVariables.ContainsKey('SPRINGDOC_SWAGGERUI_ENABLED')) { throw 'Unapproved inherited environment reached packaged application.' }
        $null = $process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        try {
            $deadline = [DateTime]::UtcNow.AddSeconds(60)
            $baseUrl = $null
            do {
                if ($process.HasExited) { throw 'Packaged application exited before ready.' }
                $listener = @(Get-NetTCPConnection -State Listen -OwningProcess $process.Id -ErrorAction SilentlyContinue | Where-Object LocalAddress -eq '127.0.0.1')
                if ($listener.Count -eq 1) {
                    $candidate = 'http://127.0.0.1:' + $listener[0].LocalPort
                    try { if ((Get-HttpResult ($candidate+'/actuator/health')).Status -eq 200) { $baseUrl=$candidate; break } } catch { }
                }
                Start-Sleep -Milliseconds 300
            } while ([DateTime]::UtcNow -lt $deadline)
            if (-not $baseUrl) { throw 'Packaged application readiness timed out.' }
            $categories = Get-HttpResult ($baseUrl+'/api/v1/activity-categories')
            $items = @($categories.Body | ConvertFrom-Json)
            if ($categories.Status -ne 200 -or $items.Count -ne 4 -or $items[0].code -cne 'ACADEMIC') { throw 'Packaged category response mismatch.' }
            $protected = Get-HttpResult ($baseUrl+'/actuator/info')
            if ($protected.Status -ne 401 -or ($protected.Body | ConvertFrom-Json).code -cne 'UNAUTHORIZED') { throw 'Protected endpoint must return JSON 401.' }
            foreach ($path in @('/api/v1/activities','/api/v1/manage/activities')) {
                if ((Get-HttpResult ($baseUrl+$path)).Status -ne 401) { throw 'Anonymous activity access must return 401.' }
            }
            $docs = Get-HttpResult ($baseUrl+'/v3/api-docs')
            if ($profile -eq 'dev') {
                if ($docs.Status -ne 200 -or -not ($docs.Body | ConvertFrom-Json).paths.'/api/v1/activity-categories') { throw 'Development OpenAPI mismatch.' }
                if (-not ($docs.Body | ConvertFrom-Json).paths.'/api/v1/manage/activities/{id}/publish') { throw 'Activity OpenAPI mapping missing.' }
                if ((Get-HttpResult ($baseUrl+'/swagger-ui/index.html')).Status -ne 200) { throw 'Swagger UI unavailable.' }
                Assert-PrivateCredentialDirectory
                $demoPath=Join-Path $c.Secrets 'campushub-demo.json'
                Assert-NoReparsePoint $demoPath
                $demo=[IO.File]::ReadAllText($demoPath) | ConvertFrom-Json
                foreach ($role in @('student','organizer','admin')) {
                    $entry=@($demo.Accounts | Where-Object LoginName -CEQ ('demo_'+$role))
                    if ($entry.Count -ne 1) { throw 'Missing local demo identity.' }
                    $csrf=(Get-HttpResult ($baseUrl+'/api/v1/auth/csrf')).Body | ConvertFrom-Json
                    $login=Post-HttpResult ($baseUrl+'/api/v1/auth/demo-login') $csrf.token @{username=$entry[0].LoginName;password=$entry[0].Password}
                    if ($login.Status -ne 200) { throw 'Packaged demo login rejected.' }
                    $me=Get-HttpResult ($baseUrl+'/api/v1/auth/me')
                    if ($me.Status -ne 200 -or ($me.Body | ConvertFrom-Json).loginName -cne ('demo_'+$role)) { throw 'Packaged identity mismatch.' }
                    foreach ($target in @('student','organizer','admin')) {
                        $expected=if ($target -eq $role) { 200 } else { 403 }
                        if ((Get-HttpResult ($baseUrl+'/api/v1/'+$target+'/access')).Status -ne $expected) { throw 'Packaged RBAC mismatch.' }
                    }
                    $activities=Get-HttpResult ($baseUrl+'/api/v1/activities?page=1&size=1')
                    if ($activities.Status -ne 200 -or ($activities.Body | ConvertFrom-Json).size -ne 1) { throw 'Packaged activity list mismatch.' }
                    $management=Get-HttpResult ($baseUrl+'/api/v1/manage/activities')
                    $expected=if ($role -eq 'student') { 403 } else { 200 }
                    if ($management.Status -ne $expected) { throw 'Packaged activity management RBAC mismatch.' }
                    $registrations=Get-HttpResult ($baseUrl+'/api/v1/student/registrations?size=1')
                    $expected=if ($role -eq 'student') { 200 } else { 403 }
                    if ($registrations.Status -ne $expected) { throw 'Packaged registration RBAC mismatch.' }
                    $csrf=(Get-HttpResult ($baseUrl+'/api/v1/auth/csrf')).Body | ConvertFrom-Json
                    if ((Post-HttpResult ($baseUrl+'/api/v1/auth/logout') $csrf.token @{}).Status -ne 204) { throw 'Packaged logout failed.' }
                    if ((Get-HttpResult ($baseUrl+'/api/v1/auth/me')).Status -ne 401) { throw 'Packaged session remained authenticated.' }
                }
            } elseif ($docs.Status -ne 401) { throw 'Production documentation must not be public.' }
            $authResult=if ($profile -eq 'dev') { 'PASS: 3 logins, 3 identities, 9 role checks, 3 logouts and anonymous 401 checks' } else { 'DISABLED: demo authentication; authenticated mapping absence covered in integration tests' }
            $results.Add([pscustomobject]@{Profile=$profile;Status='PASS';JarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash;Health=200;Categories=$items;Protected=401;OpenApi=$docs.Status;Authentication=$authResult;ActivityChecks='Anonymous 401; dev authenticated lists and management role matrix';Credential='campushub_app only';FlywayEnvironmentPresent=$process.StartInfo.EnvironmentVariables.ContainsKey('FLYWAY_PASSWORD')})
        } finally {
            # Only the exact Java process started above is stopped; no Windows service changes.
            if (-not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
            $log = ($stdout.Result + $stderr.Result).Replace($credential.Password,'[REDACTED]')
            $path = Join-Path $c.RepoRoot ('.local\backend-logs\package-'+$profile+'-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')+'.log')
            [IO.File]::WriteAllText($path,$log,(New-Object Text.UTF8Encoding($false)))
            $process.Dispose()
        }
    }
    Assert-LegacyUnchanged $before
    $report = [pscustomobject]@{Status='PASS';CheckedAtUtc=[DateTime]::UtcNow.ToString('o');Results=$results.ToArray();LegacyUnchanged=$true;ProductionMappingAbsence='Verified separately by authenticated integration tests'}
$report | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $c.RepoRoot 'docs\verification\m1.3-package.json') -Encoding UTF8
    $report | Select-Object Status,CheckedAtUtc,LegacyUnchanged
} finally { $client.Dispose(); Assert-LegacyUnchanged $before }
