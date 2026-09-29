# The trainer evaluates and plans the weeks ahead — Implementation Plan (D93–D98)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D93–D98. The Trainer screen offers **Evaluate me and plan the weeks ahead**: a form (2 · 4 · 6
weeks; 2 · 3 · 4 · 5 sessions a week; optional words) sends one request and gets back an evaluation
(headline, going well, to work on, since last time) and a weekly plan (weeks of sessions, each with a
kind, minutes, an effort and a line). **Keep this plan** makes it the running plan from a Monday. The
phone ticks planned sessions off from the record, with no AI; the plan card shows this week. **Plan my
next session** is pre-filled from, and tells the trainer about, the next planned session; feedback is
told which planned session a session ticked. **Adjust the plan** rewrites what is left (end date fixed,
old version kept); **Stop this plan** ends it; an ended plan shows its count for 14 days. Room version
10 adds `trainer_programmes`; the backup becomes format 8. Version 0.68.0.

**Decision:** the owner's, 2026-09-29 — `docs/superpowers/specs/2026-09-29-the-trainer-evaluates-and-plans-weeks-design.md`
(D93 amends D84; D96 amends D86 and D87; D97 amends D85; D98 amends D88's backup).

**Architecture:**

```
domain/trainer/Programme.kt             the vocabulary: ProgrammeAsk, PlannedSession, WeeksPlan,
                                        Evaluation, Programme, PlannedTick, LastEvaluation              JUnit 5
domain/trainer/ProgrammeCalendar.kt     pure: start rule, last day, week index, ended, ended shown      JUnit 5
domain/trainer/PlanProgress.kt          pure: ticks from the record (D95); Programmes helpers            JUnit 5
domain/trainer/NextInPlan.kt            pure: the plan form's pre-fill (D96)                             JUnit 5
domain/trainer/Trainer.kt               TrainerQuestion gains Evaluate, Adjust, and `planned` on
                                        Plan and Review; the Trainer port gains evaluate and adjust       JUnit 5 (pinned fields)
data/ai/TrainerPrompt.kt                two new bodies, schemas and instructions; `planned` sent         JUnit 5
data/ai/TrainerResponse.kt              read, check and encode an evaluation and a weeks plan            JUnit 5
data/ai/OpenAiTrainer.kt                the two calls                                                     JUnit 5 + MockWebServer
data/trainer/TrainerEntities.kt, TrainerDao.kt   + trainer_programmes                                     CI (Room)
data/day/ProgrammeMigration.kt          MIGRATION_9_10, verbatim from 10.json                            tools/check-migration-9-10.py + MigrationTest (CI)
data/trainer/ProgrammeStore.kt          port + RoomProgrammeStore + mapping                              JUnit 5 (proxy) + HealthRecordStoreTest (CI)
domain/backup/Backup.kt + data/backup/BackupRepository.kt + ui/settings/BackupWording.kt   format 8   JUnit 5 + BackupRoundTripTest (CI)
data/trainer/AskTheTrainer.kt           evaluate, keep, adjust, keep adjusted, stop, running, next       JUnit 5 (fakes)
domain/trainer/TrainerHome.kt           + PlanCard (running / ended / none) and the next planned         JUnit 5
ui/trainer/ProgrammeWording.kt          every sentence the plan's screens build                          JUnit 5
ui/screen/trainer/*                     Trainer screen card; EvaluatePlan and AdjustPlan screens + VMs;
                                        PlanSession pre-fill                                              JUnit 5 VM + Robolectric render
ui/nav/MetaSelfNavHost.kt, di/DataModule.kt   two destinations; the store and the migration
```

**Tech Stack:** Kotlin, Room (schema v10, hand-written migration), kotlinx.serialization, OkHttp via the
shared `OpenAiCall`, Compose Material 3 (`FilterChip` + `FlowRow` as `PlanSessionScreen` uses them), Hilt,
JUnit 5 + Truth, `okhttp3.mockwebserver`, Robolectric (JUnit 4) for render tests. No new dependency.

**Red lines (stop and report if crossed):**

- **Only what D84 + D93 list leaves the phone, and only on a tap.** The two new requests carry D84's
  record plus the form or the adjust words, the start date, the last kept evaluation with its plan and
  done-counts (evaluate), or the running plan with its done-counts (adjust). Nothing from `init`, a flow
  or a background job; `nextPlanned()` and `running()` read only. `TrainerRequestTest` and
  `TrainerPromptTest` pin the shapes.
- **An AI answer is never shown as a measurement (D4).** The evaluation and every plan are under "From
  the AI trainer · advice, not a measurement". **Ticks and done-counts are counted on the phone** and
  handed to the model, never asked of it.
- **The migration is the exported schema's own SQL**, character for character, checked by
  `tools/check-migration-9-10.py` here and `MigrationTest` in CI. `app/schemas` gains `10.json` only.
- **D8:** a failed call leaves the form as it was and says why in `TrainerWording.failure`'s sentences; a
  failed write is said (`ActionRefused`) and logged; nothing throws upwards. The problem log gets a
  failure's kind, **never the owner's words or the model's answer**.
- **CI-only database tests go inside `HealthRecordStoreTest`, `MigrationTest` or `BackupRoundTripTest`**,
  so the local skip list stays at exactly the ten classes `CLAUDE.md` names.
- **No early return out of an inline composable** (`return@Column` and kin); `InlineComposableReturnGuardTest`
  fails the build. Branch with `when`/`if`.
- **Anonymisation:** every figure, date and word in a test or comment is invented and round, built on
  `TEST_EPOCH_DAY` (Thursday 3 September 2026) and `aProfile()`. Nothing is copied from the mock-ups
  (`build/eval-canvas`, private) but their wording. No real session, weight or rate anywhere.
- **Never `git add -A`; never stage `app/.settings/*`, `build/` or `tools/__pycache__`; never bare
  `./gradlew`; never pipe a build whose result is reported.** No push. The release APK is the
  controller's (`~/bin/ms-release`).

## Design questions the spec does not answer — settled here

1. **`stoppedEpochDay` is set whenever a plan stops running**, whatever the reason — Stop (STOPPED), a
   newer plan kept (REPLACED), an adjusted version kept (ADJUSTED). D98 said "null unless stopped"; but
   D93's "how many of its sessions were done, week by week" needs the day a replaced plan stopped
   counting, or its later weeks would be ticked by the next plan's sessions. Task 0 amends D98's line.
2. **The last evaluation (D93, D98)** is the newest row with an evaluation whose status is not OFFERED.
   Its plan, as sent, is the **newest kept version in its chain** (a row whose `replacesId` names it and
   whose status is not OFFERED, followed forward) — an adjusted plan is what ran. Done-counts are D95's
   over that version's weeks, up to `min(stoppedEpochDay ?: today, last day)`, one number per week that
   had begun. Its date is the evaluation row's creation day in the phone's zone.
3. **An adjusted version carries no evaluation** (`evaluation` null). "See the plan" shows the
   evaluation of the chain: the version's own, else the one it replaces, and so on back.
4. **Adjusting before week 1 starts** (kept Friday to Sunday) rewrites every week: week index 0, nothing
   ticked, `max_this_week` = week 1's planned count. Adjusting after the last day is not offered (the
   card is Ended).
5. **"This week allowed as many sessions as are not yet ticked"** means the old week's planned count minus
   its ticked count. The composed version's current week is the ticked planned sessions, in plan order,
   followed by the model's; its focus is the model's.
6. **Keep the old one** stores nothing more: the new version stays an OFFERED row (a few hundred bytes).
   **Keep this version** requires the old one still RUNNING; otherwise the write refuses (`ActionRefused.NOTHING_CHANGED`).
7. **A planned session's kind** is one of the record's kinds except "unrecognised"; the reply says walk,
   run, cycle, swim, strength or other. A walk ticks a planned walk (treadmill or outdoors — the record
   does not tell them apart).
8. **The session line under a tick:** "Done Mon · Walking, 32 min" — the day heading and the session's
   name and minutes (invented figures).
9. **Plan my next session's pre-fill** happens once, when the form opens empty; a form the owner has
   already touched is left alone. The form shows "Next in your plan: steady walk, 40 min" above the rows.
10. **Privacy lines:** the evaluate form's names what D93 adds ("your last evaluation and how its plan
    went"); the adjust page's names "your plan, how it has gone, and your words"; the single-session form
    names "the next session in your weekly plan" only when there is one.
11. **Room store writes** for keep, keep-adjusted and stop run in one transaction each.
12. **The restore confirmation** names weekly plans only when either side holds some, as the health
    record and the trainer's plans are named ("…, 2 trainer plans, 1 session review and 1 weekly plan").

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` and `~/bin/ms-release` share one lock; a
finished build's daemon lingers up to ten minutes. **Before every Gradle command run `free -m`;** under
about 4000 MB available (6000 MB before `ms-release`), wait and check again. Never `gradlew --stop`.
One build at a time; subagents build one at a time.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-weeks.log 2>&1; echo "exit $?"
```

Read the result from the log and from `app/build/test-results/testDebugUnitTest/*.xml`, never from a
pipe. SQLite classes skip locally (the ten in `CLAUDE.md`); report them as skipped, not passed.

All paths below are under `app/src/main/java/com/metaself/app/` (main) or
`app/src/test/java/com/metaself/app/` (test) unless written in full.

---

### Task 0: D98's line about the stop day

**Files:** Modify `docs/superpowers/specs/2026-09-29-the-trainer-evaluates-and-plans-weeks-design.md`

- [ ] **Step 1:** In D98's first bullet replace `stoppedEpochDay (null unless stopped)` with
  `stoppedEpochDay (the day it stopped running — stopped, replaced, or adjusted — else null)`, and in the
  "last evaluation" bullet replace `Its plan's counts are D95's, over the weeks it ran.` with
  `Its plan is the newest kept version of it, and that plan's counts are D95's, over the weeks it ran,
  up to the day it stopped running.`
- [ ] **Step 2: Commit** the spec: `docs(spec): a plan's stop day is kept whatever stopped it (D98)`.

---

### Task 1: The vocabulary, and the calendar

**Files:**
- Create: `domain/trainer/Programme.kt`, `domain/trainer/ProgrammeCalendar.kt`
- Test: `domain/trainer/ProgrammeCalendarTest.kt`

- [ ] **Step 1: Failing test** `domain/trainer/ProgrammeCalendarTest.kt`:

```kotlin
package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** D94, D97. TEST_EPOCH_DAY is Thursday 3 September 2026; its Monday is 31 August (20_696). */
class ProgrammeCalendarTest {

    private val monday = TEST_EPOCH_DAY - 3

    @Test
    fun `kept Monday to Thursday starts this week, Friday to Sunday the next`() {
        (0L..3L).forEach { assertThat(ProgrammeCalendar.startFor(monday + it)).isEqualTo(monday) }
        (4L..6L).forEach { assertThat(ProgrammeCalendar.startFor(monday + it)).isEqualTo(monday + 7) }
    }

    @Test
    fun `a plan ends on the Sunday of its last week`() {
        assertThat(ProgrammeCalendar.lastDay(monday, 4)).isEqualTo(monday + 27)
    }

    @Test
    fun `a day's week is counted from the start, before it negative`() {
        assertThat(ProgrammeCalendar.weekIndex(monday, monday)).isEqualTo(0)
        assertThat(ProgrammeCalendar.weekIndex(monday, monday + 6)).isEqualTo(0)
        assertThat(ProgrammeCalendar.weekIndex(monday, monday + 7)).isEqualTo(1)
        assertThat(ProgrammeCalendar.weekIndex(monday, monday - 1)).isEqualTo(-1)
    }

    @Test
    fun `an ended plan is shown for fourteen days after its last day`() {
        val last = ProgrammeCalendar.lastDay(monday, 2)
        assertThat(ProgrammeCalendar.ended(monday, 2, last)).isFalse()
        assertThat(ProgrammeCalendar.ended(monday, 2, last + 1)).isTrue()
        assertThat(ProgrammeCalendar.endedShown(monday, 2, last)).isFalse()
        assertThat(ProgrammeCalendar.endedShown(monday, 2, last + 1)).isTrue()
        assertThat(ProgrammeCalendar.endedShown(monday, 2, last + 14)).isTrue()
        assertThat(ProgrammeCalendar.endedShown(monday, 2, last + 15)).isFalse()
    }

    @Test
    fun `the form's answers are two, four or six weeks and two to five sessions`() {
        assertThrows<IllegalArgumentException> { ProgrammeAsk(3, 3) }
        assertThrows<IllegalArgumentException> { ProgrammeAsk(4, 6) }
        assertThat(ProgrammeAsk(6, 5).weeks).isEqualTo(6)
    }

    @Test
    fun `a planned session is a known kind of five to 180 minutes`() {
        assertThrows<IllegalArgumentException> { PlannedSession(WorkoutKind.UNRECOGNISED, 30, PlannedEffort.EASY, "w") }
        assertThrows<IllegalArgumentException> { PlannedSession(WorkoutKind.WALK, 4, PlannedEffort.EASY, "w") }
        assertThrows<IllegalArgumentException> { PlannedSession(WorkoutKind.WALK, 181, PlannedEffort.EASY, "w") }
    }

    @Test
    fun `a new plan fits the weeks and sessions asked, an adjusted rest may leave this week empty`() {
        val one = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk")
        val twoWeeks = WeeksPlan("t", listOf(PlanWeek("a", listOf(one, one)), PlanWeek("b", listOf(one))), "y")
        assertThat(twoWeeks.fits(ProgrammeAsk(2, 2))).isTrue()
        assertThat(twoWeeks.fits(ProgrammeAsk(4, 2))).isFalse()
        assertThat(twoWeeks.copy(weeks = listOf(PlanWeek("a", emptyList()), PlanWeek("b", listOf(one)))).fits(ProgrammeAsk(2, 2))).isFalse()
        assertThat(twoWeeks.fits(ProgrammeAsk(2, 3))).isTrue()

        val rest = WeeksPlan("t", listOf(PlanWeek("now", emptyList()), PlanWeek("next", listOf(one))), "y")
        assertThat(rest.fitsRest(weeksLeft = 2, perWeek = 3, thisWeekMax = 0)).isTrue()
        assertThat(rest.fitsRest(weeksLeft = 3, perWeek = 3, thisWeekMax = 0)).isFalse()
        assertThat(twoWeeks.fitsRest(weeksLeft = 2, perWeek = 3, thisWeekMax = 1)).isFalse()
        assertThat(rest.fitsRest(weeksLeft = 0, perWeek = 3, thisWeekMax = 0)).isFalse()
    }
}
```

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.trainer.ProgrammeCalendarTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement** `domain/trainer/Programme.kt`:

```kotlin
package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.WorkoutKind

/** D93's form: how many weeks, how many sessions a week the owner can manage, and optional words. */
data class ProgrammeAsk(val weeks: Int, val perWeek: Int, val words: String = "") {
    init {
        require(weeks in WEEKS) { "a plan is 2, 4 or 6 weeks, not $weeks" }
        require(perWeek in PER_WEEK) { "2 to 5 sessions a week, not $perWeek" }
    }

    companion object {
        val WEEKS = listOf(2, 4, 6)
        val PER_WEEK = 2..5
    }
}

/** D94: how hard a planned session is meant to be. Stored and sent by name. */
enum class PlannedEffort { EASY, STEADY, PUSH }

/** One planned session (D94): no day — it may be done on any day of its week. */
data class PlannedSession(val kind: WorkoutKind, val minutes: Int, val effort: PlannedEffort, val what: String) {
    init {
        require(kind != WorkoutKind.UNRECOGNISED) { "a planned session is of a known kind" }
        require(minutes in MINUTES) { "a planned session is 5 to 180 minutes, not $minutes" }
    }

    companion object {
        val MINUTES = 5..180
    }
}

data class PlanWeek(val focus: String, val sessions: List<PlannedSession>)

/** A plan of weeks as the model gave it, or as an adjustment composed it (D94, D97). Advice (D4). */
data class WeeksPlan(val title: String, val weeks: List<PlanWeek>, val why: String) {

    /** D94: as many weeks as asked, each with one to [ProgrammeAsk.perWeek] sessions. */
    fun fits(ask: ProgrammeAsk): Boolean = weeks.size == ask.weeks && weeks.all { it.sessions.size in 1..ask.perWeek }

    /** D97: this week and the weeks after — this week up to [thisWeekMax] sessions, none allowed; later weeks one to [perWeek]. */
    fun fitsRest(weeksLeft: Int, perWeek: Int, thisWeekMax: Int): Boolean =
        weeksLeft >= 1 && weeks.size == weeksLeft &&
            weeks.first().sessions.size in 0..thisWeekMax &&
            weeks.drop(1).all { it.sessions.size in 1..perWeek }
}

/** D94: where the owner stands. [sinceLast] is "" when no earlier evaluation was sent. Advice (D4). */
data class Evaluation(val headline: String, val goingWell: String, val toWorkOn: String, val sinceLast: String)

/** One evaluate reply (D94). */
data class EvaluationAndPlan(val evaluation: Evaluation, val plan: WeeksPlan)

/** D98. There is no ENDED: a RUNNING plan past its last Sunday is ended by date. */
enum class ProgrammeStatus { OFFERED, RUNNING, REPLACED, ADJUSTED, STOPPED }

/**
 * One stored answer (D98): an evaluation with its plan, or an adjusted version of a plan ([evaluation]
 * null, [replacesId] the version it was made from). [startEpochDay] is null until kept.
 * [stoppedEpochDay] is the day it stopped running, whatever stopped it (design question 1).
 */
data class Programme(
    val id: Long,
    val createdAtMillis: Long,
    val ask: ProgrammeAsk,
    val evaluation: Evaluation?,
    val plan: WeeksPlan,
    val model: String,
    val startEpochDay: Long? = null,
    val status: ProgrammeStatus = ProgrammeStatus.OFFERED,
    val stoppedEpochDay: Long? = null,
    val replacesId: Long? = null,
)

/** A planned session with its week, counted from 1 (D96). */
data class PlannedTick(val week: Int, val session: PlannedSession)

/** D93: the last kept evaluation, the plan that ran with it, and how many of its sessions were done each week. */
data class LastEvaluation(val epochDay: Long, val evaluation: Evaluation, val plan: WeeksPlan, val doneByWeek: List<Int>)
```

`domain/trainer/ProgrammeCalendar.kt`:

```kotlin
package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.MovementWeek

/** D94, D97: a plan's weeks are Monday to Sunday, as everywhere else (D74). Pure. */
object ProgrammeCalendar {

    /** How long an ended plan's card stays (D97). */
    const val ENDED_SHOWN_DAYS = 14

    /** Kept Monday to Thursday: this week's Monday; Friday to Sunday: the next Monday. */
    fun startFor(keptOn: Long): Long {
        val monday = MovementWeek.mondayOf(keptOn)
        return if (keptOn - monday <= 3) monday else monday + 7
    }

    fun lastDay(start: Long, weeks: Int): Long = start + 7L * weeks - 1

    /** 0 for week 1; negative before the start; [weeks] or more after the end. */
    fun weekIndex(start: Long, day: Long): Int = Math.floorDiv(day - start, 7L).toInt()

    fun monday(start: Long, index: Int): Long = start + 7L * index

    fun ended(start: Long, weeks: Int, today: Long): Boolean = today > lastDay(start, weeks)

    fun endedShown(start: Long, weeks: Int, today: Long): Boolean =
        today - lastDay(start, weeks) in 1..ENDED_SHOWN_DAYS.toLong()
}
```

- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** `Programme.kt`, `ProgrammeCalendar.kt`, `ProgrammeCalendarTest.kt`:
  `feat: the weekly plan's vocabulary and calendar (D93, D94)`.

---

### Task 2: Ticks from the record, the last evaluation, and the form's pre-fill — pure

**Files:**
- Create: `domain/trainer/PlanProgress.kt`, `domain/trainer/NextInPlan.kt`
- Test: `domain/trainer/PlanProgressTest.kt`

- [ ] **Step 1: Failing test** `domain/trainer/PlanProgressTest.kt`:

