<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Cartoon style: rounded display font** — headings and labels now switch to a bundled
  Baloo 2 family (SIL OFL, 3 static weights, Latin-only, ~268KB) when Cartoon style is on.
  `body*` text and code stay on the system font / monospace in both modes, so long-form reading
  and Chinese prose are untouched. OFL notice ships at `assets/licenses/Baloo2-OFL.txt`.
- **Cartoon style: icon treatments** — two new wrappers in `ui/components/CartoonIcon.kt`.
  `CartoonInkIcon` inks the glyph silhouette (a 12-sample offset ring) and is used on the chat
  composer, top bars, list actions and FABs; `CartoonStickerIcon` sits the glyph on a tilted,
  outlined, hard-shadowed chip and is used on the 40dp+ empty-state and hero icons.
- **Cartoon style: `MaterialTheme.shapes` ramp** — the whole shape scale (10/16/26/32/38dp) is
  swapped while Cartoon style is on, so the M3 components that never took an adaptive shape
  (cards, snackbars, outlined text fields, chips, FABs, dropdown menus) round out too.

### Changed
- Settings → Appearance → Cartoon style description now mentions the rounded heading font and the
  ink-outlined icons (both locales).
- **Session list: batch compact** — a Compress action in the multi-select top bar now summarizes
  every selected session in one go (using each session's own model, falling back to the server
  default) to reduce context, mirroring the single-session Compact menu item.

## [3.0.0] - 2026-09-20

### Added
- **Project Overview** — a new entry in the chat top-bar ⋮ menu that scans the session's project
  directory and shows file/line statistics by type (code/comment/blank), largest files, and Git
  repository info (branch, commit count, last commit, modified files and diffstat). Shell-side
  scanning uses `find | xargs wc -c` + an awk line-classifier with C-style and `#`-style comment
  detection; Git stats are extracted via a separate command block.
- **Prompt & session templates** — an editable prompt-template library (add/edit/delete/reorder) and
  reusable session templates (directory + system prompt + model + prompt) for one-tap new sessions.
- **Session timeline** — a per-session timeline reconstructing tool calls, permission requests,
  questions, sub-agent spawns, and todo progress from in-memory event state.
- **Custom system prompt + context budget** — per-server system prompt (injected on first message) and
  a live token-usage estimate with a warning when approaching the model's context window.
- **File diff viewer** — syntax-highlighted diff of working-tree and pending-vs-saved edits in the
  workspace file browser.
- **In-editor AI actions** — explain / refactor / write tests on selected code (backend LLM with
  on-device MNN fallback), with copy/apply and a diff preview.
- **On-device code assist** — an offline complete/rewrite action via the on-device MNN model.
- **Service log live tail** — a streaming server-log viewer with keyword filter and auto-scroll.
- **Bookmark tags & groups** — tag bookmarks and filter/group the bookmark list by tag.
- **Encrypted backup & restore** — export/import settings + server configs as a passphrase-encrypted
  bundle (AES-256-GCM + PBKDF2) via the Storage Access Framework.
- **Token usage card** — a workbench card surfacing `/api/stats` (token usage / tasks / archives).
- **Notification quick actions** — inline RemoteInput reply from question/completion notifications.

### Changed
- **Top bar subtitle** — the chat top bar now shows the session's cumulative token usage
  (`totalInputTokens + totalOutputTokens`) and cost, distinct from the input bar's budget ring
  which shows the current context occupancy (`estimatedContextTokens / effectiveContextWindow`).
  The previous unified-estimate subtitle duplicated the input bar's figure; the two now have
  different purposes: the subtitle reports total consumption, the ring reports current context fill.
- **Flame theme** — palette retuned for a more vivid "passionate red": primary
  `#E53935`→`#FF1744` (Red A400), secondary `#F4511E`→`#FF6D00` (Orange A700),
  tertiary `#B71C1C`→`#D50000` (Red A700); dark-mode counterparts brightened accordingly.
