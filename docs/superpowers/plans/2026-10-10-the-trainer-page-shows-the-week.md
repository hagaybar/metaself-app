# The Trainer page shows the week — Implementation Plan (D113–D116)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D113–D116. Today's top bar gains the word "Trainer" and its menu a "Trainer" item. The top of the
Trainer page becomes the week: a header with a one-sentence verdict counted by the phone, a week strip (a
column a day, each counted session a block — dark green when it ticked a planned session, light green beyond
the plan — and the open planned sessions dashed in a "To do" column), the shorter-session question under it,
three boxes (steps, sleep against its usual, food against the target), the coach's note (the newest
`week_note`, which every trainer answer and the weekly letter now also return), one main button, then the
waiting session, the kept single-session plan, and four plain rows (Your plan, Weekly letters, Earlier
sessions, About me). Earlier sessions get a page of their own; "Adjust the plan" moves to the plan screen.
Version 0.75.0.

**Decision:** the owner's, 2026-10-10 — `docs/superpowers/specs/2026-10-10-the-trainer-page-shows-the-week-design.md`
(D113, D114, D115, D116). Branch `the-trainer-page-shows-the-week`, already checked out — **do not switch
branches**. No Room change: the strip and the boxes are counted from the week's ledger (D110) every time the
page is read, and the note lives in the preferences DataStore beside About me.

**Architecture:**

```
domain/trainer/WeekLedger.kt     WeekLedger.minutes / .sessions; WeekLedgers.practiceOf (the week's
                                 WeekProgress the ledger itself counts)                           JUnit 5   Task 1
domain/trainer/WeekStrip.kt      NEW, pure: StripBlock, StripDay, WeekStrip(.of, .height),
                                 TrainerWeek(.of) — ledger and strip from one read               JUnit 5   Task 1
ui/trainer/TrainerWeekWording.kt NEW: header, verdict (D115), boxes, legend, rows, note's "when"   JUnit 5   Task 2
domain/trainer/TrainerHome.kt    MainAction + TrainerHome.main (D114 item 5)                      JUnit 5   Task 3
domain/trainer/Trainer.kt        TrainerReply.Answered.weekNote                                   JUnit 5   Task 4
data/ai/TrainerResponse.kt       readWeekNote; every parse carries it                             JUnit 5   Task 4
data/ai/TrainerPrompt.kt         WEEK_NOTE on every path; week_note in every reply schema          JUnit 5   Task 4
data/ai/LetterResponse.kt, LetterPrompt.kt, OpenAiLetterWriter.kt, domain/letter/LetterWriter.kt
                                 the letter's week_note, asked and read                           JUnit 5   Task 4
domain/trainer/WeekNote.kt       NEW, pure: WeekNote, WeekNoteSource, WeekNotes (replaces, shown)  JUnit 5   Task 5
data/trainer/WeekNoteStore.kt    NEW: WeekNoteStore, DataStoreWeekNoteStore; Hilt in AiModule     JUnit 5   Task 5
data/trainer/AskTheTrainer.kt    keeps each answer's note; thisWeek()                             JUnit 5   Tasks 5, 6
data/letter/WriteWeeklyLetter.kt keeps the letter's note                                          JUnit 5   Task 5
ui/screen/trainer/TrainerViewModel.kt   State.week, State.note                                    JUnit 5   Task 6
ui/theme/Colours.kt, MetaSelfTheme.kt   the amber tint (MetaSelfTint.caution)                     JUnit 5   Task 7
ui/screen/trainer/TrainerScreen.kt      the page redrawn (D114)                                   Robolectric Task 7
ui/screen/trainer/EarlierSessionsScreen.kt NEW: the list that was on the page                     Robolectric Task 7
ui/nav/MetaSelfNavHost.kt               Earlier sessions; the strip opens Movement                             Task 7
ui/screen/trainer/EvaluatePlanScreen.kt, EvaluatePlanViewModel.kt  Adjust the plan; read again on return
                                                                                                  both     Task 8
ui/screen/day/DayPager.kt               TodayActions: the word "Trainer" and the menu item        Robolectric Task 9
app/build.gradle.kts                    0.75.0 / 131                                                        Task 10
```

**Tech Stack:** Kotlin 1.9.22, Compose (BOM 2024.02.00, Material 3), DataStore preferences, kotlinx.serialization
JSON builders. JUnit 5 + Truth for everything pure; JUnit 4 + Robolectric only for the Compose render tests
(`TrainerScreenRenderTest`, `EarlierSessionsScreenRenderTest`, `EvaluatePlanScreenRenderTest`,
`TodayActionsRenderTest`), through `ComposeRender`.

---

## Design choices the spec leaves open — settled here

