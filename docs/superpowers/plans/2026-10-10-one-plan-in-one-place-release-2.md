# Goals and blocks — Implementation Plan (release 2 of D119–D125: D119, D120, D121, D124's form, D123's Month, D125)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** release 2 of `docs/superpowers/specs/2026-10-10-one-plan-in-one-place-design.md`, and release 1's three
known limits. **Plan ahead** asks for a goal — **Keep a steady rhythm**, or **a distance by a date** (run, walk,
swim or ride; km; a date 6 to 30 weeks ahead) — sessions a week, and words; no number of weeks. The answer carries
an **outline**: blocks (base, build, peak, taper, steady), each with a focus and one target per week (total minutes,
the longest session's minutes, an easier week or not), which the phone checks covers every week to the goal (a
steady plan: exactly four) and lies in D94's bounds. **Only the first two weeks carry sessions**; later weeks carry
their target. **Fill in next week** is offered on the plan page from Friday when next week is empty (and is the
Trainer page's main button on Sunday); **Plan this week** when this week has begun empty; a steady plan's last week
offers **Plan the next block**. Adjust is unchanged in purpose: it rewrites the filled weeks and may change later
targets. The plan page's **Month** view draws the goal line and each block's weeks as bars of their targets — past
weeks with the minutes done drawn over, easier weeks lighter, this week "you are here"; Week shows a week beyond the
filled ones as its target. Every plan request carries the goal, the outline and which weeks are filled; the
instructions carry the coaching conventions, labelled as conventions, and the sum-near-target rule. A plan made
before this release has no goal and no outline and **reads and draws exactly as now**. Release 1's limits: a review
is matched to the steps of the planned session it ticked; steps follow their session through an adjustment; a
replaced plan counts through its Sunday. Version 0.78.0 (134).

**Decision:** the owner's, 2026-10-10 — the spec above (release 2 of its "Built in two releases"). Branch
`goals-and-blocks`, from `one-plan-in-one-place` (release 1, unmerged). **No Room schema change** (choice 1).

**Architecture:**

```
domain/trainer/Programme.kt        PlanGoal, GoalSport, Phase, WeekTarget, Block, Outline, PlanAheadAsk;
                                   ProgrammeAsk.goal; WeeksPlan.outline; Programmes.inEffect               JUnit 5   Tasks 1, 2
data/ai/TrainerResponse.kt         outline in the weeks plan's JSON; goal beside it; the new replies        JUnit 5   Tasks 1, 3
data/ai/TrainerPrompt.kt           goal, outline, filled_weeks sent; conventions; FILL and NEXT_BLOCK        JUnit 5   Task 3
data/trainer/ProgrammeStore.kt     goal read and written; a replaced plan stops on its Sunday               JUnit 5   Tasks 1, 2
data/trainer/TrainerStore.kt + Dao moveSlot (an UPDATE, no schema change)                                   JUnit 5   Task 2
domain/trainer/PlanProgress.kt     placeOf(workoutId)                                                        JUnit 5   Task 2
domain/trainer/StepsCarry.kt       NEW, pure: where kept steps go in a new version                           JUnit 5   Task 2
domain/trainer/TrainerHome.kt      PlanCard.Running.replacedFrom; MainAction's new cases                     JUnit 5   Tasks 2, 4
domain/trainer/TrainerRequest.kt   TrainerQuestion.FillWeek, NextBlock; Trainer.fill, nextBlock              JUnit 5   Task 3
domain/trainer/TrainerPath.kt      FILL_WEEK, NEXT_BLOCK; WORKBENCH (the four it lists)                      JUnit 5   Task 3
data/trainer/AskTheTrainer.kt      in-effect card, steps carried, stepsFor(workout); planAhead, fillWeek,
                                   nextBlock, adjust with an outline; planShown's month                      JUnit 5   Tasks 2, 3, 4
domain/trainer/PlanFilling.kt      NEW, pure: Fill in next week / Plan this week / Plan the next block       JUnit 5   Task 4
domain/trainer/PlanMonth.kt        NEW, pure: the Month view's blocks and bars                               JUnit 5   Task 4
domain/trainer/PlanTimeline.kt     TimelineWeek.target                                                       JUnit 5   Task 4
ui/trainer/GoalWording.kt          NEW: the goal line, the target line, the phases, the form's words          JUnit 5   Task 4
ui/trainer/PlanPageWording.kt, PlanTimelineWording.kt, TrainerWeekWording.kt                                JUnit 5   Task 4
ui/screen/trainer/EvaluatePlanViewModel.kt  the goal form; fill, next block; the month                      JUnit 5   Task 5
ui/screen/trainer/ReviewSessionViewModel.kt the steps of the ticked session                                 JUnit 5   Task 2
ui/screen/trainer/TrainerViewModel.kt       the plan in effect                                              JUnit 5   Task 2
ui/screen/settings/TrainerWorkbench*        the goal form's steady case; WORKBENCH paths                    JUnit 5   Task 5
ui/screen/trainer/PlanMonthView.kt          NEW: the Month view                                             Robolectric Task 6
ui/screen/trainer/EvaluatePlanScreen.kt, PlanTimelineView.kt, TrainerScreen.kt, ui/nav/MetaSelfNavHost.kt   Robolectric Task 6
app/build.gradle.kts                        0.78.0 / 134                                                    Task 7
```

**Tech Stack:** Kotlin 1.9.22, Compose (BOM 2024.02.00, Material 3). JUnit 5 + Truth for everything pure and for
the view models; JUnit 4 + Robolectric only for the Compose render tests, through `ComposeRender`.

---

## Design choices the spec leaves open — settled here

1. **Storage — no schema change.** A plan's row already holds its weeks as JSON in `trainer_programmes.plan`. The
   **outline** is one more key of that JSON, `"outline": {"blocks": [{"phase": "base", "focus": "…", "weeks":
   [{"minutes": 120, "longest_minutes": 40, "easier": false}, …]}, …]}`, written by `TrainerPrompt.weeksPlanJson`
   when the plan has one — so it is also what the trainer is sent back (D125). The **goal** is a sibling key,
   `"goal": {"kind": "steady"}` or `{"kind": "distance", "sport": "run", "km": 15.0, "by": "2026-12-13"}`, written
   by `TrainerResponse.encodeStoredWeeksPlan(plan, goal)` and read by `readGoal` (release 1's `for_planned`
   pattern). A row written before this release has neither key: goal null, outline null, read exactly as now. The
   `weeks` column keeps the plan's length — any number of weeks for a plan with a goal. A key present but
   malformed makes the row unreadable, as a malformed plan is today.
2. **The goal.** `PlanGoal.Steady` or `PlanGoal.Distance(sport, km, byEpochDay)`; `GoalSport` RUN, WALK, SWIM, RIDE
   (ride is a cycle, `WorkoutKind.CYCLE`). The form's date is chosen in whole weeks, 6 to 30, by − and +: the Sunday
   of the week that many weeks after this one ("by Sun 13 Dec · 14 weeks", invented). Km is typed, one decimal at
   most, more than 0 and at most 1000. The plan's length follows: steady, 4 weeks; a distance, its first Monday to
   the goal's week. Wording: "15 km run · by Sun 13 Dec · 14 weeks to go" ("1 week to go", "goal week" in that
   week, "goal date passed" after), and "Keep a steady rhythm".
