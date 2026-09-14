<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# StarBurst v1.2.0 - Release Notes

This release adds on-device voice input, global search, a much richer session list, and SSH
tunnelling, together with a batch of session-list and archive fixes.

## Highlights

- **On-device voice input** — an on-device **MNN sherpa-mnn streaming Zipformer (bilingual zh/en,
  int8)** model downloaded from ModelScope with per-file SHA-256 verification. Hold-to-talk with
  release-to-fill, slide-up-to-cancel, and a live volume waveform; the mic appears after the model
  is downloaded in Settings.
- **Global search** — cross-server, cross-project aggregation of root sessions, searchable by
  title/directory/project/branch and filterable by time.
- **Session list** — one-tap multi-server switching, pinned-session drag-to-reorder, search time
  filter, and a compact layout that reuses the chat compact-mode toggle.
- **SSH tunnel & health monitoring** — optional SSH tunnel (JSch local port-forwarding) to connect
  and restart the opencode service remotely, plus connection health (latency / heartbeat / status).
- **Chat** — custom Slash commands, quote-reply, and session export (Markdown / JSON).

## Fixes

- Status flicker / stuck conversation: status polling no longer clobbers a live `busy` state; idle
  reconciliation only happens when disconnected.
- Foldable two-pane back navigation now pops back to the parent session.
- Session list no longer misses sessions from other projects (cross-project root-session endpoint +
  authoritative snapshot).
- Archiving/unarchiving now takes effect via the nested `time.archived` field.
- Opening/creating a session no longer wipes the other sessions from the list.
- Voice model download entry (and the chat mic) now appears; fixed the bundled ASR JNI's MNN
  dependency.

## Version

- `versionName`: `1.2.0`
- `versionCode`: `4`
