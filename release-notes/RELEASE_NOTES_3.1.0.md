<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# StarBurst v3.1.0 - Release Notes

> Draft — 3.1.0 is under development. Functional scope below is implemented and verified
> (compile + 272 JVM unit tests); APK checksums and the release tag are filled in at release time.

Test Intelligence lands on the APP, together with server monitoring alerts and a
starburst-backend sync channel.

## Highlights

- **Test Intelligence (client)** — a new entry in the session-list top bar (shown when a capable
  backend is connected). Browse intel projects and modules, inspect feature points with their
  involved endpoints, start a single test run and read per-case results, close the loop on issues
  (mark resolved, link to a feature), review and apply fix suggestions (diff preview, write-mode
  file/patch/branch, rollback), and ask the feature-scoped AI for a context-bundled answer. The APP
  is a lightweight client: heavy configuration (creating projects, command allowlists, rule editing)
  stays on the Web UI.
- **Server monitoring alerts** — a card in server management shows live CPU / memory / disk usage,
  thresholds and an enable switch (`GET/POST /api/alerts`). When a metric crosses its threshold the
  backend pushes `alert.hardware` and the app raises a notification (and another on recovery).
- **Multi-device sync over starburst-backend** — a fourth sync storage option mirrors the sync
  payload to the backend's `sync_bundle` KV table (`GET/PUT /api/sync`), for users who already run a
  backend and prefer it over Gist / WebDAV / a file provider.
- **Test Intelligence push** — the app subscribes to `intel.*` events on `/api/ws` and notifies on
  finished runs and security/compliance audit findings.
- **Knowledge base** — a server-management entry opens the KB: collection list/create/delete,
  document list, text/file ingestion and semantic search with snippet results
  (`/api/kb/collections`, `/api/kb/ingest`, `/api/kb/search`).

## Added

- Test Intelligence: project list, per-project feature/test/issue/fix sections, feature AI chat,
  SECURITY_WARNING highlighting.
- `BackendApi`: 23 methods covering `/api/intel/*`, `/api/alerts` and `/api/sync`; `IntelModels`,
  `BackendAlerts`, `BackendSync` DTOs.
- Second `/api/ws` subscription (`intelEventFlow`) with a typed event parser and three notification
  builders (run finished, hardware alert/recovery, audit finding).
- Server management monitoring-alert card (usage + thresholds + enable).
- Backend sync storage option (`BackendSyncTransport`, key `global`).

## Changed

- `BackendGate.REQUIRED_BACKEND_VERSION` split into `MIN_BACKEND_VERSION` (upgrade gate) and
  `INSTALL_BACKEND_VERSION` (installer pin); both **2.1.0** in this release.
- The starburst-backend sync token is now stored in `LocalSyncSecretStore` (Android Keystore
  AES-GCM) instead of DataStore plaintext.

## Fixed

- `update.json` advertised v2.0.0; it now points at the shipped v3.0.0 release (correct in-app
  update detection).
- ROADMAP checkboxes for Material You dynamic color and full-text search (already implemented).

## Security

- Sync backend token encrypted at rest (Android Keystore AES-GCM), consistent with the GitHub token
  and WebDAV password.
- Backend sync accepts only `https://` (or loopback) URLs, matching the WebDAV policy — a plain-HTTP
  backend is rejected because the Bearer token would be exposed.

## Backend pairing

This version requires starburst-backend **>= 2.1.0** (`BackendGate.MIN_BACKEND_VERSION`).

- The Test Intelligence client, the monitoring-alert card and the sync channel target the
  `/api/intel/*`, `/api/alerts` and `/api/sync` endpoints shipped by the backend 2.x line.
- The one-click installer pins the `v2.1.0` tag (`BackendGate.INSTALL_BACKEND_VERSION`).

```bash
curl -fsSL https://raw.githubusercontent.com/hiylo/starburst-backend/v2.1.0/scripts/install.sh \
  | sudo env STARBURST_DEFAULT_TOKEN=<your-token> bash -- --port 18880 --version 2.1.0
```

## Install and verify

```bash
# Fill in at release time:
sha256sum app-release-v3.1.0.apk
adb install -r app-release-v3.1.0.apk
```
