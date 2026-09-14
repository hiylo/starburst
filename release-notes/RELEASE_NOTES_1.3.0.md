<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# StarBurst v1.3.0 - Release Notes

This release adds theme schemes, backend-first suggestions, a Task Center backed by
starburst-backend, a home-screen Widget, bookmarks and full-text search, server-side ASR, and a
one-click backend installer — plus a major native-memory fix.

## Highlights

- **Theme schemes** — three full cartoon color schemes (**Candy / Ocean / Sunset**) that recolor the
  whole app (surfaces, containers, secondary/tertiary), plus five extra accent colors in a
  wrap-around palette.
- **Backend-first suggestions** — next-step suggestions now try the backend-configured LLM first,
  then the app's external provider, then the on-device MNN model; the suggestion area shows its
  source (server / cloud / on-device / fallback).
- **Task Center** — background tasks, batch runs and archives via starburst-backend, with AI plan
  breakdown (streaming), scheduling (immediate / delayed / at-time / cron), dependency
  blocking/unblocking, and finished-task purge.
- **Home-screen Widget & App Shortcuts** — session/server/task snapshots with deep links (open
  session / connect server / new session / search / task center), plus long-press shortcuts.
- **Bookmarks & full-text search** — message bookmarks and cross-session full-text search, both
  scoped to the current server (entry lives in the session-list top bar).
- **Backend detection & one-click install** — the server settings page probes `/api/health`; when
  the backend is missing and SSH is configured, one tap installs it over SSH and re-verifies.
- **Server-side ASR** — streaming speech recognition via the backend engine as a fallback when the
  on-device model isn't available.

## Fixes

- **Native memory blow-up (~790MB)** — removed the unconditional MNN model preload on chat open;
  the model now loads on demand (native heap ~790MB → ~21MB).
- Voice-input echo duplication and late correction writes after send.
- Multi-line input height and the fixed-size send button.
- SSE stall self-healing (busy-period REST fallback polling).
- Skills page SKILL.md font sizing.

## Version

- `versionName`: `1.3.0`
- `versionCode`: `5`
