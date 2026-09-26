# The health record is copied — Implementation Plan (health record, phase 2)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans, task by task. Steps use checkbox syntax for tracking.

**Goal:** on every app open and pull-to-refresh, copy every health kind the owner allows out of Health
Connect into the phase-1 tables — only what changed, newest first, spread across opens — then compute
each touched day's summary and each touched workout's heart-rate figures, and say in Settings how far
the record reaches. Ten new read permissions; one status line; nothing else new on screen.

**Architecture:** four layers, the logic in the two that run anywhere.

```
domain/health/      HealthKind, HeartRateZones                          pure, JUnit 5
data/health/        ReadRecord (+ Sample, StageSpan), HealthRows,       pure, JUnit 5
                    DaySummary, WorkoutKinds, HealthRecordSync,
                    HealthRecordWording (ui/health/)
data/health/        RoomHealthStore (implements HealthStore)            Room, CI only
data/health/        HealthConnectReader (implements HealthSource)       Health Connect, thin, untested
```

`HealthRecordSync` holds every decision (token or window, catch-up slice, budget, refusal) and talks
only to two interfaces, `HealthSource` and `HealthStore`, so it is tested with fakes on any machine.
`RoomHealthStore` applies batches in one transaction and computes summaries through the pure
`DaySummary` and `HeartRateZones`. `HealthConnectReader` only translates Health Connect's types into
`ReadRecord`s, in the pattern of `HealthConnectSteps`.

**Decision:** the owner's, 2026-09-26 — D65 to D72 in
`docs/superpowers/specs/2026-09-26-health-record-design.md`; this is §5 phase 2. It also carries two
review notes from phase 1: a restore clears the copying bookmarks, and the export reads a night and
its stages together.

**Tech stack:** Kotlin 1.9.22, Room 2.6.1, Health Connect client `1.1.0-alpha07` (pinned, D64/D72),
Hilt, Compose. JUnit 5 + Truth for pure code; JUnit 4 + Robolectric only for the Room store.

**Red lines (stop and report if crossed):**

- **Nothing here may throw upwards (D8).** Every Health Connect failure is a `ProblemLog` entry of kind
  `"health"` and an empty or partial result. The owner logging lunch never waits on this.
- **The day's steps, distance and calories come from the aggregation API, never from summing raw rows
  (D12c, D69).** Raw rows are stored; totals are asked for.
- **No estimate is presented as a measurement (D4).** Every summary figure carries its source; zones say
  their maximum was ESTIMATED.
- **D12's step credit is untouched.** `HealthConnectSteps`, `StepSource`, `readMovement()` do not change
  behaviour. `HealthConnectSteps.PERMISSIONS` stays the three it is; the launcher asks for the new, larger
  set.
- **No schema change.** Phase 1's tables are enough; only DAO queries are added. `6.json` must not change
  (`git status app/schemas` clean after every build).
- **Every number written into code as a limit is a choice, and its comment says so.** None is presented
  as Health Connect's documented quota, which this project has not measured.
- **Never `git add -A`. Never bare `./gradlew`.**
- **Anonymisation:** every fixture figure invented and round, and says so; no heart rate, sleep length,
  step count or frequency that reads as a person's; test names too. Use `com.example.band` as the origin.

---

## The shared box — read before any build

Another project on this machine runs Gradle builds too. `~/bin/gradlew-safe` and `~/bin/ms-release`
take one shared lock, but a finished build's idle daemon stays resident up to ten minutes. **Before
every Gradle command, run `free -m`;** if "available" is under about 4000 MB (about 6000 MB before
`ms-release`), wait and check again. Never `gradlew --stop`. Never two builds at once.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-health2.log 2>&1; echo "exit $?"
```

SQLite-backed classes **skip** on this box; report them as skipped. After this phase the local skip list
is ten classes: the nine in `CLAUDE.md` plus `HealthRecordStoreTest` (Task 11 updates `CLAUDE.md`).

---

## File structure

| File | Responsibility |
|---|---|
| Create `app/src/main/java/com/metaself/app/domain/health/HealthKind.kt` | the thirteen kinds, reading units, display names |
| Create `app/src/main/java/com/metaself/app/domain/health/HeartRateZones.kt` | estimated maximum; avg, max, five zone totals |
| Create `app/src/main/java/com/metaself/app/data/health/ReadRecord.kt` | what the reader hands over, free of Health Connect types |
| Create `app/src/main/java/com/metaself/app/data/health/HealthRows.kt` | `ReadRecord` → entity rows, days by local zone |
| Create `app/src/main/java/com/metaself/app/data/health/WorkoutKinds.kt` | Health Connect exercise type → `WorkoutKind` name |
| Create `app/src/main/java/com/metaself/app/data/health/SleepNight.kt` | a night with its stages (`@Relation`) |
| Create `app/src/main/java/com/metaself/app/data/health/DaySummary.kt` | one day's rows → one `HealthDayEntity` |
| Create `app/src/main/java/com/metaself/app/data/health/HealthPorts.kt` | `HealthSource`, `HealthStore`, `ChangesPage`, `DayTotals`, `HealthRecordCopier`, `HealthRecordStatus` |
| Create `app/src/main/java/com/metaself/app/data/health/HealthRecordSync.kt` | the copying decisions |
| Create `app/src/main/java/com/metaself/app/data/health/RoomHealthStore.kt` | applying batches, summarising days |
| Create `app/src/main/java/com/metaself/app/data/health/HealthConnectReader.kt` | Health Connect → `ReadRecord` |
| Create `app/src/main/java/com/metaself/app/data/health/HealthPermissions.kt` | the thirteen read permissions |
| Create `app/src/main/java/com/metaself/app/ui/health/HealthRecordWording.kt` | the Settings lines |
| Modify the six DAOs in `data/health/` | queries the store needs |
| Modify `app/src/main/AndroidManifest.xml` | ten permissions, `<queries>` |
| Modify `app/src/main/java/com/metaself/app/di/DataModule.kt` | bindings |
| Modify `app/src/main/java/com/metaself/app/ui/screen/day/DayViewModel.kt` | copy on open and refresh |
| Modify `app/src/main/java/com/metaself/app/ui/screen/settings/SettingsViewModel.kt`, `SettingsScreen.kt`, `SettingsUiState` | the status line and Connect |
| Modify `app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt` | the launcher asks for all thirteen |
| Modify `app/src/main/java/com/metaself/app/data/backup/BackupRepository.kt` | restore clears bookmarks; export reads nights whole |
| Modify `app/src/main/res/values/strings.xml` | Connect button text |
| Modify `app/build.gradle.kts` | 109 / 0.55.0 |
| Tests: `HealthKindTest`, `HeartRateZonesTest`, `HealthRowsTest`, `WorkoutKindsTest`, `DaySummaryTest`, `HealthRecordSyncTest`, `HealthRecordWordingTest` (JUnit 5); `HealthRecordStoreTest` (Robolectric, CI); `BackupRestoreOrderTest` (extended) | |

---

### Task 1: The kinds, and heart-rate zones

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/health/HealthKind.kt`
- Create: `app/src/main/java/com/metaself/app/domain/health/HeartRateZones.kt`
- Test: `app/src/test/java/com/metaself/app/domain/health/HealthKindTest.kt`
- Test: `app/src/test/java/com/metaself/app/domain/health/HeartRateZonesTest.kt`

- [ ] **Step 0: Branch.** `git checkout main && git pull && git checkout -b the-health-record-is-copied`,
  then `git add docs/superpowers/plans/2026-09-26-the-health-record-is-copied.md && git commit -m "docs: the health record's phase 2 plan"` (message ending with the co-author line, as every commit here).

- [ ] **Step 1: Failing tests.**

```kotlin
package com.metaself.app.domain.health

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HealthKindTest {

    @Test
    fun `there are thirteen kinds, eleven of them simple readings`() {
        assertThat(HealthKind.entries).hasSize(13)
        assertThat(HealthKind.entries.filter { it.isReading }).hasSize(11)
        assertThat(HealthKind.SLEEP.isReading).isFalse()
        assertThat(HealthKind.EXERCISE.isReading).isFalse()
    }

    @Test
    fun `every reading has a unit, and nothing else does`() {
        HealthKind.entries.forEach { kind ->
            assertThat(kind.unit != null).isEqualTo(kind.isReading)
        }
    }

    @Test
    fun `a stored name reads back, and an unknown one does not`() {
        assertThat(HealthKind.parse("HEART_RATE")).isEqualTo(HealthKind.HEART_RATE)
        assertThat(HealthKind.parse("STRESS")).isNull()
    }
}
```

```kotlin
package com.metaself.app.domain.health

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Every figure is invented and round. */
class HeartRateZonesTest {

    @Test
    fun `the estimated maximum is 220 minus age`() {
        // The standard fixture body, born 1980, in 2026.
        assertThat(HeartRateZones.estimatedMax(birthYear = 1980, currentYear = 2026)).isEqualTo(174)
    }

    @Test
    fun `nothing measured is nothing, not zero`() {
        assertThat(HeartRateZones.of(emptyList(), endMillis = 60_000, maxHeartRate = 200)).isNull()
    }

    /**
     * Maximum 200, so the zones start at 100, 120, 140, 160 and 180 bpm. Each sample lasts until the
     * next one, and the last until the session ends.
     */
    @Test
    fun `each sample's time lands in its zone`() {
        val figures = HeartRateZones.of(
            samples = listOf(0L to 110.0, 60_000L to 130.0, 120_000L to 190.0),
            endMillis = 180_000,
            maxHeartRate = 200,
        )!!

        assertThat(figures.zoneSeconds).containsExactly(60, 60, 0, 0, 60).inOrder()
        assertThat(figures.average).isEqualTo(143)
        assertThat(figures.maximum).isEqualTo(190)
    }

    @Test
    fun `below half the maximum is in no zone, at or above the maximum is zone 5`() {
        val figures = HeartRateZones.of(
            samples = listOf(0L to 90.0, 60_000L to 210.0),
            endMillis = 120_000,
            maxHeartRate = 200,
        )!!

        assertThat(figures.zoneSeconds).containsExactly(0, 0, 0, 0, 60).inOrder()
    }

    /** A gap with no sample is not time spent at the last rate seen. */
    @Test
    fun `a sample never counts for longer than the longest span allowed`() {
        val figures = HeartRateZones.of(
            samples = listOf(0L to 110.0),
            endMillis = 3_600_000,
            maxHeartRate = 200,
        )!!

        assertThat(figures.zoneSeconds[0]).isEqualTo(HeartRateZones.LONGEST_SAMPLE_SECONDS)
    }

    @Test
    fun `zones are written as five comma-separated totals`() {
        assertThat(HeartRateZones.Figures(140, 160, listOf(0, 300, 900, 600, 0)).zonesAsText())
            .isEqualTo("0,300,900,600,0")
    }
}
```

- [ ] **Step 2: Run, see them fail.** `--tests "com.metaself.app.domain.health.*"` → compilation errors.

- [ ] **Step 3: `HealthKind.kt`:**

```kotlin
package com.metaself.app.domain.health

/**
 * Every kind of health data the record copies (D66). Stored by name, never by ordinal.
 *
 * Eleven are simple readings — a value at a moment or over a span, kept in the shared table (D68) —
 * and carry the [unit] their values are in. Sleep and workouts have structure and tables of their own.
 */
enum class HealthKind(val unit: String?, val displayName: String) {
    STEPS("count", "Steps"),
    DISTANCE("m", "Distance"),
    ACTIVE_KCAL("kcal", "Active calories"),
    TOTAL_KCAL("kcal", "Total calories"),
    HEART_RATE("bpm", "Heart rate"),
    RESTING_HEART_RATE("bpm", "Resting heart rate"),
    HRV_RMSSD("ms", "Heart-rate variability"),
    OXYGEN_SATURATION("%", "Blood oxygen"),
    RESPIRATORY_RATE("breaths/min", "Breathing rate"),
    WEIGHT("kg", "Weight"),
    BODY_FAT("%", "Body fat"),
    SLEEP(null, "Sleep"),
    EXERCISE(null, "Workouts");

    val isReading: Boolean get() = unit != null

    companion object {
        fun parse(stored: String?): HealthKind? = entries.firstOrNull { it.name == stored }
    }
}
```

- [ ] **Step 4: `HeartRateZones.kt`:**

```kotlin
package com.metaself.app.domain.health

import kotlin.math.roundToInt

/**
 * Heart-rate figures for a stretch of time — a workout — against a maximum (D70).
 *
 * The maximum is `220 − age` until the trainer offers an observed one, and is always labelled
 * ESTIMATED where it is stored. Zones are the common five bands at 50–60, 60–70, 70–80, 80–90 and
 * 90–100 % of the maximum; a rate at or above the maximum counts in zone 5, one below half of it in
 * none.
 */
object HeartRateZones {