1. **The strip is counted from the ledger's own partition.** `WeekLedgers.practiceOf(monday, today, workouts,
   plans)` returns the very `WeekProgress` the ledger counts its practice from; a block is ticked exactly when
   its session is some tick's `by` — the ledger's rule (D110 choice 2), so a confirmed short session is ticked
   and an open candidate is beyond the plan. `TrainerWeek.of` builds the ledger and the strip from one read, so
   the page cannot disagree with what the trainer was sent.
2. **"A plan running this week" is `ledger.practice != null`** — a kept plan covers the week (D110). The verdict's
   second clause, the legend's three entries and the "To do" column all follow it. A covered week whose plan
   holds no session (an adjustment may leave this week empty, D97) has no second clause.
3. **The scale** is the tallest column's minutes, the "To do" column included (its open sessions stacked); a
   block's height is `max(least, full × minutes / scale)`. The strip is 140 dp, as in the mockup, of which 22 dp
   on top is room for the tick; the least block is 8 dp. A day before or on today with no session shows a 2 dp
   hairline (the mockup's); a day after today shows nothing.
4. **Kinds in words** (the verdict and the main button): walk, run, swim, ride, and "session" for strength and
   other — D115's list, which has no "strength".
5. **Numbers in words** to ten ("Two" … "Ten"), digits above; the minutes clause says "1 minute" for one.
6. **Sleep's difference** is rounded to ten minutes **half away from zero** (`(|d| + 5) / 10 × 10`, signed): 25
   minutes under is "30 min under usual", 4 minutes over is "as usual". Amber only when the rounded difference
   is below zero. The average is written "6 h 50", "7 h", "7 h 05", or "45 min" under an hour.
7. **The boxes' lines**: Steps "8,900" / "a day"; Sleep "6 h 50" / "30 min under usual" (no usual: "a night");
   Food "1,300" / "kcal · target 1,650" (no profile: "kcal"), and "over 3 logged days" ("over 1 logged day")
   when fewer than seven were logged. **A box with no figure is absent** — steps too, though the spec names only
   sleep and food: a null is never drawn as zero (D110).
8. **The legend with no plan** reads "Sessions": the spec gives it one entry but no word, and "Beyond the plan"
   would describe a plan that is not there.
9. **The main button.** Running with an open session this week (`PlanCard.Running.next`): "Plan the open walk",
   with D96's "Next in your plan: …" line kept under it; running with nothing open (or before week 1): "Plan my
   next session"; none or ended: "Evaluate me and plan the weeks ahead", filled. **When the main button is
   Evaluate, "Plan my next session" stays as an outlined button under it** — the spec names one main button and
   says Evaluate becomes the filled one "where it was the outlined one"; without the outlined one, asking for a
   single session with no weekly plan would have no way in. Both open the same forms as today.
10. **"Your plan · week 2 of 2"** is shown only while a plan runs (`PlanCard.Running`); before week 1 it reads
    "Your plan · starts Mon 7 Sep". An ended plan has no row (the plan screen shows only a running plan); its
    count, which the old card showed, goes with the card.
11. **The coach's note** is drawn with its "when" ("Written Sat 09:12" for a trainer answer, "From Sunday's
    letter" — the weekday the letter was written — for the letter) and, under it, the trainer's standing label
    `FROM_TRAINER` ("From the AI trainer · advice, not a measurement"): every AI answer on screen carries it (D4).
12. **Which note is kept.** A note replaces the kept one when it is about a later week, or about the same week
    and written no earlier — so a letter written on Monday about last week never displaces a note already
    written about this week. **Shown only when its week is this week** (`WeekNotes.shown`). A trainer note's week
    is the Monday of the day it was written; the letter's is the letter's week.
13. **Where the note is kept:** the preferences DataStore (`DataStoreWeekNoteStore`, four keys beside About me).
    Not in the backup: it is about this week only, and the next answer writes a new one. Storing it never fails
    an answer: a failed write is swallowed (`runCatching`), like the letter's notification.
14. **Every path asks for it**: `TrainerPrompt.WEEK_NOTE` is the last paragraph of each path's instructions and
    of the letter's; `week_note` is a required string in the plan, feedback, evaluation and adjust schemas
    (adjust gets its own top-level schema; the nested plan schema inside the evaluation stays as it is) and in
    the letter's. The phone reads it leniently: absent, not a string, or blank is no note, and the reply stands.
    The workbench (D106) is not a trainer answer: it never keeps a note.
15. **The page's week is read** whenever this week's sessions or days change, a plan is kept or answered, or the
    page comes to the front (food logged elsewhere shows on return). A failure to read it makes the page
    unreadable and is logged once, as a failure to read the rest does.
16. **Earlier sessions** is a page of its own (`trainer/earlier`) drawing `TrainerHome.earlier` as the page drew
    it, from its own `TrainerViewModel`; with none it says "No reviewed sessions yet."
17. **Adjust the plan on the plan screen**: an outlined button under the running plan. The plan screen reads the
    running plan again whenever it comes to the front, so an adjustment kept there shows on return.
18. **The tint**: dark green is the theme's `primary`, light green its `primaryContainer`, the cards the app's
    default `Card`, the dashed outline and the "40′" `primary`. The amber has no theme equivalent: it is added
    as `MetaSelfTint.caution` — the mockup's light value and a dark twin — and `InkLadderTest` measures the
    text steps on it in both schemes.

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` holds the one lock. **Never run bare
`./gradlew`.** **Before every Gradle command run `free -m`;** under about 4000 MB available, wait and check
again. One Gradle command at a time, never in parallel, never `gradle --stop`. Never pipe a build; redirect
and read the exit code:

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > "$SCRATCH/ms-week.log" 2>&1; echo "exit $?"
```

Read the log (`tail -60 "$SCRATCH/ms-week.log"`). Stage by explicit path; **never `git add -A`**. Commit
trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and **no** session line.

Fixture rule (CLAUDE.md): every figure here is invented and round; the standard day is `TEST_EPOCH_DAY`
(Thursday 3 September 2026), whose Monday is `TEST_EPOCH_DAY - 3`; the standard profile is `aProfile()`.

---

### Task 1: The week strip's model (pure)

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/trainer/WeekLedger.kt`
- Create: `app/src/main/java/com/metaself/app/domain/trainer/WeekStrip.kt`
- Test: `app/src/test/java/com/metaself/app/domain/trainer/WeekStripTest.kt`

- [ ] **Step 1: Write the failing test.** Create `WeekStripTest.kt`:

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
 * D114: the week strip, counted from the ledger's own partition (D110). The week is Monday 31 August 2026 to
 * today, Thursday 3 September ([TEST_EPOCH_DAY]); every session and plan is invented, and round.
 */
class WeekStripTest {

    private val monday = TEST_EPOCH_DAY - 3
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
    private val run20 = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Short run")

    @Test
    fun `each session is a block in its day's column, ticked or beyond as the ledger says`() {
        val sessions = listOf(
            session(1, monday), session(2, monday + 1), session(3, monday + 2),
            session(4, monday + 2, WorkoutKind.SWIM, minutes = 30, hour = 9),
        )

        val week = week(sessions, listOf(kept(plan(walk30, walk30))))

        assertThat(week.strip.days.map { day -> day.blocks.map { it.workoutId to it.ticked } }).containsExactly(
            listOf(1L to true), listOf(2L to true), listOf(3L to false, 4L to false),
            emptyList<Pair<Long, Boolean>>(), emptyList<Pair<Long, Boolean>>(),
            emptyList<Pair<Long, Boolean>>(), emptyList<Pair<Long, Boolean>>(),
        ).inOrder()
        assertThat(week.strip.days.map { it.ticked }).containsExactly(true, true, false, false, false, false, false).inOrder()
        // The same partition as the ledger's: two ticked, two beyond.
        val blocks = week.strip.days.flatMap { it.blocks }
        assertThat(blocks.count { it.ticked }).isEqualTo(week.ledger.practice!!.sessions.count { it.outcome != PlannedOutcome.NOT_DONE })
        assertThat(blocks.count { !it.ticked }).isEqualTo(week.ledger.beyond.kinds.sumOf { it.sessions })
    }

    @Test
    fun `a confirmed shorter session counts as ticked, an open one is beyond the plan`() {
        val short = session(1, monday, minutes = 20)
        val confirmed = week(listOf(short), listOf(kept(plan(walk30), counting = PlanCounting(1, 0, mapOf(1L to true)))))
        val open = week(listOf(short), listOf(kept(plan(walk30))))

        assertThat(confirmed.strip.days.first().blocks.single().ticked).isTrue()
        assertThat(open.strip.days.first().blocks.single().ticked).isFalse()
        assertThat(open.strip.toDo).containsExactly(30)
    }

    @Test
    fun `open planned sessions are in To do only, with their minutes`() {
        val week = week(listOf(session(1, monday)), listOf(kept(plan(run20, walk30))))

        assertThat(week.strip.toDo).containsExactly(20)
        assertThat(week.strip.withPlan).isTrue()
        assertThat(week.strip.days.flatMap { it.blocks }.map { it.minutes }).containsExactly(40)
    }

    @Test
    fun `with no plan there is no To do and nothing is ticked`() {
        val week = week(listOf(session(1, monday), session(2, monday + 1)), emptyList())

        assertThat(week.strip.toDo).isEmpty()
        assertThat(week.strip.withPlan).isFalse()
        assertThat(week.strip.days.flatMap { it.blocks }.none { it.ticked }).isTrue()
    }

    @Test
    fun `days after today are empty, and hidden or uncounted sessions are not drawn`() {
        val sessions = listOf(
            session(1, monday + 4), session(2, monday).copy(hidden = true), session(3, monday).copy(counted = false),
            session(4, monday - 1),
        )

        val strip = week(sessions, emptyList()).strip

        assertThat(strip.days.map { it.epochDay }).containsExactlyElementsIn((monday..monday + 6).toList()).inOrder()
        assertThat(strip.days.all { it.blocks.isEmpty() }).isTrue()
        assertThat(strip.isAfterToday(strip.days[3])).isFalse()
        assertThat(strip.isAfterToday(strip.days[4])).isTrue()
    }

