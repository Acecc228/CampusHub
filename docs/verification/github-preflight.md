# M1.5-G01 GitHub 公开交付预审

> G02 续接：保留本报告当时结论；个人路径已脱敏、冻结原件及厂商网页已排除。当前结果见 [G02](github-final-confirmation.md)。

日期：2026-10-24（北京时间）。范围：`E:\Project\CampusHub` 的公开交付文件、Git、安全与 README；不是新功能开发、生产安全审计或重新执行 MVP 验收。

**结论：预审完成，作品集公开展示有条件就绪。** 未发现真实凭据泄漏或必须轮换的秘密，实际交付问题已修复。公开前仍应由作者确认历史证据中低风险的本机用户目录披露，以及是否为自有代码选择开源许可证。本轮不代替作者决定这两项；项目当前明确为未指定开源许可证。不能将结论解释为生产就绪、完整渗透测试、全部第三方许可已审计或新电脑一键安装已通过。

## 1. Git 状态与历史边界

| 检查 | 实际结果 |
|---|---|
| 分支 | `setup/mysql84-preparation`，unborn HEAD |
| commit / remote | 0 个 commit；无 remote，未发现 URL 内嵌凭据 |
| 索引 | 27 个既有 intent-to-add 标记，索引中的 blob 均为空；`git diff --cached --quiet` 返回 0。业务源码大部分仍未跟踪，不代表已经提交 |
| 内部快照 | 4 个 `refs/codex/turn-diffs/checkpoints/…` tree 引用；逐一遍历后检查 154 个不同 blob，无真实秘密命中 |
| 操作边界 | 仅只读 Git 检查，未 stage、commit、添加 remote、push、清理对象或改写历史 |

`git rev-list --all --count` 为 0 只证明没有提交，不能跳过内部 tree 快照检查。本轮另外通过 `git for-each-ref`、`git ls-tree -r`、`git cat-file blob` 检查可达快照。未对所有不可达/已删除 Git 对象作取证；不应将本地 `.git` 目录打包公开，不使用 `--mirror` 或 `--all` 推送内部引用。

## 2. 敏感信息检查与处理

检查工作树中 Git 未忽略的交付候选文件、当前索引内容与可达内部快照。匹配私钥头、常见平台令牌、含密码的 URL、会话/CSRF 长值、密码字面量、个人目录、邮箱和微信 AppID；只记录路径、类别、行号或脱敏结论，不保存秘密原文。另将当前仓库外凭据仅在内存中与交付候选文件及快照逐字节比对，结果见 [定向检查摘要](github-preflight-checks.json)。此方法不能证明不存在所有未知格式或已废弃的秘密。

| 文件路径 | 风险类型与脱敏结论 | 处理/状态 |
|---|---|---|
| `backend/mvnw.cmd` | 个人目录硬编码，影响公共仓库可运行性 | 改为调用已有 `pwsh.exe`，实际 Maven/Java 版本调用 PASS；保留 JDK、分发地址与 SHA-256 |
| `backend/src/test/java/com/campushub/BackendIT.java` | 疑似密码字符串 | 实为从环境变量读取密码的请求构造，非明文凭据，保留 |
| `scripts/Test-DatabaseBootstrap.ps1`、`scripts/tests/Test-BackendSafety.ps1` | 疑似密码字面量 | 实为格式检查、伪造负例及环境清理测试夹具，未删除或弱化测试 |
| `miniprogram/project.config.json` | 微信测试 AppID | 非 AppSecret，不是可认证密码；保留可导入项目配置，README 说明他人使用自己的官方测试号 |
| `docs/verification/m1.4-b-wechat-download.html` | 官方网页中的 AppID | 公共下载页面内容，非项目私密凭据 |
| `docs/verification/m1.4-c-04/mini-offline-home-desktop.png`、`mini-offline-unknown-desktop.png` | 工具顶部个人微信头像 | 精确加入忽略规则，原始证据仍本地保留，不编辑或删除截图 |
| `docs/environment.md`、`docs/mysql84-install-handoff.md`、`docs/verification/install-preflight.json`、`m1.0-a-install.md`、`uac-launch.json` | 本机 Windows 用户目录，低风险隐私/环境元数据 | 保留历史证据并明确披露；不属于认证凭据。`install-preflight.json` 受安装器固定哈希保护，不直接匿名化原件 |
| `docs/verification/m1.4-a-e2e-first-failure.json`、`m1.4-a-e2e.json`、`m1.4-a-review-red.json`、`docs/verification/m1.4-c-04/browser-e2e.json` | 历史浏览器参数含用户目录，低风险隐私 | 保留真实取证，不伪造 argv；公开前由作者确认是否接受该元数据披露 |
| `E:\CampusHubRuntime\mysql84\secrets`（仓库外） | 数据库及开发演示账号真实凭据 | 未复制进交付文件；只在内存读取用于精确比对，不打印值或哈希，不轮换、不修改 |