```kotlin
package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/** D95, D96, D98. The plan starts Monday 31 August 2026; every session and figure is invented. */
class PlanProgressTest {

    private val start = TEST_EPOCH_DAY - 3
    private val easyWalk = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
    private val steadyWalk = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Steady walk")
    private val run = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Short run")
    private val plan = WeeksPlan(
        "Invented title",
        listOf(PlanWeek("first", listOf(easyWalk, steadyWalk, run)), PlanWeek("second", listOf(easyWalk, run))),
        "Invented reason.",
    )

    @Test
    fun `each session ticks the first unticked planned session of its kind, in start order`() {
        val progress = PlanProgress.of(plan, start, listOf(session(2, start + 2, WorkoutKind.WALK), session(1, start, WorkoutKind.WALK)), until = start + 6)

        val week = progress.weeks.first()
        assertThat(week.ticks.map { it.by?.id }).containsExactly(1L, 2L, null).inOrder()
        assertThat(week.done).isEqualTo(2)
        assertThat(week.next).isEqualTo(run)
    }

    @Test
    fun `a session with nothing of its kind left, or of an unknown kind, ticks nothing`() {
        val sessions = listOf(
            session(1, start, WorkoutKind.RUN), session(2, start + 1, WorkoutKind.RUN),
            session(3, start + 2, WorkoutKind.CYCLE), session(4, start + 3, WorkoutKind.UNRECOGNISED),
        )

        val week = PlanProgress.of(plan, start, sessions, until = start + 6).weeks.first()

        assertThat(week.ticks.map { it.by?.id }).containsExactly(null, null, 1L).inOrder()
    }

    @Test
    fun `hidden and uncounted sessions, and days after until, count for nothing`() {
        val sessions = listOf(
            session(1, start, WorkoutKind.WALK).copy(hidden = true),
            session(2, start, WorkoutKind.WALK).copy(counted = false),
            session(3, start + 8, WorkoutKind.WALK),
        )

        val progress = PlanProgress.of(plan, start, sessions, until = start + 7)

        assertThat(progress.done).isEqualTo(0)
        assertThat(progress.planned).isEqualTo(5)
    }

    @Test
    fun `sessions tick their own week only`() {
        val progress = PlanProgress.of(plan, start, listOf(session(1, start + 7, WorkoutKind.WALK)), until = start + 13)

        assertThat(progress.weeks.map { it.done }).containsExactly(0, 1).inOrder()
        assertThat(progress.tickOf(1)).isEqualTo(PlannedTick(2, easyWalk))
        assertThat(progress.tickOf(9)).isNull()
    }

    @Test
    fun `the last evaluation is the newest kept one, with the newest kept version of its plan`() {
        val eval = Evaluation("h", "g", "t", "")
        val first = programme(1, createdAt = 100, evaluation = eval, status = ProgrammeStatus.ADJUSTED)
        val adjusted = programme(2, createdAt = 200, evaluation = null, status = ProgrammeStatus.RUNNING, replacesId = 1)
        val notKept = programme(3, createdAt = 300, evaluation = null, status = ProgrammeStatus.OFFERED, replacesId = 2)
        val offeredEval = programme(4, createdAt = 400, evaluation = eval, status = ProgrammeStatus.OFFERED)

        val (evaluated, latest) = Programmes.lastEvaluated(listOf(first, adjusted, notKept, offeredEval))!!

        assertThat(evaluated.id).isEqualTo(1)
        assertThat(latest.id).isEqualTo(2)
        assertThat(Programmes.evaluationOf(adjusted, listOf(first, adjusted))).isEqualTo(eval)
        assertThat(Programmes.lastEvaluated(listOf(offeredEval))).isNull()
    }

    @Test
    fun `a version counts until the day it stopped, never past its last day`() {
        val running = programme(1, createdAt = 0, evaluation = null, status = ProgrammeStatus.RUNNING).copy(startEpochDay = start)
        assertThat(Programmes.countedUntil(running, today = start + 100)).isEqualTo(start + 13)
        assertThat(Programmes.countedUntil(running.copy(stoppedEpochDay = start + 5), today = start + 100)).isEqualTo(start + 5)
        assertThat(Programmes.countedUntil(running, today = start + 3)).isEqualTo(start + 3)
    }

    @Test
    fun `the form is pre-filled with the shortest time that fits and the effort's wish`() {
        assertThat(NextInPlan.time(20)).isEqualTo(TimeAvailable.MIN_20)
        assertThat(NextInPlan.time(25)).isEqualTo(TimeAvailable.MIN_30)
        assertThat(NextInPlan.time(45)).isEqualTo(TimeAvailable.MIN_45)
        assertThat(NextInPlan.time(60)).isEqualTo(TimeAvailable.MIN_60_OR_MORE)
        assertThat(NextInPlan.time(90)).isEqualTo(TimeAvailable.MIN_60_OR_MORE)
        assertThat(NextInPlan.wish(PlannedEffort.EASY)).isEqualTo(Wish.EASY)
        assertThat(NextInPlan.wish(PlannedEffort.PUSH)).isEqualTo(Wish.PUSH)
        assertThat(NextInPlan.wish(PlannedEffort.STEADY)).isEqualTo(Wish.NOT_SURE)
    }

    private fun programme(id: Long, createdAt: Long, evaluation: Evaluation?, status: ProgrammeStatus, replacesId: Long? = null) =
        Programme(id, createdAt, ProgrammeAsk(2, 3), evaluation, plan, "a-model", start, status, null, replacesId)

    private fun session(id: Long, day: Long, kind: WorkoutKind) = Workout(
        id = id, epochDay = day, startedAtMillis = day * 86_400_000L + 7 * 3_600_000L, durationMinutes = 30,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )
}
```

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.trainer.PlanProgressTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement** `domain/trainer/PlanProgress.kt`:

```kotlin
package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind

/** One planned session and the session that ticked it, if any (D95). */
data class Tick(val planned: PlannedSession, val by: Workout?)

data class WeekProgress(val index: Int, val monday: Long, val focus: String, val ticks: List<Tick>) {
    val done: Int get() = ticks.count { it.by != null }
    val planned: Int get() = ticks.size

    /** The first unticked planned session, in the plan's order (D96's "next in your plan"). */
    val next: PlannedSession? get() = ticks.firstOrNull { it.by == null }?.planned
}

/**
 * D95: which planned sessions are done, counted afresh from the record every time — nothing is stored,
 * so a session deleted, split or combined later changes the ticks with no bookkeeping. Pure.
 */
data class PlanProgress(val weeks: List<WeekProgress>) {
    val done: Int get() = weeks.sumOf { it.done }
    val planned: Int get() = weeks.sumOf { it.planned }

    /** D96: the planned session [workoutId] ticked, with its week counted from 1; null when it ticked none. */
    fun tickOf(workoutId: Long): PlannedTick? = weeks.firstNotNullOfOrNull { week ->
        week.ticks.firstOrNull { it.by?.id == workoutId }?.let { PlannedTick(week.index + 1, it.planned) }
    }

    companion object {
        /**
         * The sessions that count are the visible, counted ones (D74, D81) — a combined session once
         * (D92) — on or before [until]. Within each week, in start order, each ticks the first unticked
         * planned session of the same kind; an unrecognised kind never ticks.
         */
        fun of(plan: WeeksPlan, start: Long, workouts: List<Workout>, until: Long): PlanProgress {
            val counted = workouts
                .filter { !it.hidden && it.counted && it.kind != WorkoutKind.UNRECOGNISED && it.epochDay <= until }
                .sortedBy { it.startedAtMillis }
            return PlanProgress(
                plan.weeks.mapIndexed { index, week ->
                    val monday = ProgrammeCalendar.monday(start, index)
                    val by = arrayOfNulls<Workout>(week.sessions.size)
                    counted.filter { it.epochDay in monday..monday + 6 }.forEach { workout ->
                        val slot = week.sessions.indices.firstOrNull { by[it] == null && week.sessions[it].kind == workout.kind }
                        if (slot != null) by[slot] = workout
                    }
                    WeekProgress(index, monday, week.focus, week.sessions.mapIndexed { at, planned -> Tick(planned, by[at]) })
                },
            )
        }
    }
}

/** D98's reading of the stored rows (design questions 1–3). Pure. */
object Programmes {

    /**
     * The newest evaluation that was kept at some point, and the newest kept version of its plan
     * (followed forward through `replacesId`); null when none was ever kept.
     */
    fun lastEvaluated(all: List<Programme>): Pair<Programme, Programme>? {
        val kept = all.filter { it.status != ProgrammeStatus.OFFERED }
        val evaluated = kept.filter { it.evaluation != null }.maxByOrNull { it.createdAtMillis } ?: return null
        var latest = evaluated
        while (true) {
            val newer = kept.firstOrNull { it.replacesId == latest.id } ?: break
            latest = newer
        }
        return evaluated to latest
    }

    /** The evaluation a version carries, else the one of the version it replaces, and so on back. */
    fun evaluationOf(programme: Programme, all: List<Programme>): Evaluation? =
        generateSequence(programme) { current -> current.replacesId?.let { id -> all.firstOrNull { it.id == id } } }
            .take(all.size + 1)
            .firstNotNullOfOrNull { it.evaluation }

    /** The last day a kept version's sessions count: the day it stopped, or [today], never past its last day. */
    fun countedUntil(programme: Programme, today: Long): Long {
        val start = requireNotNull(programme.startEpochDay) { "only a kept plan is counted" }
        return minOf(programme.stoppedEpochDay ?: today, ProgrammeCalendar.lastDay(start, programme.ask.weeks))
    }
}
```

(`take(all.size + 1)` guards a `replacesId` loop in a hand-edited backup.)

`domain/trainer/NextInPlan.kt`:

```kotlin
package com.metaself.app.domain.trainer

/** D96: the single-session form opened from a running plan's next session. Pure. */
object NextInPlan {

    /** The smallest choice at least that long; 60-or-more when longer. */
    fun time(minutes: Int): TimeAvailable =
        TimeAvailable.entries.sortedBy { it.minutes }.firstOrNull { it.minutes >= minutes } ?: TimeAvailable.MIN_60_OR_MORE

    fun wish(effort: PlannedEffort): Wish = when (effort) {
        PlannedEffort.EASY -> Wish.EASY
        PlannedEffort.PUSH -> Wish.PUSH
        PlannedEffort.STEADY -> Wish.NOT_SURE
    }
}
```

- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** `PlanProgress.kt`, `NextInPlan.kt`, `PlanProgressTest.kt`:
  `feat: planned sessions are ticked from the record (D95, D96)`.

---

### Task 3: The questions and the port

**Files:**
- Modify: `domain/trainer/TrainerRequest.kt` (`TrainerQuestion`, `Trainer`)
- Modify (test): `data/trainer/FakeTrainer.kt`, `domain/trainer/TrainerRequestTest.kt`

- [ ] **Step 1: Failing test.** In `TrainerRequestTest`, extend the pinned-field test (its KDoc gains
  "D93 and D97 add two questions; D96 a planned session on the other two"):

```kotlin
        assertThat(fieldsOf(TrainerQuestion.Plan::class.java)).containsExactly("answers", "planned")
        assertThat(fieldsOf(TrainerQuestion.Review::class.java)).containsExactly("session", "planned")
        assertThat(fieldsOf(TrainerQuestion.Evaluate::class.java)).containsExactly("ask", "startEpochDay", "last")
        assertThat(fieldsOf(TrainerQuestion.Adjust::class.java)).containsExactly(
            "ask", "startEpochDay", "plan", "weekIndex", "doneByWeek", "tickedThisWeek", "thisWeekMax", "words",
        )
        assertThat(fieldsOf(LastEvaluation::class.java)).containsExactly("epochDay", "evaluation", "plan", "doneByWeek")
        assertThat(fieldsOf(PlannedSession::class.java)).containsExactly("kind", "minutes", "effort", "what")
```

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.trainer.TrainerRequestTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement.** Replace `TrainerQuestion` in `TrainerRequest.kt`:

```kotlin
/** What is being asked (D84's "the question"). */
sealed interface TrainerQuestion {
    /** [planned]: the running weekly plan's next session, which the suggestion is shaped around (D96). */
    data class Plan(val answers: PlanAnswers, val planned: PlannedTick? = null) : TrainerQuestion

    /**
     * [session] carries the felt effort, the words and the matched plan being asked about; [planned] is
     * the weekly plan's session it ticked, and its week (D96).
     */
    data class Review(val session: SessionFacts, val planned: PlannedTick? = null) : TrainerQuestion

    /**
     * D93: the form, the Monday the plan would start if kept now (D94), and the last kept evaluation
     * with how its plan went (D98), or null.
     */
    data class Evaluate(val ask: ProgrammeAsk, val startEpochDay: Long, val last: LastEvaluation?) : TrainerQuestion

    /**
     * D97: the running [plan], the week the owner is in ([weekIndex], from 0), the done-count of each week
     * before it, the planned sessions already ticked this week, how many this week may still hold
     * ([thisWeekMax]), and the owner's words. Every count is the phone's (D95).
     */
    data class Adjust(
        val ask: ProgrammeAsk,
        val startEpochDay: Long,
        val plan: WeeksPlan,
        val weekIndex: Int,
        val doneByWeek: List<Int>,
        val tickedThisWeek: List<PlannedSession>,
        val thisWeekMax: Int,
        val words: String,
    ) : TrainerQuestion
}
```

Add to `interface Trainer`:

```kotlin
    /** [request]'s question must be [TrainerQuestion.Evaluate] (D93). */
    suspend fun evaluate(request: TrainerRequest): TrainerReply<EvaluationAndPlan>

    /** [request]'s question must be [TrainerQuestion.Adjust] (D97). The reply is this week and the weeks after. */
    suspend fun adjust(request: TrainerRequest): TrainerReply<WeeksPlan>
```

`FakeTrainer` gains:

```kotlin
    val evaluations = mutableListOf<TrainerReply<EvaluationAndPlan>>()
    val adjustments = mutableListOf<TrainerReply<WeeksPlan>>()

    override suspend fun evaluate(request: TrainerRequest): TrainerReply<EvaluationAndPlan> {
        asked += request
        return evaluations.removeAt(0)
    }

    override suspend fun adjust(request: TrainerRequest): TrainerReply<WeeksPlan> {
        asked += request
        return adjustments.removeAt(0)
    }
```

(and its KDoc: "each call takes the next of its scripted list"). `OpenAiTrainer` gets the two methods in
Task 6; until then give it `override suspend fun evaluate(request: TrainerRequest): TrainerReply<EvaluationAndPlan> = TODO("Task 6")`
and the same for `adjust` so the build compiles — **Task 6 removes both**. Grep for every other
`: Trainer` implementation (`grep -rn ": Trainer\b\|Trainer by" app/src`) — test doubles built with
`object : Trainer by trainer` need nothing.

`TrainerPrompt.question()` is an exhaustive `when`: until Task 4, add
`is TrainerQuestion.Evaluate, is TrainerQuestion.Adjust -> throw IllegalArgumentException("Task 4")`.

- [ ] **Step 4: Run, expect PASS**; then the whole trainer package:
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.trainer.*" --tests "com.metaself.app.data.trainer.*" --tests "com.metaself.app.data.ai.Trainer*" > /tmp/ms-weeks.log 2>&1; echo "exit $?"` → exit 0.
- [ ] **Step 5: Commit** `TrainerRequest.kt`, `OpenAiTrainer.kt`, `TrainerPrompt.kt`, `FakeTrainer.kt`,
  `TrainerRequestTest.kt`: `feat: the trainer can be asked to evaluate and to adjust (D93, D97)`.

---
### Task 4: The two request bodies, and the planned session sent — pure

**Files:**
- Modify: `data/ai/TrainerPrompt.kt`
- Test: `data/ai/TrainerPromptTest.kt`

- [ ] **Step 1: Failing tests.** Append to `TrainerPromptTest` (fixtures beside the existing ones):

```kotlin
    // --- D93–D97 -----------------------------------------------------------------------------------

    @Test
    fun `an evaluation request names its schema and sends the form, the start and the last evaluation`() {
        val body = Json.parseToJsonElement(TrainerPrompt.evaluateBody("a-model", evaluateRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val user = Json.parseToJsonElement(userContent(body)).jsonObject
        val question = user.getValue("question").jsonObject

        assertThat(body.toString()).contains("evaluation_and_plan")
        assertThat(user.keys).containsExactly(
            "question", "today", "about_me", "sessions", "weeks", "months", "weight", "goal", "body", "this_week",
            "earlier_feedback",
        )
        assertThat(question.keys).containsExactly("kind", "weeks", "sessions_a_week", "words", "starts", "last_evaluation")
        assertThat(question.getValue("kind").jsonPrimitive.content).isEqualTo("evaluate")
        assertThat(question.getValue("starts").jsonPrimitive.content).isEqualTo("2026-08-31")
        val last = question.getValue("last_evaluation").jsonObject
        assertThat(last.keys).containsExactly("date", "evaluation", "plan", "done_by_week")
        assertThat(last.getValue("done_by_week").jsonArray.map { it.jsonPrimitive.int }).containsExactly(3, 2).inOrder()
        val planned = last.getValue("plan").jsonObject.getValue("weeks").jsonArray.first().jsonObject
            .getValue("sessions").jsonArray.first().jsonObject
        assertThat(planned.keys).containsExactly("kind", "minutes", "effort", "what")
        assertThat(planned.getValue("effort").jsonPrimitive.content).isEqualTo("steady")
    }

    @Test
    fun `with no earlier evaluation, last_evaluation is null and since_last may be empty`() {
        val body = Json.parseToJsonElement(TrainerPrompt.evaluateBody("a-model", evaluateRequest(last = null), RequestProfile.DETERMINISTIC)).jsonObject
        val question = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("question").jsonObject

        assertThat(question.getValue("last_evaluation")).isEqualTo(JsonNull)
        assertThat(systemContent(TrainerPrompt.evaluateBody("a-model", evaluateRequest()))).contains("empty string when there is none")
    }

    @Test
    fun `an adjust request sends the plan, the week, the phone's counts and the words`() {
        val body = Json.parseToJsonElement(TrainerPrompt.adjustBody("a-model", adjustRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val question = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("question").jsonObject

        assertThat(body.toString()).contains("weeks_plan")
        assertThat(question.keys).containsExactly(
            "kind", "weeks", "sessions_a_week", "starts", "plan", "this_week_number", "done_by_week", "done_this_week",
            "max_this_week", "words",
        )
        assertThat(question.getValue("this_week_number").jsonPrimitive.int).isEqualTo(2)
        assertThat(question.getValue("done_by_week").jsonArray.map { it.jsonPrimitive.int }).containsExactly(3)
        assertThat(question.getValue("done_this_week").jsonArray).hasSize(1)
        assertThat(question.getValue("max_this_week").jsonPrimitive.int).isEqualTo(2)
    }

    @Test
    fun `the weekly plan's instructions say what may come back, and that the counts are the phone's`() {
        val evaluate = systemContent(TrainerPrompt.evaluateBody("a-model", evaluateRequest()))
        val adjust = systemContent(TrainerPrompt.adjustBody("a-model", adjustRequest()))

        assertThat(evaluate).contains("exactly as many weeks as asked")
        assertThat(evaluate).contains("5 to 180")
        assertThat(evaluate).contains("do not invent a test or a score")
        assertThat(adjust).contains("max_this_week")
        assertThat(adjust).contains("Weeks already over are not yours to change")
        listOf(evaluate, adjust).forEach { system ->
            assertThat(system).contains("counted by the app")
            assertThat(system).contains("pain, dizziness or chest discomfort")
            assertThat(system).contains("an evaluation or a change to their weekly plan")
        }
    }

    @Test
    fun `a plan question and a review carry the weekly plan's session, or null`() {
        val tick = PlannedTick(2, PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Steady walk"))
        val withPlanned = request(
            TrainerQuestion.Plan(PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.FRESH, Wish.NOT_SURE), tick),
        )
        val question = Json.parseToJsonElement(userContent(Json.parseToJsonElement(
            TrainerPrompt.planBody("a-model", withPlanned, RequestProfile.DETERMINISTIC),
        ).jsonObject)).jsonObject.getValue("question").jsonObject
        val review = Json.parseToJsonElement(userContent(Json.parseToJsonElement(
            TrainerPrompt.feedbackBody("a-model", reviewRequest(), RequestProfile.DETERMINISTIC),
        ).jsonObject)).jsonObject.getValue("question").jsonObject

        assertThat(question.getValue("planned").jsonObject.keys).containsExactly("week", "kind", "minutes", "effort", "what")
        assertThat(question.getValue("planned").jsonObject.getValue("week").jsonPrimitive.int).isEqualTo(2)
        assertThat(review.getValue("planned")).isEqualTo(JsonNull)
        assertThat(systemContent(TrainerPrompt.planBody("a-model", withPlanned))).contains("question.planned")
    }

    @Test
    fun `an evaluate body for another question, or an adjust body for another, is refused`() {
        assertThrows<IllegalArgumentException> { TrainerPrompt.evaluateBody("a-model", planRequest()) }
        assertThrows<IllegalArgumentException> { TrainerPrompt.adjustBody("a-model", evaluateRequest()) }
    }

    /** Invented: a four-week, three-a-week ask; the plan starts Monday 31 August 2026. */
    private fun evaluateRequest(last: LastEvaluation? = LAST) = request(
        TrainerQuestion.Evaluate(ProgrammeAsk(4, 3, "Invented words."), TEST_EPOCH_DAY - 3, last),
    )

    private fun adjustRequest() = request(
        TrainerQuestion.Adjust(
            ProgrammeAsk(4, 3), TEST_EPOCH_DAY - 10, WEEKS, weekIndex = 1, doneByWeek = listOf(3),
            tickedThisWeek = listOf(STEADY), thisWeekMax = 2, words = "Invented words.",
        ),
    )
```

Add to the file's companion (create `private companion object` if the file has none):

```kotlin
        val STEADY = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Steady walk")
        val WEEKS = WeeksPlan(
            "Invented plan",
            listOf(PlanWeek("settle in", listOf(STEADY, STEADY, STEADY)), PlanWeek("a little longer", listOf(STEADY, STEADY, STEADY))),
            "Invented reason.",
        )
        val LAST = LastEvaluation(TEST_EPOCH_DAY - 30, Evaluation("Invented.", "Invented.", "Invented.", ""), WEEKS, listOf(3, 2))
```

In `each body is built one way, from one request`, extend the list to
`listOf("planBody", "feedbackBody", "evaluateBody", "adjustBody")`. In
`pain, dizziness or chest discomfort in the question…` and `the instructions name no gender`, add the two
new bodies to the list checked (the first test still asserts `question.words` and `question.session.words`).
Imports: `Evaluation`, `LastEvaluation`, `PlanWeek`, `PlannedEffort`, `PlannedSession`, `PlannedTick`,
`ProgrammeAsk`, `WeeksPlan`.

- [ ] **Step 2: Run, expect failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.TrainerPromptTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement** in `TrainerPrompt`.

  In `COMMON`, replace
  `(question.words when they ask for a plan, question.session.words when they ask about a session)` with
  `(question.words when they ask for a plan, an evaluation or a change to their weekly plan;
  question.session.words when they ask about a session)`.

  Append to `PLAN`:

```
        When question.planned is given, they are following a weekly plan and this is its next session
        (its week, kind, minutes, effort and what it is): shape the suggestion around it unless their
        answers say otherwise.
```

  Append to `FEEDBACK`:

```
        When question.planned is given, the session ticked that session of their weekly plan (counted by
        the app); say how it went against it in against_plan as well.
```

  New instructions, after `FEEDBACK`:

```kotlin
    private val EVALUATE = """
        They are asking where they stand, and for a plan for the weeks ahead. The question gives how many
        weeks, how many sessions a week they can manage (sessions_a_week), any words of theirs, the date the
        plan starts (starts, a Monday; weeks run Monday to Sunday), and your last evaluation if there is one,
        with the plan that ran with it and how many of its sessions were done each week (done_by_week,
        counted by the app).

        Reply with an evaluation and a plan. The evaluation: a one-line headline; going_well; to_work_on;
        and since_last, what has changed since the last evaluation, or an empty string when there is none.
        Judge from the record; do not invent a test or a score.

        The plan: a title; exactly as many weeks as asked, in order, each with a short focus and between
        one and sessions_a_week sessions; each session has a kind (walk, run, cycle, swim, strength or
        other), whole minutes from 5 to 180, an effort (easy, steady or push) and one line on what it is.
        Sessions have no day: each can be done on any day of its week. Then one paragraph on why.
    """.trimIndent()

    private val ADJUST = """
        They are following your weekly plan and ask you to change what is left of it. The question gives
        the plan, the week they are in (this_week_number, from 1), how many planned sessions were done in
        each week before it (done_by_week, counted by the app), the planned sessions already done this week
        (done_this_week, counted by the app), and their words.

        Reply with a plan in the same shape for this week and the weeks after: a title; one entry per
        remaining week, starting with this one; and one paragraph on why. For this week give only the
        sessions still to do, at most max_this_week, and none is allowed; for each later week between one
        and sessions_a_week. Each session has a kind (walk, run, cycle, swim, strength or other), whole
        minutes from 5 to 180, an effort (easy, steady or push) and one line on what it is. Keep the same
        number of weeks; the end date does not move. Weeks already over are not yours to change.
    """.trimIndent()
```

  Bodies, beside `planBody`:

```kotlin
    fun evaluateBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Evaluate) { "an evaluation is asked with an evaluation question" }
        return ChatRequest.body(model, profile, messages(EVALUATE, request), "evaluation_and_plan", EVALUATION_SCHEMA)
    }

    fun adjustBody(model: String, request: TrainerRequest, profile: RequestProfile = RequestProfile.guess(model)): String {
        require(request.question is TrainerQuestion.Adjust) { "an adjustment is asked with an adjust question" }
        return ChatRequest.body(model, profile, messages(ADJUST, request), "weeks_plan", WEEKS_PLAN_SCHEMA)
    }