    @Test
    fun `a day's sessions are stacked oldest first`() {
        val strip = week(listOf(session(1, monday, hour = 18), session(2, monday, hour = 7)), emptyList()).strip

        assertThat(strip.days.first().blocks.map { it.workoutId }).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `the scale is the tallest column, To do included`() {
        val sessions = listOf(session(1, monday), session(2, monday + 2, minutes = 30), session(3, monday + 2, minutes = 30, hour = 9))

        assertThat(week(sessions, emptyList()).strip.scaleMinutes).isEqualTo(60)
        assertThat(week(sessions, listOf(kept(plan(run20, run20, run20, run20)))).strip.scaleMinutes).isEqualTo(80)
        assertThat(week(emptyList(), emptyList()).strip.scaleMinutes).isEqualTo(0)
    }

    @Test
    fun `a block's height is its share of the strip, never under the least`() {
        assertThat(WeekStrip.height(minutes = 60, scaleMinutes = 120, full = 100f, least = 8f)).isEqualTo(50f)
        assertThat(WeekStrip.height(minutes = 120, scaleMinutes = 120, full = 100f, least = 8f)).isEqualTo(100f)
        assertThat(WeekStrip.height(minutes = 5, scaleMinutes = 120, full = 100f, least = 8f)).isEqualTo(8f)
        assertThat(WeekStrip.height(minutes = 0, scaleMinutes = 0, full = 100f, least = 8f)).isEqualTo(8f)
    }

    @Test
    fun `the ledger's minutes and sessions are every counted session of the week`() {
        val sessions = listOf(session(1, monday), session(2, monday + 1, minutes = 20), session(3, monday + 2, WorkoutKind.SWIM, minutes = 30))

        val ledger = week(sessions, listOf(kept(plan(walk30)))).ledger

        assertThat(ledger.minutes).isEqualTo(90)
        assertThat(ledger.sessions).isEqualTo(3)
        assertThat(week(emptyList(), emptyList()).ledger.sessions).isEqualTo(0)
    }

    private fun week(workouts: List<Workout>, plans: List<CountedPlan>) =
        TrainerWeek.of(monday, TEST_EPOCH_DAY, workouts, emptyList(), emptyMap(), targetKcal = null, plans = plans)

    private fun plan(vararg sessions: PlannedSession) =
        WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", sessions.toList()) }, "Invented reason.")

    private fun kept(plan: WeeksPlan, counting: PlanCounting = PlanCounting(1, 0, emptyMap())) =
        CountedPlan(Programme(1, 0, ProgrammeAsk(2, 2), null, plan, "a-model", monday, ProgrammeStatus.RUNNING), counting)

    private fun session(id: Long, day: Long, kind: WorkoutKind = WorkoutKind.WALK, minutes: Int = 40, hour: Int = 7) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + hour * HOUR, durationMinutes = minutes,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
    }
}
```

- [ ] **Step 2: Run it; see it fail** (`WeekStrip`, `TrainerWeek`, `minutes`, `sessions` unresolved):
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "*WeekStripTest" > "$SCRATCH/ms-week.log" 2>&1; echo "exit $?"`

- [ ] **Step 3: The ledger's totals and its practice.** In `WeekLedger.kt`, add to `WeekLedger`'s body:

```kotlin
) {
    /** D115: every counted session of the week — ticked ones by their minutes done, and every one beyond. */
    val sessions: Int get() = (practice?.sessions.orEmpty().count { it.outcome != PlannedOutcome.NOT_DONE }) + beyond.kinds.sumOf { it.sessions }

    /** D115: the week's minutes, summed over [sessions]. */
    val minutes: Int get() = practice?.sessions.orEmpty().sumOf { it.minutesDone ?: 0 } + beyond.kinds.sumOf { it.minutes }
}
```

and to `WeekLedgers`:

```kotlin
    /** D114: the week's practice as [of] counts it — the covering plan's week, to today; null when none covers it. */
    fun practiceOf(monday: Long, today: Long, workouts: List<Workout>, plans: List<CountedPlan>): WeekProgress? =
        practiceWeek(plans, monday, minOf(today, monday + 6), workouts)?.second
```

- [ ] **Step 4: The strip.** Create `WeekStrip.kt`:

```kotlin
package com.metaself.app.domain.trainer

import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout

/** D114: one counted session on the week strip; [ticked] when it ticked a planned session (the ledger's practice). */
data class StripBlock(val workoutId: Long, val minutes: Int, val ticked: Boolean)

/** D114: one day's column, its sessions oldest first — drawn from the bottom up. */
data class StripDay(val epochDay: Long, val blocks: List<StripBlock>) {
    val minutes: Int get() = blocks.sumOf { it.minutes }

    /** A tick above the column: some session of the day ticked a planned session. */
    val ticked: Boolean get() = blocks.any { it.ticked }
}

/**
 * D114: the week strip — seven columns, Monday to Sunday — and the open planned sessions' minutes for the
 * "To do" column, which planned sessions are drawn in because they have no day (D94). [withPlan] is whether a
 * kept plan covers the week. Counted from the same partition as the week's ledger (D110). Pure.
 */
data class WeekStrip(val monday: Long, val today: Long, val days: List<StripDay>, val toDo: List<Int>, val withPlan: Boolean) {

    /** The tallest column's minutes, "To do" included: the strip's full height. 0 for an empty week. */
    val scaleMinutes: Int get() = maxOf(days.maxOf { it.minutes }, toDo.sum())

    fun isAfterToday(day: StripDay): Boolean = day.epochDay > today

    companion object {
        /** [practice] is [WeekLedgers.practiceOf]'s: a block is ticked exactly when its session is some tick's `by`. */
        fun of(monday: Long, today: Long, workouts: List<Workout>, practice: WeekProgress?): WeekStrip {
            require(MovementWeek.mondayOf(monday) == monday) { "not a Monday: $monday" }
            val ticked = practice?.ticks.orEmpty().mapNotNull { it.by?.id }.toSet()
            val sessions = workouts
                .filter { !it.hidden && it.counted && it.epochDay in monday..minOf(today, monday + 6) }
                .sortedBy { it.startedAtMillis }
            val days = (0..6).map { offset ->
                val day = monday + offset
                StripDay(day, sessions.filter { it.epochDay == day }.map { StripBlock(it.id, it.durationMinutes, it.id in ticked) })
            }
            return WeekStrip(monday, today, days, practice?.ticks.orEmpty().filter { it.by == null }.map { it.planned.minutes }, practice != null)
        }

        /** A block's height: its share of [full] as its minutes are of [scaleMinutes], never under [least]. */
        fun height(minutes: Int, scaleMinutes: Int, full: Float, least: Float): Float =
            if (scaleMinutes <= 0) least else maxOf(least, full * minutes / scaleMinutes)
    }
}

/** D114: this week as the Trainer page shows it — its ledger (D110) and its strip, counted from one read. */
data class TrainerWeek(val ledger: WeekLedger, val strip: WeekStrip) {
    companion object {
        /** [WeekLedgers.of]'s arguments; the strip is counted from the same practice. */
        fun of(
            monday: Long,
            today: Long,
            workouts: List<Workout>,
            days: List<HealthDay>,
            food: Map<Long, DayTotals>,
            targetKcal: Int?,
            plans: List<CountedPlan>,
        ): TrainerWeek = TrainerWeek(
            WeekLedgers.of(monday, today, workouts, days, food, targetKcal, plans),
            WeekStrip.of(monday, today, workouts, WeekLedgers.practiceOf(monday, today, workouts, plans)),
        )
    }
}
```

- [ ] **Step 5: Run it; see it pass**, and `*WeekLedgersTest` still passes.

- [ ] **Step 6: Commit** `WeekLedger.kt`, `WeekStrip.kt`, `WeekStripTest.kt`:
  `feat(trainer): the week strip's model, from the ledger's own partition (D114)`.

