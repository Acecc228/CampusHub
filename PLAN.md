# CampusHub 阶段计划

## 当前授权

2026-10-24：用户一次性正式授权首次 commit、创建个人账号 Acecc228 下 Public 仓库 CampusHub、推送 main 与 v1.0.0 标签。此前 G01/G02 的不提交、不推送限制仅为历史阶段范围，本次授权已取代。

许可证已明确选择 MIT，版权 2026 Acecc228；仓库级 Git 作者为 Acecc228 <185771718+Acecc228@users.noreply.github.com>。首次提交信息为 `feat: release CampusHub MVP v1.0.0`。发布过程与最终结果见 [v1.0.0 发布记录](docs/verification/github-v1.0.0-release.md)。

仅执行本次 GitHub 源码交付，沿用 G01/G02 与 C-04；不重跑完整历史检查或业务测试，不改业务、数据库或权限。完成即停，不部署、不正式发布微信小程序、不进入 M2.0。

## GitHub v1.0.0 正式交付（PASS，完成即停）

- [x] 公开仓库 https://github.com/Acecc228/CampusHub 已创建，默认分支 main 已成功推送。
- [x] 首次 root commit：`685066adb0bdaab425717207c93d5a96d709bb5e`，信息 `feat: release CampusHub MVP v1.0.0`，599 文件。
- [x] 已推送 annotated tag v1.0.0，解引用指向上述首次正式发布提交；不移动或重写标签。
- [x] 远程完整树 599 文件与本地发布提交逐路径一致，三端源码、Wrapper、锁文件及必要配置模板齐全；私有原图、凭据和构建产物未推送。
- [x] GitHub 实际页面 README 正常渲染，5 张项目截图 complete 且 naturalWidth > 0；GitHub 许可证 API 识别 MIT。
- [x] 作者与提交者为 Acecc228 <185771718+Acecc228@users.noreply.github.com>，仅仓库级配置；MIT 版权 2026 Acecc228，第三方声明保留。
- [x] 主 Agent 1 个，子 Agent 0 个；没有重跑全量业务测试或历史扫描。首次 staged 格式检查的既有证据空白问题如实保留，非凭据或业务问题。
- [x] 实际远程核验后补充本节、DEV_HANDOFF 与发布记录，以单独文档提交归档并推送；不改发布标签所指的 MVP 提交，不留下未提交发布记录。
- [x] 本次发布完成后停止；无新增发布阻断，C-04 已知风险/NOT_RUN 保留。不部署、不正式微信发布、不进入 M2.0。

## M1.5-G02 最终确认

- [x] 8份公开文件个人路径脱敏、本地备份；冻结安装原件不改，提供标注的公开副本并精确忽略原件。
- [x] 两张个人头像原图继续忽略；两份厂商网页缓存本地保留；截图复用 G01 检查。
- [x] 首次提交逐文件清单、规模、排除项及验证见 [G02](docs/verification/github-final-confirmation.md)。未操作索引。
- [x] 第三方素材和许可证建议已整理，项目许可未确定。
- [x] Git 身份继承全局非 noreply 邮箱，待用户确认；建议 main，未改分支或设置。
- [x] 单主 Agent，无子 Agent；本阶段完成即停。
- [x] 用户已选择 MIT、确认公开作者及 noreply 邮箱，并一次性授权 main 首次提交、公开仓库创建、推送与 v1.0.0 标签。

## M1.5-G01 GitHub 交付预审

- [x] Git 工作树、索引、分支、remote、现有本地快照与敏感信息检查；无提交历史、无 remote，未操作 Git 索引或历史。
- [x] 补齐凭据/数据库/构建/测试缓存忽略规则；两张含个人头像的原始桌面证据仅本地保留，必需源码、Wrapper、锁文件和模板可交付。
- [x] README 功能、真实双端截图、Mermaid 架构与 ER 图、安全/事务、环境运行、验收与六项遗留风险披露。
- [x] 修复 Windows Wrapper 对个人目录的依赖；版本调用及启动器默认预览定向验证，不改业务、依赖、数据库或安全规则。
- [x] 明确项目尚未选择开源许可证；保留第三方 Wrapper 声明及 Apache-2.0 副本，不擅自授权自有代码。
- [x] 定向检查和最终结论见 [GitHub 预审](docs/verification/github-preflight.md)。历史证据中的本机用户名路径为低风险隐私残留，未修改受哈希保护的原验收记录。停止本阶段，首次提交/推送须另获授权。