历史快照中的疑似密码项同样为测试代码/环境引用；旧 Wrapper 个人路径仍可能存在于本地内部快照，未重写历史。当前没有发现需要撤销或轮换的凭据；若以后发现真实泄漏，应先确认撤销/轮换方案，再另行处理，不能用删文件掩盖。

`install-preflight.json` 的原 SHA-256 仍为 `F7067F8C32F2902A54D86441264C113813E1C50EBC811497474A3BA919FC88FC`，未修改保护链、数据库夹具或历史结果 JSON。

## 3. 忽略规则与工程交付

- 原有 `.local`、环境文件、cnf、私钥、日志、安装包、target、node_modules、dist、微信私有配置保持忽略。
- 增补 secrets 目录、证书/keystore、数据库文件/转储、JAR/class、TS 构建缓存、coverage、Playwright 报告/结果、Vite/cache；显式允许可能需要的 Wrapper JAR。当前 Wrapper 为 only-script，不要求 JAR。
- 精确忽略既有零字节 `({status` 临时文件及上述两张原始桌面截图，未删除任何现有文件。没有整目录忽略历史验收、源代码或测试。
- 必需 Wrapper、校验和属性、两个前端锁文件、源码、迁移及现有安全模板均可被 Git 纳入；`.env.example` 例外仍有效。项目不自动读取 dotenv，不新增不起作用的环境模板。
- 锁文件根依赖声明与 `package.json` 一致；POM/XML 与候选 JSON 解析通过。源码按业务包/客户端组织，无影响公开交付的结构问题，无业务重构。
- 历史 C-02 报告原先链接到两个被忽略的日志，已改为公开 JSON 摘要并注明日志仅本地保留；原日志未改。

## 4. README、截图与许可

README 已补齐介绍、三角色功能、技术栈、目录、分层架构 Mermaid、六表关系 ER 图、Session/CSRF/RBAC、RC 与锁顺序/版本/回滚亮点、本地运行、验收矩阵、限制与后续计划。

直接复用 5 张 C-04 真实页面截图：管理工作台、组织者名单、学生首页、报名详情和我的报名。逐张查看原图；其余交付候选截图以联系表作概览检查，桌面原图另作原尺寸隐私检查。未制作虚构数据图、去掉测试前缀、修改 UI 或重新截图；联系表只是本地审查辅助，不作为新一轮业务验收。未执行逐像素 OCR、图片取证或 GitHub 在线渲染验收。

新增 `LICENSE.md` 明确自有代码尚未选择开源许可证；不擅自标 MIT/Apache。新增 `THIRD_PARTY_NOTICES.md`，保留 Maven Wrapper 的原始文件头和本地适配说明，并附从 Apache 官方地址取得的 `LICENSES/Apache-2.0.txt`（SHA-256：`CFC7749B96F63BD31C3C42B5C471BF756814053E847C10F3EB003417BC523D30`）。没有声称完成全部传递依赖许可审计；未来分发二进制需另行核对。

## 5. 实际验证与未执行项

具体计数见 [机器可读摘要](github-preflight-checks.json)。

| 检查 | 状态与范围 |
|---|---|
| Git 状态/索引/remote/可达快照 | PASS；0 commit、0 remote、154 个不同快照 blob 检查 |
| 候选交付文件秘密规则扫描与当前凭据精确比对 | PASS（已披露个人目录低风险残留）；无真实凭据命中，不保存秘密 |
| 忽略规则与必需文件 | PASS；覆盖凭据、产物、源码、Wrapper、锁文件、模板与截图规则 |
| Markdown 相对链接/HTML 图片 src | PASS；同时检查目标存在且没有被忽略；不将纯文本日志文件名误算成公开附件 |
| JSON/XML/锁文件直接依赖一致性 | PASS；不是完整安装或集成验证 |
| `cmd /d /c backend\mvnw.cmd -v` | PASS，退出 0；Maven 3.9.16 / Java 21.0.2，未触发构建或数据库连接 |
| 两个启动器不带 `-Apply` | PASS；后端 Verify 和 test 后端 Run 均输出 PREVIEW，无数据库操作 |
| `git diff --check` | PASS；首轮发现 README/PLAN 尾部空行，整理后复验通过 |
| 全量 Maven/并发、浏览器 E2E、微信全流程、npm audit | NOT_RUN（本轮）；未受影响，不重复执行，引用 C-04 快照 |
| GitHub 在线渲染、新电脑安装、生产部署、完整许可/渗透审计 | NOT_RUN；不在本轮范围 |

