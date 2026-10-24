# CampusHub 新对话开发交接

更新日期：2026-10-24（北京时间）。工作目录：`E:\Project\CampusHub`。首次 GitHub v1.0.0 正式交付已完成：公开仓库、main、首次提交及版本标签均已实测确认。历史 G01/G02 尚未授权的描述保留为当时记录，以本节最新授权为准。

## GitHub v1.0.0 正式交付（PASS，已完成并停止）

- 仓库：https://github.com/Acecc228/CampusHub，Public，默认分支 main 已推送。首次 root commit `685066adb0bdaab425717207c93d5a96d709bb5e`，599 文件，提交信息 `feat: release CampusHub MVP v1.0.0`。annotated tag v1.0.0 已推送且指向该提交。
- 用户已明确选择 MIT，版权 2026 Acecc228；Git 作者姓名 Acecc228，官方 noreply 邮箱 185771718+Acecc228@users.noreply.github.com，仅写仓库级配置。
- G02 的 598 文件清单作为基线，仅补许可证、README、计划、交接及发布证据。业务代码、数据库和既有验收结论不变。
- 远程完整树 599 文件与发布提交逐路径一致；三端源码、构建入口与锁文件齐全，排除文件未推送。实际 GitHub 页面 README 渲染和 5 图加载 PASS；GitHub API 识别 MIT。详见 [发布记录](verification/github-v1.0.0-release.md)。
- 远程核验后以单独文档提交归档 PLAN、本交接与发布记录，再推送 main；v1.0.0 保持指向首次 MVP 提交。主 Agent 1 个，子 Agent 0 个；无新增发布阻断，C-04 既有风险继续保留。
- 首次 staged 格式检查发现旧验收文本尾随空格/文件尾空行，未改写历史证据；本次修改的发布文档定向检查 PASS。不将历史格式问题伪报为全部差异检查通过。
- 完成本次交付后立即停止；不部署后端、不正式发布微信小程序、不进入 M2.0。不重跑 MySQL 安装、初始化、baseline 或完整业务测试。

## M1.5-G02 历史结果（当时尚未授权发布）

- 8份公开文件个人路径脱敏；原件仅本地 `.local/g02-originals`。冻结 install-preflight.json 哈希不变且忽略；install-preflight.public.json 不能替代安装器哈希基线。新克隆缺原件时历史安装器预期拒绝，不绕过保护。
- 完整文件名见 `verification/github-first-commit-files.txt`；规模、排除项及定向验证见 G02/JSON。无 stage/commit/remote/push，内部 tree 引用保留，禁止 mirror/all 推送。
- README 既有5图复用 G01 审查，两张头像原图继续忽略。厂商 HTML 缓存不公开，第三方说明已补充。
- Git 姓名/邮箱继承全局，邮箱非 GitHub noreply；未输出原文或改设置。unborn 分支仍 setup/mysql84-preparation，建议确认 main。
- 项目许可未指定。愿意允许复用建议 MIT，仅展示且不愿授权复用则保持现状；用户明确选择、确认公开作者姓名和本人 noreply 邮箱后再实施。
- 单 Agent，未重跑无关完整业务/安全测试。完成即停；等待许可与首次提交授权，remote/push/部署/微信发布/M2.0未授权。

## M1.5-G01 历史结果与交付入口（隐私后续以 G02 为准）

- Git 当前分支 `setup/mysql84-preparation`；无 commit、remote。27 项既有 intent-to-add 标记不是已提交业务文件；其余大量文件仍未跟踪，本轮不 stage/commit。另有 Codex 内部 tree 快照，具体扫描边界见预审报告；未来只推送明确分支，不使用 `--mirror`。
- README 补齐功能、技术栈、分层架构/ER Mermaid、真实 C-04 双端截图、Session/CSRF/RBAC、RC 报名事务、运行与验收矩阵、已知限制。未新增 UI、业务、依赖或数据库改动。
- `.gitignore` 扩展凭据容器、数据库文件、构建/测试缓存；精确忽略零字节临时文件及两张含个人微信头像的桌面原图。未删除文件/夹具/数据库；原图和原验收文件继续本地保留。
- `backend/mvnw.cmd` 只将个人目录内 PowerShell 绝对路径改为调用已有 `pwsh.exe`；JDK、Maven 版本/分发校验和不变。本机 Wrapper 版本调用 PASS；未重新运行全量 Maven、E2E 或模拟器。
- 自有代码未选择开源许可证，已明确展示许可状态；Maven Wrapper 保留 Apache-2.0 声明并补副本。公开展示准备不等于已获得完整依赖许可审计或生产就绪。
- 未发现需轮换的真实凭据；历史安装/浏览器报告仍含本机 Windows 用户目录，属于已披露的低风险隐私残留。受哈希保护的 `install-preflight.json` 不修改；若要求所有历史证据匿名化，应另议保留原件的公开副本方案，不能直接破坏历史保护链。
- C-04 六项风险保留：真实双端本地验收已完成；未部署/正式微信登录；真机和部分原生网络 NOT_RUN；小程序开发链 phin 相关 5 moderate；两端运行时审计在 C-04 时为 0；角色为登录时快照。
- 单主 Agent 执行，未启用 Luna 子 Agent。下一步仅在另行授权后审查具体暂存清单、作者身份和项目许可证选择，再进行首次 commit/remote/push；本轮不执行。

## M1.4-C-04 最终结果与交付入口

