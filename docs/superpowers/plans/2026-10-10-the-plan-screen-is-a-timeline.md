# The plan screen is a timeline — Implementation Plan (D117, D118)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D117, D118. The screen "See the plan" opens stops being a page of text. Top to bottom: the plan's
title in the display face with "4 weeks · 28 Sep – 25 Oct · you're in week 2" under it; the plan's weeks as a
timeline — a past week a filled node with a tick and "2 of 2 planned · plus 4 of your own" (the last part from
the week's ledger, D110), this week an outlined node with one tile per planned session (done, done short, open,
drawn as the Trainer page draws planned sessions), a week ahead a small grey node with its sessions on one line;
then where you stood — the kept evaluation's headline under "WHERE YOU STOOD" and its date, with the rest folded
under "Read the trainer's full evaluation", closed by default; then two buttons, Adjust the plan and the Trainer
page's main action (D114), or, for a plan ended or stopped, one "Evaluate me and plan again". The answers to
Evaluate me and plan the weeks ahead (D93) and Adjust the plan (D97) are drawn with the same timeline before they
are kept, with their Keep / Ask again (Keep this version / Keep the old one) unchanged. Version 0.76.0.

**Decision:** the owner's, 2026-10-10 — `docs/superpowers/specs/2026-10-10-the-plan-screen-is-a-timeline-design.md`
(D117, D118). Branch `the-plan-screen-is-a-timeline`, already checked out — **do not switch branches**. No Room
change: the timeline is counted afresh from the record every time the screen is read, like the week strip (D114).

**Architecture:**

```
domain/trainer/PlanTimeline.kt   NEW, pure: PlanStanding, TileState, PlanTile, TimelinePlace, TimelineWeek,
                                 PlanTimeline(.of), DatedEvaluation, PlanShown                     JUnit 5   Task 1
ui/trainer/PlanTimelineWording.kt NEW: subtitle, week heading, past and ahead lines, tiles,
                                 "WHERE YOU STOOD", the fold's two labels, Evaluate again           JUnit 5   Task 2
ui/trainer/TrainerWeekWording.kt planRow for an ended plan                                          JUnit 5   Task 2
domain/trainer/TrainerHome.kt    MainAction.of — the Trainer page's main button, shared              JUnit 5   Task 3
domain/trainer/PlanProgress.kt   Programmes.evaluatedOf — the version that carries the evaluation    JUnit 5   Task 3
data/trainer/AskTheTrainer.kt    planShown(), timelineOf(), datedEvaluationOf(), planShownOf()       JUnit 5   Task 3
ui/screen/trainer/EvaluatePlanViewModel.kt  State.planShown, State.offered                          JUnit 5   Task 4
ui/screen/trainer/AdjustPlanViewModel.kt    State.offered                                           JUnit 5   Task 5
ui/screen/trainer/PlanTimelineView.kt       NEW: the title, timeline, tiles, the fold, the buttons  Robolectric Task 6
ui/screen/trainer/DashedOutline.kt          NEW: the open session's dashed outline, shared          Robolectric Task 6
ui/screen/trainer/WeeksPlanView.kt          deleted (FromTrainerLabel moves to PlanTimelineView.kt)            Task 6
ui/screen/trainer/EvaluatePlanScreen.kt, AdjustPlanScreen.kt, TrainerScreen.kt   drawn with it      Robolectric Task 6
ui/nav/MetaSelfNavHost.kt                   the plan screen's main action and Evaluate again                   Task 6
ui/trainer/ProgrammeWording.kt, res/values/strings.xml   wording only the old view used, removed               Task 6
app/build.gradle.kts                        0.76.0 / 132                                                        Task 7
```

**Tech Stack:** Kotlin 1.9.22, Compose (BOM 2024.02.00, Material 3). JUnit 5 + Truth for everything pure and for
the view models; JUnit 4 + Robolectric only for the Compose render tests (`EvaluatePlanScreenRenderTest`,
`AdjustPlanScreenRenderTest`, `TrainerScreenRenderTest`), through `ComposeRender`.

---

## Design choices the spec leaves open — settled here

1. **Which plan the screen shows.** The running plan; else, when none runs, one that **ended** in the last
   fourteen days (`PlanCard.Ended`, the window the Trainer page already uses) or was **stopped** in the last
   fourteen days (the newest `STOPPED` version whose stop day is within it); else "No plan is running." A
   replaced or adjusted version is never shown: a later version runs.
