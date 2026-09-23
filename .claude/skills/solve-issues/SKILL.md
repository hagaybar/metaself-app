---
name: solve-issues
description: Survey every open GitHub issue in MetaSelf, judge which ones are worth doing now and which touch dangerous ground, pick the best value-for-effort issue or a coherent small batch, and carry it through to a tested, merged-ready branch. Use this whenever the owner asks what to work on next, says "pick an issue", "work through the issues", "knock a few off", "what is next", "streamline this", "clear the backlog", or otherwise hands over a pile of issues rather than naming one. Also use it when he names a single issue but has not said how much ceremony it deserves, because deciding that proportionately is most of this skill's value. Do NOT use it for the earlier stage where a feature is still being discussed and designed — that is brainstorming, then writing-plans, then raising the issue.
---

# Solving issues in MetaSelf

## Where this sits

The owner's pipeline has two halves. The first is a conversation: he and Claude talk an idea or a
complaint through, it becomes a spec, a plan, and an issue. **This skill is the second half.** It
starts from a backlog that already exists and ends with working, tested code.

Its whole value is judgement about *what to do next and how much ceremony that thing deserves*.
Picking well and keeping away from dangerous ground matters more than implementing fast, because
this is a personal app holding the owner's real food and weight history — a bad merge costs him
data he cannot get back, and there is no staging environment and no other user to notice first.

What it is for: look at every open issue and the state of the tree, recognise the problematic
paths, and take only the safe ones, choosing the easiest and most effective first. What it
optimises: the most benefit to the app for the least investment — complexity, time, decisions and
tokens all count.

Read that as a ratio, not a budget. A change that takes an afternoon and removes a
daily irritation beats three that take an hour each and remove nothing he would notice.

## The shape of a run

1. Survey everything open, and the state of the tree.
2. Screen out the dangerous ground.
3. Score what is left, benefit over investment.
4. Pick one issue, or a batch that genuinely belongs together.
5. Choose ceremony proportional to risk.
6. Find out everything you need *before* asking him anything.
7. Ask him only what is genuinely his to decide, all at once.
8. Build it, test-first.
9. Verify, and report honestly.

**Green work runs without asking and without announcing.** Do not describe the pick, do not offer a
plan, do not wait for a yes. Safe fixes run without questions; that is the point of the skill. A green issue is green *because* nothing about it needs his judgement — pausing
to tell him what you are about to do spends the one thing this skill exists to save.

Stop and ask only for amber that turns out to need a decision, and for red. Everything else: build
it, test it, report it in a few lines when it is done.

## 0. This repository is public

Everything this skill writes — commits, branch names, plans, issue comments, test names — is
world-readable the moment it is pushed. Follow `CLAUDE.md` § "This repository is PUBLIC": no real
figure, no real food eaten, no frequency of anyone's habits, no quote in the owner's voice. Describe
a defect by what the app does, never by the day it happened to him.

## 1. Survey

Run `scripts/survey.sh`. It gathers, in one pass: every open issue with labels and body, the
current branch and whether the tree is clean, which branches are unmerged, which specs and plans
already exist, and the recent commits.

Two things it surfaces that matter more than they look:

- **An issue may already have a plan.** The convention is `docs/superpowers/plans/<date>-issue-<N>.md`,
  one per issue. (Plans written before 2026-09-22 stayed in the archived private repository and use
  its old issue numbers.) If one exists, the thinking is done — go and execute it rather than re-deriving it.
- **An issue may be in flight.** A branch whose name matches an issue's subject, or a spec written
  today, means someone is mid-thought. Leave it alone.

## 2. Screen out the dangerous ground

This is the step the owner asked for by name. Sort every candidate into one of three bands. **When
unsure, sort upward** — the cost of skipping a safe issue is one wasted minute; the cost of
breaking a risky one is his data.

### Red — do not start without asking him first

- **Room entities, DAOs, migrations, the exported schema.** Eight test classes stand aside on this
  machine because Robolectric's SQLite has no aarch64 build, so database work **cannot be verified
  here at all** — only in CI. Anything in `data/` that touches persistence is red by default.
