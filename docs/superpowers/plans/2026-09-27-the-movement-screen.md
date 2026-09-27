# The Movement screen — Implementation Plan (D73–D75)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D73–D75. A screen called **Movement**, reached from the day screen's step line and from the
top-right menu, that shows this week from the stored health record and the meal log: the week's
distance as the one large figure, the average movement calories beneath it, the workouts' count and
time, one short row per day (today first and open, one open at a time) and a last-four-weeks line.

**Architecture:**

```
domain/movement/Workout.kt              + avgHeartRate (defaulted)                     compiles only
domain/movement/MovementWeek.kt         pure: days + workouts + meals → the week       JUnit 5
ui/movement/MovementWeekWording.kt      pure: every sentence the screen says           JUnit 5
data/health/MovementRecord.kt           port + Room adapter + entity → domain mapping  JUnit 5 (mapping)
di/DataModule.kt                        binds MovementRecord
ui/screen/movement/MovementUiState.kt   the screen's state
ui/screen/movement/MovementViewModel.kt combines the flows; which day is open          JUnit 5 + coroutines-test
ui/screen/movement/MovementScreen.kt    the page                                       Robolectric (JUnit 4)
ui/day/StepBar.kt, ui/screen/day/*      the step line becomes a door; menu entry       Robolectric (JUnit 4)
ui/nav/MetaSelfNavHost.kt               the route                                      JUnit 5
```

The view model reads the health record through a two-method port (`MovementRecord`) rather than the
DAOs, so its tests use a small fake and run on this machine; the Room adapter is two `map`s over DAO
queries that already exist and are already tested in CI (`HealthDayDao.observeBetween`,
`WorkoutDao.observeBetween`). **No new DAO query and no schema change:** the previous four weeks'
distances are summed from the same `observeBetween` read, widened to five weeks. Eaten comes from
`MealRepository.observeDay` for each day of the week and `DayTotals.of`, the same two pieces the day
screen uses, so the Movement screen and the day screen cannot disagree about what a day held.

**Decision:** the owner's, 2026-09-27 — D73, D74, D75 in
`docs/superpowers/specs/2026-09-27-movement-screen-design.md`, amending §4.2 of
`docs/superpowers/specs/2026-09-25-physical-activity-module-design.md`.

**Tech Stack:** Kotlin, Compose (Material 3), Hilt, Room (read only), kotlinx-coroutines-test, JUnit 5 +
Truth, Robolectric (JUnit 4) for render tests. No new dependency.

**Red lines (stop and report if crossed):**

- **No schema change.** `app/schemas` untouched; no new entity, column, DAO query or migration.
- **No figure is invented (D4, D69).** A missing figure is left out, never written as zero; a day with
  no health row is "nothing recorded"; the headline's distance and average are absent, not "0.0 km",
  when no day has them. No allowance per day (D73), no net-calories figure and no total burn (D63).
- **No "Log a workout" button** (D75). No other control that does nothing.
- **Nothing on this screen reads Health Connect.** Only Room and the meal log.
- **D8:** a read that fails is logged (kind `"movement"`) and said on the screen; nothing throws upwards.
- **Never `git add -A`; never bare `./gradlew`; never pipe a build whose result is reported.**
  Anonymisation: every figure in a test is invented, round, and says so; origin `com.example.band`.

## Design questions the spec does not answer — settled here

The executor follows these; each is repeated in the PR body so the owner can overturn it.

1. **Which rows.** "This week's days, Monday to Sunday, today first" is read as: **today, then each
   earlier day back to Monday** — one row on a Monday, seven on a Sunday. Days after today are not
   shown (they cannot hold anything), and last week's days are not borrowed to make seven.
2. **A closed day with none of the three parts** (movement calories, workouts, sleep) but with other
   figures (steps only, say, or only food): the closed row shows **the first line the open row would
   show** ("9,000 steps · phone and band"). "nothing recorded" is said only when the open row would be
   empty too — otherwise the closed row would say "nothing recorded" over a day that has steps.
3. **An open day keeps its summary.** Every day row shows its heading and its one-line summary, open
   or closed; an open day adds its detail lines beneath the summary. A detail line that says exactly
   what the summary says is not repeated, so a day with nothing but steps, or nothing at all, opens
   onto nothing more. (`summaryLine` and `detailLines`, named for what they hold: the summary stays on
   screen with the detail added beneath it, never replaced.)
4. **Every summary figure carries its source**, steps included: "9,000 steps · phone and band",
   "9,000 steps · you set this". TOTAL → "phone and band", CORRECTED → "you set this"; any other
   source (READ, COMPUTED, unknown) has no suffix, since movement calories and steps are only ever
   TOTAL or CORRECTED today (`DaySummary.kt`).
5. **Pace is shown whenever a workout has a distance** ("· 5:10 /km"), for every kind, as the spec's
   line is written; it is not restricted to running.
6. **The last-four-weeks line is left out entirely when all four weeks have no distance**, rather than
   printing "— · — · — · — km" on a new install.
7. **Body figures with a decimal are rounded to whole numbers** on screen: "HRV 42 ms", "oxygen 97%",
   "breathing 14/min" — the spec's own examples are whole.
8. **The step line is a door on every day the pager shows**, past days included; it always opens the
   Movement screen at this week (browsing earlier weeks is not built, per the spec).
9. **The day's distance is not shown in a day row.** The spec lists movement calories and steps for an
   open day, not distance; it is only summed into the headline and the four-week line.
10. **Dates use `Locale.US`, not `Locale.UK`:** on Java 17 `EEE d MMM` in `Locale.UK` gives
    "Thu 3 Sept" (measured with `jshell` on this box, 2026-09-27); the spec writes "Sep".
11. **Placement:** `ui/screen/movement/` and `domain/movement/MovementWeek.kt`, not the activity spec's
    `ui/screen/activity/` and `WeekOfActivity` — the screen is now called Movement (D73–D75).

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` and `~/bin/ms-release` share one lock; a
finished build's daemon lingers up to ten minutes. **Before every Gradle command run `free -m`;** under
about 4000 MB available (6000 MB before `ms-release`), wait and check again. Never `gradlew --stop`.
One build at a time.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-health3.log 2>&1; echo "exit $?"
```

SQLite classes skip locally (ten, listed in `CLAUDE.md`); report them as skipped.