2. **How it is reached when ended.** The Trainer page's "Your plan" row (D114) was absent for an ended plan,
   because the plan screen showed only a running one. D117 draws the ended plan, so **the row is shown for an
   ended plan too: "Your plan · ended Sun 25 Oct"**, for the same fourteen days. A stopped plan has no row (the
   Trainer page's card is `None` once stopped); it is seen on the plan screen when Stop this plan returns there,
   and until the screen is left.
3. **Past, this week, ahead.** A running plan: weeks before today's are past, today's is this week, the rest
   ahead; before week 1 all are ahead; after its last day all are past. **Stopped:** a week whose Monday is on or
   before the stop day is past (the week it stopped in included, counted to the stop day); the rest are drawn
   ahead, greyed. "Under the stop" is read as *after it on the timeline*; no separate marker is drawn — the
   subtitle already says "stopped Wed 14 Oct".
4. **A past week's line.** "2 of 2 planned" is the plan's own count of that week (`PlanProgress`, which is
   exactly what the week's ledger counts its practice from, D110); "· plus 4 of your own" is the number of the
   ledger's sessions beyond the plan (`beyond.kinds`' sessions summed), shown only when above zero. A past week
   with no ledger read counts none of its own.
5. **Tiles.** "Easy 30′" — the effort and the minutes, as the mockup; the kind is not named (a plan's sessions
   are mostly one kind, and the week's heading carries its focus). Done: "Done Mon" (the ticking session's day);
   done short: "Done Mon · 25 min" (its minutes); open: "Open". Three to a row; a fourth or fifth wraps.
6. **Colours, mapped as the Trainer page mapped them.** Done tile and past node: the theme's `primary` with
   `onPrimary` text — **measured 5.00:1 light, 7.29:1 dark**, and `InkLadderTest` gains the assertion. Open tile:
   the strip's dashed `primary` outline (now `Modifier.dashedOutline`, shared with the strip's "To do" blocks)
   with ink text. This week's node: a `primary` ring. A week ahead: an `outlineVariant` dot, its heading and line
   in `onSurfaceVariant`. The line under a past week `primary`, every other `outlineVariant`. "— this week" in
   `onSurfaceVariant`. **The fold's label is ink, not green**: the theme's `primary` on the default card measures
   3.86:1, under the 4.5:1 floor (the same reason the strip's "40′" is ink); a `primary` arrow beside it carries
   the colour (non-text, 3:1).
7. **The fold.** One line, "Read the trainer's full evaluation" with a down arrow; opened, it reads "Hide the
   trainer's full evaluation" with an up arrow, and under it Going well., To work on., Since last time. (only when
   there is one) and Why. (the plan's own why). Its open state survives rotation (`rememberSaveable`) but not
   leaving the screen. **A plan with no evaluation shows neither the headline nor the fold** — so its why is not
   shown; the spec says neither, and an evaluate answer always has an evaluation, so this is an adjusted chain
   whose evaluation could not be found.
8. **"WHERE YOU STOOD · SUN 27 SEP"** — the day the evaluation was made (its version's `createdAtMillis` in the
   phone's zone, as the last evaluation sent to the trainer is dated, D98). An answer just asked for is dated
   today.
9. **The D4 label** "From the AI trainer · advice, not a measurement" stays drawn once, above the title — the
   title, focuses and evaluation are the trainer's words.
10. **The buttons.** Running (before week 1 included): Adjust the plan (outlined) and, beside it, the Trainer
    page's main action — `MainAction.of(card, card.next)`, now shared with `TrainerHome.main`: "Plan the open
    walk" (the open session's kind) or "Plan my next session"; both open the single-session form, as on the
    Trainer page. Ended or stopped: one filled "Evaluate me and plan again", opening the evaluate form. Stop this
    plan stays on the adjust page.
11. **An offered evaluation (D118).** Drawn as the timeline it would be if kept today: its start is the Monday
    keeping it today gives (D94); if that is this Monday, week 1 is this week with every session an open tile,
    else every week is ahead. Nothing is counted for an answer never kept, so the screen computes it without
    reading the record (`State.offered`, derived).
12. **An offered adjustment (D118).** Drawn as the version would be counted once kept: weeks already past as
    past (from the running plan's record, which they copy, D97), **this week's tiles counted** — a session already
    done this week shows done, not open (the version keeps this week's ticked sessions first, D97, so drawing them
    open would contradict the record) — the rest ahead. Its evaluation is its chain's. It is read once, with the
    answer (`AskTheTrainer.planShownOf`).
13. **The old view goes.** `WeeksPlanView` and `EvaluationView` are deleted, with `ProgrammeWording.span`,
    `planHeading`, `tickTitle`, `tickLine` and the string `weeks_where`, which only they used.
    `AskTheTrainer.evaluationOf` goes too: the plan screen was its only caller.

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

Read the log (`tail -60 "$SCRATCH/ms-plan.log"`). Stage by explicit path; **never `git add -A`**. Commit
trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and **no** session line.

Fixture rule (CLAUDE.md): every figure here is invented and round; the standard day is `TEST_EPOCH_DAY`
(Thursday 3 September 2026), whose Monday is `TEST_EPOCH_DAY - 3` (31 August).

---

### Task 1: The timeline's model (pure)

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/trainer/PlanTimeline.kt`
- Test: `app/src/test/java/com/metaself/app/domain/trainer/PlanTimelineTest.kt`

- [ ] **Step 1: Write the failing test.** Create `PlanTimelineTest.kt`:

```kotlin
package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/**
 * D117, D118: a weekly plan drawn as its weeks. The plan is four weeks from Monday 24 August 2026, so today,
 * Thursday 3 September ([TEST_EPOCH_DAY]), is in week 2; every session, plan and figure is invented, and round.
 */
class PlanTimelineTest {

    private val monday = TEST_EPOCH_DAY - 3
    private val start = monday - 7
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
    private val walk45 = PlannedSession(WorkoutKind.WALK, 45, PlannedEffort.STEADY, "Invented line")
    private val run20 = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Invented line")
    private val plan = WeeksPlan(
        "Invented plan",
        listOf(
            PlanWeek("Settle in", listOf(walk30, walk30)),
            PlanWeek("Add a run", listOf(walk30, walk45, run20)),
            PlanWeek("Hold it", listOf(walk30, walk45, run20)),
            PlanWeek("Take stock", listOf(walk30, walk45)),
        ),
        "Invented reason.",
    )
    private val kept = Programme(1, 0, ProgrammeAsk(4, 3), null, plan, "a-model", start, ProgrammeStatus.RUNNING)

    @Test
    fun `a running plan has its past weeks, this week and the weeks ahead`() {
        val timeline = timeline()

        assertThat(timeline.standing).isEqualTo(PlanStanding.InWeek(2))
        assertThat(timeline.weeks.map { it.place::class }).containsExactly(
            TimelinePlace.Past::class, TimelinePlace.ThisWeek::class, TimelinePlace.Ahead::class, TimelinePlace.Ahead::class,
        ).inOrder()
        assertThat(timeline.weeks.map { it.number to it.focus }).containsExactly(
            1 to "Settle in", 2 to "Add a run", 3 to "Hold it", 4 to "Take stock",
        ).inOrder()
        assertThat(timeline.weeks[2].place).isEqualTo(TimelinePlace.Ahead(listOf(walk30, walk45, run20)))
        assertThat(timeline.title).isEqualTo("Invented plan")
        assertThat(timeline.why).isEqualTo("Invented reason.")
        assertThat(timeline.lastDay).isEqualTo(start + 27)
        assertThat(timeline.over).isFalse()
    }

    @Test
    fun `before its first week every week is ahead`() {
        val later = kept.copy(startEpochDay = monday + 7)

        val timeline = PlanTimeline.of(later, monday + 7, TEST_EPOCH_DAY, progress(later, emptyList()), emptyMap())

        assertThat(timeline.standing).isEqualTo(PlanStanding.Before(monday + 7))
        assertThat(timeline.weeks.all { it.place is TimelinePlace.Ahead }).isTrue()
    }

    @Test
    fun `after its last day every week is past`() {
        val timeline = timeline(today = start + 28)

        assertThat(timeline.standing).isEqualTo(PlanStanding.Ended(start + 27))
        assertThat(timeline.weeks.all { it.place is TimelinePlace.Past }).isTrue()
        assertThat(timeline.over).isTrue()
    }

    @Test
    fun `stopped mid-way, the weeks it reached are past and the rest are drawn ahead`() {
        val stopped = kept.copy(status = ProgrammeStatus.STOPPED, stoppedEpochDay = monday + 1)

        val timeline = PlanTimeline.of(stopped, start, TEST_EPOCH_DAY, progress(stopped, emptyList()), emptyMap())

        assertThat(timeline.standing).isEqualTo(PlanStanding.Stopped(monday + 1))
        assertThat(timeline.weeks.map { it.place::class }).containsExactly(
            TimelinePlace.Past::class, TimelinePlace.Past::class, TimelinePlace.Ahead::class, TimelinePlace.Ahead::class,
        ).inOrder()
        assertThat(timeline.over).isTrue()
        // A version replaced or adjusted also has a stop day, but a later version runs: it is not "stopped".
        val replaced = kept.copy(status = ProgrammeStatus.REPLACED, stoppedEpochDay = monday + 1)
        assertThat(PlanTimeline.of(replaced, start, TEST_EPOCH_DAY, null, emptyMap()).standing).isEqualTo(PlanStanding.InWeek(2))
    }

    @Test
    fun `a past week counts its planned sessions done and, from its ledger, the sessions of its own`() {
        val sessions = listOf(
            session(1, start), session(2, start + 1), session(3, start + 2, WorkoutKind.SWIM), session(4, start + 3),
        )
        val ledger = WeekLedgers.of(start, TEST_EPOCH_DAY, sessions, emptyList(), emptyMap(), null, listOf(CountedPlan(kept, COUNTING)))

        val withLedger = PlanTimeline.of(kept, start, TEST_EPOCH_DAY, progress(kept, sessions), mapOf(start to ledger))
        val without = PlanTimeline.of(kept, start, TEST_EPOCH_DAY, progress(kept, sessions), emptyMap())

        assertThat(withLedger.weeks[0].place).isEqualTo(TimelinePlace.Past(done = 2, planned = 2, own = 2))
        assertThat(without.weeks[0].place).isEqualTo(TimelinePlace.Past(done = 2, planned = 2, own = 0))
        assertThat(timeline().weeks[0].place).isEqualTo(TimelinePlace.Past(done = 0, planned = 2, own = 0))
    }

    @Test
    fun `this week's tiles are done, done short or open, with the day of the session that ticked each`() {
        val short = session(5, monday, minutes = 20)
        val full = session(6, monday + 2, minutes = 45)
        val counting = PlanCounting(1, 0, mapOf(5L to true))
        val progress = PlanProgress.of(plan, start, listOf(short, full), TEST_EPOCH_DAY, counting)

        val timeline = PlanTimeline.of(kept, start, TEST_EPOCH_DAY, progress, emptyMap())

        assertThat(timeline.weeks[1].place).isEqualTo(
            TimelinePlace.ThisWeek(
                listOf(
                    PlanTile(walk30, TileState.DONE_SHORT, day = monday, minutes = 20),
                    PlanTile(walk45, TileState.DONE, day = monday + 2, minutes = 45),
                    PlanTile(run20, TileState.OPEN),
                ),
            ),
        )
    }

    /** D118: an answer not yet kept, which would start this Monday — nothing is counted for it. */
    @Test
    fun `an answer never kept has every session of this week open`() {
        val offered = Programme(2, 0, ProgrammeAsk(4, 3), null, plan, "a-model")

        val timeline = PlanTimeline.of(offered, monday, TEST_EPOCH_DAY, null, emptyMap())

        assertThat(timeline.standing).isEqualTo(PlanStanding.InWeek(1))
        assertThat(timeline.weeks[0].place).isEqualTo(TimelinePlace.ThisWeek(listOf(PlanTile(walk30, TileState.OPEN), PlanTile(walk30, TileState.OPEN))))
        assertThat(timeline.weeks.drop(1).all { it.place is TimelinePlace.Ahead }).isTrue()
    }

    private fun timeline(today: Long = TEST_EPOCH_DAY) = PlanTimeline.of(kept, start, today, progress(kept, emptyList(), today), emptyMap())

    private fun progress(programme: Programme, sessions: List<Workout>, today: Long = TEST_EPOCH_DAY) =
        PlanProgress.of(programme.plan, programme.startEpochDay!!, sessions, Programmes.countedUntil(programme, today), COUNTING)

    private fun session(id: Long, day: Long, kind: WorkoutKind = WorkoutKind.WALK, minutes: Int = 30) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 7 * HOUR, durationMinutes = minutes,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        val COUNTING = PlanCounting(1, 0, emptyMap())
    }
}
```

The past-week test's sessions: the walks on Monday and Tuesday tick week 1's two walks; the swim and the
Thursday walk tick nothing, so the ledger counts two of its own.

- [ ] **Step 2: Run it to see it fail** — `~/bin/gradlew-safe :app:testDebugUnitTest --tests "*PlanTimelineTest"`.
  Expected: compilation fails, `PlanTimeline` unresolved.

- [ ] **Step 3: Write the model.** Create `PlanTimeline.kt`:

```kotlin
package com.metaself.app.domain.trainer

/** D117: where the owner stands in a plan — the last part of the plan screen's subtitle. */
sealed interface PlanStanding {
    /** The plan starts on [start], after today. */
    data class Before(val start: Long) : PlanStanding

    /** Today is in week [number], counted from 1. */
    data class InWeek(val number: Int) : PlanStanding

    /** Its last day, [lastDay], is past. */
    data class Ended(val lastDay: Long) : PlanStanding

    /** Stopped on [day] (D97). */
    data class Stopped(val day: Long) : PlanStanding
}

/** D117: how one planned session of this week stands, as its tile shows it. */
enum class TileState { DONE, DONE_SHORT, OPEN }

/** D117: a planned session of this week; [day] and [minutes] are the session that ticked it, null while open. */
data class PlanTile(val planned: PlannedSession, val state: TileState, val day: Long? = null, val minutes: Int? = null)

/** D117: a week's place on the timeline. */
sealed interface TimelinePlace {
    /** [done] of [planned] planned sessions; [own], the week's sessions beyond the plan, from its ledger (D110). */
    data class Past(val done: Int, val planned: Int, val own: Int) : TimelinePlace

    /** One tile per planned session. */
    data class ThisWeek(val tiles: List<PlanTile>) : TimelinePlace

    /** A week ahead, or one a stopped plan never reached. */
    data class Ahead(val sessions: List<PlannedSession>) : TimelinePlace
}

/** D117: one row of the timeline, [number] counted from 1. */
data class TimelineWeek(val number: Int, val focus: String, val place: TimelinePlace)

/**
 * D117, D118: a weekly plan drawn as its weeks — kept, or offered and not yet kept. Every count is the phone's
 * (D95): the plan's progress, and each past week's ledger. Pure.
 */
data class PlanTimeline(
    val title: String,
    val why: String,
    val start: Long,
    val standing: PlanStanding,
    val weeks: List<TimelineWeek>,
) {
    val lastDay: Long get() = ProgrammeCalendar.lastDay(start, weeks.size)

    /** Ended or stopped: the screen offers one thing, an evaluation (D117). */
    val over: Boolean get() = standing is PlanStanding.Ended || standing is PlanStanding.Stopped

    companion object {
        /**
         * @param start the plan's first Monday: its own once kept, else the one keeping it today would give (D94).
         * @param progress the plan's ticks, counted to today or to its stop day; null for an answer nothing is
         *   counted for — every session of this week open.
         * @param ledgers the ledger (D110) of each week begun, by its Monday; a past week with none has no
         *   sessions of its own.
         *
         * A stopped plan's weeks begun by its stop day are past and the rest are drawn ahead; an ended plan's
         * are all past. Only a STOPPED version is stopped: a replaced or adjusted one has a stop day too, but a
         * later version runs.
         */
        fun of(programme: Programme, start: Long, today: Long, progress: PlanProgress?, ledgers: Map<Long, WeekLedger>): PlanTimeline {
            val plan = programme.plan
            val last = ProgrammeCalendar.lastDay(start, plan.weeks.size)
            val stopped = programme.stoppedEpochDay.takeIf { programme.status == ProgrammeStatus.STOPPED }
            val current = ProgrammeCalendar.weekIndex(start, today)
            val standing = when {
                stopped != null -> PlanStanding.Stopped(stopped)
                today > last -> PlanStanding.Ended(last)
                today < start -> PlanStanding.Before(start)
                else -> PlanStanding.InWeek(current + 1)
            }
            val weeks = plan.weeks.mapIndexed { index, week ->
                val monday = ProgrammeCalendar.monday(start, index)
                val ticks = progress?.weeks?.getOrNull(index)?.ticks
                val past = TimelinePlace.Past(
                    done = ticks.orEmpty().count { it.by != null },
                    planned = week.sessions.size,
                    own = ledgers[monday]?.beyond?.kinds.orEmpty().sumOf { it.sessions },
                )
                val place = when {
                    stopped != null -> if (monday <= stopped) past else TimelinePlace.Ahead(week.sessions)
                    index < current -> past
                    index == current -> TimelinePlace.ThisWeek(week.sessions.mapIndexed { at, planned -> tile(planned, ticks?.getOrNull(at)) })
                    else -> TimelinePlace.Ahead(week.sessions)
                }
                TimelineWeek(index + 1, week.focus, place)
            }
            return PlanTimeline(plan.title, plan.why, start, standing, weeks)
        }

        private fun tile(planned: PlannedSession, tick: Tick?): PlanTile {
            val by = tick?.by ?: return PlanTile(planned, TileState.OPEN)
            return PlanTile(planned, if (tick.short) TileState.DONE_SHORT else TileState.DONE, by.epochDay, by.durationMinutes)
        }
    }
}

/** D93, D117: an evaluation with the day it was made, for "WHERE YOU STOOD". */
data class DatedEvaluation(val evaluation: Evaluation, val epochDay: Long)

/**
 * D117: what the plan screen draws — the plan as its weeks, the running plan's card behind its buttons (null
 * when the plan has ended or stopped, or is an answer not yet kept), and the evaluation it carries.
 */
data class PlanShown(val timeline: PlanTimeline, val running: PlanCard.Running?, val evaluation: DatedEvaluation?)
```

- [ ] **Step 4: Run it to see it pass** — the same command. Expected: 7 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/metaself/app/domain/trainer/PlanTimeline.kt \
        app/src/test/java/com/metaself/app/domain/trainer/PlanTimelineTest.kt
git commit -m "feat(trainer): the plan's timeline model — past, this week, ahead (D117, D118)" \
  -m "PlanTimeline.of draws a weekly plan as its weeks: past (done of planned, and the ledger's sessions of its own), this week (a tile per planned session: done, done short or open, with the ticking session's day) and ahead; before its start, after its end and stopped mid-way." \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The timeline in words

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/trainer/PlanTimelineWording.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/trainer/TrainerWeekWording.kt` (`planRow`)
- Test: `app/src/test/java/com/metaself/app/ui/trainer/PlanTimelineWordingTest.kt`,
  `app/src/test/java/com/metaself/app/ui/trainer/TrainerWeekWordingTest.kt` (`the plan's row`)

- [ ] **Step 1: Write the failing tests.** Create `PlanTimelineWordingTest.kt`:

```kotlin
package com.metaself.app.ui.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanStanding
import com.metaself.app.domain.trainer.PlanTile
import com.metaself.app.domain.trainer.PlanTimeline
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.TileState
import com.metaself.app.domain.trainer.TimelinePlace
import com.metaself.app.domain.trainer.TimelineWeek
import org.junit.jupiter.api.Test

/**
 * What the plan screen says (D117). A four-week plan from Monday 24 August 2026; today is Thursday 3 September
 * ([TEST_EPOCH_DAY]). Every figure is invented.
 */
class PlanTimelineWordingTest {

    private val monday = TEST_EPOCH_DAY - 3
    private val start = monday - 7
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
    private val walk45 = PlannedSession(WorkoutKind.WALK, 45, PlannedEffort.STEADY, "Invented line")
    private val run20 = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Invented line")

    @Test
    fun `the subtitle's four forms`() {
        assertThat(PlanTimelineWording.subtitle(timeline(start, PlanStanding.InWeek(2))))
            .isEqualTo("4 weeks · 24 Aug – 20 Sep · you're in week 2")
        assertThat(PlanTimelineWording.subtitle(timeline(monday + 7, PlanStanding.Before(monday + 7))))
            .isEqualTo("4 weeks · 7 Sep – 4 Oct · starts Mon 7 Sep")
        assertThat(PlanTimelineWording.subtitle(timeline(start, PlanStanding.Ended(start + 27))))
            .isEqualTo("4 weeks · 24 Aug – 20 Sep · ended Sun 20 Sep")
        assertThat(PlanTimelineWording.subtitle(timeline(start, PlanStanding.Stopped(monday + 1))))
            .isEqualTo("4 weeks · 24 Aug – 20 Sep · stopped Tue 1 Sep")
    }

    @Test
    fun `a past week's line says its own sessions only when there are some`() {
        assertThat(PlanTimelineWording.pastLine(TimelinePlace.Past(2, 2, 0))).isEqualTo("2 of 2 planned")
        assertThat(PlanTimelineWording.pastLine(TimelinePlace.Past(2, 2, 4))).isEqualTo("2 of 2 planned · plus 4 of your own")
        assertThat(PlanTimelineWording.pastLine(TimelinePlace.Past(0, 3, 1))).isEqualTo("0 of 3 planned · plus 1 of your own")
    }

    @Test
    fun `a week ahead's sessions on one line`() {
        assertThat(PlanTimelineWording.aheadLine(listOf(walk30, walk45, run20))).isEqualTo("Easy 30′ · Steady 45′ · Push 20′")
    }

    @Test
    fun `a tile's two lines`() {
        assertThat(PlanTimelineWording.tileTitle(walk30)).isEqualTo("Easy 30′")
        assertThat(PlanTimelineWording.tileLine(PlanTile(walk30, TileState.DONE, monday, 30))).isEqualTo("Done Mon")
        assertThat(PlanTimelineWording.tileLine(PlanTile(walk30, TileState.DONE_SHORT, monday + 2, 25))).isEqualTo("Done Wed · 25 min")
        assertThat(PlanTimelineWording.tileLine(PlanTile(walk30, TileState.OPEN))).isEqualTo("Open")
    }

    @Test
    fun `a week's heading, and where you stood`() {
        assertThat(PlanTimelineWording.weekHeading(TimelineWeek(2, "Add a run", TimelinePlace.Ahead(emptyList())))).isEqualTo("Week 2 · Add a run")
        assertThat(PlanTimelineWording.weekHeading(TimelineWeek(3, " ", TimelinePlace.Ahead(emptyList())))).isEqualTo("Week 3")
        assertThat(PlanTimelineWording.THIS_WEEK).isEqualTo(" — this week")
        assertThat(PlanTimelineWording.stood(monday - 1)).isEqualTo("WHERE YOU STOOD · SUN 30 AUG")
    }

    private fun timeline(start: Long, standing: PlanStanding) =
        PlanTimeline("Invented plan", "Invented reason.", start, standing, List(4) { TimelineWeek(it + 1, "", TimelinePlace.Ahead(emptyList())) })
}
```

In `TrainerWeekWordingTest`'s `the plan's row`, replace
`assertThat(TrainerWeekWording.planRow(PlanCard.of(running, emptyList(), TEST_EPOCH_DAY + 7, counting))).isNull()` with:

```kotlin
        // D117: an ended plan is still drawn on the plan screen, for the fourteen days the card is.
        assertThat(TrainerWeekWording.planRow(PlanCard.of(running, emptyList(), TEST_EPOCH_DAY + 7, counting))).isEqualTo("Your plan · ended Sun 6 Sep")
        assertThat(TrainerWeekWording.planRow(PlanCard.of(running, emptyList(), TEST_EPOCH_DAY + 30, counting))).isNull()
```

(The running plan there starts Monday 24 August and has two weeks: its last day is Sunday 6 September; the 3rd
plus 30 is past the fourteen days.)

- [ ] **Step 2: Run them to see them fail** — `--tests "*PlanTimelineWordingTest" --tests "*TrainerWeekWordingTest"`.
  Expected: compilation fails, `PlanTimelineWording` unresolved.

- [ ] **Step 3: Write the wording.** Create `PlanTimelineWording.kt`:

```kotlin
package com.metaself.app.ui.trainer

import com.metaself.app.domain.trainer.PlanStanding
import com.metaself.app.domain.trainer.PlanTile
import com.metaself.app.domain.trainer.PlanTimeline
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.TileState
import com.metaself.app.domain.trainer.TimelinePlace
import com.metaself.app.domain.trainer.TimelineWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the plan screen says (D117, D118). Every count is the phone's (D95, D110). */
object PlanTimelineWording {

    private const val SEP = " · "
    private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val DAY_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US)

    /** After this week's heading, in the lighter ink. */
    const val THIS_WEEK = " — this week"

    const val READ_FULL = "Read the trainer's full evaluation"
    const val HIDE_FULL = "Hide the trainer's full evaluation"

    /** D117: an ended or stopped plan's one button. */
    const val EVALUATE_AGAIN = "Evaluate me and plan again"

    /** "4 weeks · 28 Sep – 25 Oct · you're in week 2"; "starts Mon 12 Oct", "ended Sun 25 Oct", "stopped Wed 14 Oct" (invented). */
    fun subtitle(timeline: PlanTimeline): String {
        val where = when (val standing = timeline.standing) {
            is PlanStanding.Before -> "starts " + dayDate(standing.start)
            is PlanStanding.InWeek -> "you're in week ${standing.number}"
            is PlanStanding.Ended -> "ended " + dayDate(standing.lastDay)
            is PlanStanding.Stopped -> "stopped " + dayDate(standing.day)
        }
        return "${timeline.weeks.size} weeks$SEP${date(timeline.start)} – ${date(timeline.lastDay)}$SEP$where"
    }

    /** "Week 2 · Add a run" (invented); a week with no focus is "Week 2". */
    fun weekHeading(week: TimelineWeek): String = ProgrammeWording.weekTitle(week.number, week.focus)

    /** "2 of 2 planned", and "· plus 4 of your own" when the week held sessions beyond the plan (invented). */
    fun pastLine(past: TimelinePlace.Past): String =
        "${past.done} of ${past.planned} planned" + if (past.own > 0) "${SEP}plus ${past.own} of your own" else ""

    /** "Easy 30′ · Steady 45′ · Push 20′" (invented). */
    fun aheadLine(sessions: List<PlannedSession>): String = sessions.joinToString(SEP) { tileTitle(it) }

    /** "Easy 30′". */
    fun tileTitle(session: PlannedSession): String = "${ProgrammeWording.effort(session.effort)} ${session.minutes}′"

    /** "Done Mon", "Done Mon · 25 min", "Open" (invented). */
    fun tileLine(tile: PlanTile): String {
        val day = tile.day?.let { LocalDate.ofEpochDay(it).format(WEEKDAY) }
        return when (tile.state) {
            TileState.DONE -> "Done $day"
            TileState.DONE_SHORT -> "Done $day$SEP${tile.minutes} min"
            TileState.OPEN -> "Open"
        }
    }

    /** "WHERE YOU STOOD · SUN 27 SEP" — the day the evaluation was made (invented). */
    fun stood(epochDay: Long): String = "WHERE YOU STOOD$SEP${dayDate(epochDay)}".uppercase(Locale.US)

    private fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DATE)

    private fun dayDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DAY_DATE)
}
```

