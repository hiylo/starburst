<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# Roadmap

> 本文件是方向性规划，**非承诺**。优先级与时间可能调整。
> 状态标记：✅ 已发布 · 🚧 进行中 · ⏳ 计划中
> 相关 issue 用 `#编号` 链接；版本用 GitHub Milestone 跟踪。

## 架构约定

- **通用客户端，不改变官方 server 端，只完善客户端功能**。
- **常驻 PTY**：连接服务器时建立一条持久终端会话，Git、文件编辑、硬件信息、重启等操作
  **共用同一条 PTY**，避免重复 shell 启动开销。
- 已有 REST API（`/config`、`/session/status`、`/global/health` 等）优先用 REST；REST 没有的
  能力统一走常驻 PTY 跑 shell 命令。

## 1.1.0（已发布 ✅）

### 服务器与服务管理 — [#16](https://github.com/hiylo/starburst/issues/16)

- [x] 常驻 PTY 提升为「连接级共享会话」（当前是 Git 页作用域）
- [x] 服务器基本信息：CPU / 内存 / 磁盘（PTY 跑 `free` / `df` / `/proc`）
- [x] 服务基本信息：版本号、运行中的活跃会话（`/global/health` + `/session/status`）
- [x] 服务配置查看与修改（`GET/PATCH /config`、`/global/config`）
- [x] 服务重启（PTY 跑重启命令，可操作性取决于服务端部署方式/权限）
- [x] 服务器健康监控仪表盘

### 文件编辑 — [#17](https://github.com/hiylo/starburst/issues/17)

- [x] 文件浏览器支持编辑与保存（写入走常驻 PTY，如 `cat > path` / `tee`）
- [x] 撤销 / 重做、保存冲突提示

### Git 深化 — [#18](https://github.com/hiylo/starburst/issues/18)

- [x] 选择性暂存（勾选文件 / hunk）
- [x] `fetch` / `stash` / `tag`
- [x] 提交历史加载更多（分页）
- [x] 未推送提交提示

### 端侧模型扩展 — [#19](https://github.com/hiylo/starburst/issues/19)

- [x] 代码补全（inline completion）
- [x] 对话总结 / 代码解释

### 聊天体验 — [#20](https://github.com/hiylo/starburst/issues/20)

- [x] Markdown 增强（mermaid 图、图片内联、代码块复制/行号/运行）
- [x] 消息操作（编辑重发、重新生成、停止生成）
- [x] 会话内搜索

## Now — 1.2.0

> 以下全部为**纯客户端**改动，不改 opencode 服务端。
> 例外：语音输入（依赖端侧 ASR 模型）、图片理解（依赖模型支持视觉），两者均不涉及服务端代码。

### 会话与项目管理

- [x] 会话搜索 / 过滤（按标题、目录、时间）
- [x] 会话归档 / 删除的批量操作
- [x] 会话置顶、拖拽排序
- [x] 项目分组可折叠、最近项目快捷入口

### 聊天体验

- [x] 消息编辑后重新发送（edit & resend）
- [x] 消息引用 / 回复（@某条消息）
- [x] 长按消息菜单：复制 / 重新编辑重发 / 引用回复
- [x] Markdown 表格渲染
- [x] 代码块一键复制 + 语言标签 + 折叠长代码
- [x] 流式输出打字机效果优化、断点续传
- [x] 发送反馈：发送后震动 / 音效、正在生成的打字光标动画
- [x] 时间戳分组：消息按时间显示分隔线（今天 / 昨天 / 更早）
- [x] 滚动回底部按钮优化：未读新消息小红点提示
- [x] 空状态：新会话引导占位（快捷命令 / 建议）
- [x] 语音输入（ASR：端侧 MNN sherpa-mnn 流式 Zipformer，从 ModelScope 下载——免费 / 可离线 / 不联网第三方，按住说话）
- [x] 图片理解（多模态：依赖当前选中模型是否支持视觉；仅支持视觉的模型启用图片入口，不支持时给出「当前模型不支持视觉」提示）

### Agent / 工具

- [x] 权限请求卡片式展示 + 「始终允许」
- [x] Todo 列表实时进度条
- [x] Sub-agent 树形 / 时间线视图
- [x] 自定义 Slash 命令快捷面板

