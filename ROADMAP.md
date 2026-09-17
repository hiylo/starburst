<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# Roadmap

> This file is directional planning, **not a commitment**. Priorities and timing may change.
> Status markers: ✅ Released · 🚧 In progress · ⏳ Planned
> Related issues link with `#number`; versions are tracked with GitHub Milestones.

## Architecture conventions

- **A universal client — never change the official server, only improve the client.**
- **Persistent PTY**: when connecting to a server, establish one long-lived terminal session that
  Git, file editing, hardware info, restart, and other operations **share**, avoiding repeated
  shell startup overhead.
- Prefer existing REST APIs (`/config`, `/session/status`, `/global/health`, etc.); anything REST
  cannot do goes through the persistent PTY running shell commands.

## 1.1.0 (Released ✅)

### Server & service management — [#16](https://github.com/hiylo/starburst/issues/16)

- [x] Persistent PTY promoted to a "connection-level shared session" (was previously scoped to the Git page)
- [x] Server basics: CPU / memory / disk (PTY runs `free` / `df` / `/proc`)
- [x] Service basics: version, active sessions (`/global/health` + `/session/status`)
- [x] Service config view & edit (`GET/PATCH /config`, `/global/config`)
- [x] Service restart (PTY runs the restart command; feasibility depends on deployment/permissions)
- [x] Server health monitoring dashboard

### File editing — [#17](https://github.com/hiylo/starburst/issues/17)

- [x] File browser supports edit & save (writes go through the persistent PTY, e.g. `cat > path` / `tee`)
- [x] Undo / redo, save-conflict prompt

### Git deepening — [#18](https://github.com/hiylo/starburst/issues/18)

- [x] Selective staging (check files / hunks)
- [x] `fetch` / `stash` / `tag`
- [x] Commit history load-more (pagination)
- [x] Unpushed-commit indicator

### On-device model expansion — [#19](https://github.com/hiylo/starburst/issues/19)

- [x] Code completion (inline completion)
- [x] Conversation summary / code explanation

### Chat experience — [#20](https://github.com/hiylo/starburst/issues/20)

- [x] Markdown enhancements (mermaid diagrams, inline images, code-block copy/line numbers/run)
- [x] Message actions (edit & resend, regenerate, stop)
- [x] In-session search

## 2.0.0 (Released ✅)

> Released 2026-09-14. AI workbench, backend full mirror and live event push.

### AI workbench

- [x] Workbench entry from the session list (dashboard icon) + navigation route
- [x] Live event stream: push-driven with polling fallback, localized event text, heartbeat / high-frequency filtering, correct local timezone
- [x] Per-session aggregation with the latest event per session
- [x] Decision panel: latest AI reply (full text), pending questions with one-tap answers, quick-reply composer with voice input, jump into full session
- [x] Session rows: status ordering, pinned expanded header with scrollable decision content, long-press menu (enter / delete session)
- [x] Event title fallback: modified-file events show directory + filename with middle-ellipsis truncation

### Backend full mirror (starburst-backend)

- [x] `/api/opencode/*` full-path mirror (prefix strip, SSE passthrough, token auth) — M-OC/M3
- [x] Event collection into Postgres (`/api/events`, JSONB sanitize, event unwrap)
- [x] `/api/ws` live push channel (401 without token, 101 with token)
- [x] Accurate `/session/status`: snapshot + event aggregation, idle set explicitly, busy corrected by recent message activity, DB-seeded
- [x] App dual-channel: backend mirror preferred when healthy (2.5s probe), direct fallback on repeated failures
- [x] SSH tunnel forwards the backend port (18880) alongside the OpenCode port so pushes work through the tunnel

### Push notifications

- [x] Completion / question / permission / error notifications over the push channel (sound + vibration, exponential-backoff reconnect)
- [x] Heads-up banner instead of full-screen intents (Android 14 denies full-screen by default)
- [x] Notifications post even while the session is open (no foreground suppression)

### Fixed

- [x] SSH-tunnel backend port not forwarded → push notifications never fired
- [x] R8 stripped JSch JCE classes → SSH tunnels broken in release builds (keep rules added)
- [x] Release startup crash from stale incremental build artifacts (Hilt classes)

## 1.4.0 (Released ✅)

> 2026-09-14. Server management, AGENTS.md 工作流与工程重构。

### Server management

- [x] Automation rules (Rules): cron / git / webhook triggers, natural-language AI generation, execution history
- [x] API tokens (Tokens): create / revoke, plaintext shown once
- [x] Audit log (Audit): recent authenticated API actions

### AGENTS.md

- [x] Detect project `AGENTS.md`; generate / improve / instruction-based modify / manual edit, save via PTY
- [x] Animated generating indicator + "Continue" retry on failure

