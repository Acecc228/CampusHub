# CampusHub 原生小程序本地运行

学生端基线始于M1.4-B，C-04复核沿用；TypeScript / WXML / WXSS，无 uni-app、Taro 或运行时 UI npm 框架。后端接口、Session/CSRF/RBAC 与数据库结构均沿用 M1.1～M1.3。当前结果与环境限制见 [C-04验收](verification/m1.4-c-04.md)。

## 启动

1. 确认既有 `MySQL84CampusHub` 服务运行，TCP `127.0.0.1:3307`。不要操作 MySQL80。
2. PowerShell 7 在仓库根执行 `pwsh -NoProfile -File scripts/Invoke-AdminTestBackend.ps1 -Apply`，保持终端运行。它只使用 test-app 和 `campushub_test`，绑定 `127.0.0.1:8080`；端口占用会拒绝，不自动停止其他进程。
3. 执行 `npm.cmd --prefix miniprogram ci`、`npm.cmd --prefix miniprogram run build`。修改 src 后重新 build；不要编辑 dist。开发者工具无需“构建 npm”。
4. 启动 `E:\CampusHubRuntime\wechat-devtools\微信开发者工具.exe`，导入 `E:\Project\CampusHub\miniprogram`，项目类型为小程序，代码目录由配置指定 `dist/`。
5. 本机已通过官方“使用测试号”生成测试 AppID，保存在 `project.config.json`。其他开发者用自己的微信扫码登录工具，并在导入界面选择官方测试号；不要把该测试号当作正式发布 AppID，也不需要 AppSecret。
6. **仅本地模拟器**：工具项目详情的本地设置允许“不校验合法域名”。本机 `project.private.config.json` 存储该选项并被 Git 忽略；公共配置 `urlCheck:true`。若需要自动化，由用户在工具安全设置开启本地服务端口。不得把这些豁免用于真机或生产。
7. 编译后使用学生演示账号 `demo_student` 登录。密码只从外部受限文件 `E:\CampusHubRuntime\mysql84\secrets\campushub_test-demo.json` 本地查看，不能复制进仓库、日志或聊天。工具的扫码登录与 CampusHub 学生登录是两个独立过程。

官方安装入口：[微信开发者工具下载](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)。本机已安装官方 x64 稳定版 2.02.2608080，腾讯 Authenticode 签名有效；来源、哈希及版本见 verification/m1.4-b-installer.json。无需重新安装。

## 数据和登录边界

活动接口需要认证，因此匿名首页提供清晰的登录提示，不伪造公开活动。首页“发现新活动”按真实 API 最新创建顺序，分类来自分类表。我的报名按本人 Session 查询；未公开活动不返回详情，仅保留本人报名编号/状态及服务端允许的取消操作。

Cookie、CSRF 和服务端身份只放内存，冷启动重新登录。每次请求显式携带固定后端的 JSESSIONID；登录轮换后重新取得 CSRF。401 清空所有受保护页面数据，403 显示边界，不自动重放写入。开发 API 只允许 `devtools` 平台；真机直接拒绝此本地演示链路。

写入超时不等于失败。报名/取消按活动去重，未知结果保留目标及原 version；查询到目标状态或更高版本才解除未知锁。仅查到旧版本不作失败判断，显示“核实状态”。如果请求从未到达服务器，旧版本可能长期不变：保持锁定，待网络恢复核查；不提供自动重试写入。退出和换账号清除本身份的操作状态。

## 验证命令

```powershell
npm.cmd --prefix miniprogram run typecheck
npm.cmd --prefix miniprogram run lint
npm.cmd --prefix miniprogram test
pwsh -NoProfile -File scripts/Test-Miniprogram.ps1 -Apply
& E:/CampusHubRuntime/wechat-devtools/cli.bat auto --project E:/Project/CampusHub/miniprogram --auto-port 9420 --trust-project --lang zh
pwsh -NoProfile -File scripts/Test-MiniprogramSimulator.ps1 -Apply
```

两个 PowerShell 验证脚本不带 `-Apply` 时仅预览。真实 API 脚本创建并保留明确标记的测试活动/第二学生，只写 test 库；不会清库。活动报名窗口有限，跨天演示前重跑 API 脚本生成新夹具。模拟器脚本使用真实学生账号，测试报名/取消/重报；取消确认弹窗出现后，需实际点击“取消报名”，桌面自动化也可完成。SDK 的 native.confirmModal 在当前工具版本未能可靠关闭此弹窗，因此不 mock 确认成功。

可选多尺寸截图：在工具中切换设备后执行 `Test-MiniprogramSimulator.ps1 -Apply -VisualLabel small`（或 large），确认输出JSON的实际width；M1.4-B当时因工具间歇无响应未完成；C-04已实际补验360/390/430六页及原生断网恢复，证据见C-04报告，脚本存在本身不代表执行通过。数据库最终只读核查：`Test-MiniprogramDatabase.ps1 -Apply`。

官方 WXML/WXSS 编译器、Node 请求层真实 HTTP、微信 wx.request 运行时结果分别记录，不能互相替代。详细矩阵、失败修复及截图见 [M1.4-B 验收](verification/m1.4-b-miniprogram.md)。

C-03 本机验证使用 Node.js 24.22.1 / npm 12.2.0；`package.json` 要求 Node.js 22.12+。`npm ci` 与锁文件一致。`lint` 解析脚本/测试 JavaScript 与源码 JSON，不解析 WXML/WXSS；后两者需先 `build`，再使用微信开发者工具附带的 `wcc.exe` / `wcsc.exe -lc` 编译 `dist` 下全部 8 个 WXML / 10 个 WXSS，文件参数须相对于 `dist`，否则 WXSS 相对 import 会误报找不到。C-03 实际编译退出码均为 0，命令和边界见 [C-03 验收](verification/m1.4-c-03.md)。

## 人工复核与未来替换

在模拟器选择小屏、常规屏和大屏，逐页检查：首页登录/卡片、活动搜索和横向分类、触底加载/下拉刷新、详情滚动及底部操作安全区、登录输入/错误、我的报名记录/取消、个人中心/退出。断开本地后端后重试，应出现可恢复错误；恢复后再查询，不应自动提交报名。

未来正式 AppID 可替换项目配置，但正式微信 code/openid 认证、HTTPS 合法域名、真机 Cookie/网络差异与发布审核不属于当前MVP。不能只换 AppID 或解除平台限制就当作生产方案。当前仅完成已授权C-04，不进入正式发布或M2.0。

切换模拟器机型可能重建运行时，内存Session会丢失，需重新通过真实表单登录；这不是正式微信授权登录。大批自动化调用应分段保存证据，若工具报无响应，只做有限恢复尝试并记录未完成项。结束演示后退出本轮工具、停止自己启动的后端/Vite，保留MySQL运行；不要批量结束其他开发进程。