只读复核入口（仓库根目录）：

```powershell
git status --short --branch
git branch --show-current
git remote
git rev-list --all --count
git for-each-ref '--format=%(refname) %(objecttype)'
git diff --cached --quiet
git ls-files --cached --others --exclude-standard
git check-ignore -v -- .env miniprogram/project.private.config.json
git diff --check
cmd /d /c backend\mvnw.cmd -v
pwsh -NoProfile -File scripts/Invoke-Backend.ps1
pwsh -NoProfile -File scripts/Invoke-AdminTestBackend.ps1
```

扫描/联系表及详细工作记录位于被忽略的 `.local/github-preflight*`，公开摘要仅保存计数、路径、分类与结果。没有把机器凭据、缓存或截图联系表加进交付集。

## 6. 必须保留的六项风险披露

1. M1.4-C-04 本地 MVP 已完成真实 Vue 与微信模拟器双端验收，结论 PASS。
2. 尚未正式部署或完成正式微信登录/发布；开发演示登录仅供本地开发与测试。
3. 真机及部分原生网络边界（长超时、服务端提交后才断网）尚未执行；Node 丢响应测试不能替代这些验证。
4. 小程序开发自动化链仍有 5 项 moderate 告警，关联 phin；未强制降级或隐藏。
5. 两端运行时依赖审计在 C-04 最终验收时为 0；本轮未重新联网审计，不作“当前仍为 0”的新声明。
6. 当前角色权限为登录时快照，动态撤权属于后续改进范围。

## 7. 本轮实际改动

| 文件 | 用途 |
|---|---|
| `README.md` | 求职展示、架构、运行、真实验收与限制 |
| `.gitignore` | 凭据/产物/缓存及两张原始桌面图精确忽略 |
| `backend/mvnw.cmd` | 一行 PowerShell 调用路径修复 |
| `LICENSE.md` | 明确尚未选定开源许可 |
| `THIRD_PARTY_NOTICES.md`、`LICENSES/Apache-2.0.txt` | Wrapper 第三方声明与许可证副本 |
| `docs/verification/m1.4-c-02.md` | 两处被忽略日志的公开链接修复 |
| `PLAN.md`、`docs/DEV_HANDOFF.md` | G01 状态、结果、边界与续接 |
| `docs/verification/github-preflight.md`、`github-preflight-checks.json` | 本报告与脱敏检查摘要 |

既有业务源码、认证/授权/报名规则、依赖和数据库未改；未更换微信 AppID、修改私有设置、删除证据或测试数据。

## 8. 后续首次 GitHub 提交与推送建议

以下为另行授权后的建议，本轮没有执行：

1. 作者先确认是否接受历史用户名路径披露，并决定继续“未指定开源许可证”还是明确选定许可证。如需匿名化受保护证据，保留原件另作明确标记的公开副本，不直接改固定哈希保护链。
2. 核对 Git 作者姓名/邮箱的公开偏好；可使用本人 GitHub 提供的 noreply 邮箱。本轮不输出或修改用户级 Git 配置。
3. 先检查非忽略候选清单，再按明确范围暂存；确认 Wrapper、锁文件、SQL、源码和公开证据完整，私有/构建文件未混入。Windows 上需在暂存后为 `backend/mvnw` 确认可执行位（面向未来类 Unix 环境），本轮未更改索引权限。
4. 审阅实际 staged diff 与 staged 文件清单，复做秘密检查和 `git diff --cached --check`，再创建首次提交。推荐明确主分支命名；不因当前 unborn 分支名而擅自改名。
5. 在用户授权创建/指定公开仓库和配置 remote 后，只 push 明确分支；不要镜像推送 `.git`、内部 Codex 引用或机器环境文件。推送后再核对 GitHub README、Mermaid 与图片的实际显示。

Agent：单主 Agent 执行，未使用 Luna 子 Agent或其他子 Agent。G01 到此停止，不执行 commit、push、部署、微信发布或 M2.0。
