# The weekly letter — Implementation Plan (D99–D104)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D99–D104. Every Sunday at a chosen hour (default 20:00, switchable off) the phone copies the
band's data (when Health Connect's background-read permission is held), counts the week — food as
weekly averages of daily totals, the weight trend, movement, the weekly plan — against the four weeks
before it, and asks the model for a short letter (headline, what you put in, where it's taking you, one
thing to look at, for next week, a closing line). The letter is stored, announced by a notification of
its own channel, shown on the day as a margin note until read, and kept under Trainer → Weekly letters.
A quiet week (no meal, no weigh-in, no counted session) gets nothing. Room version 12 adds
`weekly_letters`; the backup becomes format 10. Version 0.70.0.

**Decision:** the owner's, 2026-09-29 — `docs/superpowers/specs/2026-09-29-the-weekly-letter-design.md`
(D99 amends D84; D101 amends D16 and D84; D103 amends D15; D104 amends the backup).

**Sequencing — read first.** A fix to the weekly plan's ticking (**D105**: "a session counts only after the
plan is kept; a short session is a candidate the owner confirms") ships FIRST as **0.69.0**, with **Room 11
and backup format 9** (a table for confirmations). This plan is written against the repository as it
stands at `bfb4642` (Room 10, backup 8, app 0.68.0) but **numbers everything after D105**: Room **12**
(migration **11→12**, `12.json`, `tools/check-migration-11-12.py`), backup format **10**, app **0.70.0 /
versionCode 126**. **Before Task 1, rebase this branch onto a `main` that contains 0.69.0; if D105 has not
merged, stop and report.** Places that must be re-checked against D105 are marked **[D105]** — chiefly:
`PlanProgress` gains candidate/attempt states, and the letter's plan row counts **only ticked (confirmed
or full) sessions**; the migration test builds on version 11; the backup's version history and its tests
name format 9 as D105's.

**Architecture:**

```
gradle/libs.versions.toml, app/build.gradle.kts   + work-runtime-ktx, hilt-work, androidx.hilt compiler    Task 1 (spike)
MetaSelfApp.kt, AndroidManifest.xml     Configuration.Provider + HiltWorkerFactory; no default initializer
domain/letter/WeekFigures.kt            pure: one week's figures, the five weeks, the 4-week average,
                                        the quiet-week rule                                               JUnit 5
data/day/MealDao.kt + DayEntities.kt    totalsByDayBetween (SQL sums per day)                            MealDaoTest (CI)
data/letter/FoodTotals.kt               port + Room impl over the query                                  JUnit 5 (fake) / CI
domain/letter/WeeklyLetter.kt           the vocabulary: LetterTexts, WeeklyLetter, LetterReply          compiles only
data/ai/LetterPrompt.kt                 pure: the request body, system prompt (D102 tone), schema       JUnit 5 (pinned)
data/ai/LetterResponse.kt               pure: reply → LetterTexts; encode/read for storage              JUnit 5
data/ai/OpenAiLetterWriter.kt           the thin caller over OpenAiCall                                  JUnit 5 + MockWebServer
data/trainer/TrainerEntities.kt, TrainerDao.kt   + weekly_letters                                        CI (Room)
data/day/LetterMigration.kt             MIGRATION_11_12, verbatim from 12.json                           tools/check-migration-11-12.py + MigrationTest (CI)
data/letter/LetterStore.kt              port + RoomLetterStore + mapping                                 JUnit 5 (proxy) + HealthRecordStoreTest (CI)
data/letter/LetterSettingsStore.kt      switch + hour, DataStore                                         JUnit 5
domain/backup/Backup.kt + data/backup/BackupRepository.kt + ui/settings/BackupWording.kt   format 10   JUnit 5 + BackupRoundTripTest (CI)
data/health/HealthConnectReader.kt, HealthPorts.kt, HealthRecordSync.kt   background read: offered,
                                        granted, one pass without the foreground gate                    JUnit 5
domain/letter/LetterSchedule.kt         pure: the next Sunday at the hour; the Monday-noon deadline     JUnit 5
data/letter/WriteWeeklyLetter.kt        the job's logic: copy, count, ask, store, say what happened      JUnit 5 (fakes)
data/letter/WeeklyLetterWorker.kt + WeeklyLetterScheduler.kt   WorkManager glue                          compiles + assembleDebug
data/letter/LetterNotifications.kt + OpenedLetters.kt + MainActivity.kt   channel, two notifications, the tap
ui/letter/LetterWording.kt              every sentence                                                   JUnit 5
ui/screen/letter/*                      letter page, Weekly letters list, VMs; the day's note; settings  JUnit 5 VM + Robolectric render
ui/screen/day/DayScreen.kt, DayPager.kt, ui/nav/*, ui/screen/settings/AiSettingsPage.kt, ui/screen/trainer/TrainerScreen.kt
privacy.html, app/build.gradle.kts      the letter named; 0.70.0
```

**Tech Stack:** Kotlin 1.9.22, Room (schema v12, hand-written migration), WorkManager (new) with Hilt's
worker factory (new), kotlinx.serialization, OkHttp via the shared `OpenAiCall`, Compose Material 3, Hilt,
DataStore, JUnit 5 + Truth, `okhttp3.mockwebserver`, Robolectric (JUnit 4) for render tests.

**Red lines (stop and report if crossed):**

- **Food leaves the phone only as weekly averages of daily totals and a count of days logged.** Never a
  meal, a food's name, an amount, a portion or a time of eating; never a single weigh-in, sleep, raw
  readings, the owner's words on a session, names, device or app names. `LetterPromptTest` pins the
  shape and fails on a new field.
- **Automatic only as D99 says:** once a week, while the switch is on, for a week that has no letter and
  is not quiet. "Write it now" is a tap. Nothing is sent from `init` or a flow.
- **An AI answer is never shown as a measurement (D4):** the letter page opens with "From the AI trainer ·
  advice, not a measurement. The figures were counted on your phone." Every figure in the box is counted
  on the phone.
- **The migration is the exported schema's own SQL**, character for character, checked by
  `tools/check-migration-11-12.py` here and `MigrationTest` in CI. `app/schemas` gains `12.json` only.
- **CI-only database tests go inside `MealDaoTest`, `HealthRecordStoreTest`, `MigrationTest` or
  `BackupRoundTripTest`**, so the local skip list stays at exactly the ten classes `CLAUDE.md` names. No
  WorkManager test that needs a database.
- **D8:** the job never throws upwards; a failure is a problem-log line by its kind — **never the owner's
  note, figures or the model's answer**. A screen's failed write is said (`ActionRefused`).
- **No early return out of an inline composable**; `InlineComposableReturnGuardTest` fails the build.
- **Anonymisation:** every figure, date and word in a test or comment is invented and round, built on
  `TEST_EPOCH_DAY` (Thursday 3 September 2026) and `aProfile()`. Nothing copied from the example letters
  (`build/letter-canvas`, private) but their headings. No real week, weight, rate or frequency.
- **Never `git add -A`; never stage `app/.settings/*`, `build/` or `tools/__pycache__`; never bare
  `./gradlew`; never pipe a build whose result is reported.** No push. The release APK is the
  controller's (`~/bin/ms-release`).

## Design questions the spec does not answer — settled here

1. **The target in the box is today's target** (`CurrentTarget.of(profile, revision, year,
   burnAdjustment)`), the same number in both columns. D100 said "the target that applied", but no history
   of targets is stored (a revision replaces the last). Task 0 amends D100's line.
2. **The day-screen notice is a margin note, not a card** — D49 made every notice on the day a margin
   note; a card would be the one exception. Text "Your week is in: <headline>", action **Read it**. Task 0
   amends D103's line. (The owner approved a card in the mock-up; the controller should mention this.)
3. **WorkManager:** `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work`, and
   `androidx.hilt:hilt-compiler` over KSP. **Task 1 is a compatibility spike** — versions that work with
   Kotlin 1.9.22 / AGP 8.10.1 / compileSdk 36 / minSdk 26 / Hilt 2.50. If none does, Task 1 stops and
   reports; the owner is asked.
4. **Scheduling:** one unique `OneTimeWorkRequest` per Sunday, named by that Sunday's week parity
   (`weekly-letter-even` / `-odd`), enqueued for the next Sunday at
   the chosen hour (`ExistingWorkPolicy.REPLACE`), network-constrained, exponential backoff from 15
   minutes. The worker enqueues the next Sunday itself when it finishes (success or give-up). Re-scheduled
   when the setting changes and on every app start (idempotent). No boot receiver: WorkManager persists.
   Switch off → the unique work is cancelled.
5. **Retry deadline:** the Monday after the letter's Sunday, 12:00 local. A retryable failure (network,
   provider error, unreadable answer) before it → `Result.retry()`; at or after it → the failure
   notification, and stop. NoKey / Refused / CeilingReached → the failure notification at once.
6. **Which week:** the Monday–Sunday week holding the moment the job runs, if that moment is a Sunday at
   or after the hour; otherwise the week that ended on the most recent Sunday (a retry after midnight
   still writes Sunday's week). The job never writes a week whose Sunday is more than one day past.
7. **Once per week:** `weekMonday` is unique in the table; the job checks before copying or asking, and
   the store inserts with `ABORT`, so two runs can never both store one.
8. **Background copy:** `HealthRecordSync.copyInBackground()` runs one pass without the foreground gate,
   only when the caller has checked `BackgroundHealthRead.granted()`. Otherwise no copy, and the letter
   records `bandDataUntil` = the record's last copy time (`HealthRecordStatus.current().lastCopiedMillis`).
   With the permission and a successful pass, `bandDataUntil` is null (nothing to say).
9. **The permission is asked from the Weekly letter setting**, once, by a button "Allow Sunday's band data"
   shown only when the phone offers the feature and it is not granted, with the reason line "So Sunday's
   letter includes sessions from today." Declining is fine; the button stays for later.
10. **Food figures:** a day counts when it holds at least one item; averages are over those days, rounded
    to whole numbers; days logged is their count out of 7.
11. **Weight:** `MonthlyLines.weightChange(monday - 1, sunday, trend)` (made `internal`): the trend's change
    across the week, null unless both ends rest on a weigh-in at most 14 days old.
12. **Movement:** sessions visible and counted (a combined session once — the record's sessions as read);
    minutes and distance summed over them; felt counts from their reviews; active kcal and steps a day are
    averages over the week's days that have them (`HealthDay.activeKcal`, `HealthDay.steps`).
13. **The weekly plan row:** the kept plan whose counted span (`startEpochDay`..`Programmes.countedUntil`)
    overlaps the week, the latest-started if several; its week's planned and done counts from
    `PlanProgress`. **[D105]** "done" must count only ticked (confirmed or full) sessions, never candidates.
14. **The 4-week average** of each figure is the mean over those of the four earlier weeks that have it,
    rounded as the figure is; "—" when none has. Days logged averages whole weeks (0 counts).
15. **Quiet week:** no day with an item, no weigh-in on any day of the week, and no counted session → no
    request, no store, no notification; the job just schedules next Sunday.
16. **The notification tap** carries an extra (`letter_week` = weekMonday, or `letters` = true for the
    failure one); `MainActivity.onCreate` puts it in `OpenedLetters` (a pending-open holder like
    `SharedWorkoutFiles`); the nav host navigates. The letter page marks it read when opened.
17. **Channel:** id `weekly_letter`, name "Weekly letter", importance default, created lazily. Notification
    ids 2 (arrived) and 3 (couldn't be written). D15's channel `daily_reminder` is untouched.
18. **Settings:** switch on by default, hour 20, allowed 18..23; stored in the preferences DataStore beside
    the AI settings; carried in the backup's `ai` block as two optional fields (`weekly_letter`,
    `weekly_letter_hour`), restored as the model and ceiling are; a file without them leaves the phone's.
19. **Write it now** on Weekly letters, shown when the last complete week (or this week on a Sunday after
    the hour) has no letter and is not quiet; it runs `WriteWeeklyLetter` in `@ApplicationScope` via
    `outlived`, without the background copy (the app is in front; the ordinary copy runs anyway).
20. **Model:** the owner's configured model, as every request (D57's learned parameters apply).

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` and `~/bin/ms-release` share one lock.
**Before every Gradle command run `free -m`;** under about 4000 MB available (6000 MB before
`ms-release`), wait and check again. Never `gradlew --stop`. One build at a time.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-letter.log 2>&1; echo "exit $?"
```

Read results from the log and `app/build/test-results/testDebugUnitTest/*.xml`, never from a pipe. SQLite
classes skip locally (the ten in `CLAUDE.md`); report them as skipped, not passed.

Paths below are under `app/src/main/java/com/metaself/app/` (main) or `app/src/test/java/com/metaself/app/`
(test) unless written in full.

---

### Task 0: Two spec lines, and the rebase

**Files:** Modify `docs/superpowers/specs/2026-09-29-the-weekly-letter-design.md`

- [ ] **Step 1: Rebase.** `git fetch origin && git rebase origin/main`. Confirm `app/build.gradle.kts` says
  `versionName = "0.69.0"` and `MetaSelfDatabase` says `version = 11`. If not, **stop and report**.
- [ ] **Step 2: D100's target.** Replace "the daily calorie target that applied" with "the daily calorie
  target in force now (no history of targets is kept; the same figure stands in both columns)".
- [ ] **Step 3: D103's day notice.** Replace "shows a card \"YOUR WEEK IS IN · <dates>\", the headline and
  **Read it**, above today's meals," with "shows a margin note, as every notice on the day is (D49): \"Your
  week is in: <headline>\" and **Read it**, with the day's other notices,".
- [ ] **Step 4: Numbers.** In D104 replace "Room version 11" with "Room version 12" and "format becomes
  **9**; formats 1–8" with "format becomes **10**; formats 1–9".
- [ ] **Step 5: Commit.**

```bash
git add docs/superpowers/specs/2026-09-29-the-weekly-letter-design.md
git commit -m "docs(spec): the letter's target is today's, its day notice a margin note, Room 12 / format 10 (D100, D103, D104)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 1: WorkManager and Hilt's worker factory — a compatibility spike

**Files:** Modify `gradle/libs.versions.toml`, `app/build.gradle.kts`, `MetaSelfApp.kt`,
`app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Choose versions.** Candidates, in order: `work = "2.9.1"`, `hilt-work = "1.1.0"` (same line
  as `hilt-navigation-compose`). Check each POM's Kotlin stdlib requirement
  (`curl -s https://dl.google.com/android/maven2/androidx/work/work-runtime-ktx/2.9.1/work-runtime-ktx-2.9.1.pom`
  and the same for `androidx/hilt/hilt-work/1.1.0`, `androidx/hilt/hilt-compiler/1.1.0`): the stdlib must be
  ≤ 2.0.21 (what Health Connect already brings) and the metadata readable by Kotlin 1.9.22 (≤ 2.0). If
  2.9.1 fails, try 2.8.1. Record the chosen versions and the reason in the commit message.
- [ ] **Step 2: Catalog.** Under `[versions]` add `work = "2.9.1"` and `hilt-work = "1.1.0"`; under
  `[libraries]`:

```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hilt-work" }
androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hilt-work" }
```

- [ ] **Step 3: Dependencies** (`app/build.gradle.kts`, after `implementation(libs.hilt.navigation.compose)`):

```kotlin
    // D99: the weekly letter is written in the background; Android gives an alarm seconds, and the
    // letter needs a copy and an AI answer. WorkManager waits for a network, retries and survives a
    // restart; Hilt's factory lets the worker be injected.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
```

- [ ] **Step 4: The application provides the configuration** (`MetaSelfApp.kt`):

```kotlin
@HiltAndroidApp
class MetaSelfApp : Application(), Configuration.Provider {

    @Inject
    lateinit var problems: ProblemLog

    /** Hilt's factory, so a worker is built with its dependencies (D99). */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
    // … onCreate and the rest unchanged
