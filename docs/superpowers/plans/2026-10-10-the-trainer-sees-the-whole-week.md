# The trainer sees the whole week — Implementation Plan (D110–D112)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D110–D112. For every Monday-to-Sunday week the phone counts a **week ledger**: the weekly plan's
practice (each planned session done, done short or not done — only when a kept plan covers the week), and
everything beyond the plan (every other session grouped by kind, with minutes, distance, brisk minutes and
attempt marks; steps and distance a day), with food (days logged, calories and protein a day, the calorie
target) and recovery (sleep, resting heart rate and HRV, each beside its 28-day usual) as context. Every
trainer request carries six ledgers in place of D84's six weekly totals; the weekly letter carries its
week's. The instructions on every path and in the letter gain D112's five rules. The line under each ask
button names the additions. Version 0.74.0.

**Decision:** the owner's, 2026-10-10 — `docs/superpowers/specs/2026-10-10-the-trainer-sees-the-whole-week-design.md`
(D110, D111 amends D84 and D101, D112). Branch `the-trainer-sees-the-whole-week`, already checked out —
**do not switch branches**. No Room change, no backup change: the ledger is counted, never stored.

**Architecture:**

```
domain/trainer/WeekLedger.kt     NEW, pure: CountedPlan (+ covering), PracticeLedger, KindLedger,
                                 BeyondPlan, FoodContext, Recovery, WeekLedger, WeekLedgers.of     JUnit 5   Task 1
domain/trainer/TrainerRequest.kt WeekFacts removed; weeks: List<WeekLedger>; of() takes food, the
                                 target and the kept plans; firstLedgerDay                         JUnit 5   Task 2
data/ai/TrainerPrompt.kt         ledgerJson (shared with the letter); "weeks" sent as ledgers      JUnit 5   Task 2
                                 LEDGER + WEEK_RULES in every path's instructions (D112)           JUnit 5   Task 4
data/trainer/AskTheTrainer.kt    reads each day's food totals, today's target, every kept plan,
                                 and the 28 days before the six weeks                              JUnit 5   Task 3
domain/letter/WeeklyLetter.kt    LetterRequest.ledger                                              JUnit 5   Task 5
data/ai/LetterPrompt.kt          "ledger" sent; LEDGER + WEEK_RULES in the instructions            JUnit 5   Task 5
data/letter/WriteWeeklyLetter.kt builds the week's ledger; its "plan of the week" uses
                                 CountedPlan.covering                                              JUnit 5   Task 5
ui/trainer/TrainerWording.kt, ui/letter/LetterWording.kt   the privacy lines                      JUnit 5 + Robolectric  Task 6
privacy.html, app/build.gradle.kts                          the policy page; 0.74.0 / 130                             Task 7
```

**Tech Stack:** Kotlin 1.9.22, kotlinx.serialization JSON builders, JUnit 5 + Truth for everything here
except the two existing Compose render tests touched in Task 6 (JUnit 4, Robolectric — leave their
imports as they are).

---

## Design choices the spec leaves open — settled here

1. **One new pure file, `domain/trainer/WeekLedger.kt`.** `WeekLedgers.of(monday, today, workouts, days,
   food, targetKcal, plans)` counts one week afresh, like `PlanProgress` (D95): nothing is stored. The
   practice ledger is `PlanProgress.of(...).weeks[index].outcome().sessions` — the exact outcomes the
   trainer is already told (D105) — so "exactly as `PlanProgress` counts it" holds by construction.
2. **"Ticked" is `Tick.by`.** A session is in the practice ledger only if it is some tick's `by`. Every
   other visible, counted session of the week (D74, D81; combined per D92 — the record hands them over
   combined) is beyond the plan. That includes sessions before the plan's chain was kept (D105's moment),
   sessions after a plan stopped, `UNRECOGNISED` ones (which `PlanProgress` never counts), and every
   session of a week no plan covers. So "exactly one ledger" is a partition, and the tests check it.
3. **Attempt marks** are D105's attempts **and open candidates** — exactly what `WeekProgress.outcome()`
   already sends as attempts. A candidate's planned session is NOT_DONE in practice; the candidate itself
   is beyond the plan with its mark. Marks are kept per kind, as `AttemptFacts(kind, minutes,
   plannedMinutes)` (reused), in start order.
4. **Kinds**: `UNRECOGNISED` is grouped with `OTHER` (the prompt already names both "other"). Groups are in
   `WorkoutKind` order (run, walk, cycle, swim, strength, other); a kind with no session has no group.
