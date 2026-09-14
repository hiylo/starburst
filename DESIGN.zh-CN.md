<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

---
version: alpha
name: StarBurst-design-system
description: |
  StarBurst 客户端的设计系统，基于 Material 3 + Jetpack Compose 实现。品牌以靛蓝（#6366F1）为核心，搭配紫罗兰与青色构成三档品牌色。界面提供 Light / Dark / AMOLED 三套语义化色板，AMOLED 模式使用纯黑表面（#000000）换取 OLED 省电。全部视觉 token 直接映射到 Compose 的 MaterialTheme.colorScheme / Typography，所有圆角、间距、组件风格集中在 ui/components 与 ui/theme 中，可直接被代码消费。

colors:
  primary: "#6366F1"
  secondary: "#8B5CF6"
  tertiary: "#06B6D4"
  dark-primary: "#9DA3FF"
  dark-primary-container: "#2D2F6E"
  dark-surface: "#121218"
  dark-on-surface: "#E5E1E9"
  dark-surface-container: "#1E1E25"
  light-primary: "#4F52B8"
  light-primary-container: "#E0E0FF"
  light-surface: "#FCF8FF"
  light-on-surface: "#1C1B22"
  light-surface-container: "#F3EFF7"
  amoled-surface: "#000000"
  amoled-surface-container: "#0D0D12"
  status-connected: "#4CAF50"
  status-error: "#EF4444"
  status-warning: "#F59E0B"

typography:
  display-large:
    fontFamily: system sans-serif
    fontSize: 57sp
    fontWeight: 400
    lineHeight: 64sp
    letterSpacing: -0.25sp
  display-medium:
    fontFamily: system sans-serif
    fontSize: 45sp
    fontWeight: 400
    lineHeight: 52sp
  display-small:
    fontFamily: system sans-serif
    fontSize: 36sp
    fontWeight: 400
    lineHeight: 44sp
  headline-large:
    fontFamily: system sans-serif
    fontSize: 32sp
    fontWeight: 400
    lineHeight: 40sp
  headline-medium:
    fontFamily: system sans-serif
    fontSize: 28sp
    fontWeight: 400
    lineHeight: 36sp
  headline-small:
    fontFamily: system sans-serif
    fontSize: 24sp
    fontWeight: 400
    lineHeight: 32sp
  title-large:
    fontFamily: system sans-serif
    fontSize: 22sp
    fontWeight: 700
    lineHeight: 28sp
  title-medium:
    fontFamily: system sans-serif
    fontSize: 16sp
    fontWeight: 700
    lineHeight: 24sp
    letterSpacing: 0.15sp
  title-small:
    fontFamily: system sans-serif
    fontSize: 14sp
    fontWeight: 500
    lineHeight: 20sp
    letterSpacing: 0.1sp
  body-large:
    fontFamily: system sans-serif
    fontSize: 16sp
    fontWeight: 400
    lineHeight: 24sp
    letterSpacing: 0.5sp
  body-medium:
    fontFamily: system sans-serif
    fontSize: 14sp
    fontWeight: 400
    lineHeight: 20sp
    letterSpacing: 0.25sp
  body-small:
    fontFamily: system sans-serif
    fontSize: 12sp
    fontWeight: 400
    lineHeight: 16sp
    letterSpacing: 0.4sp
  label-large:
    fontFamily: system sans-serif
    fontSize: 14sp
    fontWeight: 500
    lineHeight: 20sp
    letterSpacing: 0.1sp
  label-medium:
    fontFamily: system sans-serif
    fontSize: 12sp
    fontWeight: 500
    lineHeight: 16sp
    letterSpacing: 0.5sp
  label-small:
    fontFamily: system sans-serif
    fontSize: 11sp
    fontWeight: 500
    lineHeight: 16sp
    letterSpacing: 0.5sp
  code:
    fontFamily: Monospace
    fontSize: 13sp
    fontWeight: 400
    lineHeight: 20sp