### Chat experience

- [x] "Continue" action on agent abort / retry failure
- [x] Fork branch navigation (jump between parent / child sessions)
- [x] Re-copy share link
- [x] Quick prompt templates (code review / tests / explain / fix bug)

### Engineering

- [x] Server-side ASR preferred over on-device model
- [x] Source split: ChatScreen / NavGraph / OpenCodeApi broken into per-concern files
- [x] Renamed project to StarBurst (app id, label, keystore)

## Now — 3.0.0 (Planned ⏳)

> 2026-09-17. Test Intelligence（智能测试体系）+ 客户端增量能力。

### Test Intelligence（智能测试体系）

> 详见 starburst-backend `docs/TEST_INTELLIGENCE.md`。通用、技术栈无关的测试能力：仓库理解与
> 契约提取、测试资产发现、Git 变更驱动增量测试、API/客户端字段级校验、被测环境供给、安全与合规
> 审计、失败归因与修复应用。后端新增 `internal/intel` 子系统，Web 与 APP 共用 `/api/intel/*` 端点。

- [ ] M1 项目+扫描器 — projects 扁平模型 + type/role 自动识别（含 monorepo 子项目 modules）+ 契约/实体/字段扫描（带 provenance）
- [ ] M2 Git delta — 增量测试（last_tested_sha→HEAD 影响面 ∪ 未解决问题）+ 问题闭环演进
- [ ] M3 测试资产发现 + 命令白名单 + 测试执行 + 报告解析 + flaky 隔离
- [ ] M4 修复应用 — 归因 → 修复建议 diff 预览 → 人工「应用/驳回」+ 备份回滚
- [ ] M5 环境管理 — 中间件/工具链/设备/远程节点逐项检测与安装 + 运行前门禁
- [ ] M6 Android 校验 — 必展示清单 + 契约↔界面差集 + 空值检查（CLIENT_MISSING_FIELD）
- [ ] M7 安全合规审计 — 依赖漏洞/静态/合规/LLM 评审 findings + 豁免/误报 + SBOM 导出
- [ ] M8 功能点 + AI — 功能点聚簇/拖动排序/单测/问题挂载/AI 对话 + 安全告警智能分级（SECURITY_WARNING）+ AI 建议规则
- [ ] M9 StarBurst APP 端 — 测试智能入口 + 功能点列表/详情 + 单测 + AI 对话 + 结果推送（能力对等，§6.1）。APP 为**轻量客户端**：不做 Web 全量内容，聚焦**关键操作**（发起单测、AI 对话归因、问题挂功能点/盯办、修复建议查看与「应用」、豁免/标记误报）与**关键信息预览**（功能点列表/详情、单测结果、问题摘要、SECURITY_WARNING 高亮）；环境管理/命令白名单/全局设置/规则编辑等复杂配置留 Web

### Client features（客户端增量）

- [ ] Token/用量统计（per 会话/服务器，后端聚合 + 工作台卡片）
- [ ] Prompt 模板库（可增删改）+ 会话模板（目录+系统提示词+模型+prompt 一键复用）
- [ ] 文件 Diff 查看器（PTY `git diff` / 文件对比，语法高亮）
- [ ] 服务日志实时 tail（PTY `tail -f` + 关键字过滤）
- [ ] 书签标签/分组
- [ ] 服务器监控告警（CPU/内存/磁盘阈值 → 推送）
- [ ] 通知栏快捷操作（RemoteInput 直接回复发起任务）
- [ ] Material You 动态取色主题
- [ ] 端侧代码补全/重写模型（MNN 离线）
- [ ] 编辑器内 AI 操作（选中代码 → 解释/重构/写测试 → diff 预览后应用）
- [ ] 会话时间线回放（agent 决策过程可视化）
- [ ] 自定义系统提示词 + 上下文预算管理（per 服务器）
- [ ] 多设备/团队同步（经 backend 同步配置/模板/书签/归档）
- [ ] 加密备份与恢复（Keystore 加密备份包，可跨设备恢复）

### Engineering quality

- [ ] Unit tests（ServerRepository 加密读写、EventReducer、SettingsRepository 等纯逻辑）
- [ ] 启动耗时 / 内存 / 卡顿性能基线

### Removed (product decision)

- [x] ~~Accessibility (TalkBack)~~ — dropped, not pursued
- [x] ~~TTS voice reading of replies~~ — dropped, not pursued

## 1.3.0 (Released ✅)

> Released 2026-09-13. Everything below is a **pure client** change — the opencode server is untouched.
> Exceptions: voice input (relies on an on-device ASR model or the starburst-backend engine) and image
> understanding (relies on the model supporting vision); neither involves opencode server-side code.