## M1.4-C 分任务验收

- [x] **C-01 PASS**：Vue创建/编辑/发布 → 微信搜索/报名/我的报名 → Vue名单 → 微信取消/重报 → Vue刷新；下架公开访问和新增报名限制、重发布历史及名额保持。全程真实UI和MySQL，活动470/报名197保留。
- [x] C-01缺陷修复：明确4xx拒绝且状态回读失败时恢复现有错误页面；网络/5xx防重提与身份epoch保护保留。新增3项回归，RED→GREEN；小程序23测试、真实微信修复复验、安全补充及最终SQL PASS。
- [x] C-01脚本默认预览/7项保护、PS7/5.1解析、格式、只读子Agent审查、证据与交接完成。2个Agent（主执行+1只读审查），没有并行写数据库。
- [x] **C-02 PASS**：Session/CSRF/RBAC、活动权限/状态机、真实MySQL独立连接并发/回滚/锁超时、跨活动首次报名与C-01定向回归；全库不一致/超额/重复/孤儿均0，原probe和C-01夹具不变。1个Luna只读子Agent，无生产代码/权限/迁移修改。
- [x] **C-03 完成（剩余风险已记录）**：两端依赖审计；Vue ESLint；小程序 JS/JSON 静态解析及官方 WXML/WXSS 编译；两端类型、单测、构建、重复构建及锁文件重装通过。自动化开发链保留 5 moderate，生产依赖 0，不执行破坏性降级。
- [x] **C-04 PASS**：后端70、Vue12/浏览器8、小程序23/真实HTTP18及官方编译通过；真实双端UI/SQL、360/390/430模拟器、真实断网、31分钟闲置及运行交付完成。开发依赖5 moderate和原生长超时/真机等NOT_RUN已披露；完成即停。证据：[C-04验收](docs/verification/m1.4-c-04.md)。

## M1.4-B 原生小程序（核心业务 PASS，部分扩展验证 NOT_RUN）

- [x] TypeScript / WXML / WXSS，首页、广场、详情、学生登录、我的报名、个人中心和原生四项导航。
- [x] Frontend Design Premium / upstream 实际应用，独立移动端 tokens、日期票签、统一组件与状态，Premium strict audit 0 errors / warnings。
- [x] 显式 Cookie / Session / CSRF，401私有数据清理、403边界、学生身份由服务端验证；本地模拟器平台限制。
- [x] 真实搜索/分类/分页、报名/取消/重新报名；去重、version冲突、未知结果回读及身份epoch隔离。
- [x] 20单测、18真实API用例、4特殊字符真实HTTP及4微信搜索、60后端回归；TS构建及官方8 WXML/10 WXSS编译通过。
- [x] 微信官方工具安装、测试号及真实wx.request验证；390px原生主流程9项通过，六页截图已保存。
- [x] MySQL最终人数一致/重复0、原probe保留，开发库仍0活动/0报名/3用户；只写test，无后端兼容改动。
- [x] [运行说明](docs/miniprogram-development.md)、设计/行为规范、验收和交接更新。
- [x] B阶段遗留多尺寸、原生断网/恢复：C-04已补齐360/390/430六页及实际wx.request离线恢复，见C-04报告。
- [ ] 真机、正式微信登录、发布、屏幕阅读器：NOT_RUN，前3项不在本阶段范围。默认Session完整31分钟闲置已于C-04真实浏览器补验PASS。

