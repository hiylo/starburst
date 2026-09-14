<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# StarBurst v1.4.0 - Release Notes

This release adds server management screens (automation rules / API tokens / audit log), a
full AGENTS.md workflow, fork-branch navigation, quick prompt templates, and continues the
project rename to StarBurst.

## Highlights

- **Server management** — three new screens under server settings:
  - **Automation rules** — scheduled / git / webhook triggered agent tasks with
    natural-language AI generation (`/api/rules/generate`) and execution history.
  - **API tokens** — create / revoke backend access tokens (plaintext shown once).
  - **Audit log** — recent authenticated API actions.
- **AGENTS.md workflow** — from the chat menu, detect a project's `AGENTS.md`, then **generate**
  (no file), **improve** (AI enhancement), **modify by instruction** (natural-language), or
  **edit manually**, and save it back to the project root. Generation shows an animated status
  indicator, and failures offer a "Continue" retry.
- **Continue on failure** — when the agent aborts or a retry fails, the error message now offers a
  "Continue" action that resumes the session.
- **Fork branch navigation** — a branch bar lets you jump between a session and its forked child
  sessions, and back to the parent.
- **Re-copy share link** — already-shared sessions can copy the share URL again from the menu.
- **Quick prompt templates** — one-tap presets (code review / generate tests / explain code / fix
  bug) fill the composer, handy on mobile.

## Changed

- **Renamed to StarBurst** — application id `org.hiylo.starburst`, app label **StarBurst**, new
  signing keystore.
- **Server-side ASR preferred** — voice input now prefers the backend streaming engine and falls
  back to the on-device MNN model.
- **"Suggestion service" → "LLM service"** — the provider setting reflects its broader role.
- **Source-code split** — ChatScreen / NavGraph / OpenCodeApi broken into per-concern files.

## Fixes

- AGENTS.md empty-file detection (the server returns 200 + empty content for missing files).
- AGENTS.md editor layout pushing the save button off-screen.
- Stats / audit / rules / tokens endpoints now accept the app token.

## Version

- `versionName`: `1.4.0`
- `versionCode`: `6`