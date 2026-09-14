<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# StarBurst v1.0.1 - Release Notes

This release adds a built-in Git experience and polishes the visual design.

## Highlights

- **Git page** — open the connected project's Git from the chat ⋮ menu (shown when the project
  is a repository). View the current branch and workspace status, the change list with per-file
  diffs (including untracked files), and commit history with per-commit file changes and
  per-file diffs.
- **Git operations** — commit, push/pull (with remote picker for multiple remotes), switch
  branch, and create a branch — all through a persistent terminal session for snappy responses.
- **AI commit messages** — generate a commit message from the recent commit style using the
  configured cloud LLM, falling back to the on-device model.
- **ModelScope model downloads** — the on-device model now downloads from ModelScope (fast in
  mainland China) with per-file SHA-256 verification; it is auto-extracted from the APK assets
  when bundled.
- **Design alignment** — colored session status badges, semantic status colors, and card
  rounding/AMOLED border consistency.

## Fixes

- Bundled on-device model was never used at runtime (always prompted a download).
- Git data misreported "not a repository", and PTY commands could be lost or time out.

## Version

- `versionName`: `1.0.1`
- `versionCode`: `2`
