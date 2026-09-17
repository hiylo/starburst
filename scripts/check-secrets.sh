#!/usr/bin/env bash
# 敏感信息提交前检查
#
# 用法:
#   scripts/check-secrets.sh            # 检查暂存区（默认，pre-commit 钩子使用）
#   scripts/check-secrets.sh --all      # 检查全部被跟踪文本文件（推送 GitHub 前自查）
#
# 命中任意规则即退出码 1，并打印 文件:行。
# 允许: 回环地址(localhost/127.0.0.1)、文档网段(192.0.2.x/198.51.100.x/203.0.113.x)、
#       开发默认弱口令、以及 .secrets-allowlist 中登记的模式（如检测逻辑的单测夹具）。
set -uo pipefail

MODE="${1:-staged}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1

# 规则 1: 私网 IP (RFC1918: 10/8, 172.16/12, 192.168/16)
PAT_IP='(^|[^0-9])(10|172\.(1[6-9]|2[0-9]|3[01])|192\.168)\.[0-9]{1,3}\.[0-9]{1,3}'
# 规则 2: 常见令牌（GitLab/GitHub/OpenAI/AWS/Google/Slack/Google OAuth/JWT）
PAT_TOKEN='(^|[^A-Za-z0-9])(glpat|ghp|gho|ghu|ghs|ghr|github_pat)_[A-Za-z0-9]{20,}|(^|[^A-Za-z0-9])sk-[A-Za-z0-9]{20,}|(^|[^A-Z0-9])AKIA[0-9A-Z]{16}|(^|[^A-Za-z0-9])AIza[0-9A-Za-z_-]{35}|(^|[^A-Za-z0-9_-])xox[baprs]-[A-Za-z0-9-]{10,}|ya29\.[A-Za-z0-9_-]{20,}|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}'
# 规则 3: URL 内嵌凭据 user:pass@host
PAT_CRED_URL='://[^/\s:@]+:[^@\s/]+@'
# 规则 4: 私钥块
PAT_PEM='-----BEGIN (RSA|EC|OPENSSH|DSA|PGP) PRIVATE KEY-----'

# 放行: 回环地址 / 文档网段
ALLOW_HOST='(localhost|127\.0\.0\.1|192\.0\.2\.|198\.51\.100\.|203\.0\.113\.)'
# 放行: 本地开发/CI 默认弱口令（user:weak@host）
WEAK_CRED='://[^/@\s]+:(pass|postgres|opencode|admin|password|test|changeme|123456|secret)@'
# 放行: .secrets-allowlist 登记的模式（每行一个 grep -E 正则，`#` 开头为注释）
ALLOWLIST='$^'
if [ -f "$ROOT/.secrets-allowlist" ]; then
  L="$(grep -vE '^\s*#|^\s*$' "$ROOT/.secrets-allowlist" | paste -sd'|' -)"
  [ -n "$L" ] && ALLOWLIST="$L"
fi

if [ "$MODE" = "--all" ]; then
  FILES="$(git ls-files | grep -vE '(^|/)(build|\.gradle|\.idea|node_modules|\.git)/' || true)"
else
  FILES="$(git diff --cached --name-only --diff-filter=ACM)"
fi
[ -z "$FILES" ] && exit 0

HITS=0
while IFS= read -r f; do
  [ -f "$f" ] || continue
  grep -Iq . "$f" 2>/dev/null || continue
  if grep -nHnE "$PAT_IP|$PAT_TOKEN|$PAT_PEM" "$f" | grep -EvE "$ALLOWLIST"; then
    HITS=$((HITS + 1))
  fi
  if grep -nHnE "$PAT_CRED_URL" "$f" | grep -EvE "$ALLOW_HOST|$WEAK_CRED|$ALLOWLIST"; then
    HITS=$((HITS + 1))
  fi
done <<< "$FILES"

if [ "$HITS" -gt 0 ]; then
  echo
  echo "发现敏感内容（见上方命中）。请改为 RFC 5737 文档网段或环境变量引用；"
  echo "确属测试夹具等误报时，可在 .secrets-allowlist 登记后重跑。"
  exit 1
fi
exit 0