---

### Task 2: What the top of the page says (pure)

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/trainer/TrainerWeekWording.kt`
- Test: `app/src/test/java/com/metaself/app/ui/trainer/TrainerWeekWordingTest.kt`

- [ ] **Step 1: Write the failing test.** Create `TrainerWeekWordingTest.kt` (JUnit 5). Its ledgers are built
  directly (`WeekLedger(monday, true, practice, BeyondPlan(...), FoodContext(...), Recovery(...))`); every
  figure invented and round. Cases:
  - `the header names the week, across a month or within one`: `header(TEST_EPOCH_DAY - 3)` is
    `"THIS WEEK · MON 31 AUG – SUN 6 SEP"`; `header(LocalDate.of(2026, 9, 7).toEpochDay())` is
    `"THIS WEEK · MON 7 – SUN 13 SEP"`.
  - `the verdict's first clause is the week's minutes`: one walk of 40 beyond → `"40 minutes moving this week."`;
    one of 1 → `"1 minute moving this week."`; nothing → `"Nothing recorded yet this week."`.
  - `every row of D115's table`: practice all done → `"… Every planned session done."`; one walk open →
    `"One planned walk still to do."`; one cycle → `"One planned ride still to do."`; one strength →
    `"One planned session still to do."`; two open → `"Two planned sessions still to do."`; no practice → no
    second clause; a covered week with no planned session → no second clause. With no session but one open:
    `"Nothing recorded yet this week. One planned walk still to do."`
  - `numbers are words to ten`: `number(2) == "Two"`, `number(10) == "Ten"`, `number(11) == "11"`.
  - `sleep's difference is rounded to ten minutes, half away from zero`: `sleepDifference(410, 440) == -30`,
    `(415, 440) == -30`, `(445, 420) == 30`, `(424, 420) == 0`, `(415, 420) == -10`.
  - `the boxes`: steps 8,900 → `Box("STEPS", "8,900", "a day")`; sleep 410 with usual 440 →
    `Box("SLEEP", "6 h 50", "30 min under usual", under = true)`; sleep 450 / 430 → `"20 min over usual"`,
    not under; 422 / 420 → `"as usual"`, not under; no usual → `"a night"`, not under; food 1,300 kcal over
    3 days with target 1,650 → `Box("FOOD", "1,300", "kcal · target 1,650", "over 3 logged days")`; 7 days → no
    extra; 1 day → `"over 1 logged day"`; no target → line `"kcal"`.
  - `absent boxes leave the others`: no steps, no sleep, no food → empty; only food → one box.
  - `a night's sleep is hours and minutes`: `sleep(410) == "6 h 50"`, `sleep(420) == "7 h"`, `sleep(425) ==
    "7 h 05"`, `sleep(45) == "45 min"`.
  - `the legend`: with a plan `["Planned, done", "Planned, open", "Beyond the plan"]`; without `["Sessions"]`.
  - `the plan's row`: running week 2 of 2 → `"Your plan · week 2 of 2"`; before week 1 →
    `"Your plan · starts Mon 7 Sep"`; none and ended → null.
  - `the note says when it was written`: a trainer note at Saturday 5 September 09:12 UTC → `"Written Sat 09:12"`;
    a letter note written on Sunday → `"From Sunday's letter"`.
  - `the day letters, and the open minutes`: `dayLetter(monday) == "M"`, `dayLetter(monday + 6) == "S"`,
    `openMinutes(40) == "40′"`.

- [ ] **Step 2: Run it; see it fail.**

- [ ] **Step 3: Write `TrainerWeekWording`:**

```kotlin
package com.metaself.app.ui.trainer

import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PracticeLedger
import com.metaself.app.domain.trainer.WeekLedger
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** What the top of the Trainer page says (D114, D115). Every figure is the week's ledger's (D110). */
object TrainerWeekWording {

    private const val SEP = " · "
    private val DAY_MONTH = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val DAY_ONLY = DateTimeFormatter.ofPattern("EEE d", Locale.US)
    private val WRITTEN = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.US)
    private val NUMBERS = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

    const val TO_DO = "To do"
    const val STRIP = "This week's sessions, day by day"
    const val OPEN_MOVEMENT = "open Movement"
    const val YOUR_LETTERS = "Weekly letters"
    const val EARLIER = "Earlier sessions"
    const val ABOUT_ME = "About me"
    const val NO_EARLIER = "No reviewed sessions yet."

    /** "THIS WEEK · MON 5 – SUN 11 OCT"; across two months, "THIS WEEK · MON 28 SEP – SUN 4 OCT" (invented). */
    fun header(monday: Long): String {
        val from = LocalDate.ofEpochDay(monday)
        val to = from.plusDays(6)
        val first = if (from.month == to.month) from.format(DAY_ONLY) else from.format(DAY_MONTH)
        return ("THIS WEEK$SEP$first – " + to.format(DAY_MONTH)).uppercase(Locale.US)
    }

    /** D115: one sentence, two clauses at most, counted from the week's ledger; never the AI's. */
    fun verdict(ledger: WeekLedger): String = listOfNotNull(minutesClause(ledger), planClause(ledger.practice)).joinToString(" ")

    private fun minutesClause(ledger: WeekLedger): String = when {
        ledger.sessions == 0 -> "Nothing recorded yet this week."
        ledger.minutes == 1 -> "1 minute moving this week."
        else -> "${ledger.minutes} minutes moving this week."
    }

    private fun planClause(practice: PracticeLedger?): String? {
        if (practice == null || practice.sessions.isEmpty()) return null
        val open = practice.sessions.filter { it.outcome == PlannedOutcome.NOT_DONE }
        return when (open.size) {
            0 -> "Every planned session done."
            1 -> "One planned ${noun(open.single().planned.kind)} still to do."
            else -> "${number(open.size)} planned sessions still to do."
        }
    }

    /** Words to ten, digits above. */
    fun number(n: Int): String = NUMBERS.getOrNull(n - 1) ?: n.toString()

    /** D115's kinds: walk, run, swim, ride, session. */
    fun noun(kind: WorkoutKind): String = when (kind) {
        WorkoutKind.WALK -> "walk"
        WorkoutKind.RUN -> "run"
        WorkoutKind.SWIM -> "swim"
        WorkoutKind.CYCLE -> "ride"
        WorkoutKind.STRENGTH, WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "session"
    }

    /** D114: the main button with a planned session open: "Plan the open walk". */
    fun planOpen(kind: WorkoutKind): String = "Plan the open ${noun(kind)}"

    /** D114: one of the three boxes. [under] tints it amber (sleep under its usual). */
    data class Box(val label: String, val figure: String, val line: String, val extra: String? = null, val under: Boolean = false)

    /** Steps, sleep and food, each absent when its figure is (a null is never zero, D110). */
    fun boxes(ledger: WeekLedger): List<Box> = listOfNotNull(
        ledger.beyond.stepsADay?.let { Box("STEPS", thousands(it), "a day") },
        ledger.recovery.sleepMinutes?.let { minutes ->
            val difference = ledger.recovery.usualSleepMinutes?.let { sleepDifference(minutes, it) }
            val line = when {
                difference == null -> "a night"
                difference == 0 -> "as usual"
                difference < 0 -> "${-difference} min under usual"
                else -> "$difference min over usual"
            }
            Box("SLEEP", sleep(minutes), line, under = difference != null && difference < 0)
        },
        ledger.food.kcalADay?.let { kcal ->
            val days = ledger.food.daysLogged
            Box(
                "FOOD", thousands(kcal), "kcal" + (ledger.food.targetKcal?.let { "${SEP}target ${thousands(it)}" } ?: ""),
                extra = if (days < 7) "over $days logged day" + (if (days == 1) "" else "s") else null,
            )
        },
    )

    /** The difference from the usual, rounded to ten minutes, half away from zero. */
    fun sleepDifference(minutes: Int, usual: Int): Int {
        val difference = minutes - usual
        val rounded = (abs(difference) + 5) / 10 * 10
        return if (difference < 0) -rounded else rounded
    }

    /** "6 h 50", "7 h", "7 h 05", "45 min" (invented). */
    fun sleep(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h %02d".format(Locale.US, minutes % 60)
    }

    fun legend(withPlan: Boolean): List<String> =
        if (withPlan) listOf("Planned, done", "Planned, open", "Beyond the plan") else listOf("Sessions")

    /** "Your plan · week 2 of 2"; before week 1, "Your plan · starts Mon 7 Sep" (invented); none without a running plan. */
    fun planRow(card: PlanCard): String? = (card as? PlanCard.Running)?.let { running ->
        "Your plan$SEP" + if (running.weekIndex >= 0) {
            "week ${running.weekIndex + 1} of ${running.programme.ask.weeks}"
        } else {
            "starts " + LocalDate.ofEpochDay(running.programme.startEpochDay ?: 0).format(DAY_MONTH)
        }
    }

    /** D116: "Written Sat 09:12", or "From Sunday's letter" (invented). */
    fun noteWhen(note: WeekNote, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(note.writtenAtMillis).atZone(zone)
        return when (note.source) {
            WeekNoteSource.TRAINER -> "Written " + at.format(WRITTEN)
            WeekNoteSource.LETTER -> "From " + at.format(WEEKDAY) + "'s letter"
        }
    }

    fun dayLetter(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).dayOfWeek.name.take(1)

    fun openMinutes(minutes: Int): String = "$minutes′"

    private fun thousands(value: Int): String = "%,d".format(Locale.US, value)
}
```

  (`WeekNote` and `WeekNoteSource` arrive in Task 5; until then leave `noteWhen` and its test for Task 5's
  commit — write them there.)