(For this plan the log file is `/tmp/ms-movement.log`. The ten skipping classes are the eight
`CLAUDE.md` names plus `HealthRecordDaoTest` and `HealthRecordStoreTest`; `CLAUDE.md`'s list is stale.)

---

### Task 1: The week, as numbers

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/movement/Workout.kt` (the `Workout` class)
- Create: `app/src/main/java/com/metaself/app/domain/movement/MovementWeek.kt`
- Test: `app/src/test/java/com/metaself/app/domain/movement/MovementWeekTest.kt`

- [ ] **Step 0: Branch.** Work on `the-movement-screen` (it exists; this plan is committed on it).
  `git status` must show only this plan's commit ahead of `main` and the untracked `tools/__pycache__/`.

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import org.junit.jupiter.api.Test

/**
 * The Movement screen's week (D73, D74), as numbers. Every figure here is invented, and round so the
 * arithmetic can be checked by eye.
 *
 * TEST_EPOCH_DAY is Thursday 3 September 2026, so its week began on Monday 31 August, epoch day
 * 20,696; the four weeks before it began on 24, 17, 10 and 3 August (20,689 / 20,682 / 20,675 /
 * 20,668).
 */
class MovementWeekTest {

    private val today = TEST_EPOCH_DAY
    private val monday = 20_696L

    @Test
    fun `the week runs from Monday to today, today first`() {
        val week = MovementWeek.of(today, days = emptyList(), workouts = emptyList(), mealsByDay = emptyMap())

        assertThat(week.monday).isEqualTo(monday)
        assertThat(week.days.map { it.epochDay })
            .containsExactly(20_699L, 20_698L, 20_697L, 20_696L).inOrder()
    }

    @Test
    fun `on a Monday the week is that one day`() {
        val week = MovementWeek.of(monday, emptyList(), emptyList(), emptyMap())

        assertThat(week.days.map { it.epochDay }).containsExactly(monday)
    }

    @Test
    fun `Sunday closes the week and Monday starts the next`() {
        assertThat(MovementWeek.mondayOf(20_702L)).isEqualTo(monday) // Sunday 6 September
        assertThat(MovementWeek.mondayOf(20_703L)).isEqualTo(20_703L) // Monday 7 September
    }

    @Test
    fun `the distance is the sum of the days that have one`() {
        val days = listOf(day(20_699, distanceM = 5_000), day(20_698), day(20_696, distanceM = 7_500))

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap()).distanceM).isEqualTo(12_500)
    }

    @Test
    fun `with no distance on any day there is no distance, not zero`() {
        val days = listOf(day(20_699, activeKcal = 300))

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap()).distanceM).isNull()
    }

    @Test
    fun `a day before Monday does not count towards this week`() {
        val days = listOf(
            day(20_695, distanceM = 9_000, activeKcal = 900),
            day(20_699, distanceM = 1_000, activeKcal = 100),
        )

        val week = MovementWeek.of(today, days, emptyList(), emptyMap())

        assertThat(week.distanceM).isEqualTo(1_000)
        assertThat(week.averageActiveKcal).isEqualTo(100)
        assertThat(week.days.map { it.epochDay }).doesNotContain(20_695L)
    }

    /** D74: days without a figure are not counted as zero. */
    @Test
    fun `the average movement counts only the days that have a figure`() {
        val days = listOf(day(20_699, activeKcal = 300), day(20_698), day(20_697, activeKcal = 500))

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap()).averageActiveKcal).isEqualTo(400)
    }

    @Test
    fun `with no movement figure on any day there is no average`() {
        val days = listOf(day(20_699, distanceM = 2_000))

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap()).averageActiveKcal).isNull()
    }

    @Test
    fun `hidden workouts are left out of the count, the time and the day`() {
        val workouts = listOf(
            workout(20_699, minutes = 30),
            workout(20_699, minutes = 45, hidden = true),
            workout(20_697, minutes = 60),
        )

        val week = MovementWeek.of(today, emptyList(), workouts, emptyMap())

        assertThat(week.workoutCount).isEqualTo(2)
        assertThat(week.workoutMinutes).isEqualTo(90)
        assertThat(week.days.first().workouts.map { it.durationMinutes }).containsExactly(30)
    }

    @Test
    fun `a workout before Monday is not this week's`() {
        val week = MovementWeek.of(today, emptyList(), listOf(workout(20_695, minutes = 60)), emptyMap())

        assertThat(week.workoutCount).isEqualTo(0)
        assertThat(week.workoutMinutes).isEqualTo(0)
    }

    @Test
    fun `a day's workouts are in the order they started`() {
        val workouts = listOf(
            workout(20_699, minutes = 20, startedAtMillis = 2_000),
            workout(20_699, minutes = 40, startedAtMillis = 1_000),
        )

        val week = MovementWeek.of(today, emptyList(), workouts, emptyMap())

        assertThat(week.days.first().workouts.map { it.durationMinutes }).containsExactly(40, 20).inOrder()
    }

    @Test
    fun `eaten is the day's logged total`() {
        val meals = mapOf(20_699L to listOf(aMeal(items = listOf(anItem(kcal = 600), anItem(kcal = 400)))))

        val week = MovementWeek.of(today, emptyList(), emptyList(), meals)

        assertThat(week.days.first().eatenKcal).isEqualTo(1_000)
        assertThat(week.days[1].eatenKcal).isNull()
    }

    @Test
    fun `a day whose meals hold nothing has no eaten figure, not zero`() {
        val meals = mapOf(20_699L to listOf(aMeal(items = emptyList())))

        assertThat(MovementWeek.of(today, emptyList(), emptyList(), meals).days.first().eatenKcal).isNull()
    }

    @Test
    fun `a day with no health row still has its row, with nothing in it`() {
        val week = MovementWeek.of(today, listOf(day(20_699, activeKcal = 300)), emptyList(), emptyMap())

        assertThat(week.days.first().health?.activeKcal).isEqualTo(300)
        assertThat(week.days[1].health).isNull()
        assertThat(week.days[1].workouts).isEmpty()
    }

    @Test
    fun `the last four weeks are newest first, with no figure for a week with no distance`() {
        val days = listOf(
            day(20_690, distanceM = 10_000), // the week of 24 August
            day(20_676, distanceM = 4_000), // the week of 10 August …
            day(20_677, distanceM = 6_000), // … twice
            day(20_668, distanceM = 2_000), // the week of 3 August
            day(20_661, distanceM = 99_000), // five weeks back: not on the line
            day(20_699, distanceM = 1_000), // this week: the headline, not the line
        )

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap()).previousWeeksM)
            .containsExactly(10_000, null, 10_000, 2_000).inOrder()
    }

    @Test
    fun `a source is read by name, and one this version does not know says so`() {
        assertThat(FigureSource.parse("CORRECTED")).isEqualTo(FigureSource.CORRECTED)
        assertThat(FigureSource.parse("GUESSED")).isEqualTo(FigureSource.UNRECOGNISED)
        assertThat(FigureSource.parse(null)).isNull()
    }

    private fun day(epochDay: Long, distanceM: Int? = null, activeKcal: Int? = null) = HealthDay(
        epochDay = epochDay,
        distanceM = distanceM,
        activeKcal = activeKcal,
        activeKcalSource = activeKcal?.let { FigureSource.TOTAL },
    )

    private fun workout(
        epochDay: Long,
        minutes: Int,
        hidden: Boolean = false,
        startedAtMillis: Long = 0,
    ) = Workout(
        id = 0, epochDay = epochDay, startedAtMillis = startedAtMillis, durationMinutes = minutes,
        kind = WorkoutKind.RUN, title = "Running", distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
        hidden = hidden, note = null,
    )
}
```

- [ ] **Step 2: Run it to see it fail.**

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.movement.MovementWeekTest" > /tmp/ms-movement.log 2>&1; echo "exit $?"
grep -m5 "e: " /tmp/ms-movement.log
```

Expected: `exit 1`; compile errors `Unresolved reference: MovementWeek` / `HealthDay` / `FigureSource`.

- [ ] **Step 3: Give a workout its heart rate.** In `app/src/main/java/com/metaself/app/domain/movement/Workout.kt`
  replace the last property of `Workout`:

```kotlin
    val hidden: Boolean,
    val note: String?,
) {
```

with:

```kotlin
    val hidden: Boolean,
    val note: String?,
    /**
     * The session's average heart rate, worked out by this app from the readings inside it (D70);
     * null when there were none. Defaulted, so every workout built before the Movement screen still is.
     */
    val avgHeartRate: Int? = null,
) {
```

- [ ] **Step 4: Write the week.** Create `app/src/main/java/com/metaself/app/domain/movement/MovementWeek.kt`:

```kotlin
package com.metaself.app.domain.movement

import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.Meal
import kotlin.math.roundToInt

/**
 * Where one figure of a day's health summary came from (D69), read back from its stored name —
 * never an ordinal, for the reason [WorkoutKind] gives.
 */
enum class FigureSource {
    TOTAL, READ, COMPUTED, CORRECTED,

    /** A name this version does not know. Shown with no source at all rather than a guessed one. */
    UNRECOGNISED;

    companion object {
        fun parse(stored: String?): FigureSource? =
            if (stored == null) null else entries.firstOrNull { it.name == stored } ?: UNRECOGNISED
    }
}

/**
 * One day of the stored health record (D65, D69), as the Movement screen reads it.
 *
 * Pure: the Room row maps to this in `data/health/MovementRecord.kt`. Every figure is nullable,
 * because no data is not zero.
 */
data class HealthDay(
    val epochDay: Long,
    val steps: Int? = null,
    val stepsSource: FigureSource? = null,
    val distanceM: Int? = null,
    val activeKcal: Int? = null,
    val activeKcalSource: FigureSource? = null,
    val restingHeartRate: Int? = null,
    val hrvMs: Double? = null,
    val oxygenPct: Double? = null,
    val respiratoryRate: Double? = null,
    val sleepMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val remMinutes: Int? = null,
    val lightMinutes: Int? = null,
)

/**
 * One row of the Movement screen (D73).
 *
 * @property health null when the record has no summary for the day.
 * @property workouts the day's visible workouts, in the order they started.
 * @property eatenKcal what the meals logged that day add up to; null when nothing was logged.
 */
data class MovementDay(
    val epochDay: Long,
    val health: HealthDay?,
    val workouts: List<Workout>,
    val eatenKcal: Int?,
)

/**
 * This week, Monday to today, as the Movement screen shows it (D73, D74).
 *
 * @property distanceM the week's distance so far: the sum of the days' de-duplicated totals (D69);
 *   null when no day this week has one.
 * @property averageActiveKcal the mean movement calories over the days that have a figure, rounded;
 *   days without are not counted as zero. Null when none has one.
 * @property days today first, back to Monday.
 * @property previousWeeksM the four weeks before this one, newest first; null for a week with no
 *   distance on any day.
 */
data class MovementWeek(
    val monday: Long,
    val distanceM: Int?,
    val averageActiveKcal: Int?,
    val workoutCount: Int,
    val workoutMinutes: Int,
    val days: List<MovementDay>,
    val previousWeeksM: List<Int?>,
) {
    companion object {

        const val PREVIOUS_WEEKS = 4

        /**
         * The Monday-based week number `WorkoutDao.observeWeeklyRunning` uses: epoch day 0 was a
         * Thursday, so `(epochDay + 3) / 7` changes on Mondays. Floored, so it stays right before 1970.
         */
        fun weekOf(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7L)

        fun mondayOf(epochDay: Long): Long = weekOf(epochDay) * 7 - 3

        /**
         * @param days the health record's days from four weeks before this Monday to [today]; any
         *   others are ignored.
         * @param workouts this week's workouts, hidden ones included — they are left out here.
         * @param mealsByDay this week's meals, by day.
         */
        fun of(
            today: Long,
            days: List<HealthDay>,
            workouts: List<Workout>,
            mealsByDay: Map<Long, List<Meal>>,
        ): MovementWeek {
            val monday = mondayOf(today)
            val byDay = days.associateBy { it.epochDay }
            val thisWeek = (monday..today).mapNotNull { byDay[it] }
            val visible = workouts
                .filter { !it.hidden && it.epochDay in monday..today }
                .sortedBy { it.startedAtMillis }
            val distances = thisWeek.mapNotNull { it.distanceM }
            val active = thisWeek.mapNotNull { it.activeKcal }
            val week = weekOf(today)

            return MovementWeek(
                monday = monday,
                distanceM = distances.takeIf { it.isNotEmpty() }?.sum(),
                averageActiveKcal = active.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                workoutCount = visible.size,
                workoutMinutes = visible.sumOf { it.durationMinutes },
                days = (today downTo monday).map { day ->
                    MovementDay(
                        epochDay = day,
                        health = byDay[day],
                        workouts = visible.filter { it.epochDay == day },
                        eatenKcal = eaten(mealsByDay[day].orEmpty()),
                    )
                },
                previousWeeksM = (1..PREVIOUS_WEEKS).map { back ->
                    days.filter { weekOf(it.epochDay) == week - back }
                        .mapNotNull { it.distanceM }
                        .takeIf { it.isNotEmpty() }
                        ?.sum()
                },
            )
        }

        /** The day screen's own sum (`DayTotals.of`), so the two screens cannot disagree. */
        private fun eaten(meals: List<Meal>): Int? =
            if (meals.all { it.items.isEmpty() }) null else DayTotals.of(meals).kcal
    }
}
```

