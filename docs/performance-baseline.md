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

## 待办

- [ ] release 包基线：签名 release + 真机（现有真机 2308CPXD0C）补测一次，对比 baseline profile 收益。