3. **The outline's checks (D120).** The blocks' weeks add up to exactly the plan's weeks (a steady plan: one block
   of four); each target's longest is in D94's session bounds (5–180 min), and its total is at least its longest
   and at most sessions-a-week × 180. Anything else is unreadable (D94). Phases are sent by name; any phase is
   accepted in any plan (the conventions are advice, not checks).
4. **Two weeks filled.** A new plan's reply carries sessions for its **first two weeks** (one when it has one); the
   phone pads the rest with empty weeks. Kept Monday to Thursday that is this week and the next, as the spec says;
   kept Friday to Sunday or as a replacement it is the plan's weeks 1 and 2 — the nearest reading where the plan
   has no "this week". A plan without an outline is untouched by the rule.
5. **Fill in next week / Plan this week (D121).** Offered for a plan with an outline, running (not the old plan
   finishing its week, choice 13): **Plan this week** whenever this week has no sessions; else **Fill in next week**
   from Friday when next week exists and has none. The answer is **kept at once**, as a new version of the plan
   (the adjust chain: the version it fills stops today, ADJUSTED; the new one runs from the chain's start) — it
   only fills an empty week, so there is nothing to compare; Adjust is there to change it. The reply's sentence
   ("why") is shown under the button once; a changed target is kept in the outline. The Trainer page's main button
   reads **Plan this week** (whenever) or **Fill in next week** (on Sunday) and opens the plan page, where the ask
   is one tap beside its privacy line — the AI request is described where it is sent.
6. **Plan the next block.** A steady plan in its last week: the plan page offers it; kept at once like a fill — the
   new version has four more weeks, empty, and the block appended. The Trainer page's main button in a steady
   plan's last week reads **Plan the next block** (a steady plan does not end), opening the plan page; a distance
   plan's last week, and a plan without an outline, keep **Plan ahead** (D124).
7. **Adjust with an outline.** The reply is this week's sessions still to do and **next week's** (when there is
   one) — the filled weeks — and `targets`: one per week from this one to the end, which replace the outline's
   targets (phases and focus kept). A plan without an outline is asked and composed exactly as now (`targets`
   empty and ignored). Adjust is not offered while an old plan finishes its week (choice 13).
8. **The Month view (D123).** The goal line; one row per block — "Base · Easy rhythm" (invented) — with its weeks
   as bars side by side, each the height of its target's minutes against the plan's largest target or done figure.
   A past week's done minutes (every visible, counted session of that week, the plan's or not — the target is the
   week's total) are drawn over its target in the green; an easier week's target is drawn lighter; this week's bar
   has "you are here" under it. Each bar's figures are said in words under the row: "Wk 3 · 150 min, 120 done"
   (invented), so nothing is only a colour. A plan without an outline: "This plan has no outline — plan ahead to set
   a goal." The answer to Plan ahead draws the Month view above its weeks (D124: "now with the Month view's outline
   above the weeks"). Colours are the theme's own: the target bar `outline`, the easier bar `outlineVariant`, done
   `primary` — no new colour, so no new contrast to measure; the words carry every figure.
9. **The Week view's targets.** A week with an outline target and no sessions — this week waiting for Plan this
   week, or a week ahead — reads "about 150 min · longest 60′ · easier week" (invented) in place of its list.
10. **What is sent (D125).** Every plan question (evaluate, adjust, fill, next block) carries `goal` (null for none)
    with, for a distance, `weeks_to_go`; the plan sent carries its `outline`; adjust, fill and next block carry
    `filled_weeks` (week numbers with sessions). The instructions gain `CONVENTIONS` — "rules of thumb from
    endurance coaching, not measured standards": blocks of about four weeks with one focus; base, build, peak, then
    a one-to-two-week taper before a race; weekly load rising by about a tenth; every fourth week easier; most
    sessions easy — and the rule that a week's sessions sum near its target unless the week's ledger argues
    otherwise, when it says why.
11. **Limit 1 — a review's steps.** A new review is offered the steps kept for the planned session the workout
    ticked (its place in the plan on the card — running or ended, D96), when there are any; otherwise, as today, the
    newest kept suggestion of the seven days before it.
12. **Limit 2 — steps through a new version.** Whenever a new version of a plan is kept (Adjust, a fill, the next
    block), every planned session of the old version is matched to one of the new version's same week, in order:
    identical (kind, minutes, effort), then same kind and effort, then same kind; each new session taken once.
    Steps follow their session's match (`TrainerStore.moveSlot`, rewriting the stored suggestion's
    `for_planned`); steps whose session has no match are un-kept — the session they were for is gone. Done after
    the keep, outside its transaction (two stores); a failure there leaves steps where they were and is logged.
