#!/bin/bash
# PostToolUse(Edit|Write), async: formats the edited Kotlin file with ktlint when it is installed.
# The full formatting check still runs in quickCheck / CI (Spotless).
set -u

path=$(jq -r '.tool_input.file_path // ""')
case "$path" in
  *.kt|*.kts) ;;
  *) exit 0 ;;
esac
[ -f "$path" ] || exit 0

if command -v ktlint >/dev/null 2>&1; then
  ktlint --format --relative "$path" >/dev/null 2>&1 || true
fi
exit 0
