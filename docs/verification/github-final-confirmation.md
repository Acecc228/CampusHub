# M1.5-G02 GitHub 首次交付前最终确认

日期：2026-10-24（北京时间）。单主 Agent，0 子 Agent。

**结论：G02 文件准备完成；首次提交仍等待许可证、公开作者身份及提交授权。** 本轮未暂存、commit、push、创建远程仓库、改写历史、部署或操作数据库。未修改业务源码、脚本执行逻辑、依赖或迁移；不重复完整业务测试与 G01 凭据扫描。

## 1. 隐私处理与原件

G01 的9处个人目录已全部处理：

| 文件 | 处理 |
|---|---|
| docs/environment.md | PowerShell 历史路径改通用用户示例，标注公开版本 |
| docs/mysql84-install-handoff.md | 历史路径改示例，公开证据链接指向脱敏副本 |
| docs/verification/m1.0-a-install.md | 同上，保留历史结论 |
| docs/verification/uac-launch.json | 用户目录脱敏并增加 `_publication` 标记 |
| docs/verification/m1.4-a-e2e-first-failure.json | 浏览器参数脱敏并标记 |
| docs/verification/m1.4-a-e2e.json | 同上 |
| docs/verification/m1.4-a-review-red.json | 同上 |
| docs/verification/m1.4-c-04/browser-e2e.json | 同上 |
| docs/verification/install-preflight.json | 冻结原件不改，精确忽略；新增 install-preflight.public.json |

8份修改前原件及冻结原件备份在 ignored `.local/g02-originals`；不公开个人信息原文。JSON 的历史测试结果字段保留，脱敏元数据明确说明公开版不是原始字节取证。冻结原件 SHA-256 仍为 `F7067F8C32F2902A54D86441264C113813E1C50EBC811497474A3BA919FC88FC`，未修改安装器或固定哈希。公开克隆没有冻结原件时历史安装入口预期拒绝；公开副本不能替代原件，也不允许改哈希绕过。

正常工程/工具路径（项目根、仓库外 runtime、JDK、ProgramData、loopback URL）、演示账号、负例字符串和测试夹具保留。公开文本定向检查个人用户目录、当前用户名、邮箱和账号来源；仅输出路径与分类，不保存原文。通用 PUBLIC_USER 示例不属于真实个人信息。具体计数与边界见 [检查 JSON](github-final-checks.json)。

截图沿用 G01 对 README 5张原图和其他候选图联系表的审查；本轮没有新增或修改图片，不重做全部图片扫描。未发现新增个人资料来源；不声称逐像素 OCR 完成。两张含个人头像的桌面原图继续精确忽略并本地保留。两份腾讯官网 HTML 快照属于下载研究缓存，未取得随项目重新授权的依据，现精确忽略；不删除文件。截图内为开发演示用户/测试活动，不当作真实学生资料；微信测试 AppID 非 AppSecret，保留配置并提醒其他开发者用自己的测试号。

## 2. 许可证建议（尚未决定）

| 选择 | 建议及条件 |
|---|---|
| MIT | 如愿意他人复制、修改、分发及商用，这是求职作品较简单的选择；要求保留版权及许可声明，需要作者确认版权署名 |
| 暂不授予开源复用许可 | 如仅想公开展示、尚不愿授权复用，保留当前 LICENSE.md；不要称项目为 MIT 或已开源授权。公开平台本身的查看/fork机制仍受平台条款约束 |
| Apache-2.0 | 如确有明确专利授权需求可考虑，通知与变更说明要求更多；当前没有必须改选此项的证据 |

未替作者选择许可证，未编造版权姓名或版权年份。建议：愿意开放复用则选 MIT；尚不确定则先保持当前状态，正式提交前给出明确选择。第三方仍分别遵循原许可证，不能以项目 MIT 覆盖 Wrapper/依赖。

