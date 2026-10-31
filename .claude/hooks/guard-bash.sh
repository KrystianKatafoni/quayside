#!/bin/bash
# PreToolUse(Bash): blocks git and shell commands that break the repo workflow.
# Decision is returned as JSON (deny / ask); silence means "no opinion".
set -u

input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // ""')
[ -z "$cmd" ] && exit 0

decide() { # $1 = deny|ask, $2 = reason
  jq -n --arg d "$1" --arg r "$2" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: $d, permissionDecisionReason: $r}}'
  exit 0
}

has() { printf '%s' "$cmd" | grep -Eq -- "$1"; }

# --- git push ---------------------------------------------------------------
if has '(^|[;&|[:space:]])git[[:space:]]+push'; then
  current=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
  # Only exception (CLAUDE.md, zasada 6): updating your own task branch after a rebase.
  if has '--force-with-lease'; then
    case "$current" in
      feat/*|fix/*|chore/*|docs/*)
        branch_re=$(printf '%s' "$current" | sed 's/[.[\*^$/]/\\&/g')
        printf '%s' "$cmd" | grep -Eq "^[[:space:]]*git[[:space:]]+push[[:space:]]+--force-with-lease([[:space:]]+origin([[:space:]]+(HEAD|$branch_re))?)?[[:space:]]*$" ||
          decide deny "--force-with-lease tylko w postaci: git push --force-with-lease [origin [HEAD|$current]] (bieżąca gałąź zadania)."
        ;;
      *) decide deny "--force-with-lease jest dozwolone tylko na własnej gałęzi feat/*, fix/*, chore/*, docs/*." ;;
    esac
  fi
  has '(--force([[:space:]]|=|$)|[[:space:]]-f([[:space:]]|$)|[[:space:]]\+[^[:space:]]+)' &&
    decide deny "Force push jest zabroniony w tym repo (CLAUDE.md, zasada 6). Po rebase użyj: git push --force-with-lease."
  has '[[:space:]](origin[[:space:]]+)?(HEAD:)?(refs/heads/)?(main|master)([[:space:]]|$)|:(main|master)([[:space:]]|$)' &&
    decide deny "Push do main/master jest zabroniony. Wypchnij gałąź zadania i otwórz PR przez /ship."
  if [ "$current" = "main" ] || [ "$current" = "master" ]; then
    decide deny "Jesteś na gałęzi $current. Utwórz gałąź zadania (feat/AM-XXX-…) zanim cokolwiek wypchniesz."
  fi
fi

# --- bypassing checks -------------------------------------------------------
has '(^|[;&|[:space:]])git[[:space:]]+(commit|push|merge)[^;&|]*--no-verify' &&
  decide deny "--no-verify jest zabronione. Napraw przyczynę błędu zamiast omijać bramkę."

# --- merging is a human decision ----------------------------------------------
has '(^|[;&|[:space:]])gh[[:space:]]+pr[[:space:]]+merge' &&
  decide deny "Scalanie PR należy do człowieka. Zostaw PR do przeglądu."
has 'gh[[:space:]]+api[^;&|]*pulls/[0-9]+/merge' &&
  decide deny "Scalanie PR przez API jest zabronione."

# --- destructive commands -----------------------------------------------------
has 'rm[[:space:]]+(-[a-zA-Z]*r[a-zA-Z]*f|-[a-zA-Z]*f[a-zA-Z]*r)[[:space:]]+(/|~|\$HOME|\.\.)([[:space:]/]|$)' &&
  decide deny "Usuwanie katalogu domowego, głównego lub nadrzędnego jest zabronione."
has '(^|[;&|[:space:]])git[[:space:]]+(reset[[:space:]]+--hard|clean[[:space:]]+-[a-zA-Z]*[fdx])' &&
  decide ask "Ta komenda nieodwracalnie usunie niezatwierdzone zmiany. Potwierdź."
has '(^|[;&|[:space:]])gh[[:space:]]+(repo[[:space:]]+delete|release[[:space:]]+delete)' &&
  decide deny "Usuwanie repozytorium lub wydania jest zabronione."

# --- agent must not loosen its own guardrails ---------------------------------
if [ "${AM_ALLOW_AGENT_CONFIG_EDIT:-0}" != "1" ]; then
  has '(>|>>|tee|sed[[:space:]]+-i|mv|cp|rm)[^;&|]*(\.claude/settings(\.local)?\.json|\.claude/hooks/|~/\.claude/settings\.json)' &&
    decide deny "Zmiana konfiguracji agenta przez powłokę jest zabroniona. Poproś człowieka."
fi

exit 0
