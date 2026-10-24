# 后端开发与认证操作

当前后端基线（延续M1.3，C-04复核）：Java 21 / Boot 4.1.1 / Security 7.1.1 / MyBatis 4.1.0 / springdoc 3.1.1 / Maven 3.9.16，真实 MySQL 8.4.11。构建与运行使用普通 PowerShell 7；若数据库服务停止，启动既有服务可能需要标准UAC。不要重跑安装器、初始化器或 baseline。当前阶段见 [C-04验收](verification/m1.4-c-04.md)。

## 构建、运行

```powershell
Set-Location 'E:\Project\CampusHub'
# 不带 -Apply 的项目操作脚本只预览。
& 'E:\Project\CampusHub\scripts\Invoke-Backend.ps1'
# 使用真实 campushub_test 完整测试并打包；Package 同样执行 verify。
& 'E:\Project\CampusHub\scripts\Invoke-Backend.ps1' -Action Verify -Apply
# 本机开发认证需要显式开关；不带开关默认关闭。
& 'E:\Project\CampusHub\scripts\Invoke-Backend.ps1' -Action Run -Profile dev -EnableDemoLogin -Apply
```

默认监听 127.0.0.1:8080，可通过 `-Port 8081` 选其他端口，不停止其他进程。只使用 campushub_app，未注册应用服务。prod/混合/未知 profile/非 loopback 误启演示认证均启动失败。`-Profile prod` 关闭文档，生产 Cookie 标记 Secure。

## 登录与退出

账号为 `demo_student` / `demo_organizer` / `demo_admin`。随机密码原文只存放在 `E:\CampusHubRuntime\mysql84\secrets\campushub-demo.json`，测试库对应 `campushub_test-demo.json`，两库密码不同。数据库保存 PBKDF2-HMAC-SHA256 哈希（16 字节盐、600000 次迭代、256 位输出）。不要打印密码文件、在命令参数写密码或把 Cookie/CSRF token 放入 Git。

以下在已启动开发服务后执行，仅输出当前身份；更换 `$loginName` 可验证另两个角色：

```powershell
$base='http://127.0.0.1:8080'
$loginName='demo_student'
$localAccounts=[IO.File]::ReadAllText('E:\CampusHubRuntime\mysql84\secrets\campushub-demo.json') | ConvertFrom-Json
$account=$localAccounts.Accounts | Where-Object LoginName -CEQ $loginName
$csrf=Invoke-RestMethod ($base+'/api/v1/auth/csrf') -SessionVariable session
$null=Invoke-RestMethod ($base+'/api/v1/auth/demo-login') -Method Post -WebSession $session -Headers @{'X-CSRF-TOKEN'=$csrf.token} -Body @{username=$account.LoginName;password=$account.Password}
Invoke-RestMethod ($base+'/api/v1/auth/me') -WebSession $session
# 登录会轮换 Session 并清除原 CSRF，退出前重新获取。
$csrf=Invoke-RestMethod ($base+'/api/v1/auth/csrf') -WebSession $session
$null=Invoke-RestMethod ($base+'/api/v1/auth/logout') -Method Post -WebSession $session -Headers @{'X-CSRF-TOKEN'=$csrf.token}
$account=$null; $localAccounts=$null; $csrf=$null; $session=$null
```

登录为 `application/x-www-form-urlencoded` 的 username/password；所有写请求保留 CSRF 防护。Session 30 分钟空闲过期，退出或重启失效。角色为登录时快照，角色变更后需重新登录。

| 接口 | 权限 |
|---|---|
| GET `/api/v1/activity-categories`、`/actuator/health`、`/api/v1/auth/csrf` | 公开 |
| POST `/api/v1/auth/demo-login` | 仅显式启用的本机 dev/test，需 CSRF |
| GET `/api/v1/auth/me`、POST `/api/v1/auth/logout` | 已认证；退出需新 CSRF |
| GET `/api/v1/student/access` | STUDENT |
| GET `/api/v1/organizer/access` | ORGANIZER |
| GET `/api/v1/admin/access` | ADMIN |
| GET `/v3/api-docs`、`/swagger-ui/index.html` | 仅 dev 公开；prod 无映射 |