```

  `question()` — replace the whole `when` (and remove Task 3's placeholder branch):

```kotlin
    private fun question(question: TrainerQuestion): JsonObject = when (question) {
        is TrainerQuestion.Plan -> buildJsonObject {
            put("kind", "plan")
            put("what", question.answers.activity.name.lowercase().replace('_', ' '))
            put("minutes_available", question.answers.time.minutes)
            put("or_more", question.answers.time.orMore)
            put("feeling", question.answers.feeling.name.lowercase())
            put("wants", question.answers.wish.name.lowercase().replace('_', ' '))
            put("words", question.answers.words.trim())
            put("planned", question.planned?.let(::plannedTick) ?: JsonNull)
        }
        is TrainerQuestion.Review -> buildJsonObject {
            put("kind", "review")
            put("session", session(question.session))
            put("planned", question.planned?.let(::plannedTick) ?: JsonNull)
        }
        is TrainerQuestion.Evaluate -> buildJsonObject {
            put("kind", "evaluate")
            put("weeks", question.ask.weeks)
            put("sessions_a_week", question.ask.perWeek)
            put("words", question.ask.words.trim())
            put("starts", date(question.startEpochDay))
            put(
                "last_evaluation",
                question.last?.let { last ->
                    buildJsonObject {
                        put("date", date(last.epochDay))
                        put("evaluation", evaluationJson(last.evaluation))
                        put("plan", weeksPlanJson(last.plan))
                        putJsonArray("done_by_week") { last.doneByWeek.forEach { add(it) } }
                    }
                } ?: JsonNull,
            )
        }
        is TrainerQuestion.Adjust -> buildJsonObject {
            put("kind", "adjust")
            put("weeks", question.ask.weeks)
            put("sessions_a_week", question.ask.perWeek)
            put("starts", date(question.startEpochDay))
            put("plan", weeksPlanJson(question.plan))
            put("this_week_number", question.weekIndex + 1)
            putJsonArray("done_by_week") { question.doneByWeek.forEach { add(it) } }
            putJsonArray("done_this_week") { question.tickedThisWeek.forEach { add(plannedJson(it)) } }
            put("max_this_week", question.thisWeekMax)
            put("words", question.words.trim())
        }
    }

    private fun plannedTick(tick: PlannedTick): JsonObject = buildJsonObject {
        put("week", tick.week)
        plannedJson(tick.session).forEach { (name, value) -> put(name, value) }
    }
```

  Shapes (public, beside `planJson`, so storage uses them — `TrainerResponse.encode*`):

```kotlin
    /** A planned session in the reply schema's own shape (D94). */
    fun plannedJson(session: PlannedSession): JsonObject = buildJsonObject {
        put("kind", kind(session.kind))
        put("minutes", session.minutes)
        put("effort", session.effort.name.lowercase())
        put("what", session.what)
    }

    /** A plan of weeks in the reply schema's own shape: sent back, and what `TrainerResponse.encodeWeeksPlan` stores. */
    fun weeksPlanJson(plan: WeeksPlan): JsonObject = buildJsonObject {
        put("title", plan.title)
        putJsonArray("weeks") {
            plan.weeks.forEach { week ->
                add(
                    buildJsonObject {
                        put("focus", week.focus)
                        putJsonArray("sessions") { week.sessions.forEach { add(plannedJson(it)) } }
                    },
                )
            }
        }
        put("why", plan.why)
    }

    /** An evaluation in the reply schema's own shape: sent as the last one, and what `TrainerResponse.encodeEvaluation` stores. */
    fun evaluationJson(evaluation: Evaluation): JsonObject = buildJsonObject {
        put("headline", evaluation.headline)
        put("going_well", evaluation.goingWell)
        put("to_work_on", evaluation.toWorkOn)
        put("since_last", evaluation.sinceLast)
    }
```

  Schemas, after `FEEDBACK_SCHEMA` (order matters: each `val` uses the ones above it):

```kotlin
    private fun enumOf(vararg values: String) = buildJsonObject {
        put("type", "string")
        putJsonArray("enum") { values.forEach { add(it) } }
    }

    private fun arrayOf(items: JsonObject) = buildJsonObject {
        put("type", "array")
        put("items", items)
    }

    private val PLANNED_SCHEMA: JsonObject = strictObject(
        "kind" to enumOf("walk", "run", "cycle", "swim", "strength", "other"),
        "minutes" to integer(),
        "effort" to enumOf("easy", "steady", "push"),
        "what" to string(),
    )

    private val WEEKS_PLAN_SCHEMA: JsonObject = strictObject(
        "title" to string(),
        "weeks" to arrayOf(strictObject("focus" to string(), "sessions" to arrayOf(PLANNED_SCHEMA))),
        "why" to string(),
    )

    private val EVALUATION_SCHEMA: JsonObject = strictObject(
        "evaluation" to strictObject(
            "headline" to string(), "going_well" to string(), "to_work_on" to string(), "since_last" to string(),
        ),
        "plan" to WEEKS_PLAN_SCHEMA,
    )
```

  (`arrayOf` shadows Kotlin's `arrayOf` inside the object only; if the compiler complains, name it
  `arraySchema`.) Imports: `Evaluation`, `PlannedSession`, `PlannedTick`, `WeeksPlan`.

- [ ] **Step 4: Run, expect PASS** (same command; the whole `TrainerPromptTest` class).
- [ ] **Step 5: Commit** `TrainerPrompt.kt`, `TrainerPromptTest.kt`:
  `feat: the evaluation and adjust requests, and the planned session sent (D93, D96, D97)`.

---

### Task 5: Reading the two replies, and writing them for storage — pure

**Files:**
- Modify: `data/ai/TrainerResponse.kt`
- Test: `data/ai/TrainerResponseTest.kt`

- [ ] **Step 1: Failing tests.** Append to `TrainerResponseTest`:

```kotlin
    // --- D94, D97. Every title, focus and line is invented. -----------------------------------------

    private fun session(kind: String = "walk", minutes: Int = 30, effort: String = "easy") =
        """{"kind":"$kind","minutes":$minutes,"effort":"$effort","what":"Invented line"}"""

    private fun week(vararg sessions: String) = """{"focus":"Invented focus","sessions":[${sessions.joinToString(",")}]}"""

    private fun weeksPlan(vararg weeks: String) = """{"title":"Invented plan","weeks":[${weeks.joinToString(",")}],"why":"Invented reason."}"""

    private val evaluation = """{"headline":"Invented headline","going_well":"Invented.","to_work_on":"Invented.","since_last":""}"""

    private fun evaluated(plan: String) = """{"evaluation":$evaluation,"plan":$plan}"""

    private val twoWeeks = weeksPlan(week(session(), session("run", 20, "push")), week(session(minutes = 40, effort = "steady")))

    @Test
    fun `an evaluation reply becomes an evaluation and a plan`() {
        val reply = TrainerResponse.parseEvaluation(reply(evaluated(twoWeeks)), "a-model", ProgrammeAsk(2, 2)) as TrainerReply.Answered

        assertThat(reply.value.evaluation.headline).isEqualTo("Invented headline")
        assertThat(reply.value.evaluation.sinceLast).isEmpty()
        assertThat(reply.value.plan.weeks.map { it.sessions.size }).containsExactly(2, 1).inOrder()
        assertThat(reply.value.plan.weeks.first().sessions.last())
            .isEqualTo(PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Invented line"))
    }

    @Test
    fun `a plan with the wrong weeks, too many or no sessions in a week, is unreadable`() {
        val threeInOneWeek = weeksPlan(week(session(), session(), session()), week(session()))
        val anEmptyWeek = weeksPlan(week(), week(session()))

        assertUnreadable(TrainerResponse.parseEvaluation(reply(evaluated(twoWeeks)), "a-model", ProgrammeAsk(4, 2)))
        assertUnreadable(TrainerResponse.parseEvaluation(reply(evaluated(threeInOneWeek)), "a-model", ProgrammeAsk(2, 2)))
        assertUnreadable(TrainerResponse.parseEvaluation(reply(evaluated(anEmptyWeek)), "a-model", ProgrammeAsk(2, 2)))
    }

    @Test
    fun `an unknown kind or effort, minutes out of range or as text, or an empty line is unreadable`() {
        val ask = ProgrammeAsk(2, 2)
        listOf(
            session(kind = "dance"), session(effort = "hard"), session(minutes = 4), session(minutes = 181),
            session().replace("30", "\"30\""), session().replace("Invented line", " "),
        ).forEach { bad ->
            assertUnreadable(TrainerResponse.parseEvaluation(reply(evaluated(weeksPlan(week(bad), week(session())))), "a-model", ask))
        }
        assertUnreadable(TrainerResponse.parseEvaluation(reply(evaluated(twoWeeks).replace("Invented headline", "")), "a-model", ask))
    }

    @Test
    fun `an adjusted rest may leave this week empty, but not a later one, and has the weeks left`() {
        val rest = weeksPlan(week(), week(session()))

        assertThat(TrainerResponse.parseAdjusted(reply(rest), "a-model", weeksLeft = 2, perWeek = 3, thisWeekMax = 1))
            .isInstanceOf(TrainerReply.Answered::class.java)
        assertUnreadable(TrainerResponse.parseAdjusted(reply(rest), "a-model", weeksLeft = 3, perWeek = 3, thisWeekMax = 1))
        assertUnreadable(TrainerResponse.parseAdjusted(reply(weeksPlan(week(session()), week())), "a-model", 2, 3, 1))
        assertUnreadable(TrainerResponse.parseAdjusted(reply(weeksPlan(week(session(), session()), week(session()))), "a-model", 2, 3, 1))
    }

    @Test
    fun `an evaluation and a plan survive being written for storage`() {
        val reply = TrainerResponse.parseEvaluation(reply(evaluated(twoWeeks)), "a-model", ProgrammeAsk(2, 2)) as TrainerReply.Answered

        assertThat(TrainerResponse.readEvaluation(TrainerResponse.encodeEvaluation(reply.value.evaluation))).isEqualTo(reply.value.evaluation)
        assertThat(TrainerResponse.readWeeksPlan(TrainerResponse.encodeWeeksPlan(reply.value.plan))).isEqualTo(reply.value.plan)
        assertThat(TrainerResponse.readWeeksPlan("not json")).isNull()
    }
```

  Imports: `PlannedEffort`, `PlannedSession`, `ProgrammeAsk`, `com.metaself.app.domain.movement.WorkoutKind`.

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.TrainerResponseTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement** in `TrainerResponse`:

```kotlin
    /** D94: the evaluation and a plan that [fits][WeeksPlan.fits] [ask]; anything else is unreadable. */
    fun parseEvaluation(body: String, model: String, ask: ProgrammeAsk): TrainerReply<EvaluationAndPlan> =
        content(body)?.let(::readEvaluationAndPlan)?.takeIf { it.plan.fits(ask) }?.let { TrainerReply.Answered(it, model) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /** D97: this week and the weeks after, [fitting the rest][WeeksPlan.fitsRest]. */
    fun parseAdjusted(body: String, model: String, weeksLeft: Int, perWeek: Int, thisWeekMax: Int): TrainerReply<WeeksPlan> =
        content(body)?.let(::readWeeksPlan)?.takeIf { it.fitsRest(weeksLeft, perWeek, thisWeekMax) }
            ?.let { TrainerReply.Answered(it, model) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    fun readEvaluationAndPlan(content: String?): EvaluationAndPlan? = runCatching {
        val payload = json.parseToJsonElement(content!!).jsonObject
        EvaluationAndPlan(
            evaluation(payload.getValue("evaluation").jsonObject)!!,
            weeksPlan(payload.getValue("plan").jsonObject)!!,
        )
    }.getOrNull()

    /** An evaluation in the reply's shape, or null for anything else. */
    fun readEvaluation(content: String?): Evaluation? =
        runCatching { evaluation(json.parseToJsonElement(content!!).jsonObject) }.getOrNull()

    /** A plan of weeks in the reply's shape, or null for anything else. A week may be empty (D97). */
    fun readWeeksPlan(content: String?): WeeksPlan? =
        runCatching { weeksPlan(json.parseToJsonElement(content!!).jsonObject) }.getOrNull()

    fun encodeEvaluation(evaluation: Evaluation): String = TrainerPrompt.evaluationJson(evaluation).toString()

    fun encodeWeeksPlan(plan: WeeksPlan): String = TrainerPrompt.weeksPlanJson(plan).toString()

    private fun evaluation(payload: JsonObject): Evaluation? = Evaluation(
        headline = payload.text("headline"),
        goingWell = payload.text("going_well"),
        toWorkOn = payload.text("to_work_on"),
        sinceLast = payload.text("since_last"),
    ).takeIf { it.headline.isNotEmpty() && it.goingWell.isNotEmpty() && it.toWorkOn.isNotEmpty() }

    private fun weeksPlan(payload: JsonObject): WeeksPlan? {
        val weeks = payload.getValue("weeks").jsonArray.map { element ->
            val week = element.jsonObject
            PlanWeek(week.text("focus"), week.getValue("sessions").jsonArray.map { planned(it.jsonObject) })
        }
        return WeeksPlan(payload.text("title"), weeks, payload.text("why"))
            .takeIf { it.title.isNotEmpty() && it.why.isNotEmpty() && it.weeks.isNotEmpty() }
    }

    /** Throws on anything unknown; [PlannedSession]'s own checks refuse minutes out of 5..180. */
    private fun planned(payload: JsonObject): PlannedSession = PlannedSession(
        kind = when (payload.text("kind")) {
            "walk" -> WorkoutKind.WALK
            "run" -> WorkoutKind.RUN
            "cycle" -> WorkoutKind.CYCLE
            "swim" -> WorkoutKind.SWIM
            "strength" -> WorkoutKind.STRENGTH
            "other" -> WorkoutKind.OTHER
            else -> null
        }!!,
        minutes = payload.minute("minutes"),
        effort = when (payload.text("effort")) {
            "easy" -> PlannedEffort.EASY
            "steady" -> PlannedEffort.STEADY
            "push" -> PlannedEffort.PUSH
            else -> null
        }!!,
        what = payload.text("what").also { require(it.isNotEmpty()) { "a planned session says what it is" } },
    )
```

  Imports: `Evaluation`, `EvaluationAndPlan`, `PlanWeek`, `PlannedEffort`, `PlannedSession`, `ProgrammeAsk`,
  `WeeksPlan`, `com.metaself.app.domain.movement.WorkoutKind`.

- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** `TrainerResponse.kt`, `TrainerResponseTest.kt`:
  `feat: an evaluation and a weekly plan are read strictly and stored in their own shape (D94, D97)`.

---

### Task 6: The two calls

**Files:**
- Modify: `data/ai/OpenAiTrainer.kt`
- Test: `data/ai/OpenAiTrainerTest.kt`

- [ ] **Step 1: Failing tests.** Append to `OpenAiTrainerTest` (use the file's own `trainer(...)`,
  `reply(...)`, server and `request(...)` helpers — read them first; the names below are the file's):

```kotlin
    @Test
    fun `an evaluation is one call, checked against the form`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_EVALUATION)))
        server.enqueue(MockResponse().setBody(reply(GOOD_EVALUATION)))

        val fits = trainer().evaluate(EVALUATE_REQUEST)
        val tooFewWeeks = trainer().evaluate(
            request(TrainerQuestion.Evaluate(ProgrammeAsk(4, 2), TEST_EPOCH_DAY - 3, null)),
        )

        assertThat(fits).isInstanceOf(TrainerReply.Answered::class.java)
        assertThat((tooFewWeeks as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        assertThat(server.takeRequest().body.readUtf8()).contains("evaluation_and_plan")
    }

    @Test
    fun `an adjustment is one call, checked against the weeks left`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_REST)))

        val reply = trainer().adjust(ADJUST_REQUEST)

        assertThat((reply as TrainerReply.Answered).value.weeks).hasSize(2)
        assertThat(server.takeRequest().body.readUtf8()).contains("weeks_plan")
    }