    /**
     * The longest one sample may stand for. A band that stops reporting for twenty minutes has not
     * measured twenty minutes at its last rate. **A choice**, not a published figure: long enough for
     * a band that samples every few minutes, short enough that a gap is not invented.
     */
    const val LONGEST_SAMPLE_SECONDS = 300

    fun estimatedMax(birthYear: Int, currentYear: Int): Int = 220 - (currentYear - birthYear)

    /**
     * @property average the plain mean of the samples, rounded — not weighted by time.
     * @property zoneSeconds five totals, zone 1 to 5.
     */
    data class Figures(val average: Int, val maximum: Int, val zoneSeconds: List<Int>) {
        fun zonesAsText(): String = zoneSeconds.joinToString(",")
    }

    /**
     * @param samples moment in epoch millis to beats per minute, in time order.
     * @param endMillis when the stretch ends; the last sample lasts until then.
     * @return null when there are no samples: nothing measured is not zero.
     */
    fun of(samples: List<Pair<Long, Double>>, endMillis: Long, maxHeartRate: Int): Figures? {
        if (samples.isEmpty()) return null
        val zones = IntArray(5)
        samples.forEachIndexed { index, (at, bpm) ->
            val until = samples.getOrNull(index + 1)?.first ?: endMillis
            val seconds = ((until - at) / 1000).toInt().coerceIn(0, LONGEST_SAMPLE_SECONDS)
            zoneOf(bpm, maxHeartRate)?.let { zones[it] += seconds }
        }
        return Figures(
            average = samples.map { it.second }.average().roundToInt(),
            maximum = samples.maxOf { it.second }.roundToInt(),
            zoneSeconds = zones.toList(),
        )
    }

    /** 0 to 4 for zones 1 to 5, or null below half the maximum. */
    private fun zoneOf(bpm: Double, max: Int): Int? {
        val fraction = bpm / max
        if (fraction < 0.5) return null
        return ((fraction - 0.5) / 0.1).toInt().coerceAtMost(4)
    }
}
```

Check the arithmetic in the zones test by hand before running: 110/200 = 0.55 → zone 1; 130/200 =
0.65 → zone 2; 190/200 = 0.95 → zone 5; mean (110+130+190)/3 = 143.3 → 143. Floating-point: 0.65 − 0.5
= 0.15000000000000002, / 0.1 = 1.5000000000000002 → 1 (zone 2). Good. If a boundary value is added to
a test later, compute it in the test's comment first.

- [ ] **Step 5: Run, see them pass.** Expected `exit 0`, 9 tests.

- [ ] **Step 6: Commit** `domain/health/HealthKind.kt`, `domain/health/HeartRateZones.kt` and the two tests:
  `feat: the health record's kinds, and heart-rate zones against an estimated maximum (D66, D70)`.

---

### Task 2: What the reader hands over, and how it becomes rows

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/ReadRecord.kt`
- Create: `app/src/main/java/com/metaself/app/data/health/HealthRows.kt`
- Create: `app/src/main/java/com/metaself/app/data/health/WorkoutKinds.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/HealthRowsTest.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/WorkoutKindsTest.kt`

- [ ] **Step 1: Failing tests.**

```kotlin
package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.health.HealthKind
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Every figure is invented and round. Times are built from local dates so the day logic is visible. */
class HealthRowsTest {

    private val zone: ZoneId = ZoneOffset.ofHours(3)
    private val rows = HealthRows(zone)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val day = LocalDate.of(2026, 9, 3)

    @Test
    fun `a series becomes one row per sample, numbered, under one record id`() {
        val record = ReadRecord.Reading(
            kind = HealthKind.HEART_RATE,
            origin = ORIGIN,
            recordId = "hr-1",
            samples = listOf(
                Sample(at(day, 10), null, 60.0),
                Sample(at(day, 10, 1), null, 62.0),
            ),
        )

        val out = rows.readings(record)

        assertThat(out.map { it.sampleIndex }).containsExactly(0, 1).inOrder()
        assertThat(out.map { it.recordId }.distinct()).containsExactly("hr-1")
        assertThat(out.map { it.unit }.distinct()).containsExactly("bpm")
        assertThat(out.map { it.epochDay }.distinct()).containsExactly(day.toEpochDay())
    }

    /** A reading belongs to the local day it starts on. */
    @Test
    fun `a span that crosses midnight belongs to the day it started`() {
        val record = ReadRecord.Reading(
            kind = HealthKind.STEPS,
            origin = ORIGIN,
            recordId = "st-1",
            samples = listOf(Sample(at(day, 23, 50), at(day.plusDays(1), 0, 10), 400.0)),
        )

        assertThat(rows.readings(record).single().epochDay).isEqualTo(day.toEpochDay())
    }

    /** D68: a night belongs to the day it ends. */
    @Test
    fun `a night belongs to the morning it ends on, with its stages`() {
        val night = ReadRecord.Night(
            origin = ORIGIN,
            recordId = "s-1",
            startMillis = at(day, 23),
            endMillis = at(day.plusDays(1), 7),
            title = null,
            stages = listOf(StageSpan("DEEP", at(day, 23), at(day.plusDays(1), 1))),
        )

        val (session, stages) = rows.night(night)

        assertThat(session.epochDay).isEqualTo(day.plusDays(1).toEpochDay())
        assertThat(stages.single().stage).isEqualTo("DEEP")
    }

    @Test
    fun `a session becomes a synced workout whose energy says it came from the band`() {
        val session = ReadRecord.Session(
            origin = ORIGIN,
            recordId = "w-1",
            startMillis = at(day, 7),
            endMillis = at(day, 7, 30),
            kind = "RUN",
            title = "Running",
            distanceM = 5_000,
            energyKcal = 300,
        )

        val workout = rows.workout(session)

        assertThat(workout.epochDay).isEqualTo(day.toEpochDay())
        assertThat(workout.durationMinutes).isEqualTo(30)
        assertThat(workout.source).isEqualTo("SYNCED")
        assertThat(workout.energySource).isEqualTo("BAND")
        assertThat(workout.origin).isEqualTo(ORIGIN)
        assertThat(workout.originId).isEqualTo("w-1")
        assertThat(workout.effort).isNull()
    }

    @Test
    fun `a session with no energy says so rather than guessing`() {
        val session = ReadRecord.Session(ORIGIN, "w-2", at(day, 7), at(day, 8), "OTHER", null, null, null)

        val workout = rows.workout(session)

        assertThat(workout.energyKcal).isNull()
        assertThat(workout.energySource).isEqualTo("NONE")
    }

    private companion object {
        const val ORIGIN = "com.example.band"
    }
}
```

```kotlin
package com.metaself.app.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WorkoutKindsTest {

    @Test
    fun `each family of exercise lands on its kind`() {
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING)).isEqualTo("RUN")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)).isEqualTo("RUN")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_WALKING)).isEqualTo("WALK")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_HIKING)).isEqualTo("WALK")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_BIKING)).isEqualTo("CYCLE")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY)).isEqualTo("CYCLE")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL)).isEqualTo("SWIM")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER)).isEqualTo("SWIM")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING)).isEqualTo("STRENGTH")
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING)).isEqualTo("STRENGTH")
    }

    @Test
    fun `anything else is other, never a run`() {
        assertThat(WorkoutKinds.of(ExerciseSessionRecord.EXERCISE_TYPE_YOGA)).isEqualTo("OTHER")
        assertThat(WorkoutKinds.of(-1)).isEqualTo("OTHER")
    }
}
```

If `WorkoutKindsTest` cannot load `ExerciseSessionRecord`'s constants in a plain JVM test (they are
`const val` in a companion and normally inlined), keep the test and say so; do not move it to
Robolectric without reporting.

- [ ] **Step 2: Run, see them fail.**

- [ ] **Step 3: `ReadRecord.kt`:**

```kotlin
package com.metaself.app.data.health

import com.metaself.app.domain.health.HealthKind

/**
 * One Health Connect record, translated into this app's terms by the reader and nothing else.
 * Free of Health Connect types, so everything downstream of the reader is testable on any machine.
 */
sealed interface ReadRecord {
    val origin: String
    val recordId: String

    /** A simple reading: one sample, or a series (heart rate). */
    data class Reading(
        val kind: HealthKind,
        override val origin: String,
        override val recordId: String,
        val samples: List<Sample>,
    ) : ReadRecord

    data class Night(
        override val origin: String,
        override val recordId: String,
        val startMillis: Long,
        val endMillis: Long,
        val title: String?,
        val stages: List<StageSpan>,
    ) : ReadRecord

    /**
     * @property kind a `WorkoutKind` name (`WorkoutKinds.of`).
     * @property distanceM, energyKcal what Health Connect's aggregation gave over the session's time,
     *   or null when it gave nothing (D4: null, never zero).
     */
    data class Session(
        override val origin: String,
        override val recordId: String,
        val startMillis: Long,
        val endMillis: Long,
        val kind: String,
        val title: String?,
        val distanceM: Int?,
        val energyKcal: Int?,
    ) : ReadRecord
}

/** A value at a moment ([endMillis] null) or over a span. */
data class Sample(val startMillis: Long, val endMillis: Long?, val value: Double)

/** @property stage AWAKE, LIGHT, DEEP, REM, SLEEPING, OUT_OF_BED, AWAKE_IN_BED or UNKNOWN. */
data class StageSpan(val stage: String, val startMillis: Long, val endMillis: Long)
```

- [ ] **Step 4: `HealthRows.kt`:**

```kotlin
package com.metaself.app.data.health

import java.time.Instant
import java.time.ZoneId

/**
 * Read records → rows, with every day in the phone's own zone (D68: a reading or a workout belongs to
 * the local day it starts; a night to the local day it ends).
 */
class HealthRows(private val zone: ZoneId) {

    fun dayOf(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun readings(record: ReadRecord.Reading): List<HealthReadingEntity> =
        record.samples.mapIndexed { index, sample ->
            HealthReadingEntity(
                kind = record.kind.name,
                startMillis = sample.startMillis,
                endMillis = sample.endMillis,
                value = sample.value,
                unit = requireNotNull(record.kind.unit) { "${record.kind} is not a simple reading" },
                origin = record.origin,
                recordId = record.recordId,
                sampleIndex = index,
                epochDay = dayOf(sample.startMillis),
            )
        }

    /** The night, and its stages with `sessionId` 0 until the night is inserted. */
    fun night(record: ReadRecord.Night): Pair<SleepSessionEntity, List<SleepStageEntity>> =
        SleepSessionEntity(
            epochDay = dayOf(record.endMillis),
            startMillis = record.startMillis,
            endMillis = record.endMillis,
            origin = record.origin,
            recordId = record.recordId,
            title = record.title,
        ) to record.stages.map {
            SleepStageEntity(sessionId = 0, stage = it.stage, startMillis = it.startMillis, endMillis = it.endMillis)
        }

    fun workout(record: ReadRecord.Session): WorkoutEntity = WorkoutEntity(
        epochDay = dayOf(record.startMillis),
        startedAtMillis = record.startMillis,
        durationMinutes = ((record.endMillis - record.startMillis) / 60_000).toInt(),
        kind = record.kind,
        title = record.title,
        distanceM = record.distanceM,
        energyKcal = record.energyKcal,
        energySource = if (record.energyKcal != null) "BAND" else "NONE",
        effort = null,
        source = "SYNCED",
        origin = record.origin,
        originId = record.recordId,
        note = null,
    )
}
```

- [ ] **Step 5: `WorkoutKinds.kt`:**

```kotlin
package com.metaself.app.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord

/** Health Connect's exercise type → the `WorkoutKind` name stored. Anything unlisted is OTHER. */
object WorkoutKinds {

    fun of(exerciseType: Int): String = KNOWN[exerciseType] ?: "OTHER"