- **PASS（本地MVP）**：本轮Maven一次verify70执行项，0失败/错误/跳过；Vue12单测、8真实Edge E2E；小程序23单测、18真实HTTP客户端回归、官方8 WXML/10 WXSS编译。两端npm ci/类型/静态检查/构建、Vue格式、Flyway双库、11数据库/25后端保护、dev/prod JAR均通过。
- 真实Vue与微信原生UI完成创建/编辑/发布/浏览/详情/报名/我的报名/名单/取消/重报/下架/重发布。主活动634 PUBLISHED/v4，容量2/人数1，报名292 CONFIRMED/v3；取消复用原记录。截止2026-10-26 02:05:49北京时间，后续演示新建未来日期活动。
- 视觉：Vue1440/1280/900及700窄宽；微信360/390/430六页、安全区、TabBar、滚动/刷新通过。微信实际Network Offline下取消结果未知、回读不盲目重提，恢复Online及显式重新登录通过；明确下架拒绝uncertain=false。异常活动642/报名295保留CONFIRMED/v1、人数1。
- 自然Session：独立Edge闲置1860秒（10:52:53～11:23:53），0 API请求，随后401、真实重新登录PASS。自然闲置的idle-relogin.png仍显示原列表401；“重新加载”后200/10行由独立真实双标签Logout触发401复验覆盖，不能算作自然闲置样本直接证明。早先中断的闲置测试不计PASS。
- 最终SQL：293活动/270报名/207有效/335用户；人数不一致/超额/重复/孤儿0；开发库0活动/0报名/3用户；probe、V1～V4、C-01活动470/v8和报名197/v5保留；MySQL80无连接或修改。
- 风险：Vue全部/两端运行时审计0，小程序自动化开发链仍5 moderate（phin），未强制降级；角色快照限制保留。真机/正式登录发布/Firefox Safari/屏幕阅读器/新电脑安装/原生长超时及提交后断网NOT_RUN。上午额外双击取消弹窗未确认，不计PASS；重复取消有后端回归覆盖。
- 本轮未改业务源码/依赖/权限/迁移；修正文档过期内容，补齐Windows启动/停止/凭据边界及5～8分钟面试演示。只增加一份独立闲置取证脚本，不重复建框架。原脚本固定历史输出先备份归档再原样恢复。
- 共2 Agent：Astra主执行与最终核验，1 Luna只读文档/覆盖/风险审查；无子Agent数据库操作。完整日志/截图/SQL与NOT_RUN见[C-04报告](verification/m1.4-c-04.md)，运行入口[README](../README.md)，演示[面试笔记](interview-notes.md)。
- 收尾：本轮补验Java25448/Vite3132与对应浏览器已停止，8080/5173/9420无监听，3307正常；包装器退出码1为主动清理。见 `verification/m1.4-c-04/cleanup.json`。未停止MySQL84或操作MySQL80；46份历史证据hash原样保持。后续任何新阶段须重新授权。

## M1.4-C-03 历史结果与续接点

- Vue 管理端 `npm audit` 0；新增 ESLint flat config（Vue 3 + TypeScript，避免格式规则），`lint` PASS。12 单测、类型、Prettier、生产构建、开发服务 HTTP 200 PASS；`npm ci` PASS。
- 小程序 `npm audit` 仍 5 moderate，均由 `miniprogram-automator@0.13.0 → jimp@0.6.8 → @jimp/core@0.6.8 → phin@2.9.3` 的同一重定向敏感请求头漏洞传播；`--omit=dev` 为 0。npm 自动方案降级 automator 至 0.5.1，兼容性风险高，未执行；现有本地截图/自动化路径未发现带敏感请求头访问不可信重定向 URL。C-04 前若更换自动化链，需独立模拟器回归。
- 小程序新增不引入依赖的 `lint`（JS 语法/JSON 解析），类型、23 单测、源码构建、官方编译器 8 WXML/10 WXSS PASS；`npm ci` PASS。两端连续构建产物 SHA-256 集合各自一致。Node 24.22.1 / npm 12.2.0；最低 Node 22.12+。
- 未改业务、认证、数据库、Flyway、后端接口或生产依赖；未提交/push/部署/微信发布。C-02 的角色登录快照限制已记录，认证机制保持原样。后端全量并发和浏览器 E2E 本阶段 NOT_RUN；留 C-04。
- 共 2 个 Agent：主 Agent 分析/修改/验证，1 个 Luna 只读审查小程序依赖与静态检查，无下级或数据库操作。完整命令、结果及剩余风险：[C-03 验收](verification/m1.4-c-03.md)。

## M1.4-C-02 当前结果与续接点

- **PASS：** 首轮69项Maven，补充管理Session超时后Activity16+单元2通过；最终70个执行项覆盖（含4次并发重复，非一次跑70项）。小程序23/Vue12单元、C-01实际客户端HTTP4组、脚本/语法8项通过，业务无FAIL/BLOCKED。
- 安全：真实旧Cookie+CSRF注销/超时后写接口401；错误/跨Session CSRF403；角色/资源归属/冒充身份/状态机通过。默认Session1800秒，单Session缩短后真实失效已验，完整30分钟等待NOT_RUN。
- 事务：8连接最后名额与跨活动首次INSERT屏障各3轮；服务真实RC、报名/取消/容量/状态竞争、确定顺序下架、真实FK/CHECK回滚、1205锁超时后恢复均通过。无生产缺陷或核心事务修改；测试外层回滚事务补显式RC。
- SQL：233活动/216报名/164有效/263用户；全库不一致/超额/重复/孤儿0，新增36活动/38报名/60用户保留。原probe、C-01活动470/报名197不变；开发库0活动/0报名/3用户；V1–V4与MySQL80指纹不变。
- C-01定向回归：真实404不锁未知，同一小程序生产actions实例重发布后可刷新报名；实际取消提交后受控丢响应/回读失败会锁重提，恢复查询CANCELLED/v2。客户端活动556/报名238保留，容量2/人数0/余量2。Node传输证据，不冒充本轮wx.request/UI/真机断网。
- 修改：RegistrationIT、ActivityIT、miniprogram/tests/c02-real-api.cjs、scripts/Test-C02Client.ps1及PLAN/本交接/专项证据。未改生产源码/数据库结构/权限/依赖；没有提交/push/部署/正式发布。
- 共2个Agent：主执行/验收 + 1个gpt-6-luna medium仅只读审查，无下级/数据库操作；新增测试复核无阻塞。证据汇总对象类型/UTC窗口问题已纠正并保留诊断，未影响业务结果。
- [专项报告与复现命令](verification/m1.4-c-02.md)，原始证据目录 `docs/verification/m1.4-c-02/`。定向客户端已有证据拒绝覆盖，未来重跑先人工归档结果，不删夹具。
- 收尾仅停止本轮Java PID98472；包装器非零属于主动清理。8080/5173/9420无监听，3307正常。C-02无必验待办；下一步仅在新授权后开展C-03工程质量/依赖审计，C-04最终UI/MVP交付仍未开始。