- [ ] **Step 5: Run it to see it pass.** Same command as Step 2. Expected: `exit 0`;
  `grep -c "FAILED" /tmp/ms-movement.log` prints `0`.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/movement/Workout.kt \
        app/src/main/java/com/metaself/app/domain/movement/MovementWeek.kt \
        app/src/test/java/com/metaself/app/domain/movement/MovementWeekTest.kt
git commit -m "feat: the Movement screen's week, as numbers (D73, D74)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: What the screen says

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/movement/MovementWeekWording.kt`
- Test: `app/src/test/java/com/metaself/app/ui/movement/MovementWeekWordingTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.metaself.app.ui.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/**
 * Every sentence the Movement screen says (D73, D74). Every figure here is invented — they are the
 * design's own invented examples, so the expected strings can be read against the spec.
 * TEST_EPOCH_DAY is Thursday 3 September 2026.
 */
class MovementWeekWordingTest {

    private val running = workout(title = "Running", minutes = 32, distanceM = 6_200, avgHeartRate = 142)

    private val fullHealth = HealthDay(
        epochDay = TEST_EPOCH_DAY,
        steps = 9_000, stepsSource = FigureSource.TOTAL,
        distanceM = 8_000,
        activeKcal = 410, activeKcalSource = FigureSource.TOTAL,
        restingHeartRate = 58, hrvMs = 42.0, oxygenPct = 97.0, respiratoryRate = 14.0,
        // 80 + 95 + 255 = 430: the stages add up to the night.
        sleepMinutes = 430, deepMinutes = 80, remMinutes = 95, lightMinutes = 255,
    )

    private val fullDay = MovementDay(TEST_EPOCH_DAY, fullHealth, listOf(running), eatenKcal = 1_840)

    private val week = MovementWeek(
        monday = 20_696, distanceM = 42_600, averageActiveKcal = 355,
        workoutCount = 4, workoutMinutes = 141,
        days = emptyList(), previousWeeksM = listOf(38_100, 45_000, null, 44_700),
    )

    @Test
    fun `the kicker names the Monday, in capitals`() {
        assertThat(MovementWeekWording.kicker(20_696)).isEqualTo("THIS WEEK · FROM MON 31 AUG")
    }

    /** Locale.UK would say "Sept" (Java 17's CLDR data); the design says "Sep". */
    @Test
    fun `a day is its weekday and date`() {
        assertThat(MovementWeekWording.dayHeading(TEST_EPOCH_DAY)).isEqualTo("Thu 3 Sep")
    }

    @Test
    fun `the headline figures`() {
        assertThat(MovementWeekWording.distance(week)).isEqualTo("42.6 km")
        assertThat(MovementWeekWording.averageMovement(week)).isEqualTo("355 kcal of movement a day, on average")
        assertThat(MovementWeekWording.workouts(week)).isEqualTo("4 workouts · 2 h 21")
    }

    @Test
    fun `a headline figure that is not recorded is not said`() {
        val empty = week.copy(distanceM = null, averageActiveKcal = null, workoutCount = 0, workoutMinutes = 0)

        assertThat(MovementWeekWording.distance(empty)).isNull()
        assertThat(MovementWeekWording.averageMovement(empty)).isNull()
        assertThat(MovementWeekWording.workouts(empty)).isNull()
    }

    @Test
    fun `one workout is one workout`() {
        assertThat(MovementWeekWording.workouts(week.copy(workoutCount = 1, workoutMinutes = 45)))
            .isEqualTo("1 workout · 45 min")
    }

    @Test
    fun `numbers have thousands separators`() {
        assertThat(MovementWeekWording.averageMovement(week.copy(averageActiveKcal = 1_200)))
            .isEqualTo("1,200 kcal of movement a day, on average")
        assertThat(MovementWeekWording.km(1_234_500)).isEqualTo("1,234.5 km")
    }

    @Test
    fun `durations, distances and paces`() {
        assertThat(MovementWeekWording.duration(45)).isEqualTo("45 min")
        assertThat(MovementWeekWording.duration(60)).isEqualTo("1 h")
        assertThat(MovementWeekWording.duration(65)).isEqualTo("1 h 05")
        assertThat(MovementWeekWording.duration(430)).isEqualTo("7 h 10")
        assertThat(MovementWeekWording.km(6_200)).isEqualTo("6.2 km")
        assertThat(MovementWeekWording.km(45_000)).isEqualTo("45.0 km")
        assertThat(MovementWeekWording.pace(310)).isEqualTo("5:10 /km")
        assertThat(MovementWeekWording.pace(365)).isEqualTo("6:05 /km")
    }

    @Test
    fun `a closed day is its movement, its workouts and its sleep`() {
        assertThat(MovementWeekWording.summaryLine(fullDay)).isEqualTo("410 kcal · Running 6.2 km · slept 7 h 10")
    }

    @Test
    fun `a workout with no distance is given by its time, and two are listed together`() {
        val weights = workout(title = "Weights", minutes = 45, distanceM = null)
        val day = MovementDay(TEST_EPOCH_DAY, null, listOf(running, weights), eatenKcal = null)

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("Running 6.2 km, Weights 45 min")
    }

    @Test
    fun `a closed day with nothing at all says so`() {
        val day = MovementDay(TEST_EPOCH_DAY, null, emptyList(), eatenKcal = null)

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("nothing recorded")
        assertThat(MovementWeekWording.detailLines(day)).containsExactly("nothing recorded")
    }

    /** Design question 2 in the plan: never "nothing recorded" over a day that has steps. */
    @Test
    fun `a closed day with only steps shows the steps, not nothing`() {
        val day = MovementDay(
            TEST_EPOCH_DAY,
            HealthDay(TEST_EPOCH_DAY, steps = 9_000, stepsSource = FigureSource.TOTAL),
            emptyList(),
            eatenKcal = null,
        )

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("9,000 steps · phone and band")
    }

    @Test
    fun `an open day is one line per part`() {
        assertThat(MovementWeekWording.detailLines(fullDay)).containsExactly(
            "410 kcal of movement · phone and band",
            "9,000 steps · phone and band",
            "Running · 6.2 km · 32 min · 5:10 /km · avg 142 bpm",
            "Slept 7 h 10 — deep 1 h 20 · REM 1 h 35 · light 4 h 15",
            "Resting 58 · HRV 42 ms · oxygen 97% · breathing 14/min",
            "1,840 kcal eaten",
        ).inOrder()
    }

    @Test
    fun `a figure the owner set says so`() {
        val corrected = fullHealth.copy(stepsSource = FigureSource.CORRECTED, activeKcalSource = FigureSource.CORRECTED)
        val lines = MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, corrected, emptyList(), null))

        assertThat(lines).contains("410 kcal of movement · you set this")
        assertThat(lines).contains("9,000 steps · you set this")
    }

    @Test
    fun `a source this version does not know is not guessed at`() {
        val unknown = HealthDay(TEST_EPOCH_DAY, activeKcal = 410, activeKcalSource = FigureSource.UNRECOGNISED)

        assertThat(MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, unknown, emptyList(), null)))
            .containsExactly("410 kcal of movement")
    }

    /** D4, D69: a missing figure is left out, never written as zero. */
    @Test
    fun `only what was recorded is said`() {
        val sparse = HealthDay(TEST_EPOCH_DAY, sleepMinutes = 430, restingHeartRate = 58)
        val bare = workout(title = "Running", minutes = 32, distanceM = null)

        val lines = MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, sparse, listOf(bare), null))

        assertThat(lines).containsExactly("Running · 32 min", "Slept 7 h 10", "Resting 58").inOrder()
    }

    @Test
    fun `a workout is named by its title, or by its kind when it has none`() {
        val swim = workout(title = null, minutes = 30, distanceM = null, kind = WorkoutKind.SWIM)
        val odd = workout(title = " ", minutes = 30, distanceM = null, kind = WorkoutKind.UNRECOGNISED)
        val yoga = workout(title = "Yoga", minutes = 30, distanceM = null, kind = WorkoutKind.OTHER)

        assertThat(MovementWeekWording.name(swim)).isEqualTo("Swimming")
        assertThat(MovementWeekWording.name(odd)).isEqualTo("Exercise")
        assertThat(MovementWeekWording.name(yoga)).isEqualTo("Yoga")
    }

    @Test
    fun `the last four weeks, newest first, with a dash for a week with no distance`() {
        assertThat(MovementWeekWording.lastFourWeeks(week))
            .isEqualTo("Last four weeks: 38.1 · 45.0 · — · 44.7 km")
    }

    /** Design question 6 in the plan. */
    @Test
    fun `with no distance in any of the four weeks the line is not drawn`() {
        assertThat(MovementWeekWording.lastFourWeeks(week.copy(previousWeeksM = listOf(null, null, null, null))))
            .isNull()
    }

    private fun workout(
        title: String?,
        minutes: Int,
        distanceM: Int?,
        kind: WorkoutKind = WorkoutKind.RUN,
        avgHeartRate: Int? = null,
    ) = Workout(
        id = 0, epochDay = TEST_EPOCH_DAY, startedAtMillis = 0, durationMinutes = minutes,
        kind = kind, title = title, distanceM = distanceM, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
        hidden = false, note = null, avgHeartRate = avgHeartRate,
    )
}
```

- [ ] **Step 2: Run it to see it fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.movement.MovementWeekWordingTest" > /tmp/ms-movement.log 2>&1; echo "exit $?"
grep -m5 "e: " /tmp/ms-movement.log
```

