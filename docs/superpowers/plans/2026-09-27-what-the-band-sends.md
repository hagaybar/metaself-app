# What the band sends — Implementation Plan (D80)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D80. A page, **Settings → Movement and health → "What the band sends"**, that says what has
arrived in the stored health record over the last 30 days: each of the thirteen kinds with its count,
its days, the apps that wrote it and its first and last day; the workouts by kind, by source and by
which details the copied ones carry; on how many days each figure of the daily summary has a value; a
line naming what Health Connect cannot carry; and **Copy as text**.

**Architecture:**

```
domain/health/BandReport.kt              pure: counts in → the report                   JUnit 5
ui/health/BandReportWording.kt           pure: every sentence, and the copied text       JUnit 5
data/health/HealthReadingDao.kt          + countsByDay (the one new query)               Robolectric, CI only
data/health/BandRecord.kt                port + Room adapter + row → report mapping      JUnit 5 (mapping); Room in CI
data/health/AppLabels.kt                 package name → the phone's name for the app     Robolectric
AndroidManifest.xml                      <queries> for apps that ask Health Connect      —
di/DataModule.kt                         binds BandRecord, AppLabels                     assembleDebug
ui/screen/settings/BandReportViewModel.kt  one read on open; labels; D8                  JUnit 5 + coroutines-test
ui/screen/settings/BandReportPage.kt     the page                                        Robolectric (JUnit 4)
ui/screen/settings/MovementSettingsPage.kt  + the row                                    Robolectric (JUnit 4)
ui/nav/SettingsGraph.kt, SettingsDestination.kt, MetaSelfNavHost.kt  the route          Robolectric (JUnit 4)
```

**Decision:** the owner's, 2026-09-27 — D80 in
`docs/superpowers/specs/2026-09-27-what-the-band-sends-design.md`. Tables: D65–D69 in
`docs/superpowers/specs/2026-09-26-health-record-design.md`. Settings pages: D79 in
`docs/superpowers/specs/2026-09-27-settings-in-six-pages-design.md`.

**Tech Stack:** Kotlin, Compose (Material 3), Hilt, Room (read only), kotlinx-coroutines-test, JUnit 5 +
Truth, Robolectric (JUnit 4) for render and Room tests. No new dependency.

**Red lines (stop and report if crossed):**

- **No schema change.** `app/schemas` untouched; no entity, column, index or migration. One new DAO
  *query* (`HealthReadingDao.countsByDay`) is allowed; it reads existing columns.
- **Nothing here writes, and nothing here reads Health Connect.** Only Room. Which kinds are not allowed
  comes from the Settings view model's existing `healthRecord.notAllowed`, already refreshed each time a
  Settings destination opens.
- **No reading's value leaves the page.** The copied text is counts, dates and app names only.
- **D8:** a failed read is logged (kind `"band report"`) and said on the page; nothing throws upwards.
- **Anonymisation:** every figure in a test is invented and round, and says so; the writing app is
  `com.example.band` (a second one `com.example.phone`). No real device, app or package name anywhere.
- **Never `git add -A`; never bare `./gradlew`; never pipe a build whose result is reported.** Never
  stage `app/.settings/*` or `tools/__pycache__`.
- **No version bump, no push, no release.**

## What was checked before writing item 4

The pinned client is `androidx.health.connect:connect-client:1.1.0` (`gradle/libs.versions.toml`). Its
`classes.jar` (from `~/.gradle/caches/modules-2/.../connect-client-1.1.0.aar`), listed on 2026-09-27,
has 41 record types in `androidx.health.connect.client.records`. **`Vo2MaxRecord` is one of
them.** Nothing named stress, training load, recovery or readiness exists anywhere in the jar.

So the spec's sentence is adjusted: VO₂ max is **not** named as having no Health Connect type. The line
reads:

> Stress, training load and recovery time are not shared through Health Connect; they stay in the
> band's app. VO₂ max has a Health Connect record, but this app does not copy it.

The second sentence is kept because it is the truthful half of what the spec wanted said: VO₂ max is
none of the thirteen kinds (D66), so it cannot appear on this page whether or not the band writes it.

## Design questions the spec does not answer — settled here

1. **What a "reading" is counted as.** A stored row: for heart rate each sample is a row (D68), so "500
   readings" of heart rate is 500 samples. Sleep is counted in **nights** (`sleep_sessions` rows) and
   workouts in **workouts** (copied `workouts` rows with an origin), so a kind's unit word follows it.
2. **The window** is today and the 29 days before it, by the stored `epochDay` of each row. "first" and
   "last" are the first and last day *within the window* that has a row; the last day reads "today"
   when it is today.
3. **Workouts in item 1 and item 2.** Item 1's "Workouts" line counts only *copied* sessions (typed ones
   did not arrive from anywhere). Item 2 counts all workouts, **hidden ones included** — a hidden session
   still arrived. Its details ("distance 12 of 14 …") are counted over the copied ones only, as the spec
   says ("for the band's ones"). "calories" means a band figure: `energySource = BAND` with a value; a
   MET estimate is this app's guess and is not something the band sent (D4).
4. **Source words.** The spec's "band / typed" is written **"copied 14 · typed 2"**: a copied session may
   come from any app writing to Health Connect, and the page already names the apps in item 1.
5. **Kinds merged by name.** `OTHER` and an unrecognised kind both read "Exercise"
   (`MovementWeekWording.kindName`), so their counts are added under one name.
6. **"not allowed"** comes from `SettingsUiState.healthRecord.notAllowed`. With nothing granted at all
   that set is empty by design (`HealthRecordState.from`), so every kind then reads "nothing arrived" —
   true, and the Movement page's own status line already says the reading is off.
7. **Its own view model.** `SettingsViewModel` backs six pages and holds nothing this page needs except
   `notAllowed`. This page's read is one-shot, belongs to this page only, and should not run every time
   *any* Settings page opens. A small `BandReportViewModel` on the page's own back-stack entry reads
   once when the page opens and is gone when it closes; `notAllowed` is taken from the shared view model
   the destination already has. It also keeps the fake for its test to one method.
8. **App names.** `PackageManager.getApplicationLabel`, falling back to the package name on any
   failure or a blank label. From Android 11 another app's package is invisible unless the manifest
   declares it. Naming the band's package in `<queries>` would name the owner's app in a public
   repository, so instead the manifest declares the two intents an app **must** declare to ask for
   Health Connect permissions — `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`, and
   `android.intent.action.VIEW_PERMISSION_USAGE` in category `android.intent.category.HEALTH_PERMISSIONS`
   (Android 14). That makes visible exactly the apps that use Health Connect and nothing else.
   **Unverified on a phone:** if a writing app is still invisible, its package name is shown, which is
   still the truth. Tests use `com.example.band`, which no phone has, so they see the fallback.
9. **Where it goes.** A row under the Movement page's existing controls, titled "What the band sends"
   with the line "What has arrived in the last 30 days, kind by kind", drawn like an index row (whole
   row one tap target). Its route is `settings/movement/band`, inside the Settings graph, so back returns
   to the Movement page and the shared view model is the same one.