13. **Limit 3 — a replaced plan counts through Sunday.** Keeping a plan while one runs stops the old one on the day
    before the new one's first Monday (its Sunday), REPLACED, not today. Until then the old plan is **in effect**
    (`Programmes.inEffect`): the Trainer page's card, its ticks, Plan my next session's planned session and the
    plan page are the old plan's, with `replacedFrom` set — its weeks after that Sunday are not drawn, the plan
    page says "Your new plan starts Mon 7 Sep." (invented), and Adjust and Stop are not offered (the plan being
    replaced has no week left to change; the new plan can be adjusted from its Monday). The week's ledger counts
    the old plan to Sunday (it already covers by stop day). Keeping yet another plan in that window replaces the
    waiting one, never the finishing one.
14. **The workbench (D106).** Its Plan ahead test sends a steady-rhythm goal with its sessions a week and words; the
    distance goal, Fill in next week and Plan the next block are not on the workbench (not built here).
15. **Plan ahead's question starts where keeping would.** `starts` is next Monday while a plan runs (release 1 kept
    it there but still told the trainer D94's Monday); the goal's weeks count from it.

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` holds the one lock. **Never run bare
`./gradlew`.** **Before every Gradle command run `free -m`;** under about 4000 MB available, wait and check
again. One Gradle command at a time, never in parallel, never `gradle --stop`. Never pipe a build; redirect
and read the exit code:

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > "$SCRATCH/ms-plan.log" 2>&1; echo "exit $?"
```