rounded:
  dialog: 20dp
  card: 12dp
  picker: 12dp
  search: 14dp
  chip: 9999px

spacing:
  xxs: 2dp
  xs: 4dp
  sm: 8dp
  md: 12dp
  lg: 16dp
  xl: 24dp
  xxl: 32dp

components:
  primary-button:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.light-surface}"
    typography: "{typography.label-large}"
    rounded: full
    height: 40dp
  primary-button-destructive:
    backgroundColor: "{colors.status-error}"
    textColor: "{colors.light-surface}"
    typography: "{typography.label-large}"
    rounded: full
  primary-button-amoled:
    backgroundColor: "#000000"
    textColor: "{colors.primary}"
    typography: "{typography.label-large}"
    rounded: full
  secondary-button:
    backgroundColor: transparent
    textColor: "{colors.primary}"
    typography: "{typography.label-large}"
    rounded: full
  secondary-button-outlined:
    backgroundColor: transparent
    textColor: "{colors.primary}"
    typography: "{typography.label-large}"
    rounded: full
  dialog:
    backgroundColor: "{colors.light-surface}"
    textColor: "{colors.light-on-surface}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.dialog}"
  dialog-amoled:
    backgroundColor: "#000000"
    textColor: "{colors.dark-on-surface}"
    rounded: "{rounded.dialog}"
  card:
    backgroundColor: "{colors.dark-surface-container}"
    textColor: "{colors.dark-on-surface}"
    typography: "{typography.body-medium}"
    rounded: "{rounded.card}"
  list-item:
    backgroundColor: transparent
    textColor: "{colors.dark-on-surface}"
    typography: "{typography.body-large}"
    rounded: none
  section-header:
    textColor: "{colors.primary}"
    typography: "{typography.label-medium}"
  loading-edge:
    backgroundColor: "{colors.primary}"
    typography: "{typography.body-small}"
  switch:
    backgroundColor: transparent
    textColor: "{colors.primary}"
    typography: "{typography.label-medium}"
    rounded: full
---

## Overview

StarBurst 是一个以「终端即服务」为核心的移动客户端：用户在本机管理多个 OpenCode 服务器、浏览会话、在真实终端仿真器中执行命令，并查看 AI 生成内容。整套 UI 建立在 **Material 3（Material You）** 之上，用 Jetpack Compose 实现，设计 token 全部来自 `app/src/main/kotlin/org/hiylo/starburst/ui/theme/`（`Color.kt` / `Theme.kt` / `Type.kt`）与 `ui/components/`（`AppSurfaces.kt` 等）。

品牌识别靠三件事：**靛蓝主色**（`#6366F1`）、**纯黑 AMOLED 表面**（`#000000`）、**M3 语义化容器层级**。它不是营销型网页风格，而是一个「克制的工具型深色优先」系统——默认 Dark 表面为 `#121218`，卡片用 `surfaceContainer` 阶梯（`#1E1E25` → `#262630` → `#31313B`）表达层级，而不是用投影。AMOLED 模式下所有表面塌缩为纯黑，卡片仅靠 1dp 描边区分，最大化 OLED 省电。

系统支持三套视觉档位：跟随系统的 Light/Dark、Android 12+ 的动态取色（Material You）、以及用户手动开启的 AMOLED 纯黑模式（`AmoledDarkColorScheme`）。AMOLED 不改变组件语义，只把表面换黑、把实心按钮换成描边按钮。

**关键特征：**
- Material 3 标准 `ColorScheme`，品牌色只作为 `StarBurstPrimary`（`#6366F1`）等 accent 供给特殊组件，主 UI 消费语义化 token（`primary` / `surfaceContainer` / `outline`…）
- Dark 优先：默认表面 `#121218`，正文 `#E5E1E9`，容器用 `surfaceContainer*` 阶梯替代投影
- AMOLED 纯黑模式：`#000000` 表面 + 1dp 描边卡片 + 描边按钮
- 圆角体系集中：对话框 20dp、卡片/列表项 12dp、搜索框 14dp
- 终端仿真与代码渲染使用等宽字体（`FontFamily.Monospace`），其余全用系统无衬线
- 状态语义色固定：连接绿 `#4CAF50`、错误红 `#EF4444`、警告琥珀 `#F59E0B`