已知开发工具限制：miniprogram-automator旧依赖链5个moderate审计告警，0 high/critical，不进入小程序运行包；原生取消确认需实际界面点击，SDK确认接口未作为可靠自动化依赖。

## M1.4-A Vue 3 管理后台（PASS）

- [x] Vue 3 / TypeScript / Vite / Element Plus / Vue Router，统一 HTTP、DTO、Session/CSRF/RBAC 接入。
- [x] 登录、Dashboard、统一布局、活动筛选与分页、创建/编辑/详情、发布/下架/重发布、报名人数与名单分页、403/404。
- [x] Frontend Design Premium 实际核验与应用，DESIGN.md / UX-CONTRACT.md / CSS tokens；静态 audit 与设计 lint 无错误/警告。
- [x] 表单校验、未保存保护、401恢复、403/409、真实断网、慢请求、防重复提交及乱序保护。
- [x] 12前端单元、8真实浏览器E2E、60后端回归全部通过；类型、格式、构建和真实MySQL回读通过。
- [x] 1440/1280/900px 实际浏览器视觉验收，发现问题后修复并复查；独立审查3项问题已RED→GREEN。
- [x] 新脚本默认预览/占用端口保护、4个PS文件双版本解析通过；测试仅写test，开发库0活动/0报名/3用户，原probe保留。
- [x] [运行说明](docs/admin-web-development.md)、[验收证据](docs/verification/m1.4-a-admin.md)、交接更新。无推送或部署。

未执行范围：ESLint未配置；Firefox/Safari、真机触控、屏幕阅读器、完整30分钟闲置等待未运行，详见验收报告。

## M1.3 报名与取消（PASS，2026-10-21 完成交接）

- [x] 真实 HTTP 失败测试：新报名接口 404；原 41 项测试通过。
- [x] V4 两库迁移、人数 CHECK、报名唯一约束；新表最小授权前后精确比对。
- [x] 报名/取消/重新报名、个人状态与分页记录、授权报名名单、活动详情人数。
- [x] 活动行锁统一串行化；报名版本防旧请求跨生命周期；编辑容量保护、保留状态机。
- [x] 60 Maven tests，0 fail/error/skip（19 报名、15 活动及原认证/基础回归）；独立连接与真实 HTTP 并发，真实 FK/CHECK 异常回滚。
- [x] 独立只读审查：跨活动首次报名 RR gap lock 死锁已由受控测试复现，报名事务改 RC 后全量转绿，活动状态机不变。
- [x] 数据库专项11 PASS、保护25 PASS、25个PS文件双版本解析0错误、打包dev/prod PASS。
- [x] 两 app 新表最小授权前后核验、真实 SELECT 与 DELETE/ALTER/跨库拒绝通过；activity 权限不变。
- [x] 权限文档、面试笔记、最终证据及交接更新；开发库无业务写入，测试夹具保留，计数一致/重复组合0；无提交、推送或部署。

证据：[M1.3 验收](docs/verification/m1.3-registrations.md)。后续M1.4-A结果见上方。

## M1.2 活动管理（PASS）

- [x] V3 活动表、外键、索引、状态/容量/时间约束；两库显式 migrate/validate，V1/V2 与 probe 不变。
- [x] 创建、全量编辑、发布、下架、重新发布、管理详情、已发布详情、分页/分类/关键词/状态筛选。
- [x] 后端资源归属校验；学生无管理权限，组织者只管理本人，管理员可管理全部；非公开活动浏览详情 404。
- [x] 已发布内容冻结、发布重验、严格 JSON 类型/长度/时间/容量校验；UTC JDBC 4.2 日期处理。
- [x] version + 状态 + 所属权限条件更新；状态变更先锁行再检查截止时间；并发竞争、旧版本与 ABA 测试通过。
- [x] 41 Maven 测试 PASS（2 单元、39 真实 MySQL/HTTP 集成，其中 15 个活动用例）；0 fail/error/skip，原 M1.1 回归保留。
- [x] 数据库专项 9 PASS，保护条件 23 PASS；24 个 PowerShell 文件在 7.6.5 / 5.1 解析 PASS；打包 dev/prod 验收 PASS。
- [x] 独立只读审查；类型强制转换、锁等待跨截止、历史日期偏移均已复现并修复，回归通过。
- [x] 开发 app 仅新增活动表 SELECT/INSERT/UPDATE；测试 app 增加 DELETE 清理本次夹具。两库活动记录及 M1.2 临时用户均为 0，既有数据保留。
- [x] 文档与证据更新；未提交、推送或部署。