```

Imports: `androidx.hilt.work.HiltWorkerFactory`, `androidx.work.Configuration`.

- [ ] **Step 5: No default initializer** (`AndroidManifest.xml`, inside `<application>`; add
  `xmlns:tools="http://schemas.android.com/tools"` to `<manifest>` if absent):

```xml
        <!--
          D99: WorkManager is configured by MetaSelfApp (Hilt's worker factory), so its own start-up
          initializer is removed. Without this the default one runs first and the factory is ignored.
        -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

- [ ] **Step 6: Build and the whole suite.**

```
free -m
~/bin/gradlew-safe :app:assembleDebug > /tmp/ms-letter-1a.log 2>&1; echo "exit $?"
free -m
~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-letter-1b.log 2>&1; echo "exit $?"
```

Expected: both exit 0; skipped = the ten SQLite classes only. A Kotlin metadata error ("was compiled with
an incompatible version of Kotlin") or a KSP failure in `hilt-compiler` → try the fallback versions; if
none works, **revert Steps 2–5 and stop: report which versions were tried and the exact errors.**

- [ ] **Step 7: Commit.**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/metaself/app/MetaSelfApp.kt app/src/main/AndroidManifest.xml
git commit -m "build: WorkManager and Hilt's worker factory, for the weekly letter (D99)

work-runtime-ktx <v>, hilt-work <v>: <why these versions>.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: One week's figures, five weeks, the average — pure

**Files:**
- Create: `domain/letter/WeekFigures.kt`
- Modify: `domain/trainer/MonthlyLines.kt` (`weightChange` `private` → `internal`)
- Test: `domain/letter/WeekFiguresTest.kt`

- [ ] **Step 1: The failing test.**

```kotlin
package com.metaself.app.domain.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend
import org.junit.jupiter.api.Test

/** D100. Every figure is invented and round; the week is the one holding [TEST_EPOCH_DAY]. */
class WeekFiguresTest {

    private val monday = MovementWeek.mondayOf(TEST_EPOCH_DAY)

    private fun walk(id: Long, day: Long, minutes: Int = 30, distanceM: Int? = 3_000) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 36_000_000, durationMinutes = minutes,
        kind = WorkoutKind.WALK, title = null, distanceM = distanceM, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
    )

    @Test
    fun `food is averaged over the days that hold something, and days logged counts them`() {
        val food = mapOf(
            monday to DayTotals(2_000, 100, 200, 80),
            monday + 1 to DayTotals(2_200, 120, 220, 60),
        )
        val week = WeekFigures.of(monday, food, emptyList(), emptyList(), emptyList(), emptyList(), plan = null)

        assertThat(week.food).isEqualTo(FoodWeek(daysLogged = 2, kcal = 2_100, proteinG = 110, carbsG = 210, fatG = 70))
    }

    @Test
    fun `movement counts visible, counted sessions; energy and steps are a day's average where recorded`() {
        val hidden = walk(3, monday + 2).copy(hidden = true)
        val days = listOf(HealthDay(monday, steps = 8_000, activeKcal = 300), HealthDay(monday + 1, steps = 10_000))
        val reviews = listOf(TrainerReview(workoutId = 1, planId = null, felt = Felt.RIGHT, words = "ignored"))

        val week = WeekFigures.of(monday, emptyMap(), emptyList(), listOf(walk(1, monday), walk(2, monday + 1, 60), hidden), reviews, days, null)

        assertThat(week.movement).isEqualTo(
            MovementFigures(sessions = 2, minutes = 90, distanceM = 6_000, easy = 0, right = 1, hard = 0, activeKcalADay = 300, stepsADay = 9_000),
        )
    }

    @Test
    fun `the weight trend's change across the week rests on fresh weigh-ins, else there is none`() {
        val trend = WeightTrend.of(listOf(WeightReading(monday - 3, 80.0), WeightReading(monday + 6, 79.0)))

        assertThat(WeekFigures.of(monday, emptyMap(), trend, emptyList(), emptyList(), emptyList(), null).weightChangeKg)
            .isWithin(0.001).of(trend.last().trendKg - trend.first().trendKg)
        val stale = WeightTrend.of(listOf(WeightReading(monday - 30, 80.0), WeightReading(monday + 6, 79.0)))
        assertThat(WeekFigures.of(monday, emptyMap(), stale, emptyList(), emptyList(), emptyList(), null).weightChangeKg).isNull()
    }

    @Test
    fun `a week with no food, no weigh-in and no counted session is quiet`() {
        val quiet = WeekFigures.of(monday, emptyMap(), emptyList(), listOf(walk(1, monday).copy(hidden = true)), emptyList(), emptyList(), null)
        val weighed = WeekFigures.of(monday, emptyMap(), WeightTrend.of(listOf(WeightReading(monday + 2, 80.0))), emptyList(), emptyList(), emptyList(), null)

        assertThat(quiet.quiet).isTrue()
        assertThat(weighed.quiet).isFalse()
    }

    @Test
    fun `the 4-week average is over the earlier weeks that have a figure, and none when no week has`() {
        fun food(kcal: Int) = mapOf(monday to DayTotals(kcal, 100, 200, 60))
        val earlier = listOf(2_000, 2_200).mapIndexed { i, kcal ->
            WeekFigures.of(monday - 7L * (i + 1), food(kcal).mapKeys { it.key - 7L * (i + 1) }, emptyList(), emptyList(), emptyList(), emptyList(), null)
        } + List(2) { i -> WeekFigures.of(monday - 7L * (i + 3), emptyMap(), emptyList(), emptyList(), emptyList(), emptyList(), null) }
        val figures = LetterFigures(WeekFigures.of(monday, emptyMap(), emptyList(), emptyList(), emptyList(), emptyList(), null), earlier, targetKcal = 2_100)

        assertThat(figures.average.kcal).isEqualTo(2_100)
        assertThat(figures.average.daysLogged).isWithin(0.001).of(0.5)
        assertThat(figures.average.weightChangeKg).isNull()
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
```

Check the `Workout` constructor's real parameter list (`domain/movement/Workout.kt`) and adapt `walk()`
to it — only the invented values above matter.

- [ ] **Step 2: Run it; it fails to compile** (`WeekFigures` does not exist).
- [ ] **Step 3: The code.** In `MonthlyLines.kt` change `private fun weightChange(` to
  `internal fun weightChange(`. Then `domain/letter/WeekFigures.kt`:

```kotlin
package com.metaself.app.domain.letter

import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.MonthlyLines
import com.metaself.app.domain.trainer.SessionReviews
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.weight.TrendPoint
import kotlin.math.roundToInt

/** A week's food (D100): averages of daily totals over the [daysLogged] days that hold something. */
data class FoodWeek(val daysLogged: Int, val kcal: Int?, val proteinG: Int?, val carbsG: Int?, val fatG: Int?)

/** A week's movement (D100). A null is a figure not recorded on any day of the week. */
data class MovementFigures(
    val sessions: Int,
    val minutes: Int,
    val distanceM: Int?,
    val easy: Int,
    val right: Int,
    val hard: Int,
    val activeKcalADay: Int?,
    val stepsADay: Int?,
)

/** The weekly plan's week (D100): its title, and this week's planned and done sessions. [D105] done = ticked only. */
data class PlanWeekFigures(val title: String, val planned: Int, val done: Int, val ended: Boolean)

/** One Monday-to-Sunday week, counted on the phone (D100). */
data class WeekFigures(
    val monday: Long,
    val food: FoodWeek,
    val weightChangeKg: Double?,
    val weighedIn: Boolean,
    val movement: MovementFigures,
    val plan: PlanWeekFigures?,
) {
    /** D99, design question 15: nothing to write about. */
    val quiet: Boolean get() = food.daysLogged == 0 && !weighedIn && movement.sessions == 0

    companion object {
        /**
         * @param food each day's totals, for days holding at least one item; days outside the week are ignored.
         * @param trend the whole smoothed trend; its change across the week is [MonthlyLines.weightChange]'s.
         * @param workouts the record's sessions as read (combined, D92); only the week's visible, counted ones count.
         * @param reviews any; each counts toward its session's felt, never its words.
         * @param days the health record's days; only the week's are read.
         */
        fun of(
            monday: Long,
            food: Map<Long, DayTotals>,
            trend: List<TrendPoint>,
            workouts: List<Workout>,
            reviews: List<TrainerReview>,
            days: List<HealthDay>,
            plan: PlanWeekFigures?,
        ): WeekFigures {
            val sunday = monday + 6
            val week = monday..sunday
            val eaten = food.filterKeys { it in week }.values
            val sessions = workouts.filter { !it.hidden && it.counted && it.epochDay in week }
            val byWorkout = SessionReviews.bySession(sessions, reviews)
            val felt = sessions.mapNotNull { byWorkout[it.id]?.felt }
            val weekDays = days.filter { it.epochDay in week }
            return WeekFigures(
                monday = monday,
                food = FoodWeek(
                    daysLogged = eaten.size,
                    kcal = eaten.averageOf { it.kcal },
                    proteinG = eaten.averageOf { it.proteinG },
                    carbsG = eaten.averageOf { it.carbsG },
                    fatG = eaten.averageOf { it.fatG },
                ),
                weightChangeKg = MonthlyLines.weightChange(monday - 1, sunday, trend),
                weighedIn = trend.any { it.reading.epochDay in week },
                movement = MovementFigures(
                    sessions = sessions.size,
                    minutes = sessions.sumOf { it.durationMinutes },
                    distanceM = sessions.mapNotNull { it.distanceM }.takeIf { it.isNotEmpty() }?.sum(),
                    easy = felt.count { it == Felt.EASY },
                    right = felt.count { it == Felt.RIGHT },
                    hard = felt.count { it == Felt.HARD },
                    activeKcalADay = weekDays.mapNotNull { it.activeKcal }.averageOrNull(),
                    stepsADay = weekDays.mapNotNull { it.steps }.averageOrNull(),
                ),
                plan = plan,
            )
        }

        private fun Collection<DayTotals>.averageOf(pick: (DayTotals) -> Int): Int? =
            takeIf { it.isNotEmpty() }?.map(pick)?.average()?.roundToInt()

        private fun List<Int>.averageOrNull(): Int? = takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    }
}

/** The mean of the earlier weeks' figures, each over the weeks that have it (design question 14). */
data class FourWeekAverage(
    val daysLogged: Double,
    val kcal: Int?,
    val proteinG: Int?,
    val weightChangeKg: Double?,
    val sessions: Double,
    val distanceM: Int?,
    val stepsADay: Int?,
)

/** This week and the four before it, oldest last (D100), with today's calorie target (design question 1). */
data class LetterFigures(val week: WeekFigures, val earlier: List<WeekFigures>, val targetKcal: Int?) {
    init {
        require(earlier.size <= WEEKS_BEFORE) { "at most $WEEKS_BEFORE earlier weeks" }
    }

    val average: FourWeekAverage by lazy {
        fun ints(pick: (WeekFigures) -> Int?) = earlier.mapNotNull(pick).takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        FourWeekAverage(
            daysLogged = earlier.map { it.food.daysLogged }.averageOrZero(),
            kcal = ints { it.food.kcal },
            proteinG = ints { it.food.proteinG },
            weightChangeKg = earlier.mapNotNull { it.weightChangeKg }.takeIf { it.isNotEmpty() }?.average(),
            sessions = earlier.map { it.movement.sessions }.averageOrZero(),
            distanceM = ints { it.movement.distanceM },
            stepsADay = ints { it.movement.stepsADay },
        )
    }

    companion object {
        const val WEEKS_BEFORE = 4

        private fun List<Int>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
    }
}
```

Check that `Workout` has `counted` and `hidden` (it does in 0.68.0) and `SessionReviews` is in
`domain.trainer` — adjust imports to the real packages.

- [ ] **Step 4: Run** `--tests "com.metaself.app.domain.letter.*" --tests "com.metaself.app.domain.trainer.MonthlyLinesTest"`;
  expected PASS, 0 skipped.
- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/letter/WeekFigures.kt app/src/main/java/com/metaself/app/domain/trainer/MonthlyLines.kt app/src/test/java/com/metaself/app/domain/letter/WeekFiguresTest.kt
git commit -m "feat: a week's figures for the letter, counted on the phone (D100)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Food totals by day, summed in the database

**Files:**
- Modify: `data/day/MealDao.kt`, `data/day/DayEntities.kt`
- Create: `data/letter/FoodTotals.kt`
- Test: `data/day/MealDaoTest.kt` (CI only), `data/letter/FakeFoodTotals.kt`

- [ ] **Step 1: The CI test** — add to `MealDaoTest` (already one of the ten; follow its existing
  `@Test` pattern and helpers for inserting a meal with items):

```kotlin
    /** D100: each day's four totals between two days, summed in the database. Invented figures. */
    @Test
    fun `totals by day sum every item of every meal on each day in the span`() = runTest {
        assumeSqliteRuntime()
        // Two meals on day 10 (items 500+300 kcal), one on day 11 (400), one on day 20 (outside).
        insertMeal(epochDay = 10, items = listOf(item(kcal = 500, protein = 20, carbs = 50, fat = 10), item(kcal = 300, protein = 10, carbs = 30, fat = 10)))
        insertMeal(epochDay = 10, items = listOf(item(kcal = 100, protein = 5, carbs = 10, fat = 2)))
        insertMeal(epochDay = 11, items = listOf(item(kcal = 400, protein = 30, carbs = 20, fat = 20)))
        insertMeal(epochDay = 20, items = listOf(item(kcal = 900, protein = 1, carbs = 1, fat = 1)))

        val rows = dao.totalsByDayBetween(10, 16).associateBy { it.epochDay }

        assertThat(rows.keys).containsExactly(10L, 11L)
        assertThat(rows.getValue(10)).isEqualTo(DayTotalsRow(10, 900, 35, 90, 22))
        assertThat(rows.getValue(11)).isEqualTo(DayTotalsRow(11, 400, 30, 20, 20))
    }
```

Use the class's existing insert helpers; if their names differ, adapt (only the figures above matter).

- [ ] **Step 2: The query and the row.** In `DayEntities.kt`, beside `DayKcal`:

```kotlin
/** One day's four totals (D100), summed in the database. */
data class DayTotalsRow(val epochDay: Long, val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int)
```

In `MealDao.kt`, after `kcalByDaySince`:

```kotlin
    /**
     * Each day's four totals from [fromEpochDay] to [toEpochDay], for the weekly letter (D100): summed
     * here, one row per day that holds an item. Nothing about a meal or a food leaves this query.
     */
    @Query(
        "SELECT m.epochDay AS epochDay, SUM(f.kcal) AS kcal, SUM(f.proteinG) AS proteinG, " +
            "SUM(f.carbsG) AS carbsG, SUM(f.fatG) AS fatG FROM meals m " +
            "INNER JOIN food_items f ON f.mealId = m.id " +
            "WHERE m.epochDay BETWEEN :fromEpochDay AND :toEpochDay " +
            "GROUP BY m.epochDay",
    )
    suspend fun totalsByDayBetween(fromEpochDay: Long, toEpochDay: Long): List<DayTotalsRow>
```

- [ ] **Step 3: The port** (`data/letter/FoodTotals.kt`):

```kotlin
package com.metaself.app.data.letter

import com.metaself.app.data.day.MealDao
import com.metaself.app.domain.day.DayTotals
import javax.inject.Inject

/** Each day's food totals over a span (D100) — the only thing the letter reads of what was eaten. */
fun interface FoodTotals {
    suspend fun byDay(from: Long, to: Long): Map<Long, DayTotals>
}

class RoomFoodTotals @Inject constructor(private val meals: MealDao) : FoodTotals {
    override suspend fun byDay(from: Long, to: Long): Map<Long, DayTotals> =
        meals.totalsByDayBetween(from, to).associate { it.epochDay to DayTotals(it.kcal, it.proteinG, it.carbsG, it.fatG) }
}
```

Bind it in `di/DataModule.kt`: `@Provides fun provideFoodTotals(totals: RoomFoodTotals): FoodTotals = totals`.
Test fake (`test/.../data/letter/FakeFoodTotals.kt`):

```kotlin
package com.metaself.app.data.letter

import com.metaself.app.domain.day.DayTotals

class FakeFoodTotals(var days: Map<Long, DayTotals> = emptyMap()) : FoodTotals {
    override suspend fun byDay(from: Long, to: Long): Map<Long, DayTotals> = days.filterKeys { it in from..to }
}
```

If any hand-written `MealDao` stand-in in tests (`BackupRestoreOrderTest`, `SettingsViewModelTest`) is a
`java.lang.reflect.Proxy`, nothing changes; if one implements the interface by hand, add the method
returning `emptyList()`.

- [ ] **Step 4: Run** `:app:testDebugUnitTest --tests "com.metaself.app.data.day.MealDaoTest" --tests "com.metaself.app.data.backup.*" --tests "com.metaself.app.ui.screen.settings.*"`;
  expected: compiles; `MealDaoTest` skips (CI), the rest pass.
- [ ] **Step 5: Commit** (explicit paths: `MealDao.kt`, `DayEntities.kt`, `FoodTotals.kt`, `DataModule.kt`,
  `MealDaoTest.kt`, `FakeFoodTotals.kt`): `feat: each day's food totals, summed in the database, for the letter (D100)`.

---

### Task 4: The letter's vocabulary, its request and its reply — pure

**Files:**
- Create: `domain/letter/WeeklyLetter.kt`, `data/ai/LetterPrompt.kt`, `data/ai/LetterResponse.kt`
- Modify: `domain/trainer/TrainerRequest.kt` (`private fun session(` → `internal fun session(`),
  `data/ai/TrainerPrompt.kt` (`private fun session(session: SessionFacts)` → `internal fun sessionJson(session: SessionFacts)`
  and its callers; `date()` → `internal`)
- Test: `data/ai/LetterPromptTest.kt`, `data/ai/LetterResponseTest.kt`

- [ ] **Step 1: The vocabulary** (`domain/letter/WeeklyLetter.kt`):

```kotlin
package com.metaself.app.domain.letter

import com.metaself.app.domain.trainer.BodyFacts
import com.metaself.app.domain.trainer.GoalFacts
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.SessionFacts

/** The six texts of a letter (D102), all non-empty. Advice, never a measurement (D4). */
data class LetterTexts(
    val headline: String,
    val effort: String,
    val progress: String,
    val lookAt: String,
    val nextWeek: String,
    val close: String,
)

/** A stored letter (D104). [bandDataUntil] is the last copy's time when the band's data may be behind (D99). */
data class WeeklyLetter(
    val id: Long = 0,
    val weekMonday: Long,
    val createdAtMillis: Long,
    val figures: LetterFigures,
    val texts: LetterTexts,
    val model: String,
    val bandDataUntil: Long? = null,
    val readAtMillis: Long? = null,
)

/**
 * Everything one letter request holds (D101) — built fresh from the record. There is deliberately no
 * field for a meal, a food, an amount, a time of eating, a single weigh-in, sleep or the owner's words;
 * `LetterPromptTest` fails if one is added.
 *
 * @property sessions this week's sessions as D84 sends them, with `words`, `felt` and `plan` always null.
 * @property lastNextWeek last week's letter's "for next week" line, when there is one.
 * @property planWeek this week of the weekly plan, when one ran.
 */
data class LetterRequest(
    val figures: LetterFigures,
    val sessions: List<SessionFacts>,
    val aboutMe: String?,
    val goal: GoalFacts?,
    val body: BodyFacts?,
    val planTitle: String?,
    val planWeek: PlanWeek?,
    val lastNextWeek: String?,
)
```

(`BodyFacts`, `GoalFacts` and `SessionFacts` are declared in `domain/trainer/TrainerRequest.kt`, package
`com.metaself.app.domain.trainer`; `PlanWeek` in `domain/trainer/Programme.kt`.) Guard in the request builder (Task 10) that every `SessionFacts`
has `words == null && felt == null && plan == null`; add to `LetterRequest`:

```kotlin
    init {
        require(sessions.all { it.words == null && it.felt == null && it.plan == null }) {
            "the letter never sends the owner's words on a session (D101)"
        }
    }
```

- [ ] **Step 2: The prompt test** (`data/ai/LetterPromptTest.kt`):

```kotlin
package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.letter.FoodWeek
import com.metaself.app.domain.letter.LetterFigures
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.MovementFigures
import com.metaself.app.domain.letter.WeekFigures
import com.metaself.app.domain.movement.MovementWeek
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/** What the letter sends (D101). Every figure and word is invented. */
class LetterPromptTest {

    private val monday = MovementWeek.mondayOf(TEST_EPOCH_DAY)

    private fun week(monday: Long) = WeekFigures(
        monday, FoodWeek(5, 2_000, 100, 200, 70), -0.2, true,
        MovementFigures(3, 120, 9_000, 1, 1, 0, 300, 8_000), null,
    )

    private fun request() = LetterRequest(
        figures = LetterFigures(week(monday), List(4) { week(monday - 7L * (it + 1)) }, 2_100),
        sessions = emptyList(), aboutMe = "Invented note.", goal = null, body = null,
        planTitle = null, planWeek = null, lastNextWeek = "Invented line.",
    )

    private fun user(body: String) = Json.parseToJsonElement(
        Json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray
            .single { it.jsonObject.getValue("role").jsonPrimitive.content == "user" }
            .jsonObject.getValue("content").jsonPrimitive.content,
    ).jsonObject

    @Test
    fun `a letter request names its schema and holds exactly D101's parts`() {
        val body = LetterPrompt.body("a-model", request(), RequestProfile.DETERMINISTIC)
        val user = user(body)

        assertThat(body).contains("weekly_letter")
        assertThat(user.keys).containsExactly(
            "week", "earlier_weeks", "target_kcal", "sessions", "about_me", "goal", "body", "plan", "last_next_week",
        )
        assertThat(user.getValue("earlier_weeks").jsonArray).hasSize(4)
    }

    @Test
    fun `food goes as a week's averages and days logged, and nothing else about eating`() {
        val food = user(LetterPrompt.body("a-model", request(), RequestProfile.DETERMINISTIC))
            .getValue("week").jsonObject.getValue("food").jsonObject

        assertThat(food.keys).containsExactly("days_logged", "kcal_a_day", "protein_g_a_day", "carbs_g_a_day", "fat_g_a_day")
    }

    @Test
    fun `the tone rules are in the instructions`() {
        val system = Json.parseToJsonElement(LetterPrompt.body("a-model", request(), RequestProfile.DETERMINISTIC)).jsonObject
            .getValue("messages").jsonArray.first().jsonObject.getValue("content").jsonPrimitive.content

        listOf("effort", "never as a failure", "missing information", "no exclamation marks", "never shame")
            .forEach { assertThat(system).contains(it) }
    }
}
```

Check `RequestProfile.DETERMINISTIC` exists (it does in `TrainerPromptTest`); the messages shape is
`ChatRequest`'s — reuse `TrainerPromptTest.userContent`'s approach if it differs.

- [ ] **Step 3: The prompt** (`data/ai/LetterPrompt.kt`):

```kotlin
package com.metaself.app.data.ai

import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.WeekFigures
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What leaves the phone for the weekly letter (D101) — the fourth thing this app sends. Pure, with its
 * own "nothing else is sent" test. Food goes only as a week's averages of daily totals and a count of
 * days logged; the tone rules (D102) are written into the instructions, not left to chance.
 */
object LetterPrompt {

    private val INSTRUCTIONS = """
        You are a walking and running trainer who writes one person a short letter at the end of each week,
        about their food, weight and movement together. You are given this week's figures and each of the
        four weeks before it, all counted by their app: food as averages a day over the days they logged
        and how many days they logged, the change of their smoothed weight trend across the week, their
        sessions with minutes, distance and how they felt, active energy and steps a day, and their weekly
        plan's sessions when one ran. Also this week's sessions, their standing note about themselves
        (about_me), their goal's direction and weekly rate, their age, sex and height, today's daily calorie
        target, and the "for next week" line of last week's letter. A null is a figure not recorded.

        How to write. They asked for recognition of their effort, and for encouragement without blame when
        a week goes badly: a trainer, not a new friend.
        - Name the effort behind a result, not only the result: what they made room for, kept up or chose.
        - Compare them only with their own earlier weeks, never with other people or ideals.
        - One thing to look at, framed as information or a next step, never as a failure. A gap in logging
          is missing information, not a fault. When a figure rests on few days, say it cannot say much.
        - Plain English, to them, in the second person. Short sentences. No exclamation marks, no emoji.
          Never shame, never exaggerate. Every figure you mention must be one given here.
        - Keep the letter consistent with last week's "for next week" line, and say how it went when the
          figures show it.
        - Nothing medical: never diagnose. If their note mentions an injury, plan around it.

        Reply with: headline (one line, about the week), effort (what they put in), progress (where it is
        taking them, against earlier weeks), look_at (one thing, as above), next_week (one concrete step),
        and close (one warm sentence about the week or the person, not a slogan).
    """.trimIndent()

    fun body(model: String, request: LetterRequest, profile: RequestProfile = RequestProfile.guess(model)): String =
        ChatRequest.body(
            model, profile,
            listOf(ChatRequest.Message("system", INSTRUCTIONS), ChatRequest.Message("user", user(request).toString())),
            "weekly_letter", SCHEMA,
        )

    private fun user(request: LetterRequest): JsonObject = buildJsonObject {
        put("week", week(request.figures.week))
        putJsonArray("earlier_weeks") { request.figures.earlier.forEach { add(week(it)) } }
        put("target_kcal", request.figures.targetKcal?.let(::JsonPrimitive) ?: JsonNull)
        putJsonArray("sessions") { request.sessions.forEach { add(TrainerPrompt.sessionJson(it)) } }
        put("about_me", request.aboutMe?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "goal",
            request.goal?.let { buildJsonObject { put("direction", it.direction.name.lowercase()); put("kg_a_week", it.kgPerWeek) } } ?: JsonNull,
        )
        put(
            "body",
            request.body?.let { buildJsonObject { put("age", it.ageYears); put("sex", it.sex.name.lowercase()); put("height_cm", it.heightCm) } } ?: JsonNull,
        )
        put(
            "plan",
            if (request.planTitle == null || request.planWeek == null) JsonNull else buildJsonObject {
                put("title", request.planTitle)
                put("focus", request.planWeek.focus)
                putJsonArray("sessions") {
                    request.planWeek.sessions.forEach { s ->
                        add(buildJsonObject { put("kind", s.kind.name.lowercase()); put("minutes", s.minutes); put("effort", s.effort.name.lowercase()); put("what", s.what) })
                    }
                }
            },
        )
        put("last_next_week", request.lastNextWeek?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun week(week: WeekFigures): JsonObject = buildJsonObject {
        put("from", TrainerPrompt.date(week.monday))
        putJsonObject("food") {
            put("days_logged", week.food.daysLogged)
            put("kcal_a_day", week.food.kcal?.let(::JsonPrimitive) ?: JsonNull)
            put("protein_g_a_day", week.food.proteinG?.let(::JsonPrimitive) ?: JsonNull)
            put("carbs_g_a_day", week.food.carbsG?.let(::JsonPrimitive) ?: JsonNull)
            put("fat_g_a_day", week.food.fatG?.let(::JsonPrimitive) ?: JsonNull)
        }
        put("weight_trend_change_kg", week.weightChangeKg?.let { JsonPrimitive(Math.round(it * 100) / 100.0) } ?: JsonNull)
        putJsonObject("movement") {
            put("sessions", week.movement.sessions)
            put("minutes", week.movement.minutes)
            put("distance_m", week.movement.distanceM?.let(::JsonPrimitive) ?: JsonNull)
            putJsonObject("felt") { put("easy", week.movement.easy); put("right", week.movement.right); put("hard", week.movement.hard) }
            put("active_kcal_a_day", week.movement.activeKcalADay?.let(::JsonPrimitive) ?: JsonNull)
            put("steps_a_day", week.movement.stepsADay?.let(::JsonPrimitive) ?: JsonNull)
        }
        put(
            "weekly_plan",
            week.plan?.let { buildJsonObject { put("planned", it.planned); put("done", it.done); put("ended", it.ended) } } ?: JsonNull,
        )
    }

    private fun text() = buildJsonObject { put("type", "string") }

    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") { NAMES.forEach { put(it, text()) } }
        putJsonArray("required") { NAMES.forEach { add(it) } }
    }

    /** The reply's six texts, in the order shown (D102). */
    val NAMES = listOf("headline", "effort", "progress", "look_at", "next_week", "close")
}
```

In `TrainerPrompt.kt`: rename `private fun session(session: SessionFacts)` to
`internal fun sessionJson(session: SessionFacts)` and update its two callers; make `private fun date(` →
`internal fun date(`. Nothing else in `TrainerPrompt` changes; `TrainerPromptTest` must still pass.

- [ ] **Step 4: The reply** (`data/ai/LetterResponse.kt`) and its test:

```kotlin
package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.letter.LetterTexts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The letter's reply, read strictly (D102) — [TrainerResponse]'s twin; also how a letter is stored. */
object LetterResponse {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Parsed {
        data class Written(val texts: LetterTexts, val model: String) : Parsed
        data class Failed(val failure: EstimateResult) : Parsed
    }

    fun parse(body: String, model: String): Parsed =
        content(body)?.let(::read)?.let { Parsed.Written(it, model) }
            ?: Parsed.Failed(EstimateResult.Unreadable("the reply was not in the shape this app asked for", content(body) ?: body))

    /** Six non-empty strings, or null. */
    fun read(content: String?): LetterTexts? = runCatching {
        val o = json.parseToJsonElement(content!!).jsonObject
        fun t(name: String): String {
            val v = o.getValue(name).jsonPrimitive
            require(v.isString) { "$name is not a string" }
            return v.content.trim().also { require(it.isNotEmpty()) { "$name is empty" } }
        }
        LetterTexts(t("headline"), t("effort"), t("progress"), t("look_at"), t("next_week"), t("close"))
    }.getOrNull()

    fun encode(texts: LetterTexts): String = buildJsonObject {
        put("headline", texts.headline); put("effort", texts.effort); put("progress", texts.progress)
        put("look_at", texts.lookAt); put("next_week", texts.nextWeek); put("close", texts.close)
    }.toString()

    private fun content(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray
            .first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    }.getOrNull()
}
```

Check the `EstimateResult.Unreadable` constructor's real parameters in `domain/ai/MealEstimator.kt` and
match `TrainerResponse`'s call. `LetterResponseTest` (JUnit 5): a full reply reads; each of the six missing,
empty, or a number → `Failed(Unreadable)`; `read(encode(x)) == x`.

- [ ] **Step 5: Run** `--tests "com.metaself.app.data.ai.*"`; expected PASS (TrainerPromptTest included).
- [ ] **Step 6: Commit** (explicit paths): `feat: the weekly letter's request and reply, pure (D101, D102)`.

---

### Task 5: The letter writer — one call

**Files:** Create `domain/letter/LetterWriter.kt`, `data/ai/OpenAiLetterWriter.kt`; modify `di/AiModule.kt`;
test `data/ai/OpenAiLetterWriterTest.kt`, `data/letter/FakeLetterWriter.kt`

- [ ] **Step 1: The port**:

```kotlin
package com.metaself.app.domain.letter

import com.metaself.app.domain.ai.EstimateResult

/** Asking for a letter (D101): one ask; an implementation never retries a failed answer. */
fun interface LetterWriter {
    suspend fun write(request: LetterRequest): LetterReply
}

sealed interface LetterReply {
    data class Written(val texts: LetterTexts, val model: String) : LetterReply
    data class Failed(val failure: EstimateResult) : LetterReply
}
```

- [ ] **Step 2: The caller**, modelled on `OpenAiTrainer` (same `OpenAiCall`, same logging by kind):

```kotlin
package com.metaself.app.data.ai

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.letter.LetterReply
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.LetterWriter
import okhttp3.OkHttpClient

/** The one [LetterWriter] (D101): [LetterPrompt] out, [LetterResponse] back, over the shared [OpenAiCall]. */
class OpenAiLetterWriter(
    keys: ApiKeyStore,
    settings: AiSettingsStore,
    client: OkHttpClient,
    profiles: RequestProfileStore,
    private val problems: ProblemLog = ProblemLog.NONE,
    baseUrl: String = OpenAiCall.OPENAI_URL,
) : LetterWriter {

    private val call = OpenAiCall(keys, settings, client, profiles, baseUrl)

    override suspend fun write(request: LetterRequest): LetterReply =
        when (val outcome = call.send(build = { model, profile -> LetterPrompt.body(model, request, profile) })) {
            is OpenAiCall.Outcome.Body -> when (val parsed = LetterResponse.parse(outcome.text, outcome.model)) {
                is LetterResponse.Parsed.Written -> { call.remember(outcome); LetterReply.Written(parsed.texts, parsed.model) }
                is LetterResponse.Parsed.Failed -> LetterReply.Failed(parsed.failure).also { recorded(it.failure, null) }
            }
            is OpenAiCall.Outcome.Failed -> LetterReply.Failed(outcome.failure).also { recorded(it.failure, outcome.status) }
        }

    /** By kind only: the request holds the owner's note and the answer may quote it. */
    private fun recorded(failure: EstimateResult, status: Int?) {
        when (failure) {
            is EstimateResult.Refused -> problems.record("letter refused", if (status != null) "the provider answered $status" else "the call could not be made")
            is EstimateResult.Unreadable -> problems.record("letter unreadable", "the reply was not in the shape this app asked for")
            is EstimateResult.Unreachable -> problems.record("letter unreachable", "no answer")
            else -> Unit
        }
    }
}
```

Check `OpenAiCall.send`'s real parameter names and `Outcome.Body`'s `text`/`model` in the file (Task
reading `OpenAiTrainer.ask` shows the pattern). Bind in `AiModule` beside `provideTrainer`:

```kotlin
    /** The weekly letter (D101): the same key, ceiling, client, profiles and log as the estimator. */
    @Provides
    @Singleton
    fun provideLetterWriter(
        keys: ApiKeyStore, settings: AiSettingsStore, client: OkHttpClient, profiles: RequestProfileStore, problems: ProblemLog,
    ): LetterWriter = OpenAiLetterWriter(keys, settings, client, profiles, problems)
```

- [ ] **Step 3: Test** with MockWebServer as `OpenAiTrainerTest` does: an answered letter → `Written`; a
  500 → `Failed(Refused)` and one problem line whose detail holds no words from the request; no key →
  `Failed(NoKey)` and no request made. `FakeLetterWriter` (test): a list of scripted replies and the
  requests asked.
- [ ] **Step 4: Run** `--tests "com.metaself.app.data.ai.*"`; **Step 5: Commit**
  `feat: the weekly letter's call (D101)`.

---

### Task 6: One table, version 12, and the migration

**Files:** Modify `data/trainer/TrainerEntities.kt`, `data/trainer/TrainerDao.kt`, `data/day/MetaSelfDatabase.kt`,
`di/DataModule.kt`, `tools/README.md`; create `data/day/LetterMigration.kt`, `tools/check-migration-11-12.py`;
test `data/MigrationTest.kt` (CI).

- [ ] **Step 1: The entity** (in `TrainerEntities.kt`):

```kotlin
/**
 * One weekly letter (D104): its week (unique — design question 7), the figures and the texts as JSON,
 * the model, whether the band's data may be behind, and when it was read.
 */
@Entity(tableName = "weekly_letters", indices = [Index(value = ["weekMonday"], unique = true)])
data class WeeklyLetterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekMonday: Long,
    val createdAtMillis: Long,
    val figures: String,
    val letter: String,
    val model: String,
    val bandDataUntil: Long?,
    val readAtMillis: Long?,
)
```

- [ ] **Step 2: The DAO** (append to `TrainerDao`):

```kotlin
    // The weekly letter (D104).
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLetter(letter: WeeklyLetterEntity): Long

    @Query("SELECT * FROM weekly_letters WHERE weekMonday = :weekMonday")
    suspend fun letterOf(weekMonday: Long): WeeklyLetterEntity?

    @Query("SELECT * FROM weekly_letters ORDER BY weekMonday DESC")
    fun observeLetters(): Flow<List<WeeklyLetterEntity>>

    @Query("UPDATE weekly_letters SET readAtMillis = :atMillis WHERE weekMonday = :weekMonday AND readAtMillis IS NULL")
    suspend fun markLetterRead(weekMonday: Long, atMillis: Long)

    @Query("SELECT * FROM weekly_letters ORDER BY id")
    suspend fun allLetters(): List<WeeklyLetterEntity>

    @Query("DELETE FROM weekly_letters")
    suspend fun deleteLetters()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLetters(letters: List<WeeklyLetterEntity>)
```

- [ ] **Step 3: The database.** Add `WeeklyLetterEntity::class` to `entities`, `version = 12`.
- [ ] **Step 4: Generate the schema.** `free -m`; `~/bin/gradlew-safe :app:compileDebugKotlin > /tmp/ms-letter-6.log 2>&1; echo "exit $?"`.
  Expected exit 0 and a new `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/12.json`. Copy the
  `weekly_letters` entity's `createSql` and its index's `createSql` **verbatim** (with `${TABLE_NAME}`
  replaced by `weekly_letters`) into the migration:

```kotlin
package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 12: the weekly letters (D104).
 *
 * **One table and its unique index are created, and nothing else is read or written.** The statements are
 * the exported schema's own, verbatim (`12.json`). `tools/check-migration-11-12.py` proves them on this
 * machine; `MigrationTest` validates in CI.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `weekly_letters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`weekMonday` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, `figures` TEXT NOT NULL, " +
                "`letter` TEXT NOT NULL, `model` TEXT NOT NULL, `bandDataUntil` INTEGER, `readAtMillis` INTEGER)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_weekly_letters_weekMonday` ON `weekly_letters` (`weekMonday`)")
    }
}
```

(The SQL above is the expected output; **12.json wins** if it differs.) Register it in `DataModule`'s
`addMigrations(…, MIGRATION_11_12)` after D105's `MIGRATION_10_11`.

- [ ] **Step 5: The local check.** Copy `tools/check-migration-9-10.py` to `tools/check-migration-11-12.py`;
  change the migration path to `LetterMigration.kt`, versions 11/12, `NEW_TABLES = ("weekly_letters",)`, the
  docstring, and replace its last-specific check with: two rows with the same `weekMonday` are refused
  (`sqlite3.IntegrityError`), two different weeks are accepted. Run `python3 tools/check-migration-11-12.py`;
  expected `OK: 2 statements, <n> tables untouched, the new table matches 12.json`. Add a line to
  `tools/README.md` beside the others.
- [ ] **Step 6: The CI test** (append to `MigrationTest`, as the 9→10 case — **[D105]** build on version 11
  and name one of D105's tables in the kept-rows list):

```kotlin
    /** D104: one new table, empty; every workout kept. Invented figures. */
    @Test
    fun `a version 11 database migrates to version 12 with an empty weekly letters table`() {
        assumeSqliteRuntime()
        helper.createDatabase(TEST_DB, 11).use { db ->
            db.execSQL(
                "INSERT INTO workouts (id, epochDay, startedAtMillis, durationMinutes, kind, energySource, source, hidden) " +
                    "VALUES (1, 20699, 1000, 40, 'WALK', 'NONE', 'SYNCED', 0)",
            )
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12)
        listOf("weekly_letters" to 0, "workouts" to 1).forEach { (table, rows) ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(rows)
            }
        }
        migrated.execSQL("INSERT INTO weekly_letters (weekMonday, createdAtMillis, figures, letter, model) VALUES (20696, 1, '{}', '{}', 'm')")
        migrated.execSQL("INSERT OR IGNORE INTO weekly_letters (weekMonday, createdAtMillis, figures, letter, model) VALUES (20696, 2, '{}', '{}', 'm')")
        migrated.query("SELECT COUNT(*) FROM weekly_letters").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }
        migrated.close()
    }