```

  Companion additions (invented):

```kotlin
        val GOOD_EVALUATION = """{"evaluation":{"headline":"Invented","going_well":"Invented.","to_work_on":"Invented.","since_last":""},
            "plan":{"title":"Invented","weeks":[
            {"focus":"a","sessions":[{"kind":"walk","minutes":30,"effort":"easy","what":"Walk"}]},
            {"focus":"b","sessions":[{"kind":"walk","minutes":40,"effort":"steady","what":"Walk"}]}],"why":"Invented."}}"""

        val GOOD_REST = """{"title":"Invented","weeks":[
            {"focus":"now","sessions":[]},
            {"focus":"next","sessions":[{"kind":"run","minutes":20,"effort":"push","what":"Run"}]}],"why":"Invented."}"""

        val EVALUATE_REQUEST = request(TrainerQuestion.Evaluate(ProgrammeAsk(2, 2), TEST_EPOCH_DAY - 3, null))

        private val SOME_PLAN = WeeksPlan(
            "Invented",
            List(2) { PlanWeek("w", listOf(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk"))) },
            "Invented.",
        )

        val ADJUST_REQUEST = request(
            TrainerQuestion.Adjust(ProgrammeAsk(2, 2), TEST_EPOCH_DAY - 3, SOME_PLAN, 0, emptyList(), emptyList(), 1, "Invented."),
        )
```

  (If the companion's `request` is declared after these vals, move these below it: companion vals
  initialise in order.)

- [ ] **Step 2: Run, expect failure** (`TODO("Task 6")`):
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.OpenAiTrainerTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement** — replace Task 3's two `TODO`s in `OpenAiTrainer`:

```kotlin
    override suspend fun evaluate(request: TrainerRequest): TrainerReply<EvaluationAndPlan> {
        val question = request.question
        require(question is TrainerQuestion.Evaluate) { "an evaluation is asked with an evaluation question" }
        return ask({ model, profile -> TrainerPrompt.evaluateBody(model, request, profile) }) { body, model ->
            TrainerResponse.parseEvaluation(body, model, question.ask)
        }
    }

    override suspend fun adjust(request: TrainerRequest): TrainerReply<WeeksPlan> {
        val question = request.question
        require(question is TrainerQuestion.Adjust) { "an adjustment is asked with an adjust question" }
        return ask({ model, profile -> TrainerPrompt.adjustBody(model, request, profile) }) { body, model ->
            TrainerResponse.parseAdjusted(body, model, question.ask.weeks - question.weekIndex, question.ask.perWeek, question.thisWeekMax)
        }
    }
```

  Failures are logged by `recorded` as before ("trainer unreadable" etc.), never with words.

- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** `OpenAiTrainer.kt`, `OpenAiTrainerTest.kt`: `feat: the evaluation and adjust calls (D93, D97)`.

---
### Task 7: One table, version 10, and the migration

**Files:**
- Modify: `data/trainer/TrainerEntities.kt` (+ `TrainerProgrammeEntity`), `data/trainer/TrainerDao.kt`
- Create: `data/day/ProgrammeMigration.kt`
- Modify: `data/day/MetaSelfDatabase.kt` (entity, `version = 10`), `di/DataModule.kt` (`MIGRATION_9_10`)
- Generated: `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/10.json`
- Create: `tools/check-migration-9-10.py`; modify `tools/README.md` (one line)
- Test: `app/src/test/java/com/metaself/app/data/MigrationTest.kt` (CI)

- [ ] **Step 1: Entity.** Append to `TrainerEntities.kt`:

```kotlin
/**
 * One evaluation with its plan, or an adjusted version of a plan (D98). Every answer that arrives is a
 * row, kept or not. [evaluation] and [plan] are `TrainerResponse.encodeEvaluation` / `encodeWeeksPlan`'s
 * JSON; [status] is a `ProgrammeStatus` name; [stoppedEpochDay] is the day it stopped running, whatever
 * stopped it. **No foreign key**: [replacesId] names another row of this table, and a restore replaces
 * the table whole.
 */
@Entity(tableName = "trainer_programmes")
data class TrainerProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val weeks: Int,
    val perWeek: Int,
    val words: String?,
    val evaluation: String?,
    val plan: String,
    val model: String,
    val startEpochDay: Long?,
    val status: String,
    val stoppedEpochDay: Long?,
    val replacesId: Long?,
)
```

- [ ] **Step 2: DAO.** Append inside `TrainerDao`, before `// The backup (D88).`:

```kotlin
    // The weekly plans (D98).
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProgramme(programme: TrainerProgrammeEntity): Long

    @Query("SELECT * FROM trainer_programmes WHERE id = :id")
    suspend fun programme(id: Long): TrainerProgrammeEntity?

    @Query("SELECT * FROM trainer_programmes WHERE status = 'RUNNING' ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeRunning(): Flow<TrainerProgrammeEntity?>

    @Query("SELECT * FROM trainer_programmes WHERE status = 'RUNNING' ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun running(): TrainerProgrammeEntity?

    /** Every running plan but [exceptId] stops today, replaced (D98). */
    @Query("UPDATE trainer_programmes SET status = 'REPLACED', stoppedEpochDay = :today WHERE status = 'RUNNING' AND id != :exceptId")
    suspend fun replaceRunning(exceptId: Long, today: Long)

    @Query("UPDATE trainer_programmes SET status = :status, stoppedEpochDay = :day WHERE id = :id")
    suspend fun endProgramme(id: Long, status: String, day: Long)

    @Query("UPDATE trainer_programmes SET status = 'RUNNING', startEpochDay = :start, stoppedEpochDay = NULL WHERE id = :id")
    suspend fun runProgramme(id: Long, start: Long)
```

  and in the backup section:

```kotlin
    @Query("SELECT * FROM trainer_programmes ORDER BY id")
    suspend fun allProgrammes(): List<TrainerProgrammeEntity>

    @Query("DELETE FROM trainer_programmes")
    suspend fun deleteProgrammes()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProgrammes(programmes: List<TrainerProgrammeEntity>)
```

  (`allProgrammes` is also what the store reads for D98's last evaluation — a few rows a month.)

- [ ] **Step 3: Version 10.** In `MetaSelfDatabase`: add `TrainerProgrammeEntity::class` to `entities`,
  `version = 10`. In `DataModule`: import and add `MIGRATION_9_10` after `MIGRATION_8_9`. Write a
  placeholder `data/day/ProgrammeMigration.kt` whose `migrate` is empty so it compiles, then generate
  the schema once (`free -m` first):
  `~/bin/gradlew-safe :app:compileDebugKotlin > /tmp/ms-weeks.log 2>&1; echo "exit $?"` → 0.
  Confirm `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/10.json` exists and
  `git status app/schemas` shows only it new.

- [ ] **Step 4: The migration, verbatim from `10.json`.** Read the `createSql` of `trainer_programmes`,
  replace `${TABLE_NAME}`, and write it character for character. Room is expected to emit exactly the
  following; **if `10.json` differs, `10.json` wins**:

```kotlin
package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 10: the trainer's weekly plans, each with its evaluation (D98).
 *
 * **One table is created, and nothing else is read or written.** No workout, review, plan or anything
 * else is touched. The statement is the exported schema's own, verbatim (`10.json`): Room validates the
 * finished database against that file. `tools/check-migration-9-10.py` proves the statement and the
 * finished shape on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trainer_programmes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                "`weeks` INTEGER NOT NULL, `perWeek` INTEGER NOT NULL, `words` TEXT, `evaluation` TEXT, " +
                "`plan` TEXT NOT NULL, `model` TEXT NOT NULL, `startEpochDay` INTEGER, `status` TEXT NOT NULL, " +
                "`stoppedEpochDay` INTEGER, `replacesId` INTEGER)",
        )
    }
}
```

- [ ] **Step 5: `tools/check-migration-9-10.py`.** Copy `tools/check-migration-8-9.py` whole and change:
  `MIGRATION` → `data/day/ProgrammeMigration.kt`; `NEW_TABLES = ("trainer_programmes",)`; versions 8/9 →
  9/10 throughout (`declared(10, NEW_TABLES)`, `build(9)`, `build(10)`, `entities(9)` / `entities(10)`,
  the messages); the docstring's WHAT IT PROVES → "every statement parses and executes; the statement
  is, character for character, the one 10.json declares for the new table; the new table has 10.json's
  shape; every table that existed is declared identically in 9.json and 10.json and keeps exactly its
  rows; an offered row with no start, no evaluation and no stop day is accepted, and a row with no plan
  is refused." Replace the session-split checks at the end of `main()` with:

```python
    db.execute("INSERT INTO trainer_programmes (createdAtMillis, weeks, perWeek, words, evaluation, plan, "
               "model, startEpochDay, status, stoppedEpochDay, replacesId) "
               "VALUES (1000, 4, 3, NULL, NULL, '{}', 'm', NULL, 'OFFERED', NULL, NULL)")
    try:
        db.execute("INSERT INTO trainer_programmes (createdAtMillis, weeks, perWeek, plan, model, status) "
                   "VALUES (1000, 4, 3, NULL, 'm', 'OFFERED')")
        failures.append("a row with no plan was accepted")
    except sqlite3.IntegrityError:
        pass
```

  Final line: `print(f"OK: {len(migration)} statements, {len(old_tables)} tables untouched, the new table matches 10.json")`.
  Run `python3 tools/check-migration-9-10.py` → `OK: …`. Add to `tools/README.md` under the 8-9 line:
  "- `check-migration-9-10.py` — the same for version 10 (D98): the weekly plans' table created verbatim
  from `10.json`, every other table declared identically and every row kept."

- [ ] **Step 6: MigrationTest (CI).** Append, importing `MIGRATION_9_10`:

```kotlin
    /** D98: one new table, empty; every workout and review kept. Invented figures. */
    @Test
    fun `a version 9 database migrates to version 10 with an empty weekly plans table, keeping its reviews`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 9).use { db ->
            db.execSQL(
                "INSERT INTO workouts (id, epochDay, startedAtMillis, durationMinutes, kind, energySource, source, hidden) " +
                    "VALUES (1, 20699, 1000, 40, 'WALK', 'NONE', 'SYNCED', 0)",
            )
            db.execSQL("INSERT INTO trainer_reviews (id, workoutId, felt) VALUES (1, 1, 'RIGHT')")
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10)

        listOf("trainer_programmes" to 0, "workouts" to 1, "trainer_reviews" to 1).forEach { (table, rows) ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(rows)
            }
        }
        migrated.close()
    }
```

  (Check the version-9 test's INSERT into `workouts` for the columns that are NOT NULL at version 9 and
  copy it exactly if it differs from the above.)

- [ ] **Step 7: Run what runs locally.** `free -m`, then
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.MigrationTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`
  → exit 0, MigrationTest **skipped** (aarch64). Python check: OK.
- [ ] **Step 8: Commit** `TrainerEntities.kt`, `TrainerDao.kt`, `ProgrammeMigration.kt`,
  `MetaSelfDatabase.kt`, `DataModule.kt`, `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/10.json`,
  `tools/check-migration-9-10.py`, `tools/README.md`, `MigrationTest.kt`:
  `feat: a table for the trainer's weekly plans (D98, schema 10)`.

---

### Task 8: The weekly plans' store

**Files:**
- Create: `data/trainer/ProgrammeStore.kt`
- Modify: `di/DataModule.kt` (bind it)
- Create (test): `data/trainer/FakeProgrammeStore.kt`, `data/trainer/RoomProgrammeStoreWritesTest.kt`
- Modify (test, CI): `data/health/HealthRecordStoreTest.kt`

- [ ] **Step 1: Failing test** `data/trainer/RoomProgrammeStoreWritesTest.kt` (no database; the pattern of
  `RoomTrainerStoreWritesTest`):

```kotlin
package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.WeeksPlan
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Proxy

/**
 * How [RoomProgrammeStore] talks to its table, with no database: which writes share a transaction, and
 * how a stored row reads back. What the table then does is `HealthRecordStoreTest`'s (CI only). Every
 * figure and word is invented.
 */
class RoomProgrammeStoreWritesTest {

    private val log = mutableListOf<String>()
    private var stored: TrainerProgrammeEntity? = null

    @Test
    fun `keeping a plan replaces the running one and starts this one, in one transaction`() = runTest {
        store().keep(id = 4, startEpochDay = 20_696, today = 20_699)

        assertThat(log).containsExactly("begin", "replaceRunning(4, 20699)", "runProgramme(4, 20696)", "commit").inOrder()
    }

    @Test
    fun `keeping an adjusted version ends the old one as adjusted and runs the new one from the old start`() = runTest {
        stored = PROGRAMME.toEntity().copy(id = 3, status = "RUNNING", startEpochDay = 20_696)

        store().keepAdjusted(newId = 5, oldId = 3, today = 20_699)

        assertThat(log).containsExactly(
            "begin", "programme(3)", "endProgramme(3, ADJUSTED, 20699)", "runProgramme(5, 20696)", "commit",
        ).inOrder()
    }

    @Test
    fun `an adjusted version of a plan no longer running is refused, and nothing is written`() = runTest {
        stored = PROGRAMME.toEntity().copy(id = 3, status = "STOPPED", startEpochDay = 20_696)

        assertThrows<IllegalStateException> { store().keepAdjusted(newId = 5, oldId = 3, today = 20_699) }
        assertThat(log.filter { it.startsWith("endProgramme") || it.startsWith("runProgramme") }).isEmpty()
    }

    @Test
    fun `stopping is one write with today's date`() = runTest {
        store().stop(id = 4, today = 20_699)

        assertThat(log).containsExactly("endProgramme(4, STOPPED, 20699)")
    }

    @Test
    fun `a row reads back as it was written, and an unreadable one as nothing`() {
        val row = PROGRAMME.copy(id = 7, status = ProgrammeStatus.RUNNING, startEpochDay = 20_696).toEntity()

        assertThat(row.toProgramme()).isEqualTo(PROGRAMME.copy(id = 7, status = ProgrammeStatus.RUNNING, startEpochDay = 20_696))
        assertThat(row.copy(plan = "not json").toProgramme()).isNull()
        assertThat(row.copy(status = "SOMETHING_NEW").toProgramme()).isNull()
        assertThat(row.copy(weeks = 3).toProgramme()).isNull()
    }

    private fun store() = RoomProgrammeStore(dao(), Transaction())

    private inner class Transaction : DatabaseTransaction {
        override suspend fun run(block: suspend () -> Unit) {
            log += "begin"
            block()
            log += "commit"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun dao(): TrainerDao =
        Proxy.newProxyInstance(TrainerDao::class.java.classLoader, arrayOf(TrainerDao::class.java)) { _, method, args ->
            when (method.name) {
                "toString" -> "TrainerDao"
                "hashCode" -> 0
                "equals" -> false
                "programme" -> {
                    log += "programme(${args[0]})"
                    stored
                }
                else -> {
                    // A suspending call's last argument is its continuation.
                    log += method.name + "(" + args.dropLast(1).joinToString() + ")"
                    Unit
                }
            }
        } as TrainerDao

    private companion object {
        val PROGRAMME = Programme(
            id = 0, createdAtMillis = 1_000, ask = ProgrammeAsk(2, 2, "Invented."),
            evaluation = com.metaself.app.domain.trainer.Evaluation("Invented.", "Invented.", "Invented.", ""),
            plan = WeeksPlan(
                "Invented",
                List(2) { PlanWeek("w", listOf(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk"))) },
                "Invented.",
            ),
            model = "a-model",
        )
    }
}
```

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.trainer.RoomProgrammeStoreWritesTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement** `data/trainer/ProgrammeStore.kt`:

```kotlin
package com.metaself.app.data.trainer

import com.metaself.app.data.ai.TrainerResponse
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The trainer's weekly plans (D98). Apart from [TrainerStore] on purpose: the two change for different
 * reasons, and the single-session plans' fakes need not grow.
 */
interface ProgrammeStore {
    /** The running plan, as stored; null when none runs, or when it cannot be read. */
    fun observeRunning(): Flow<Programme?>
    suspend fun running(): Programme?

    /** Every readable row, for D98's last evaluation and "See the plan"'s evaluation. */
    suspend fun all(): List<Programme>

    /** Stores a new answer, OFFERED; its id. */
    suspend fun add(programme: Programme): Long

    /** D94: [id] runs from [startEpochDay]; any other running plan stops [today], replaced. One transaction. */
    suspend fun keep(id: Long, startEpochDay: Long, today: Long)

    /**
     * D97: [oldId] stops [today], adjusted, and [newId] runs from its start. Refused (throws) when [oldId]
     * is no longer running. One transaction.
     */
    suspend fun keepAdjusted(newId: Long, oldId: Long, today: Long)

    /** D97: [id] stops [today]. */
    suspend fun stop(id: Long, today: Long)
}

class RoomProgrammeStore @Inject constructor(
    private val dao: TrainerDao,
    private val transaction: DatabaseTransaction,
) : ProgrammeStore {

    override fun observeRunning(): Flow<Programme?> = dao.observeRunning().map { it?.toProgramme() }
    override suspend fun running(): Programme? = dao.running()?.toProgramme()
    override suspend fun all(): List<Programme> = dao.allProgrammes().mapNotNull { it.toProgramme() }

    override suspend fun add(programme: Programme): Long = dao.insertProgramme(
        programme.copy(id = 0, status = ProgrammeStatus.OFFERED, startEpochDay = null, stoppedEpochDay = null).toEntity(),
    )

    override suspend fun keep(id: Long, startEpochDay: Long, today: Long) = transaction.run {
        dao.replaceRunning(id, today)
        dao.runProgramme(id, startEpochDay)
    }

    override suspend fun keepAdjusted(newId: Long, oldId: Long, today: Long) = transaction.run {
        val old = dao.programme(oldId)
        val start = old?.startEpochDay
        check(old != null && old.status == ProgrammeStatus.RUNNING.name && start != null) {
            "the plan being adjusted is no longer running"
        }
        dao.endProgramme(oldId, ProgrammeStatus.ADJUSTED.name, today)
        dao.runProgramme(newId, start)
    }

    override suspend fun stop(id: Long, today: Long) = dao.endProgramme(id, ProgrammeStatus.STOPPED.name, today)
}

/** Null when this version cannot read the row: such a plan is offered nowhere and sent nowhere. */
fun TrainerProgrammeEntity.toProgramme(): Programme? {
    val ask = runCatching { ProgrammeAsk(weeks, perWeek, words.orEmpty()) }.getOrNull() ?: return null
    val readPlan = TrainerResponse.readWeeksPlan(plan) ?: return null
    val readEvaluation = if (evaluation == null) null else TrainerResponse.readEvaluation(evaluation) ?: return null
    val readStatus = ProgrammeStatus.entries.firstOrNull { it.name == status } ?: return null
    return Programme(id, createdAtMillis, ask, readEvaluation, readPlan, model, startEpochDay, readStatus, stoppedEpochDay, replacesId)
}

fun Programme.toEntity(): TrainerProgrammeEntity = TrainerProgrammeEntity(
    id = id, createdAtMillis = createdAtMillis, weeks = ask.weeks, perWeek = ask.perWeek,
    words = ask.words.trim().ifEmpty { null }, evaluation = evaluation?.let(TrainerResponse::encodeEvaluation),
    plan = TrainerResponse.encodeWeeksPlan(plan), model = model, startEpochDay = startEpochDay,
    status = status.name, stoppedEpochDay = stoppedEpochDay, replacesId = replacesId,
)
```

  `DatabaseTransaction.run` takes `suspend () -> Unit`; `keepAdjusted`'s `check` throws inside it, so
  the transaction rolls back and nothing is written. (If `run` is declared to return something else,
  match `RoomTrainerStore.keep`.)

  `di/DataModule.kt`, beside `provideTrainerStore`:

```kotlin
    @Provides
    fun provideProgrammeStore(store: RoomProgrammeStore): ProgrammeStore = store
```

  (Match the surrounding annotations — `@Singleton` if `provideTrainerStore` has it.)

  Test double `data/trainer/FakeProgrammeStore.kt`:

```kotlin
package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [ProgrammeStore] in memory, with the Room store's rules. [failing] makes every write throw. */
class FakeProgrammeStore : ProgrammeStore {
    val rows = MutableStateFlow<List<Programme>>(emptyList())
    var failing = false
    private var nextId = 1L

    override fun observeRunning(): Flow<Programme?> = rows.map { all -> running(all) }
    override suspend fun running(): Programme? = running(rows.value)
    override suspend fun all(): List<Programme> = rows.value

    override suspend fun add(programme: Programme): Long {
        write()
        val id = nextId++
        rows.value = rows.value + programme.copy(id = id, status = ProgrammeStatus.OFFERED, startEpochDay = null, stoppedEpochDay = null)
        return id
    }

    override suspend fun keep(id: Long, startEpochDay: Long, today: Long) {
        write()
        rows.value = rows.value.map {
            when {
                it.id == id -> it.copy(status = ProgrammeStatus.RUNNING, startEpochDay = startEpochDay, stoppedEpochDay = null)
                it.status == ProgrammeStatus.RUNNING -> it.copy(status = ProgrammeStatus.REPLACED, stoppedEpochDay = today)
                else -> it
            }
        }
    }

    override suspend fun keepAdjusted(newId: Long, oldId: Long, today: Long) {
        write()
        val old = rows.value.first { it.id == oldId }
        check(old.status == ProgrammeStatus.RUNNING) { "the plan being adjusted is no longer running" }
        rows.value = rows.value.map {
            when (it.id) {
                oldId -> it.copy(status = ProgrammeStatus.ADJUSTED, stoppedEpochDay = today)
                newId -> it.copy(status = ProgrammeStatus.RUNNING, startEpochDay = old.startEpochDay)
                else -> it
            }
        }
    }

    override suspend fun stop(id: Long, today: Long) {
        write()
        rows.value = rows.value.map { if (it.id == id) it.copy(status = ProgrammeStatus.STOPPED, stoppedEpochDay = today) else it }
    }

    private fun running(all: List<Programme>) =
        all.filter { it.status == ProgrammeStatus.RUNNING }.maxByOrNull { it.createdAtMillis }

    private fun write() = check(!failing) { "disk full" }
}
```

- [ ] **Step 4: CI test.** Append to `HealthRecordStoreTest` (it has `db`; invented figures):

```kotlin
    /** D98 over a real table: keep, adjust, stop; one running at a time. */
    @Test
    fun `a weekly plan is kept, adjusted and stopped, one running at a time`() = runTest {
        val programmes = RoomProgrammeStore(db.trainerDao(), RoomDatabaseTransaction(db))
        val plan = com.metaself.app.domain.trainer.WeeksPlan(
            "Invented",
            List(2) {
                com.metaself.app.domain.trainer.PlanWeek(
                    "w", listOf(com.metaself.app.domain.trainer.PlannedSession(WorkoutKind.WALK, 30, com.metaself.app.domain.trainer.PlannedEffort.EASY, "Walk")),
                )
            },
            "Invented.",
        )
        val offered = com.metaself.app.domain.trainer.Programme(0, 1_000, com.metaself.app.domain.trainer.ProgrammeAsk(2, 2), null, plan, "m")
        val first = programmes.add(offered)
        val second = programmes.add(offered.copy(createdAtMillis = 2_000))

        programmes.keep(first, startEpochDay = day - 3, today = day)
        programmes.keep(second, startEpochDay = day - 3, today = day)
        val adjusted = programmes.add(offered.copy(createdAtMillis = 3_000, replacesId = second))
        programmes.keepAdjusted(adjusted, second, today = day)

        val all = programmes.all().associateBy { it.id }
        assertThat(all.getValue(first).status).isEqualTo(com.metaself.app.domain.trainer.ProgrammeStatus.REPLACED)
        assertThat(all.getValue(second).status).isEqualTo(com.metaself.app.domain.trainer.ProgrammeStatus.ADJUSTED)
        assertThat(programmes.running()!!.id).isEqualTo(adjusted)
        assertThat(programmes.running()!!.startEpochDay).isEqualTo(day - 3)

        programmes.stop(adjusted, today = day)
        assertThat(programmes.running()).isNull()
    }
```

  (Import the domain types at the top instead of fully qualifying if the file's style is imports —
  it is; the qualified names above are only to show which package each comes from.)

- [ ] **Step 5: Run, expect PASS locally** (`HealthRecordStoreTest` skips here):
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.trainer.*" --tests "com.metaself.app.data.health.HealthRecordStoreTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"` → 0.
- [ ] **Step 6: Commit** `ProgrammeStore.kt`, `DataModule.kt`, `FakeProgrammeStore.kt`,
  `RoomProgrammeStoreWritesTest.kt`, `HealthRecordStoreTest.kt`:
  `feat: the weekly plans' store — keep, adjust, stop (D98)`.

---

### Task 9: The backup, format 8

**Files:**
- Modify: `domain/backup/Backup.kt`, `data/backup/BackupRepository.kt`, `ui/settings/BackupWording.kt`
- Test: `domain/backup/BackupCodecTest.kt`, `data/backup/BackupRestoreOrderTest.kt`,
  `domain/backup/BackupWordingTest.kt`, `data/backup/BackupRoundTripTest.kt` (CI)

- [ ] **Step 1: Failing tests.**

  `BackupCodecTest` — rename `the format is version 7` to `the format is version 8` asserting `8`, and add:

```kotlin
    /** D98: the weekly plans ride in the file as rows; a version 1–7 file has none. Invented. */
    @Test
    fun `weekly plans are written and read back, and an older file has none`() {
        val programme = BackupTrainerProgramme(
            id = 3, createdAtMillis = 1_000, weeks = 4, perWeek = 3, words = null, evaluation = null,
            plan = "{}", model = "m", startEpochDay = 20_696, status = "RUNNING", stoppedEpochDay = null, replacesId = 2,
        )
        val text = BackupCodec.encode(full.copy(trainerProgrammes = listOf(programme)))

        assertThat(text).contains("\"trainer_programmes\"")
        assertThat(BackupCodec.decode(text)!!.trainerProgrammes).containsExactly(programme)
        // A version 7 file has no block at all: built the way the session-splits test builds its version 6 file.
        val version7 = BackupCodec.encode(full.copy(version = 7))
        assertThat(BackupCodec.decode(version7)!!.trainerProgrammes).isEmpty()
    }
```

  (Use the file's own base fixture's name if it is not `full`.)

  `BackupRestoreOrderTest` — in the order test's `containsExactly`, add `"trainer.deleteProgrammes"` right
  after `"trainer.deletePlans"` and `"trainer.insertProgrammes"` right after `"trainer.insertReviews"`.
  In `an older file restored over reviews and plans leaves none`, add
  `assertThat(written.getValue("trainer.insertProgrammes").first() as List<*>).isEmpty()` and
  `assertThat(result.trainerProgrammes).isEqualTo(0)`. Add:

```kotlin
    /** D98: the weekly plans are replaced inside the transaction, keeping their ids. Invented. */
    @Test
    fun `weekly plans are emptied and restored with their ids, and counted`() = runTest {
        val row = BackupTrainerProgramme(3, 1_000, 4, 3, null, null, "{}", "m", 20_696, "RUNNING", null, null)
        val file = aFile().copy(trainerProgrammes = listOf(row, row))

        val result = restorer().restore(file)

        @Suppress("UNCHECKED_CAST")
        val rows = written.getValue("trainer.insertProgrammes").first() as List<TrainerProgrammeEntity>
        assertThat(rows.map { it.id }).containsExactly(3L)
        assertThat(result.trainerProgrammes).isEqualTo(1)
    }
```

  `BackupWordingTest`:

```kotlin
    /** D98: weekly plans are named when either side has some, after the trainer's plans and reviews. */
    @Test
    fun `the confirmation names weekly plans when either side has some`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 4, weights = 6, hasProfile = true, trainerPlans = 3, trainerReviews = 2, trainerProgrammes = 2),
            incoming = RestoreResult(meals = 4, weights = 5, hasProfile = true),
        )

        assertThat(text).contains("3 trainer plans, 2 session reviews and 2 weekly plans already on this phone")
        assertThat(text).contains("0 session reviews and 0 weekly plans from the file")
    }

    @Test
    fun `a phone holding only weekly plans still has something to lose`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 0, weights = 0, hasProfile = false, trainerProgrammes = 1),
            incoming = RestoreResult(meals = 4, weights = 5, hasProfile = true),
        )

        assertThat(text).contains("1 weekly plan")
        assertThat(text).doesNotContain("nothing here to lose")
    }
```

  `BackupRoundTripTest` (CI) — find the test that fills every table and round-trips it (it inserts
  `trainer_plans` rows); insert one `TrainerProgrammeEntity` beside them (invented values as in the codec
  test, `plan` a real `TrainerResponse.encodeWeeksPlan(...)`), and assert
  `db.trainerDao().allProgrammes()` equals what was inserted after restore.

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.backup.*" --tests "com.metaself.app.data.backup.*" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement.**

  `Backup.kt` — after `sessionSplits`:

```kotlin
    /** D98: the trainer's weekly plans, every row, kept or not. Empty in a file from before version 8. */
    @SerialName("trainer_programmes") val trainerProgrammes: List<BackupTrainerProgramme> = emptyList(),
```

  KDoc paragraph after version 7's: "Version 8 adds the trainer's weekly plans (D98), [trainerProgrammes],
  each row as stored with its id, which another row's `replaces_id` may name. A version 1–7 file has none,
  and restoring one leaves none — a restore replaces." `CURRENT_VERSION = 8`. New class beside
  `BackupTrainerPlan`:

```kotlin
/**
 * One weekly plan (D98), as its row holds it. [evaluation] and [plan] are the answers' own JSON, kept as
 * text; [status] a `ProgrammeStatus` name, kept verbatim so a later name survives a round trip.
 */
@Serializable
data class BackupTrainerProgramme(
    val id: Long,
    @SerialName("created_at") val createdAtMillis: Long,
    val weeks: Int,
    @SerialName("per_week") val perWeek: Int,
    val words: String? = null,
    val evaluation: String? = null,
    val plan: String,
    val model: String,
    @SerialName("start_epoch_day") val startEpochDay: Long? = null,
    val status: String,
    @SerialName("stopped_epoch_day") val stoppedEpochDay: Long? = null,
    @SerialName("replaces_id") val replacesId: Long? = null,
)
```

  `BackupRepository.kt`:
  - `RestoreResult` gains `/** D98. */ val trainerProgrammes: Int = 0,`; `inFile` sets
    `trainerProgrammes = backup.trainerProgrammes.size`.
  - `export`: `trainerProgrammes = trainer.allProgrammes().map { it.toBackup() },`
  - `restore`: `trainer.deleteProgrammes()` after `trainer.deletePlans()`; `trainer.insertProgrammes(prepared.programmes)`
    after `trainer.insertReviews(prepared.reviews)`; the result gains `trainerProgrammes = prepared.programmes.size`.
  - `prepare`: `programmes = backup.trainerProgrammes.distinctBy { it.id }.map { it.toEntity() },`
  - `Prepared` gains `val programmes: List<TrainerProgrammeEntity>,` after `reviews`.
  - `whatIsHere`: `trainerProgrammes = trainer.allProgrammes().size,`
  - Beside `TrainerPlanEntity.toBackup()`:

```kotlin
private fun TrainerProgrammeEntity.toBackup() = BackupTrainerProgramme(
    id, createdAtMillis, weeks, perWeek, words, evaluation, plan, model, startEpochDay, status, stoppedEpochDay, replacesId,
)

private fun BackupTrainerProgramme.toEntity() = TrainerProgrammeEntity(
    id, createdAtMillis, weeks, perWeek, words, evaluation, plan, model, startEpochDay, status, stoppedEpochDay, replacesId,
)
```

  `BackupWording.kt`:

```kotlin
    fun confirmReplacing(here: RestoreResult, incoming: RestoreResult): String = buildString {
        val withHealth = hasHealth(here) || hasHealth(incoming)
        val withTrainer = hasTrainer(here) || hasTrainer(incoming)
        val withWeeks = hasWeeks(here) || hasWeeks(incoming)
        append("This will delete ")
        append(record(here, withHealth, withTrainer, withWeeks))
        append(" already on this phone, and put back ")
        append(record(incoming, withHealth, withTrainer, withWeeks))
        append(" from the file.")
        if (here.meals == 0 && here.weights == 0 && !hasHealth(here) && !hasTrainer(here) && !hasWeeks(here)) {
            append(" There is nothing here to lose.")
        }
    }
```

```kotlin
    private fun hasWeeks(result: RestoreResult): Boolean = result.trainerProgrammes > 0

    private fun record(
        result: RestoreResult,
        withHealth: Boolean = hasHealth(result),
        withTrainer: Boolean = hasTrainer(result),
        withWeeks: Boolean = hasWeeks(result),
    ): String {
        val parts = listOf(meals(result.meals), weights(result.weights)) +
            (if (withHealth) listOf(workouts(result.workouts), healthDays(result.healthDays)) else emptyList()) +
            (if (withTrainer) listOf(trainerPlans(result.trainerPlans), sessionReviews(result.trainerReviews)) else emptyList()) +
            if (withWeeks) listOf(weeklyPlans(result.trainerProgrammes)) else emptyList()
        return parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    private fun weeklyPlans(count: Int): String = if (count == 1) "1 weekly plan" else "$count weekly plans"
```

  The confirmation's KDoc gains "…and the weekly plans (D98)…".

- [ ] **Step 4: Run, expect PASS** (same command); also
  `--tests "com.metaself.app.ui.screen.settings.SettingsViewModelTest"` (its DAO proxy must answer
  `allProgrammes`: if it fails there, make the proxy's `all*` branch return an empty list for it, as it
  does for the other `all*` calls).
- [ ] **Step 5: Commit** `Backup.kt`, `BackupRepository.kt`, `BackupWording.kt`, `BackupCodecTest.kt`,
  `BackupRestoreOrderTest.kt`, `BackupWordingTest.kt`, `BackupRoundTripTest.kt` (and
  `SettingsViewModelTest.kt` if touched): `feat: weekly plans ride in the backup, format 8 (D98)`.

---
### Task 10: The running plan on the Trainer screen — pure

**Files:**
- Modify: `domain/trainer/TrainerHome.kt` (+ `PlanCard`, `next`)
- Test: `domain/trainer/TrainerHomeTest.kt`

- [ ] **Step 1: Failing tests.** Append to `TrainerHomeTest` (read its fixtures first; `session(...)` below
  is a helper to add if the file has none — a synced 30-minute session at 07:00 on a day, invented):

```kotlin
    // --- D95, D97. The plan starts Monday 31 August 2026 (TEST_EPOCH_DAY - 3). --------------------

    private val start = TEST_EPOCH_DAY - 3
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
    private val walk40 = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Steady walk")
    private val running = Programme(
        id = 1, createdAtMillis = 0, ask = ProgrammeAsk(2, 2), evaluation = null,
        plan = WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(walk30, walk40)) }, "Invented."),
        model = "a-model", startEpochDay = start, status = ProgrammeStatus.RUNNING,
    )

    @Test
    fun `a running plan shows its week, its ticks and the next planned session`() {
        val card = PlanCard.of(running, listOf(planSession(1, start + 1)), today = TEST_EPOCH_DAY) as PlanCard.Running

        assertThat(card.weekIndex).isEqualTo(0)
        assertThat(card.progress.weeks.first().done).isEqualTo(1)
        assertThat(card.next).isEqualTo(PlannedTick(1, walk40))
    }

    @Test
    fun `before week 1 there is no next session, and after the last day the plan has ended for fourteen days`() {
        val later = running.copy(startEpochDay = start + 7)
        val before = PlanCard.of(later, emptyList(), today = TEST_EPOCH_DAY) as PlanCard.Running
        assertThat(before.weekIndex).isEqualTo(-1)
        assertThat(before.next).isNull()

        val last = start + 13
        assertThat(PlanCard.of(running, emptyList(), today = last + 1)).isInstanceOf(PlanCard.Ended::class.java)
        assertThat(PlanCard.of(running, emptyList(), today = last + 15)).isEqualTo(PlanCard.None)
        assertThat(PlanCard.of(null, emptyList(), today = TEST_EPOCH_DAY)).isEqualTo(PlanCard.None)
    }

    @Test
    fun `the home carries the card and the next session`() {
        val home = TrainerHome.of(
            today = TEST_EPOCH_DAY, nowMillis = 0, recent = emptyList(), reviewed = emptyList(), reviews = emptyList(),
            kept = null, running = running, planWorkouts = emptyList(),
        )

        assertThat(home.plan).isInstanceOf(PlanCard.Running::class.java)
        assertThat(home.next).isEqualTo(PlannedTick(1, walk30))
    }

    private fun planSession(id: Long, day: Long) = Workout(
        id = id, epochDay = day, startedAtMillis = day * 86_400_000L + 7 * 3_600_000L, durationMinutes = 30,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )
```

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.trainer.TrainerHomeTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement** in `TrainerHome.kt`. `TrainerHome` gains two defaulted fields (existing
  `TrainerHome(a, b, c)` calls in tests keep compiling):

```kotlin
data class TrainerHome(
    val waiting: Workout?,
    val keptPlan: TrainerPlan?,
    val earlier: List<ReviewedSession>,
    /** D95, D97: the weekly plan's card. */
    val plan: PlanCard = PlanCard.None,
    /** D96: the running plan's next session this week, for Plan my next session. */
    val next: PlannedTick? = null,
)
```

  `of(...)` gains `running: Programme? = null, planWorkouts: List<Workout> = emptyList()` as its last two
  parameters and ends:

```kotlin
            val plan = PlanCard.of(running, planWorkouts, today)
            return TrainerHome(waiting, PlanMatch.offered(kept, nowMillis), earlier, plan, (plan as? PlanCard.Running)?.next)
```

  New, in the same file:

```kotlin
/** D95, D97: what the Trainer screen shows of the weekly plan. */
sealed interface PlanCard {
    /** No plan runs, or one ended more than fourteen days ago: the card offers an evaluation. */
    data object None : PlanCard

    /** [weekIndex] is 0 for week 1, and -1 before it starts (kept Friday to Sunday). */
    data class Running(val programme: Programme, val progress: PlanProgress, val weekIndex: Int) : PlanCard {
        /** This week's first unticked planned session, with its week counted from 1; none before week 1. */
        val next: PlannedTick?
            get() = if (weekIndex in progress.weeks.indices) {
                progress.weeks[weekIndex].next?.let { PlannedTick(weekIndex + 1, it) }
            } else {
                null
            }
    }

    /** The day after its last Sunday, for fourteen days. */
    data class Ended(val programme: Programme, val progress: PlanProgress) : PlanCard

    companion object {
        /** [workouts]: the record over the plan's weeks; only its counted sessions tick (D95). */
        fun of(running: Programme?, workouts: List<Workout>, today: Long): PlanCard {
            val start = running?.startEpochDay
            if (running == null || start == null || running.status != ProgrammeStatus.RUNNING) return None
            val last = ProgrammeCalendar.lastDay(start, running.ask.weeks)
            val progress = PlanProgress.of(running.plan, start, workouts, minOf(today, last))
            return when {
                today <= last -> Running(running, progress, ProgrammeCalendar.weekIndex(start, today).coerceAtLeast(-1))
                ProgrammeCalendar.endedShown(start, running.ask.weeks, today) -> Ended(running, progress)
                else -> None
            }
        }
    }
}
```

  The class KDoc gains "…the weekly plan's card (D95, D97)…".

- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** `TrainerHome.kt`, `TrainerHomeTest.kt`: `feat: the Trainer screen knows the running plan (D95, D97)`.

---

### Task 11: Asking — evaluate, keep, adjust, stop, and the planned session carried

**Files:**
- Modify: `data/trainer/AskTheTrainer.kt`
- Modify (test): `data/trainer/AskTheTrainerTest.kt`, `ui/screen/trainer/TrainerScreens.kt`

- [ ] **Step 1: Failing tests.** In `AskTheTrainerTest`: add `private val programmes = FakeProgrammeStore()`,
  pass it in `ask()` (after `aboutMe`), and append (invented; the store's plan starts Monday 31 August):

```kotlin
    // --- D93–D98 -----------------------------------------------------------------------------------

    @Test
    fun `an evaluation is stored as offered, with the form's answers, and sent with the start it would have`() = runTest {
        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, WEEKS), "a-model")

        val outcome = ask().evaluate(ProgrammeAsk(2, 2, "Invented words.")) as AskTheTrainer.Evaluated.Offered

        assertThat(outcome.programme.status).isEqualTo(ProgrammeStatus.OFFERED)
        assertThat(outcome.programme.evaluation).isEqualTo(EVALUATION)
        assertThat(programmes.rows.value).hasSize(1)
        val question = trainer.asked.single().question as TrainerQuestion.Evaluate
        assertThat(question.startEpochDay).isEqualTo(MONDAY)
        assertThat(question.last).isNull()
    }

    @Test
    fun `a failed evaluation stores nothing and says why`() = runTest {
        trainer.evaluations += TrainerReply.Failed(EstimateResult.NoKey)

        assertThat(ask().evaluate(ProgrammeAsk(2, 2))).isEqualTo(AskTheTrainer.Evaluated.Failed(EstimateResult.NoKey))
        assertThat(programmes.rows.value).isEmpty()
    }

    @Test
    fun `keeping starts it from this week's Monday and replaces a running plan`() = runTest {
        val old = programmes.add(offered())
        programmes.keep(old, MONDAY - 14, TEST_EPOCH_DAY - 14)
        val new = programmes.add(offered())

        val start = ask().keepProgramme(new)

        assertThat(start).isEqualTo(MONDAY)
        assertThat(programmes.running()!!.id).isEqualTo(new)
        assertThat(programmes.rows.value.first { it.id == old }.status).isEqualTo(ProgrammeStatus.REPLACED)
    }

    @Test
    fun `the last kept evaluation is sent with the plan that ran and its done-counts`() = runTest {
        val first = programmes.add(offered())
        programmes.keep(first, MONDAY, TEST_EPOCH_DAY)
        store.workouts.value = listOf(walk(id = 1, day = MONDAY), walk(id = 2, day = MONDAY + 1))
        record.workouts.value = store.workouts.value
        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, WEEKS), "a-model")

        ask().evaluate(ProgrammeAsk(2, 2))

        val last = (trainer.asked.single().question as TrainerQuestion.Evaluate).last!!
        assertThat(last.evaluation).isEqualTo(EVALUATION)
        assertThat(last.doneByWeek).containsExactly(2)
    }

    @Test
    fun `the plan form's question carries the next planned session`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        assertThat(ask().nextPlanned()).isEqualTo(PlannedTick(1, WALK_30))
        ask().suggest(ANSWERS)

        assertThat((trainer.asked.single().question as TrainerQuestion.Plan).planned).isEqualTo(PlannedTick(1, WALK_30))
    }

    @Test
    fun `feedback is told which planned session the session ticked`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.feedback += TrainerReply.Answered(FEEDBACK.copy(followed = PlanFollowed.NO_PLAN), "a-model")

        ask().save(workoutId = 1, felt = Felt.RIGHT, words = "", planId = null, withFeedback = true)

        assertThat((trainer.asked.single().question as TrainerQuestion.Review).planned).isEqualTo(PlannedTick(1, WALK_30))
    }

    @Test
    fun `adjusting sends the phone's counts and composes past weeks, this week's ticks and the answer`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, TEST_EPOCH_DAY - 7)
        store.workouts.value = listOf(walk(id = 1, day = MONDAY - 7), walk(id = 2, day = MONDAY))
        record.workouts.value = store.workouts.value
        val rest = WeeksPlan("Invented new", listOf(PlanWeek("lighter", listOf(WALK_20))), "Invented.")
        trainer.adjustments += TrainerReply.Answered(rest, "a-model")

        val outcome = ask().adjust("Invented words.") as AskTheTrainer.Adjusted.Offered

        val question = trainer.asked.single().question as TrainerQuestion.Adjust
        assertThat(question.weekIndex).isEqualTo(1)
        assertThat(question.doneByWeek).containsExactly(1)
        assertThat(question.tickedThisWeek).containsExactly(WALK_30)
        assertThat(question.thisWeekMax).isEqualTo(1)
        val composed = outcome.programme.plan
        assertThat(composed.weeks.first()).isEqualTo(WEEKS.weeks.first())
        assertThat(composed.weeks[1].sessions).containsExactly(WALK_30, WALK_20).inOrder()
        assertThat(outcome.programme.replacesId).isEqualTo(id)
        assertThat(outcome.programme.status).isEqualTo(ProgrammeStatus.OFFERED)
    }

    @Test
    fun `with no plan running, adjusting asks nothing`() = runTest {
        assertThat(ask().adjust("Invented.")).isEqualTo(AskTheTrainer.Adjusted.NotRunning)
        assertThat(trainer.asked).isEmpty()
    }

    @Test
    fun `keeping the adjusted version runs it from the old start; stopping ends it today`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        val version = programmes.add(offered().copy(replacesId = id))

        ask().keepAdjusted(programmes.rows.value.first { it.id == version })
        assertThat(programmes.running()!!.id).isEqualTo(version)
        assertThat(programmes.running()!!.startEpochDay).isEqualTo(MONDAY)

        ask().stop(version)
        assertThat(programmes.running()).isNull()
        assertThat(programmes.rows.value.first { it.id == version }.stoppedEpochDay).isEqualTo(TEST_EPOCH_DAY)
    }