## M1.4-C-01 当前结果与续接点

- **PASS：** 15步真实Vue/微信模拟器跨端闭环、下架公开列表/详情及新增报名限制、重新发布及失败恢复。不是独立HTTP脚本或静态截图替代UI；取消确认与错误页重载由真实桌面点击完成。
- 保留活动470（标题前缀M14C01-1792756204468）、报名197/学生1；最终活动PUBLISHED/version8，报名CONFIRMED/version5，人数1/容量3/余量2。额外取证与修复复验周期解释见报告。既有测试学生158负例无新增记录。活动报名截止2026-10-25 19:50:15北京时间，后续不得用过期夹具冒充成功报名。
- 修复：`miniprogram/src/domain/registration.ts` 对明确4xx拒绝且状态回读失败不再锁成未知；既有页面隐藏不可浏览详情。`miniprogram/tests/registration.test.cjs`新增3项。RED 1 fail/6 pass；GREEN小程序23 pass（含TS/build），真实微信重新编译后复验PASS。网络/5xx未知保护和身份epoch不变；无后端/权限/事务/迁移/UI模板修改。
- 新增 `scripts/Test-CrossClientDatabase.ps1`：默认预览、只读SQL、-Apply保存不覆盖的检查点。10个SQL快照PASS；全库人数不一致0/重复0，原probe保留，开发库0活动/0报名/3用户。7项脚本保护及PS7/5.1静态解析PASS；修改文件格式PASS。
- 本轮Session轮换/CSRF/角色/资源归属真实补充检查PASS；最终微信学生1与Vue组织者2隔离。完整安全并发复核仍留C-02，未重跑无关60后端测试。
- 共2个Agent：Astra主执行/修复/验收，1个gpt-6-luna medium只读审查，无下级Agent/数据库写入。审查无Critical/Important/Minor，子Agent未重复运行测试。
- [完整验收与复现](verification/m1.4-c-01.md)，详细证据 `docs/verification/m1.4-c-01/`。工具build后可能仍用缓存模块，须实际点击微信工具“编译”再登录；修复初次复验旧代码失败、最终新代码PASS均如实记录。
- 收尾续接检查8080/5173/9420未监听，3307仍监听；下次先查端口，再按[Vue运行](admin-web-development.md)/[微信运行](miniprogram-development.md)启动。无需安装/初始化/扫码重配，若工具要求扫码由用户操作。
- 无未解决C-01阻塞。真机/完整闲置/原生断网、多尺寸最终视觉NOT_RUN；开发依赖告警和ESLint待C-03，完整MVP回归与交付待C-04。C-02后续已获授权并完成，见本文件顶部。未提交/push/部署/正式发布，保留其他未提交内容。

## M1.4-B 当前结果与入口

- `miniprogram/src`：原生TypeScript/WXML/WXSS六页（home、activities、detail、login、registrations、profile），4项原生tabBar，两公共组件，独立移动端tokens和线性PNG图标。源码构建到ignored dist；运行时不引入npm框架。
- Premium/upstream实际使用，DESIGN.md / UX-CONTRACT.md / m1.4-b-design.md建立移动规范；日期票签、Indigo、卡片、触控/安全区及状态反馈。活动数据来自真实API，首页标明最新排序；无虚构推荐或封面。
- Session/CSRF/RBAC不变；wx.request显式读cookies/Set-Cookie、带JSESSIONID，登录轮换后刷新CSRF。只在内存存身份/凭据；401清私有内容；403不自动退出；本地API只接受devtools平台。
- 报名每活动去重、传报名version。网络未知结果保留目标/原版本，旧版本回读不能解锁；身份epoch阻止旧请求污染。取消/重报保留记录；公开详情404不泄漏下架内容。搜索提交和分页分离，合法括号/单引号等已修复。
- 结果：20unit、18真实API、4特殊字符真实HTTP及4微信搜索、60后端回归PASS；TS/build、官方8WXML/10WXSS编译PASS；Premium audit零错误警告。实际微信模拟器9项完整主流程PASS，0捕获异常；390px六页截图已保存。未把Node transport当wx.request证据。
- 微信工具官方稳定版2.02.2608080位于 `E:\CampusHubRuntime\wechat-devtools`，腾讯签名核验通过；用户已扫码登录并手动开启服务端口，官方UI实际生成测试号。公共配置urlCheck=true，ignored private仅本机模拟器豁免。无需重新安装，也不要索取AppSecret。
- 2026-10-23下午恢复工具时出现“模拟器长时间没有响应”，正常退出/重启后一度恢复并完成4个特殊字符微信搜索，追加视觉脚本又出现automator响应超时；附加验证仍未完成。多屏宽和真实原生断网/恢复NOT_RUN；保留此前成功主流程和截图，不冒充全部视觉检查通过。工具取消确认SDK不可靠，验收通过桌面实际点击完成。
- 开发依赖审计仍5 moderate（automator/Jimp/phin链），0 high/critical；运行时依赖审计0。真机/正式登录/发布、屏幕阅读器和30分钟完整闲置NOT_RUN。
- 后端/API/权限/数据库结构/管理后台业务无兼容修改。最终真实MySQL人数一致、重复0，原probe保留；开发库0活动/0报名/3用户。测试夹具保留，不自动删除。未提交/推送/部署，原工作区其他阶段未提交文件保持。
- 启动说明：[小程序运行](miniprogram-development.md)；证据：[M1.4-B验收](verification/m1.4-b-miniprogram.md)。当前test后端曾由 `Invoke-AdminTestBackend.ps1 -Apply` 启动于127.0.0.1:8080；下次先查端口，不重复启动。密码仍仅外部受限test-demo文件。

