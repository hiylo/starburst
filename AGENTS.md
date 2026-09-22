<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

# AGENTS.md — opencode（StarBurst Android）项目规约

## 项目概述

StarBurst —— OpenCode AI 编程助手的原生 Android 客户端（Material 3 + Jetpack Compose）。连接任意 OpenCode 服务器进行流式聊天、文件浏览/编辑、内置 Git 页、PTY 终端、会话管理、端侧 LLM 建议、端侧语音识别、测试智能工作台。

- **namespace / applicationId**：`org.hiylo.starburst`（debug 后缀 `.debug`）
- **versionCode / versionName**：8 / 3.0.0
- **minSdk / targetSdk / compileSdk**：26 / 34 / 34

## 技术栈

| 层 | 选型 |
|---|---|
| 构建 | Gradle 8.6 + AGP 8.4.0 + Kotlin 2.0.21（含 compose/serialization 插件） |
| JDK | **JDK 17**（硬约束，Gradle 8.6 在 JDK 25 上直接失败） |
| UI | Jetpack Compose BOM 2024.12.01、Material3、navigation-compose、Coil、Accompanist |
| DI | Hilt 2.52（kapt） |
| 网络 | Ktor 2.3.11（OkHttp + WebSockets + SSE 流式） |
| 端侧 ML | MNN（libMNN.so + OpenCV + Vulkan）+ Qwen3.5-0.8B 端侧 LLM |
| 端侧语音 | sherpa-mnn 流式 Zipformer 双语 |
| 测试 | JUnit 4.13.2 + Espresso 3.6.1 + Compose ui-test-junit4；E2E 用 **Maestro** |

`gradle.properties` 已含工作区硬性要求：`org.gradle.daemon=false`、`kotlin.compiler.execution.strategy=in-process`。

## 架构 / 包结构

模块：`:app` + `:baselineprofile`。源码 313 个 .kt，其中 ui 145 个。

```
app/src/main/kotlin/org/hiylo/starburst/
├── MainActivity.kt / StarBurstApp.kt
├── ui/        (145)  screens/ components/ navigation/ theme/
├── data/      (59)   api/ backend/ repository/ search/ shell/ sync/ update/
├── domain/    (15)
├── service/   (7)    StarBurstConnectionService 等
├── ml/        (6)
├── widget/    (4)
├── di/        (2)
└── logging/   (1)
```

> 本项目为 Android MVVM + 手写 repository 风格，全局 Java 分层规范（controller/service/entity/dto/vo）在此项目无作用对象。

## 安全红线（提交 / 推送前必须自查）

1. **禁止提交以下内容**（写入 commit 视为事故，需重写历史）：
   - 内网/私有 IP（`10.x`、`172.16-31.x`、`192.168.x`）及内网域名
   - 明文口令、API Key、token（GitLab PAT、GitHub PAT、OpenAI `sk-`、AWS `AKIA` 等）
   - 私钥（`*.pem`、`id_rsa`、keystore、`.jks`）、`.env`、连接串内嵌凭据（`user:pass@host`）
2. **地址占位统一用 RFC 5737 文档网段**：`192.0.2.x` / `198.51.100.x` / `203.0.113.x`（例：`http://192.0.2.150:18090`），或 `<host>:<port>` 变量形式
3. **git remote 禁止在 URL 内嵌 token**，改用 SSH 或 git credential helper
4. **本地提交**：已提供 pre-commit 钩子，新 clone 后执行一次 `git config core.hooksPath githooks`，之后每次 commit 自动运行 `scripts/check-secrets.sh`
5. **推送 GitHub 前**：手动跑 `scripts/check-secrets.sh --all` 复查全仓；CI 另有 `.github/workflows/secret-scan.yml`（gitleaks）兜底
6. 确认为误报需放行时：先整改为合规写法；实在无法避免才 `git commit --no-verify`，并在提交说明注明原因

## 构建与测试

```bash
# 构建
./gradlew assembleDebug                     # 调试
./gradlew assembleRelease                   # 发布（需 app/keystore/signing.properties）

# 测试
./gradlew test                              # JVM 单测
./gradlew test --no-daemon --stacktrace     # CI 写法（JDK 必须 17）

# E2E（Maestro）
./scripts/run-e2e.sh                        # 跑 e2e/smoke.yml 等

# 行数门禁
./scripts/check-file-size.sh                # 目标 1000 / 硬上限 1500

# 性能基线
./scripts/measure-startup.sh
```

### E2E 规约
- 专用 AVD `ocb_e2e`，端口 5566，`JAVA_HOME_JDK17=/usr/lib/jvm/dragonwell-17`
- 产物落 `/opt/test-artifacts/opencode/e2e/<时间戳>/`
- Maestro 日志 `tee` 到 `/opt/test-artifacts/opencode/logs/maestro/`
- **模拟器每次开新实例、用完即关**（`pkill -f qemu-system-x86_64`）

## Git 规范

- 提交消息：`{type}({scope}): {subject}`，type：`feat`/`fix`/`refactor`/`docs`/`chore`/`test`
- 远端：`github`（SSH）、`gitlab`（内网，URL 中不含 token）

## CI

- `.github/workflows/ci.yml`：unit-tests（JDK 17 + 行数门禁 + `./gradlew test`）
- `.github/workflows/release.yml`：从 `build.gradle.kts` 抽版本、校验 `update.json`、GitHub Secrets keystore 签名
- `.github/workflows/secret-scan.yml`：gitleaks

## 文档索引

| 文件 | 说明 |
|------|------|
| `docs/code-audit-3.1.0.md` | 3.1.0 代码审计报告 |
| `docs/performance-baseline.md` | 冷启动/内存基线 |
| `docs/release-readiness-audit-3.1.0.md` | 上线就绪度审计 |
| `docs/verification-checklist.md` | 3.0.0 审查修复验证清单 |
| `docs/screenshots/` | UI 截图 |
| `CHANGELOG.md` | 变更记录 |
| `KNOWN_ISSUES.md` | 已知问题 |
| `SECURITY.md` | 安全说明 |
| `ROADMAP.md` | 路线图 |
| `DESIGN.md` / `DESIGN.zh-CN.md` | 设计规范 |
