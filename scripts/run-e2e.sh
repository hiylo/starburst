#!/usr/bin/env bash
# ============================================================
# OpenCode (StarBurst) Android E2E 测试脚本（Maestro）
#   - 使用专属 AVD ocb_e2e + 专属端口，避免与其他会话冲突
#   - 用 JDK 17 构建 debug APK（默认 JDK 25 会导致 Gradle 8.6 失败）
#   - 批量运行 e2e/*.yml（排除 subflows/），输出 JUnit 报告
#   - 产物统一落 /opt/test-artifacts/opencode/（测试产物路径规约，勿写项目 build/）
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
JAVA_HOME_JDK17="/usr/lib/jvm/dragonwell-17"
AVD_NAME="ocb_e2e"
PORT="${E2E_PORT:-5566}"
SERIAL="emulator-$PORT"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
FLOWS=(
  "$ROOT/e2e/smoke.yml"
  "$ROOT/e2e/add-server-connect.yml"
  "$ROOT/e2e/session-list.yml"
  "$ROOT/e2e/settings.yml"
  "$ROOT/e2e/workbench.yml"
)
# 产物按测试产物路径规约落 /opt/test-artifacts/<项目名>/，禁止写项目 build/ 或 Home。
REPORT_DIR="/opt/test-artifacts/opencode/e2e/$(date +%Y%m%d-%H%M%S)"
MAESTRO_LOG="/opt/test-artifacts/opencode/logs/maestro/$(date +%Y%m%d).log"

export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$HOME/.maestro/bin"

log() { echo -e "[e2e] $*"; }

# 1) 构建 debug APK（JDK 17）
if [ -d "$JAVA_HOME_JDK17" ]; then
  export JAVA_HOME="$JAVA_HOME_JDK17"
else
  log "WARN: 未找到 $JAVA_HOME_JDK17，使用默认 JDK（可能构建失败）"
fi
log "构建 debug APK ..."
(cd "$ROOT" && ./gradlew :app:assembleDebug --no-daemon -q)
[ -f "$APK" ] || { echo "[e2e] APK 未生成"; exit 1; }
log "APK: $(stat -c '%y' "$APK")"

# 2) 确保专属模拟器在线
if ! adb get-state >/dev/null 2>&1 || [ -z "$(adb devices | grep "$SERIAL")" ]; then
  log "启动专属模拟器 $AVD_NAME @ $SERIAL ..."
  "$ANDROID_HOME/emulator/emulator" -avd "$AVD_NAME" -no-window -no-audio \
    -no-snapshot -no-boot-anim -gpu swiftshader_indirect -memory 4096 -port "$PORT" \
    >/tmp/emu-$PORT.log 2>&1 &
  echo $! > /tmp/emu-$PORT.pid
  adb wait-for-device
  for i in $(seq 1 100); do
    [ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 2
  done
fi
adb -s "$SERIAL" shell getprop sys.boot_completed | tr -d '\r' | grep -q 1 \
  || { echo "[e2e] 模拟器启动超时"; exit 1; }
log "模拟器就绪: $SERIAL"

# 3) 安装 + 清理数据
adb -s "$SERIAL" install -r -t "$APK" >/dev/null 2>&1
adb -s "$SERIAL" shell pm clear org.hiylo.starburst.debug >/dev/null 2>&1

# 4) 运行全部 flows（仅测试用顶层流程，不跑 subflows）
mkdir -p "$REPORT_DIR" "$(dirname "$MAESTRO_LOG")"
log "运行 ${#FLOWS[@]} 个 flow ..."
maestro --device "$SERIAL" test "${FLOWS[@]}" \
  --format junit --output "$REPORT_DIR/e2e-junit.xml" 2>&1 | tee -a "$MAESTRO_LOG"

log "JUnit 报告: $REPORT_DIR/e2e-junit.xml"
grep -oE 'tests="[0-9]+" failures="[0-9]+"' "$REPORT_DIR/e2e-junit.xml" || true

# 5) 清理：仅关闭本次启动的模拟器
if [ -f /tmp/emu-$PORT.pid ]; then
  PID="$(cat /tmp/emu-$PORT.pid)"
  if kill -0 "$PID" 2>/dev/null; then
    log "关闭本次模拟器 (pid $PID)"
    kill "$PID" 2>/dev/null || true
  fi
  rm -f /tmp/emu-$PORT.pid
fi
log "完成"
