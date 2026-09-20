<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# StarBurst v3.0.0 - Release Notes

Released 2026-09-20. The **Test Intelligence** roadmap begins, plus major client increments:
project overview, battery optimization, lifecycle-aware polling, E2E test suite, and a refined
Flame theme.

## Highlights

- **Project Overview** — a new entry in the chat top-bar ⋮ menu. Scans the session's project
  directory and shows file/line statistics by type (code/comment/blank), the largest files, and
  Git repository info (branch, commit count, last commit, modified files and diffstat). Shell-side
  scanning uses `find | xargs wc -c` + an awk line-classifier with C-style and `#`-style comment
  detection.
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
- Largest Kotlin sources split by responsibility; no file exceeds the 1500-line hard cap.
- CI runs JVM unit tests on every push/PR (`./gradlew test` on JDK 17) + gitleaks + file-size guard.
- Session loading parallelized: child-session BFS, git state, and initial diff run concurrently.
- Unread status is now push-driven (removed 10s polling fallback).

## Added

- Project Overview (file/line stats + Git repository info) in the chat ⋮ menu.
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

## Fixed

- Long model names no longer push the budget ring off-screen (truncated at 24 chars).
- Hilt 2.51→2.52: fixed debug crash (`StarBurstApp_GeneratedInjector` NoClassDefFoundError) and
  release R8 missing class.
- Server connection hang on "connecting" — blocking work moved outside the connection lock.
- Connection state desync — flags now reconcile atomically.
- MnnLlm/MnnAsr native access serialized under one lock (use-after-free guard).
- ASR recorder stops on ViewModel clear (no leaked microphone).

## Engineering

- Unit tests (259 cases) + Compose instrumentation (2 cases) + Maestro E2E (5 flows) all green.
- File-size CI guard (`scripts/check-file-size.sh`, 1500-line hard cap).
- Single-file governance: largest sources split into same-package extension files.
