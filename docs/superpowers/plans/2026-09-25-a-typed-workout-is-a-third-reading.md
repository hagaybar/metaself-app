# A typed workout is a third reading — Implementation Plan (activity module, phase 1)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans, task by task. Steps use checkbox syntax for tracking.

**Goal:** the pure model the rest of the activity module stands on — a workout the app can reason
about, a cited MET estimate of what a typed one cost, the day's movement energy taken as the largest
of THREE readings (steps, band, typed workouts) instead of two, and the day screen's "against usual"
line speaking in whichever reading decided the credit. No database, no screen, no Health Connect
change.

**Architecture:** everything lands in `domain/movement/` as plain Kotlin beside `ActivityEnergy`
and `MovementCredit`, tested with JUnit 5 + Truth on the standard 80 kg body. `DayMovement` grows one
defaulted field, `MovementToday` grows two, and `DayViewModel` fills them at its two existing
construction sites. Nothing else in the app changes.

**Decision:** the owner's, 2026-09-25 — D59 to D64 in
`docs/superpowers/specs/2026-09-25-physical-activity-module-design.md`, §3.3 for this phase. The
wording fix is the "KNOWN GAP" recorded in milestone 1 §6.

**Tech stack:** Kotlin 1.9.22, JUnit 5, Truth. No new dependencies.

**Red lines (stop and report if crossed):**

- **Max, never sum (D12b, D60).** No reading is ever added to another.
- **A number in code is a published one or a computed one.** Every MET value carries its Compendium
  activity code beside it; nothing is remembered or rounded for convenience.
- **Nothing claims he burned a number.** `MovementWordingTest`'s *nothing claims he burned* test
  passes unchanged in what it forbids; the new line says "movement", not "burned".
- **No schema change, no manifest change, no new permission.** Those are phase 2 and phase 3.
- **A person with no band and no typed workouts sees exactly what they see today.** Every existing
  test in `domain/movement/` and `DayScreenRenderTest` passes unchanged in what it asserts.
- **Anonymisation:** fixture workouts are named ("Running"), never narrated; no real distance, pace or
  frequency anywhere, test names included. The 80 kg body and round invented figures only.

---

## File structure

| File | Responsibility |
|---|---|
| Create `app/src/main/java/com/metaself/app/domain/movement/Workout.kt` | `WorkoutKind`, `Effort`, `EnergySource`, `WorkoutSource`, `Workout` (with pace) |
| Create `app/src/main/java/com/metaself/app/domain/movement/MetEstimate.kt` | the cited MET table and `netKcal(...)` |
| Modify `app/src/main/java/com/metaself/app/domain/movement/MovementCredit.kt` | `DayMovement.typedWorkoutsKcal`, `MovementSource.TYPED_WORKOUT` |
| Modify `app/src/main/java/com/metaself/app/domain/movement/ActivityEnergy.kt` | three readings |
| Modify `app/src/main/java/com/metaself/app/domain/movement/MovementToday.kt` | `energy`, `normalEnergyKcal` |
| Modify `app/src/main/java/com/metaself/app/ui/movement/MovementWording.kt` | `againstUsual` in the deciding currency |
| Modify `app/src/main/java/com/metaself/app/ui/screen/day/DayViewModel.kt:887-909` | fill the two new fields |
| Test `app/src/test/java/com/metaself/app/domain/movement/WorkoutTest.kt` | new |
| Test `app/src/test/java/com/metaself/app/domain/movement/MetEstimateTest.kt` | new |
| Test `app/src/test/java/com/metaself/app/domain/movement/ActivityEnergyTest.kt` | extended |
| Test `app/src/test/java/com/metaself/app/domain/movement/MovementWordingTest.kt` | extended |
| Test `app/src/test/java/com/metaself/app/ui/screen/day/DayScreenRenderTest.kt` | one band-day case |

Test command for the pure parts (always through the lock, never bare `./gradlew`):

```
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.movement.*" > /tmp/claude-1001/-home-ubuntu-projects-metaself-app/f76400c8-6a34-438f-ad01-a71ea0a2333f/scratchpad/test.log 2>&1; echo "exit $?"
```

Never pipe a build whose result is reported; redirect and check `$?`.

---

## Task 1: the workout model

**Files:** create `Workout.kt`, test `WorkoutTest.kt`.

- [ ] **Step 1 — failing test**