In `TrainerWeekWording.planRow`, give the ended plan its row:

```kotlin
    /**
     * "Your plan · week 2 of 2"; before week 1, "Your plan · starts Mon 7 Sep"; ended, "Your plan · ended Sun 20 Sep"
     * (invented) — the plan screen draws an ended plan too (D117). Null without a plan.
     */
    fun planRow(card: PlanCard): String? = when (card) {
        is PlanCard.Running -> "Your plan$SEP" + if (card.weekIndex >= 0) {
            "week ${card.weekIndex + 1} of ${card.programme.ask.weeks}"
        } else {
            "starts " + LocalDate.ofEpochDay(card.programme.startEpochDay ?: 0).format(DAY_MONTH)
        }
        is PlanCard.Ended -> {
            val programme = card.programme
            "Your plan${SEP}ended " + LocalDate.ofEpochDay(ProgrammeCalendar.lastDay(programme.startEpochDay ?: 0, programme.ask.weeks)).format(DAY_MONTH)
        }
        PlanCard.None -> null
    }
```

(import `com.metaself.app.domain.trainer.ProgrammeCalendar`).

- [ ] **Step 4: Run them to see them pass.** Expected: 0 failures. Also run `--tests "*TrainerScreenRenderTest"`:
  its row test draws a running plan and none, so it is unchanged.

- [ ] **Step 5: Commit** `PlanTimelineWording.kt`, its test, `TrainerWeekWording.kt` and its test:
  `feat(trainer): the plan's timeline in words; an ended plan keeps its row (D117)`.

---

### Task 3: The plan screen's reads

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/trainer/TrainerHome.kt` (`MainAction.of`)
- Modify: `app/src/main/java/com/metaself/app/domain/trainer/PlanProgress.kt` (`Programmes.evaluatedOf`)
- Modify: `app/src/main/java/com/metaself/app/data/trainer/AskTheTrainer.kt`
- Test: `app/src/test/java/com/metaself/app/domain/trainer/TrainerHomeTest.kt`,
  `app/src/test/java/com/metaself/app/domain/trainer/PlanProgressTest.kt`,
  `app/src/test/java/com/metaself/app/data/trainer/AskTheTrainerTest.kt`

- [ ] **Step 1: Write the failing tests.**

In `TrainerHomeTest`, after `the main button is the next thing to do`:

```kotlin
    /** D117: the plan screen's main action is the Trainer page's, from the running card alone. Invented. */
    @Test
    fun `the main action is read from a running card`() {
        val card = PlanCard.of(running, emptyList(), today = TEST_EPOCH_DAY, counting = PlanCounting(1, 0, emptyMap())) as PlanCard.Running
        val done = PlanCard.of(
            running, listOf(planSession(1, start), planSession(2, start + 1).copy(durationMinutes = 40)),
            today = TEST_EPOCH_DAY, counting = PlanCounting(1, 0, emptyMap()),
        ) as PlanCard.Running

        assertThat(MainAction.of(card, card.next)).isEqualTo(MainAction.PlanOpen(WorkoutKind.WALK))
        assertThat(MainAction.of(done, done.next)).isEqualTo(MainAction.PlanNext)
        assertThat(MainAction.of(PlanCard.None, null)).isEqualTo(MainAction.Evaluate)
    }
```

In `PlanProgressTest`, in the test that asserts `Programmes.evaluationOf(adjusted, listOf(first, adjusted))`, add:

```kotlin
        assertThat(Programmes.evaluatedOf(adjusted, listOf(first, adjusted))!!.id).isEqualTo(first.id)
        assertThat(Programmes.evaluatedOf(first, listOf(first, adjusted))!!.id).isEqualTo(first.id)
```

In `AskTheTrainerTest`, a section before `// --- D105`:

```kotlin
    // --- D117, D118 ------------------------------------------------------------------------------

    /**
     * Kept from the Monday a week ago: week 1 is past — two walks ticked its two planned walks, the third is
     * the week's own — and week 2 is this week, its first walk done Monday. The evaluation was made the Sunday
     * fifteen days before this Monday, at noon (in any zone within eleven hours of UTC, that Sunday).
     */
    @Test
    fun `the plan shown is the running one, drawn as its weeks, with its evaluation's day`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, MONDAY - 7)
        record.workouts.value = listOf(walk(1, MONDAY - 7), walk(2, MONDAY - 6), walk(3, MONDAY - 5), walk(4, MONDAY))

        val shown = ask().planShown()!!

        assertThat(shown.timeline.standing).isEqualTo(PlanStanding.InWeek(2))
        assertThat(shown.timeline.weeks[0].place).isEqualTo(TimelinePlace.Past(done = 2, planned = 2, own = 1))
        assertThat(shown.timeline.weeks[1].place).isEqualTo(
            TimelinePlace.ThisWeek(listOf(PlanTile(WALK_30, TileState.DONE, MONDAY, 40), PlanTile(WALK_30, TileState.OPEN))),
        )
        assertThat(shown.running!!.programme.id).isEqualTo(id)
        assertThat(shown.evaluation).isEqualTo(DatedEvaluation(EVALUATION, MONDAY - 15))
    }

    @Test
    fun `with nothing kept there is no plan to show, and a stopped plan is shown stopped for fourteen days`() = runTest {
        assertThat(ask().planShown()).isNull()
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, MONDAY - 7)
        programmes.stop(id, MONDAY + 1)

        val shown = ask().planShown()!!
        assertThat(shown.timeline.standing).isEqualTo(PlanStanding.Stopped(MONDAY + 1))
        assertThat(shown.running).isNull()
        assertThat(shown.timeline.weeks.all { it.place is TimelinePlace.Past }).isTrue()

        day = MONDAY + 1 + 15
        assertThat(ask().planShown()).isNull()
    }

    @Test
    fun `an ended plan is shown, ended, with no running card`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 14, MONDAY - 14)

        val shown = ask().planShown()!!

        assertThat(shown.timeline.standing).isEqualTo(PlanStanding.Ended(MONDAY - 1))
        assertThat(shown.running).isNull()
    }

    /** D118: the adjusted version offered keeps the weeks already past, and this week's done walk shows done. */
    @Test
    fun `an adjusted version not yet kept is drawn as it would be counted, with its chain's evaluation`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, MONDAY - 7)
        record.workouts.value = listOf(walk(1, MONDAY - 7), walk(2, MONDAY))
        val adjusted = WeeksPlan("Invented adjusted", listOf(PlanWeek("w", listOf(WALK_30, WALK_30)), PlanWeek("x", listOf(WALK_30, WALK_20))), "Invented.")
        val version = programmes.add(offered().copy(evaluation = null, plan = adjusted, replacesId = id))

        val shown = ask().planShownOf(programmes.rows.value.first { it.id == version }, MONDAY - 7)

        assertThat(shown.timeline.title).isEqualTo("Invented adjusted")
        assertThat(shown.timeline.weeks[0].place).isEqualTo(TimelinePlace.Past(done = 1, planned = 2, own = 0))
        assertThat(shown.timeline.weeks[1].place).isEqualTo(
            TimelinePlace.ThisWeek(listOf(PlanTile(WALK_30, TileState.DONE, MONDAY, 40), PlanTile(WALK_20, TileState.OPEN))),
        )
        assertThat(shown.running).isNull()
        assertThat(shown.evaluation).isEqualTo(DatedEvaluation(EVALUATION, MONDAY - 15))
    }

    /** D118: an evaluation's answer, never kept, counts nothing — this Monday's walk ticks no tile. */
    @Test
    fun `an answer never kept has every session open`() = runTest {
        record.workouts.value = listOf(walk(1, MONDAY))
        val programme = programmes.rows.value.first { it.id == programmes.add(offered()) }

        val timeline = ask().timelineOf(programme, MONDAY)

        assertThat(timeline.weeks[0].place).isEqualTo(TimelinePlace.ThisWeek(listOf(PlanTile(WALK_30, TileState.OPEN), PlanTile(WALK_30, TileState.OPEN))))
    }
```

with the imports `DatedEvaluation`, `PlanStanding`, `PlanTile`, `TileState`, `TimelinePlace`. (`walk` is forty
minutes at 08:00; `offered()` is two weeks of two easy 30-minute walks, its evaluation made at noon on
`MONDAY - 15`.)

- [ ] **Step 2: Run them to see them fail** — `--tests "*TrainerHomeTest" --tests "*PlanProgressTest" --tests "*AskTheTrainerTest"`.
  Expected: compilation fails on `MainAction.of`, `evaluatedOf`, `planShown`, `planShownOf`, `timelineOf`.

- [ ] **Step 3: Write them.**

`TrainerHome.kt` — `main` delegates, and `MainAction` gains:

```kotlin
    /** D114: the page's one main button — the open planned session, the next session, or an evaluation. */
    val main: MainAction get() = MainAction.of(plan, next)
...
sealed interface MainAction {
    ...
    companion object {
        /** D114, D117: the next thing to do, for [plan] and its running week's [next] open session. */
        fun of(plan: PlanCard, next: PlannedTick?): MainAction =
            if (plan is PlanCard.Running) next?.let { PlanOpen(it.session.kind) } ?: PlanNext else Evaluate
    }
}
```

`PlanProgress.kt`, `Programmes`:

```kotlin
    /** The evaluation a version carries, else the one of the version it replaces, and so on back. */
    fun evaluationOf(programme: Programme, all: List<Programme>): Evaluation? = evaluatedOf(programme, all)?.evaluation

    /** D117: the version whose evaluation [evaluationOf] gives — for the day it was made; null when none has one. */
    fun evaluatedOf(programme: Programme, all: List<Programme>): Programme? =
        generateSequence(programme) { current -> current.replacesId?.let { id -> all.firstOrNull { it.id == id } } }
            .take(all.size + 1)
            .firstOrNull { it.evaluation != null }
```

`AskTheTrainer.kt` — `evaluationOf` is replaced by:

```kotlin
    /**
     * D117: the plan "See the plan" draws — the running one; else one that ended, or was stopped, in the last
     * [ProgrammeCalendar.ENDED_SHOWN_DAYS] days; null when none. Reads only.
     */
    suspend fun planShown(): PlanShown? {
        val card = card()
        val programme = when (card) {
            is PlanCard.Running -> card.programme
            is PlanCard.Ended -> card.programme
            PlanCard.None -> recentlyStopped(today().toEpochDay())
        } ?: return null
        val start = programme.startEpochDay ?: return null
        return PlanShown(timelineOf(programme, start), card as? PlanCard.Running, datedEvaluationOf(programme))
    }

    /** D118: an answer offered and not yet kept, drawn as it would be from [start], with its chain's evaluation. Reads only. */
    suspend fun planShownOf(offered: Programme, start: Long): PlanShown =
        PlanShown(timelineOf(offered, start), null, datedEvaluationOf(offered))

    private suspend fun recentlyStopped(day: Long): Programme? = programmes.all()
        .filter { programme ->
            val stopped = programme.stoppedEpochDay
            programme.status == ProgrammeStatus.STOPPED && programme.startEpochDay != null && stopped != null &&
                day - stopped in 0..ProgrammeCalendar.ENDED_SHOWN_DAYS.toLong()
        }
        .maxByOrNull { it.createdAtMillis }

    /**
     * D117, D118: [programme] drawn as its weeks from [start]. A kept version is counted as the Trainer page
     * counts it, to today or its stop day; an adjusted version not yet kept as its chain would count it (its
     * weeks already past are the running plan's, D97); an evaluation's answer not yet kept counts nothing. Each
     * week begun gets its ledger (D110), for the sessions of its own. Reads only.
     */
    suspend fun timelineOf(programme: Programme, start: Long): PlanTimeline {
        val day = today().toEpochDay()
        if (programme.startEpochDay == null && programme.replacesId == null) {
            return PlanTimeline.of(programme, start, day, null, emptyMap())
        }
        val read = minOf(day, ProgrammeCalendar.lastDay(start, programme.plan.weeks.size))
        val until = if (programme.startEpochDay != null) Programmes.countedUntil(programme, day) else read
        val workouts = if (read < start) emptyList() else record.observeWorkouts(start, read).first()
        val progress = PlanProgress.of(programme.plan, start, workouts, until, counting(programme))
        val plans = countedPlans()
        val ledgers = programme.plan.weeks.indices.map { ProgrammeCalendar.monday(start, it) }
            .filter { it <= read }
            .associateWith { WeekLedgers.of(it, day, workouts, emptyList(), emptyMap(), null, plans) }
        return PlanTimeline.of(programme, start, day, progress, ledgers)
    }

    /** D117: the evaluation [programme] carries, or its chain's, with the day it was made (D98). Reads only. */
    suspend fun datedEvaluationOf(programme: Programme): DatedEvaluation? {
        val evaluated = Programmes.evaluatedOf(programme, programmes.all()) ?: return null
        val evaluation = evaluated.evaluation ?: return null
        val made = Instant.ofEpochMilli(evaluated.createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        return DatedEvaluation(evaluation, made)
    }
```

(imports `DatedEvaluation`, `PlanShown`, `PlanTimeline`, `WeekLedgers`; `Evaluation` is no longer used here.)
The ledgers are counted with no days and no food: only their sessions are read.

- [ ] **Step 4: Run them to see them pass.** `EvaluatePlanViewModel` still calls `ask.evaluationOf` and fails
  to compile: replace its two lines in `readRunning` with
  `val running = ask.running(); val evaluation = running?.let { ask.datedEvaluationOf(it.programme)?.evaluation }`
  for now (Task 4 replaces `readRunning`). Expected: 0 failures.

- [ ] **Step 5: Commit** the five files and their tests:
  `feat(trainer): the plan screen's reads — the plan shown, its timeline and its evaluation's day (D117, D118)`.

---

### Task 4: The plan screen's view model

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/screen/trainer/EvaluatePlanViewModel.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/trainer/EvaluatePlanViewModelTest.kt`

- [ ] **Step 1: Write the failing tests.** In `EvaluatePlanViewModelTest`, rewrite the two running-plan tests and
  add two:

```kotlin
    @Test
    fun `opened on the running plan, it shows the plan as its weeks with its chain's evaluation`() = runTest {
        val id = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), ANSWER.evaluation, ANSWER.plan, "a-model"))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)

        val viewModel = watched(SavedStateHandle(mapOf(EvaluatePlanViewModel.SHOW to EvaluatePlanViewModel.RUNNING)))
        advanceUntilIdle()

        val shown = viewModel.state.value.planShown!!
        assertThat(shown.running!!.programme.id).isEqualTo(id)
        assertThat(shown.timeline.standing).isEqualTo(PlanStanding.InWeek(1))
        assertThat(shown.evaluation!!.evaluation).isEqualTo(ANSWER.evaluation)
        assertThat(viewModel.state.value.loading).isFalse()
    }

    /** D114: Adjust the plan opens from here, so an adjustment kept there shows when the plan screen comes back. Invented. */
    @Test
    fun `back on the plan screen, the running plan is read again`() = runTest {
        ... as now, ending:
        assertThat(viewModel.state.value.planShown!!.running!!.programme.id).isEqualTo(adjusted)
        assertThat(viewModel.state.value.planShown!!.evaluation!!.evaluation).isEqualTo(ANSWER.evaluation)
    }

    /** D117: Stop this plan returns here; the plan is drawn stopped, with no running card. */
    @Test
    fun `back from Stop this plan, the plan is shown stopped`() = runTest {
        val id = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), ANSWER.evaluation, ANSWER.plan, "a-model"))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        val viewModel = watched(SavedStateHandle(mapOf(EvaluatePlanViewModel.SHOW to EvaluatePlanViewModel.RUNNING)))
        advanceUntilIdle()

        programmes.stop(id, TEST_EPOCH_DAY)
        viewModel.lookedAt()
        advanceUntilIdle()

        assertThat(viewModel.state.value.planShown!!.timeline.standing).isEqualTo(PlanStanding.Stopped(TEST_EPOCH_DAY))
        assertThat(viewModel.state.value.planShown!!.running).isNull()
    }

    /** D118: asked on a Thursday, the answer would start this Monday — week 1 is this week, every session open. */
    @Test
    fun `an answer is drawn as its weeks before it is kept`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(2, 2))
        viewModel.ask()
        advanceUntilIdle()

        val offered = viewModel.state.value.offered!!
        assertThat(offered.timeline.standing).isEqualTo(PlanStanding.InWeek(1))
        assertThat(offered.timeline.weeks[0].place).isEqualTo(TimelinePlace.ThisWeek(listOf(PlanTile(WALK, TileState.OPEN), PlanTile(WALK, TileState.OPEN))))
        assertThat(offered.evaluation).isEqualTo(DatedEvaluation(ANSWER.evaluation, TEST_EPOCH_DAY))
        assertThat(offered.running).isNull()

        viewModel.askAgain()
        advanceUntilIdle()
        assertThat(viewModel.state.value.offered).isNull()
    }