10. **Dates use `Locale.US`** ("5 Aug", "3 Sep"): Java 17's `Locale.UK` gives "Sept"
    (`MovementWeekWording`'s KDoc records the measurement).
11. **While the read is under way** the page shows its title only; a read of four small queries is not
    worth a spinner.

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

(For this plan the log file is `/tmp/ms-band.log`. The ten skipping classes are the eight `CLAUDE.md`
names plus `HealthRecordDaoTest` and `HealthRecordStoreTest`; `CLAUDE.md`'s list is stale. This plan's
Room tests go **inside `HealthRecordDaoTest`**, so the count stays at ten.)

---

### Task 1: The report, as numbers

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/health/BandReport.kt`
- Test: `app/src/test/java/com/metaself/app/domain/health/BandReportTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.metaself.app.domain.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test

/**
 * What arrived, counted (D80). TEST_EPOCH_DAY is 3 Sep 2026, so the window is 5 Aug (20,670) to 3 Sep.
 * Every count is invented and round; `com.example.band` and `com.example.phone` are invented apps.
 */
class BandReportTest {

    private val today = TEST_EPOCH_DAY
    private val from = BandReport.fromDayFor(today)

    @Test
    fun `the window is today and the 29 days before it`() {
        assertThat(from).isEqualTo(today - 29)
        assertThat(report().windowDays).isEqualTo(30)
    }

    @Test
    fun `every kind is listed, in order, those with nothing included`() {
        assertThat(report().kinds.map { it.kind }).containsExactlyElementsIn(HealthKind.entries).inOrder()
        assertThat(report().kinds.single { it.kind == HealthKind.WEIGHT })
            .isEqualTo(KindArrivals(HealthKind.WEIGHT, count = 0, days = 0, origins = emptyList(), firstDay = null, lastDay = null))
    }

    @Test
    fun `a kind's rows are summed, its days counted once, and its apps listed most first`() {
        val arrivals = (from..today).map { Arrival(HealthKind.STEPS, BAND, it, 10) } +
            Arrival(HealthKind.STEPS, PHONE, today, 5)

        val steps = report(arrivals = arrivals).kinds.single { it.kind == HealthKind.STEPS }

        assertThat(steps).isEqualTo(
            KindArrivals(HealthKind.STEPS, count = 305, days = 30, origins = listOf(BAND, PHONE), firstDay = from, lastDay = today),
        )
    }

    @Test
    fun `rows outside the window are not counted`() {
        val arrivals = listOf(
            Arrival(HealthKind.HEART_RATE, BAND, from - 1, 100),
            Arrival(HealthKind.HEART_RATE, BAND, today - 9, 400),
            Arrival(HealthKind.HEART_RATE, BAND, today, 100),
        )

        val beats = report(arrivals = arrivals).kinds.single { it.kind == HealthKind.HEART_RATE }

        assertThat(beats.count).isEqualTo(500)
        assertThat(beats.days).isEqualTo(2)
        assertThat(beats.firstDay).isEqualTo(today - 9)
    }

    @Test
    fun `workouts arrive from the copied sessions, not from any row handed in as exercise`() {
        val report = report(
            arrivals = listOf(Arrival(HealthKind.EXERCISE, BAND, today, 7)),
            workouts = listOf(copied(WorkoutKind.WALK), copied(WorkoutKind.RUN), typed(WorkoutKind.SWIM)),
        )

        val exercise = report.kinds.single { it.kind == HealthKind.EXERCISE }
        assertThat(exercise.count).isEqualTo(2)
        assertThat(exercise.origins).containsExactly(BAND)
    }

    @Test
    fun `workouts are counted by kind, by source, and by the details the copied ones carry`() {
        val workouts = List(3) { copied(WorkoutKind.WALK, distance = true, heartRate = true, title = true) } +
            copied(WorkoutKind.RUN, calories = true, heartRate = true) +
            typed(WorkoutKind.SWIM)

        assertThat(report(workouts = workouts).workouts).isEqualTo(
            WorkoutArrivals(
                total = 5,
                byKind = listOf(WorkoutKind.WALK to 3, WorkoutKind.RUN to 1, WorkoutKind.SWIM to 1),
                copied = 4,
                typed = 1,
                copiedWithDistance = 3,
                copiedWithCalories = 1,
                copiedWithHeartRate = 4,
                copiedWithTitle = 3,
            ),
        )
    }

    @Test
    fun `each figure of the daily summary is counted on the days it has a value`() {
        val days = (from..today).map { day ->
            DayCoverage(day, if (day == today - 1) setOf(DayFigure.STEPS, DayFigure.SLEEP) else setOf(DayFigure.STEPS))
        }

        val counted = report(days = days).daysWith

        assertThat(counted.keys).containsExactlyElementsIn(DayFigure.entries)
        assertThat(counted[DayFigure.STEPS]).isEqualTo(30)
        assertThat(counted[DayFigure.SLEEP]).isEqualTo(1)
        assertThat(counted[DayFigure.OXYGEN]).isEqualTo(0)
    }

    @Test
    fun `the apps that wrote anything are gathered once`() {
        val report = report(
            arrivals = listOf(Arrival(HealthKind.STEPS, BAND, today, 1), Arrival(HealthKind.WEIGHT, PHONE, today, 1)),
            workouts = listOf(copied(WorkoutKind.WALK)),
        )

        assertThat(report.origins).containsExactly(BAND, PHONE)
    }

    private fun report(
        arrivals: List<Arrival> = emptyList(),
        workouts: List<ArrivedWorkout> = emptyList(),
        days: List<DayCoverage> = emptyList(),
    ) = BandReport.of(from, today, arrivals, workouts, days)

    private fun copied(
        kind: WorkoutKind,
        distance: Boolean = false,
        calories: Boolean = false,
        heartRate: Boolean = false,
        title: Boolean = false,
    ) = ArrivedWorkout(today, kind, typed = false, origin = BAND, distance, calories, heartRate, title)

    private fun typed(kind: WorkoutKind) =
        ArrivedWorkout(today, kind, typed = true, origin = null, hasDistance = false, hasCalories = false, hasHeartRate = false, hasTitle = false)

    private companion object {
        const val BAND = "com.example.band"
        const val PHONE = "com.example.phone"
    }
}
```

- [ ] **Step 2: Run it to see it fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.health.BandReportTest" > /tmp/ms-band.log 2>&1; echo "exit $?"
grep -m5 "e: " /tmp/ms-band.log
```

Expected: `exit 1`; `Unresolved reference: BandReport`.

- [ ] **Step 3: Write the report.** Create `app/src/main/java/com/metaself/app/domain/health/BandReport.kt`:

```kotlin
package com.metaself.app.domain.health

import com.metaself.app.domain.movement.WorkoutKind

/** How many of [kind]'s rows [origin] wrote on [epochDay]. A night is one sleep row. */
data class Arrival(val kind: HealthKind, val origin: String, val epochDay: Long, val count: Int)

/** One stored workout, as the report needs it: what it is, where it came from, which details it has. */
data class ArrivedWorkout(
    val epochDay: Long,
    val kind: WorkoutKind,
    val typed: Boolean,
    /** The writing app; null for a typed workout. */
    val origin: String?,
    val hasDistance: Boolean,
    /** A figure the band sent (energy source BAND), not this app's estimate (D4). */
    val hasCalories: Boolean,
    /** Worked out here from the readings inside the session (D70). */
    val hasHeartRate: Boolean,
    val hasTitle: Boolean,
)

/** The figures of the daily summary (D69) the report counts days for, in the spec's order. */
enum class DayFigure {
    STEPS, DISTANCE, ACTIVE_KCAL, TOTAL_KCAL, RESTING_HEART_RATE, HRV, OXYGEN, BREATHING, SLEEP, WORKOUTS,
}

/** Which figures one stored day has a value for. */
data class DayCoverage(val epochDay: Long, val figures: Set<DayFigure>)

/** One kind over the window. [origins] are the writing apps, most rows first, a tie by name. */
data class KindArrivals(
    val kind: HealthKind,
    val count: Int,
    val days: Int,
    val origins: List<String>,
    val firstDay: Long?,
    val lastDay: Long?,
) {
    companion object {
        fun of(kind: HealthKind, rows: List<Arrival>): KindArrivals = KindArrivals(
            kind = kind,
            count = rows.sumOf { it.count },
            days = rows.map { it.epochDay }.distinct().size,
            origins = rows.groupBy { it.origin }
                .mapValues { (_, byApp) -> byApp.sumOf { it.count } }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key },
            firstDay = rows.minOfOrNull { it.epochDay },
            lastDay = rows.maxOfOrNull { it.epochDay },
        )
    }
}

/**
 * The window's workouts, hidden ones included — a hidden session still arrived. The `copiedWith…`
 * counts are over the [copied] ones only.
 */
data class WorkoutArrivals(
    val total: Int = 0,
    /** Most first; a tie in [WorkoutKind]'s order. */
    val byKind: List<Pair<WorkoutKind, Int>> = emptyList(),
    val copied: Int = 0,
    val typed: Int = 0,
    val copiedWithDistance: Int = 0,
    val copiedWithCalories: Int = 0,
    val copiedWithHeartRate: Int = 0,
    val copiedWithTitle: Int = 0,
)

/**
 * What arrived in the stored health record over [fromDay]..[toDay], kind by kind (D80): counts, days,
 * dates and the apps that wrote them — never a reading's value. Pure, so it is tested without Room.
 */
data class BandReport(
    val fromDay: Long,
    val toDay: Long,
    /** Every one of the thirteen kinds, in [HealthKind]'s order, those with nothing included. */
    val kinds: List<KindArrivals>,
    val workouts: WorkoutArrivals,
    /** On how many days of the window each figure has a value; every figure is a key. */
    val daysWith: Map<DayFigure, Int>,
) {
    val windowDays: Int get() = (toDay - fromDay + 1).toInt()

    /** Every app that wrote anything, so each name is looked up once. */
    val origins: Set<String> get() = kinds.flatMapTo(mutableSetOf()) { it.origins }

    companion object {
        /** The last 30 days, today included: the length of the copying window (D80). */
        const val WINDOW_DAYS = 30

        fun fromDayFor(today: Long): Long = today - (WINDOW_DAYS - 1)

        /**
         * Rows outside [fromDay]..[toDay] are ignored. Workouts' arrivals are worked out from
         * [workouts] — the copied ones with an origin — so any [Arrival] of [HealthKind.EXERCISE]
         * handed in is ignored rather than counted twice.
         */
        fun of(
            fromDay: Long,
            toDay: Long,
            arrivals: List<Arrival>,
            workouts: List<ArrivedWorkout>,
            days: List<DayCoverage>,
        ): BandReport {
            val window = fromDay..toDay
            val theWorkouts = workouts.filter { it.epochDay in window }
            val copied = theWorkouts.filter { !it.typed }
            val sessions = copied.mapNotNull { w -> w.origin?.let { Arrival(HealthKind.EXERCISE, it, w.epochDay, 1) } }
            val rows = (arrivals.filter { it.kind != HealthKind.EXERCISE } + sessions)
                .filter { it.epochDay in window && it.count > 0 }
                .groupBy { it.kind }
            val theDays = days.filter { it.epochDay in window }.distinctBy { it.epochDay }

            return BandReport(
                fromDay = fromDay,
                toDay = toDay,
                kinds = HealthKind.entries.map { kind -> KindArrivals.of(kind, rows[kind].orEmpty()) },
                workouts = WorkoutArrivals(
                    total = theWorkouts.size,
                    byKind = theWorkouts.groupingBy { it.kind }.eachCount().entries
                        .sortedWith(compareByDescending<Map.Entry<WorkoutKind, Int>> { it.value }.thenBy { it.key.ordinal })
                        .map { it.key to it.value },
                    copied = copied.size,
                    typed = theWorkouts.size - copied.size,
                    copiedWithDistance = copied.count { it.hasDistance },
                    copiedWithCalories = copied.count { it.hasCalories },
                    copiedWithHeartRate = copied.count { it.hasHeartRate },
                    copiedWithTitle = copied.count { it.hasTitle },
                ),
                daysWith = DayFigure.entries.associateWith { figure -> theDays.count { figure in it.figures } },
            )
        }
    }
}
```

- [ ] **Step 4: Run it to see it pass.** Same command as Step 2. Expected: `exit 0`, no `FAILED`.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/health/BandReport.kt \
        app/src/test/java/com/metaself/app/domain/health/BandReportTest.kt
git commit -m "feat: what arrived in the health record, counted kind by kind (D80)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: What the page says

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/health/BandReportWording.kt`
- Test: `app/src/test/java/com/metaself/app/ui/health/BandReportWordingTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.metaself.app.ui.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.DayFigure
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.health.KindArrivals
import com.metaself.app.domain.health.WorkoutArrivals
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test

/**
 * Every sentence of "What the band sends" (D80). TEST_EPOCH_DAY is 3 Sep 2026; the window starts
 * 5 Aug. Every count is invented and round; the apps are invented.
 */
class BandReportWordingTest {

    private val today = TEST_EPOCH_DAY
    private val from = BandReport.fromDayFor(today)

    @Test
    fun `a kind that arrived says how many, on how many days, from which app`() {
        val steps = KindArrivals(HealthKind.STEPS, 1_200, 30, listOf(BAND), from, today)

        assertThat(BandReportWording.kindLine(steps, emptySet(), emptyMap()))
            .isEqualTo("1,200 readings · 30 days · from com.example.band")
        assertThat(BandReportWording.kindDates(steps, today)).isEqualTo("first 5 Aug · last today")
    }

    @Test
    fun `an app the phone knows is named by its label`() {
        val steps = KindArrivals(HealthKind.STEPS, 1, 1, listOf(BAND, PHONE), today - 1, today - 1)

        assertThat(BandReportWording.kindLine(steps, emptySet(), mapOf(BAND to "Example Band")))
            .isEqualTo("1 reading · 1 day · from Example Band and com.example.phone")
        assertThat(BandReportWording.kindDates(steps, today)).isEqualTo("first 2 Sep · last 2 Sep")
    }

    @Test
    fun `three apps are listed with commas and a last and`() {
        val beats = KindArrivals(HealthKind.HEART_RATE, 30, 1, listOf("a.one", "b.two", "c.three"), today, today)

        assertThat(BandReportWording.kindLine(beats, emptySet(), emptyMap()))
            .isEqualTo("30 readings · 1 day · from a.one, b.two and c.three")
    }

    @Test
    fun `sleep counts nights and workouts count workouts`() {
        val sleep = KindArrivals(HealthKind.SLEEP, 20, 20, listOf(BAND), from, today)
        val exercise = KindArrivals(HealthKind.EXERCISE, 1, 1, listOf(BAND), today, today)

        assertThat(BandReportWording.kindLine(sleep, emptySet(), emptyMap())).startsWith("20 nights · 20 days")
        assertThat(BandReportWording.kindLine(exercise, emptySet(), emptyMap())).startsWith("1 workout · 1 day")
    }

    @Test
    fun `a kind with nothing says so, or that it is not allowed`() {
        val nothing = KindArrivals(HealthKind.WEIGHT, 0, 0, emptyList(), null, null)

        assertThat(BandReportWording.kindLine(nothing, emptySet(), emptyMap())).isEqualTo("nothing arrived")
        assertThat(BandReportWording.kindLine(nothing, setOf(HealthKind.WEIGHT), emptyMap())).isEqualTo("not allowed")
        assertThat(BandReportWording.kindDates(nothing, today)).isNull()
    }

    @Test
    fun `workouts are said by count, kind, source and detail`() {
        val workouts = WorkoutArrivals(
            total = 16,
            byKind = listOf(WorkoutKind.WALK to 12, WorkoutKind.RUN to 2, WorkoutKind.OTHER to 1, WorkoutKind.UNRECOGNISED to 1),
            copied = 14, typed = 2,
            copiedWithDistance = 12, copiedWithCalories = 0, copiedWithHeartRate = 14, copiedWithTitle = 14,
        )

        assertThat(BandReportWording.workoutLines(workouts)).containsExactly(
            "16 workouts",
            "Walking 12 · Running 2 · Exercise 2",
            "copied 14 · typed 2",
            "distance 12 of 14 · calories 0 of 14 · heart rate 14 of 14 · title 14 of 14",
        ).inOrder()
    }

    @Test
    fun `typed workouts alone have no details line`() {
        val workouts = WorkoutArrivals(total = 1, byKind = listOf(WorkoutKind.SWIM to 1), copied = 0, typed = 1)

        assertThat(BandReportWording.workoutLines(workouts))
            .containsExactly("1 workout", "Swimming 1", "copied 0 · typed 1").inOrder()
    }

    @Test
    fun `no workouts is one line`() {
        assertThat(BandReportWording.workoutLines(WorkoutArrivals())).containsExactly("none in these 30 days")
    }

    @Test
    fun `each figure of the daily summary is a line of days`() {
        val report = aReport(daysWith = DayFigure.entries.associateWith { 0 } + (DayFigure.STEPS to 30))

        val lines = BandReportWording.dayLines(report)

        assertThat(lines).hasSize(10)
        assertThat(lines.first()).isEqualTo("steps 30 of 30 days")
        assertThat(lines).contains("movement calories 0 of 30 days")
        assertThat(lines.last()).isEqualTo("workouts 0 of 30 days")
    }

    @Test
    fun `the window is said from its first day to today`() {
        assertThat(BandReportWording.window(aReport(), today)).isEqualTo("The last 30 days, 5 Aug to today.")
    }

    @Test
    fun `the line about what Health Connect cannot carry names only what has no record type`() {
        assertThat(BandReportWording.NOT_SHARED)
            .startsWith("Stress, training load and recovery time are not shared through Health Connect")
        assertThat(BandReportWording.NOT_SHARED)
            .contains("VO₂ max has a Health Connect record, but this app does not copy it.")
    }

    @Test
    fun `the copied text holds counts, dates and names, in the page's order`() {
        val report = aReport(
            kinds = HealthKind.entries.map { kind ->
                if (kind == HealthKind.STEPS) {
                    KindArrivals(kind, 300, 30, listOf(BAND), from, today)
                } else {
                    KindArrivals(kind, 0, 0, emptyList(), null, null)
                }
            },
        )

        val text = BandReportWording.asText(report, setOf(HealthKind.WEIGHT), mapOf(BAND to "Example Band"), today)
        val lines = text.lines()

        assertThat(lines.first()).isEqualTo("What the band sends — the last 30 days, 5 Aug to today")
        assertThat(lines).contains("Steps: 300 readings · 30 days · from Example Band · first 5 Aug · last today")
        assertThat(lines).contains("Weight: not allowed")
        assertThat(lines).contains("Distance: nothing arrived")
        assertThat(lines).contains("Workouts: none in these 30 days")
        assertThat(lines.indexOf("Daily summary")).isGreaterThan(lines.indexOf("Workouts: none in these 30 days"))
        assertThat(lines.last()).isEqualTo(BandReportWording.NOT_SHARED)
    }

    private fun aReport(
        kinds: List<KindArrivals> = HealthKind.entries.map { KindArrivals(it, 0, 0, emptyList(), null, null) },
        daysWith: Map<DayFigure, Int> = DayFigure.entries.associateWith { 0 },
    ) = BandReport(from, today, kinds, WorkoutArrivals(), daysWith)

    private companion object {
        const val BAND = "com.example.band"
        const val PHONE = "com.example.phone"
    }
}
```

- [ ] **Step 2: Run it to see it fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.health.BandReportWordingTest" > /tmp/ms-band.log 2>&1; echo "exit $?"
```

Expected: `exit 1`; `Unresolved reference: BandReportWording`.

- [ ] **Step 3: Write the wording.** Create `app/src/main/java/com/metaself/app/ui/health/BandReportWording.kt`:

```kotlin
package com.metaself.app.ui.health

import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.DayFigure
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.health.KindArrivals
import com.metaself.app.domain.health.WorkoutArrivals
import com.metaself.app.ui.movement.MovementWeekWording
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every sentence of "What the band sends" (D80), and the text Copy as text puts on the clipboard:
 * counts, dates and app names only, never a reading's value.
 *
 * [labels] maps a package name to the phone's name for that app; a package missing from it is shown
 * as itself.
 */
object BandReportWording {

    /**
     * What Health Connect cannot carry. Checked against the pinned client (connect-client 1.1.0): it has
     * no record type for stress, training load or recovery time, and it does have `Vo2MaxRecord` —
     * which is not one of the thirteen kinds this app copies (D66).
     */
    const val NOT_SHARED = "Stress, training load and recovery time are not shared through Health Connect; " +
        "they stay in the band's app. VO₂ max has a Health Connect record, but this app does not copy it."

    /** [Locale.US]: Java 17's UK data writes "Sept" (see [MovementWeekWording]). */
    private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.US)

    private const val SEP = " · "

    /** "The last 30 days, 5 Aug to today." */
    fun window(report: BandReport, today: Long): String =
        "The last ${report.windowDays} days, ${day(report.fromDay, today)} to ${day(report.toDay, today)}."

    /** "1,200 readings · 30 days · from com.example.band"; "nothing arrived"; "not allowed". */
    fun kindLine(kind: KindArrivals, notAllowed: Set<HealthKind>, labels: Map<String, String>): String {
        if (kind.count == 0) return if (kind.kind in notAllowed) "not allowed" else "nothing arrived"
        return count(kind.count, unitOf(kind.kind)) + SEP + count(kind.days, "day", "days") + SEP +
            "from " + names(kind.origins.map { labels[it] ?: it })
    }

    /** "first 5 Aug · last today", or null when nothing arrived. */
    fun kindDates(kind: KindArrivals, today: Long): String? {
        val first = kind.firstDay ?: return null
        val last = kind.lastDay ?: return null
        return "first ${day(first, today)}" + SEP + "last ${day(last, today)}"
    }

    /** The workouts section, one line each: count, kinds, sources, the copied ones' details. */
    fun workoutLines(w: WorkoutArrivals): List<String> {
        if (w.total == 0) return listOf("none in these 30 days")
        val byName = linkedMapOf<String, Int>()
        w.byKind.forEach { (kind, n) ->
            val name = MovementWeekWording.kindName(kind)
            byName[name] = (byName[name] ?: 0) + n
        }
        return buildList {
            add(count(w.total, "workout", "workouts"))
            add(byName.entries.joinToString(SEP) { (name, n) -> "$name $n" })
            add("copied ${w.copied}" + SEP + "typed ${w.typed}")
            if (w.copied > 0) {
                add(
                    listOf(
                        "distance" to w.copiedWithDistance,
                        "calories" to w.copiedWithCalories,
                        "heart rate" to w.copiedWithHeartRate,
                        "title" to w.copiedWithTitle,
                    ).joinToString(SEP) { (detail, n) -> "$detail $n of ${w.copied}" },
                )
            }
        }
    }

    /** "steps 30 of 30 days", for each figure of the daily summary, in the spec's order. */
    fun dayLines(report: BandReport): List<String> = DayFigure.entries.map { figure ->
        "${figureName(figure)} ${report.daysWith[figure] ?: 0} of ${report.windowDays} days"
    }

    /** The whole page as plain text, for pasting into a conversation. */
    fun asText(report: BandReport, notAllowed: Set<HealthKind>, labels: Map<String, String>, today: Long): String =
        buildList {
            add("What the band sends — the last ${report.windowDays} days, " +
                "${day(report.fromDay, today)} to ${day(report.toDay, today)}")
            add("")
            add("Kinds of reading")
            report.kinds.forEach { kind ->
                val dates = kindDates(kind, today)?.let { SEP + it }.orEmpty()
                add("${kind.kind.displayName}: ${kindLine(kind, notAllowed, labels)}$dates")
            }
            add("")
            val workouts = workoutLines(report.workouts)
            add("Workouts: " + workouts.first())
            addAll(workouts.drop(1))
            add("")
            add("Daily summary")
            addAll(dayLines(report))
            add("")
            add(NOT_SHARED)
        }.joinToString("\n")

    private fun unitOf(kind: HealthKind): Pair<String, String> = when (kind) {
        HealthKind.SLEEP -> "night" to "nights"
        HealthKind.EXERCISE -> "workout" to "workouts"
        else -> "reading" to "readings"
    }

    private fun count(n: Int, unit: Pair<String, String>): String = count(n, unit.first, unit.second)

    private fun count(n: Int, one: String, many: String): String =
        String.format(Locale.US, "%,d", n) + " " + if (n == 1) one else many

    private fun names(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    private fun day(epochDay: Long, today: Long): String =
        if (epochDay == today) "today" else LocalDate.ofEpochDay(epochDay).format(DAY)

    private fun figureName(figure: DayFigure): String = when (figure) {
        DayFigure.STEPS -> "steps"
        DayFigure.DISTANCE -> "distance"
        DayFigure.ACTIVE_KCAL -> "movement calories"
        DayFigure.TOTAL_KCAL -> "total calories"
        DayFigure.RESTING_HEART_RATE -> "resting heart rate"
        DayFigure.HRV -> "heart-rate variability"
        DayFigure.OXYGEN -> "blood oxygen"
        DayFigure.BREATHING -> "breathing rate"
        DayFigure.SLEEP -> "sleep"
        DayFigure.WORKOUTS -> "workouts"
    }
}
```

- [ ] **Step 4: Run it to see it pass.** Same command as Step 2. Expected: `exit 0`.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/health/BandReportWording.kt \
        app/src/test/java/com/metaself/app/ui/health/BandReportWordingTest.kt
git commit -m "feat: what the band-sends page says, and the text it copies (D80)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Reading the record

**Files:**
- Modify: `app/src/main/java/com/metaself/app/data/health/HealthReadingDao.kt` (one query, one row type)
- Create: `app/src/main/java/com/metaself/app/data/health/BandRecord.kt`
- Modify: `app/src/main/java/com/metaself/app/di/DataModule.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/BandRecordTest.kt` (JUnit 5, mapping)
- Test: `app/src/test/java/com/metaself/app/data/health/HealthRecordDaoTest.kt` (Robolectric, CI only)

- [ ] **Step 1: Write the failing tests.** `BandRecordTest.kt`:

```kotlin
package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.Arrival
import com.metaself.app.domain.health.DayFigure
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test

/** Stored rows, as the band report reads them (D80). Every figure is invented. */
class BandRecordTest {

    @Test
    fun `a count of a kind this version knows becomes an arrival, and one it does not is left out`() {
        assertThat(ReadingCount("STEPS", BAND, TEST_EPOCH_DAY, 10).toArrival())
            .isEqualTo(Arrival(HealthKind.STEPS, BAND, TEST_EPOCH_DAY, 10))
        assertThat(ReadingCount("SKIN_TEMPERATURE", BAND, TEST_EPOCH_DAY, 10).toArrival()).isNull()
    }

    @Test
    fun `a night is one sleep arrival on its waking day`() {
        val night = SleepSessionEntity(epochDay = TEST_EPOCH_DAY, startMillis = 0, endMillis = 1, origin = BAND, recordId = "n1", title = null)

        assertThat(night.toArrival()).isEqualTo(Arrival(HealthKind.SLEEP, BAND, TEST_EPOCH_DAY, 1))
    }

    @Test
    fun `a copied workout says which details it carries, and only the band's calories count`() {
        val band = aWorkout(distanceM = 5_000, energyKcal = 300, energySource = "BAND", avgHeartRate = 120, title = "Walk").toArrived()
        val estimated = aWorkout(energyKcal = 300, energySource = "MET_ESTIMATE", title = " ").toArrived()

        assertThat(band.typed).isFalse()
        assertThat(band.origin).isEqualTo(BAND)
        assertThat(band.kind).isEqualTo(WorkoutKind.WALK)
        assertThat(listOf(band.hasDistance, band.hasCalories, band.hasHeartRate, band.hasTitle)).containsExactly(true, true, true, true)
        assertThat(listOf(estimated.hasDistance, estimated.hasCalories, estimated.hasHeartRate, estimated.hasTitle))
            .containsExactly(false, false, false, false)
    }

    @Test
    fun `a typed workout is typed`() {
        assertThat(aWorkout(source = "TYPED", origin = null).toArrived().typed).isTrue()
    }

    @Test
    fun `a stored day says which figures it has`() {
        val day = HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 0, steps = 9_000, hrvMs = 40.0, sleepMinutes = 420, workoutCount = 0)

        assertThat(day.toCoverage().figures)
            .containsExactly(DayFigure.STEPS, DayFigure.HRV, DayFigure.SLEEP, DayFigure.WORKOUTS)
        assertThat(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 0).toCoverage().figures).isEmpty()
    }

    private fun aWorkout(
        source: String = "SYNCED",
        origin: String? = BAND,
        distanceM: Int? = null,
        energyKcal: Int? = null,
        energySource: String = "NONE",
        avgHeartRate: Int? = null,
        title: String? = null,
    ) = WorkoutEntity(
        epochDay = TEST_EPOCH_DAY, startedAtMillis = 0, durationMinutes = 30, kind = "WALK", title = title,
        distanceM = distanceM, energyKcal = energyKcal, energySource = energySource, effort = null,
        source = source, origin = origin, originId = origin?.let { "s1" }, note = null, avgHeartRate = avgHeartRate,
    )

    private companion object {
        const val BAND = "com.example.band"
    }
}
```

And in `HealthRecordDaoTest.kt`, a new section before the helpers (the class's `beat`, `night`, `typed`,
`synced` helpers and `ORIGIN` are reused). Add imports `com.metaself.app.domain.health.BandReport` and
`com.metaself.app.domain.health.HealthKind`:

```kotlin
    // --- What the band sends (D80) -----------------------------------------------------------------

    @Test
    fun `readings are counted by kind, app and day, only for the kinds and days asked for`() = runTest {
        val dao = db.healthReadingDao()
        dao.insertAll(
            listOf(
                beat("r1", 0, 60.0),
                beat("r1", 1, 61.0),
                beat("r2", 0, 62.0, day = TEST_EPOCH_DAY - 1),
                beat("r3", 0, 63.0, day = TEST_EPOCH_DAY - 40),
            ),
        )

        assertThat(dao.countsByDay(listOf("HEART_RATE"), TEST_EPOCH_DAY - 29, TEST_EPOCH_DAY)).containsExactly(
            ReadingCount("HEART_RATE", ORIGIN, TEST_EPOCH_DAY, 2),
            ReadingCount("HEART_RATE", ORIGIN, TEST_EPOCH_DAY - 1, 1),
        )
        assertThat(dao.countsByDay(listOf("STEPS"), TEST_EPOCH_DAY - 29, TEST_EPOCH_DAY)).isEmpty()
    }

    @Test
    fun `the band report reads readings, nights, workouts and days from the record`() = runTest {
        db.healthReadingDao().insertAll(listOf(beat("r1", 0, 60.0), beat("r1", 1, 61.0)))
        db.sleepDao().insertSession(night("n1"))
        db.workoutDao().insert(synced("s1"))
        db.workoutDao().insert(typed())
        db.healthDayDao().put(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 0, steps = 9_000))

        val report = RoomBandRecord(db).report(BandReport.fromDayFor(TEST_EPOCH_DAY), TEST_EPOCH_DAY)

        assertThat(report.kinds.single { it.kind == HealthKind.HEART_RATE }.count).isEqualTo(2)
        assertThat(report.kinds.single { it.kind == HealthKind.SLEEP }.count).isEqualTo(1)
        assertThat(report.kinds.single { it.kind == HealthKind.EXERCISE }.origins).containsExactly(ORIGIN)
        assertThat(report.workouts.copied).isEqualTo(1)
        assertThat(report.workouts.typed).isEqualTo(1)
        assertThat(report.daysWith.getValue(com.metaself.app.domain.health.DayFigure.STEPS)).isEqualTo(1)
    }