Stage by explicit path; **never `git add -A`**. Commit trailer: `Co-Authored-By: Claude Opus 5.5
<noreply@anthropic.com>` and **no** session line.

Fixture rule (CLAUDE.md): every figure here is invented and round; the standard day is `TEST_EPOCH_DAY`
(Thursday 3 September 2026), whose Monday is `TEST_EPOCH_DAY - 3` (31 August). The goal fixture: **15 km run by
Sunday 13 December 2026** (`TEST_EPOCH_DAY + 101`), 14 weeks to go, a 15-week plan from 31 August in blocks of
4 (base), 4 (build), 5 (peak) and 2 (taper) weeks.

---

### Task 1: Goal and outline — the model and its storage

**Files:**
- Modify: `domain/trainer/Programme.kt`, `data/ai/TrainerResponse.kt`, `data/ai/TrainerPrompt.kt` (`weeksPlanJson`),
  `data/trainer/ProgrammeStore.kt`
- Test: `domain/trainer/OutlineTest.kt` (new), `data/trainer/ProgrammeMappingTest.kt` (or the existing mapping test)

- [ ] **Step 1: Write the failing tests.**
  - `OutlineTest`: `covers(15)` for the fixture, not for 14 or 16; a steady outline covers 4 and nothing else;
    `fits(perWeek = 3)` refuses a longest of 4 or 181, a total under its longest, a total over 540; `targets`
    flattens in order; `withTargetsFrom(index, targets)` keeps phases and focus; `plus(block)`.
  - `ProgrammeAsk`: a goal-less ask still takes only 2, 4 or 6 weeks; with a goal, any number from 1;
    `PlanAheadAsk(Distance(RUN, 15.0, TEST_EPOCH_DAY + 101), 3).toAsk(start = TEST_EPOCH_DAY - 3)` has 15 weeks,
    and from next Monday (`TEST_EPOCH_DAY + 4`) 14; `Steady` has 4.
  - Mapping: a programme with a goal and an outline reads back equal; an older row (no keys) reads back with goal
    and outline null and its JSON unchanged; a row whose outline is malformed is unreadable.

- [ ] **Step 2: Run them to see them fail.**

- [ ] **Step 3: Implement** the types, `WeeksPlan.outline: Outline? = null` (last, defaulted), `ProgrammeAsk.goal:
  PlanGoal? = null`, `weeksPlanJson` writing the outline when present, `readWeeksPlan` reading it, `encodeStoredWeeksPlan`
  / `readGoal`, and the store's mapping.

- [ ] **Step 4: Run them to see them pass**, then `--tests "*trainer*"`.

- [ ] **Step 5: Commit** — `feat(trainer): a plan's goal and outline, stored in its JSON (D119, D120)`.

---

### Task 2: Release 1's three limits