### 服务器与连接

- [x] 多服务器同时在线、一键切换
- [x] SSH 隧道直连、连接健康度监控
- [x] 消息分页 / 历史加载优化（当前已有 load older）

### 本地化与体验

- [x] 深色 / AMOLED 主题独立切换、跟随系统
- [x] 字体大小 / 行距更细粒度
- [x] 全局搜索：标题 / 目录 / 时间搜索（跨会话、跨项目）
- [x] 导出会话（Markdown / JSON）

## 3.0.0（已发布 ✅）

> 2026-09-21 发布。测试智能体系（Test Intelligence）启动 + 客户端增量能力：项目概览、耗电优化、
> 生命周期感知轮询、E2E 测试套件、圆体卡通风格、Flame 主题精修。

### 测试智能体系（Test Intelligence）

> 后端：starburst-backend `docs/TEST_INTELLIGENCE.md`。新增与编排子系统平级的技术栈无关测试子系
> 统（`internal/intel`），Web 与 APP 共用 `/api/intel/*` 端点。覆盖：仓库理解与契约提取、测试资产
> 发现、Git 变更驱动增量测试、API/客户端字段级校验、被测环境供给、安全与合规审计、失败归因与
> 修复应用。里程碑 M1–M9。

- [ ] M1 项目+扫描器 — 扁平项目模型；type/role 自动识别（可插拔 Profile 注册表：Java/Maven、Go、Android、iOS、Web(Vue/React)、BFF(GraphQL)、Node…）；monorepo 子项目（modules）识别；实体/表/列 + 端点/字段契约提取，带 `source_file:line` provenance。
- [ ] M2 Git delta — 增量范围 = `last_tested_sha..HEAD` 影响面 ∪ 未解决问题；变更文件分类（构建/依赖 → 全量、配置/SQL → 集成、测试 → 直接入范围、源码 → 按契约反查）；ref/force-push 回退全量；问题闭环（open/resolved/removed/regression）。
- [ ] M3 测试资产+执行 — 测试资产发现与分类（JUnit/Playwright/XCTest/Go/Node）；每项目命令白名单；白名单内后台 `os/exec` 执行；报告解析（surefire/playwright/go test）；flaky 自动隔离。
- [ ] M4 修复应用 — 归因 → 修复建议（patch 草稿带 provenance）→ diff 预览 → 人工应用/驳回；写回形态（直接写文件/补丁/剪贴板/git 分支+提交）；备份 + 一键回滚；审计留痕。
- [ ] M5 环境供给 — 中间件（容器、口令加密）/工具链（逐项安装与卸载）/Android 设备（USB/无线 ADB，绑定持久化）/远程节点（SSH + 能力标签路由 + host key 校验）；运行前门禁（就绪/缺失可修给途径/平台不支持明确告知）。
- [ ] M6 Android 校验 — 必展示字段清单；契约↔界面差集；空值检查 → `CLIENT_MISSING_FIELD`。
- [ ] M7 安全合规审计 — 依赖漏洞（Trivy/OSV/govulncheck/npm audit，去重+快照缓存）、静态/lint、AGENTS.md 合规规则、LLM 评审 → findings；误报/豁免；SBOM 导出（CycloneDX）。
- [ ] M8 功能点+AI — 功能点聚簇、拖动排序、涉及端；功能点单测（连通性+期望数据）；问题↔功能点挂载；带实测上下文 AI 对话；智能分级（明文敏感字段升 SECURITY_WARNING）；AI 建议规则（提示词可配、AI 润色、逐条扫描）。
- [ ] M9 StarBurst APP 端 — 测试智能入口 + 功能点列表/详情 + 单测 + AI 对话 + 结果推送（能力对等，§6.1）。APP 为**轻量客户端**：关键操作（单测、AI 对话归因、问题挂功能点/盯办、修复建议查看与应用、豁免/误报）+ 关键信息预览（功能点列表/详情、单测结果、问题摘要、SECURITY_WARNING 高亮）；复杂配置（环境、命令白名单、全局设置、规则编辑）留 Web。**（客户端实施已移至 3.1.0，见下章）**