Expected: `exit 1`; `Unresolved reference: MovementWeekWording`.

- [ ] **Step 3: Write the wording.** Create `app/src/main/java/com/metaself/app/ui/movement/MovementWeekWording.kt`:

```kotlin
package com.metaself.app.ui.movement

import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the Movement screen says (D73, D74).
 *
 * Every part is said only when it was recorded: a missing figure is left out, never written as zero
 * (D4, D69). Movement calories are shown as themselves — never a total burn, never eaten minus
 * burned (D63).
 */
object MovementWeekWording {

    /**
     * [Locale.US], not the UK locale [com.metaself.app.ui.day.DayWording] uses: Java 17's CLDR data
     * abbreviates September as "Sept" for en-GB, and the design writes "Sep".
     */
    private val SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

    private const val SEP = " · "

    const val NOTHING = "nothing recorded"

    /** "THIS WEEK · FROM MON 31 AUG". Set in capitals here: these are the app's words, not the owner's. */
    fun kicker(monday: Long): String =
        "THIS WEEK · FROM " + LocalDate.ofEpochDay(monday).format(SHORT_DATE).uppercase(Locale.US)

    /** The one large figure (D74): "42.6 km". */
    fun distance(week: MovementWeek): String? = week.distanceM?.let(::km)

    fun averageMovement(week: MovementWeek): String? =
        week.averageActiveKcal?.let { "${number(it)} kcal of movement a day, on average" }

    /** "4 workouts · 2 h 21"; null with none. */
    fun workouts(week: MovementWeek): String? {
        if (week.workoutCount == 0) return null
        val count = if (week.workoutCount == 1) "1 workout" else "${week.workoutCount} workouts"
        return count + SEP + duration(week.workoutMinutes)
    }

    /** "Thu 3 Sep". No year: the screen only ever shows this week. */
    fun dayHeading(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(SHORT_DATE)

    /**
     * A closed day (D73): up to three parts — movement calories, workouts, sleep. With none of them,
     * the first line the open day would show, so a day with steps is never called empty; "nothing
     * recorded" only when the open day has nothing either.
     */
    fun summaryLine(day: MovementDay): String {
        val health = day.health
        val parts = listOfNotNull(
            health?.activeKcal?.let { "${number(it)} kcal" },
            day.workouts.takeIf { it.isNotEmpty() }?.joinToString(", ") { workout ->
                name(workout) + " " + (workout.distanceM?.let(::km) ?: duration(workout.durationMinutes))
            },
            health?.sleepMinutes?.let { "slept ${duration(it)}" },
        )
        return if (parts.isNotEmpty()) parts.joinToString(SEP) else detailLines(day).first()
    }

    /** An open day (D73): one line per part, each only when recorded. */
    fun detailLines(day: MovementDay): List<String> {
        val health = day.health
        val movement = if (health == null) {
            emptyList()
        } else {
            listOfNotNull(
                health.activeKcal?.let { withSource("${number(it)} kcal of movement", health.activeKcalSource) },
                health.steps?.let { withSource("${number(it)} steps", health.stepsSource) },
            )
        }
        val rest = listOfNotNull(
            health?.let(::sleepLine),
            health?.let(::bodyLine),
            day.eatenKcal?.let { "${number(it)} kcal eaten" },
        )
        return (movement + day.workouts.map(::workoutLine) + rest).ifEmpty { listOf(NOTHING) }
    }

    /** "Last four weeks: 38.1 · 45.0 · — · 44.7 km"; null when none of the four has a distance. */
    fun lastFourWeeks(week: MovementWeek): String? {
        if (week.previousWeeksM.all { it == null }) return null
        return "Last four weeks: " +
            week.previousWeeksM.joinToString(SEP) { metres -> metres?.let(::kmFigure) ?: "—" } +
            " km"
    }

    /** The recording app's name for a session, or the kind's, as `ExerciseNames` names them. */
    fun name(workout: Workout): String = workout.title?.takeIf { it.isNotBlank() } ?: when (workout.kind) {
        WorkoutKind.RUN -> "Running"
        WorkoutKind.WALK -> "Walking"
        WorkoutKind.CYCLE -> "Cycling"
        WorkoutKind.SWIM -> "Swimming"
        WorkoutKind.STRENGTH -> "Weights"
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "Exercise"
    }

    fun km(metres: Int): String = kmFigure(metres) + " km"

    /** "45 min", "1 h", "7 h 10". */
    fun duration(minutes: Int): String {
        if (minutes < 60) return "$minutes min"
        val rest = minutes % 60
        return if (rest == 0) "${minutes / 60} h" else String.format(Locale.US, "%d h %02d", minutes / 60, rest)
    }

    /** "5:10 /km". */
    fun pace(secondsPerKm: Int): String =
        String.format(Locale.US, "%d:%02d /km", secondsPerKm / 60, secondsPerKm % 60)

    private fun workoutLine(workout: Workout): String = listOfNotNull(
        name(workout),
        workout.distanceM?.let(::km),
        duration(workout.durationMinutes),
        workout.paceSecondsPerKm?.let(::pace),
        workout.avgHeartRate?.let { "avg $it bpm" },
    ).joinToString(SEP)

    private fun sleepLine(health: HealthDay): String? {
        val total = health.sleepMinutes ?: return null
        val stages = listOfNotNull(
            health.deepMinutes?.let { "deep ${duration(it)}" },
            health.remMinutes?.let { "REM ${duration(it)}" },
            health.lightMinutes?.let { "light ${duration(it)}" },
        )
        val slept = "Slept ${duration(total)}"
        return if (stages.isEmpty()) slept else slept + " — " + stages.joinToString(SEP)
    }

    private fun bodyLine(health: HealthDay): String? = listOfNotNull(
        health.restingHeartRate?.let { "Resting $it" },
        health.hrvMs?.let { "HRV ${it.roundToInt()} ms" },
        health.oxygenPct?.let { "oxygen ${it.roundToInt()}%" },
        health.respiratoryRate?.let { "breathing ${it.roundToInt()}/min" },
    ).takeIf { it.isNotEmpty() }?.joinToString(SEP)

    /** D4: a figure says where it came from. TOTAL and CORRECTED are the two a summary figure can be. */
    private fun withSource(text: String, source: FigureSource?): String = when (source) {
        FigureSource.TOTAL -> text + SEP + "phone and band"
        FigureSource.CORRECTED -> text + SEP + "you set this"
        else -> text
    }

    private fun kmFigure(metres: Int): String = String.format(Locale.US, "%,.1f", metres / 1000.0)

    private fun number(value: Int): String = String.format(Locale.US, "%,d", value)
}
```

- [ ] **Step 4: Run it to see it pass.** Same command as Step 2. Expected: `exit 0`, no `FAILED`.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/movement/MovementWeekWording.kt \
        app/src/test/java/com/metaself/app/ui/movement/MovementWeekWordingTest.kt
git commit -m "feat: what the Movement screen says (D73, D74)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Reading the record

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/MovementRecord.kt`
- Modify: `app/src/main/java/com/metaself/app/di/DataModule.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/MovementRecordTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/** The stored rows, as the Movement screen reads them. Every figure is invented. */
class MovementRecordTest {

    @Test
    fun `a stored day keeps every figure and where it came from`() {
        val stored = HealthDayEntity(
            epochDay = 20_699, computedAtMillis = 0,
            steps = 9_000, stepsSource = "CORRECTED",
            distanceM = 8_000, distanceSource = "TOTAL",
            activeKcal = 410, activeKcalSource = "TOTAL",
            restingHeartRate = 58, restingHeartRateSource = "READ",
            hrvMs = 42.0, hrvSource = "COMPUTED",
            oxygenPct = 97.0, oxygenSource = "COMPUTED",
            respiratoryRate = 14.0, respiratoryRateSource = "COMPUTED",
            sleepMinutes = 430, deepMinutes = 80, lightMinutes = 255, remMinutes = 95, sleepSource = "COMPUTED",
        )

        assertThat(stored.toHealthDay()).isEqualTo(
            HealthDay(
                epochDay = 20_699,
                steps = 9_000, stepsSource = FigureSource.CORRECTED,
                distanceM = 8_000,
                activeKcal = 410, activeKcalSource = FigureSource.TOTAL,
                restingHeartRate = 58, hrvMs = 42.0, oxygenPct = 97.0, respiratoryRate = 14.0,
                sleepMinutes = 430, deepMinutes = 80, remMinutes = 95, lightMinutes = 255,
            ),
        )
    }