- **Provenance and the source rank** — `Source`, `Provenance`, `perUnitSourceRank` and the
  rank-guarded writes in `FoodDao`. Decision D4 lives here. A mistake is silent, permanent, and
  indistinguishable afterwards from a correct value.
- **Backup and restore**, and anything the release or signing path touches.
- **Anything whose fix would change a numbered decision** (D2–D51, in `docs/superpowers/specs/`).
  The decisions are the record. Argue with them in the spec, never in code.
- **Anything waiting on a product decision.** If the issue body contains a question only he can
  answer, it is not ready — collect it for step 7 rather than guessing.

### Amber — fine to take, but write a plan first

- Changes to what appears on screen beyond wording.
- The AI seam: prompts, response parsing, the estimator interfaces.
- Navigation across more than one screen.
- Anything touching the daily target arithmetic or the smoothed trend.

### Green — take it straight to a test and a fix, no spec, no plan

- String resources and wording.
- Keyboard type, padding, colour token, text style.
- Moving a composable within a column, or pinning something that scrolls.
- Pure domain functions with JUnit 5 + Truth and no Android dependency.
- Wiring up a capability that already exists and is already tested — adding the button or the link
  that reaches it.

That last one deserves emphasis. **In this repository, most complaints about a missing feature turn
out to be a feature that exists and cannot be reached.** Ticking two foods once appeared to do
nothing; the action bar was correct and had merely scrolled out of view. Before you conclude
anything is absent, grep for it. An issue that turns out to be "already built, just unreachable" is
usually the cheapest and most satisfying thing on the whole board.

## 3. Score

For each surviving candidate, judge two things and write one line each.

**Benefit** — what changes for him. Weight most heavily: things that lose data or state something
untrue; things he hits daily; things that trap him with no way forward. Weight least: internal
tidiness he will never see.

**Investment** — in this order, because this is the order of what is scarce:

1. **His decisions.** The most expensive currency in the loop. An issue needing no decision from
   him is worth far more than one needing two.
2. **Blast radius.** How many files, and do any of them sit under a numbered decision.
3. **CI runs.** Every push to a branch triggers a full test-lint-assemble, and a fix that lands as
   branch-then-merge costs two. Minutes are free on this public repository, but each run still takes
   wall-clock time before a merge: five separate one-line fixes cost ten runs, one batched branch two.
4. **Tokens.** Read the few files that matter rather than sweeping the tree. Do not re-derive what
   an existing spec already settled.

You are not computing a number. You are making an argument in one sentence: *"this one, because it
stops weight readings being lost, touches one file, needs nothing from him, and there is already a
confirmation dialog to copy."*

## 4. Pick one, or a batch that belongs together

**Batch freely, as long as every member is green.** Five or six unrelated green fixes on one branch
is a good day's work, not a mess — because the unit of risk is the commit, not the branch. Keep
each fix its own commit, and a bad one can be pulled back without touching the other five.

What a batch buys: one CI round instead of six, one version, one APK, and **one walk through the app
on his phone instead of six**. That last one is the real saving — his attention is the scarce thing,
and each separate release costs him a check.

**Never batch across risk bands.** One amber issue in a batch of greens makes the whole branch
amber, and an amber change that needs pulling back takes the greens with it.

A batch does not need to share a file or a screen. It needs every member to be independently safe
and independently revertible. If you find yourself unable to give a member its own commit, it does
not belong.

## 5. Ceremony proportional to risk

This is where "minimum investment" is won or lost. Writing a spec for a one-line keyboard fix is
waste; skipping one on a change to the trend arithmetic is how the app starts lying.

| Band | What to produce |
|---|---|
| Green, single issue | A failing test, the fix, a commit. Nothing else. |
| Green, batch | One branch, one plan file only if the batch needs an order. |
| Amber | A plan at `docs/superpowers/plans/<date>-issue-<N>.md`, then execute it. |
| Red | Stop. A spec first, and his approval, before any plan exists. |

