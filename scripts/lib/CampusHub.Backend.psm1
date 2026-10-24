#requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'CampusHub.MySql.psm1')

function Assert-DemoRoleInventory {
    param([string]$Roles)
    if ($Roles -cne 'ADMIN,ORGANIZER,STUDENT') { throw 'BackendGuard: demo roles are incomplete or unexpected.' }
}

function Get-MigrationAction {
    param([string]$Database, [string[]]$Tables, [switch]$InitializeTestHistory)
    if ($Database -notin @('campushub','campushub_test')) { throw 'BackendGuard: invalid database.' }
    $business = @('activity_category','app_user','app_role','app_user_role','activity','activity_registration')
    $allowed = @('flyway_schema_history') + $business
    if ($Database -eq 'campushub_test') { $allowed += 'm10b_unicode_probe' }
    foreach ($table in $Tables) { if ($table -cnotin $allowed) { throw 'BackendGuard: unknown table.' } }
    if ($Database -eq 'campushub_test' -and 'm10b_unicode_probe' -cnotin $Tables) { throw 'BackendGuard: missing evidence table.' }
    if ('flyway_schema_history' -cin $Tables) {
        if ($InitializeTestHistory) { throw 'BackendGuard: history already exists; do not baseline again.' }
        return 'Migrate'
    }
    foreach ($table in $business) { if ($table -cin $Tables) { throw 'BackendGuard: business table exists without history.' } }
    if ($Database -eq 'campushub_test') {
        if (-not $InitializeTestHistory) { throw 'BackendGuard: explicit InitializeTestHistory is required for verified probe.' }
        return 'BaselineThenMigrate'
    }
    if ($InitializeTestHistory) { throw 'BackendGuard: baseline is limited to test evidence.' }
    return 'Migrate'
}

function Read-BackendCredentialText {
    param([string]$Text, [string]$ExpectedUser)
    $values = @{}
    foreach ($line in ($Text -split '\r?\n')) {
        if (-not $line -or $line -eq '[client]') { continue }
        if ($line -notmatch '^([a-z-]+)=(.+)$' -or $values.ContainsKey($Matches[1])) { throw 'BackendGuard: invalid credential format.' }
        $values[$Matches[1]] = $Matches[2]
    }
    if ($values.Count -ne 6 -or $values.user -cne $ExpectedUser -or $values.host -cne '127.0.0.1' -or $values.port -cne '3307' -or $values.protocol -cne 'TCP' -or $values['ssl-mode'] -cne 'REQUIRED' -or $values.password -cnotmatch '^"([a-f0-9]{64})"$') {
        throw 'BackendGuard: credential identity or format differs.'
    }
    return @{User=$values.user;Password=$values.password.Trim('"')}
}

function Get-BackendCredential {
    param([ValidateSet('app','test-app','migrator','test-migrator')][string]$Name)
    $c = Get-CampusHubConfig
    Assert-PrivateCredentialDirectory
    $path = Join-Path $c.Secrets ($Name + '.cnf')
    Assert-NoReparsePoint $path
    $names = @{app='campushub_app';'test-app'='campushub_test_app';migrator='campushub_migrator';'test-migrator'='campushub_test_migrator'}
    Read-BackendCredentialText -Text ([IO.File]::ReadAllText($path)) -ExpectedUser $names[$Name]
}

function Clear-BackendDatabaseEnvironment {
    param($Environment)
    foreach ($key in @($Environment.Keys)) {
        if ($key -match '^(FLYWAY_|CH_|CAMPUSHUB_|SERVER_|SPRING_|SPRINGDOC_|MYSQL_|MAVEN_ARGS$|MAVEN_OPTS$|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$)') { $Environment.Remove($key) }
    }
}