## M1.1 三角色认证与权限（PASS）

- [x] Flyway V2 用户/角色/关系模型；两库 migrate/validate，保留 V1 与原 probe。
- [x] Spring Security 7.1.1 数据库账号密码认证、Session、CSRF、身份查询、30 分钟空闲过期、退出销毁和会话轮换。
- [x] 学生/组织者/管理员独立权限矩阵；真实后端检查，客户端 userId/role 不作为认证依据。
- [x] 演示认证默认关闭；显式开关 + 仅 dev/test + loopback，生产/混合/未知 profile 误启直接拒绝启动。
- [x] 独立演示账号配置，仓库外随机凭据、数据库 PBKDF2 哈希、可重复核验，冲突失败；运行账号无 DDL。
- [x] 26 项 Maven 测试通过（2 单元、24 真实 MySQL/HTTP 集成），零失败/错误/跳过；M1.0-C 原有测试全部保留通过。
- [x] 数据库专项 9 PASS；脚本保护 21 PASS；23 个 PowerShell 文件在 7.6.5 / 5.1 均静态解析通过。
- [x] 打包 JAR dev/prod 验收，开发库三角色登录、九格权限、退出与匿名身份验证通过；临时应用进程均已停止。
- [x] 独立审查、匿名退出回归修复、演示配置冲突保护收紧、证据及交接更新。

已知边界：角色是登录时快照，改角色后重新登录；正式 wx.login、实时撤权、限流、分布式 Session 均未实施，不作为当前完成能力。

## M1.0-C 最小后端

- [x] 官方资料、Maven 实际解析确认兼容；Java 21 / Boot 4.1.1 / MyBatis 4.1.0 / springdoc 3.1.1 / Maven 3.9.16，无降级或 Spring 核心版本覆盖。
- [x] Wrapper 固定分发校验和及本机 JDK，Controller/Service/Mapper 分层与真实分类接口。
- [x] 先核实实际结构，测试库显式 baseline 0、两库 V1 migrate/validate；保留原 probe；迁移副本 checksum 不匹配被拒绝。
- [x] 两 app 表级最小授权，Flyway 仅迁移 Maven 插件进程；应用 JAR 无 Flyway。
- [x] 公开分类与 health，其他匿名请求 401；开发文档可访问，生产文档经已认证测试确认 404。
- [x] 真实 MySQL 回读、MyBatis、Unicode、事务回滚、权限和实例边界验证；错误响应及日志脱敏测试。
- [x] Maven verify 与可执行 JAR dev/prod 启动验证，保护测试和 PowerShell 5.1 静态解析分开记录。
- [x] 独立只读审查；修复继承 SPRINGDOC 环境覆盖的条件风险并验证。
- [x] 最终证据归档与交接复核完成；M1.0-C 完成即停。

操作入口见 [backend-development.md](docs/backend-development.md)，详细证据见 [M1.0-C 报告](docs/verification/m1.0-c-backend.md)。

## M1.0-B 数据库及账号初始化（已完成）