#### 识别与校正（overrides）
- 置信度分级（high/medium/low）；表格直改 + 提示词批量辅助；覆写层（`auto_value`/`manual_value`）带 provenance；锚点变更 → 待复核队列，绝不静默吞掉人工修正。

#### 自动化与推送
- `intel-run` 规则（GitLab push/tag webhook + cron）；`intel.*` WS 事件推送到 APP；`/api/batch` 多项目并行分析/执行。

### 客户端增量功能

- [x] Token/用量统计（每会话/每服务器，后端聚合 + 工作台卡片）
- [x] Prompt 模板库（增删改）+ 会话模板（目录+系统提示词+模型+prompt 一键复用）
- [x] 文件 Diff 查看器（PTY `git diff` / 文件对比，语法高亮）
- [x] 服务日志实时 tail（PTY `tail -f` + 关键字过滤）
- [x] 书签标签/分组
- [ ] 服务器监控告警（CPU/内存/磁盘阈值 → 推送）（→3.1.0）
- [x] 通知栏快捷操作（RemoteInput 直接回复发起任务）
- [x] Material You 动态取色主题（Theme.kt `dynamicColor`，API 31+，含首选项与测试）
- [x] 端侧代码补全/重写模型（MNN 离线）
- [x] 编辑器内 AI 操作（选中代码 → 解释/重构/写测试 → diff 预览后应用）
- [x] 会话时间线回放（agent 决策过程可视化）
- [x] 自定义系统提示词 + 上下文预算管理（每服务器）
- [ ] 多设备/团队同步（经 backend 同步配置/模板/书签/归档）（→3.1.0，后端通道）
- [x] 加密备份与恢复（Keystore 加密备份包，跨设备恢复）
- [x] 圆体卡通风格：圆体标题字体（Baloo 2, OFL）+ 墨线/贴纸图标 + `MaterialTheme.shapes` 圆角阶
- [x] 会话列表多选 Compress（批量压缩）

### 工程质量

- [x] 单元测试（ServerRepository 加密读写、SettingsRepository 持久化等纯逻辑）——
  `EventReducer` 已由 `EventReducerTest`（33 例）覆盖，加密备份包也已覆盖
  （`BackupPayloadTest` 验证 `PasswordCrypto` 往返与错误口令拒绝）。
  2026-09-21：261 例 JVM 单测 + 2 例 Compose instrumentation + 5 条 Maestro E2E 全绿。
- [ ] 启动耗时 / 内存 / 卡顿性能基线（→3.1.0）

### 移除（产品决策）

- [x] ~~无障碍（TalkBack）~~ — 移除，不做
- [x] ~~语音朗读回复（TTS）~~ — 移除，不做

## 3.1.0（进行中 🚧）

> 主题：Test Intelligence 落地到 App（M9 收尾）+ 后端监控/同步能力透传到 App + 知识库与文档
> 能力（后端 v2.1.0 配套），配套少量修复与清理。
> 后端 M1–M8 大体已落地（`/api/intel/*` 已注册，含 intel.* WS 推送，详见 starburst-backend
> `docs/TEST_INTELLIGENCE.md`）；APP 为**轻量客户端**：重配置（创建项目/命令白名单/规则编辑）留 Web。

### 测试智能 M9 — StarBurst APP 端（主线）

- [x] 网络层：`/api/intel/*` 端点 + 数据模型（项目/模块/功能点/run/结果/问题/修复/问答）
- [x] 推送：`intel.*` 事件消费（run 状态、门禁拦截、审计发现、修复建议/应用、问答完成）+ 通知
- [x] 入口：会话列表 / 工作台「测试智能」入口（backendReady 门控）
- [x] 项目列表页：intel 项目 + 模块 + 分析状态
- [x] 项目详情页：功能点列表/详情（涉及端、source、锚点）
- [x] 单测：发起 run（项目/模块）+ run 列表（状态徽标/progress）+ 结果（逐用例 passed/failed）
- [x] 问题闭环：问题列表（severity/location）+ ack 标记 resolved + 挂载功能点
- [x] 修复建议：列表 + diff 预览 + 应用（file/patch/branch）+ 回滚/驳回
- [x] 功能点 AI 对话：问答历史 + 带上下文归因回复
- [x] SECURITY_WARNING 高亮；空状态/错误友好提示

### 服务器监控告警 → App