function New-BackendProcess {
    param([string]$FilePath, [string[]]$Arguments, [hashtable]$Environment = @{})
    $c = Get-CampusHubConfig
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $FilePath
    $info.WorkingDirectory = Join-Path $c.RepoRoot 'backend'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
    $info.StandardErrorEncoding = New-Object Text.UTF8Encoding($false)
    # Fixed arguments only. Secrets are supplied exclusively through the child environment.
    foreach ($arg in $Arguments) { if ($arg -match '["\r\n]') { throw 'BackendGuard: unsafe process argument.' } }
    $info.Arguments = (($Arguments | ForEach-Object { '"' + $_ + '"' }) -join ' ')
    Clear-BackendDatabaseEnvironment $info.EnvironmentVariables
    $info.EnvironmentVariables['JAVA_HOME'] = 'D:\develop\jdk'
    $info.EnvironmentVariables['MAVEN_OPTS'] = '-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'
    foreach ($key in $Environment.Keys) { $info.EnvironmentVariables[$key] = $Environment[$key] }
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    return $process
}

function Invoke-BackendMaven {
    param([string[]]$Goals, [hashtable]$Environment = @{}, [string]$Label)
    $c = Get-CampusHubConfig
    foreach ($goal in $Goals) { if ($goal -notmatch '^[A-Za-z0-9:./=,_-]+$') { throw 'BackendGuard: invalid Maven argument.' } }
    # cmd /c requires the outer quotes around a quoted executable and its arguments.
    $process = New-BackendProcess -FilePath $env:ComSpec -Arguments @() -Environment $Environment
    $wrapper = Join-Path $c.RepoRoot 'backend\mvnw.cmd'
    $process.StartInfo.Arguments = '/d /s /c ""' + $wrapper + '" --batch-mode --no-transfer-progress ' + ($Goals -join ' ') + '"'
    $null = $process.Start()
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $output = $stdout.Result + $stderr.Result
        foreach ($key in $Environment.Keys) { if ($key -match 'PASSWORD|SECRET|TOKEN') { $output = $output.Replace($Environment[$key], '[REDACTED]') } }
        $logDir = Join-Path $c.RepoRoot '.local\backend-logs'
        if (-not (Test-Path -LiteralPath $logDir)) { $null = New-Item -ItemType Directory -Path $logDir }
        $log = Join-Path $logDir ($Label + '-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff') + '.log')
        [IO.File]::WriteAllText($log, $output, (New-Object Text.UTF8Encoding($false)))
        if ($process.ExitCode -ne 0) { throw ('BackendMavenFailed: exit=' + $process.ExitCode + '; sanitized log=' + $log) }
        [pscustomobject]@{Status='PASS';Stage=$Label;ExitCode=$process.ExitCode;Log=$log}
    } finally { $process.Dispose() }
}

function Assert-BackendProbe {
    $columns = (Invoke-CampusSql -CredentialName test-migrator -Database campushub_test -Sql "SELECT column_name,column_type,is_nullable,column_key FROM information_schema.columns WHERE table_schema='campushub_test' AND table_name='m10b_unicode_probe' ORDER BY ordinal_position;").Trim()
    $table = (Invoke-CampusSql -CredentialName test-migrator -Database campushub_test -Sql "SELECT engine,table_collation,table_type FROM information_schema.tables WHERE table_schema='campushub_test' AND table_name='m10b_unicode_probe';").Trim()
    $data = (Invoke-CampusSql -CredentialName test-migrator -Database campushub_test -Sql 'SELECT id,HEX(payload) FROM m10b_unicode_probe ORDER BY id;').Trim()
    if ($columns.Replace("`r`n", "`n") -cne "id`tint`tNO`tPRI`npayload`tvarchar(100)`tNO" -or $table -cne "InnoDB`tutf8mb4_0900_ai_ci`tBASE TABLE" -or $data -cne "1`tE6A0A1E59BADE6B4BBE58AA87CE298837CF09F98807CF0A080807C65CC81") {
        throw 'BackendGuard: original evidence structure or data differs.'
    }
}

Export-ModuleMember -Function Assert-DemoRoleInventory,Get-MigrationAction,Read-BackendCredentialText,Get-BackendCredential,Clear-BackendDatabaseEnvironment,New-BackendProcess,Invoke-BackendMaven,Assert-BackendProbe
