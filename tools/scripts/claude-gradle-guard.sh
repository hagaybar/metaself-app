#!/usr/bin/env bash
# PreToolUse guard: refuse bare ./gradlew, point at the serialized wrapper.
#
# On 2026-08-24 concurrent Gradle builds (several agents, each re-running the
# suite) exhausted a 12GB box that also serves live nginx sites, and the owner
# could not SSH in. ~/bin/gradlew-safe holds a flock so only one build runs at
# a time. This hook exists because the habit of typing ./gradlew is strong and
# a guardrail nobody can bypass by reflex is the only kind that holds.
set -euo pipefail
cmd=$(jq -r '.tool_input.command // ""' 2>/dev/null || echo "")

# Already going through the wrapper (or not gradle at all) -> allow silently.
case "$cmd" in *gradlew-safe*) exit 0 ;; esac
printf '%s' "$cmd" | grep -qE '(^|[^[:alnum:]_./-])\./gradlew([[:space:]]|$)' || exit 0

jq -nc '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "deny",
    permissionDecisionReason:
      "Bare ./gradlew is blocked on this machine. Use ~/bin/gradlew-safe instead — it holds a flock so only ONE Gradle build runs at a time. Concurrent builds once exhausted this 12GB box (which also serves live nginx sites) and locked the owner out of SSH. Same arguments, e.g. ~/bin/gradlew-safe :app:testDebugUnitTest. For an untrusted/runaway build add GRADLE_HARD_CAP=1 for a hard cgroup memory ceiling."
  }
}'
