# CampusHub UX Contract

## Product context and business sources

ORGANIZER/ADMIN 的中文活动管理后台。来源：用户 M1.4-A 简报、docs/m1.1-design.md、docs/m1.2-design.md、docs/m1.3-design.md 及实际 Controller/Service；核对日期 2026-10-21。活动状态和资源权限以后端为准，学生直接进入 403。北京时间 Asia/Shanghai、公历、zh-CN，WCAG 2.2 AA 为目标。

## Visual contract

根目录 DESIGN.md，runtime canonical 为 admin-web/src/styles/tokens.css，Element Plus 变量引用语义令牌，design.test.ts 验证镜像。仅浅色主题；无既有 sibling，新增列表/名单作为相互比较基准。

## Canonical UI Map

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
|---|---|---|---|---|
| Select/Listbox | Element Plus ElSelect/ElOption | DESIGN.md | authored，中文选项，trigger 等宽 popup | browser keyboard/popup |
| Date | src/components/DateField.vue | M1.2 时间契约 | typed 北京时间，无宿主 calendar | unit + E2E |
| Form | src/components/FormField.vue + domain/activity.ts | API + 本契约 | create/edit/login | unit + E2E |
| Scrollbar | src/styles/tokens.css | DESIGN.md | 表格自身横向滚动 | computed style |
| Toast | src/ui/feedback.ts | 本契约 | success | live region + E2E |
| CRUD | src/api/activity.ts + router.ts | M1.2 | create/edit 回原列表；状态变更留详情 | full-flow E2E |

## Dataset navigation

活动搜索/分类/状态/page/size 持久化 URL。默认 10 条、可选 10/20/50；服务端分页，过界回最后一页；报名列表 page/size 独立 URL 参数。搜索 300ms debounce，IME 合成期间不发请求，清除/Enter 即时，AbortController 与序号避免旧响应覆盖。无 bulk 或选择表格需求。表格 loading/error/empty 保持基本高度，隐藏失效数据并给重试入口。

## Flow ledger

| Operation | Pending | Success | Failure recovery |
|---|---|---|---|
| 登录 | 禁用提交 | 服务端 me 身份后进入安全返回路由 | 行内错误，保留账号，清空密码 |
| 创建/编辑 | 禁用重复提交 | 回保留筛选的列表 + 成功提示 | 保留输入，字段错误/业务 banner |
| 发布/下架/重新发布 | 明确命名确认框 + 禁用按钮 | 留详情，使用服务端新 version | 常驻错误；409 手动刷新，不自动重放 |
| 查询 | 共享加载态 | 展示新数据 | 重试；无旧页面数据伪装最新 |
| 取消/返回 | 脏表单离开确认 | 回列表 | 取消确认继续编辑 |

## Navigation and responsive behavior

各路由中文 document.title；跳页焦点落主区域标题，路由 403/404 保留导航和退出。760px 以下导航顶部化；表格独立横滚、不裁剪字段和操作；长文本自然换行。列表状态通过 query 传递到详情与表单，并带回原列表。

## Overlays and feedback

ElMessageBox 提供焦点陷阱、Escape、焦点恢复；下架提示报名不删除。ElMessage 统一成功反馈与去重；关键错误在当前页面保留。未保存更改通过路由确认；浏览器关闭由 beforeunload 原生离页警告（仅平台数据丢失防护，不是产品 confirm）。

## Async and resilience

所有写入等待服务器，不做 optimistic/自动 retry；15 秒 timeout。写入网络/超时结果未知必须回读确认，创建按钮锁定直到离开核实，避免重复活动。401 清除身份并隐藏受保护页面，在同一页以登录层恢复，内存草稿仅同用户可恢复；其他身份清空草稿并回工作台。无敏感数据 localStorage/sessionStorage 持久化。主动退出清空状态。

409 保留编辑并提供重载选项，不变更本地 version 以绕过冲突。403 显示权限边界，名单不保留旧活动结果。请求取消不显示网络错误。网络故障手动重试；401 以外错误不误判退出。

## Validation

共享 validateActivity：必填/长度/整数/容量及时间顺序，错误与 label/aria-describedby 关联，提交定位首错；后端仍最终校验。表单 novalidate，textarea resize none + 足够行数。详细内容按文本渲染，禁止 v-html。密码隐藏、autocomplete=current-password，可粘贴，不记日志。

## Verification

typecheck、production build、Vitest、真实 Playwright E2E、后端 Maven verify。浏览器 1440/1280/900，键盘、无结果、加载、失败、慢请求、Session、选择 popup 和减少动画。Premium audit 仅静态证据。实际结果统一写 docs/verification/m1.4-a-admin.md；无生产部署、无小程序。

## M1.4-B 原生学生端契约

业务来源不变，用户新授权增加学生端；后端匿名活动浏览仍为401。首页、活动、我的报名、我的使用原生 tabBar；登录/详情使用 navigateTo/navigateBack。页面 onShow 重新验证服务器身份；冷启动不持久化 Session。搜索/分类只保留当前小程序内存，原生页面无 Web URL query；详情 id 用页面 query 传递。搜索明确提交，避免中文 IME 半成品查询；每次查询递增序号阻止旧结果覆盖。分页每页10条，触底和“加载更多”共用锁；未提交的输入不会改变当前分页查询。

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
|---|---|---|---|---|
| Native category filter | pages/activities + 原生button | 服务端分类 | 首页跳转、广场单选 | simulator |
| Native date | miniprogram/src/domain/activity.ts | ISO-8601 + 北京时间 | 卡片、详情、报名记录 | unit + simulator |
| Native form | pages/login + app.wxss | M1.1 | 学生演示登录 | simulator |
| Native scrollbar | 微信平台文档滚动 / scroll-view | 本契约 | 分类横向、页面纵向 | simulator widths |
| Native toast / confirm | miniprogram/src/ui/navigation.ts | 本契约 | 成功toast、取消showModal | simulator |
| Native registration | miniprogram/src/domain/registration.ts | M1.3 | 报名、取消、核实 | unit + real API + simulator |

所有业务写入非乐观、不自动重放。每活动去重，使用报名 version，不使用 activity version。HTTP业务拒绝展示原文并重新查询；网络/超时/5xx可能提交，保留操作目标和原版本，回读同版本非目标状态仍是未知，只有目标状态或更新的版本才能解除未知锁。记录身份epoch，退出后旧请求不得改变新用户状态。我的报名接口只有本人记录，以公开详情补充；下架/草稿详情404时展示编号与“已不可浏览”，允许按本人记录取消，服务端最后检查时间。不会调用管理接口填充隐蔽内容。

401同时清空身份、列表、总数、反馈、busy、未知状态；403显示角色边界，不自动判定退出。非学生登录会尝试服务端退出，客户端不接受其进入学生端。退出网络失败时不宣称服务器撤销成功，保留明确重试。密码失焦离页清空，不写storage、不打印；Cookie与CSRF只存请求模块内存。URL限定固定loopback源，wx平台非devtools直接拒绝本地演示。合法域名豁免仅项目private本地配置，公共配置保持校验，不作真机/上线方案。

验证证据：docs/verification/m1.4-b-miniprogram.md。Node transport真实HTTP、官方WXML/WXSS编译器、实际微信模拟器三者分开报告；测试可独立mock，但业务验收不依赖mock。Web原生alert/confirm禁令不适用于微信官方wx.showModal这一明确的平台变体。