```

- [ ] **Step 7: Run** `--tests "com.metaself.app.data.MigrationTest"` (skips locally) and the python check.
- [ ] **Step 8: Commit** (explicit paths incl. `app/schemas/.../12.json`):
  `feat: a table for the weekly letters (D104, schema 12)`.

---

### Task 7: The letters' store

**Files:** Create `data/letter/LetterStore.kt`, `data/letter/LetterCodec.kt`; modify `di/DataModule.kt`;
test `data/letter/RoomLetterStoreTest.kt` (proxy DAO), `data/letter/FakeLetterStore.kt`,
`data/letter/LetterCodecTest.kt`, `data/health/HealthRecordStoreTest.kt` (CI).

- [ ] **Step 1: The figures as JSON** (`LetterCodec.kt`) — `@Serializable` mirrors of `WeekFigures`,
  `FoodWeek`, `MovementFigures`, `PlanWeekFigures`, `LetterFigures` (private DTOs in this file, snake_case
  `@SerialName`s), with `fun encode(figures: LetterFigures): String` and `fun read(text: String): LetterFigures?`
  (null on anything unreadable, never a throw). Test: `read(encode(x)) == x` for a full and an all-null
  figure set; `read("{}")` and `read("not json")` are null.

- [ ] **Step 2: The port and Room implementation:**

```kotlin
package com.metaself.app.data.letter

