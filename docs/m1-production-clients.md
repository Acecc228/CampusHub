# M1 生产客户端构建与接入

管理端 `admin-web` 使用同源 `/api/v1`：`npm ci` 后运行 `npm run build`。反向代理需把该路径转发至后端并维持同源 HTTPS、Session 和 CSRF。正式登录调用 `/auth/login`，不提供预填凭据。仅本地开发时显式设置 `VITE_DEMO_LOGIN=true` 后运行 `npm run dev` 才使用演示登录；后端也必须明确启用 dev/test 演示认证。生产构建即使设置该变量也不启用演示认证。

小程序本地开发执行 `npm ci`、`npm run build`，输出 `dist/`，仅限开发者工具模拟器；生产构建在当前进程设置 `CAMPUSHUB_API_ORIGIN` 为精确的 `https://实际域名/api/v1` 和 `CAMPUSHUB_APPID` 为正式小程序 AppID，执行 `npm run build:production`。该命令输出 `dist-production/`（包含生产用 `project.config.json`），在微信开发者工具中将该目录作为项目导入；与本地构建分开。缺少或不合规的输入及设置 `CAMPUSHUB_DEMO_LOGIN` 均会失败。失败不会删除旧输出，不应将旧输出视为本次构建结果。示例域名仅供测试，不可作为实际接入地址。

上线前运营方需在微信后台配置 HTTPS request 合法域名、核对 AppID、发布真实的隐私政策和服务说明并完成微信平台要求。小程序在用户点击登录时调用 `wx.login`，一次性 code 仅发送到后端；Session 和 CSRF 存内存。请求超时或服务端 5xx 后，界面锁定重复登录并允许查询身份；回读 200 恢复登录，明确 401 后允许用户重新点击并获取全新的 code，网络/5xx 继续锁定。不自动重发旧 code。未包含真实域名、运营方身份、隐私政策或发布配置；不代表已部署或通过微信审核。