- [ ] **Step 4: Run it; see it pass.**

- [ ] **Step 5: Commit** `TrainerWeekWording.kt`, `TrainerWeekWordingTest.kt`:
  `feat(trainer): the week's header, verdict, boxes and rows in words (D114, D115)`.

---

### Task 3: The main button (pure)

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/trainer/TrainerHome.kt`
- Test: `app/src/test/java/com/metaself/app/domain/trainer/TrainerHomeTest.kt`

- [ ] **Step 1: Write the failing test** in `TrainerHomeTest`:

```kotlin
    /** D114: the one main button — the open session's kind, the next session, or an evaluation. Invented. */
    @Test
    fun `the main button is the next thing to do`() {
        val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val run20 = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.STEADY, "Invented line")
        val running = Programme(
            1, 0, ProgrammeAsk(2, 2), null, WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(run20, walk30)) }, "Invented."),
            "a-model", TEST_EPOCH_DAY - 3, ProgrammeStatus.RUNNING,
        )
        val counting = PlanCounting(1, 0, emptyMap())
        fun main(workouts: List<Workout>, today: Long = TEST_EPOCH_DAY) =
            TrainerHome.of(today, NOW, emptyList(), emptyList(), emptyList(), null, running, workouts, counting).main

        assertThat(main(emptyList())).isEqualTo(MainAction.PlanOpen(WorkoutKind.RUN))
        val run = walk(1, TEST_EPOCH_DAY - 1, hour = 7).copy(kind = WorkoutKind.RUN, durationMinutes = 20)
        assertThat(main(listOf(run))).isEqualTo(MainAction.PlanOpen(WorkoutKind.WALK))
        assertThat(main(listOf(run, walk(2, TEST_EPOCH_DAY, hour = 7)))).isEqualTo(MainAction.PlanNext)
        assertThat(main(emptyList(), today = TEST_EPOCH_DAY + 11)).isEqualTo(MainAction.Evaluate)
        assertThat(home().main).isEqualTo(MainAction.Evaluate)
    }
```

  (`walk(id, day, hour)` is the test's existing forty-minute walk; add the imports it needs.)

- [ ] **Step 2: Run it; see it fail.**

- [ ] **Step 3:** In `TrainerHome.kt`, add:

```kotlin
/** D114: the Trainer page's one main button. */
sealed interface MainAction {
    /** A plan runs and this week's first open planned session is of [kind]: "Plan the open walk". */
    data class PlanOpen(val kind: WorkoutKind) : MainAction

    /** A plan runs with nothing open this week, or has not begun: "Plan my next session". */
    data object PlanNext : MainAction

    /** No plan runs, or it has ended: "Evaluate me and plan the weeks ahead". */
    data object Evaluate : MainAction
}
```

  and to `TrainerHome`'s body: `val main: MainAction get() = if (plan is PlanCard.Running) next?.let { MainAction.PlanOpen(it.session.kind) } ?: MainAction.PlanNext else MainAction.Evaluate`.
  Update the class KDoc to D114's order.

- [ ] **Step 4: Run it; see it pass.** **Step 5: Commit** `feat(trainer): the page's main button is the next thing to do (D114)`.

---

### Task 4: Every answer and the letter carry week_note (D116, asked and read)

**Files:**
- Modify: `domain/trainer/Trainer.kt`, `data/ai/TrainerResponse.kt`, `data/ai/TrainerPrompt.kt`,
  `data/ai/LetterResponse.kt`, `data/ai/LetterPrompt.kt`, `data/ai/OpenAiLetterWriter.kt`, `domain/letter/LetterWriter.kt`
- Test: `TrainerResponseTest`, `TrainerPromptTest`, `LetterResponseTest`, `LetterPromptTest`, `OpenAiLetterWriterTest`

- [ ] **Step 1: Write the failing tests.**
  - `TrainerResponseTest`: `every path's reply carries its week_note` — the plan, feedback, evaluation and adjust
    replies with `"week_note":"Invented week note."` added each give `Answered.weekNote == "Invented week note."`;
    `a reply without week_note is still a reply` — the four good replies without it are `Answered` with
    `weekNote == null`, and with `"week_note":3` or `"week_note":"  "` too.
  - `TrainerPromptTest`: `every path asks for week_note last, and its schema requires it` — for each
    `TrainerPath`, `instructions(path)` ends with `TrainerPrompt.WEEK_NOTE`; for each of the four bodies the
    schema's `properties` and `required` contain `week_note`. Update `a feedback reply is asked for as headline,
    note and plan_followed` to `"headline", "note", "plan_followed", "week_note"`, renamed
    `a feedback reply is asked for as headline, note, plan_followed and week_note`. Assert `WEEK_NOTE` contains
    `"at most two sentences about this week as a whole"`.
  - `LetterResponseTest`: `the letter's week_note is read, and a letter without one is still written`.
  - `LetterPromptTest`: the instructions contain `TrainerPrompt.WEEK_NOTE`; the schema requires `week_note`
    beside the six `NAMES` (which stay six).
  - `OpenAiLetterWriterTest`: a reply holding `week_note` comes back as `LetterReply.Written(…, weekNote = …)`.

- [ ] **Step 2: Run them; see them fail.**

