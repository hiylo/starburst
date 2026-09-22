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

## 3.0.0 (Released ✅)

> Released 2026-09-21. Test Intelligence roadmap kickoff + client increments: project overview,
> battery optimization, lifecycle-aware polling, E2E test suite, refined Flame theme.

### Test Intelligence (智能测试体系)

> Backend: starburst-backend `docs/TEST_INTELLIGENCE.md`. A tech-stack-agnostic testing subsystem
> (`internal/intel`) shared by Web and APP via `/api/intel/*`. Covers repository understanding &
> contract extraction, test-asset discovery, Git-delta-driven incremental testing, field-level API/client
> validation, test-environment provisioning, security & compliance audit, failure root-cause & fix
> application. Milestones M1–M9.

- [ ] M1 Projects + scanner — flat project model; type/role auto-detection via a pluggable Profile registry (Java/Maven, Go, Android, iOS, Web(Vue/React), BFF(GraphQL), Node, …); monorepo sub-project (modules) detection; entity/table/column + endpoint/field contract extraction with `source_file:line` provenance.
- [ ] M2 Git delta — incremental scope = `last_tested_sha..HEAD` impact ∪ unresolved issues; change-file classification (build/deps → full, config/SQL → integration, tests → direct, source → contract reverse-lookup); ref/force-push → full rescan; issue lifecycle (open/resolved/removed/regression).
- [ ] M3 Test assets + execution — discover & classify test assets (JUnit/Playwright/XCTest/Go/Node); per-project command allowlist; background `os/exec` within the allowlist; report parsing (surefire/playwright/go test); flaky auto-quarantine.
- [ ] M4 Fix application — root-cause → fix suggestion (patch draft with provenance) → diff preview → manual apply/reject; write modes (direct file / patch / clipboard / git branch+commit); backup + one-click rollback; audited.
- [ ] M5 Environment provisioning — middleware (containers, encrypted credentials) / toolchain (per-item install & uninstall) / Android devices (USB / wireless ADB, persisted binding) / remote nodes (SSH + capability routing + host-key check); pre-run gate (ready / missing-with-fix / unsupported).
- [ ] M6 Android validation — required-display field list; contract↔UI diff; null-value check → `CLIENT_MISSING_FIELD`.
- [ ] M7 Security & compliance audit — dependency vulns (Trivy/OSV/govulncheck/npm audit, dedup + snapshot cache), static/lint, AGENTS.md compliance rules, LLM review → findings; false-positive/waive; SBOM export (CycloneDX).
- [ ] M8 Features + AI — feature-point clustering, drag-sort, involved-ends; feature single-test (connectivity + expected data); issue↔feature linkage; AI chat with measured context; smart severity (SECURITY_WARNING for plaintext secrets); AI suggestion rules (configurable prompts, AI polish, per-rule scan).
- [ ] M9 StarBurst APP — Test Intelligence entry + feature list/detail + single-test + AI chat + push (parity, §6.1). APP is a **lightweight client**: key operations (single-test, AI chat root-cause, issue→feature linkage/tracking, view/apply fix suggestions, waive/false-positive) + key info preview (feature list/detail, single-test results, issue summary, SECURITY_WARNING highlight); heavy config (env, command allowlist, global settings, rule editing) stays on Web. *(client work moved to 3.1.0, next section)*

#### Recognition & correction (overrides)
- Confidence tiers (high/medium/low); inline table edit + prompt-assisted batch overrides; override layer (`auto_value`/`manual_value`) with provenance; anchor-change → re-review queue, never silently overwrite manual fixes.

#### Automation & push
- `intel-run` rules (GitLab push/tag webhook + cron); `intel.*` WS events to APP; `/api/batch` multi-project parallel analyze/run.

### Client features (客户端增量)