- [x] 网络层：GET/POST `/api/alerts`（快照 + 阈值配置）
- [x] 服务器管理页：监控告警卡片（启用 + CPU/内存/磁盘阈值）
- [x] 推送：`alert.hardware` 消费 → 通知（warning/info 分级）
- [x] 本地告警历史（AlertHistoryRepository SQLite）与周期轮询 `GET /api/alerts` 兜底对齐（WS 会丢帧）

### 多设备/团队同步（后端通道）

- [x] 网络层：GET/PUT `/api/sync`（key 命名空间 + revision 漂移检测）
- [x] `SyncTransport` 新实现：BackendSyncTransport（starburst-backend `sync_bundle`）
- [x] SyncSettingsScreen 新增后端选项「starburst-backend」

### 知识库（KB）客户端

- [x] 入口：服务器管理页（后端就绪时）→ 知识库
- [x] 集合列表/新建/删除 + 文档列表
- [x] 摄入：文本 / 文件（`POST /api/kb/ingest`）
- [x] 语义搜索（`/api/kb/search`，带得分与片段）
- [x] `/api/documents/*` 生成文档 API 客户端（BackendDocumentsApi，预览留待下载端点就绪）

### 工程修复与清理

- [x] `update.json` 落后（2.0.0 → 3.0.0）
- [x] `BackendGate.REQUIRED_BACKEND_VERSION` 拆分「最低门槛」与「安装目标版本」（MIN / INSTALL = 2.1.0）
- [x] ROADMAP 过期勾选修复（Material You 动态取色 / 全文搜索已实现）

### 性能基线 + Baseline Profile

- [x] 启动/内存测量脚本 `scripts/measure-startup.sh` + 基线文档 `docs/performance-baseline.md`（debug+软渲染：冷启动中位数 1054ms，PSS ~50MB）
- [x] Baseline Profile：`profileinstaller` 依赖 + `:baselineprofile` 模块 + 生成 `app/src/main/baseline-prof.txt`（12,423 行，release merged ART profile 10596→17908）

### 知识库与文档能力（后端 v2.1.0 配套）

> 接入后端 v2.1.0 的文档子系统（知识库 / 文档解析 / RAG-in-Prompt / 文档生成 / 会话内预览 /
> 聊天上下文驱动迭代）。后端设计见 starburst-backend `docs/RAG_PROMPT.md` 与
> `docs/DOCUMENTS.md`；APP 仍为**轻量客户端**，复杂管理（ingest、模板、集合配置）留 Web。
> 前置：`BackendGate.REQUIRED_BACKEND_VERSION` 提升到 **2.1.0**。

#### 知识库引用（RAG-in-Prompt 透传）

- [ ] 会话发送自动带知识库上下文（后端 `prompt_async` 镜像代理层拼装，App 零改动）
- [ ] 可选：消费 `X-Rag-Spliced` 响应头 → 提示「已带入 N 条资料」/「未检索到相关资料」
- [ ] 知识库引用来源展示（来源文档 / 章节，点击跳转预览）

#### 文档生成与迭代

- [ ] 网络层：`/api/documents/*`（generate / regenerate）+ `doc.event` WS 消费
- [ ] 入口：工作台 / 聊天动作「生成 PPT / Word / Excel」（backendReady + 版本门控）
- [ ] 生成任务卡片（进度 + 完成推送）+ 产物附件回投会话（`-@doc-{id}` 文件名约定）
- [ ] 附件卡片操作：「重新生成」/「按意见修改」→ regenerate（带最近会话上下文）

#### 会话内文档预览

- [ ] 网络层：文档附件 url 解析 + 渲染页地址拼接
- [ ] 文档附件卡片 → WebView 弹层加载后端 `webui/static/doc/preview.html`（pdf.js / mammoth /
      SheetJS / pptxjs，双端共用一套渲染页）
- [ ] 预览内翻页 / 缩放 / 下载；`.docx/.xlsx/.pptx/.pdf` 全覆盖

## Later — Backlog

- [ ] 消息内容全文搜索（本地 FTS，需评估数据量）
- [ ] 工程质量（单元测试、性能、无障碍、平板/折叠屏适配） — [#23](https://github.com/hiylo/starburst/issues/23)
