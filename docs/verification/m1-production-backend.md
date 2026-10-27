# M1 生产认证后端实现与验证

2026-10-27，分支 `codex/m1-production`。本记录仅覆盖后端子任务；数据库操作与完整集成验收由主 Agent 串行执行并另行记录。本子任务没有连接或写入数据库，也没有运行 MySQL 集成测试、推送、合并或部署。

## 范围

- `POST /api/v1/auth/wechat-login` 仅接收 JSON `code`，字符串类型及长度受限。服务端调用固定 HTTPS `api.weixin.qq.com/sns/jscode2session`，连接 3 秒、整个交换最长 5 秒，响应最大 16 KiB，不跟随重定向，没有应用层重试。上游错误内容、code、secret、openid、session_key 不写日志或响应；session_key 不保存在身份模型、数据库或 Session。
- V8 新增 `app_wechat_identity`，唯一 `(appid,openid)` 与唯一内部 `user_id`。事务先创建/锁定映射，再创建随机内部用户名、无密码的非演示 STUDENT，并绑定映射；异常整体回滚。已有账号必须启用、非演示且角色恰为 STUDENT，否则拒绝，绝不授予管理权限。
- `POST /api/v1/auth/login` 使用现有 PBKDF2 参数，仅接受非演示 ORGANIZER/ADMIN 管理账号。演示认证保持独立过滤器，仅在既有本地 dev/test 策略下启用。
- 微信认证显式轮换 Session ID、清理旧 CSRF 并保存 SecurityContext；表单认证使用 Spring Security 会话与 CSRF 策略。`csrf/me/logout` 响应契约保持。后续请求重新核验数据库启用状态、完整角色列表、演示标记与密码散列指纹，变化后清理 Session 并返回 401；指纹独立于 provider 会擦除的 User.password，仅保存 SHA-256 派生值。数据库不可用时拒绝访问并返回安全 503。
- 单实例进程级每分钟 600 次正式认证，管理用户名每分钟 10 次；固定窗口、受限映射，无转发 IP 头信任。反向代理承担真实客户端 IP 限流，避免把全部代理请求视为同一用户。
- prod 必须为唯一活动 profile、禁演示、有明确生产数据源/数据库/CURRENT_USER 身份及微信凭据。强制 loopback 绑定、安全/HttpOnly/Strict Cookie、仅 Cookie 跟踪、禁止信任转发头及应用内 Flyway。JDBC 只允许明确端口、`sslMode=VERIFY_IDENTITY`，以及可选的 `characterEncoding=UTF-8`、`connectionTimeZone=UTC`；拒绝编码/重复/未知参数及 Hikari 的 JDBC URL、账号密码、数据源类、JNDI、data-source-properties 覆盖。连接核验 `DATABASE()/CURRENT_USER()`。原本地 dev/test MySQL 指纹保护保留。

## 本子任务验证

| 检查 | 结果 | 证据/限制 |
| --- | --- | --- |
| 测试先行 RED：身份/映射/生产配置 | PASS | 新测试先编写，Maven testCompile 因缺少新类/方法失败，随后实现。初轮也发现原 BackendIT 构造器调用需显式传测试 Environment，已修正。 |
| 测试先行 RED：限流/撤销/响应大小 | PASS | 新测试因缺少新过滤器和 LimitedBodySubscriber 失败，随后实现。 |
| 行为 RED：code 必须 JSON 字符串 | PASS | 2026-10-27 09:44:03，4 项定向测试中 1 项失败：Jackson 默认把数字转为字符串。复用现有 StrictStringDeserializer 后通过。 |
| 全部单元测试 GREEN | PASS | `JAVA_HOME=D:\develop\jdk`，`backend/mvnw.cmd -o test`，09:44:26：19 项，0 failure/error/skipped，包括原 2 项异常日志保护。 |
| 密码重置撤销 RED | PASS | 09:46:59：IdentityServiceTest 3 项中 1 失败，旧实现重置后仍返回 true。随后新增独立于已擦除密码的单向指纹与 demo 标记检查。 |
| 密码重置撤销 GREEN | PASS | 09:48:05：全部 20 项单元测试，0 failure/error/skipped。 |
| 生产有效配置保护 RED | PASS | 21:31:56：ProductionAuthPolicyTest 5 项中 3 失败，复现有效 Cookie/绑定/转发/Flyway、弱 TLS/连接参数、Hikari 覆盖缺口。首次运行与改名操作重叠出现旧文件路径编译失败，属执行问题，重新运行后取得上述行为 RED。 |
| 最终后端单元 GREEN | PASS | 21:33:13：`backend/mvnw.cmd -o test`，23 项，0 failure/error/skipped；含有效配置 Binder 检查、百分号编码 TLS 参数、重复/未知 JDBC 参数、Hikari relaxed 名称覆盖与合法池大小配置。 |
| Python 管理账号散列兼容 | PASS | Python hashlib 的固定 salt16 / SHA256 / 600000 / derived32 向量，经 Spring PBKDF2 正确密码匹配、错误密码拒绝。向量仅为公开测试夹具。 |
| 后端定向差异格式 | PASS | `git diff --check -- backend infra/mysql/migrations/V8__create_wechat_identity.sql` 退出 0。 |
| 真实 MySQL / 既有 70 项回归 | NOT_RUN | 本子任务明确不执行 DB 集成，由主 Agent 运行。 |
| 真微信 code2session / 真机 / 生产 TLS / 部署 | NOT_RUN | 缺外部真实凭据和部署环境，未以 fake upstream 冒充实测。 |

## 待主 Agent 集成执行

`ProductionAuthenticationIT` 是真实 HTTP + 真实 MySQL 的**假微信上游契约测试**，不是微信官方链路验收。覆盖六线程首次登录唯一映射/用户、严格 STUDENT、Session 轮换、旧 CSRF 拒绝、logout、管理登录、无客户端角色提升、停用/角色变化后的撤销和安全错误。追加管理登录→密码重置→me 401→新密码登录、失败重登清 Session 与认证/CSRF 输出 no-store 断言。测试夹具保留，不清理映射；迁移 V8 与授权必须由主 Agent 先完成。

主 Agent 首次完整 verify 的 5 项微信假上游/真实 MySQL 契约测试已 PASS；全套仅 DemoEnvironmentIT 因缺失生产微信占位符提前解析而 FAIL。根据实际报告，为 WechatHttpClient 添加对 demoPolicy 的显式初始化依赖，使不安全 demo profile 首先由既有策略拒绝；未弱化生产配置、修改测试预期或添加绕过开关。后续完整结果由主 Agent 记录，本记录不把首轮失败伪报全绿。

原 `ProductionConfigurationIT` 改名为 `FormalDefaultsIT`，方法名同步反映 test profile 的正式默认配置，避免产生已启动真实 prod 环境的误读。只删除该改名留下的一个旧 target 测试 class，避免后续 Maven 误跑过期类；既有原始验收报告保持。

应用新增最小权限：`app_wechat_identity` 的 SELECT/INSERT/UPDATE；`app_user` 的 INSERT；`app_user_role` 的 INSERT。已有查询权限保持，正式运行无需新增 DELETE/DDL 权限。

生产配置键：`spring.datasource.url/username/password`，`campushub.database.production.name/account`，`campushub.wechat.app-id/app-secret`；由主 Agent 的 application-prod.yml 映射 `CH_PROD_DB_*` 与 `CH_WECHAT_*`。生产凭据不进入本记录或测试夹具。