```

- [ ] **Step 2: Run them to see them fail.** Expected: `planShown` and `offered` unresolved.

- [ ] **Step 3: Write it.** In `State`, `running` and `evaluation` are replaced by `planShown`, and `offered` is
  derived:

```kotlin
     * @property onRunning opened on the plan "See the plan" shows; [planShown] is it (null when there is none).
     */
    data class State(
        ...
        val onRunning: Boolean = false,
        val planShown: PlanShown? = null,
        ...
    ) {
        ...
        /**
         * D118: the answer drawn as its weeks before it is kept, from the Monday keeping it today would give.
         * Nothing is counted for an answer, so nothing is read; an answer just asked for was made today.
         */
        val offered: PlanShown?
            get() = shown?.let { programme ->
                PlanShown(
                    PlanTimeline.of(programme, startIfKept, today, null, emptyMap()),
                    null,
                    programme.evaluation?.let { DatedEvaluation(it, today) },
                )
            }
    }
```

and `readRunning` reads `ask.planShown()`:

```kotlin
    private fun readRunning() {
        guarded(problems, onRefused = { local.update { it.copy(loading = false, refused = ActionRefused.COULD_NOT_OPEN) } }) {
            val shown = ask.planShown()
            local.update { it.copy(loading = false, planShown = shown) }
        }
    }
```

The screen still reads `state.running` / `state.evaluation`: change its running branch to
`state.planShown?.running` and `state.planShown?.evaluation?.evaluation` until Task 6 redraws it.

- [ ] **Step 4: Run them to see them pass** (`--tests "*EvaluatePlanViewModelTest" --tests "*EvaluatePlanScreenRenderTest"`;
  the render test's two running cases build `State(running = …)` — change them to
  `planShown = PlanShown(PlanTimeline.of(programme, start, TEST_EPOCH_DAY, running.progress, emptyMap()), running, null)`
  until Task 6 rewrites them). Expected: 0 failures.

- [ ] **Step 5: Commit** — `feat(trainer): the plan screen reads the plan shown; an answer is drawn as its weeks (D117, D118)`.

---

### Task 5: The adjust page's offered version

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/screen/trainer/AdjustPlanViewModel.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/trainer/AdjustPlanViewModelTest.kt`

- [ ] **Step 1: Write the failing test.** In `AdjustPlanViewModelTest`, at the end of
  `it opens on the running plan, and adjusting shows the new version without keeping it`:

```kotlin
        // D118: the new version drawn as its weeks — the plan began this Monday, so week 1 is this week.
        val offered = viewModel.state.value.offered!!
        assertThat(offered.timeline.title).isEqualTo(viewModel.state.value.shown!!.plan.title)
        assertThat(offered.timeline.standing).isEqualTo(PlanStanding.InWeek(1))
        assertThat(offered.running).isNull()
```

- [ ] **Step 2: Run it to see it fail.** Expected: `offered` unresolved.