## M1.4-A 当前结果与入口

- `admin-web` 已实现真实登录/退出/身份/Session恢复、Dashboard、统一布局、活动筛选分页、创建编辑详情、发布/下架/重发布、人数/余量/报名名单分页、403/404。前端基线 Vue3.5.44、TS5.9.3、Vite8.3.4、ElementPlus2.14.7、VueRouter4.6.4；精确版本锁定package-lock。
- 统一fetch client复用Session/CSRF，401隐藏业务数据并关闭确认框、同身份恢复内存草稿、换身份重建；403/409/超时/网络错误可恢复；表单校验、未保存保护、防重复提交和搜索乱序保护已实现。角色仅优化导航，归属/权限最终由后端负责。
- Dashboard统计来自真实列表total，最近活动按创建顺序；没有新增统计接口或伪造图表。名单仅展示现有DTO中的用户编号/状态/时间。北京时间显式转换，纯文本内容。
- Frontend Design Premium及upstream实际核验；根DESIGN.md、UX-CONTRACT.md、premium-ui.json和CSS tokens建立设计规范。冷灰/白/Indigo、日期签，统一表格/表单/弹层/错误态；没有增加无效功能入口。
- 最终12前端unit、8真实浏览器E2E、60后端回归全部PASS，0失败/跳过；vue-tsc、Prettier、production build PASS。1440/1280/900px实际浏览器视觉复查PASS，Premium audit和design lint零错误/警告。ESLint未配置，Safari/Firefox/真机/屏幕阅读器及完整30分钟空闲等待NOT_RUN，不能冒充全面验证。
- 后端源码/API/数据库结构/权限无改动。联调固定test-app、127.0.0.1:3307/campushub_test。开发库保持0活动/0报名/3用户，人数一致、重复组合0，原probe保留；测试夹具保留，最终M14A前缀活动52条。每轮名单分页另创建11个独立测试学生，仅在MySQL内复制既有test学生哈希，不打印秘密、不自动删除。
- 追加授权：用户明确“你自行启动”后，通过标准UAC启动原MySQL84CampusHub；未修改服务配置、MySQL80、ACL或权限。该服务保持Running，应用临时进程在验收后停止。无Git提交/推送/部署，保留原工作区未提交内容。
- 启动：先在一个PowerShell7终端执行 `scripts/Invoke-AdminTestBackend.ps1 -Apply`，另一终端执行 `npm.cmd --prefix admin-web run dev`，访问127.0.0.1:5173；测试账号密码只在外部受限test-demo文件。脚本默认预览，8080占用拒绝，不自动启停数据库服务。
- 入口：[运行说明](admin-web-development.md)、[验收及截图](verification/m1.4-a-admin.md)、[设计/执行计划](m1.4-a-design.md)。**停止于M1.4-A，不自动实施小程序。**

## M1.3 历史结果与入口

- 两库 V4 migrate/validate PASS，checksum `653386617`；V1/V2/V3 不变，原 probe 完整保留。新增 activity_registration、唯一(activity_id,user_id)、外键/状态/版本约束；activity 增加 registered_count 及 `0<=人数<=容量` CHECK。
- 学生 POST `/api/v1/activities/{id}/registrations` 报名、同路径 `/cancel` 取消，body `{version}`；GET `/activities/{id}/registration` 本人状态；GET `/student/registrations` 个人分页；GET `/manage/activities/{id}/registrations` 授权名单。组织者只本人活动，管理员沿用全活动范围。所有身份来自 Session，保留 CSRF/RBAC。
- 报名窗口左闭右开；活动开始前可取消，包括下架期间。取消后重报复用原记录并重验状态/窗口/余量。NONE/0→CONFIRMED/1→CANCELLED/2→CONFIRMED/3；重复同状态不改人数，旧取消版本不能误取消新报名。
- 人数与明细同事务，先 activity 行锁再 registration 行锁。报名写事务明确 READ_COMMITTED，避免不同活动首次报名的 RR 缺失键 gap lock 死锁；该问题已由真实双事务 INSERT 屏障复现并修复。外层组合调用须同样 RC。详情/列表保留默认只读快照。
- 活动详情增加 registeredCount、remainingCapacity、myRegistration；管理详情/列表增加人数。M1.2 编辑也锁活动行并限制容量不小于人数；保留已发布冻结、活动状态机及活动 version 语义，报名不增加活动 version，重发布不写人数。
- 用户另明确批准两个 app 各自新表 SELECT/INSERT/UPDATE；无 DELETE/DDL/全库/跨库/GRANT OPTION。activity 原有权限足够，其他权限未变。前后完整 SHOW GRANTS 和两个 app 的 SELECT 成功、DELETE/ALTER/跨库拒绝均通过。授权入口 `Grant-RegistrationAccess.ps1` 默认预览，非未来阶段通用授权。
- 验收：60 Maven tests（2 单元+58真实 MySQL/HTTP，含19报名测试）0 fail/error/skip；M1.1/M1.2 全部回归。数据库11 PASS、保护25 PASS、25个PS文件双版本解析0错误、打包dev/prod PASS。独立只读审查的重要问题已通过 RED→GREEN 修复。
- 最终开发库0活动/0报名/3原用户；测试库78活动/88报名/115用户，67有效报名；全部活动人数与有效明细一致、重复组合0。M1.3 随机测试夹具保留，无新表 DELETE，不自动清理；旧 probe、MySQL80原指纹不变。临时Java子进程已退出，无提交/推送/部署。
- 入口：[设计](m1.3-design.md)、[验收](verification/m1.3-registrations.md)、[权限文档](database-permissions.md)、[面试笔记](interview-notes.md)、[API操作](backend-development.md)。M1.3当时完成即停；后续管理端结果见顶部M1.4-A。

