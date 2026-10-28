# M1 双平台代码上传（2026-10-28）

用户一次性授权将 M1 生产化分支推送 GitHub，以及正式小程序账号开发版本上传。禁止合并main、移动v1.0.0、推送M2、部署公网、微信审核/正式发布。原验收提交af7006a，当前仅补齐无API环境下的开发版本构建。

- 用户提供正式AppID `wx8ed96bab7f0fbe47`；公开账号配置在miniprogram/upload-account.json，与根project.config.json旧测试号分离。AppID为公开标识，无AppSecret。
- `npm run build:upload`生成dist-production，正式AppID、miniprogramRoot=./、urlCheck=true、DEMO_LOGIN=false、API_ORIGIN为空。客户端请求在传输前返回明确API_NOT_CONFIGURED；不填虚假HTTPS域名、不联系本机API。正式wx.login代码保留，未进行服务端身份交换。
- npm ci/typecheck/lint PASS；31项单元PASS（含2项先RED后GREEN的新构建/无请求保护）；官方wcc/wcsc 8 WXML/10 WXSS PASS。既有5 moderate开发依赖风险保留；不重跑未受影响的后端验收。
- 官方CLI islogin返回true；已打开正确dist-production工程。真实开发者工具模拟器已编译显示“生产API尚未配置”说明；没有网络业务或正式登录成功声明。
- 检查M1祖先、所有变更文件、敏感凭据标记、产物配置；无M2模块/迁移V5–V7，无发现的真实凭据/私有配置。忽略node_modules/dist与project.private.config.json，AppSecret仅以服务端环境引用/公开测试夹具出现。

## 最终结果

- GitHub PASS：`221300ee0ffdd0fbe3f3f39f20792e027220a479`已推送至同名分支，ls-remote核实一致。包含已验收af7006a和本次必要构建适配；随后仅追加上传证据文档提交。远程main仍7ba348d，v1.0.0仍指向685066a；远程未出现M2分支，本地M2仍4f373ea。
- 微信开发版本上传 PASS：官方CLI执行upload，明确使用正式AppID，指定版本1.0.1及用户原文备注，返回退出码0和“√ upload”。上传包84,904字节（82.9KB）。原始CLI info仅含size，私有保存在.local/m1-upload/upload-info.json，无凭据归档。上传来自本分支221300e对应源码构建。
- 官方编译 PASS：独立wcc/wcsc通过，正式工程在真实开发者工具编译显示无API说明；没有把本地模拟器运行写为真机/官方微信交换成功。
- 公众平台版本管理页面确认 BLOCKED：Computer Use在读取现有Edge会话时无法可靠确定当前浏览器URL，触发安全停止。未继续界面操作，也未读取或复制登录令牌。不能声称已经在网页核对上传时间/状态；上传成功依据是官方CLI服务器成功返回。
- 最少人工操作：打开微信公众平台正式账号，进入“管理 → 版本管理 → 开发版本”，核对版本1.0.1、最新上传时间和备注。无需再次上传；若页面尚未刷新先刷新查看。不得点击提交审核或发布。
- 正式微信审核与上线 NOT_RUN；生产API/服务器未配置，备案仍办理中。已有开发依赖告警保持披露。
