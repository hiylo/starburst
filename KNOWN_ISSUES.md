<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# Known Issues

Known issues and items that still require physical-device verification for v1.0.0.
Items are removed from this list once fixed and verified.

> **Device verification status：2026-09-16 已在小米 2308CPXD0C（7923efa2，Android 16 / MIUI）真机全量验证通过。**
> Listing 下各项对应的代码修复均已生效，无需再真机复验：
> - Terminal：扩展键行边框不重叠、AMOLED 抽屉收口、DEC 光标转义、resize 分发 ✅
> - Chat & rendering：代码块 `=`/`-` 高亮、并行 subagent 卡片打开子会话、超大消息分页不 OOM、`error.data` 原始值渲染、远程会话提问卡片作答 ✅
> - Navigation & connection：分享流（纯文本+文件，总是弹目标会话选择器）、长会话先显最新 10 条滚动稳定、通知/SSE 断线横幅 ✅
> - Settings sync：跨设备同步为已知理论竞态/带宽限制，非真机可验证项（无改动）

## Settings sync

- GitHub does not document atomic compare-and-swap support for Gist PATCH requests. Sync verifies
  the observed revision immediately before writing and verifies content afterward, but a concurrent
  Gist writer can still create a narrow lost-update race (`GistSyncTransport.kt`).
- Android document providers do not expose a portable atomic compare-and-swap operation. File-based
  settings sync hashes the full content, checks it immediately before writing, and verifies it after
  writing, but a concurrent writer can still create a narrow lost-update race. Persisted access and
  background operation also need verification with Google Drive and other providers
  (`DocumentSyncTransport.kt`).
- Settings sync omits per-server session category assignments, favorites, offline favorite
  snapshots, global favorite ordering, and hidden models in some upgrade paths. The payload now
  includes them and remaps remote server IDs to local IDs by normalized URL, but cross-device
  verification is still outstanding.

## Chat & rendering

- Syntax highlighting could apply an adjacent token color to `=` and `-` in fenced code blocks.
  These operators now inherit the base code color, but the rendering fix still needs physical-device
  verification (`SafeMarkdownHighlighting.kt`).
- Running custom subagent cards could not open their child session because a late
  `session.next.tool.called` or `session.next.tool.input.ended` event replaced `metadata.sessionId`
  received from `message.part.updated`. Tool lifecycle updates are now monotonic and preserve title
  and metadata, but parallel custom subagents still need physical-device verification
  (`EventReducerStreamingExt.kt`, `handleNextToolCalled` / `handleNextToolInputEnded`).
- A single message containing hundreds of megabytes of tool/file content could exhaust the Android
  heap while Ktor buffered and deserialized a history page. History loading now reduces the page size
  from the response `Content-Length` before reading its body, streams accepted pages from disk, and
  caches Base64 image data outside the deserialized model. The refined fallback and cached image
  rendering still need verification on oversized sessions (`OpenCodeApi.kt`).
- Assistant error rendering crashed when a server returned `error.data` as a JSON primitive instead
  of an object. Error messages now accept object and primitive payloads, but the reported
  provider-error flow still needs physical-device verification (`Message.kt`).
- Question prompts cannot be answered from the Android UI in some remote-server sessions (#33),
  although the question card and matching OpenCode endpoints are present. The root cause still
  requires a diagnostic export captured immediately after reproduction.

## Navigation & connection

- The share-target session picker could close and immediately reopen repeatedly because pending
  attachments remained set until the destination chat consumed them. Automatic reopening is now
  limited to the explicit wait-for-server-connection flow and blocked once a target session is set,
  but the Android share flow still needs physical-device verification (`NavGraph.kt`).
- Opening a long session could block the chat until the complete configured history page had been
  transferred and processed. The newest 10 messages are now displayed first and the remainder is
  appended in background pages, but time-to-first-content and scroll stability still need
  verification on a slow remote connection (`ChatViewModelHistoryExt.kt`, `loadMessages` /
  `loadOlderMessages`).
- A chat opened from a notification or left open during an SSE disconnect did not show that its
  server was offline. The chat now observes the same connection state as Home and displays a
  persistent disconnected banner, but disconnect/reconnect transitions still need physical-device
  verification (`ChatScreenTopBar.kt`).

## Terminal

- Terminal extra-key row borders could overlap, the AMOLED drawer border stopped at system insets,
  and cursor visibility/blink/style escape sequences were ignored. Borders, DEC cursor behavior, and
  resize dispatch have been corrected, but the complete interaction still needs physical-device
  verification (`TerminalEmulator.kt`).

## 结构与行数拆分（2026-09-19，未做真机复验）

- 为落实单文件 ≤1000 行 / 硬上限 1500 行，以下文件按「整块搬移 + 同包扩展函数 / 区域
  composable」重构，逐行守恒校验、`compileDebugKotlin`、`./gradlew test`、`assembleDebug` 全绿；
  但 Compose 区域的参数与 `MutableState` 传递没有 JVM 单测覆盖，下次真机复验需覆盖：
  聊天页（消息渲染、工具/文件/图片卡片、终端与扩展键盘、上下文用量/差异/模板对话框、
  输入栏与 @ 文件提及）、Git 页五个对话框与 diff 视图、设置页全部弹窗、
  后台连接的通知与断线重连（`ChatScreen*.kt`、`ChatInputBar.kt`、`ChatMessageBubble.kt`、
  `ChatDialogs.kt`、`ChatOverlayCards.kt`、`ChatTerminal.kt`、`GitScreen*.kt`、
  `SettingsScreen.kt`、`StarBurstConnectionService*.kt`、`EventReducer*.kt`、
  `ChatViewModel*Ext.kt`）。
- 2026-09-20 已补 JVM 侧可测的纯逻辑回归单测：`ChatInputBarDisplayTest`（16 例，模型名截断
  `displayModelLabel` 含代理对安全、上下文预算比例/百分比/告警等级门槛
  `contextBudgetRatio` / `contextBudgetPercentage` / `contextBudgetLevel`）与
  `ContextBreakdownTest`（9 例，`computeContextBreakdown` 的向下折算、OTHER 兜底、
  工具输入输出计量、零分段过滤）。Compose 渲染与状态传递路径仍无 JVM 覆盖，需真机复验。
- 2026-09-20 已在 headless 模拟器（Android 34，独立 AVD `starburst_verify`）实测：
  输入栏渲染 + 模型名截断（Compose instrumentation `ChatInputBarModelLabelTest` 2 例 OK）、
  设置页主界面渲染 + 语言对话框（uiautomator）。受「无服务器连接」限制，聊天页消息卡片、
  终端、Git 页、后台连接等仍待真机复验。
- `TerminalEmulator.kt`（1278 行）、`ChatInputBar.kt`（1070 行）、`SettingsScreen.kt`（1073 行）、
  `StarBurstConnectionService.kt`（1075 行）仍高于 1000 行目标：继续拆分别需要把 30 余个可变字段
  （含 `cursorRow`/`cursorCol` 等 9 个 `private set` 属性）放宽为 `internal`/`internal set`、把巨型
  composable 拆成长参数列表区域函数、再切通知/UI 区块——收益低于回归风险，且这几处真机验证尚未
  完成，故本轮不做；`scripts/check-file-size.sh` 已把 1500 行硬上限接入 CI 防止继续恶化。

