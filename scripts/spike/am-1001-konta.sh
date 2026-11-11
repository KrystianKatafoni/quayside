#!/usr/bin/env bash
# AM-1001: nagrania CLI dla dwóch katalogów konfiguracji (ADR 0029, "Do ręcznego sprawdzenia").
# Użycie: scripts/spike/am-1001-konta.sh <faza> [katalog-wyjściowy]
# Fazy: status | daemon | bg
# Skrypt nie zapisuje w ~/.claude (poza tym, co robi sam claude przy zwykłym wywołaniu). Konto testowe: ~/.claude-quayside/spike.
# E-mail, orgId i ścieżki domowe są zastępowane w nagraniach (redact).
set -u
PHASE="${1:?podaj fazę: status | daemon | bg}"
OUT="${2:-$(cd "$(dirname "$0")/../.." && pwd -P)/build/spike-am-1001}"
A=""   # konto domyślne: zmienna CLAUDE_CONFIG_DIR NIE jest ustawiana (patrz wynik 1 w docs/compat.md)
B="$HOME/.claude-quayside/spike"
mkdir -p "$OUT" "$B"

redact() {
  sed -E \
    -e "s#$HOME#~#g" \
    -e 's#[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}#user@example.com#g' \
    -e 's#(backgrounded · |claude (attach|logs|stop) )[0-9a-f]{8}#\100000001#g' \
    -e 's#"id": "[0-9a-f]{8}"#"id": "00000001"#g' \
    -e 's#[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}#UUID-REDACTED#g'
}

# rec <etykieta> <katalog-konfiguracji> <komenda...>: zapisuje stdout, stderr i kod wyjścia.
rec() {
  local label="$1" dir="$2"; shift 2
  local base="$OUT/$label"
  if [ -z "$dir" ]; then
    env -u CLAUDE_CONFIG_DIR "$@" >"$base.stdout" 2>"$base.stderr"
  else
    CLAUDE_CONFIG_DIR="$dir" "$@" >"$base.stdout" 2>"$base.stderr"
  fi
  echo "$?" >"$base.exit"
  for f in stdout stderr; do redact <"$base.$f" >"$base.$f.r" && mv "$base.$f.r" "$base.$f"; done
  echo "[$label] kod $(cat "$base.exit")"
}

case "$PHASE" in
  status)  # punkt 2 (konto zalogowane A i wylogowane B)
    rec auth-status-A "$A" claude auth status
    rec auth-status-B "$B" claude auth status
    rec auth-status-B-text "$B" claude auth status --text
    ;;
  daemon)  # punkty 1 i 6 (osobne demony, kolizje)
    rec daemon-status-A-before "$A" claude daemon status
    rec daemon-status-B-before "$B" claude daemon status
    rec agents-A "$A" claude agents --json --all
    rec agents-B "$B" claude agents --json --all
    rec daemon-status-A-after "$A" claude daemon status
    rec daemon-status-B-after "$B" claude daemon status
    ps -axo pid,ppid,command | grep -i '[c]laude' | grep -i daemon | redact >"$OUT/ps-daemons.txt"
    ls -la "$B" | redact >"$OUT/ls-B.txt"
    ls -la /tmp 2>/dev/null | grep -i claude | redact >"$OUT/ls-tmp-claude.txt"
    ;;
  bg)      # punkty 1, 5, 7: sesja w tle na koncie B (wymaga zalogowanego B)
    WORK="$(cd "$(mktemp -d)" && pwd -P)"
    # zaufanie tylko w pliku konta testowego B (nie ruszamy konta domyślnego)
    python3 -I - "$B/.claude.json" "$WORK" <<'PY'
import json, sys, pathlib
path, work = pathlib.Path(sys.argv[1]), sys.argv[2]
data = json.loads(path.read_text()) if path.exists() else {}
data.setdefault("projects", {}).setdefault(work, {})["hasTrustDialogAccepted"] = True
path.write_text(json.dumps(data, indent=2))
PY
    cd "$WORK" || exit 1
    rec bg-start-B "$B" claude --bg --name spike-b -- "Reply with the single word OK and stop."
    sleep 20
    rec agents-B-after-bg "$B" claude agents --json --all
    rec agents-A-after-bg "$A" claude agents --json --all
    ls -R "$B" 2>/dev/null | head -80 | redact >"$OUT/tree-B-after-bg.txt"
    echo "Katalog roboczy testu: $WORK"
    ;;
  *) echo "nieznana faza: $PHASE" >&2; exit 2 ;;
esac
echo "Nagrania w: $OUT"