**Files:**
- Modify: `domain/trainer/PlanProgress.kt` (`placeOf`, `Programmes.inEffect`), `domain/trainer/TrainerHome.kt`
  (`PlanCard.of` with a finishing plan, `Running.replacedFrom`), `domain/trainer/StepsCarry.kt` (new),
  `data/trainer/ProgrammeStore.kt` (+ fake), `data/trainer/TrainerStore.kt` + `TrainerDao.kt` (+ fake: `moveSlot`),
  `data/trainer/AskTheTrainer.kt`, `ui/screen/trainer/ReviewSessionViewModel.kt`, `ui/screen/trainer/TrainerViewModel.kt`
- Test: `StepsCarryTest` (new), `PlanCardTest`/`TrainerHomeTest`, `AskTheTrainerTest`, `RoomProgrammeStoreWritesTest`,
  `ReviewSessionViewModelTest`, `TrainerViewModelTest`

- [ ] **Step 1: Write the failing tests.**
  - `StepsCarryTest`: a week [easy walk 30 (open), steady walk 40 (done)] adjusted to [steady walk 40, easy walk 30]
    moves the first's place 0 → 1 and the second's 1 → 0; a session changed in minutes keeps its steps by kind and
    effort; a session removed has none (`null`); other weeks are identity.
  - `AskTheTrainerTest`:
    - *limit 2*: steps kept for this week's open session, then an adjustment that puts the ticked session first and
      is kept — the steps are at the open session's new place, `planShown().steps` shows them there.
    - *limit 3*: with a plan running, a new one kept on Thursday — the old plan stops on Sunday (`MONDAY + 6`),
      REPLACED; `running()` is the old plan with `replacedFrom == MONDAY + 7` and its open session as `next`; a
      session on Saturday ticks the old plan; on the next Monday `running()` is the new plan in its week 1.
    - *limit 1*: `stepsFor(workout)` is the steps kept for the session the workout ticked, not a newer
      suggestion kept for another session; a workout that ticked nothing gets the newest kept of the seven days.
  - `RoomProgrammeStoreWritesTest`: keeping from next Monday writes `replaceRunning(4, <Sunday>)`.
  - `ReviewSessionViewModelTest`: a new review opens with the ticked session's steps.
  - `TrainerViewModelTest`: in the window the card is the old plan's, its main action the session action.

- [ ] **Step 2: Run them to see them fail.**

- [ ] **Step 3: Implement** (choices 11–13). `ProgrammeStore.keep` stops the old plan on `maxOf(today, start - 1)`.
  `Programmes.inEffect(all, today)`: the RUNNING version once begun; before it begins, the newest REPLACED version
  begun by today and stopping on or after today, else the RUNNING one. `PlanCard.of` accepts that finishing
  version (its last counted day its stop day) as `Running(…, replacedFrom = stop + 1)`. `AskTheTrainer.card()` and
  `TrainerViewModel` read the plan in effect; `adjust()` answers `NotRunning` in the window. `PlanTimeline.of`
  leaves out a finishing version's weeks after its stop day. `keepAdjusted` (and every version kept, Task 3) carries
  steps; `stepsFor(workout)` serves the review form.

- [ ] **Step 4: Run them to see them pass**, then `--tests "*trainer*"`.

- [ ] **Step 5: Commit** — `fix(trainer): a review gets its session's steps; steps follow an adjustment; a replaced plan counts to Sunday (D122, D124)`.

---

### Task 3: The asks — Plan ahead with a goal, Fill in a week, the next block, Adjust with an outline; what is sent

**Files:**
- Modify: `domain/trainer/TrainerRequest.kt`, `domain/trainer/TrainerPath.kt`, `data/ai/TrainerPrompt.kt`,
  `data/ai/TrainerResponse.kt`, `data/ai/OpenAiTrainer.kt`, `data/trainer/AskTheTrainer.kt`,
  `data/trainer/TrainerWorkbench.kt`, test `FakeTrainer`
- Test: `TrainerPromptTest`, `TrainerResponseTest`, `AskTheTrainerTest`, `OpenAiTrainerTest`

