# 3.1.0 代码审计报告（2.X 之后版本）

> 审计范围：`git diff v2.0.0..HEAD`（App 3.0.0 + 3.1.0 全部改动，264 文件 / 48,828 行新增）。
> 深度审查聚焦 3.1.0 新增（`v3.0.0..HEAD`，71 文件 / 20,640 行，其中 baseline-prof.txt 12,423 行为生成物）。
> 方法：机械规范扫描（全仓）+ `lintDebug` 静态分析 + 3 个并行深度审查子代理（测试智能 / 告警·同步·KB·文档 / 服务层·导航·门控）。
> 日期：2026-09-22。

## 一、机械扫描结论（全通过）

| 项 | 结果 |
|---|---|
| 单文件行数 >1500 | 无 |
| 文件头 Copyright | 全部存在（部分 `Date` 缺 `HH:mm:ss`，见低） |
| 禁用注解（@Tag/@Operation/@Schema/@Api/Knife4j/Swagger/SpringDoc/@SuppressFBWarnings） | 无 |
| 中英 strings key 对齐 | 完全对齐（0 差异） |
| TODO/FIXME/HACK | 无 |
| `check-secrets.sh --all` | 干净 |
| `lintDebug` | 无 error（BUILD SUCCESSFUL） |

## 二、高严重（需发版前修复）

- **H1 知识库入口必现崩溃** — `NavGraphSessions.kt:331/481` 导航到 `Screen.KnowledgeBase`（route `knowledge_base`），但该 route **无任何 NavGraph 注册**（`NavGraphKb.kt` 只注册 `kb`/`kb_collection`）；后端就绪后点会话列表知识库图标必抛 `IllegalArgumentException`。修复：入口改走 `Screen.Kb.createRoute(...)`，删除 `Screen.kt` 孤儿 `KnowledgeBase`/`KnowledgeBaseCollection`。
- **H2 SSH 隧道重建后 Intel 推送永久失效** — `intelPushJob` 仅在 `connectInternal` 启动一次（`:544`）；`rebuildConnection`（`:939`）/`replaceSshSession`（`:790`）重建隧道（`setPortForwardingL(0,…)` 每次随机端口）时只重启 `pushJob`，不重启 `intelPushJob`，其持续订阅旧 `127.0.0.1:<旧端口>`，网络切换/假死恢复后 `intel.*`/`alert.hardware` 通知静默失效且残留空转 job。修复：重建时同步 cancel 并按新 `backendLocalPort` 重启。
- **H3 审计发现 high 级从不通知** — `StarBurstConnectionServiceGatewayExt.kt` 用 `severity in {"warning","critical"}` 过滤，但后端 `finding.Severity` 取值是 `critical|high|medium|low|info`（无 `warning`）。修复：改为 `severity in setOf("high","critical")`。
- **H4 `disconnect()` 误清全部凭据** — `SyncRepository.kt:389` 调 `secretStore.clearAll()`，会清空与同步无关的 `LLM_PROVIDER_API_KEY`、`SFTP_PASSWORD`。修复：disconnect 只清除 `GITHUB_TOKEN/WEBDAV_PASSWORD/SYNC_PASSPHRASE/BACKEND_TOKEN` 四个 sync 相关 key。
- **H5 KB 文件摄入 OOM** — `KbCollectionDetailViewModel` `readTextFile` 先 `readBytes()` 全量读入内存再判 5MB 上限。修复：用 `OpenableColumns.SIZE` 预检 + 流式截断。
- **H6 告警历史双路写入重复** — 推送路径（`GatewayExt`）与 60s 轮询（`reconcileAlerts`）无去重，同一越线事件可能落两条。修复：reconcile 落库前按时间窗查重，或 push 后同步更新 snapshot 基准。
- **H7 文档预览/下载安全** — `DocumentPreviewSheet` 用 `requestUrl.startsWith(backendUrl)` 白名单（前缀绕过）；`BackendDocumentsApi.resolveDocumentUrl` 对绝对 URL 原样放行，`downloadDocument` 会把 Bearer token 附加到任意第三方 host。修复：解析 host+port 精确比较；跨源时拒绝或剥离 Authorization。

## 三、中

