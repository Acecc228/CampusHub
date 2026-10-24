#requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

function Get-CampusHubConfig {
    $c = Import-PowerShellDataFile (Join-Path $script:RepoRoot 'infra\mysql\mysql84.psd1')
    if ($c.RuntimeRoot -ne 'E:\CampusHubRuntime\mysql84' -or $c.ServiceName -ne 'MySQL84CampusHub' -or $c.Port -ne 3307) {
        throw 'The approved runtime, service name or port has changed. Stop for review.'
    }
    $c.RepoRoot = $script:RepoRoot
    $c.Server = Join-Path $c.RuntimeRoot 'server'
    $c.Data = Join-Path $c.RuntimeRoot 'data'
    $c.ConfigFile = Join-Path $c.RuntimeRoot 'config\my.ini'
    $c.Secrets = Join-Path $c.RuntimeRoot 'secrets'
    $c.ServerExe = Join-Path $c.Server 'bin\mysqld.exe'
    $c.ClientExe = Join-Path $c.Server 'bin\mysql.exe'
    return $c
}

function Assert-ContainedPath([string]$Root, [string]$Path) {
    if ($Root -notmatch '^[A-Za-z]:[\\/]' -or $Path -notmatch '^[A-Za-z]:[\\/]') { throw 'An absolute local drive path is required.' }
    $base = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/') + '\'
    $target = [IO.Path]::GetFullPath($Path)
    if (-not $target.StartsWith($base, [StringComparison]::OrdinalIgnoreCase)) { throw 'Path is outside the approved root.' }
}

function Assert-NoReparsePoint([string]$Path) {
    $cursor = [IO.Path]::GetFullPath($Path)
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            $item = Get-Item -LiteralPath $cursor -Force
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse point is not allowed: $cursor" }
        }
        $parent = Split-Path $cursor -Parent
        if ($parent -eq $cursor) { break }
        $cursor = $parent
    }
}

function Assert-DatabaseIdentity {
    param([string]$Version, [int]$Port, [string]$DataDirectory, [string]$Database, [string]$ExpectedDatabase)
    $c = Get-CampusHubConfig
    $actualData = $DataDirectory.Replace('/', '\').TrimEnd('\')
    if ($Version -ne $c.Version -or $Port -ne $c.Port -or $actualData -ine $c.Data) { throw 'Unexpected MySQL instance. No writes are permitted.' }
    if ($ExpectedDatabase -and $Database -cne $ExpectedDatabase) { throw 'Unexpected database. No writes are permitted.' }
}

function Assert-MySqlConfiguration([string]$Text) {
    # A small allowlist prevents an edited template from redirecting initialization.
    $expected = @{
        'mysqld.basedir'='E:/CampusHubRuntime/mysql84/server'; 'mysqld.datadir'='E:/CampusHubRuntime/mysql84/data'
        'mysqld.tmpdir'='E:/CampusHubRuntime/mysql84/tmp'; 'mysqld.log-error'='E:/CampusHubRuntime/mysql84/logs/mysql84.err'
        'mysqld.port'='3307'; 'mysqld.bind-address'='127.0.0.1'; 'mysqld.mysqlx'='OFF'
        'mysqld.character-set-server'='utf8mb4'; 'mysqld.collation-server'='utf8mb4_0900_ai_ci'
        'mysqld.default-time-zone'='+00:00'; 'mysqld.default-storage-engine'='InnoDB'
        'mysqld.local-infile'='OFF'; 'mysqld.general-log'='OFF'; 'mysqld.partial-revokes'='OFF'
        'client.host'='127.0.0.1'; 'client.port'='3307'; 'client.protocol'='TCP'; 'client.default-character-set'='utf8mb4'
    }
    $seen = @{}
    $section = ''
    foreach ($line in ($Text -split '\r?\n')) {
        $line = $line.Trim()
        if (-not $line -or $line.StartsWith('#') -or $line.StartsWith(';')) { continue }
        if ($line -match '^\[(mysqld|client)\]$') { $section=$Matches[1]; continue }
        if ($line -notmatch '^([a-z-]+)=(.+)$') { throw 'Unapproved MySQL configuration syntax or include directive.' }
        $key = $section + '.' + $Matches[1]
        if (-not $expected.ContainsKey($key) -or $seen.ContainsKey($key) -or $Matches[2] -cne $expected[$key]) { throw "Unapproved or duplicate MySQL setting: $key" }
        $seen[$key] = $true
    }
    if ($seen.Count -ne $expected.Count) { throw 'Required MySQL configuration settings are missing.' }
}

function Assert-ServiceCommandLine([string]$CommandLine) {
    $c = Get-CampusHubConfig
    $binary = [regex]::Escape($c.ServerExe)
    $config = [regex]::Escape($c.ConfigFile)
    $pattern = '^"?' + $binary + '"?\s+(?:"--defaults-file=' + $config + '"|--defaults-file="' + $config + '"|--defaults-file=' + $config + ')\s+MySQL84CampusHub\s*$'
    if ($CommandLine -notmatch $pattern) { throw 'Unexpected service binary, configuration path or extra service arguments.' }
}

function Expand-AccountSql([string]$Template, [hashtable]$Passwords) {
    $result = $Template
    foreach ($key in $Passwords.Keys) {
        if ($Passwords[$key] -cnotmatch '^[a-f0-9]{64}$') { throw 'Password template accepts generated hexadecimal secrets only.' }
        $result = $result.Replace(('{{' + $key + '}}'), $Passwords[$key])
    }
    if ($result -match '\{\{[^}]+\}\}') { throw 'Unresolved SQL placeholder.' }
    return $result
}

function Assert-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Administrator PowerShell is required for this approved installation step.' }
}