## Colors

> **来源文件**：`Color.kt`（品牌/状态色）、`Theme.kt`（三套 M3 色板）。所有 token 由 `StarBurstTheme` 统一注入，组件内一律读 `MaterialTheme.colorScheme`，禁止硬编码 hex。

### 品牌色（Accent）
- **Primary Indigo**（`{colors.primary}` — `#6366F1`）：品牌核心色。Light 模式主按钮填充、AMOLED 模式按钮描边/文字、选中态高亮、加载条、Section 标题。
- **Secondary Purple**（`{colors.secondary}` — `#8B5CF6`）：次要品牌强调，用于特殊高亮与渐变（如加载条的过渡色）。
- **Tertiary Cyan**（`{colors.tertiary}` — `#06B6D4`）：第三强调色，用于需要与靛蓝区分的点缀。

### 状态色（Semantic）
- **Connected**（`{colors.status-connected}` — `#4CAF50`）：服务器健康、连接成功、成功提示。
- **Error**（`{colors.status-error}` — `#EF4444`）：错误、破坏性操作、未连接告警。
- **Warning**（`{colors.status-warning}` — `#F59E0B`）：电池优化提示、谨慎告警。

### Dark 色板（默认）
- **Surface**（`{colors.dark-surface}` — `#121218`）：页面主背景。
- **SurfaceContainer**（`#1E1E25`）→ **SurfaceContainerHigh**（`#262630`）→ **SurfaceContainerHighest**（`#31313B`）：卡片与浮层层级。
- **Primary**（`{colors.dark-primary}` — `#9DA3FF`）：Dark 下的主色（提亮以保证对比度）。
- **PrimaryContainer**（`#2D2F6E`）/**OnPrimaryContainer**（`#DEE0FF`）：选中态容器与其中文字。
- **OnSurface**（`#E5E1E9`）/**OnSurfaceVariant**（`#C8C5D0`）：正文与次级文字。
- **Outline**（`#918F9A`）/**OutlineVariant**（`#47464F`）：描边与分割线。

### Light 色板
- **Surface**（`{colors.light-surface}` — `#FCF8FF`）/**OnSurface**（`#1C1B22`）。
- **Primary**（`{colors.light-primary}` — `#4F52B8`）/**PrimaryContainer**（`#E0E0FF`）：深一档的靛蓝，保证白底可读。
- **SurfaceContainer**（`#F3EFF7`）阶梯用于卡片。

### AMOLED 色板
`AmoledDarkColorScheme = DarkColorScheme.copy(...)` 覆盖为纯黑：
- **Surface / Background / SurfaceContainerLowest**：`#000000`
- **SurfaceContainer**：`#0D0D12` · **Low**：`#080810` · **High**：`#141419` · **Highest**：`#1C1C24` · **SurfaceVariant**：`#1A1A22`
- 正文 `OnSurface` 保持 `#E5E1E9` 不变

## Typography

### Font Family
- **正文/标题**：`FontFamily.Default`（系统无衬线）。不引入第三方字体，保证中英文同构。
- **代码/终端**：`FontFamily.Monospace`，固定 13sp/20sp（`CodeTypography`），用于会话代码块与终端输出。

### Hierarchy（完整 M3 层级表）

| Token | Size | Weight | Line Height | Letter Spacing | Use |
|---|---|---|---|---|---|
| display-large | 57sp | 400 | 64sp | -0.25sp | 极少数大数字/首屏 |
| display-medium | 45sp | 400 | 52sp | 0 | — |
| display-small | 36sp | 400 | 44sp | 0 | 空状态大图标场景 |
| headline-large | 32sp | 400 | 40sp | 0 | — |
| headline-medium | 28sp | 400 | 36sp | 0 | 对话框标题 |
| headline-small | 24sp | 400 | 32sp | 0 | — |
| title-large | 22sp | 700 | 28sp | 0 | 页面大标题 |
| title-medium | 16sp | 700 | 24sp | 0.15sp | 列表项标题、卡片主文案 |
| title-small | 14sp | 500 | 20sp | 0.1sp | 次级标题 |
| body-large | 16sp | 400 | 24sp | 0.5sp | 列表主文案、按钮标签 |
| body-medium | 14sp | 400 | 20sp | 0.25sp | 默认正文、描述 |
| body-small | 12sp | 400 | 16sp | 0.4sp | 辅助信息、元数据 |
| label-large | 14sp | 500 | 20sp | 0.1sp | 按钮、标签 |
| label-medium | 12sp | 500 | 16sp | 0.5sp | Section 标题、chips |
| label-small | 11sp | 500 | 16sp | 0.5sp | 状态文字、角标 |

### 原则
层级主要靠**字重 + 字号**构建：列表项标题一律 `titleMedium`（700），正文 `bodyLarge/bodyMedium`（400），状态与辅助信息用 `labelSmall/bodySmall`。标题类偏 700（`titleLarge/titleMedium`），正文偏 400，Secondary/辅助用 500。所有文字通过 `MaterialTheme.typography.*` 引用，禁止散落裸 `TextStyle`。

## Layout

### Spacing System
- **基础单位：4dp**，常用 2/4/8/12/16/24/32dp。
- 列表项内边距 `12~16dp` 横向；卡片内边距 `14~16dp`；对话框内容 `24dp`。
- 屏幕水平留白统一 `16dp`（部分列表 12dp），组件间距 `8dp`（`Arrangement.spacedBy(8.dp)`）。
- 空状态纵向留白 `48dp` 起。

### 页面结构
- **单列手机布局**，无桌面适配。每个页面 `Scaffold` + `TopAppBar`（返回箭头 + 标题 + 右侧操作）。
- **会话列表**：`LazyColumn` + 会话卡片，卡片含标题（`titleMedium`）、预览（`bodySmall` 等宽）、时间与状态徽章。
- **聊天页**：终端区（等宽字体，`terminal-area`）+ 底部 Composer（圆角输入框 `24dp` + 圆形发送按钮）。
- **设置页**：按语义分 Section（通用/通知/外观/聊天显示/聊天行为/高级），每个 Section 用 `SectionHeader`（`labelMedium` 靛蓝）+ `ListItem`（标题 + 支持文字 + 尾部控件）。

### 网格与容器
- 无固定栅格系统；卡片全宽、圆角 12dp。
- Provider 图标网格 `MCP grid` 3 列；颜色选择器等采用水平滚动行。

### 留白哲学
工具型密度：信息优先，卡片间 `8dp`、Section 间 `16dp`。AMOLED 模式下通过纯黑背景放大「留黑」的节能价值，视觉层级交给描边而非留白。

## Elevation & Depth

| Level | Treatment | Use |
|---|---|---|
| 0 — Flat | 无投影，仅底色 | 页面背景、列表项、Section |
| 1 — Container 阶梯 | `surfaceContainer → High → Highest` 逐步提亮 | 卡片、下拉、弹层底部容器 |
| 2 — 对话框 | `surface` + 6dp tonalElevation（AMOLED 下 0dp + 1dp 描边） | AppDialog |
| 3 — AMOLED 描边 | 纯黑表面 + `1dp outlineVariant(alpha 0.55)` | AMOLED 模式所有卡片/弹层 |

系统**以容器色阶梯替代投影**：同一表面下用 `surfaceContainer*` 提亮表达「更上层」，而不是靠阴影。唯一常用的视觉动效是 `AppLoadingEdge`——顶部 3dp 高亮条用 primary 渐变横向缩放脉冲（`FastOutSlowInEasing` 700ms 往返）表达加载态。

## Shapes

### Border Radius Scale

| Token | Value | Use |
|---|---|---|
| `{rounded.dialog}` | 20dp | 对话框容器（AppDialog） |
| `{rounded.card}` | 12dp | 卡片、会话卡片、列表项选中态（AppCardShape / AppPickerItemShape） |
| `{rounded.search}` | 14dp | 搜索框（AppSearchShape） |
| full | 9999px | 按钮、输入框、发送按钮、chips、开关 |

半径词汇：**交互元素尽量圆**（按钮/输入/发送键全圆），**容器 12dp**，**对话框 20dp**。图标不做裁剪圆角（用 Material 图标原形）。

## Components

> **状态约定**：Material 3 组件原生携带 hover/focus/pressed/disabled 状态，文档只标注关键覆盖。

### 按钮（AppSurfaces.kt）
- **`primary-button`（AppPrimaryButton）**：实心填充 `{colors.primary}`，文字 `onPrimary`。Light 模式默认 M3 `Button`；**AMOLED 模式自动切换为描边按钮**——黑底 + 1dp `{colors.primary}` 描边 + primary 文字。
- **`primary-button-destructive`**：填充 `{colors.status-error}`，文字 `onError`（Light）；AMOLED 下为 error 色描边按钮。
- **`secondary-button`（AppSecondaryButton）**：`TextButton`，文字 `{colors.primary}`，无背景。`outlined = true` 时变为 1dp primary 描边 + 透明/黑底。
- **`dialog-actions`（AppDialogActions）**：右对齐 `spacedBy(8.dp)` 的 `[secondary, primary]` 按钮对，破坏性确认传 `destructiveConfirm`。

### 对话框
- **`dialog`（AppDialog）**：`BasicAlertDialog` + Surface，圆角 20dp，`AlertDialogDefaults.containerColor`，6dp tonalElevation。AMOLED 下容器 `#000000`、0dp 投影、1dp 描边。
- 标题用 `titleMedium/headlineSmall`，正文 `bodyMedium` onSurfaceVariant。

### 卡片与列表项
- **`card`**：圆角 12dp，`surfaceContainer` 阶梯背景。用于服务器卡片、会话卡片、文件列表项。
- **`list-item`**：透明背景、`bodyLarge` 标题 + `bodySmall` 支持文字 + 右侧 `Switch`/`TextButton`/`›`。设置页、诊断页大量使用。
- **选中态**（`AppPickerItemShape` 12dp）：`primaryContainer.copy(alpha 0.5)` 底 + primary 文字 + ✓（AMOLED 下黑底 + 1dp primary 描边）。

### 其他
- **`switch`**：checked 轨道 `{colors.primary}`；AMOLED 下黑轨道 + primary 描边。
- **`loading-edge`（AppLoadingEdge）**：顶部 3dp primary 渐变脉冲条。
- **状态徽章/chips**：连接成功 `{colors.status-connected}`、错误 `{colors.status-error}`、警告 `{colors.status-warning}`。
- **Section 标题**：`labelMedium` 500，`{colors.primary}`，`padding(start 16dp, top 16dp, bottom 4dp)`。

## Do's and Don'ts

### Do
- 一切颜色读 `MaterialTheme.colorScheme`，一切文字读 `MaterialTheme.typography`；**禁止在组件里硬编码 hex/字号**。品牌色只从 `Color.kt` 的 `StarBurstPrimary/Secondary/Tertiary` 进入系统。
- 层级用 `surfaceContainer*` 阶梯表达，默认不投影。
- 按钮走 `AppPrimaryButton / AppSecondaryButton` 封装——AMOLED 语义（黑底描边）由封装自动处理，业务代码不感知。
- 状态语义固定：连接绿 `#4CAF50`、错误红 `#EF4444`、警告琥珀 `#F59E0B`。
- 代码与终端统一 `FontFamily.Monospace` 13sp；中文与界面文字用系统无衬线。
- 圆角遵循：按钮/输入全圆、容器 12dp、对话框 20dp、搜索框 14dp。

### Don't
- 不要新增第四套主题色板；三档（Light/Dark/AMOLED）之外的颜色必须能映射回现有 token。
- 不要在业务页面里用 `darkColorScheme/lightColorScheme` 临时造色板，统一走 `StarBurstTheme`。
- 不要把 AMOLED 当「又一个暗色」——它只换表面为纯黑 + 描边，不改变组件语义与层级。
- 不要引入营销式视觉（大投影、渐变背景、装饰性插画）；系统是工具型、信息优先的。
- 不要用非等宽字体渲染代码/终端内容。

## Responsive Behavior

### 断点与适配
Android 原生响应式，无 Web 断点概念。适配维度是**屏幕宽度（手机 vs 平板）与密度（dp）**：
- 竖屏手机（默认）：单列，卡片全宽，列表项横向撑满。
- 横屏/平板：`LazyColumn` 内容仍单列；聊天页 Composer 保持底部吸附；必要时外层 `Box` 限制最大内容宽度居中。
- 系统字体缩放（fontScale）：Compose `Text` 全部用 `sp`，自动跟随无障碍放大。

### 触控目标
- 按钮高度 ≥ 40dp；图标按钮 `36dp` 圆角 `12dp`；Composer 发送按钮 `36dp` 全圆。
- 列表项整行可点（`Modifier.clickable` 铺满），行高 ≥ 48dp。
- 开关、滑块等 M3 组件自带 48dp 最小热区。

### 折叠策略
- 顶部栏：窄屏只保留返回 + 标题 + 必要操作，多余操作进「⋮」菜单。
- 设置/诊断等长列表：`verticalScroll` 整页滚动，Section 内用 `ListItem` 分组。
- 终端：水平溢出滚动（`horizontalScroll`），垂直方向保留滚动回退缓冲。

## Iteration Guide

1. 逐个组件迭代：改 `ui/components` 或 `ui/theme` 的 token，业务页面立即生效——先验证颜色/字号解析，再验证 AMOLED 与 Light 两档。
2. 新增组件优先用现有词汇（`AppDialog` / `AppCard` / `AppPrimaryButton` / `AppPickerItemShape` 12dp + 容器阶梯），不够再引入新 token。
3. 加颜色时同时补三档：`DarkColorScheme`、`LightColorScheme`、`AmoledDarkColorScheme`，缺一不可。
4. 文字一律走 `MaterialTheme.typography` 已有档位；确实没有的档位才扩展 `Type.kt` 的 `Typography`。
5. 每改一个色板，检查 AMOLED 对比度：纯黑表面上正文必须保持 `#E5E1E9`，描边 `outlineVariant(0.55)`。
6. 破坏性操作统一走 error 色（`{colors.status-error}`），不另造红色。

## Known Gaps

- **字体单位 `sp`**：本项目是 Android Compose 应用，字号/行高以 `sp`（+部分 `dp`）为标准单位；`npx @google/design.md lint` 是 CSS/px 取向工具，会把 `sp`/`dp` 报为 "not a valid dimension" 错误。这是工具与平台的单位差异，非设计缺陷，生成 Compose 代码时保持 `sp`/`dp` 原样。
- **Hover/focus 态**未单独文档化——由 M3 组件原生承担，无自定义覆盖。
- **动态取色（Material You）**：Android 12+ 开启后色板由系统生成，本文档的靛蓝色板不再生效，仅在 AMOLED 叠加纯黑表面；未单独文档化动态色下的一致性规则。
- **无障碍（accessibility）**：除字号 `sp` 与触控高度外，对比度与 TalkBack 语义未纳入本文档。`#6366F1` 上的白字对比度约 4.26:1，接近但略低于 WCAG AA 4.5:1——正文场景建议改用 `primaryContainer`/`onPrimaryContainer` 组合。
- **横屏/平板布局**未在预览与文档中穷举，当前以竖屏手机为基准。
- **WebView 内嵌页面**（如 GitHub 登录）不属本设计系统管辖，样式随宿主页面。