```kotlin
package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WorkoutTest {

    private fun run(minutes: Int, distanceM: Int?) = Workout(
        id = 1, epochDay = 20_699, startedAtMillis = 0, durationMinutes = minutes,
        kind = WorkoutKind.RUN, title = null, distanceM = distanceM,
        energyKcal = null, energySource = EnergySource.NONE, effort = null,
        source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    /** 10 km in an hour is six minutes a kilometre. */
    @Test
    fun `pace is minutes per kilometre`() {
        assertThat(run(minutes = 60, distanceM = 10_000).paceSecondsPerKm).isEqualTo(360)
        assertThat(run(minutes = 30, distanceM = 5_000).paceSecondsPerKm).isEqualTo(360)
    }

    @Test
    fun `without a distance there is no pace`() {
        assertThat(run(minutes = 30, distanceM = null).paceSecondsPerKm).isNull()
        assertThat(run(minutes = 30, distanceM = 0).paceSecondsPerKm).isNull()
    }

    /** Stored as strings (D4's pattern); a value nobody recognises reads back as such, never crashes. */
    @Test
    fun `an unknown kind reads back as unrecognised`() {
        assertThat(WorkoutKind.parse("RUN")).isEqualTo(WorkoutKind.RUN)
        assertThat(WorkoutKind.parse("SKATEBOARD")).isEqualTo(WorkoutKind.UNRECOGNISED)
        assertThat(WorkoutKind.parse(null)).isEqualTo(WorkoutKind.UNRECOGNISED)
    }
}
```

- [ ] **Step 2 — run it; expected: compilation failure (`Workout` unresolved).**
- [ ] **Step 3 — implement**

```kotlin
package com.metaself.app.domain.movement

import kotlin.math.roundToInt

/**
 * What a workout was. Stored as a string, never an ordinal (the same reason as a food's [Source]):
 * an ordinal is meaningless the moment somebody reorders the enum.
 */
enum class WorkoutKind {
    RUN, WALK, CYCLE, SWIM, STRENGTH, OTHER,

    /** A value this version does not know. Shown as "Exercise", never counted as a run. */
    UNRECOGNISED;

    companion object {
        fun parse(stored: String?): WorkoutKind =
            entries.firstOrNull { it.name == stored && it != UNRECOGNISED } ?: UNRECOGNISED
    }
}

/** How hard it felt. Only a workout the owner typed has one; a band's session is what it is. */
enum class Effort { EASY, MODERATE, HARD }

/**
 * Where a workout's energy figure came from (D4). A band's own number, a formula's guess from kind
 * and effort, the owner's typed figure, or nothing — and the screen says which.
 */
enum class EnergySource { BAND, MET_ESTIMATE, TYPED, NONE }

/** Whether the band recorded it or the owner did. */
enum class WorkoutSource { SYNCED, TYPED }

/**
 * One workout, as the rest of the app reasons about it.
 *
 * Pure: no Room annotations, no Health Connect types. The entity that stores it arrives in phase 2
 * and maps to and from this.
 */
data class Workout(
    val id: Long,
    val epochDay: Long,
    val startedAtMillis: Long,
    val durationMinutes: Int,
    val kind: WorkoutKind,
    val title: String?,
    val distanceM: Int?,
    val energyKcal: Int?,
    val energySource: EnergySource,
    val effort: Effort?,
    val source: WorkoutSource,
    val hidden: Boolean,
    val note: String?,
) {
    /** Seconds per kilometre — "5:30 /km" on screen — or null without a distance. */
    val paceSecondsPerKm: Int?
        get() = distanceM?.takeIf { it > 0 && durationMinutes > 0 }
            ?.let { (durationMinutes * 60.0 / (it / 1000.0)).roundToInt() }
}
```

- [ ] **Step 4 — run `WorkoutTest`; expected PASS.**
- [ ] **Step 5 — commit:** `git add` the two files;
  `feat(movement): the workout model (D59, D61)`.

---

## Task 2: the MET estimate

**Files:** create `MetEstimate.kt`, test `MetEstimateTest.kt`.

Every MET value below is transcribed from the **2024 Adult Compendium of Physical Activities**
(pacompendium.com), read 2026-09-25, with its activity code. The estimate is **net of resting** —
one MET subtracted — so it sits on the same footing as `MovementCredit.KCAL_PER_STEP_PER_KG`, which
is also net. The three-quarters discount is applied downstream by `MovementCredit`, not here.

```
netKcal = (MET − 1) × weightKg × durationMinutes / 60, rounded
```

Worked figures on the 80 kg body (these are what the tests assert):

