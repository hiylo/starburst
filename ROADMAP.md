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

### Server & service management — [#16](https://github.com/hiylo/opencode/issues/16)

- [x] Persistent PTY promoted to a "connection-level shared session" (was previously scoped to the Git page)
- [x] Server basics: CPU / memory / disk (PTY runs `free` / `df` / `/proc`)
- [x] Service basics: version, active sessions (`/global/health` + `/session/status`)
- [x] Service config view & edit (`GET/PATCH /config`, `/global/config`)
- [x] Service restart (PTY runs the restart command; feasibility depends on deployment/permissions)
- [x] Server health monitoring dashboard

### File editing — [#17](https://github.com/hiylo/opencode/issues/17)

- [x] File browser supports edit & save (writes go through the persistent PTY, e.g. `cat > path` / `tee`)
- [x] Undo / redo, save-conflict prompt

### Git deepening — [#18](https://github.com/hiylo/opencode/issues/18)

- [x] Selective staging (check files / hunks)
- [x] `fetch` / `stash` / `tag`
- [x] Commit history load-more (pagination)
- [x] Unpushed-commit indicator

### On-device model expansion — [#19](https://github.com/hiylo/opencode/issues/19)

- [x] Code completion (inline completion)
- [x] Conversation summary / code explanation

### Chat experience — [#20](https://github.com/hiylo/opencode/issues/20)

- [x] Markdown enhancements (mermaid diagrams, inline images, code-block copy/line numbers/run)
- [x] Message actions (edit & resend, regenerate, stop)
- [x] In-session search

## Now — 1.2.0

> Everything below is a **pure client** change — the opencode server is untouched.
> Exceptions: voice input (relies on an on-device ASR model) and image understanding (relies on the
> model supporting vision); neither involves server-side code.

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

## 1.3.0 (In progress 🚧)

> Pure client features — no backend dependency, opencode server untouched.

### Notifications & background

- [x] Actionable notifications: "view / retry" actions on task-complete/fail, tap to open session
- [x] Notification result summary: completion notification body carries result/error excerpt

### Content & search

- [x] Full-text message search (local SQLite, cross-session message body; FTS5 unavailable on device → plain table + LIKE)
- [x] Message bookmarks: mark key messages, jump across sessions
- [x] Shared-session read-only viewer (server already exposes `share`)

### Security & privacy

- [x] App lock & sensitive masking — **removed by product decision** (out of scope for this release)

### Mobile experience

- [x] Home-screen Widget + App Shortcuts (long-press new session / global search)
- [x] Foldable / tablet two-pane adaptation polish

### Engineering quality

- [x] Large-message memory / streaming-render optimization (delta StringBuilder accumulation, 50ms throttle sampling)
- [ ] Accessibility (TalkBack)

## Later — Backlog

- [x] Backend integration — task center with real API (tasks + batch + archives; zero-config: auto-derive `:18880` + default token `ocb_default`), WS realtime status, one-click backend install
- [x] Task arrangement — structured fields (name/prompt/directory/session/dependency), scheduling (delay / at-time / recurring cron via `scheduled` status + scheduler), multi-step plans (auto `dependsOn` chain), conversational AI breakdown (`POST /api/tasks/generate`)
- [x] Session archive to backend — long-press in session list / chat overflow menu → snapshot to backend archives; fixed upstream message-shape parsing so archives carry real transcript content
- [ ] Backend integration — stats / rules (needs Web-Session login flow on the client)
- [ ] Voice input via backend / sherpa-onnx (ASR JNI vs split-MNN conflict pending)
- [ ] Remaining engineering quality (unit tests, performance testing)