- **Single-file size governance** — the largest Kotlin sources were split by responsibility with no
  behaviour change: chat screen (`ChatScreen*.kt`, `ChatInputBar.kt`), message bubble, chat
  dialogs and overlay cards, terminal panel, Git screen, settings screen, plus the SSE event
  reducer, the chat view model and the connection service moved their handler groups into
  same-package extension files. No source file exceeds the 1500-line hard cap now; the few files
  still above the 1000-line target are listed with their rationale in `KNOWN_ISSUES.md`.
- **CI** — JVM unit tests now run on every push and pull request (`./gradlew test` on JDK 17,
  `.github/workflows/ci.yml`), alongside the existing gitleaks secret scan and a new
  `scripts/check-file-size.sh` line-count guard wired into the same job.

### Security
- SSH host-key verification via known_hosts TOFU (previously `StrictHostKeyChecking=no`).
- Server config stored encrypted at rest (Android Keystore AES-GCM) with legacy plaintext migration.
- Passwords removed from Intents/PendingIntents (service resolves config by server id).
- WebView hardening (no mixed content, external links open in browser, HTTP-auth host check,
  block network loads for HTML previews).
- Install script pinned to a release tag with a randomized backend token.

### Fixed
- Long model display names pushed the context-budget ring and budget text off-screen in the bottom
  selector row. Model names now truncate at `MODEL_LABEL_MAX_CHARS` (24) with an ellipsis (the full
  name remains available in the model picker and context-usage dialog); truncation backs off before
  splitting a surrogate pair, so emoji/CJK model names cannot produce a lone surrogate. The budget
  ratio, percentage and warning/critical thresholds moved into testable pure functions
  (`ChatInputBarDisplay.kt`), with `ChatInputBarDisplayTest` (16 cases) and `ContextBreakdownTest`
  (9 cases) covering model-label truncation, budget math and the context breakdown scaling.
- **Build** — Hilt upgraded 2.51 → 2.52. Hilt 2.51 with Kotlin 2.0.21 produced debug APKs that
  crashed on launch (`NoClassDefFoundError: StarBurstApp_GeneratedInjector`, the generated
  `@GeneratedEntryPoint` was not packaged into dex) and release builds that failed R8 on the same
  missing class. 2.52 removes the runtime reference; both debug and release now build and the app
  launches. Verified with a Compose instrumentation test (`ChatInputBarModelLabelTest`) on an
  Android 34 emulator.
- Server connection could hang on "connecting" and block disconnect; blocking SSH/gateway work now
  runs outside the connection lock.
- Connection state could desync (working but shown as disconnected/connecting); flags now reconcile
  atomically with the connection state.
- MnnLlm/MnnAsr native access serialized under one lock (use-after-free guard).
- ASR recorder now stops on ViewModel clear (no more leaked microphone).

### Engineering
- Unit tests for backup payload, bookmark-tag backward compat, template serialization, and token
  estimation (15 cases).

### Backend (starburst-backend, non-intel)
- Hardware threshold alerts (CPU/memory/disk → `alert.hardware` push).
- Multi-device sync (`GET/PUT /api/sync`, last-write-wins).

## [2.0.0] - 2026-09-16

### Added
- **AI workbench** — a dashboard of all agent activity on the connected server: a live event stream
  (push-driven with polling fallback, localized event text, heartbeat/high-frequency filtering,
  correct local timezone), aggregated per-session rows, and a decision panel per session showing the
  latest AI reply, pending questions (with quick one-tap answers), a quick-reply composer with voice
  input, and a jump-into-full-session action.
- **Backend full mirror** — when a starburst-backend is reachable, the app routes through its
  `/api/opencode/*` mirror (Bearer token) for consistent state and faster pushes, with automatic
  fallback to a direct connection on repeated failures. Over SSH, the backend port (18880) is now
  forwarded alongside the OpenCode port so pushes work through the tunnel.
- **Event collection & push** — the backend records session events (`/api/events`) and pushes them
  live over `/api/ws`; the app turns session completion / question / permission / error events into
  heads-up notifications with sound and vibration, even while the session is open.
- **Accurate session status** — backend status endpoint combines a snapshot with event aggregation
  (idle set explicitly, busy corrected by recent message activity, seeded from the database), and
  push-driven updates keep the session list and workbench in sync.
