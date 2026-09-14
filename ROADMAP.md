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

## Now — 1.4.0 (In development)

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
- [ ] Accessibility (TalkBack)

## Later — Backlog

- [x] Backend integration — task center with real API (tasks + batch + archives; zero-config: auto-derive `:18880` + default token `ocb_default`), WS realtime status, one-click backend install *(released 1.3.0)*
- [x] Task arrangement — structured fields (name/prompt/directory/session/dependency), scheduling (delay / at-time / recurring cron via `scheduled` status + scheduler), multi-step plans (auto `dependsOn` chain), conversational AI breakdown (`POST /api/tasks/generate`) *(released 1.3.0)*
- [x] Session archive to backend — long-press in session list / chat overflow menu → snapshot to backend archives; fixed upstream message-shape parsing so archives carry real transcript content *(released 1.3.0)*
- [x] Backend integration — stats / rules / audit / tokens management screens (APP-token readable, no web-session login needed) *(released 1.4.0)*
- [x] Voice input via backend streaming engine *(released 1.3.0 as server-side ASR fallback)*
- [ ] Remaining engineering quality (unit tests, performance testing)