| Case | MET (code) | Arithmetic | kcal |
|---|---|---|---|
| Strength, moderate, 45 min | 3.5 (02054) | 2.5 × 80 × 0.75 | 150 |
| Strength, easy, 30 min | 2.8 (02024) | 1.8 × 80 × 0.5 | 72 |
| Strength, hard, 60 min | 6.0 (02050) | 5.0 × 80 × 1 | 400 |
| Run, 10 km in 60 min (10 km/h = 6.21 mph → row "6–6.3 mph") | 9.3 (12050) | 8.3 × 80 × 1 | 664 |
| Run, 5 km in 30 min (same speed) | 9.3 (12050) | 8.3 × 80 × 0.5 | 332 |
| Run, 10 km in 50 min (12 km/h = 7.46 mph → row "7 mph") | 11.0 (12070) | 10 × 80 × 50/60 | 667 |
| Run, no distance, moderate, 30 min | 9.3 (12050) | 8.3 × 80 × 0.5 | 332 |
| Run, no distance, easy, 30 min | 7.5 (12020) | 6.5 × 80 × 0.5 | 260 |
| Run, no distance, hard, 30 min | 11.8 (12080) | 10.8 × 80 × 0.5 | 432 |
| Walk, 5 km in 60 min (5 km/h = 3.11 mph → row "2.8–3.4 mph") | 3.8 (17190) | 2.8 × 80 × 1 | 224 |
| Walk, no distance, easy / moderate / hard, 60 min | 3.0 (17170) / 3.8 (17190) / 4.8 (17200) | | 160 / 224 / 304 |
| Cycle, 20 km in 60 min (12.4 mph → row "12–13.9 mph") | 8.0 (01030) | 7 × 80 × 1 | 560 |
| Cycle, no distance, easy / moderate / hard, 60 min | 3.5 (01018) / 7.0 (01014) / 10.0 (01040) | | 200 / 480 / 720 |
| Swim, easy / moderate / hard, 30 min | 5.8 (18240) / 6.0 (18310) / 9.8 (18230) | | 192 / 200 / 352 |
| Other, easy / moderate / hard, 30 min | 2.8 (02024) / 5.5 (02060) / 7.5 (02020) | | 72 / 180 / 260 |
| Anything, 0 min | | | 0 |

- [ ] **Step 1 — failing test**

```kotlin
package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * What a typed workout cost, from the Compendium's MET table, net of resting.
 *
 * Every expected figure is worked in the plan
 * (`docs/superpowers/plans/2026-09-25-a-typed-workout-is-a-third-reading.md`, Task 2) on the
 * standard 80 kg body.
 */
class MetEstimateTest {

    private val weightKg = 80.0

    private fun kcal(
        kind: WorkoutKind,
        effort: Effort = Effort.MODERATE,
        minutes: Int,
        distanceM: Int? = null,
    ) = MetEstimate.netKcal(kind, effort, minutes, distanceM, weightKg)

    @Test
    fun `a moderate strength session is the spec's worked example`() {
        assertThat(kcal(WorkoutKind.STRENGTH, Effort.MODERATE, minutes = 45)).isEqualTo(150)
    }

    @Test
    fun `strength follows the felt effort`() {
        assertThat(kcal(WorkoutKind.STRENGTH, Effort.EASY, minutes = 30)).isEqualTo(72)
        assertThat(kcal(WorkoutKind.STRENGTH, Effort.HARD, minutes = 60)).isEqualTo(400)
    }

    /** With a distance, a run's MET follows its pace and the effort is ignored. */
    @Test
    fun `a run with a distance is priced by its pace`() {
        assertThat(kcal(WorkoutKind.RUN, Effort.EASY, minutes = 60, distanceM = 10_000)).isEqualTo(664)
        assertThat(kcal(WorkoutKind.RUN, Effort.HARD, minutes = 30, distanceM = 5_000)).isEqualTo(332)
        assertThat(kcal(WorkoutKind.RUN, minutes = 50, distanceM = 10_000)).isEqualTo(667)
    }

    @Test
    fun `a run without a distance follows the felt effort`() {
        assertThat(kcal(WorkoutKind.RUN, Effort.EASY, minutes = 30)).isEqualTo(260)
        assertThat(kcal(WorkoutKind.RUN, Effort.MODERATE, minutes = 30)).isEqualTo(332)
        assertThat(kcal(WorkoutKind.RUN, Effort.HARD, minutes = 30)).isEqualTo(432)
    }

    @Test
    fun `a walk is priced by pace when it has one, else by effort`() {
        assertThat(kcal(WorkoutKind.WALK, minutes = 60, distanceM = 5_000)).isEqualTo(224)
        assertThat(kcal(WorkoutKind.WALK, Effort.EASY, minutes = 60)).isEqualTo(160)
        assertThat(kcal(WorkoutKind.WALK, Effort.MODERATE, minutes = 60)).isEqualTo(224)
        assertThat(kcal(WorkoutKind.WALK, Effort.HARD, minutes = 60)).isEqualTo(304)
    }

    @Test
    fun `cycling is priced by speed when it has one, else by effort`() {
        assertThat(kcal(WorkoutKind.CYCLE, minutes = 60, distanceM = 20_000)).isEqualTo(560)
        assertThat(kcal(WorkoutKind.CYCLE, Effort.EASY, minutes = 60)).isEqualTo(200)
        assertThat(kcal(WorkoutKind.CYCLE, Effort.MODERATE, minutes = 60)).isEqualTo(480)
        assertThat(kcal(WorkoutKind.CYCLE, Effort.HARD, minutes = 60)).isEqualTo(720)
    }

    /** A swim's distance is not priced: the table's rows are by effort, and a band reports it anyway. */
    @Test
    fun `swimming and other follow the felt effort only`() {
        assertThat(kcal(WorkoutKind.SWIM, Effort.EASY, minutes = 30)).isEqualTo(192)
        assertThat(kcal(WorkoutKind.SWIM, Effort.MODERATE, minutes = 30, distanceM = 1_000)).isEqualTo(200)
        assertThat(kcal(WorkoutKind.SWIM, Effort.HARD, minutes = 30)).isEqualTo(352)
        assertThat(kcal(WorkoutKind.OTHER, Effort.EASY, minutes = 30)).isEqualTo(72)
        assertThat(kcal(WorkoutKind.OTHER, Effort.MODERATE, minutes = 30)).isEqualTo(180)
        assertThat(kcal(WorkoutKind.OTHER, Effort.HARD, minutes = 30)).isEqualTo(260)
    }

    @Test
    fun `nothing costs nothing`() {
        assertThat(kcal(WorkoutKind.RUN, minutes = 0)).isEqualTo(0)
        assertThat(kcal(WorkoutKind.RUN, minutes = 0, distanceM = 5_000)).isEqualTo(0)
        assertThat(kcal(WorkoutKind.UNRECOGNISED, minutes = 30)).isEqualTo(
            kcal(WorkoutKind.OTHER, minutes = 30),
        )
    }

    /** Every kind and effort has a row; none falls through to an exception or a zero. */
    @Test
    fun `every kind and effort has a value`() {
        WorkoutKind.entries.forEach { kind ->
            Effort.entries.forEach { effort ->
                assertThat(kcal(kind, effort, minutes = 30)).isGreaterThan(0)
            }
        }
    }
}
```

- [ ] **Step 2 — run it; expected: compilation failure (`MetEstimate` unresolved).**
- [ ] **Step 3 — implement**

```kotlin
package com.metaself.app.domain.movement