## M1.2 历史结果与入口（M1.3 增量以上方为准）

- 两库 V3 migrate/validate PASS，checksum `-852546724`。新增 activity，包含创建者/分类外键、状态/容量/时间 CHECK 与查询索引。V1 `1160888031`、V2 `436036048` 未变，原 probe 完整保留。
- `GET /api/v1/activities` 与 `/{id}` 供登录后的三角色浏览，只返回 PUBLISHED，草稿/下架详情统一 404；列表 page 1..10000、size 1..100，支持 categoryId、keyword（字面匹配）、status=PUBLISHED。
- `/api/v1/manage/activities`：GET 列表、POST 创建；`/{id}` GET 详情、PUT 全量编辑；`/{id}/publish` 和 `/unpublish` POST 状态变更。组织者仅本人、管理员全部，学生 403。身份和创建者来自 Session principal，客户端身份字段无效。
- 状态 `DRAFT -> PUBLISHED -> UNPUBLISHED -> PUBLISHED`。发布须完整校验，报名未截止且活动未开始；已发布字段冻结，管理员也须先下架再编辑。状态变更先取得行锁再重验时间。编辑/变更均必须携带 version，条件更新冲突 409。
- 业务时间采用带时区 ISO-8601 字符串、整秒、UTC、年份 1000..9999；报名开始 < 报名结束 <= 活动开始 < 活动结束。容量 1..2147483647；文本和整数严格 JSON 类型。JDBC 4.2 LocalDateTime 映射避免历史历法偏移。完整契约见 [M1.2 设计](m1.2-design.md)。
- 开发 app 仅新增 activity SELECT/INSERT/UPDATE；测试 app 活动表 DML。其他表、迁移账号最小权限不变；迁移/授权凭据不进入业务进程。新增 `Grant-ActivityAccess.ps1` 默认预览，本轮已显式授权完成。
- 验收：41 Maven tests（2 单元、39 真实 MySQL/HTTP，其中 15 个 ActivityIT），0 fail/error/skip；原 M1.1 全部回归通过。数据库专项 9 PASS、保护测试 23 PASS、24 个 PS 文件双版本解析 PASS，打包 dev/prod 验证 PASS。
- 独立审查的类型转换、锁等待跨截止、历史日期偏移均已通过失败用例复现并修复。证据：[M1.2 验收](verification/m1.2-activities.md)。两库活动数均为 0、M1.2 临时用户数均为 0；业务测试仅写 test，按 ID 清理自身夹具，未清空已有数据。
- 打包验收临时 Java 子进程已退出；无服务操作、无 MySQL80 修改、无提交/推送/部署。**M1.2 完成即停**，报名事务、人数扣减、Vue/小程序、正式微信均未开发。

## M1.1 历史结果与认证入口（继续有效）

- 两库 V2 已执行并 validate：checksum `436036048`；新增 `app_user/app_role/app_user_role`，三个角色；V1 未改，原 probe 保持。演示账号由独立脚本配置，不写入迁移 SQL。
- Security 7.1.1 使用数据库密码认证 + Session + CSRF；登录轮换、30 分钟空闲过期、退出撤销。身份由后端查询，userId/role 参数不能冒充。三角色无隐式继承，权限矩阵见 [设计](m1.1-design.md)。
- 显式演示启动：`scripts/Invoke-Backend.ps1 -Action Run -Profile dev -EnableDemoLogin -Apply`；不带开关默认关闭。prod/混合/未知 profile/非 loopback 误启均启动失败。
- 登录账号：`demo_student`、`demo_organizer`、`demo_admin`；开发/测试密码各自独立随机生成，只在原受限 secrets 目录的 `campushub-demo.json` / `campushub_test-demo.json`，不得打印、复制到仓库或聊天。数据库只有 PBKDF2 哈希。`Set-DemoAccounts.ps1 -Apply` 已执行；重复调用只核验，冲突停止，不重置。
- API：GET `auth/csrf` → 带返回的 CSRF header、Cookie POST form `auth/demo-login` → GET `auth/me`；登录后重新 GET CSRF，再 POST `auth/logout`。均在 `/api/v1/` 下。三个 `student/organizer/admin/access` 接口只验证能力，不表示活动业务已实现。
- 业务 app 仅分类和三个认证表 SELECT；test-app 可对分类、用户、关系表 DML，对角色表仅 SELECT。迁移、授权、业务凭据仍隔离，JAR 不含 Flyway。
- 验收：26 Maven tests（2 单元 + 24 真实 MySQL/HTTP），0 fail/error/skip；9 数据库专项、21 脚本保护；23 个 PS 文件在 7.6.5/5.1 解析通过。打包 JAR 的 dev/prod 和开发库三角色链路均通过，临时 Java 子进程已退出。独立审查问题已修复并复核，无剩余 Critical/Important。
- 操作：[后端开发操作](backend-development.md)。证据：[M1.1 验收](verification/m1.1-authentication.md)。未提交、推送或部署，未改服务/ACL/执行策略，未操作 MySQL80；验收只读取既有指纹。
- 后续边界：Session 中角色为登录时快照；未来微信须服务端 code 交换与 openid 映射，再创建受信任 principal。正式微信、角色实时撤权、限流、报名、管理端和小程序均未实施；活动已由后续 M1.2 完成。M1.1 当时完成即停。