- 新 Intel/告警/审计三条通知忽略 `notificationsEnabled`/`silentNotifications`（用户关通知/静默仍响铃）。
- 三条通知标题/正文硬编码中文（未走 strings.xml）。
- notifId 未按 server 隔离（`100_000+run.id` 等），跨服务器互相覆盖；`abs(Int.MIN_VALUE)` 仍负。
- `createNotificationChannels` 每次进程启动 delete+recreate 渠道（`BuildConfig.VERSION_CODE>=1` 恒真，无持久化标记），重置用户渠道定制。
- `intelPushJob` 在 `connections.compute` 内、状态入 map 前启动，极小竞态。
- run 结果加载失败被永久缓存为空（`loadRunResults` 失败写 `emptyList`，幂等守卫使重试失效）。
- `task.failure` 解析契约不匹配（后端无 `status` 字段，事件被静默丢弃；当前无功能影响）。
- `TestIntelProjectViewModel` `projectId` 静默兜底 0，UI 无错误说明。
- 通知 PendingIntent 为裸 MainActivity，无法深链到 run/项目/集合。
- `ServerManagementViewModel.refresh()` 不重载告警历史；`reconcileAlerts` 后不刷新列表。

## 四、低

- 未使用 import（ServerManagementViewModel/ServerManagementScreen/SyncSettingsScreen/DocumentGenerateDialog/ChatInputBar 等 6 处）。
- 文件头 `Date : 2026/09/22` 缺 `HH:mm:ss` 且缺 `Contact` 行（BackendAlerts/BackendSync/AlertHistoryRepository/Kb*/NavGraphIntel/NavGraphKb 等）。
- 行宽超 120（NavGraphIntel.kt:30/62、BackendPushListener.kt:168/171、StarBurstConnectionServiceGatewayExt.kt:290 等）。
- `DocumentPreviewSheet` 硬编码中文「文档预览」/「预览页加载失败」；WebView `databaseEnabled/allowContentAccess=true` 非必需；关闭未 `destroy()`。
- 告警/审计通知 `VISIBILITY_PUBLIC`，正文含文件路径等敏感信息锁屏可见。
- 无障碍：项目卡/可展开行裸 `clickable` 无 role/contentDescription。
- `feature.ends()` 在组合中反复解析 JSON（应 `remember`）。
- `test_intel_feature_security` 等资源未使用；`BackendApi.intelRunDetail`/`featureRunTest` 无调用方（死代码）。
- 告警历史 `onUpgrade` 直接 DROP 表；删除服务器未清 `AlertHistoryRepository.clear(serverId)`。
- 触发一次渠道重建后未持久化（同 H11 根因，已列中）。

## 五、结论

3.1.0 代码整体质量较高：异常隔离、token 脱敏、并发清理、解析安全降级、中英资源对齐均扎实。**发版前必须修 H1（必崩）、H2/H3（功能静默失效）、H4（数据丢失）、H5（OOM）、H7（安全）**；H6/H8/H11 及中低项可随后续小版本处理。

## 六、修复状态（2026-09-22，已编译 + 272 单测全绿 + check-secrets 干净）

已修复：
- H1 ✅ 会话列表知识库入口改走已注册的 `Screen.Kb`，删除 `Screen.KnowledgeBase`/`KnowledgeBaseCollection` 孤儿 route
- H2 ✅ `rebuildConnection`/`replaceSshSession` 重建隧道时同步重启 `intelPushJob`（按新 backendLocalPort）
- H3 ✅ 审计发现通知过滤改为 `severity in {high, critical}`
- H4 ✅ `LocalSyncSecretStore` 新增 `clearSyncSecrets()`（仅清 4 个同步凭据），`disconnect()` 改调它
- H5 ✅ KB 摄入：`OpenableColumns.SIZE` 预检 + 8KB 流式限 5MB
- H6 ✅ `reconcileAlerts` 落库前按 5 分钟窗口同 metric+state 去重
- H7 ✅ 文档预览/下载改 scheme+host+port 精确同源校验；WebView 移除 `databaseEnabled`/`allowContentAccess`，DisposableEffect destroy
- H8 ✅ 三条新通知接入 `notificationsEnabled`/`silentNotifications`（silent 走 TASKS_SILENT）
- H9 ✅ 通知文案迁入 strings.xml（中英，4 个新 key + 复用既有）
- H10 ✅ notifId 掺 serverId 盐 + `and Int.MAX_VALUE` 规避负值
- H11 ✅ 渠道迁移加 SharedPreferences 标记 `notif_channels_migrated_v1`，仅首次重建

遗留（低，建议随后续小版本处理）：文件头 `Date`/`Contact` 统一、行宽 >120、未使用资源/死代码、无障碍 role/contentDescription、通知 PendingIntent 深链、锁屏可见性、`run` 结果失败缓存空、`task.failure` 契约、告警历史 `onUpgrade` DROP 表。