```

  Companion additions:

```kotlin
        val MONDAY = TEST_EPOCH_DAY - 3
        val WALK_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
        val WALK_20 = PlannedSession(WorkoutKind.WALK, 20, PlannedEffort.EASY, "Short walk")
        val WEEKS = WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK_30, WALK_30)) }, "Invented reason.")
        val EVALUATION = Evaluation("Invented headline.", "Invented.", "Invented.", "")

        /** An evaluation made at noon, so its day is the same in any zone within eleven hours of UTC. */
        fun offered() = Programme(0, TEST_EPOCH_DAY * DAY + 12 * HOUR, ProgrammeAsk(2, 2), EVALUATION, WEEKS, "a-model")
```

  (`walk(id, day)` starts at 08:00 — the file's existing helper; `TEST_EPOCH_DAY` walk id 1 is set up in
  `setUp`, which is the session the feedback test ticks.)

  In `ui/screen/trainer/TrainerScreens.kt`, `ask(...)` gains
  `programmes: ProgrammeStore = FakeProgrammeStore()` as its last parameter and passes it on.

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.trainer.AskTheTrainerTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement** in `AskTheTrainer`. Constructor gains `private val programmes: ProgrammeStore,`
  after `aboutMe`. New results beside `Suggested`:

```kotlin
    sealed interface Evaluated {
        data class Offered(val programme: Programme) : Evaluated
        data class Failed(val failure: EstimateResult) : Evaluated
    }

    sealed interface Adjusted {
        /** The new version, stored OFFERED, not yet kept (D97). */
        data class Offered(val programme: Programme) : Adjusted
        data class Failed(val failure: EstimateResult) : Adjusted

        /** No plan runs, or it has ended: nothing was asked. */
        data object NotRunning : Adjusted
    }