function Assert-FreshInstall {
    $c = Get-CampusHubConfig
    Assert-NoReparsePoint $c.RuntimeRoot
    if (Test-Path -LiteralPath $c.RuntimeRoot) { throw 'Runtime already exists. No overwrite, cleanup or automatic recovery is allowed.' }
    if (Get-CimInstance Win32_Service -Filter "Name='MySQL84CampusHub'") { throw 'Service name already exists. Stop for review.' }
    if (Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -eq $c.Port }) { throw 'Port 3307 is occupied.' }
    foreach ($family in @('ipv4', 'ipv6')) {
        $ranges = & netsh interface $family show excludedportrange protocol=tcp
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect TCP excluded port ranges.' }
        foreach ($line in $ranges) {
            if ($line -match '^\s*(\d+)\s+(\d+)\s*\*?\s*$' -and $c.Port -ge [int]$Matches[1] -and $c.Port -le [int]$Matches[2]) { throw 'Port 3307 is reserved by Windows.' }
        }
    }
}

function Get-LegacyFingerprint {
    $c = Get-CampusHubConfig
    $svc = Get-CimInstance Win32_Service -Filter "Name='MySQL80'"
    if (-not $svc) { throw 'Expected MySQL80 service was not found; investigate before installation.' }
    [ordered]@{ Name=$svc.Name; State=$svc.State; StartMode=$svc.StartMode; StartName=$svc.StartName; PathName=$svc.PathName; ConfigHash=(Get-FileHash -LiteralPath $c.LegacyConfig -Algorithm SHA256).Hash }
}

function Assert-LegacyUnchanged($Before) {
    $after = Get-LegacyFingerprint
    foreach ($key in @('Name','State','StartMode','StartName','PathName','ConfigHash')) {
        if ($Before[$key] -cne $after[$key]) { throw "MySQL80 baseline changed ($key). Stop; do not repair the original instance automatically." }
    }
}