- [x] Token/usage stats (per session/server; backend aggregation + workbench card)
- [x] Prompt template library (add/edit/delete) + session templates (directory + system prompt + model + prompt one-tap reuse)
- [x] File diff viewer (PTY `git diff` / file compare, syntax highlight)
- [x] Service log live tail (PTY `tail -f` + keyword filter)
- [x] Bookmark tags/groups
- [x] Cartoon style: rounded display font (Baloo 2, OFL) + inked/sticker icon wrappers +
  `MaterialTheme.shapes` ramp
- [x] Session list multi-select Compress (batch compact)
- [ ] Server monitoring alerts (CPU/memory/disk thresholds → push) →3.1.0
- [x] Notification quick actions (RemoteInput reply to launch a task)
- [x] Material You dynamic color theme (Theme.kt `dynamicColor`, API 31+, with preference + tests)
- [x] On-device code completion/rewrite model (MNN, offline)
- [x] In-editor AI actions (select code → explain/refactor/write tests → diff preview then apply)
- [x] Session timeline replay (agent decision-process visualization)
- [x] Custom system prompt + context-budget management (per server)
- [ ] Multi-device/team sync (backend sync of config/templates/bookmarks/archives) →3.1.0 (backend channel)
- [x] Encrypted backup & restore (Keystore backup bundle, cross-device restore)

### Engineering quality

- [x] Unit tests (ServerRepository encryption read/write, SettingsRepository persistence, …) —
  `EventReducer` is already covered by `EventReducerTest` (33 cases), as is the encrypted backup
  envelope (`PasswordCrypto` round-trip and wrong-passphrase rejection in `BackupPayloadTest`).
  2026-09-21: 261 JVM unit tests + 2 Compose instrumentation + 5 Maestro E2E flows all green.
- [ ] Startup time / memory / jank performance baseline →3.1.0

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

## 3.1.0 (In progress 🚧)

> Theme: Test Intelligence lands on the APP (M9 wrap-up) + backend monitoring/sync capabilities surfaced
> to the APP + knowledge-base & document capabilities (backend v2.1.0 pairing), plus small fixes and
> cleanup. Backend M1–M8 are largely landed (`/api/intel/*` registered, `intel.*` WS pushes emitted;
> see starburst-backend `docs/TEST_INTELLIGENCE.md`). The APP is a **lightweight client** — heavy
> config (project creation / command allowlist / rule editing) stays on Web.

### Test Intelligence M9 — StarBurst APP side (main track)

- [x] Network layer: `/api/intel/*` endpoints + data models (project/module/feature/run/result/issue/fix/chat)
- [x] Push: consume `intel.*` events (run status, gate block, audit finding, fix suggest/apply, chat answer) + notifications
- [x] Entry: "Test Intelligence" icon in session list / workbench (backendReady-gated)
- [x] Project list screen: intel projects + modules + analysis status
- [x] Project detail screen: feature list/detail (ends, source, anchor)
- [x] Single tests: start a run (project/module) + run list (status badge/progress) + per-case results (passed/failed)
- [x] Issue loop: issue list (severity/location) + ack to resolved + link to feature
- [x] Fix suggestions: list + diff preview + apply (file/patch/branch) + rollback/reject
- [x] Feature AI chat: question history + context-bundled attribution answers
- [x] SECURITY_WARNING highlight; friendly empty/error states

### Server monitoring alerts → APP

- [x] Network layer: GET/POST `/api/alerts` (snapshot + threshold config)
- [x] Server management screen: monitoring-alert card (enable + CPU/mem/disk thresholds)
- [x] Push: consume `alert.hardware` → notifications (warning/info tier)
- [x] Local alert history (AlertHistoryRepository SQLite) + periodic `GET /api/alerts` reconciliation (WS drops frames)

### Knowledge base (KB) client

- [x] Entry: server-management (when backend ready) → Knowledge Base
- [x] Collection list / create / delete + document list
- [x] Ingest: text / file (`POST /api/kb/ingest`)
- [x] Semantic search (`/api/kb/search`, scored snippets)
- [x] `/api/documents/*` generated-document API client (BackendDocumentsApi; preview waits for a download endpoint)

