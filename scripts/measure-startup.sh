#!/usr/bin/env bash
# Cold-start latency + memory baseline for StarBurst on a connected device/emulator.
#
# Usage:
#   scripts/measure-startup.sh [runs]      # default 5 runs
#   APK=path/to/app-debug.apk scripts/measure-startup.sh   # install APK first
#   ID of a running device can be pinned via adb -s, or ANDROID_SERIAL env.
set -euo pipefail

PKG="org.hiylo.starburst"
ACTIVITY="${PKG}/.MainActivity"
RUNS="${1:-5}"

adb() { command adb "$@"; }

adb wait-for-device || { echo "no device connected"; exit 1; }

inject_apk() {
    if [ -n "${APK:-}" ]; then
        echo "installing $APK"
        adb install -r "$APK"
    fi
}

cold_start_total() {
    adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
    sleep 1
    adb shell am start -W -n "$ACTIVITY" 2>/dev/null
}

median_of() {
    local sorted count mid
    sorted=$(printf '%s\n' "$@" | grep -E '^[0-9]+$' | sort -n)
    count=$(printf '%s\n' "$sorted" | wc -l)
    if [ "$count" -eq 0 ]; then
        echo "NA"
        return
    fi
    mid=$(((count + 1) / 2))
    printf '%s\n' "$sorted" | sed -n "${mid}p"
}

avg_of() {
    local sorted
    sorted=$(printf '%s\n' "$@" | grep -E '^[0-9]+$')
    if [ -z "$sorted" ]; then
        echo "NA"
        return
    fi
    printf '%s\n' "$sorted" | awk '{ s += $1; n += 1 } END { if (n > 0) printf "%.0f", s / n }'
}

inject_apk

echo "=== cold start ($RUNS runs) ==="
times=()
for i in $(seq 1 "$RUNS"); do
    out=$(cold_start_total)
    total=$(printf '%s\n' "$out" | awk -F: 'gsub(/^ *| *$/,"",$2) { print $3 }' /dev/null)
    # am start -W output: "TotalTime:    1234" (and WaitTime). Parse robustly.
    total=$(printf '%s\n' "$out" | sed -n 's/^ *TotalTime: *\([0-9]*\).*/\1/p')
    echo "  run $i: TotalTime=${total:-NA}ms"
    if [ -n "$total" ]; then
        times+=("$total")
    fi
done

echo "--- summary ---"
echo "cold-start median: $(median_of "${times[@]:-0}")ms"
echo "cold-start avg:   $(avg_of "${times[@]:-0}")ms"

echo "=== memory (settled after ~4s foreground) ==="
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
sleep 1
adb shell am start -n "$ACTIVITY" >/dev/null 2>&1 || true
sleep 4
adb shell dumpsys meminfo "$PKG" 2>/dev/null | grep -E "TOTAL PSS|TOTAL RSS" | head -2 || true

echo "=== done ==="