#requires -Version 5.1
[CmdletBinding()]
param([ValidateSet('campushub','campushub_test')][string]$Database='campushub_test',[switch]$Apply)
$ErrorActionPreference='Stop'
if (-not $Apply) { [pscustomobject]@{Mode='PREVIEW';Database=$Database;Instance='127.0.0.1:3307';Action='Create local demo accounts or verify existing configuration without reset'}; return }
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.MySql.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'lib\CampusHub.Backend.psm1') -Force
$c=Get-CampusHubConfig
Assert-CampusService
Assert-PrivateCredentialDirectory
$credential=if ($Database -eq 'campushub') { 'migrator' } else { 'test-migrator' }
Confirm-CampusDatabase -CredentialName $credential -Database $Database
$roleInventory=(Invoke-CampusSql -CredentialName $credential -Database $Database -Sql 'SELECT GROUP_CONCAT(code ORDER BY code) FROM app_role;').Trim()
Assert-DemoRoleInventory $roleInventory
$path=Join-Path $c.Secrets ($Database+'-demo.json')
Assert-NoReparsePoint $path
$roles=@('student','organizer','admin')
$existing=(Invoke-CampusSql -CredentialName $credential -Database $Database -Sql "SELECT COUNT(*) FROM app_user WHERE demo_account=1 OR login_name IN ('demo_student','demo_organizer','demo_admin');").Trim()
function Derive([string]$Password,[byte[]]$Salt) {
    $kdf=New-Object Security.Cryptography.Rfc2898DeriveBytes($Password,$Salt,600000,[Security.Cryptography.HashAlgorithmName]::SHA256)
    try { return $kdf.GetBytes(32) } finally { $kdf.Dispose() }
}
function Hex([byte[]]$Bytes) { return ([BitConverter]::ToString($Bytes)).Replace('-','').ToLowerInvariant() }
if (Test-Path -LiteralPath $path) {
    $saved=[IO.File]::ReadAllText($path) | ConvertFrom-Json
    if ($existing -cne '3' -or $saved.Database -cne $Database -or @($saved.Accounts).Count -ne 3) { throw 'Demo configuration conflict; no automatic repair.' }
    foreach ($role in $roles) {
        $account=@($saved.Accounts | Where-Object LoginName -CEQ ('demo_'+$role))
        if ($account.Count -ne 1 -or $account[0].Password -cnotmatch '^[a-f0-9]{64}$') { throw 'Invalid demo credential format.' }
        $row=(Invoke-CampusSql -CredentialName $credential -Database $Database -Sql ("SELECT u.password_hash,u.enabled,u.demo_account,GROUP_CONCAT(r.code ORDER BY r.code) FROM app_user u JOIN app_user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE u.login_name='demo_"+$role+"' GROUP BY u.id;")).Trim().Split("`t")
        if ($row.Count -ne 4 -or $row[0] -cnotmatch '^[a-f0-9]{96}$' -or $row[1] -ne '1' -or $row[2] -ne '1' -or $row[3] -cne $role.ToUpperInvariant()) { throw 'Demo identity or roles conflict.' }
        $salt=New-Object byte[] 16
        for ($i=0; $i -lt 16; $i++) { $salt[$i]=[Convert]::ToByte($row[0].Substring($i*2,2),16) }
        if ((Hex ($salt+(Derive $account[0].Password $salt))) -cne $row[0]) { throw 'Demo credential mismatch; no reset performed.' }
    }
    [pscustomobject]@{Status='PASS';Database=$Database;Action='Verified existing demo accounts';CredentialFile=$path}; return
}
if ($existing -cne '0') { throw 'Demo accounts exist without matching local credential file; stop.' }
$accounts=@()
$sql="START TRANSACTION;`n"
foreach ($role in $roles) {
    $password=New-DatabaseSecret
    $salt=New-Object byte[] 16
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($salt) } finally { $rng.Dispose() }
    $hash=Hex ($salt+(Derive $password $salt))
    $accounts+= [pscustomobject]@{LoginName=('demo_'+$role);Password=$password}
    $sql+="INSERT INTO app_user(login_name,display_name,password_hash,enabled,demo_account) VALUES ('demo_$role','Demo $role','$hash',1,1);`n"
    $sql+="INSERT INTO app_user_role(user_id,role_id) VALUES (LAST_INSERT_ID(),(SELECT id FROM app_role WHERE code='$($role.ToUpperInvariant())'));`n"
}
$sql+='COMMIT;'
# Save first, exclusively. A later SQL failure leaves a visible conflict, never resets accounts.
$content=[pscustomobject]@{Database=$Database;Accounts=$accounts} | ConvertTo-Json -Depth 4
$stream=[IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try { $bytes=(New-Object Text.UTF8Encoding($false)).GetBytes($content); $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
$null=Invoke-CampusSql -CredentialName $credential -Database $Database -Sql $sql
$linked=(Invoke-CampusSql -CredentialName $credential -Database $Database -Sql "SELECT u.login_name,GROUP_CONCAT(r.code ORDER BY r.code) FROM app_user u JOIN app_user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE u.demo_account=1 GROUP BY u.id,u.login_name ORDER BY u.login_name;").Trim().Replace("`r`n","`n")
if ($linked -cne "demo_admin`tADMIN`ndemo_organizer`tORGANIZER`ndemo_student`tSTUDENT") { throw 'Demo post-write verification failed; no automatic repair.' }
[pscustomobject]@{Status='PASS';Database=$Database;Action='Created demo accounts';CredentialFile=$path}