import com.metaself.app.data.ai.LetterResponse
import com.metaself.app.data.trainer.TrainerDao
import com.metaself.app.data.trainer.WeeklyLetterEntity
import com.metaself.app.domain.letter.WeeklyLetter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The weekly letters (D104). */
interface LetterStore {
    /** Newest first; a row this version cannot read is left out. */
    fun observeAll(): Flow<List<WeeklyLetter>>
    suspend fun of(weekMonday: Long): WeeklyLetter?

    /** Throws if the week already has one (design question 7). */
    suspend fun add(letter: WeeklyLetter): Long
    suspend fun markRead(weekMonday: Long, atMillis: Long)
}

class RoomLetterStore @Inject constructor(private val dao: TrainerDao) : LetterStore {
    override fun observeAll(): Flow<List<WeeklyLetter>> = dao.observeLetters().map { rows -> rows.mapNotNull { it.toLetter() } }
    override suspend fun of(weekMonday: Long): WeeklyLetter? = dao.letterOf(weekMonday)?.toLetter()
    override suspend fun add(letter: WeeklyLetter): Long = dao.insertLetter(letter.toEntity())
    override suspend fun markRead(weekMonday: Long, atMillis: Long) = dao.markLetterRead(weekMonday, atMillis)
}

fun WeeklyLetterEntity.toLetter(): WeeklyLetter? {
    val figures = LetterCodec.read(figures) ?: return null
    val texts = LetterResponse.read(letter) ?: return null
    return WeeklyLetter(id, weekMonday, createdAtMillis, figures, texts, model, bandDataUntil, readAtMillis)
}