    private val KNOWN: Map<Int, String> = mapOf(
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to "RUN",
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL to "RUN",
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING to "WALK",
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING to "WALK",
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "CYCLE",
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY to "CYCLE",
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to "SWIM",
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER to "SWIM",
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING to "STRENGTH",
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING to "STRENGTH",
    )
}
```

- [ ] **Step 6: Run, see them pass. Step 7: Commit** the five files:
  `feat: a record read from Health Connect becomes rows, on the right local day (D68)`.

---

### Task 3: One day, summarised

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/SleepNight.kt`
- Create: `app/src/main/java/com/metaself/app/data/health/DaySummary.kt`
- Modify: `app/src/main/java/com/metaself/app/data/health/HealthPorts.kt` (create; `DayTotals` only in this task)
- Test: `app/src/test/java/com/metaself/app/data/health/DaySummaryTest.kt`

- [ ] **Step 1: Failing test.**

```kotlin
package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test

/** Every figure is invented and round. */
class DaySummaryTest {

    private val day = TEST_EPOCH_DAY

    @Test
    fun `nothing at all is no summary`() {
        assertThat(DaySummary.of(day, DayTotals(), emptyList(), emptyList(), emptyList(), null, 1_000)).isNull()
    }

    /** D69: the four totals are Health Connect's de-duplicated aggregate, never the rows added up. */
    @Test
    fun `the totals are taken as given and say so`() {
        val rows = listOf(reading("STEPS", 999_999.0))

        val summary = DaySummary.of(
            day, DayTotals(steps = 9_000, distanceM = 6_000, activeKcal = 300, totalKcal = 2_400),
            rows, emptyList(), emptyList(), null, 1_000,
        )!!

        assertThat(summary.steps).isEqualTo(9_000)
        assertThat(summary.stepsSource).isEqualTo("TOTAL")
        assertThat(summary.distanceM).isEqualTo(6_000)
        assertThat(summary.activeKcal).isEqualTo(300)
        assertThat(summary.totalKcal).isEqualTo(2_400)
        assertThat(summary.totalKcalSource).isEqualTo("TOTAL")
    }

    @Test
    fun `averages are computed from the day's readings, and say so`() {
        val summary = DaySummary.of(
            day, DayTotals(),
            listOf(
                reading("HEART_RATE", 60.0), reading("HEART_RATE", 80.0),
                reading("HRV_RMSSD", 40.0),
                reading("OXYGEN_SATURATION", 96.0), reading("OXYGEN_SATURATION", 98.0),
                reading("RESPIRATORY_RATE", 14.0),
            ),
            emptyList(), emptyList(), null, 1_000,
        )!!

        assertThat(summary.avgHeartRate).isEqualTo(70)
        assertThat(summary.avgHeartRateSource).isEqualTo("COMPUTED")
        assertThat(summary.hrvMs).isEqualTo(40.0)
        assertThat(summary.oxygenPct).isEqualTo(97.0)
        assertThat(summary.respiratoryRate).isEqualTo(14.0)
    }

    /** The band's own resting figure is read, never worked out here; the latest one wins. */
    @Test
    fun `the resting heart rate is the day's last reading of it`() {
        val summary = DaySummary.of(
            day, DayTotals(),
            listOf(reading("RESTING_HEART_RATE", 60.0, at = 1_000), reading("RESTING_HEART_RATE", 58.0, at = 2_000)),
            emptyList(), emptyList(), null, 1_000,
        )!!

        assertThat(summary.restingHeartRate).isEqualTo(58)
        assertThat(summary.restingHeartRateSource).isEqualTo("READ")
    }

    @Test
    fun `a night's stages add up into the day it ended`() {
        val night = SleepNight(
            session = SleepSessionEntity(1, day, 0, 480 * MINUTE, ORIGIN, "s-1", null),
            stages = listOf(
                stage("LIGHT", 0, 240), stage("DEEP", 240, 330), stage("REM", 330, 420),
                stage("AWAKE", 420, 450), stage("SLEEPING", 450, 480),
            ),
        )

        val summary = DaySummary.of(day, DayTotals(), emptyList(), listOf(night), emptyList(), null, 1_000)!!

        assertThat(summary.lightMinutes).isEqualTo(240)
        assertThat(summary.deepMinutes).isEqualTo(90)
        assertThat(summary.remMinutes).isEqualTo(90)
        assertThat(summary.awakeMinutes).isEqualTo(30)
        assertThat(summary.sleepMinutes).isEqualTo(450)
        assertThat(summary.sleepSource).isEqualTo("COMPUTED")
    }

    @Test
    fun `a night with no stages counts its whole length as sleep`() {
        val night = SleepNight(SleepSessionEntity(1, day, 0, 420 * MINUTE, ORIGIN, "s-1", null), emptyList())

        val summary = DaySummary.of(day, DayTotals(), emptyList(), listOf(night), emptyList(), null, 1_000)!!

        assertThat(summary.sleepMinutes).isEqualTo(420)
        assertThat(summary.deepMinutes).isNull()
    }

    @Test
    fun `hidden workouts are not counted`() {
        val summary = DaySummary.of(
            day, DayTotals(), emptyList(), emptyList(),
            listOf(workout(30), workout(45), workout(60).copy(hidden = true)),
            null, 1_000,
        )!!

        assertThat(summary.workoutCount).isEqualTo(2)
        assertThat(summary.workoutMinutes).isEqualTo(75)
    }

    /** D12d: the owner's figure wins, and the summary says it is his. */
    @Test
    fun `a correction replaces what was read and says so`() {
        val summary = DaySummary.of(
            day, DayTotals(steps = 30_000, activeKcal = 900), emptyList(), emptyList(), emptyList(),
            MovementCorrectionEntity(day, steps = 8_000, activeKcal = null, setAtMillis = 1, note = null),
            1_000,
        )!!

        assertThat(summary.steps).isEqualTo(8_000)
        assertThat(summary.stepsSource).isEqualTo("CORRECTED")
        assertThat(summary.activeKcal).isEqualTo(900)
        assertThat(summary.activeKcalSource).isEqualTo("TOTAL")
    }

    private fun reading(kind: String, value: Double, at: Long = 1_000) = HealthReadingEntity(
        kind = kind, startMillis = at, endMillis = null, value = value, unit = "u",
        origin = ORIGIN, recordId = "r-$kind-$at-$value", epochDay = day,
    )

    private fun stage(name: String, fromMinute: Int, toMinute: Int) =
        SleepStageEntity(sessionId = 1, stage = name, startMillis = fromMinute * MINUTE, endMillis = toMinute * MINUTE)

    private fun workout(minutes: Int) = WorkoutEntity(
        epochDay = day, startedAtMillis = 0, durationMinutes = minutes, kind = "RUN", title = null,
        distanceM = null, energyKcal = null, energySource = "NONE", effort = null, source = "SYNCED",
        origin = ORIGIN, originId = "w-$minutes", note = null,
    )

    private companion object {
        const val ORIGIN = "com.example.band"
        const val MINUTE = 60_000L
    }
}
```

- [ ] **Step 2: Run, see it fail.**

- [ ] **Step 3: `SleepNight.kt`:**

```kotlin
package com.metaself.app.data.health

import androidx.room.Embedded
import androidx.room.Relation

/** A night with its stages, read in one query so the two can never disagree. */
data class SleepNight(
    @Embedded val session: SleepSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val stages: List<SleepStageEntity>,
)
```

- [ ] **Step 4: `HealthPorts.kt`** (this task adds only `DayTotals`; Task 4 adds the rest):

```kotlin
package com.metaself.app.data.health

/**
 * One day's four totals from Health Connect's aggregation API, which removes the overlap between
 * apps (D12c, D69). A null is a total Health Connect did not give — no data, or no permission.
 */
data class DayTotals(
    val steps: Int? = null,
    val distanceM: Int? = null,
    val activeKcal: Int? = null,
    val totalKcal: Int? = null,
)
```

- [ ] **Step 5: `DaySummary.kt`:**

```kotlin
package com.metaself.app.data.health

import kotlin.math.roundToInt

/**
 * One day of the record, summarised (D69). Pure.
 *
 * Every figure says where it came from: TOTAL (Health Connect's de-duplicated aggregate), READ (one
 * record), COMPUTED (worked out here from the day's rows) or CORRECTED (the owner's figure, D12d).
 * No data is null, never zero; a day with nothing at all has no summary.
 */
object DaySummary {

    private val ASLEEP = setOf("LIGHT", "DEEP", "REM", "SLEEPING")
    private val AWAKE = setOf("AWAKE", "AWAKE_IN_BED", "OUT_OF_BED")

    fun of(
        epochDay: Long,
        totals: DayTotals,
        readings: List<HealthReadingEntity>,
        nights: List<SleepNight>,
        workouts: List<WorkoutEntity>,
        correction: MovementCorrectionEntity?,
        nowMillis: Long,
    ): HealthDayEntity? {
        fun mean(kind: String): Double? =
            readings.filter { it.kind == kind }.map { it.value }.takeIf { it.isNotEmpty() }?.average()

        val resting = readings.filter { it.kind == "RESTING_HEART_RATE" }.maxByOrNull { it.startMillis }
        val steps = correction?.steps ?: totals.steps
        val active = correction?.activeKcal ?: totals.activeKcal
        val visible = workouts.filterNot { it.hidden }
        val sleep = sleepOf(nights)

        val day = HealthDayEntity(
            epochDay = epochDay,
            computedAtMillis = nowMillis,
            steps = steps,
            stepsSource = source(steps, corrected = correction?.steps != null, otherwise = "TOTAL"),
            distanceM = totals.distanceM,
            distanceSource = totals.distanceM?.let { "TOTAL" },
            activeKcal = active,
            activeKcalSource = source(active, corrected = correction?.activeKcal != null, otherwise = "TOTAL"),
            totalKcal = totals.totalKcal,
            totalKcalSource = totals.totalKcal?.let { "TOTAL" },
            restingHeartRate = resting?.value?.roundToInt(),
            restingHeartRateSource = resting?.let { "READ" },
            avgHeartRate = mean("HEART_RATE")?.roundToInt(),
            avgHeartRateSource = mean("HEART_RATE")?.let { "COMPUTED" },
            hrvMs = mean("HRV_RMSSD"),
            hrvSource = mean("HRV_RMSSD")?.let { "COMPUTED" },
            oxygenPct = mean("OXYGEN_SATURATION"),
            oxygenSource = mean("OXYGEN_SATURATION")?.let { "COMPUTED" },
            respiratoryRate = mean("RESPIRATORY_RATE"),
            respiratoryRateSource = mean("RESPIRATORY_RATE")?.let { "COMPUTED" },
            sleepMinutes = sleep?.asleep,
            deepMinutes = sleep?.stage("DEEP"),
            lightMinutes = sleep?.stage("LIGHT"),
            remMinutes = sleep?.stage("REM"),
            awakeMinutes = sleep?.awake,
            sleepSource = sleep?.let { "COMPUTED" },
            workoutCount = visible.size.takeIf { it > 0 },
            workoutMinutes = visible.sumOf { it.durationMinutes }.takeIf { visible.isNotEmpty() },
            workoutSource = "COMPUTED".takeIf { visible.isNotEmpty() },
        )
        return day.takeUnless { it.copy(computedAtMillis = 0) == HealthDayEntity(epochDay, 0) }
    }

    private fun source(value: Int?, corrected: Boolean, otherwise: String): String? = when {
        value == null -> null
        corrected -> "CORRECTED"
        else -> otherwise
    }

    private class Sleep(val asleep: Int, val awake: Int?, private val byStage: Map<String, Int>) {
        fun stage(name: String): Int? = byStage[name]
    }

    /** Stage minutes when there are stages; the whole night as sleep when there are none. */
    private fun sleepOf(nights: List<SleepNight>): Sleep? {
        if (nights.isEmpty()) return null
        val stages = nights.flatMap { it.stages }
        if (stages.isEmpty()) {
            val whole = nights.sumOf { minutes(it.session.startMillis, it.session.endMillis) }
            return Sleep(asleep = whole, awake = null, byStage = emptyMap())
        }
        val byStage = stages.groupBy { it.stage }.mapValues { (_, spans) ->
            spans.sumOf { minutes(it.startMillis, it.endMillis) }
        }
        return Sleep(
            asleep = byStage.filterKeys { it in ASLEEP }.values.sum(),
            awake = byStage.filterKeys { it in AWAKE }.values.sum().takeIf { byStage.keys.any { it in AWAKE } },
            byStage = byStage,
        )
    }

    private fun minutes(from: Long, to: Long): Int = ((to - from) / 60_000).toInt()
}
```

- [ ] **Step 6: Run, see it pass. Step 7: Commit** the four files:
  `feat: a day of the health record is summarised, every figure with its source (D69)`.

---

### Task 4: The copying decisions

**Files:**
- Modify: `app/src/main/java/com/metaself/app/data/health/HealthPorts.kt`
- Create: `app/src/main/java/com/metaself/app/data/health/HealthRecordSync.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/HealthRecordSyncTest.kt`

**The rules (D67), which the tests pin:**

1. Only kinds the owner has granted are touched. None granted → nothing happens.
2. A kind with no bookmark first **takes a changes token, then** starts its catch-up at "now" — token
   first, so anything written during the catch-up is picked up by the next changes read.
3. A kind with a token **drains its changes** (following `hasMore`), applying upserts and deletions, and
   keeps the new token. An **expired** token means: re-read the last 30 days of that kind and replace
   them, then take a fresh token.
4. A kind whose catch-up is not done reads **one slice of 7 days per step, newest first**, moving the
   cursor back. It is done after **8 empty slices in a row** or once the cursor is **730 days** back.
5. A read that **throws** during catch-up stops that kind for this open. If the slice was older than
   30 days, the refusal is taken as Health Connect's history limit: the catch-up is marked done and the
   problem log says from which date reading was refused — **this is the measurement of the history limit**
   the spec asks for. Newer than that, it is treated as a passing refusal (quota) and retried next open.
6. **At most 60 reads per open**, across all kinds (a changes page and a slice each count one). When
   the budget runs out, the bookmarks already saved stand and the next open continues. The totals call
   of rule 7 is always made and not counted: a day summarised without its totals would lose them.
7. Every day touched by an applied batch is re-summarised once, after all kinds, with the four totals
   asked for over the touched range in one call per metric.
8. A second copy started while one is running does nothing.

7, 30, 8, 730 and 60 are **choices**, stated as such in code: a week is a readable slice; 30 days
matches Health Connect's documented read limit; eight empty weeks means the record has begun; two years
bounds a phone with nothing; sixty reads keeps one open cheap. None is a measured quota.

- [ ] **Step 1: Append to `HealthPorts.kt`:**

```kotlin
/** One page of changes since a token. */
data class ChangesPage(
    val upserts: List<ReadRecord>,
    val deletedIds: List<String>,
    val nextToken: String,
    val hasMore: Boolean,
    val expired: Boolean,
)