```

- [ ] **Step 2: Run them to see them fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.health.BandRecordTest" > /tmp/ms-band.log 2>&1; echo "exit $?"
```

Expected: `exit 1`; `Unresolved reference: ReadingCount` / `toArrival`.

- [ ] **Step 3: Add the query.** In `HealthReadingDao.kt`, inside the interface after `ofKindBetween`:

```kotlin
    /**
     * How many rows each app wrote of each of [kinds] on each day of [fromDay]..[toDay] (D80). Counts
     * only, never a value. `kind IN` lets the `(kind, epochDay, startMillis)` index serve the range.
     */
    @Query(
        "SELECT kind, origin, epochDay, COUNT(*) AS count FROM health_readings " +
            "WHERE kind IN (:kinds) AND epochDay BETWEEN :fromDay AND :toDay GROUP BY kind, origin, epochDay",
    )
    suspend fun countsByDay(kinds: List<String>, fromDay: Long, toDay: Long): List<ReadingCount>
```

and after `RecordKey`:

```kotlin
/** One app's rows of one kind on one day, counted. */
data class ReadingCount(val kind: String, val origin: String, val epochDay: Long, val count: Int)
```

- [ ] **Step 4: Write the port.** Create `app/src/main/java/com/metaself/app/data/health/BandRecord.kt`:

```kotlin
package com.metaself.app.data.health

import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.domain.health.Arrival
import com.metaself.app.domain.health.ArrivedWorkout
import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.DayCoverage
import com.metaself.app.domain.health.DayFigure
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.movement.WorkoutKind
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** What "What the band sends" reads (D80): the stored record only, never Health Connect. */
fun interface BandRecord {
    suspend fun report(fromDay: Long, toDay: Long): BandReport
}

/**
 * Four reads: the readings counted by the one new query, and the window's nights, workouts and days
 * through queries that already exist. Not one transaction: a copy landing between two reads can make
 * one section a moment newer than another, which a page of counts can bear.
 */
class RoomBandRecord @Inject constructor(private val database: MetaSelfDatabase) : BandRecord {

    override suspend fun report(fromDay: Long, toDay: Long): BandReport {
        val readings = database.healthReadingDao().countsByDay(READING_KINDS, fromDay, toDay).mapNotNull { it.toArrival() }
        val nights = database.sleepDao().sessionsBetween(fromDay, toDay).map { it.toArrival() }
        val workouts = database.workoutDao().observeBetween(fromDay, toDay).first().map { it.toArrived() }
        val days = database.healthDayDao().observeBetween(fromDay, toDay).first().map { it.toCoverage() }
        return BandReport.of(fromDay, toDay, readings + nights, workouts, days)
    }

    private companion object {
        val READING_KINDS = HealthKind.entries.filter { it.isReading }.map { it.name }
    }
}

/** Null for a kind this version does not know. */
fun ReadingCount.toArrival(): Arrival? = HealthKind.parse(kind)?.let { Arrival(it, origin, epochDay, count) }

fun SleepSessionEntity.toArrival(): Arrival = Arrival(HealthKind.SLEEP, origin, epochDay, 1)

/** Anything but TYPED reads as copied, as `toWorkout` reads an unknown source. */
fun WorkoutEntity.toArrived(): ArrivedWorkout = ArrivedWorkout(
    epochDay = epochDay,
    kind = WorkoutKind.parse(kind),
    typed = source == "TYPED",
    origin = origin,
    hasDistance = distanceM != null,
    hasCalories = energyKcal != null && energySource == "BAND",
    hasHeartRate = avgHeartRate != null,
    hasTitle = !title.isNullOrBlank(),
)

fun HealthDayEntity.toCoverage(): DayCoverage = DayCoverage(
    epochDay,
    buildSet {
        if (steps != null) add(DayFigure.STEPS)
        if (distanceM != null) add(DayFigure.DISTANCE)
        if (activeKcal != null) add(DayFigure.ACTIVE_KCAL)
        if (totalKcal != null) add(DayFigure.TOTAL_KCAL)
        if (restingHeartRate != null) add(DayFigure.RESTING_HEART_RATE)
        if (hrvMs != null) add(DayFigure.HRV)
        if (oxygenPct != null) add(DayFigure.OXYGEN)
        if (respiratoryRate != null) add(DayFigure.BREATHING)
        if (sleepMinutes != null) add(DayFigure.SLEEP)
        if (workoutCount != null) add(DayFigure.WORKOUTS)
    },
)
```