    @Test
    fun `a day with nothing recorded reads as nothing, not zeros`() {
        assertThat(HealthDayEntity(epochDay = 20_699, computedAtMillis = 0).toHealthDay())
            .isEqualTo(HealthDay(epochDay = 20_699))
    }

    @Test
    fun `a stored workout keeps whether it is hidden, and its heart rate`() {
        val workout = aWorkout(hidden = true, avgHeartRate = 142).toWorkout()

        assertThat(workout.kind).isEqualTo(WorkoutKind.RUN)
        assertThat(workout.title).isEqualTo("Running")
        assertThat(workout.durationMinutes).isEqualTo(32)
        assertThat(workout.distanceM).isEqualTo(6_200)
        assertThat(workout.hidden).isTrue()
        assertThat(workout.avgHeartRate).isEqualTo(142)
    }

    @Test
    fun `a stored name this version does not know never breaks the read`() {
        val workout = aWorkout(kind = "SKATEBOARD", energySource = "GUESSED", effort = "BRUTAL", source = "BEAMED")
            .toWorkout()

        assertThat(workout.kind).isEqualTo(WorkoutKind.UNRECOGNISED)
        assertThat(workout.energySource).isEqualTo(EnergySource.NONE)
        assertThat(workout.effort).isNull()
        assertThat(workout.source).isEqualTo(WorkoutSource.SYNCED)
    }

    private fun aWorkout(
        kind: String = "RUN",
        energySource: String = "NONE",
        effort: String? = null,
        source: String = "SYNCED",
        hidden: Boolean = false,
        avgHeartRate: Int? = null,
    ) = WorkoutEntity(
        id = 7, epochDay = 20_699, startedAtMillis = 1_000, durationMinutes = 32,
        kind = kind, title = "Running", distanceM = 6_200, energyKcal = null,
        energySource = energySource, effort = effort, source = source,
        origin = "com.example.band", originId = "session-1",
        hidden = hidden, note = null, avgHeartRate = avgHeartRate,
    )
}
```

- [ ] **Step 2: Run it to see it fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.health.MovementRecordTest" > /tmp/ms-movement.log 2>&1; echo "exit $?"
grep -m5 "e: " /tmp/ms-movement.log
```

Expected: `exit 1`; `Unresolved reference: toHealthDay` / `toWorkout`.

- [ ] **Step 3: Write the port.** Create `app/src/main/java/com/metaself/app/data/health/MovementRecord.kt`:

```kotlin
package com.metaself.app.data.health

import com.metaself.app.domain.movement.Effort
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * What the Movement screen reads from the health record (D73–D75), observed, so a copy that lands
 * while the screen is open shows at once. Nothing here reads Health Connect.
 */
interface MovementRecord {
    /** The daily summaries of [from]..[to], inclusive. */
    fun observeDays(from: Long, to: Long): Flow<List<HealthDay>>

    /** The workouts of [from]..[to], hidden ones included; the week leaves those out. */
    fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>>
}

/** Over the two DAO reads that already exist; no new query (the plan's red line). */
class RoomMovementRecord @Inject constructor(
    private val days: HealthDayDao,
    private val workouts: WorkoutDao,
) : MovementRecord {

    override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> =
        days.observeBetween(from, to).map { rows -> rows.map { it.toHealthDay() } }

    override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> =
        workouts.observeBetween(from, to).map { rows -> rows.map { it.toWorkout() } }
}

fun HealthDayEntity.toHealthDay(): HealthDay = HealthDay(
    epochDay = epochDay,
    steps = steps,
    stepsSource = FigureSource.parse(stepsSource),
    distanceM = distanceM,
    activeKcal = activeKcal,
    activeKcalSource = FigureSource.parse(activeKcalSource),
    restingHeartRate = restingHeartRate,
    hrvMs = hrvMs,
    oxygenPct = oxygenPct,
    respiratoryRate = respiratoryRate,
    sleepMinutes = sleepMinutes,
    deepMinutes = deepMinutes,
    remMinutes = remMinutes,
    lightMinutes = lightMinutes,
)

/**
 * A stored workout as the rest of the app reasons about it. A name this version does not know never
 * breaks the read: an unknown kind is UNRECOGNISED, an unknown effort none. An unknown energy source
 * reads as NONE and an unknown origin as SYNCED — the Movement screen shows neither, and SYNCED is
 * the side that cannot be deleted by hand.
 */
fun WorkoutEntity.toWorkout(): Workout = Workout(
    id = id,
    epochDay = epochDay,
    startedAtMillis = startedAtMillis,
    durationMinutes = durationMinutes,
    kind = WorkoutKind.parse(kind),
    title = title,
    distanceM = distanceM,
    energyKcal = energyKcal,
    energySource = EnergySource.entries.firstOrNull { it.name == energySource } ?: EnergySource.NONE,
    effort = Effort.entries.firstOrNull { it.name == effort },
    source = WorkoutSource.entries.firstOrNull { it.name == source } ?: WorkoutSource.SYNCED,
    hidden = hidden,
    note = note,
    avgHeartRate = avgHeartRate,
)
```

- [ ] **Step 4: Bind it.** In `app/src/main/java/com/metaself/app/di/DataModule.kt`, add two imports beside
  the other `data.health` ones:

```kotlin
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.health.RoomMovementRecord
```

and, directly after `provideHealthRecordStatus`, add:

```kotlin
    /** What the Movement screen reads (D73–D75): the health record's days and workouts, observed. */
    @Provides
    @Singleton
    fun provideMovementRecord(record: RoomMovementRecord): MovementRecord = record
```

- [ ] **Step 5: Run it to see it pass.** Same command as Step 2. Expected: `exit 0`, no `FAILED`.
  Then compile the whole app, because Hilt checks the graph only at build time:

```
free -m
~/bin/gradlew-safe :app:assembleDebug > /tmp/ms-movement.log 2>&1; echo "exit $?"
```

Expected: `exit 0`.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/metaself/app/data/health/MovementRecord.kt \
        app/src/main/java/com/metaself/app/di/DataModule.kt \
        app/src/test/java/com/metaself/app/data/health/MovementRecordTest.kt
git commit -m "feat: the Movement screen reads the health record through one port

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The view model

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/screen/movement/MovementUiState.kt`
- Create: `app/src/main/java/com/metaself/app/ui/screen/movement/MovementViewModel.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/movement/MovementViewModelTest.kt`

- [ ] **Step 1: Write the failing test.** JUnit 5 with `StandardTestDispatcher` + `setMain`, as
  `WeightViewModelTest` does.

```kotlin
package com.metaself.app.ui.screen.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.InMemoryMealRepository
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * TEST_EPOCH_DAY is Thursday 3 September 2026: its week began on Monday 31 August (20,696), and the
 * four weeks before that on 3 August (20,668). Every figure is invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovementViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var date: LocalDate = LocalDate.ofEpochDay(TEST_EPOCH_DAY)
    private val today = Today { date }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `today is first and starts open`() = runTest {
        val state = viewModel().state.first { it.week != null }

        assertThat(state.openDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(state.week!!.days.first().epochDay).isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `it reads this week, and the four before it for their distances`() = runTest {
        val record = FakeRecord()

        viewModel(record).state.first { it.week != null }

        assertThat(record.daysAsked).containsExactly(20_668L to TEST_EPOCH_DAY)
        assertThat(record.workoutsAsked).containsExactly(20_696L to TEST_EPOCH_DAY)
    }

    /** D73: one day open at a time; tapping an open day closes it. */
    @Test
    fun `tapping another day opens it and closes today, and tapping it again closes it`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }

        model.toggle(20_698L)
        assertThat(model.state.first { it.openDay != TEST_EPOCH_DAY }.openDay).isEqualTo(20_698L)

        model.toggle(20_698L)
        assertThat(model.state.first { it.openDay != 20_698L }.openDay).isNull()
    }

    @Test
    fun `a copy that lands while the screen is open shows at once`() = runTest {
        val record = FakeRecord()
        val model = viewModel(record)
        model.state.first { it.week != null }

        record.days.value = listOf(HealthDay(epochDay = TEST_EPOCH_DAY, distanceM = 5_000))

        assertThat(model.state.first { it.week?.distanceM != null }.week!!.distanceM).isEqualTo(5_000)
    }

    @Test
    fun `what was eaten comes from the meals logged that day, and follows a new one`() = runTest {
        val meals = InMemoryMealRepository(
            listOf(aMeal(epochDay = TEST_EPOCH_DAY, items = listOf(anItem(kcal = 600), anItem(kcal = 400)))),
        )
        val model = MovementViewModel(FakeRecord(), meals, today, ProblemLog.NONE)

        assertThat(model.state.first { it.week != null }.week!!.days.first().eatenKcal).isEqualTo(1_000)

        meals.log(aMeal(epochDay = TEST_EPOCH_DAY, items = listOf(anItem(kcal = 500))))

        assertThat(model.state.first { it.week?.days?.first()?.eatenKcal == 1_500 }).isNotNull()
    }

    @Test
    fun `back on the screen after midnight, the new day is today and open`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }
        model.toggle(20_698L)

        date = LocalDate.ofEpochDay(TEST_EPOCH_DAY + 1)
        model.lookedAt()

        val state = model.state.first { it.week?.days?.first()?.epochDay == TEST_EPOCH_DAY + 1 }
        assertThat(state.openDay).isEqualTo(TEST_EPOCH_DAY + 1)
    }

    @Test
    fun `back on the screen the same day, the open day is left alone`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }
        model.toggle(20_698L)

        model.lookedAt()

        assertThat(model.state.first { it.openDay != TEST_EPOCH_DAY }.openDay).isEqualTo(20_698L)
    }

    /** D8: a read that fails is said on the screen and logged, never thrown. */
    @Test
    fun `a record that cannot be read is said and logged`() = runTest {
        val problems = RecordingProblemLog()
        val broken = object : MovementRecord {
            override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> =
                flow { throw IllegalStateException("disk full") }

            override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> = flowOf(emptyList())
        }
        val model = MovementViewModel(broken, InMemoryMealRepository(), today, problems)

        assertThat(model.state.first { it.unreadable }.week).isNull()
        assertThat(problems.recorded.single().kind).isEqualTo("movement")
    }

    private fun viewModel(record: MovementRecord = FakeRecord()) =
        MovementViewModel(record, InMemoryMealRepository(), today, ProblemLog.NONE)

    private class FakeRecord : MovementRecord {
        val days = MutableStateFlow<List<HealthDay>>(emptyList())
        val workouts = MutableStateFlow<List<Workout>>(emptyList())
        val daysAsked = mutableListOf<Pair<Long, Long>>()
        val workoutsAsked = mutableListOf<Pair<Long, Long>>()

        override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> {
            daysAsked += from to to
            return days.map { all -> all.filter { it.epochDay in from..to } }
        }

        override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> {
            workoutsAsked += from to to
            return workouts.map { all -> all.filter { it.epochDay in from..to } }
        }
    }
}
```

- [ ] **Step 2: Run it to see it fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.movement.MovementViewModelTest" > /tmp/ms-movement.log 2>&1; echo "exit $?"
grep -m5 "e: " /tmp/ms-movement.log
```

