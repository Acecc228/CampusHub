#requires -Version 5.1
[CmdletBinding()]
param([string]$ArchivePath, [switch]$Apply)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Package.psm1') -Force
$c = Get-CampusHubConfig
if (-not $ArchivePath) { $ArchivePath = Join-Path $c.PackageRoot $c.ArchiveName }
$ArchivePath = [IO.Path]::GetFullPath($ArchivePath)
$template = Join-Path $c.RepoRoot 'infra\mysql\my.ini.template'
$validatedTemplate = [IO.File]::ReadAllText($template)
Assert-MySqlConfiguration $validatedTemplate
$diagnostics = Join-Path $c.RuntimeRoot 'diagnostics'
$owner = (Get-Acl -LiteralPath $c.RepoRoot).Owner
$developerSid = (New-Object Security.Principal.NTAccount($owner)).Translate([Security.Principal.SecurityIdentifier]).Value
$commands = @(
    'Verify pinned official GPG fingerprint/signature, official MD5 and recorded SHA-256; hold ZIP read lock until installation exits.',
    "Create protected new directories; developer SID=$developerSid; Administrators/SYSTEM=FullControl; LocalService=ReadAndExecute, and Modify only on data/logs/tmp; no LocalService access to secrets/diagnostics.",
    "Expand verified archive '$ArchivePath' into new '$($c.RuntimeRoot)\package'.",
    "Move only '$($c.RuntimeRoot)\package\$($c.ArchiveDirectory)' to new '$($c.Server)'.",
    "Write validated '$template' to '$($c.ConfigFile)'; save package verification and read-only legacy baseline.",
    "& '$($c.ServerExe)' --no-defaults --version",
    "& '$($c.ServerExe)' '--defaults-file=$($c.ConfigFile)' --initialize",
    "& '$($c.ServerExe)' --install-manual MySQL84CampusHub '--defaults-file=$($c.ConfigFile)' --local-service",
    'Start-Service -Name MySQL84CampusHub'
)
if (-not $Apply) {
    [PSCustomObject]@{ Mode='PREVIEW'; Version=$c.Version; Archive=$ArchivePath; ExpectedMd5=$c.ArchiveMd5; ExpectedSha256=$c.ArchiveSha256; SignatureRequired=$true; SigningFingerprint=$c.SigningFingerprint; Runtime=$c.RuntimeRoot; TemplateSha256=(Get-FileHash -LiteralPath $template -Algorithm SHA256).Hash; Actions=$commands }
    return
}

Assert-Administrator
Assert-FreshInstall

function New-IsolatedDirectory([string]$Path, [string]$ServiceRights) {
    # Each target is an explicit NEW directory, never an existing directory tree.
    if (Test-Path -LiteralPath $Path) { throw "Directory already exists: $Path" }
    $allowed = @((Split-Path $c.RuntimeRoot -Parent), $c.RuntimeRoot)
    if ($Path -notin $allowed) { Assert-ContainedPath $c.RuntimeRoot $Path }
    Assert-NoReparsePoint $Path
    New-Item -ItemType Directory -Path $Path | Out-Null
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($sid in @($developerSid, 'S-1-5-32-544', 'S-1-5-18') | Select-Object -Unique) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule((New-Object Security.Principal.SecurityIdentifier($sid)), 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    if ($ServiceRights) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule((New-Object Security.Principal.SecurityIdentifier('S-1-5-19')), $ServiceRights, 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $Path -AclObject $acl
    $allowedSids = @($developerSid, 'S-1-5-32-544', 'S-1-5-18')
    if ($ServiceRights) { $allowedSids += 'S-1-5-19' }
    Assert-PrivateAcl (Get-Acl -LiteralPath $Path) $allowedSids
}

$packageLock = Open-PackageReadLock $ArchivePath
try {
    $packageEvidence = Get-VerifiedMySqlPackage -ArchivePath $ArchivePath
    $before = Get-LegacyFingerprint
    # No runtime mutation occurs until the actual package has passed all checks.
    $parent = Split-Path $c.RuntimeRoot -Parent
    if (-not (Test-Path -LiteralPath $parent)) { New-IsolatedDirectory $parent 'ReadAndExecute' }
    New-IsolatedDirectory $c.RuntimeRoot 'ReadAndExecute'
    foreach ($name in @('config','package')) { New-IsolatedDirectory (Join-Path $c.RuntimeRoot $name) 'ReadAndExecute' }
    foreach ($name in @('data','logs','tmp')) { New-IsolatedDirectory (Join-Path $c.RuntimeRoot $name) 'Modify' }
    New-IsolatedDirectory $c.Secrets ''
    New-IsolatedDirectory $diagnostics ''
    [IO.File]::WriteAllText((Join-Path $c.RuntimeRoot 'legacy-before.json'), ($before | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
    [IO.File]::WriteAllText((Join-Path $c.RuntimeRoot 'package-verification.json'), ($packageEvidence | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath (Join-Path $c.RuntimeRoot 'package')
    $source = [IO.Path]::GetFullPath((Join-Path $c.RuntimeRoot ('package\' + $c.ArchiveDirectory)))
    $destination = [IO.Path]::GetFullPath($c.Server)
    Assert-ContainedPath $c.RuntimeRoot $source
    Assert-ContainedPath $c.RuntimeRoot $destination
    Assert-NoReparsePoint $source
    if (Test-Path -LiteralPath $destination) { throw 'Server destination already exists.' }
    Move-Item -LiteralPath $source -Destination $destination
    [IO.File]::WriteAllText($c.ConfigFile, $validatedTemplate, (New-Object Text.UTF8Encoding($false)))
    $version = Invoke-PrivateProcess -FilePath $c.ServerExe -Arguments @('--no-defaults','--version') -Stage Version -CommandCategory mysql-server -DiagnosticDirectory $diagnostics
    if ($version -notmatch ('\b' + [regex]::Escape($c.Version) + '\b')) { throw 'Extracted server version does not match the reviewed version.' }
    if (@(Get-ChildItem -LiteralPath $c.Data -Force).Count -ne 0) { throw 'Data directory is not empty. Initialization refused.' }
    $null = Invoke-PrivateProcess -FilePath $c.ServerExe -Arguments @("--defaults-file=$($c.ConfigFile)",'--initialize') -Stage Initialize -CommandCategory mysql-server -DiagnosticDirectory $diagnostics
    # Fail on a conflict that appeared after the initial preflight; never replace it.
    if (Get-CimInstance Win32_Service -Filter "Name='MySQL84CampusHub'") { throw 'Service conflict appeared during preparation.' }
    if (Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -eq 3307 }) { throw 'Port conflict appeared during preparation.' }
    $null = Invoke-PrivateProcess -FilePath $c.ServerExe -Arguments @('--install-manual','MySQL84CampusHub',"--defaults-file=$($c.ConfigFile)",'--local-service') -Stage RegisterService -CommandCategory mysql-server -DiagnosticDirectory $diagnostics
    Start-Service -Name 'MySQL84CampusHub'
    (Get-Service -Name 'MySQL84CampusHub').WaitForStatus('Running', [TimeSpan]::FromSeconds(30))
    Assert-CampusService
    Assert-LegacyUnchanged $before
    [PSCustomObject]@{ Status='PASS'; Stage='Service installation only'; DatabaseBootstrap='NOT_RUN'; Runtime=$c.RuntimeRoot; LegacySql='NOT_RUN' }
} finally { $packageLock.Dispose() }
