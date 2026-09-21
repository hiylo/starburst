<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# StarBurst v3.0.0 - Release Notes

Released 2026-09-21. The **Test Intelligence** roadmap begins, plus major client increments:
project overview, battery optimization, lifecycle-aware polling, E2E test suite, a rounded Cartoon
style, and a refined Flame theme.

## Highlights

- **Project Overview** — a new entry in the chat top-bar ⋮ menu. Scans the session's project
  directory and shows file/line statistics by type (code/comment/blank), the largest files, and
  Git repository info (branch, commit count, last commit, modified files and diffstat). Shell-side
  scanning uses `find | xargs wc -c` + an awk line-classifier with C-style and `#`-style comment
  detection.
- **Cartoon style: rounded display font + icon treatments** — headings and labels switch to a
  bundled Baloo 2 family (SIL OFL, 3 weights, Latin-only, ~268KB) and icons gain two wrappers
  (`CartoonInkIcon` inked glyph, `CartoonStickerIcon` tilted hard-shadowed chip);
  `body*` text, code and Chinese prose stay on the system font / monospace. OFL notice ships at
  `assets/licenses/Baloo2-OFL.txt`.
- **Cartoon style: `MaterialTheme.shapes` ramp** — the whole shape scale is swapped while Cartoon
  style is on, so M3 components that never took an adaptive shape (cards, snackbars, outlined text
  fields, chips, FABs, dropdown menus) round out too.
- **Session list: batch compact** — a Compress action in the multi-select top bar summarizes every
  selected session at once (each using its own model, falling back to the server default) to reduce
  context, mirroring the single-session Compact menu item.
- **Battery optimization** — WakeLock now held only when a session is actively running (busy/retry),
  not during idle background; WS ping interval 20s→60s; release builds no longer persist INFO logs
  to SQLite; `backgroundWakeLock` default changed to false.
- **Lifecycle-aware polling** — all background polling (chat status, log tail, server management,
  session list, workbench) now suspends when the app is in the background via
  `launchWhileStarted` (repeatOnLifecycle), resuming on return.
- **Top bar token usage** — the chat top-bar subtitle now shows cumulative session token usage
  (total input + output), distinct from the input bar's budget ring (current context fill).
- **Flame theme** — retuned to a vivid passionate red: primary `#FF1744` (Red A400),
  secondary `#FF6D00` (Orange A700), tertiary `#D50000` (Red A700).
- **E2E test suite** — 5 Maestro flows (smoke, add-server-connect, session-list, settings,
  workbench) with a batch runner (`scripts/run-e2e.sh`) and `.secrets-allowlist`.
- **Model name truncation** — long model names no longer push the budget ring off-screen;
  truncated at 24 chars with surrogate-pair-safe ellipsis.

## Changed

- Top bar subtitle shows cumulative token usage (total input + output) and cost, distinct from the
  input bar budget ring (current context occupancy / effective window).
- Flame theme palette retuned for a more vivid red.
- Settings → Appearance → Cartoon style description mentions the rounded heading font and the
  ink-outlined icons (both locales).
- Largest Kotlin sources split by responsibility; no file exceeds the 1500-line hard cap.
- CI runs JVM unit tests on every push/PR (`./gradlew test` on JDK 17) + gitleaks + file-size guard.
- Session loading parallelized: child-session BFS, git state, and initial diff run concurrently.
- Unread status is now push-driven (removed 10s polling fallback).

## Added

- Project Overview (file/line stats + Git repository info) in the chat ⋮ menu.
- Cartoon style rounded display font (Baloo 2, OFL), inked/sticker icon wrappers, and a
  `MaterialTheme.shapes` ramp.
- Session list multi-select Compress (batch compact).
- Prompt & session templates, session timeline, custom system prompt + context budget.
- File diff viewer, in-editor AI actions, on-device code assist (MNN).
- Service log live tail, bookmark tags & groups, encrypted backup & restore.
- Token usage card, notification quick actions (RemoteInput).
- E2E test infrastructure (5 Maestro flows + batch runner).
- Lifecycle-aware polling utility (`launchWhileStarted`).

## Security

- SSH host-key verification (known_hosts TOFU).
- Server config encrypted at rest (Android Keystore AES-GCM) with legacy plaintext migration.
- Passwords removed from Intents/PendingIntents.
- WebView hardening (no mixed content, external links in browser, HTTP-auth host check).
- Install script pinned to a release tag with a randomized backend token.
- **Required backend bumped to 2.0.1** — one-click install now pulls `install.sh` from the `v2.0.1`
  tag, which verifies the downloaded binary against the release's `SHA256SUMS` (mismatch aborts and
  deletes the partial file). v2.0.0 also fixes the artifact-name break that made installs from
  v1.0.0/v1.1.0 404 (`startburst-backend-*` → `starburst-backend-*`) and restores the missing
  `windows-amd64.exe` + `SHA256SUMS` assets.

## Fixed

- Long model names no longer push the budget ring off-screen (truncated at 24 chars).
- Hilt 2.51→2.52: fixed debug crash (`StarBurstApp_GeneratedInjector` NoClassDefFoundError) and
  release R8 missing class.
- Server connection hang on "connecting" — blocking work moved outside the connection lock.
- Connection state desync — flags now reconcile atomically.
- MnnLlm/MnnAsr native access serialized under one lock (use-after-free guard).
- ASR recorder stops on ViewModel clear (no leaked microphone).
- Parent sessions showed as idle while their sub-agent children were still running (session list
  and workbench). Child-session push events are no longer dropped — only the "reply ready"
  notification is suppressed, matching the official Web UI / TUI — the list aggregates child
  busy/retry onto the parent, and a monotonic guard stops a stale lagging `idle` push from
  overwriting the aggregated state.

## Engineering

- Unit tests (261 cases) + Compose instrumentation (2 cases) + Maestro E2E (5 flows) all green.
- File-size CI guard (`scripts/check-file-size.sh`, 1500-line hard cap).
- Single-file governance: largest sources split into same-package extension files.

## Install and verify

```bash
# Verify the APK
sha256sum app-release-v3.0.0.apk
# Expected: 9c99e4ecca34c2eb1f11647f21d6bb62513d562dae6a1959ee0a4b0e08b145e9

# Install (overwrite an existing installation)
adb install -r app-release-v3.0.0.apk
```

## Backend pairing

This version requires starburst-backend **>= 2.0.1** (`BackendGate.REQUIRED_BACKEND_VERSION`).

- Backend **v2.0.0**: Test Intelligence becomes the third main capability; the artifact name is
  corrected from `startburst-backend-*` to `starburst-backend-*` -- the already-published assets of
  v1.0.0 / v1.1.0 still use the old name, so `install.sh` returns 404 for them.
- Backend **v2.0.1**: `install.sh` verifies the download against SHA-256 (mismatch deletes the
  artifact and exits).

The app's one-click install pulls `install.sh` from the `v2.0.1` tag, so verification is applied
automatically.

```bash
curl -fsSL https://raw.githubusercontent.com/hiylo/starburst-backend/v2.0.1/scripts/install.sh \
  | sudo env STARBURST_DEFAULT_TOKEN=<your-token> bash -- --port 18880 --version 2.0.1
```