- **Expanded session pinned header** — the expanded session's title row stays fixed while its
  decision content scrolls; all sessions support long-press menus (enter / delete session).

### Changed
- **Event display** — modified-file events show the full file path (directory + filename) with
  middle-ellipsis truncation instead of a possibly-ambiguous session title.
- **Status colors** — busy/processing is now shown in blue while new messages stay green, so a busy
  session with unread messages no longer shows two green dots.
- **Notifications** — event notifications use high-priority heads-up banners (sound + vibration)
  instead of full-screen intents, which Android 14 denies by default.

### Fixed
- SSH-tunnel deployments could not reach the backend (only the OpenCode port was forwarded), so push
  notifications never fired; the backend port is now forwarded too.
- R8 shrinking stripped JSch's JCE classes (`com.jcraft.jsch.jce.Random`), breaking SSH tunnels in
  release builds; keep rules now preserve them.
- Release builds could crash on startup due to stale incremental build artifacts (Hilt generated
  classes); full clean builds are reliable.
- Loading earlier messages in chat could jump the viewport to the oldest message; the list now uses
  reverse layout so the reading position is preserved.
- Sending a message or stopping only cleared pending questions locally; they are now rejected on the
  server so they stay gone after re-entering the session.
- In the workbench, tapping a question option sent it as a normal chat message instead of answering
  the question; options are now submitted as question answers.
- The keyboard could cover bottom fields and the confirm/cancel buttons in dialogs; dialogs now lift
  above the IME and text fields expose Next/Done keyboard actions for faster navigation.

## [1.4.0] - 2026-09-14

### Added
- **Server management screens** — Automation rules (Rules), API tokens (Tokens) and audit log (Audit) as first-class screens under server settings; rules support natural-language AI generation (`/api/rules/generate`), scheduled/git/webhook triggers and execution history.
- **AGENTS.md workflow** — detect a project's `AGENTS.md`, then generate (no file), improve (AI enhancement), modify (natural-language instruction) or manually edit it, and save it back to the project root via PTY. Generation shows an animated status indicator and failed attempts offer a "Continue" retry.
- **Continue on failure** — when the agent aborts or a retry fails, the error message now offers a "Continue" action that resumes the session.
- **Fork branch navigation** — a branch bar lets you jump between a session and its forked child sessions, and back to the parent.
- **Re-copy share link** — already-shared sessions can copy the share URL again from the overflow menu.
- **Quick prompt templates** — one-tap presets (code review / generate tests / explain code / fix bug) fill the composer, handy on mobile.

### Changed
- **Renamed project to StarBurst** — application id `org.hiylo.starburst`, app label StarBurst, new signing keystore.
- **Server-side ASR preferred** — voice input now prefers the backend streaming engine and falls back to the on-device MNN model.
- **"Suggestion service" renamed to "LLM service"** — the provider setting now reflects its broader role.
- **Source-code split** — ChatScreen / NavGraph / OpenCodeApi split into per-concern files for maintainability.

### Fixed
- AGENTS.md empty-file detection (server returns 200 + empty content for missing files).
- AGENTS.md editor layout pushing the save button off-screen.
- Stats/audit/rules/tokens endpoints accept the app token.

## [1.3.0] - 2026-09-13

### Added
- **Theme schemes** — three full cartoon color schemes (Candy / Ocean / Sunset) covering surface/container/secondary/tertiary tones, plus five extra accent colors (pink/orange/lime/sky/mint) in a wrap-around palette; non-default schemes replace the accent entirely for a complete look.
- **Backend-first suggestions** — next-step suggestions now prefer the backend-configured LLM (`/api/llm/generate`), then the app's external provider, then the on-device MNN model; the suggestion area labels its source (server / cloud / on-device / fallback).
- **Task Center** — background tasks, batch runs and archives via starburst-backend; AI plan breakdown (fresh + refine streaming), scheduling (immediate / delayed / at-time / cron), dependency blocking/unblocking, finished-task purge.
- **Home-screen Widget & App Shortcuts** — session/server/task snapshot with deep links (open session / connect server / new session / search / task center), plus long-press shortcuts.
- **Server-side ASR** — streaming speech recognition via the backend engine as a fallback when the on-device model isn't available, with a 5-minute per-server availability cache.
- **Bookmarks & full-text search** — message bookmarks and cross-session full-text search, both scoped to the current server (entry moved from Settings to the session-list top bar).
- **Backend detection & one-click install** — the server settings page probes `/api/health`; when the backend is absent and SSH is configured, a one-click installer runs `install.sh` over SSH and re-verifies.
- **Shared-session read-only viewer**.