## M1.0-C 历史结果（当前入口以顶部 M1.4-A 为准）

- `backend` 为可构建、可运行的 Java 21 / Boot 4.1.1 工程，MyBatis 4.1.0、springdoc 3.1.1、Wrapper Maven 3.9.16；Flyway 12.4.0、Connector/J 9.7.0、Security 7.1.1、Spring 7.0.9 由 Boot BOM 管理并已实际解析。
- 两库均有 `activity_category` 和 `flyway_schema_history`；V1 checksum `1160888031`，各有四条分类。测试库另保留原 `m10b_unicode_probe` 和原证据。首次测试库经严格结构/内容核对后显式 baseline 0；不使用 baselineOnMigrate，不能重复 baseline。
- 开发 app 仅分类表 SELECT；test-app 仅测试分类表 SELECT/INSERT/UPDATE/DELETE；两个迁移账号仍保留原八项权限，无 DROP/全局管理/跨库权限。应用 JAR 不含 Flyway 和迁移 SQL，运行时只注入 app 密码。
- GET 分类和 health 公开，其余匿名路径默认 401（用户本轮要求优先于旧未知路径 404 规划）。dev 文档公开，prod 文档映射在已认证测试下实际 404。尚无登录方式、默认演示用户或三角色业务。
- `Invoke-Backend.ps1 -Action Verify -Apply` 完整构建、测试、打包；`-Action Run -Profile dev -Apply` 启动 JAR，默认 127.0.0.1:8080。使用普通 PowerShell 7。配置子进程 JDK，不修改全局 Java；每个写操作入口默认预览。
- 验收包括真实 MySQL、MyBatis、Unicode/回滚、DDL/history/probe/跨库拒绝、精确授权、checksum 拒绝、生产文档关闭、打包 dev/prod 启动、原 MySQL80 指纹不变。独立审查发现的 SPRINGDOC 环境继承问题已加失败用例、修复和复验。
- 详细命令：[后端开发操作](backend-development.md)；准确统计、失败修复记录与范围：[M1.0-C 验收](verification/m1.0-c-backend.md)。验收临时应用进程已退出；两个原数据库服务保持运行。未提交、推送或部署。

三角色认证由M1.1完成，活动管理由M1.2完成，报名事务由M1.3完成，Vue管理端由M1.4-A完成，原生学生端由M1.4-B完成；当前结论以上方M1.4-C-03结果及限制为准。

## 1. 项目、技术栈与最终目标

CampusHub 是用于求职面试展示和长期维护的校园活动全栈项目，最终提供微信小程序、管理端、真实后端与持久化数据库。M1 目标是三角色认证及活动创建、发布、查询、报名、取消的完整链路，正确处理重复报名、满员、截止和权限不足；不能用静态页面、虚假成功接口或模拟数据库代替。

已批准且实际构建验证的技术基线：Java 21、Spring Boot 4.1.1、MyBatis Spring Boot Starter 4.1.0、MySQL 8.4.11 LTS、Spring Security、Flyway、springdoc-openapi 3.1.1、Maven 3.9.16。Spring 生态依赖遵循 Boot parent/BOM，不手动覆盖核心组件版本。

开发环境为Windows原生，默认不依赖Docker。管理端已有Vue3真实业务实现；小程序已有原生学生端与真实模拟器核心流程，正式微信登录与上线未实施。

## 2. 当前阶段与已完成工作

- 已建立 Git 仓库、工程规范、阶段计划、环境文档、独立 MySQL 配置、初始化/授权 SQL 模板和 PowerShell 脚本。
- M1.0-A-Fix 已完成可信安装包校验、敏感输出保护、脱敏失败诊断及保护条件测试。安装前记录为原 25 项加新增 24 项保护测试通过，外层启动器另有 5 项检查；这些不是数据库业务集成测试。
- M1.0-A 已完成系统数据目录初始化、限定 ACL、Manual / LocalService 服务注册和安装后验收。
- M1.0-B 于 07:11 完成一次数据库及账号初始化，07:13 完成真实数据库专项 13 PASS / 0 FAIL。两个业务库、四个独立账号已存在；root 密码已轮换，旧临时密码实际认证被拒绝。
- M1.0-B完成时开发库无表、测试库仅有probe；M1.0-C当时新增分类表和Flyway历史。后续后端业务与admin-web已完成，当前结果见顶部M1.4-A。
- 两个服务均 Running，旧配置哈希匹配安装前基线。本轮未连接 3306/33060，未修改任何服务、ACL 或执行策略。`secrets` 现有六个受限客户端文件，禁止打印或提交。

## 3. MySQL 8.4.11 实际安装验收

正式验收记录时间为 2026-10-19 06:51：**16 PASS、0 FAIL、0 BLOCKED、4 NOT_RUN**。

