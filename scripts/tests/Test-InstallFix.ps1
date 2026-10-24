#requires -Version 5.1
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Import-Module (Join-Path $repo 'scripts\lib\CampusHub.MySql.psm1') -Force
$packageModule = Join-Path $repo 'scripts\lib\CampusHub.Package.psm1'
if (Test-Path -LiteralPath $packageModule) { Import-Module $packageModule -Force }
$testRoot = Join-Path $repo ('.local\tests\install-fix-' + [guid]::NewGuid().ToString('N'))
Assert-NoReparsePoint $testRoot
New-Item -ItemType Directory -Path $testRoot | Out-Null
$script:results = New-Object 'Collections.Generic.List[object]'
function Check([string]$Name, [scriptblock]$Action) {
    try { & $Action | Out-Null; $script:results.Add([pscustomobject]@{Name=$Name;Status='PASS'}) }
    catch { $script:results.Add([pscustomobject]@{Name=$Name;Status='FAIL'}); Write-Host "FAIL: $Name; line=$($_.InvocationInfo.ScriptLineNumber); type=$($_.Exception.GetType().Name)" }
}
function Reject([scriptblock]$Action, [string]$Expected) {
    $caught = $null
    try { & $Action | Out-Null } catch { $caught = $_.Exception }
    if (-not $caught -or $caught.Message -notmatch [regex]::Escape($Expected)) { throw 'Expected rejection was not observed.' }
}
function Equal($Actual, $Expected) { if ($Actual -cne $Expected) { throw 'Assertion failed.' } }
$fixture = Join-Path $testRoot 'mysql-8.4.11-winx64.zip'
[IO.File]::WriteAllText($fixture, 'fixture-v1')
$md5 = (Get-FileHash $fixture -Algorithm MD5).Hash
$sha = (Get-FileHash $fixture -Algorithm SHA256).Hash
Check 'missing archive' { Reject { Assert-PackageHashes (Join-Path $testRoot 'missing.zip') 'missing.zip' $md5 $sha } 'PackageMissing' }
Check 'wrong archive filename' { Reject { Assert-PackageHashes $fixture 'wrong.zip' $md5 $sha } 'PackageNameMismatch' }
Check 'MD5 mismatch' { Reject { Assert-PackageHashes $fixture 'mysql-8.4.11-winx64.zip' ('0'*32) $sha } 'PackageMd5Mismatch' }
Check 'SHA256 mismatch' { Reject { Assert-PackageHashes $fixture 'mysql-8.4.11-winx64.zip' $md5 ('0'*64) } 'PackageSha256Mismatch' }
Check 'unchanged package digest' { Assert-PackageHashes $fixture 'mysql-8.4.11-winx64.zip' $md5 $sha }
Check 'package changed after verification' {
    Assert-PackageHashes $fixture 'mysql-8.4.11-winx64.zip' $md5 $sha
    [IO.File]::WriteAllText($fixture, 'fixture-v2')
    Reject { Assert-PackageHashes $fixture 'mysql-8.4.11-winx64.zip' $md5 $sha } 'PackageMd5Mismatch'
}
Check 'read lock prevents replacement or writes' {
    $lock = Open-PackageReadLock $fixture
    try {
        $blocked=$false
        try { [IO.File]::WriteAllText($fixture, 'blocked-write') } catch { $blocked=$_.Exception.GetBaseException() -is [IO.IOException] }
        Equal $blocked $true
    } finally { $lock.Dispose() }
}
$fingerprint='BCA43417C3B485DD128EC6D4B7B3B788A8D3785C'
$valid="[GNUPG:] VALIDSIG $fingerprint 2026-07-01 1782929968 0 4 0 1 8 00 $fingerprint"
Check 'valid pinned GPG status' { Assert-GpgSignatureStatus 0 $valid $fingerprint }
Check 'invalid signature status' { Reject { Assert-GpgSignatureStatus 1 '[GNUPG:] BADSIG untrusted' $fingerprint } 'SignatureNotTrusted' }
Check 'signature verification unavailable' { Reject { Assert-GpgSignatureStatus 0 '' $fingerprint } 'SignatureNotTrusted' }
Check 'wrong signer' { Reject { Assert-GpgSignatureStatus 0 ($valid.Replace($fingerprint,('A'*40))) $fingerprint } 'SignatureNotTrusted' }
Check 'expired key is rejected' { Reject { Assert-GpgSignatureStatus 0 ($valid+"`n[GNUPG:] EXPKEYSIG bad") $fingerprint } 'SignatureNotTrusted' }
Check 'real GPG rejects data that does not match official signature' {
    $c=Get-CampusHubConfig
    # Git GPG checks a Unix socket path even with --no-autostart; keep it short.
    $gpgHome=Join-Path $testRoot 'g'
    New-Item -ItemType Directory -Path $gpgHome | Out-Null
    $result=& (Get-Module CampusHub.Package) {
        param($c,$gpgHome,$fixture)
        $common=@('--no-options','--homedir',(ConvertTo-GitGpgPath $gpgHome),'--batch','--no-auto-key-retrieve','--no-auto-key-import','--no-autostart')
        $null=& $c.GpgExecutable @common --import (ConvertTo-GitGpgPath (Join-Path $c.PackageRoot $c.PublicKeyName)) 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'Test key import failed.' }
        $status=(& $c.GpgExecutable @common --status-fd 1 --verify (ConvertTo-GitGpgPath (Join-Path $c.PackageRoot ($c.ArchiveName+'.asc'))) (ConvertTo-GitGpgPath $fixture) 2>$null) -join "`n"
        [pscustomobject]@{Code=$LASTEXITCODE;Status=$status}
    } $c $gpgHome $fixture
    if ($result.Code -eq 0 -or $result.Status -notmatch '\[GNUPG:\] BADSIG ') { throw 'Real GPG did not reject altered data.' }
    Reject { Assert-GpgSignatureStatus $result.Code $result.Status $fingerprint } 'SignatureNotTrusted'
}
Check 'real GPG cannot trust a signature without the pinned public key' {
    $c=Get-CampusHubConfig
    $gpgHome=Join-Path $testRoot 'n'
    New-Item -ItemType Directory -Path $gpgHome | Out-Null
    $result=& (Get-Module CampusHub.Package) {
        param($c,$gpgHome,$fixture)
        $common=@('--no-options','--homedir',(ConvertTo-GitGpgPath $gpgHome),'--batch','--no-auto-key-retrieve','--no-auto-key-import','--no-autostart')
        $status=(& $c.GpgExecutable @common --status-fd 1 --verify (ConvertTo-GitGpgPath (Join-Path $c.PackageRoot ($c.ArchiveName+'.asc'))) (ConvertTo-GitGpgPath $fixture) 2>$null) -join "`n"
        [pscustomobject]@{Code=$LASTEXITCODE;Status=$status}
    } $c $gpgHome $fixture
    if ($result.Code -eq 0 -or $result.Status -notmatch '\[GNUPG:\] NO_PUBKEY ') { throw 'Expected missing-key failure was not observed.' }
    Reject { Assert-GpgSignatureStatus $result.Code $result.Status $fingerprint } 'SignatureNotTrusted'
}

