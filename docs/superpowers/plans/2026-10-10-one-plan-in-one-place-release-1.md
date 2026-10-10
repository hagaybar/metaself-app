# One place for sessions — Implementation Plan (release 1 of D119–D125: D122, D123's Week and Session, D124's "always")

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** release 1 of `docs/superpowers/specs/2026-10-10-one-plan-in-one-place-design.md`. Plan my next session,
while a weekly plan runs, is for one planned session, and **keeping it attaches its steps to that session**: the
plan screen's tile then reads "Steps ready", and the steps are shown where the session is. A planned session holds
one set of steps; keeping another replaces them; feedback on a session no longer throws them away. The plan screen
opens with a switch, **Month · Week · Session**, Week first: Week is the timeline of D117; Session lists the
sessions coming up — this week's open ones, then next week's — each a card that opens to its steps or offers **Get
the steps**, with this week's done ones folded above; with no plan running, the kept single session is its one
card, **Next session**, for seven days. Month says "This plan has no outline — plan ahead to set a goal." until
release 2. The Trainer page's "Your kept plan" card goes. **Evaluate me and plan the weeks ahead becomes Plan
ahead**, everywhere; it is on the plan screen always, and the Trainer page's main button reads Plan ahead when no
plan runs or the plan ends this week. Keeping a new plan while one runs asks first — "This replaces your current
plan from Mon 7 Sep." — and the new plan starts next Monday. Version 0.77.0.

**Decision:** the owner's, 2026-10-10 — the spec above (D122, D123, D124; release 1 of its "Built in two
releases"). Branch `one-plan-in-one-place`, already checked out on top of the unmerged plan-timeline branch —
**do not switch branches**. **No Room schema change** (choice 1).

**Architecture:**

```
domain/trainer/Trainer.kt          SessionSlot, PlanPlace; TrainerPlan.slot                           JUnit 5   Task 1
data/ai/TrainerResponse.kt         encodeStoredPlan(plan, slot), readSlot(content)                    JUnit 5   Task 1
data/trainer/TrainerStore.kt       mapping with the slot; keep() replaces only its own place;
                                   keptPlans(); observeKeptPlan() removed in Task 5                   JUnit 5   Task 1
data/trainer/TrainerDao.kt         keptPlans() (a query, no schema change)                                       Task 1
domain/trainer/PlanTimeline.kt     PlanTile.stepsReady, PlanTimeline.withSteps, PlanShown.steps       JUnit 5   Task 2
data/trainer/AskTheTrainer.kt      suggest(answers, slot), nextFor(), plannedAt(), nextSession(),
                                   replacingFrom(), keepProgramme from next Monday, planShown's steps,
                                   feedback leaves attached steps                                     JUnit 5   Task 2
domain/trainer/PlanSessions.kt     NEW, pure: the Session view's cards                                JUnit 5   Task 3
domain/trainer/TrainerHome.kt      MainAction.PlanAhead (was Evaluate) and its rule; keptPlan goes    JUnit 5   Task 3
ui/trainer/PlanPageWording.kt      NEW: the switch, Month's line, Session's words, Plan ahead,
                                   the replace sentence, the kept lines                               JUnit 5   Task 3
ui/trainer/PlanTimelineWording.kt  "Steps ready" (Task 3); EVALUATE_AGAIN goes (Task 5)               JUnit 5   Task 3
ui/trainer/TrainerWeekWording.kt   planRow: "Your plan" with no plan                                  JUnit 5   Task 3
ui/screen/trainer/EvaluatePlanViewModel.kt  view, nextSession, sessions, replacingFrom, confirming    JUnit 5   Task 4
ui/screen/trainer/PlanSessionViewModel.kt   opened for a place; the kept route goes                   JUnit 5   Task 4
ui/screen/trainer/TrainerViewModel.kt       no kept plan                                              JUnit 5   Task 5
ui/screen/trainer/PlanPageViews.kt          NEW: the switch, Session view, cards, StepsList           Robolectric Task 5
ui/screen/trainer/EvaluatePlanScreen.kt, PlanTimelineView.kt, PlanSessionScreen.kt, TrainerScreen.kt   Robolectric Task 5
ui/nav/MetaSelfNavHost.kt                   Get the steps, Plan ahead, the kept route gone                         Task 5
res/values/strings.xml, ui/trainer/WorkbenchWording.kt   Plan ahead                                               Task 5
app/build.gradle.kts                        0.77.0 / 133                                                           Task 6
```

