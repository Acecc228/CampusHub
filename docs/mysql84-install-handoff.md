# MySQL 8.4 安装交接与执行记录

> G02 公开版本：历史 Windows 用户目录已替换为通用示例；原始文件仅本地保留，示例路径不能直接用于本机操作。

## 当前结果：安装及只读验收完成

2026-10-19 06:51（北京时间）：MySQL84CampusHub 为 Running / Manual / LocalService，实际版本 8.4.11，仅监听 127.0.0.1:3307。完整结果见 [安装验收报告](verification/m1.0-a-install.md) 和 [最终 JSON](verification/mysql84-install-acceptance-final.json)。认证 SQL 与业务初始化尚未执行。

用户后续授权标准 UAC 和仅当前进程 RemoteSigned，取代下面历史交接中的“必须手动打开终端、不自动提权”限制。已创建并检查 `scripts/Invoke-ReviewedMySqlInstall.ps1`，完整保留外层基线/源码锁、环境检查和安装器内部包签名校验。通过标准 UAC 执行一次安装成功。电脑随后重启，Manual 服务未自动启动；经标准 UAC 仅启动了现有新服务，没有再次初始化或注册。

**下面原核验内容是安装前历史快照。安装已完成，不要再次运行其中的安装命令，也不要更改冻结的 install-preflight.json 来满足全新安装条件。** 业务数据库、账号、Flyway 和后端仍需单独授权。

## 原安装前核验（历史记录）

2026-10-19（北京时间）。用户已条件批准独立实例安装及只读验收，但当前 Codex 进程没有管理员权限。**未执行安装；不请求再次批准同一范围，也不自动提权。**

## 实际完成的检查

证据：[install-preflight.public.json](verification/install-preflight.public.json)。

| 检查 | 状态 | 结果 |
|---|---|---|
| 官方包及固定路径 | PASS | `E:\Project\CampusHub.local\packages\mysql-8.4.11-winx64.zip`，281191914 字节 |
| MD5 | PASS | `2e833921898a9a030ea6bfe81bd811bc` |
| SHA-256 | PASS | `A492371D687D2BAB088B0062581144A0044B8964BAEFDF4FAA579292B423D25C`，验证后的本地固定值 |
| GPG / 官方完整指纹 | PASS | 退出码 0；VALIDSIG；`BCA43417C3B485DD128EC6D4B7B3B788A8D3785C` |
| MySQL80 | PASS（只读） | Running / Auto / NetworkService；3306、33060 正常监听；配置 SHA-256 与原记录一致 |
| 新服务 / 新 runtime | PASS（安装前） | 均不存在；父目录 `E:\CampusHubRuntime` 也不存在 |
| 3307 | PASS（安装前） | 未监听，未在 IPv4/IPv6 TCP 排除范围内 |
| 源码与参数 | PASS（重新审阅） | 未更改安装器、辅助模块或配置；服务名/端口/独立路径/Manual/LocalService 方案不变 |
| PowerShell | PASS | 7.6.5，程序 Microsoft Authenticode 签名 Valid |
| 管理员权限 | BLOCKED | 当前进程 IsAdministrator=false，安装未尝试 |
| 新实例版本、监听、服务、实际 ACL | NOT_RUN | 尚未安装，不能根据配置模板宣称运行通过 |
| 认证 SQL / @@datadir | NOT_RUN | 未建立可用凭据，不读取临时 root 密码 |
| 账号初始化 / Flyway / 后端 | NOT_RUN，未授权 | 本轮明确不执行 |

本轮实际调用了 `Test-MySqlPackage.ps1`、`Test-Environment.ps1`、`Assert-FreshInstall`、`Assert-LegacyUnchanged`、安装器默认 PREVIEW、`Get-AuthenticodeSignature` 和文件哈希检查。没有执行任何 `-Apply`、ACL 写入、服务变更或 MySQL 程序。安装退出码为“不适用”，不是安装失败或成功。