无隐式角色继承；客户端 role/userId 参数不能改变身份。未登录 401、已登录权限不足 403、已认证未知路径 404。access 接口仅验证权限，活动业务通过下面的独立接口提供。

## M1.2 活动 API

所有活动接口需要 Session；所有写请求使用 JSON 并携带登录后重新获取的 CSRF header。沿用已有直接 JSON 成功响应，错误为 `{code,message}`。

| 接口（前缀 /api/v1） | 权限与请求 |
|---|---|
| GET `/activities`、`/activities/{id}` | 三角色可查询已发布活动；草稿/下架/不存在详情 404 |
| GET `/manage/activities`、`/manage/activities/{id}` | 组织者只管理本人，管理员全部；越权 403 |
| POST `/manage/activities` | 组织者/管理员创建草稿，成功 201 |
| PUT `/manage/activities/{id}` | 全量业务字段和当前 version；已发布不可编辑 |
| POST `/manage/activities/{id}/publish`、`/unpublish` | 请求 `{ "version": 0 }`，使用实际当前版本 |

创建字段：title（1..120）、summary（1..500）、content（1..20000）、categoryId、location（1..200）、startTime、endTime、registrationStart、registrationEnd、capacity（1..2147483647）。长度按去首尾空白后的 Unicode 码点计；内容按普通文本处理。时间必须是带时区 ISO-8601 字符串，如 `2026-12-01T10:00:00+08:00`，只允许整秒，返回 UTC；四个时间必须满足报名开始 < 报名结束 <= 活动开始 < 活动结束。

创建者、初始状态和版本由服务端决定。创建响应包含 id/version，后续更新带响应中的最新 version；成功 version+1。草稿与下架可编辑，发布须报名尚未截止且活动未开始；下架后符合条件可重新发布。管理员也受状态及版本约束。参数错误 400；状态/过期版本冲突 409，应刷新后重试。

列表支持 `page=1&size=20&categoryId=1&keyword=讲座`，page 最大 10000、size 最大 100；结果 `{items,page,size,total}` 按 id 降序，items 不含长内容。管理列表可筛选 DRAFT/PUBLISHED/UNPUBLISHED；浏览只允许 PUBLISHED。关键词按标题或简介的字面包含匹配。

## M1.3 报名 API

均要求现有 Session，POST 还要求 CSRF；不接收用于身份判断的 userId。写请求 JSON `{ "version": 0 }` 使用 GET 返回的最新报名版本，和活动编辑 version 是两个独立版本。

| 接口（前缀 /api/v1） | 权限与响应 |
|---|---|
| POST `/activities/{id}/registrations` | STUDENT 报名/重报，200 返回本人报名记录 |
| POST `/activities/{id}/registrations/cancel` | STUDENT 取消，200 返回本人当前状态 |
| GET `/activities/{id}/registration` | STUDENT 本人状态；无记录返回 NONE/version=0，非公开活动仅已有报名者可查自己的记录 |
| GET `/student/registrations?page=1&size=20` | STUDENT 本人记录，含已取消记录 |
| GET `/manage/activities/{id}/registrations?page=1&size=20` | ORGANIZER 仅本人活动，ADMIN 全部；包含报名状态 |

分页上限与活动列表相同。记录包含 id、activityId、userId、status、version、createdAt、updatedAt；不返回登录名、密码、联系方式或活动非公开内容。列表按报名 id 降序，重报复用原记录，不改变列表中的 ID 排序。

报名要求 PUBLISHED 且 registrationStart <= now < registrationEnd。取消要求 now < startTime，下架期间也允许取消；无记录取消是无副作用的 NONE。CONFIRMED/重复报名、CANCELLED/重复取消不改人数，版本不能超前；需要切换状态时必须匹配当前版本。正常生命周期 0→1→2→3，旧 version=1 的取消不能取消 version=3 的重新报名。

已发布详情增加 registeredCount、remainingCapacity（capacity 减人数）和 myRegistration。管理详情/列表也增加人数。未公开详情仍按 M1.2 返回 404。参数错误 400，角色/归属拒绝 403；名额满、未开始、截止、活动不可报名、报名版本冲突均 409（未公开且无本人记录时 404）。