/** What the copying reads from. The real one is Health Connect; tests use a fake. */
interface HealthSource {
    suspend fun grantedKinds(): Set<com.metaself.app.domain.health.HealthKind>
    suspend fun changesToken(kind: com.metaself.app.domain.health.HealthKind): String
    suspend fun changes(kind: com.metaself.app.domain.health.HealthKind, token: String): ChangesPage
    /** Every record of [kind] starting in [fromMillis, toMillis), all pages. */
    suspend fun readWindow(kind: com.metaself.app.domain.health.HealthKind, fromMillis: Long, toMillis: Long): List<ReadRecord>
    /** Daily totals for every day in [fromDay, toDay], one aggregation call per metric. */
    suspend fun dayTotals(fromDay: Long, toDay: Long): Map<Long, DayTotals>
}

/** What the copying writes to. The real one is Room; tests use a fake. */
interface HealthStore {
    suspend fun bookmark(kind: com.metaself.app.domain.health.HealthKind): HealthSyncEntity?
    suspend fun saveBookmark(bookmark: HealthSyncEntity)
    /** Applies one batch in one transaction; returns the local days it touched. */
    suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long>
    /** Replaces every row of [kind] starting in the window with [records]; returns the days touched. */
    suspend fun replaceWindow(
        kind: com.metaself.app.domain.health.HealthKind,
        fromMillis: Long,
        toMillis: Long,
        records: List<ReadRecord>,
    ): Set<Long>
    /** Recomputes each day's summary and its workouts' heart-rate figures; marks its archive month. */
    suspend fun summarise(days: Set<Long>, totals: Map<Long, DayTotals>, nowMillis: Long)
}

/** The one thing the day screen asks for. */
fun interface HealthRecordCopier {
    suspend fun copyNow()

    companion object {
        val NONE = HealthRecordCopier { }
    }
}
```

(Use a proper `import com.metaself.app.domain.health.HealthKind` at the top of the file instead of the
qualified names above — they are written out here only so the snippet is unambiguous.)

- [ ] **Step 2: Failing test** — `HealthRecordSyncTest` with a `FakeSource` and a `FakeStore` inside the
  test file:

```kotlin
package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The copying rules (D67), with no Health Connect and no database. Every figure is invented; "now" is
 * day 100 at midnight UTC, so windows are easy to read.
 */
class HealthRecordSyncTest {

    private val now = 100 * DAY
    private val source = FakeSource()
    private val store = FakeStore()
    private val problems = RecordingProblems()
    private val sync = HealthRecordSync(source, store, problems, now = { now })

    @Test
    fun `nothing granted, nothing read`() = runTest {
        sync.copyNow()

        assertThat(source.calls).isEmpty()
    }