官方 MD5 与指纹再次对照 [下载页](https://dev.mysql.com/downloads/mysql/8.4.html) 和 [签名手册](https://dev.mysql.com/doc/refman/8.4/en/checking-gpg-signature.html)。源码通过重新读取并对照本会话已审阅内容复核；上轮未生成源码摘要清单，本次第一次固定 6 个执行文件的 SHA-256，作为从本次核验到手动执行的比对基线，不声称存在过去的签名源码证明。

## 在管理员 PowerShell 7 执行

先由用户通过 Windows 界面打开管理员终端。如果打开的是 Windows PowerShell 5.1，可在这个已提升权限的终端中启动本机已核验的 PowerShell 7：

```powershell
& 'C:\Users\PUBLIC_USER\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile
```

该命令本身不提权，必须由用户先打开管理员终端。不要修改 ExecutionPolicy。

然后将下面整块命令作为一次操作执行。它先检查管理员、受信任 PowerShell、已固定的核验记录和源码，再重新确认旧实例及全新安装条件。任何异常会退出此代码块，不继续安装。最后只调用已经批准的原安装器：

```powershell
& {
    $ErrorActionPreference = 'Stop'
    if ($PSVersionTable.PSVersion.Major -ne 7) { throw 'PowerShell 7 is required.' }
    $principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Administrator terminal is required.' }
    $pwshPath = (Get-Process -Id $PID).Path
    $signature = Get-AuthenticodeSignature -LiteralPath $pwshPath
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'CN=Microsoft Corporation') { throw 'PowerShell trust verification failed.' }

    $repo = 'E:\Project\CampusHub'
    $recordPath = Join-Path $repo 'docs\verification\install-preflight.json'
    if ((Get-FileHash -LiteralPath $recordPath -Algorithm SHA256).Hash -cne 'F7067F8C32F2902A54D86441264C113813E1C50EBC811497474A3BA919FC88FC') { throw 'Preflight record changed; stop for review.' }
    $record = Get-Content -LiteralPath $recordPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $sourceLocks = @()
    try {
        foreach ($entry in $record.SourceFiles) {
            $path = Join-Path $repo $entry.RelativePath
            $sourceLocks += [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
            if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -cne $entry.Sha256) { throw 'Reviewed source changed; stop for review.' }
        }
        Import-Module (Join-Path $repo 'scripts\lib\CampusHub.MySql.psm1') -Force
        $before = @{}
        foreach ($property in $record.Environment.LegacyFingerprint.PSObject.Properties) { $before[$property.Name] = $property.Value }
        Assert-LegacyUnchanged $before
        if ((Get-Service -Name MySQL80).Status -ne 'Running') { throw 'MySQL80 is not running.' }
        foreach ($port in @(3306,33060)) {
            if (-not (Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq $port)) { throw 'Legacy listener is absent.' }
        }
        Assert-FreshInstall
        # 原安装器在任何 runtime/ACL 修改之前，重新验证 ZIP、MD5、SHA-256、GPG。
        & 'E:\Project\CampusHub\scripts\Install-MySql84.ps1' -ArchivePath 'E:\Project\CampusHub.local\packages\mysql-8.4.11-winx64.zip' -Apply
    } finally {
        foreach ($handle in $sourceLocks) { $handle.Dispose() }
    }
}
```

脚本哈希或环境发生变化时不要改记录以求通过。失败后保留全部现场，不再次运行安装、不清空目录、不删服务、不修改 ACL；返回脚本的脱敏错误与阶段即可，不粘贴 mysql84.err、凭据文件或临时密码。

## 安装后交接

无论成功或失败，先停止，不执行 `Initialize-Database.ps1 -Apply`。用户返回安装器的结构化结果或脱敏错误后，Codex 可在当前会话中执行已授权的只读验收：

1. 读取服务名、Running、Manual、LocalService、完整程序与配置路径。
2. 核对监听与服务进程归属，确认新实例只监听 `127.0.0.1:3307`，没有意外 X Protocol 监听。
3. 核对新程序 `--no-defaults --version` 与 TCP 协议握手，确认实际 8.4.11；不以 ZIP 名称代替运行结果。
4. 检查新配置与 data 实际文件路径；无认证 SQL 时单独将 `@@datadir` 标为 NOT_RUN。
5. 实际读取 data/logs/tmp/secrets/diagnostics 及 mysql84.err 的 ACL：无额外 Allow，目录继承隔离；LocalService 仅对 data/logs/tmp 拥有 Modify，在 secrets/diagnostics 无授权。读取 ACL 不读取敏感日志正文。
6. 对比 MySQL80 的 Running、3306/33060、配置指纹和原基线。
7. 成功时整理 PASS/FAIL/NOT_RUN 及下一阶段候选方案；失败时明确已完成步骤、数值退出码和脱敏分类，保留现场等待确认。

新服务启停测试、临时密码轮换、业务库/账号创建、Flyway 和后端开发不在本次自动执行范围。当前尚不具备宣告安装验收完成、进入业务账号初始化的条件。

G02：冻结原件 `docs/verification/install-preflight.json` 仅本地保留且哈希未变；公开副本含脱敏标记，不能替代安装器哈希基线。新克隆没有原件，历史安装入口会拒绝执行，这是预期保护，不要复制副本或改哈希绕过。
