# StarBurst 性能基线（Performance Baseline）

> 记录启动耗时与内存基线上的实测数据，作为后续优化与回归比较的参考点。
> 复测命令与口径见 `scripts/measure-startup.sh`。**数据随环境变化，先在本文档登记一次。

## 测量环境（2026-09-22）

| 项 | 值 |
|---|---|
| 构建 | `app-debug.apk`（48,856,369 B），未混淆、未 Baseline Profile |
| AVD | `starburst_test`（x86_64 / Android 14 / API 34，`-no-window -no-snapshot -gpu swiftshader_indirect -memory 4096`） |
| 渲染 | swiftshader（软件渲染，窗口内帧时间会比真机更高属正常） |
| 采集 | `adb shell am start -W` 的 `TotalTime`；5 次冷启动（每次先 `force-stop`）；内存为前台稳定 4s 后 `dumpsys meminfo` |

## 冷启动（TotalTime）

| 统计 | 值 |
|---|---|
| median | **1054 ms** |
| avg | 1048 ms |
| 原始 | 1058 / 1052 / 1021 / 1054 / 1054 ms |

## 内存（前台稳定态）

| 指标 | 值 |
|---|---|
| TOTAL PSS | **50,662 KB**（~49.5 MiB） |
| TOTAL RSS | 177,208 KB（~173 MiB） |

## 口径说明与注意

- 上述为 **debug + 软件渲染模拟器** 数据，供相对回归；release/真机数据会明显更低，需另测。
- 启动耗时含 `MainActivity` 冷启动（Compose 初始化、Hilt、后台 `StarBurstConnectionService` 拉起）；debug 禁用状态会有更多开销。
- 内存基线未含 MNN 端侧模型加载场景（ASR/补全模型按需加载后 RSS 会显著上升，属预期）。

## 复测

```bash
# 连接设备/模拟器后：
APK=app/build/outputs/apk/debug/app-debug.apk bash scripts/measure-startup.sh 5
```

## Baseline Profile（2026-09-22 已引入）

- 依赖 `androidx.profileinstaller:profileinstaller:1.3.1`（安装时应用 AOT 配置）。
- 新增 `:baselineprofile` 模块（`com.android.test` + `androidx.baselineprofile` 1.3.1 + managed device `pixel2Api34`）。
- 生成物 `app/src/main/baseline-prof.txt`（12,423 行，冷启动到主界面热路径）；release 构建 merged ART profile 10,596 → 17,908 行确认已接入。
- 生成：`./gradlew :baselineprofile:connectedNonMinifiedReleaseAndroidTest` 后
  `./gradlew :baselineprofile:collectNonMinifiedReleaseBaselineProfile`，产物在
  `baselineprofile/build/outputs/connected_android_test_additional_output/.../BaselineProfileGenerator_generate-baseline-prof.txt`，复制到 `app/src/main/baseline-prof.txt`。

## 真机基线（2026-09-27，release 包）

> 补上此前缺失的「release + 真机」口径。上文 AVD 数字为 debug + swiftshader 软件渲染，
> 仅供相对回归，不可与本节横向比较。

### 测量环境

| 项 | 值 |
|---|---|
| 构建 | `starburst-release-3.0.0-v2fix75.apk`（28,165,139 B），已签名 release |
| 设备 | 小米 2308CPXD0C（Android 16 / MIUI，USB 调试，序列号 `7923efa2`） |
| 采集 | `scripts/measure-startup.sh 5`（每次先 `force-stop` 再 `am start -W` 取 `TotalTime`）；内存为前台稳定 4s 后 `dumpsys meminfo` |

### 冷启动（TotalTime）

| 统计 | 值 |
|---|---|
| median | **272 ms** |
| avg | 297 ms |
| 原始 | 405 / 250 / 268 / 272 / 288 ms |

与 AVD debug 的 1054 ms 相比快约 3.9 倍——差距主要来自真机硬件与 GPU 硬件加速，
**不能据此推断 release 相对 debug 的收益**（两者构建类型与渲染路径都不同）。

### 内存（前台稳定态）

| 指标 | 值 |
|---|---|
| TOTAL PSS | **52,323 KB**（~51.1 MiB） |
| TOTAL RSS | 172,824 KB（~168.8 MiB） |
| TOTAL SWAP PSS | 240 KB |

PSS 与 AVD 的 50,662 KB 基本持平（+1.6 MiB），符合真机后台服务更多的预期。

### 帧率/卡顿：本次未取到有效样本

`dumpsys gfxinfo` 在程序化滑动下只渲染出 3 帧（`Janky frames: 1 (33.33%)`），
样本量不足且分位数只有一个取值（36ms / 4950ms），**不具统计意义，故不登记**。
有效卡顿基线需在真机上做真实交互（会话列表滚动 + 长会话滚动 + 工具卡片展开），
属真机交互测量项。

## 待办

- [x] release 包基线：签名 release + 真机（2308CPXD0C）补测一次 —— 见上节「真机基线」。
- [ ] 帧率/卡顿基线：需真机真实交互采集（程序化滑动无法驱动本 App 的滚动视图）。