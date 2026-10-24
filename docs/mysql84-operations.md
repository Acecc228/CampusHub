# 独立 MySQL 8.4 操作说明

**安装与M1.0-B两库、四账号、root轮换已完成；后续已完成Flyway V1～V4、最小业务授权、后端和双端客户端。不要再次执行安装器、初始化器或baseline。** 当前运行入口见 [README](../README.md) 和 [后端说明](backend-development.md)；安装历史见 [安装验收](verification/m1.0-a-install.md)、[M1.0-B报告](verification/m1.0-b-bootstrap.md)。以下安装/初始化命令仅为历史参考，不是重跑授权。

## 固定配置与权限

| 项目 | 新实例配置 |
|---|---|
| 版本 / 服务 | MySQL 8.4.11 / `MySQL84CampusHub`，手动启动 |
| Windows 身份 | `NT AUTHORITY\LocalService` |
| 端点 | `127.0.0.1:3307`，mysqlx 关闭，不新增防火墙规则 |
| 程序 | `E:\CampusHubRuntime\mysql84\server` |
| 数据 | `E:\CampusHubRuntime\mysql84\data` |
| 配置 | `E:\CampusHubRuntime\mysql84\config\my.ini` |
| 日志 / 临时 / 凭据 / 诊断 | 同一 runtime 下 `logs` / `tmp` / `secrets` / `diagnostics` |
| 已创建数据库 | `campushub`、`campushub_test`；utf8mb4 / utf8mb4_0900_ai_ci；UTC；开发库无表，测试库保留专用验收表及一条记录 |

开发者（当前项目目录所有者）、Administrators、SYSTEM 对新目录具有 FullControl。LocalService 对父目录、runtime、程序、配置具有 ReadAndExecute；仅对 data、logs、tmp 具有 Modify。secrets、diagnostics 取消继承，只有开发者、Administrators、SYSTEM 可访问。新文件继承各自父目录的权限。`logs/mysql84.err` 可能含临时 root 密码，属于敏感文件，禁止复制到普通日志、报告、聊天或仓库。

ACL 仅通过 `Install-MySql84.ps1` 的 `New-IsolatedDirectory` 函数设置：目标必须是本次创建的新目录，调用 `Set-Acl -LiteralPath $Path -AclObject $acl`，随后读取 ACL 检查继承已隔离且没有非白名单身份。不用 `/T`、递归 Set-Acl 或针对旧目录的权限重置。若 `E:\CampusHubRuntime` 不存在，也会新建并设置该父目录权限；如果已存在，只检查、不修改它的 ACL。现有父目录若不允许服务遍历，需要停止诊断并重新审批。

| 数据库账号（均限定 @127.0.0.1） | 初始权限 | 迁移后 |
|---|---|---|
| campushub_migrator | 自己库的 SELECT / INSERT / UPDATE / DELETE / CREATE / ALTER / INDEX / REFERENCES | 只用于显式 Flyway 步骤 |
| campushub_test_migrator | 仅测试库的同类权限 | 只用于测试库迁移 |
| campushub_app | 无业务表权限 | 仅 `campushub.activity_category` SELECT |
| campushub_test_app | 无业务表权限 | 仅测试库分类表 SELECT / INSERT / UPDATE / DELETE |