- [ ] **Step 3: Implement.**
  - `TrainerReply.Answered<T>(val value: T, val model: String, val weekNote: String? = null)`.
  - `TrainerResponse.readWeekNote(content: String?): String?` — the `week_note` member if it is a JSON string,
    trimmed, null when blank or absent; never throws. Each of the four `parse…` passes `readWeekNote(content(body))`.
  - `TrainerPrompt.WEEK_NOTE`:

```kotlin
    /** D116: asked on every path and in the letter, in the same request — never in a request of its own. */
    internal val WEEK_NOTE = """
        Also give week_note: at most two sentences about this week as a whole, as its ledger shows it,
        following the rules above for using a week's ledger: both parts, what matters most first, recovery
        shaping intensity, food as context, and no figure the app did not count. It is shown on its own,
        apart from the rest of your reply, so it must make sense without it.
    """.trimIndent()
```

    appended as the last paragraph of `instructions(path)`; `"week_note" to string()` added last to
    `PLAN_SCHEMA`, `FEEDBACK_SCHEMA` and `EVALUATION_SCHEMA`; a new `ADJUST_SCHEMA` (`WEEKS_PLAN_SCHEMA`'s three
    members and `week_note`) for `adjustBody`.
  - `LetterResponse.Parsed.Written(texts, model, weekNote: String? = null)`, read with
    `TrainerResponse.readWeekNote`; `LetterReply.Written(texts, model, weekNote: String? = null)`;
    `OpenAiLetterWriter` passes it on. `LetterPrompt.INSTRUCTIONS` ends with `TrainerPrompt.WEEK_NOTE`; its
    `SCHEMA` holds `NAMES` and then `week_note`.

- [ ] **Step 4: Run** `*TrainerResponseTest`, `*TrainerPromptTest`, `*LetterResponseTest`, `*LetterPromptTest`,
  `*OpenAiLetterWriterTest`, `*OpenAiTrainerTest`, `*TrainerWorkbench*`; see them pass.

- [ ] **Step 5: Commit** `feat(trainer): every answer and the letter also give a week_note (D116)`.

---

### Task 5: The newest note is kept

**Files:**
- Create: `domain/trainer/WeekNote.kt`, `data/trainer/WeekNoteStore.kt`
- Modify: `di/AiModule.kt`, `data/trainer/AskTheTrainer.kt`, `data/letter/WriteWeeklyLetter.kt`,
  `ui/trainer/TrainerWeekWording.kt` (`noteWhen`)
- Test: `domain/trainer/WeekNotesTest.kt`, `data/trainer/DataStoreWeekNoteStoreTest.kt`,
  `data/trainer/InMemoryWeekNoteStore.kt` (fake), `AskTheTrainerTest`, `WriteWeeklyLetterTest`,
  `TrainerWeekWordingTest` (`noteWhen`)

