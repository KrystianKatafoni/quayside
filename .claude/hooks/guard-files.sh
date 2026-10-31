#!/bin/bash
# PreToolUse(Edit|Write|NotebookEdit): protects agent config, secrets, accepted ADRs and CI workflows.
set -u

input=$(cat)
path=$(printf '%s' "$input" | jq -r '.tool_input.file_path // .tool_input.notebook_path // ""')
[ -z "$path" ] && exit 0

root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
rel="${path#"$root"/}"

decide() {
  jq -n --arg d "$1" --arg r "$2" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: $d, permissionDecisionReason: $r}}'
  exit 0
}

# Secrets and signing material: never.
case "$rel" in
  *.p12|*.cer|*.mobileprovision|.env|.env.*|*/.env|*/.env.*)
    decide deny "Pliki z sekretami nie mogą być tworzone ani edytowane przez agenta." ;;
esac

# Files outside the repository (e.g. ~/.claude/settings.json).
case "$path" in
  /*) case "$path" in
        "$root"/*) ;;
        *) decide ask "Zapis poza repozytorium: $path. Potwierdź, że to zamierzone." ;;
      esac ;;
esac

# Agent configuration: only on explicit human request.
if [ "${AM_ALLOW_AGENT_CONFIG_EDIT:-0}" != "1" ]; then
  case "$rel" in
    .claude/settings.json|.claude/settings.local.json|.claude/hooks/*)
      decide deny "Konfiguracja i hooki agenta są chronione. Człowiek może zezwolić, uruchamiając sesję z AM_ALLOW_AGENT_CONFIG_EDIT=1." ;;
  esac
fi

# CI and release workflows: always ask.
case "$rel" in
  .github/workflows/*) decide ask "Zmiana workflow GitHub Actions ($rel). Potwierdź." ;;
esac

# Accepted ADRs are immutable; supersede them with a new ADR instead.
case "$rel" in
  docs/adr/0000-template.md) ;;
  docs/adr/*.md)
    if [ -f "$path" ] && grep -q 'Status:\*\* przyjęta' "$path"; then
      decide deny "ADR $rel jest przyjęty i niezmienny. Utwórz nowy ADR (/adr), który go zastępuje."
    fi ;;
esac

exit 0