fun WeeklyLetter.toEntity() = WeeklyLetterEntity(
    id = 0, weekMonday = weekMonday, createdAtMillis = createdAtMillis, figures = LetterCodec.encode(figures),
    letter = LetterResponse.encode(texts), model = model, bandDataUntil = bandDataUntil, readAtMillis = readAtMillis,
)
```

Bind `@Provides @Singleton fun provideLetterStore(store: RoomLetterStore): LetterStore = store`.

- [ ] **Step 3: Tests.** `RoomLetterStoreTest` with a `Proxy` DAO as `RoomProgrammeStoreWritesTest`: an
  added letter is inserted with id 0 and reads back equal; a row whose JSON is broken is left out.
  `FakeLetterStore` (test): a `MutableStateFlow<List<WeeklyLetter>>`, `add` throws
  `IllegalStateException` on a repeated week. In `HealthRecordStoreTest` (CI) add: a letter added and read
  by week; a second for the same week throws and the first stands; `markRead` sets the time once
  (a second call leaves the first time).
- [ ] **Step 4: Run** `--tests "com.metaself.app.data.letter.*" --tests "com.metaself.app.data.health.HealthRecordStoreTest"`.
- [ ] **Step 5: Commit** `feat: the weekly letters' store (D104)`.

---

### Task 8: The setting — on, and the hour

**Files:** Create `data/letter/LetterSettingsStore.kt`; modify `di/AiModule.kt`; test
`data/letter/DataStoreLetterSettingsStoreTest.kt` (as `DataStoreAboutMeStoreTest`), `data/letter/InMemoryLetterSettingsStore.kt`.