- [ ] **Step 1: Write the failing tests.**
  - `TrainerResponseTest`: a goal evaluation reply with two weeks and an outline of 15 weeks parses to a 15-week plan,
    weeks 3–15 empty, outline kept; refused when the outline covers 14 or 16 weeks, a target is out of bounds, or
    three weeks carry sessions; a steady reply needs exactly four. A fill reply: sessions 1..perWeek, its target in
    bounds. A next-block reply: four targets. An adjust reply for an outline plan: two weeks and `targets` for every
    remaining week.
  - `TrainerPromptTest`: the evaluate question carries `goal` (`kind`, `sport`, `km`, `by`, `weeks_to_go`) and the
    sent plans their `outline`; adjust, fill and next block carry `filled_weeks`; the instructions of the four plan
    paths contain the conventions paragraph and its label "not measured standards", and the sum-near-target rule;
    feedback and plan-my-next-session do not.
  - `AskTheTrainerTest`: `planAhead` with a distance goal asks with 15 weeks and keeps the composed plan; while a plan
    runs `starts` is next Monday; `fillWeek()` on Friday fills next week as a kept version and keeps the
    changed target; `nextBlock()` in a steady plan's last week adds four weeks; adjust on an outline plan replaces
    the filled weeks and the targets.

- [ ] **Step 2: Run them to see them fail.**

- [ ] **Step 3: Implement** (choices 2–7, 10, 15). `TrainerQuestion.FillWeek`, `NextBlock`; `Trainer.fill`,
  `nextBlock`; `TrainerPath.FILL_WEEK`, `NEXT_BLOCK` and `TrainerPath.WORKBENCH`; prompt texts and schemas;
  `AskTheTrainer.planAhead`, `planAheadQuestion`, `fillWeek`, `nextBlock`, `fillQuestion`, `nextBlockQuestion`.

- [ ] **Step 4: Run them to see them pass**, then `--tests "*trainer*" --tests "*Workbench*"`.

- [ ] **Step 5: Commit** — `feat(trainer): plan ahead to a goal; fill in a week; plan the next block; the trainer is told the outline (D119-D121, D124, D125)`.

---

### Task 4: What the pages show — Month, targets, the offers, the words