没有全局管理权限、GRANT OPTION、DROP 或应用账号的迁移历史权限。后续新增业务表按需要追加授权；不为了测试扩大开发账号权限。库级授权对 `campushub_test` 的下划线使用反斜杠转义；配置固定 `partial-revokes=OFF`，避免把 `_` 解释成额外数据库的通配符。[MySQL GRANT 规则](https://dev.mysql.com/doc/refman/8.4/en/grant.html)

## 1. 审阅与只读检查

普通 PowerShell 7；环境检查与预览只读，测试会在 `.local/tests` 生成夹具和脱敏记录，包验证会维护隔离公钥环：

```powershell
Set-Location 'E:\Project\CampusHub'
& .\scripts\Test-Environment.ps1
& .\scripts\Test-MySqlPackage.ps1
& .\scripts\Test-Preparation.ps1
& .\scripts\Install-MySql84.ps1 | ConvertTo-Json -Depth 4
Get-Service -Name MySQL80, MySQL84CampusHub -ErrorAction SilentlyContinue
Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in 3306,3307,33060 }
netsh interface ipv4 show excludedportrange protocol=tcp
netsh interface ipv6 show excludedportrange protocol=tcp
```

预览本身不要求安装包存在，不创建 runtime，不申请管理员权限。真正安装前还会重新检查目录、服务、监听和端口保留范围。

## 2. 获取与可信验证安装包（已完成）

2026-10-18 已从 [官方 ZIP 地址](https://dev.mysql.com/get/Downloads/MySQL-8.4/mysql-8.4.11-winx64.zip) 经官方 CDN 下载到 `E:\Project\CampusHub.local\packages\mysql-8.4.11-winx64.zip`，共 281191914 字节。这是仓库外目录。2026-10-19 安装前、安装内及安装后均复验通过；已在独立 runtime 解压和运行。最新证据见 [安装验收 JSON](verification/mysql84-install-acceptance-final.json)。

同时保存了 [官方 detached 签名](https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.11-winx64.zip.asc)（同名 `.zip.asc`）及 [官方仓库公钥](https://repo.mysql.com/RPM-GPG-KEY-mysql-2025)（本地名 `mysql-official-signing-key.asc`）。完整指纹独立对照 [MySQL 8.4 官方签名文档](https://dev.mysql.com/doc/refman/8.4/en/checking-gpg-signature.html)，不以与 ZIP 一起取得的公钥自证可信。

```powershell
& 'E:\Project\CampusHub\scripts\Test-MySqlPackage.ps1' -ArchivePath 'E:\Project\CampusHub.local\packages\mysql-8.4.11-winx64.zip'
```

| 核验项 | 固定值 / 通过条件 |
|---|---|
| 官网公布 MD5 | `2e833921898a9a030ea6bfe81bd811bc` |
| 本地 SHA-256 | `A492371D687D2BAB088B0062581144A0044B8964BAEFDF4FAA579292B423D25C` |
| 官方完整指纹 | `BCA43417C3B485DD128EC6D4B7B3B788A8D3785C` |
| 签名 | GPG 退出码 0；唯一 `VALIDSIG` 的签名与主密钥指纹均匹配；没有无效、撤销、过期状态 |
| ZIP | 存在、名称/版本一致、包含预期 mysqld.exe；所有条目属于预期根目录，无穿越/符号链接 |

SHA-256 是对已验证包的本地固定值，**不是官方发布的 SHA-256 真实性证明**。MD5 也不是签名。信任依据是官方文档独立核对的完整公钥指纹及密码学签名验证。

脚本使用已有 `D:\Git\usr\bin\gpg.exe`（2.4.9）和仓库内被忽略的 `.local/gpg-verification` 公钥环，不改默认钥匙环、不提升 ownertrust、不自动从 keyserver 导入密钥。Git GPG 的文件参数转换为 MSYS `/e/...`，Windows MySQL 仍使用原生路径。`TRUST_UNDEFINED` 表示未建立个人 web of trust，本项目以核对后的完整指纹为信任依据；绝不只根据“Good signature”文本判断。

未来若文件缺失，人工从上述三个官方链接下载到指定目录，先核对官网版本、MD5、文档指纹，再执行验证脚本；不要覆盖已有文件、改 hash 求通过或换未知镜像。下载/公钥/工具/签名任一步无法验证就停止。官方更新版本或密钥时另行审查。

`-Apply` 不相信以前的 JSON 报告：会重新执行全部校验，并从复验前到安装结束持续持有 ZIP 只读共享锁，阻止验证后写入或替换。验证结果保存为新 runtime 的 `package-verification.json`。缺失、冲突、哈希或签名不符均在创建 runtime 前终止。

## 3. 已执行的管理员安装流程（禁止重复安装）

本次由已审阅的 `Invoke-ReviewedMySqlInstall.ps1` 校验外层源码与基线后调用下列命令。用户在标准 UAC 中亲自确认，受信任 PowerShell 7 仅为当前进程使用 RemoteSigned；无永久策略修改。以下仅记录内部调用，不应绕过交接启动器单独执行：

```powershell
Set-Location 'E:\Project\CampusHub'
& .\scripts\Install-MySql84.ps1 -ArchivePath 'E:\Project\CampusHub.local\packages\mysql-8.4.11-winx64.zip' -Apply
```

此命令先复验安装包，创建前述全新目录与限定 ACL（包括新 diagnostics），解压到新 package 目录，将新包目录移动到新 server 目录，写入新 my.ini、包验证证据和旧实例的只读基线记录。然后按顺序调用以下准确命令，**它们是脚本内部动作，不应跳过保护检查单独复制执行**：

```powershell
& 'E:\CampusHubRuntime\mysql84\server\bin\mysqld.exe' --no-defaults --version
& 'E:\CampusHubRuntime\mysql84\server\bin\mysqld.exe' '--defaults-file=E:\CampusHubRuntime\mysql84\config\my.ini' --initialize
& 'E:\CampusHubRuntime\mysql84\server\bin\mysqld.exe' --install-manual MySQL84CampusHub '--defaults-file=E:\CampusHubRuntime\mysql84\config\my.ini' --local-service
Start-Service -Name MySQL84CampusHub
```

第一条核对程序版本；第二条只初始化新数据目录，临时 root 密码写在受限的新错误日志中；第三条新增一项 Windows 服务注册；第四条启动该新服务。服务为手动启动，重启 Windows 后需手动启动。新数据、二进制日志、错误日志会占用磁盘。没有修改 PATH、JAVA_HOME、旧服务或旧配置的步骤。

已有 runtime（即使空目录）、同名服务、端口占用、保留端口、重解析点、非空数据目录、配置指向变化、安装包不符都会停止。失败可能留下部分新目录或已注册的新服务；不自动删除、卸载、重置或再次初始化。先保存现场并诊断。

## 4. 数据库与账号初始化（已完成，不重跑）

安装验收已完成；本步骤仍须用户单独批准。获批后以项目开发者的普通 PowerShell 7 运行，不需要赋予应用 Windows 管理员权限：

```powershell
& 'E:\Project\CampusHub\scripts\Initialize-Database.ps1'       # 预览
# 仅在用户确认安装及数据库初始化范围之后：
& 'E:\Project\CampusHub\scripts\Initialize-Database.ps1' -Apply
& 'E:\Project\CampusHub\scripts\Test-MySql84.ps1' -Stage Bootstrap
```

脚本先验证新服务可执行文件、完整参数、配置、运行账号及监听进程，再读取新日志中的临时密码。由于临时 root 密码已过期，先只轮换这个新实例的 root；然后验证实际版本/端口/数据目录/认证账号，创建两个空库和四个独立账号，授予各自迁移权限。数据库与账号存在、secrets 非空时停止，不自动接管已有内容。

密码各自使用 32 字节随机数生成，保存为仓库外 `secrets\root.cnf`、`app.cnf`、`migrator.cnf`、`test-app.cnf`、`test-migrator.cnf`；初始凭据文件保留但密码已失效。写入前检查 secrets ACL。凭据文件是受 ACL 保护的本地明文配置，不是加密保险库。SQL 通过标准输入发送，密码不进入进程参数、PowerShell 历史或普通输出。禁止对初始化步骤启用会收集秘密的调试跟踪，禁止提交凭据或完整错误日志。日志中历史临时密码在轮换后无效，仍按敏感文件保护。

这里的 SQL 只负责库、用户和授权，不创建业务表。初始化失败后不自动重跑；MySQL DDL 不具备整个脚本事务回滚语义。Flyway 才是后续表结构版本的唯一来源。

## 5. 迁移与日常运行约定

当前已有Maven工程与两库V1～V4历史，复核只运行 `Invoke-BackendMigration.ps1 -Operation Validate -Database campushub_test -Apply`（开发库校验显式换库名）。未来新增迁移须另获授权；不得修改历史、repair、clean或重复baseline。完整当前步骤见 [后端说明](backend-development.md)。以下列表是M1.0-B时制定的历史实施顺序，相关授权已经完成，不按此列表重复授权或初始化：

1. 每个独立迁移进程只加载目标库 migrator 凭据，核对实际库名、3307、8.4.11 和新数据目录后运行固定版本 Flyway `validate → migrate → validate/info`。首次空库正常迁移，重复执行不得新增重复版本。
2. 持久化真实版本、checksum、成功状态证据。checksum 不符或失败迁移必须停止；不调用 `repair`、`clean`、跳过校验或修改历史来掩盖问题。
3. 在两个库都迁移并验证成功后，执行 `scripts/Grant-ApplicationAccess.ps1 -Apply`。该脚本只检查目标实例及分类表存在并授权，不替代 Flyway checksum 验证。
4. `scripts/Test-MySql84.ps1 -Stage Ready` 核对业务表权限矩阵；后端负向操作和事务测试仍需另行实现。M1.0-B 已通过数据库层隔离、Unicode 和回滚验证，不代表后端测试通过。
5. 后端进程仅注入 app 凭据，`spring.flyway.enabled=false`；不继承迁移用户名、密码、root 文件路径。测试进程只加载 test-app，写前核对 `campushub_test`。

当前Maven脚本通过子进程环境传递凭据，不用 `-Dpassword=...`；生产禁用开发登录与文档端点已有实际验证。日常后端仅持有app凭据，迁移账号不进入业务进程。

## 6. 安装后的验收及原实例 SQL

批准安装后的顺序：

1. 先运行包验证与环境检查，保存 MySQL80 基线；确认新服务/runtime 不存在、3307 未占用或保留。
2. 执行已批准的安装命令。检查新服务 Manual / LocalService、路径、回环监听 3307；读取 `package-verification.json`。失败即停，不重复 `-Apply`。
3. 使用 `Get-Acl` 检查每个新目录与 `logs/mysql84.err`、diagnostics 文件的实际权限。logs 只允许开发者/Administrators/SYSTEM/LocalService；secrets 和 diagnostics 不允许 LocalService。检查文件没有额外显式 Allow，不能仅凭目录模板推断实际文件权限。
4. 安装成功且数据库初始化范围获批后，执行 `Initialize-Database.ps1 -Apply`、`Test-MySql84.ps1 -Stage Bootstrap`，验证实际版本/3307/独立 data/两个库/四个账号与初始权限。
5. 新服务主动启停、旧实例 SQL 和后续 Flyway/MyBatis 等工作按各自授权另行安排。任何 BLOCKED/NOT_RUN 都保留，不改成 PASS；本轮未连接旧端口。

`Test-MySql84.ps1` 只读取服务、实例、账号和授权信息，不创建或修改任何表。退出码：0 表示本脚本已执行检查通过；1 表示 FAIL；2 表示存在 BLOCKED。输出中的 NOT_RUN 项即使退出码为 0 也仍未完成，不能当作整体验收通过。

原实例凭据不用发送到聊天。最简单的人工 SQL 验收是使用原客户端的本机密码提示：

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --no-defaults --no-login-paths --protocol=TCP --host=127.0.0.1 --port=3306 --user=YOUR_EXISTING_USER --password --execute='SELECT VERSION(), @@port, @@datadir, 1;'
```

将 `YOUR_EXISTING_USER` 换为自己已有账号；密码仅在本机提示中输入。确认 8.0.44 / 3306 / 原 data 路径 / 1，并只记录脱敏结果。脚本自动验收也支持 `-LegacyClientConfig` 指向**已有**的仓库外受限客户端配置；不要为此把旧密码写进仓库。

安装后，经已批准的新服务启停验证，在下面各阶段重复原实例只读 SQL：新服务运行时、新服务停止后、新服务再次启动后。此流程不会停止 MySQL80：

```powershell
Stop-Service -Name MySQL84CampusHub
# 在本机验证 MySQL80 的只读 SQL
Start-Service -Name MySQL84CampusHub
# 再次验证 MySQL80，执行 Test-MySql84.ps1
```

没有原账号凭据时标记“旧实例 SQL 验证待完成”；服务 Running、端口或握手不能替代 SQL 通过。

## 故障排查

子进程失败的结构化文件位于 `E:\CampusHubRuntime\mysql84\diagnostics\failure-<随机ID>.json`。写入前实际读取目录 ACL，确认继承隔离且只有开发者、Administrators、SYSTEM；检查失败不降级写入其他目录。普通测试使用 `.local/tests`，只写同样的脱敏元数据。M1.0-B 的预期访问拒绝也会产生该类文件；应结合专项验收报告判断，不能把这些预期拒绝误判为初始化失败。

记录字段为阶段（Version / Initialize / RegisterService / DatabaseSql）、命令类别、退出码、启动失败的 Windows 数值错误码、是否有 stdout/stderr、已识别的 MySQL 错误码和固定错误分类。任意原文、参数、SQL、密码、账号细节和 Token 均不保存。未知输出记 `UNCLASSIFIED_OUTPUT_WITHHELD`；不能把这当作已经定位了根因。初始化尚未创建 mysql84.err 就失败、完全无输出、进程无法启动，同样能留下阶段和失败证据。

诊断写入失败时，控制台仍报告原阶段、类别、退出码和 `DiagnosticWriteFailed`，以异常结束。原始 stdout/stderr 只在内存分类，不提供“另存原始输出”后门。若需更详细信息，安装获批后仅在本机检查受限 mysql84.err 或系统加载错误，人工提取脱敏信息；不要复制完整日志。失败现场始终保留，不自动删除文件、服务或清空数据。

| 现象 | 安全处理 |
|---|---|
| 3307 或服务名冲突 | 读取监听 PID、服务 PathName，停止安装并报告；不停止未知进程、不接管旧服务 |
| runtime 存在、初始化失败 | 保留目录与受限日志，查看失败步骤；不清空目录重试 |
| 服务无法读写目录 | 对明确的新路径使用 Get-Acl；对照权限矩阵、LocalService 身份和父目录遍历权限；修改前重新审批 |
| 缺少 Visual C++ 运行库或启动 DLL | 确认官方系统要求；单独说明所需运行库和系统影响后再安装，不内嵌自动安装 |
| 认证失败 | 检查连接端口、CURRENT_USER、对应 .cnf 和 ACL；不打印密码、不重置旧 root、不启用 skip-grant-tables |
| 子进程失败 | 查看脱敏 JSON 的 Stage / ExitCode / NativeErrorCode / Findings；需要更多信息时仅本机查看受限 mysql84.err |
| DiagnosticWriteFailed | 主操作仍是失败；检查明确诊断目录的存在性、ACL、磁盘空间，不切换到普通日志或继续安装 |
| GPG/MD5/SHA-256 不符或 GPG 不可用 | 保留文件，检查官网、公钥指纹及工具路径；不跳过、不用改 hash 的方式消除失败 |
| PowerShell 策略拒绝脚本 | 先查 MachinePolicy / UserPolicy；无强制冲突且获得当前授权时，受信任 PowerShell 7 可使用仅进程级 RemoteSigned；不改永久策略、不用 Bypass / Unrestricted |
| 运行中 mysql84.err 读取出现 IOException | 只读 FileStream 使用 FileShare.ReadWrite 与服务写日志共存；仅输出白名单错误码，不停止服务、改日志内容或 ACL |
| JDK 错误 | 后端脚本显式使用 D:\develop\jdk；不修改全局配置解决 |
| Flyway checksum/版本失败 | 保留历史和迁移文件，分析差异；禁止为求通过而自动 repair/clean/跳过测试 |

静态检查不能验证 DLL、服务启动时序、NTFS 实际继承或真实数据库授权行为；这些必须在批准安装后逐项实测。