function Assert-CampusService {
    $c = Get-CampusHubConfig
    foreach ($path in @($c.ServerExe,$c.ClientExe,$c.ConfigFile,$c.Data,$c.Secrets,(Join-Path $c.RuntimeRoot 'logs'),(Join-Path $c.RuntimeRoot 'tmp'))) { Assert-NoReparsePoint $path }
    Assert-MySqlConfiguration ([IO.File]::ReadAllText($c.ConfigFile))
    $svc = Get-CimInstance Win32_Service -Filter "Name='MySQL84CampusHub'"
    if (-not $svc -or $svc.State -ne 'Running') { throw 'The CampusHub service must be running.' }
    if ($svc.StartMode -ne 'Manual' -or $svc.StartName -notmatch '(?i)\\Local\s?Service$') { throw 'Unexpected service startup mode or service account.' }
    Assert-ServiceCommandLine $svc.PathName
    $pids = @([int]$svc.ProcessId)
    $pids += @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object { $_.ParentProcessId -eq $svc.ProcessId } | ForEach-Object { [int]$_.ProcessId })
    $listener = @(Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -eq $c.Port })
    if ($listener.Count -ne 1 -or $listener[0].LocalAddress -ne '127.0.0.1' -or $listener[0].OwningProcess -notin $pids) { throw 'Port 3307 does not belong exclusively to the approved local service.' }
}

function Assert-PrivateAcl($Acl, [string[]]$AllowedSids) {
    if (-not $Acl.AreAccessRulesProtected) { throw 'DiagnosticInheritanceNotRestricted' }
    foreach ($rule in $Acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) {
        if ($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Value -notin $AllowedSids) { throw 'UnexpectedDiagnosticIdentity' }
    }
}

function Write-ProcessDiagnostic([string]$Directory, [hashtable]$Record) {
    $c = Get-CampusHubConfig
    $path = [IO.Path]::GetFullPath($Directory)
    $localRoot = Join-Path $c.RepoRoot '.local'
    $runtimeDiagnostics = Join-Path $c.RuntimeRoot 'diagnostics'
    if ($path -ine $runtimeDiagnostics) {
        try { Assert-ContainedPath $localRoot $path } catch { throw 'DiagnosticPathNotAllowed' }
    }
    Assert-NoReparsePoint $path
    if ($path -ieq $runtimeDiagnostics) {
        # Installation creates this private directory in its approved ACL step.
        # A failed check must never fall back to an unrestricted location.
        $owner = (Get-Acl -LiteralPath $c.RepoRoot).Owner
        $sid = (New-Object Security.Principal.NTAccount($owner)).Translate([Security.Principal.SecurityIdentifier]).Value
        Assert-PrivateAcl (Get-Acl -LiteralPath $path) @($sid,'S-1-5-18','S-1-5-32-544')
    } elseif (-not (Test-Path -LiteralPath $path -PathType Container)) {
        New-Item -ItemType Directory -Path $path | Out-Null
    }
    # Record contains only closed-set metadata, never raw stdout/stderr/arguments/SQL.
    $file = Join-Path $path ('failure-' + [guid]::NewGuid().ToString('N') + '.json')
    $stream = [IO.File]::Open($file, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $bytes = (New-Object Text.UTF8Encoding($false)).GetBytes(($Record | ConvertTo-Json -Depth 4))
        $stream.Write($bytes, 0, $bytes.Length)
    } finally { $stream.Dispose() }
    return $file
}

function Stop-PrivateProcessFailure {
    param([string]$Stage, [string]$Category, $ExitCode, [string]$OutText, [string]$ErrorText, [string]$Directory, [string]$FailureKind='ProcessFailed', $NativeErrorCode=$null)
    $findings = @()
    $known = @{ '1045'='AUTHENTICATION_DENIED'; '1044'='DATABASE_ACCESS_DENIED'; '1049'='UNKNOWN_DATABASE'; '1142'='TABLE_ACCESS_DENIED'; '2003'='CONNECTION_FAILED'; '2026'='TLS_ERROR' }
    foreach ($code in $known.Keys) {
        if ($ErrorText -match ('(?m)^\s*ERROR\s+'+$code+'(?:\s|\(|:)')) { $findings += ('MYSQL_'+$code+'_'+$known[$code]) }
    }
    if ($ErrorText -match '(?i)permission denied|access is denied|access denied') { $findings += 'ACCESS_DENIED' }
    if ($ErrorText -match '(?i)unknown (option|variable)|unrecognized option') { $findings += 'INVALID_CONFIGURATION' }
    if ($ErrorText -match '(?i)cannot (open|create|write)|could not (open|create|write)|no such file|file not found') { $findings += 'FILE_ACCESS_FAILED' }
    if ($ErrorText -match '(?i)address already in use|bind on TCP/IP port') { $findings += 'PORT_BIND_FAILED' }
    if (-not $findings.Count) { $findings = @('UNCLASSIFIED_OUTPUT_WITHHELD') }
    $record = @{
        TimeUtc=[DateTime]::UtcNow.ToString('o'); Stage=$Stage; CommandCategory=$Category
        Status='FAIL'; FailureKind=$FailureKind; ExitCode=$ExitCode; NativeErrorCode=$NativeErrorCode
        StdoutPresent=[bool]$OutText; StderrPresent=[bool]$ErrorText; Findings=$findings; RawOutputRetained=$false
    }
    $diagnostic = 'DiagnosticWriteFailed'
    try { $diagnostic = Write-ProcessDiagnostic -Directory $Directory -Record $record } catch { }
    # Even a diagnostic-write failure must preserve the original failure outcome.
    throw "$FailureKind; stage=$Stage; category=$Category; exit=$ExitCode; findings=$($findings -join ','); diagnostic=$diagnostic"
}