- [ ] **Step 3: Write it.** `State` gains `val offered: PlanShown? = null` ("D118: [shown] drawn as its weeks, read
  with it"). `adjust()`'s work reads it with the answer, so the two arrive together:

```kotlin
        val start = local.value.running?.programme?.startEpochDay ?: return
        ...
            work = {
                val outcome = ask.adjust(words)
                outcome to (outcome as? AskTheTrainer.Adjusted.Offered)?.let { ask.planShownOf(it.programme, start) }
            },
        ) { (outcome, offered) ->
            when (outcome) {
                is AskTheTrainer.Adjusted.Offered -> local.update { it.copy(asking = false, shown = outcome.programme, offered = offered) }
                ...
```

(A failure to read the timeline refuses the ask as a failure of `adjust` itself would: the version stays stored
OFFERED, and Ask again — Adjust — is open.)

- [ ] **Step 4: Run it to see it pass** (`--tests "*AdjustPlan*"`). Expected: 0 failures.

- [ ] **Step 5: Commit** — `feat(trainer): the adjusted version is read as its weeks with the answer (D118)`.

---

### Task 6: The plan screen drawn as a timeline

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/screen/trainer/PlanTimelineView.kt`
- Create: `app/src/main/java/com/metaself/app/ui/screen/trainer/DashedOutline.kt`
- Delete: `app/src/main/java/com/metaself/app/ui/screen/trainer/WeeksPlanView.kt`
- Modify: `EvaluatePlanScreen.kt`, `AdjustPlanScreen.kt`, `TrainerScreen.kt` (OpenBlock uses the shared outline),
  `ui/nav/MetaSelfNavHost.kt`, `ui/trainer/ProgrammeWording.kt`, `res/values/strings.xml`
- Test: `EvaluatePlanScreenRenderTest.kt` (rewritten), `AdjustPlanScreenRenderTest.kt`,
  `ui/theme/InkLadderTest.kt`, `ui/trainer/ProgrammeWordingTest.kt`

- [ ] **Step 1: Write the failing tests.**

`InkLadderTest` gains:

```kotlin
    /** D117: a done tile and a past week's tick are drawn in `onPrimary` on `primary`, in both schemes. */
    @Test
    fun `a done tile's text is legible on the green`() {
        assertLegible(mapOf("onPrimary" to MetaSelfLightColours.onPrimary), MetaSelfLightColours.primary, "light green tile")
        assertLegible(mapOf("onPrimary" to MetaSelfDarkColours.onPrimary), MetaSelfDarkColours.primary, "dark green tile")
    }
```

`EvaluatePlanScreenRenderTest` keeps its two form tests and replaces the rest. The plan is four weeks of easy
30-minute and steady 45-minute walks from Monday 24 August, so today is in week 2; its evaluation was made Sunday
30 August:

```kotlin
    @Test
    fun `the running plan is drawn as its weeks, in D117's order`() {
        val texts = draw(running(workouts = listOf(TrainerScreens.walk(1, MONDAY))))

        assertThat(texts.count { it == "From the AI trainer · advice, not a measurement" }).isEqualTo(1)
        assertThat(texts).containsAtLeast(
            "From the AI trainer · advice, not a measurement",
            "Invented plan", "4 weeks · 24 Aug – 20 Sep · you're in week 2",
            "Week 1 · settle in", "0 of 2 planned",
            "Week 2 · add a walk — this week", "Easy 30′", "Done Mon", "Steady 45′", "Open",
            "Week 3 · hold it", "Easy 30′ · Steady 45′",
            "Week 4", "Easy 30′",
            "WHERE YOU STOOD · SUN 30 AUG", "Invented headline.", "Read the trainer's full evaluation",
            "Adjust the plan", "Plan the open walk",
        ).inOrder()
        assertThat(texts).doesNotContain("Going well.")
        assertThat(texts).doesNotContain("Keep this plan")
    }

    @Test
    fun `the full evaluation opens from its line, with the plan's why`() {
        draw(running())
        render.click("Read the trainer's full evaluation")

        assertThat(render.textsAgain()).containsAtLeast(
            "Invented headline.", "Hide the trainer's full evaluation", "Going well.", "Invented going well.",
            "To work on.", "Invented to work on.", "Why.", "Invented reason.",
        ).inOrder()
        assertThat(render.textsAgain()).doesNotContain("Since last time.")
    }

    @Test
    fun `a plan with no evaluation shows neither its headline nor the full evaluation`() {
        val texts = draw(running(evaluation = null))

        assertThat(texts.none { it.startsWith("WHERE YOU STOOD") }).isTrue()
        assertThat(texts).doesNotContain("Read the trainer's full evaluation")
    }

    @Test
    fun `while it runs, Adjust the plan and the main action, and with nothing open Plan my next session`() {
        val opened = mutableListOf<String>()
        val done = listOf(TrainerScreens.walk(1, MONDAY), TrainerScreens.walk(2, MONDAY + 1).copy(durationMinutes = 45))
        draw(running(workouts = done), onAdjust = { opened += "adjust" }, onPlan = { opened += "plan" })

        assertThat(render.roleOf("Adjust the plan")).isEqualTo(Role.Button)
        render.click("Adjust the plan")
        render.click("Plan my next session")
        assertThat(opened).containsExactly("adjust", "plan").inOrder()
        assertThat(render.textsAgain()).doesNotContain("Evaluate me and plan again")
    }

    @Test
    fun `ended or stopped, one button — Evaluate me and plan again`() {
        var evaluated = 0
        val ended = draw(running(today = START + 28), onEvaluate = { evaluated++ })
        assertThat(ended).contains("4 weeks · 24 Aug – 20 Sep · ended Sun 20 Sep")
        assertThat(ended).doesNotContain("Adjust the plan")
        render.click("Evaluate me and plan again")

        val stopped = draw(running(stoppedOn = MONDAY + 1), onEvaluate = { evaluated++ })
        assertThat(stopped).contains("4 weeks · 24 Aug – 20 Sep · stopped Tue 1 Sep")
        assertThat(stopped).containsAtLeast("Week 2 · add a walk", "0 of 2 planned", "Week 3 · hold it", "Easy 30′ · Steady 45′").inOrder()
        assertThat(stopped).doesNotContain("Plan the open walk")
        render.click("Evaluate me and plan again")
        assertThat(evaluated).isEqualTo(2)
    }

    @Test
    fun `with no plan to show it says so`() {
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, onRunning = true))

        assertThat(texts).contains("No plan is running.")
        assertThat(texts).doesNotContain("Adjust the plan")
    }

    /** D118: the answer is drawn as the timeline it would be, kept today — week 1 is this week, its sessions open. */
    @Test
    fun `an answer is drawn as its weeks, with Keep this plan and Ask again`() {
        var kept = false
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, shown = SHOWN), onKeep = { kept = true })

        assertThat(texts.count { it == "From the AI trainer · advice, not a measurement" }).isEqualTo(1)
        assertThat(texts).containsAtLeast(
            "From the AI trainer · advice, not a measurement", "Invented plan", "4 weeks · 31 Aug – 27 Sep · you're in week 1",
            "Week 1 · settle in — this week", "Easy 30′", "Open", "Week 2 · add a walk",
            "WHERE YOU STOOD · THU 3 SEP", "Invented headline.", "Read the trainer's full evaluation",
            "Keep this plan", "Ask again", "Keeping it replaces any plan you have now. The evaluation is kept either way.",
        ).inOrder()
        assertThat(texts).doesNotContain("Adjust the plan")
        render.click("Keep this plan")
        assertThat(kept).isTrue()
    }

    @Test
    fun `a kept answer says so`() {
        assertThat(draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, shown = SHOWN, kept = true)))
            .contains("Kept. It is on the Trainer screen.")
    }

    /** A tap on Keep is not taken twice: while it writes, Keep and Ask again cannot be pressed. */
    @Test
    fun `while Keep is writing, Keep and Ask again cannot be pressed`() { ... unchanged ... }

    private fun draw(
        state: EvaluatePlanViewModel.State,
        onKeep: () -> Unit = {},
        onAdjust: () -> Unit = {},
        onPlan: () -> Unit = {},
        onEvaluate: () -> Unit = {},
    ): List<String> = render.texts {
        EvaluatePlanScreen(
            state = state, onBack = {}, onChange = {}, onAsk = {}, onKeep = onKeep, onAskAgain = {},
            onAdjust = onAdjust, onPlan = onPlan, onEvaluate = onEvaluate,
        )
    }

    /** The plan kept from [START], as the screen reads it on [today]; [stoppedOn] stops it. */
    private fun running(
        today: Long = TEST_EPOCH_DAY,
        workouts: List<Workout> = emptyList(),
        evaluation: DatedEvaluation? = DatedEvaluation(SHOWN.evaluation!!, MONDAY - 1),
        stoppedOn: Long? = null,
    ): EvaluatePlanViewModel.State {
        val kept = SHOWN.copy(startEpochDay = START, status = ProgrammeStatus.RUNNING)
        val programme = if (stoppedOn == null) kept else kept.copy(status = ProgrammeStatus.STOPPED, stoppedEpochDay = stoppedOn)
        val counting = PlanCounting(1, 0, emptyMap())
        val progress = PlanProgress.of(programme.plan, START, workouts, Programmes.countedUntil(programme, today), counting)
        val card = if (stoppedOn == null) PlanCard.of(kept, workouts, today, counting) as? PlanCard.Running else null
        val shown = PlanShown(PlanTimeline.of(programme, START, today, progress, emptyMap()), card, evaluation)
        return EvaluatePlanViewModel.State(today = today, onRunning = true, planShown = shown)
    }

    private companion object {
        val MONDAY = TEST_EPOCH_DAY - 3
        val START = MONDAY - 7
        val WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val WALK_45 = PlannedSession(WorkoutKind.WALK, 45, PlannedEffort.STEADY, "Invented line")
        val SHOWN = Programme(
            id = 1, createdAtMillis = 0, ask = ProgrammeAsk(4, 2),
            evaluation = Evaluation("Invented headline.", "Invented going well.", "Invented to work on.", ""),
            plan = WeeksPlan(
                "Invented plan",
                listOf(
                    PlanWeek("settle in", listOf(WALK, WALK)), PlanWeek("add a walk", listOf(WALK, WALK_45)),
                    PlanWeek("hold it", listOf(WALK, WALK_45)), PlanWeek("", listOf(WALK)),
                ),
                "Invented reason.",
            ),
            model = "a-model",
        )
    }
```

(`TrainerScreens.walk` is forty minutes at 07:00: on Monday it ticks week 2's easy 30; the forty-five on Tuesday
ticks the steady 45, so nothing is open.)

`AdjustPlanScreenRenderTest`'s `a new version shows with Keep this version and Keep the old one` draws
`offered = PlanShown(PlanTimeline.of(version, start, TEST_EPOCH_DAY, null, emptyMap()), null, null)` with the state
and asserts `render.isDrawnBefore("From the AI trainer", "Invented")` (the title) and
`render.isDrawnBefore("Week 1", "Keep this version")` in place of `"THE PLAN"`.

`ProgrammeWordingTest` loses the assertions on `span`, `planHeading`, `tickTitle` and `tickLine`.

- [ ] **Step 2: Run them to see them fail** — `--tests "*EvaluatePlanScreenRenderTest" --tests "*AdjustPlanScreenRenderTest" --tests "*InkLadderTest"`.
  Expected: compilation fails (`onPlan`, `onEvaluate`, `offered` on the adjust state's screen use). `InkLadderTest`'s
  new test passes on its own once compiled — it is a measurement, recorded, not a behaviour (5.00:1 and 7.29:1).

- [ ] **Step 3: Draw it.**

`DashedOutline.kt`:

```kotlin
package com.metaself.app.ui.screen.trainer

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * D114, D117: an open planned session's outline — dashed, in [color], inset by half its stroke so no edge is
 * clipped. The week strip's "To do" blocks and the plan screen's open tiles share it.
 */
internal fun Modifier.dashedOutline(color: Color, corner: Dp, stroke: Dp = 2.dp, dash: Dp = 4.dp, gap: Dp = 3.dp): Modifier =
    drawBehind {
        val width = stroke.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(width / 2, width / 2),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(corner.toPx()),
            style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
        )
    }
```

`TrainerScreen.OpenBlock` uses `.dashedOutline(outline, corner = 6.dp)` in place of its own `drawBehind`.

`PlanTimelineView.kt` — `FromTrainerLabel` moves here unchanged, and:

```kotlin
/** The timeline's node column; the gap between it and the week. */
private val NODE = 28.dp
private val NODE_GAP = 14.dp

/** Below a week's text, before the next week's node; as the approved mockup. */
private val WEEK_GAP = 18.dp
private val TILES_PER_ROW = 3

/**
 * D117 items 1–3, D118: the plan's title in the display face and where the owner is in it, the timeline, and
 * where he stood — the evaluation's headline, its full text folded under "Read the trainer's full evaluation",
 * closed by default; neither when there is no evaluation. Drawn into the caller's column, after its
 * [FromTrainerLabel] and before its buttons.
 */