**Tech Stack:** Kotlin 1.9.22, Compose (BOM 2024.02.00, Material 3). JUnit 5 + Truth for everything pure and for
the view models; JUnit 4 + Robolectric only for the Compose render tests, through `ComposeRender`.

---

## Design choices the spec leaves open — settled here

1. **Where the link is stored — no schema change.** A kept suggestion lives in `trainer_plans`, whose
   `suggestion` column already holds the suggestion's JSON. The link rides in that JSON as one more key,
   `"for_planned": {"chain": 7, "week": 1, "session": 0}` (invented), written by `TrainerResponse.encodeStoredPlan`
   and read by `readSlot`. `readPlan` already ignores keys it does not know, so a row written before this reads
   back unlinked, and one written by this version still reads as a plan to anything that only wants the plan. A
   backup copies the column whole, so the link survives a restore. A column would also have meant a migration whose
   test cannot run on this box (CLAUDE.md); the JSON key needs none.
2. **What the link names.** `SessionSlot(chainId, week, session)`: the chain's **first** version's id (as D105's
   answers are stored, so an adjusted version keeps its steps), the week's index from the plan's first Monday (an
   adjusted version keeps the chain's start, D97), and the session's index in that week. A new plan (Plan ahead)
   is a new chain, so steps for the old plan's sessions are shown nowhere afterwards.
3. **Which planned session Plan my next session is for.** Opened from the Trainer page or the plan screen's main
   action: the running plan's next open session this week (D96's) — `nextFor()`. Opened from **Get the steps**:
   that card's session — this week's or next week's (`plannedAt`); the form's line reads "For week 3 of your plan:
   easy walk, 30 min" (invented). With no plan running, or none open this week, or before week 1 from the Trainer
   page, it is for no planned session: kept, it is the **Next session**.
4. **Kept, many at once.** "At most one is kept" (D88) becomes **at most one per place**: keeping a suggestion
   un-keeps the kept one for the same planned session (or, unlinked, the kept unlinked one) and no other, in one
   transaction. `keptPlan()` — the newest kept, any place — stays what a review matches against (D87,
   `PlanMatch.forSession`), unchanged. **Feedback no longer un-keeps attached steps** (D122: they belong to the
   planned session, ticked or not); it still un-keeps an unlinked one, as today.
5. **Next session.** The newest kept unlinked suggestion made in the last seven days (`PlanMatch.offered`). Shown
   on the Session view whenever there is one (the spec names the no-plan case; a plan with nothing open this week
   can produce one too), first, above the planned sessions. With no plan to show, the Week view says "No plan is
   running." and draws the Next session card under it, so a kept session is never one tap further than before.
6. **Session view.** Only for a plan that runs (`PlanShown.running`): this week's open sessions, in the plan's
   order, under "THIS WEEK"; then next week's (the plan week whose Monday is next Monday — week 1 of a plan kept
   Friday to Sunday) under "NEXT WEEK". Each card: "Easy walk, 30 min" (kind, effort, minutes); with steps, "Steps
   ready" and a "Show the steps" fold (closed by default) opening the suggestion's title and steps; without, a
   **Get the steps** button. This week's done sessions sit above, folded under "Done this week · 2" (closed by
   default), each "Done Mon" or "Done Mon · 25 min", and their steps foldable if they have them. Nothing coming:
   "Nothing planned is coming up."
7. **"Steps ready" on the Week view.** An **open** tile of this week with steps reads "Steps ready" in place of
   "Open". A done tile keeps "Done Mon". A week ahead's one-line list is unchanged (the Session view shows next
   week's steps).
8. **The switch.** Three chips, Month · Week · Session, under the title; Week chosen when the screen opens; the
   choice is the view model's, so it survives rotation and is reset by leaving. **Month** (release 1): a plan shown
   — "This plan has no outline — plan ahead to set a goal."; none — "No plan is running."
