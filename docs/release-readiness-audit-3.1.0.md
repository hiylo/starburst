# 3.1.0 上线就绪度审计报告（功能完备性）

> 目的：对照 ROADMAP 3.1.0 承诺，逐项核实功能是否**真正闭环可用**、有无缺口/半成品/缺陷。
> 方法：3 个并行子代理按功能域（测试智能 / 告警·同步 / KB·文档）做「入口→VM→API→后端契约」三层
> 接线核对 + 闭环/反馈/边界检查；另核发版就绪度。
> 日期：2026-09-22。结论：**无阻塞上线项**；1 个明确缺陷已修，若干需求决策项见文末。

## 一、测试智能 M9 —— ✅ 可上线

- **完备**：会话列表入口（backendReady 门控）→ 项目列表 → 详情四区块（功能点 + 单测 + 问题 + 修复）→ AI 对话，三层接线全通、与后端契约逐字段一致；问题 ack/挂功能点、修复 apply(file/patch/branch)/回滚/驳回、diff 预览、失败/空/加载态齐全。
- **⚠️ 缺口（不阻塞，已完善 2026-09-22）**：
  - `intel.gate.blocked`/`fix.suggested`/`fix.applied`/`chat.answer`/`env.ready` 事件被解析但无通知/UI 出口（落 `Unit`）；门禁失败仅 Toast「运行失败」无原因 → **已修**：五类事件接入通知（`showGateBlockedNotification` 等），`startRun` 失败展示后端原因。
  - 发起单测后 run 状态**不自动更新**（不消费 `intel.run.event` 刷新列表，需手动刷新）→ **已修**：新增全局 `IntelRunEventBus`，推送层发布 `intel.run.event`，项目详情页订阅后按 projectId 实时合并 run 列表；run 终态顺带刷新问题/修复分块。
  - `loadRunResults` 失败会被**永久缓存为空**（幂等守卫使本页无法重试）→ **已修**：失败不再写空缓存，改为 `runResultsFailed` 集合标记，UI 显示「加载失败，点击重试」。
  - **SECURITY_WARNING 高亮是死代码**：后端 `recordRunIssues` 把 issue severity 硬编码 `"medium"` 且 `featureId=0`，App 红「!」徽标**永不触发**；真正的 security finding（`/api/intel/findings`）无客户端。
  - 项目列表缺「模块」展示（`BackendApi.intelModules` 零调用方）；功能点 `anchor` 未渲染。
  - 死代码：`intelRunDetail`/`featureRunTest` 无调用方（且后端仅 admin scope 可用，无碍）。

## 二、服务器监控告警 —— ✅ 可上线

- **完备**：GET/POST `/api/alerts` 契约一致；服务器管理卡片（启用+阈值编辑 0-100 + 越线红点）；`alert.hardware` 推送→通知+落库；本地历史（SQLite 7 天）+ 60s 轮询 reconcile 去重。
- **⚠️ 缺口（不阻塞）**：
  - severity **warning/info 分级未落地**（解析后闲置，alert/ok 同渠道同优先级）。
  - 告警历史**落库后不自动刷新 UI**（须手动点刷新）。
  - 阈值=0 后端静默回退 90（UI 回显 90 与输入不符）。
  - 删除服务器不清理历史（7 天自动过期）。
  - 非 2xx 响应反序列化成空快照（401/500 显示「监控关闭」而非失败提示；与项目未开 expectSuccess 的全局模式一致）。

## 三、多设备同步 —— ✅ 可上线（已修 1 个正式缺陷）