import kotlin.math.roundToInt

/**
 * What a typed workout cost, when no band said.
 *
 * MET values from the 2024 Adult Compendium of Physical Activities, each with its activity code so
 * that a reader can check it against the published table. **Net of resting** — one MET is
 * subtracted — because the daily target already pays for existing, exactly as
 * [MovementCredit.KCAL_PER_STEP_PER_KG] is net. The three-quarters discount of D12 is applied by
 * [MovementCredit] afterwards, not here; a value discounted twice would be wrong in the stingy
 * direction, which is the safe one but still wrong.
 *
 * A run, walk or ride with a distance is priced by its speed, using the table's speed rows; the felt
 * effort is then ignored, because the pace is the better witness. Without a distance the felt effort
 * chooses among three rows. Swimming and everything else are by effort only.
 */
object MetEstimate {

    private data class Row(val code: Int, val met: Double)

    /** A speed row: applies from [atLeastMph] up to the next row's threshold. */
    private data class SpeedRow(val atLeastMph: Double, val code: Int, val met: Double)

    private const val METRES_PER_MILE = 1_609.344

    fun netKcal(
        kind: WorkoutKind,
        effort: Effort,
        durationMinutes: Int,
        distanceM: Int?,
        weightKg: Double,
    ): Int {
        if (durationMinutes <= 0) return 0
        val row = bySpeed(kind, durationMinutes, distanceM) ?: byEffort(kind, effort)
        val hours = durationMinutes / 60.0
        return ((row.met - 1.0) * weightKg * hours).roundToInt()
    }

    private fun bySpeed(kind: WorkoutKind, minutes: Int, distanceM: Int?): Row? {
        val metres = distanceM?.takeIf { it > 0 } ?: return null
        val table = when (kind) {
            WorkoutKind.RUN -> RUNNING_BY_SPEED
            WorkoutKind.WALK -> WALKING_BY_SPEED
            WorkoutKind.CYCLE -> CYCLING_BY_SPEED
            else -> return null
        }
        val mph = (metres / METRES_PER_MILE) / (minutes / 60.0)
        val chosen = table.last { mph >= it.atLeastMph }
        return Row(chosen.code, chosen.met)
    }

    private fun byEffort(kind: WorkoutKind, effort: Effort): Row = when (kind) {
        WorkoutKind.RUN -> when (effort) {
            Effort.EASY -> Row(12020, 7.5)      // Jogging, general, self-selected pace
            Effort.MODERATE -> Row(12050, 9.3)  // Running, 6-6.3 mph (10 min/mile)
            Effort.HARD -> Row(12080, 11.8)     // Running, 7.5 mph (8 min/mile)
        }
        WorkoutKind.WALK -> when (effort) {
            Effort.EASY -> Row(17170, 3.0)      // Walking, 2.5 mph, firm, level surface
            Effort.MODERATE -> Row(17190, 3.8)  // Walking, 2.8 to 3.4 mph, level, moderate pace
            Effort.HARD -> Row(17200, 4.8)      // Walking, 3.5 to 3.9 mph, level, brisk
        }
        WorkoutKind.CYCLE -> when (effort) {
            Effort.EASY -> Row(1018, 3.5)       // Bicycling, leisure 5.5 mph
            Effort.MODERATE -> Row(1014, 7.0)   // Bicycling, general
            Effort.HARD -> Row(1040, 10.0)      // Bicycling, 14-15.9 mph, fast, vigorous effort
        }
        WorkoutKind.SWIM -> when (effort) {
            Effort.EASY -> Row(18240, 5.8)      // Swimming laps, freestyle, slow, recreational
            Effort.MODERATE -> Row(18310, 6.0)  // Swimming, leisurely, not lap swimming, general
            Effort.HARD -> Row(18230, 9.8)      // Swimming laps, freestyle, fast, vigorous effort
        }
        WorkoutKind.STRENGTH -> when (effort) {
            Effort.EASY -> Row(2024, 2.8)       // Calisthenics (curl ups, crunches, plank), light
            Effort.MODERATE -> Row(2054, 3.5)   // Resistance training, multiple exercises, 8-15 reps
            Effort.HARD -> Row(2050, 6.0)       // Resistance, power lifting or body building, vigorous
        }
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> when (effort) {
            Effort.EASY -> Row(2024, 2.8)       // Calisthenics, light effort
            Effort.MODERATE -> Row(2060, 5.5)   // Health club exercise, general
            Effort.HARD -> Row(2020, 7.5)       // Calisthenics, vigorous effort
        }
    }