- [x] 复核服务、3307、独立路径、ACL 和 MySQL80 基线；未重新安装或修改旧实例。
- [x] 审阅初始化及验收脚本和 SQL；非敏感夹具复现日志共享 IOException，改用只读 FileStream + FileShare.ReadWrite 后通过。
- [x] 轮换新实例引导 root 密码，实际认证确认版本/端口/数据目录/账号，创建 campushub / campushub_test 和四个独立应用/迁移账号；旧 root 密码实际认证被拒绝。
- [x] 两个 migrator 各自仅持有对应库的八项既定权限；两个 app 保留 USAGE。未执行业务表授权、Flyway 或后端开发。
- [x] 两库字符集、精确权限、跨库拒绝、五组当前凭据的 TLS、仓库外凭据 ACL 和独立随机密码通过验收；MySQL80 基线不变。
- [x] 真实测试库完成中文、emoji、补充平面字符及组合字符提交后跨连接读回、UTF-8 字节比对和事务回滚。仅保留 campushub_test.m10b_unicode_probe 及一条证据记录；开发库无表。
- [x] 独立源码审查无 Critical / Important 问题；环境失败报告的一项轻微问题已按先失败后通过的测试修复。

详细证据见 [M1.0-B 验收报告](docs/verification/m1.0-b-bootstrap.md)。真实数据库专项 13 PASS / 0 FAIL；只读账号验收 6 PASS / 1 BLOCKED / 2 NOT_RUN，其中 BLOCKED 是未提供凭据的旧实例 SQL，不属于本轮新实例初始化失败。

## M1.0-A 安装前核验与安装验收（历史）

- [x] 指定 ZIP、MD5、SHA-256、GPG 和官方公钥指纹重新验证通过。
- [x] MySQL80 Running，原配置指纹一致，3306/33060 监听正常。
- [x] 新服务/runtime 不存在，3307 空闲且不在排除范围内。
- [x] 重新读取安装器、两个模块及配置；未修改执行文件，保存 6 个文件 SHA-256 供手动运行前比对。
- [x] PowerShell 7.6.5 的 Microsoft 签名有效；当前进程没有管理员权限。
- [x] 通过用户亲自确认的标准 UAC 执行完整交接启动器，安装一次成功。
- [x] 安装后逐项只读验收：16 PASS、0 FAIL、0 BLOCKED、4 NOT_RUN；不自动进入账号初始化。
- [x] 保留首次日志读取共享模式错误的报告；仅调整只读读取方式后复验通过，未修改日志、ACL 或冻结脚本。

最终证据与准确命令：docs/verification/mysql84-install-acceptance-final.json、docs/verification/m1.0-a-install.md。install-preflight.json 继续作为不可修改的安装前哈希基线；历史交接见 docs/mysql84-install-handoff.md。

## M1.0-A-Fix

- [x] 从官方下载 ZIP、签名、公钥，并将指纹与独立的官方文档核对；初次 GPG 验证通过。
- [x] 固定 SHA-256、强制签名校验，安装时复验并持有只读锁。
- [x] 保存结构化脱敏诊断；不保存原始输出，不新增本轮 ACL 操作。
- [x] 原 25 个测试与新增 24 个测试全部通过；12 文件在 PowerShell 7/5.1 解析无错误。
- [x] 完成最终独立只读审查；Fix 交付时管理员安装尚未授权（后续授权见本文件顶部）。

本轮决定：按用户最新明确路径使用 E:\Project\CampusHub.local\packages。Git 附带 GPG 是 MSYS 版本，文件参数转换为 /e/...；MySQL 本身仍使用 Windows 原生路径。诊断采用白名单分类和错误码，不保留任意原文，避免依靠不完整的密码正则脱敏。未知错误明确标记为未分类，绝不伪装成功。

## M1.0-A 执行清单

- [x] 初始化当前空目录为 Git 仓库，建立基础目录。
- [x] 准备仅针对新实例的安装脚本、配置模板和 SQL。
- [x] 分离迁移、开发运行、测试运行账号及凭据（脚本层面，尚未创建账号）。
- [x] 准备只读环境检查与安装后验收脚本。
- [x] 执行语法、危险命令、默认预览和路径/实例保护检查。
- [x] 独立审查、修复并更新验证报告与操作说明。

## 执行决定