| 项目 | 实际结果 |
|---|---|
| 新服务 | `MySQL84CampusHub`，Running / Manual / `NT AUTHORITY\LocalService` |
| 版本与监听 | 程序与 TCP 握手均为 8.4.11，仅监听 `127.0.0.1:3307`；X Protocol 关闭 |
| 程序 / 配置 | `E:\CampusHubRuntime\mysql84\server` / `E:\CampusHubRuntime\mysql84\config\my.ini` |
| 数据目录 | `E:\CampusHubRuntime\mysql84\data`；安装阶段验证配置、文件和路径隔离；M1.0-B 已通过认证 SQL 核实 `@@datadir` |
| 包完整性 | 官方 ZIP、官方 MD5、本地固定 SHA-256、GPG 签名和独立核对的官方完整公钥指纹均通过 |
| 目录与日志 ACL | 实际检查通过；LocalService 仅对 data/logs/tmp 有 Modify，对程序和配置为 ReadAndExecute，对 secrets/diagnostics 无 Allow |
| 原实例 | MySQL80 正常运行，握手 8.0.44，3306/33060 正常，配置 SHA-256 与基线一致 |
| 执行策略 | 标准 UAC 由用户亲自确认；受信任 PS7 子进程仅使用进程级 RemoteSigned；永久策略未改变 |

该历史报告的 4 项 NOT_RUN 不回写覆盖。新实例认证 SQL、业务库和账号已由 M1.0-B 独立证据补齐，Flyway/后端已由 M1.0-C 补齐；旧实例认证 SQL、主动启停配合 SQL 的隔离测试仍未执行。

安装后系统曾重启，Manual 服务未自动启动；之后仅启动了现有新服务，没有重复安装。首次验收的日志读取器因共享模式产生 IOException，改为只读 FileStream + FileShare.ReadWrite 后通过，原失败记录保留。不要为重跑全新安装测试清理现有 runtime。

详细证据只引用文件：[安装验收报告](E:/Project/CampusHub/docs/verification/m1.0-a-install.md)、[最终验收 JSON](E:/Project/CampusHub/docs/verification/mysql84-install-acceptance-final.json)、[首次验收记录](E:/Project/CampusHub/docs/verification/mysql84-install-acceptance.json)、[Fix 测试证据](E:/Project/CampusHub/docs/verification/preparation-fix-result.json)。

## 4. 原 MySQL80 保护要求

禁止停止、重启、修改、重置、升级、卸载或删除 MySQL80；不得改变其账号、数据、配置、ACL 或 3306/33060 端口配置。

- 原配置：`C:\ProgramData\MySQL\MySQL Server 8.0\my.ini`。
- 原数据：`C:\ProgramData\MySQL\MySQL Server 8.0\Data`。
- 对比基线：[install-preflight.json](E:/Project/CampusHub/docs/verification/install-preflight.public.json) 的 `Environment.LegacyFingerprint`；旧配置哈希已保留在记录中，无需重新编造基线。
- 只允许已授权的只读核验。旧账号密码不得发到聊天或写进仓库；没有安全可用凭据时，旧实例 SQL 保持待验证。

## 5. 尚未完成的功能

- M1.0-C 最小后端、M1.1 用户角色/认证、M1.2 活动管理与 M1.3 报名事务已完成。
- 管理端及原生小程序核心联调已完成，微信真机测试NOT_RUN。
- M0 的完整 PRD、架构/ER/API、页面/UI、优先级、里程碑、风险和面试材料；正式微信登录、预约、签到、AI 留待后续阶段。

## 6. M1.0-B 历史完成状态（当前状态以顶部 M1.4-A 为准）

**本轮已获授权并完成；不得重跑 `Initialize-Database.ps1 -Apply`。** 凭据和数据库均已存在，冲突保护应拒绝重复初始化，不能通过删除文件或库来绕过。

| 具体任务 | 实际结果 |
|---|---|
| 环境与脚本 | PASS：服务、3307、配置/数据目录、secrets/diagnostics ACL 和旧实例基线通过；未重新安装 |
| 日志读取修复 | PASS：非敏感夹具先复现 IOException，再改用 FileStream + FileShare.ReadWrite；未停服务或放宽 ACL |
| root 轮换 | PASS：本机随机生成并受限保存；轮换后认证核实版本、端口、数据目录和账号；原临时密码返回 1045 |
| 两库及四账号 | PASS：utf8mb4 / utf8mb4_0900_ai_ci；四账号限定 @127.0.0.1，独立密码，精确权限矩阵与跨库拒绝通过 |
| 真实读写 | PASS：仅测试库专用验收表，中文、emoji、补充平面及组合字符提交后跨连接读取和字节比对通过；回滚通过 |
| 凭据及连接 | PASS：五组当前随机凭据、六个客户端文件（包含失效 root-initial），文件 ACL 核实，五组当前连接均使用 TLS；旧实例基线不变 |

初始化阶段权限矩阵：

| 账号 | 目标库 / 权限 |
|---|---|
| `campushub_migrator` | 仅 campushub：SELECT / INSERT / UPDATE / DELETE / CREATE / ALTER / INDEX / REFERENCES |
| `campushub_test_migrator` | 仅 campushub_test：同类库级权限，不可访问开发库 |
| `campushub_app` | 初始化时仅 USAGE；M1.0-C 已仅对 campushub.activity_category 授 SELECT |
| `campushub_test_app` | 初始化时仅 USAGE；M1.0-C 已仅对测试分类表授 SELECT/INSERT/UPDATE/DELETE |