Expected: `exit 1`; `Unresolved reference: MovementViewModel`.

- [ ] **Step 3: Write the state.** Create `app/src/main/java/com/metaself/app/ui/screen/movement/MovementUiState.kt`:

```kotlin
package com.metaself.app.ui.screen.movement

import com.metaself.app.domain.movement.MovementWeek

/**
 * @property week null until the first read has answered, and when it failed.
 * @property openDay the one open day (D73); null when the owner has closed every day.
 * @property unreadable the record could not be read; the screen says so (D8).
 */
data class MovementUiState(
    val week: MovementWeek? = null,
    val openDay: Long? = null,
    val unreadable: Boolean = false,
)
```

- [ ] **Step 4: Write the view model.** Create `app/src/main/java/com/metaself/app/ui/screen/movement/MovementViewModel.kt`:

```kotlin
package com.metaself.app.ui.screen.movement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.movement.MovementWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * This week from the stored health record and the meal log (D73–D75), and which day is open.
 *
 * Everything is observed, so a copy of the health record, or a meal logged, while the screen is open
 * shows at once. Eaten is read through [MealRepository.observeDay] — the day screen's own read — and
 * summed by `DayTotals`, so the two screens cannot disagree about a day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovementViewModel @Inject constructor(
    private val record: MovementRecord,
    private val meals: MealRepository,
    private val today: Today,
    private val problems: ProblemLog,
) : ViewModel() {

    private val calendarToday = MutableStateFlow(today().toEpochDay())

    /** Today starts open (D73). */
    private val openDay = MutableStateFlow<Long?>(calendarToday.value)

    private val week: Flow<MovementWeek> = calendarToday.flatMapLatest { day ->
        val monday = MovementWeek.mondayOf(day)
        combine(
            // Five weeks: this one, and the four the foot of the screen sums (D74).
            record.observeDays(monday - 7L * MovementWeek.PREVIOUS_WEEKS, day),
            record.observeWorkouts(monday, day),
            mealsOn((monday..day).toList()),
        ) { days, workouts, mealsByDay -> MovementWeek.of(day, days, workouts, mealsByDay) }
    }

    val state: StateFlow<MovementUiState> = combine(week, openDay) { built, open ->
        MovementUiState(week = built, openDay = open)
    }
        .catch { failure ->
            problems.record(PROBLEM_KIND, failure.message ?: failure::class.java.simpleName)
            emit(MovementUiState(unreadable = true))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MovementUiState(),
        )

    /** A day's row was tapped: open it, closing any other — or close it, if it was the open one. */
    fun toggle(epochDay: Long) {
        openDay.update { open -> if (open == epochDay) null else epochDay }
    }

    /**
     * The screen came to the front. A screen left open past midnight moves to the new day, and opens
     * it, as the day pager does; on the same day nothing changes.
     */
    fun lookedAt() {
        val now = today().toEpochDay()
        if (now == calendarToday.value) return
        openDay.value = now
        calendarToday.value = now
    }

    private fun mealsOn(days: List<Long>): Flow<Map<Long, List<Meal>>> =
        combine(days.map { day -> meals.observeDay(day).map { day to it } }) { pairs -> pairs.toMap() }

    companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PROBLEM_KIND = "movement"
    }
}
```

- [ ] **Step 5: Run it to see it pass.** Same command as Step 2. Expected: `exit 0`, no `FAILED`.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/screen/movement/MovementUiState.kt \
        app/src/main/java/com/metaself/app/ui/screen/movement/MovementViewModel.kt \
        app/src/test/java/com/metaself/app/ui/screen/movement/MovementViewModelTest.kt
git commit -m "feat: the Movement screen's view model; today starts open (D73)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The screen

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (after `weight_open`)
- Modify: `app/src/test/java/com/metaself/app/ui/ComposeRender.kt` (one helper, after `roleOf`)
- Create: `app/src/main/java/com/metaself/app/ui/screen/movement/MovementScreen.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/movement/MovementScreenRenderTest.kt`

What a render test here can and cannot prove (`CLAUDE.md`, Testing): it proves which words are drawn,
their order, that a row is a button with an open/closed state, and that tapping it asks for that day.
It cannot prove the large figure's size, its face, the rows' touch height or any wrapping — those are
phone checks in Task 7.

- [ ] **Step 1: Add the screen-reader helper.** In `ComposeRender.kt`, directly after `fun roleOf(...)`:

```kotlin
    /**
     * What a screen reader is told the state of the node matching [prefix] is — "open", "closed" —
     * or null when it is told none.
     *
     * Reads the LAST render, so call [texts] first.
     */
    fun stateOf(prefix: String): String? =
        nodeStartingWith(prefix).config.getOrNull(SemanticsProperties.StateDescription)
```

- [ ] **Step 2: Write the failing test.**