function Invoke-PrivateProcess {
    param(
        [string]$FilePath, [string[]]$Arguments, [string]$InputText = '',
        [ValidateSet('Version','Initialize','RegisterService','DatabaseSql','ValidationProbe')][string]$Stage='ValidationProbe',
        [ValidateSet('mysql-server','mysql-client','verification')][string]$CommandCategory='verification',
        [string]$DiagnosticDirectory=(Join-Path $script:RepoRoot '.local\diagnostics')
    )
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $FilePath
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    # Arguments are fixed flags/validated Windows paths, never passwords or SQL.
    foreach ($arg in $Arguments) { if ($arg.Contains('"') -or $arg.Contains("`r") -or $arg.Contains("`n")) { throw 'Unsafe process argument.' } }
    $info.Arguments = (($Arguments | ForEach-Object { '"' + $_ + '"' }) -join ' ')
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
    $info.StandardErrorEncoding = New-Object Text.UTF8Encoding($false)
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        try { $started = $process.Start() } catch {
            $native = $_.Exception.GetBaseException()
            $nativeCode = if ($native -is [ComponentModel.Win32Exception]) { $native.NativeErrorCode } else { $null }
            Stop-PrivateProcessFailure $Stage $CommandCategory $null '' '' $DiagnosticDirectory 'ProcessStartFailed' $nativeCode
        }
        if (-not $started) { Stop-PrivateProcessFailure $Stage $CommandCategory $null '' '' $DiagnosticDirectory 'ProcessStartFailed' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        # .NET Framework (Windows PowerShell 5.1) has no StandardInputEncoding.
        $writer = New-Object IO.StreamWriter($process.StandardInput.BaseStream, (New-Object Text.UTF8Encoding($false)))
        $inputFailed = $false
        try { $writer.Write($InputText) } catch { $inputFailed = $true }
        finally { try { $writer.Dispose() } catch { $inputFailed = $true } }
        $process.WaitForExit()
        $result = $stdout.Result
        $errorText = $stderr.Result
        if ($process.ExitCode -ne 0 -or $inputFailed) { Stop-PrivateProcessFailure $Stage $CommandCategory $process.ExitCode $result $errorText $DiagnosticDirectory }
        return $result
    } finally { $process.Dispose() }
}

function Invoke-CampusSql {
    param([string]$CredentialName, [string]$Sql, [string]$Database = '', [switch]$ExpiredPassword)
    if ($CredentialName -notin @('root-initial','root','app','migrator','test-app','test-migrator')) { throw 'Unknown credential name.' }
    $c = Get-CampusHubConfig
    $credentialFile = Join-Path $c.Secrets ($CredentialName + '.cnf')
    Assert-NoReparsePoint $credentialFile
    if (-not (Test-Path -LiteralPath $credentialFile -PathType Leaf)) { throw 'Required local credential file is absent.' }
    $clientArguments = @("--defaults-file=$credentialFile", '--no-login-paths', '--protocol=TCP', '--host=127.0.0.1', '--port=3307', '--connect-timeout=10', '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names')
    if ($Database) {
        if ($Database -notin @('campushub','campushub_test')) { throw 'Unknown database.' }
        $clientArguments += "--database=$Database"
    }
    if ($ExpiredPassword) { $clientArguments += '--connect-expired-password' }
    Invoke-PrivateProcess -FilePath $c.ClientExe -Arguments $clientArguments -InputText $Sql -Stage DatabaseSql -CommandCategory mysql-client -DiagnosticDirectory (Join-Path $c.RuntimeRoot 'diagnostics')
}