依据：[MIT](https://choosealicense.com/licenses/mit/)、[不授予许可](https://choosealicense.com/no-permission/)、[Apache-2.0](https://choosealicense.com/licenses/apache-2.0/)。素材与本地包许可核对详见 [THIRD_PARTY_NOTICES](../../THIRD_PARTY_NOTICES.md)。图标来自 Element Plus MIT 包与小程序自有生成脚本，没有发现需另行署名的图库照片、字体或外部模板。完整传递依赖许可审计/二进制分发审计 NOT_RUN，未来若分发 dist/JAR 须补核；当前无证据表明源码交付存在许可证冲突。

## 3. 首次提交完整清单

当前快照 **598个文件，约12.6 MiB**（工作树字节数，非 Git 压缩体积），最大单文件约130 KiB。

逐文件清单：[github-first-commit-files.txt](github-first-commit-files.txt)。该清单是非忽略文件的审查快照，**不代表已暂存**。所有候选都逐名归类：Java 源码/测试与资源、Vue 源码/测试、原生小程序源码/图标/测试、四份迁移、SQL与安全配置模板、Wrapper及校验和、两端锁文件、设计与架构/运行文档、真实历史验收和截图、G02记录。空 `.gitkeep` 是现有目录占位文件，保留不影响交付。规模及按目录统计见检查 JSON，未发现超 GitHub 单文件100MiB的候选。

精确新增排除：冻结安装基线、两份厂商 HTML。既有排除继续生效：两张个人头像桌面原图、`.local`（包括原件备份和审查辅助文件）、`.superpowers`、secrets、实际环境文件/凭据、微信私有配置、数据库文件/转储、安装包、target/dist/node_modules、缓存、日志和临时文件。仓库外数据库、运行数据和凭据本来就不在候选树中，不遍历或打印。没有删除任何夹具、数据、私有配置或历史对象。

公开历史 JSON/TXT 中的活动/报名编号及假造测试用户属于可追踪的验收夹具，不是测试数据库转储；保留失败/RED与NOT_RUN，不把历史失败伪造成成功。内部工具快照不在文件清单中；不把 `.git` 打包公开。

## 4. Git 身份、分支与可达性

- 作者姓名和邮箱均已配置，来源为 global；邮箱不是 GitHub noreply，存在作者邮箱随提交公开风险。本报告不保存原文。实际作者/提交者环境覆盖也只记录存在状态；首次提交前必须确认。
- 当前 unborn HEAD 为 `setup/mysql84-preparation`；0 commit、0 remote。建议首次正式提交前由用户确认主分支 `main`，本轮未改名。
- 索引仍是27个既有 intent-to-add 空 blob，无已暂存有效文件。检查嵌套 `.git`、gitlink mode 160000、`.gitmodules` 的结果见 JSON。
- 4个 `refs/codex/turn-diffs/checkpoints/...` 引用指向 tree，沿用 G01 154个 blob 的秘密检查，不重扫。内部旧个人路径仍可能在这些本地对象中；无 commit 祖先链可自动带入首次提交。未来首次 commit 应仅由确认的索引生成，显式分支 push 不会把无关 tree 引用作为祖先推送；与选中文件相同的 blob 内容可正常复用。
- 未执行不可达对象取证、gc、prune、filter 或引用删除；禁止 mirror/all 推送，禁止复制 `.git` 目录交付。首次实际暂存后仍需审查索引内容，这份准备清单不能替代 staged 审查。

## 5. 验证和后续流程

**定向检查 PASS**：140项必需文件、299处文档链接、排除规则均通过；个人路径/当前用户名/邮箱模式命中0，JSON解析失败0，历史JSON结果字段保持，冻结原件哈希不变，无嵌套仓库或子模块，diff检查退出0。

本轮定向结果见 JSON：候选个人信息扫描、JSON解析、链接可交付性、必需文件、排除项、原件哈希、Git状态/规模/嵌套检查和 `git diff --check`。业务/数据库/浏览器/微信/完整秘密复扫 NOT_RUN（未修改相关内容，复用 C-04/G01，不宣称新一轮PASS）。GitHub在线渲染、新电脑安装、完整许可证审计 NOT_RUN。

待用户决定后，下一轮建议依次：

1. 明确 MIT 或保持未授予复用许可；如选 MIT，确认公开版权署名。
2. 确认公开 Git 姓名及从本人 GitHub Settings → Emails 复制的 noreply 邮箱，仅在另获授权后设置仓库级身份；不修改全局或 GitHub 账号资料。见 [GitHub 官方说明](https://docs.github.com/en/account-and-profile/how-tos/email-preferences/setting-your-commit-email-address)。确认 main 或保留当前分支。
3. 获得首次 commit 授权后刷新清单，逐文件或按已审查清单暂存，禁止盲目 git add . / -A。确认 `backend/mvnw` 的100755执行位（当前未改索引），核对 staged 清单、diff、差异检查与秘密边界，许可证变更纳入复审，再执行首次 commit。
4. 检查首次 commit 文件/作者/邮箱/父提交（应无父提交）。仅在用户另行授权创建/指定仓库、remote及push后操作，使用明确分支，不 mirror/all；上线核对 README、图片和 Mermaid。

当前阻止直接进入首次提交的事项：许可证选择、公开作者身份确认与正式 commit 授权；push还缺远程仓库和授权。G02完成即停止，不把待决策项当成已批准。
