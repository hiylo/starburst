<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

<div align="center">

# StarBurst (Android)

**A native Android client for [OpenCode](https://github.com/anomalyco/opencode) AI coding agents**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-26-brightgreen)]()
[![Platform](https://img.shields.io/badge/Platform-Android-blue)]()

**English · [简体中文](README.zh-CN.md)**

</div>

StarBurst is a feature-rich, native Material 3 client that lets you drive your
[OpenCode](https://opencode.ai) AI coding agent from your phone or tablet. Connect to any OpenCode
server over the network, chat with your agent, browse workspace files, run a full terminal, and
manage sessions — all from a mobile-first UI.

> This is an **independently maintained community project**, not affiliated with the OpenCode team.

---

## ✨ Features

- **Native chat** — Material 3 UI with GFM Markdown, code blocks, syntax highlighting and copy actions
- **Real-time streaming** — messages stream in live with auto-scroll
- **On-device suggestions** — next-step prompts are generated **fully offline** via an on-device
  MNN model (Qwen3.5-0.8B). The weights are fetched from **ModelScope** on first use (fast inside
  mainland China) with per-file SHA-256 verification; release builds that pre-seed
  `app/src/main/assets/models/` instead auto-extract them from the APK. With a backend configured,
  suggestions prefer the **backend LLM first**, then the external provider, then on-device — each
  showing its source label
- **On-device voice input** — hold-to-talk speech recognition powered by an on-device **MNN
  sherpa-mnn streaming Zipformer (bilingual zh/en)** model downloaded from ModelScope (per-file
  SHA-256 verified). Hold to talk, release to fill the input, slide up to cancel, with a live
  volume waveform. The mic button appears only after the model is downloaded in Settings
- **External LLM provider** — bring your own OpenAI-compatible endpoint (`/v1/chat/completions`)
  for suggestion generation, with automatic silent fallback to the on-device model. The
  **Test connection** button sends a real single-turn conversation and shows the model's reply
- **Conversation links** — links pointing to the connected server open inside an in-app WebView
  (with Basic Auth); all other links keep the system browser, so credentials never leak
- **Git** — a built-in Git page (from the chat ⋮ menu when the project is a repository) with branch/status, change list and diffs, commit history, and commit/push/pull/checkout/branch operations — plus AI-generated commit messages (cloud LLM with on-device fallback)
- **Server & provider management** — server info (version/active sessions), CPU/memory/disk, config view/edit, restart, and custom provider management (add/edit/remove providers and models)
- **File editing** — edit and save workspace files in-app
- **Conversation summaries** — per-message or whole-session summaries via the cloud LLM (with on-device fallback), streamed live
- **Accent color & theme schemes** — six brand accent palettes plus three full cartoon theme schemes
  (**Candy / Ocean / Sunset**) that recolor the whole app, applied app-wide (light/dark/AMOLED) and
  synced
- **Bookmarks & full-text search** — bookmark key messages and full-text search across a server's
  messages (both scoped to the current server)
- **Task Center (starburst-backend)** — background tasks, batch runs and archives, with AI plan
  breakdown, scheduling and dependency blocking; the backend is detected automatically with a
  one-click install over SSH when missing
- **Workspace files** — browse project folders, preview highlighted text, Markdown and images, and
  download files
- **Attachments** — send images, PDFs, text, source code and config files from device storage
- **Terminal mode** — full-screen terminal with PTY over WebSocket
- **Session management** — search, favorite, categorize, fork, compact, share, export, delete, and
  pin sessions with new activity; global search across servers/projects; pinned-session
  drag-to-reorder; search time filter
- **Model & agent control** — search providers/models, cycle agents, view token usage and context
- **Multi-server** — connect to several OpenCode servers at once, with stable reconnection and
  one-tap switching from the session list
- **AI workbench** — a live dashboard of agent activity across sessions: real-time event stream,
  per-session latest status, and a decision panel with the latest AI reply, pending questions
  (one-tap answers), quick-reply composer with voice input, and jump-into-session
- **Backend mirror & live push (starburst-backend)** — the live event/notification channel and the
  AI workbench route through the backend `/api/opencode/*` mirror when reachable (direct fallback
  otherwise), while chat and session-list REST calls still connect to OpenCode directly (Basic
  auth); records session events and pushes completion / question / permission / error notifications
  with sound, vibration and heads-up banners, even while the session is open
- **Home-screen Widget & App Shortcuts** — session/server/task snapshot with deep links, plus
  long-press shortcuts (new session / global search / task center)
- **SSH tunnel** — optionally connect and restart the OpenCode service over an SSH tunnel, with
  connection health (latency/heartbeat/status) monitoring
- **Custom Slash commands** — define `/name` commands that insert a prompt, persisted and manageable
  from the chat input
- **Adaptive layout** — two-pane (session list + chat) on foldables and tablets, single pane on phones
- **Settings** — card-grouped sections, language, theme, dynamic color, AMOLED dark mode, accent
  color, notifications, haptics, and more
- **Secure updates** — GitHub Release APKs verified by SHA-256, package, version and signing cert

## 📸 Screenshots

**Folded** (phone-style single pane)

<table>
  <tr>
    <td align="center"><b>Home</b></td>
    <td align="center"><b>Session list</b></td>
    <td align="center"><b>Chat</b></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/home_folded.png" width="230" alt="Home (folded)"></td>
    <td><img src="docs/screenshots/sessions_folded.png" width="230" alt="Session list (folded)"></td>
    <td><img src="docs/screenshots/chat_folded.png" width="230" alt="Chat (folded)"></td>
  </tr>
  <tr>
    <td align="center"><b>Chat — session list</b></td>
    <td align="center"><b>Chat (expanded)</b></td>
    <td align="center"><b>Two-pane layout</b></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/sessions_expanded.png" width="230" alt="Session list (foldable expanded)"></td>
    <td><img src="docs/screenshots/chat_expanded.png" width="230" alt="Chat (foldable expanded)"></td>
    <td><img src="docs/screenshots/dual_pane_expanded.png" width="230" alt="Two-pane layout on foldable"></td>
  </tr>
  <tr>
    <td align="center" colspan="3"><b>On-device suggestions</b></td>
  </tr>
  <tr>
    <td colspan="3" align="center">
      <img src="docs/screenshots/suggestions_expanded.png" width="520" alt="On-device next-step suggestions">
    </td>
  </tr>
</table>

## 🚀 Getting Started

### Requirements

- Android 8.0+ (API 26)
- An OpenCode server reachable over your network

### 1. Start your OpenCode server

```bash
opencode serve --port 4096 --hostname 0.0.0.0
```

### 2. Connect in the app

1. Tap **+** on the home screen
2. Enter the server URL (e.g. `http://192.0.2.10:4096`), username and optional password
3. Tap **Connect**

### 3. (Optional) Configure an external LLM for suggestions

By default, "Generate suggestions" runs on a bundled **on-device MNN model** (offline). For better
suggestions you can optionally configure your own cloud LLM:

1. Open **Settings → Suggestion service**
2. Fill in **Base URL** (OpenAI-compatible, e.g. `https://api.openai.com/v1`) — the app appends
   `/chat/completions`, so include any provider-specific prefix such as `/v1` if required
3. Fill in **Model** and **API Key**
4. Tap **Test connection** — this sends a simple single-turn conversation and shows the model's
   real reply, with friendly error messages for auth, 404, timeouts, DNS and malformed URLs — then
   tap **Save**

When configured, suggestions are generated by your cloud provider; if it is unreachable or returns
nothing, the app **silently falls back** to the on-device model. Your API key is encrypted with the
Android Keystore and never leaves the device in plaintext.

## 🔨 Building

### Android Studio

1. Open the project and sync Gradle (uses the bundled CMake/NDK setup)
2. Run on a device or emulator

### Command line

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Install on a connected device
adb install -r app/build/outputs/apk/release/app-release.apk

# Unit tests
./gradlew testDebugUnitTest
```

> The release build links the MNN native libraries, so the first build takes a few minutes. The
> model weights under `app/src/main/assets/models/` are gitignored and never committed, so a build
> from a clean clone works normally and downloads the model (~520 MB) from **ModelScope** on first
> use. Pre-seeding that directory before building instead bundles the weights into the APK.

## 🏗️ Architecture

### Structure

```
app/src/main/
├── cpp/                    # C++ layer
│   ├── starburst_mnn_jni.cpp  # JNI wrapper (load / streaming generate / reset / release)
│   ├── CMakeLists.txt        # Links MNN native libs
│   └── include/              # MNN 3.6.1 headers (version-locked to the .so)
├── jniLibs/                # Prebuilt MNN runtime (libMNN, libllm, …) + libsherpa-mnn-jni.so (ASR)
├── assets/models/          # On-device Qwen3.5-0.8B (MNN) + config + tokenizer
└── kotlin/org/hiylo/starburst/
    ├── data/api/           # OpenCode server API + SuggestionProvider (external LLM)
    ├── data/repository/    # EventReducer, settings, server/session repositories
    ├── data/sync/          # Cross-device sync (gist/webdav) + Keystore-secured secrets
    ├── ml/MnnLlm.kt        # Kotlin wrapper around the on-device model
    ├── ml/MnnAsr.kt        # Kotlin wrapper around the on-device ASR (Zipformer) model
    ├── ml/MnnAsrRecorder.kt# AudioRecord streaming into the ASR recognizer
    ├── service/            # Foreground connection service (SSE + reconnection) + SshRunner
    └── ui/                 # Compose UI: home, chat, sessions, settings, navigation
```

**Suggestion pipeline**

```
User taps "Generate suggestions"
  ├─ External LLM configured?
  │    ├─ YES → POST {baseUrl}/chat/completions   (OpenAI-compatible)
  │    │        ├─ success → show cloud suggestions
  │    │        └─ fail/timeout/empty → fall through
  │    └─ NO/fallback → on-device MNN (Qwen3.5-0.8B, fully offline)
  └─ both fail → localized template suggestions
```

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a PR.

## 🔒 Security

If you find a security issue, please read [SECURITY.md](SECURITY.md) and report it privately.

## 📄 License

This project is licensed under the [MIT License](LICENSE). It includes code derived from
**OC Remote** (MIT-licensed); see the LICENSE file for full attribution.

## 🙏 Acknowledgements

- [OpenCode](https://opencode.ai) — the AI coding agent this client connects to
- [MNN](https://github.com/alibaba/MNN) — on-device inference engine
- [Qwen](https://github.com/QwenLM) — the on-device language model
- [OC Remote](https://github.com/crim50n/oc-remote) — original Android client this project builds upon
