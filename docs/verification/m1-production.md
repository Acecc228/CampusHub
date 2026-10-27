# M1 生产化本地交付验收

完成日期：2026-10-28（北京时间）。结论：**已授权的本地实现与验证 PASS；正式部署、官方微信交换、真机、审核发布 BLOCKED/NOT_RUN**。用户确认正式微信 AppID/权限、服务器、域名/证书及备案资源均尚未准备。本报告不宣称正式上线或生产验收通过。

## 交付范围

- 服务端固定微信 HTTPS code2session 交换、临时 code 输入校验、超时/响应大小限制、安全错误；AppSecret 仅服务端，session_key 不持久化、不返回前端。
- V8 appid/openid 唯一映射和事务内首次 STUDENT 创建；并发首次登录只产生一组映射/用户。拒绝客户端角色控制和微信账号自动绑定管理账号。
- 非演示组织者/管理员表单登录，可信运维创建/停用/重置密码工具；Session 轮换、CSRF 更新、注销和数据库身份核验。停用、角色变更、密码重置、演示标志变更在后续请求撤销会话；已开始的请求不保证瞬时撤回。
- prod 独占 profile、禁演示、显式独立 DB、CA及主机身份验证、Cookie 安全属性、仅 loopback、忽略转发头、应用禁 Flyway。严格 JDBC 参数白名单拒绝编码/重复 TLS 覆盖和危险 Hikari 覆盖。
- Vue 同源正式登录，原生小程序独立生产构建/HTTPS/AppID检查/wx.login/Session+CSRF。明确401身份回读允许新的一次用户主动登录，网络不确定时不盲目重发旧 code。既有设计和活动业务规则保持。
- Nginx TLS、管理网络限制、限流、脱敏访问日志；systemd 服务、独立账号权限模板、加密备份与空库恢复脚本、可迁移摘要校验、部署/回滚说明。它们是交付材料，未在生产 Linux 运行。

## 测试结果

| 检查 | 状态 | 实际证据与边界 |
|---|---|---|
| 最终 Maven verify / JAR 构建 | PASS | 2026-10-28 一次完整执行：23单元+75集成执行项=98，0失败/错误/跳过；[摘要](m1-production-maven.txt)。私有完整日志 `.local/backend-logs/verify-20261027T232259888.log` |
| 正式认证与安全 | PASS | 7项真实HTTP+真实MySQL的微信**假上游契约**集成；包含6线程首次登录、Session/CSRF、管理账号限制、撤权/密码重置、失败重登、no-store。不是官方微信接口成功证据 |
| 既有业务回归 | PASS | ActivityIT16、RegistrationIT28（含并发重复执行）、AuthenticationIT9、BackendIT11，沿用真实MySQL。活动/报名业务源码和历史V1–V4迁移无差异 |
| 生产配置保护 | PASS | 5项单元涵盖有效配置、编码/重复/未知JDBC参数、Cookie/loopback/Flyway/转发头/Hikari覆盖；DemoEnvironmentIT真实启动拒绝通过；FormalDefaultsIT使用test profile，不冒充生产连接 |
| V8测试迁移/validate | PASS | `Prepare-M1ProductionTest.ps1 -Apply`；仅127.0.0.1:3307/campushub_test。V5–V7精确历史只存在私有测试解析目录；生产交付SQL只有V1–V4,V8 |
| 最终SQL不变量 | PASS | [SQL摘要](m1-production-sql.txt)：418活动、395报名、767用户、9映射；人数不一致/超额/重复/未绑定映射/孤儿映射均0；probe仍一条且内容指纹通过 |
| Vue工程检查 | PASS | npm ci/typecheck/lint/format/build、14单元；[客户端记录](m1-production-clients.md) |
| 微信工程检查 | PASS | npm ci/typecheck/lint、最终29单元（初轮27+2恢复回归）、dev/prod构建；生产参数缺失拒绝。占位生产构建不等于可发布配置 |
| 官方微信静态编译 | PASS | dev/prod各8 WXML、10 WXSS，wcc/wcsc退出0；最终生产包2026-10-28重验。无模拟器/真机登录成功声明 |
| 真实管理UI | PASS | 真实Edge headless、Vue生产构建preview、Spring dev profile连接真实测试库；正式非demo账号POST /auth/login，创建/发布活动845、Cookie HttpOnly/Strict、注销后me401；0浏览器错误。[结果](m1-production-ui/result.json)、1440登录/详情和900详情截图 |
| 运维工具保护 | PASS | 4项Python账户/SQL/散列测试，Java实际PBKDF2兼容向量；1项文件摘要异地迁移+篡改拒绝测试；新PS脚本7/5.1语法、默认预览及Bash语法通过 |
| 真实公网HTTPS/Secure Cookie | BLOCKED | 没有生产服务器、域名/证书；本地HTTP UI只证明HttpOnly/Strict，不冒充Secure传输验收 |
| 全新生产迁移/最小权限实机 | BLOCKED | 没有独立生产实例；本地test-app有既有测试DML权限，不能冒充生产最小权限验证 |
| 全库加密备份恢复演练 | BLOCKED | Linux/age/独立空恢复库及密钥未部署；仅摘要搬迁/篡改保护实际执行，未用它代替全库恢复 |
| 官方微信交换/合法域名/真机 | BLOCKED | 缺正式AppID/权限和公网环境；Node单元、假上游、官方编译均不替代它 |
| 微信审核与发布 | NOT_RUN | 未提交、未发布，仍需主体/隐私资料与单独授权 |

