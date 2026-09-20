#!/usr/bin/env bash
# 单文件行数治理检查（规约见 workspace 级 AGENTS.md：目标 1000 行 / 硬上限 1500 行）
#
# 用法:
#   scripts/check-file-size.sh            # 检查全部被跟踪源码
#   scripts/check-file-size.sh --staged   # 只检查暂存区（供 pre-commit 使用）
#
# 超过硬上限(1500) 退出码 1；超过目标(1000) 仅告警，不阻断。
# 豁免（按规约不算“代码”）：配置文件、生成代码、SQL 迁移脚本、单文件嵌入式前端。
set -uo pipefail

MODE="${1:-}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

TARGET=1000
HARD=1500

if [ "$MODE" = "--staged" ]; then
  FILES="$(git diff --cached --name-only --diff-filter=ACM)"
else
  FILES="$(git ls-files)"
fi

# 只统计 Kotlin 源码（含 .kts），排除构建产物与生成目录
SRC='\.(kt|kts)$'
EXCLUDE='(/build/|^build/|/generated/|/\.gradle/|/node_modules/)'

HITS=0
WARN=0
while IFS= read -r f; do
  [ -f "$f" ] || continue
  echo "$f" | grep -qE "$SRC" || continue
  echo "$f" | grep -qE "$EXCLUDE" && continue
  n=$(wc -l < "$f")
  if [ "$n" -gt "$HARD" ]; then
    echo "$f: $n 行 > 硬上限 $HARD"
    HITS=$((HITS + 1))
  elif [ "$n" -gt "$TARGET" ]; then
    echo "$f: $n 行 > 目标 $TARGET（待拆分）"
    WARN=$((WARN + 1))
  fi
done <<< "$FILES"

if [ "$HITS" -gt 0 ]; then
  echo
  echo "发现 $HITS 个文件超过 $HARD 行硬上限：按职责整块搬移为同包文件（Compose 用区域"
  echo "composable，类用扩展函数 *Ext.kt），搬完必须 compileDebugKotlin + test 全绿。"
  exit 1
fi
[ "$WARN" -gt 0 ] && echo "（$WARN 个文件高于 $TARGET 行目标，未阻断）"
exit 0