- [ ] **Step 5: Bind it.** In `DataModule.kt`, imports beside the other `data.health` ones:

```kotlin
import com.metaself.app.data.health.BandRecord
import com.metaself.app.data.health.RoomBandRecord
```

and after `provideTypedWorkouts`:

```kotlin
    /** What "What the band sends" reads (D80): counts from the stored record. */
    @Provides
    @Singleton
    fun provideBandRecord(record: RoomBandRecord): BandRecord = record
```

- [ ] **Step 6: Run to see them pass.** Same command as Step 2, then the DAO class (it compiles and
  skips locally; it runs in CI), then compile the app for Hilt:

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.health.BandRecordTest" --tests "com.metaself.app.data.health.HealthRecordDaoTest" > /tmp/ms-band.log 2>&1; echo "exit $?"
free -m
~/bin/gradlew-safe :app:assembleDebug > /tmp/ms-band.log 2>&1; echo "exit $?"
```

Expected: `exit 0` both; `HealthRecordDaoTest` skipped.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/com/metaself/app/data/health/HealthReadingDao.kt \
        app/src/main/java/com/metaself/app/data/health/BandRecord.kt \
        app/src/main/java/com/metaself/app/di/DataModule.kt \
        app/src/test/java/com/metaself/app/data/health/BandRecordTest.kt \
        app/src/test/java/com/metaself/app/data/health/HealthRecordDaoTest.kt
git commit -m "feat: the band report reads counts from the stored health record (D80)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: App names

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/AppLabels.kt`
- Modify: `app/src/main/AndroidManifest.xml` (the `<queries>` block)
- Modify: `app/src/main/java/com/metaself/app/di/DataModule.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/AppLabelsTest.kt` (Robolectric, runs locally)

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.metaself.app.data.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * An app's name for "What the band sends" (D80): the phone's label, or the package name. JUnit 4
 * because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class AppLabelsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `an app the phone does not know is shown by its package name`() {
        assertThat(PackageManagerAppLabels(context).labelOf("com.example.band")).isEqualTo("com.example.band")
    }

    @Test
    fun `an app the phone knows is shown by its label`() {
        val label = PackageManagerAppLabels(context).labelOf(context.packageName)

        assertThat(label).isNotEmpty()
        assertThat(label).isNotEqualTo(context.packageName)
    }
}
```

- [ ] **Step 2: Run it to see it fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.health.AppLabelsTest" > /tmp/ms-band.log 2>&1; echo "exit $?"
```