**Files:**
- Create: `domain/trainer/PlanFilling.kt`, `domain/trainer/PlanMonth.kt`, `ui/trainer/GoalWording.kt`
- Modify: `domain/trainer/PlanTimeline.kt`, `domain/trainer/TrainerHome.kt` (`MainAction`), `data/trainer/AskTheTrainer.kt`
  (`planShown`'s month and offer), `ui/trainer/PlanTimelineWording.kt`, `ui/trainer/PlanPageWording.kt`
- Test: `PlanFillingTest`, `PlanMonthTest`, `GoalWordingTest` (new), `PlanTimelineTest`, `TrainerHomeTest`,
  `PlanTimelineWordingTest`, `AskTheTrainerTest`

- [ ] **Step 1: Write the failing tests.**
  - `PlanFillingTest`: Monday to Thursday nothing for next week; Friday "Fill in next week"; this week empty "Plan
    this week" any day; a plan without an outline never; the last week of a steady plan "Plan the next block", of a
    distance plan nothing; a finishing plan nothing.
  - `TrainerHomeTest`: main is `PlanThisWeek` when this week is empty; `FillNextWeek` only on Sunday; `NextBlock` in a
    steady plan's last week; `PlanAhead` in a distance plan's last week; the session action in a finishing plan.
  - `PlanMonthTest`: the fixture in week 6 — four blocks of 4, 4, 5, 2 bars; weeks 1–5 carry done minutes (the week's
    visible counted sessions), week 6 is here, later weeks neither; easier weeks marked; no outline, no month.
  - `PlanTimelineTest`: weeks 3–15 carry their targets; an empty this week has its target.
  - Wording: "15 km run · by Sun 13 Dec · 14 weeks to go", "1 week to go", "goal week", "Keep a steady rhythm";
    "about 150 min · longest 60′ · easier week"; "Base · Easy rhythm"; "Wk 3 · 150 min, 120 done"; "you are here".

- [ ] **Step 2: Run them to see them fail.**

- [ ] **Step 3: Implement** (choices 5, 6, 8, 9).

- [ ] **Step 4: Run them to see them pass.**

- [ ] **Step 5: Commit** — `feat(trainer): the Month view's model, a week's target, and when a week is filled in (D121, D123)`.

---

### Task 5: The view models

**Files:**
- Modify: `ui/screen/trainer/EvaluatePlanViewModel.kt`, `ui/screen/settings/TrainerWorkbenchViewModel.kt`
- Test: `EvaluatePlanViewModelTest`, `TrainerWorkbenchViewModelTest`

- [ ] **Step 1: Write the failing tests.** The form opens on a steady goal, 3 a week, ready; a distance goal is not
  ready until km is typed; − and + move the date within 6 to 30 weeks; asking sends `PlanAheadAsk`. On the plan
  page: `fill()` asks once, then reads the plan again and shows the reply's sentence; a failure is said; `nextBlock()`
  likewise. The workbench's Plan ahead sends a steady goal.

- [ ] **Step 2: Run them to see them fail.**

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run them to see them pass.**

- [ ] **Step 5: Commit** — `feat(trainer): the goal form, fill in and the next block, in the view models (D119, D121, D124)`.

---

### Task 6: The screens and the navigation

**Files:**
- Create: `ui/screen/trainer/PlanMonthView.kt`
- Modify: `EvaluatePlanScreen.kt`, `PlanTimelineView.kt`, `TrainerScreen.kt`, `ui/screen/settings/TrainerWorkbenchPage.kt`,
  `ui/nav/MetaSelfNavHost.kt`, `res/values/strings.xml`
- Test: `EvaluatePlanScreenRenderTest`, `TrainerScreenRenderTest`, `TrainerWorkbenchPageRenderTest`

- [ ] **Step 1: Write the failing tests.** The form: GOAL with its two choices, the distance rows when chosen, no
  HOW MANY WEEKS. Month: the goal line, the block rows, their words and "you are here"; a plan without an outline
  says so. Week: a week ahead's target line; "Your new plan starts …" in the window. The buttons: Fill in next week
  (Friday), Plan this week, Plan the next block, each with the privacy line; Adjust absent in the window. The answer
  draws the Month above its weeks. The Trainer page's three new main buttons open the plan page.

- [ ] **Step 2: Run them to see them fail.**

- [ ] **Step 3: Implement.**

- [ ] **Step 4: Run them to see them pass** — `--tests "*trainer*" --tests "*Workbench*"`.

- [ ] **Step 5: Commit** — `feat(trainer): goals and blocks — the goal form, the Month view, fill in and the next block (D119-D125)`.

---

### Task 7: 0.78.0

**Files:** Modify `app/build.gradle.kts` (`versionCode = 134`, `versionName = "0.78.0"`).

- [ ] **Step 1:** Bump the version.
- [ ] **Step 2:** Run once each, one at a time, each redirected to its own log with `$?` read:
  `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`. Expected: tests pass; skipped are exactly
  the ten Room classes CLAUDE.md lists; lint clean; the debug APK builds.
- [ ] **Step 3: Commit** — `0.78.0: goals and blocks (D119-D125)`.

---

## Not built here

- Days for planned sessions, reminders, pace targets, a race-result prediction, strength sets (the spec's own list).
- The workbench tries Plan ahead with a steady goal only; Fill in next week and Plan the next block are not on it.
- The phone does not enforce the conventions (load rising by a tenth, an easier fourth week, the taper): they are
  told to the trainer as conventions, as the spec says.
- A fill or a next block is kept at once, with no "Keep this / Keep the old one" step (choice 5).
- No release build, push or pull request in this run.

## Phone checks for the owner

1. Plan ahead: the form asks for a goal, sessions a week and words — no weeks. Choose a distance by a date, ask, and
   the answer shows the Month view (blocks, bars) above the weeks; only the first two weeks list sessions, later
   ones their target.
2. Keep it; on the plan page, Month shows the goal line and "you are here" on this week.
3. On a Friday, the plan page offers Fill in next week; on Sunday it is the Trainer page's main button. Tap it: next
   week gets sessions.
4. With a plan running, keep a new one mid-week: the Trainer page still shows the old plan's open sessions until
   Sunday; the plan page says when the new plan starts; on Monday the new plan is there.
5. Keep steps for an open session, then Adjust and keep the new version: the steps are still on that session.
6. Review a session that ticked a planned session with steps: the review names those steps.
7. A plan kept before this version still draws exactly as before; its Month view says it has no outline.
