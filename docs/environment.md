# 开发环境与依赖基线

> G02 公开版本：历史 Windows 用户目录已替换为通用示例；原始文件仅本地保留，示例路径不能直接用于本机操作。

基础环境检查日期：2026-10-18；2026-10-19 M1.0-C 已实际验证 JDK 21.0.2、Maven Wrapper 3.9.16、全部后端依赖、启动及真实 MySQL 集成。当前后端结果见 [M1.0-C 验收](verification/m1.0-c-backend.md)，安装历史见 [安装验收](verification/m1.0-a-install.md)。

## 本机实测

| 项目 | 结果 |
|---|---|
| 工作目录 | `E:\Project\CampusHub`；已初始化 Git，分支 `setup/mysql84-preparation` |
| Windows | Windows 11 家庭版 x64，10.0.26200；当前进程不是管理员 |
| 原服务 | `MySQL80`，Running / Auto / `NT AUTHORITY\NetworkService` |
| 原端口 | 3306、33060 正在监听；协议握手版本为 8.0.44，尚未做账号认证 SQL 验收 |
| 原配置 | `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini` |
| 原数据 | `C:\ProgramData\MySQL\MySQL Server 8.0\Data` |
| 新服务、目录 | `MySQL84CampusHub` Running / Manual / LocalService；独立 runtime 为 `E:\CampusHubRuntime\mysql84` |
| 新端口 | 唯一监听 `127.0.0.1:3307`；程序及 TCP 握手版本均为 8.4.11 |
| 项目 JDK | `D:\develop\jdk`，java / javac 21.0.2 |
| 全局 Java | PATH 解析到 Java 23，JAVA_HOME 指向 Android JBR 17；未改动 |
| Maven | Wrapper 已引入固定 Maven 3.9.16；不要求全局 Maven/PATH |
| PowerShell | 当前 7.6.5；Windows PowerShell 5.1.26100.9711 可做语法解析，但本机策略禁止其执行 `.ps1` |
| 检查工具 | Git 2.54；未安装 PSScriptAnalyzer，本次用内置 AST 解析器和保护条件测试 |
| GPG | Git 附带的 `D:\Git\usr\bin\gpg.exe`，2.4.9；隔离公钥环，使用 MSYS 文件路径；不更改默认钥匙环 |
| 微信开发者工具 / Docker | 在已检查的位置未发现；不将其视为已安装，也不依赖 Docker |

原配置 SHA-256：

```text
1253B6E07E8CF0323D093EE21C50A02FF5B06585B4060C6F18C9C65F91F7263F
```

此次脚本通过的执行环境是 PowerShell 7，路径为：

```text
C:\Users\PUBLIC_USER\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe
```

此路径由本机工具提供，未来可能变化。已验证 Microsoft Authenticode 签名有效。5.1 的所有策略作用域为 Undefined，有效策略为 Restricted；7 的 LocalMachine 为既有 RemoteSigned，MachinePolicy / UserPolicy 均 Undefined。经用户授权，标准 UAC 启动的新 PowerShell 7 进程使用 `-ExecutionPolicy RemoteSigned`；未永久修改任何作用域，安装后复核原策略不变。5.1 没有执行安装流程。

## 已选版本与验证边界

| 组件 | 准确版本 | 当前证据 |
|---|---|---|
| Java | 21；本机补丁 21.0.2 | java / javac 实测；不宣称是最新安全补丁 |
| Spring Boot | 4.1.1 | Maven verify、启动和打包运行通过 |
| MyBatis Starter | 4.1.0 | 真实 MySQL 分类及 Unicode 查询通过 |
| springdoc-openapi | 3.1.1 | dev OpenAPI/UI 和 prod 文档关闭验证通过 |
| Maven Wrapper 分发版 | 3.9.16 | Wrapper 3.3.4，分发 SHA-512 核对并固定 SHA-256 |
| Flyway | 12.4.0 | 两库 migrate/validate 通过，独立 Maven 插件 |
| MySQL Connector/J | 9.7.0 | 真实 MySQL 8.4.11 TLS 连接验证通过 |
| Spring Security | 7.1.1 | 公开 200、默认 401、已认证未知路由 404 验证通过 |
| MySQL Community LTS | 8.4.11 Windows x64 ZIP | 官方 MD5、固定 SHA-256、GPG 验证通过；实际安装、服务运行、TCP 握手及 ACL 验收通过 |

MySQL 包名、官网 MD5、官方指纹和验证后的本地 SHA-256 记录在 `infra/mysql/mysql84.psd1`。SHA-256 并非官网公布的真实性证明；真实性依据是官方文档独立核对完整指纹后的 GPG 签名。实测证据见 [包核验 JSON](verification/mysql-package.json)。安装时重新验证并锁定 ZIP；若文件或版本变化，停止审查，不静默换包或更新 hash 求通过。

选型依据：Java 21 LTS，Spring Boot 4.1 官方支持路线；MyBatis 使用对应 Starter；Spring 生态组件遵循 Boot BOM。M1.0-C 已通过实际依赖解析、构建、启动和真实 MySQL 集成测试；[依赖树](verification/m1.0-c-dependencies.txt) 保留准确版本。后续若出现兼容性问题，先分析，不擅自降级 Boot。

## 官方资料

- [MySQL 8.4 下载](https://dev.mysql.com/downloads/mysql/8.4.html)、[Windows 多实例](https://dev.mysql.com/doc/refman/8.4/en/multiple-windows-services.html)、[服务注册](https://dev.mysql.com/doc/refman/8.4/en/windows-start-service.html)。
- [Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)、[管理依赖](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)、[数据库初始化](https://docs.spring.io/spring-boot/how-to/data-initialization.html)。这些默认入口可能随版本变化，后端实施时要确认页面是 4.1 对应版本。
- [MyBatis 4.1.0 发布说明](https://github.com/mybatis/spring-boot-starter/releases/tag/mybatis-spring-boot-4.1.0)、[springdoc 文档](https://springdoc.org/)。