- **完备**：GET/PUT `/api/sync` 契约一致（key=global、revision 漂移检测）；SyncSettingsScreen 后端选项完整（仅 https/回环 + token 必填）；15 分钟 WorkManager 自动同步；`disconnect()` 只清同步凭据（已核修）。
- **❌→✅ 已修**：`syncGetBundle` 的 404→null 处理失效（HttpClient 未开 expectSuccess，`ClientRequestException` 不抛，404 被反序列化成空 Bundle）→ **全新后端首次同步必现假 CONFLICT**。已改为按 `status==404` 显式返回 null（`BackendApi.kt:764`），首次同步恢复 MISSING_UPLOAD 语义。`read()/write()` 的 `catch (ClientRequestException)` 为防御性保留。

## 四、知识库 KB —— ✅ 可上线（1 处承诺缺口需决策）

- **完备**：双入口（服务器管理 + 会话列表，均已指向已注册 `Kb` 路由）；集合列表/新建、文档列表、文本/文件摄入（大小预检+流式）、语义搜索（得分+片段），三层接线全通、契约一致。
- **⚠️ 承诺缺口**：
  - **删除集合/文档无 UI**：`BackendKbApi.deleteCollection/deleteDocument` 零调用方，而 ROADMAP 已勾选「集合列表/新建/删除」——**需决策**：补删除 UI 或按「重管理留 Web」改 ROADMAP 措辞。
  - 文件摄入仅 `text/*`（`contentBase64` 无调用方，后端支持 PDF/Office multipart）。
  - 大小上限错位：客户端 5 MiB vs 后端 JSON 体 4 MiB，4–5 MiB 文本会被 413 拒。
  - KB 文档不可预览（后端无下载端点，ROADMAP 已标延后）。

## 五、生成文档 —— ✅ 可上线（预览按钮需决策）

- **完备**：输入栏发起 → 生成对话框（PPT/Word/Excel）→ 消息卡片（下载/修改）→ 按意见 regenerate，契约一致、闭环。
- **⚠️ 缺陷（需决策）**：
  - **预览按钮运行期必然 401**：`DocumentPreviewSheet` 的 WebView 不带鉴权头，后端下载端点要求 `X-Web-Session`/`Authorization`（App token 走 WebView 两者皆无）。ROADMAP 将预览列为延后项，但按钮已暴露——**需决策**：隐藏预览入口，或修鉴权（注入 header）。
  - 预留 API（listDocuments/getDocument/deleteDocument）无调用方（无害）。

## 六、发版就绪度

| 项 | 状态 |
|---|---|
| `build.gradle.kts` versionCode/versionName | **8 / 3.0.0**（未 bump → 9 / 3.1.0，发版时改） |
| `update.json` | 3.0.0（发 3.1.0 时更新 versionName/versionCode/APK URL/sha256） |
| CHANGELOG | `[Unreleased] - 3.1.0`（发版时日期化） |
| release-notes | Draft（发版时补 sha256/安装校验） |
| 后端 | v2.1.0 开发中（最近提交：KB 完善/文档能力），App `MIN_BACKEND_VERSION=2.1.0` 待后端发版 |
| 性能基线 / Baseline Profile | 已就绪 |

## 七、上线前待决策项（非阻塞）

1. **文档预览按钮 401** —— 隐藏预览入口，或给 `DocumentPreviewSheet` 注入鉴权头（优先，功能价值高）。
2. **KB 删除集合/文档 UI** —— 补 UI（约 1 屏 + 2 弹窗），或撤回 ROADMAP「删除」勾选改标「Web 管理」。
3. **SECURITY_WARNING 高亮死代码** —— 后端修正 issue severity/featureId，或 App 接 `/api/intel/findings`（跨后端 v2.1.0 契约，属下一迭代）。
4. ~~测试智能实时性（run 状态自动更新、失败可重试）、gate/fix/chat 通知出口~~ —— **已完善（2026-09-22）**：`IntelRunEventBus` 实时合并 run + `runResultsFailed` 可重试 + 五类事件通知出口 + `startRun` 失败原因，单测覆盖（`TestIntelRunEventsTest`）。
5. 告警 severity 分级、历史自动刷新、阈值 0 语义、删服务器清理 —— 体验优化。