### Changed
- Session-list top bar: "group by project" merged into the filter menu to reduce icon clutter.
- Full-text search results and bookmarks are now filtered by server instead of mixed across servers.

### Fixed
- **Native memory blow-up (~790MB)** — removed the unconditional MNN model preload on chat open; the model now loads on demand, dropping native heap from ~790MB to ~21MB.
- Voice-input echo duplication and late correction writes after send.
- Multi-line input height and fixed-size send button.
- SSE stall self-healing (busy-period REST fallback polling).
- Skills page SKILL.md font sizing.

## [1.2.0] - 2026-09-10

### Added
- **On-device voice input** — replaced the system speech recognizer with an on-device **MNN sherpa-mnn streaming Zipformer (bilingual zh/en, int8)** model downloaded from ModelScope with per-file SHA-256 verification; hold-to-talk with release-to-fill, slide-up-to-cancel, and a live volume waveform. The mic button only appears after the model is downloaded in Settings.
- **Global search** — cross-server, cross-project aggregation of all root sessions, searchable by title/directory/project/branch and filterable by time (today / 7 days / 30 days).
- **Session list** — one-tap multi-server switching via a server chip row; pinned-session drag-to-reorder through a dedicated reorder dialog; search time filter; compact layout reusing the chat compact-mode toggle.
- **SSH tunnel & health monitoring** — optional SSH tunnel (JSch local port-forwarding) to connect and restart the opencode service remotely; connection health (latency / heartbeat / status) on the server management page.
- **Chat** — custom Slash commands (`/name` inserts a prompt, persisted, add/remove); quote-reply that fills the input with a Markdown blockquote; session export (Markdown / JSON).

### Fixed
- **Status flicker / stuck conversation** — the session status poll no longer clobbers a live `busy` state with a stale snapshot while the SSE stream is connected; omitted sessions are only reconciled to idle when disconnected.
- **Foldable two-pane back navigation** — entering a child session now pushes onto the pane back stack and back returns to the parent session instead of exiting the whole screen.
- **Session list missing sessions across projects** — the list now uses the cross-project root-session endpoint and treats the server snapshot as authoritative, so sessions from other projects no longer go missing (nor linger as stale ghosts).
- **Archive not working** — archiving now writes the nested `time.archived` field the server expects, so archiving/unarchiving actually takes effect.
- **Session list cleared after opening a session** — opening/creating a session no longer wipes the other sessions from the list (partial lists are merged via upsert instead of replacing the whole snapshot).
- **Voice model download entry hidden** — fixed the bundled ASR JNI's MNN dependency so the Settings voice-model download entry (and the chat mic button) appears instead of silently failing to load.
- Recovered model/API layers (SSH config, session archive, custom commands, voice methods) that had been lost in a worktree reset.

## [1.1.0] - 2026-09-08

### Added
- **Server & service management** — connection-level shared PTY session; server management page with service info (version / active sessions) and system info (CPU/memory/disk/load); service config view and edit (`/config`); service restart.
- **Provider management** — add/edit/remove custom providers and their models (model ID + name), saved via `PATCH /config` with `apiKey` and other fields preserved.
- **Git page** — entry from the chat ⋮ menu (shown only when the project is a git repository), repo picker (top-level + submodules + nested repos), branch/status, change list (including untracked file contents), commit history (expanded file changes + per-file diffs), commit/push/pull (multi-remote selection) / checkout / create branch, and AI-generated commit messages (cloud-first with on-device fallback).
- **Git deepening** — selective staging (file checkboxes), `fetch`/`stash`/`tag`, paginated commit-history load-more, and an unpushed-commit indicator.
- **File editing** — file preview supports editing and saving (write via the shared PTY, base64 chunks).
- **Chat experience** — message actions reworked into a ⋮ dropdown menu (copy / regenerate / edit / summarize / undo); conversation summaries (message-level and session-level, cloud LLM first with on-device fallback, streamed, dismissible anytime).
- **Design alignment** — colored session status badges, semantic status colors, and unified diagnostics page rounding / AMOLED borders.