Follow the repository's own rhythm: **one step, one plan, one branch.** Branch names describe the
behaviour that changes, not the issue number.

## 6. Find out everything before you ask

The single most valuable habit here. Before forming any question for him:

- **Verify the issue's claim against the code.** Issues in this repo are frequently right about the
  symptom and wrong about the cause. Read the composable, check the order things appear in the
  column, grep for the string, look at the render test that asserts what is on screen.
- **Read the numbered decision** that governs the area, if there is one. The answer is often already
  written down and settled, and asking him again wastes the one thing he has least of.
- **Check the tests.** Whatever you are about to change is probably already pinned
  by one, and that test tells you what the behaviour is *meant* to be.

If, after this, the answer exists anywhere — in the code, the spec, an old issue, a commit message —
you have no question. Proceed.

## 7. Ask only what is his, and ask once

A real question is one where two defensible answers exist and the difference is a matter of taste,
priority, or what he wants the app to be. Everything else you can settle yourself.

When you do have questions, **batch them into one message** and follow `~/.claude/CLAUDE.md`
exactly — he owns the product, does not read code, and cannot hold identifiers in his head:

- Never make an issue number, class or file the subject of a sentence. Say what the thing does on
  screen; put the number in brackets afterwards, once.
- Lead with the effect, then the mechanism.
- State the real choice, what each option costs, and your recommendation. If answering would need
  him to go and look something up, the question is written wrong.
- Be brief.

If a question blocks the pick but the board has other work, **say so and take the next issue
instead** rather than idling. Leave the blocked one with a comment on the issue recording exactly
what is needed.

## 8. Build it

Test first, always. The house rules that actually bite:

- **Never run bare `./gradlew` — a hook blocks it.** Use `~/bin/gradlew-safe` with the same
  arguments, and `export ANDROID_HOME=/home/ubuntu/android-sdk` first.
- **Never pipe a build whose result you intend to report.** The pipe returns the filter's exit
  code, so a failed build reports success.
- **JUnit 5 + Truth for anything pure; JUnit 4 only where Robolectric or Compose demands it.** The
  two `@Test` annotations look identical at the call site and mixing the imports produces a test
  that silently never runs.
- **Exactly eight test classes skip on this machine** — `MigrationTest`, `MealDaoTest`,
  `RoomMealRepositoryTest`, `RoomFoodRepositoryTest`, `RoomSavedMealRepositoryTest`, `WeightDaoTest`,
  `RoomWeightRepositoryTest`, `BackupRoundTripTest`. Anything else skipping is a mis-wired framework,
  not a supported case. Stop and investigate.
- **A Compose render test reads text through `EditableText`, not `Text`.** A helper reading only
  `Text` sees a form's labels and nothing typed into it.
- **The release build is `~/bin/ms-release`**, never `assembleRelease`.

## 9. Verify, then report — briefly

Run `~/bin/gradlew-safe :app:testDebugUnitTest` and `:app:lintDebug` and read the real output before
claiming anything passes. Evidence before assertions.

**Then keep the report short.** A long report on safe work he did not
have to think about is the same waste as asking permission for it. Aim for something he reads in
under thirty seconds:

- One line per fix: what is different on his phone now.
- One line for where it is: branch, or PR.
- One line for anything unverified here — anything touching the database ran only in CI.
- Questions, if any, last and few.

No preamble, no restating the issues, no explaining the reasoning behind safe choices he did not
need to make. If he wants the reasoning it is in the commit messages, which is what they are for.

Close the loop on GitHub: reference the issue in the commit so it links (issue numbers inside
code comments are the OLD private ones; this repository's own issues start from 1), and leave a comment on
anything you looked at and deliberately did not take, saying why. **A board where the skipped items
explain themselves is worth more than one where they silently rot.**

## When the board is all red

Say so rather than lowering the bar. "Everything open either needs a decision from you or touches
the database, which cannot be tested on this machine" is a useful answer, and picking a risky issue
to look busy is not. Offer the decisions he could make to unblock the most work, cheapest first.
