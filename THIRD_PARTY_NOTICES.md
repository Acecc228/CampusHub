# 第三方文件与依赖

| 项目 | 使用方式 | 许可记录 |
|---|---|---|
| Apache Maven Wrapper 3.3.4 | `backend/mvnw`、`backend/mvnw.cmd` | 保留原 Apache Software Foundation 文件头，附 [Apache-2.0](LICENSES/Apache-2.0.txt) 副本 |
| 后端与管理端 npm/Maven 依赖 | 通过 `pom.xml`、`package.json` 和锁文件解析，不提交下载的包/JAR | 遵循各依赖随包提供的许可证；本文件不是完整 SBOM 或依赖许可审计 |
| 小程序自动化开发工具 | 仅开发依赖，不进入小程序运行包 | 依赖版本和间接依赖见小程序锁文件；phin 风险见 README |
| 微信开发者工具、MySQL 安装包 | 仓库外安装，不随源码交付 | 遵循对应厂商条款，不以本项目许可重新授权 |
| 页面截图 | 本地 MVP 的实际页面与演示测试数据 | 用作验收和作品展示，无真实学生资料；两张含工具个人头像的桌面原图仅在本地保留 |

Wrapper 在本项目中有本地 Windows JDK/PowerShell 启动适配；M1.5-G01 将 `mvnw.cmd` 的个人目录绝对路径改为调用已有 `pwsh.exe`，未修改 Maven 分发地址或 SHA-256。

不对整个项目或全部依赖作“已获统一开源授权”的声明；未来若分发构建包、安装包或改选项目许可证，需按实际交付内容继续核对第三方许可和通知。

## G02 素材来源核对（源码交付）

- Vue 使用 `@element-plus/icons-vue@2.3.2`（MIT），本地 LICENSE 保留 Element Plus 版权声明。Vue 3.5.44、Vue Router 4.6.4、Element Plus 2.14.7 的本地元数据和 LICENSE 均为 MIT。通过 npm 安装，未复制包源码到仓库。未来分发 dist 必须保留适用版权及许可文本。
- 小程序8张 TabBar PNG 可由自有 `miniprogram/scripts/icons.py` 重建。当前素材/生成代码中未发现外部图库照片、字体文件或复制的 UI 模板；使用系统字体和真实测试截图。此检查不替代作者对素材权属的保证。
- 小程序开发包：TypeScript 5.9.3 为 Apache-2.0（本地 LICENSE.txt），miniprogram-api-typings 5.2.3 为 MIT（本地 LICENSE）；miniprogram-automator 0.13.0 元数据为 MIT，但本地未发现独立 LICENSE，未随源码交付该包，再分发前需补核上游许可文本。
- 两份微信官网 HTML 快照仅本地保留，不重新分发；历史报告保留来源与下载结论。安装包、SDK、JAR、node_modules 和构建产物不在首次源码交付范围。
- Wrapper 保留 Apache-2.0 原文件头、副本和适配说明，未改授 MIT。MIT 自有代码与单独保留 Apache-2.0 的 Wrapper 可并存。后端仅经 Maven 解析，未复制第三方 Java 实现；传递依赖完整 SBOM/许可审计 NOT_RUN，未声称生产二进制许可验收通过。