5. **Brisk minutes** = zone 2 to zone 5 seconds (`zoneSeconds[1..4]`, D70's ≥ 60 %), summed over the kind's
   sessions that have zones, rounded once to the nearest minute (`(s + 30) / 60`, the request's own
   rounding). A session whose zones are all zero has zones: it adds 0. No session with zones → null.
6. **Which plan covers a week** moves out of `WriteWeeklyLetter` into `CountedPlan.covering`: the version
   that started by the week's Sunday and was counted on or after its Monday, latest-started — **and, of two
   with the same start, the one made later.** An adjustment keeps its plan's start (D97), so the old and
   the new version both cover the adjustment week; the letter today breaks that tie by list order, and
   `ProgrammeDao.allProgrammes()` reads `ORDER BY id`, so it picks the **older** version. The later one is
   the version the Trainer card counts the week under. **This changes the letter's plan row in an
   adjustment week** (to agree with the card). Reported in the final reply.
7. **"This week so far"**: a ledger is counted to `min(today, Sunday)`; `current` is true for the
   calendar's week. The trainer sends it as `so_far`. The letter's ledger is counted with `today = Sunday`
   and sent **without** `so_far` (the letter's week is the whole week).
8a. **A week's recovery average needs at least 2 recorded days** (`WEEK_LEAST_DAYS = 2`), else null:
   an average of one night is that night, which D111 never sends.
8. **The recovery usual window** is the 28 days `monday − 28 .. monday − 1`; at least 7 days recording the
   figure, else null. Each figure is separate: a week can have a sleep usual and no HRV usual.
9. **Averages** are rounded to whole numbers (HRV to whole ms). Sleep is sent in minutes a night.
10. **The calorie target** is the one the letter already uses: `CurrentTarget.of(profile, revision, year,
    burnAdjustment).kcal`, null with no profile. It is today's target, the same in all six ledgers.
11. **Food** comes from `FoodTotals.byDay` — the letter's own reader, already bound in Hilt. It reads SQL
    sums per day; no meal is read. `AskTheTrainer` gains it as its **last** constructor parameter with a
    default of no food (as `WriteWeeklyLetter`'s `notifier` does), so the five test call sites still
    compile; Hilt always passes the real one.
12. **This week's rhythm (D87) is unchanged**: `sessionsSoFar` was `weeks.first().sessions`; it is now
    counted directly with the same filter (visible, counted, Monday..today).
13. **JSON names**: the request's key stays `"weeks"` (so D84's top-level shape is unchanged); each element
    is a ledger: `from`, `so_far`, `practice` (`week`, `of_weeks`, `sessions[]` in `how_it_went`'s shape),
    `beyond_the_plan` (`sessions_by_kind[]` with `kind`, `sessions`, `minutes`, `distance_m`,
    `brisk_minutes`, `attempts[]` of `minutes` and `planned_minutes`; `everyday` with `steps_a_day` and
    `distance_m_a_day`), `food` (`days_logged`, `kcal_a_day`, `protein_g_a_day`, `target_kcal`), `recovery`
    (`sleep_minutes_a_night`, `usual_sleep_minutes_a_night`, `resting_heart_rate`,
    `usual_resting_heart_rate`, `hrv_ms`, `usual_hrv_ms`). The letter sends the same object under `"ledger"`.
14. **Instructions**: two shared constants in `TrainerPrompt` — `LEDGER` (what a ledger holds) and
    `WEEK_RULES` (D112's five rules) — appended to every path's instructions and to the letter's. D112 rule
    5 is worded "every figure you state **about their record**", so it does not contradict the existing
    VOICE line that lets the trainer propose minutes for the next session.
15. **The "never sent" tests are updated deliberately, for D111 and no further.** `TrainerPromptTest`'s
    forbidden words lose `"sleep"` and `"resting"` (the rules and keys now say them) and its forbidden
    values lose the single-night figures, which the fixtures re-pick so that each day's figure differs
    from its week's average: the day's figures stay forbidden, the average may go. Sleep stages, oxygen,
    breathing rate, a day's calories and protein are added to the forbidden list. `TrainerRequestTest`'s
    shape test swaps `WeekFacts` for the ledger types; `LetterPromptTest`'s path list gains `ledger.*`.
16. **Privacy lines**: the four ask buttons' shared line gains "weekly averages of your sleep, resting heart
    rate, heart-rate variability and food, with your calorie target". The letter's line (under *Write it
    now*, the letter's ask button) gains "your week's averages of sleep, resting heart rate and heart-rate
    variability with their usual levels" — otherwise it would list what goes and omit something that now
    goes. `privacy.html` is updated for both, and its "Never sent" lists say *a single night's sleep*
    instead of *sleep*.

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` holds the one lock. **Never run bare
`./gradlew`.** **Before every Gradle command run `free -m`;** under about 4000 MB available, wait and check
again. One Gradle command at a time, never in parallel, never `gradle --stop`. Never pipe a build; redirect
and read the exit code:

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-ledger.log 2>&1; echo "exit $?"
```

Read the log (`tail -60 /tmp/ms-ledger.log`). Stage by explicit path; **never `git add -A`**. Commit
trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and **no** session line.

Fixture rule (CLAUDE.md): every figure here is invented and round; the standard day is `TEST_EPOCH_DAY`
(Thursday 3 September 2026), whose Monday is `TEST_EPOCH_DAY - 3`; the standard profile is `aProfile()`.

---

### Task 1: The week ledger (pure)

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/trainer/WeekLedger.kt`
- Test: `app/src/test/java/com/metaself/app/domain/trainer/WeekLedgersTest.kt`

- [ ] **Step 1: Write the failing test.** Create `WeekLedgersTest.kt`:

```kotlin
package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/**
 * D110: one week's ledger. The week is Monday 31 August 2026 to today, Thursday 3 September
 * ([TEST_EPOCH_DAY]); every session, day and figure is invented, and round.
 */
class WeekLedgersTest {

    private val monday = TEST_EPOCH_DAY - 3
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
    private val run20 = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Short run")

    @Test
    fun `every counted session lands in exactly one ledger`() {
        val sessions = listOf(
            session(1, monday), session(2, monday + 1), session(3, monday + 2),
            session(4, monday + 2, WorkoutKind.SWIM, minutes = 30, hour = 9), session(5, monday + 3, minutes = 10),
            session(6, monday, minutes = 50).copy(hidden = true), session(7, monday + 1, minutes = 50).copy(counted = false),
        )

        val ledger = ledger(sessions, listOf(kept(plan(walk30, walk30))))

        assertThat(ledger.practice).isEqualTo(
            PracticeLedger(1, 2, listOf(SessionOutcome(walk30, PlannedOutcome.DONE, 40), SessionOutcome(walk30, PlannedOutcome.DONE, 40))),
        )
        // Nothing of their kind left to tick: the third walk and the ten-minute one; and the swim.
        assertThat(ledger.beyond.kinds).containsExactly(
            KindLedger(WorkoutKind.WALK, 2, 50, null, null, emptyList()),
            KindLedger(WorkoutKind.SWIM, 1, 30, null, null, emptyList()),
        ).inOrder()
        // Five visible, counted sessions: two ticked, three beyond — none twice, none dropped.
        val placed = ledger.practice!!.sessions.count { it.outcome != PlannedOutcome.NOT_DONE } +
            ledger.beyond.kinds.sumOf { it.sessions }
        assertThat(placed).isEqualTo(5)
    }

    @Test
    fun `a planned session not done stays not done, however much else the week holds`() {
        val walks = (1L..6L).map { session(it, monday + (it - 1) / 2, hour = 7 + it.toInt()) }

        val ledger = ledger(walks, listOf(kept(plan(run20, walk30))))

        assertThat(ledger.practice!!.sessions).containsExactly(
            SessionOutcome(run20, PlannedOutcome.NOT_DONE, null), SessionOutcome(walk30, PlannedOutcome.DONE, 40),
        ).inOrder()
        assertThat(ledger.beyond.kinds).containsExactly(KindLedger(WorkoutKind.WALK, 5, 200, null, null, emptyList()))
    }

    /**
     * D105: ten minutes against thirty is under half — an attempt; twenty, unanswered, is a candidate. Both
     * are beyond the plan, each marked with the planned minutes it attempted; the place stays not done.
     */
    @Test
    fun `an attempt keeps its mark, beside its kind`() {
        val sessions = listOf(session(1, monday, minutes = 10), session(2, monday + 1), session(3, monday + 2, minutes = 20))

        val ledger = ledger(sessions, listOf(kept(plan(walk30, walk30))))

        assertThat(ledger.practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.NOT_DONE).inOrder()
        assertThat(ledger.beyond.kinds).containsExactly(
            KindLedger(
                WorkoutKind.WALK, 2, 30, null, null,
                listOf(AttemptFacts(WorkoutKind.WALK, 10, 30), AttemptFacts(WorkoutKind.WALK, 20, 30)),
            ),
        )
    }

    @Test
    fun `a week no plan covers has no practice ledger, and every session is beyond it`() {
        val sessions = listOf(session(1, monday), session(2, monday + 1, minutes = 20))

        // No plan at all; and a plan kept on a Friday, which starts next Monday.
        listOf(emptyList<CountedPlan>(), listOf(kept(plan(walk30), start = monday + 7))).forEach { plans ->
            val ledger = ledger(sessions, plans)

            assertThat(ledger.practice).isNull()
            assertThat(ledger.beyond.kinds).containsExactly(KindLedger(WorkoutKind.WALK, 2, 60, null, null, emptyList()))
        }
    }

    @Test
    fun `a session before the plan was kept is beyond it`() {
        val sessions = listOf(session(1, monday), session(2, monday + 1))
        val keptTuesday = PlanCounting(chainId = 1, fromMillis = (monday + 1) * DAY, answers = emptyMap())

        val ledger = ledger(sessions, listOf(kept(plan(walk30, walk30), counting = keptTuesday)))

        assertThat(ledger.practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.NOT_DONE).inOrder()
        assertThat(ledger.beyond.kinds).containsExactly(KindLedger(WorkoutKind.WALK, 1, 40, null, null, emptyList()))
    }

    @Test
    fun `after a plan stops, the rest of its week is beyond it`() {
        val sessions = listOf(session(1, monday), session(2, monday + 2))
        val stopped = kept(plan(walk30, walk30), status = ProgrammeStatus.STOPPED, stopped = monday + 1)

        val ledger = ledger(sessions, listOf(stopped))

        assertThat(ledger.practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.NOT_DONE).inOrder()
        assertThat(ledger.beyond.kinds).containsExactly(KindLedger(WorkoutKind.WALK, 1, 40, null, null, emptyList()))
    }

    /**
     * D97: an adjusted version keeps its plan's start. Under the old one (a 60-minute walk) the forty-minute
     * walk would be an unanswered candidate; under the adjusted one (30 minutes) it ticks. The week counts
     * under the version made later, whatever order the versions are read in.
     */
    @Test
    fun `of two versions with one start, the one made later counts the week`() {
        val longWalk = PlannedSession(WorkoutKind.WALK, 60, PlannedEffort.STEADY, "Long walk")
        val old = kept(plan(longWalk), id = 1, createdAtMillis = 0, status = ProgrammeStatus.ADJUSTED, stopped = monday + 2)
        val adjusted = kept(plan(walk30), id = 2, createdAtMillis = (monday + 2) * DAY)

        listOf(listOf(old, adjusted), listOf(adjusted, old)).forEach { plans ->
            val ledger = ledger(listOf(session(1, monday)), plans)

            assertThat(ledger.practice!!.sessions).containsExactly(SessionOutcome(walk30, PlannedOutcome.DONE, 40))
        }
    }

    /**
     * D70: zones 2 to 5 are brisk. The first walk has 1,200 + 600 + 600 + 0 seconds there: 40 minutes. The
     * second has zones, all in zone 1: it adds nothing. The third has none. A cycle whose zones are all zero
     * was measured: 0. A run with no zones at all has no brisk figure.
     */
    @Test
    fun `brisk minutes are zones 2 to 5 over the sessions with zones, and absent, not zero, for a kind with none`() {
        val sessions = listOf(
            session(1, monday).copy(zoneSeconds = listOf(600, 1_200, 600, 600, 0)),
            session(2, monday + 1).copy(zoneSeconds = listOf(300, 0, 0, 0, 0)),
            session(3, monday + 2),
            session(4, monday + 2, WorkoutKind.RUN, minutes = 20, hour = 9),
            session(5, monday + 3, WorkoutKind.CYCLE, minutes = 30).copy(zoneSeconds = listOf(0, 0, 0, 0, 0)),
        )

        val kinds = ledger(sessions).beyond.kinds.associateBy { it.kind }

        assertThat(kinds.getValue(WorkoutKind.WALK).briskMinutes).isEqualTo(40)
        assertThat(kinds.getValue(WorkoutKind.RUN).briskMinutes).isNull()
        assertThat(kinds.getValue(WorkoutKind.CYCLE).briskMinutes).isEqualTo(0)
    }

    @Test
    fun `a kind's distance sums the sessions that recorded one`() {
        val sessions = listOf(session(1, monday).copy(distanceM = 3_000), session(2, monday + 1).copy(distanceM = 4_000), session(3, monday + 2))

        assertThat(ledger(sessions).beyond.kinds.single().distanceM).isEqualTo(7_000)
        assertThat(ledger(listOf(session(1, monday))).beyond.kinds.single().distanceM).isNull()
    }

    @Test
    fun `an unrecognised session is beyond the plan, as other`() {
        val sessions = listOf(session(1, monday, WorkoutKind.UNRECOGNISED), session(2, monday + 1, WorkoutKind.OTHER))

        val ledger = ledger(sessions, listOf(kept(plan(walk30))))

        assertThat(ledger.beyond.kinds).containsExactly(KindLedger(WorkoutKind.OTHER, 2, 80, null, null, emptyList()))
    }

    /** Never split into "in sessions" and "outside them" (D110): the day's figures, averaged, and nothing else. */
    @Test
    fun `everyday movement is steps and distance a day, each over the days that record it`() {
        val days = listOf(
            HealthDay(monday, steps = 6_000, distanceM = 5_000),
            HealthDay(monday + 1, steps = 8_000),
            HealthDay(monday + 2, distanceM = 3_000),
            HealthDay(monday - 1, steps = 20_000, distanceM = 15_000),
        )

        val beyond = ledger(days = days).beyond

        assertThat(beyond.stepsADay).isEqualTo(7_000)
        assertThat(beyond.distanceMADay).isEqualTo(4_000)
        assertThat(ledger().beyond.stepsADay).isNull()
        assertThat(ledger().beyond.distanceMADay).isNull()
    }

    @Test
    fun `food is the days logged, the averages over them, and the target`() {
        val food = mapOf(
            monday to DayTotals(2_000, 100, 200, 70),
            monday + 2 to DayTotals(2_400, 140, 260, 80),
            monday - 1 to DayTotals(3_000, 50, 300, 90),
        )

        assertThat(ledger(food = food).food).isEqualTo(FoodContext(2, 2_200, 120, 2_100))
        assertThat(ledger().food).isEqualTo(FoodContext(0, null, null, 2_100))
    }

    /** Each figure over the days that record it; each usual over the 28 days before Monday. */
    @Test
    fun `recovery is each average over the days that record it, beside its usual`() {
        val week = listOf(
            HealthDay(monday, sleepMinutes = 420, restingHeartRate = 60, hrvMs = 40.0),
            HealthDay(monday + 1, sleepMinutes = 480),
            HealthDay(monday + 2, restingHeartRate = 62, hrvMs = 50.0),
        )
        // Seven days in the 28 before Monday, the earliest on its first day; one more just outside, which does not count.
        val before = (1L..7L).map { HealthDay(monday - it * 4, sleepMinutes = 400, restingHeartRate = 58, hrvMs = 30.0) } +
            HealthDay(monday - 29, sleepMinutes = 600, restingHeartRate = 90, hrvMs = 90.0)

        assertThat(ledger(days = week + before).recovery).isEqualTo(Recovery(450, 400, 61, 58, 45, 30))
    }

    /** One recorded night is never sent as the week's average: that average IS the night (D110). */
    @Test
    fun `a recovery figure recorded on one day of the week has no weekly average`() {
        val one = listOf(HealthDay(monday, sleepMinutes = 420, restingHeartRate = 60, hrvMs = 40.0))

        assertThat(ledger(days = one).recovery).isEqualTo(Recovery(null, null, null, null, null, null))
    }

    /** A usual of six nights is not a usual (D110). */
    @Test
    fun `there is no usual under seven recorded days`() {
        val six = (1L..6L).map { HealthDay(monday - it, sleepMinutes = 400, restingHeartRate = 58, hrvMs = 30.0) }
        val seven = six + HealthDay(monday - 7, sleepMinutes = 400, restingHeartRate = 58, hrvMs = 30.0)

        assertThat(ledger(days = six).recovery).isEqualTo(Recovery(null, null, null, null, null, null))
        assertThat(ledger(days = seven).recovery).isEqualTo(Recovery(null, 400, null, 58, null, 30))
    }

    @Test
    fun `this week is counted to today and is current; an earlier week is all seven days`() {
        val sessions = listOf(session(1, monday - 1), session(2, monday - 7), session(3, monday), session(4, monday + 4))

        val earlier = ledger(sessions, week = monday - 7)
        val thisWeek = ledger(sessions)

        assertThat(earlier.current).isFalse()
        assertThat(earlier.beyond.kinds.single().sessions).isEqualTo(2)
        assertThat(thisWeek.current).isTrue()
        assertThat(thisWeek.beyond.kinds.single().sessions).isEqualTo(1)
    }

    /** Two weeks, each of [sessions]. */
    private fun plan(vararg sessions: PlannedSession) =
        WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", sessions.toList()) }, "Invented reason.")

    private fun kept(
        plan: WeeksPlan,
        start: Long = monday,
        id: Long = 1,
        createdAtMillis: Long = 0,
        status: ProgrammeStatus = ProgrammeStatus.RUNNING,
        stopped: Long? = null,
        counting: PlanCounting = ALL,
    ) = CountedPlan(Programme(id, createdAtMillis, ProgrammeAsk(2, 2), null, plan, "a-model", start, status, stopped), counting)

    private fun ledger(
        workouts: List<Workout> = emptyList(),
        plans: List<CountedPlan> = emptyList(),
        days: List<HealthDay> = emptyList(),
        food: Map<Long, DayTotals> = emptyMap(),
        week: Long = monday,
    ) = WeekLedgers.of(week, TEST_EPOCH_DAY, workouts, days, food, targetKcal = 2_100, plans = plans)

    private fun session(id: Long, day: Long, kind: WorkoutKind = WorkoutKind.WALK, minutes: Int = 40, hour: Int = 7) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + hour * HOUR, durationMinutes = minutes,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        val ALL = PlanCounting(chainId = 1, fromMillis = 0, answers = emptyMap())
    }
}
```

- [ ] **Step 2: Run it to see it fail.**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.trainer.WeekLedgersTest" > /tmp/ms-ledger-1.log 2>&1; echo "exit $?"
```

  Expected: non-zero exit; compile errors — `WeekLedgers`, `CountedPlan`, `PracticeLedger`, `KindLedger`,
  `FoodContext`, `Recovery` unresolved.

- [ ] **Step 3: Write the ledger.** Create `WeekLedger.kt`:

```kotlin
package com.metaself.app.domain.trainer

import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import kotlin.math.roundToInt

/** D110: a kept version of a weekly plan (D94), with how its sessions are counted (D105). */
data class CountedPlan(val programme: Programme, val counting: PlanCounting) {
    init {
        require(programme.startEpochDay != null) { "only a kept plan is counted" }
    }

    val start: Long get() = programme.startEpochDay!!

    companion object {
        /**
         * The version counted during [monday]'s week: of those that started by its Sunday and were still
         * counted on or after its Monday, the latest-started; of two with the same start — an adjustment
         * keeps its plan's start (D97) — the one made later, which the week counts under. Null when none.
         */
        fun covering(plans: List<CountedPlan>, monday: Long): CountedPlan? = plans
            .filter { it.start <= monday + 6 && Programmes.countedUntil(it.programme, monday + 6) >= monday }
            .maxWithOrNull(compareBy<CountedPlan>({ it.start }, { it.programme.createdAtMillis }))
    }
}

/** D110: a week of a weekly plan, [week] counted from 1 of [ofWeeks], each planned session's outcome (D105). */
data class PracticeLedger(val week: Int, val ofWeeks: Int, val sessions: List<SessionOutcome>)

/**
 * D110: one kind's sessions beyond the plan. [distanceM] sums the sessions that recorded one, null when none
 * did. [briskMinutes] is the minutes in heart-rate zones 2 to 5 (D70) over the sessions that have zones, null
 * when none has — nothing measured is not zero. [attempts] are those of the sessions that attempted a planned
 * session (D105), each with the planned minutes it attempted, in start order.
 */
data class KindLedger(
    val kind: WorkoutKind,
    val sessions: Int,
    val minutes: Int,
    val distanceM: Int?,
    val briskMinutes: Int?,
    val attempts: List<AttemptFacts>,
)

/**
 * D110: every session that ticked no planned session, by kind; and steps and distance a day, each averaged
 * over the days that record it. Distance is never split into "in sessions" and "outside them".
 */
data class BeyondPlan(val kinds: List<KindLedger>, val stepsADay: Int?, val distanceMADay: Int?)

/** D110: the letter's food figures (D100) — averages over the [daysLogged] days — with today's calorie target. */
data class FoodContext(val daysLogged: Int, val kcalADay: Int?, val proteinGADay: Int?, val targetKcal: Int?)

/**
 * D110: sleep a night (minutes), resting heart rate and HRV (ms), each averaged over the days that record it;
 * each usual is the same average over the 28 days before the week's Monday, null under 7 recorded days.
 */
data class Recovery(
    val sleepMinutes: Int?,
    val usualSleepMinutes: Int?,
    val restingHeartRate: Int?,
    val usualRestingHeartRate: Int?,
    val hrvMs: Int?,
    val usualHrvMs: Int?,
)

/**
 * D110: one Monday-to-Sunday week as the trainer is told it. Every visible, counted session of the week is
 * in exactly one of [practice] (as the session that ticked a planned one) and [beyond]. [practice] is null
 * for a week no kept plan covers. [current] is the calendar's week, counted to today.
 */
data class WeekLedger(
    val monday: Long,
    val current: Boolean,
    val practice: PracticeLedger?,
    val beyond: BeyondPlan,
    val food: FoodContext,
    val recovery: Recovery,
)

/**
 * D110: a week's ledger, counted afresh from the record every time, like [PlanProgress] (D95): only stored
 * answers are kept, so a session deleted, split or combined later changes the ledger with no bookkeeping.
 * Pure.
 */
object WeekLedgers {

    /** The days before a week's Monday that its usuals are counted over. */
    const val USUAL_DAYS = 28

    /** Fewer recorded days than this in [USUAL_DAYS] and there is no usual. */
    const val USUAL_LEAST_DAYS = 7

    /** Fewest days a week must record a recovery figure for its average to go (D110: one night is never sent). */
    const val WEEK_LEAST_DAYS = 2

    /**
     * @param monday the week; a Monday, never after this one.
     * @param today the calendar's today; this week is counted to it.
     * @param workouts the record's sessions as read (combined, D92); hidden and uncounted ones are left out here.
     * @param days the health record's days; the week's and the [USUAL_DAYS] before it are read.
     * @param food each day's food totals, for days holding at least one item; the week's are read.
     * @param targetKcal today's calorie target; null with no profile.
     * @param plans every kept version of a weekly plan; the one [CountedPlan.covering] the week gives practice.
     */
    fun of(
        monday: Long,
        today: Long,
        workouts: List<Workout>,
        days: List<HealthDay>,
        food: Map<Long, DayTotals>,
        targetKcal: Int?,
        plans: List<CountedPlan>,
    ): WeekLedger {
        require(MovementWeek.mondayOf(monday) == monday) { "not a Monday: $monday" }
        require(monday <= MovementWeek.mondayOf(today)) { "a week after this one: $monday" }
        val last = minOf(today, monday + 6)
        val week = monday..last
        val sessions = workouts
            .filter { !it.hidden && it.counted && it.epochDay in week }
            .sortedBy { it.startedAtMillis }
        val practice = practiceWeek(plans, monday, last, workouts)
        val progress = practice?.second
        val ticked = progress?.ticks.orEmpty().mapNotNull { it.by?.id }.toSet()
        // D105: attempts, and open candidates — what WeekProgress.outcome() sends as attempts.
        val attempted: Map<Long, PlannedSession> = progress?.let { p ->
            p.attempts.associate { it.workout.id to it.against } +
                p.ticks.mapNotNull { tick -> tick.candidate?.let { it.id to tick.planned } }
        }.orEmpty()
        val byKind = sessions
            .filter { it.id !in ticked }
            .groupBy { if (it.kind == WorkoutKind.UNRECOGNISED) WorkoutKind.OTHER else it.kind }
        val weekDays = days.filter { it.epochDay in week }
        val before = days.filter { it.epochDay in monday - USUAL_DAYS until monday }
        val eaten = food.filterKeys { it in week }.values
        return WeekLedger(
            monday = monday,
            current = MovementWeek.mondayOf(today) == monday,
            practice = practice?.let { (plan, weekProgress) ->
                PracticeLedger(weekProgress.index + 1, plan.programme.plan.weeks.size, weekProgress.outcome().sessions)
            },
            beyond = BeyondPlan(
                kinds = WorkoutKind.entries.mapNotNull { kind -> byKind[kind]?.let { kindLedger(kind, it, attempted) } },
                stepsADay = weekDays.mapNotNull { it.steps }.roundedMean(),
                distanceMADay = weekDays.mapNotNull { it.distanceM }.roundedMean(),
            ),
            food = FoodContext(
                daysLogged = eaten.size,
                kcalADay = eaten.map { it.kcal }.roundedMean(),
                proteinGADay = eaten.map { it.proteinG }.roundedMean(),
                targetKcal = targetKcal,
            ),
            recovery = Recovery(
                sleepMinutes = weekly(weekDays) { it.sleepMinutes },
                usualSleepMinutes = usual(before) { it.sleepMinutes },
                restingHeartRate = weekly(weekDays) { it.restingHeartRate },
                usualRestingHeartRate = usual(before) { it.restingHeartRate },
                hrvMs = weekly(weekDays) { it.hrvMs },
                usualHrvMs = usual(before) { it.hrvMs },
            ),
        )
    }

    /** The plan covering the week and the week's progress in it, counted to [last]; null when none covers it. */
    private fun practiceWeek(plans: List<CountedPlan>, monday: Long, last: Long, workouts: List<Workout>): Pair<CountedPlan, WeekProgress>? {
        val plan = CountedPlan.covering(plans, monday) ?: return null
        val index = ProgrammeCalendar.weekIndex(plan.start, monday)
        if (index !in plan.programme.plan.weeks.indices) return null
        val until = Programmes.countedUntil(plan.programme, last)
        return plan to PlanProgress.of(plan.programme.plan, plan.start, workouts, until, plan.counting).weeks[index]
    }

    private fun kindLedger(kind: WorkoutKind, sessions: List<Workout>, attempted: Map<Long, PlannedSession>): KindLedger {
        val zoned = sessions.mapNotNull { it.zoneSeconds }
        return KindLedger(
            kind = kind,
            sessions = sessions.size,
            minutes = sessions.sumOf { it.durationMinutes },
            distanceM = sessions.mapNotNull { it.distanceM }.takeIf { it.isNotEmpty() }?.sum(),
            // Zones 2 to 5 (D70): the seconds summed, then rounded once to the nearest minute, as the request rounds.
            briskMinutes = if (zoned.isEmpty()) null else (zoned.sumOf { zones -> zones.drop(1).sum() } + 30) / 60,
            attempts = sessions.mapNotNull { session ->
                attempted[session.id]?.let { AttemptFacts(session.kind, session.durationMinutes, it.minutes) }
            },
        )
    }

    private fun usual(before: List<HealthDay>, pick: (HealthDay) -> Number?): Int? =
        before.mapNotNull(pick).takeIf { it.size >= USUAL_LEAST_DAYS }?.roundedMean()

    /** A week's recovery average needs two recorded days: an average of one night IS that night (D110). */
    private fun weekly(weekDays: List<HealthDay>, pick: (HealthDay) -> Number?): Int? =
        weekDays.mapNotNull(pick).takeIf { it.size >= WEEK_LEAST_DAYS }?.roundedMean()

    private fun List<Number>.roundedMean(): Int? = if (isEmpty()) null else map { it.toDouble() }.average().roundToInt()
}
```

- [ ] **Step 4: Run it to see it pass.** Same command as Step 2. Expected: `exit 0`; all 15 tests pass
  (`grep -c '<testcase' app/build/test-results/testDebugUnitTest/TEST-com.metaself.app.domain.trainer.WeekLedgersTest.xml`
  → 15, no `<failure`).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/trainer/WeekLedger.kt app/src/test/java/com/metaself/app/domain/trainer/WeekLedgersTest.kt
git commit -m "$(cat <<'EOF'
feat(trainer): a week's ledger — practice, beyond the plan, food and recovery (D110)

WeekLedgers.of counts one Monday-to-Sunday week afresh from the record:
the covering weekly plan's outcomes exactly as PlanProgress counts them,
every other visible counted session by kind (minutes, distance, brisk
minutes in zones 2-5, attempt marks), steps and distance a day, food
over the days logged with the target, and sleep, resting heart rate and
HRV each beside its 28-day usual (none under 7 recorded days).
CountedPlan.covering picks the version a week counts under; of two with
one start (an adjustment), the one made later.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: The request carries six ledgers, and sends them (D111)

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/trainer/TrainerRequest.kt`
- Modify: `app/src/main/java/com/metaself/app/data/ai/TrainerPrompt.kt`
- Modify: `app/src/main/java/com/metaself/app/data/trainer/AskTheTrainer.kt` (compile only; Task 3 wires it)
- Test: `app/src/test/java/com/metaself/app/domain/trainer/TrainerRequestTest.kt`
- Test: `app/src/test/java/com/metaself/app/data/ai/TrainerPromptTest.kt`
- Test: `app/src/test/java/com/metaself/app/data/ai/OpenAiTrainerTest.kt`
- Test: `app/src/test/java/com/metaself/app/data/ai/OpenAiWorkbenchTest.kt`

- [ ] **Step 1: TrainerRequestTest — the helper.** Add imports `com.metaself.app.domain.day.DayTotals`. Replace
  the `request(...)` helper (it ends at `earliestDay = earliest, aboutMe = aboutMe,\n    )`) with:

```kotlin
    private fun request(
        question: TrainerQuestion = TrainerQuestion.Plan(
            PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_30, Feeling.FRESH, Wish.PUSH),
        ),
        readings: List<WeightReading> = listOf(WeightReading(20_671, 80.0), WeightReading(20_685, 80.0), WeightReading(20_699, 80.0)),
        profile: com.metaself.app.domain.profile.Profile? = aProfile(),
        feedback: List<Feedback> = emptyList(),
        workouts: List<Workout> = all,
        earliest: Long? = null,
        aboutMe: String? = null,
        food: Map<Long, DayTotals> = emptyMap(),
        weeklyPlans: List<CountedPlan> = emptyList(),
    ) = TrainerRequest.of(
        question = question, today = TEST_EPOCH_DAY, workouts = workouts, reviews = listOf(review),
        plans = mapOf(9L to plan),
        days = listOf(HealthDay(20_699, distanceM = 4_000, activeKcal = 300), HealthDay(20_698, distanceM = 2_000, activeKcal = 100),
            HealthDay(20_690, distanceM = 5_000)),
        food = food, targetKcal = 2_000, weeklyPlans = weeklyPlans,
        readings = readings, profile = profile, currentYear = TEST_YEAR, earlierFeedback = feedback,
        earliestDay = earliest, aboutMe = aboutMe,
    )
```

- [ ] **Step 2: TrainerRequestTest — the six weeks.** Replace the test `six weeks of totals, this one first
  and so far` (and its `/** D74's figures ... */` KDoc) with these three:

```kotlin
    /** D110, D111: six ledgers, newest first, this one so far. No plan: every session is beyond it. */
    @Test
    fun `six week ledgers, this one first and so far`() {
        val weeks = request().weeks

        assertThat(weeks.map { it.monday }).containsExactly(20_696L, 20_689L, 20_682L, 20_675L, 20_668L, 20_661L).inOrder()
        assertThat(weeks.first().current).isTrue()
        assertThat(weeks.drop(1).none { it.current }).isTrue()
        assertThat(weeks.none { it.practice != null }).isTrue()
        // The walk and the strength session; the hidden and the uncounted ones left out.
        assertThat(weeks[0].beyond.kinds.map { it.kind to it.sessions })
            .containsExactly(WorkoutKind.WALK to 1, WorkoutKind.STRENGTH to 1).inOrder()
        assertThat(weeks[0].beyond.distanceMADay).isEqualTo(3_000)
        assertThat(weeks[1].beyond.kinds).isEmpty()
        assertThat(weeks[1].beyond.distanceMADay).isEqualTo(5_000)
    }

    /** D110: a kept plan of two weeks of two 30-minute walks, begun the Monday before this one. Invented. */
    @Test
    fun `a week a kept plan covers carries its practice`() {
        val planned = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented walk")
        val weeksPlan = WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", listOf(planned, planned)) }, "Invented.")
        val kept = CountedPlan(
            Programme(1, 0, ProgrammeAsk(2, 2), null, weeksPlan, "a-model", 20_689, ProgrammeStatus.RUNNING),
            PlanCounting(1, 0, emptyMap()),
        )

        val weeks = request(weeklyPlans = listOf(kept)).weeks

        assertThat(weeks[0].practice!!.week).isEqualTo(2)
        assertThat(weeks[0].practice!!.ofWeeks).isEqualTo(2)
        assertThat(weeks[0].practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.NOT_DONE).inOrder()
        assertThat(weeks[0].beyond.kinds.map { it.kind }).containsExactly(WorkoutKind.STRENGTH)
        assertThat(weeks[1].practice!!.week).isEqualTo(1)
        assertThat(weeks[2].practice).isNull()
    }

    /** D110: each week's food, over the days logged, with today's target. Invented totals. */
    @Test
    fun `each week carries its food and the target`() {
        val food = mapOf(20_697L to DayTotals(2_000, 100, 200, 70), 20_698L to DayTotals(2_400, 140, 200, 70))

        val weeks = request(food = food).weeks

        assertThat(weeks[0].food).isEqualTo(FoodContext(2, 2_200, 120, 2_000))
        assertThat(weeks[1].food).isEqualTo(FoodContext(0, null, null, 2_000))
    }
```

- [ ] **Step 3: TrainerRequestTest — the shape.** In `the request has room for exactly what D84 lists`,
  replace the KDoc's first sentence with: `D84's "never sent", as a shape, amended by D111: there is no field
  for a meal, a food, a single night's sleep or a single day's heart rate, a raw reading, a weigh-in, a target
  weight, a name, a title or an origin; a week's food and recovery go only as averages.` Then replace the line

```kotlin
        assertThat(fieldsOf(WeekFacts::class.java)).containsExactly("monday", "distanceM", "averageActiveKcal", "sessions", "current")
```

  with

```kotlin
        // D110, D111: each week's ledger, in place of D84's weekly totals.
        assertThat(fieldsOf(WeekLedger::class.java)).containsExactly("monday", "current", "practice", "beyond", "food", "recovery")
        assertThat(fieldsOf(PracticeLedger::class.java)).containsExactly("week", "ofWeeks", "sessions")
        assertThat(fieldsOf(BeyondPlan::class.java)).containsExactly("kinds", "stepsADay", "distanceMADay")
        assertThat(fieldsOf(KindLedger::class.java)).containsExactly("kind", "sessions", "minutes", "distanceM", "briskMinutes", "attempts")
        // D111's additions, and no further: food as days logged and averages with the target; recovery as averages and usuals.
        assertThat(fieldsOf(FoodContext::class.java)).containsExactly("daysLogged", "kcalADay", "proteinGADay", "targetKcal")
        assertThat(fieldsOf(Recovery::class.java)).containsExactly(
            "sleepMinutes", "usualSleepMinutes", "restingHeartRate", "usualRestingHeartRate", "hrvMs", "usualHrvMs",
        )
```

- [ ] **Step 4: TrainerPromptTest — the helpers.** Add imports:

```kotlin
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.trainer.CountedPlan
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeStatus
import kotlinx.serialization.json.boolean
```

  Replace the `request(question, earlier)` helper with:

```kotlin
    /**
     * Two days this week, each with its own sleep, resting heart rate and HRV, so the week's averages (430
     * minutes, 58, 45 ms) differ from every single day's; one of them also holds sleep stages, oxygen and a
     * breathing rate, which never go. Two days of food, whose averages (2,100 kcal, 110 g) differ from each day's.
     */
    private fun request(question: TrainerQuestion, earlier: List<Feedback> = emptyList(), weeklyPlans: List<CountedPlan> = emptyList()) =
        TrainerRequest.of(
            question = question, today = TEST_EPOCH_DAY, workouts = listOf(walk, strength), reviews = listOf(review),
            plans = mapOf(9L to plan),
            days = listOf(
                HealthDay(
                    TEST_EPOCH_DAY, distanceM = 4_000, activeKcal = 300, sleepMinutes = 410, restingHeartRate = 55, hrvMs = 37.0,
                    deepMinutes = 95, oxygenPct = 96.5, respiratoryRate = 14.5,
                ),
                HealthDay(TEST_EPOCH_DAY - 1, sleepMinutes = 450, restingHeartRate = 61, hrvMs = 53.0),
            ),
            food = mapOf(TEST_EPOCH_DAY - 1 to DayTotals(1_900, 94, 200, 70), TEST_EPOCH_DAY to DayTotals(2_300, 126, 200, 70)),
            targetKcal = 2_000,
            weeklyPlans = weeklyPlans,
            readings = listOf(
                WeightReading(TEST_EPOCH_DAY - 28, 80.4), WeightReading(TEST_EPOCH_DAY - 14, 79.2), WeightReading(TEST_EPOCH_DAY, 78.6),
            ),
            profile = aProfile(weightKg = 83.0, goal = Goal.lose(0.5, targetKg = 71.5)),
            currentYear = TEST_YEAR, earlierFeedback = earlier,
        )

    /** [planRequest] with a kept plan of two weeks of two steady walks, begun this Monday: the walk ticks one. */
    private fun ledgerRequest() = request(
        TrainerQuestion.Plan(PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_30, Feeling.FRESH, Wish.PUSH)),
        weeklyPlans = listOf(
            CountedPlan(
                Programme(
                    1, 0, ProgrammeAsk(2, 2), null,
                    WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", listOf(STEADY, STEADY)) }, "Invented."),
                    "a-model", TEST_EPOCH_DAY - 3, ProgrammeStatus.RUNNING,
                ),
                PlanCounting(1, 0, emptyMap()),
            ),
        ),
    )
```

  In `yearRequest()`: change its KDoc's "two days with steps and distance, one of them with the day's sleep and
  resting heart rate" to "two June days with steps and distance, one of them with a night's sleep and the
  day's resting heart rate, outside every week's 28 days"; change its `days` to

```kotlin
            days = listOf(
                HealthDay(TEST_EPOCH_DAY, distanceM = 4_000, activeKcal = 300),
                HealthDay(JUNE_1 + 9, steps = 6_000, distanceM = 6_000, sleepMinutes = 470, restingHeartRate = 64),
                HealthDay(JUNE_1 + 10, steps = 8_000),
            ),
            food = emptyMap(),
            targetKcal = 2_000,
            weeklyPlans = emptyList(),
```

  (keep the rest of the call as it is).

- [ ] **Step 5: TrainerPromptTest — "never sent", deliberately for D111.** Replace the KDoc and test
  `no meal, sleep, weigh-in, target, title or app name is sent` with:

```kotlin
    /**
     * D84's "never sent", amended by D111 and no further. If this fails because the prompt's own wording used
     * a word, rephrase the prompt.
     *
     * Each category has at least one value the fixtures really hold: the title "Quillberry loop"; a single
     * night's sleep (410, 450), a single day's resting heart rate (55, 61) and HRV (37, 53) — whose week's
     * averages (430, 58, 45) may go (D111); a single day's calories (1,900, 2,300) and protein (94, 126) —
     * whose averages may go; sleep stages (95 deep minutes), oxygen (96.5) and breathing rate (14.5); the
     * weigh-ins (80.4, 79.2, 78.6) and the profile's weight (83.0); the target weight (71.5). [yearRequest]
     * adds a June day outside every week's 28 days, whose sleep (470) and resting heart rate (64) stay on the
     * phone, and weigh-ins (82.0, 80.0) that must too (D89). A number is matched as a whole number, so 410
     * does not match inside 4100. The words and the package name have no path into a request; they stay as
     * a guard on the prompt.
     */
    @Test
    fun `no meal, single night or day, raw reading, weigh-in, target weight, title or app name is sent`() {
        listOf(
            TrainerPrompt.planBody("a-model", planRequest()),
            TrainerPrompt.feedbackBody("a-model", reviewRequest()),
            TrainerPrompt.planBody("a-model", yearRequest()),
        )
            .map { it.lowercase() }
            .forEach { body ->
                listOf(
                    "quillberry", "com.example", "slept", "meal", "eaten", "breakfast", "oxygen", "respiratory",
                    "deep_minutes", "83.0", "80.4", "79.2", "78.6", "71.5", "82.0", "80.0", "96.5", "14.5",
                ).forEach { forbidden -> assertThat(body).doesNotContain(forbidden) }
                listOf("410", "450", "55", "61", "37", "53", "1900", "2300", "94", "126", "95", "470", "64").forEach { forbidden ->
                    assertThat(Regex("(?<![0-9.])$forbidden(?![0-9.])").containsMatchIn(body)).isFalse()
                }
            }
    }
```

  (If one of the whole-number checks fails on an **unrelated** figure — say a date or a computed value that
  happens to equal it — pick another invented pair for that fixture whose average stays round, and say why in
  the KDoc. Never weaken the check.)

- [ ] **Step 6: TrainerPromptTest — the ledger's shape.** Add, after `the days left are named as after today`:

```kotlin
    /** D110, D111: six ledgers, newest first; each with exactly these keys, and only the phone's counts. */
    @Test
    fun `each week is sent as its ledger, with exactly these keys`() {
        val body = Json.parseToJsonElement(TrainerPrompt.planBody("a-model", ledgerRequest(), RequestProfile.DETERMINISTIC)).jsonObject
        val weeks = Json.parseToJsonElement(userContent(body)).jsonObject.getValue("weeks").jsonArray.map { it.jsonObject }

        assertThat(weeks.map { it.getValue("from").jsonPrimitive.content })
            .containsExactly("2026-08-31", "2026-08-24", "2026-08-17", "2026-08-10", "2026-08-03", "2026-07-27").inOrder()
        val week = weeks.first()
        assertThat(week.keys).containsExactly("from", "so_far", "practice", "beyond_the_plan", "food", "recovery")
        assertThat(week.getValue("so_far").jsonPrimitive.boolean).isTrue()
        assertThat(weeks[1].getValue("so_far").jsonPrimitive.boolean).isFalse()

        val practice = week.getValue("practice").jsonObject
        assertThat(practice.keys).containsExactly("week", "of_weeks", "sessions")
        val planned = practice.getValue("sessions").jsonArray.map { it.jsonObject }
        assertThat(planned.first().keys).containsExactly("kind", "planned_minutes", "outcome", "minutes_done")
        assertThat(planned.map { it.getValue("outcome").jsonPrimitive.content }).containsExactly("done", "not_done").inOrder()
        assertThat(weeks[1].getValue("practice")).isEqualTo(JsonNull)

        val beyond = week.getValue("beyond_the_plan").jsonObject
        assertThat(beyond.keys).containsExactly("sessions_by_kind", "everyday")
        val strength = beyond.getValue("sessions_by_kind").jsonArray.single().jsonObject
        assertThat(strength.keys).containsExactly("kind", "sessions", "minutes", "distance_m", "brisk_minutes", "attempts")
        assertThat(strength.getValue("kind").jsonPrimitive.content).isEqualTo("strength")
        assertThat(strength.getValue("minutes").jsonPrimitive.int).isEqualTo(45)
        assertThat(strength.getValue("brisk_minutes")).isEqualTo(JsonNull)
        assertThat(beyond.getValue("everyday").jsonObject.keys).containsExactly("steps_a_day", "distance_m_a_day")

        val food = week.getValue("food").jsonObject
        assertThat(food.keys).containsExactly("days_logged", "kcal_a_day", "protein_g_a_day", "target_kcal")
        assertThat(food.getValue("days_logged").jsonPrimitive.int).isEqualTo(2)
        assertThat(food.getValue("kcal_a_day").jsonPrimitive.int).isEqualTo(2_100)
        assertThat(food.getValue("protein_g_a_day").jsonPrimitive.int).isEqualTo(110)
        assertThat(food.getValue("target_kcal").jsonPrimitive.int).isEqualTo(2_000)

        val recovery = week.getValue("recovery").jsonObject
        assertThat(recovery.keys).containsExactly(
            "sleep_minutes_a_night", "usual_sleep_minutes_a_night", "resting_heart_rate", "usual_resting_heart_rate",
            "hrv_ms", "usual_hrv_ms",
        )
        assertThat(recovery.getValue("sleep_minutes_a_night").jsonPrimitive.int).isEqualTo(430)
        assertThat(recovery.getValue("resting_heart_rate").jsonPrimitive.int).isEqualTo(58)
        assertThat(recovery.getValue("hrv_ms").jsonPrimitive.int).isEqualTo(45)
        assertThat(recovery.getValue("usual_sleep_minutes_a_night")).isEqualTo(JsonNull)
    }
```

- [ ] **Step 7: OpenAiTrainerTest and OpenAiWorkbenchTest fixtures.** In both files replace the import
  `com.metaself.app.domain.trainer.WeekFacts` with

```kotlin
import com.metaself.app.domain.trainer.BeyondPlan
import com.metaself.app.domain.trainer.FoodContext
import com.metaself.app.domain.trainer.Recovery
import com.metaself.app.domain.trainer.WeekLedger
```

  and in each the `weeks = (0 until 6).map { back -> WeekFacts(...) }` argument with

```kotlin
            weeks = (0 until 6).map { back ->
                WeekLedger(
                    monday = TEST_EPOCH_DAY - 3 - 7L * back, current = back == 0, practice = null,
                    beyond = BeyondPlan(emptyList(), null, null), food = FoodContext(0, null, null, null),
                    recovery = Recovery(null, null, null, null, null, null),
                )
            },
```

- [ ] **Step 8: Run to see it fail.**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.trainer.TrainerRequestTest" --tests "com.metaself.app.data.ai.TrainerPromptTest" > /tmp/ms-ledger-2.log 2>&1; echo "exit $?"
```

  Expected: non-zero exit; compile errors (`food`, `targetKcal`, `weeklyPlans` are not parameters of `of`;
  `WeekFacts` still referenced by `TrainerPrompt`).

- [ ] **Step 9: TrainerRequest.** In `TrainerRequest.kt`:

  1. Add `import com.metaself.app.domain.day.DayTotals`.
  2. Delete the whole `WeekFacts` class and its KDoc (`/** One Monday-based week's D74 figures. [current] is this week, so far. */`).
  3. Replace the `TrainerRequest` KDoc's first paragraph with:

```kotlin
/**
 * Everything one trainer request holds (D84, amended by D111) — built fresh from the stored record every
 * time; no conversation is kept or replayed. There is deliberately no field for a meal, a food, a single
 * night's sleep or a single day's heart rate, a raw reading, a single weigh-in, a target weight, a name, or
 * an app or device name; a week's food and recovery go only as averages in its ledger (D110).
 * `TrainerRequestTest` fails if a field is added.
 *
 * @property weeks six week ledgers (D110), this week first and so far, then the five before it.
```

  (keep the existing `@property aboutMe` and `@property months` lines after it), and change
  `val weeks: List<WeekFacts>,` to `val weeks: List<WeekLedger>,`.

  4. In the companion, after `firstSummaryDay`, add:

```kotlin
        /** The first day the six ledgers read: the 28 days before the first week, for recovery's usuals (D110). */
        fun firstLedgerDay(today: Long): Long = firstSummaryDay(today) - WeekLedgers.USUAL_DAYS
```

  5. Replace the KDoc and signature of `of(...)` down to `val first = firstDay(today)` with:

```kotlin
        /**
         * @param workouts any workouts; only the visible, counted ones of the 42 days are sent as
         *   sessions; those of the six weeks are placed in their ledgers (D110); those of the months before
         *   the 42 days are counted into the monthly lines (D89).
         * @param reviews any reviews; each is attached to its session.
         * @param plans the stored single-session plans the reviews name, by id.
         * @param days the daily summaries from [firstLedgerDay] (or [firstRecordDay], for the monthly
         *   lines) to [today].
         * @param food each day's food totals over the six weeks (D110); a meal is never read.
         * @param targetKcal today's calorie target, beside each week's food (D110); null with no profile.
         * @param weeklyPlans every kept version of a weekly plan, with its counting (D105): each week's practice.
         * @param earlierFeedback newest first; the first [FEEDBACK_COUNT] are sent.
         * @param earliestDay the first day the record holds anything; months before it have no line (D89).
         * @param aboutMe the owner's note (D90), as stored.
         */
        fun of(
            question: TrainerQuestion,
            today: Long,
            workouts: List<Workout>,
            reviews: List<TrainerReview>,
            plans: Map<Long, TrainerPlan>,
            days: List<HealthDay>,
            food: Map<Long, DayTotals>,
            targetKcal: Int?,
            weeklyPlans: List<CountedPlan>,
            readings: List<WeightReading>,
            profile: Profile?,
            currentYear: Int,
            earlierFeedback: List<Feedback>,
            earliestDay: Long? = null,
            aboutMe: String? = null,
        ): TrainerRequest {
            val first = firstDay(today)
```

  6. Replace

```kotlin
            val weeks = (0 until WEEKS).map { back ->
                val week = MovementWeek.of(today, days, workouts, emptyMap(), thisMonday - 7L * back)
                WeekFacts(week.monday, week.distanceM, week.averageActiveKcal, week.workoutCount + week.walkCount, back == 0)
            }
```

  with

```kotlin
            val weeks = (0 until WEEKS).map { back ->
                WeekLedgers.of(thisMonday - 7L * back, today, workouts, days, food, targetKcal, weeklyPlans)
            }
```

  and

```kotlin
                thisWeek = Rhythm(weeks.first().sessions, (thisMonday + 6 - today).toInt()),
```

  with

```kotlin
                // D87, unchanged: this week's visible, counted sessions so far, as the Movement screen counts them.
                thisWeek = Rhythm(
                    workouts.count { !it.hidden && it.counted && it.epochDay in thisMonday..today },
                    (thisMonday + 6 - today).toInt(),
                ),
```

- [ ] **Step 10: TrainerPrompt — the ledger, serialised.** In `TrainerPrompt.kt`:

  1. Add imports `com.metaself.app.domain.trainer.SessionOutcome`, `com.metaself.app.domain.trainer.WeekLedger`
     and `kotlinx.serialization.json.JsonElement`.
  2. In `user(request)`, replace the whole `putJsonArray("weeks") { ... }` block with:

```kotlin
        putJsonArray("weeks") { request.weeks.forEach { add(ledgerJson(it)) } }
```

  3. In `weekOutcome`, replace the inner `add(buildJsonObject { put("kind", ...) ... put("minutes_done", ...) })`
     for each session with `add(sessionOutcomeJson(session))`, and add after `weekOutcome`:

```kotlin
    /** D105: a planned session's outcome, as `how_it_went` and a ledger's practice send it. Minutes only. */
    private fun sessionOutcomeJson(session: SessionOutcome): JsonObject = buildJsonObject {
        put("kind", kind(session.planned.kind))
        put("planned_minutes", session.planned.minutes)
        put("outcome", outcome(session.outcome))
        put("minutes_done", session.minutesDone?.let(::JsonPrimitive) ?: JsonNull)
    }

    /**
     * D110: one week's ledger, every figure the phone's; a null was not recorded. Shared with the letter,
     * which sends its week whole and so without `so_far` ([withSoFar] false).
     */
    internal fun ledgerJson(ledger: WeekLedger, withSoFar: Boolean = true): JsonObject = buildJsonObject {
        put("from", date(ledger.monday))
        if (withSoFar) put("so_far", ledger.current)
        put(
            "practice",
            ledger.practice?.let { practice ->
                buildJsonObject {
                    put("week", practice.week)
                    put("of_weeks", practice.ofWeeks)
                    putJsonArray("sessions") { practice.sessions.forEach { add(sessionOutcomeJson(it)) } }
                }
            } ?: JsonNull,
        )
        putJsonObject("beyond_the_plan") {
            putJsonArray("sessions_by_kind") {
                ledger.beyond.kinds.forEach { group ->
                    add(
                        buildJsonObject {
                            put("kind", kind(group.kind))
                            put("sessions", group.sessions)
                            put("minutes", group.minutes)
                            put("distance_m", number(group.distanceM))
                            put("brisk_minutes", number(group.briskMinutes))
                            putJsonArray("attempts") {
                                group.attempts.forEach { attempt ->
                                    add(
                                        buildJsonObject {
                                            put("minutes", attempt.minutes)
                                            put("planned_minutes", attempt.plannedMinutes)
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            putJsonObject("everyday") {
                put("steps_a_day", number(ledger.beyond.stepsADay))
                put("distance_m_a_day", number(ledger.beyond.distanceMADay))
            }
        }
        putJsonObject("food") {
            put("days_logged", ledger.food.daysLogged)
            put("kcal_a_day", number(ledger.food.kcalADay))
            put("protein_g_a_day", number(ledger.food.proteinGADay))
            put("target_kcal", number(ledger.food.targetKcal))
        }
        putJsonObject("recovery") {
            put("sleep_minutes_a_night", number(ledger.recovery.sleepMinutes))
            put("usual_sleep_minutes_a_night", number(ledger.recovery.usualSleepMinutes))
            put("resting_heart_rate", number(ledger.recovery.restingHeartRate))
            put("usual_resting_heart_rate", number(ledger.recovery.usualRestingHeartRate))
            put("hrv_ms", number(ledger.recovery.hrvMs))
            put("usual_hrv_ms", number(ledger.recovery.usualHrvMs))
        }
    }

    private fun number(value: Int?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
```

- [ ] **Step 11: AskTheTrainer compiles.** In `AskTheTrainer.request(...)`, add three arguments after
  `days = ...`, so the build compiles; **Task 3 replaces all three with the real reads** and its test fails
  until it does:

```kotlin
            food = emptyMap(),
            targetKcal = null,
            weeklyPlans = emptyList(),
```

- [ ] **Step 12: Run to see it pass.** Same command as Step 8, then the whole trainer and AI suites:

```bash
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.trainer.*" --tests "com.metaself.app.data.ai.*" --tests "com.metaself.app.data.trainer.*" > /tmp/ms-ledger-2b.log 2>&1; echo "exit $?"
```

  Expected: `exit 0` both times.

- [ ] **Step 13: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/trainer/TrainerRequest.kt app/src/main/java/com/metaself/app/data/ai/TrainerPrompt.kt app/src/main/java/com/metaself/app/data/trainer/AskTheTrainer.kt app/src/test/java/com/metaself/app/domain/trainer/TrainerRequestTest.kt app/src/test/java/com/metaself/app/data/ai/TrainerPromptTest.kt app/src/test/java/com/metaself/app/data/ai/OpenAiTrainerTest.kt app/src/test/java/com/metaself/app/data/ai/OpenAiWorkbenchTest.kt
git commit -m "$(cat <<'EOF'
feat(trainer): every request carries six week ledgers (D111)

The six weekly totals (D84) are replaced by six ledgers, this week first
and so far, sent under "weeks" with practice, beyond_the_plan, food and
recovery. The rhythm keeps its own count. The never-sent tests are
updated for D111's additions and no further: a week's averages may go,
each single night's and day's figure stays forbidden, and sleep stages,
oxygen, breathing rate and a day's food are added to the list.

AskTheTrainer passes no food, target or plans yet; the next commit reads them.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Every ask reads what the ledgers need

**Files:**
- Modify: `app/src/main/java/com/metaself/app/data/trainer/AskTheTrainer.kt`
- Test: `app/src/test/java/com/metaself/app/data/trainer/AskTheTrainerTest.kt`

- [ ] **Step 1: Write the failing tests.** In `AskTheTrainerTest.kt` add imports:

```kotlin
import com.metaself.app.data.letter.FakeFoodTotals
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.trainer.FoodContext
import com.metaself.app.domain.trainer.KindLedger
import com.metaself.app.domain.trainer.PlannedOutcome
```

  (skip any already imported). Add the field `private val food = FakeFoodTotals()` beside the other fakes, and
  change `ask()` to pass it last:

```kotlin
    private fun ask() = AskTheTrainer(
        record, store, weights, FakeProfileRepository(aProfile()), trainer, aboutMe, programmes,
        Today { LocalDate.ofEpochDay(day) }, Now { NOW }, CurrentYear { TEST_YEAR }, food,
    )
```

  Add, after `the request holds the months and the note`:

```kotlin
    /**
     * D110, D111: six ledgers, newest first. The plan (two weeks of two 30-minute walks) began last Monday;
     * every walk is forty minutes. Last week one walk ticked one of two; this week two tick both and a third
     * is beyond the plan. Two days of food this week. Invented.
     */
    @Test
    fun `the request carries six week ledgers, with the plan's practice, the food and the target`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, TEST_EPOCH_DAY - 7)
        store.workouts.value = listOf(
            walk(id = 1, day = MONDAY - 7), walk(id = 2, day = MONDAY), walk(id = 3, day = MONDAY + 1), walk(id = 4, day = TEST_EPOCH_DAY),
        )
        record.workouts.value = store.workouts.value
        food.days = mapOf(MONDAY to DayTotals(2_000, 100, 200, 70), MONDAY + 1 to DayTotals(2_200, 120, 200, 70))
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        ask().suggest(ANSWERS)

        val weeks = trainer.asked.single().weeks
        assertThat(weeks.map { it.monday })
            .containsExactly(MONDAY, MONDAY - 7, MONDAY - 14, MONDAY - 21, MONDAY - 28, MONDAY - 35).inOrder()
        assertThat(weeks[0].practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.DONE)
        assertThat(weeks[0].beyond.kinds).containsExactly(KindLedger(WorkoutKind.WALK, 1, 40, 4_000, null, emptyList()))
        assertThat(weeks[1].practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.NOT_DONE).inOrder()
        assertThat(weeks[1].beyond.kinds).isEmpty()
        assertThat(weeks[2].practice).isNull()
        assertThat(weeks[0].food).isEqualTo(FoodContext(2, 2_100, 110, CurrentTarget.of(aProfile(), null, TEST_YEAR, 0).kcal))
        assertThat(weeks[1].food.daysLogged).isEqualTo(0)
    }

    /** D110: the sixth week's usual needs the 28 days before its Monday; seven nights there are enough. Invented. */
    @Test
    fun `the request reads the 28 days before its first week, for recovery's usuals`() = runTest {
        val sixth = MONDAY - 35
        record.days.value = (1L..7L).map { HealthDay(sixth - it * 4, sleepMinutes = 420) }
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        ask().suggest(ANSWERS)

        assertThat(trainer.asked.single().weeks.last().recovery.usualSleepMinutes).isEqualTo(420)
    }
```

- [ ] **Step 2: Run to see it fail.**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.trainer.AskTheTrainerTest" > /tmp/ms-ledger-3.log 2>&1; echo "exit $?"
```

  Expected: non-zero exit — a compile error, `AskTheTrainer` takes no eleventh argument.

- [ ] **Step 3: Read the food, the target, the plans and the days.** In `AskTheTrainer.kt`:

  1. Add imports:

```kotlin
import com.metaself.app.data.letter.FoodTotals
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.trainer.CountedPlan
import com.metaself.app.domain.trainer.ProgrammeStatus
```

  2. Add the last constructor parameter, after `private val year: CurrentYear,`:

```kotlin
    /** Each day's food totals (D110), the letter's reader; never a meal. Defaulted for tests that send no food. */
    private val food: FoodTotals = FoodTotals { _, _ -> emptyMap() },
```

  3. In the class KDoc, after "the owner's note (D90)", add ", each day's food totals and the kept weekly plans (D110)".
  4. Replace the body of `request(...)` with:

```kotlin
    suspend fun request(question: TrainerQuestion, exceptWorkoutId: Long = NO_WORKOUT): TrainerRequest {
        val day = today().toEpochDay()
        val reviews = store.observeReviews().first()
        // A year back for the monthly lines (D89); the 42 days, the six weeks and the 28 days before them
        // for the usuals (D110) are within it — the minOf says so rather than assuming it.
        val first = minOf(TrainerRequest.firstRecordDay(day), TrainerRequest.firstLedgerDay(day))
        val profile = profiles.profile.first()
        return TrainerRequest.of(
            question = question,
            today = day,
            workouts = record.observeWorkouts(first, day).first(),
            reviews = reviews,
            plans = store.plans(reviews.mapNotNull { it.planId }),
            days = record.observeDays(first, day).first(),
            food = food.byDay(TrainerRequest.firstSummaryDay(day), day),
            targetKcal = profile?.let {
                CurrentTarget.of(it, profiles.revision.first(), year(), profiles.burnAdjustmentKcal.first()).kcal
            },
            weeklyPlans = countedPlans(),
            readings = weights.readings.first(),
            profile = profile,
            currentYear = year(),
            earlierFeedback = store.latestFeedback(TrainerRequest.FEEDBACK_COUNT, exceptWorkoutId),
            earliestDay = record.observeEarliestDay().first(),
            aboutMe = aboutMe.note.first(),
        )
    }

    /** D110: every kept version of a weekly plan, each counted from its chain's first keep (D105). */
    private suspend fun countedPlans(): List<CountedPlan> {
        val all = programmes.all()
        return all.filter { it.status != ProgrammeStatus.OFFERED && it.startEpochDay != null }.map { programme ->
            val root = Programmes.rootOf(programme, all)
            CountedPlan(programme, PlanCounting(root.id, root.createdAtMillis, programmes.confirmations(root.id)))
        }
    }
```

- [ ] **Step 4: Run to see it pass.** Same command as Step 2, then everything that builds an `AskTheTrainer`:

```bash
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.trainer.*" --tests "com.metaself.app.ui.screen.trainer.*" --tests "com.metaself.app.ui.screen.settings.TrainerWorkbenchViewModelTest" > /tmp/ms-ledger-3b.log 2>&1; echo "exit $?"
```

  Expected: `exit 0` both times.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/data/trainer/AskTheTrainer.kt app/src/test/java/com/metaself/app/data/trainer/AskTheTrainerTest.kt
git commit -m "$(cat <<'EOF'
feat(trainer): every ask reads food totals, the target and the kept plans (D110, D111)

The request now reads each day's food totals over the six weeks (the
letter's FoodTotals; never a meal), today's calorie target, every kept
version of a weekly plan with its chain's counting, and the health days
from the 28 days before the first week, so each ledger has its practice,
food and recovery usuals. The workbench uses the same request.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: The trainer's instructions — what a ledger is, and D112's rules

**Files:**
- Modify: `app/src/main/java/com/metaself/app/data/ai/TrainerPrompt.kt`
- Test: `app/src/test/java/com/metaself/app/data/ai/TrainerPromptTest.kt`

- [ ] **Step 1: Write the failing test.** In `TrainerPromptTest.kt`, add after `every path's instructions say
  of_weeks is the plan's length`:

```kotlin
    /** D110, D112: every path's instructions say what a ledger holds and carry the five rules for using it. */
    @Test
    fun `every path's instructions carry the ledger and the rules for using it`() {
        TrainerPath.entries.forEach { path ->
            val system = TrainerPrompt.instructions(path)

            assertThat(system).contains(TrainerPrompt.LEDGER)
            assertThat(system).contains(TrainerPrompt.WEEK_RULES)
            assertThat(system).contains("weeks holds six week ledgers, this week first")
            RULES.forEach { assertThat(system).contains(it) }
            assertThat(system).doesNotContain("six weekly totals")
        }
    }

    /** D110: the ledger's own words for what is never added up, and what a null is. */
    @Test
    fun `the ledger says everyday movement is never split, and a null is not zero`() {
        assertThat(TrainerPrompt.LEDGER).contains("so never add the two or take one from the other")
        assertThat(TrainerPrompt.LEDGER).contains("A null is a figure not recorded, never zero.")
        assertThat(TrainerPrompt.LEDGER).contains("null when fewer than 7 of those days recorded it")
    }
```

  and in the companion object add:

```kotlin
        /** D112's five rules, as fragments each on one line of the text. */
        val RULES = listOf(
            "Both parts, every time.",
            "never excuse it with other activity",
            "Lead with what matters most this week, whichever part it is in.",
            "is itself worth saying, and is a reason to suggest more.",
            "Recovery shapes intensity.",
            "is a reason for an easier session or a rest day; say so. Do not diagnose.",
            "Food is context.",
            "Never prescribe a diet, a food or an amount.",
            "No figure the app did not count.",
            "you may compare two of them, but never estimate one.",
        )
```

- [ ] **Step 2: Run to see it fail.**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.TrainerPromptTest" > /tmp/ms-ledger-4.log 2>&1; echo "exit $?"
```

  Expected: non-zero exit — `LEDGER` and `WEEK_RULES` unresolved.

- [ ] **Step 3: The text.** In `TrainerPrompt.kt`:

  1. In `COMMON`'s first paragraph replace `six weekly totals (this\n        week first, so far),` so the
     paragraph reads:

```
        You are a walking and running trainer for one person. You are given their activity record: every
        session of the last 42 days with its figures and where each came from, six week ledgers (weeks,
        below), a line for each of up to twelve months before those 42 days (months, oldest
        first), their smoothed weight trend and its weekly change, their goal's direction and weekly rate,
        their age, sex and height, and your last feedback to them.
```

  2. After `COMMON`, add:

```kotlin
    /** D111: the six ledgers, as a trainer request holds them. */
    private val SIX_WEEKS = """
        weeks holds six week ledgers, this week first and then the five before it; so_far is true for this
        week, which is not over yet.
    """.trimIndent()

    /** D110: what a week's ledger holds. Shared, word for word, with the letter's instructions. */
    internal val LEDGER = """
        A week's ledger runs Monday to Sunday from "from" and was counted by the app from their record. Every
        session of the week is in exactly one of its two parts, so nothing is counted twice and nothing is
        left out.
        practice is their weekly plan's week, when a kept plan covered it, and null when none did: week
        (counted from 1) of of_weeks, and each planned session with its kind, planned_minutes, outcome
        ("done", "done_short_confirmed" or "not_done") and minutes_done.
        beyond_the_plan is everything else they did. sessions_by_kind gives, for each kind, how many sessions
        ticked no planned session, their total minutes, their total distance_m where recorded, and
        brisk_minutes: the minutes in heart-rate zones 2 to 5, summed over those sessions that had zones
        (null when none had any, which is not zero). attempts lists those of them that were an attempt at a
        planned session, each with its minutes and the planned_minutes it attempted. everyday gives steps and
        distance a day, each averaged over the days that recorded it; it already includes the sessions' movement,
        so never add the two or take one from the other.
        food gives the days they logged food, their average calories and protein a day over those days, and
        their daily calorie target.
        recovery gives their average sleep a night in minutes, resting heart rate and heart-rate variability
        (hrv_ms), each over the days that recorded it, and each with its usual: the same average over the 28
        days before the week's Monday, null when fewer than 7 of those days recorded it.
        A null is a figure not recorded, never zero.
    """.trimIndent()

    /** D112: how the ledgers are used — on every path and in the letter, word for word. */
    internal val WEEK_RULES = """
        How to use a week's ledger:
        - Both parts, every time. Judge planned practice as a commitment: name a planned session that was
          not done as not done, and never excuse it with other activity. Recognise what was done beyond the
          plan specifically, with its figures (for example "three other walks, 90 minutes, 40 of them
          brisk"), never with a phrase like "keep it up".
        - Lead with what matters most this week, whichever part it is in. A plan that asks for less than
          they already do is itself worth saying, and is a reason to suggest more.
        - Recovery shapes intensity. Sleep below its usual, or a resting heart rate above its usual,
          is a reason for an easier session or a rest day; say so. Do not diagnose.
        - Food is context. You may note eating well below or above the target alongside the training load.
          Never prescribe a diet, a food or an amount.
        - No figure the app did not count. Every figure you state about their record comes from what you
          are given; you may compare two of them, but never estimate one.
    """.trimIndent()
```

  (Check by eye that each fragment in the test's `RULES` sits on one line of the text above — they do as
  written; if you rewrap, keep them so. Mind the existing "no gender" test: no he/she/his/her.)

  3. Change `instructions(path)` to:

```kotlin
    fun instructions(path: TrainerPath): String = COMMON + "\n\n" + SIX_WEEKS + "\n\n" + LEDGER + "\n\n" + WEEK_RULES + "\n\n" + when (path) {
        TrainerPath.FEEDBACK -> VOICE_NOTE + "\n\n" + FEEDBACK
        TrainerPath.PLAN -> VOICE + "\n\n" + PLAN
        TrainerPath.EVALUATE -> VOICE + "\n\n" + EVALUATE
        TrainerPath.ADJUST -> VOICE + "\n\n" + ADJUST
    }
```

  and extend its KDoc's first sentence: "the shared part, the six ledgers and D112's rules, the voice
  paragraph and the path's own".

- [ ] **Step 4: Run to see it pass.** Same command as Step 2, then all AI tests (the never-sent test now reads
  the new text too):

```bash
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.*" --tests "com.metaself.app.data.trainer.*" > /tmp/ms-ledger-4b.log 2>&1; echo "exit $?"
```

  Expected: `exit 0`. If the never-sent test fails on a word of the new text, rephrase the text (as that
  test's KDoc says), never the test.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/data/ai/TrainerPrompt.kt app/src/test/java/com/metaself/app/data/ai/TrainerPromptTest.kt
git commit -m "$(cat <<'EOF'
feat(trainer): every path is told what a week's ledger holds and how to use it (D112)

Every path's instructions now say what the six ledgers hold (practice,
beyond the plan with brisk minutes and attempt marks, everyday movement
never split, food with the target, recovery with its usuals, a null is
not zero) and carry D112's five rules: both parts every time, lead with
what matters most, recovery shapes intensity, food is context, no figure
the app did not count. LEDGER and WEEK_RULES are shared with the letter.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: The weekly letter carries its week's ledger and the rules

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/letter/WeeklyLetter.kt`
- Modify: `app/src/main/java/com/metaself/app/data/ai/LetterPrompt.kt`
- Modify: `app/src/main/java/com/metaself/app/data/letter/WriteWeeklyLetter.kt`
- Test: `app/src/test/java/com/metaself/app/data/ai/LetterPromptTest.kt`
- Test: `app/src/test/java/com/metaself/app/data/letter/WriteWeeklyLetterTest.kt`
- Test: `app/src/test/java/com/metaself/app/data/ai/OpenAiLetterWriterTest.kt` (fixture only)

- [ ] **Step 0: OpenAiLetterWriterTest's fixture.** It builds the only other `LetterRequest`. Add imports
  `com.metaself.app.domain.trainer.BeyondPlan`, `com.metaself.app.domain.trainer.FoodContext`,
  `com.metaself.app.domain.trainer.Recovery`, `com.metaself.app.domain.trainer.WeekLedger`, and in its
  companion's `REQUEST` add after the `figures = ...,` line:

```kotlin
            ledger = WeekLedger(
                monday = MONDAY, current = true, practice = null, beyond = BeyondPlan(emptyList(), null, null),
                food = FoodContext(0, null, null, 2_100), recovery = Recovery(null, null, null, null, null, null),
            ),
```

- [ ] **Step 1: LetterPromptTest — the fixture.** Add imports:

```kotlin
import com.metaself.app.domain.trainer.AttemptFacts
import com.metaself.app.domain.trainer.BeyondPlan
import com.metaself.app.domain.trainer.FoodContext
import com.metaself.app.domain.trainer.KindLedger
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PracticeLedger
import com.metaself.app.domain.trainer.Recovery
import com.metaself.app.domain.trainer.SessionOutcome
import com.metaself.app.domain.trainer.WeekLedger
```

  Before `request()` add, and pass `ledger = ledger,` in `request()` right after `figures = ...`:

```kotlin
    /** The week's ledger, every part filled so every key path shows. Invented figures. */
    private val ledger = WeekLedger(
        monday = monday, current = true,
        practice = PracticeLedger(
            1, 2, listOf(SessionOutcome(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk"), PlannedOutcome.DONE, 40)),
        ),
        beyond = BeyondPlan(listOf(KindLedger(WorkoutKind.WALK, 2, 50, 4_000, 20, listOf(AttemptFacts(WorkoutKind.WALK, 10, 30)))), 8_000, 6_000),
        food = FoodContext(5, 2_000, 100, 2_100),
        recovery = Recovery(420, 400, 60, 58, 45, 40),
    )
```

- [ ] **Step 2: LetterPromptTest — the shape and the rules.**

  1. In `a letter request names its schema and holds exactly D101's parts`, add `"ledger"` to the expected
     `user.keys`.
  2. In `nothing else is sent`: extend its KDoc's last sentence-list with "; the week's ledger (D111): its
     practice, what was beyond the plan, its food as averages with the target, and its recovery as averages
     with their usuals — never a single night or day". Add before `val expected`:

```kotlin
        val ledger = listOf(
            "from", "practice", "practice.week", "practice.of_weeks", "practice.sessions", "practice.sessions[].kind",
            "practice.sessions[].planned_minutes", "practice.sessions[].outcome", "practice.sessions[].minutes_done",
            "beyond_the_plan", "beyond_the_plan.sessions_by_kind", "beyond_the_plan.sessions_by_kind[].kind",
            "beyond_the_plan.sessions_by_kind[].sessions", "beyond_the_plan.sessions_by_kind[].minutes",
            "beyond_the_plan.sessions_by_kind[].distance_m", "beyond_the_plan.sessions_by_kind[].brisk_minutes",
            "beyond_the_plan.sessions_by_kind[].attempts", "beyond_the_plan.sessions_by_kind[].attempts[].minutes",
            "beyond_the_plan.sessions_by_kind[].attempts[].planned_minutes", "beyond_the_plan.everyday",
            "beyond_the_plan.everyday.steps_a_day", "beyond_the_plan.everyday.distance_m_a_day",
            "food", "food.days_logged", "food.kcal_a_day", "food.protein_g_a_day", "food.target_kcal",
            "recovery", "recovery.sleep_minutes_a_night", "recovery.usual_sleep_minutes_a_night",
            "recovery.resting_heart_rate", "recovery.usual_resting_heart_rate", "recovery.hrv_ms", "recovery.usual_hrv_ms",
        )
```

     and in `expected` add `"ledger"` to the first `listOf(...)` and `+ ledger.map { "ledger.$it" }` after
     `session.map { "sessions[].$it" }`. (No `ledger.so_far`: the letter's week is whole.)
  3. In `the request has room for exactly what D101 lists`, add `"ledger"` to `LetterRequest`'s fields.
  4. Add after `the tone rules are in the instructions`:

```kotlin
    /** D112: the letter is told what its ledger holds, and the trainer's five rules, word for word. */
    @Test
    fun `the ledger and the rules for using it are in the instructions`() {
        val system = system(body())

        assertThat(system).contains("ledger is this week's ledger.")
        assertThat(system).contains(TrainerPrompt.LEDGER)
        assertThat(system).contains(TrainerPrompt.WEEK_RULES)
        listOf(
            "Both parts, every time.", "never excuse it with other activity", "Recovery shapes intensity.",
            "Food is context.", "Never prescribe a diet, a food or an amount.", "No figure the app did not count.",
        ).forEach { assertThat(system).contains(it) }
    }
```

- [ ] **Step 3: WriteWeeklyLetterTest — the letter's ledger.** Add imports:

```kotlin
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.trainer.AttemptFacts
import com.metaself.app.domain.trainer.FoodContext
import com.metaself.app.domain.trainer.KindLedger
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PracticeLedger
import com.metaself.app.domain.trainer.SessionOutcome
```

  Add after `a candidate the owner confirmed counts as done`:

```kotlin
    /**
     * D110, D111: the letter carries its week's ledger. The plan's second week holds two 40-minute walks:
     * one is done; a 25-minute walk, never answered, is a candidate — beyond the plan, marked as an attempt.
     * Seven nights of 400 minutes before the week; two in it, of 400 and 440. Invented.
     */
    @Test
    fun `the request carries the week's ledger`() = runTest {
        val planned = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Invented walk")
        val plan = WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", listOf(planned, planned)) }, "Invented.")
        programmes.rows.value = listOf(
            Programme(1, 0, ProgrammeAsk(2, 2), null, plan, "a-model", LETTER_MONDAY - 7, ProgrammeStatus.RUNNING),
        )
        record.workouts.value = listOf(walk(1, LETTER_MONDAY), walk(2, LETTER_MONDAY + 2, minutes = 25))
        record.days.value = (1L..7L).map { HealthDay(LETTER_MONDAY - it, sleepMinutes = 400) } +
            listOf(HealthDay(LETTER_MONDAY, sleepMinutes = 400), HealthDay(LETTER_MONDAY + 1, sleepMinutes = 440))
        someFood()

        job()(LETTER_MONDAY)

        val ledger = writer.asked.single().ledger
        assertThat(ledger.monday).isEqualTo(LETTER_MONDAY)
        assertThat(ledger.practice).isEqualTo(
            PracticeLedger(2, 2, listOf(SessionOutcome(planned, PlannedOutcome.DONE, 40), SessionOutcome(planned, PlannedOutcome.NOT_DONE, null))),
        )
        assertThat(ledger.beyond.kinds).containsExactly(
            KindLedger(WorkoutKind.WALK, 1, 25, 3_000, null, listOf(AttemptFacts(WorkoutKind.WALK, 25, 40))),
        )
        assertThat(ledger.food).isEqualTo(FoodContext(1, 2_000, 100, CurrentTarget.of(aProfile(), null, 2026, 0).kcal))
        assertThat(ledger.recovery.sleepMinutes).isEqualTo(420)
        assertThat(ledger.recovery.usualSleepMinutes).isEqualTo(400)
    }
```

- [ ] **Step 4: Run to see it fail.**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.ai.LetterPromptTest" --tests "com.metaself.app.data.letter.WriteWeeklyLetterTest" > /tmp/ms-ledger-5.log 2>&1; echo "exit $?"
```

  Expected: non-zero exit — `LetterRequest` has no `ledger`.

- [ ] **Step 5: LetterRequest.** In `WeeklyLetter.kt` add `import com.metaself.app.domain.trainer.WeekLedger`;
  change the KDoc's first sentence to "Everything one letter request holds (D101, amended by D111) — built fresh
  from the record. There is deliberately no field for a meal, a food, an amount, a time of eating, a single
  weigh-in, a single night's sleep or a single day's heart rate, or the owner's words; `LetterPromptTest` fails
  if one is added." Add `@property ledger the week's ledger (D110), counted to its Sunday.` and the field:

```kotlin
data class LetterRequest(
    val figures: LetterFigures,
    val ledger: WeekLedger,
    val sessions: List<SessionFacts>,
```

- [ ] **Step 6: LetterPrompt.** In `LetterPrompt.kt`:

  1. In the object's KDoc, after "a count of days logged", add "; sleep, resting heart rate and HRV only as the
     week's averages beside their usuals (D111)".
  2. In `INSTRUCTIONS`' first paragraph change `today's daily calorie target, and the "for next week" line of
     last week's letter. A null is a` to `today's daily calorie target, the "for next week" line of last week's
     letter, and this week's ledger (below). A null is a` — rewrap the remaining line as needed.
  3. Split `INSTRUCTIONS` before its last paragraph, so it reads:

```kotlin
    private val INSTRUCTIONS = """
        ...the first two paragraphs, unchanged but for the edit above...
    """.trimIndent() + "\n\n" + "ledger is this week's ledger." + "\n\n" + TrainerPrompt.LEDGER + "\n\n" +
        TrainerPrompt.WEEK_RULES + "\n\n" + """
        Reply with: headline (one line, about the week), effort (what he put in), progress (where it is
        taking him, against his earlier weeks), look_at (one thing, as above), next_week (one concrete
        step), and close (as above).
    """.trimIndent()
```

  4. In `user(request)`, after `put("week", week(request.figures.week))`, add:

```kotlin
        put("ledger", TrainerPrompt.ledgerJson(request.ledger, withSoFar = false))
```

- [ ] **Step 7: WriteWeeklyLetter.** In `WriteWeeklyLetter.kt`:

  1. Add imports `com.metaself.app.domain.day.DayTotals`, `com.metaself.app.domain.movement.HealthDay`,
     `com.metaself.app.domain.trainer.CountedPlan`, `com.metaself.app.domain.trainer.WeekLedgers`. `Programme`
     and `PlanCounting` stay imported (`counting` takes and returns them).
  2. Replace the `Counted` class and `count(...)` with:

```kotlin
    /** The record over [weekMonday]'s week and the four before it, read once. */
    private class Counted(
        val workouts: List<Workout>,
        val days: List<HealthDay>,
        val food: Map<Long, DayTotals>,
        val kept: List<CountedPlan>,
        val week: (Long) -> WeekFigures,
    )

    private suspend fun count(weekMonday: Long): Counted {
        val sunday = weekMonday + 6
        // The four earlier weeks (D100) and the ledger's 28 days of usuals (D110) — the same span today.
        val first = minOf(weekMonday - 7L * LetterFigures.WEEKS_BEFORE, weekMonday - WeekLedgers.USUAL_DAYS)
        val foodByDay = food.byDay(first, sunday)
        val trend = WeightTrend.of(weights.readings.first())
        val workouts = record.observeWorkouts(first, sunday).first()
        val days = record.observeDays(first, sunday).first()
        val allReviews = reviews.observeReviews().first()
        val all = programmes.all()
        val kept = all.filter { it.status != ProgrammeStatus.OFFERED && it.startEpochDay != null }
            .map { CountedPlan(it, counting(it, all)) }
        return Counted(workouts, days, foodByDay, kept) { monday ->
            WeekFigures.of(monday, foodByDay, trend, workouts, allReviews, days, planWeek(kept, workouts, monday))
        }
    }
```

  3. Delete `runningIn(...)` and its KDoc. Replace `planWeek(...)` with:

```kotlin
    /**
     * Its week's planned and done; done counts only ticked sessions — full, or confirmed short (D105). The
     * plan is [CountedPlan.covering]'s: design question 13's latest-started, and of two with one start, the
     * one made later (D110).
     */
    private fun planWeek(kept: List<CountedPlan>, workouts: List<Workout>, monday: Long): PlanWeekFigures? {
        val plan = CountedPlan.covering(kept, monday) ?: return null
        val p = plan.programme
        val index = ProgrammeCalendar.weekIndex(plan.start, monday)
        val week = PlanProgress.of(p.plan, plan.start, workouts, Programmes.countedUntil(p, monday + 6), plan.counting).weeks.getOrNull(index)
            ?: return null
        return PlanWeekFigures(p.plan.title, week.planned, week.done, ProgrammeCalendar.ended(plan.start, p.ask.weeks, monday + 6))
    }
```

  4. In `invoke(...)`, replace `val running = runningIn(counted.kept, weekMonday)?.first` with
     `val running = CountedPlan.covering(counted.kept, weekMonday)?.programme`, and in the `LetterRequest(...)`
     add after `figures = figures,`:

```kotlin
            // D110, D111: the week's ledger, counted to its Sunday.
            ledger = WeekLedgers.of(weekMonday, sunday, workouts, counted.days, counted.food, target, counted.kept),
```

- [ ] **Step 8: Run to see it pass.** Same command as Step 4, then the letter and AI suites:

```bash
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.letter.*" --tests "com.metaself.app.data.ai.*" --tests "com.metaself.app.domain.letter.*" --tests "com.metaself.app.ui.screen.letter.*" > /tmp/ms-ledger-5b.log 2>&1; echo "exit $?"
```

  Expected: `exit 0` both times. (The three `LetterRequest(...)` sites are `WriteWeeklyLetter`, `LetterPromptTest`
  and `OpenAiLetterWriterTest`; if the compiler names another, give it a `WeekLedger` built as in Step 0.)

- [ ] **Step 9: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/letter/WeeklyLetter.kt app/src/main/java/com/metaself/app/data/ai/LetterPrompt.kt app/src/main/java/com/metaself/app/data/letter/WriteWeeklyLetter.kt app/src/test/java/com/metaself/app/data/ai/LetterPromptTest.kt app/src/test/java/com/metaself/app/data/letter/WriteWeeklyLetterTest.kt app/src/test/java/com/metaself/app/data/ai/OpenAiLetterWriterTest.kt
git commit -m "$(cat <<'EOF'
feat(letter): the weekly letter carries its week's ledger and the trainer's rules (D111, D112)

LetterRequest gains the week's ledger, counted to its Sunday and sent
under "ledger" without so_far; the instructions gain LEDGER and
WEEK_RULES word for word. The letter's figures and box are unchanged.

Which kept plan covers a week is now CountedPlan.covering, shared with
the trainer: of two versions with one start (an adjustment week), the one
made later. Before, the list order (ORDER BY id) picked the older one,
so the letter's plan row could disagree with the Trainer card.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: The privacy lines name the additions

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/trainer/TrainerWording.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/letter/LetterWording.kt`
- Test: `app/src/test/java/com/metaself/app/ui/trainer/TrainerWordingTest.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/trainer/PlanSessionScreenRenderTest.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/trainer/ReviewSessionScreenRenderTest.kt`
- Test: `app/src/test/java/com/metaself/app/ui/letter/LetterWordingTest.kt`

- [ ] **Step 1: Write the failing tests.** In each of the four exact privacy strings — `TrainerWordingTest`
  (`privacyPlan(30)` and `privacyReview(30)`), `PlanSessionScreenRenderTest` (`ask the trainer waits for all
  four rows, and says what it sends`) and `ReviewSessionScreenRenderTest` (the `"Sends to OpenAI, with your key:
  this session and your words; "` string) — replace the line

```kotlin
                "your note about yourself; your sessions of the last six weeks, with your words on them; weekly totals; " +
```

  with

```kotlin
                "your note about yourself; your sessions of the last six weeks, with your words on them; weekly totals; " +
                "weekly averages of your sleep, resting heart rate, heart-rate variability and food, with your calorie target; " +
```

  In `TrainerWordingTest`, add after `the weekly plan's privacy lines name what they add`:

```kotlin
    /** D111: every ask's line names the weekly averages that may now leave the phone. */
    @Test
    fun `every ask's privacy line names the weekly averages D111 adds`() {
        listOf(
            TrainerWording.privacyPlan(30), TrainerWording.privacyReview(30),
            TrainerWording.privacyEvaluate(30), TrainerWording.privacyAdjust(30),
        ).forEach {
            assertThat(it).contains(
                "; weekly averages of your sleep, resting heart rate, heart-rate variability and food, with your calorie target; ",
            )
        }
    }
```

  In `LetterWordingTest`'s `the list, Write it now and what it sends`, replace the expected `privacyLetter(20)` with:

```kotlin
        assertThat(LetterWording.privacyLetter(20)).isEqualTo(
            "Sends to the AI provider, with your key: your week's food as averages a day, your weight trend, your " +
                "sessions and steps, your week's averages of sleep, resting heart rate and heart-rate variability with " +
                "their usual levels, your weekly plan's title and this week's planned sessions, your note about " +
                "yourself, your goal, age, sex and height, and last week's \"for next week\" line. Never a meal or " +
                "a food. One of today's 20 AI requests.",
        )
```

- [ ] **Step 2: Run to see it fail.**

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.trainer.TrainerWordingTest" --tests "com.metaself.app.ui.letter.LetterWordingTest" --tests "com.metaself.app.ui.screen.trainer.PlanSessionScreenRenderTest" --tests "com.metaself.app.ui.screen.trainer.ReviewSessionScreenRenderTest" > /tmp/ms-ledger-6.log 2>&1; echo "exit $?"
```

  Expected: non-zero exit; the string comparisons fail.

- [ ] **Step 3: The wording.** In `TrainerWording.kt`, `privacy(...)` becomes:

```kotlin
    private fun privacy(first: String, ceiling: Int, extra: String? = null): String =
        "Sends to OpenAI, with your key: $first; your note about yourself; " +
            "your sessions of the last six weeks, with your words on them; weekly totals; " +
            "weekly averages of your sleep, resting heart rate, heart-rate variability and food, with your calorie target; " +
            "a line for each month of the year before; your weight trend and goal rate; your age, sex and height; " +
            (extra?.let { "$it; " } ?: "") +
            "and the trainer's last three feedbacks. One of today's $ceiling AI requests."
```

  and add to `privacyPlan`'s KDoc: "D111: and each week's averages of sleep, resting heart rate, HRV and food,
  with the calorie target." In `LetterWording.kt`, `privacyLetter` becomes:

```kotlin
    fun privacyLetter(ceiling: Int): String =
        "Sends to the AI provider, with your key: your week's food as averages a day, your weight trend, your " +
            "sessions and steps, your week's averages of sleep, resting heart rate and heart-rate variability with " +
            "their usual levels, your weekly plan's title and this week's planned sessions, your note about " +
            "yourself, your goal, age, sex and height, and last week's \"for next week\" line. Never a meal or " +
            "a food. One of today's $ceiling AI requests."
```

- [ ] **Step 4: Run to see it pass.** Same command as Step 2, then the screens that show these lines:

```bash
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.trainer.*" --tests "com.metaself.app.ui.letter.*" --tests "com.metaself.app.ui.screen.trainer.*" --tests "com.metaself.app.ui.screen.letter.*" > /tmp/ms-ledger-6b.log 2>&1; echo "exit $?"
```

  Expected: `exit 0` both times.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/trainer/TrainerWording.kt app/src/main/java/com/metaself/app/ui/letter/LetterWording.kt app/src/test/java/com/metaself/app/ui/trainer/TrainerWordingTest.kt app/src/test/java/com/metaself/app/ui/screen/trainer/PlanSessionScreenRenderTest.kt app/src/test/java/com/metaself/app/ui/screen/trainer/ReviewSessionScreenRenderTest.kt app/src/test/java/com/metaself/app/ui/letter/LetterWordingTest.kt
git commit -m "$(cat <<'EOF'
feat(trainer): the line under each ask button names the weekly averages (D111)

The four trainer ask buttons' line now names "weekly averages of your
sleep, resting heart rate, heart-rate variability and food, with your
calorie target"; the letter's line under Write it now names the week's
sleep, resting heart rate and HRV averages with their usual levels.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: The privacy page, 0.74.0, the suite and the build

**Files:**
- Modify: `privacy.html`
- Modify: `app/build.gradle.kts:24,35`

- [ ] **Step 1: privacy.html.** Change `<p class="updated">Last updated 29 September 2026</p>` to the day of
  the change (`10 October 2026` if done today). In the trainer item (`Your training record, to OpenAI, when you
  ask the trainer`):

  - replace `weekly totals for six\n  weeks;` with

```html
  for each of six weeks, what you did in and beyond your weekly plan (each planned session's outcome; other
  sessions by kind with their minutes, distance and minutes in brisk heart-rate zones; steps and distance a
  day), your food as the days you logged and the average calories and protein a day, with your calorie
  target, and your average sleep a night, resting heart rate and heart-rate variability, each beside its
  usual over the four weeks before;
```

  - replace `<strong>Never sent:</strong> meals or anything you ate, sleep, individual\n  readings,` with

```html
<strong>Never sent:</strong> a meal, a food's name, an amount or a time of eating, a single night's
  sleep or a single day's heart rate, sleep stages, oxygen, breathing rate, individual
  readings,
```

  In the letter item (`Your week in figures, to OpenAI, for the weekly letter`):

  - after `and your steps and active calories a day;` insert ` what you did in and beyond the weekly plan, and
    your average sleep a night, resting heart rate and heart-rate variability, each beside its usual over the
    four weeks before;`
  - replace `a meal or anything about one, sleep,\n  individual readings,` with `a meal or anything about one, a
    single night's sleep or a single day's heart rate, sleep stages, oxygen, breathing rate,\n  individual
    readings,`

  Read the two items back whole afterwards: every list still reads as one sentence, and nothing it says is
  sent contradicts its "Never sent".

- [ ] **Step 2: Version.** In `app/build.gradle.kts`: `versionCode = 129` → `130`; `versionName = "0.73.0"` →
  `"0.74.0"`.

- [ ] **Step 3: The suite, lint, the build** — one at a time, `free -m` before each:

```bash
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-ledger-7a.log 2>&1; echo "exit $?"
free -m
~/bin/gradlew-safe :app:lintDebug > /tmp/ms-ledger-7b.log 2>&1; echo "exit $?"
free -m
~/bin/gradlew-safe :app:assembleDebug > /tmp/ms-ledger-7c.log 2>&1; echo "exit $?"
```

  Expected: `exit 0` each. In the suite's results exactly the ten Room classes CLAUDE.md names are skipped,
  nothing else: `grep -l 'skipped="[1-9]' app/build/test-results/testDebugUnitTest/*.xml`.

- [ ] **Step 4: Commit.**

```bash
git add privacy.html app/build.gradle.kts
git commit -m "$(cat <<'EOF'
0.74.0: the trainer sees the whole week (D110-D112)

The privacy page lists each week's ledger in the trainer's request and
the letter's, and says a single night's sleep, a single day's heart rate,
sleep stages, oxygen and breathing rate are never sent.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 5: Phone checks to hand the owner** (the release build is the controller's, `~/bin/ms-release`):
  under each ask button the line names the weekly averages; under *Write it now* the letter's line does;
  feedback after a week with more walks than planned recognises the extra walks with their figures and still
  names any planned session not done; a plan request after a short night says to go easier (only if the
  record has a usual); the workbench's *Save the app's instructions* file now holds the ledger paragraph and
  the five rules.

---

## Self-review against the spec

- **D110 — two ledgers, exactly one each**: Task 1 (`every counted session lands in exactly one ledger`; the
  partition is "is some tick's `by`" vs everything else). Practice only for a kept plan's week, counted from
  D105's moment: `covering` + `PlanProgress.of` with the chain's `PlanCounting` (tests: no plan, plan from next
  Monday, session before the keep, stopped plan, adjusted tie). Outcomes exactly as `PlanProgress`:
  `outcome().sessions`. Not done stays not done: its own test.
- **Beyond the plan**: grouped by kind with count, minutes, distance where recorded, brisk minutes zones 2–5
  over sessions with zones, null for a kind with none (test); attempts marked (attempt + candidate test);
  everyday steps and distance a day over recording days, never split (test; LEDGER says never add or
  subtract).
- **Context**: food days logged, kcal and protein over logged days, target (Task 1 test, Task 2 test, Task 3
  test reads the real target); recovery averages over recording days with 28-day usuals, none under 7 (two
  tests). The phone never says worse or better: no comparison is computed, only the two figures.
- **Absent, never zero; pure; counted afresh**: nullable figures throughout; `WeekLedgers` is a pure object
  reading only its arguments.
- **D111 — six ledgers on every path**: `TrainerRequest.of` (Task 2) is the only builder, used by every ask
  and the workbench (Task 3); newest first (Task 2, Task 3 tests). The 42 days, months and rhythm unchanged
  (rhythm recounted with D87's filter; its existing test still passes).
- **Letter carries its week's ledger; figures and box unchanged**: Task 5.
- **What may leave / never sent**: field-shape tests (Task 2 Step 3; Task 5) and the "never sent" body test
  (Task 2 Step 5), updated for D111's additions and no further.
- **Privacy line**: Task 6 (all four ask lines, and the letter's); `privacy.html` Task 7.
- **D112 — five rules on every path and in the letter**: Task 4 (each path), Task 5 (letter), with
  fragments pinned.
- **Not built**: no screen change beyond the privacy lines; no conversation, goals, reminders, observed
  maximum, sets and reps; no distance split.
- **Types**: `CountedPlan`, `PracticeLedger`, `KindLedger`, `BeyondPlan`, `FoodContext`, `Recovery`,
  `WeekLedger`, `WeekLedgers.of(monday, today, workouts, days, food, targetKcal, plans)`,
  `TrainerRequest.firstLedgerDay`, `TrainerPrompt.ledgerJson(ledger, withSoFar)`, `TrainerPrompt.LEDGER`,
  `TrainerPrompt.WEEK_RULES` — named the same in every task.