    @Test
    fun `a new kind takes its token before its first slice`() = runTest {
        source.granted = setOf(HealthKind.STEPS)

        sync.copyNow()

        assertThat(source.calls.first()).isEqualTo("token STEPS")
        assertThat(source.calls[1]).isEqualTo("window STEPS ${93 * DAY}..${100 * DAY}")
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).changesToken).isEqualTo("t-STEPS-1")
    }

    @Test
    fun `catch-up goes back a week at a time and stops after eight empty weeks`() = runTest {
        source.granted = setOf(HealthKind.STEPS)

        sync.copyNow()

        val windows = source.calls.filter { it.startsWith("window") }
        assertThat(windows).hasSize(8)
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isTrue()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpCursorMillis).isEqualTo(44 * DAY)
    }

    @Test
    fun `a week with data resets the count of empty weeks`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.windowRecords = { from, _ -> if (from == 86 * DAY) listOf(steps("st-1", 88 * DAY)) else emptyList() }

        sync.copyNow()

        // The week 93..100 is empty, 86..93 has data, then eight empty weeks follow.
        assertThat(source.calls.count { it.startsWith("window") }).isEqualTo(10)
        assertThat(store.applied.flatMap { it.first }.map { it.recordId }).contains("st-1")
    }

    @Test
    fun `a kind with a token drains its changes and keeps the new token`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        store.bookmarks[HealthKind.STEPS] = done("t-old")
        source.pages = mutableListOf(
            ChangesPage(listOf(steps("st-1", 99 * DAY)), emptyList(), "t-2", hasMore = true, expired = false),
            ChangesPage(emptyList(), listOf("st-0"), "t-3", hasMore = false, expired = false),
        )

        sync.copyNow()

        assertThat(source.calls).containsExactly("changes STEPS t-old", "changes STEPS t-2", "totals 99..99").inOrder()
        assertThat(store.applied.map { it.second }).containsExactly(emptyList<String>(), listOf("st-0")).inOrder()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).changesToken).isEqualTo("t-3")
    }

    @Test
    fun `an expired token re-reads the last thirty days and takes a fresh token`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        store.bookmarks[HealthKind.STEPS] = done("t-old")
        source.pages = mutableListOf(ChangesPage(emptyList(), emptyList(), "", hasMore = false, expired = true))

        sync.copyNow()

        assertThat(store.replaced).containsExactly("STEPS ${70 * DAY}..${100 * DAY}")
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).changesToken).isEqualTo("t-STEPS-1")
    }

    /** Rule 5: an old refusal is the history limit, and the log says from when. */
    @Test
    fun `a refused read older than thirty days ends the catch-up and is recorded`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.refuseBefore = 65 * DAY

        sync.copyNow()

        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isTrue()
        assertThat(problems.logged.single().kind).isEqualTo("health")
        assertThat(problems.logged.single().detail).contains("STEPS")
    }

    @Test
    fun `a refused recent read stops the kind for now and tries again next time`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        source.refuseBefore = 100 * DAY

        sync.copyNow()

        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpDone).isFalse()
        assertThat(store.bookmarks.getValue(HealthKind.STEPS).catchUpCursorMillis).isEqualTo(100 * DAY)
    }

    @Test
    fun `one open never reads more than its budget`() = runTest {
        source.granted = HealthKind.entries.toSet()

        sync.copyNow()

        assertThat(source.calls.count { !it.startsWith("token") }).isAtMost(HealthRecordSync.READS_PER_OPEN)
    }

    @Test
    fun `touched days are summarised once, with totals asked for their whole range`() = runTest {
        source.granted = setOf(HealthKind.STEPS, HealthKind.HEART_RATE)
        store.bookmarks[HealthKind.STEPS] = done("a")
        store.bookmarks[HealthKind.HEART_RATE] = done("b", HealthKind.HEART_RATE)
        source.pages = mutableListOf(
            ChangesPage(listOf(steps("st-1", 97 * DAY)), emptyList(), "a2", false, false),
            ChangesPage(listOf(steps("st-2", 99 * DAY)), emptyList(), "b2", false, false),
        )

        sync.copyNow()

        assertThat(source.calls.filter { it.startsWith("totals") }).containsExactly("totals 97..99")
        assertThat(store.summarised).containsExactly(setOf(97L, 99L))
    }

    @Test
    fun `nothing it does throws`() = runTest {
        source.granted = setOf(HealthKind.STEPS)
        store.bookmarks[HealthKind.STEPS] = done("t")
        source.changesThrow = true

        sync.copyNow()

        assertThat(problems.logged).isNotEmpty()
    }

    // --- Fakes -----------------------------------------------------------------------------------

    private fun done(token: String, kind: HealthKind = HealthKind.STEPS) = HealthSyncEntity(
        kind = kind.name, changesToken = token, tokenAtMillis = 0, catchUpCursorMillis = 0, catchUpDone = true,
    )

    private fun steps(id: String, at: Long) = ReadRecord.Reading(
        HealthKind.STEPS, "com.example.band", id, listOf(Sample(at, at + 60_000, 100.0)),
    )

    private class FakeSource : HealthSource {
        val calls = mutableListOf<String>()
        var granted: Set<HealthKind> = emptySet()
        var pages = mutableListOf<ChangesPage>()
        var windowRecords: (Long, Long) -> List<ReadRecord> = { _, _ -> emptyList() }
        var refuseBefore: Long? = null
        var changesThrow = false
        private var tokens = 0

        override suspend fun grantedKinds() = granted
        override suspend fun changesToken(kind: HealthKind): String {
            calls += "token $kind"
            return "t-$kind-${++tokens}"
        }
        override suspend fun changes(kind: HealthKind, token: String): ChangesPage {
            calls += "changes $kind $token"
            if (changesThrow) throw IllegalStateException("unavailable")
            return pages.removeFirst()
        }
        override suspend fun readWindow(kind: HealthKind, fromMillis: Long, toMillis: Long): List<ReadRecord> {
            calls += "window $kind $fromMillis..$toMillis"
            refuseBefore?.let { if (toMillis <= it) throw SecurityException("refused") }
            return windowRecords(fromMillis, toMillis)
        }
        override suspend fun dayTotals(fromDay: Long, toDay: Long): Map<Long, DayTotals> {
            calls += "totals $fromDay..$toDay"
            return emptyMap()
        }
    }

    private class FakeStore : HealthStore {
        val bookmarks = mutableMapOf<HealthKind, HealthSyncEntity>()
        val applied = mutableListOf<Pair<List<ReadRecord>, List<String>>>()
        val replaced = mutableListOf<String>()
        val summarised = mutableListOf<Set<Long>>()

        override suspend fun bookmark(kind: HealthKind) = bookmarks[kind]
        override suspend fun saveBookmark(bookmark: HealthSyncEntity) {
            bookmarks[HealthKind.parse(bookmark.kind)!!] = bookmark
        }
        override suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long> {
            applied += records to deletedIds
            return records.filterIsInstance<ReadRecord.Reading>()
                .flatMap { r -> r.samples.map { it.startMillis / DAY } }.toSet()
        }
        override suspend fun replaceWindow(kind: HealthKind, fromMillis: Long, toMillis: Long, records: List<ReadRecord>): Set<Long> {
            replaced += "$kind $fromMillis..$toMillis"
            return emptySet()
        }
        override suspend fun summarise(days: Set<Long>, totals: Map<Long, DayTotals>, nowMillis: Long) {
            summarised += days
        }
    }

    private class RecordingProblems : ProblemLog {
        val logged = mutableListOf<Problem>()
        override fun recent() = logged.toList()
        override fun record(kind: String, detail: String) { logged += Problem(0, kind, detail) }
        override fun clear() = logged.clear()
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
```

Note on the empty-weeks test: slices end at 100, 93, 86, 79, 72, 65, 58, 51 (days); the eighth empty
slice starts at 44, so the cursor lands on day 44. Recount if you change the rule, never the expectation
alone.

- [ ] **Step 3: Run, see it fail.**

- [ ] **Step 4: `HealthRecordSync.kt`:**

```kotlin
package com.metaself.app.data.health

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.time.Now
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copying the health record, once per open and on refresh (D67). Every decision lives here; the
 * reading and the writing are behind [HealthSource] and [HealthStore].
 *
 * **Nothing here throws upwards (D8).** A failure is a problem-log entry and the next open carries on
 * from the last bookmark saved.
 */
@Singleton
class HealthRecordSync @Inject constructor(
    private val source: HealthSource,
    private val store: HealthStore,
    private val problems: ProblemLog,
    private val now: Now,
) : HealthRecordCopier {

    private val running = Mutex()

    override suspend fun copyNow() {
        if (!running.tryLock()) return
        try {
            copy()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            note("copying stopped: ${failure::class.java.simpleName} ${failure.message}")
        } finally {
            running.unlock()
        }
    }

    private suspend fun copy() {
        val nowMillis = now()
        val granted = source.grantedKinds()
        if (granted.isEmpty()) return

        val budget = Budget(READS_PER_OPEN)
        val touched = mutableSetOf<Long>()
        for (kind in HealthKind.entries.filter { it in granted }) {
            if (budget.spent) break
            try {
                touched += copyKind(kind, nowMillis, budget)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                note("$kind: ${failure::class.java.simpleName} ${failure.message}")
            }
        }
        if (touched.isEmpty()) return

        // Always asked for, outside the budget: a day summarised without its totals would lose them.
        val totals = runCatching { source.dayTotals(touched.min(), touched.max()) }
            .onFailure { note("totals: ${it::class.java.simpleName} ${it.message}") }
            .getOrDefault(emptyMap())
        store.summarise(touched, totals, nowMillis)
    }

    private suspend fun copyKind(kind: HealthKind, nowMillis: Long, budget: Budget): Set<Long> {
        val touched = mutableSetOf<Long>()
        var mark = store.bookmark(kind)

        if (mark?.changesToken == null) {
            // Token first: whatever is written while the catch-up runs is in the next changes read.
            mark = HealthSyncEntity(
                kind = kind.name,
                changesToken = source.changesToken(kind),
                tokenAtMillis = nowMillis,
                catchUpCursorMillis = mark?.catchUpCursorMillis ?: nowMillis,
                catchUpDone = mark?.catchUpDone ?: false,
            )
            store.saveBookmark(mark)
        } else {
            mark = drainChanges(kind, mark, nowMillis, budget, touched)
        }

        if (!mark.catchUpDone) catchUp(kind, mark, nowMillis, budget, touched)
        return touched
    }

    private suspend fun drainChanges(
        kind: HealthKind,
        start: HealthSyncEntity,
        nowMillis: Long,
        budget: Budget,
        touched: MutableSet<Long>,
    ): HealthSyncEntity {
        var mark = start
        while (budget.take()) {
            val page = source.changes(kind, mark.changesToken!!)
            if (page.expired) {
                val from = nowMillis - WINDOW_DAYS * DAY
                touched += store.replaceWindow(kind, from, nowMillis, source.readWindow(kind, from, nowMillis))
                mark = mark.copy(changesToken = source.changesToken(kind), tokenAtMillis = nowMillis)
                store.saveBookmark(mark)
                return mark
            }
            touched += store.apply(page.upserts, page.deletedIds)
            mark = mark.copy(changesToken = page.nextToken, tokenAtMillis = nowMillis)
            store.saveBookmark(mark)
            if (!page.hasMore) break
        }
        return mark
    }

    private suspend fun catchUp(
        kind: HealthKind,
        start: HealthSyncEntity,
        nowMillis: Long,
        budget: Budget,
        touched: MutableSet<Long>,
    ) {
        var mark = start
        var emptyInARow = 0
        while (!mark.catchUpDone && budget.take()) {
            val to = mark.catchUpCursorMillis ?: nowMillis
            val from = to - SLICE_DAYS * DAY
            val records = try {
                source.readWindow(kind, from, to)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (refused: Exception) {
                if (to <= nowMillis - WINDOW_DAYS * DAY) {
                    note("$kind: reading before ${dateOf(to)} was refused; taken as the history limit")
                    store.saveBookmark(mark.copy(catchUpDone = true))
                } else {
                    note("$kind: reading before ${dateOf(to)} was refused; will try again")
                }
                return
            }
            touched += store.apply(records, emptyList())
            emptyInARow = if (records.isEmpty()) emptyInARow + 1 else 0
            val done = emptyInARow >= EMPTY_SLICES_TO_STOP || from <= nowMillis - FURTHEST_DAYS * DAY
            mark = mark.copy(catchUpCursorMillis = from, catchUpDone = done)
            store.saveBookmark(mark)
        }
    }

    private fun note(detail: String) = problems.record(kind = "health", detail = detail)

    private fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    private class Budget(private var left: Int) {
        val spent: Boolean get() = left <= 0
        fun take(): Boolean = if (left > 0) { left--; true } else false
    }

    companion object {
        private const val DAY = 86_400_000L

        /** A choice: a week is a readable slice for any kind. */
        const val SLICE_DAYS = 7L

        /** A choice matching Health Connect's documented 30-day read limit; not a measured quota. */
        const val WINDOW_DAYS = 30L

        /** A choice: eight empty weeks in a row means the record has begun. */
        const val EMPTY_SLICES_TO_STOP = 8

        /** A choice: two years bounds the catch-up on a phone that has nothing. */
        const val FURTHEST_DAYS = 730L

        /** A choice that keeps one open cheap. Health Connect's real quota is not measured here. */
        const val READS_PER_OPEN = 60
    }
}
```

The empty-weeks rule counts the eighth empty slice as done, so in the "resets" test the
`86..93` slice with data is followed by eight empties (79, 72, 65, 58, 51, 44, 37, 30) — ten windows.
The budget test with thirteen kinds must stop at 60 reads; `grantedKinds` and `changesToken` do not
count (they are cheap metadata calls), which is why that test excludes tokens.

- [ ] **Step 5: Run, see it pass.** If a test's expected numbers disagree with the rules above, stop and
  report which; do not change the rules to fit the test or the test to fit the code without saying so.

- [ ] **Step 6: Commit** `HealthPorts.kt`, `HealthRecordSync.kt`, the test:
  `feat: the health record is copied by token, newest first, within a budget (D67)`.

---

### Task 5: The store, over Room

**Files:**
- Modify: the DAOs in `app/src/main/java/com/metaself/app/data/health/`
- Create: `app/src/main/java/com/metaself/app/data/health/RoomHealthStore.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/HealthRecordStoreTest.kt` (Robolectric, CI)

- [ ] **Step 1: DAO queries.** Add exactly these (no schema change):

`HealthReadingDao`:
```kotlin
    @Query("SELECT DISTINCT epochDay FROM health_readings WHERE recordId = :recordId")
    suspend fun daysOf(recordId: String): List<Long>

    @Query("DELETE FROM health_readings WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: String)

    @Query("SELECT DISTINCT epochDay FROM health_readings WHERE kind = :kind AND startMillis >= :from AND startMillis < :to")
    suspend fun daysOfKindBetween(kind: String, from: Long, to: Long): List<Long>

    @Query("DELETE FROM health_readings WHERE kind = :kind AND startMillis >= :from AND startMillis < :to")
    suspend fun deleteKindBetween(kind: String, from: Long, to: Long)

    @Query(
        "SELECT * FROM health_readings WHERE kind = :kind AND startMillis >= :from AND startMillis < :to " +
            "ORDER BY startMillis, sampleIndex",
    )
    suspend fun ofKindBetween(kind: String, from: Long, to: Long): List<HealthReadingEntity>
```

`SleepDao`:
```kotlin
    @Transaction
    @Query("SELECT * FROM sleep_sessions WHERE epochDay = :epochDay ORDER BY startMillis")
    suspend fun nightsOn(epochDay: Long): List<SleepNight>

    @Transaction
    @Query("SELECT * FROM sleep_sessions ORDER BY startMillis, id")
    suspend fun allNights(): List<SleepNight>

    @Query("SELECT DISTINCT epochDay FROM sleep_sessions WHERE recordId = :recordId")
    suspend fun daysOf(recordId: String): List<Long>

    @Query("DELETE FROM sleep_sessions WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: String)

    @Query("SELECT DISTINCT epochDay FROM sleep_sessions WHERE startMillis >= :from AND startMillis < :to")
    suspend fun daysBetween(from: Long, to: Long): List<Long>

    @Query("DELETE FROM sleep_sessions WHERE startMillis >= :from AND startMillis < :to")
    suspend fun deleteBetween(from: Long, to: Long)
```

`WorkoutDao`:
```kotlin
    @Query("SELECT * FROM workouts WHERE epochDay = :epochDay ORDER BY startedAtMillis")
    suspend fun onDay(epochDay: Long): List<WorkoutEntity>

    @Query("SELECT DISTINCT epochDay FROM workouts WHERE source = 'SYNCED' AND originId = :originId")
    suspend fun daysOfSynced(originId: String): List<Long>

    /** Deleted in the writing app. A hidden row is kept: it was hidden on purpose. */
    @Query("DELETE FROM workouts WHERE source = 'SYNCED' AND originId = :originId AND hidden = 0")
    suspend fun deleteSynced(originId: String)

    @Query(
        "SELECT DISTINCT epochDay FROM workouts WHERE source = 'SYNCED' AND hidden = 0 " +
            "AND startedAtMillis >= :from AND startedAtMillis < :to",
    )
    suspend fun daysOfSyncedBetween(from: Long, to: Long): List<Long>

    @Query(
        "DELETE FROM workouts WHERE source = 'SYNCED' AND hidden = 0 " +
            "AND startedAtMillis >= :from AND startedAtMillis < :to",
    )
    suspend fun deleteSyncedBetween(from: Long, to: Long)
```

`HealthDayDao`:
```kotlin
    @Query("DELETE FROM health_days WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("SELECT COUNT(*) FROM health_days")
    fun observeCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT MIN(epochDay) FROM health_days")
    fun observeEarliest(): kotlinx.coroutines.flow.Flow<Long?>
```

`MovementCorrectionDao`:
```kotlin
    @Query("SELECT * FROM movement_corrections WHERE epochDay = :epochDay")
    suspend fun day(epochDay: Long): MovementCorrectionEntity?
```

`HealthBookkeepingDao`:
```kotlin
    @Query("SELECT * FROM health_sync")
    fun observeSync(): kotlinx.coroutines.flow.Flow<List<HealthSyncEntity>>

    @Query("DELETE FROM health_sync")
    suspend fun clearSync()

    @Query("SELECT * FROM archive_months WHERE month = :month")
    suspend fun month(month: String): ArchiveMonthEntity?
```

(import `kotlinx.coroutines.flow.Flow` properly in each file.)

- [ ] **Step 2: Failing CI test** `HealthRecordStoreTest` (JUnit 4, Robolectric, `assumeSqliteRuntime()` in
  `@Before`, in-memory database as `HealthRecordDaoTest` builds it). Build the store with
  `RoomHealthStore(db, RoomDatabaseTransaction(db), HealthRows(ZoneOffset.UTC), FakeProfileRepository(aProfile()), Today { LocalDate.of(2026, 9, 3) })`.
  Tests (invented figures; `DAY = 86_400_000L`; `day = 20_699L`, i.e. `TEST_EPOCH_DAY`):

```kotlin
    @Test
    fun `a record read twice is stored once, as its latest version`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0, 62.0))), emptyList())
        store.apply(listOf(heart("hr-1", listOf(70.0))), emptyList())

        assertThat(db.healthReadingDao().onDay(day).map { it.value }).containsExactly(70.0)
    }

    @Test
    fun `a deletion removes a record wherever it is, and says which day it touched`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0)), night("s-1"), session("w-1")), emptyList())

        val touched = store.apply(emptyList(), listOf("hr-1", "s-1", "w-1"))

        assertThat(touched).containsExactly(day)
        assertThat(db.healthReadingDao().onDay(day)).isEmpty()
        assertThat(db.sleepDao().allSessions()).isEmpty()
        assertThat(db.workoutDao().all()).isEmpty()
    }

    /** The owner's hiding survives a re-read (activity spec §3.4 step 3). */
    @Test
    fun `a session read again keeps its hidden flag and note`() = runTest {
        store.apply(listOf(session("w-1")), emptyList())
        val row = db.workoutDao().all().single()
        db.workoutDao().update(row.copy(hidden = true, note = "not a run"))

        store.apply(listOf(session("w-1", distanceM = 6_000)), emptyList())

        val again = db.workoutDao().all().single()
        assertThat(again.distanceM).isEqualTo(6_000)
        assertThat(again.hidden).isTrue()
        assertThat(again.note).isEqualTo("not a run")
    }

    @Test
    fun `a night read again replaces its stages`() = runTest {
        store.apply(listOf(night("s-1", stages = 2)), emptyList())
        store.apply(listOf(night("s-1", stages = 1)), emptyList())

        assertThat(db.sleepDao().allStages()).hasSize(1)
    }

    @Test
    fun `replacing a window drops what is no longer there, and nothing outside it`() = runTest {
        store.apply(listOf(heart("hr-in", listOf(60.0)), heart("hr-out", listOf(60.0), at = (day - 40) * DAY)), emptyList())

        store.replaceWindow(HealthKind.HEART_RATE, (day - 29) * DAY, (day + 1) * DAY, emptyList())

        assertThat(db.healthReadingDao().ofKindBetween("HEART_RATE", 0, Long.MAX_VALUE).map { it.recordId })
            .containsExactly("hr-out")
    }

    @Test
    fun `a summarised day is written from its readings and its totals`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0, 80.0))), emptyList())
        store.summarise(setOf(day), mapOf(day to DayTotals(steps = 9_000)), nowMillis = 1_000)

        val summary = db.healthDayDao().day(day)!!
        assertThat(summary.steps).isEqualTo(9_000)
        assertThat(summary.avgHeartRate).isEqualTo(70)
    }

    @Test
    fun `a day with nothing left is removed`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0))), emptyList())
        store.summarise(setOf(day), emptyMap(), nowMillis = 1_000)

        store.apply(emptyList(), listOf("hr-1"))
        store.summarise(setOf(day), emptyMap(), nowMillis = 2_000)

        assertThat(db.healthDayDao().day(day)).isNull()
    }

    /** A totals call that failed must not wipe a day's steps: what was stored as TOTAL stands. */
    @Test
    fun `a total not given this time keeps the one stored`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0))), emptyList())
        store.summarise(setOf(day), mapOf(day to DayTotals(steps = 9_000)), nowMillis = 1_000)

        store.summarise(setOf(day), emptyMap(), nowMillis = 2_000)

        assertThat(db.healthDayDao().day(day)!!.steps).isEqualTo(9_000)
    }

    /** D70, on the standard body: born 1980, in 2026, so an estimated maximum of 174. */
    @Test
    fun `a workout gets its heart-rate figures from the readings inside it`() = runTest {
        store.apply(listOf(session("w-1"), heart("hr-1", listOf(120.0, 140.0), at = day * DAY + 60_000)), emptyList())

        store.summarise(setOf(day), emptyMap(), nowMillis = 1_000)

        val workout = db.workoutDao().all().single()
        assertThat(workout.avgHeartRate).isEqualTo(130)
        assertThat(workout.maxHeartRate).isEqualTo(140)
        assertThat(workout.zoneMaxSource).isEqualTo("ESTIMATED")
        assertThat(workout.zoneSeconds).isNotNull()
    }

    @Test
    fun `a summarised day marks its month as out of date`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0))), emptyList())

        store.summarise(setOf(day), emptyMap(), nowMillis = 1_000)

        assertThat(db.healthBookkeepingDao().monthsOutOfDate().map { it.month }).containsExactly("2026-09")
    }