### Sessions & project management

- [x] Session search / filter (by title, directory, time)
- [x] Batch archive / delete
- [x] Session pinning with drag-to-reorder
- [x] Collapsible project groups, quick entry for recent projects

### Chat experience

- [x] Edit & resend after editing a message
- [x] Message quoting / reply (@ a message)
- [x] Long-press message menu: copy / re-edit & resend / quote & reply
- [x] Markdown table rendering
- [x] One-tap code-block copy + language label + long-code collapse
- [x] Streaming typewriter optimization, resume on reconnect
- [x] Send feedback: vibration / sound after sending, typing-cursor animation while generating
- [x] Timestamp grouping: message dividers by time (today / yesterday / earlier)
- [x] Scroll-to-bottom button polish: unread-new-message red dot
- [x] Empty state: new-session onboarding placeholder (quick commands / suggestions)
- [x] Voice input (ASR: on-device MNN sherpa-mnn streaming Zipformer, downloaded from ModelScope —
  free / offline / no third-party network service; hold-to-talk)
- [x] Image understanding (multimodal: depends on whether the selected model supports vision; the
  image entry is enabled only for vision-capable models, otherwise show "model does not support
  vision")

### Agents / tools

- [x] Permission-request cards + "always allow"
- [x] Todo list with live progress bars
- [x] Sub-agent tree / timeline view
- [x] Custom Slash command quick panel

### Servers & connectivity

- [x] Multiple servers online simultaneously, one-tap switching
- [x] SSH tunnel direct connection, connection health monitoring
- [x] Message pagination / history-loading optimization (load-older already exists)

### Localization & experience

- [x] Independent Dark / AMOLED theme toggles, follow-system
- [x] Finer-grained font size / line height
- [x] Global search: title / directory / time (across sessions and projects)
- [x] Session export (Markdown / JSON)

## 1.3.0 (Released ✅)

> Released 2026-09-13. Includes optional starburst-backend integration (task center, archives,
> server-side ASR fallback, backend-first suggestions, one-click backend install).

### Notifications & background

- [x] Actionable notifications: "view / retry" actions on task-complete/fail, tap to open session
- [x] Notification result summary: completion notification body carries result/error excerpt

### Content & search

- [x] Full-text message search (local SQLite, cross-session message body; FTS5 unavailable on device → plain table + LIKE), scoped to the current server
- [x] Message bookmarks: mark key messages, jump across sessions
- [x] Shared-session read-only viewer (server already exposes `share`)

### Theme & appearance

- [x] Theme schemes: Candy / Ocean / Sunset full color schemes + 5 extra accent colors

### Backend integration (starburst-backend, optional)

- [x] Task Center: background tasks / batch runs / archives with real API; AI plan breakdown, scheduling, dependency blocking
- [x] Backend-first next-step suggestions (`/api/llm/generate`) with source label
- [x] Server-side ASR streaming fallback (5-min availability cache)
- [x] Backend health probe + one-click install over SSH (install.sh)

### Security & privacy

- [x] App lock & sensitive masking — **removed by product decision** (out of scope for this release)

### Mobile experience

- [x] Home-screen Widget + App Shortcuts (long-press new session / global search)
- [x] Foldable / tablet two-pane adaptation polish

### Engineering quality

- [x] Large-message memory / streaming-render optimization (delta StringBuilder accumulation, 50ms throttle sampling)
- [x] Removed unconditional MNN model preload (native heap ~790MB → ~21MB)
- [x] SSE stall self-healing (busy-period REST fallback polling)
- [x] ~~Accessibility (TalkBack)~~ — dropped by product decision (3.0.0)

## Later — Backlog

- [x] Backend integration — task center with real API (tasks + batch + archives; zero-config: auto-derive `:18880` + default token `ocb_default`), WS realtime status, one-click backend install *(released 1.3.0)*
- [x] Task arrangement — structured fields (name/prompt/directory/session/dependency), scheduling (delay / at-time / recurring cron via `scheduled` status + scheduler), multi-step plans (auto `dependsOn` chain), conversational AI breakdown (`POST /api/tasks/generate`) *(released 1.3.0)*
- [x] Session archive to backend — long-press in session list / chat overflow menu → snapshot to backend archives; fixed upstream message-shape parsing so archives carry real transcript content *(released 1.3.0)*
- [x] Backend integration — stats / rules / audit / tokens management screens (APP-token readable, no web-session login needed) *(released 1.4.0)*
- [x] Voice input via backend streaming engine *(released 1.3.0 as server-side ASR fallback)*
- [ ] Remaining engineering quality (unit tests, performance testing) — moved to 3.0.0