Expected: `exit 1`; `Unresolved reference: PackageManagerAppLabels`.

- [ ] **Step 3: Write it.** `AppLabels.kt`:

```kotlin
package com.metaself.app.data.health

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The phone's name for an app, by its package name. */
fun interface AppLabels {
    fun labelOf(packageName: String): String
}

/**
 * [PackageManager.getApplicationLabel], or the package name itself when the phone does not know the
 * app, will not say, or gives a blank label. From Android 11 another app is only visible when the
 * manifest's `<queries>` covers it; the manifest declares the intents an app asking for Health Connect
 * permissions has to declare, so the apps that write to it should be visible. Never throws (D8).
 */
class PackageManagerAppLabels @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppLabels {

    override fun labelOf(packageName: String): String = try {
        val packages = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packages.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packages.getApplicationInfo(packageName, 0)
        }
        packages.getApplicationLabel(info).toString().takeIf { it.isNotBlank() } ?: packageName
    } catch (failure: Exception) {
        packageName
    }
}
```

In `AndroidManifest.xml`, the `<queries>` block becomes:

```xml
    <queries>
        <package android:name="com.google.android.apps.healthdata" />
        <!-- An app asking for Health Connect permissions has to declare one of these two. Seeing
             them lets "What the band sends" (D80) name a writing app; anything unseen is shown by
             its package name. -->
        <intent>
            <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
        </intent>
        <intent>
            <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
            <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
        </intent>
    </queries>
```

In `DataModule.kt`, imports `com.metaself.app.data.health.AppLabels` and
`com.metaself.app.data.health.PackageManagerAppLabels`, and after `provideBandRecord`:

```kotlin
    @Provides
    @Singleton
    fun provideAppLabels(labels: PackageManagerAppLabels): AppLabels = labels
```

- [ ] **Step 4: Run it to see it pass.** Same command as Step 2. Expected: `exit 0`.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/data/health/AppLabels.kt \
        app/src/main/AndroidManifest.xml \
        app/src/main/java/com/metaself/app/di/DataModule.kt \
        app/src/test/java/com/metaself/app/data/health/AppLabelsTest.kt
git commit -m "feat: a writing app is named by the phone's label, else its package (D80)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The view model

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/screen/settings/BandReportViewModel.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/settings/BandReportViewModelTest.kt`

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.AppLabels
import com.metaself.app.data.health.BandRecord
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.Arrival
import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** The band report's one read (D80). TEST_EPOCH_DAY is 3 Sep 2026. Every count is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class BandReportViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }
    private val problems = RecordingProblemLog()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `it reads the last 30 days once, and names each app`() = runTest {
        val asked = mutableListOf<Pair<Long, Long>>()
        val record = BandRecord { from, to ->
            asked += from to to
            BandReport.of(from, to, listOf(Arrival(HealthKind.STEPS, BAND, to, 10)), emptyList(), emptyList())
        }
        val labels = AppLabels { if (it == BAND) "Example Band" else it }

        val state = BandReportViewModel(record, labels, today, problems).state.first { it.report != null }

        assertThat(asked).containsExactly(TEST_EPOCH_DAY - 29 to TEST_EPOCH_DAY)
        assertThat(state.labels).containsExactly(BAND, "Example Band")
        assertThat(state.today).isEqualTo(TEST_EPOCH_DAY)
        assertThat(state.unreadable).isFalse()
    }

    @Test
    fun `a read that fails is logged and said, never thrown (D8)`() = runTest {
        val record = BandRecord { _, _ -> error("an invented failure") }

        val state = BandReportViewModel(record, AppLabels { it }, today, problems).state.first { it.unreadable }

        assertThat(state.report).isNull()
        assertThat(problems.recorded.single().kind).isEqualTo(BandReportViewModel.PROBLEM_KIND)
        assertThat(problems.recorded.single().detail).contains("an invented failure")
    }

    private companion object {
        const val BAND = "com.example.band"
    }
}
```

- [ ] **Step 2: Run it to see it fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.settings.BandReportViewModelTest" > /tmp/ms-band.log 2>&1; echo "exit $?"
```

Expected: `exit 1`; `Unresolved reference: BandReportViewModel`.

- [ ] **Step 3: Write it.** `BandReportViewModel.kt`:

```kotlin
package com.metaself.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.AppLabels
import com.metaself.app.data.health.BandRecord
import com.metaself.app.data.time.Today
import com.metaself.app.domain.health.BandReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What "What the band sends" shows (D80).
 *
 * @property report null while the read is under way, and when it failed.
 * @property labels each writing app's name, by package; a package missing from it is shown as itself.
 * @property unreadable the read failed; the page says so (D8).
 */
data class BandReportUiState(
    val report: BandReport? = null,
    val labels: Map<String, String> = emptyMap(),
    val today: Long = 0,
    val unreadable: Boolean = false,
)

/**
 * The page's own view model, on its own back-stack entry rather than the Settings graph's: the read is
 * this page's alone, happens once when the page opens, and should not run each time another Settings
 * page does. Which kinds are not allowed is not read here; the destination takes it from the Settings
 * view model, which already has it.
 */