    /** Compendium running rows by speed, ascending. Below 4 mph a "run" is priced as a jog. */
    private val RUNNING_BY_SPEED = listOf(
        SpeedRow(0.0, 12026, 3.3),   // Jogging 2.6 to 3.7 mph
        SpeedRow(4.0, 12028, 6.5),   // Running, 4 to 4.2 mph (13 min/mile)
        SpeedRow(4.3, 12029, 7.8),   // Running 4.3 to 4.8 mph
        SpeedRow(5.0, 12030, 8.5),   // Running, 5.0 to 5.2 mph (12 min/mile)
        SpeedRow(5.5, 12045, 9.0),   // Running, 5.5-5.8 mph
        SpeedRow(6.0, 12050, 9.3),   // Running, 6-6.3 mph (10 min/mile)
        SpeedRow(6.7, 12060, 10.5),  // Running, 6.7 mph (9 min/mile)
        SpeedRow(7.0, 12070, 11.0),  // Running, 7 mph (8.5 min/mile)
        SpeedRow(7.5, 12080, 11.8),  // Running, 7.5 mph (8 min/mile)
        SpeedRow(8.0, 12090, 12.0),  // Running, 8 mph (7.5 min/mile)
        SpeedRow(8.6, 12100, 12.5),  // Running, 8.6 mph (7 min/mile)
        SpeedRow(9.0, 12110, 13.0),  // Running, 9 mph (6.5 min/mile)
        SpeedRow(9.3, 12115, 14.8),  // Running, 9.3 to 9.6 mph
        SpeedRow(11.0, 12130, 16.8), // Running, 11 mph (5.5 min/mile)
        SpeedRow(12.0, 12132, 18.5), // Running, 12 mph (5.0 min/mile)
    )

    private val WALKING_BY_SPEED = listOf(
        SpeedRow(0.0, 17170, 3.0),   // Walking, 2.5 mph, firm, level surface
        SpeedRow(2.8, 17190, 3.8),   // Walking, 2.8 to 3.4 mph, level, moderate pace
        SpeedRow(3.5, 17200, 4.8),   // Walking, 3.5 to 3.9 mph, level, brisk
        SpeedRow(4.0, 17220, 5.5),   // Walking, 4.0 to 4.4 mph, level, very brisk pace
        SpeedRow(4.5, 17230, 7.0),   // Walking, 4.5 to 4.9 mph, level, very, very brisk
    )

    private val CYCLING_BY_SPEED = listOf(
        SpeedRow(0.0, 1018, 3.5),    // Bicycling, leisure 5.5 mph
        SpeedRow(10.0, 1020, 6.8),   // Bicycling, 10-11.9 mph, leisure, slow, light effort
        SpeedRow(12.0, 1030, 8.0),   // Bicycling, 12-13.9 mph, leisure, moderate effort
        SpeedRow(14.0, 1040, 10.0),  // Bicycling, 14-15.9 mph, racing or leisure, fast, vigorous
        SpeedRow(16.0, 1050, 12.0),  // Bicycling, 16-19 mph, racing/not drafting, very fast
    )
}
```

- [ ] **Step 4 — run `MetEstimateTest`; expected PASS.** If a figure disagrees, the arithmetic in the
  table above is the reference; recompute before changing either side.
- [ ] **Step 5 — commit:** `feat(movement): the MET estimate for a typed workout, cited (D60)`.

---

## Task 3: three readings, never a sum

**Files:** modify `MovementCredit.kt` (`DayMovement`, `MovementSource`), `ActivityEnergy.kt`;
extend `ActivityEnergyTest.kt`.

- [ ] **Step 1 — failing tests**, appended to `ActivityEnergyTest`:

```kotlin
    private fun day(steps: Int, activeKcal: Int? = null, typedKcal: Int = 0) = ActivityEnergy.of(
        DayMovement(epochDay = 1, steps = steps, activeKcal = activeKcal, typedWorkoutsKcal = typedKcal),
        weightKg,
    )
    // (replace the existing two-argument helper with this one; every existing call still compiles)

    /** D60: a run typed by hand on a day the phone also counted its steps is captured once. */
    @Test
    fun `a typed workout is a third reading, not an addition`() {
        val ran = day(steps = 10_000, typedKcal = 350)

        assertThat(ran.kcal).isEqualTo(350)
        assertThat(ran.source).isEqualTo(MovementSource.TYPED_WORKOUT)
        assertThat(ran.kcal).isNotEqualTo(300 + 350)
    }

    @Test
    fun `the band still wins when it is the largest of the three`() {
        val swamAndRan = day(steps = 10_000, activeKcal = 340, typedKcal = 320)

        assertThat(swamAndRan.kcal).isEqualTo(340)
        assertThat(swamAndRan.source).isEqualTo(MovementSource.ACTIVE_CALORIES)
    }

    /** A tie falls to the least-estimated reading, which is the step count. */
    @Test
    fun `a tie falls to the steps`() {
        assertThat(day(steps = 10_000, typedKcal = 300).source).isEqualTo(MovementSource.STEPS)
    }

    @Test
    fun `a day with nothing typed behaves exactly as before`() {
        assertThat(day(steps = 10_000, activeKcal = 340)).isEqualTo(
            ActivityEnergy.of(DayMovement(epochDay = 1, steps = 10_000, activeKcal = 340), weightKg),
        )
    }