```

  `suggest` sends the next planned session:

```kotlin
    suspend fun suggest(answers: PlanAnswers): Suggested =
        when (val reply = trainer.suggest(request(TrainerQuestion.Plan(answers, nextPlanned()), exceptWorkoutId = NO_WORKOUT))) {
```

  `save`, where the review question is built:

```kotlin
        val question = TrainerRequest.reviewQuestion(workout, saved, plan).copy(planned = running()?.progress?.tickOf(workout.id))
```

  New methods:

```kotlin
    /** D93: one ask; the answer is stored OFFERED. The plan would start on the Monday keeping it today gives (D94). */
    suspend fun evaluate(ask: ProgrammeAsk): Evaluated {
        val day = today().toEpochDay()
        val question = TrainerQuestion.Evaluate(ask, ProgrammeCalendar.startFor(day), lastEvaluation(day))
        return when (val reply = trainer.evaluate(request(question, exceptWorkoutId = NO_WORKOUT))) {
            is TrainerReply.Failed -> Evaluated.Failed(reply.failure)
            is TrainerReply.Answered -> {
                val programme = Programme(0, now(), ask, reply.value.evaluation, reply.value.plan, reply.model)
                Evaluated.Offered(programme.copy(id = programmes.add(programme)))
            }
        }
    }

    /** D94: [id] runs from the Monday keeping it today gives, which is returned; a running plan is replaced. */
    suspend fun keepProgramme(id: Long): Long {
        val day = today().toEpochDay()
        val start = ProgrammeCalendar.startFor(day)
        programmes.keep(id, start, day)
        return start
    }

    /**
     * D97: the running plan's rest, rewritten. The model returns this week's sessions still to do and
     * the weeks after; the phone composes the version — past weeks unchanged, this week's ticked sessions
     * first — and stores it OFFERED (design questions 4, 5).
     */
    suspend fun adjust(words: String): Adjusted {
        val running = running() ?: return Adjusted.NotRunning
        val programme = running.programme
        val start = requireNotNull(programme.startEpochDay)
        val index = running.weekIndex.coerceAtLeast(0)
        val week = running.progress.weeks[index]
        val ticked = week.ticks.filter { it.by != null }.map { it.planned }
        val question = TrainerQuestion.Adjust(
            ask = programme.ask,
            startEpochDay = start,
            plan = programme.plan,
            weekIndex = index,
            doneByWeek = running.progress.weeks.take(index).map { it.done },
            tickedThisWeek = ticked,
            thisWeekMax = week.planned - ticked.size,
            words = words.trim(),
        )
        return when (val reply = trainer.adjust(request(question, exceptWorkoutId = NO_WORKOUT))) {
            is TrainerReply.Failed -> Adjusted.Failed(reply.failure)
            is TrainerReply.Answered -> {
                val rest = reply.value
                val thisWeek = PlanWeek(rest.weeks.first().focus, ticked + rest.weeks.first().sessions)
                val composed = WeeksPlan(rest.title, programme.plan.weeks.take(index) + thisWeek + rest.weeks.drop(1), rest.why)
                val version = Programme(
                    id = 0, createdAtMillis = now(), ask = programme.ask.copy(words = words.trim()), evaluation = null,
                    plan = composed, model = reply.model, replacesId = programme.id,
                )
                Adjusted.Offered(version.copy(id = programmes.add(version)))
            }
        }
    }

    /** D97: Keep this version. Refused (throws) when the plan it adjusts no longer runs (design question 6). */
    suspend fun keepAdjusted(version: Programme) =
        programmes.keepAdjusted(version.id, requireNotNull(version.replacesId) { "not an adjusted version" }, today().toEpochDay())

    /** D97: Stop this plan. */
    suspend fun stop(id: Long) = programmes.stop(id, today().toEpochDay())

    /** The running plan with its ticks, while it runs; null when none runs or it has ended. Reads only. */
    suspend fun running(): PlanCard.Running? {
        val programme = programmes.running() ?: return null
        val start = programme.startEpochDay ?: return null
        val workouts = record.observeWorkouts(start, ProgrammeCalendar.lastDay(start, programme.ask.weeks)).first()
        return PlanCard.of(programme, workouts, today().toEpochDay()) as? PlanCard.Running
    }

    /** D96: the running plan's next session this week, for the plan form. Reads only; nothing is sent. */
    suspend fun nextPlanned(): PlannedTick? = running()?.next

    /** D98: the evaluation of the running or last plan — its own, or its chain's (design question 3). */
    suspend fun evaluationOf(programme: Programme): Evaluation? = Programmes.evaluationOf(programme, programmes.all())

    /** D93, D98: the last kept evaluation with the newest kept version of its plan and its done-counts. */
    private suspend fun lastEvaluation(day: Long): LastEvaluation? {
        val (evaluated, latest) = Programmes.lastEvaluated(programmes.all()) ?: return null
        val evaluation = evaluated.evaluation ?: return null
        val start = latest.startEpochDay ?: return null
        val until = Programmes.countedUntil(latest, day)
        val workouts = if (until < start) emptyList() else record.observeWorkouts(start, until).first()
        val progress = PlanProgress.of(latest.plan, start, workouts, until)
        val weeksBegun = (ProgrammeCalendar.weekIndex(start, until) + 1).coerceIn(0, latest.ask.weeks)
        val madeOn = Instant.ofEpochMilli(evaluated.createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        return LastEvaluation(madeOn, evaluation, latest.plan, progress.weeks.take(weeksBegun).map { it.done })
    }
```

  Imports: `Evaluation`, `LastEvaluation`, `PlanCard`, `PlanProgress`, `PlanWeek`, `PlannedTick`,
  `Programme`, `ProgrammeAsk`, `ProgrammeCalendar`, `Programmes`, `WeeksPlan`, `java.time.Instant`,
  `java.time.ZoneId`. The class KDoc: "The trainer's uses (D86, D87, D93, D97)…".

- [ ] **Step 4: Run, expect PASS**; then the trainer screens' tests, which build `AskTheTrainer` through
  `TrainerScreens.ask`:
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.trainer.*" --tests "com.metaself.app.ui.screen.trainer.*" > /tmp/ms-weeks.log 2>&1; echo "exit $?"` → 0.
- [ ] **Step 5: Commit** `AskTheTrainer.kt`, `AskTheTrainerTest.kt`, `TrainerScreens.kt`:
  `feat: ask for an evaluation, keep, adjust and stop a weekly plan (D93–D97)`.

---
### Task 12: Wording

**Files:**
- Create: `ui/trainer/ProgrammeWording.kt`
- Modify: `ui/trainer/TrainerWording.kt` (privacy lines), `app/src/main/res/values/strings.xml`
- Test: `ui/trainer/ProgrammeWordingTest.kt`, `ui/trainer/TrainerWordingTest.kt`

- [ ] **Step 1: Failing tests** `ui/trainer/ProgrammeWordingTest.kt`:

```kotlin
package com.metaself.app.ui.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.WeeksPlan
import org.junit.jupiter.api.Test

/** What the weekly plan's screens say (D94–D97). The plan starts Monday 31 August 2026; every figure is invented. */
class ProgrammeWordingTest {

    private val start = TEST_EPOCH_DAY - 3
    private val steady = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Invented line")
    private val plan = WeeksPlan("Invented", List(4) { PlanWeek("w", listOf(steady, steady, steady)) }, "Invented.")

    @Test
    fun `a planned session is its effort, its kind and its minutes`() {
        assertThat(ProgrammeWording.plannedTitle(steady)).isEqualTo("Steady walk, 40 min")
        assertThat(ProgrammeWording.plannedTitle(PlannedSession(WorkoutKind.CYCLE, 60, PlannedEffort.PUSH, "x"))).isEqualTo("Push ride, 60 min")
        assertThat(ProgrammeWording.plannedTitle(PlannedSession(WorkoutKind.OTHER, 20, PlannedEffort.EASY, "x"))).isEqualTo("Easy session, 20 min")
        assertThat(ProgrammeWording.nextInPlan(PlannedTick(2, steady))).isEqualTo("Next in your plan: steady walk, 40 min")
    }

    @Test
    fun `the card names the plan's length and week, or when it starts`() {
        assertThat(ProgrammeWording.cardHeading(4, 1, start)).isEqualTo("YOUR 4-WEEK PLAN · WEEK 2 OF 4")
        assertThat(ProgrammeWording.cardHeading(4, -1, start + 7)).isEqualTo("YOUR 4-WEEK PLAN · STARTS MON 7 SEP")
        assertThat(ProgrammeWording.weekDates(start)).isEqualTo("This week, Mon 31 Aug – Sun 6 Sep")
        assertThat(ProgrammeWording.span(start, 4)).isEqualTo("Starts Mon 31 Aug, ends Sun 27 Sep")
        assertThat(ProgrammeWording.planHeading(ProgrammeAsk(4, 3))).isEqualTo("THE PLAN · 4 WEEKS · 3 SESSIONS A WEEK")
        assertThat(ProgrammeWording.weekTitle(1, "settle in")).isEqualTo("Week 1 · settle in")
        assertThat(ProgrammeWording.weekTitle(2, " ")).isEqualTo("Week 2")
    }

    @Test
    fun `ticks say done or to do, and which session did it`() {
        val progress = PlanProgress.of(plan, start, listOf(session(1, start)), until = TEST_EPOCH_DAY)
        val week = progress.weeks.first()

        assertThat(ProgrammeWording.tickTitle(week.ticks[0])).isEqualTo("Done: Steady walk, 40 min")
        assertThat(ProgrammeWording.tickTitle(week.ticks[1])).isEqualTo("To do: Steady walk, 40 min")
        assertThat(ProgrammeWording.tickLine(week.ticks[0].by!!)).isEqualTo("Done Mon · Walking, 32 min")
        assertThat(ProgrammeWording.pastWeek(week)).isEqualTo("Week 1: 1 of 3 done")
        assertThat(ProgrammeWording.thisWeekSoFar(week)).isEqualTo("Week 1 (this week): 1 of 3 so far")
    }

    @Test
    fun `an ended plan counts every week, and the adjust page says what is rewritten`() {
        val progress = PlanProgress.of(plan, start, listOf(session(1, start), session(2, start + 7)), until = start + 27)

        assertThat(ProgrammeWording.endedHeading(4)).isEqualTo("YOUR 4-WEEK PLAN HAS ENDED")
        assertThat(ProgrammeWording.endedLine(progress)).isEqualTo("2 of 12 planned sessions done · weeks 1 of 3, 1 of 3, 0 of 3, 0 of 3")
        assertThat(ProgrammeWording.rewritten(3, 4)).isEqualTo("Weeks 3 and 4: to be rewritten")
        assertThat(ProgrammeWording.rewritten(2, 4)).isEqualTo("Weeks 2 to 4: to be rewritten")
        assertThat(ProgrammeWording.rewritten(4, 4)).isEqualTo("Week 4: to be rewritten")
        assertThat(ProgrammeWording.adjustRule(start, 4)).isEqualTo(
            "The trainer rewrites this week's remaining sessions and the weeks after. Weeks already over stay as they were. " +
                "The end date stays Sun 27 Sep.",
        )
    }

    private fun session(id: Long, day: Long) = Workout(
        id = id, epochDay = day, startedAtMillis = day * 86_400_000L + 7 * 3_600_000L, durationMinutes = 32,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )
}
```

  Append to `TrainerWordingTest`:

```kotlin
    /** D93, D97 (design question 10): each weekly-plan request names what it adds. */
    @Test
    fun `the weekly plan's privacy lines name what they add`() {
        assertThat(TrainerWording.privacyEvaluate(30)).contains("your last evaluation and how its plan went")
        assertThat(TrainerWording.privacyAdjust(30)).startsWith("Sends to OpenAI, with your key: your plan, how it has gone, and your words;")
        assertThat(TrainerWording.privacyPlan(30, withPlan = true)).contains("these answers and the next session in your weekly plan")
        assertThat(TrainerWording.privacyPlan(30)).isEqualTo(TrainerWording.privacyPlan(30, withPlan = false))
        listOf(TrainerWording.privacyEvaluate(30), TrainerWording.privacyAdjust(30)).forEach {
            assertThat(it).endsWith("One of today's 30 AI requests.")
        }
    }
```

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.trainer.*" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement** `ui/trainer/ProgrammeWording.kt`:

```kotlin
package com.metaself.app.ui.trainer

import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.Tick
import com.metaself.app.domain.trainer.WeekProgress
import com.metaself.app.ui.movement.MovementWeekWording
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the weekly plan's screens say (D94–D97). Every count here is the phone's (D95). */
object ProgrammeWording {

    private const val SEP = " · "
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US)

    fun effort(effort: PlannedEffort): String = when (effort) {
        PlannedEffort.EASY -> "Easy"
        PlannedEffort.STEADY -> "Steady"
        PlannedEffort.PUSH -> "Push"
    }

    private fun noun(kind: WorkoutKind): String = when (kind) {
        WorkoutKind.WALK -> "walk"
        WorkoutKind.RUN -> "run"
        WorkoutKind.CYCLE -> "ride"
        WorkoutKind.SWIM -> "swim"
        WorkoutKind.STRENGTH -> "strength session"
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "session"
    }

    /** "Steady walk, 40 min" (invented). */
    fun plannedTitle(session: PlannedSession): String = "${effort(session.effort)} ${noun(session.kind)}, ${session.minutes} min"

    /** D96: "Next in your plan: steady walk, 40 min". */
    fun nextInPlan(tick: PlannedTick): String =
        "Next in your plan: " + plannedTitle(tick.session).replaceFirstChar { it.lowercase(Locale.US) }

    /** "YOUR 4-WEEK PLAN · WEEK 2 OF 4", or before week 1, "… · STARTS MON 7 SEP". */
    fun cardHeading(weeks: Int, weekIndex: Int, start: Long): String =
        "YOUR $weeks-WEEK PLAN$SEP" + if (weekIndex >= 0) {
            "WEEK ${weekIndex + 1} OF $weeks"
        } else {
            "STARTS " + date(start).uppercase(Locale.US)
        }

    fun weekDates(monday: Long): String = "This week, ${date(monday)} – ${date(monday + 6)}"

    fun span(start: Long, weeks: Int): String = "Starts ${date(start)}, ends ${date(ProgrammeCalendar.lastDay(start, weeks))}"

    fun planHeading(ask: ProgrammeAsk): String = "THE PLAN$SEP${ask.weeks} WEEKS$SEP${ask.perWeek} SESSIONS A WEEK"

    fun weekTitle(number: Int, focus: String): String = if (focus.isBlank()) "Week $number" else "Week $number$SEP${focus.trim()}"

    fun tickTitle(tick: Tick): String = (if (tick.by != null) "Done: " else "To do: ") + plannedTitle(tick.planned)

    /** Design question 8: "Done Mon · Walking, 32 min". */
    fun tickLine(workout: Workout): String =
        "Done ${LocalDate.ofEpochDay(workout.epochDay).format(WEEKDAY)}$SEP${MovementWeekWording.name(workout)}, ${workout.durationMinutes} min"

    fun pastWeek(week: WeekProgress): String = "Week ${week.index + 1}: ${week.done} of ${week.planned} done"

    fun thisWeekSoFar(week: WeekProgress): String = "Week ${week.index + 1} (this week): ${week.done} of ${week.planned} so far"

    fun endedHeading(weeks: Int): String = "YOUR $weeks-WEEK PLAN HAS ENDED"

    fun endedLine(progress: PlanProgress): String =
        "${progress.done} of ${progress.planned} planned sessions done$SEP" +
            "weeks " + progress.weeks.joinToString(", ") { "${it.done} of ${it.planned}" }

    /** Weeks [from] to [to], counted from 1. */
    fun rewritten(from: Int, to: Int): String = when (to - from) {
        0 -> "Week $from: to be rewritten"
        1 -> "Weeks $from and $to: to be rewritten"
        else -> "Weeks $from to $to: to be rewritten"
    }

    fun adjustRule(start: Long, weeks: Int): String =
        "The trainer rewrites this week's remaining sessions and the weeks after. Weeks already over stay as they were. " +
            "The end date stays ${date(ProgrammeCalendar.lastDay(start, weeks))}."

    private fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DAY)
}
```

  In `TrainerWording`, the privacy part becomes:

```kotlin
    /**
     * Design question 20: everything one request holds (D84), under the plan form's ask button — with a
     * running weekly plan, its next session too (D96).
     */
    fun privacyPlan(ceiling: Int, withPlan: Boolean = false): String =
        privacy(if (withPlan) "these answers and the next session in your weekly plan" else "these answers", ceiling)

    /** D93: the evaluation's request also holds the last evaluation and how its plan went. */
    fun privacyEvaluate(ceiling: Int): String = privacy("these answers", ceiling, extra = "your last evaluation and how its plan went")

    /** D97. */
    fun privacyAdjust(ceiling: Int): String = privacy("your plan, how it has gone, and your words", ceiling)

    private fun privacy(first: String, ceiling: Int, extra: String? = null): String =
        "Sends to OpenAI, with your key: $first; your note about yourself; " +
            "your sessions of the last six weeks, with your words on them; weekly totals; " +
            "a line for each month of the year before; your weight trend and goal rate; your age, sex and height; " +
            (extra?.let { "$it; " } ?: "") +
            "and the trainer's last three feedbacks. One of today's $ceiling AI requests."
```

  `strings.xml`, after `feedback_plan_next`:

```xml
    <string name="trainer_evaluate">Evaluate me and plan the weeks ahead</string>
    <string name="trainer_evaluate_again">Evaluate me and plan again</string>
    <string name="trainer_see_plan">See the plan</string>
    <string name="trainer_adjust_plan">Adjust the plan</string>
    <string name="weeks_title">Evaluate me and plan</string>
    <string name="weeks_intro">The trainer reads your record — a year of monthly lines, six weeks of sessions, your note — says where you stand, then plans the weeks ahead.</string>
    <string name="weeks_row_weeks">HOW MANY WEEKS</string>
    <string name="weeks_row_per_week">SESSIONS A WEEK I CAN MANAGE</string>
    <string name="weeks_result_title">Evaluation and plan</string>
    <string name="weeks_running_title">Your plan</string>
    <string name="weeks_not_running">No plan is running.</string>
    <string name="weeks_where">WHERE YOU STAND</string>
    <string name="weeks_going_well">Going well.</string>
    <string name="weeks_to_work_on">To work on.</string>
    <string name="weeks_since_last">Since last time.</string>
    <string name="weeks_why">Why.</string>
    <string name="weeks_keep">Keep this plan</string>
    <string name="weeks_keep_note">Keeping it replaces any plan you have now. The evaluation is kept either way.</string>
    <string name="weeks_kept">Kept. It is on the Trainer screen.</string>
    <string name="adjust_title">Adjust the plan</string>
    <string name="adjust_so_far">SO FAR, COUNTED ON THE PHONE</string>
    <string name="adjust_words">What should change?</string>
    <string name="adjust_ask">Adjust</string>
    <string name="adjust_keep_new">Keep this version</string>
    <string name="adjust_keep_old">Keep the old one</string>
    <string name="adjust_stop">Stop this plan</string>
    <string name="adjust_stop_question">Stop this plan? It is kept on record.</string>
    <string name="adjust_stop_yes">Stop it</string>
    <string name="adjust_stop_no">Keep going</string>
```

  (The form's words field reuses `plan_words`; the ask button `plan_ask` / `plan_asking`.)

- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** `ProgrammeWording.kt`, `TrainerWording.kt`, `strings.xml`, `ProgrammeWordingTest.kt`,
  `TrainerWordingTest.kt`: `feat: the weekly plan's wording (D94–D97)`.

---

### Task 13: The Trainer screen's plan card

**Files:**
- Modify: `ui/screen/trainer/TrainerViewModel.kt`, `ui/screen/trainer/TrainerScreen.kt`
- Test: `ui/screen/trainer/TrainerViewModelTest.kt`, `ui/screen/trainer/TrainerScreenRenderTest.kt`

- [ ] **Step 1: Failing tests.** `TrainerViewModelTest`: add `private val programmes = FakeProgrammeStore()`
  beside `store`, pass it in `viewModel(...)` (`TrainerViewModel(movement, store, programmes, aboutMe, …)`),
  and add:

```kotlin
    /** D95: a plan kept elsewhere shows at once, ticked from the record. Invented. */
    @Test
    fun `a running plan shows with its ticks`() = runTest {
        record.workouts.value = listOf(walk(1, TEST_EPOCH_DAY))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        val id = programmes.add(RUNNING_PLAN)
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        advanceUntilIdle()

        val card = viewModel.state.value.home!!.plan as PlanCard.Running
        assertThat(card.progress.weeks.first().done).isEqualTo(1)
    }
```

  Companion: `val RUNNING_PLAN = Programme(0, NOW, ProgrammeAsk(2, 2), null, WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(EASY_30, EASY_30)) }, "Invented."), "a-model")`
  with `val EASY_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")`.

  `TrainerScreenRenderTest` — `draw(...)` gains `onEvaluate`, `onSeePlan`, `onAdjust` (default `{}`) passed
  through; add:

```kotlin
    @Test
    fun `with no plan the screen offers an evaluation`() {
        var asked = false
        val texts = draw(TrainerHome(null, null, emptyList()), onEvaluate = { asked = true })

        assertThat(texts).contains("Evaluate me and plan the weeks ahead")
        render.click("Evaluate me and plan the weeks ahead")
        assertThat(asked).isTrue()
    }

    @Test
    fun `a running plan shows its week, its ticks, the doors, and the next session under Plan my next session`() {
        var adjusted = false
        val card = PlanCard.of(RUNNING, listOf(WAITING.copy(epochDay = TEST_EPOCH_DAY - 3)), TEST_EPOCH_DAY) as PlanCard.Running
        val texts = draw(TrainerHome(null, null, emptyList(), card, card.next), onAdjust = { adjusted = true })

        assertThat(texts).contains("YOUR 2-WEEK PLAN · WEEK 1 OF 2")
        assertThat(texts).contains("This week, Mon 31 Aug – Sun 6 Sep")
        assertThat(texts).contains("Done: Easy walk, 30 min")
        assertThat(texts).contains("To do: Easy walk, 30 min")
        assertThat(texts).contains("Next in your plan: easy walk, 30 min")
        assertThat(texts).contains("See the plan")
        render.click("Adjust the plan")
        assertThat(adjusted).isTrue()
    }

    @Test
    fun `an ended plan shows its count and offers a new evaluation`() {
        val ended = PlanCard.of(RUNNING, emptyList(), TEST_EPOCH_DAY + 11) as PlanCard.Ended
        val texts = draw(TrainerHome(null, null, emptyList(), ended))

        assertThat(texts).contains("YOUR 2-WEEK PLAN HAS ENDED")
        assertThat(texts).contains("0 of 4 planned sessions done · weeks 0 of 2, 0 of 2")
        assertThat(texts).contains("Evaluate me and plan again")
    }
```

  Companion: `RUNNING` = `Programme(1, 0, ProgrammeAsk(2, 2), null, WeeksPlan("Invented plan", List(2) { PlanWeek("w", listOf(EASY_30, EASY_30)) }, "Invented."), "a-model", TEST_EPOCH_DAY - 3, ProgrammeStatus.RUNNING)`
  with `EASY_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")`. (The plan's last
  day is `TEST_EPOCH_DAY + 10`, so `+ 11` is the first ended day.)

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.trainer.TrainerViewModelTest" --tests "com.metaself.app.ui.screen.trainer.TrainerScreenRenderTest" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement.** `TrainerViewModel` gains `programmes: ProgrammeStore` after `store`, and:

```kotlin
    /** D95: the running plan and the record over its weeks, so a session synced mid-plan ticks at once. */
    private val plan: Flow<Pair<Programme?, List<Workout>>> = programmes.observeRunning().flatMapLatest { programme ->
        val start = programme?.startEpochDay
        if (programme == null || start == null) {
            flowOf<Pair<Programme?, List<Workout>>>(programme to emptyList())
        } else {
            record.observeWorkouts(start, ProgrammeCalendar.lastDay(start, programme.ask.weeks)).map { programme to it }
        }
    }
```

  (declared before `state`, since `state` uses it), and in `state` the inner `combine` takes it as a fifth
  flow:

```kotlin
            combine(
                record.observeWorkouts(day - (TrainerHome.WAITING_DAYS - 1), day),
                store.observeReviewedWorkouts(),
                store.observeReviews(),
                store.observeKeptPlan(),
                plan,
            ) { recent, reviewed, reviews, kept, (running, planWorkouts) ->
                State(TrainerHome.of(day, now(), recent, reviewed, reviews, kept, running, planWorkouts), day)
            }
```

  `TrainerScreen` gains three parameters after `onAboutMe` — `onEvaluate: () -> Unit = {}`,
  `onSeePlan: () -> Unit = {}`, `onAdjust: () -> Unit = {}` — and, between the waiting card and the
  **Plan my next session** button, `PlanCardView(home.plan, onEvaluate, onSeePlan, onAdjust)`. The button
  becomes:

```kotlin
                Button(onClick = onPlan, modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.trainer_plan_next))
                        home.next?.let { Text(ProgrammeWording.nextInPlan(it), style = MaterialTheme.typography.bodySmall) }
                    }
                }
```

  New, in the same file:

```kotlin
/** D95, D97: the weekly plan's card — an offer, the running plan's week, or an ended plan's count. */
@Composable
private fun PlanCardView(card: PlanCard, onEvaluate: () -> Unit, onSeePlan: () -> Unit, onAdjust: () -> Unit) {
    when (card) {
        PlanCard.None -> OutlinedButton(onClick = onEvaluate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.trainer_evaluate))
        }
        is PlanCard.Running -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    ProgrammeWording.cardHeading(card.programme.ask.weeks, card.weekIndex, card.programme.startEpochDay ?: 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(card.programme.plan.title, style = MaterialTheme.typography.titleMedium)
                if (card.weekIndex in card.progress.weeks.indices) {
                    val week = card.progress.weeks[card.weekIndex]
                    Text(ProgrammeWording.weekDates(week.monday), style = MaterialTheme.typography.bodyMedium)
                    week.ticks.forEach { tick ->
                        Column(Modifier.heightIn(min = 44.dp), verticalArrangement = Arrangement.Center) {
                            Text(ProgrammeWording.tickTitle(tick), style = MaterialTheme.typography.titleSmall)
                            Text(
                                tick.by?.let(ProgrammeWording::tickLine) ?: tick.planned.what,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    card.progress.weeks.take(card.weekIndex).forEach { past ->
                        Text(ProgrammeWording.pastWeek(past), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                    TextButton(onClick = onSeePlan) { Text(stringResource(R.string.trainer_see_plan)) }
                    TextButton(onClick = onAdjust) { Text(stringResource(R.string.trainer_adjust_plan)) }
                }
            }
        }
        is PlanCard.Ended -> Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                Text(
                    ProgrammeWording.endedHeading(card.programme.ask.weeks),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(card.programme.plan.title, style = MaterialTheme.typography.titleMedium)
                Text(ProgrammeWording.endedLine(card.progress), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onEvaluate) { Text(stringResource(R.string.trainer_evaluate_again)) }
            }
        }
    }
}
```

  Imports: `Alignment`, `Row`, `OutlinedButton`, `PlanCard`, `ProgrammeWording`. The screen's KDoc lists
  the card (D95, D97).

- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** `TrainerViewModel.kt`, `TrainerScreen.kt`, `TrainerViewModelTest.kt`,
  `TrainerScreenRenderTest.kt`: `feat: the Trainer screen shows the weekly plan (D95, D97)`.

---
### Task 14: Evaluate me and plan — the form, the answer, and the running plan

**Files:**
- Create: `ui/screen/trainer/EvaluatePlanViewModel.kt`, `ui/screen/trainer/EvaluatePlanScreen.kt`,
  `ui/screen/trainer/WeeksPlanView.kt`
- Test: `ui/screen/trainer/EvaluatePlanViewModelTest.kt`, `ui/screen/trainer/EvaluatePlanScreenRenderTest.kt`

- [ ] **Step 1: Failing view-model test** `ui/screen/trainer/EvaluatePlanViewModelTest.kt` (the pattern of
  `PlanSessionViewModelTest`: a test dispatcher, an `outliving` scope on it, `watched`):

```kotlin
package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.trainer.FakeProgrammeStore
import com.metaself.app.data.trainer.FakeTrainer
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.EvaluationAndPlan
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** D93, D94: the form, one ask, the answer, Keep and Ask again; and the running plan. Every answer is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class EvaluatePlanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val outliving = CoroutineScope(SupervisorJob() + dispatcher)
    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()
    private val programmes = FakeProgrammeStore()
    private val trainer = FakeTrainer()
    private val settings = FakeAiSettings()
    private val problems = RecordingProblemLog()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() {
        outliving.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is asked until both rows are answered, and then once`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()

        viewModel.change(EvaluatePlanViewModel.Form(weeks = 2))
        viewModel.ask()
        advanceUntilIdle()
        assertThat(trainer.asked).isEmpty()

        viewModel.change(EvaluatePlanViewModel.Form(weeks = 2, perWeek = 2, words = "Invented words."))
        viewModel.ask()
        viewModel.ask()
        advanceUntilIdle()

        assertThat(trainer.asked).hasSize(1)
        assertThat(viewModel.state.value.shown!!.evaluation).isEqualTo(ANSWER.evaluation)
        assertThat(viewModel.state.value.startIfKept).isEqualTo(TEST_EPOCH_DAY - 3)
    }

    @Test
    fun `a failure says why and keeps the form`() = runTest {
        trainer.evaluations += TrainerReply.Failed(EstimateResult.NoKey)
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(2, 2))

        viewModel.ask()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failure).isEqualTo(EstimateResult.NoKey)
        assertThat(viewModel.state.value.form).isEqualTo(EvaluatePlanViewModel.Form(2, 2))
    }

    @Test
    fun `keep makes it the running plan, and ask again returns to the form with the answers`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(2, 2))
        viewModel.ask()
        advanceUntilIdle()

        viewModel.keep()
        advanceUntilIdle()
        assertThat(viewModel.state.value.kept).isTrue()
        assertThat(programmes.running()!!.status).isEqualTo(ProgrammeStatus.RUNNING)

        viewModel.askAgain()
        assertThat(viewModel.state.value.shown).isNull()
        assertThat(viewModel.state.value.form).isEqualTo(EvaluatePlanViewModel.Form(2, 2))
    }

    @Test
    fun `opened on the running plan, it shows the plan with its chain's evaluation`() = runTest {
        val id = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), ANSWER.evaluation, ANSWER.plan, "a-model"))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)

        val viewModel = watched(SavedStateHandle(mapOf(EvaluatePlanViewModel.SHOW to EvaluatePlanViewModel.RUNNING)))
        advanceUntilIdle()

        assertThat(viewModel.state.value.running!!.programme.id).isEqualTo(id)
        assertThat(viewModel.state.value.evaluation).isEqualTo(ANSWER.evaluation)
        assertThat(viewModel.state.value.loading).isFalse()
    }

    @Test
    fun `a failed keep is said`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(2, 2))
        viewModel.ask()
        advanceUntilIdle()
        programmes.failing = true

        viewModel.keep()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.kept).isFalse()
    }

    private fun TestScope.watched(saved: SavedStateHandle = SavedStateHandle()): EvaluatePlanViewModel {
        val make = {
            EvaluatePlanViewModel(saved, TrainerScreens.ask(record, store, trainer, programmes), settings, TrainerScreens.today, problems, outliving)
        }
        val viewModel = ViewModelProvider(ViewModelStore(), TrainerScreens.factory(make))[EvaluatePlanViewModel::class.java]
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private companion object {
        val WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val ANSWER = EvaluationAndPlan(
            Evaluation("Invented headline.", "Invented.", "Invented.", ""),
            WeeksPlan("Invented plan", List(2) { PlanWeek("w", listOf(WALK, WALK)) }, "Invented reason."),
        )
    }
}
```

- [ ] **Step 2: Failing render test** `ui/screen/trainer/EvaluatePlanScreenRenderTest.kt` (JUnit 4,
  Robolectric, the pattern of `PlanSessionScreenRenderTest`):

```kotlin
package com.metaself.app.ui.screen.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4. Which words the evaluate page draws (D93, D94),
 * and that each door calls back. TEST_EPOCH_DAY is Thursday 3 September 2026; every word is invented.
 */
@RunWith(RobolectricTestRunner::class)
class EvaluatePlanScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the form has its two rows, the words, and a privacy line naming the last evaluation`() {
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY))

        assertThat(texts).containsAtLeast("HOW MANY WEEKS", "2 weeks", "4 weeks", "6 weeks", "SESSIONS A WEEK I CAN MANAGE", "2", "5").inOrder()
        assertThat(texts.any { it.contains("your last evaluation and how its plan went") }).isTrue()
        assertThat(render.isEnabled("Ask the trainer")).isFalse()
    }

    /** D4: the evaluation and the plan are labelled advice. */
    @Test
    fun `the answer shows where you stand, the plan with its dates, and Keep and Ask again`() {
        var kept = false
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, shown = SHOWN), onKeep = { kept = true })

        assertThat(texts).contains("From the AI trainer · advice, not a measurement")
        assertThat(texts).containsAtLeast("WHERE YOU STAND", "Invented headline.", "Going well.", "To work on.").inOrder()
        assertThat(texts).doesNotContain("Since last time.")
        assertThat(texts).containsAtLeast("THE PLAN · 2 WEEKS · 2 SESSIONS A WEEK", "Invented plan", "Starts Mon 31 Aug, ends Sun 13 Sep").inOrder()
        assertThat(texts).containsAtLeast("Week 1 · settle in", "Easy walk, 30 min", "Invented line").inOrder()
        assertThat(texts).contains("Keeping it replaces any plan you have now. The evaluation is kept either way.")
        render.click("Keep this plan")
        assertThat(kept).isTrue()
    }

    @Test
    fun `a kept answer says so; the running plan shows its ticks`() {
        assertThat(draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, shown = SHOWN, kept = true)))
            .contains("Kept. It is on the Trainer screen.")

        val running = PlanCard.of(SHOWN.copy(startEpochDay = TEST_EPOCH_DAY - 3, status = ProgrammeStatus.RUNNING), emptyList(), TEST_EPOCH_DAY) as PlanCard.Running
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, onRunning = true, running = running, evaluation = SHOWN.evaluation))
        assertThat(texts).contains("Your plan")
        assertThat(texts).contains("To do: Easy walk, 30 min")
        assertThat(texts).doesNotContain("Keep this plan")
    }

    private fun draw(
        state: EvaluatePlanViewModel.State,
        onKeep: () -> Unit = {},
    ): List<String> = render.texts {
        EvaluatePlanScreen(state = state, onBack = {}, onChange = {}, onAsk = {}, onKeep = onKeep, onAskAgain = {})
    }

    private companion object {
        val WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val SHOWN = Programme(
            id = 1, createdAtMillis = 0, ask = ProgrammeAsk(2, 2),
            evaluation = Evaluation("Invented headline.", "Invented going well.", "Invented to work on.", ""),
            plan = WeeksPlan("Invented plan", listOf(PlanWeek("settle in", listOf(WALK, WALK)), PlanWeek("", listOf(WALK))), "Invented reason."),
            model = "a-model",
        )
    }
}
```

- [ ] **Step 3: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.trainer.EvaluatePlan*" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 4: Implement** `ui/screen/trainer/EvaluatePlanViewModel.kt`:

```kotlin
package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.di.ApplicationScope
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import com.metaself.app.ui.outlived
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * D93, D94: the form, one ask, the evaluation with its plan, Keep and Ask again — or, opened with
 * [RUNNING], the running plan with its ticks and its evaluation (design question 3). The trainer is asked
 * only from [ask], a tap (D84); the ask and the storing of its answer run in [outliving], so leaving the
 * page mid-request does not throw away a paid answer.
 */
@HiltViewModel
class EvaluatePlanViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val ask: AskTheTrainer,
    settings: AiSettingsStore,
    today: Today,
    private val problems: ProblemLog,
    @ApplicationScope private val outliving: CoroutineScope,
) : ViewModel() {

    /** The form's two rows and its words; a row is null until answered. */
    data class Form(val weeks: Int? = null, val perWeek: Int? = null, val words: String = "") {
        fun ask(): ProgrammeAsk? = ProgrammeAsk(weeks ?: return null, perWeek ?: return null, words)
    }

    /**
     * @property shown the answer just arrived: OFFERED until kept.
     * @property onRunning opened on the running plan; [running] is it (null when none runs) and
     *   [evaluation] its chain's.
     */
    data class State(
        val loading: Boolean = false,
        val form: Form = Form(),
        val asking: Boolean = false,
        val failure: EstimateResult? = null,
        val shown: Programme? = null,
        val kept: Boolean = false,
        val onRunning: Boolean = false,
        val running: PlanCard.Running? = null,
        val evaluation: Evaluation? = null,
        val today: Long = 0,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
        val refused: ActionRefused? = null,
    ) {
        val canAsk: Boolean get() = form.ask() != null && !asking

        /** The Monday keeping the answer today would start it on (D94). */
        val startIfKept: Long get() = shown?.startEpochDay ?: ProgrammeCalendar.startFor(today)
    }

    private val openedOnRunning = savedState.get<String>(SHOW) == RUNNING

    private val local = MutableStateFlow(State(loading = openedOnRunning, onRunning = openedOnRunning, today = today().toEpochDay()))

    val state: StateFlow<State> = combine(local, settings.settings) { s, ai -> s.copy(ceiling = ai.dailyCeiling) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        if (openedOnRunning) {
            guarded(problems, onRefused = { local.update { it.copy(loading = false, refused = ActionRefused.COULD_NOT_OPEN) } }) {
                val running = ask.running()
                val evaluation = running?.let { ask.evaluationOf(it.programme) }
                local.update { it.copy(loading = false, running = running, evaluation = evaluation) }
            }
        }
    }

    fun change(form: Form) = local.update { if (it.asking) it else it.copy(form = form, failure = null) }

    fun ask() {
        val answers = local.value.form.ask() ?: return
        if (local.value.asking) return
        local.update { it.copy(asking = true, failure = null, refused = null) }
        outlived(
            outliving,
            problems,
            onRefused = { local.update { it.copy(asking = false, refused = ActionRefused.NOTHING_CHANGED) } },
            work = { ask.evaluate(answers) },
        ) { outcome ->
            when (outcome) {
                is AskTheTrainer.Evaluated.Offered -> local.update { it.copy(asking = false, shown = outcome.programme, kept = false) }
                is AskTheTrainer.Evaluated.Failed -> local.update { it.copy(asking = false, failure = outcome.failure) }
            }
        }
    }

    fun keep() {
        val shown = local.value.shown ?: return
        if (local.value.kept) return
        local.update { it.copy(refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.NOTHING_CHANGED) } }) {
            val start = ask.keepProgramme(shown.id)
            local.update { it.copy(kept = true, shown = shown.copy(startEpochDay = start, status = ProgrammeStatus.RUNNING)) }
        }
    }

    /** Back to the form, the answers kept (D94); the answer stays stored, offered. */
    fun askAgain() = local.update { it.copy(shown = null, kept = false, failure = null, refused = null) }

    companion object {
        /** The navigation argument: [RUNNING] opens on the running plan; anything else on the form. */
        const val SHOW = "show"
        const val FORM = "form"
        const val RUNNING = "running"
    }
}
```

  `ui/screen/trainer/WeeksPlanView.kt` — shared by this page and the adjust page:

```kotlin
package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.ProgrammeWording
import com.metaself.app.ui.trainer.TrainerWording

/** D94: where you stand — the headline and its parts; "since last time" only when there is one. */
@Composable
internal fun EvaluationView(evaluation: Evaluation) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Text(
            stringResource(R.string.weeks_where),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(evaluation.headline, style = MaterialTheme.typography.titleLarge)
        LabelledPart(R.string.weeks_going_well, evaluation.goingWell)
        LabelledPart(R.string.weeks_to_work_on, evaluation.toWorkOn)
        if (evaluation.sinceLast.isNotBlank()) LabelledPart(R.string.weeks_since_last, evaluation.sinceLast)
    }
}

@Composable
private fun LabelledPart(label: Int, text: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * D94, D95: a plan's heading, dates, weeks and why. With [progress], each session says whether it is done
 * (the running plan); without, it says what it is (an answer not yet kept).
 */
@Composable
internal fun WeeksPlanView(plan: WeeksPlan, ask: ProgrammeAsk, start: Long, progress: PlanProgress?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
        Text(
            TrainerWording.FROM_TRAINER,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            ProgrammeWording.planHeading(ask),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(plan.title, style = MaterialTheme.typography.titleLarge)
        Text(ProgrammeWording.span(start, ask.weeks), style = MaterialTheme.typography.bodyMedium)
        plan.weeks.forEachIndexed { index, week ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.Section), verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                    Text(ProgrammeWording.weekTitle(index + 1, week.focus), style = MaterialTheme.typography.titleSmall)
                    val ticks = progress?.weeks?.getOrNull(index)?.ticks
                    week.sessions.forEachIndexed { at, session ->
                        val tick = ticks?.getOrNull(at)
                        Text(
                            if (tick != null) ProgrammeWording.tickTitle(tick) else ProgrammeWording.plannedTitle(session),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            tick?.by?.let(ProgrammeWording::tickLine) ?: session.what,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Column {
            Text(stringResource(R.string.weeks_why), style = MaterialTheme.typography.titleSmall)
            Text(plan.why, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

  `ui/screen/trainer/EvaluatePlanScreen.kt`:

```kotlin
package com.metaself.app.ui.screen.trainer

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.metaself.app.R
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.TrainerWording

/**
 * D93, D94: the form, then the evaluation and the plan with Keep this plan and Ask again; or the running
 * plan. Branches only — no early return out of an inline composable (InlineComposableReturnGuardTest).
 */
@Composable
fun EvaluatePlanScreen(
    state: EvaluatePlanViewModel.State,
    onBack: () -> Unit,
    onChange: (EvaluatePlanViewModel.Form) -> Unit,
    onAsk: () -> Unit,
    onKeep: () -> Unit,
    onAskAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = state.shown
    val running = state.running
    val title = stringResource(
        when {
            state.onRunning -> R.string.weeks_running_title
            shown == null -> R.string.weeks_title
            else -> R.string.weeks_result_title
        },
    )
    MetaSelfScreen(title = title, modifier = modifier, onBack = onBack) {
        when {
            state.loading -> Unit
            state.onRunning && running == null -> Text(stringResource(R.string.weeks_not_running))
            state.onRunning && running != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                state.evaluation?.let { EvaluationView(it) }
                WeeksPlanView(running.programme.plan, running.programme.ask, running.programme.startEpochDay ?: 0, running.progress)
            }
            shown == null -> EvaluateForm(state, onChange, onAsk)
            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                shown.evaluation?.let { EvaluationView(it) }
                WeeksPlanView(shown.plan, shown.ask, state.startIfKept, null)
                if (state.kept) {
                    Text(stringResource(R.string.weeks_kept), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Button(onClick = onKeep, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.weeks_keep)) }
                    Text(
                        stringResource(R.string.weeks_keep_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onAskAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plan_again)) }
            }
        }
        state.refused?.let { refused -> Text(stringResource(refused.sentence), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun EvaluateForm(
    state: EvaluatePlanViewModel.State,
    onChange: (EvaluatePlanViewModel.Form) -> Unit,
    onAsk: () -> Unit,
) {
    val form = state.form
    val open = !state.asking
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Text(stringResource(R.string.weeks_intro), style = MaterialTheme.typography.bodyMedium)
        NumberRow(R.string.weeks_row_weeks, ProgrammeAsk.WEEKS, form.weeks, { "$it weeks" }, open) { onChange(form.copy(weeks = it)) }
        NumberRow(R.string.weeks_row_per_week, ProgrammeAsk.PER_WEEK.toList(), form.perWeek, { "$it" }, open) {
            onChange(form.copy(perWeek = it))
        }
        OutlinedTextField(
            value = form.words,
            onValueChange = { onChange(form.copy(words = it)) },
            enabled = open,
            label = { Text(stringResource(R.string.plan_words)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Button(onClick = onAsk, enabled = state.canAsk, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.asking) R.string.plan_asking else R.string.plan_ask))
            }
            Text(
                TrainerWording.privacyEvaluate(state.ceiling),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.failure?.let { Text(TrainerWording.failure(it), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberRow(
    @StringRes label: Int,
    choices: List<Int>,
    picked: Int?,
    word: (Int) -> String,
    enabled: Boolean,
    onPick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            choices.forEach { choice ->
                FilterChip(selected = picked == choice, onClick = { onPick(choice) }, label = { Text(word(choice)) }, enabled = enabled)
            }
        }
    }
}
```

- [ ] **Step 5: Run, expect PASS** (same command as Step 3).
- [ ] **Step 6: Commit** `EvaluatePlanViewModel.kt`, `EvaluatePlanScreen.kt`, `WeeksPlanView.kt`,
  `EvaluatePlanViewModelTest.kt`, `EvaluatePlanScreenRenderTest.kt`:
  `feat: evaluate me and plan the weeks ahead (D93, D94)`.

---

### Task 15: Adjust the plan, keep a version, or stop it

**Files:**
- Create: `ui/screen/trainer/AdjustPlanViewModel.kt`, `ui/screen/trainer/AdjustPlanScreen.kt`
- Test: `ui/screen/trainer/AdjustPlanViewModelTest.kt`, `ui/screen/trainer/AdjustPlanScreenRenderTest.kt`

- [ ] **Step 1: Failing view-model test** `ui/screen/trainer/AdjustPlanViewModelTest.kt` — the same
  harness as `EvaluatePlanViewModelTest` (dispatcher, `outliving`, fakes, `watched()` building
  `AdjustPlanViewModel(TrainerScreens.ask(record, store, trainer, programmes), settings, TrainerScreens.today, problems, outliving)`),
  with a running plan set up in `@BeforeEach` after `Dispatchers.setMain`:

```kotlin
    private var runningId = 0L

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        runBlocking {
            runningId = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), null, PLAN, "a-model"))
            programmes.keep(runningId, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        }
    }

    @Test
    fun `it opens on the running plan, and adjusting shows the new version without keeping it`() = runTest {
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        assertThat(viewModel.state.value.running!!.programme.id).isEqualTo(runningId)

        viewModel.words("Invented words.")
        viewModel.adjust()
        viewModel.adjust()
        advanceUntilIdle()

        assertThat(trainer.asked).hasSize(1)
        assertThat(viewModel.state.value.shown!!.replacesId).isEqualTo(runningId)
        assertThat(programmes.running()!!.id).isEqualTo(runningId)
    }

    @Test
    fun `keep this version runs it and closes; keep the old one closes and changes nothing`() = runTest {
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.adjust()
        advanceUntilIdle()

        viewModel.keepNew()
        advanceUntilIdle()

        assertThat(programmes.running()!!.id).isEqualTo(viewModel.state.value.shown!!.id)
        assertThat(viewModel.state.value.finished).isTrue()
    }

    @Test
    fun `stop asks first, then ends the plan today and closes`() = runTest {
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.askStop()
        assertThat(viewModel.state.value.confirmStop).isTrue()
        viewModel.cancelStop()
        assertThat(programmes.running()).isNotNull()

        viewModel.askStop()
        viewModel.confirmStop()
        advanceUntilIdle()

        assertThat(programmes.running()).isNull()
        assertThat(viewModel.state.value.finished).isTrue()
    }

    @Test
    fun `a failed adjustment says why and keeps the words`() = runTest {
        trainer.adjustments += TrainerReply.Failed(EstimateResult.NoKey)
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.words("Invented words.")

        viewModel.adjust()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failure).isEqualTo(EstimateResult.NoKey)
        assertThat(viewModel.state.value.words).isEqualTo("Invented words.")
        assertThat(viewModel.state.value.shown).isNull()
    }
