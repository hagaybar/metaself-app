#!/usr/bin/env bash
# One pass over everything a picker needs to choose the next issue.
#
# Written once and bundled because every run needs exactly this and nothing more: re-deriving it
# each time costs tokens and, worse, tempts a sweep of the whole tree when six commands will do.
#
# Read-only. Safe to run at any time, on any branch.

set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)" || exit 1

rule() { printf '\n==================== %s ====================\n' "$1"; }

rule "OPEN ISSUES"
gh issue list --state open --limit 100 \
  --json number,title,labels,createdAt,comments \
  --template '{{range .}}#{{.number}}  {{.title}}
      labels: {{if .labels}}{{range .labels}}{{.name}} {{end}}{{else}}none{{end}}   comments: {{len .comments}}   raised: {{timeago .createdAt}}
{{end}}' 2>&1

rule "WHICH ISSUES ALREADY HAVE A PLAN"
# Convention: docs/superpowers/plans/<date>-issue-<N>.md, one per issue. A plan means the thinking
# is done -- execute it rather than deriving it again.
if ls docs/superpowers/plans/*issue-*.md >/dev/null 2>&1; then
  for f in docs/superpowers/plans/*issue-*.md; do
    n=$(basename "$f" | sed -n 's/.*issue-\([0-9]\+\).*/\1/p')
    state=$(gh issue view "$n" --json state --jq .state 2>/dev/null || echo "?")
    printf '  issue #%-4s %-10s %s\n' "$n" "$state" "$f"
  done
else
  echo "  none named for an issue"
fi

rule "THE LAST FEW COMMITS THAT TOUCHED SPECS OR PLANS"
# Something written this week may be mid-thought -- do not take an issue out from under it.
# Git history, not file timestamps: a fresh checkout makes every file look modified today.
# The last few commits that touched them, with dates -- bounded, and judgeable at a glance.
# A single reorganising commit can touch thirty plan files, which says nothing about intent.
git log -4 --date=short --name-only --pretty=format:'  %ad  %s' -- \
  docs/superpowers/specs docs/superpowers/plans 2>/dev/null \
  | sed 's/^docs/      docs/' || true
echo
# Uncommitted ones count too -- they are the most likely to be mid-thought of all.
git status --porcelain -- docs/superpowers 2>/dev/null \
  | sed 's/^/  UNCOMMITTED /' || true

rule "TREE AND BRANCHES"
printf 'current branch: %s\n' "$(git rev-parse --abbrev-ref HEAD)"
if [ -n "$(git status --porcelain)" ]; then
  echo "WORKING TREE IS DIRTY -- finish or stash before starting new work:"
  git status --short | sed 's/^/  /'
else
  echo "working tree clean"
fi
echo
echo "branches not merged into main (work in flight -- leave these issues alone):"
git branch --no-merged main 2>/dev/null | sed 's/^/  /' || echo "  none"

rule "RECENT COMMITS"
git log --oneline -12 | sed 's/^/  /'

rule "LAST CI RUNS"
gh run list --limit 5 2>/dev/null | sed 's/^/  /' || echo "  unavailable"

rule "SIZE OF THE FIELD"
printf '  main sources: %s   test files: %s\n' \
  "$(find app/src/main -name '*.kt' 2>/dev/null | wc -l)" \
  "$(find app/src/test -name '*Test.kt' 2>/dev/null | wc -l)"

cat <<'NOTE'

==================== REMINDERS ====================
  Sort upward when unsure. Red beats amber beats green.
  RED by default: anything under data/ that persists, provenance and source rank,
       backup/restore, the release path, anything changing a numbered decision,
       anything whose issue body asks a question only the owner can answer.
  Before concluding a feature is MISSING, grep for it. In this repo the usual
       answer is that it exists and cannot be reached.
  Read the issue's claim against the code before believing it.
NOTE
