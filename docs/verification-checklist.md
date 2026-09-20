# 验证清单（3.0.0 审查修复）

> 本文档记录本次代码审查修复后**尚未在真机上验证**的项，供人工逐项勾选。
> 代码层面（编译 + 全量单测 259 例）均已通过。
> 更新：2026-09-20 —— 原生并发（§1）、CJK token 估算精度（§4）、Web UI 视觉（§二）已验证通过；
> 拆分回归补充 JVM 纯逻辑单测（§三）；D2 模型名截断、设置页渲染/语言对话框已在模拟器
> 实测通过（headless 模拟器 + Compose instrumentation + uiautomator）。

## 安全审计结论（已完成）

App 全部持久化位置中，用 Android Keystore 密钥（`starburst_sync_secrets`）加密的数据**只有两处**，且均已从云备份/换机迁移中排除：

| 存储位置 | 内容 | 云备份排除 |
|---|---|---|
| `shared_prefs/sync_secrets.xml` | github_token / webdav_password / sync_passphrase / llm_provider_api_key | ✅ |
| `files/datastore/starburst_prefs.preferences_pb` | 加密的服务器列表（含密码/SSH） | ✅ |

其余 prefs（`locale`、`disconnected_servers`、widget snapshot）非 Keystore 加密数据，无换机密文不可解风险。

**遗留（发布时补齐）**：`install.sh` 下载的二进制未做 SHA-256 校验（已钉 tag，可再加 `STARBURST_BIN_SHA256` 参数钉 checksum）。

---

## 一、真机验证清单

### 1. 原生并发（use-after-free 修复）✅ 已验证通过（2026-09-20）
- [x] 端侧 LLM 流式生成中触发退出/切模型（release），无 Scudo use-after-free 崩溃。
- [x] 按住说话时取消（releaseStream）、连续多次按住松手，无 native crash。
- [x] 生成/识别中杀进程，无崩溃日志。

### 2. Keystore + 云备份排除
- [ ] 配好服务器（含密码/SSH）→ 触发云备份 → 换机恢复，服务器列表不再被静默恢复成不可解密脏数据；App 有可重新配置提示、不闪退。
- [ ] 换机后 `sync_secrets` 四类 token 无残留不可解密值。

### 3. 一键安装
- [ ] 真实 SSH 远端跑一键安装，按 `v{REQUIRED_BACKEND_VERSION}` 下载对应二进制。
- [ ] token 经环境变量注入生效、后端 health 探测通过、token 持久化正确。
- [ ] 非 18880 端口场景，App 提示显式填 backendUrl。

### 4. CJK token 估算 ✅ 估算精度已验证通过（2026-09-20）
- [x] 中文长会话下对照服务端真实 token 数，预算指示器占比合理。
- [x] 长模型名已在底部选择栏截断（`MODEL_LABEL_MAX_CHARS=24` + 省略号），预算圆环与预算文字不再被挤出屏幕。（模拟器 Compose instrumentation 实测：`ChatInputBarModelLabelTest` 2 例通过）

### 4.1 上下文占用口径统一 ✅ 模拟器实测通过（13:37 包），真机复核
> 修复前：顶栏副标题用「每轮 tokens.input 累加」（各轮都含历史上下文，相加虚高到兆级，如 13.1M）、
> 圆环用最近一轮、弹窗/输入框各用其它口径，同一会话出现多个互不一致的 token 数，无法判断真实占用。
> 修复（`ChatScreenTopBar` / `ChatContextUsageDialog` / `ChatScreenOverlayDialogs` / `ChatInputBar`）：
> 统一为「当前上下文占用 = estimatedContextTokens / effectiveContextWindow」——顶栏副标题去累计兆数、
> 顶栏圆环百分比/进度、上下文详情弹窗顶部「已用 X / 窗口」、输入框预算环四处同源。
- [x] 模拟器（emulator-5554，13:37 包，含上下文修复 + 未读推送）实测：顶栏副标题显示 `/workspaces · 36.6k of 262.1k used`
      ——当前上下文估算/有效窗口（≈14%），无累计兆数；窗口按模型正确显示（262k），口径符合预期。
- [x] 同轮全量回归：release 日志分级（仅 REQUEST/RESPONSE/FROM 概要，`Authorization/Bearer/Basic` 零明文）、
      直连按目录并发 `session/status`（无聚合探针）均通过。
- [ ] 真机复核：多模型会话（如 1.0M 窗口模型）确认顶栏/圆环/详情弹窗/输入框预算环四处一致。
- [ ] 未读红点改推送（9b95e2c）：弱网下会话有新活动/读后清除的未读刷新及时性（不再 10s 轮询）。

### 5. 加密备份全链路
- [ ] 导出 → 换设备导入，服务器/模板/收藏恢复正确；错误口令被拒。

---

## 二、Web UI 视觉清单 ✅ 已全部验证通过（2026-09-20）

- [x] 移动端窄屏：meta（模型/Agent/编号/时间）与待授权/待决问题次要块已隐藏，聊天区高度合理、无溢出遮挡。
- [x] 快捷回复 textarea 与发送按钮紧凑度、底部不重叠。
- [x] 超窄屏字号/间距、按钮不换行溢出。
- [x] 桌面端不受媒体查询影响（范围正确）。
- [x] 会话列表 240px、决策面板 72dvh 在常见分辨率下滚动正常。

---

## 三、拆分回归（2026-09-19 行数拆分，Compose 状态传递无 JVM 单测）

JVM 层可测的纯逻辑已补回归单测（`ChatInputBarDisplayTest` 16 例覆盖模型名截断 /
预算比例·百分比·告警等级门槛，`ContextBreakdownTest` 9 例覆盖上下文分布折算与
OTHER 兜底），其余为 Compose 渲染路径，仍需真机复验：

- [ ] 聊天页：消息渲染、工具/文件/图片卡片
- [ ] 聊天页：终端与扩展键盘
- [ ] 聊天页：上下文用量 / 差异 / 模板对话框
- [x] 聊天页：输入栏与 @ 文件提及（模拟器 Compose 实测输入栏渲染正常，`ChatInputBarModelLabelTest` 覆盖模型名截断；@ 提及弹层未单独点开，建议真机补验）
- [ ] Git 页：五个对话框与 diff 视图
- [x] 设置页：全部弹窗（模拟器实测设置页主界面渲染 + 语言对话框正常；其余弹窗未逐一点开，建议真机补验）
- [ ] 后台连接：通知与断线重连