9. **The buttons, under every view.** A running plan: Adjust the plan (outlined) beside the session action ("Plan
   the open walk" / "Plan my next session"), as D117; under them, **Plan ahead**, outlined, full width. Ended,
   stopped, or no plan: one filled **Plan ahead**. Plan ahead opens the current form (D93: weeks, sessions a week,
   words — release 2 changes it).
10. **The Trainer page's main button.** `MainAction.PlanAhead` when no plan runs, it has ended, **or this week is
    its last** (`weekIndex == weeks - 1`); then, under it, outlined, the session action — "Plan the open walk" when
    one is open, else "Plan my next session". The "Your plan" row is always there now — "Your plan" with no plan —
    because the plan screen is where Plan ahead and the Next session live.
11. **Replacing.** "While a plan runs" is `AskTheTrainer.running()` — a `PlanCard.Running`, before week 1
    included, not an ended one. Then Keep this plan first shows, in place of its button, "This replaces your
    current plan from Mon 7 Sep." with **Replace it** and **Not now**, drawn inline (a dialog is a window
    `ComposeRender` does not read); Replace it keeps it. **The new plan starts next Monday** (the spec's test list:
    "the replacement from the next Monday"; its D124 "from its first Monday" read as the new plan's first Monday,
    which is that one); the old plan stops today, REPLACED, as now — its sessions this week still count in the
    week's ledger to today (D110), and the rest of the week has no plan open. The answer is drawn from that Monday
    (D118). With no plan running, keeping is unchanged (D94: kept Monday to Thursday, this Monday; else the next).