```

with helpers building `ReadRecord.Reading(HealthKind.HEART_RATE, ORIGIN, id, samples one minute apart from `at` (default `day * DAY`))`,
`ReadRecord.Night(ORIGIN, id, day * DAY - 3_600_000, day * DAY + 3_600_000, null, stages = n spans of 10 minutes)`,
and `ReadRecord.Session(ORIGIN, id, day * DAY, day * DAY + 1_800_000, "RUN", "Running", distanceM, null)`.

- [ ] **Step 3: `RoomHealthStore.kt`:**

```kotlin
package com.metaself.app.data.health

import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.Today
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.health.HeartRateZones
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * The health record's writes (D65). One transaction per batch, so a record and its rows — a night and
 * its stages, a series and its samples — are never half-stored.
 *
 * A record read again replaces its earlier rows: rows are found by Health Connect's record id, deleted,
 * and inserted afresh. A synced workout is the exception: it is updated in place, keeping the owner's
 * `hidden` and `note`.
 */
class RoomHealthStore @Inject constructor(
    private val database: MetaSelfDatabase,
    private val transaction: DatabaseTransaction,
    private val rows: HealthRows,
    private val profiles: ProfileRepository,
    private val today: Today,
) : HealthStore {

    private val readings get() = database.healthReadingDao()
    private val sleep get() = database.sleepDao()
    private val workouts get() = database.workoutDao()
    private val days get() = database.healthDayDao()
    private val corrections get() = database.movementCorrectionDao()
    private val bookkeeping get() = database.healthBookkeepingDao()

    override suspend fun bookmark(kind: HealthKind): HealthSyncEntity? = bookkeeping.sync(kind.name)

    override suspend fun saveBookmark(bookmark: HealthSyncEntity) = bookkeeping.putSync(bookmark)

    override suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long> {
        val touched = mutableSetOf<Long>()
        transaction.run {
            deletedIds.forEach { id ->
                touched += readings.daysOf(id) + sleep.daysOf(id) + workouts.daysOfSynced(id)
                readings.deleteByRecordId(id)
                sleep.deleteByRecordId(id)
                workouts.deleteSynced(id)
            }
            records.forEach { record -> touched += store(record) }
        }
        return touched
    }

    override suspend fun replaceWindow(
        kind: HealthKind,
        fromMillis: Long,
        toMillis: Long,
        records: List<ReadRecord>,
    ): Set<Long> {
        val touched = mutableSetOf<Long>()
        transaction.run {
            when (kind) {
                HealthKind.SLEEP -> {
                    touched += sleep.daysBetween(fromMillis, toMillis)
                    sleep.deleteBetween(fromMillis, toMillis)
                }
                HealthKind.EXERCISE -> {
                    touched += workouts.daysOfSyncedBetween(fromMillis, toMillis)
                    workouts.deleteSyncedBetween(fromMillis, toMillis)
                }
                else -> {
                    touched += readings.daysOfKindBetween(kind.name, fromMillis, toMillis)
                    readings.deleteKindBetween(kind.name, fromMillis, toMillis)
                }
            }
            records.forEach { record -> touched += store(record) }
        }
        return touched
    }

    /** Inside a transaction the caller holds. */
    private suspend fun store(record: ReadRecord): Set<Long> = when (record) {
        is ReadRecord.Reading -> {
            readings.deleteRecord(record.origin, record.recordId)
            val rowsOf = rows.readings(record)
            readings.insertAll(rowsOf)
            rowsOf.map { it.epochDay }.toSet()
        }
        is ReadRecord.Night -> {
            val before = sleep.daysOf(record.recordId)
            sleep.deleteRecord(record.origin, record.recordId)
            val (night, stages) = rows.night(record)
            val id = sleep.insertSession(night)
            sleep.insertStages(stages.map { it.copy(sessionId = id) })
            before.toSet() + night.epochDay
        }
        is ReadRecord.Session -> {
            val fresh = rows.workout(record)
            val existing = workouts.synced(record.origin, record.recordId)
            if (existing == null) {
                workouts.insert(fresh)
                setOf(fresh.epochDay)
            } else {
                workouts.update(
                    fresh.copy(
                        id = existing.id,
                        hidden = existing.hidden,
                        note = existing.note,
                        avgHeartRate = existing.avgHeartRate,
                        maxHeartRate = existing.maxHeartRate,
                        zoneSeconds = existing.zoneSeconds,
                        zoneMaxSource = existing.zoneMaxSource,
                    ),
                )
                setOf(existing.epochDay, fresh.epochDay)
            }
        }
    }

    override suspend fun summarise(days: Set<Long>, totals: Map<Long, DayTotals>, nowMillis: Long) {
        val maxHeartRate = profiles.profile.first()
            ?.let { HeartRateZones.estimatedMax(it.birthYear, today().year) }
        days.sorted().forEach { epochDay ->
            transaction.run {
                val dayWorkouts = workouts.onDay(epochDay).map { withHeartRate(it, maxHeartRate) }
                val summary = DaySummary.of(
                    epochDay = epochDay,
                    totals = keepingStored(totals[epochDay] ?: DayTotals(), this.days.day(epochDay)),
                    readings = readings.onDay(epochDay),
                    nights = sleep.nightsOn(epochDay),
                    workouts = dayWorkouts,
                    correction = corrections.day(epochDay),
                    nowMillis = nowMillis,
                )
                if (summary == null) this.days.delete(epochDay) else this.days.put(summary)
                markMonth(epochDay, nowMillis)
            }
        }
    }

    /**
     * A total Health Connect did not give this time — a failed call, a withdrawn permission — keeps
     * the one already stored, when that one was Health Connect's. A total is never zeroed by a miss.
     */
    private fun keepingStored(given: DayTotals, stored: HealthDayEntity?): DayTotals = DayTotals(
        steps = given.steps ?: stored?.steps?.takeIf { stored.stepsSource == "TOTAL" },
        distanceM = given.distanceM ?: stored?.distanceM?.takeIf { stored.distanceSource == "TOTAL" },
        activeKcal = given.activeKcal ?: stored?.activeKcal?.takeIf { stored.activeKcalSource == "TOTAL" },
        totalKcal = given.totalKcal ?: stored?.totalKcal?.takeIf { stored.totalKcalSource == "TOTAL" },
    )

    /** A workout's heart-rate figures from the readings inside it (D70); unchanged when none. */
    private suspend fun withHeartRate(workout: WorkoutEntity, maxHeartRate: Int?): WorkoutEntity {
        if (maxHeartRate == null) return workout
        val end = workout.startedAtMillis + workout.durationMinutes * 60_000L
        val samples = readings.ofKindBetween(HealthKind.HEART_RATE.name, workout.startedAtMillis, end)
            .map { it.startMillis to it.value }
        val figures = HeartRateZones.of(samples, end, maxHeartRate) ?: return workout
        val updated = workout.copy(
            avgHeartRate = figures.average,
            maxHeartRate = figures.maximum,
            zoneSeconds = figures.zonesAsText(),
            zoneMaxSource = "ESTIMATED",
        )
        if (updated != workout) workouts.update(updated)
        return updated
    }

    private suspend fun markMonth(epochDay: Long, nowMillis: Long) {
        val month = LocalDate.ofEpochDay(epochDay).toString().substring(0, 7)
        val known = bookkeeping.month(month)
        bookkeeping.putMonth(
            ArchiveMonthEntity(month = month, changedAtMillis = nowMillis, writtenAtMillis = known?.writtenAtMillis),
        )
    }
}
```

Two naming traps to avoid when compiling: the property `days` and the parameter `days` in `summarise`
(the snippet uses `this.days` for the DAO — rename the DAO property to `dayDao` if clearer), and
`sleep` shadowing nothing important. Rename freely for clarity; keep behaviour.

- [ ] **Step 4: Compile and run.** `--tests "com.metaself.app.data.health.*"`: the pure tests pass,
  `HealthRecordStoreTest` and `HealthRecordDaoTest` skip. `git status app/schemas` clean.

- [ ] **Step 5: Commit** the DAOs, `RoomHealthStore.kt`, the test:
  `feat: the health record's store applies batches and summarises days (D65, D69, D70)`.

---

### Task 6: Reading Health Connect

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/HealthPermissions.kt`
- Create: `app/src/main/java/com/metaself/app/data/health/HealthConnectReader.kt`

No unit test: like `HealthConnectSteps`, this only translates Health Connect's types, and a fake of
Health Connect would test the fake. It must compile against `1.1.0-alpha07` and never throw upwards
except where `HealthRecordSync` expects it (`changes`, `readWindow`, `changesToken` may throw; the sync
catches them). Check every class and property name below against the pinned library's sources jar in
`~/.gradle/caches` before writing; report any that differ.

- [ ] **Step 1: `HealthPermissions.kt`:**

```kotlin
package com.metaself.app.data.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import com.metaself.app.domain.health.HealthKind
import kotlin.reflect.KClass

/** Every kind's Health Connect record type and read permission (D66). */
object HealthPermissions {

    fun recordType(kind: HealthKind): KClass<out Record> = when (kind) {
        HealthKind.STEPS -> StepsRecord::class
        HealthKind.DISTANCE -> DistanceRecord::class
        HealthKind.ACTIVE_KCAL -> ActiveCaloriesBurnedRecord::class
        HealthKind.TOTAL_KCAL -> TotalCaloriesBurnedRecord::class
        HealthKind.HEART_RATE -> HeartRateRecord::class
        HealthKind.RESTING_HEART_RATE -> RestingHeartRateRecord::class
        HealthKind.HRV_RMSSD -> HeartRateVariabilityRmssdRecord::class
        HealthKind.OXYGEN_SATURATION -> OxygenSaturationRecord::class
        HealthKind.RESPIRATORY_RATE -> RespiratoryRateRecord::class
        HealthKind.WEIGHT -> WeightRecord::class
        HealthKind.BODY_FAT -> BodyFatRecord::class
        HealthKind.SLEEP -> SleepSessionRecord::class
        HealthKind.EXERCISE -> ExerciseSessionRecord::class
    }

    fun of(kind: HealthKind): String = HealthPermission.getReadPermission(recordType(kind))

    /** What the Connect button asks for: every kind, so a new device needs no app update (D66). */
    val ALL: Set<String> get() = HealthKind.entries.map(::of).toSet()
}
```

- [ ] **Step 2: `HealthConnectReader.kt`:**

```kotlin
package com.metaself.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.movement.ExerciseNames
import com.metaself.app.domain.health.HealthKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Health Connect, translated into [ReadRecord]s (D65–D67). Thin on purpose: every decision is in
 * `HealthRecordSync`, where it is tested.
 *
 * Totals go through the aggregation API, one metric per call, for the two reasons `HealthConnectSteps`
 * records: aggregation is what de-duplicates across apps (D12c), and one missing permission must not
 * take the other totals with it.
 */
