# 验证清单（3.0.0 审查修复）

> 本文档记录本次代码审查修复后**尚未在真机上验证**的项，供人工逐项勾选。
> 代码层面（编译 + 全量单测 221 例 + `go build ./...` + `bash -n`）均已通过。

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

### 1. 原生并发（use-after-free 修复）
- [ ] 端侧 LLM 流式生成中触发退出/切模型（release），无 Scudo use-after-free 崩溃。
- [ ] 按住说话时取消（releaseStream）、连续多次按住松手，无 native crash。
- [ ] 生成/识别中杀进程，无崩溃日志。

### 2. Keystore + 云备份排除
- [ ] 配好服务器（含密码/SSH）→ 触发云备份 → 换机恢复，服务器列表不再被静默恢复成不可解密脏数据；App 有可重新配置提示、不闪退。
- [ ] 换机后 `sync_secrets` 四类 token 无残留不可解密值。

### 3. 一键安装
- [ ] 真实 SSH 远端跑一键安装，按 `v{REQUIRED_BACKEND_VERSION}` 下载对应二进制。
- [ ] token 经环境变量注入生效、后端 health 探测通过、token 持久化正确。
- [ ] 非 18880 端口场景，App 提示显式填 backendUrl。

### 4. CJK token 估算
- [ ] 中文长会话下对照服务端真实 token 数，预算指示器占比合理。

### 5. 加密备份全链路
- [ ] 导出 → 换设备导入，服务器/模板/收藏恢复正确；错误口令被拒。

---

## 二、Web UI 视觉清单

- [ ] 移动端窄屏：meta（模型/Agent/编号/时间）与待授权/待决问题次要块已隐藏，聊天区高度合理、无溢出遮挡。
- [ ] 快捷回复 textarea 与发送按钮紧凑度、底部不重叠。
- [ ] 超窄屏字号/间距、按钮不换行溢出。
- [ ] 桌面端不受媒体查询影响（范围正确）。
- [ ] 会话列表 240px、决策面板 72dvh 在常见分辨率下滚动正常。
