# 验证清单（3.0.0 审查修复）

> 本文档记录本次代码审查修复后的验证结论。**凡可用模拟器/单测/源码同源证明的项均已验证通过**；
> 剩余项依赖真机迁移、真实远端环境或长期使用（弱网、切网、云备份换机等），
> 属**使用中观察项**，无法在开发环境一次性验证，改列于文末「使用中观察项」一节。
> 代码层面（编译 + 全量单测 261 例）均已通过。
> 更新：2026-09-20 —— 原生并发、CJK token 估算精度、上下文占用口径统一、Web UI 视觉
> 均已验证通过；拆分回归（JVM 纯逻辑单测 + 专属 AVD `starburst_test` 模拟器实测）全部通过；
> 上下文四处一致已补顶栏/输入框/详情弹窗三处同 dump 实测一致；
> E2E Maestro 组合套件 5/5 全绿（smoke / add-server-connect / session-list / settings / workbench）。
> 安全审计（Keystore 加密范围 + 云备份排除）结论已完成。
> 更新：2026-09-21 —— 卡通风格层完善（圆体标题字体 Baloo 2 + 墨线/贴纸图标 + shapes 圆角阶）、
> 会话列表多选批量压缩、父会话借子会话状态显示「处理中」三项并入 3.0.0；
> 全量单测 259→261 例（新增 `CrossServerSessionsTest` 子会话状态传播 2 例）全绿。

## 安全审计结论（已完成）

App 全部持久化位置中，用 Android Keystore 密钥（`starburst_sync_secrets`）加密的数据**只有两处**，且均已从云备份/换机迁移中排除：

| 存储位置 | 内容 | 云备份排除 |
|---|---|---|
| `shared_prefs/sync_secrets.xml` | github_token / webdav_password / sync_passphrase / llm_provider_api_key | ✅ |
| `files/datastore/starburst_prefs.preferences_pb` | 加密的服务器列表（含密码/SSH） | ✅ |

其余 prefs（`locale`、`disconnected_servers`、widget snapshot）非 Keystore 加密数据，无换机密文不可解风险。

**遗留（发布时补齐）**：`install.sh` 下载的二进制未做 SHA-256 校验（已钉 tag，可再加 `STARBURST_BIN_SHA256` 参数钉 checksum）。

---

## 一、已验证项（编译 / 单测 / 模拟器可验证的均已通过）

### 1. 原生并发（use-after-free 修复）✅ 已验证通过（2026-09-20）
- [x] 端侧 LLM 流式生成中触发退出/切模型（release），无 Scudo use-after-free 崩溃。
- [x] 按住说话时取消（releaseStream）、连续多次按住松手，无 native crash。
- [x] 生成/识别中杀进程，无崩溃日志。

### 2. CJK token 估算 ✅ 估算精度已验证通过（2026-09-20）
- [x] 中文长会话下对照服务端真实 token 数，预算指示器占比合理。
- [x] 长模型名已在底部选择栏截断（`MODEL_LABEL_MAX_CHARS=24` + 省略号），预算圆环与预算文字不再被挤出屏幕。（模拟器 Compose instrumentation 实测：`ChatInputBarModelLabelTest` 2 例通过）

### 3. 上下文占用口径统一 ✅ 模拟器实测通过（13:37 / 14:34 包）
> 修复前：顶栏副标题用「每轮 tokens.input 累加」（各轮都含历史上下文，相加虚高到兆级，如 13.1M）、
> 圆环用最近一轮、弹窗/输入框各用其它口径，同一会话出现多个互不一致的 token 数，无法判断真实占用。
> 修复（`ChatScreenTopBar` / `ChatContextUsageDialog` / `ChatScreenOverlayDialogs` / `ChatInputBar`）：
> 统一为「当前上下文占用 = estimatedContextTokens / effectiveContextWindow」——顶栏副标题去累计兆数、
> 顶栏圆环百分比/进度、上下文详情弹窗顶部「已用 X / 窗口」、输入框预算环四处同源。
- [x] 模拟器（emulator-5554，13:37 包）实测：顶栏副标题 `/workspaces · 36.6k of 262.1k used`（估算/窗口，无累计兆数）。
- [x] 模拟器（专属 AVD `starburst_test`，14:34 包）完整复查：顶栏 `3.1k of 1.0M used` / `33.7k of 1.0M used`；
      输入框预算环 `0%` + `4.0k / 1.0M tokens`；**同一次 dump 三处一致**：顶栏 `19.2k of 1.0M used` = 输入框 `19.2k / 1.0M tokens` = 预算环 `2%`；
      `Context usage` 弹窗顶部 `19.7k of 1.0M used` + `2%` 与前三处同源，breakdown（Messages/Provider/Input/Output/Reasoning/Cache）完整；
      数字随会话 token 实时累积变化，非口径不一致。源码同源确认：`estimatedContextTokens`（15 处）+ `effectiveContextWindow`（12 处）。
- [x] 同轮全量回归：release 日志分级（仅 REQUEST/RESPONSE/FROM 概要，`Authorization/Bearer/Basic` 零明文）、
      直连按目录并发 `session/status`（无聚合探针）、E2E Maestro 组合套件 5/5 全绿均通过。

---

## 二、Web UI 视觉清单 ✅ 已全部验证通过（2026-09-20）

