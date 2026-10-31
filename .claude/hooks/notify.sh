#!/bin/bash
# Notification: on macOS, show a system notification and (optionally) speak it.
# Set AM_VOICE=0 to mute, AM_VOICE_NAME to choose the voice (default: Zosia).
set -u

input=$(cat)
[ "$(uname)" = "Darwin" ] || exit 0

type=$(printf '%s' "$input" | jq -r '.notification_type // .matcher // ""')
msg=$(printf '%s' "$input" | jq -r '.message // "Claude czeka na Ciebie"')
repo=$(basename "${CLAUDE_PROJECT_DIR:-$(pwd)}")

esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
osascript -e "display notification \"$(esc "$msg")\" with title \"Claude Code · $(esc "$repo")\"" >/dev/null 2>&1 || true

if [ "${AM_VOICE:-1}" != "0" ] && command -v say >/dev/null 2>&1; then
  case "$type" in
    permission_prompt) text="Agent w projekcie $repo prosi o zgodę" ;;
    *)                 text="Agent w projekcie $repo czeka na odpowiedź" ;;
  esac
  say -v "${AM_VOICE_NAME:-Zosia}" "$text" >/dev/null 2>&1 &
fi
exit 0
