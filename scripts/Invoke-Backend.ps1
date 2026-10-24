#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('Verify','Package','Run','Dependencies')][string]$Action='Verify',
    [ValidateSet('dev','prod')][string]$Profile='dev',
    [ValidateRange(1024,65535)][int]$Port=8080,
    [switch]$EnableDemoLogin,
    [switch]$Apply
)
$ErrorActionPreference = 'Stop'
if ($EnableDemoLogin -and ($Profile -ne 'dev' -or $Action -ne 'Run')) { throw 'Demo login switch requires Run with dev profile.' }
if (-not $Apply) {
    [pscustomobject]@{Mode='PREVIEW';Action=$Action;Profile=$Profile;Jdk='D:\develop\jdk';TestDatabase='campushub_test';RunDatabase='campushub';Port=$Port}
    return
}
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
if ($Action -eq 'Dependencies') { Invoke-BackendMaven -Goals @('dependency:tree','-DoutputFile=../docs/verification/m1.0-c-dependencies.txt') -Label dependencies; exit 0 }
Assert-CampusService
$before = Get-LegacyFingerprint
try {
    if ($Action -in @('Verify','Package')) {
        Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
        $credential = Get-BackendCredential test-app
        # Both commands run the integration suite; Package does not skip verification.
        Assert-PrivateCredentialDirectory
        $demoPath=Join-Path (Get-CampusHubConfig).Secrets 'campushub_test-demo.json'
        Assert-NoReparsePoint $demoPath
        $demo=[IO.File]::ReadAllText($demoPath) | ConvertFrom-Json
        if ($demo.Database -cne 'campushub_test' -or @($demo.Accounts).Count -ne 3) { throw 'Invalid test demo credential file.' }
        $testEnvironment=@{CH_TEST_DB_PASSWORD=$credential.Password}
        foreach ($role in @('student','organizer','admin')) {
            $entry=@($demo.Accounts | Where-Object LoginName -CEQ ('demo_'+$role))
            if ($entry.Count -ne 1 -or $entry[0].Password -cnotmatch '^[a-f0-9]{64}$') { throw 'Invalid test demo credential entry.' }
            $testEnvironment['CH_DEMO_'+$role.ToUpperInvariant()+'_PASSWORD']=$entry[0].Password
        }
        Invoke-BackendMaven -Goals @('verify') -Environment $testEnvironment -Label verify
    } else {
        Confirm-CampusDatabase -CredentialName app -Database campushub
        $credential = Get-BackendCredential app
        $jar = Join-Path (Get-CampusHubConfig).RepoRoot 'backend\target\campushub-backend-0.1.0-SNAPSHOT.jar'
        $process = New-BackendProcess -FilePath 'D:\develop\jdk\bin\java.exe' -Arguments @('-jar',$jar,('--spring.profiles.active='+$Profile),('--server.port='+$Port),('--campushub.auth.demo-enabled='+([bool]$EnableDemoLogin).ToString().ToLowerInvariant())) -Environment @{CH_DB_PASSWORD=$credential.Password}
        $process.StartInfo.RedirectStandardOutput = $false
        $process.StartInfo.RedirectStandardError = $false
        $null = $process.Start()
        try { $process.WaitForExit(); if ($process.ExitCode -ne 0) { throw 'Backend process failed.' } } finally { $process.Dispose() }
    }
} finally { Assert-LegacyUnchanged $before }