@Composable
internal fun PlanShownView(shown: PlanShown) {
    val timeline = shown.timeline
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(timeline.title, style = MaterialTheme.typography.headlineMedium)
        Text(PlanTimelineWording.subtitle(timeline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Timeline(timeline)
    shown.evaluation?.let { Stood(it, timeline.why) }
}

/** D117 item 2: one row a week — its node and the line to the next, then its heading and its line or tiles. */
@Composable
private fun Timeline(timeline: PlanTimeline) {
    Column(Modifier.fillMaxWidth()) {
        timeline.weeks.forEachIndexed { index, week ->
            val last = index == timeline.weeks.lastIndex
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(NODE_GAP)) {
                Column(Modifier.width(NODE).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Node(week.place)
                    if (!last) {
                        val line = if (week.place is TimelinePlace.Past) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        Box(Modifier.width(2.dp).weight(1f).background(line))
                    }
                }
                Column(
                    Modifier.weight(1f).padding(bottom = if (last) 0.dp else WEEK_GAP),
                    verticalArrangement = Arrangement.spacedBy(if (week.place is TimelinePlace.ThisWeek) 10.dp else 2.dp),
                ) {
                    WeekHeading(week)
                    when (val place = week.place) {
                        is TimelinePlace.Past -> WeekLine(PlanTimelineWording.pastLine(place))
                        is TimelinePlace.ThisWeek -> Tiles(place.tiles)
                        is TimelinePlace.Ahead -> WeekLine(PlanTimelineWording.aheadLine(place.sessions))
                    }
                }
            }
        }
    }
}

/** A past week: filled, with a tick; this week: a ring; a week ahead: a small grey dot. */
@Composable
private fun Node(place: TimelinePlace) {
    val green = MaterialTheme.colorScheme.primary
    when (place) {
        is TimelinePlace.Past -> Box(Modifier.size(NODE).background(green, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
        is TimelinePlace.ThisWeek -> Box(
            Modifier.size(NODE).background(MaterialTheme.colorScheme.background, CircleShape).border(3.dp, green, CircleShape),
        )
        is TimelinePlace.Ahead -> Box(Modifier.padding(4.dp).size(20.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
    }
}

/** "Week 2 · Add a run", then " — this week" in the lighter ink; a week ahead is all in the lighter ink. */
@Composable
private fun WeekHeading(week: TimelineWeek) {
    val lighter = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        buildAnnotatedString {
            append(PlanTimelineWording.weekHeading(week))
            if (week.place is TimelinePlace.ThisWeek) {
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = lighter)) { append(PlanTimelineWording.THIS_WEEK) }
            }
        },
        style = MaterialTheme.typography.titleSmall,
        color = if (week.place is TimelinePlace.Ahead) lighter else Color.Unspecified,
    )
}

@Composable
private fun WeekLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** This week's planned sessions, three to a row, as the Trainer page draws planned sessions (D114). */
@Composable
private fun Tiles(tiles: List<PlanTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        tiles.chunked(TILES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                row.forEach { Tile(it, Modifier.weight(1f)) }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Done (short or not): filled in the dark green, its text `onPrimary` (5.00:1 light, 7.29:1 dark — InkLadderTest).
 * Open: the strip's dashed outline, its text in ink, as the strip's "40′" is.
 */
@Composable
private fun Tile(tile: PlanTile, modifier: Modifier) {
    val open = tile.state == TileState.OPEN
    val shape = RoundedCornerShape(12.dp)
    val surface = if (open) {
        Modifier.dashedOutline(MaterialTheme.colorScheme.primary, corner = 12.dp)
    } else {
        Modifier.background(MaterialTheme.colorScheme.primary, shape)
    }
    val ink = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
    Column(modifier.then(surface).padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(PlanTimelineWording.tileTitle(tile.planned), style = MaterialTheme.typography.titleSmall, color = ink)
        Text(
            PlanTimelineWording.tileLine(tile),
            style = MaterialTheme.typography.bodySmall,
            color = if (open) MaterialTheme.colorScheme.onSurfaceVariant else ink,
        )
    }
}

/**
 * D117 item 3: "WHERE YOU STOOD" and its date, the headline, and the rest folded — going well, to work on,
 * since last time (when there is one) and the plan's why. The fold's words are ink (the green on a card is
 * 3.86:1, under the floor); its arrow carries the green.
 */
@Composable
private fun Stood(dated: DatedEvaluation, why: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    val evaluation = dated.evaluation
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Text(PlanTimelineWording.stood(dated.epochDay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(evaluation.headline, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Tight),
            ) {
                Text(
                    if (open) PlanTimelineWording.HIDE_FULL else PlanTimelineWording.READ_FULL,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (open) {
                LabelledPart(R.string.weeks_going_well, evaluation.goingWell)
                LabelledPart(R.string.weeks_to_work_on, evaluation.toWorkOn)
                if (evaluation.sinceLast.isNotBlank()) LabelledPart(R.string.weeks_since_last, evaluation.sinceLast)
                LabelledPart(R.string.weeks_why, why)
            }
        }
    }
}

@Composable
private fun LabelledPart(@StringRes label: Int, text: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * D117 item 4: a plan ended or stopped has one filled "Evaluate me and plan again"; a running one, Adjust the
 * plan (outlined) beside the Trainer page's main action (D114).
 */
@Composable
internal fun PlanButtons(shown: PlanShown, onAdjust: () -> Unit, onPlan: () -> Unit, onEvaluate: () -> Unit) {
    val running = shown.running
    when {
        shown.timeline.over -> Button(onClick = onEvaluate, modifier = Modifier.fillMaxWidth()) { Text(PlanTimelineWording.EVALUATE_AGAIN) }
        running != null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            OutlinedButton(onClick = onAdjust, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.trainer_adjust_plan), textAlign = TextAlign.Center)
            }
            Button(onClick = onPlan, modifier = Modifier.weight(1f)) {
                val main = MainAction.of(running, running.next)
                Text(
                    if (main is MainAction.PlanOpen) TrainerWeekWording.planOpen(main.kind) else stringResource(R.string.trainer_plan_next),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
```

`EvaluatePlanScreen` gains `onPlan: () -> Unit = {}` and `onEvaluate: () -> Unit = {}`, and its two answer
branches become:

```kotlin
            state.onRunning && planShown == null -> Text(stringResource(R.string.weeks_not_running))
            state.onRunning && planShown != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                FromTrainerLabel()
                PlanShownView(planShown)
                PlanButtons(planShown, onAdjust, onPlan, onEvaluate)
            }
            shown == null -> EvaluateForm(state, onChange, onAsk)
            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                FromTrainerLabel()
                state.offered?.let { PlanShownView(it) }
                ... Keep this plan / kept, Ask again, the keep note — unchanged ...
            }
```

`AdjustPlanScreen`'s offered branch draws `FromTrainerLabel()` then `state.offered?.let { PlanShownView(it) }` in
place of `WeeksPlanView(…)`, then its two Keep buttons, unchanged.

`MetaSelfNavHost`, the plan screen: `onPlan = { navController.navigate(Destination.PlanSession.form()) }`,
`onEvaluate = { navController.navigate(Destination.EvaluatePlan.form()) }`.

Delete `WeeksPlanView.kt`; delete `ProgrammeWording.span`, `planHeading`, `tickTitle`, `tickLine` (and the
imports only they used) and the string `weeks_where`.

- [ ] **Step 4: Run them to see them pass** — `--tests "*trainer*"` (the trainer screens, view models and
  wording). Expected: 0 failures.

- [ ] **Step 5: Commit** — `feat(trainer): the plan screen is a timeline (D117, D118)`.

---

### Task 7: 0.76.0

**Files:** Modify `app/build.gradle.kts` (`versionCode = 132`, `versionName = "0.76.0"`).

- [ ] **Step 1:** Bump the version.
- [ ] **Step 2:** Run once each, one at a time, each redirected to its own log with `$?` read:
  `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`. Expected: tests pass; skipped are exactly
  the ten Room classes CLAUDE.md lists; lint clean; the debug APK builds.
- [ ] **Step 3: Commit** — `0.76.0: the plan screen is a timeline (D117, D118)`.

---

## Not built here (the spec's list, and this plan's)

- Days for planned sessions, reminders, a goal; tapping a tile to open its session; Movement's and the letter's
  redesign (part 4 onward).
- A row on the Trainer page for a stopped plan (choice 2), and a drawn marker at the stop (choice 3).
- No release build, push or pull request in this run.

## Phone checks for the owner

1. Trainer → Your plan: the title, "N weeks · … · you're in week N", the past weeks ticked with "N of N planned"
   (and "plus N of your own" for a week with extra sessions), this week's tiles (done filled, open dashed), the
   weeks ahead greyed with their sessions on one line.
2. "Read the trainer's full evaluation" opens and closes the rest; the headline and its date sit above it.
3. The two buttons: Adjust the plan opens the adjust page; the other names the open walk (or says Plan my next
   session) and opens the session form.
4. Evaluate me and plan the weeks ahead → ask: the answer is drawn as the timeline, Keep this plan / Ask again work
   as before. Adjust the plan → Adjust: the new version is drawn the same way, Keep this version / Keep the old one
   work as before.
5. Stop this plan from the adjust page: back on the plan screen, "stopped <day>", the unreached weeks greyed, and
   one button, Evaluate me and plan again.