function Confirm-CampusDatabase {
    param([string]$CredentialName, [string]$Database = '')
    $row = (Invoke-CampusSql -CredentialName $CredentialName -Database $Database -Sql 'SELECT VERSION(), @@port, @@datadir, DATABASE(), CURRENT_USER(), @@partial_revokes;').Trim().Split("`t")
    if ($row.Count -ne 6) { throw 'Unexpected database identity response.' }
    Assert-DatabaseIdentity -Version $row[0] -Port ([int]$row[1]) -DataDirectory $row[2] -Database $row[3] -ExpectedDatabase $Database
    $accounts = @{ root='root@localhost'; migrator='campushub_migrator@127.0.0.1'; app='campushub_app@127.0.0.1'; 'test-app'='campushub_test_app@127.0.0.1'; 'test-migrator'='campushub_test_migrator@127.0.0.1' }
    if ($row[4] -cne $accounts[$CredentialName] -or $row[5] -ne '0') { throw 'Unexpected authenticated account or database grant semantics.' }
}

function New-DatabaseSecret {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}

function Write-ClientCredential {
    param([string]$Name, [string]$User, [string]$Password)
    if ($Name -notin @('root-initial','root','app','migrator','test-app','test-migrator')) { throw 'Unknown credential name.' }
    if ($User -notmatch '^(root|campushub(_test)?_(app|migrator))$') { throw 'Unknown database account.' }
    $c = Get-CampusHubConfig
    Assert-PrivateCredentialDirectory
    $path = Join-Path $c.Secrets ($Name + '.cnf')
    Assert-NoReparsePoint $path
    if (Test-Path -LiteralPath $path) { throw 'Credential already exists. Do not overwrite or reset automatically.' }
    $escaped = $Password.Replace('\','\\').Replace('"','\"').Replace("`r",'\r').Replace("`n",'\n')
    $content = "[client]`nuser=$User`npassword=`"$escaped`"`nhost=127.0.0.1`nport=3307`nprotocol=TCP`nssl-mode=REQUIRED`n"
    [IO.File]::WriteAllText($path, $content, (New-Object Text.UTF8Encoding($false)))
}

function Assert-PrivateCredentialDirectory {
    $c = Get-CampusHubConfig
    Assert-NoReparsePoint $c.Secrets
    $acl = Get-Acl -LiteralPath $c.Secrets
    $owner = (Get-Acl -LiteralPath $c.RepoRoot).Owner
    $developerSid = (New-Object Security.Principal.NTAccount($owner)).Translate([Security.Principal.SecurityIdentifier]).Value
    $allowed = @($developerSid, 'S-1-5-32-544', 'S-1-5-18')
    if (-not $acl.AreAccessRulesProtected) { throw 'Credential directory must have explicitly restricted inheritance before writing secrets.' }
    foreach ($rule in $acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) {
        if ($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Value -notin $allowed) { throw 'Credential directory permits an unexpected identity.' }
    }
}

Export-ModuleMember -Function Get-CampusHubConfig,Assert-ContainedPath,Assert-NoReparsePoint,Assert-DatabaseIdentity,Assert-MySqlConfiguration,Assert-ServiceCommandLine,Expand-AccountSql,Assert-Administrator,Assert-FreshInstall,Get-LegacyFingerprint,Assert-LegacyUnchanged,Assert-CampusService,Invoke-PrivateProcess,Invoke-CampusSql,Confirm-CampusDatabase,New-DatabaseSecret,Write-ClientCredential,Assert-PrivateCredentialDirectory,Write-ProcessDiagnostic,Assert-PrivateAcl
