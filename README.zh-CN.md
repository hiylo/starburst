<!--
Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
-->

<div align="center">

# OpenCode（安卓版）

**面向 [OpenCode](https://github.com/anomalyco/opencode) AI 编程代理的原生安卓客户端**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-26-brightgreen)]()
[![Platform](https://img.shields.io/badge/Platform-Android-blue)]()

**[English](README.md) · 简体中文**

</div>

OpenCode（安卓版）是一款功能丰富的原生 Material 3 客户端，让你在手机或平板上操控自己的
[OpenCode](https://opencode.ai) AI 编程代理。通过网络连接任意 OpenCode 服务器，与代理聊天、
浏览工作区文件、运行完整终端、管理会话——一切都在移动端优先的界面中完成。

> 这是一个**独立维护的社区项目**，与 OpenCode 官方团队无关。

---

## ✨ 功能特性

- **原生聊天** — Material 3 界面，支持 GFM Markdown、代码块、语法高亮和复制操作
- **实时流式输出** — 消息实时流式传输，自动滚动
- **端侧建议** — 通过内置 MNN 模型（Qwen3.5-0.8B）**完全离线**生成下一步建议，可选配**云端 LLM 服务**以获得更高质量。模型随 APK 打包并在首次使用时自动解压；未打包权重的构建会改为从 **ModelScope**（国内访问快）逐个文件下载，并对每个文件做 SHA-256 校验。配置后端后，建议优先走后端 LLM，其次外部服务，最后端侧模型，并显示来源标签
- **端侧语音输入** — 通过端侧 **MNN sherpa-mnn 流式 Zipformer（中英双语）** 模型实现**按住说话**的离线语音识别，模型从 ModelScope 下载并逐文件 SHA-256 校验。按住说话、松手上屏、上滑取消，带实时音量波形；模型在设置中下载后才会显示麦克风按钮
- **外部 LLM 服务** — 自带 OpenAI 兼容接口（`/v1/chat/completions`）用于建议生成，自动静默降级到端侧模型；**测试连接**会真正发送一轮简单对话并回显模型回复
- **工作区文件** — 浏览项目目录，预览高亮文本、Markdown 和图片，并可下载文件
- **附件** — 从设备存储发送图片、PDF、文本、源码和配置文件
- **终端模式** — 通过 WebSocket 提供 PTY 的全屏终端
- **会话管理** — 搜索、收藏、分类、分叉、压缩、分享、导出、删除，以及置顶有新动态的会话；跨服务器/跨项目的全局搜索；置顶会话拖拽排序；搜索时间过滤
- **模型与代理控制** — 搜索提供方/模型、切换代理、查看 token 用量与上下文
- **多服务器** — 同时连接多个 OpenCode 服务器，支持稳定重连与会话列表一键切换
- **桌面小部件与快捷方式** — 会话/服务器/任务快照，点击直达；长按快捷方式（新建会话/全局搜索/任务中心）
- **SSH 隧道** — 可选通过 SSH 隧道连接并远程重启 opencode 服务，服务器管理页提供连接健康监控（延迟/心跳/状态）
- **自定义 Slash 命令** — 定义 `/名称` 命令插入提示词，可持久化并在输入框管理
- **自适应布局** — 折叠屏和平板双栏布局（会话列表 + 聊天），手机上单栏
- **会话链接** — 指向已连接服务器的链接在应用内 WebView 打开（带 Basic Auth），其余链接保持系统浏览器打开，避免凭证泄漏
- **Git** — 内置 Git 页面（项目为 git 仓库时从对话页 ⋮ 菜单进入），支持分支/状态、变更列表与 diff、提交历史，以及 commit/push/pull/切换分支/新建分支等操作；commit message 可 AI 生成（云端 LLM，回退端侧模型）
- **服务器与服务商管理** — 服务信息（版本/活跃会话）、CPU/内存/磁盘、配置查看与修改、重启，以及自定义服务商管理（添加/编辑/删除服务商与模型）
- **文件编辑** — 应用内编辑并保存工作区文件
- **对话总结** — 消息级或整个会话的总结（云端 LLM，回退端侧模型），流式展示
- **自定义强调色与主题方案** — 六种品牌强调色，外加三套完整卡通配色方案（糖果/海洋/日落）整体换肤，全局生效（亮/暗/AMOLED）并支持设置同步
- **书签与全文搜索** — 收藏关键消息，并按当前服务器跨会话全文搜索（均绑定当前服务器）
- **任务中心（opencode-backend）** — 后台任务、批量与归档，支持 AI 规划拆解、调度与依赖阻塞；自动探测后端，缺失时可通过 SSH 一键安装
- **设置** — 卡片分组式界面，提供语言、主题、动态取色、AMOLED 深色模式、强调色、通知、触感等
- **安全更新** — 应用内下载 GitHub Release APK，通过 SHA-256、包名、版本和签名证书校验

## 📸 截图

**收起态**（手机风格单栏）

<table>
  <tr>
    <td align="center"><b>首页</b></td>
    <td align="center"><b>会话列表</b></td>
    <td align="center"><b>聊天</b></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/home_folded.png" width="230" alt="首页（收起态）"></td>
    <td><img src="docs/screenshots/sessions_folded.png" width="230" alt="会话列表（收起态）"></td>
    <td><img src="docs/screenshots/chat_folded.png" width="230" alt="聊天（收起态）"></td>
  </tr>
  <tr>
    <td align="center"><b>会话列表 — 展开态</b></td>
    <td align="center"><b>聊天 — 展开态</b></td>
    <td align="center"><b>双栏布局</b></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/sessions_expanded.png" width="230" alt="会话列表（展开态）"></td>
    <td><img src="docs/screenshots/chat_expanded.png" width="230" alt="聊天（展开态）"></td>
    <td><img src="docs/screenshots/dual_pane_expanded.png" width="230" alt="折叠屏双栏布局"></td>
  </tr>
  <tr>
    <td align="center" colspan="3"><b>端侧建议</b></td>
  </tr>
  <tr>
    <td colspan="3" align="center">
      <img src="docs/screenshots/suggestions_expanded.png" width="520" alt="端侧下一步建议">
    </td>
  </tr>
</table>

## 🚀 快速开始

### 环境要求

- Android 8.0 及以上（API 26）
- 一个可通过网络访问的 OpenCode 服务器

### 1. 启动你的 OpenCode 服务器

```bash
opencode serve --port 4096 --hostname 0.0.0.0
```

### 2. 在应用中连接

1. 在首页点击 **+**
2. 输入服务器地址（例如 `http://192.168.0.10:4096`）、用户名和可选密码
3. 点击 **连接**

### 3.（可选）为建议配置外部 LLM

默认情况下，"生成建议"使用内置的**端侧 MNN 模型**（离线）。如需更高质量的建议，可以可选地配置自己的云端 LLM：

1. 打开 **设置 → 建议生成服务**
2. 填写 **Base URL**（OpenAI 兼容，例如 `https://api.openai.com/v1`）——应用会在末尾拼接 `/chat/completions`，如需 `/v1` 等提供方前缀请自行带上
3. 填写 **模型** 和 **API Key**
4. 点击 **测试连接** —— 会发送一轮简单的单次对话并回显模型真实回复，认证失败、404、超时、域名解析错误、URL 格式错误等都会给出友好提示 —— 然后点击 **保存**

配置后建议由你的云端服务商生成；如果服务不可达或返回空内容，应用会**静默降级**到端侧模型。你的 API Key 使用 Android Keystore 加密存储，绝不会以明文离开设备。

## 🔨 构建

### Android Studio

1. 打开项目并同步 Gradle（使用内置的 CMake/NDK 配置）
2. 在设备或模拟器上运行

### 命令行

```bash
# 调试 APK
./gradlew assembleDebug

# 发布 APK
./gradlew assembleRelease

# 安装到已连接设备
adb install -r app/build/outputs/apk/release/app-release.apk

# 单元测试
./gradlew testDebugUnitTest
```

> 发布构建包含端侧模型（约 520 MB）和 MNN 原生库，因此首次构建需要几分钟。`app/src/main/assets/models/` 下的模型权重已在 `.gitignore` 中：未打包权重时构建仍可正常使用，首次使用时改为从 **ModelScope** 下载模型。

## 🏗️ 架构

### 结构

```
app/src/main/
├── cpp/                    # C++ 层
│   ├── opencode_mnn_jni.cpp  # JNI 封装（加载 / 流式生成 / 重置 / 释放）
│   ├── CMakeLists.txt        # 链接 MNN 原生库
│   └── include/              # MNN 3.6.1 头文件（与 .so 版本锁定）
├── jniLibs/                # 预编译 MNN 运行时（libMNN、libllm 等）
├── assets/models/          # 端侧 Qwen3.5-0.8B（MNN）+ 配置 + 分词器
└── kotlin/org/hiylo/opencode/
    ├── data/api/           # OpenCode 服务器 API + SuggestionProvider（外部 LLM）
    ├── data/repository/    # EventReducer、设置、服务器/会话仓库
    ├── data/sync/          # 跨设备同步（gist/webdav）+ Keystore 加密密钥
    ├── ml/MnnLlm.kt        # 端侧模型的 Kotlin 封装
    ├── service/            # 前台连接服务（SSE + 重连）
    └── ui/                 # Compose UI：首页、聊天、会话、设置、导航
```

**建议生成链路**

```
用户点击"生成建议"
  ├─ 已配置外部 LLM？
  │    ├─ 是 → POST {baseUrl}/chat/completions   （OpenAI 兼容）
  │    │        ├─ 成功 → 展示云端建议
  │    │        └─ 失败/超时/为空 → 继续向下
  │    └─ 否/降级 → 端侧 MNN（Qwen3.5-0.8B，完全离线）
  └─ 两者都失败 → 本地化模板建议
```

## 🤝 参与贡献

欢迎贡献！提交 PR 前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 🔒 安全

如果发现安全问题，请阅读 [SECURITY.md](SECURITY.md) 并私下报告。

## 📄 许可证

本项目采用 [MIT 许可证](LICENSE)。它包含衍生自 **OC Remote**（MIT 许可）的代码；
完整的署名信息参见 LICENSE 文件。

## 🙏 致谢

- [OpenCode](https://opencode.ai) — 本客户端所连接的 AI 编程代理
- [MNN](https://github.com/alibaba/MNN) — 端侧推理引擎
- [Qwen](https://github.com/QwenLM) — 端侧语言模型
- [OC Remote](https://github.com/crim50n/oc-remote) — 本项目所基于的原版安卓客户端