```

  Companion: `WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")`,
  `PLAN = WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK, WALK)) }, "Invented.")`,
  `REST = WeeksPlan("Invented new", listOf(PlanWeek("now", listOf(WALK)), PlanWeek("next", listOf(WALK))), "Invented.")`.
  (`runBlocking` from `kotlinx.coroutines`; `tearDown` as in `EvaluatePlanViewModelTest`.)

- [ ] **Step 2: Failing render test** `ui/screen/trainer/AdjustPlanScreenRenderTest.kt` (JUnit 4,
  Robolectric):

```kotlin
    @Test
    fun `the page says what is counted, what is rewritten, and when the plan ends`() {
        val texts = draw(AdjustPlanViewModel.State(loading = false, running = RUNNING, today = TEST_EPOCH_DAY))

        assertThat(texts).containsAtLeast("SO FAR, COUNTED ON THE PHONE", "Week 1 (this week): 0 of 2 so far", "Week 2: to be rewritten").inOrder()
        assertThat(texts.any { it.startsWith("The trainer rewrites this week's remaining sessions") && it.endsWith("The end date stays Sun 13 Sep.") }).isTrue()
        assertThat(texts.any { it.startsWith("Sends to OpenAI, with your key: your plan, how it has gone, and your words;") }).isTrue()
        assertThat(texts).contains("Stop this plan")
    }

    @Test
    fun `a new version shows with Keep this version and Keep the old one`() {
        var keptNew = false
        val texts = draw(
            AdjustPlanViewModel.State(loading = false, running = RUNNING, shown = RUNNING.programme.copy(id = 2, replacesId = 1), today = TEST_EPOCH_DAY),
            onKeepNew = { keptNew = true },
        )

        assertThat(texts).contains("From the AI trainer · advice, not a measurement")
        assertThat(texts).contains("Keep the old one")
        render.click("Keep this version")
        assertThat(keptNew).isTrue()
    }

    @Test
    fun `stopping asks first`() {
        val texts = draw(AdjustPlanViewModel.State(loading = false, running = RUNNING, confirmStop = true, today = TEST_EPOCH_DAY))

        assertThat(texts).contains("Stop this plan? It is kept on record.")
        assertThat(texts).containsAtLeast("Stop it", "Keep going")
    }

    private fun draw(
        state: AdjustPlanViewModel.State,
        onKeepNew: () -> Unit = {},
    ): List<String> = render.texts {
        AdjustPlanScreen(
            state = state, onBack = {}, onWords = {}, onAdjust = {}, onKeepNew = onKeepNew, onKeepOld = {},
            onAskStop = {}, onCancelStop = {}, onConfirmStop = {}, onFinished = {},
        )
    }
```

  with `RUNNING = PlanCard.of(Programme(1, 0, ProgrammeAsk(2, 2), null, WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK, WALK)) }, "Invented."), "a-model", TEST_EPOCH_DAY - 3, ProgrammeStatus.RUNNING), emptyList(), TEST_EPOCH_DAY) as PlanCard.Running`
  and the class header, imports and `render`/`tearDown` as in `EvaluatePlanScreenRenderTest`. (If the
  dialog's texts are not in `render.texts` — Robolectric draws an `AlertDialog` in its own window — assert
  on the dialog through `render.textsAgain()` after it shows, or drop the dialog assertion and say so.)

- [ ] **Step 3: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.trainer.AdjustPlan*" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 4: Implement** `ui/screen/trainer/AdjustPlanViewModel.kt`:

```kotlin
package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.di.ApplicationScope
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import com.metaself.app.ui.outlived
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * D97: the running plan's counts, the owner's words, one ask, and the new version with Keep this version
 * and Keep the old one; or Stop this plan, asked first. The trainer is asked only from [adjust], a tap
 * (D84), in [outliving]. [State.finished] closes the page.
 */
@HiltViewModel
class AdjustPlanViewModel @Inject constructor(
    private val ask: AskTheTrainer,
    settings: AiSettingsStore,
    today: Today,
    private val problems: ProblemLog,
    @ApplicationScope private val outliving: CoroutineScope,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val running: PlanCard.Running? = null,
        val words: String = "",
        val asking: Boolean = false,
        val failure: EstimateResult? = null,
        val shown: Programme? = null,
        val confirmStop: Boolean = false,
        val finished: Boolean = false,
        val today: Long = 0,
        val ceiling: Int = AiSettings.DEFAULT_CEILING,
        val refused: ActionRefused? = null,
    ) {
        val canAsk: Boolean get() = running != null && !asking && shown == null
    }

    private val local = MutableStateFlow(State(today = today().toEpochDay()))

    val state: StateFlow<State> = combine(local, settings.settings) { s, ai -> s.copy(ceiling = ai.dailyCeiling) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        guarded(problems, onRefused = { local.update { it.copy(loading = false, refused = ActionRefused.COULD_NOT_OPEN) } }) {
            val running = ask.running()
            local.update { it.copy(loading = false, running = running) }
        }
    }

    fun words(words: String) = local.update { if (it.asking) it else it.copy(words = words, failure = null) }

    fun adjust() {
        if (!local.value.canAsk) return
        val words = local.value.words
        local.update { it.copy(asking = true, failure = null, refused = null) }
        outlived(
            outliving,
            problems,
            onRefused = { local.update { it.copy(asking = false, refused = ActionRefused.NOTHING_CHANGED) } },
            work = { ask.adjust(words) },
        ) { outcome ->
            when (outcome) {
                is AskTheTrainer.Adjusted.Offered -> local.update { it.copy(asking = false, shown = outcome.programme) }
                is AskTheTrainer.Adjusted.Failed -> local.update { it.copy(asking = false, failure = outcome.failure) }
                AskTheTrainer.Adjusted.NotRunning -> local.update { it.copy(asking = false, running = null) }
            }
        }
    }

    fun keepNew() {
        val shown = local.value.shown ?: return
        local.update { it.copy(refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.NOTHING_CHANGED) } }) {
            ask.keepAdjusted(shown)
            local.update { it.copy(finished = true) }
        }
    }

    /** Design question 6: nothing more is stored; the new version stays offered. */
    fun keepOld() = local.update { it.copy(finished = true) }

    fun askStop() = local.update { it.copy(confirmStop = true) }

    fun cancelStop() = local.update { it.copy(confirmStop = false) }

    fun confirmStop() {
        val id = local.value.running?.programme?.id ?: return
        local.update { it.copy(confirmStop = false, refused = null) }
        guarded(problems, onRefused = { local.update { it.copy(refused = ActionRefused.NOTHING_CHANGED) } }) {
            ask.stop(id)
            local.update { it.copy(finished = true) }
        }
    }
}
```

  `ui/screen/trainer/AdjustPlanScreen.kt`:

```kotlin
package com.metaself.app.ui.screen.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.metaself.app.R
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.Spacing
import com.metaself.app.ui.trainer.ProgrammeWording
import com.metaself.app.ui.trainer.TrainerWording

/**
 * D97: what is counted, what is rewritten, the words, Adjust; then the new version with Keep this version
 * and Keep the old one; Stop this plan behind a question. Branches only — no early return out of an inline
 * composable (InlineComposableReturnGuardTest).
 */
@Composable
fun AdjustPlanScreen(
    state: AdjustPlanViewModel.State,
    onBack: () -> Unit,
    onWords: (String) -> Unit,
    onAdjust: () -> Unit,
    onKeepNew: () -> Unit,
    onKeepOld: () -> Unit,
    onAskStop: () -> Unit,
    onCancelStop: () -> Unit,
    onConfirmStop: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    val running = state.running
    val shown = state.shown
    MetaSelfScreen(title = stringResource(R.string.adjust_title), modifier = modifier, onBack = onBack) {
        when {
            state.loading -> Unit
            running == null -> Text(stringResource(R.string.weeks_not_running))
            shown != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                WeeksPlanView(shown.plan, shown.ask, running.programme.startEpochDay ?: 0, null)
                Button(onClick = onKeepNew, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.adjust_keep_new)) }
                OutlinedButton(onClick = onKeepOld, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.adjust_keep_old)) }
            }
            else -> AdjustForm(state, running, onWords, onAdjust, onAskStop)
        }
        state.refused?.let { refused -> Text(stringResource(refused.sentence), color = MaterialTheme.colorScheme.error) }
    }
    if (state.confirmStop) {
        AlertDialog(
            onDismissRequest = onCancelStop,
            text = { Text(stringResource(R.string.adjust_stop_question)) },
            confirmButton = { TextButton(onClick = onConfirmStop) { Text(stringResource(R.string.adjust_stop_yes)) } },
            dismissButton = { TextButton(onClick = onCancelStop) { Text(stringResource(R.string.adjust_stop_no)) } },
        )
    }
}

@Composable
private fun AdjustForm(
    state: AdjustPlanViewModel.State,
    running: PlanCard.Running,
    onWords: (String) -> Unit,
    onAdjust: () -> Unit,
    onAskStop: () -> Unit,
) {
    val programme = running.programme
    val start = programme.startEpochDay ?: 0
    val current = running.weekIndex.coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
            Text(
                stringResource(R.string.adjust_so_far),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            running.progress.weeks.take(current).forEach { Text(ProgrammeWording.pastWeek(it)) }
            if (running.weekIndex >= 0) Text(ProgrammeWording.thisWeekSoFar(running.progress.weeks[current]))
            val firstRewritten = if (running.weekIndex >= 0) current + 2 else 1
            if (firstRewritten <= programme.ask.weeks) {
                Text(
                    ProgrammeWording.rewritten(firstRewritten, programme.ask.weeks),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = state.words,
            onValueChange = onWords,
            enabled = !state.asking,
            label = { Text(stringResource(R.string.adjust_words)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(ProgrammeWording.adjustRule(start, programme.ask.weeks), style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
            Button(onClick = onAdjust, enabled = state.canAsk, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.asking) R.string.plan_asking else R.string.adjust_ask))
            }
            Text(
                TrainerWording.privacyAdjust(state.ceiling),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.failure?.let { Text(TrainerWording.failure(it), color = MaterialTheme.colorScheme.error) }
        }
        TextButton(onClick = onAskStop) { Text(stringResource(R.string.adjust_stop)) }
    }
}
```

- [ ] **Step 5: Run, expect PASS** (same command as Step 3).
- [ ] **Step 6: Commit** `AdjustPlanViewModel.kt`, `AdjustPlanScreen.kt`, `AdjustPlanViewModelTest.kt`,
  `AdjustPlanScreenRenderTest.kt`: `feat: adjust the plan, keep a version, or stop it (D97)`.

---

### Task 16: Plan my next session starts from the plan

**Files:**
- Modify: `ui/screen/trainer/PlanSessionViewModel.kt`, `ui/screen/trainer/PlanSessionScreen.kt`
- Test: `ui/screen/trainer/PlanSessionViewModelTest.kt`, `ui/screen/trainer/PlanSessionScreenRenderTest.kt`

- [ ] **Step 1: Failing tests.** `PlanSessionViewModelTest` — add a `programmes = FakeProgrammeStore()`
  field, pass it to `TrainerScreens.ask(record, store, trainer, programmes)` in `watched`, and:

```kotlin
    /** D96: the form opens on the next planned session's time and effort; every row stays changeable. */
    @Test
    fun `the empty form is pre-filled from the next planned session`() = runTest {
        val walk40 = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Invented line")
        val id = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), null, WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(walk40, walk40)) }, "Invented."), "a-model"))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)

        val viewModel = watched()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.next).isEqualTo(PlannedTick(1, walk40))
        assertThat(state.form.time).isEqualTo(TimeAvailable.MIN_45)
        assertThat(state.form.wish).isEqualTo(Wish.NOT_SURE)
        assertThat(state.form.activity).isNull()
    }

    @Test
    fun `with no plan running the form opens empty`() = runTest {
        val viewModel = watched()
        advanceUntilIdle()

        assertThat(viewModel.state.value.next).isNull()
        assertThat(viewModel.state.value.form).isEqualTo(PlanSessionViewModel.Form())
    }
```

  `PlanSessionScreenRenderTest`:

```kotlin
    @Test
    fun `the next planned session is named above the rows, and in the privacy line`() {
        val next = PlannedTick(1, PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Invented line"))
        val texts = draw(PlanSessionViewModel.State(next = next))

        assertThat(texts).containsAtLeast("Next in your plan: steady walk, 40 min", "WHAT").inOrder()
        assertThat(texts.any { it.contains("these answers and the next session in your weekly plan") }).isTrue()
    }
```

- [ ] **Step 2: Run, expect compile failure:**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.trainer.PlanSession*" > /tmp/ms-weeks.log 2>&1; echo "exit $?"`

- [ ] **Step 3: Implement.** `PlanSessionViewModel.State` gains
  `/** D96: the running plan's next session; the form was pre-filled from it. */ val next: PlannedTick? = null,`.
  In `init`, after the `openedOnKept` branch:

```kotlin
        if (!openedOnKept) {
            // D96: a convenience — a failed read leaves the form empty (and is logged), nothing more.
            guarded(problems, onRefused = {}) {
                val next = ask.nextPlanned() ?: return@guarded
                local.update { now ->
                    if (now.form != Form() || now.asking) {
                        now.copy(next = next)
                    } else {
                        now.copy(
                            next = next,
                            form = Form(time = NextInPlan.time(next.session.minutes), wish = NextInPlan.wish(next.session.effort)),
                        )
                    }
                }
            }
        }
```

  (`return@guarded` is out of a suspending lambda, not an inline composable — allowed.)
  `PlanSessionScreen.PlanForm`: first child of its `Column`:

```kotlin
        state.next?.let { Text(ProgrammeWording.nextInPlan(it), style = MaterialTheme.typography.titleSmall) }
```

  and the privacy line becomes `TrainerWording.privacyPlan(state.ceiling, withPlan = state.next != null)`.

- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit** `PlanSessionViewModel.kt`, `PlanSessionScreen.kt`, `PlanSessionViewModelTest.kt`,
  `PlanSessionScreenRenderTest.kt`: `feat: plan my next session starts from the weekly plan (D96)`.

---

### Task 17: The two destinations

**Files:** Modify `ui/nav/MetaSelfNavHost.kt`

- [ ] **Step 1:** Destinations, beside `PlanSession`:

```kotlin
    /** D93, D94: evaluate me and plan; or the running plan. */
    data object EvaluatePlan : Destination("trainer/weeks?show={show}") {
        fun form(): String = "trainer/weeks?show=" + EvaluatePlanViewModel.FORM
        fun running(): String = "trainer/weeks?show=" + EvaluatePlanViewModel.RUNNING
    }

    /** D97. */
    data object AdjustPlan : Destination("trainer/weeks/adjust")
```

- [ ] **Step 2:** `TrainerScreen(...)` in the Trainer composable gains:

```kotlin
                onEvaluate = { navController.navigate(Destination.EvaluatePlan.form()) },
                onSeePlan = { navController.navigate(Destination.EvaluatePlan.running()) },
                onAdjust = { navController.navigate(Destination.AdjustPlan.route) },
```

- [ ] **Step 3:** After the `PlanSession` composable:

```kotlin
        composable(
            route = Destination.EvaluatePlan.route,
            arguments = listOf(
                navArgument(EvaluatePlanViewModel.SHOW) {
                    type = NavType.StringType
                    defaultValue = EvaluatePlanViewModel.FORM
                },
            ),
        ) {
            val evaluateViewModel: EvaluatePlanViewModel = hiltViewModel()
            val evaluateState by evaluateViewModel.state.collectAsStateWithLifecycle()
            EvaluatePlanScreen(
                state = evaluateState,
                onBack = { navController.popBackStack() },
                onChange = evaluateViewModel::change,
                onAsk = evaluateViewModel::ask,
                onKeep = evaluateViewModel::keep,
                onAskAgain = evaluateViewModel::askAgain,
            )
        }

        composable(Destination.AdjustPlan.route) {
            val adjustViewModel: AdjustPlanViewModel = hiltViewModel()
            val adjustState by adjustViewModel.state.collectAsStateWithLifecycle()
            AdjustPlanScreen(
                state = adjustState,
                onBack = { navController.popBackStack() },
                onWords = adjustViewModel::words,
                onAdjust = adjustViewModel::adjust,
                onKeepNew = adjustViewModel::keepNew,
                onKeepOld = adjustViewModel::keepOld,
                onAskStop = adjustViewModel::askStop,
                onCancelStop = adjustViewModel::cancelStop,
                onConfirmStop = adjustViewModel::confirmStop,
                onFinished = { navController.popBackStack() },
            )
        }
```

  Imports for the four new classes.
- [ ] **Step 4: Compile:** `free -m`; `~/bin/gradlew-safe :app:compileDebugKotlin > /tmp/ms-weeks.log 2>&1; echo "exit $?"` → 0.
- [ ] **Step 5: Commit** `MetaSelfNavHost.kt`: `feat: the evaluate and adjust pages are reachable from the Trainer screen (D97)`.

---

### Task 18: The privacy page, version 0.68.0, the suite and the build

**Files:** Modify `privacy.html`, `app/build.gradle.kts`

- [ ] **Step 1: Privacy page.** In the "Your training record, to OpenAI" item of `privacy.html`, change
  "When you press the button to plan a session or to get feedback on one" to "When you press the button
  to plan a session, to get feedback on one, to be evaluated and given a plan for the weeks ahead, or to
  adjust that plan", and before "and the trainer's last three feedbacks" add "when you ask for an
  evaluation, your last evaluation and how many of its plan's sessions you did each week; when you adjust
  a plan, the plan and how many of its sessions you have done;". Read the whole paragraph after editing:
  it must still say "Nothing is sent unless you press the button" in substance and name nothing new that
  the code does not send.
- [ ] **Step 2: Version.** `app/build.gradle.kts`: `versionCode = 124`, `versionName = "0.68.0"`.
- [ ] **Step 3: Full suite.** `free -m`; `~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-weeks.log 2>&1; echo "exit $?"`
  → exit 0. From `app/build/test-results/testDebugUnitTest/*.xml` count tests, failures and skips; the
  skipped classes must be exactly the ten `CLAUDE.md` names (no new Room test class was added — the new
  database tests live in `MigrationTest`, `HealthRecordStoreTest` and `BackupRoundTripTest`).
- [ ] **Step 4: Lint.** `free -m`; `~/bin/gradlew-safe :app:lintDebug > /tmp/ms-weeks-lint.log 2>&1; echo "exit $?"` → 0.
- [ ] **Step 5: Debug build.** `free -m`; `~/bin/gradlew-safe :app:assembleDebug > /tmp/ms-weeks-build.log 2>&1; echo "exit $?"` → 0.
- [ ] **Step 6: Anonymisation read.** Read every new comment, KDoc, test name and fixture in the diff
  (`git diff main --stat`, then each file) for a real figure, a real food, a frequency or the owner's
  voice. Fix and re-run the touched tests.
- [ ] **Step 7: Commit** `privacy.html`, `app/build.gradle.kts`: `0.68.0: the trainer evaluates and plans the weeks ahead (D93–D98)`.
  **The release APK is built by the controller with `~/bin/ms-release`** (≥ 6000 MB available), not here.

---

## What this plan does not build

- A timed fitness test or any computed score (the owner chose the record-reading evaluation).
- Following a defined target (the trainer's fourth use).
- Moving a planned session to a day, reminders, notifications, charts of a plan's progress.
- Editing a plan by hand; a chat with the trainer about it.
- A list of past plans or evaluations to browse (they are stored and backed up; nothing shows them yet
  beyond the running one and the last evaluation sent).
