---
version: alpha
name: CampusHub
description: 清晰、有秩序的校园活动管理工作台
colors:
  primary: "#4f46e5"
  background: "#f6f7fb"
  surface: "#ffffff"
  text: "#182239"
  muted: "#606b80"
  border: "#e4e7ef"
  success: "#187658"
  danger: "#b42318"
typography:
  sans:
    fontFamily: "'Segoe UI', 'Microsoft YaHei', sans-serif"
  display:
    fontFamily: "'Bahnschrift', 'Microsoft YaHei', sans-serif"
  mono:
    fontFamily: "'Cascadia Code', Consolas, monospace"
rounded:
  DEFAULT: "10px"
  sm: "6px"
  lg: "16px"
spacing:
  section-gap: "24px"
  page-max: "1280px"
components:
  button:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
  dialog:
    textColor: "{colors.text}"
    backgroundColor: "{colors.surface}"
  table:
    textColor: "{colors.muted}"
    backgroundColor: "{colors.surface}"
  input:
    textColor: "{colors.text}"
    backgroundColor: "{colors.surface}"
  divider:
    backgroundColor: "{colors.border}"
  status-success:
    textColor: "{colors.success}"
  error:
    textColor: "{colors.danger}"
---

# CampusHub Design System

## Overview

校园活动策划者的编排桌：日期签、清晰的活动状态、克制的 Indigo 导航。面向中国校园组织者和管理员，中文产品界面，桌面为主兼容平板。目标是快速了解活动进度并完成发布和名单查看；不使用营销大图或无来源图表。

本文件 seed 来自 M1.4-A 用户简报与现有 API，暂无旧前端可复用。表达重点集中于工作台的日期签及标题，其他控件保持熟悉和安静。不采用默认 Element Plus Demo 排版、大面积渐变、玻璃拟态。

Token ownership：runtime canonical（Model B）。`admin-web/src/styles/tokens.css` 为唯一运行时数值来源，以上值镜像其语义令牌；Element Plus 变量仅引用 `--ch-*`。`src/domain/design.test.ts` 验证核心镜像值，组件不建立独立 palette。

## Colors

primary 为主操作/焦点/当前导航；background 为画布，surface 为卡片；text/muted 形成两级内容层次；border 只划分结构。success 表示已发布，danger 用于错误与下架确认。选中态使用浅靛蓝，草稿采用灰色，下架采用琥珀色，均配文本。仅实现浅色主题，forced-colors 保留系统色。

## Typography

界面使用 Segoe UI / Microsoft YaHei，本地字体无需外网。品牌和数字用 Bahnschrift，编号用 Cascadia Code；中文保持相同无衬线回退。标题 28/22/18px，正文 14px、行高 1.6，辅助 12px；数字等宽，长标题换行、正文预格式换行但不注入 HTML。

## Layout

8px 主节奏，辅助 4px；桌面侧栏 232px，Header 72px，主区最大 1280px，内边距 32px。1100px 以下收窄侧栏，760px 以下采用顶部导航。表格滚动归属各自面板，表单文档自然滚动。卡片间距 24px，输入高度 40px。所有尺寸使用共享 tokens 或有意义布局规则。

## Elevation & Depth

卡片以细边框为主，轻阴影只用于卡片和浮层。无背景模糊和多重装饰。弹层层级由 Element Plus 管理，焦点与滚动锁由库组件负责。

## Shapes

控件 10px，卡片 16px，徽章小胶囊，日期签 10px。图标使用 Element Plus icons，线条式，旁有文本，纯图标具有中文 accessible name。

## Components

### Foundational visual states

hover 轻底色，focus-visible 2px 靛蓝描边并留白，active 深一档；disabled 降饱和并说明原因。busy 固定按钮尺寸并显示库加载图标，PageState 统管页面加载/错误/空态。无骨架屏和假进度。

### Buttons and actions

每区域一个主要操作；次要动作描边，导航用链接。发布/重新发布均确认，说明学生可见性；下架说明不删除报名。保存无需二次确认。统一提示模块使用 Element Plus Message，关键错误同时常驻页面。

### Navigation and data display

侧栏仅工作台与活动管理，不放无实现入口。面包屑体现层级。原生语义 table + 有界分页；名单与活动列表共用表格与分页风格。状态有文字，人数以有效报名/容量呈现。

### Forms and overlays

表单分基本信息、时间安排、报名设置。所有字段显式 label、help、error；select 由 Element Plus ElSelect/ElOption 提供，日期采用统一 typed DateField（北京时间 YYYY-MM-DD HH:mm:ss），规避宿主时区隐式转换。Element Plus MessageBox 统一确认与未保存保护；表单失败保留输入。

### Motion

仅 120ms hover/颜色过渡；prefers-reduced-motion 取消动画。全局 scrollbar thumb/track/hover/active 令牌，系统高对比模式保留可操作滚动条。

### Content and data visualization

中文动作使用“创建活动”“保存修改”“发布活动”“下架活动”“重新发布”；报名人数不混入取消记录。没有统计接口时不用假图表、增长百分比或总报名量。

## Do's and Don'ts

- Do：列表、详情、表单、名单共享一致的内容宽度与反馈。
- Do：显示数据范围、北京时间和不可操作原因。
- Don't：通过颜色单独表达状态，或给不可用功能留按钮。
- Don't：展示密码、令牌、未授权个人资料或伪造数据。

## 微信原生学生端（M1.4-B）

移动端面对中文校园学生，product register；不改变上述管理端设计。主视觉为校园日程票签：真实月/日、分类、地点和人数构成活动卡，不使用无来源活动照片、热度数字或推荐排名。首页“发现新活动”明确按最新创建排序。

移动端 runtime canonical 是 `miniprogram/src/styles/tokens.wxss`（Model B 的平台适配）；八个核心品牌/语义颜色与 `admin-web/src/styles/tokens.css` 一致，由 `miniprogram/tests/design.test.cjs` 检查。WXSS 不导入 Vue/CSS 组件。颜色语义沿用 colors.*；`--ch-radius:32rpx` 对应手机卡片约16px，`--ch-gap:24rpx` 为卡片内部节奏，`--ch-touch:44px` 为最低触控高度。消费者为 app.wxss、activity-card 和 page-state；原生导航的颜色由 app.json 显式适配并受测试检查。

系统无衬线中文字体，品牌/日期加粗；正文15px，辅助14px，小注释12px，页面标题27px、首页表达32px。小于350px隐藏非必要印章，保持正文可读。页面32rpx左右边距、最大640px、自然滚动；固定详情动作条包含底部 safe-area，原生 tabBar 自行管理底部区域。八个仓库自绘线性 PNG 导航图标来自 scripts/icons.py，不从外部服务加载。

状态 owner：page-state 处理 loading/login/error/empty；错误长驻、重试明确；按钮 busy 禁用且尺寸稳定。已报名、截止、满员均有文字，不依赖颜色。平台原生 wx.showModal 统一取消确认，wx.showToast 只做辅助反馈；关键结果同页保留。无持续动画和默认骨架。微信 style v2 按钮默认固定宽度必须由统一规则覆盖，活动卡使用完整可用宽度。原生滚动条、返回手势、系统字体跟随平台，不复制 Web scrollbar 或焦点陷阱实现。

设计偏差核验：核心颜色 MATCH；Web 10/16px 圆角在手机分别采用14px控件/32rpx卡片，为有意的平台变体；日期票签 MATCH；Web 14px正文在学生端提升为15px；全部功能入口实际可用。平台 TalkBack/VoiceOver 仍需专门实测，不将 label/aria 声明等同无障碍验收。