- [ ] **Step 1: Write the failing tests.**
  - `WeekNotesTest`: `a note replaces the kept one when it is about a later week, or as late a week written no
    earlier` (null kept → replaces; same week later → replaces; same week earlier → not; earlier week written
    later — Monday's letter about last week — → not); `only this week's note is shown`.
  - `DataStoreWeekNoteStoreTest` (`@TempDir`, as `DataStoreAboutMeStoreTest`): an untouched store has no note; a
    kept note reads back whole (text, moment, week, source); an older one does not replace it.
  - `AskTheTrainerTest`: `each answer keeps its week_note, with the moment and this week` — suggest, feedback,
    evaluate and adjust each scripted with a `weekNote`; after each the store holds
    `WeekNote(text, NOW, MONDAY-of-today, TRAINER)`; `an answer without a note keeps none and still answers`.
  - `WriteWeeklyLetterTest`: `the letter's week_note is kept with its week`, and `a letter without one keeps none`.
  - `TrainerWeekWordingTest`: `the note says when it was written` (Task 2's case).

- [ ] **Step 2: Run them; see them fail.**

- [ ] **Step 3: Implement.**

```kotlin
// domain/trainer/WeekNote.kt
/** D116: what wrote a week_note. */
enum class WeekNoteSource { TRAINER, LETTER }

/** D116: the newest week_note — its text, the moment it was written, the Monday of the week it is about, and what wrote it. */
data class WeekNote(val text: String, val writtenAtMillis: Long, val weekMonday: Long, val source: WeekNoteSource)

/** D116's two rules. Pure. */
object WeekNotes {
    /** [incoming] replaces [kept] when it is about a later week, or about the same week and written no earlier. */
    fun replaces(kept: WeekNote?, incoming: WeekNote): Boolean =
        kept == null || compareValuesBy(incoming, kept, { it.weekMonday }, { it.writtenAtMillis }) >= 0

    /** A week's note is about its week: shown only in the week of [monday]. */
    fun shown(note: WeekNote?, monday: Long): WeekNote? = note?.takeIf { it.weekMonday == monday }
}
```

  `WeekNoteStore` (`val note: Flow<WeekNote?>`, `suspend fun keep(note: WeekNote)` — kept only when
  `WeekNotes.replaces`, inside one `edit`), `NONE`, and `DataStoreWeekNoteStore` over the shared preferences
  store (keys `trainer_week_note`, `trainer_week_note_at`, `trainer_week_note_week`, `trainer_week_note_from`;
  a row missing a key or with an unknown source reads as no note). Bound `@Singleton` in `AiModule` beside About me.

  `AskTheTrainer` gains `private val weekNotes: WeekNoteStore = WeekNoteStore.NONE` as its last parameter, and
  `keepNote(reply.weekNote)` on every `Answered` (suggest, feedback, evaluate, adjust) — before anything else is
  stored, so an answer discarded later still leaves it:

```kotlin
    /** D116: an answer's week_note, about this week; a note is never worth failing a paid answer for. */
    private suspend fun keepNote(text: String?) {
        if (text == null) return
        val monday = MovementWeek.mondayOf(today().toEpochDay())
        runCatching { weekNotes.keep(WeekNote(text, now(), monday, WeekNoteSource.TRAINER)) }
    }
```

  `WriteWeeklyLetter` gains `private val weekNotes: WeekNoteStore = WeekNoteStore.NONE` after `notifier`; on
  `Written`, after the letter is stored: `reply.weekNote?.let { runCatching { weekNotes.keep(WeekNote(it, now(), weekMonday, WeekNoteSource.LETTER)) } }`.

- [ ] **Step 4: Run them; see them pass.** **Step 5: Commit** `feat(trainer): the newest week_note is kept (D116)`.

---

### Task 6: The Trainer page reads the week and the note

**Files:**
- Modify: `data/trainer/AskTheTrainer.kt` (`thisWeek`, `targetKcal`), `ui/screen/trainer/TrainerViewModel.kt`
- Test: `ui/screen/trainer/TrainerViewModelTest.kt`

- [ ] **Step 1: Write the failing tests** in `TrainerViewModelTest`:
  - `this week is counted from the record`: a 40-minute walk on `TEST_EPOCH_DAY` → `state.week!!.ledger.minutes
    == 40`, the strip's Thursday holds its block; a second walk synced while open shows without reopening.
  - `the plan's week ticks on the strip`: the running plan kept as in the existing tests, one walk → its block ticked,
    one 30 in "To do".
  - `only this week's note is shown`: a note about this week → `state.note`; one about last week → null.
  - Update `the recent sessions are read for the last three days` to assert `contains((TEST_EPOCH_DAY - 2) to
    TEST_EPOCH_DAY)` — the page now also reads this week's sessions — and that this week (`TEST_EPOCH_DAY - 3 to
    TEST_EPOCH_DAY`) is read too.

- [ ] **Step 2: Run it; see it fail.**

- [ ] **Step 3: Implement.** In `AskTheTrainer`:

```kotlin
    /** D114: this week for the Trainer page, from [workouts] and [days] as the page observes them. Reads food, the target and the plans. */
    suspend fun thisWeek(day: Long, workouts: List<Workout>, days: List<HealthDay>): TrainerWeek {
        val monday = MovementWeek.mondayOf(day)
        return TrainerWeek.of(monday, day, workouts, days, food.byDay(monday, day), targetKcal(), countedPlans())
    }
```

  (`targetKcal()` is `request`'s existing reading of `CurrentTarget`, moved to a function both use.)

  `TrainerViewModel` gains `weekNotes: WeekNoteStore`; `State` gains `week: TrainerWeek? = null` and
  `note: WeekNote? = null`. Inside `calendarToday.flatMapLatest { day -> … }` the home is combined with

```kotlin
val monday = MovementWeek.mondayOf(day)
combine(
    record.observeWorkouts(monday, day),
    record.observeDays(monday - WeekLedgers.USUAL_DAYS, day),
    plan,
    looked,
) { workouts, days, _, _ -> workouts to days }
    .mapLatest { (workouts, days) -> ask.thisWeek(day, workouts, days) }
```

  before the existing `catch`, so one failure is logged once and makes the page unreadable. `looked` is a counter
  `lookedAt()` bumps. The note: `.combine(weekNotes.note.catch { emit(null) })` → `note = WeekNotes.shown(it, MovementWeek.mondayOf(state.today))`.

- [ ] **Step 4: Run** `*TrainerViewModelTest`, `*AskTheTrainerTest`; see them pass. **Step 5: Commit**
  `feat(trainer): the Trainer page reads this week's ledger, strip and note (D114, D116)`.

---

### Task 7: The Trainer page redrawn, and Earlier sessions a page of its own

**Files:**
- Modify: `ui/theme/Colours.kt`, `ui/theme/MetaSelfTheme.kt`, `ui/screen/trainer/TrainerScreen.kt`,
  `ui/nav/MetaSelfNavHost.kt`, `res/values/strings.xml`, `ui/trainer/ProgrammeWording.kt`
- Create: `ui/screen/trainer/EarlierSessionsScreen.kt`
- Test: `ui/theme/InkLadderTest.kt`, `ui/screen/trainer/TrainerScreenRenderTest.kt` (rewritten),
  `ui/screen/trainer/EarlierSessionsScreenRenderTest.kt`, `ui/trainer/ProgrammeWordingTest.kt`

- [ ] **Step 1: Write the failing tests.**
  - `InkLadderTest`: `every text step clears AA on the caution tint, in both schemes` — `onSurface`,
    ink two (`MetaSelfLightInkTwo` / `MetaSelfDarkInkTwo`) and `onSurfaceVariant` on `MetaSelfLightCaution` / `MetaSelfDarkCaution` ≥ 4.5.
  - `TrainerScreenRenderTest`, rewritten for D114 (JUnit 4, Robolectric):
    - `the page runs in D114's order`: with a waiting session, a kept plan, a running plan and a note:
      header → verdict → `"M"` (the strip) → `"STEPS"` → the note → the main button → `WAITING FOR YOUR WORDS` →
      `YOUR KEPT PLAN` → `Your plan · week 1 of 2` → `Weekly letters` → `Earlier sessions` → `About me`
      (`isDrawnBefore` pairwise). No `ABOUT ME` card, no `EARLIER SESSIONS` heading, no `Adjust the plan`.
    - `the main button's three cases`: open walk → `"Plan the open walk"` calls `onPlan`; all done →
      `"Plan my next session"`; no plan → `"Evaluate me and plan the weeks ahead"` calls `onEvaluate`, and an
      outlined `"Plan my next session"` under it calls `onPlan`.
    - `the shorter-session question is still answerable, under the strip`: `"Easy walk, 30 min"`,
      `"Walking, 20 min — count it for this?"`, `"Yes"`, `"No"` in order, after `"M"` and before `"STEPS"`; Yes
      answers `5L to true`; while answering both are disabled.
    - `the strip opens Movement`: `clickDescribed(TrainerWeekWording.STRIP)` calls `onMovement`.
    - `the legend and To do follow the plan`: with a plan, the three legend entries and `"To do"` and `"30′"`;
      without, `"Sessions"` and no `"To do"`.
    - `the boxes, and an absent one`: steps / sleep / food lines drawn; with no sleep figure no `"SLEEP"`.
    - `the coach's note with when it was written, and none without one`.
    - `each row opens what it names`: `Your plan · …` → `onSeePlan`; `Weekly letters` → `onLetters`;
      `Earlier sessions` → `onEarlier`; `About me` → `onAboutMe`; with no plan no `Your plan` row.
    - Keep: the title and way back, the waiting session, the kept plan, the unreadable record.
  - `EarlierSessionsScreenRenderTest`: the moved `earlier sessions show their name, date and line, and open`
    test, and `with none, it says so`.
  - `ProgrammeWordingTest`: drop the tests of `cardHeading`, `weekDates`, `endedHeading` and `endedLine`, whose
    only caller was the old card.

- [ ] **Step 2: Run them; see them fail.**

- [ ] **Step 3: Implement.**
  - The tint: `MetaSelfLightCaution = Color(0xFFFBEFD9)` (the mockup's) and `MetaSelfDarkCaution = Color(0xFF4A3B1F)`,
    a `LocalMetaSelfCaution` provided by `MetaSelfTheme` from `useDarkTheme`, read through `MetaSelfTint.caution`.
  - `TrainerScreen(state, onBack, onReview, onPlan, onOpenKept, onAboutMe, onEvaluate, onSeePlan, onLetters,
    onEarlier, onMovement, modifier, onAnswer)` — `onAdjust` goes. Top to bottom, inside `home != null`:
    `WeekHeader` (header in `labelSmall`, verdict in `headlineMedium`, the display face), `StripCard` (a `Card`:
    the strip — a 140 dp `Row` of seven or eight weighted columns, each a bottom-aligned `Column` with the tick
    (`Icons.Filled.Check`, `primary`) over its blocks, newest on top, `primary` or `primaryContainer`, rounded 6 dp;
    a dashed `primary` outline drawn with `drawBehind` for each open minutes in "To do"; the day letters under,
    today's `SemiBold` in `onSurface`; the legend in a `FlowRow`; the whole strip `clickable(role = Button,
    onClickLabel = OPEN_MOVEMENT)` with `contentDescription = STRIP`; then each open candidate of this week:
    the planned title, the candidate line, Yes / No (unchanged `CandidateRow`), and `refused` under them),
    `Boxes` (a `Row` of weighted `Card`s, `MetaSelfTint.caution` when `under`, text in `onSurface`), the note
    (`bodyLarge`, then `noteWhen` and `FROM_TRAINER` in `bodySmall`), the main button (and the outlined Plan
    my next session when it is Evaluate), the waiting card, the kept plan, then `Rows` (each a 48 dp
    `clickable(role = Button)` row with a trailing "›", separated by `HorizontalDivider`s).
    `state.week == null` draws everything from the main button down.
  - `EarlierSessionsScreen(state, onBack, onReview)` — the list as the page drew it, titled "Earlier sessions",
    with `NO_EARLIER` when empty.
  - Nav: `Destination.EarlierSessions("trainer/earlier")`; the Trainer page wires `onEarlier`, and `onMovement`
    to `Destination.Movement.route` (Movement opens on this week); the Earlier sessions page has its own
    `TrainerViewModel` with `lookedAt` on resume.
  - Remove the strings and wording only the old page used: `trainer_about_me`, `trainer_earlier`,
    `trainer_evaluate_again`, `ProgrammeWording.cardHeading`, `weekDates`, `endedHeading`, `endedLine`.
    (`trainer_adjust_plan` stays: Task 8 uses it on the plan screen.)

- [ ] **Step 4: Run** `*TrainerScreenRenderTest`, `*EarlierSessionsScreenRenderTest`, `*InkLadderTest`,
  `*ProgrammeWordingTest`; see them pass. **Step 5: Commit**
  `feat(trainer): the Trainer page shows the week (D114)`.

---

### Task 8: Adjust the plan, from the plan screen

**Files:**
- Modify: `ui/screen/trainer/EvaluatePlanScreen.kt`, `ui/screen/trainer/EvaluatePlanViewModel.kt`, `ui/nav/MetaSelfNavHost.kt`
- Test: `EvaluatePlanScreenRenderTest`, `EvaluatePlanViewModelTest`

- [ ] **Step 1: Write the failing tests.** Render: `the running plan offers Adjust the plan` — opened on a
  running plan, `"Adjust the plan"` is a button and calls `onAdjust`; the form and an answer do not show it.
  View model: `back on the plan screen, the running plan is read again` — opened on running, the plan adjusted
  and kept in the fake store, `lookedAt()` → `state.running` is the new version.

- [ ] **Step 2: Run them; see them fail.**

- [ ] **Step 3: Implement.** `EvaluatePlanScreen(…, onAdjust: () -> Unit = {})`; in the running branch, after
  `WeeksPlanView`, `OutlinedButton(onClick = onAdjust, Modifier.fillMaxWidth()) { Text(stringResource(R.string.trainer_adjust_plan)) }`.
  `EvaluatePlanViewModel.lookedAt()`: when opened on running and not loading, read `ask.running()` and its
  evaluation again (the init's own read, made a function). Nav: `onAdjust` → `Destination.AdjustPlan.route`, and
  `LifecycleResumeEffect` → `lookedAt()`.

- [ ] **Step 4: Run them; see them pass.** **Step 5: Commit** `feat(trainer): Adjust the plan moves to the plan screen (D114)`.

---

### Task 9: The Trainer from Today (D113)

**Files:**
- Modify: `ui/screen/day/DayPager.kt`, `ui/nav/MetaSelfNavHost.kt`
- Test: `ui/screen/day/TodayActionsRenderTest.kt`

- [ ] **Step 1: Write the failing test** (JUnit 4, Robolectric): `the bar says Trainer, beside the menu, and it
  opens the Trainer` — `TodayActions` drawn, `"Trainer"` is a button, clicking it calls `onOpenTrainer`;
  `the menu lists Trainer after Movement` — after `clickDescribed("More")`, `"Movement"` is drawn before the
  second `"Trainer"` and clicking that item calls `onOpenTrainer` too.

- [ ] **Step 2: Run it; see it fail.**

- [ ] **Step 3: Implement.** Move the bar's actions out of `DayPagerOn` into
  `@Composable internal fun TodayActions(onOpenTrainer, onOpenWeight, onOpenMovement, onOpenProfile, onOpenManager, onOpenSettings)`:
  a `TextButton` "Trainer" (`R.string.trainer_open`) before the menu's `IconButton`, and a `DropdownMenuItem`
  "Trainer" after "Movement". `DayPager` and `DayPagerOn` gain `onOpenTrainer: () -> Unit = {}`; the nav passes
  `navController.navigate(Destination.Trainer.route)`.

- [ ] **Step 4: Run it; see it pass.** **Step 5: Commit** `feat(today): the Trainer from Today's bar and menu (D113)`.

---

### Task 10: 0.75.0, the suite and the build

- [ ] **Step 1:** `app/build.gradle.kts`: `versionCode = 131`, `versionName = "0.75.0"`.
- [ ] **Step 2:** Run once each, one at a time, with `free -m` before each:
  `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`. Expected `exit 0` each; exactly the ten Room
  classes CLAUDE.md names skipped: `grep -l 'skipped="[1-9]' app/build/test-results/testDebugUnitTest/*.xml`.
- [ ] **Step 3: Commit** `app/build.gradle.kts`: `0.75.0: the Trainer page shows the week (D113-D116)`.
- [ ] **Step 4: Phone checks to hand the owner** (the release build is the controller's, `~/bin/ms-release`):
  Today's bar shows "Trainer" beside the menu and opens the page; the menu lists Trainer after Movement; the page
  opens on the week's header and verdict; the strip shows this week's sessions (dark green with a tick for planned
  ones done, light green for the rest), a dashed "To do" for open planned sessions, today's letter bold; tapping
  the strip opens Movement on this week; a shorter session's "count it for this?" is under the strip and Yes/No
  still work; the three boxes match the week, sleep amber when under its usual; after any trainer answer the
  coach's note appears with "Written …", and after Sunday's letter "From Sunday's letter"; Earlier sessions opens
  the old list; See the plan → Adjust the plan works, and the plan screen shows the kept adjustment on return.

---

## Self-review against the spec

- **D113**: the word "Trainer" beside the menu, a menu item after Movement (Task 9); Movement's step bar, menu
  and own Trainer button untouched.
- **D114 order**: header + verdict, strip card (with the shorter-session question), boxes, note, main button,
  waiting, kept plan, rows (Task 7 order test). The old plan card, About me's old place and the "Weekly letters"
  heading go (Task 7).
- **Strip**: a block per counted session in its day, oldest at the bottom, proportional with a least height,
  tallest column fills (Task 1 tests + Task 7 drawing); ticked dark green with a tick, beyond light green,
  confirmed short ticked (Task 1); open planned dashed in "To do" only, labelled with minutes (Tasks 1, 7); days
  after today empty, today bold (Tasks 1, 7); legend three entries or one (Tasks 2, 7); tap opens Movement
  (Task 7).
- **Boxes**: steps a day; sleep average with difference rounded to ten, "as usual", amber only under, no usual →
  average alone, no average → absent; food kcal and target, "over N logged days"; absent boxes leave the others
  (Task 2; Task 7 draws). Every figure the ledger's (Task 1's `TrainerWeek.of`).
- **Note shown** with when; none → nothing (Task 7); **main button** three cases (Tasks 3, 7); **rows** (Tasks 2, 7);
  Earlier sessions a page (Task 7); Adjust on the plan screen (Task 8).
- **D115**: minutes clause, empty week, every table row, words to ten, no praise (Task 2).
- **D116**: asked on every path and the letter in the same request (Task 4); parsed from each (Task 4); kept with
  time and source, discarded answers still leave it (stored at reply time, Task 5); an earlier week's note not
  shown (Tasks 5, 6); a reply without it valid (Task 4).
- **Not built**: the plan screen's redesign, Movement's, the letter's; conversation, goals, reminders; tapping a
  block. No Room change; no privacy change (week_note is a reply, nothing more is sent).
- **Types**: `StripBlock`, `StripDay`, `WeekStrip`, `TrainerWeek`, `WeekLedgers.practiceOf`, `WeekLedger.minutes`,
  `WeekLedger.sessions`, `MainAction`, `TrainerHome.main`, `TrainerReply.Answered.weekNote`,
  `TrainerResponse.readWeekNote`, `TrainerPrompt.WEEK_NOTE`, `WeekNote`, `WeekNoteSource`, `WeekNotes`,
  `WeekNoteStore`, `AskTheTrainer.thisWeek`, `MetaSelfTint.caution`, `TodayActions` — named the same in every task.
