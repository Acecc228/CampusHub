#requires -Version 5.1
[CmdletBinding()]
param([switch]$Apply)
$ErrorActionPreference='Stop'
if (-not $Apply) { [pscustomobject]@{Mode='PREVIEW';Database='campushub_test';Host='127.0.0.1';Port=3307;Frontend='127.0.0.1:4173 production preview';Action='Create unique non-demo test manager; real Edge create/publish/logout'};return }
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Assert-CampusService
Confirm-CampusDatabase -CredentialName test-app -Database campushub_test
$before=Get-LegacyFingerprint
$saved=@{CH_M1_UI_APPLY=$env:CH_M1_UI_APPLY;CH_M1_UI_USERNAME=$env:CH_M1_UI_USERNAME;CH_M1_UI_PASSWORD=$env:CH_M1_UI_PASSWORD}
try {
    $name='m1_ui_'+[Guid]::NewGuid().ToString('N')
    $password=New-DatabaseSecret
    $salt=New-Object byte[] 16
    $random=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($salt) } finally { $random.Dispose() }
    $kdf=[Security.Cryptography.Rfc2898DeriveBytes]::new($password,$salt,600000,[Security.Cryptography.HashAlgorithmName]::SHA256)
    try { $hash=([BitConverter]::ToString($salt+$kdf.GetBytes(32))).Replace('-','').ToLowerInvariant() } finally { $kdf.Dispose() }
    $sql="START TRANSACTION; INSERT INTO app_user(login_name,display_name,password_hash,enabled,demo_account) VALUES('$name','M1 test organizer','$hash',1,0); INSERT INTO app_user_role(user_id,role_id) SELECT LAST_INSERT_ID(),id FROM app_role WHERE code='ORGANIZER'; COMMIT;"
    $null=Invoke-CampusSql -CredentialName test-app -Database campushub_test -Sql $sql
    $env:CH_M1_UI_APPLY='true';$env:CH_M1_UI_USERNAME=$name;$env:CH_M1_UI_PASSWORD=$password
    & node (Join-Path $PSScriptRoot 'm1-production-ui.cjs')
    if ($LASTEXITCODE -ne 0) { throw 'Production UI test failed; test fixture retained.' }
} finally {
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key,$saved[$key],'Process') }
    Assert-LegacyUnchanged $before
}