@Singleton
class HealthConnectReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val problems: ProblemLog,
) : HealthSource {

    private val client: HealthConnectClient?
        get() = runCatching {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        }.getOrNull()

    private fun connect(): HealthConnectClient =
        client ?: throw IllegalStateException("Health Connect is not available")

    override suspend fun grantedKinds(): Set<HealthKind> = withContext(Dispatchers.IO) {
        val granted = runCatching { client?.permissionController?.getGrantedPermissions() }
            .getOrNull() ?: return@withContext emptySet()
        HealthKind.entries.filter { HealthPermissions.of(it) in granted }.toSet()
    }

    override suspend fun changesToken(kind: HealthKind): String = withContext(Dispatchers.IO) {
        connect().getChangesToken(ChangesTokenRequest(setOf(HealthPermissions.recordType(kind))))
    }

    override suspend fun changes(kind: HealthKind, token: String): ChangesPage = withContext(Dispatchers.IO) {
        val response = connect().getChanges(token)
        val upserted = response.changes.filterIsInstance<UpsertionChange>().map { it.record }
        ChangesPage(
            upserts = translate(upserted),
            deletedIds = response.changes.filterIsInstance<DeletionChange>().map { it.recordId },
            nextToken = response.nextChangesToken,
            hasMore = response.hasMore,
            expired = response.changesTokenExpired,
        )
    }

    override suspend fun readWindow(kind: HealthKind, fromMillis: Long, toMillis: Long): List<ReadRecord> =
        withContext(Dispatchers.IO) {
            val range = TimeRangeFilter.between(Instant.ofEpochMilli(fromMillis), Instant.ofEpochMilli(toMillis))
            translate(readAll(HealthPermissions.recordType(kind), range))
        }

    /** Every page. One page is a thousand records, a few days of a busy series (the first steps bug). */
    private suspend fun readAll(type: kotlin.reflect.KClass<out Record>, range: TimeRangeFilter): List<Record> {
        val out = mutableListOf<Record>()
        var page: String? = null
        do {
            @Suppress("UNCHECKED_CAST")
            val response = connect().readRecords(
                ReadRecordsRequest(recordType = type as kotlin.reflect.KClass<Record>, timeRangeFilter = range, pageToken = page),
            )
            out += response.records
            page = response.pageToken
        } while (page != null)
        return out
    }

    override suspend fun dayTotals(fromDay: Long, toDay: Long): Map<Long, DayTotals> = withContext(Dispatchers.IO) {
        val from = LocalDate.ofEpochDay(fromDay)
        val to = LocalDate.ofEpochDay(toDay)
        val steps = daily(StepsRecord.COUNT_TOTAL, "steps", from, to) { it.toInt() }
        val distance = daily(DistanceRecord.DISTANCE_TOTAL, "distance", from, to) { it.inMeters.roundToInt() }
        val active = daily(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, "active calories", from, to) {
            it.inKilocalories.roundToInt()
        }
        val total = daily(TotalCaloriesBurnedRecord.ENERGY_TOTAL, "total calories", from, to) {
            it.inKilocalories.roundToInt()
        }
        (steps.keys + distance.keys + active.keys + total.keys).associateWith { day ->
            DayTotals(steps[day], distance[day], active[day], total[day])
        }
    }

    private suspend fun <T : Any> daily(
        metric: AggregateMetric<T>,
        name: String,
        from: LocalDate,
        to: LocalDate,
        asInt: (T) -> Int,
    ): Map<Long, Int> = runCatching {
        connect().aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(metric),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        ).mapNotNull { bucket ->
            bucket.result[metric]?.let { bucket.startTime.toLocalDate().toEpochDay() to asInt(it) }
        }.toMap()
    }.onFailure {
        problems.record("health", "totals of $name: ${it::class.java.simpleName} ${it.message}")
    }.getOrDefault(emptyMap())

    private suspend fun translate(records: List<Record>): List<ReadRecord> = records.mapNotNull { record ->
        val origin = record.metadata.dataOrigin.packageName
        val id = record.metadata.id
        fun one(kind: HealthKind, at: Instant, value: Double) =
            ReadRecord.Reading(kind, origin, id, listOf(Sample(at.toEpochMilli(), null, value)))
        fun span(kind: HealthKind, start: Instant, end: Instant, value: Double) =
            ReadRecord.Reading(kind, origin, id, listOf(Sample(start.toEpochMilli(), end.toEpochMilli(), value)))
        when (record) {
            is StepsRecord -> span(HealthKind.STEPS, record.startTime, record.endTime, record.count.toDouble())
            is DistanceRecord -> span(HealthKind.DISTANCE, record.startTime, record.endTime, record.distance.inMeters)
            is ActiveCaloriesBurnedRecord ->
                span(HealthKind.ACTIVE_KCAL, record.startTime, record.endTime, record.energy.inKilocalories)
            is TotalCaloriesBurnedRecord ->
                span(HealthKind.TOTAL_KCAL, record.startTime, record.endTime, record.energy.inKilocalories)
            is HeartRateRecord -> ReadRecord.Reading(
                HealthKind.HEART_RATE, origin, id,
                record.samples.map { Sample(it.time.toEpochMilli(), null, it.beatsPerMinute.toDouble()) },
            )
            is RestingHeartRateRecord ->
                one(HealthKind.RESTING_HEART_RATE, record.time, record.beatsPerMinute.toDouble())
            is HeartRateVariabilityRmssdRecord ->
                one(HealthKind.HRV_RMSSD, record.time, record.heartRateVariabilityMillis)
            is OxygenSaturationRecord -> one(HealthKind.OXYGEN_SATURATION, record.time, record.percentage.value)
            is RespiratoryRateRecord -> one(HealthKind.RESPIRATORY_RATE, record.time, record.rate)
            is WeightRecord -> one(HealthKind.WEIGHT, record.time, record.weight.inKilograms)
            is BodyFatRecord -> one(HealthKind.BODY_FAT, record.time, record.percentage.value)
            is SleepSessionRecord -> ReadRecord.Night(
                origin, id, record.startTime.toEpochMilli(), record.endTime.toEpochMilli(), record.title,
                record.stages.map { StageSpan(stageName(it.stage), it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) },
            )
            is ExerciseSessionRecord -> session(record, origin, id)
            else -> null
        }
    }

    /** Distance and energy over the session's own time, each its own call (D4: null when none). */
    private suspend fun session(record: ExerciseSessionRecord, origin: String, id: String): ReadRecord.Session {
        val range = TimeRangeFilter.between(record.startTime, record.endTime)
        val distance = runCatching {
            connect().aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), range))[DistanceRecord.DISTANCE_TOTAL]
        }.getOrNull()
        val energy = runCatching {
            connect().aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range))[
                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
            ]
        }.getOrNull()
        return ReadRecord.Session(
            origin = origin,
            recordId = id,
            startMillis = record.startTime.toEpochMilli(),
            endMillis = record.endTime.toEpochMilli(),
            kind = WorkoutKinds.of(record.exerciseType),
            title = ExerciseNames.of(record.exerciseType, record.title),
            distanceM = distance?.inMeters?.roundToInt(),
            energyKcal = energy?.inKilocalories?.roundToInt(),
        )
    }

    private fun stageName(stage: Int): String = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> "AWAKE"
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "SLEEPING"
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "OUT_OF_BED"
        SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
        SleepSessionRecord.STAGE_TYPE_REM -> "REM"
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "AWAKE_IN_BED"
        else -> "UNKNOWN"
    }

    private companion object {
        @Suppress("unused")
        val ZONE: ZoneId = ZoneId.systemDefault()
    }
}
```

Remove the unused `ZONE` companion if lint flags it (it is there only to make the time-zone question
visible; totals use `LocalDateTime` ranges, which Health Connect interprets in the phone's zone). A
session's two aggregate calls are outside the sync's read budget; that is accepted (sessions are few)
and must be said in the KDoc of `session`.

- [ ] **Step 3: Compile.** `:app:compileDebugKotlin` → `exit 0`. Fix names against the library; report.

- [ ] **Step 4: Commit** both files: `feat: Health Connect is read into the health record's terms (D66)`.

---

### Task 7: Permissions, wiring, and copying on open

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/metaself/app/di/DataModule.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/screen/day/DayViewModel.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt`

- [ ] **Step 1: Manifest.** After the three existing health permissions add:

```xml
    <uses-permission android:name="android.permission.health.READ_DISTANCE" />
    <uses-permission android:name="android.permission.health.READ_TOTAL_CALORIES_BURNED" />
    <uses-permission android:name="android.permission.health.READ_HEART_RATE" />
    <uses-permission android:name="android.permission.health.READ_RESTING_HEART_RATE" />
    <uses-permission android:name="android.permission.health.READ_HEART_RATE_VARIABILITY" />
    <uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION" />
    <uses-permission android:name="android.permission.health.READ_RESPIRATORY_RATE" />
    <uses-permission android:name="android.permission.health.READ_WEIGHT" />
    <uses-permission android:name="android.permission.health.READ_BODY_FAT" />
    <uses-permission android:name="android.permission.health.READ_SLEEP" />
```

Replace the comment above the health permissions ("only the three things this app can actually
use…") with one that states D66: every kind that could serve the trainer is asked for once; a kind no
device writes stays empty. Add, as a direct child of `<manifest>`:

```xml
    <!-- Below Android 14 Health Connect is a separate app, and getSdkStatus can only see it if it is
         declared here (activity spec §1.1). Harmless above. -->
    <queries>
        <package android:name="com.google.android.apps.healthdata" />
    </queries>
```

If a `<queries>` element already exists, add the `<package>` inside it instead.

- [ ] **Step 2: `DataModule.kt`.** Add bindings:

```kotlin
    @Provides
    @Singleton
    fun provideHealthRows(): HealthRows = HealthRows(java.time.ZoneId.systemDefault())

    @Provides
    @Singleton
    fun provideHealthSource(reader: HealthConnectReader): HealthSource = reader

    @Provides
    @Singleton
    fun provideHealthStore(store: RoomHealthStore): HealthStore = store

    @Provides
    @Singleton
    fun provideHealthRecordCopier(sync: HealthRecordSync): HealthRecordCopier = sync
```

- [ ] **Step 3: `DayViewModel`.** Add a constructor parameter **with a default**, so tests and the
  simulated app keep compiling unchanged:

```kotlin
    /** Copying the health record (D67). Defaulted so tests and the walk need not supply one. */
    private val healthRecord: HealthRecordCopier = HealthRecordCopier.NONE,
```

In `init`, after `readTodaysSteps()`, add `copyTheHealthRecord()`, and in `refresh()`, after
`readMovement()` inside the `try`, add `healthRecord.copyNow()`. The new function:

```kotlin
    /**
     * The health record, copied on open (D67). Like the daily backup, nothing it does reaches the
     * screen, and nothing it fails at stops anything else.
     */
    private fun copyTheHealthRecord() {
        quietly { healthRecord.copyNow() }
    }
```

Confirm Hilt accepts a defaulted constructor parameter here (it injects every parameter regardless of
Kotlin defaults); the binding in Step 2 makes that possible.

- [ ] **Step 4: `MetaSelfNavHost.kt`.** The Connect launcher asks for everything:
  `onConnectSteps = { askForSteps.launch(HealthPermissions.ALL) }`, and its result callback also calls
  `settingsViewModel.refreshHealthRecord()` (added in Task 8) beside `refreshSteps()`.

- [ ] **Step 5: Compile and run the day tests.** `--tests "com.metaself.app.ui.screen.day.*"` → pass
  unchanged. (Task 8 adds `refreshHealthRecord`; if compiling the nav host fails before Task 8 exists, do
  Step 4 at the start of Task 8 instead and say so.)

- [ ] **Step 6: Commit** the four files: `feat: the health record is copied on open and on refresh (D66, D67)`.

---

### Task 8: The Settings line and the Connect button

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/health/HealthRecordWording.kt`
- Test: `app/src/test/java/com/metaself/app/ui/health/HealthRecordWordingTest.kt`
- Modify: `app/src/main/java/com/metaself/app/data/health/HealthPorts.kt` (`HealthRecordStatus`)
- Modify: `SettingsViewModel.kt`, `SettingsUiState` (wherever it is declared), `SettingsScreen.kt`,
  `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Failing test.**

```kotlin
package com.metaself.app.ui.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.health.HealthKind
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Every figure is invented. */
class HealthRecordWordingTest {

    private val now = 1_000_000_000L

    @Test
    fun `nothing copied yet says so`() {
        assertThat(HealthRecordWording.status(days = 0, earliest = null, lastCopiedMillis = null, nowMillis = now, catchingUp = false))
            .isEqualTo("Health record: nothing copied yet.")
    }

    @Test
    fun `the record says how far it reaches and when it was last copied`() {
        assertThat(
            HealthRecordWording.status(
                days = 52, earliest = LocalDate.of(2026, 8, 6),
                lastCopiedMillis = now - 2 * 60_000, nowMillis = now, catchingUp = true,
            ),
        ).isEqualTo("Health record: 52 days, from 6 August · last copied 2 minutes ago · still catching up")
    }

