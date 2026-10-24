#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply, [ValidatePattern('^[a-f0-9]{32}$')][string]$RunId)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repo=Split-Path $PSScriptRoot -Parent
$locks=@()
$stage='Policy'
$reportDirectory=$null
$exitCode=0
$result=[ordered]@{Status='RUNNING';Mode=$(if($Apply){'APPLY'}else{'PREVIEW'});Stage=$stage;SourceFilesVerified=0;PackageSignature=$null;InstallerInvoked=$false;InstallerResult=$null;NativeExitCode=$null;NativeStage=$null;ErrorType=$null;TimeUtc=[DateTime]::UtcNow.ToString('o')}
function Write-RunRecord([string]$Name, $Value) {
    Assert-NoReparsePoint $reportDirectory
    $target=Join-Path $reportDirectory $Name
    $stream=[IO.File]::Open($target,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
    try {
        $bytes=[Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 5))
        $stream.Write($bytes,0,$bytes.Length)
    } finally { $stream.Dispose() }
}
try {
    if($repo -ine 'E:\Project\CampusHub' -or $PSVersionTable.PSVersion.Major -ne 7){throw 'WorkspaceOrPowerShellMismatch'}
    foreach($scope in @('MachinePolicy','UserPolicy')) {
        if((Get-ExecutionPolicy -Scope $scope) -notin @('Undefined','RemoteSigned')){throw 'MandatoryPolicyConflict'}
    }
    if((Get-ExecutionPolicy) -ne 'RemoteSigned'){throw 'ProcessMustUseRemoteSigned'}
    $stage='TrustedShell'
    $pwshPath=(Get-Process -Id $PID).Path
    $signature=Get-AuthenticodeSignature -LiteralPath $pwshPath
    if($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'CN=Microsoft Corporation'){throw 'PowerShellSignatureInvalid'}
    if($Apply) {
        $stage='Administrator'
        $principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
        if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator) -or -not $RunId){throw 'AdministratorAndRunIdRequired'}
    }
    $stage='FrozenSources'
    $recordPath=Join-Path $repo 'docs\verification\install-preflight.json'
    $locks += [IO.File]::Open($recordPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
    if((Get-FileHash -LiteralPath $recordPath -Algorithm SHA256).Hash -cne 'F7067F8C32F2902A54D86441264C113813E1C50EBC811497474A3BA919FC88FC'){throw 'FrozenRecordMismatch'}
    $record=Get-Content -LiteralPath $recordPath -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach($entry in $record.SourceFiles) {
        $path=Join-Path $repo $entry.RelativePath
        $locks += [IO.File]::Open($path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
        if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -cne $entry.Sha256){throw 'FrozenSourceMismatch'}
        if(@(Get-Item -LiteralPath $path -Stream *).Stream -contains 'Zone.Identifier'){throw 'SourceZoneMarkerRequiresReview'}
        $result.SourceFilesVerified++
    }
    Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
    Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Package.psm1') -Force
    foreach($entry in $record.SourceFiles){Assert-NoReparsePoint (Join-Path $repo $entry.RelativePath)}
    if($Apply) {
        $stage='RunRecord'
        $candidate=Join-Path $repo ('.local\install-run\'+$RunId)
        Assert-NoReparsePoint $candidate
        if(-not (Test-Path -LiteralPath $candidate -PathType Container)){throw 'PreparedRunDirectoryMissing'}
        if(@(Get-ChildItem -LiteralPath $candidate -Force).Count -ne 0){throw 'RunAlreadyUsed'}
        $reportDirectory=$candidate
        Write-RunRecord 'started.json' @{TimeUtc=[DateTime]::UtcNow.ToString('o');Status='STARTED';Administrator=$true;EffectivePolicy=(Get-ExecutionPolicy).ToString();RunId=$RunId}
    }
    $stage='Environment'
    $before=@{}
    foreach($property in $record.Environment.LegacyFingerprint.PSObject.Properties){$before[$property.Name]=$property.Value}
    Assert-LegacyUnchanged $before
    if((Get-Service -Name MySQL80).Status -ne 'Running'){throw 'LegacyServiceNotRunning'}
    foreach($port in @(3306,33060)) {
        if(-not (Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq $port)){throw 'LegacyListenerMissing'}
    }
    Assert-FreshInstall
    $stage='Package'
    $package=Get-VerifiedMySqlPackage -ArchivePath 'E:\Project\CampusHub.local\packages\mysql-8.4.11-winx64.zip'
    $result.PackageSignature=$package.Signature
    if($Apply) {
        $stage='Installer'
        $result.InstallerInvoked=$true
        $result.InstallerResult=& 'E:\Project\CampusHub\scripts\Install-MySql84.ps1' -ArchivePath 'E:\Project\CampusHub.local\packages\mysql-8.4.11-winx64.zip' -Apply
        if($result.InstallerResult.Status -cne 'PASS'){throw 'InstallerDidNotPass'}
    }
    $result.Status='PASS'
    $stage='Complete'
} catch {
    $exitCode=1
    $result.Status='FAIL'
    $result.ErrorType=$_.Exception.GetBaseException().GetType().Name
    # Native installer exceptions already classify errors; retain only these fixed fields.
    if($_.Exception.Message -match 'exit=(-?\d+)'){$result.NativeExitCode=[int]$Matches[1]}
    if($_.Exception.Message -match 'stage=(Version|Initialize|RegisterService|DatabaseSql|ValidationProbe);'){$result.NativeStage=$Matches[1]}
} finally {
    $result.Stage=$stage
    try {
        if($reportDirectory){Write-RunRecord 'result.json' $result}
    } catch {
        $exitCode=1
        $result.Status='FAIL'
        $result.ErrorType='RunRecordWriteFailed'
    } finally { foreach($handle in $locks){$handle.Dispose()} }
}
[pscustomobject]$result
if($exitCode){throw "ReviewedLauncherFailed; stage=$stage; nativeExit=$($result.NativeExitCode); nativeStage=$($result.NativeStage)"}