- [ ] **Step 1: Test first** (copy `DataStoreAboutMeStoreTest`'s DataStore setup): defaults are on and 20;
  `setHour(23)` stores 23; `setHour(17)` and `setHour(24)` are clamped to 18 and 23; `setOn(false)` stores
  false; both survive a new store over the same file.
- [ ] **Step 2: The code:**

```kotlin
package com.metaself.app.data.letter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** D99: whether the weekly letter is written, and at which Sunday hour. */
data class LetterSettings(val on: Boolean = true, val hour: Int = DEFAULT_HOUR) {
    companion object {
        const val DEFAULT_HOUR = 20
        val HOURS = 18..23
    }
}

interface LetterSettingsStore {
    val settings: Flow<LetterSettings>
    suspend fun setOn(on: Boolean)
    /** Clamped to [LetterSettings.HOURS]. */
    suspend fun setHour(hour: Int)
}

/** Over the preferences DataStore the AI settings use, so a restore's settings snapshot covers it. */
class DataStoreLetterSettingsStore(private val store: DataStore<Preferences>) : LetterSettingsStore {
    override val settings: Flow<LetterSettings> = store.data.map {
        LetterSettings(on = it[ON] ?: true, hour = (it[HOUR] ?: LetterSettings.DEFAULT_HOUR).coerceIn(LetterSettings.HOURS))
    }

    override suspend fun setOn(on: Boolean) {
        store.edit { it[ON] = on }
    }

    override suspend fun setHour(hour: Int) {
        store.edit { it[HOUR] = hour.coerceIn(LetterSettings.HOURS) }
    }

    private companion object {
        val ON = booleanPreferencesKey("weekly_letter_on")
        val HOUR = intPreferencesKey("weekly_letter_hour")
    }
}
```

Bind in `AiModule`: `@Provides @Singleton fun provideLetterSettingsStore(store: DataStore<Preferences>): LetterSettingsStore = DataStoreLetterSettingsStore(store)`.
`InMemoryLetterSettingsStore` (test): a `MutableStateFlow(LetterSettings())`.

- [ ] **Step 3: Run; Step 4: Commit** `feat: the weekly letter's setting — on, and the hour (D99)`.

---

### Task 9: The backup, format 10

**Files:** Modify `domain/backup/Backup.kt`, `data/backup/BackupRepository.kt`, `ui/settings/BackupWording.kt`;
tests `domain/backup/BackupCodecTest.kt`, `data/backup/BackupRestoreOrderTest.kt`,
`domain/backup/BackupWordingTest.kt`, `data/backup/BackupRoundTripTest.kt` (CI).

- [ ] **Step 1: The file's shape.** In `Backup`:

```kotlin
    /** D104: the weekly letters, each as stored. Empty in a file from before version 10. */
    @SerialName("weekly_letters") val weeklyLetters: List<BackupWeeklyLetter> = emptyList(),
```

Raise `CURRENT_VERSION` to **10** and add to its KDoc: "Version 10 adds the weekly letters (D104),
[weeklyLetters], and on [ai] the weekly letter's setting. A version 1–9 file has neither; restoring one
leaves no letters — a restore replaces — and the phone's setting as it is." **[D105]** keep D105's
version-9 paragraph above it. The row:

```kotlin
/** One weekly letter (D104); [figures] and [letter] are the stored JSON, kept as text. */
@Serializable
data class BackupWeeklyLetter(
    @SerialName("week_monday") val weekMonday: Long,
    @SerialName("created_at") val createdAtMillis: Long,
    val figures: String,
    val letter: String,
    val model: String,
    @SerialName("band_data_until") val bandDataUntil: Long? = null,
    @SerialName("read_at") val readAtMillis: Long? = null,
)
```

On `BackupAi` add `@SerialName("weekly_letter") val weeklyLetter: Boolean? = null,
@SerialName("weekly_letter_hour") val weeklyLetterHour: Int? = null` (defaults, so older files read).

- [ ] **Step 2: Export and restore** in `BackupRepository`, following the programmes' lines exactly:
  - constructor: add, **last**, `private val letterSettings: LetterSettingsStore` (update the four test
    call sites — `BackupRoundTripTest`, `BackupRestoreOrderTest` ×2, `SettingsViewModelTest` — passing
    `InMemoryLetterSettingsStore()`);
  - export: `weeklyLetters = trainer.allLetters().map { it.toBackup() }` and `ai = BackupAi(…,
    weeklyLetter = letter.on, weeklyLetterHour = letter.hour)` where `val letter = letterSettings.settings.first()`;
  - restore, inside the transaction: `trainer.deleteLetters()` beside `trainer.deleteProgrammes()`, then
    `trainer.insertLetters(prepared.letters)` after `insertProgrammes`; `Prepared` gains
    `val letters: List<WeeklyLetterEntity>` = `backup.weeklyLetters.distinctBy { it.weekMonday }.map { it.toEntity() }`
    (id 0, so they are numbered afresh);
  - `restoreSettings`: inside `prepared.ai?.let { … }` add
    `it.weeklyLetter?.let { on -> letterSettings.setOn(on) }; it.weeklyLetterHour?.let { h -> letterSettings.setHour(h) }`;
  - `RestoreResult` gains `val weeklyLetters: Int = 0` (from the file: distinct weeks; from the phone:
    `trainer.allLetters().size`).
  - After a restore the job must be re-scheduled; that happens on the next app start (design question 4) —
    nothing here.
- [ ] **Step 3: Wording.** `BackupWording.record` names "1 weekly letter" / "N weekly letters" when either
  side has any (as `weeklyPlans`), last in the list.
- [ ] **Step 4: Tests.** Codec: a version-9 file (hand-written JSON string, as the version-8 test) reads with
  no letters and `ai.weeklyLetter == null`; a round trip of `Backup` with one letter. Restore order: the
  expected call list gains `"trainer.deleteLetters"` after `"trainer.deleteProgrammes"` and
  `"trainer.insertLetters"` after `"trainer.insertProgrammes"`; a file with the same week twice writes one
  row. Wording: "…, 1 weekly plan and 2 weekly letters" (invented counts). Round trip (CI): a letter
  exported and restored equals itself but for its id; a version-9 file leaves none.
- [ ] **Step 5: Run** `--tests "com.metaself.app.domain.backup.*" --tests "com.metaself.app.data.backup.*" --tests "com.metaself.app.ui.screen.settings.*"`.
- [ ] **Step 6: Commit** `feat: weekly letters ride in the backup, format 10 (D104)`.

---

### Task 10: Band data in the background

**Files:** Modify `data/health/HealthPorts.kt`, `data/health/HealthConnectReader.kt`,
`data/health/HealthRecordSync.kt`, `data/health/HealthPermissions.kt`, `di/DataModule.kt` (or wherever
`HealthSource` is bound), `AndroidManifest.xml`; test `data/health/HealthRecordSyncTest.kt`.

- [ ] **Step 1: Test first** (in `HealthRecordSyncTest`, with its existing fakes): with the app **not** in
  the foreground (`AppForeground` whose value is false), `copyNow()` reads nothing (existing behaviour) but
  `copyInBackground()` runs a pass — the fake source's `changes` is called — and returns true; a pass the
  source refuses with a `BackgroundReadRefused` returns false and logs nothing.
- [ ] **Step 2: The ports** (`HealthPorts.kt`):

```kotlin
/** Health Connect's background-read permission (D99): whether this phone offers it, and whether it is held. */
interface BackgroundHealthRead {
    /** Never throws; false on anything that goes wrong. */
    suspend fun offered(): Boolean
    suspend fun granted(): Boolean

    companion object {
        val NONE = object : BackgroundHealthRead {
            override suspend fun offered() = false
            override suspend fun granted() = false
        }
    }
}

/** One copy pass without the foreground gate (D99), for a caller that has checked [BackgroundHealthRead.granted]. */
fun interface BackgroundHealthCopy {
    /** True when the pass ran to the end; false when it was refused or failed (already logged by kind). */
    suspend fun copyInBackground(): Boolean
}
```

- [ ] **Step 3: The reader** (`HealthConnectReader` implements `BackgroundHealthRead` too), as
  `historyAvailable`/`historyGranted`:

```kotlin
    override suspend fun offered(): Boolean = withContext(Dispatchers.IO) {
        try {
            client?.features?.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            false
        }
    }

    override suspend fun granted(): Boolean = withContext(Dispatchers.IO) {
        offered() && try {
            HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in (client?.permissionController?.getGrantedPermissions() ?: emptySet())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            false
        }
    }
```

`HealthPermissions` gains `val BACKGROUND: String get() = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND`.
Manifest: `<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND" />`
beside `READ_HEALTH_DATA_HISTORY`, with a one-line comment "D99: the weekly letter copies Sunday's band
data before writing." Bind `BackgroundHealthRead` to the reader where `HealthSource` is bound.

- [ ] **Step 4: The pass** (`HealthRecordSync` implements `BackgroundHealthCopy`):

```kotlin
    /**
     * D99: one pass with no foreground gate — for the weekly letter, whose caller has checked that the
     * background-read permission is held. Shares [running] with [copyNow], so the two never overlap.
     */
    override suspend fun copyInBackground(): Boolean {
        if (!running.tryLock()) return false
        return try {
            val refused = pass()
            if (refused) oweRecheck()
            !refused
        } finally {
            running.unlock()
        }
    }
```

`pass()` already logs a failure and returns false for it; a failure therefore returns true from `!refused`
— tighten by having the logged-failure branch return a distinct result: change `pass()`'s return type to an
enum `PassEnd { DONE, REFUSED, FAILED }` inside the class and map `copyNow`'s use (`REFUSED` → `oweRecheck`)
accordingly; `copyInBackground()` returns `end == PassEnd.DONE`. Bind `BackgroundHealthCopy` to the
`HealthRecordSync` singleton.

- [ ] **Step 5: Run** `--tests "com.metaself.app.data.health.*"`; **Step 6: Commit**
  `feat: band data can be copied in the background, when allowed (D99)`.

---

### Task 11: When — the next Sunday, and the deadline — pure

**Files:** Create `domain/letter/LetterSchedule.kt`; test `domain/letter/LetterScheduleTest.kt`.

- [ ] **Step 1: Test first.**

```kotlin
package com.metaself.app.domain.letter

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/** Design questions 4–6. Dates invented (September 2026: the 6th is a Sunday). */
class LetterScheduleTest {

    private val sunday = LocalDateTime.of(2026, 9, 6, 0, 0)

    @Test
    fun `the next run is this Sunday at the hour, or next Sunday once it has passed`() {
        assertThat(LetterSchedule.nextRun(LocalDateTime.of(2026, 9, 3, 12, 0), 20)).isEqualTo(sunday.withHour(20))
        assertThat(LetterSchedule.nextRun(sunday.withHour(19), 20)).isEqualTo(sunday.withHour(20))
        assertThat(LetterSchedule.nextRun(sunday.withHour(20), 20)).isEqualTo(sunday.plusDays(7).withHour(20))
    }

    @Test
    fun `the week written is Sunday's, from the hour until Monday noon, and none otherwise`() {
        val monday = java.time.LocalDate.of(2026, 8, 31).toEpochDay()
        assertThat(LetterSchedule.weekToWrite(sunday.withHour(20), 20)).isEqualTo(monday)
        assertThat(LetterSchedule.weekToWrite(sunday.plusDays(1).withHour(11), 20)).isEqualTo(monday)
        assertThat(LetterSchedule.weekToWrite(sunday.withHour(19), 20)).isNull()
        assertThat(LetterSchedule.weekToWrite(sunday.plusDays(2).withHour(9), 20)).isNull()
    }

    @Test
    fun `a failure may be retried until Monday noon after the letter's Sunday`() {
        val monday = java.time.LocalDate.of(2026, 8, 31).toEpochDay()
        assertThat(LetterSchedule.mayRetry(monday, sunday.plusDays(1).withHour(11))).isTrue()
        assertThat(LetterSchedule.mayRetry(monday, sunday.plusDays(1).withHour(12))).isFalse()
    }
}
```

- [ ] **Step 2: The code:**

```kotlin
package com.metaself.app.domain.letter

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** When the weekly letter runs, and which week it writes (D99; design questions 4–6). Pure. */
object LetterSchedule {

    private const val DEADLINE_HOUR = 12

    /** The first Sunday at [hour] strictly after [now]. */
    fun nextRun(now: LocalDateTime, hour: Int): LocalDateTime {
        val thisSunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(hour, 0)
        return if (thisSunday.isAfter(now)) thisSunday else thisSunday.plusWeeks(1)
    }

    /** The Monday of the week to write at [now], or null outside Sunday [hour]..Monday noon. */
    fun weekToWrite(now: LocalDateTime, hour: Int): Long? {
        val date = now.toLocalDate()
        val sunday: LocalDate = when {
            date.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= hour -> date
            date.dayOfWeek == DayOfWeek.MONDAY && now.hour < DEADLINE_HOUR -> date.minusDays(1)
            else -> return null
        }
        return sunday.minusDays(6).toEpochDay()
    }

    /** Whether a retryable failure for [weekMonday]'s letter may still be retried at [now]. */
    fun mayRetry(weekMonday: Long, now: LocalDateTime): Boolean =
        now.isBefore(LocalDate.ofEpochDay(weekMonday).plusDays(7).atTime(DEADLINE_HOUR, 0))
}
```

- [ ] **Step 3: Run; Step 4: Commit** `feat: when the weekly letter runs, and which week it writes (D99)`.

---

### Task 12: Writing a letter — the job's logic

**Files:** Create `data/letter/WriteWeeklyLetter.kt`; test `data/letter/WriteWeeklyLetterTest.kt`.

- [ ] **Step 1: Test first** (JUnit 5, fakes: `FakeFoodTotals`, `InMemoryWeightRepository`,
  `FakeMovementRecord`, `FakeTrainerStore` (reviews), `FakeProgrammeStore`, `FakeProfileRepository(aProfile())`,
  `InMemoryAboutMeStore`, `FakeLetterWriter`, `FakeLetterStore`, a counting `BackgroundHealthCopy`, a
  `BackgroundHealthRead` returning a settable `granted`, a `HealthRecordStatus` fake with
  `lastCopiedMillis`, fixed `Now`, `CurrentYear`):
  1. a quiet week → `Outcome.Quiet`, nothing asked, nothing stored;
  2. a week that already has a letter → `Outcome.AlreadyWritten`, nothing asked, no copy;
  3. with the permission → the copy runs before the ask; the stored letter's `bandDataUntil` is null;
  4. without it → no copy; `bandDataUntil` = the record's last copy time;
  5. a written reply → stored with the figures (this week + 4 earlier, target = `CurrentTarget`'s kcal)
     and `Outcome.Written(letter)`;
  6. the request carries no session words (a reviewed session's words are absent), last week's letter's
     `nextWeek` as `lastNextWeek`, and the food only as `WeekFigures`;
  7. `NoKey`, `Refused`, `CeilingReached` → `Outcome.GiveUp(failure)`; `Unreachable`, `Unreadable` →
     `Outcome.Retry(failure)`; nothing stored in either case.
- [ ] **Step 2: The code:**

```kotlin
package com.metaself.app.data.letter

import com.metaself.app.data.health.BackgroundHealthCopy
import com.metaself.app.data.health.BackgroundHealthRead
import com.metaself.app.data.health.HealthRecordStatus
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.trainer.AboutMeStore
import com.metaself.app.data.trainer.ProgrammeStore
import com.metaself.app.data.trainer.TrainerReviews
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.letter.LetterFigures
import com.metaself.app.domain.letter.LetterReply
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.LetterWriter
import com.metaself.app.domain.letter.PlanWeekFigures
import com.metaself.app.domain.letter.WeekFigures
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.domain.profile.ageYears
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.trainer.BodyFacts
import com.metaself.app.domain.trainer.GoalFacts
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.Programmes
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.weight.WeightTrend
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * One weekly letter (D99–D102), from the record: copy the band's data if allowed, count the week and the
 * four before it, ask once, store. Used by the worker (Sunday) and by Write it now (a tap). Never throws
 * for a failed ask — it says what happened; a failed write (the store) throws, and the caller logs it.
 */