报名事务为 READ_COMMITTED，先锁活动再锁报名；明细与人数一起提交或回滚。详情/分页保持默认只读事务快照。编辑容量不得小于人数；下架/重发布不重置人数。详见 [设计](m1.3-design.md) 和 [面试笔记](interview-notes.md)。

## 账号配置与数据库边界

两库认证模型和演示账号已配置。`Set-DemoAccounts.ps1 -Database campushub -Apply` 可重复核验，不覆盖密码。角色/账号/凭据冲突立即失败，数据库失败后不删除文件或自动重置。首次创建使用独立 migrator，固定表级授权由 `Grant-AuthenticationAccess.ps1 -Apply` 完成。

| 执行方式 | 凭据 / 权限 |
|---|---|
| 日常应用 | app.cnf → CH_DB_PASSWORD；分类和三个认证表仅 SELECT；activity、activity_registration SELECT/INSERT/UPDATE |
| 测试应用 | test-app.cnf → CH_TEST_DB_PASSWORD；测试分类、用户、关系、activity 表 DML；角色表 SELECT；activity_registration SELECT/INSERT/UPDATE（无 DELETE） |
| 开发/测试迁移 | 各自 migrator.cnf，独立 Maven 进程；仅对应库既定八项权限，无 DROP |
| 表级授权 | 既有 root 客户端；不把 root/迁移密码传入业务进程 |

启动器清除继承的 CH/FLYWAY/CAMPUSHUB/SERVER/SPRING/SPRINGDOC/MySQL/JVM 覆盖变量。测试额外读取测试演示密码，仅注入测试进程。应用 JAR 不含 Flyway 或迁移 SQL，自动 SQL 初始化关闭，启动核实实例和账号。

## 显式迁移与复核

两库已到 V4，禁止修改已执行 V1/V2/V3/V4。V1 checksum 1160888031，V2 checksum 436036048，V3 checksum -852546724，V4 checksum 653386617。原 `m10b_unicode_probe` 和证据保留；M1.0-C 的测试库 baseline 0 仅是历史，不可重做。M1.3 已执行 `Grant-RegistrationAccess.ps1 -Apply`，仅授权新表三项权限；完整范围见 [数据库权限文档](database-permissions.md)。

```powershell
& 'E:\Project\CampusHub\scripts\Invoke-BackendMigration.ps1' -Database campushub_test -Operation Validate -Apply
& 'E:\Project\CampusHub\scripts\Invoke-BackendMigration.ps1' -Database campushub -Operation Validate -Apply
& 'E:\Project\CampusHub\scripts\tests\Test-BackendSafety.ps1'
& 'E:\Project\CampusHub\scripts\Test-BackendDatabase.ps1' -Apply
& 'E:\Project\CampusHub\scripts\Test-BackendPackage.ps1' -Apply
```

未来只有批准新迁移后才使用 `-Operation Migrate -Apply`。始终显式 127.0.0.1:3307、库名和对应账号；checksum 错误立即停止，无 repair/clean/baselineOnMigrate。

数据库专项读取精确权限、V1/V2/V3/V4、分类、角色及 probe，验证 checksum 副本和 DELETE/ALTER/跨库访问被拒绝；不写业务数据。打包专项启动并停止自己创建的 dev/prod Java 子进程，验证开发库三角色登录/权限/退出、分类、活动查询、我的报名查询及管理角色权限，不操作 Windows 服务。业务测试仅写 campushub_test，M1.3 随机夹具保留，不自动清理。

Wrapper 3.3.4 / Maven 3.9.16 固定分发校验，入口局部设置 `D:\develop\jdk`，不改变系统 PATH/JAVA_HOME/执行策略。尚未声称跨机器运行已验证。

详细结果：[C-04最终回归](verification/m1.4-c-04.md)、[M1.3历史](verification/m1.3-registrations.md)、[M1.2历史](verification/m1.2-activities.md)、[M1.1历史](verification/m1.1-authentication.md)。脱敏日志位于忽略的 `.local/backend-logs`，本轮验收目录保留脱敏运行日志与统计。只完成已授权C-04，不启动正式部署或M2.0。完整test双端启动和停止方式见 [README](../README.md)。
