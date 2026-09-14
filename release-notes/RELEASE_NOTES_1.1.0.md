<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# StarBurst v1.1.0 - Release Notes

This release adds server & provider management, workspace file editing, and a much richer
Git experience.

## Highlights

- **Server & provider management** — a server management page (service info, CPU/memory/disk,
  config view/edit, restart) and provider management (add/edit/remove custom providers with
  models), all over a connection-level shared PTY session.
- **Git page** — open the connected project's Git from the chat menu: repo picker, branch/status,
  change list, commit history, and commit/push/pull/checkout/branch operations, with AI-generated
  commit messages.
- **Git deepening** — selective staging, fetch/stash/tag, paginated commit history, and
  unpushed-commit indicator.
- **File editing** — edit and save workspace files in-app.
- **Chat experience** — message actions moved into a per-message menu, and conversation summaries
  (per-message or whole-session) using the cloud LLM with on-device fallback, streamed live.
- **Design alignment** — colored session status badges and semantic status colors.

## Fixes

- Git first-open could misreport "not a repository" (directory/PTY race); now auto-retries.
- Localized provider save/delete toasts.
- Tech debt: cloud summary/commit-message token cap, provider config concurrent-write window,
  and large-file base64 write overflow.

## Version

- `versionName`: `1.1.0`
- `versionCode`: `3`