```kotlin
package com.metaself.app.ui.screen.movement

import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * TEST_EPOCH_DAY is Thursday 3 September 2026. Every figure is invented: today moved 410 kcal and
 * slept 7 h 10 with one 32-minute run; yesterday moved 300 kcal; Tuesday and Monday hold nothing; the
 * week of 24 August has one day with a distance.
 */
@RunWith(RobolectricTestRunner::class)
class MovementScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    private val week = MovementWeek.of(
        today = TEST_EPOCH_DAY,
        days = listOf(
            HealthDay(
                epochDay = 20_699, steps = 9_000, stepsSource = FigureSource.TOTAL, distanceM = 8_000,
                activeKcal = 410, activeKcalSource = FigureSource.TOTAL, sleepMinutes = 430,
            ),
            HealthDay(epochDay = 20_698, distanceM = 4_400, activeKcal = 300, activeKcalSource = FigureSource.TOTAL),
            HealthDay(epochDay = 20_690, distanceM = 10_000),
        ),
        workouts = listOf(
            Workout(
                id = 1, epochDay = 20_699, startedAtMillis = 0, durationMinutes = 32,
                kind = WorkoutKind.RUN, title = "Running", distanceM = 6_200, energyKcal = null,
                energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
                hidden = false, note = null,
            ),
        ),
        mealsByDay = emptyMap(),
    )

    @Test
    fun `the screen is called Movement and has a way back`() {
        val texts = draw()

        assertThat(texts).contains("Movement")
        assertThat(texts).contains("Back")
    }

    /** D74: 8.0 + 4.4 km; (410 + 300) / 2 kcal; one run. */
    @Test
    fun `the headline is the week's distance, the average movement and the workouts`() {
        val texts = draw()

        assertThat(texts).contains("THIS WEEK · FROM MON 31 AUG")
        assertThat(texts).contains("12.4 km")
        assertThat(texts).contains("355 kcal of movement a day, on average")
        assertThat(texts).contains("1 workout · 32 min")
    }

    @Test
    fun `today starts open, with its detail beneath its summary`() {
        val texts = draw(openDay = TEST_EPOCH_DAY)

        assertThat(texts).contains("410 kcal of movement · phone and band")
        assertThat(texts).contains("9,000 steps · phone and band")
        assertThat(texts).contains("Running · 6.2 km · 32 min · 5:10 /km")
        assertThat(texts).contains("Slept 7 h 10")
        assertThat(texts).doesNotContain("410 kcal · Running 6.2 km · slept 7 h 10")
    }

    @Test
    fun `a closed day shows its summary and not its detail`() {
        val texts = draw(openDay = TEST_EPOCH_DAY)

        assertThat(texts).contains("300 kcal")
        assertThat(texts).doesNotContain("300 kcal of movement · phone and band")
    }

    @Test
    fun `with every day closed, today shows its summary`() {
        assertThat(draw(openDay = null)).contains("410 kcal · Running 6.2 km · slept 7 h 10")
    }

    @Test
    fun `a day with nothing says so`() {
        assertThat(draw().count { it == "nothing recorded" }).isEqualTo(2)
    }

    @Test
    fun `tapping a day asks for that day`() {
        var asked: Long? = null
        draw(onToggleDay = { asked = it })

        render.click("Wed 2 Sep")

        assertThat(asked).isEqualTo(20_698L)
    }

    @Test
    fun `each day is a button that says whether it is open`() {
        draw(openDay = TEST_EPOCH_DAY)

        assertThat(render.roleOf("Thu 3 Sep")).isEqualTo(Role.Button)
        assertThat(render.stateOf("Thu 3 Sep")).isEqualTo("open")
        assertThat(render.stateOf("Wed 2 Sep")).isEqualTo("closed")
    }

    @Test
    fun `the last four weeks are at the foot`() {
        val texts = draw()

        assertThat(texts).contains("Last four weeks: 10.0 · — · — · — km")
        assertThat(render.isDrawnBefore("Mon 31 Aug", "Last four weeks")).isTrue()
    }

    /** D75: no button that does nothing. D63: no burn, no net. */
    @Test
    fun `nothing is offered that does nothing yet, and nothing claims a burn`() {
        val texts = draw()

        assertThat(texts.none { it.contains("Log a workout") }).isTrue()
        assertThat(texts.none { it.contains("burn", ignoreCase = true) }).isTrue()
    }

    @Test
    fun `a week with nothing recorded has no large figure and every day says so`() {
        val empty = MovementWeek.of(TEST_EPOCH_DAY, emptyList(), emptyList(), emptyMap())

        val texts = draw(state = MovementUiState(week = empty, openDay = TEST_EPOCH_DAY))

        assertThat(texts).contains("THIS WEEK · FROM MON 31 AUG")
        assertThat(texts.none { it.endsWith(" km") }).isTrue()
        assertThat(texts.count { it == "nothing recorded" }).isEqualTo(4)
    }

    @Test
    fun `a record that could not be read says so`() {
        val texts = draw(state = MovementUiState(unreadable = true))

        assertThat(texts).contains("The movement record could not be read; Recent problems says why.")
    }

    private fun draw(
        openDay: Long? = TEST_EPOCH_DAY,
        state: MovementUiState = MovementUiState(week = week, openDay = openDay),
        onToggleDay: (Long) -> Unit = {},
    ): List<String> = render.texts {
        MovementScreen(state = state, onToggleDay = onToggleDay, onBack = {})
    }
}
```

- [ ] **Step 3: Run it to see it fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.movement.MovementScreenRenderTest" > /tmp/ms-movement.log 2>&1; echo "exit $?"
grep -m5 "e: " /tmp/ms-movement.log
```

Expected: `exit 1`; `Unresolved reference: MovementScreen`.

- [ ] **Step 4: Add the strings.** In `app/src/main/res/values/strings.xml`, directly after
  `<string name="weight_open">Weight</string>`:

```xml

    <string name="movement_title">Movement</string>
    <string name="movement_open">Movement</string>
    <string name="movement_open_label">open Movement</string>
    <string name="movement_day_open">open</string>
    <string name="movement_day_closed">closed</string>
    <string name="movement_day_show">show this day</string>
    <string name="movement_day_hide">close this day</string>
    <string name="movement_unreadable">The movement record could not be read; Recent problems says why.</string>
```

- [ ] **Step 5: Write the screen.** Create `app/src/main/java/com/metaself/app/ui/screen/movement/MovementScreen.kt`:

```kotlin
package com.metaself.app.ui.screen.movement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.metaself.app.R
import com.metaself.app.domain.movement.MovementDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.movement.MovementWeekWording
import com.metaself.app.ui.theme.Spacing

/**
 * This week's movement (D73–D75): the week's distance as the one large figure, the average movement
 * calories and the workouts beneath it, a short row per day — today first — and the last four weeks
 * at the foot.
 *
 * It LOOKS; it takes no input yet. "Log a workout" arrives with logging by hand (D75), not before.
 */
@Composable
fun MovementScreen(
    state: MovementUiState,
    onToggleDay: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.movement_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        val week = state.week
        when {
            state.unreadable -> Text(
                text = stringResource(R.string.movement_unreadable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            week == null -> Unit

            else -> {
                Headline(week)

                Column(modifier = Modifier.fillMaxWidth()) {
                    week.days.forEach { day ->
                        DayRow(
                            day = day,
                            open = day.epochDay == state.openDay,
                            onToggle = { onToggleDay(day.epochDay) },
                        )
                    }
                }

                MovementWeekWording.lastFourWeeks(week)?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** D74 items 1–4: the kicker, the one large figure, and the two smaller lines beneath it. */
@Composable
private fun Headline(week: MovementWeek) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
        Text(
            text = MovementWeekWording.kicker(week.monday),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The one number this screen exists to answer: `displayLarge` is Fraunces ExtraLight (D48).
        MovementWeekWording.distance(week)?.let { distance ->
            Text(
                text = distance,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        MovementWeekWording.averageMovement(week)?.let { average ->
            Text(text = average, style = MaterialTheme.typography.bodyLarge)
        }
        MovementWeekWording.workouts(week)?.let { workouts ->
            Text(
                text = workouts,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One day (D73). The heading and, when closed, the one-line summary are one button that says
 * whether it is open; an open day's detail lines sit beneath it, outside the button, so a screen
 * reader reads them one at a time.
 */
@Composable
private fun DayRow(day: MovementDay, open: Boolean, onToggle: () -> Unit) {
    val said = stringResource(if (open) R.string.movement_day_open else R.string.movement_day_closed)
    val action = stringResource(if (open) R.string.movement_day_hide else R.string.movement_day_show)

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = action, onClick = onToggle)
                // The usual 48 of touch; a render here cannot measure it (CLAUDE.md), the phone can.
                .heightIn(min = 48.dp)
                .semantics { stateDescription = said }
                .padding(vertical = Spacing.Related),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            Text(
                text = MovementWeekWording.dayHeading(day.epochDay),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!open) {
                Text(
                    text = MovementWeekWording.summaryLine(day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.Related),
                verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
            ) {
                MovementWeekWording.detailLines(day).forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 6: Run it to see it pass.** Same command as Step 3. Expected: `exit 0`, no `FAILED`.
  If `the last four weeks are at the foot` fails with "no node whose text starts with", the Monday row
  was not placed on the default canvas — pass `heightPx = 4000` through `render.texts` in `draw` and
  say so in the commit; do not weaken the assertion.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/test/java/com/metaself/app/ui/ComposeRender.kt \
        app/src/main/java/com/metaself/app/ui/screen/movement/MovementScreen.kt \
        app/src/test/java/com/metaself/app/ui/screen/movement/MovementScreenRenderTest.kt
git commit -m "feat: the Movement screen (D73, D74)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The ways in

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/day/StepBar.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/screen/day/DayScreen.kt` (`DayScreenContent`)
- Modify: `app/src/main/java/com/metaself/app/ui/screen/day/DayPager.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt`
- Modify: `app/src/test/java/com/metaself/app/sim/SimulatedApp.kt` (`TodayHere`)
- Test: `app/src/test/java/com/metaself/app/ui/screen/day/DayScreenRenderTest.kt`
- Test: `app/src/test/java/com/metaself/app/ui/nav/MetaSelfNavHostRenderTest.kt`

The top-right menu is not render-tested: `DropdownMenu` draws in a popup window, which
`ComposeRender` does not walk, and `DayPager` needs a real `DayViewModel`. Its entry is a phone check.

- [ ] **Step 1: Write the failing tests.** In `DayScreenRenderTest.kt`, add the import
  `import androidx.compose.ui.semantics.Role`, give `draw(...)` one more parameter after
  `onScan: () -> Unit = {},`:

```kotlin
        onOpenMovement: (() -> Unit)? = null,
```

  pass it on after `onOpenPart = onOpenPart,` inside `DayScreenContent(...)`:

```kotlin
            onOpenMovement = onOpenMovement,
```

  and add, after `a quiet day still shows its steps`:

```kotlin
    /** D75: the step line is a door to the Movement screen; nothing new is drawn on the day. */
    @Test
    fun `the step line is a door to the Movement screen`() {
        var opened = false
        draw(
            meals = emptyList(),
            movement = MovementToday(steps = 3_100, normalSteps = 5_200),
            onOpenMovement = { opened = true },
        )

        assertThat(render.roleOf("3,100 steps")).isEqualTo(Role.Button)
        assertThat(render.clickLabelOf("3,100 steps")).isEqualTo("open Movement")
        render.click("3,100 steps")
        assertThat(opened).isTrue()
    }
```

  In `MetaSelfNavHostRenderTest.kt`, add `Destination.Movement.route,` to the list in
  `the destinations have distinct routes`, and add:

```kotlin
    @Test
    fun `the Movement screen has its own route`() {
        assertThat(Destination.Movement.route).isEqualTo("movement")
    }
```