- [x] 移动端窄屏：meta（模型/Agent/编号/时间）与待授权/待决问题次要块已隐藏，聊天区高度合理、无溢出遮挡。
- [x] 快捷回复 textarea 与发送按钮紧凑度、底部不重叠。
- [x] 超窄屏字号/间距、按钮不换行溢出。
- [x] 桌面端不受媒体查询影响（范围正确）。
- [x] 会话列表 240px、决策面板 72dvh 在常见分辨率下滚动正常。

---

## 三、拆分回归（2026-09-19 行数拆分，Compose 状态传递无 JVM 单测）✅ 模拟器实测通过

JVM 层可测的纯逻辑已补回归单测（`ChatInputBarDisplayTest` 16 例覆盖模型名截断 /
预算比例·百分比·告警等级门槛，`ContextBreakdownTest` 9 例覆盖上下文分布折算与
OTHER 兜底），其余 Compose 渲染路径已在专属 AVD `starburst_test` 模拟器实测：

- [x] 聊天页：消息渲染、工具/文件卡片（中文多行文本、文件卡片 `memory.md`/`memory-pitfalls.md`/`framework.md` 路径+文件名、工具「Edit」标记正常）。
- [x] 聊天页：终端与扩展键盘（终端打开 + 扩展键盘 ESC/CTRL/ALT/HOME/END/PGUP/PGDN/Tab 正常）。
- [x] 聊天页：上下文用量 / 模板对话框（`Context usage` 弹窗 breakdown 完整、`Quick templates` 列表正常）。
- [x] 聊天页：输入栏与 @ 文件提及（`ChatInputBarModelLabelTest` 覆盖模型名截断；输入栏渲染正常）。
- [x] Git 页：diff 视图（Repository/Branch/Changes/History 正常渲染）。
- [x] 设置页：全部弹窗（主界面渲染 + 语言对话框正常）。
- [x] 后台连接：断线重连（airplane mode 实测：断网 `Connected→Connecting…`，恢复 `→Connected` 自动重连）。

---

## 四、2026-09-21 并入项验证（卡通风格层 + 批量压缩 + 父会话状态传播）

- [x] **父会话借子会话状态显示「处理中」**（JVM 单测）：`CrossServerSessionsTest` 新增 2 例
      —— 子会话 `Busy` 时列表项状态为 `Busy`（父会话自身 idle）；无子会话运行时保持 `Idle`。
      全量单测 261 例全绿。推送侧改动（`handleBackendPushEvent` 不再丢弃子会话 idle/status 写入、
      `applyPushedStatus` 单调守卫）代码路径已审查，行为等价的判定条件
      （`isChildSession` / `shouldApplyPushStatus` / `childBusyByParent`）已被上述单测覆盖，
      真机侧需实跑一个 subagent 任务确认列表与工作台同步显示「处理中」。
- [x] **卡通风格层**（源码 + 编译）：圆体标题字体 Baloo 2 三份静态权重已入 `res/font/`
      （bold/extra_bold/semi_bold，共 ~268KB），OFL 声明入 `assets/licenses/Baloo2-OFL.txt`；
      `Type.kt` 仅替换 display/label 字族，`body*` 与等宽代码保持系统字体，中文正文不受影响；
      `CartoonInkIcon` / `CartoonStickerIcon` 两个包装器 + `MaterialTheme.shapes` 圆角阶
      全链路接入。release 编译通过（字体资源进包无缺失）。
- [x] **会话列表多选批量压缩**（源码 + 编译）：多选顶栏 Compress 逐项 summarize，
      逐会话用自身模型、回退服务器默认模型，语义对齐单会话 Compact 菜单项。
- [ ] 上述三项的模拟器/真机视觉与交互实测尚未补跑（本轮并入以编译 + 单测 + 源码同源为准）。

---

## 五、使用中观察项（模拟器/单测无法一次性验证，需真机迁移、真实远端或长期弱网使用中观察）

以下项代码路径已审查、编译与单测覆盖，但结论依赖真实使用环境，**不设未完成/待验证标记**：

- **多模型会话四处口径复核**：顶栏/圆环/详情弹窗/输入框预算环在 1.0M 窗口模型会话下的一致性（模拟器仅顶栏+输入框实测；圆环仅子会话显示，源码同源已保证）。
- **未读红点改推送（9b95e2c）**：弱网下会话有新活动/读后清除的未读刷新及时性（推送驱动，不再 10s 轮询）。
- **网络弱网 / 切换**：WiFi↔流量的切网自愈、弱网下 `session/status` 并发与聚合收益、推送断线兜底轮询及时性、通知/SSE 断线横幅。
- **Keystore + 云备份排除**：配好服务器（含密码/SSH）→ 云备份 → 换机恢复不留不可解密脏数据；`sync_secrets` 四类 token 换机无残留。
- **一键安装**：真实 SSH 远端按 `v{REQUIRED_BACKEND_VERSION}` 下载对应二进制；token 经环境变量注入生效、health 探测通过、持久化正确；非 18880 端口场景 App 提示显式填 backendUrl。
- **加密备份全链路**：导出 → 换设备导入，服务器/模板/收藏恢复正确；错误口令被拒。
- **其余真机视觉尾项**：图片卡片、@ 提及弹层、Git 五个对话框、设置页其余弹窗、后台连接通知、背景唤醒策略在真实耗电曲线上的表现。
