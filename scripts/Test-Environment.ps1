#requires -Version 5.1
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
$c = Get-CampusHubConfig
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
$service = @(Get-CimInstance Win32_Service | Where-Object { $_.Name -in @('MySQL80','MySQL84CampusHub') } | Select-Object Name,State,StartMode,StartName,PathName)
$listeners = @(Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in @(3306,3307,33060,33070) } | Select-Object LocalAddress,LocalPort,OwningProcess)
[PSCustomObject]@{
    CheckedAt = (Get-Date).ToUniversalTime().ToString('o')
    IsAdministrator = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    Services = $service
    Listeners = $listeners
    RuntimeExists = Test-Path -LiteralPath $c.RuntimeRoot
    LegacyFingerprint = Get-LegacyFingerprint
    Java21ExecutableExists = Test-Path -LiteralPath 'D:\develop\jdk\bin\java.exe'
    MavenOnPath = [bool](Get-Command mvn -ErrorAction SilentlyContinue)
    DatabaseSqlChecks = 'NOT_RUN (this script never authenticates or writes to MySQL)'
} | ConvertTo-Json -Depth 6