```

- [ ] **Step 2 — run `ActivityEnergyTest`; expected: compilation failure (`typedWorkoutsKcal`,
  `TYPED_WORKOUT` unresolved).**
- [ ] **Step 3 — implement.** In `MovementCredit.kt`:

```kotlin
data class DayMovement(
    val epochDay: Long,
    val steps: Int,
    val activeKcal: Int? = null,
    val sessions: List<ExerciseSession> = emptyList(),
    /**
     * What the workouts the owner typed for this day cost, by [MetEstimate] or his own figure —
     * summed across them, since two typed sessions are two different things done. Zero until the
     * store that holds typed workouts exists (phase 2). It is a THIRD reading beside steps and the
     * band, never added to either (D60).
     */
    val typedWorkoutsKcal: Int = 0,
)
```

and in `MovementSource`, after `ACTIVE_CALORIES`:

```kotlin
    /** What the owner's typed workouts cost, by the MET table, which beat both the other readings. */
    TYPED_WORKOUT,
```

In `ActivityEnergy.kt`, replace the body of `of`:

```kotlin
        fun of(day: DayMovement, weightKg: Double): ActivityEnergy {
            val fromSteps = (day.steps * MovementCredit.KCAL_PER_STEP_PER_KG * weightKg).roundToInt()

            // Ordered least-estimated first, so a tie resolves to the steps: maxBy keeps the FIRST
            // of equal elements.
            return listOf(
                ActivityEnergy(fromSteps, MovementSource.STEPS),
                ActivityEnergy(day.activeKcal ?: 0, MovementSource.ACTIVE_CALORIES),
                ActivityEnergy(day.typedWorkoutsKcal, MovementSource.TYPED_WORKOUT),
            ).maxBy { it.kcal }
        }
```

Update the class KDoc's first line to "The largest of THREE readings, never their sum (D12b, D60)"
and add one sentence: "A workout the owner typed is the third; a run typed by hand on a day the phone
also counted its steps is captured once, by whichever reading is larger."

- [ ] **Step 4 — run the whole `domain/movement` package; expected: every test PASS**, including the
  unchanged `MovementCreditTest` and `NormalDayTest`.
- [ ] **Step 5 — commit:** `feat(movement): a typed workout is a third reading, never a sum (D60)`.

---

## Task 4: the "against usual" line speaks in the deciding currency

**Files:** modify `MovementToday.kt`, `MovementWording.kt`; extend `MovementWordingTest.kt`.

The KNOWN GAP from milestone 1 §6: the credit is decided on energy (the D12b maximum), but the line
under the count compares steps. On a swimming day the two say opposite things. The fix: when the
reading that decided the day is not the steps, the line speaks in kcal of movement.

Wording, exactly:

| Day | Deciding reading | Line |
|---|---|---|
| Above usual | steps | `3,800 more than your usual 5,200` (unchanged) |
| Quiet | steps | `Your usual day is 5,200` (unchanged) |
| Above usual | band or typed workout | `180 kcal more movement than your usual 400` |
| Quiet | band or typed workout | `Your usual day is 400 kcal of movement` |

- [ ] **Step 1 — failing tests**, appended to `MovementWordingTest` (the file's `today()` helper at
  the bottom stays; add a second helper):

```kotlin
    private fun bandDay(steps: Int, energyKcal: Int, normalEnergy: Int?, normalSteps: Int? = 5_200) =
        MovementToday(
            steps = steps,
            normalSteps = normalSteps,
            energy = ActivityEnergy(energyKcal, MovementSource.ACTIVE_CALORIES),
            normalEnergyKcal = normalEnergy,
        )

    /**
     * The KNOWN GAP of milestone 1 §6: on a swimming day the steps barely move, so a line that
     * compares steps reads "quiet" while the band's figure is earning calories underneath. The line
     * speaks in whichever reading decided the credit.
     */
    @Test
    fun `a band-driven day above usual speaks in movement energy`() {
        assertThat(MovementWording.againstUsual(bandDay(steps = 900, energyKcal = 580, normalEnergy = 400)))
            .isEqualTo("180 kcal more movement than your usual 400")
    }

    @Test
    fun `a band-driven quiet day states the usual in the same currency`() {
        val text = MovementWording.againstUsual(bandDay(steps = 900, energyKcal = 300, normalEnergy = 400))!!

        assertThat(text).isEqualTo("Your usual day is 400 kcal of movement")
        assertThat(text.lowercase()).doesNotContain("behind")
        assertThat(text.lowercase()).doesNotContain("burn")
    }

    @Test
    fun `a typed-workout day speaks in the same currency as a band day`() {
        val typed = MovementToday(
            steps = 900,
            normalSteps = 5_200,
            energy = ActivityEnergy(580, MovementSource.TYPED_WORKOUT),
            normalEnergyKcal = 400,
        )

        assertThat(MovementWording.againstUsual(typed))
            .isEqualTo("180 kcal more movement than your usual 400")
    }

    /** A step-driven day is worded exactly as before, whether or not the energy is known. */
    @Test
    fun `a step-driven day still speaks in steps`() {
        val stepDay = MovementToday(
            steps = 9_000,
            normalSteps = 5_200,
            energy = ActivityEnergy(270, MovementSource.STEPS),
            normalEnergyKcal = 156,
        )

        assertThat(MovementWording.againstUsual(stepDay)).isEqualTo("3,800 more than your usual 5,200")
    }

    @Test
    fun `a band-driven day before the usual energy is known falls back to steps`() {
        assertThat(MovementWording.againstUsual(bandDay(steps = 9_000, energyKcal = 580, normalEnergy = null)))
            .isEqualTo("3,800 more than your usual 5,200")
    }
