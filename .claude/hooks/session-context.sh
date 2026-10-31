#!/bin/bash
# SessionStart: gives Claude a short picture of the repo state. Plain stdout becomes context.
set -u
root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$root" || exit 0

echo "## Stan repozytorium (SessionStart)"
echo "Gałąź: $(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
dirty=$(git status --short 2>/dev/null | head -10)
[ -n "$dirty" ] && { echo "Niezatwierdzone zmiany:"; echo "$dirty"; }
echo "Ostatnie commity:"
git log --oneline -3 2>/dev/null | sed 's/^/  /'

if [ -f docs/ROADMAP.md ]; then
  echo "W toku ([~]):"
  grep -E '^- \[~\] AM-[0-9]' docs/ROADMAP.md | awk -F ' · ' '{print "  " $1 " · " $2}' | head -5
  echo "Następne do zrobienia ([ ]):"
  grep -E '^- \[ \] AM-[0-9]' docs/ROADMAP.md | awk -F ' · ' '{print "  " $1 " · " $2}' | head -3
fi

if command -v gh >/dev/null 2>&1; then
  # macOS has no `timeout` by default; GH_PROMPT_DISABLED keeps gh from waiting for input.
  prs=$( (GH_PROMPT_DISABLED=1 gh pr list --state open --limit 5 2>/dev/null || true) | cut -f1-3)
  [ -n "$prs" ] && { echo "Otwarte PR-y:"; echo "$prs" | sed 's/^/  /'; }
fi
exit 0
