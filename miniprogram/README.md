# CampusHub 学生端

原生微信小程序，TypeScript / WXML / WXSS。源文件在 `src`，构建目录为 `dist`。

```powershell
npm.cmd ci
npm.cmd test
npm.cmd run typecheck
npm.cmd run lint
```

从仓库根启动专用测试后端：`pwsh -NoProfile -File scripts/Invoke-AdminTestBackend.ps1 -Apply`。微信开发者工具导入本目录；本机官方测试号已配置，其他开发者应选择自己的官方测试号。

- [完整运行和本地安全边界](../docs/miniprogram-development.md)
- [真实联调、模拟器截图与未执行项](../docs/verification/m1.4-b-miniprogram.md)
- [移动端设计](../docs/m1.4-b-design.md)

本阶段仅本地模拟器开发演示。不得把 localhost / 域名豁免 / 演示账号认证作为真机或正式发布方案；不需要 AppSecret。
