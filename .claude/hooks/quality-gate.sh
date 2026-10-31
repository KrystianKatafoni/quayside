#!/bin/bash
# Stop: before Claude finishes a turn with uncommitted Kotlin changes, run the fast quality gate
# (Gradle task `quickCheck`: Spotless, detekt, architecture tests). On failure, exit 2 sends the
# summary back to Claude so it keeps fixing. After 3 failed rounds it hands over to the human.
set -u

input=$(cat)
root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$root" || exit 0

[ -x ./gradlew ] || exit 0
[ "${AM_SKIP_QUALITY_GATE:-0}" = "1" ] && exit 0

changed=$(git status --porcelain -- '*.kt' '*.kts' 2>/dev/null)
[ -z "$changed" ] && exit 0

session=$(printf '%s' "$input" | jq -r '.session_id // "unknown"')
active=$(printf '%s' "$input" | jq -r '.stop_hook_active // false')
state_dir="$root/.claude/.state"
mkdir -p "$state_dir"
count_file="$state_dir/gate-$session.count"
pass_file="$state_dir/gate-$session.pass"

fingerprint=$( { git diff -- '*.kt' '*.kts'; git status --porcelain -- '*.kt' '*.kts'; } | shasum | cut -d' ' -f1)
[ -f "$pass_file" ] && [ "$(cat "$pass_file")" = "$fingerprint" ] && exit 0

count=0
[ "$active" = "true" ] && [ -f "$count_file" ] && count=$(cat "$count_file")
if [ "$count" -ge 3 ]; then
  rm -f "$count_file"
  jq -n '{systemMessage: "Bramka quickCheck nie przeszła 3 razy z rzędu. Zatrzymuję poprawki i oddaję decyzję człowiekowi."}'
  exit 0
fi

log="$state_dir/gate-$session.log"
if ./gradlew quickCheck --quiet --console=plain >"$log" 2>&1; then
  printf '%s' "$fingerprint" >"$pass_file"
  rm -f "$count_file"
  exit 0
fi

# The task is introduced in AM-105; before that the gate is a no-op.
grep -q "Task 'quickCheck' not found" "$log" && exit 0

echo $((count + 1)) >"$count_file"
{
  echo "Bramka quickCheck nie przeszła (runda $((count + 1))/3). Napraw błędy przed zakończeniem:"
  grep -E 'e: |w: |FAILED|Violation|violates|Lint error|\.kt:[0-9]+' "$log" | head -40
  echo "Pełny log: .claude/.state/gate-$session.log"
} >&2
exit 2