12. **Plan ahead, renamed.** `trainer_evaluate` and `weeks_title` read "Plan ahead"; `EVALUATE_AGAIN` ("Evaluate
    me and plan again") goes — the ended or stopped plan's button is Plan ahead; the workbench's row for the same
    ask (D106) reads "Plan ahead" too.
13. **The kept route goes.** `PlanSession`'s `show=kept` was opened only by the Trainer page's kept card; the steps
    are on the Session view now, so the route, `PlanSessionViewModel`'s kept-opening and their tests go. The plan
    form's kept line says where it went: "Kept. Its steps are on your plan, with that session." or "Kept. It is on
    your plan for seven days, under Session."

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
(Thursday 3 September 2026), whose Monday is `TEST_EPOCH_DAY - 3` (31 August) and next Monday 7 September.

---

### Task 1: Steps carry their planned session (storage, no schema change)

**Files:**
- Modify: `domain/trainer/Trainer.kt`, `data/ai/TrainerResponse.kt`, `data/trainer/TrainerStore.kt`,
  `data/trainer/TrainerDao.kt`; test fake `data/trainer/FakeTrainerStore.kt`
- Test: `data/trainer/TrainerMappingTest.kt`, `data/trainer/RoomTrainerStoreWritesTest.kt`

- [ ] **Step 1: Write the failing tests.**

`TrainerMappingTest`:

```kotlin
    @Test
    fun `a plan kept for a planned session reads back with its place, and an older row with none`() {
        val linked = ENTITY.copy(suggestion = TrainerResponse.encodeStoredPlan(PLAN, SessionSlot(7, 1, 0)))

        assertThat(linked.toPlan()!!.slot).isEqualTo(SessionSlot(7, 1, 0))
        assertThat(linked.toPlan()!!.plan).isEqualTo(PLAN)
        assertThat(linked.toPlan()!!.toEntity()).isEqualTo(linked)
        assertThat(ENTITY.toPlan()!!.slot).isNull()
        assertThat(TrainerResponse.readPlan(linked.suggestion)).isEqualTo(PLAN)
    }
```

`RoomTrainerStoreWritesTest` (the proxy's `keptPlans` returns `kept`; `plan` returns the row asked for):

```kotlin
    @Test
    fun `keeping steps for a planned session un-keeps only that session's, in one transaction`() = runTest {
        kept = listOf(row(1, SessionSlot(7, 1, 0)), row(2, SessionSlot(7, 1, 1)), row(3, null))
        rows[4] = row(4, SessionSlot(7, 1, 0))

        store().keep(4)

        assertThat(log).containsExactly("begin", "plan", "keptPlans", "setKept(1,false)", "setKept(4,true)", "commit").inOrder()
    }

    @Test
    fun `keeping an unlinked suggestion un-keeps only the unlinked one`() = runTest {
        kept = listOf(row(1, SessionSlot(7, 1, 0)), row(3, null))
        rows[5] = row(5, null)

        store().keep(5)

        assertThat(log).containsExactly("begin", "plan", "keptPlans", "setKept(3,false)", "setKept(5,true)", "commit").inOrder()
    }
```

- [ ] **Step 2: Run them to see them fail** — `--tests "*TrainerMappingTest" --tests "*RoomTrainerStoreWritesTest"`:
  `SessionSlot`, `encodeStoredPlan`, `keptPlans` unresolved.

- [ ] **Step 3: Implement.**
  - `Trainer.kt`: `data class PlanPlace(val week: Int, val session: Int)` (both from 0) and
    `data class SessionSlot(val chainId: Long, val week: Int, val session: Int) { val place get() = PlanPlace(week, session) }`;
    `TrainerPlan` gains `val slot: SessionSlot? = null` (last, defaulted).
  - `TrainerResponse.encodeStoredPlan(plan, slot)`: `planJson(plan)` plus `for_planned` when `slot != null`;
    `readSlot(content)`: the three numbers, or null when absent or unreadable. `encodePlan` stays for callers
    that store no place.
  - `TrainerStore.kt`: `toPlan()` reads `slot = TrainerResponse.readSlot(suggestion)`; `toEntity()` writes
    `encodeStoredPlan(plan, slot)`. The interface gains `suspend fun keptPlans(): List<TrainerPlan>` (newest
    first); `observeKeptPlan()` stays until Task 5 takes the kept card away. `RoomTrainerStore.keep(planId)`: in one transaction, read the row's slot,
    un-keep each kept row of the same slot (null matching null) other than it, keep it.
  - `TrainerDao`: `keptPlans()` = `SELECT * FROM trainer_plans WHERE kept = 1 ORDER BY createdAtMillis DESC`;
    `unkeepAll()` goes (no caller left); `observeKept()` goes in Task 5 with its last caller.
  - `FakeTrainerStore`: the same rules.

- [ ] **Step 4: Run them to see them pass**, then `--tests "*trainer*"`: nothing else changes yet.

- [ ] **Step 5: Commit** — `feat(trainer): kept steps carry the planned session they are for (D122)`.

---

### Task 2: The trainer attaches, keeps and shows steps; replacing starts next Monday

**Files:**
- Modify: `domain/trainer/PlanTimeline.kt`, `data/trainer/AskTheTrainer.kt`
- Test: `domain/trainer/PlanTimelineTest.kt`, `data/trainer/AskTheTrainerTest.kt`

- [ ] **Step 1: Write the failing tests.** In `AskTheTrainerTest` (the plan `WEEKS`, two weeks of two easy
  thirty-minute walks, kept from `MONDAY`, chain id = its id):

```kotlin
    @Test
    fun `keeping a suggestion attaches it to the next open session, and keeping another replaces it`() = runTest {
        val id = programmes.add(offered()); programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.plans += TrainerReply.Answered(PLAN, "a-model"); trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        val first = (ask().suggest(ANSWERS) as AskTheTrainer.Suggested.Planned).plan
        ask().keep(first.id)
        val second = (ask().suggest(ANSWERS) as AskTheTrainer.Suggested.Planned).plan
        ask().keep(second.id)

        assertThat(first.slot).isEqualTo(SessionSlot(id, 0, 0))
        assertThat(store.keptPlans().map { it.id }).containsExactly(second.id)
    }

    @Test
    fun `Get the steps attaches to the named session, this week's or next week's, and nothing further`() = runTest {
        val id = programmes.add(offered()); programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        assertThat(ask().plannedAt(SessionSlot(id, 1, 1))).isEqualTo(PlannedFor(SessionSlot(id, 1, 1), PlannedTick(2, 2, WALK_30)))
        assertThat(ask().plannedAt(SessionSlot(id, 2, 0))).isNull()
        assertThat(ask().plannedAt(SessionSlot(id + 1, 1, 1))).isNull()
        val plan = (ask().suggest(ANSWERS, SessionSlot(id, 1, 1)) as AskTheTrainer.Suggested.Planned).plan
        assertThat(plan.slot).isEqualTo(SessionSlot(id, 1, 1))
        assertThat((trainer.asked.single().question as TrainerQuestion.Plan).planned).isEqualTo(PlannedTick(2, 2, WALK_30))
    }

    @Test
    fun `feedback leaves steps that belong to a planned session`() = runTest { /* keep attached, save with it → still kept */ }

    @Test
    fun `the next session is the kept unlinked suggestion of the last seven days`() = runTest { /* 7 days, 8 days, linked none */ }

    @Test
    fun `the plan shown carries its chain's steps, and an open tile with steps is ready`() = runTest { /* … */ }

    @Test
    fun `while a plan runs, keeping a new one starts it next Monday and the old one stops today`() = runTest {
        val old = programmes.add(offered()); programmes.keep(old, MONDAY, TEST_EPOCH_DAY)
        val new = programmes.add(offered())

        assertThat(ask().replacingFrom()).isEqualTo(MONDAY + 7)
        assertThat(ask().keepProgramme(new)).isEqualTo(MONDAY + 7)
        assertThat(programmes.rows.value.first { it.id == old }.let { it.status to it.stoppedEpochDay })
            .isEqualTo(ProgrammeStatus.REPLACED to TEST_EPOCH_DAY)
        assertThat(ask().replacingFrom()).isEqualTo(MONDAY + 7) // the new one runs, not yet begun
    }
```

  `PlanTimelineTest`: `withSteps(setOf(PlanPlace(1, 1)))` marks week 2's second tile `stepsReady` and nothing else.

- [ ] **Step 2: Run them to see them fail.**

- [ ] **Step 3: Implement.**
  - `PlanTimeline.kt`: `PlanTile.stepsReady: Boolean = false`; `PlanTimeline.withSteps(places: Set<PlanPlace>)`
    marks this week's tiles at those places; `PlanShown` gains `steps: Map<PlanPlace, TrainerPlan> = emptyMap()`.
  - `AskTheTrainer`:
    - `data class PlannedFor(val slot: SessionSlot, val tick: PlannedTick)`;
    - `nextFor()`: the running card's next open session this week, with its place (`weekIndex`, the first
      unticked tick's index) under the chain's root id;
    - `plannedAt(slot)`: the running plan's session at that place when the chain is the running one's and the
      week's Monday is this Monday or next Monday; else null;
    - `suggest(answers, slot: SessionSlot? = null)`: `planned = slot?.let { plannedAt(it) } ?: nextFor()` when
      no slot is named, `plannedAt(slot)` when one is (a named place gone stale attaches to nothing); the question
      carries `planned?.tick`; the stored suggestion carries `planned?.slot`. `planQuestion(answers, slot)` is
      the one builder (the workbench calls it with none);
    - `save(...)`: un-keeps the plan only when it carries no slot;
    - `nextSession()`: `PlanMatch.offered(keptPlans().firstOrNull { it.slot == null }, now())`;
    - `replacingFrom()`: `running()?.let { MovementWeek.mondayOf(today) + 7 }`; `keepProgramme` starts on it when
      it is not null, else `ProgrammeCalendar.startFor(today)`;
    - `planShown()`: the chain's kept steps by place; the timeline `withSteps(their places)`.

- [ ] **Step 4: Run them to see them pass** — `--tests "*AskTheTrainerTest" --tests "*PlanTimelineTest"`.

- [ ] **Step 5: Commit** — `feat(trainer): steps attach to their planned session; a new plan replaces from next Monday (D122, D124)`.

---

### Task 3: The Session view's model, Plan ahead, and the words

**Files:**
- Create: `domain/trainer/PlanSessions.kt`, `ui/trainer/PlanPageWording.kt`
- Modify: `domain/trainer/TrainerHome.kt`, `ui/trainer/PlanTimelineWording.kt`, `ui/trainer/TrainerWeekWording.kt`,
  `ui/trainer/ProgrammeWording.kt`
- Test: `domain/trainer/PlanSessionsTest.kt` (new), `domain/trainer/TrainerHomeTest.kt`,
  `ui/trainer/PlanTimelineWordingTest.kt`, `ui/trainer/TrainerWeekWordingTest.kt`, `ui/trainer/PlanPageWordingTest.kt` (new)

- [ ] **Step 1: Write the failing tests.**
  - `PlanSessionsTest`: for a four-week plan in week 2 with one session done — `done` is that one with its day;
    `thisWeek` the open ones in plan order with their places and steps; `nextWeek` week 3's; a plan before week 1
    starting next Monday has week 1 as `nextWeek`; an ended, stopped or offered plan (no `running`) has neither,
    only `next`.
  - `TrainerHomeTest`: `MainAction.of` is `PlanAhead` with no card, an ended card, and a running card in its last
    week; `PlanOpen` / `PlanNext` otherwise.
  - `PlanTimelineWordingTest`: an open tile with steps reads "Steps ready".
  - `TrainerWeekWordingTest`: `planRow(PlanCard.None)` is "Your plan".
  - `PlanPageWordingTest`: "This replaces your current plan from Mon 7 Sep."; "Done this week · 2"; "Done Mon ·
    25 min"; the two kept lines; "For week 2 of your plan: easy walk, 30 min".

- [ ] **Step 2: Run them to see them fail.**

- [ ] **Step 3: Implement** `PlanSessions` (`SessionCard(place, planned, steps, day, minutes)`,
  `PlanSessions(done, thisWeek, nextWeek, next)`, `of(shown: PlanShown?, today: Long, next: TrainerPlan?)`),
  `MainAction.PlanAhead` (replacing `Evaluate`; `TrainerScreen`'s `when` renamed with it, nothing drawn changes
  until Task 5) and the rule; the wording. `PlanTimelineWording.EVALUATE_AGAIN` goes in Task 5 with its caller.

- [ ] **Step 4: Run them to see them pass.**

- [ ] **Step 5: Commit** — `feat(trainer): the Session view's cards and Plan ahead's rule (D123, D124)`.

---

### Task 4: The view models

**Files:**
- Modify: `ui/screen/trainer/EvaluatePlanViewModel.kt`, `ui/screen/trainer/PlanSessionViewModel.kt`
- Test: `EvaluatePlanViewModelTest`, `PlanSessionViewModelTest`

- [ ] **Step 1: Write the failing tests.**
  - Evaluate: opened on the plan, `view` is WEEK and `choose(SESSION)` changes it; `nextSession` is read with the
    plan (and with no plan); keeping while a plan runs first sets `confirming` and writes nothing, a second keep
    writes and the plan starts next Monday, `cancelKeep` clears it; with a plan running the answer is drawn from
    next Monday (`startIfKept`).
  - Plan session: opened with `week` and `session`, the form is pre-filled from that session, the line names it,
    and the ask carries its slot; `keptLine` follows the shown plan's slot; the kept-opening tests go.

- [ ] **Step 2: Run them to see them fail.**

- [ ] **Step 3: Implement.** `EvaluatePlanViewModel`: `enum class PlanView { MONTH, WEEK, SESSION }`; `State.view`,
  `nextSession`, `replacingFrom`, `confirming`; `State.sessions` (derived, `PlanSessions.of`); `startIfKept` uses
  `replacingFrom`; `choose(view)`, `cancelKeep()`; the form reads `replacingFrom()` once at opening (a failed read
  is logged and leaves it null — keeping still starts next Monday, the store decides). `PlanSessionViewModel`:
  arguments `WEEK`, `SESSION` (−1: none); `State.slot`, `State.chosen`; the kept-opening and `KEPT` go.

- [ ] **Step 4: Run them to see them pass** — `--tests "*ViewModelTest"` under `ui.screen.trainer`.

- [ ] **Step 5: Commit** — `feat(trainer): the plan page's views, Get the steps and the replace confirmation, in the view models (D122–D124)`.

---

### Task 5: The screens and the navigation

**Files:**
- Create: `ui/screen/trainer/PlanPageViews.kt`
- Modify: `EvaluatePlanScreen.kt`, `PlanTimelineView.kt`, `PlanSessionScreen.kt`, `TrainerScreen.kt`,
  `TrainerViewModel.kt`, `domain/trainer/TrainerHome.kt` (the `kept` argument and `keptPlan` go),
  `data/trainer/TrainerStore.kt` + `TrainerDao.kt` + the fake (`observeKeptPlan` / `observeKept` go),
  `ui/nav/MetaSelfNavHost.kt`, `res/values/strings.xml`, `ui/trainer/WorkbenchWording.kt`
- Test: `EvaluatePlanScreenRenderTest`, `PlanSessionScreenRenderTest`, `TrainerScreenRenderTest`,
  `TrainerViewModelTest`, `TrainerHomeTest` (its seven-day kept test goes: Task 2 tests the seven days as the Next
  session), `TrainerWorkbenchPageRenderTest`

- [ ] **Step 1: Write the failing tests.** The plan screen: the switch's three chips with Week selected, the
  timeline under it; Session: Next session, "Done this week · 1" closed then open, THIS WEEK / NEXT WEEK cards,
  "Steps ready" with "Show the steps" opening the steps, "Get the steps" calling back with its place; Month's one
  line; Plan ahead under every view, outlined beside Adjust while running and filled alone when ended, stopped or
  absent; no plan: "No plan is running." with the Next session card. The answer: with `replacingFrom`, Keep this
  plan shows the replace sentence and Replace it / Not now. The Trainer page: no "YOUR KEPT PLAN"; Plan ahead in
  its three cases with the session action outlined under it; "Your plan" with no plan. The plan form's kept lines.

- [ ] **Step 2: Run them to see them fail.**

- [ ] **Step 3: Implement**, and in `MetaSelfNavHost`: `PlanSession` becomes
  `trainer/plan?week={week}&session={session}` (both −1 by default), `forSession(week, session)`; the plan screen's
  `onGetSteps` and `onPlanAhead`; the Trainer page's `onOpenKept` goes. Strings: `trainer_evaluate` and
  `weeks_title` "Plan ahead"; `trainer_kept_plan`, `trainer_open_plan`, `plan_kept` go (wording moves to
  `PlanPageWording`).

- [ ] **Step 4: Run them to see them pass** — `--tests "*trainer*" --tests "*Workbench*"`.

- [ ] **Step 5: Commit** — `feat(trainer): one place for sessions — Month · Week · Session, Plan ahead always (D122–D124)`.

---

### Task 6: 0.77.0

**Files:** Modify `app/build.gradle.kts` (`versionCode = 133`, `versionName = "0.77.0"`).

- [ ] **Step 1:** Bump the version.
- [ ] **Step 2:** Run once each, one at a time, each redirected to its own log with `$?` read:
  `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`. Expected: tests pass; skipped are exactly
  the ten Room classes CLAUDE.md lists; lint clean; the debug APK builds.
- [ ] **Step 3: Commit** — `0.77.0: one place for sessions (D122-D124)`.

---

## Not built here (the spec's release 2, and this plan's)

- Goals (D119), outlines and blocks (D120), Fill in next week / Plan this week / Plan the next block (D121), the
  new Plan ahead form without weeks (D124's form), the Month view's bars (D123), and what the trainer is told of
  them (D125).
- A review matched to the steps of the planned session it ticked: a review still matches the newest kept
  suggestion of the seven days before it (D87), as today.
- Adjust the plan re-ordering this week's sessions (ticked first, D97) can move steps to a different session of
  the same week: the place is by index. Not handled.
- No release build, push or pull request in this run.

## Phone checks for the owner

1. With a plan running: Trainer → Plan the open walk → ask → Keep. Open Your plan: on Week, that session's tile
   reads "Steps ready"; on Session, its card shows "Steps ready" and "Show the steps" opens them.
2. Review that session with feedback: the steps are still there afterwards.
3. Session → a next-week card → Get the steps: the form names "week N of your plan"; keep; the card now has steps.
4. The Trainer page has no "Your kept plan" card; its main button reads Plan ahead in the plan's last week.
5. Your plan → Plan ahead → ask → Keep this plan: "This replaces your current plan from Mon …" (next Monday); Replace
   it; the plan screen then says "starts Mon …".
6. With no plan: Your plan is still on the Trainer page; a kept single session shows as Next session for a week.