$shell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
function Probe([string]$Code, [string]$Directory) {
    $encoded=[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($Code))
    Invoke-PrivateProcess -FilePath $shell -Arguments @('-NoProfile','-NonInteractive','-EncodedCommand',$encoded) -Stage 'ValidationProbe' -CommandCategory 'verification' -DiagnosticDirectory $Directory
}
Check 'nonzero exit preserves stage and useful classification' {
    $dir=Join-Path $testRoot 'failure'
    Reject { Probe "[Console]::Error.WriteLine('ERROR 1045: Access denied'); exit 73" $dir } 'exit=73'
    $record=Get-Content -Raw (Get-ChildItem -LiteralPath $dir -Filter '*.json').FullName | ConvertFrom-Json
    Equal $record.Stage 'ValidationProbe'; Equal $record.ExitCode 73
    if ($record.Findings -notcontains 'MYSQL_1045_AUTHENTICATION_DENIED') { throw 'Missing classification.' }
}
Check 'nonzero exit without output' {
    $dir=Join-Path $testRoot 'empty'
    Reject { Probe 'exit 74' $dir } 'exit=74'
    $record=Get-Content -Raw (Get-ChildItem -LiteralPath $dir -Filter '*.json').FullName | ConvertFrom-Json
    Equal $record.StdoutPresent $false; Equal $record.StderrPresent $false
}
Check 'sensitive output never reaches diagnostics or exception' {
    $dir=Join-Path $testRoot 'sensitive'
    $marker='TEST_SECRET_DO_NOT_LOG_7f9a'
    $code="[Console]::Error.WriteLine('A temporary password is generated for root@localhost: $marker'); [Console]::WriteLine('token=$marker password=$marker'); exit 75"
    $errorText=''
    try { Probe $code $dir } catch { $errorText=$_.Exception.Message }
    if (-not $errorText.Contains('exit=75')) { throw 'Failure hidden.' }
    $json=Get-Content -Raw (Get-ChildItem -LiteralPath $dir -Filter '*.json').FullName
    if (($json+$errorText).Contains($marker)) { throw 'Secret leaked.' }
}
Check 'diagnostic file creation failure keeps original error' {
    $blocked=Join-Path $testRoot 'blocked-directory'
    [IO.File]::WriteAllText($blocked, 'occupied')
    Reject { Probe 'exit 76' $blocked } 'DiagnosticWriteFailed'
    Equal ([IO.File]::ReadAllText($blocked)) 'occupied'
}
Check 'missing executable is diagnosed before MySQL log exists' {
    Reject { Invoke-PrivateProcess -FilePath (Join-Path $testRoot 'missing.exe') -Arguments @() -DiagnosticDirectory (Join-Path $testRoot 'start-failed') } 'ProcessStartFailed'
}
Check 'unapproved diagnostic target is rejected without creating resources' {
    Reject { Write-ProcessDiagnostic -Directory 'C:\CampusHub-Unapproved-Diagnostic' -Record @{} } 'DiagnosticPathNotAllowed'
    Equal (Test-Path -LiteralPath 'C:\CampusHub-Unapproved-Diagnostic') $false
}
Check 'installation gate rejects invalid package before mutation' {
    Reject { Get-VerifiedMySqlPackage -ArchivePath $fixture -GpgHome (Join-Path $testRoot 'gpg') } 'PackageMd5Mismatch'
    Equal (Test-Path -LiteralPath (Join-Path $testRoot 'gpg')) $false
}
Check 'restricted diagnostics ACL rejects broad access in memory' {
    $acl=New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true,$false)
    $rule=New-Object Security.AccessControl.FileSystemAccessRule((New-Object Security.Principal.SecurityIdentifier('S-1-1-0')), 'Read', 'Allow')
    $acl.AddAccessRule($rule)
    Reject { Assert-PrivateAcl $acl @('S-1-5-18','S-1-5-32-544') } 'UnexpectedDiagnosticIdentity'
}
Check 'installer revalidates and locks archive before any runtime mutation' {
    $text=[IO.File]::ReadAllText((Join-Path $repo 'scripts\Install-MySql84.ps1'))
    $lockPosition=$text.IndexOf('$packageLock = Open-PackageReadLock')
    $verifyPosition=$text.IndexOf('$packageEvidence = Get-VerifiedMySqlPackage')
    $mutationPosition=$text.IndexOf('New-IsolatedDirectory $c.RuntimeRoot')
    if ($lockPosition -lt 0 -or $verifyPosition -le $lockPosition -or $mutationPosition -le $verifyPosition -or -not $text.Contains('$packageLock.Dispose()')) { throw 'Installer trust gate is missing or too late.' }
    $preview=& (Join-Path $repo 'scripts\Install-MySql84.ps1')
    Equal $preview.Mode 'PREVIEW'; Equal $preview.SignatureRequired $true
}
Check 'installer routes all native failures to private runtime diagnostics' {
    $text=[IO.File]::ReadAllText((Join-Path $repo 'scripts\Install-MySql84.ps1'))
    foreach($stage in @('Version','Initialize','RegisterService')) {
        if ($text -notmatch ('Invoke-PrivateProcess[^\r\n]+-Stage '+$stage+'[^\r\n]+-DiagnosticDirectory \$diagnostics')) { throw 'Native phase lacks protected diagnostics.' }
    }
    if (-not $text.Contains('New-IsolatedDirectory $diagnostics ' + "''")) { throw 'Private diagnostics directory is not created.' }
}
$failed=@($script:results | Where-Object Status -eq 'FAIL').Count
[pscustomobject]@{Suite='Install fix (no installation)';Passed=$script:results.Count-$failed;Failed=$failed;TestRoot=$testRoot;Results=$script:results.ToArray()} | ConvertTo-Json -Depth 4
if($failed){throw "Install fix suite failed: $failed"}