### Fixed
- Git first-open could misreport "not a repository" (directory/PTY race); now auto-retries.
- Provider save/delete toasts localized (were hard-coded English).
- Tech debt: cloud summary/commit-message token cap, provider config concurrent-write window, and large-file single-line base64 command overflow.

## [1.0.1] - 2026-09-08

### Changed
- On-device model download source switched from GitHub Releases to **ModelScope** (faster direct connections in mainland China), with per-file downloads and SHA-256 verification; at runtime the model prefers extracting from bundled assets, otherwise downloads from ModelScope.
- Settings → LLM "Test connection" now sends a real single-turn conversation and echoes the model's reply, with friendly classified error messages (bilingual zh/en).

### Fixed
- Bundled on-device model was never used at runtime (always prompted a download).

## [1.0.0] - 2026-09-07

### Added
- Native Material 3 chat interface with GFM Markdown, code blocks, syntax highlighting, and copy actions.
- Real-time message streaming with auto-scroll.
- **On-device suggestions** — next-step prompts generated fully offline by a bundled MNN model
  (Qwen3.5-0.8B), with an optional external OpenAI-compatible LLM provider for higher quality and
  silent fallback to the on-device model.
- **Bundled model auto-extraction** — the on-device model ships in `assets/models/` and is extracted
  on first use with progress; when a build has no bundled weights the model is downloaded from
  **ModelScope** (per-file, SHA-256 verified), shown in Settings with prepare/download progress.
- **ModelScope download source** — replaces the GitHub Release zip for model distribution (fast
  inside mainland China), with an app-tuned `config.json` (12 threads, low precision, thinking off).
- **Settings download entry** — a "on-device model" item in Settings with status, progress and a
  download button.
- **Accent color** — six brand accent palettes (indigo/violet/cyan/green/amber/red) applied
  app-wide across Light/Dark/AMOLED and included in settings sync.
- **Grouped settings cards** — settings sections rendered as rounded cards (12dp, surfaceContainer,
  1dp outline in AMOLED) matching the design system.
- **Conversation links** — same-origin links open inside an in-app WebView with Basic Auth; external
  links stay in the system browser, avoiding credential leaks.
- **Friendly LLM connection test** — Test connection sends a real single-turn conversation and
  shows the model's reply, mapping auth/404/4xx/5xx/timeout/DNS/connect/URL errors to friendly,
  bilingual (zh/en) messages.
- External LLM provider configuration screen (Base URL / model / API key stored in the Android Keystore).
- Adaptive two-pane layout on foldables and tablets (session list + chat side by side).
- Session management: search, favorite, categorize, fork, compact, share, export, and delete.
- Completed-but-unread sessions pinned to the top of the session list.
- Multi-server connection with stable reconnection and status polling.
- Terminal mode with PTY over WebSocket.
- Secure in-app updates verified by SHA-256 and signing certificate.
- DESIGN.md — the app's Material 3 design system document.

### Changed
- Suggestion generation moved from a server round-trip to on-device inference.
- Session list sorted by the last user message time instead of response time, eliminating
  re-ordering while streaming.
- Model download source switched from GitHub Releases to ModelScope.
- Removed the Termux local-runtime feature.

### Fixed
- Crash on JNI streaming callback due to R8-obfuscated interface class names.
- Native crash (Scudo) when a coroutine timeout cancelled a blocking MNN generation.
- Bundled model never being used at runtime (code only looked at the download path).
- Completed sessions not being removed from the list after delete.
- Parent sessions not showing as busy while their sub-agent children were running.
- Notification tasks channel producing no sound on some devices (channel now binds the system
  default notification sound).
