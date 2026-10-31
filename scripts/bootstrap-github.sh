#!/bin/bash
# One-time GitHub setup for this repository (run by a human or the agent after the repo exists):
# labels, milestones and issues from docs/ROADMAP.md. Branch protection stays a manual step
# (docs/13-plan-wdrozenia.md, section 2) because it needs admin rights.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v gh >/dev/null || { echo "Zainstaluj GitHub CLI: brew install gh"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Zaloguj się: gh auth login"; exit 1; }

python3 scripts/roadmap_sync.py --dry-run
read -r -p "Zastosować powyższe zmiany na GitHubie? [t/N] " answer
[ "${answer:-N}" = "t" ] || [ "${answer:-N}" = "T" ] || { echo "Przerwano."; exit 0; }
python3 scripts/roadmap_sync.py

cat <<'EOF'

Gotowe. Pozostałe kroki ręczne:
  1. Settings → Branches: chroń `main` (wymagany PR, status CI, zakaz force push).
  2. W repo uruchom `claude` i `/install-github-app` (Claude GitHub App + sekret CLAUDE_CODE_OAUTH_TOKEN).
  3. Zatwierdź zmianę docs/ROADMAP.md (numery issue) przez `/roadmap-sync` lub `/ship`.
EOF
