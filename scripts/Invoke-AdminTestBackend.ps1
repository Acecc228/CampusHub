#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference = 'Stop'
if (-not $Apply) { [pscustomobject]@{Mode='PREVIEW';Database='campushub_test';User='campushub_test_app';Address='127.0.0.1:3307';Http='127.0.0.1:8080';Action='Run existing JAR with test database and demo login; no migration or service changes'}; return }
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
$c = Get-CampusHubConfig
Assert-CampusService
Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
if (@(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue).Count -gt 0) { throw 'Port 8080 is already occupied; no process was stopped.' }
$credential = Get-BackendCredential test-app
$before = Get-LegacyFingerprint
$jar = Join-Path $c.RepoRoot 'backend\target\campushub-backend-0.1.0-SNAPSHOT.jar'
$process = New-BackendProcess -FilePath 'D:\develop\jdk\bin\java.exe' -Arguments @('-jar',$jar,'--spring.profiles.active=dev','--server.port=8080','--campushub.auth.demo-enabled=true','--campushub.database-purpose=test') -Environment @{
    CH_DB_PASSWORD=$credential.Password
    SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3307/campushub_test?sslMode=REQUIRED&characterEncoding=UTF-8&connectionTimeZone=UTC'
    SPRING_DATASOURCE_USERNAME='campushub_test_app'
}
$null = $process.Start()
$stdout = $process.StandardOutput.ReadToEndAsync()
$stderr = $process.StandardError.ReadToEndAsync()
try {
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline -and -not $process.HasExited) {
        try { $health=Invoke-RestMethod -Uri 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 2; if ($health.status -eq 'UP') { $ready=$true; break } } catch { }
        Start-Sleep -Milliseconds 300
    }
    if (-not $ready) { throw 'Test backend readiness failed. Raw application output suppressed.' }
    [pscustomobject]@{Status='READY';Pid=$process.Id;Database='campushub_test';Address='127.0.0.1:3307';Http='http://127.0.0.1:8080';Credential='test-app only'}
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'Test backend stopped with nonzero exit code.' }
} finally {
    if (-not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
    $process.Dispose()
    Assert-LegacyUnchanged $before
}
