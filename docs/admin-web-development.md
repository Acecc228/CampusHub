# CampusHub 管理后台：开发与运行

管理端基线始于M1.4-A，C-04复核沿用。源代码位于 `admin-web`，仅面向 ORGANIZER / ADMIN；STUDENT 可通过既有公开业务 API 查看已发布活动和报名。后端认证、权限、状态机及数据库结构未变。当前结果见 [C-04验收](verification/m1.4-c-04.md)。

## 启动真实测试环境

要求本机 Node.js 22.12+（C-03 验证使用 24.22.1 / npm 12.2.0）、PowerShell 7，以及现有 MySQL84CampusHub 运行。不要操作 MySQL80，不重跑安装、初始化或迁移基线。

在 `E:\Project\CampusHub` 的 PowerShell 7 终端执行：

```powershell
npm.cmd --prefix admin-web ci
# 仅在后端 JAR 尚不存在或后端源码变动时构建；包含真实数据库回归测试
pwsh -NoProfile -File scripts/Invoke-Backend.ps1 -Action Verify -Apply
# 常驻终端一，固定连接 campushub_test，端口 8080 被占用会直接拒绝
pwsh -NoProfile -File scripts/Invoke-AdminTestBackend.ps1 -Apply
```

另一个终端运行：

```powershell
Set-Location 'E:\Project\CampusHub'
npm.cmd --prefix admin-web run dev
```

打开 `http://127.0.0.1:5173`。使用 `demo_organizer` 或 `demo_admin`，密码从本机受限文件 `E:\CampusHubRuntime\mysql84\secrets\campushub_test-demo.json` 私下读取并手动输入。不要把文件复制到前端、聊天或 Git。无写死密码、免认证登录或本地假身份。测试与开发账号密码独立。

日常开发库演示使用既有 `Invoke-Backend.ps1 -Action Run -Profile dev -EnableDemoLogin -Apply`，读取对应开发库凭据。它与测试启动器使用同一 8080 端口，不能同时启动；**不要对开发库运行 E2E**。退出各自终端的应用进程即可，无需停止数据库服务。

## 工程与 API

- `src/api`：类型 DTO、同源 fetch、auth/activity/registration；携带 Cookie，真实 CSRF 获取与刷新，15 秒超时，401/403/409/网络错误归一化；写请求不自动重放。
- `src/composables/auth.ts`：后端 `/auth/me` 身份、Session 恢复。无需 Pinia；不持久化角色或密码。401 隐藏业务区域并关闭确认弹层，同身份可恢复内存表单，换身份重建页面。
- `src/pages`：懒加载登录、Dashboard、活动列表、创建/编辑、详情/名单、403/404。列表查询保存在 URL；搜索防乱序，分页可刷新恢复。
- `src/components`、`src/styles`：复用布局、字段、日期输入、分页、状态；CSS Variables 与 Element Plus 主题映射。设计依据见根目录 `DESIGN.md`、`UX-CONTRACT.md`。
- 活动时间统一北京时间，输入格式 `YYYY-MM-DD HH:mm:ss`，转换为带时区整秒 ISO；服务端校验为最终依据。详细内容是纯文本。
- 名单只显示 API 授权返回的学生用户编号、状态和时间，不推测姓名、学号或联系方式。
- Vite `/api` 代理到 loopback 8080；浏览器始终同源。未关闭 CSRF、未放宽 CORS/RBAC。生产构建仅生成 `admin-web/dist`，正式部署不在本阶段范围。

## 检查与复现

```powershell
npm.cmd --prefix admin-web run format:check
npm.cmd --prefix admin-web run lint
npm.cmd --prefix admin-web run typecheck
npm.cmd --prefix admin-web run build
npm.cmd --prefix admin-web test
# 以下需先启动上述测试后端和 Vite，默认不加 -Apply 只预览
pwsh -NoProfile -File scripts/tests/Test-AdminSafety.ps1 -Apply
pwsh -NoProfile -File scripts/Test-AdminWeb.ps1 -Apply
pwsh -NoProfile -File scripts/Test-AdminDatabase.ps1 -Apply
```

E2E 使用本机 Microsoft Edge，单 worker、无重试。运行器检查实际后端进程的 test 标识，且核对 test 数据库身份后才允许写入。每次创建 11 个随机前缀测试学生，密码哈希仅在 MySQL 内复制原测试学生值，随后通过真实登录与报名 API 完成名单分页；不会输出密码或哈希。测试活动、学生、报名夹具保留，不自动 DELETE。旧 probe 不变。不得将 `test:e2e` 直接指向其他环境。

前端单元测试中的 fetch stub 只验证客户端分支，不作为真实联调证据。真实联调由 Playwright + Spring Boot + MySQL 验证。ESLint flat config 检查 Vue/TypeScript 代码问题；格式由 Prettier 检查，类型由 vue-tsc 检查。固定版本与 `package-lock.json` 配合 `npm ci` 重装。

人工复核：分别使用 1440、1280、900px 宽度，登录后完成创建→编辑→发布→名单→下架→重发布；查看表格自身横向滚动、键盘打开下拉/Escape 返回焦点、确认框取消、未保存提醒、断网重试及失效恢复。当前详细结果与截图见 [验收报告](verification/m1.4-a-admin.md)。

C-04新增证据统一见 [最终回归](verification/m1.4-c-04.md)，上方M1.4-A链接仅作历史参考。结束时分别在两个常驻终端按Ctrl+C，仅停止本轮启动的后端/Vite；不要停止MySQL。端口核查和异常中断恢复见 [README](../README.md)。默认Session空闲30分钟；验证时使用独立浏览器会话且期间不发API请求，随后点击活动管理，应出现401和重新登录层，重新认证后恢复。单纯清Cookie或注销并不等于完整闲置验收。

C-04默认Session已实际闲置31分钟验证401和重新登录。同身份恢复保留当前页面/草稿，之前失败的只读列表需点击页面的‘重新加载’；自然闲置取证的idle-relogin.png仍显示原列表401，200及10条数据来自独立真实双标签Logout失效后的重新登录/手动重试复验，不是自然闲置样本直接证明。不自动重放写操作。