```

Also add `"kcal of movement"`-bearing lines to the *nothing claims he burned* test's `everything`
list: `MovementWording.againstUsual(bandDay(900, 580, 400))` and
`MovementWording.againstUsual(bandDay(900, 300, 400))`.

- [ ] **Step 2 — run `MovementWordingTest`; expected: compilation failure (`energy`,
  `normalEnergyKcal` unresolved on `MovementToday`).**
- [ ] **Step 3 — implement.** In `MovementToday.kt`, two new defaulted properties after `sessions`:

```kotlin
    /**
     * What the day's movement was worth and which reading decided it — the D12b maximum. Null when
     * the day has no record. The line under the count speaks in this reading's currency, because a
     * band-driven day described in steps says the opposite of what it did (milestone 1 §6).
     */
    val energy: ActivityEnergy? = null,
    /** The usual day in that same currency; null while still learning. */
    val normalEnergyKcal: Int? = null,
```

and one derived property beside `extraSteps`:

```kotlin
    /** True when something other than the step count decided the day's movement. */
    val decidedByEnergy: Boolean
        get() = energy != null && energy.source != MovementSource.STEPS && normalEnergyKcal != null
```

In `MovementWording.kt`, replace `againstUsual`:

```kotlin
    /**
     * How today stands against a usual day, in plain words — and **in whichever reading decided
     * the credit.** A swimming day moves no steps, so comparing steps would call it quiet while the
     * band's figure was earning calories underneath; on such a day the line speaks in kcal of
     * movement instead. A day with no band is worded exactly as it always was.
     *
     * Never a reproach. A quiet day says what a usual day is and stops; it does not say he is
     * behind, because a target that already assumes normal movement is not owed anything by a
     * quiet Tuesday.
     */
    fun againstUsual(today: MovementToday?): String? {
        if (today?.recorded == false) return null
        if (today?.decidedByEnergy == true) return againstUsualEnergy(today)
        val usual = today?.normalSteps ?: return null
        val extra = today.extraSteps ?: return null
        return if (extra > 0) {
            "${number(extra)} more than your usual ${number(usual)}"
        } else {
            "Your usual day is ${number(usual)}"
        }
    }

    private fun againstUsualEnergy(today: MovementToday): String? {
        val energy = today.energy ?: return null
        val usual = today.normalEnergyKcal ?: return null
        val extra = (energy.kcal - usual).coerceAtLeast(0)
        return if (extra > 0) {
            "${number(extra)} kcal more movement than your usual ${number(usual)}"
        } else {
            "Your usual day is ${number(usual)} kcal of movement"
        }
    }