- [ ] **Step 2: Run them to see them fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.day.DayScreenRenderTest" --tests "com.metaself.app.ui.nav.MetaSelfNavHostRenderTest" > /tmp/ms-movement.log 2>&1; echo "exit $?"
grep -m5 "e: " /tmp/ms-movement.log
```

Expected: `exit 1`; `No parameter with name 'onOpenMovement' found` and `Unresolved reference: Movement`.

- [ ] **Step 3: The step line becomes a door.** In `StepBar.kt`, add imports:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.metaself.app.R
```

  replace the signature and the opening of the outer `Column`:

```kotlin
fun StepBar(today: MovementToday, modifier: Modifier = Modifier) {
    val reached = today.aboveUsual

    Column(
        modifier = modifier.fillMaxWidth(),
```

  with:

```kotlin
fun StepBar(
    today: MovementToday,
    modifier: Modifier = Modifier,
    /**
     * Opens the Movement screen (D75, D49 item 8's rule: every line is a door). Null draws the line
     * as it always was, with nothing to press.
     */
    onOpen: (() -> Unit)? = null,
) {
    val reached = today.aboveUsual
    val opens = stringResource(R.string.movement_open_label)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onOpen == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClickLabel = opens, onClick = onOpen)
                },
            ),
```

- [ ] **Step 4: The day passes it on.** In `DayScreen.kt`, in `DayScreenContent`'s parameters, replace:

```kotlin
    onOpenPart: (DayPart) -> Unit = {},
) = CompositionLocalProvider(LocalMoves provides
```

  with:

```kotlin
    onOpenPart: (DayPart) -> Unit = {},
    // The Movement screen (D75). Null — every caller written before it — leaves the step line a
    // line, not a door.
    onOpenMovement: (() -> Unit)? = null,
) = CompositionLocalProvider(LocalMoves provides
```

  and replace `            StepBar(today = walked)` with:

```kotlin
            StepBar(today = walked, onOpen = onOpenMovement)
```

- [ ] **Step 5: The pager and its menu.** In `DayPager.kt`:

  (a) with replace-all, replace each of the two occurrences of `    onOpenWeight: () -> Unit,` (in
  `DayPager` and `DayPagerOn`) with:

```kotlin
    onOpenWeight: () -> Unit,
    /** The Movement screen (D75): from the top-right menu and from the day's step line. */
    onOpenMovement: () -> Unit,
```

  (b) replace `            onOpenWeight = onOpenWeight,` (inside `key(todayEpochDay)`) with:

```kotlin
            onOpenWeight = onOpenWeight,
            onOpenMovement = onOpenMovement,
```

  (c) in the `DropdownMenu`, directly after the Weight item's closing `),` — i.e. replace:

```kotlin
                        onOpenWeight()
                    },
                )
```

  with:

```kotlin
                        onOpenWeight()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.movement_open)) },
                    onClick = {
                        showingMenu = false
                        onOpenMovement()
                    },
                )
```

  (d) in the `DayScreenContent(...)` call, replace:

```kotlin
                        onOpenSettings = onOpenSettings,
                        onDismissTargetChange = viewModel::dismissTargetChange,
```

  with:

```kotlin
                        onOpenSettings = onOpenSettings,
                        onOpenMovement = onOpenMovement,
                        onDismissTargetChange = viewModel::dismissTargetChange,
```

- [ ] **Step 6: The route.** In `MetaSelfNavHost.kt`:

  (a) add imports:

```kotlin
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.metaself.app.ui.screen.movement.MovementScreen
import com.metaself.app.ui.screen.movement.MovementViewModel
```

  (b) in `sealed class Destination`, replace `    data object Weight : Destination("weight")` with:

```kotlin
    data object Weight : Destination("weight")

    /** This week's movement (D73–D75). */
    data object Movement : Destination("movement")
```

  (c) in the `DayPager(...)` call, replace
  `                onOpenWeight = { navController.navigate(Destination.Weight.route) },` with:

```kotlin
                onOpenWeight = { navController.navigate(Destination.Weight.route) },
                onOpenMovement = { navController.navigate(Destination.Movement.route) },
```

  (d) directly before `        composable(Destination.WeightChart.route) {`, add:

```kotlin
        composable(Destination.Movement.route) {
            val movementViewModel: MovementViewModel = hiltViewModel()
            val movementState by movementViewModel.state.collectAsStateWithLifecycle()
            // Left open past midnight, it moves to the new day on return, as the day pager does.
            LifecycleResumeEffect(movementViewModel) {
                movementViewModel.lookedAt()
                onPauseOrDispose { }
            }
            MovementScreen(
                state = movementState,
                onToggleDay = movementViewModel::toggle,
                onBack = { navController.popBackStack() },
            )
        }

```

- [ ] **Step 7: The simulated app.** In `SimulatedApp.kt`'s `TodayHere`, replace
  `        onOpenWeight = {},` with:

```kotlin
        onOpenWeight = {},
        // The Movement screen is not part of this shell; a walk that opens it finds nothing.
        onOpenMovement = {},
```

- [ ] **Step 8: Run them to see them pass.** Same command as Step 2. Expected: `exit 0`, no `FAILED`.
  Every existing test in `DayScreenRenderTest` must still pass — the step line is now one merged
  node, and `texts` still returns each of its lines.

- [ ] **Step 9: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/day/StepBar.kt \
        app/src/main/java/com/metaself/app/ui/screen/day/DayScreen.kt \
        app/src/main/java/com/metaself/app/ui/screen/day/DayPager.kt \
        app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt \
        app/src/test/java/com/metaself/app/sim/SimulatedApp.kt \
        app/src/test/java/com/metaself/app/ui/screen/day/DayScreenRenderTest.kt \
        app/src/test/java/com/metaself/app/ui/nav/MetaSelfNavHostRenderTest.kt
git commit -m "feat: the step line and the top menu open the Movement screen (D75)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Version, suite, lint, PR, CI, release

- [ ] `app/build.gradle.kts`: `versionCode = 112` → `113`, `versionName = "0.57.1"` → `"0.58.0"`.
- [ ] Whole suite (`free -m` first):
  `~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-movement.log 2>&1; echo "exit $?"` — 0 failures;
  skipped exactly the ten SQLite classes. Lint:
  `~/bin/gradlew-safe :app:lintDebug > /tmp/ms-movement-lint.log 2>&1; echo "exit $?"` — exit 0.
  `git status app/schemas` clean. Anonymisation read of every added line, test names included (they
  print in CI): no real figure, no frequency, no story about a day.
- [ ] Commit `app/build.gradle.kts` alone (`0.58.0: the Movement screen (D73–D75)`, co-author line).
- [ ] Push, PR `0.58.0: the Movement screen (D73–D75)`. Body: what the screen shows and where it reads
  from; no schema change and no new DAO query; the eleven design questions settled in this plan, one
  line each; what the render tests cannot prove (large-figure size, row height, wrapping, the menu in
  its popup); what CI must show (0 skipped); the phone checks below. No session link.
- [ ] CI green with 0 skipped → squash-merge. `free -m` ≥ ~6000 MB and lock free → `~/bin/ms-release`;
  send the APK.
- [ ] **Phone checks:** the app installs over 0.57.1; the top-right menu has **Movement** after Weight;
  tapping the day's step line opens the same screen; the screen shows "THIS WEEK · FROM MON …", the
  week's distance large and on one line, the average movement calories, the workouts line; today's row
  is open, tapping another day opens it and closes today, tapping it again closes it; the last-four-weeks
  line sits at the foot; with TalkBack a day row is read as a button with "open"/"closed"; back returns
  to the day.

## Self-review (2026-09-27)

**Spec coverage.** D73 closed row (movement kcal, workouts by name and distance or time, sleep; "nothing
recorded") → Task 2 `summaryLine`, rendered in Task 5. Open row (movement with source, steps, each visible
workout with pace and avg bpm, sleep with stages, body, eaten) → Task 2 `detailLines`. Today first and open,
one open at a time, tap to close → Task 1 (order), Task 4 (`toggle`, default), Task 5 (rows). No
allowance per day → nothing computes one. D74 kicker, large distance, average over days that have one,
workouts count and time, rows, last four weeks with "—" → Tasks 1, 2, 5. No net, no burn → Task 5 test.
D75 step line door + menu entry + no Log button → Tasks 5, 6. "Observes them, so a copy that lands while
it is open updates it" → Task 4 test. Nothing reads Health Connect → the port is Room only (Task 3).
Not built: earlier weeks, editing/hiding, charts, running target, trainer text — none planned.

**Placeholder scan.** No TBD/TODO; every code step has its code; every run step its command and expected
result.

**Type consistency.** `MovementWeek.of(today, days, workouts, mealsByDay)`, `mondayOf`, `weekOf`,
`PREVIOUS_WEEKS`; `MovementDay(epochDay, health, workouts, eatenKcal)`; `HealthDay` fields as declared in
Task 1 and used in Tasks 2–5; `FigureSource.parse`; `Workout.avgHeartRate`; `MovementWeekWording.kicker /
distance / averageMovement / workouts / dayHeading / summaryLine / detailLines / lastFourWeeks / name / km /
duration / pace`; `MovementRecord.observeDays / observeWorkouts`; `MovementUiState(week, openDay,
unreadable)`; `MovementViewModel.toggle / lookedAt / state`; `MovementScreen(state, onToggleDay, onBack)`;
`StepBar(onOpen)`; `DayScreenContent(onOpenMovement)`; `DayPager(onOpenMovement)`;
`Destination.Movement`; string names `movement_*` match between Task 5 and Task 6. `ComposeRender.stateOf`
added in Task 5 before its first use.