@HiltViewModel
class BandReportViewModel @Inject constructor(
    private val record: BandRecord,
    private val labels: AppLabels,
    private val today: Today,
    private val problems: ProblemLog,
) : ViewModel() {

    private val _state = MutableStateFlow(BandReportUiState())
    val state: StateFlow<BandReportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { read() }
    }

    private suspend fun read() {
        val day = today().toEpochDay()
        _state.value = try {
            val report = record.report(BandReport.fromDayFor(day), day)
            BandReportUiState(report = report, labels = report.origins.associateWith(labels::labelOf), today = day)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            problems.record(PROBLEM_KIND, "not read: " + (failure.message ?: failure::class.java.simpleName))
            BandReportUiState(today = day, unreadable = true)
        }
    }

    companion object {
        const val PROBLEM_KIND = "band report"
    }
}
```

- [ ] **Step 4: Run it to see it pass.** Same command as Step 2. Expected: `exit 0`.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/screen/settings/BandReportViewModel.kt \
        app/src/test/java/com/metaself/app/ui/screen/settings/BandReportViewModelTest.kt
git commit -m "feat: the band report is read once, when its page opens (D80)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The page, the row, and the way there

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/screen/settings/BandReportPage.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/metaself/app/ui/screen/settings/MovementSettingsPage.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt` (`Destination.Settings.bandReport`, the graph call)
- Modify: `app/src/main/java/com/metaself/app/ui/nav/SettingsGraph.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/nav/SettingsDestination.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/settings/BandReportRenderTest.kt`
- Modify tests: `SettingsPagesRenderTest.kt`, `SettingsHistoryRenderTest.kt` (the new parameter),
  `app/src/test/java/com/metaself/app/ui/nav/SettingsGraphSessionTest.kt`,
  `app/src/test/java/com/metaself/app/ui/nav/MetaSelfNavHostRenderTest.kt`

- [ ] **Step 1: Write the failing tests.** `BandReportRenderTest.kt`:

```kotlin
package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.Arrival
import com.metaself.app.domain.health.ArrivedWorkout
import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.health.BandReportWording
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * "What the band sends" drawn (D80): what is on it and in which order, and that Copy as text calls
 * back. Nothing about size or wrapping (`CLAUDE.md`). Every count is invented; the app is invented.
 * JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class BandReportRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    private val today = TEST_EPOCH_DAY
    private val report = BandReport.of(
        BandReport.fromDayFor(today), today,
        arrivals = listOf(Arrival(HealthKind.STEPS, BAND, today, 300)),
        workouts = listOf(ArrivedWorkout(today, WorkoutKind.WALK, typed = false, origin = BAND, true, false, true, false)),
        days = emptyList(),
    )

    @Test
    fun `every kind is listed in order, each with what arrived`() {
        val texts = page(BandReportUiState(report = report, labels = mapOf(BAND to "Example Band"), today = today), setOf(HealthKind.WEIGHT))

        assertThat(texts).contains("What the band sends")
        val names = HealthKind.entries.map { it.displayName }
        assertThat(texts).containsAtLeastElementsIn(names).inOrder()
        assertThat(texts).contains("300 readings · 1 day · from Example Band")
        assertThat(texts).contains("first today · last today")
        assertThat(texts).contains("nothing arrived")
        assertThat(texts).contains("not allowed")
    }

    @Test
    fun `workouts, the daily summary, the line on what cannot arrive, and Copy follow the kinds`() {
        val texts = page(BandReportUiState(report = report, today = today))

        assertThat(texts).containsAtLeast(
            "Body fat", "1 workout", "Walking 1", "copied 1 · typed 0",
            "steps 0 of 30 days", "workouts 0 of 30 days", BandReportWording.NOT_SHARED, "Copy as text",
        ).inOrder()
    }

    @Test
    fun `Copy as text calls back`() {
        var copied = 0
        render.texts(heightPx = TALL) {
            BandReportPage(state = BandReportUiState(report = report, today = today), notAllowed = emptySet(), onCopy = { copied++ }, onBack = {})
        }

        render.click("Copy as text")

        assertThat(copied).isEqualTo(1)
    }

    @Test
    fun `a read that failed is said, and nothing is offered to copy`() {
        val texts = page(BandReportUiState(unreadable = true, today = today))

        assertThat(texts).contains("The health record could not be read; Recent problems says why.")
        assertThat(texts).doesNotContain("Copy as text")
    }

    @Test
    fun `while reading, only the title is there`() {
        val texts = page(BandReportUiState())

        assertThat(texts).contains("What the band sends")
        assertThat(texts).doesNotContain("Copy as text")
        assertThat(texts).doesNotContain("Steps")
    }

    private fun page(state: BandReportUiState, notAllowed: Set<HealthKind> = emptySet()): List<String> =
        render.texts(heightPx = TALL) {
            BandReportPage(state = state, notAllowed = notAllowed, onCopy = {}, onBack = {})
        }

    private companion object {
        const val TALL = 20_000
        const val BAND = "com.example.band"
    }
}
```

In `SettingsPagesRenderTest.kt`, under `// --- Movement and health ---`, add:

```kotlin
    /** D80: the way to what the band sends, from the page about what can be read. */
    @Test
    fun `the Movement page has a row for what the band sends, and it opens the page`() {
        var opened = 0
        val texts = render.texts {
            MovementSettingsPage(state = SettingsUiState(), onConnectSteps = {}, onOpenBandReport = { opened++ }, onBack = {})
        }

        assertThat(texts).contains("What the band sends")
        render.click("What the band sends")
        assertThat(opened).isEqualTo(1)
    }
```

and its `movement(...)` helper passes `onOpenBandReport = {}`. `SettingsHistoryRenderTest.kt`'s call
to `MovementSettingsPage` gains `onOpenBandReport = {},` too.

In `MetaSelfNavHostRenderTest.kt`, add:

```kotlin
    /** D80: under the Settings graph, beside the Movement page it is reached from. */
    @Test
    fun `the band report has its own route under Settings`() {
        assertThat(Destination.Settings.bandReport).isEqualTo("settings/movement/band")
    }
```

In `SettingsGraphSessionTest.kt`, the `settingsGraph(nav) { … }` call gains the second lambda and the
Movement stand-in gains a button; add the test:

```kotlin
    /** D80: the Movement page's row opens the band report; back returns to the Movement page. */
    @Test
    fun `the band report opens from the Movement page and shares the visit's view model`() {
        draw()
        press("Open settings")
        press("Open MOVEMENT")

        press("Open the band report")
        assertThat(routes().last()).isEqualTo("settings/movement/band")
        compose.onNodeWithText("BAND REPORT").assertExists()
        assertThat(probes.toSet()).hasSize(1)

        press("Back")
        assertThat(routes().last()).isEqualTo("settings/movement")
    }
```

with the graph in `Graph()` becoming:

```kotlin
            settingsGraph(
                nav,
                content = { page, here, graph ->
                    probes += viewModel<Probe>(viewModelStoreOwner = graph)
                    Column {
                        Text(if (page == null) "INDEX" else "PAGE ${page.name}")
                        if (page == null) {
                            SettingsPage.entries.forEach { row ->
                                TextButton(onClick = { nav.openSettingsPage(here, row) }) {
                                    Text("Open ${row.name}")
                                }
                            }
                        }
                        if (page == SettingsPage.MOVEMENT) {
                            TextButton(onClick = { nav.openBandReport(here) }) { Text("Open the band report") }
                        }
                        TextButton(onClick = { nav.popFrom(here) }) { Text("Back") }
                    }
                },
                bandReport = { here, graph ->
                    probes += viewModel<Probe>(viewModelStoreOwner = graph)
                    Column {
                        Text("BAND REPORT")
                        TextButton(onClick = { nav.popFrom(here) }) { Text("Back") }
                    }
                },
            )
```

- [ ] **Step 2: Run them to see them fail.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.settings.*" --tests "com.metaself.app.ui.nav.*" > /tmp/ms-band.log 2>&1; echo "exit $?"
```

Expected: `exit 1`; `Unresolved reference: BandReportPage` / `onOpenBandReport` / `bandReport`.

- [ ] **Step 3: Strings.** In `strings.xml`, after `settings_movement_readings_pointer`:

```xml
    <string name="settings_band_title">What the band sends</string>
    <string name="settings_band_row">What has arrived in the last 30 days, kind by kind</string>
    <string name="settings_band_kinds">Kinds of reading</string>
    <string name="settings_band_workouts">Workouts</string>
    <string name="settings_band_days">Daily summary</string>
    <string name="settings_band_copy">Copy as text</string>
    <string name="settings_band_unreadable">The health record could not be read; Recent problems says why.</string>
```

- [ ] **Step 4: The page.** `BandReportPage.kt`:

```kotlin
package com.metaself.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.health.BandReportWording
import com.metaself.app.ui.theme.MetaSelfInk
import com.metaself.app.ui.theme.Spacing

/**
 * "What the band sends" (D80): what has arrived in the stored health record over the last 30 days,
 * kind by kind, then the workouts, then the daily summary's coverage, then what Health Connect cannot
 * carry, then Copy as text. Reads nothing and writes nothing itself; [notAllowed] comes from Settings.
 */
@Composable
fun BandReportPage(
    state: BandReportUiState,
    notAllowed: Set<HealthKind>,
    onCopy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.settings_band_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        val report = state.report
        when {
            state.unreadable -> Text(
                text = stringResource(R.string.settings_band_unreadable),
                style = MaterialTheme.typography.bodyMedium,
            )

            report != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Related)) {
                Text(
                    text = BandReportWording.window(report, state.today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MetaSelfInk.two,
                )

                Heading(stringResource(R.string.settings_band_kinds))
                report.kinds.forEach { kind ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tight)) {
                        Text(kind.kind.displayName, style = MaterialTheme.typography.bodyMedium)
                        Small(BandReportWording.kindLine(kind, notAllowed, state.labels))
                        BandReportWording.kindDates(kind, state.today)?.let { Small(it) }
                    }
                }

                Heading(stringResource(R.string.settings_band_workouts))
                BandReportWording.workoutLines(report.workouts).forEach { Small(it) }

                Heading(stringResource(R.string.settings_band_days))
                BandReportWording.dayLines(report).forEach { Small(it) }

                Text(BandReportWording.NOT_SHARED, style = MaterialTheme.typography.bodySmall)

                TextButton(onClick = onCopy) { Text(stringResource(R.string.settings_band_copy)) }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Small(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MetaSelfInk.two)
}
```

- [ ] **Step 5: The row.** In `MovementSettingsPage.kt`, add the parameter `onOpenBandReport: () -> Unit,`
  after `onConnectSteps`, and after the steps `Column`'s closing brace (still inside `MetaSelfScreen`'s
  content, which is already a `Column` spaced by `Spacing.Section`, so no wrapper is needed):

```kotlin
        // --- what has arrived (D80) ---

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenBandReport)
                .padding(vertical = Spacing.Related),
            verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
        ) {
            Text(stringResource(R.string.settings_band_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_band_row),
                style = MaterialTheme.typography.bodyMedium,
                color = MetaSelfInk.two,
            )
        }
```

(imports: `clickable`, `fillMaxWidth`, `padding`, `MetaSelfInk`.) A clickable
`Column` merges its texts into one node, so `render.click("What the band sends")` finds it.

- [ ] **Step 6: The route.** In `MetaSelfNavHost.kt`, inside `Destination.Settings`:

```kotlin
        /** "What the band sends" (D80), reached from the Movement and health page. */
        const val bandReport: String = "settings/movement/band"
```

In `SettingsGraph.kt`, `settingsGraph` gains a second content parameter and registers the route, and a
guarded opener is added:

```kotlin
fun NavGraphBuilder.settingsGraph(
    nav: NavController,
    content: @Composable (page: SettingsPage?, here: NavBackStackEntry, graph: NavBackStackEntry) -> Unit,
    bandReport: @Composable (here: NavBackStackEntry, graph: NavBackStackEntry) -> Unit,
) {
    navigation(startDestination = Destination.Settings.index, route = Destination.Settings.route) {
        composable(Destination.Settings.index) { here ->
            content(null, here, graphOf(nav, here))
        }
        SettingsPage.entries.forEach { page ->
            composable(Destination.Settings.page(page)) { here ->
                content(page, here, graphOf(nav, here))
            }
        }
        composable(Destination.Settings.bandReport) { here ->
            bandReport(here, graphOf(nav, here))
        }
    }
}

/** Opens "What the band sends" from the Movement page, once however quickly it is tapped. */
fun NavController.openBandReport(here: NavBackStackEntry) {
    here.ifResumed { navigate(Destination.Settings.bandReport) }
}
```

In `MetaSelfNavHost.kt`, the call becomes:

```kotlin
        settingsGraph(
            navController,
            content = { page, here, graph ->
                SettingsDestination(page = page, here = here, graph = graph, navController = navController)
            },
            bandReport = { here, graph ->
                BandReportDestination(here = here, graph = graph, navController = navController)
            },
        )
```

In `SettingsDestination.kt`, the Movement page's call passes
`onOpenBandReport = { navController.openBandReport(here) },` and a new composable is added at the end of
the file:

```kotlin
/**
 * "What the band sends" (D80): its own view model on this entry, and which kinds are not allowed from
 * the visit's Settings view model on [graph] — refreshed when the Movement page opened. The clipboard
 * is here, like Recent problems' Copy them, so the page stays drawable without an activity.
 */
@Composable
internal fun BandReportDestination(
    here: NavBackStackEntry,
    graph: NavBackStackEntry,
    navController: NavController,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel(graph)
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val bandViewModel: BandReportViewModel = hiltViewModel(here)
    val state by bandViewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val notAllowed = settingsState.healthRecord.notAllowed

    BandReportPage(
        state = state,
        notAllowed = notAllowed,
        onCopy = {
            state.report?.let { report ->
                clipboard.setText(AnnotatedString(BandReportWording.asText(report, notAllowed, state.labels, state.today)))
            }
        },
        onBack = { navController.popFrom(here) },
    )
}
```

(imports `BandReportPage`, `BandReportViewModel`, `com.metaself.app.ui.health.BandReportWording`.)

- [ ] **Step 7: Run them to see them pass.** Same command as Step 2. Expected: `exit 0`, no `FAILED`.

- [ ] **Step 8: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/screen/settings/BandReportPage.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/java/com/metaself/app/ui/screen/settings/MovementSettingsPage.kt \
        app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt \
        app/src/main/java/com/metaself/app/ui/nav/SettingsGraph.kt \
        app/src/main/java/com/metaself/app/ui/nav/SettingsDestination.kt \
        app/src/test/java/com/metaself/app/ui/screen/settings/BandReportRenderTest.kt \
        app/src/test/java/com/metaself/app/ui/screen/settings/SettingsPagesRenderTest.kt \
        app/src/test/java/com/metaself/app/ui/screen/settings/SettingsHistoryRenderTest.kt \
        app/src/test/java/com/metaself/app/ui/nav/SettingsGraphSessionTest.kt \
        app/src/test/java/com/metaself/app/ui/nav/MetaSelfNavHostRenderTest.kt
git commit -m "feat: Settings → Movement and health → What the band sends (D80)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The whole suite, lint, build

- [ ] **Step 1: The whole suite.**

```
free -m
~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-band.log 2>&1; echo "exit $?"
```

Expected `exit 0`. Count from the XML, not the log:

```
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t=f=e=s=0; skipped=set()
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    r=ET.parse(p).getroot(); t+=int(r.get('tests')); f+=int(r.get('failures')); e+=int(r.get('errors'))
    k=int(r.get('skipped')); s+=k
    if k: skipped.add(r.get('name').split('.')[-1])
print(t,f,e,s); print(sorted(skipped))
EOF
```

Expected: 0 failures, 0 errors; skipped classes exactly the ten.

- [ ] **Step 2: Lint and build.**

```
free -m
~/bin/gradlew-safe :app:lintDebug > /tmp/ms-band.log 2>&1; echo "exit $?"
free -m
~/bin/gradlew-safe :app:assembleDebug > /tmp/ms-band.log 2>&1; echo "exit $?"
```

Expected: `exit 0` both. No version bump, no push, no release.

## Self-review (2026-09-27)

- Spec item 1 → Tasks 1–3 (counts, days, apps, first/last), Task 4 (labels), Task 6 (not allowed from
  Settings). Item 2 → Task 1 `WorkoutArrivals`, Task 2 `workoutLines`. Item 3 → `DayFigure`,
  `dayLines`; the ten figures are exactly the spec's (the stored `avgHeartRate` is not one of them).
  Item 4 → `NOT_SHARED`, adjusted after the client check above. Item 5 → `asText` and the destination's
  clipboard. "Nothing writes; nothing reads Health Connect; D8" → red lines, Task 5's failure test.
- Names used across tasks: `BandReport.of/fromDayFor/origins/windowDays`, `KindArrivals`,
  `WorkoutArrivals.copiedWith…`, `DayFigure`, `DayCoverage`, `Arrival`, `ArrivedWorkout`,
  `ReadingCount`, `toArrival/toArrived/toCoverage`, `BandRecord.report`, `AppLabels.labelOf`,
  `BandReportUiState`, `BandReportPage(state, notAllowed, onCopy, onBack)`,
  `Destination.Settings.bandReport`, `openBandReport` — checked consistent.
- What a render test here cannot prove: that the page fits or wraps well at phone width, that the row
  is a comfortable touch target, and that the clipboard really receives the text (the destination is
  not rendered; `asText` is tested pure).