```

- [ ] **Step 4 — run the `domain/movement` package; expected: all PASS**, the existing steps-worded
  tests unchanged.
- [ ] **Step 5 — commit:** `fix(movement): the line under the count speaks in the reading that decided the credit`.

---

## Task 5: the day fills the two new fields

**Files:** modify `DayViewModel.kt:887-909`; extend `DayScreenRenderTest.kt`.

- [ ] **Step 1 — failing render test**, added to `DayScreenRenderTest` beside *a busy day shows the
  steps, the surplus and what it earned* (reuse that test's `draw(meals = emptyList(), movement = …)`
  helper):

```kotlin
    /**
     * The KNOWN GAP of milestone 1 §6, closed: on a day the band's figure decided the credit, the
     * line beneath the count speaks in that figure, not in steps.
     */
    @Test
    fun `a band-driven day explains itself in movement energy`() {
        val swam = MovementToday(
            steps = 900,
            normalSteps = 5_200,
            energy = ActivityEnergy(580, MovementSource.ACTIVE_CALORIES),
            normalEnergyKcal = 400,
            sessions = listOf(ExerciseSession("Swimming", 45)),
        )

        val texts = draw(meals = emptyList(), movement = swam)

        assertThat(texts).contains("900 steps")
        assertThat(texts).contains("Swimming · 45 min")
        assertThat(texts).contains("180 kcal more movement than your usual 400")
        assertThat(texts).doesNotContain("Your usual day is 5,200")
    }
```

Imports to add at the top of the file if absent: `com.metaself.app.domain.movement.ActivityEnergy`,
`…domain.movement.ExerciseSession`, `…domain.movement.MovementSource`.

This passes as soon as Task 4 compiles, because the render draws whatever `MovementToday` carries;
it is here to pin the screen, and the view-model change below is what makes it true on the phone.

- [ ] **Step 2 — implement** in `DayViewModel.kt`, the `walked` construction (currently ~line 896):

```kotlin
                val walked = inputs.movement?.byDay?.get(inputs.day)?.let { day ->
                    val normalEnergy = inputs.movement.normalEnergyKcal
                    val energy = ActivityEnergy.of(day, inputs.movement.weightKg)
                    MovementToday(
                        steps = day.steps,
                        normalSteps = inputs.movement.normalSteps,
                        sessions = day.sessions,
                        energy = energy,
                        normalEnergyKcal = normalEnergy,
                        credit = if (isToday && normalEnergy != null) {
                            MovementCredit.of(
                                today = energy,
                                normalEnergyKcal = normalEnergy,
                                capKcal = inputs.movement.capKcal,
                            ).takeIf { it.kcal > 0 }
                        } else {
                            null
                        },
                    )
                }
```

The `nothingYet` construction (~line 887) is unchanged: a day with no record has no energy.

- [ ] **Step 3 — run the full unit suite**, redirected, and check the exit code:

```
export ANDROID_HOME=/home/ubuntu/android-sdk
~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/claude-1001/-home-ubuntu-projects-metaself-app/f76400c8-6a34-438f-ad01-a71ea0a2333f/scratchpad/suite.log 2>&1; echo "exit $?"
grep -c "SKIPPED" /tmp/claude-1001/-home-ubuntu-projects-metaself-app/f76400c8-6a34-438f-ad01-a71ea0a2333f/scratchpad/suite.log   # expected: exactly the eight database classes, nothing else
```

Expected: exit 0; the render test passes; `DayViewModelTest` and `SimulatedApp`-based tests
unchanged (the `StepSource` fakes build `DayMovement` with defaults).

- [ ] **Step 4 — commit:** `feat(day): the step line carries the reading that decided it`.

---

## Task 6: verify, version, hand over

- [ ] **Step 1 — lint:** `~/bin/gradlew-safe :app:lintDebug > /tmp/claude-1001/-home-ubuntu-projects-metaself-app/f76400c8-6a34-438f-ad01-a71ea0a2333f/scratchpad/lint.log 2>&1; echo "exit $?"` — expected exit 0.
- [ ] **Step 2 — the anonymisation read:** read every new test name and comment as published prose.
  Fixture figures are the 80 kg body's and the invented 5,200-step / 400-kcal usual day; no
  distance, pace or frequency describes a person.
- [ ] **Step 3 — version:** `app/build.gradle.kts` → `versionCode 107`, `versionName "0.53.0"`.
  Commit: `MetaSelf 0.53.0`.
- [ ] **Step 4 — release build** with `~/bin/ms-release` (never `assembleRelease`); report the APK path.
- [ ] **Step 5 — push the branch and open the pull request** titled
  `0.53.0: a typed workout is a third reading (D59–D64, phase 1)`, body listing the four behaviours
  above and the one visible change (the band-day line). Attribution per the project's rule: the
  `Co-Authored-By` trailer and the "Generated with Claude Code" line; **no session link**.

---

## Not built in this phase

- No `workouts` table, no migration, no backup change — phase 2.
- `DayMovement.typedWorkoutsKcal` is always zero on the phone until phase 4 logs a workout; the
  third reading is real in the arithmetic and dormant on screen.
- No `<queries>` entry, no `READ_DISTANCE` — phase 3.
- The MET table prices a swim by effort only; the Compendium's swimming rows are by stroke and
  effort, not speed.

## What the owner sees after this phase

Nothing new on an ordinary day. On a day when the band's calories beat the step count, the line
under the step count reads in kcal of movement — the first visible sign of the module, and the
closing of a gap recorded three weeks ago.