未授全局管理权限、GRANT OPTION 或 DROP。测试库授权中的下划线已转义，实际 `partial-revokes=OFF`。现有库、账号、凭据与验收表应保留，不覆盖、接管或自动清理。

`Test-MySql84.ps1 -Stage Bootstrap` 已实际执行，6 PASS / 1 BLOCKED / 2 NOT_RUN，退出码 2；BLOCKED 仅是未提供凭据的旧实例 SQL。新实例专项 `Test-DatabaseBootstrap.ps1 -Apply` 为 13 PASS / 0 FAIL，退出码 0。后者会写入专用测试表，已执行完成，不得重跑或删除证据表；其默认调用只预览。应用账号在 Bootstrap 阶段仅 USAGE，不能要求它成功选中业务库。

证据：[M1.0-B 报告](verification/m1.0-b-bootstrap.md)、[初始化](verification/m1.0-b-initialize.json)、[只读账号验收](verification/m1.0-b-accounts.json)、[真实数据库专项](verification/m1.0-b-integration.json)。保护测试和静态检查独立列出，不与数据库测试混算。

**M1.0-B/C当时均完成即停。** 后续M1.1、M1.2、M1.3、M1.4-A及本次M1.4-B均另获授权实施，当前停止于M1.4-B，不自动进入M1.4-C。

## 7. 重要路径与安全限制

| 用途 | 文件或目录 |
|---|---|
| 工程规范与阶段计划 | [AGENTS.md](E:/Project/CampusHub/AGENTS.md)、[PLAN.md](E:/Project/CampusHub/PLAN.md) |
| 环境与操作说明 | [environment.md](E:/Project/CampusHub/docs/environment.md)、[mysql84-operations.md](E:/Project/CampusHub/docs/mysql84-operations.md) |
| 初始化与验收入口（初始化已完成） | [Initialize-Database.ps1](E:/Project/CampusHub/scripts/Initialize-Database.ps1)、[Test-MySql84.ps1](E:/Project/CampusHub/scripts/Test-MySql84.ps1)、[Test-DatabaseBootstrap.ps1](E:/Project/CampusHub/scripts/Test-DatabaseBootstrap.ps1) |
| 已执行初始化 / 授权 SQL | `infra/mysql/sql/01-create-databases.sql`、`02-create-accounts.sql.template`、`03-grant-migration.sql`；M1.0-C 已执行 `04-grant-application.sql`；V1 位于 `infra/mysql/migrations` |
| 公共模块与固定配置 | [CampusHub.MySql.psm1](E:/Project/CampusHub/scripts/lib/CampusHub.MySql.psm1)、[mysql84.psd1](E:/Project/CampusHub/infra/mysql/mysql84.psd1)、[my.ini.template](E:/Project/CampusHub/infra/mysql/my.ini.template) |
| 冻结安装基线与历史交接 | [install-preflight.json](E:/Project/CampusHub/docs/verification/install-preflight.public.json)、[mysql84-install-handoff.md](E:/Project/CampusHub/docs/mysql84-install-handoff.md) |
| 官方 ZIP | `E:\Project\CampusHub.local\packages\mysql-8.4.11-winx64.zip`（仓库外） |
| 敏感凭据、日志与诊断 | `E:\CampusHubRuntime\mysql84\secrets`、`logs\mysql84.err`、`diagnostics`；不复制敏感内容到交接文件 |

写操作默认预览，显式 `-Apply` 才执行；不修改冻结安装源码/校验基线以跳过检查，不关闭包签名或权限保护。不自动清理目录、删除服务、重置数据、递归放宽 ACL 或重复失败初始化；MySQL DDL 不提供整个初始化脚本的事务回滚保证。

不修改全局 PATH、JAVA_HOME、防火墙、系统安全策略或 Codex 沙盒配置。管理员操作仅在明确范围内通过标准 UAC 由用户亲自确认；强制策略存在时停止，不使用 Bypass / Unrestricted。普通文件与只读工作按已授权范围主动完成。

## 8. 新对话必须沿用的技术决策

- 本地完整演示必须是真实全栈。严禁 H2、Mock 数据或跳过测试冒充真实 MySQL 集成通过；业务测试只能写 campushub_test，并先核对库名、3307 和目标实例。
- 数据库迁移与业务运行分离：显式开发/部署步骤运行 Flyway；日常 Spring Boot 仅持有 app 凭据，禁用自动迁移，不继承 migrator/root 凭据。迁移失败或 checksum 不符即停，不自动 repair/clean 或改历史求通过。
- 后端已采用 Boot 4.1 的 webmvc Starter、Jackson 3 和测试 API；未经确认不降级 Boot。Wrapper 已创建，使用 `D:\develop\jdk`（实际 21.0.2）及 Maven 3.9.16，不依赖全局 Java 指向。
- 三角色演示登录仅在明确开发配置启用，仍经过后端认证与权限校验；生产禁用，不允许任意用户 ID 绕过。保留正式 wx.login / AppID / AppSecret / 服务端身份交换边界，不能因正式微信登录阻塞核心业务。
- 微信开发者工具的合法域名校验豁免仅限本地开发，不能当作真机或发布方案；后续验收明确区分本地、开发者工具和真机结果。
- 当前 Git 分支为 `setup/mysql84-preparation`，尚无 Git 提交；文件已在当前工作区磁盘上。新对话继续使用同一目录，不 reset/clean 或切换到不含这些未提交文件的工作区。本次未提交或推送。
- 阶段命名按本次交接与 PLAN.md：M1.0-B 首先只做数据库及账号初始化。历史报告、脚本说明中把 Flyway/后端称作 M1.0-B 的文字是旧规划，不构成当前授权；实际行动始终以用户最新指令为准。