    @Test
    fun `one is not ones, and a fresh copy is just now`() {
        assertThat(
            HealthRecordWording.status(1, LocalDate.of(2026, 9, 3), now - 10_000, now, catchingUp = false),
        ).isEqualTo("Health record: 1 day, from 3 September · last copied just now")
    }

    @Test
    fun `hours and days read as hours and days`() {
        assertThat(HealthRecordWording.ago(now - 3 * 3_600_000, now)).isEqualTo("3 hours ago")
        assertThat(HealthRecordWording.ago(now - 2 * 86_400_000L, now)).isEqualTo("2 days ago")
        assertThat(HealthRecordWording.ago(now - 60_000, now)).isEqualTo("1 minute ago")
    }

    @Test
    fun `kinds not allowed are named, with how to allow them`() {
        assertThat(HealthRecordWording.notAllowed(setOf(HealthKind.HEART_RATE, HealthKind.SLEEP)))
            .isEqualTo("Heart rate and sleep are not allowed — tap Connect to allow them.")
        assertThat(HealthRecordWording.notAllowed(setOf(HealthKind.SLEEP)))
            .isEqualTo("Sleep is not allowed — tap Connect to allow it.")
        assertThat(HealthRecordWording.notAllowed(emptySet())).isNull()
    }

    @Test
    fun `many kinds are counted rather than listed`() {
        assertThat(HealthRecordWording.notAllowed(HealthKind.entries.toSet()))
            .isEqualTo("13 kinds of health data are not allowed — tap Connect to allow them.")
    }

    /** D71: the raw readings go to Drive in the next phase; until then they are on this phone only. */
    @Test
    fun `the detailed readings are said not to be backed up`() {
        assertThat(HealthRecordWording.NOT_BACKED_UP)
            .isEqualTo("Detailed readings are kept on this phone only; the daily backup has the summaries.")
    }
}
```

- [ ] **Step 2: Run, see it fail.**

- [ ] **Step 3: `HealthRecordWording.kt`:**

```kotlin
package com.metaself.app.ui.health

import com.metaself.app.domain.health.HealthKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What Settings says about the health record (D65–D71). Counts and dates, never reassurance. */
object HealthRecordWording {

    const val NOT_BACKED_UP =
        "Detailed readings are kept on this phone only; the daily backup has the summaries."

    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Locale.UK)

    fun status(days: Int, earliest: LocalDate?, lastCopiedMillis: Long?, nowMillis: Long, catchingUp: Boolean): String {
        if (days == 0 || earliest == null) return "Health record: nothing copied yet."
        return buildString {
            append("Health record: ")
            append(if (days == 1) "1 day" else "$days days")
            append(", from ").append(earliest.format(DAY_MONTH))
            lastCopiedMillis?.let { append(" · last copied ").append(ago(it, nowMillis)) }
            if (catchingUp) append(" · still catching up")
        }
    }

    fun ago(thenMillis: Long, nowMillis: Long): String {
        val minutes = (nowMillis - thenMillis) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> plural(minutes, "minute") + " ago"
            minutes < 24 * 60 -> plural(minutes / 60, "hour") + " ago"
            else -> plural(minutes / (24 * 60), "day") + " ago"
        }
    }

    fun notAllowed(kinds: Set<HealthKind>): String? {
        if (kinds.isEmpty()) return null
        if (kinds.size == 1) return "${kinds.single().displayName} is not allowed — tap Connect to allow it."
        if (kinds.size > 3) return "${kinds.size} kinds of health data are not allowed — tap Connect to allow them."
        val names = kinds.sortedBy { it.ordinal }.map { it.displayName }
        val listed = names.dropLast(1).joinToString(", ") + " and " + names.last().replaceFirstChar { it.lowercase() }
        return "$listed are not allowed — tap Connect to allow them."
    }

    private fun plural(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"
}
```

Note the lower-casing: "Heart rate and sleep" — only the names after the first are lower-cased, and
only their first letter.

- [ ] **Step 4: `HealthRecordStatus` in `HealthPorts.kt`:**

```kotlin
/** What Settings shows about the record. */
data class HealthRecordState(
    val days: Int = 0,
    val earliest: Long? = null,
    val lastCopiedMillis: Long? = null,
    val catchingUp: Boolean = false,
    val notAllowed: Set<com.metaself.app.domain.health.HealthKind> = emptySet(),
)

interface HealthRecordStatus {
    suspend fun current(): HealthRecordState

    companion object {
        val NONE = object : HealthRecordStatus {
            override suspend fun current() = HealthRecordState()
        }
    }
}
```

and implement it in a small class `RoomHealthRecordStatus @Inject constructor(database: MetaSelfDatabase, source: HealthSource)`
in `data/health/RoomHealthRecordStatus.kt`: `days` and `earliest` from `healthDayDao().observeCount().first()`
and `observeEarliest().first()`; `lastCopiedMillis` = max `tokenAtMillis` over `observeSync().first()`;
`catchingUp` = any sync row of a granted kind with `catchUpDone == false`, or any granted kind with no
row yet; `notAllowed` = `HealthKind.entries - granted` when at least one kind is granted, else empty (with
nothing granted, the existing "Off. Allow MetaSelf to read your steps…" line and the Connect button
already say it). Bind it in `DataModule` (`HealthRecordStatus` →
`RoomHealthRecordStatus`, `@Singleton`).

- [ ] **Step 5: `SettingsViewModel`.** Add a constructor parameter with a default,
  `private val healthStatus: HealthRecordStatus = HealthRecordStatus.NONE,`; a `MutableStateFlow<HealthRecordState>`;
  `fun refreshHealthRecord()` that reads `healthStatus.current()` inside `quietly` (the same pattern
  `refreshSteps` uses); combine it into `SettingsUiState` as `healthRecord: HealthRecordState`
  (add the field with a default to `SettingsUiState`). Call `refreshHealthRecord()` wherever
  `refreshSteps()` is called on screen open (`MetaSelfNavHost`'s `LaunchedEffect`).

- [ ] **Step 6: `SettingsScreen`, the steps section.** Rename the section title string
  `settings_steps_title` value to `Movement` (D62's word, and the spec §4.4), and
  `settings_steps_connect` to `Allow MetaSelf to read your health data`. Below the existing band-energy
  line, add three lines in the same `bodySmall`/`onSurfaceVariant` style:

```kotlin
            Text(
                text = HealthRecordWording.status(
                    days = state.healthRecord.days,
                    earliest = state.healthRecord.earliest?.let(LocalDate::ofEpochDay),
                    lastCopiedMillis = state.healthRecord.lastCopiedMillis,
                    nowMillis = System.currentTimeMillis(),
                    catchingUp = state.healthRecord.catchingUp,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HealthRecordWording.notAllowed(state.healthRecord.notAllowed)?.let { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.healthRecord.days > 0) {
                Text(
                    HealthRecordWording.NOT_BACKED_UP,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
```

and show the Connect button when steps are not permitted **or** any kind is not allowed:
`if (state.stepAccess == StepAccess.NOT_PERMITTED || state.healthRecord.notAllowed.isNotEmpty())`.
(`System.currentTimeMillis()` in a composable is acceptable here: the line is re-read on every Settings
open; if the screen already receives a clock, use that instead.)

- [ ] **Step 7: Run** `--tests "com.metaself.app.ui.health.*" --tests "com.metaself.app.ui.screen.settings.*"`
  → pass. If any existing Settings render test asserts the old section title or button text, update the
  expected text to the new words and say so in the report.

- [ ] **Step 8: Commit** every file touched: `feat: Settings says how far the health record reaches (D65, D66)`.

---

### Task 9: A restore clears the copying bookmarks; an export reads nights whole

**Files:**
- Modify: `app/src/main/java/com/metaself/app/data/backup/BackupRepository.kt`
- Modify: `app/src/test/java/com/metaself/app/data/backup/BackupRestoreOrderTest.kt`,
  `BackupRoundTripTest.kt`, `app/src/test/java/com/metaself/app/ui/screen/settings/SettingsViewModelTest.kt`

The two phase-1 review notes. A restore deletes synced workouts and nights but used to keep the copying
bookmarks, so anything recorded after the backup was made would never be read again; clearing them makes
the next open catch up from scratch, and because a record read again replaces its rows, nothing is
duplicated. And the export read stages and nights in two queries, which a copy running between them
could split.

- [ ] **Step 1: Failing order test.** In `BackupRestoreOrderTest`, add `bookkeeping = dao(prefix = "bookkeeping.")`
  to every `BackupRepository(...)` construction, and insert `"bookkeeping.clearSync"` into the expected log
  of `the settings are written inside the transaction, …` directly after `"corrections.deleteAll"`.

- [ ] **Step 2: Run, see it fail** (no such parameter).

- [ ] **Step 3: `BackupRepository`.** Add constructor parameter `private val bookkeeping: HealthBookkeepingDao,`
  after `corrections`; in `restore`'s transaction, after `corrections.deleteAll()`, add

```kotlin
                // The copying starts again from scratch: a record read again replaces its rows, so
                // nothing is doubled, and nothing recorded after this file was made is missed.
                bookkeeping.clearSync()
```

and in `export`, replace the two-query sleep block with one:

```kotlin
            sleep = sleep.allNights().map { night -> night.session.toBackup(night.stages) },
```

Add `bookkeeping = db.healthBookkeepingDao()` to `BackupRoundTripTest.repository(...)`, and to
`SettingsViewModelTest`'s `Daos` (`val bookkeeping: HealthBookkeepingDao = table(failing)`) and
`backups(...)`.

- [ ] **Step 4: Run** `--tests "com.metaself.app.data.backup.*" --tests "com.metaself.app.ui.screen.settings.*"` → pass
  (round trip skips).

- [ ] **Step 5: Commit** the four files:
  `fix: a restore makes the health record copy again; an export reads each night whole`.

---

### Task 10: Version, the whole suite, CI, release

- [ ] **Step 1:** `app/build.gradle.kts`: `versionCode = 108` → `109`, `versionName = "0.54.0"` → `"0.55.0"`.
- [ ] **Step 2:** `CLAUDE.md` Testing: "nine classes" → "ten classes", add `HealthRecordStoreTest`.
- [ ] **Step 3:** Whole suite (check `free -m`): `~/bin/gradlew-safe :app:testDebugUnitTest` → 0 failures;
  skipped classes exactly the ten, counted from the XML. `git status app/schemas` clean.
- [ ] **Step 4:** `~/bin/gradlew-safe :app:lintDebug` → exit 0.
- [ ] **Step 5:** Anonymisation read of every added line, test names included.
- [ ] **Step 6:** Commit, push, PR titled `0.55.0: the health record is copied (D65–D72, phase 2)`. The body
  states what is copied and when, the choices (7/30/8/730/60) as choices, that the history-limit
  measurement is a problem-log line, what CI must show (`HealthRecordStoreTest` ran), and what the phone
  must show. No session link.
- [ ] **Step 7:** Wait for CI; merge only when green with 0 skipped; squash, delete branch.
- [ ] **Step 8:** `free -m` ≥ ~6000 MB, then `~/bin/ms-release`; send the APK. **Phone checks:** the
  Connect button in Settings → Movement opens Health Connect asking for the new kinds; after allowing and
  reopening the app a few times, the status line counts days and stops saying "still catching up";
  Settings → Recent problems shows the `health` line saying from which date reading was refused (the
  history limit, measured).

## Amended after review (2026-09-26)

A review of Tasks 1–4 changed these rules; the code and its tests are the record of each.

1. **Zones:** boundaries compared exactly (`bpm × 10` against `max × k`), so a rate exactly on 60 % or 70 % is no longer a zone low; time is summed in milliseconds and rounded once; samples are sorted first; each sample still capped at `LONGEST_SAMPLE_SECONDS`.
2. **Expired token:** the fresh token is taken first, then the window is re-read and replaced, then the bookmark saved.
3. **Order of work:** phase A takes a token for every granted kind (uncounted) or drains its changes (counted, until the budget is spent); phase B catches up round-robin, one week per kind per turn, newest first; days touched are summarised after phase A and after each turn. Accepted: a crash between a saved bookmark and its summarise leaves that day stale until it changes again.
4. **Cancellation:** the totals call rethrows cancellation; `copyNow` catches any `Throwable` except cancellation, so an error from the client never escapes (D8).
5. **Refusals:** an old refusal ends the catch-up only when it is not transient (I/O, RemoteException, or a rate limit); a transient one keeps the cursor. The end of a catch-up by empty weeks or by two years is logged with its date; log dates are in the phone's zone.
6. **Tests added** for one copy at a time, the two-year stop, and the next open resuming from the cursor.
7. **Sleep:** overlapping nights from different apps count once (the longest); each night kept is counted by its stages or, with none, its whole length; milliseconds are summed and rounded once; UNKNOWN counts as asleep.
8. **An empty day** is decided by checking every figure is null; each mean is computed once.
9. **Rows:** the zone is asked for at each row, so a travelling phone files by its current zone; a workout's length is rounded to the nearest minute.