### Multi-device/team sync (backend channel)

- [x] Network layer: GET/PUT `/api/sync` (per-key namespaces + revision drift detection)
- [x] New `SyncTransport` impl: BackendSyncTransport (starburst-backend `sync_bundle`)
- [x] SyncSettingsScreen gains a "starburst-backend" storage option

### Engineering fixes & cleanup

- [x] `update.json` stale (2.0.0 → 3.0.0)
- [x] Split `BackendGate.REQUIRED_BACKEND_VERSION` into "minimum gate" vs "install target" (MIN / INSTALL = 2.1.0)
- [x] ROADMAP stale checkboxes (Material You dynamic color / full-text search already implemented)

### Performance baseline + Baseline Profile

- [x] Startup/memory measurement script `scripts/measure-startup.sh` + baseline doc `docs/performance-baseline.md` (debug + soft render: cold-start median 1054ms, PSS ~50MB)
- [x] Baseline Profile: `profileinstaller` dep + `:baselineprofile` module + generated `app/src/main/baseline-prof.txt` (12,423 lines; release merged ART profile 10,596 → 17,908)

### Knowledge base & document capabilities (backend v2.1.0 pairing)

> Adopt the backend v2.1.0 document subsystem (knowledge base / document parsing /
> RAG-in-Prompt / document generation / in-session preview / chat-context-driven iteration).
> Backend design lives in starburst-backend `docs/RAG_PROMPT.md` and `docs/DOCUMENTS.md`;
> the APP stays a **lightweight client** — heavy management (ingest, templates, collection
> config) stays on Web. Prerequisite: raise `BackendGate.REQUIRED_BACKEND_VERSION` to **2.1.0**.

#### Knowledge-base references (RAG-in-Prompt passthrough)

- [ ] Session sends carry knowledge-base context automatically (spliced in the backend
      `prompt_async` mirror-proxy layer; zero APP changes)
- [ ] Optional: consume `X-Rag-Spliced` response header → "N sources brought in" / "no
      relevant material found" hint
- [ ] Source display for KB citations (doc / section, tap-through to preview)

#### Document generation & iteration

- [ ] Network layer: `/api/documents/*` (generate / regenerate) + `doc.event` WS consumption
- [ ] Entry points: workbench / chat action "Generate PPT / Word / Excel" (backendReady +
      version gate)
- [ ] Generation task card (progress + completion push) + product attached back into the
      session (`-@doc-{id}` filename convention)
- [ ] Attachment-card actions: "Regenerate" / "Revise per instruction" → regenerate (with
      recent session context)

#### In-session document preview

- [ ] Network layer: document-attachment URL resolution + preview-page address composition
- [ ] Document attachment card → WebView sheet loading the backend
      `webui/static/doc/preview.html` (pdf.js / mammoth / SheetJS / pptxjs; one renderer
      page shared by both clients)
- [ ] In-preview paging / zoom / download; full coverage of `.docx/.xlsx/.pptx/.pdf`

## Later — Backlog

- [x] Backend integration — task center with real API (tasks + batch + archives; zero-config: auto-derive `:18880` + default token `ocb_default`), WS realtime status, one-click backend install *(released 1.3.0)*
- [x] Task arrangement — structured fields (name/prompt/directory/session/dependency), scheduling (delay / at-time / recurring cron via `scheduled` status + scheduler), multi-step plans (auto `dependsOn` chain), conversational AI breakdown (`POST /api/tasks/generate`) *(released 1.3.0)*
- [x] Session archive to backend — long-press in session list / chat overflow menu → snapshot to backend archives; fixed upstream message-shape parsing so archives carry real transcript content *(released 1.3.0)*
- [x] Backend integration — stats / rules / audit / tokens management screens (APP-token readable, no web-session login needed) *(released 1.4.0)*
- [x] Voice input via backend streaming engine *(released 1.3.0 as server-side ASR fallback)*
- [ ] Remaining engineering quality (unit tests, performance testing) — moved to 3.0.0
