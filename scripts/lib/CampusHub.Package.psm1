#requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'CampusHub.MySql.psm1')

function Open-PackageReadLock([string]$Path) {
    Assert-NoReparsePoint $Path
    [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
}

function Assert-PackageHashes([string]$Path, [string]$ExpectedName, [string]$Md5, [string]$Sha256) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'PackageMissing' }
    Assert-NoReparsePoint $Path
    if ([IO.Path]::GetFileName($Path) -cne $ExpectedName) { throw 'PackageNameMismatch' }
    if ($Md5 -notmatch '^[a-fA-F0-9]{32}$' -or $Sha256 -notmatch '^[a-fA-F0-9]{64}$') { throw 'PackageDigestNotPinned' }
    if ((Get-FileHash -LiteralPath $Path -Algorithm MD5).Hash -ine $Md5) { throw 'PackageMd5Mismatch' }
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ine $Sha256) { throw 'PackageSha256Mismatch' }
}

function Assert-GpgSignatureStatus([int]$ExitCode, [string]$Status, [string]$Fingerprint) {
    $valid = @($Status -split '\r?\n' | Where-Object { $_ -match '^\[GNUPG:\] VALIDSIG ' })
    if ($ExitCode -ne 0 -or $valid.Count -ne 1 -or $Status -match '\[GNUPG:\] (BADSIG|ERRSIG|NO_PUBKEY|EXPKEYSIG|EXPSIG|REVKEYSIG|KEYEXPIRED|KEYREVOKED|FAILURE)') { throw 'SignatureNotTrusted' }
    $fields = $valid[0] -split ' '
    if ($fields.Count -ne 12 -or $fields[2] -cne $Fingerprint -or $fields[11] -cne $Fingerprint) { throw 'SignatureNotTrusted: unexpected signer' }
}

function ConvertTo-GitGpgPath([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    if ($full -notmatch '^([A-Za-z]):[\\/](.*)$') { throw 'GPG requires an absolute local path.' }
    '/' + $Matches[1].ToLowerInvariant() + '/' + $Matches[2].Replace('\','/')
}

function Get-VerifiedMySqlPackage {
    param([string]$ArchivePath, [string]$GpgHome)
    $c = Get-CampusHubConfig
    if (-not $ArchivePath) { $ArchivePath=Join-Path $c.PackageRoot $c.ArchiveName }
    $ArchivePath=[IO.Path]::GetFullPath($ArchivePath)
    if (-not $GpgHome) { $GpgHome=Join-Path $c.RepoRoot '.local\gpg-verification' }
    Assert-ContainedPath (Join-Path $c.RepoRoot '.local') $GpgHome
    if ($c.ArchiveName -cne "mysql-$($c.Version)-winx64.zip" -or $c.ArchiveDirectory -cne "mysql-$($c.Version)-winx64") { throw 'PackageVersionMismatch' }
    Assert-PackageHashes $ArchivePath $c.ArchiveName $c.ArchiveMd5 $c.ArchiveSha256
    $signature=$ArchivePath+'.asc'
    $key=Join-Path (Split-Path $ArchivePath -Parent) $c.PublicKeyName
    foreach ($file in @($signature,$key,$c.GpgExecutable)) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw 'SignatureVerificationUnavailable: required file or GPG is missing.' }
        Assert-NoReparsePoint $file
    }
    Assert-NoReparsePoint $GpgHome
    if (-not (Test-Path -LiteralPath $GpgHome)) { New-Item -ItemType Directory -Path $GpgHome | Out-Null }
    $locks = @()
    try {
        foreach ($file in @($ArchivePath,$signature,$key)) { $locks += Open-PackageReadLock $file }
        Assert-PackageHashes $ArchivePath $c.ArchiveName $c.ArchiveMd5 $c.ArchiveSha256
        $common=@('--no-options','--homedir',(ConvertTo-GitGpgPath $GpgHome),'--batch','--no-auto-key-retrieve','--no-auto-key-import','--no-autostart')
        # Public GPG output is captured, not echoed. The Windows paths are passed as MSYS paths for Git's GPG.
        $keyInfo=@(& $c.GpgExecutable @common --with-colons --import-options show-only --import (ConvertTo-GitGpgPath $key) 2>$null)
        if ($LASTEXITCODE -ne 0) { throw 'SignatureVerificationUnavailable: key inspection failed.' }
        $pubs=@($keyInfo | Where-Object {$_ -match '^pub:'})
        $fprs=@($keyInfo | Where-Object {$_ -match '^fpr:'} | ForEach-Object {($_ -split ':')[9]})
        if ($pubs.Count -ne 1 -or $fprs.Count -lt 1 -or $fprs[0] -cne $c.SigningFingerprint) { throw 'SignatureNotTrusted: public key differs from official documentation.' }
        $null=& $c.GpgExecutable @common --import (ConvertTo-GitGpgPath $key) 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'SignatureVerificationUnavailable: public key import failed.' }
        $status=(& $c.GpgExecutable @common --status-fd 1 --verify (ConvertTo-GitGpgPath $signature) (ConvertTo-GitGpgPath $ArchivePath) 2>$null) -join "`n"
        $exitCode=$LASTEXITCODE
        Assert-GpgSignatureStatus $exitCode $status $c.SigningFingerprint
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip=[IO.Compression.ZipFile]::OpenRead($ArchivePath)
        try {
            if (-not $zip.GetEntry($c.ArchiveDirectory+'/bin/mysqld.exe')) { throw 'PackageVersionMismatch: expected server entry missing.' }
            foreach($entry in $zip.Entries) {
                $name=$entry.FullName.Replace('\','/')
                if (-not $name.StartsWith($c.ArchiveDirectory+'/',[StringComparison]::Ordinal) -or $name -match '(^|/)\.\.(/|$)|:' -or (($entry.ExternalAttributes -shr 16) -band 0xf000) -eq 0xa000) { throw 'UnsafePackageEntry' }
            }
        } finally { $zip.Dispose() }
        [pscustomobject]@{
            Status='PASS'; VerifiedAtUtc=[DateTime]::UtcNow.ToString('o'); Version=$c.Version
            ArchivePath=$ArchivePath; Bytes=(Get-Item -LiteralPath $ArchivePath).Length
            Md5=$c.ArchiveMd5; Sha256=$c.ArchiveSha256; Sha256Basis='Locally measured after pinned official GPG verification'
            Signature='VALIDSIG'; SignerFingerprint=$c.SigningFingerprint; GpgExitCode=$exitCode
            ValidSigStatus=($status -split '\r?\n' | Where-Object { $_ -match '^\[GNUPG:\] VALIDSIG ' })
            GpgExecutable=$c.GpgExecutable; TrustBasis='Full fingerprint independently checked against official MySQL manual'
            SignatureSha256=(Get-FileHash -LiteralPath $signature -Algorithm SHA256).Hash
            KeySha256=(Get-FileHash -LiteralPath $key -Algorithm SHA256).Hash
            DownloadPage=$c.DownloadPage; DownloadUrl=$c.DownloadUrl; SignatureUrl=$c.SignatureUrl; PublicKeyUrl=$c.PublicKeyUrl; FingerprintSource=$c.FingerprintSource
        }
    } finally { foreach($lock in $locks){$lock.Dispose()} }
}

Export-ModuleMember -Function Open-PackageReadLock,Assert-PackageHashes,Assert-GpgSignatureStatus,Get-VerifiedMySqlPackage