class WriteWeeklyLetter @Inject constructor(
    private val food: FoodTotals,
    private val weights: WeightRepository,
    private val record: MovementRecord,
    private val reviews: TrainerReviews,
    private val programmes: ProgrammeStore,
    private val profiles: ProfileRepository,
    private val aboutMe: AboutMeStore,
    private val writer: LetterWriter,
    private val letters: LetterStore,
    private val backgroundRead: BackgroundHealthRead,
    private val backgroundCopy: BackgroundHealthCopy,
    private val status: HealthRecordStatus,
    private val now: Now,
    private val year: CurrentYear,
) {
    sealed interface Outcome {
        data object Quiet : Outcome
        data object AlreadyWritten : Outcome
        data class Written(val letter: WeeklyLetter) : Outcome
        /** A failure worth trying again later (network, provider, an unreadable answer). */
        data class Retry(val failure: EstimateResult) : Outcome
        /** A failure no retry mends: no key, a refusal, the day's ceiling. */
        data class GiveUp(val failure: EstimateResult) : Outcome
    }

    /** [copy] false for Write it now: the app is in front and its own copy runs anyway (design question 19). */
    suspend operator fun invoke(weekMonday: Long, copy: Boolean = true): Outcome {
        if (letters.of(weekMonday) != null) return Outcome.AlreadyWritten
        val copied = copy && backgroundRead.granted() && backgroundCopy.copyInBackground()
        val bandDataUntil = if (copied) null else status.current().lastCopiedMillis

        val sunday = weekMonday + 6
        val first = weekMonday - 7L * LetterFigures.WEEKS_BEFORE
        val foodByDay = food.byDay(first, sunday)
        val trend = WeightTrend.of(weights.readings.first())
        val workouts = record.observeWorkouts(first, sunday).first()
        val days = record.observeDays(first, sunday).first()
        val allReviews = reviews.observeReviews().first()
        val kept = programmes.all().filter { it.status != ProgrammeStatus.OFFERED && it.startEpochDay != null }

        fun week(monday: Long) = WeekFigures.of(monday, foodByDay, trend, workouts, allReviews, days, planWeek(kept, workouts, monday))
        val figuresWeek = week(weekMonday)
        if (figuresWeek.quiet) return Outcome.Quiet

        val profile = profiles.profile.first()
        val target = profile?.let {
            CurrentTarget.of(it, profiles.revision.first(), year(), profiles.burnAdjustmentKcal.first()).kcal
        }
        val figures = LetterFigures(figuresWeek, (1..LetterFigures.WEEKS_BEFORE).map { week(weekMonday - 7L * it) }, target)
        val running = runningIn(kept, weekMonday)
        val request = LetterRequest(
            figures = figures,
            sessions = workouts
                .filter { !it.hidden && it.counted && it.epochDay in weekMonday..sunday }
                .sortedBy { it.startedAtMillis }
                .map { TrainerRequest.session(it, review = null, plan = null) },
            aboutMe = aboutMe.note.first().trim().ifEmpty { null },
            goal = profile?.let { GoalFacts(it.goal.direction, it.goal.kgPerWeek) },
            body = profile?.let { BodyFacts(it.ageYears(year()), it.sex, it.heightCm) },
            planTitle = running?.plan?.title,
            planWeek = running?.let { p -> p.plan.weeks.getOrNull(ProgrammeCalendar.weekIndex(p.startEpochDay!!, weekMonday)) },
            lastNextWeek = letters.of(weekMonday - 7)?.texts?.nextWeek,
        )
        return when (val reply = writer.write(request)) {
            is LetterReply.Failed -> when (reply.failure) {
                is EstimateResult.NoKey, is EstimateResult.Refused, is EstimateResult.CeilingReached -> Outcome.GiveUp(reply.failure)
                else -> Outcome.Retry(reply.failure)
            }
            is LetterReply.Written -> {
                val letter = WeeklyLetter(
                    weekMonday = weekMonday, createdAtMillis = now(), figures = figures, texts = reply.texts,
                    model = reply.model, bandDataUntil = bandDataUntil,
                )
                letters.add(letter)
                Outcome.Written(letter)
            }
        }
    }

    /** Design question 13: the kept plan counted during [monday]'s week, the latest-started. */
    private fun runningIn(kept: List<Programme>, monday: Long): Programme? = kept
        .filter { p ->
            val start = p.startEpochDay!!
            start <= monday + 6 && Programmes.countedUntil(p, monday + 6) >= monday
        }
        .maxByOrNull { it.startEpochDay!! }

    private fun planWeek(kept: List<Programme>, workouts: List<com.metaself.app.domain.movement.Workout>, monday: Long): PlanWeekFigures? {
        val p = runningIn(kept, monday) ?: return null
        val start = p.startEpochDay!!
        val index = ProgrammeCalendar.weekIndex(start, monday)
        // [D105] PlanProgress's "done" must count only ticked (confirmed or full) sessions.
        val week = PlanProgress.of(p.plan, start, workouts, Programmes.countedUntil(p, monday + 6)).weeks.getOrNull(index)
            ?: return null
        return PlanWeekFigures(p.plan.title, week.planned, week.done, ProgrammeCalendar.ended(start, p.ask.weeks, monday + 6))
    }
}
```

Check against the real code: `TrainerRequest.session` must be made `internal` (Task 4) and its parameter
names (`workout, review, plan`); `Profile` field names (`goal`, `sex`, `heightCm`); `ageYears` import;
`HealthRecordStatus` is the port `RoomHealthRecordStatus` implements (`current(): HealthRecordState`);
`Programmes.countedUntil(programme, today)`'s meaning (min of stop day, today, last day) — here "today" is
the week's Sunday; `ProgrammeCalendar.weekIndex` may be negative for a week before the start — guarded by
`getOrNull`.

- [ ] **Step 3: Run** `--tests "com.metaself.app.data.letter.*"`; **Step 4: Commit**
  `feat: a weekly letter written from the record (D99–D102)`.

---

### Task 13: WorkManager glue — the worker and the scheduler

**Files:** Create `data/letter/WeeklyLetterWorker.kt`, `data/letter/WeeklyLetterScheduler.kt`,
`data/letter/LetterNotifications.kt` (Task 14 fills its bodies; here only the calls), modify `MetaSelfApp.kt`
(schedule on start). Test: `data/letter/LetterRunTest.kt` (the decision logic, pure).

- [ ] **Step 1: The decision, pure and tested first** (`data/letter/LetterRun.kt`):

```kotlin
package com.metaself.app.data.letter

import com.metaself.app.domain.letter.LetterSchedule
import java.time.LocalDateTime

/** What the worker does with an outcome (design question 5). Pure. */
enum class LetterStep { DONE, RETRY, NOTIFY_FAILED }

object LetterRun {
    fun after(outcome: WriteWeeklyLetter.Outcome, weekMonday: Long, now: LocalDateTime): LetterStep = when (outcome) {
        is WriteWeeklyLetter.Outcome.Written, WriteWeeklyLetter.Outcome.Quiet, WriteWeeklyLetter.Outcome.AlreadyWritten -> LetterStep.DONE
        is WriteWeeklyLetter.Outcome.GiveUp -> LetterStep.NOTIFY_FAILED
        is WriteWeeklyLetter.Outcome.Retry -> if (LetterSchedule.mayRetry(weekMonday, now)) LetterStep.RETRY else LetterStep.NOTIFY_FAILED
    }
}
```

Test each branch (Retry before and at Monday noon).

- [ ] **Step 2: The worker:**

```kotlin
package com.metaself.app.data.letter

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.metaself.app.data.diagnostics.ProblemLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * Sunday's letter (D99). Everything it decides is [WriteWeeklyLetter]'s and [LetterRun]'s; this only
 * runs them and tells WorkManager. It schedules the next Sunday whenever it does not retry.
 */