## 失败与修复记录

1. 初轮完整verify：19单元通过，73集成中DemoEnvironmentIT失败，原因是生产微信占位符先于demo策略解析。通过明确bean依赖修复，最终真实启动拒绝通过；未弱化生产保护。
2. 密码重置后旧Session未撤销：先复现单元RED，加入独立于Spring擦除密码字段的单向凭据指纹，单元及真实登录→重置→401集成GREEN。
3. JDBC编码参数可覆盖TLS：独立审查用实际Connector/J解析器复现；有效配置/URL/Hikari测试先RED，再改严格白名单，GREEN。
4. 微信回读明确401仍锁住登录：回归先RED，改为允许用户重新发起全新wx.login；离线仍锁定、回读成功不重复取code，29项GREEN。
5. 备份摘要记录源机绝对路径：改为纯摘要并校验显式选定文件；异地副本通过、篡改拒绝。Windows路径转义导致首轮校验测试失败，改stdin散列后GREEN。完整恢复仍未执行。
6. UI脚本前两次使用ISO分钟字符串，页面要求北京时间秒字符串，表单正常拒绝；修正测试输入。随后发现SPA跳转尚未完成时采集ID为null，及精确URL断言未允许既有returnTo查询；补pathname等待和正整数断言，最终完整PASS，结果活动845。失败夹具保留，没有重试同一报名写操作。
7. 服务恢复需要标准UAC；曾因固定PowerShell路径不存在/会话中断未完成启动，改用实际已验证Microsoft签名的PowerShell路径，用户确认后仅启动MySQL84。MySQL80从未修改；新脚本执行前后比较其既有指纹。

## 审查、分工和剩余风险

主Agent负责架构选择、数据库串行写入、部署工具、集成/回归、真实浏览器和最终验收。实际指定模型：GPT-6.1 Sol实现后端，GPT-6.1 Terra实现双端，另用Astra进行只读安全审查；同时不超过2个子Agent，无递归、无并行写库、无重叠文件修改。审查四项Important均完成上述修复；没有以未部署环境推定代码已获生产验收。

小程序原有开发自动化链5项moderate仍保留；未强制降级依赖。单实例内存Session/限流随重启重置，重启需重新登录。微信身份不是学籍证明。无短信/MFA/自助密码找回；管理端公网入口需按模板配置可信网络限制。Linux实际服务、流量容量、TLS续期、日志轮转、异机完整恢复、微信隐私资料与官方审核仍需真实环境验证。

## Git、清理与下一步

基于main `7ba348d` 的独立 `codex/m1-production`，原M2 worktree仍干净，分支提交`4f373ea`未覆盖，v1.0.0仍`685066a`。2026-10-28远程main/tag复核一致，GitHub releases API返回空列表；本轮不创建Release、不推送/合并/新标签。建议真实验收后的新版本v1.1.0，不复用旧标签。

临时Java12440及本轮Vite preview已按PID与命令核对后停止，8080/4173无监听。MySQL84保持Running，MySQL80保持原状态。测试夹具及私有日志保留，不复制到生产。

最少人工步骤：准备正式小程序账号/AppID及开发权限；提供独立服务器和域名/TLS/必要备案；在服务器私密配置中填入密钥；确认具体部署目标后依[运行手册](../m1-production-deployment.md)执行真实验收，再授权微信审核与发布。不要把AppSecret/密码/私钥发送到聊天或提交Git。

设计参考：[Spring Security会话与显式持久化](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)。微信官方文档地址为[code2session](https://developers.weixin.qq.com/miniprogram/dev/server/API/user-login/api_code2session.html)和[wx.login](https://developers.weixin.qq.com/miniprogram/dev/api/open-api/login/wx.login.html)；本轮网页读取工具无法打开，不将该失败写成官方链路验证成功。
