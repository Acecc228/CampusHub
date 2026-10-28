# M1 双平台代码上传（2026-10-28）

用户一次性授权将 M1 生产化分支推送 GitHub，以及正式小程序账号开发版本上传。禁止合并main、移动v1.0.0、推送M2、部署公网、微信审核/正式发布。原验收提交af7006a，当前仅补齐无API环境下的开发版本构建。

- 用户提供正式AppID `wx8ed96bab7f0fbe47`；公开账号配置在miniprogram/upload-account.json，与根project.config.json旧测试号分离。AppID为公开标识，无AppSecret。
- `npm run build:upload`生成dist-production，正式AppID、miniprogramRoot=./、urlCheck=true、DEMO_LOGIN=false、API_ORIGIN为空。客户端请求在传输前返回明确API_NOT_CONFIGURED；不填虚假HTTPS域名、不联系本机API。正式wx.login代码保留，未进行服务端身份交换。
- npm ci/typecheck/lint PASS；31项单元PASS（含2项先RED后GREEN的新构建/无请求保护）；官方wcc/wcsc 8 WXML/10 WXSS PASS。既有5 moderate开发依赖风险保留；不重跑未受影响的后端验收。
- 官方CLI islogin返回true；已打开正确dist-production工程。真实开发者工具模拟器已编译显示“生产API尚未配置”说明；没有网络业务或正式登录成功声明。
- 检查M1祖先、所有变更文件、敏感凭据标记、产物配置；无M2模块/迁移V5–V7，无发现的真实凭据/私有配置。忽略node_modules/dist与project.private.config.json，AppSecret仅以服务端环境引用/公开测试夹具出现。

GitHub推送与微信上传的最终结果随后补记。任何本地PASS不代表微信服务器上传成功。