@HiltWorker
class WeeklyLetterWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val write: WriteWeeklyLetter,
    private val settings: LetterSettingsStore,
    private val scheduler: WeeklyLetterScheduler,
    private val problems: ProblemLog,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        val chosen = settings.settings.first()
        if (!chosen.on) return Result.success()
        val week = LetterSchedule.weekToWrite(now, chosen.hour)
        if (week == null) {
            scheduler.schedule()
            return Result.success()
        }
        val step = try {
            val outcome = write(week)
            if (outcome is WriteWeeklyLetter.Outcome.Written) LetterNotifications.arrived(applicationContext, outcome.letter)
            LetterRun.after(outcome, week, now)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // By kind only (D8): the figures and the note never go in the log.
            problems.record("letter", "not written: ${failure::class.java.simpleName}")
            if (LetterSchedule.mayRetry(week, now)) LetterStep.RETRY else LetterStep.NOTIFY_FAILED
        }
        return when (step) {
            LetterStep.RETRY -> Result.retry()
            LetterStep.NOTIFY_FAILED -> {
                LetterNotifications.failed(applicationContext)
                scheduler.schedule()
                Result.success()
            }
            LetterStep.DONE -> {
                scheduler.schedule()
                Result.success()
            }
        }
    }
}
```

Imports as above plus `com.metaself.app.domain.letter.LetterSchedule`; an `AlreadyWritten` or `Quiet` week
posts nothing.

- [ ] **Step 3: The scheduler:**

```kotlin
package com.metaself.app.data.letter

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.metaself.app.domain.letter.LetterSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps exactly one Sunday run queued (design question 4); cancels it when the letter is switched off. */
@Singleton
class WeeklyLetterScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: LetterSettingsStore,
) {
    /**
     * Queues the next Sunday's run under a name by that Sunday's week parity (`REPLACE`). Consecutive
     * Sundays differ in parity, so a running worker queueing its successor, or the app starting while a
     * Sunday run is under way, never replaces the run in progress; a later call for the same Sunday
     * replaces the one queued. Switched off, both are cancelled. At worst a stale run for this Sunday
     * survives an hour change and writes this week's letter once — the week's uniqueness (design
     * question 7) keeps it to one letter.
     */
    suspend fun schedule(now: LocalDateTime = LocalDateTime.now()) {
        val manager = WorkManager.getInstance(context)
        val chosen = settings.settings.first()
        if (!chosen.on) {
            NAMES.forEach(manager::cancelUniqueWork)
            return
        }
        val at = LetterSchedule.nextRun(now, chosen.hour)
        val delay = Duration.between(now.atZone(ZoneId.systemDefault()), at.atZone(ZoneId.systemDefault()))
        val request = OneTimeWorkRequestBuilder<WeeklyLetterWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        val parity = Math.floorMod(MovementWeek.weekOf(at.toLocalDate().toEpochDay()), 2L).toInt()
        manager.enqueueUniqueWork(NAMES[parity], ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        val NAMES = listOf("weekly-letter-even", "weekly-letter-odd")
    }
}
```

(Import `com.metaself.app.domain.movement.MovementWeek`.) A retry keeps its own work (WorkManager retries
the same request), and its successor is queued under the other parity.

- [ ] **Step 4: Schedule on start.** In `MetaSelfApp.onCreate`, after the crash handler, launch in the
  `@ApplicationScope` scope (inject it): `scope.launch { runCatching { letterScheduler.schedule() }.onFailure { problems.record("letter", "not scheduled: ${it::class.java.simpleName}") } }`.
- [ ] **Step 5: Build** `:app:assembleDebug` (Hilt must generate the worker's factory) and run
  `--tests "com.metaself.app.data.letter.*"`.
- [ ] **Step 6: Commit** `feat: the weekly letter runs on Sunday, and retries until Monday noon (D99)`.

---

### Task 14: Notifications and the tap

**Files:** Create `data/letter/LetterNotifications.kt`, `data/letter/OpenedLetters.kt`,
`ui/nav/OpenedLettersViewModel.kt`; modify `MainActivity.kt`; test `data/letter/OpenedLettersTest.kt`,
`MainActivityIntentTest`-style pure test of the extraction (as `sharedWorkoutFile`).

- [ ] **Step 1: The holder and the extraction, tested first:**

```kotlin
package com.metaself.app.data.letter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What a notification's tap asked to open (design question 16), until the navigation takes it. */
sealed interface LetterOpen {
    data class Letter(val weekMonday: Long) : LetterOpen
    data object List : LetterOpen
}

@Singleton
class OpenedLetters @Inject constructor() {
    private val held = MutableStateFlow<LetterOpen?>(null)
    val pending: StateFlow<LetterOpen?> = held.asStateFlow()

    fun offer(open: LetterOpen) {
        held.value = open
    }

    fun take(): LetterOpen? = held.getAndSet(null)

    private fun <T> MutableStateFlow<T>.getAndSet(value: T): T {
        while (true) {
            val now = this.value
            if (compareAndSet(now, value)) return now
        }
    }
}

/** The letter a launch asks to open: the notification's extras (D103). Null for any other launch. */
internal fun letterOpen(weekMonday: Long?, list: Boolean): LetterOpen? = when {
    weekMonday != null -> LetterOpen.Letter(weekMonday)
    list -> LetterOpen.List
    else -> null
}
```

In `MainActivity`: inject `OpenedLetters`; in `onCreate` (same `savedInstanceState == null` guard), unless
the intent was `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`, read
`intent.getLongExtra(LetterNotifications.EXTRA_WEEK, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }` and
`intent.getBooleanExtra(LetterNotifications.EXTRA_LIST, false)`, and `letterOpen(...)?.let(openedLetters::offer)`.

- [ ] **Step 2: The notifications** — `LetterNotifications` in the pattern of `Notifications`:
  `CHANNEL_ID = "weekly_letter"`, channel name from `LetterWording.CHANNEL` ("Weekly letter"), ids 2 and 3,
  `EXTRA_WEEK = "letter_week"`, `EXTRA_LIST = "letters"`, `fun arrived(context, letter: WeeklyLetter)`
  (title `LetterWording.ARRIVED_TITLE` = "Your week is in", text = the headline, intent with `EXTRA_WEEK`),
  `fun failed(context)` (title "Your weekly letter couldn't be written", text "Tap to try again.", intent
  with `EXTRA_LIST`). Distinct `PendingIntent` request codes (2 and 3). `runCatching { manager.notify(...) }`
  as the reminder does. Small icon: `R.drawable.ic_stat_reminder`.
- [ ] **Step 3: The nav's view model** — `OpenedLettersViewModel(opened: OpenedLetters)` exposing `pending`
  and `take()`, like `SharedFileViewModel`.
- [ ] **Step 4: Run** `--tests "com.metaself.app.data.letter.*"`; **Step 5: Commit**
  `feat: the weekly letter's notifications and what their tap opens (D103)`.

---

### Task 15: Wording

**Files:** Create `ui/letter/LetterWording.kt`; add strings to `app/src/main/res/values/strings.xml`; test
`ui/letter/LetterWordingTest.kt`.

- [ ] **Step 1: Test first**, then the object. Sentences (all fixed here; numbers formatted with
  `Locale.US` grouping as `TrainerWording.number` does):
  - `CHANNEL = "Weekly letter"`, `ARRIVED_TITLE = "Your week is in"`,
    `FAILED_TITLE = "Your weekly letter couldn't be written"`, `FAILED_TEXT = "Tap to try again."`;
  - `FROM_TRAINER = "From the AI trainer · advice, not a measurement. The figures were counted on your phone."`;
  - headings `WHAT YOU PUT IN`, `WHERE IT'S TAKING YOU`, `ONE THING TO LOOK AT`, `FOR NEXT WEEK`;
  - `weekLabel(monday)` → "WEEKLY LETTER · 31 AUG – 6 SEP" (invented; day month, upper-case, across months);
  - `dayNote(headline)` → "Your week is in: <headline>"; action "Read it";
  - box rows `rows(figures): List<Row(name, thisWeek, average)>`: Calories a day ("2,100" / "—"), Target,
    Protein a day ("110 g"), Days logged ("5 of 7" / average "4.5"), Weight trend ("−0.3 kg", "+0.2 kg",
    "—"; the minus is U+2212), Sessions ("3" / "2.5"), Distance ("12 km", one decimal under 10 km:
    "4.5 km"), Steps a day ("8,000"), and Weekly plan ("2 of 3" / "—") only when this week has a plan;
  - `foodNote(daysLogged)` → "Calories and protein are averages over the 5 days you logged." (1 day: "the 1
    day"); `bandNote(untilMillis, zone)` → "Band data up to Sun 14:00." (invented);
  - settings: `SETTING_TITLE = "Weekly letter"`, `settingLine(on, hour)` → "Sundays at 20:00" / "Off";
    `ASK_BACKGROUND = "Allow Sunday's band data"`, `BACKGROUND_REASON = "So Sunday's letter includes sessions from today."`;
  - list: `LIST_TITLE = "Weekly letters"`, `WRITE_NOW = "Write it now"`, `EMPTY = "Your first letter comes on Sunday evening."`,
    `privacyLetter(ceiling)` → "Sends to the AI provider, with your key: your week's food as averages a day,
    your weight trend, your sessions and steps, your note about yourself, your goal, age, sex and height.
    Never a meal or a food. One of today's <ceiling> AI requests."
- [ ] **Step 2: Run; Step 3: Commit** `feat: the weekly letter's wording (D102, D103)`.

---

### Task 16: The letter page and Weekly letters

**Files:** Create `ui/screen/letter/LetterScreen.kt`, `LetterViewModel.kt`, `LettersScreen.kt`,
`LettersViewModel.kt`; tests `ui/screen/letter/LetterViewModelTest.kt`, `LettersViewModelTest.kt` (JUnit 5),
`LetterScreenRenderTest.kt`, `LettersScreenRenderTest.kt` (JUnit 4 + Robolectric, `ComposeRender`).

- [ ] **Step 1: View models, tested first.**
  - `LetterViewModel(savedState: weekMonday arg, store: LetterStore, now: Now, problems)`: state
    `Loading | Shown(letter) | Missing | Unreadable`; on first `Shown`, `guarded { store.markRead(week, now()) }`
    once (tests: marked once; a failing store shows the letter anyway and logs by kind).
  - `LettersViewModel(store, write: WriteWeeklyLetter, today: Today, clock: () -> LocalDateTime,
    settings: LetterSettingsStore, ai: AiSettingsStore, problems, @ApplicationScope outliving)`: state = the
    letters (week label + headline + read flag), `canWriteNow` = the week `LetterSchedule.weekToWrite(now,
    hour) ?: the Monday of last complete week` has no letter; `writeNow()` guarded by a synchronous
    `writing` flag, runs `write(week, copy = false)` via `outlived`; shows `Written` (the list updates by
    flow), `Quiet` ("Nothing was logged that week, so there is nothing to write." — add to wording),
    `Retry/GiveUp` → `TrainerWording.failure(failure)`. Tests: double tap asks once; quiet says so; a failure
    is worded; nothing is asked from `init`.
- [ ] **Step 2: Screens.** `LetterScreen`: `MetaSelfScreen(title = "Weekly letter")`, then the week label,
  the headline (`titleLarge`), `FROM_TRAINER` (label, **before** anything the model wrote), the four parts
  under their headings, the close, then the box (a `Column` of three-cell rows: name / this week / 4-week
  avg, header row "THE WEEK · THIS WEEK · 4-WEEK AVG"), then `foodNote` and `bandNote` when there is one.
  `LettersScreen`: `MetaSelfScreen(title = "Weekly letters")`, **Write it now** (when `canWriteNow`) with
  `privacyLetter` under it and any failure sentence, then one row per letter (label, headline, "New" when
  unread; row height ≥ 48 dp; `clickable(role = Role.Button)`), or `EMPTY`. Branch with `when`/`if` only.
- [ ] **Step 3: Render tests** (JUnit 4): the letter's `FROM_TRAINER` is drawn before its headline's first
  part heading (`isDrawnBefore`); the box shows "5 of 7" and "—" (invented figures); the band note shows only
  when `bandDataUntil` is set; the list shows Write it now and the privacy line; an unread letter shows
  "New".
- [ ] **Step 4: Run** `--tests "com.metaself.app.ui.screen.letter.*"`; **Step 5: Commit**
  `feat: the weekly letter's page and the list of letters (D103)`.

---

### Task 17: The day's note, the setting, and the doors

**Files:** Modify `ui/screen/day/DayScreen.kt`, `ui/screen/day/DayPager.kt`, `ui/nav/MetaSelfNavHost.kt`,
`ui/nav/SettingsDestination.kt`, `ui/screen/settings/AiSettingsPage.kt`, `ui/screen/trainer/TrainerScreen.kt`;
create `ui/screen/letter/UnreadLetterViewModel.kt`, `ui/screen/letter/LetterSettingsSection.kt`,
`LetterSettingsViewModel.kt`; tests: `DayScreen` render test (existing class) for the note,
`LetterSettingsViewModelTest.kt`, `TrainerScreenRenderTest` (the new row), the nav compiles.

- [ ] **Step 1: The day's note.** `DayScreenContent` gains, after `onOpenMovement`, two defaulted params:
  `weeklyLetter: String? = null` (the unread letter's headline) and `onOpenWeeklyLetter: () -> Unit = {}`;
  right after the goal/milestone note: `if (state.isToday && weeklyLetter != null) MarginNote(text =
  LetterWording.dayNote(weeklyLetter), dismissLabel = LetterWording.READ_IT, onDismiss = onOpenWeeklyLetter)`.
  `DayPager` gains the same two params and passes them. `UnreadLetterViewModel(store)`: the newest letter if
  unread (`readAtMillis == null`) and at most 7 days old, else null. The nav host supplies both.
  Render test: the note appears for today with the headline, not for a past day.
- [ ] **Step 2: The setting.** `LetterSettingsViewModel(settings: LetterSettingsStore, scheduler:
  WeeklyLetterScheduler, background: BackgroundHealthRead, problems)`: state (on, hour, `askBackground` =
  offered && !granted, refreshed on `lookedAt()`); `setOn`, `setHour` write then `scheduler.schedule()`,
  both `guarded`. `LetterSettingsSection` (a `@Composable` taking state + callbacks): title, a `Switch` row
  "Weekly letter" with `settingLine`, hour choices 18–23 as `FilterChip`s in a `FlowRow` (only when on), and,
  when `askBackground`, an `OutlinedButton(ASK_BACKGROUND)` with `BACKGROUND_REASON` under it.
  `AiSettingsPage` gains, **after** `modifier`, `weeklyLetter: @Composable () -> Unit = {}` drawn at the page's
  end; `SettingsDestination`'s AI branch passes `{ LetterSettingsSection(...) }` with a
  `rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract())` that
  launches `setOf(HealthPermissions.BACKGROUND)` and calls `lookedAt()` on return; also ask
  `POST_NOTIFICATIONS` on API 33+ when the switch is turned on (as the reminder does). VM tests: toggling
  writes and re-schedules (a fake scheduler interface — extract `interface LetterScheduling { suspend fun
  schedule() }` implemented by `WeeklyLetterScheduler` so the VM is testable); the ask shows only when
  offered and not granted.
- [ ] **Step 3: The doors.** Destinations `Letters("trainer/letters")` and `Letter("trainer/letter/{week}")`
  with `of(week)`; composables wiring the two screens; `TrainerScreen` gains a required `onLetters: () ->
  Unit` (before `modifier`) and a row "Weekly letters" under About me (update `TrainerScreenRenderTest`'s
  positional call with one more `{}`); the day's note navigates to `Letter.of(week)`; the nav host collects
  `OpenedLettersViewModel.pending` like the shared file (`LaunchedEffect`), `take()`s it and navigates to
  `Letter.of(week)` or `Letters`.
- [ ] **Step 4: Run** the full unit suite once (`free -m` first):
  `~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-letter-17.log 2>&1; echo "exit $?"`; expected exit 0,
  skips = the ten.
- [ ] **Step 5: Commit** `feat: the letter on the day, in Settings, and from the Trainer screen (D99, D103)`.

---

### Task 18: The privacy page, version 0.70.0, the suite and the build

**Files:** Modify `privacy.html` (repo root), `app/build.gradle.kts`.

- [ ] **Step 1: Privacy.** In the paragraph that lists what the trainer sends, add a paragraph for the
  weekly letter: it is written once a week, on Sunday evening, **without a tap** while the Weekly letter
  setting is on; it sends the week's food only as averages a day of calories, protein, carbs and fat and the
  number of days logged — never a meal, a food's name, an amount or a time of eating — the weight trend's
  change (never a single weigh-in), the week's sessions and steps (never your words on them), your note
  about yourself, your goal, age, sex and height, today's calorie target, and last week's "for next week"
  line; the same figures for the four weeks before; with your own key; and it can be switched off in
  Settings → AI. Also: with the background permission it copies the band's data first.
- [ ] **Step 2: Version.** `versionCode = 126`, `versionName = "0.70.0"`.
- [ ] **Step 3: The suite, lint, the build** (one at a time, `free -m` before each):

```
~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-letter-18a.log 2>&1; echo "exit $?"
~/bin/gradlew-safe :app:lintDebug > /tmp/ms-letter-18b.log 2>&1; echo "exit $?"
~/bin/gradlew-safe :app:assembleDebug > /tmp/ms-letter-18c.log 2>&1; echo "exit $?"
```

Expected: exit 0 each; skips exactly the ten classes; no new lint issue in a file this branch changed
(a new `MissingPermission`/`WorkerHasAPublicModifier`-style warning is fixed, not suppressed).

- [ ] **Step 4: Commit** `0.70.0: the weekly letter (D99–D104)` (explicit paths).

---

## Self-review against the spec

- D99: Sunday + hour setting + switch (Tasks 8, 11, 13, 17); automatic exception, once a week (13); quiet
  week (2, 12); one per week (6, 7, 12); background copy + permission asked once with the reason (10, 17);
  band-data line (12, 15, 16); retries until Monday noon, failure notification, Write it now (11, 13, 14,
  16); one request against the ceiling (5 — `OpenAiCall` counts).
- D100: food, weight, movement, plan; four earlier weeks; average with "—" (2, 12, 15). Target: design
  question 1 amends the spec (Task 0).
- D101: exactly these parts, pinned (4); no words, meals, foods, weigh-ins (4, 12).
- D102: six texts, strict schema, tone rules verbatim in the instructions, safety line (4).
- D103: own channel (14), notification opens the letter (14, 17), day notice until read (17 — a margin note,
  design question 2), list from the Trainer screen (17), letter page with D4 line first, box, notes (16).
- D104: Room 12, backup 10, settings restored with the AI settings (6, 9).
- **Gaps / doubts:** (1) no stored target history — the box's target is today's (Task 0 amends D100);
  (2) the day notice becomes a margin note (Task 0 amends D103; the owner saw a card); (3) WorkManager
  versions are unverified until Task 1; (4) the worker re-queues itself under the other week-parity name so
  `REPLACE` never cancels the run in progress (Task 13) — confirm against the chosen WorkManager version; (5) everything touching `PlanProgress` and the migration's base version is **[D105]**-dependent;
  (6) the background permission's feature check (`FEATURE_READ_HEALTH_DATA_IN_BACKGROUND`) exists in
  connect-client 1.1.0 — confirm the constant name when compiling Task 10.
