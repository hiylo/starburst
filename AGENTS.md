# AGENTS.md — 项目规约

## 安全红线（提交 / 推送前必须自查）

1. **禁止提交以下内容**（写入 commit 视为事故，需重写历史）：
   - 内网/私有 IP（`10.x`、`172.16-31.x`、`192.168.x`）及内网域名；
   - 明文口令、API Key、token（GitLab PAT、GitHub PAT、OpenAI `sk-`、AWS `AKIA` 等）；
   - 私钥（`*.pem`、`id_rsa`、keystore、`.jks`）、`.env`、连接串内嵌凭据（`user:pass@host`）。
2. **地址占位统一用 RFC 5737 文档网段**：`192.0.2.x` / `198.51.100.x` / `203.0.113.x`
   （例：`http://192.0.2.150:18090`），或 `<host>:<port>` 变量形式；禁止写真实内网 IP。
3. **git remote 禁止在 URL 内嵌 token**（如 `http://user:glpat-xxx@host/...`），改用 SSH 或 git credential helper。
4. **本地提交**：已提供 pre-commit 钩子，新 clone 后执行一次
   `git config core.hooksPath githooks`，之后每次 commit 自动运行 `scripts/check-secrets.sh`。
5. **推送 GitHub 前**：手动跑 `scripts/check-secrets.sh --all` 复查全仓；
   CI 另有 `.github/workflows/secret-scan.yml`（gitleaks）兜底，命中即 CI 失败。
6. 确认为误报需放行时：先整改为合规写法；实在无法避免才 `git commit --no-verify`，并在提交说明注明原因。

## 构建与测试

- Android：Gradle 项目，构建 `./gradlew assembleRelease`，单测 `./gradlew test`。
- 修改字符串资源后至少本地跑相关单测；涉及打包时跑 build。