- 在用户明确指定的全新目录 E:\Project\CampusHub 初始化专用分支 setup/mysql84-preparation；不存在需隔离的既有代码，不另建 worktree。
- 原 M1.0-A 准备阶段仅生成材料；Fix 阶段新增官方下载，未执行安装。后续安装阶段另获授权并已完成，见顶部状态。
- 安装器只接受完整的新 runtime，已有目录/服务/端口冲突时终止；失败恢复由人工审查，不做自动删除。
- 最小应用仅需分类表 SELECT；测试表 DML 只授予独立测试账号。
- 原计划“应用启动自动迁移”由用户补充要求替换为“显式迁移步骤”；未来业务进程禁用 Flyway 自动迁移且不持有迁移密码。
- 本轮最新授权明确其余请求默认认证：未知匿名路径同样返回 401；已认证未知路径返回 404。403 处理已预留，三角色业务权限验证留待后续。

## 后续步骤及验收

| 阶段 | 目标与验收 | 外部依赖 | 当前状态 |
|---|---|---|---|
| 安装审批 | 前置检查通过，标准 UAC 由用户亲自确认 | 已完成 | PASS |
| MySQL 系统初始化与服务安装 | 独立 3307/数据目录、低权限手动服务；MySQL80 基线不变 | 已实际安装和验收 | PASS（不含认证 SQL） |
| M1.0-B 数据库及账号初始化 | 两库、四个独立账号、最小授权、认证后实例及权限验证 | 已获授权并完成 | PASS；旧实例 SQL 另列 BLOCKED |
| M1.0-C 最小后端 | 显式迁移、真实分类 API、安全边界、文档隔离、MyBatis、Unicode 和事务回滚 | 本轮已获授权 | PASS；证据见独立报告 |
| M1.0-C 工程验收 | Maven verify、依赖树、打包运行、权限与迁移测试真实记录 | 后端实现、数据库 | PASS |
| M1.1 认证与权限 | 三角色认证、身份查询、Session/CSRF、401/403、防冒充、生产误启阻断 | 已完成 | PASS；见 M1.1 验收 |
| M1.2 活动管理 | 活动生命周期、资源级权限、分页查询、V3 与安全回归 | 已完成 | PASS；见 M1.2 验收 |
| M1.3 报名与取消 | 唯一报名、容量、截止、取消事务及并发验证 | 已完成 | PASS；60测试，见 M1.3 验收 |
| M1.4-A 管理后台 | Vue 3、真实认证/活动/名单闭环、设计系统和多尺寸浏览器验收 | 已完成 | PASS |
| M1.4-B 微信小程序 | 六页学生端、原生认证与报名闭环 | 已授权实施，真实核心业务通过；工具限制见验收 | PASS（扩展验证部分NOT_RUN） |
| M0 文档补齐 | PRD、模块优先级、ER、架构、API、页面/UI、里程碑及风险 | 业务规则细化 | NOT_RUN |
| M1.4-C-01 跨端闭环 | Vue创建发布、微信报名取消重报、Vue名单、下架重发布 | 真实UI与MySQL已完成 | PASS |
| M1.4-C-02 安全与一致性 | Session/CSRF/RBAC、状态机、独立连接并发与回滚 | 已完成 | PASS |
| M1.4-C-03 工程质量与依赖安全 | Vue ESLint、两端审计及可重复构建 | 本轮正式授权并完成 | 检查 PASS；5 项开发依赖 moderate 保留并记录；最终MVP结果见 C-04 |

| M1.4-C-04 MVP最终验收与交付 | 全量回归、真实双端、视觉/网络/Session、可复现运行 | 已完成并停止 | PASS；开发依赖5 moderate及NOT_RUN详见报告 |

每阶段完成后报告真实结果并等待验收，不自动推进预约、签到、AI 或正式部署。

## 本次验证记录

最新证据见 docs/verification/m1.4-c-04.md。此前报告和失败记录保留为历史，不覆盖既有证据，不重跑初始化或 M1.0-B 写入验收。
