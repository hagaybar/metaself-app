# A workout can be typed in — Implementation Plan (D76–D78, public issue #58)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D76–D78 in `docs/superpowers/specs/2026-09-27-typing-in-a-workout-design.md`. A **"Log a
workout"** button on the Movement screen opens a sheet that types a workout onto the open day; a typed
workout can be changed or deleted by tapping it; its energy becomes the day's third movement reading,
never an addition (D60 made live, D77); the day screen's step line draws its rule from whichever
reading decided the day (public issue #58); and the week reads more tidily (D78: same-kind sessions
combined, pace for runs only, walks counted apart from workouts).

**Architecture:**

```
domain/movement/Workout.kt              + a shared pace helper                              JUnit 5
domain/movement/MetEstimate.kt          + pricedByPace (which the sheet's wording needs)    JUnit 5
domain/movement/WorkoutDraft.kt         pure: the sheet's fields → validity, estimate, pace JUnit 5
ui/movement/WorkoutSheetWording.kt      pure: the estimate and pace lines                   JUnit 5
domain/movement/MovementWeek.kt         D78: walks counted apart                            JUnit 5
ui/movement/MovementWeekWording.kt      D78: combined summary, pace for runs, typed energy  JUnit 5
data/health/TypedWorkouts.kt            port + Room adapter over WorkoutDao + HealthStore   JUnit 5 (fakes); CI pin
data/health/MovementRecord.kt           + Workout.toTypedEntity()                           JUnit 5
di/DataModule.kt                        binds TypedWorkouts
domain/movement/TypedReading.kt         pure: typed workouts → kcal by day; merge           JUnit 5
ui/screen/day/DayViewModel.kt           D77: typedWorkoutsKcal filled, observed             JUnit 5 + coroutines-test
domain/movement/MovementToday.kt        #58: rule and fill follow the deciding reading      JUnit 5
ui/screen/movement/MovementUiState.kt   + WorkoutSheetState
ui/screen/movement/MovementViewModel.kt log / open / change / save / delete / close         JUnit 5 + coroutines-test
ui/screen/movement/WorkoutSheet.kt      the sheet (chrome + content)                        Robolectric (JUnit 4)
ui/screen/movement/MovementScreen.kt    the button; a typed workout's line is a door        Robolectric (JUnit 4)
ui/nav/MetaSelfNavHost.kt               passes the view model's actions
```

**No schema change.** `workouts` already has every column (`source`, `effort`, `energySource`,
`origin`/`originId` nullable, `note`). **One new DAO read, no schema change:** the adapter uses `observeBetween`,
`insert`, `update` and `deleteTyped`, which exist, and `byId`, added so a change or delete is keyed on
the row's id rather than searched for on a day. The day's stored summary
(`health_days`) is refreshed through the store path that already exists, `HealthStore.summarise`, with
`TotalsResult.ALL_FAILED` — which keeps every total already stored from Health Connect, so saving a
workout makes no cross-process call. `DaySummary` already counts every visible workout whatever its
source; a test pins that for typed ones.

**Decision:** the owner's, 2026-09-27 — D76, D77, D78 in the spec above, building the activity spec's
phase 4 (`2026-09-25-physical-activity-module-design.md` §3.3, §4.3, D60, §8 item 4) on the Movement
screen (`2026-09-27-movement-screen-design.md`, D73–D75). Public issue #58 is folded in because typed
workouts make band- and typed-decided days common.

**Tech Stack:** Kotlin, Compose (Material 3 1.2.0 via BOM 2024.02.00), Hilt, Room (no schema change),
kotlinx-coroutines-test, JUnit 5 + Truth, Robolectric (JUnit 4) for render tests. No new dependency.

**Red lines (stop and report if crossed):**

- **No schema change.** `git status app/schemas` stays clean; no entity, column, migration or new DAO
  query.
- **D4:** every stored kcal says where it came from — MET_ESTIMATE, TYPED, or NONE with a null figure.
  Nothing is guessed: no weight, no estimate.
- **D60/D77:** a typed workout's energy is a third candidate for the maximum. It is never added to the
  steps or the band figure. Tests show max-never-sum.
- **A synced workout is never changed or deleted here** (D76; corrections are a later phase).
- **D8:** a failed read or write is logged (kind `"movement"`) and said; nothing throws upwards.
- **Never `git add -A`; never bare `./gradlew`; never pipe a build whose result is reported.**
  Anonymisation: every figure in a test is invented, round, and says so (CLAUDE.md). A note in a
  fixture is "a note" — never a story about a session.

## Design questions the code raised — settled here

The executor follows these; each is repeated in the PR body, one line each, so the owner can overturn it.

1. **Save sits in the sheet's title row, not at its foot.** Material 3 1.2.0's `ModalBottomSheet`
   pads its content for the system bars only (`BottomSheetDefaults.windowInsets` is
   `systemBarsForVisualComponents.only(Vertical)`, read from the 1.2.0 class file on this box), so
   the keyboard can cover the foot of the sheet. A button at the top cannot be covered by something
   rising from the bottom. The fields scroll between the title row and the foot (the meal-naming
   sheet's 0.52.1 shape), and the content also takes `imePadding()` so the lower fields can be
   scrolled clear of the keyboard. Whether that padding is doubled on the phone is a phone check.
2. **No kind is chosen when the sheet opens.** Kind is required (D76); pre-choosing Run would save a
   run for somebody who forgot to tap. Save is disabled until a kind and minutes are in.
3. **What typed values count:** minutes a whole number from 1 to 1,440 (a day's minutes); a distance
   in km greater than 0 and at most 1,000, a comma read as a decimal point, plain digits only (no
   "1e3"), optional; the owner's own kcal a whole number from 0 to 20,000. Both ceilings are sanity
   choices, not measurements. A distance or kcal that is not one of these blocks Save and marks the
   field.
4. **The energy line says what it was estimated from.** `MetEstimate` prices a run, walk or ride that
   has a distance by its speed and ignores the effort, so saying "estimated from the effort" would
   then be untrue. It says "about 332 kcal, estimated from the pace" there, and "…from the effort"
   everywhere else.
5. **With no profile weight there is no estimate**, the line says so ("No estimate: the profile has no
   weight"), and the workout saves with no energy (`NONE`) unless the owner sets it himself.
6. **Where a typed workout starts:** the open day at the clock time now (D76's wording, taken
   literally). Changing a workout keeps its day and start. **Correction (D4, this fix):** a typed
   workout never carries heart-rate figures at all — not merely because a workout logged after it
   ended would have no readings inside its window, but because nothing recorded it: a reading that
   happens to fall inside a typed window is not evidence of that workout's heart rate, so the store
   never computes one for a typed row, whatever readings exist at the time.
7. **Changing a workout estimates again** on today's profile weight (the only weight the sheet has).
8. **Delete is immediate, then Undo is offered** — the app's existing rule for a thing it can rebuild
   (`WeightViewModel.delete`, and the record screen's rows): no question first. Delete closes the
   sheet; a "Deleted · Undo" line (the record screen's `UndoRow`) is pinned under the Movement
   screen's title bar, where the list cannot scroll it away — the bottom edge, where the record
   screen pins it, holds "Log a workout". Undo logs the deleted workout again through
   `TypedWorkouts.log`: the same day, start, kind, figures, energy source, effort, note and hidden
   flag, under a new id (nothing refers to a typed workout's id), with its day's summary and its
   heart-rate figures worked out again in the same transaction. Deletes stack; Undo takes the most
   recent. A failed delete leaves nothing to undo and says "Not deleted; Recent problems says why.";
   a failed Undo keeps it offered and says so (D8). (Undo was added on the controller's instruction of
   2026-09-27, after Task 6's code below was drafted; `MovementViewModel` as built is the record.)
9. **An open day's line for a typed workout shows its energy and its source:** "Weights · 45 min ·
   about 150 kcal, estimated" or "… · 150 kcal, you set this". A synced workout's line is unchanged.
10. **Strength is called "Weights" on the Movement screen**, as `ExerciseNames` and the existing
    wording already call it, although the sheet's chip says "Strength" (the spec's word).
11. **The combined summary:** sessions are grouped by kind, in the order each kind first started, and
    groups are joined by ", ". A group of one keeps today's wording ("Running 6.2 km"); a group of
    more is "Walking ×3 · 2 h 30" — the spec's form. A group is named by the title its sessions share,
    or by its kind when they differ.
12. **A walk is a session of kind WALK**, which includes a synced hike (Health Connect's hiking is
    stored as WALK).
13. **Typed kcal is laid into the days the phone recorded, and today.** A past day with no step
    record is not invented as a day of zero steps (it would enter the usual-day median). Today is the
    exception: before the first step of the morning there is no record, and a workout typed then
    still counts — the usual-day median never reads today, so it cannot move. Today's count still
    says no steps are recorded. Nothing at all is earned when steps cannot be read — there is then no
    usual day to measure it against.
14. **Typed kcal enters the usual-day energy median**, as a band figure does: routine typed
    workouts raise the usual day and stop earning, the rule `ActivityEnergyTest` already states.
15. **The day's stored summary is re-worked in the same database transaction as the write**, with
    `TotalsResult.ALL_FAILED`, so the stored Health Connect totals stand and the save makes no
    cross-process call.
16. **#58:** when the band or a typed workout decided the day, the hairline and its green "reached"
    fill are drawn from that reading's kcal against the usual day's kcal. The count above it stays
    steps.
17. **The button is an extended floating button at the bottom edge**, like the day's add button, so
    it never scrolls away; it is not drawn while the record is unreadable or not yet read.
18. **A typed run's distance does not enter the week's headline distance.** D74's figure is the sum
    of Health Connect's de-duplicated daily totals; adding typed distance would double a run the phone
    also counted. The typed distance shows on its day's line and in its day's summary.
19. **A change or delete finds its row by id** (`WorkoutDao.byId`, a read with no schema change), acts
    only on a TYPED row, and works out the STORED row's day again. When it finds nothing to act on it
    answers false, and the sheet says the write failed and the problem log says why — never silent.
20. **A failed read of typed workouts on the day screen is tried again** every 30 seconds (a choice)
    and logged once as "movement"; it counts as none meanwhile, and no longer turns typed workouts off
    until the next day.

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` and `~/bin/ms-release` share one lock; a
finished build's daemon lingers up to ten minutes. **Before every Gradle command run `free -m`;** under
about 4000 MB available (6000 MB before `ms-release`), wait and check again. Never `gradlew --stop`.
One build at a time. Never pipe the build; read the log afterwards.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-typed.log 2>&1; echo "exit $?"
grep -E "FAILED|tests completed|BUILD" /tmp/ms-typed.log
```

SQLite classes skip locally; report them as skipped. **There are ten:** the eight `CLAUDE.md` names
plus `HealthRecordDaoTest` and `HealthRecordStoreTest` (`CLAUDE.md`'s list is stale; this plan adds
no new skipping class — its one database test goes into `HealthRecordStoreTest`).

---

### Task 1: The sheet's arithmetic

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/movement/Workout.kt`
- Modify: `app/src/main/java/com/metaself/app/domain/movement/MetEstimate.kt`
- Create: `app/src/main/java/com/metaself/app/domain/movement/WorkoutDraft.kt`
- Create: `app/src/main/java/com/metaself/app/ui/movement/WorkoutSheetWording.kt`
- Create: `app/src/test/java/com/metaself/app/domain/movement/Workouts.kt` (fixture)
- Test: `app/src/test/java/com/metaself/app/domain/movement/WorkoutDraftTest.kt`
- Test: `app/src/test/java/com/metaself/app/domain/movement/MetEstimateTest.kt`
- Test: `app/src/test/java/com/metaself/app/ui/movement/WorkoutSheetWordingTest.kt`

- [ ] **Step 0: Branch.** Work on `a-workout-can-be-typed-in` (it exists; this plan is committed on
  it). `git status` must show only the untracked `tools/__pycache__/`.

- [ ] **Step 1: The fixture.** Create `app/src/test/java/com/metaself/app/domain/movement/Workouts.kt`:

```kotlin
package com.metaself.app.domain.movement

import com.metaself.app.domain.day.TEST_EPOCH_DAY

/**
 * A workout the owner typed, for tests that care about one field at a time. Every figure is invented.
 * The default energy is what the sheet would estimate for it: 45 minutes of moderate strength work on
 * `aProfile()`'s 80 kg is (3.5 − 1) × 80 × 0.75 = 150 kcal by [MetEstimate].
 */
fun aTypedWorkout(
    id: Long = 1,
    epochDay: Long = TEST_EPOCH_DAY,
    kind: WorkoutKind = WorkoutKind.STRENGTH,
    minutes: Int = 45,
    distanceM: Int? = null,
    energyKcal: Int? = 150,
    energySource: EnergySource = EnergySource.MET_ESTIMATE,
    effort: Effort = Effort.MODERATE,
    hidden: Boolean = false,
    note: String? = null,
    startedAtMillis: Long = 0,
) = Workout(
    id = id, epochDay = epochDay, startedAtMillis = startedAtMillis, durationMinutes = minutes,
    kind = kind, title = null, distanceM = distanceM, energyKcal = energyKcal,
    energySource = energySource, effort = effort, source = WorkoutSource.TYPED,
    hidden = hidden, note = note,
)
```

- [ ] **Step 2: Write the failing tests.** Create
  `app/src/test/java/com/metaself/app/domain/movement/WorkoutDraftTest.kt`:

```kotlin
package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * A workout being typed in (D76). The weight is `aProfile()`'s 80 kg; every figure is invented, and
 * each expected kcal is (MET − 1) × 80 × hours with the row [MetEstimate] holds, worked beside it.
 */
class WorkoutDraftTest {

    private val weight = 80.0

    @Test
    fun `a new draft starts on Moderate with nothing else chosen, and cannot be saved`() {
        val draft = WorkoutDraft()

        assertThat(draft.effort).isEqualTo(Effort.MODERATE)
        assertThat(draft.kind).isNull()
        assertThat(draft.canSave).isFalse()
        assertThat(draft.toWorkout(0, TEST_EPOCH_DAY, 0, weight)).isNull()
    }

    @Test
    fun `kind and minutes are all that is required`() {
        assertThat(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45").canSave).isTrue()
    }

    @Test
    fun `minutes are a whole number from one to a day's worth`() {
        listOf("0", "-5", "1441", "4.5", "abc").forEach { typed ->
            val draft = WorkoutDraft(kind = WorkoutKind.RUN, minutes = typed)
            assertThat(draft.minutesValue).isNull()
            assertThat(draft.minutesProblem).isTrue()
            assertThat(draft.canSave).isFalse()
        }
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = " 1440 ").minutesValue).isEqualTo(1_440)
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "").minutesProblem).isFalse()
    }

    @Test
    fun `a distance is offered for runs, walks, rides and swims only`() {
        assertThat(WorkoutDraft.KINDS.filter { WorkoutDraft(kind = it).takesDistance })
            .containsExactly(WorkoutKind.RUN, WorkoutKind.WALK, WorkoutKind.CYCLE, WorkoutKind.SWIM)
            .inOrder()
        assertThat(WorkoutDraft().takesDistance).isFalse()
    }

    @Test
    fun `a distance is read in kilometres, a comma as a decimal point`() {
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, distanceKm = "6.2").distanceM).isEqualTo(6_200)
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, distanceKm = "6,2").distanceM).isEqualTo(6_200)
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, distanceKm = "").distanceM).isNull()
    }

    @Test
    fun `a distance that is not one blocks saving, and a blank one does not`() {
        val bad = WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "five")

        assertThat(bad.distanceProblem).isTrue()
        assertThat(bad.canSave).isFalse()
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "0").canSave).isFalse()
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "").canSave).isTrue()
    }

    @Test
    fun `a distance typed before switching to strength is kept but not used`() {
        val draft = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", distanceKm = "five")

        assertThat(draft.distanceM).isNull()
        assertThat(draft.distanceProblem).isFalse()
        assertThat(draft.canSave).isTrue()
        assertThat(draft.copy(kind = WorkoutKind.RUN).distanceKm).isEqualTo("five")
    }

    /** 30 minutes over 5 km: 1,800 s / 5 = 360 s a kilometre. */
    @Test
    fun `a run with minutes and a distance has a pace`() {
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5").paceSecondsPerKm)
            .isEqualTo(360)
    }

    /** D78: pace for runs only. */
    @Test
    fun `a walk or a ride has no pace, and nor does a run missing either figure`() {
        assertThat(WorkoutDraft(kind = WorkoutKind.WALK, minutes = "50", distanceKm = "4").paceSecondsPerKm).isNull()
        assertThat(WorkoutDraft(kind = WorkoutKind.CYCLE, minutes = "50", distanceKm = "20").paceSecondsPerKm).isNull()
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30").paceSecondsPerKm).isNull()
        assertThat(WorkoutDraft(kind = WorkoutKind.RUN, distanceKm = "5").paceSecondsPerKm).isNull()
    }

    /** Resistance training, MET 3.5: (3.5 − 1) × 80 × 0.75 = 150. */
    @Test
    fun `without a distance the estimate follows the effort`() {
        val draft = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45")

        assertThat(draft.estimateKcal(weight)).isEqualTo(150)
        assertThat(draft.pricedByPace).isFalse()
    }

    /** 5 km in 30 min is 6.21 mph, the 6–6.3 mph row, MET 9.3: 8.3 × 80 × 0.5 = 332 — not Hard's 432. */
    @Test
    fun `a run with a distance is priced by its pace, whatever the effort`() {
        val draft = WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5", effort = Effort.HARD)

        assertThat(draft.estimateKcal(weight)).isEqualTo(332)
        assertThat(draft.pricedByPace).isTrue()
    }

    @Test
    fun `with no weight there is no estimate`() {
        assertThat(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45").estimateKcal(null)).isNull()
    }

    @Test
    fun `it saves as a typed workout carrying its estimate, and says so`() {
        val workout = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", note = "  a note  ")
            .toWorkout(id = 0, epochDay = TEST_EPOCH_DAY, startedAtMillis = 1_000, weightKg = weight)

        assertThat(workout).isEqualTo(
            Workout(
                id = 0, epochDay = TEST_EPOCH_DAY, startedAtMillis = 1_000, durationMinutes = 45,
                kind = WorkoutKind.STRENGTH, title = null, distanceM = null, energyKcal = 150,
                energySource = EnergySource.MET_ESTIMATE, effort = Effort.MODERATE,
                source = WorkoutSource.TYPED, hidden = false, note = "a note",
            ),
        )
    }

    @Test
    fun `the owner's own figure is saved as his, and no estimate is made`() {
        val workout = WorkoutDraft(kind = WorkoutKind.SWIM, minutes = "40", ownEnergy = true, energyKcal = "300")
            .toWorkout(0, TEST_EPOCH_DAY, 0, weight)!!

        assertThat(workout.energyKcal).isEqualTo(300)
        assertThat(workout.energySource).isEqualTo(EnergySource.TYPED)
    }

    @Test
    fun `setting it yourself needs a whole number of zero or more`() {
        val own = WorkoutDraft(kind = WorkoutKind.SWIM, minutes = "40", ownEnergy = true)

        assertThat(own.canSave).isFalse()
        assertThat(own.copy(energyKcal = "lots").energyProblem).isTrue()
        assertThat(own.copy(energyKcal = "-1").canSave).isFalse()
        assertThat(own.copy(energyKcal = "0").canSave).isTrue()
    }

    /** D4: no weight, no estimate — and nothing guessed in its place. */
    @Test
    fun `with no weight and no figure of his own it saves with no energy, saying so`() {
        val workout = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45").toWorkout(0, TEST_EPOCH_DAY, 0, null)!!

        assertThat(workout.energyKcal).isNull()
        assertThat(workout.energySource).isEqualTo(EnergySource.NONE)
    }

    @Test
    fun `a strength session saves no distance, whatever was typed`() {
        val workout = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", distanceKm = "5")
            .toWorkout(0, TEST_EPOCH_DAY, 0, weight)!!

        assertThat(workout.distanceM).isNull()
    }

    @Test
    fun `a typed workout opens filled, as it was saved`() {
        val run = aTypedWorkout(kind = WorkoutKind.RUN, minutes = 32, distanceM = 6_200, effort = Effort.HARD, note = "a note")

        assertThat(WorkoutDraft.from(run)).isEqualTo(
            WorkoutDraft(kind = WorkoutKind.RUN, minutes = "32", distanceKm = "6.2", effort = Effort.HARD, note = "a note"),
        )
        assertThat(WorkoutDraft.from(aTypedWorkout(kind = WorkoutKind.WALK, distanceM = 10_000)).distanceKm)
            .isEqualTo("10")
    }

    @Test
    fun `a figure he set opens as his, and an estimate opens as an estimate`() {
        val own = aTypedWorkout(energyKcal = 300, energySource = EnergySource.TYPED)

        assertThat(WorkoutDraft.from(own).ownEnergy).isTrue()
        assertThat(WorkoutDraft.from(own).energyKcal).isEqualTo("300")
        assertThat(WorkoutDraft.from(aTypedWorkout()).ownEnergy).isFalse()
        assertThat(WorkoutDraft.from(aTypedWorkout()).energyKcal).isEmpty()
    }

    /** D76: onto the open day, at the clock time now. Now is 15:00 UTC on TEST_EPOCH_DAY. */
    @Test
    fun `a workout starts on the day it is logged to, at the time it is now`() {
        val now = TEST_EPOCH_DAY * DAY_MS + 15 * HOUR_MS

        assertThat(WorkoutDraft.startOn(TEST_EPOCH_DAY - 1, now, ZoneOffset.UTC))
            .isEqualTo((TEST_EPOCH_DAY - 1) * DAY_MS + 15 * HOUR_MS)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val HOUR_MS = 3_600_000L
    }
}
```

  In `app/src/test/java/com/metaself/app/domain/movement/MetEstimateTest.kt`, add inside the class:

```kotlin
    /** The sheet says "from the pace" exactly when this is true (plan design question 4). */
    @Test
    fun `a run, walk or ride with a distance is priced by its pace, anything else by its effort`() {
        assertThat(MetEstimate.pricedByPace(WorkoutKind.RUN, 5_000)).isTrue()
        assertThat(MetEstimate.pricedByPace(WorkoutKind.WALK, 4_000)).isTrue()
        assertThat(MetEstimate.pricedByPace(WorkoutKind.CYCLE, 20_000)).isTrue()
        assertThat(MetEstimate.pricedByPace(WorkoutKind.SWIM, 1_000)).isFalse()
        assertThat(MetEstimate.pricedByPace(WorkoutKind.STRENGTH, 1_000)).isFalse()
        assertThat(MetEstimate.pricedByPace(WorkoutKind.RUN, null)).isFalse()
        assertThat(MetEstimate.pricedByPace(WorkoutKind.RUN, 0)).isFalse()
    }
```

  Create `app/src/test/java/com/metaself/app/ui/movement/WorkoutSheetWordingTest.kt`:

```kotlin
package com.metaself.app.ui.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutKind
import org.junit.jupiter.api.Test

/** What the sheet says about a draft (D76). 80 kg is `aProfile()`'s; every figure is invented. */
class WorkoutSheetWordingTest {

    @Test
    fun `the estimate says it is one, and what it was estimated from`() {
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), 80.0))
            .isEqualTo("about 150 kcal, estimated from the effort")
        assertThat(
            WorkoutSheetWording.estimate(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5"), 80.0),
        ).isEqualTo("about 332 kcal, estimated from the pace")
    }

    @Test
    fun `nothing is estimated until kind and minutes are in`() {
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(), 80.0)).isNull()
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(kind = WorkoutKind.RUN), 80.0)).isNull()
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(minutes = "30"), 80.0)).isNull()
    }

    @Test
    fun `with no weight it says why there is no estimate`() {
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), null))
            .isEqualTo("No estimate: the profile has no weight")
    }

    /** Running at moderate effort for five hours, no distance: MET 9.3, 8.3 × 80 × 5 = 3,320. */
    @Test
    fun `a thousand and more has a separator`() {
        assertThat(WorkoutSheetWording.estimate(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "300"), 80.0))
            .isEqualTo("about 3,320 kcal, estimated from the effort")
    }

    @Test
    fun `the pace line is a run's pace, and a walk has none`() {
        assertThat(WorkoutSheetWording.pace(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5")))
            .isEqualTo("6:00 /km")
        assertThat(WorkoutSheetWording.pace(WorkoutDraft(kind = WorkoutKind.WALK, minutes = "50", distanceKm = "4")))
            .isNull()
    }
}
```

- [ ] **Step 3: Run them to see them fail.**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.movement.WorkoutDraftTest" --tests "com.metaself.app.domain.movement.MetEstimateTest" --tests "com.metaself.app.ui.movement.WorkoutSheetWordingTest" > /tmp/ms-typed.log 2>&1; echo "exit $?"`
  Expected: exit non-zero; the log shows `Unresolved reference: WorkoutDraft`, `pricedByPace` and
  `WorkoutSheetWording` (test compilation fails).

- [ ] **Step 4: Implement.** In `Workout.kt`, add above `data class Workout` and use it in the
  getter:

```kotlin
/** Seconds per kilometre for [minutes] over [metres]. One formula for a stored workout and a draft. */
internal fun paceOf(minutes: Int, metres: Int): Int =
    (minutes * 60.0 / (metres / 1000.0)).roundToInt()
```

```kotlin
    /** Seconds per kilometre — "5:30 /km" on screen — or null without a distance. */
    val paceSecondsPerKm: Int?
        get() = distanceM?.takeIf { it > 0 && durationMinutes > 0 }
            ?.let { paceOf(durationMinutes, it) }
```

  In `MetEstimate.kt`, add inside the object, after `netKcal`:

```kotlin
    /**
     * Whether [netKcal] prices a workout of [kind] with [distanceM] by its speed — the felt effort then
     * ignored — so that the sheet can say which of the two its estimate came from.
     */
    fun pricedByPace(kind: WorkoutKind, distanceM: Int?): Boolean =
        (distanceM ?: 0) > 0 && kind in SPEED_KINDS

    private val SPEED_KINDS = setOf(WorkoutKind.RUN, WorkoutKind.WALK, WorkoutKind.CYCLE)
```

  Create `app/src/main/java/com/metaself/app/domain/movement/WorkoutDraft.kt`:

```kotlin
package com.metaself.app.domain.movement

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A workout being typed in (D76), as the sheet holds it: the fields as typed, and what they add up to.
 *
 * The text is kept exactly as typed, never reformatted under the owner's thumb; what it means is
 * worked out here. Only [kind] and [minutes] are required. [effort] is never null: it starts on
 * Moderate and cannot be cleared (activity spec §8 item 4), so [MetEstimate] is never asked to price
 * a typed workout without one.
 *
 * @property distanceKm kept when the kind changes to one without a distance, so changing back finds
 *   it; used only while [takesDistance].
 * @property ownEnergy the owner chose "set it yourself": [energyKcal] is his figure (TYPED) and no
 *   estimate is made.
 */
data class WorkoutDraft(
    val kind: WorkoutKind? = null,
    val minutes: String = "",
    val distanceKm: String = "",
    val effort: Effort = Effort.MODERATE,
    val ownEnergy: Boolean = false,
    val energyKcal: String = "",
    val note: String = "",
) {
    val takesDistance: Boolean get() = kind in DISTANCE_KINDS

    /** Whole minutes from one to a day's worth; null when blank or anything else. */
    val minutesValue: Int? get() = minutes.trim().toIntOrNull()?.takeIf { it in 1..MINUTES_IN_A_DAY }

    val minutesProblem: Boolean get() = minutes.isNotBlank() && minutesValue == null

    /** Metres, when the kind takes a distance and a positive one was typed; a comma is a decimal point. */
    val distanceM: Int?
        get() {
            if (!takesDistance) return null
            val km = distanceKm.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
            val metres = km.movePointRight(3).setScale(0, RoundingMode.HALF_UP)
            if (metres.signum() <= 0 || metres > BigDecimal(Int.MAX_VALUE)) return null
            return metres.toInt()
        }

    val distanceProblem: Boolean get() = takesDistance && distanceKm.isNotBlank() && distanceM == null

    /** The owner's own figure, when he chose to give one: a whole number of kcal, zero or more. */
    val ownKcal: Int? get() = if (ownEnergy) energyKcal.trim().toIntOrNull()?.takeIf { it >= 0 } else null

    val energyProblem: Boolean get() = ownEnergy && energyKcal.isNotBlank() && ownKcal == null

    /** Seconds per kilometre for a run with both minutes and a distance, shown live (D76; runs only, D78). */
    val paceSecondsPerKm: Int?
        get() {
            if (kind != WorkoutKind.RUN) return null
            val minutes = minutesValue ?: return null
            val metres = distanceM ?: return null
            return paceOf(minutes, metres)
        }

    /** Whether the estimate follows the pace rather than the felt effort ([MetEstimate.pricedByPace]). */
    val pricedByPace: Boolean
        get() = kind != null && minutesValue != null && MetEstimate.pricedByPace(kind, distanceM)

    /** What the MET table says it cost on [weightKg]; null without a kind, minutes or a weight. */
    fun estimateKcal(weightKg: Double?): Int? {
        val kind = kind ?: return null
        val minutes = minutesValue ?: return null
        val weight = weightKg ?: return null
        return MetEstimate.netKcal(kind, effort, minutes, distanceM, weight)
    }

    val canSave: Boolean
        get() = kind != null && minutesValue != null && !distanceProblem && (!ownEnergy || ownKcal != null)

    /**
     * The workout this draft saves as, or null while it cannot be saved. Its energy is the owner's
     * figure (TYPED) when he gave one; else the estimate (MET_ESTIMATE); else, with no weight to
     * estimate from, nothing (NONE) — never a guess (D4).
     */
    fun toWorkout(id: Long, epochDay: Long, startedAtMillis: Long, weightKg: Double?): Workout? {
        if (!canSave) return null
        val kind = kind ?: return null
        val minutes = minutesValue ?: return null
        val estimate = estimateKcal(weightKg)
        val (kcal, source) = when {
            ownEnergy -> ownKcal to EnergySource.TYPED
            estimate != null -> estimate to EnergySource.MET_ESTIMATE
            else -> null to EnergySource.NONE
        }
        return Workout(
            id = id,
            epochDay = epochDay,
            startedAtMillis = startedAtMillis,
            durationMinutes = minutes,
            kind = kind,
            title = null,
            distanceM = distanceM,
            energyKcal = kcal,
            energySource = source,
            effort = effort,
            source = WorkoutSource.TYPED,
            hidden = false,
            note = note.trim().takeIf { it.isNotEmpty() },
        )
    }

    companion object {

        /** The kinds the sheet offers, in its order (D76). */
        val KINDS = listOf(
            WorkoutKind.RUN, WorkoutKind.WALK, WorkoutKind.CYCLE,
            WorkoutKind.SWIM, WorkoutKind.STRENGTH, WorkoutKind.OTHER,
        )

        private val DISTANCE_KINDS = setOf(WorkoutKind.RUN, WorkoutKind.WALK, WorkoutKind.CYCLE, WorkoutKind.SWIM)

        /** 24 × 60: no session is longer than the day it is filed on. */
        const val MINUTES_IN_A_DAY = 1_440

        /** A typed workout, as the sheet opens it to be changed. */
        fun from(workout: Workout): WorkoutDraft = WorkoutDraft(
            kind = workout.kind,
            minutes = workout.durationMinutes.toString(),
            distanceKm = workout.distanceM
                ?.let { BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString() }
                .orEmpty(),
            effort = workout.effort ?: Effort.MODERATE,
            ownEnergy = workout.energySource == EnergySource.TYPED,
            energyKcal = workout.energyKcal
                ?.takeIf { workout.energySource == EnergySource.TYPED }
                ?.toString()
                .orEmpty(),
            note = workout.note.orEmpty(),
        )

        /** [epochDay] at the clock time of [nowMillis] in [zone]: where a typed workout starts (D76). */
        fun startOn(epochDay: Long, nowMillis: Long, zone: ZoneId): Long =
            LocalDate.ofEpochDay(epochDay)
                .atTime(Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime())
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
    }
}
```

  Create `app/src/main/java/com/metaself/app/ui/movement/WorkoutSheetWording.kt`:

```kotlin
package com.metaself.app.ui.movement

import com.metaself.app.domain.movement.WorkoutDraft
import java.util.Locale

/**
 * What the log-a-workout sheet says about the draft (D76). The energy is always called an estimate
 * and says what it was estimated from (D4): the pace when [WorkoutDraft.pricedByPace], else the effort.
 */
object WorkoutSheetWording {

    const val NO_WEIGHT = "No estimate: the profile has no weight"

    /** "about 150 kcal, estimated from the effort"; null until a kind and minutes are in. */
    fun estimate(draft: WorkoutDraft, weightKg: Double?): String? {
        if (draft.kind == null || draft.minutesValue == null) return null
        val kcal = draft.estimateKcal(weightKg) ?: return NO_WEIGHT
        val from = if (draft.pricedByPace) "the pace" else "the effort"
        return "about ${String.format(Locale.US, "%,d", kcal)} kcal, estimated from $from"
    }

    /** "6:00 /km", for a run with minutes and a distance; null otherwise (D78). */
    fun pace(draft: WorkoutDraft): String? = draft.paceSecondsPerKm?.let(MovementWeekWording::pace)
}
```

- [ ] **Step 5: Run them to see them pass.** Same command as Step 3 with
  `--tests "com.metaself.app.domain.movement.WorkoutTest"` added (the stored workout's pace getter now
  calls the shared helper). Expected: exit 0; all four classes pass.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/movement/Workout.kt \
  app/src/main/java/com/metaself/app/domain/movement/MetEstimate.kt \
  app/src/main/java/com/metaself/app/domain/movement/WorkoutDraft.kt \
  app/src/main/java/com/metaself/app/ui/movement/WorkoutSheetWording.kt \
  app/src/test/java/com/metaself/app/domain/movement/Workouts.kt \
  app/src/test/java/com/metaself/app/domain/movement/WorkoutDraftTest.kt \
  app/src/test/java/com/metaself/app/domain/movement/MetEstimateTest.kt \
  app/src/test/java/com/metaself/app/ui/movement/WorkoutSheetWordingTest.kt
git commit -m "feat: a typed workout's draft — what is required, its estimate and its pace (D76)" \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Tidier weeks (D78)

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/movement/MovementWeek.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/movement/MovementWeekWording.kt`
- Test: `app/src/test/java/com/metaself/app/domain/movement/MovementWeekTest.kt`
- Test: `app/src/test/java/com/metaself/app/ui/movement/MovementWeekWordingTest.kt`

- [ ] **Step 1: Write the failing tests.** In `MovementWeekTest.kt`, give the private `workout(...)`
  helper a `kind` parameter (default `WorkoutKind.RUN`) and pass it through:

```kotlin
    private fun workout(
        epochDay: Long,
        minutes: Int,
        hidden: Boolean = false,
        startedAtMillis: Long = 0,
        kind: WorkoutKind = WorkoutKind.RUN,
    ) = Workout(
        id = 0, epochDay = epochDay, startedAtMillis = startedAtMillis, durationMinutes = minutes,
        kind = kind, title = "Running", distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
        hidden = hidden, note = null,
    )
```

  and add, after `a workout before Monday is not this week's`:

```kotlin
    /** D78: walks are counted apart from workouts; the time is every session's. Invented minutes. */
    @Test
    fun `walks are counted apart from workouts, and the time is every session's`() {
        val workouts = listOf(
            workout(20_699, minutes = 30),
            workout(20_698, minutes = 45),
            workout(20_697, minutes = 60, kind = WorkoutKind.WALK),
            workout(20_697, minutes = 20, kind = WorkoutKind.WALK, hidden = true),
        )

        val week = MovementWeek.of(today, emptyList(), workouts, emptyMap())

        assertThat(week.workoutCount).isEqualTo(2)
        assertThat(week.walkCount).isEqualTo(1)
        assertThat(week.workoutMinutes).isEqualTo(135)
    }
```

  In `MovementWeekWordingTest.kt`:

  (a) add imports `com.metaself.app.domain.movement.aTypedWorkout`, and give the `week` value
  `walkCount = 0,` after `workoutCount = 4,`; in `a headline figure that is not recorded is not
  said`, add `walkCount = 0` to the `copy(...)`.

  (b) replace the private `workout(...)` helper with one that also takes `startedAtMillis`:

```kotlin
    private fun workout(
        title: String?,
        minutes: Int,
        distanceM: Int?,
        kind: WorkoutKind = WorkoutKind.RUN,
        avgHeartRate: Int? = null,
        startedAtMillis: Long = 0,
    ) = Workout(
        id = 0, epochDay = TEST_EPOCH_DAY, startedAtMillis = startedAtMillis, durationMinutes = minutes,
        kind = kind, title = title, distanceM = distanceM, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
        hidden = false, note = null, avgHeartRate = avgHeartRate,
    )
```

  (c) in `a workout with no distance is given by its time, and two are listed together`, make the
  weights session a strength one (two runs would now be combined):

```kotlin
        val weights = workout(title = "Weights", minutes = 45, distanceM = null, kind = WorkoutKind.STRENGTH)
```

  (d) add these tests:

```kotlin
    /** D78: "2 workouts · 3 walks · 4 h 30" — 270 minutes, invented. */
    @Test
    fun `the headline counts walks apart from workouts, with every session's time`() {
        val busy = week.copy(workoutCount = 2, walkCount = 3, workoutMinutes = 270)

        assertThat(MovementWeekWording.workouts(busy)).isEqualTo("2 workouts · 3 walks · 4 h 30")
    }

    @Test
    fun `either part is left out when it is zero, and one is one`() {
        assertThat(MovementWeekWording.workouts(week.copy(workoutCount = 0, walkCount = 3, workoutMinutes = 270)))
            .isEqualTo("3 walks · 4 h 30")
        assertThat(MovementWeekWording.workouts(week.copy(workoutCount = 0, walkCount = 1, workoutMinutes = 45)))
            .isEqualTo("1 walk · 45 min")
    }

    /** D78: 50 + 40 + 60 = 150 minutes, invented. */
    @Test
    fun `same-kind sessions are combined in the summary, by their total time`() {
        val walks = listOf(50, 40, 60).mapIndexed { i, minutes ->
            workout(title = "Walking", minutes = minutes, distanceM = null, kind = WorkoutKind.WALK, startedAtMillis = i.toLong())
        }

        assertThat(MovementWeekWording.summaryLine(MovementDay(TEST_EPOCH_DAY, null, walks, null)))
            .isEqualTo("Walking ×3 · 2 h 30")
    }

    @Test
    fun `combined sessions give their total distance when every one has one`() {
        val walks = listOf(
            workout(title = "Walking", minutes = 30, distanceM = 2_000, kind = WorkoutKind.WALK),
            workout(title = "Walking", minutes = 45, distanceM = 3_100, kind = WorkoutKind.WALK, startedAtMillis = 1),
        )

        assertThat(MovementWeekWording.summaryLine(MovementDay(TEST_EPOCH_DAY, null, walks, null)))
            .isEqualTo("Walking ×2 · 5.1 km")
    }

    @Test
    fun `combined sessions with one distance missing give their time`() {
        val walks = listOf(
            workout(title = "Walking", minutes = 30, distanceM = 2_000, kind = WorkoutKind.WALK),
            workout(title = "Walking", minutes = 40, distanceM = null, kind = WorkoutKind.WALK, startedAtMillis = 1),
        )

        assertThat(MovementWeekWording.summaryLine(MovementDay(TEST_EPOCH_DAY, null, walks, null)))
            .isEqualTo("Walking ×2 · 1 h 10")
    }

    @Test
    fun `combined sessions with different titles are named by their kind, in the order kinds first started`() {
        val day = MovementDay(
            TEST_EPOCH_DAY,
            null,
            listOf(
                running.copy(startedAtMillis = 0),
                workout(title = "Hiking", minutes = 40, distanceM = null, kind = WorkoutKind.WALK, startedAtMillis = 1),
                workout(title = "Walking", minutes = 50, distanceM = null, kind = WorkoutKind.WALK, startedAtMillis = 2),
            ),
            null,
        )

        assertThat(MovementWeekWording.summaryLine(day)).isEqualTo("Running 6.2 km, Walking ×2 · 1 h 30")
    }

    /** D78: pace for runs only. */
    @Test
    fun `a walk with a distance shows no pace`() {
        val walk = workout(title = "Walking", minutes = 50, distanceM = 4_000, kind = WorkoutKind.WALK)

        assertThat(MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, null, listOf(walk), null)))
            .containsExactly("Walking · 4.0 km · 50 min")
    }

    /** D4: a typed workout's energy says where it came from. */
    @Test
    fun `a typed workout's line says its energy and where it came from`() {
        fun line(workout: Workout) =
            MovementWeekWording.detailLines(MovementDay(TEST_EPOCH_DAY, null, listOf(workout), null)).single()

        assertThat(line(aTypedWorkout())).isEqualTo("Weights · 45 min · about 150 kcal, estimated")
        assertThat(line(aTypedWorkout(energyKcal = 300, energySource = EnergySource.TYPED)))
            .isEqualTo("Weights · 45 min · 300 kcal, you set this")
        assertThat(line(aTypedWorkout(energyKcal = null, energySource = EnergySource.NONE)))
            .isEqualTo("Weights · 45 min")
    }

    @Test
    fun `an open day's workout line carries its workout, and no other line does`() {
        val typed = aTypedWorkout()
        val rows = MovementWeekWording.detailRows(MovementDay(TEST_EPOCH_DAY, fullHealth, listOf(typed), 1_840))

        assertThat(rows.mapNotNull { it.workout }).containsExactly(typed)
        assertThat(rows.single { it.workout != null }.text).isEqualTo("Weights · 45 min · about 150 kcal, estimated")
    }
```

- [ ] **Step 2: Run them to see them fail.**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.movement.MovementWeekTest" --tests "com.metaself.app.ui.movement.MovementWeekWordingTest" > /tmp/ms-typed.log 2>&1; echo "exit $?"`
  Expected: exit non-zero; `Unresolved reference: walkCount` and `detailRows`.

- [ ] **Step 3: Implement.** In `MovementWeek.kt`, the KDoc of `MovementWeek` gains, after
  `@property averageActiveKcal …`:

```kotlin
 * @property workoutCount visible sessions this week that are not walks (D78).
 * @property walkCount visible walks this week, counted apart from workouts (D78).
 * @property workoutMinutes every visible session's time, walks included (D78).
```

  the class gains `val walkCount: Int,` after `val workoutCount: Int,`, and `of(...)` counts it — replace
  the two lines `workoutCount = visible.size,` and `workoutMinutes = …` with:

```kotlin
                workoutCount = visible.count { it.kind != WorkoutKind.WALK },
                walkCount = visible.count { it.kind == WorkoutKind.WALK },
                workoutMinutes = visible.sumOf { it.durationMinutes },
```

  In `MovementWeekWording.kt`: add imports `com.metaself.app.domain.movement.EnergySource` and
  `com.metaself.app.domain.movement.WorkoutSource`; add above the object:

```kotlin
/**
 * One line of an open day. [workout] is set on a workout's own line, so the screen can make a typed
 * workout's line a door to change it (D76).
 */
data class DetailLine(val text: String, val workout: Workout? = null)
```

  and replace `workouts`, `summaryLine`, `detailLines`, `name`, `partLines` and `workoutLine` with:

```kotlin
    /** "2 workouts · 3 walks · 4 h 30" (D78): either part left out at zero; null with neither. */
    fun workouts(week: MovementWeek): String? {
        val parts = listOfNotNull(
            week.workoutCount.takeIf { it > 0 }?.let { if (it == 1) "1 workout" else "$it workouts" },
            week.walkCount.takeIf { it > 0 }?.let { if (it == 1) "1 walk" else "$it walks" },
        )
        if (parts.isEmpty()) return null
        return (parts + duration(week.workoutMinutes)).joinToString(SEP)
    }

    /**
     * A day's one-line summary (D73), shown whether the day is open or closed: up to three parts —
     * movement calories, workouts, sleep. With none of them, the first line the open day would show,
     * so a day with steps is never called empty; "nothing recorded" only when there is nothing at all.
     */
    fun summaryLine(day: MovementDay): String {
        val health = day.health
        val parts = listOfNotNull(
            health?.activeKcal?.let { "${number(it)} kcal" },
            sessions(day.workouts),
            health?.sleepMinutes?.let { "slept ${duration(it)}" },
        )
        return if (parts.isNotEmpty()) parts.joinToString(SEP) else partLines(day).firstOrNull()?.text ?: NOTHING
    }

    /**
     * What an open day shows beneath its summary (D73): one line per part, each only when recorded.
     * A line the summary already says is not repeated, so a day with nothing but steps, or nothing at
     * all, has nothing beneath.
     */
    fun detailRows(day: MovementDay): List<DetailLine> {
        val summary = summaryLine(day)
        return partLines(day).filterNot { it.text == summary }
    }

    /** [detailRows]' words alone. */
    fun detailLines(day: MovementDay): List<String> = detailRows(day).map { it.text }

    /** The recording app's name for a session, or its kind's. */
    fun name(workout: Workout): String = workout.title?.takeIf { it.isNotBlank() } ?: kindName(workout.kind)

    /** A kind's name, as `ExerciseNames` names the same kinds on the day screen. */
    fun kindName(kind: WorkoutKind): String = when (kind) {
        WorkoutKind.RUN -> "Running"
        WorkoutKind.WALK -> "Walking"
        WorkoutKind.CYCLE -> "Cycling"
        WorkoutKind.SWIM -> "Swimming"
        WorkoutKind.STRENGTH -> "Weights"
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "Exercise"
    }

    /**
     * A day's sessions in its summary (D78): same-kind sessions combined — "Walking ×3 · 2 h 30" —
     * in the order each kind first started. A kind's total distance when every one of its sessions
     * has one, its total time otherwise. Named by the title its sessions share, or by the kind when
     * the titles differ. A kind with one session reads as it always did: "Running 6.2 km".
     */
    private fun sessions(workouts: List<Workout>): String? {
        if (workouts.isEmpty()) return null
        return workouts.groupBy { it.kind }.values.joinToString(", ") { same ->
            val label = same.map(::name).distinct().singleOrNull() ?: kindName(same.first().kind)
            val distances = same.mapNotNull { it.distanceM }
            val measure = if (distances.size == same.size) km(distances.sum()) else duration(same.sumOf { it.durationMinutes })
            if (same.size == 1) "$label $measure" else "$label ×${same.size}$SEP$measure"
        }
    }

    /** One line per recorded part of the day, in the order an open day shows them. */
    private fun partLines(day: MovementDay): List<DetailLine> {
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
        return movement.map { DetailLine(it) } +
            day.workouts.map { DetailLine(workoutLine(it), it) } +
            rest.map { DetailLine(it) }
    }

    private fun workoutLine(workout: Workout): String = listOfNotNull(
        name(workout),
        workout.distanceM?.let(::km),
        duration(workout.durationMinutes),
        // D78: pace for runs only.
        workout.paceSecondsPerKm?.takeIf { workout.kind == WorkoutKind.RUN }?.let(::pace),
        workout.avgHeartRate?.let { "avg $it bpm" },
        typedEnergy(workout),
    ).joinToString(SEP)

    /** A typed workout's energy with where it came from (D4). A synced one's is not shown here. */
    private fun typedEnergy(workout: Workout): String? {
        if (workout.source != WorkoutSource.TYPED) return null
        val kcal = workout.energyKcal ?: return null
        return when (workout.energySource) {
            EnergySource.MET_ESTIMATE -> "about ${number(kcal)} kcal, estimated"
            EnergySource.TYPED -> "${number(kcal)} kcal, you set this"
            EnergySource.BAND, EnergySource.NONE -> null
        }
    }
```

  Leave `kicker`, `distance`, `averageMovement`, `dayHeading`, `lastFourWeeks`, `km`, `duration`,
  `pace`, `sleepLine`, `bodyLine`, `withSource`, `kmFigure` and `number` as they are.

- [ ] **Step 4: Run them to see them pass.** Same command as Step 2, then the Movement screen's render
  test, which reads these words:
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.movement.*" > /tmp/ms-typed.log 2>&1; echo "exit $?"`
  Expected: exit 0 both times. (The render test's week has one run, so "1 workout · 32 min" and the
  run's "5:10 /km" still read as before.)

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/movement/MovementWeek.kt \
  app/src/main/java/com/metaself/app/ui/movement/MovementWeekWording.kt \
  app/src/test/java/com/metaself/app/domain/movement/MovementWeekTest.kt \
  app/src/test/java/com/metaself/app/ui/movement/MovementWeekWordingTest.kt
git commit -m "feat: tidier weeks — same-kind sessions combined, pace for runs, walks counted apart (D78)" \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Storing a typed workout

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/TypedWorkouts.kt`
- Modify: `app/src/main/java/com/metaself/app/data/health/MovementRecord.kt`
- Modify: `app/src/main/java/com/metaself/app/di/DataModule.kt`
- Create: `app/src/test/java/com/metaself/app/data/health/FakeTypedWorkouts.kt` (shared fake)
- Test: `app/src/test/java/com/metaself/app/data/health/RoomTypedWorkoutsTest.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/MovementRecordTest.kt`
- Test: `app/src/test/java/com/metaself/app/data/health/DaySummaryTest.kt` (pins)
- Test: `app/src/test/java/com/metaself/app/data/health/HealthRecordStoreTest.kt` (pin, CI only)

- [ ] **Step 1: Write the failing tests.** Create
  `app/src/test/java/com/metaself/app/data/health/RoomTypedWorkoutsTest.kt`:

```kotlin
package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.time.Now
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.movement.aTypedWorkout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The typed-workout store over a fake DAO and store, so it runs on this machine; the real DAO and
 * the real summarise are covered in CI (`HealthRecordStoreTest`). Every figure is invented.
 */
class RoomTypedWorkoutsTest {

    private val dao = FakeWorkoutDao()
    private val transaction = TrackedTransaction()
    private val store = SummarisingStore(transaction)
    private val typed = RoomTypedWorkouts(dao, transaction, store, Now { STAMP })

    @Test
    fun `a logged workout is stored as typed, with no origin, and its day is summarised again`() = runTest {
        val id = typed.log(aTypedWorkout(id = 0))

        val row = dao.rows.single()
        assertThat(row.id).isEqualTo(id)
        assertThat(row.source).isEqualTo("TYPED")
        assertThat(row.origin).isNull()
        assertThat(row.originId).isNull()
        assertThat(row.kind).isEqualTo("STRENGTH")
        assertThat(row.effort).isEqualTo("MODERATE")
        assertThat(row.energyKcal).isEqualTo(150)
        assertThat(row.energySource).isEqualTo("MET_ESTIMATE")
        assertThat(store.summarised).containsExactly(setOf(TEST_EPOCH_DAY))
        assertThat(store.totalsGiven.single()).isEqualTo(TotalsResult.ALL_FAILED)
        assertThat(store.nowGiven.single()).isEqualTo(STAMP)
    }

    /** Plan design question 15: the summary moves with the write, never half of it. */
    @Test
    fun `the day is summarised inside the same transaction as the write`() = runTest {
        typed.log(aTypedWorkout(id = 0))

        assertThat(store.insideTransaction).containsExactly(true)
    }

    @Test
    fun `only typed workouts are observed`() = runTest {
        dao.rows += syncedRow(id = 50)
        typed.log(aTypedWorkout(id = 0))

        val seen = typed.observe(TEST_EPOCH_DAY, TEST_EPOCH_DAY).first()

        assertThat(seen.map { it.source }).containsExactly(WorkoutSource.TYPED)
    }

    @Test
    fun `a change replaces the typed row in place, keeping its day, start and hidden flag`() = runTest {
        val id = typed.log(aTypedWorkout(id = 0, startedAtMillis = 1_000))
        dao.rows[0] = dao.rows[0].copy(hidden = true)
        store.summarised.clear()

        typed.change(aTypedWorkout(id = id, minutes = 60, energyKcal = 200, startedAtMillis = 9_999))

        val row = dao.rows.single()
        assertThat(row.durationMinutes).isEqualTo(60)
        assertThat(row.energyKcal).isEqualTo(200)
        assertThat(row.startedAtMillis).isEqualTo(1_000)
        assertThat(row.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(row.hidden).isTrue()
        assertThat(store.summarised).containsExactly(setOf(TEST_EPOCH_DAY))
    }

    /** D76: a synced session is not editable here. */
    @Test
    fun `a change never touches a synced session`() = runTest {
        dao.rows += syncedRow(id = 50)

        typed.change(aTypedWorkout(id = 50, minutes = 60))

        assertThat(dao.rows.single()).isEqualTo(syncedRow(id = 50))
        assertThat(store.summarised).isEmpty()
    }

    @Test
    fun `a delete removes a typed workout and summarises its day again`() = runTest {
        val id = typed.log(aTypedWorkout(id = 0))
        store.summarised.clear()

        typed.delete(aTypedWorkout(id = id))

        assertThat(dao.rows).isEmpty()
        assertThat(store.summarised).containsExactly(setOf(TEST_EPOCH_DAY))
    }

    @Test
    fun `a delete never removes a synced session`() = runTest {
        dao.rows += syncedRow(id = 50)

        typed.delete(aTypedWorkout(id = 50).copy(source = WorkoutSource.SYNCED))
        typed.delete(aTypedWorkout(id = 50))

        assertThat(dao.rows.single()).isEqualTo(syncedRow(id = 50))
    }

    private fun syncedRow(id: Long) = WorkoutEntity(
        id = id, epochDay = TEST_EPOCH_DAY, startedAtMillis = 0, durationMinutes = 32, kind = "RUN",
        title = "Running", distanceM = 6_200, energyKcal = null, energySource = "NONE", effort = null,
        source = "SYNCED", origin = "com.example.band", originId = "session-1", note = null,
    )

    private class TrackedTransaction : DatabaseTransaction {
        var open = false
        override suspend fun run(block: suspend () -> Unit) {
            open = true
            try {
                block()
            } finally {
                open = false
            }
        }
    }

    private class SummarisingStore(private val transaction: TrackedTransaction) : HealthStore {
        val summarised = mutableListOf<Set<Long>>()
        val totalsGiven = mutableListOf<TotalsResult>()
        val nowGiven = mutableListOf<Long>()
        val insideTransaction = mutableListOf<Boolean>()

        override suspend fun summarise(days: Set<Long>, totals: TotalsResult, nowMillis: Long) {
            summarised += days
            totalsGiven += totals
            nowGiven += nowMillis
            insideTransaction += transaction.open
        }

        override suspend fun bookmark(kind: HealthKind): HealthSyncEntity? = error("not used")
        override suspend fun saveBookmark(bookmark: HealthSyncEntity): Unit = error("not used")
        override suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long> = error("not used")
        override suspend fun replaceWindow(
            kind: HealthKind,
            fromMillis: Long,
            toMillis: Long,
            records: List<ReadRecord>,
        ): Set<Long> = error("not used")
        override suspend fun historyActedOn(): Boolean = error("not used")
        override suspend fun markHistoryActedOn(): Unit = error("not used")
    }

    /** Only what the typed store calls behaves; the rest says it was not expected. */
    private class FakeWorkoutDao : WorkoutDao {
        val rows = mutableListOf<WorkoutEntity>()
        private val changes = MutableStateFlow(0)
        private var nextId = 1L

        override fun observeBetween(from: Long, to: Long): Flow<List<WorkoutEntity>> =
            changes.map { rows.filter { it.epochDay in from..to }.sortedBy { it.startedAtMillis } }

        override suspend fun insert(workout: WorkoutEntity): Long {
            val id = nextId++
            rows += workout.copy(id = id)
            changes.value++
            return id
        }

        override suspend fun update(workout: WorkoutEntity) {
            val at = rows.indexOfFirst { it.id == workout.id }
            if (at >= 0) rows[at] = workout
            changes.value++
        }

        /** As the SQL does: only a TYPED row. */
        override suspend fun deleteTyped(id: Long) {
            rows.removeAll { it.id == id && it.source == "TYPED" }
            changes.value++
        }

        override suspend fun onDay(epochDay: Long): List<WorkoutEntity> = rows.filter { it.epochDay == epochDay }

        override fun observeWeeklyRunning(weeks: Int): Flow<List<WeekOfRunning>> = error("not used")
        override suspend fun synced(origin: String, originId: String): WorkoutEntity? = error("not used")
        override suspend fun syncedIdsBetween(from: Long, to: Long): List<String> = error("not used")
        override suspend fun dropSyncedNotIn(from: Long, to: Long, keep: List<String>): Unit = error("not used")
        override suspend fun setHidden(id: Long, hidden: Boolean): Unit = error("not used")
        override suspend fun all(): List<WorkoutEntity> = error("not used")
        override suspend fun insertAll(workouts: List<WorkoutEntity>): Unit = error("not used")
        override suspend fun deleteAll(): Unit = error("not used")
        override suspend fun daysOfSynced(originId: String): List<Long> = error("not used")
        override suspend fun deleteSynced(originId: String): Unit = error("not used")
        override suspend fun visibleSyncedBetween(from: Long, to: Long): List<WorkoutEntity> = error("not used")
        override suspend fun deleteSyncedRow(id: Long): Unit = error("not used")
    }

    private companion object {
        const val STAMP = 1_000_000L
    }
}
```

  In `MovementRecordTest.kt`, add the import `com.metaself.app.domain.movement.aTypedWorkout` and:

```kotlin
    @Test
    fun `a typed workout is stored with no origin and reads back as it was`() {
        val typed = aTypedWorkout(id = 7, note = "a note")

        val entity = typed.toTypedEntity()

        assertThat(entity.source).isEqualTo("TYPED")
        assertThat(entity.origin).isNull()
        assertThat(entity.originId).isNull()
        assertThat(entity.effort).isEqualTo("MODERATE")
        assertThat(entity.energySource).isEqualTo("MET_ESTIMATE")
        assertThat(entity.toWorkout()).isEqualTo(typed)
    }
```

  In `DaySummaryTest.kt`, add after `hidden workouts are not counted` (**pins**: `DaySummary` already
  counts every visible workout; these pass at once and hold D77's "the day's summary counts it"):

```kotlin
    /** D77: a workout the owner typed is one of the day's workouts. */
    @Test
    fun `a typed workout counts among the day's workouts`() {
        val summary = DaySummary.of(day, DayTotals(), emptyList(), emptyList(), listOf(workout(30), typed(40)), null, 1_000)!!

        assertThat(summary.workoutCount).isEqualTo(2)
        assertThat(summary.workoutMinutes).isEqualTo(70)
    }

    @Test
    fun `a day with nothing but a typed workout has a summary`() {
        val summary = DaySummary.of(day, DayTotals(), emptyList(), emptyList(), listOf(typed(40)), null, 1_000)

        assertThat(summary?.workoutCount).isEqualTo(1)
    }

    private fun typed(minutes: Int) = workout(minutes).copy(
        kind = "STRENGTH", source = "TYPED", origin = null, originId = null,
        effort = "MODERATE", energySource = "MET_ESTIMATE", energyKcal = 150,
    )
```

  In `HealthRecordStoreTest.kt` (Robolectric; skips here, runs in CI), add after `a failed total keeps
  the one stored` (a **pin** of the store path the typed store relies on):

```kotlin
    /** D77: summarising again after a typed workout counts it and keeps the stored totals. */
    @Test
    fun `a typed workout is counted when its day is summarised again, and the stored totals stay`() = runTest {
        store.summarise(setOf(day), TotalsResult(byDay = mapOf(day to DayTotals(steps = 9_000))), STAMP)
        db.workoutDao().insert(
            WorkoutEntity(
                epochDay = day, startedAtMillis = day * 86_400_000L, durationMinutes = 45, kind = "STRENGTH",
                title = null, distanceM = null, energyKcal = 150, energySource = "MET_ESTIMATE",
                effort = "MODERATE", source = "TYPED", origin = null, originId = null, note = null,
            ),
        )

        store.summarise(setOf(day), TotalsResult.ALL_FAILED, STAMP)

        val summary = db.healthDayDao().day(day)!!
        assertThat(summary.steps).isEqualTo(9_000)
        assertThat(summary.workoutCount).isEqualTo(1)
        assertThat(summary.workoutMinutes).isEqualTo(45)
    }
```

- [ ] **Step 2: Run them to see them fail.**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.health.*" > /tmp/ms-typed.log 2>&1; echo "exit $?"`
  Expected: exit non-zero; `Unresolved reference: RoomTypedWorkouts` and `toTypedEntity`.

- [ ] **Step 3: Implement.** Create `app/src/main/java/com/metaself/app/data/health/TypedWorkouts.kt`:

```kotlin
package com.metaself.app.data.health

import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.time.Now
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The workouts the owner types in (D76): observed for the day's third reading (D77), and logged,
 * changed and deleted from the Movement screen's sheet. A synced workout is never changed or deleted
 * through here — its numbers are the band's.
 */
interface TypedWorkouts {

    /** The typed workouts of [from]..[to], hidden ones included. */
    fun observe(from: Long, to: Long): Flow<List<Workout>>

    /** Stores [workout] as a new typed workout; returns its id. */
    suspend fun log(workout: Workout): Long

    /** Replaces the typed workout with [workout]'s id, keeping its day, start and hidden flag. */
    suspend fun change(workout: Workout)

    /** Deletes [workout] if it is typed. */
    suspend fun delete(workout: Workout)

    companion object {
        /** For a caller that only reads, in a test: nothing typed, and nothing may be written. */
        val NONE: TypedWorkouts = object : TypedWorkouts {
            override fun observe(from: Long, to: Long): Flow<List<Workout>> = flowOf(emptyList())
            override suspend fun log(workout: Workout): Long = error("no store")
            override suspend fun change(workout: Workout): Unit = error("no store")
            override suspend fun delete(workout: Workout): Unit = error("no store")
        }
    }
}

/**
 * Over [WorkoutDao], with no new query. After each write the day's stored summary is worked out again
 * (`health_days`, D69) through [HealthStore.summarise], **in the same transaction**, so the day's
 * workout count never disagrees with its workouts. [TotalsResult.ALL_FAILED] keeps every total already
 * stored from Health Connect: saving a workout makes no cross-process call.
 */
class RoomTypedWorkouts @Inject constructor(
    private val workouts: WorkoutDao,
    private val transaction: DatabaseTransaction,
    private val store: HealthStore,
    private val now: Now,
) : TypedWorkouts {

    override fun observe(from: Long, to: Long): Flow<List<Workout>> =
        workouts.observeBetween(from, to).map { rows ->
            rows.filter { it.source == TYPED }.map { it.toWorkout() }
        }

    override suspend fun log(workout: Workout): Long {
        var id = 0L
        transaction.run {
            id = workouts.insert(workout.toTypedEntity().copy(id = 0))
            summariseAgain(workout.epochDay)
        }
        return id
    }

    override suspend fun change(workout: Workout) {
        transaction.run {
            val stored = workouts.onDay(workout.epochDay).firstOrNull { it.id == workout.id && it.source == TYPED }
            if (stored != null) {
                workouts.update(
                    workout.toTypedEntity().copy(
                        id = stored.id,
                        epochDay = stored.epochDay,
                        startedAtMillis = stored.startedAtMillis,
                        hidden = stored.hidden,
                        avgHeartRate = stored.avgHeartRate,
                        maxHeartRate = stored.maxHeartRate,
                        zoneSeconds = stored.zoneSeconds,
                        zoneMaxSource = stored.zoneMaxSource,
                    ),
                )
                summariseAgain(stored.epochDay)
            }
        }
    }

    override suspend fun delete(workout: Workout) {
        if (workout.source != WorkoutSource.TYPED) return
        transaction.run {
            workouts.deleteTyped(workout.id)
            summariseAgain(workout.epochDay)
        }
    }

    private suspend fun summariseAgain(epochDay: Long) {
        store.summarise(setOf(epochDay), TotalsResult.ALL_FAILED, now())
    }

    private companion object {
        val TYPED = WorkoutSource.TYPED.name
    }
}
```

  In `MovementRecord.kt`, add at the end:

```kotlin
/** A typed workout as it is stored (D76): no origin, `source = TYPED`, every enum by its name. */
fun Workout.toTypedEntity(): WorkoutEntity = WorkoutEntity(
    id = id,
    epochDay = epochDay,
    startedAtMillis = startedAtMillis,
    durationMinutes = durationMinutes,
    kind = kind.name,
    title = title,
    distanceM = distanceM,
    energyKcal = energyKcal,
    energySource = energySource.name,
    effort = effort?.name,
    source = WorkoutSource.TYPED.name,
    origin = null,
    originId = null,
    hidden = hidden,
    note = note,
)
```

  In `DataModule.kt`, add the imports `com.metaself.app.data.health.RoomTypedWorkouts` and
  `com.metaself.app.data.health.TypedWorkouts`, and after `provideMovementRecord`:

```kotlin
    /** Workouts the owner types in (D76), with the day's summary worked out again after each write. */
    @Provides
    @Singleton
    fun provideTypedWorkouts(typed: RoomTypedWorkouts): TypedWorkouts = typed
```

  Create `app/src/test/java/com/metaself/app/data/health/FakeTypedWorkouts.kt` (used by Tasks 4 and 6):

```kotlin
package com.metaself.app.data.health

import com.metaself.app.domain.movement.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Typed workouts in memory, remembering what was asked. [failing] makes every write throw it. */
class FakeTypedWorkouts(initial: List<Workout> = emptyList()) : TypedWorkouts {

    val workouts = MutableStateFlow(initial)
    val logged = mutableListOf<Workout>()
    val changed = mutableListOf<Workout>()
    val deleted = mutableListOf<Workout>()
    var failing: Exception? = null
    private var nextId = 100L

    override fun observe(from: Long, to: Long): Flow<List<Workout>> =
        workouts.map { all -> all.filter { it.epochDay in from..to } }

    override suspend fun log(workout: Workout): Long {
        failing?.let { throw it }
        val id = nextId++
        logged += workout
        workouts.value = workouts.value + workout.copy(id = id)
        return id
    }

    override suspend fun change(workout: Workout) {
        failing?.let { throw it }
        changed += workout
        workouts.value = workouts.value.map { if (it.id == workout.id) workout else it }
    }

    override suspend fun delete(workout: Workout) {
        failing?.let { throw it }
        deleted += workout
        workouts.value = workouts.value.filterNot { it.id == workout.id }
    }
}
```

- [ ] **Step 4: Run them to see them pass.** Same command as Step 2. Expected: exit 0;
  `HealthRecordStoreTest` and `HealthRecordDaoTest` are reported skipped (aarch64), everything else
  in `data.health` passes.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/data/health/TypedWorkouts.kt \
  app/src/main/java/com/metaself/app/data/health/MovementRecord.kt \
  app/src/main/java/com/metaself/app/di/DataModule.kt \
  app/src/test/java/com/metaself/app/data/health/FakeTypedWorkouts.kt \
  app/src/test/java/com/metaself/app/data/health/RoomTypedWorkoutsTest.kt \
  app/src/test/java/com/metaself/app/data/health/MovementRecordTest.kt \
  app/src/test/java/com/metaself/app/data/health/DaySummaryTest.kt \
  app/src/test/java/com/metaself/app/data/health/HealthRecordStoreTest.kt
git commit -m "feat: typed workouts are stored, and their day's summary counts them (D76, D77)" \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The third reading, live (D77)

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/movement/TypedReading.kt`
- Modify: `app/src/main/java/com/metaself/app/domain/movement/MovementCredit.kt` (KDoc of
  `typedWorkoutsKcal` only)
- Modify: `app/src/main/java/com/metaself/app/ui/screen/day/DayViewModel.kt`
- Test: `app/src/test/java/com/metaself/app/domain/movement/TypedReadingTest.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/day/DayViewModelTest.kt`

- [ ] **Step 1: Write the failing tests.** Create
  `app/src/test/java/com/metaself/app/domain/movement/TypedReadingTest.kt`:

```kotlin
package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test

/**
 * The typed workouts as the day's third reading (D60, D77). Invented, round figures on `aProfile()`'s
 * 80 kg: 8,000 steps are 8,000 × 0.000375 × 80 = 240 kcal.
 */
class TypedReadingTest {

    @Test
    fun `a day's visible typed workouts are summed, since two sessions are two things done`() {
        val workouts = listOf(
            aTypedWorkout(id = 1, energyKcal = 150),
            aTypedWorkout(id = 2, energyKcal = 100),
            aTypedWorkout(id = 3, energyKcal = 400, hidden = true),
            aTypedWorkout(id = 4, energyKcal = 500).copy(source = WorkoutSource.SYNCED),
            aTypedWorkout(id = 5, energyKcal = null, energySource = EnergySource.NONE),
            aTypedWorkout(id = 6, epochDay = TEST_EPOCH_DAY - 1, energyKcal = 90),
        )

        assertThat(TypedReading.kcalByDay(workouts))
            .containsExactly(TEST_EPOCH_DAY, 250, TEST_EPOCH_DAY - 1, 90)
    }

    @Test
    fun `a day with typed workouts but no energy has no reading`() {
        assertThat(TypedReading.kcalByDay(listOf(aTypedWorkout(energyKcal = null, energySource = EnergySource.NONE))))
            .isEmpty()
    }

    /** Plan design question 13: a day the phone did not record is not made up. */
    @Test
    fun `typed kcal is laid into the days the phone recorded, and no day is added`() {
        val history = listOf(DayMovement(TEST_EPOCH_DAY - 1, steps = 8_000), DayMovement(TEST_EPOCH_DAY, steps = 8_000))

        val merged = TypedReading.merge(history, mapOf(TEST_EPOCH_DAY to 300, TEST_EPOCH_DAY - 5 to 200))

        assertThat(merged.map { it.epochDay }).containsExactly(TEST_EPOCH_DAY - 1, TEST_EPOCH_DAY).inOrder()
        assertThat(merged.map { it.typedWorkoutsKcal }).containsExactly(0, 300).inOrder()
    }

    /** D60: the largest of three, never their sum. */
    @Test
    fun `a typed workout joins the maximum and is never added to it`() {
        val day = DayMovement(TEST_EPOCH_DAY, steps = 8_000, activeKcal = 400)

        val smaller = TypedReading.merge(listOf(day), mapOf(TEST_EPOCH_DAY to 300)).single()
        val larger = TypedReading.merge(listOf(day), mapOf(TEST_EPOCH_DAY to 500)).single()

        assertThat(ActivityEnergy.of(smaller, 80.0)).isEqualTo(ActivityEnergy(400, MovementSource.ACTIVE_CALORIES))
        assertThat(ActivityEnergy.of(larger, 80.0)).isEqualTo(ActivityEnergy(500, MovementSource.TYPED_WORKOUT))
    }
}
```

  In `DayViewModelTest.kt`:

  (a) add imports:

```kotlin
import com.metaself.app.data.health.FakeTypedWorkouts
import com.metaself.app.data.health.TypedWorkouts
import com.metaself.app.domain.movement.ActivityEnergy
import com.metaself.app.domain.movement.MovementSource
import com.metaself.app.domain.movement.MovementToday
import com.metaself.app.domain.movement.aTypedWorkout
```

  (b) in the private `viewModel(...)` helper, add two parameters after `problems: ProblemLog =
  ProblemLog.NONE,`:

```kotlin
        // No Health Connect in a test, which the app treats exactly as it treats an ordinary day.
        steps: StepSource = object : StepSource {
            override suspend fun access() = StepAccess.UNAVAILABLE
            override suspend fun history(from: LocalDate, to: LocalDate) = emptyList<DayMovement>()
        },
        typedWorkouts: TypedWorkouts = TypedWorkouts.NONE,
```

  replace the inline `steps = object : StepSource { … },` argument (and its comment) with
  `steps = steps,`, and add `typedWorkouts = typedWorkouts,` after `problems = problems,`.

  (c) add the tests and helpers:

```kotlin
    /**
     * D77 (D60 made live). Invented, round figures on `aProfile()`'s 80 kg: thirty past days of 8,000
     * steps are 240 kcal each (8,000 × 0.000375 × 80), so the usual day is 240; today the band says
     * 400. The cap for losing 0.5 kg a week is 275. A typed workout joins as a third reading, the
     * largest decides, and nothing is added.
     */
    @Test
    fun `a typed workout is the third reading of today's movement, never added to the others`() = runTest {
        val typed = FakeTypedWorkouts()
        val viewModel = viewModel(steps = bandDays(todayActiveKcal = 400), typedWorkouts = typed)
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        assertThat(movementOf(viewModel).energy).isEqualTo(ActivityEnergy(400, MovementSource.ACTIVE_CALORIES))
        assertThat(movementOf(viewModel).credit!!.kcal).isEqualTo(120) // (400 − 240) × 0.75

        typed.workouts.value = listOf(aTypedWorkout(energyKcal = 300))
        advanceUntilIdle()

        assertThat(movementOf(viewModel).energy).isEqualTo(ActivityEnergy(400, MovementSource.ACTIVE_CALORIES))
        assertThat(movementOf(viewModel).credit!!.kcal).isEqualTo(120)

        typed.workouts.value = listOf(aTypedWorkout(energyKcal = 500))
        advanceUntilIdle()

        assertThat(movementOf(viewModel).energy).isEqualTo(ActivityEnergy(500, MovementSource.TYPED_WORKOUT))
        assertThat(movementOf(viewModel).credit!!.kcal).isEqualTo(195) // (500 − 240) × 0.75

        job.cancel()
    }

    @Test
    fun `a hidden typed workout is no reading at all`() = runTest {
        val typed = FakeTypedWorkouts(listOf(aTypedWorkout(energyKcal = 500, hidden = true)))
        val viewModel = viewModel(steps = bandDays(todayActiveKcal = 400), typedWorkouts = typed)
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        assertThat(movementOf(viewModel).energy).isEqualTo(ActivityEnergy(400, MovementSource.ACTIVE_CALORIES))

        job.cancel()
    }

    /** Plan design question 14: routine typed workouts raise the usual day, as routine walks do. */
    @Test
    fun `typed workouts on past days raise the usual day`() = runTest {
        val past = (1..30).map { back -> aTypedWorkout(id = back.toLong(), epochDay = TEST_EPOCH_DAY - back, energyKcal = 300) }
        val viewModel = viewModel(steps = bandDays(todayActiveKcal = 400), typedWorkouts = FakeTypedWorkouts(past))
        val job = launch { viewModel.state.collect {} }
        advanceUntilIdle()

        assertThat(movementOf(viewModel).normalEnergyKcal).isEqualTo(300)
        assertThat(movementOf(viewModel).credit!!.kcal).isEqualTo(75) // (400 − 300) × 0.75

        job.cancel()
    }

    private fun movementOf(viewModel: DayViewModel): MovementToday =
        (viewModel.state.value as DayUiState.Ready).movement!!

    /** Thirty past days of 8,000 steps, and today's 8,000 with the band's [todayActiveKcal]. Invented. */
    private fun bandDays(todayActiveKcal: Int) = object : StepSource {
        override suspend fun access() = StepAccess.GRANTED
        override suspend fun history(from: LocalDate, to: LocalDate) =
            (TEST_EPOCH_DAY - 30..TEST_EPOCH_DAY).map { day ->
                DayMovement(
                    epochDay = day,
                    steps = 8_000,
                    activeKcal = if (day == TEST_EPOCH_DAY) todayActiveKcal else null,
                )
            }
    }
```

  (The typed-past-days test: the window is the thirty days before today; each is max(240, 300) =
  300, so the median is 300. The helper's window is `(today − 30)..<today`, which is exactly those
  thirty days.)

- [ ] **Step 2: Run them to see them fail.**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.movement.TypedReadingTest" --tests "com.metaself.app.ui.screen.day.DayViewModelTest" > /tmp/ms-typed.log 2>&1; echo "exit $?"`
  Expected: exit non-zero; `Unresolved reference: TypedReading` and `No parameter with name
  'typedWorkouts'`.

- [ ] **Step 3: Implement.** Create
  `app/src/main/java/com/metaself/app/domain/movement/TypedReading.kt`:

```kotlin
package com.metaself.app.domain.movement

/**
 * The owner's typed workouts as each day's third reading (D60, D77): what the day's visible typed
 * workouts cost, summed — two typed sessions are two different things done — and laid beside that
 * day's steps and band figure, never added to them. [ActivityEnergy.of] takes the largest.
 */
object TypedReading {

    /** Kcal by day of the visible typed workouts that carry a figure. A day with none has no entry. */
    fun kcalByDay(workouts: List<Workout>): Map<Long, Int> = workouts
        .filter { it.source == WorkoutSource.TYPED && !it.hidden }
        .groupBy { it.epochDay }
        .mapValues { (_, day) -> day.sumOf { it.energyKcal ?: 0 } }
        .filterValues { it > 0 }

    /**
     * [history] with each day's typed kcal laid in. **Only the days the phone recorded:** a day with no
     * record is absent, not a day of zero steps, and [NormalDay] must keep ignoring it — so a typed
     * workout on such a day is not a reading at all.
     */
    fun merge(history: List<DayMovement>, kcalByDay: Map<Long, Int>): List<DayMovement> =
        history.map { day -> day.copy(typedWorkoutsKcal = kcalByDay[day.epochDay] ?: 0) }
}
```

  In `MovementCredit.kt`, the KDoc of `typedWorkoutsKcal` becomes:

```kotlin
    /**
     * What the workouts the owner typed for this day cost, by [MetEstimate] or his own figure —
     * summed across them, since two typed sessions are two different things done. Filled by
     * [TypedReading.merge] (D77). It is a THIRD reading beside steps and the band, never added to
     * either (D60).
     */
```

  In `DayViewModel.kt`:

  (a) imports: `com.metaself.app.data.health.TypedWorkouts`,
  `com.metaself.app.domain.movement.TypedReading`, `kotlinx.coroutines.flow.Flow`,
  `kotlinx.coroutines.flow.catch` (both flow imports are absent today).

  (b) constructor, after `healthRecord`:

```kotlin
    /** The owner's typed workouts, the day's third movement reading (D60, D77). Defaulted like [healthRecord]. */
    private val typedWorkouts: TypedWorkouts = TypedWorkouts.NONE,
```

  (c) replace the private `Movement` data class with:

```kotlin
    /**
     * What was read from Health Connect: every day's steps, and what a usual day looks like — with the
     * owner's typed workouts laid in by [withTyped] (D77).
     *
     * @property history the days as read, before any typed workout is laid in; kept so the usual day
     *   can be worked out again when a workout is typed while the app is open.
     * @property readOn the day the read was made: the usual day is measured up to it.
     */
    private data class Movement(
        val byDay: Map<Long, DayMovement>,
        val normalSteps: Int?,
        val normalEnergyKcal: Int?,
        val weightKg: Double,
        val capKcal: Int,
        val history: List<DayMovement>,
        val readOn: Long,
    ) {
        /**
         * Each recorded day with its typed kcal as the third reading, and the usual day's energy worked
         * out again with them in it: a routine typed workout raises the usual, as a routine walk does.
         * The step median does not move — typed workouts are not steps.
         */
        fun withTyped(kcalByDay: Map<Long, Int>): Movement {
            if (kcalByDay.isEmpty()) return this
            val merged = TypedReading.merge(history, kcalByDay)
            return copy(
                byDay = merged.associateBy { it.epochDay },
                normalEnergyKcal = NormalDay.energyKcal(merged, readOn, weightKg),
            )
        }
    }
```

  (d) in `readMovement()`, the `Movement(...)` gains, after `capKcal = MovementCap.forGoal(profile.goal),`:

```kotlin
                history = history,
                readOn = todayDate.toEpochDay(),
```

  (e) immediately above `val state: StateFlow<DayUiState> = combine(`, add:

```kotlin
    /**
     * The typed workouts of the usual-day window, as kcal by day (D77) — observed, so a workout typed on
     * the Movement screen moves today's credit without a refresh. A read that fails is logged and
     * counts as none (D8): nothing about it stops the day screen.
     */
    private val typedKcal: Flow<Map<Long, Int>> = _calendarToday.flatMapLatest { day ->
        typedWorkouts.observe(day - NormalDay.WINDOW_DAYS, day)
            .map(TypedReading::kcalByDay)
            .catch { failure ->
                problems.record(TYPED_PROBLEM_KIND, failure.message ?: failure::class.java.simpleName)
                emit(emptyMap())
            }
    }

    /** What was read, with the typed workouts laid in (D77). */
    private val movementWithTyped: Flow<Movement?> =
        combine(_movement, typedKcal) { movement, typed -> movement?.withTyped(typed) }
```

  (f) in the `state` chain, replace
  `.combine(_movement) { inputs, movement -> inputs.copy(movement = movement) }` with
  `.combine(movementWithTyped) { inputs, movement -> inputs.copy(movement = movement) }`.

  (g) in the `private companion object`, add:

```kotlin
        /** The problem log's kind for a typed-workout read that failed: the Movement screen's own. */
        const val TYPED_PROBLEM_KIND = "movement"
```

  `grep -n "_movement" app/src/main/java/com/metaself/app/ui/screen/day/DayViewModel.kt` must then show
  only the declaration, the encouragement's `_movement.value` (steps only — typed workouts are not
  steps), the assignment in `readMovement`, and the new `combine`.

- [ ] **Step 4: Run them to see them pass.** Same command as Step 2. Expected: exit 0; every
  `DayViewModelTest` test passes, the three new ones included.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/movement/TypedReading.kt \
  app/src/main/java/com/metaself/app/domain/movement/MovementCredit.kt \
  app/src/main/java/com/metaself/app/ui/screen/day/DayViewModel.kt \
  app/src/test/java/com/metaself/app/domain/movement/TypedReadingTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/day/DayViewModelTest.kt
git commit -m "feat: a typed workout is the day's third movement reading, never an addition (D77)" \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The step line's rule follows the deciding reading (public issue #58)

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/movement/MovementToday.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/day/StepBar.kt` (comment only)
- Test: `app/src/test/java/com/metaself/app/domain/movement/MovementTodayTest.kt`

Only unit tests: a render here cannot see a rule's length or its colour (CLAUDE.md, Testing), and
`StepBar` draws exactly `fractionOfUsual` and `aboveUsual`, so those two are what is tested. The look
is a phone check.

- [ ] **Step 1: Write the failing tests.** Add to `MovementTodayTest.kt`:

```kotlin
    /** Public issue #58, invented figures: few steps, but the band's figure beat the usual day. */
    @Test
    fun `on a day the band decided, the rule is filled and marked by the band's figure`() {
        val swim = MovementToday(
            steps = 1_000, normalSteps = 8_000,
            energy = ActivityEnergy(480, MovementSource.ACTIVE_CALORIES), normalEnergyKcal = 240,
        )

        assertThat(swim.decidedByEnergy).isTrue()
        assertThat(swim.fractionOfUsual).isEqualTo(1f)
        assertThat(swim.aboveUsual).isTrue()
    }

    @Test
    fun `on a day a typed workout decided but fell short of usual, the rule is part-filled in kcal`() {
        val short = MovementToday(
            steps = 1_000, normalSteps = 8_000,
            energy = ActivityEnergy(120, MovementSource.TYPED_WORKOUT), normalEnergyKcal = 240,
        )

        assertThat(short.fractionOfUsual).isWithin(1e-6f).of(0.5f)
        assertThat(short.aboveUsual).isFalse()
    }

    /** 4,000 steps on 80 kg are 4,000 × 0.000375 × 80 = 120 kcal: the steps decided. */
    @Test
    fun `on a day the steps decided, the rule is the steps, as before`() {
        val walk = MovementToday(
            steps = 4_000, normalSteps = 8_000,
            energy = ActivityEnergy(120, MovementSource.STEPS), normalEnergyKcal = 240,
        )

        assertThat(walk.fractionOfUsual).isWithin(1e-6f).of(0.5f)
        assertThat(walk.aboveUsual).isFalse()
    }
```

- [ ] **Step 2: Run to see it fail.**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.movement.MovementTodayTest" > /tmp/ms-typed.log 2>&1; echo "exit $?"`
  Expected: exit non-zero; the band test fails (`fractionOfUsual` is 0.125 from the steps, not 1.0),
  and so does the typed one.

- [ ] **Step 3: Implement.** In `MovementToday.kt`, replace `aboveUsual` and `fractionOfUsual` with:

```kotlin
    /**
     * Whether the day went past a usual one, in the reading that decided it (public issue #58): on a
     * day the band or a typed workout decided, its kcal against the usual day's; otherwise the steps.
     */
    val aboveUsual: Boolean
        get() {
            val energy = energy
            val usualEnergy = normalEnergyKcal
            if (decidedByEnergy && energy != null && usualEnergy != null) return energy.kcal > usualEnergy
            return normalSteps != null && steps > normalSteps
        }

    /**
     * How full the rule is, against a usual day, in the reading that decided it (public issue #58) —
     * a swim is not drawn as a near-empty rule under "kcal more movement than your usual".
     *
     * Capped at one: a day that doubles his usual fills the rule and stops, because a rule that keeps
     * a huge day in scale makes every ordinary day look like nothing.
     */
    val fractionOfUsual: Float
        get() {
            val energy = energy
            val usualEnergy = normalEnergyKcal
            if (decidedByEnergy && energy != null && usualEnergy != null) {
                if (usualEnergy <= 0) return 0f
                return (energy.kcal.toFloat() / usualEnergy).coerceIn(0f, 1f)
            }
            val usual = normalSteps?.takeIf { it > 0 } ?: return 0f
            return (steps.toFloat() / usual).coerceIn(0f, 1f)
        }
```

  In `StepBar.kt`, the class KDoc's paragraph "The rule fills to a usual day and stops…" gains one
  sentence at its end: `On a day the band or a typed workout decided, the rule and its green are that
  reading's kcal against the usual day's (public issue #58); the count above stays steps.`

- [ ] **Step 4: Run to see it pass.** Same command. Expected: exit 0, six tests.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/domain/movement/MovementToday.kt \
  app/src/main/java/com/metaself/app/ui/day/StepBar.kt \
  app/src/test/java/com/metaself/app/domain/movement/MovementTodayTest.kt
git commit -m "fix: the step line's rule follows the reading that decided the day (public issue #58)" \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The sheet's state and the view model (D76)

Split from the sheet's drawing (Task 7) because the view model is JUnit 5 and runs in seconds, while
the sheet is Robolectric.

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/screen/movement/MovementUiState.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/screen/movement/MovementViewModel.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/movement/MovementViewModelTest.kt`

- [ ] **Step 1: Write the failing tests.** In `MovementViewModelTest.kt`:

  (a) add imports:

```kotlin
import com.metaself.app.data.health.FakeTypedWorkouts
import com.metaself.app.data.health.TypedWorkouts
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.Now
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.movement.aTypedWorkout
import com.metaself.app.domain.profile.aProfile
import java.time.Instant
import java.time.ZoneId
```

  (b) add a clock beside `today`:

```kotlin
    /** 15:00 UTC on TEST_EPOCH_DAY; only its clock time reaches a saved workout. */
    private val now = Now { TEST_EPOCH_DAY * 86_400_000L + 15 * 3_600_000L }
```

  (c) replace the `viewModel(...)` helper with:

```kotlin
    private fun viewModel(
        record: MovementRecord = FakeRecord(),
        typed: TypedWorkouts = FakeTypedWorkouts(),
        profiles: ProfileRepository = FakeProfileRepository(aProfile()),
        problems: ProblemLog = ProblemLog.NONE,
    ) = MovementViewModel(record, InMemoryMealRepository(), today, problems, typed, profiles, now)
```

  and in the three tests that construct `MovementViewModel(...)` directly, append
  `, FakeTypedWorkouts(), FakeProfileRepository(aProfile()), now` to the argument list.

  (d) add the tests:

```kotlin
    @Test
    fun `logging a workout opens an empty sheet on today, priced on the profile's weight`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }

        model.logWorkout()

        val sheet = model.state.first { it.sheet != null }.sheet!!
        assertThat(sheet.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(sheet.weightKg).isEqualTo(80.0)
        assertThat(sheet.draft).isEqualTo(WorkoutDraft())
        assertThat(sheet.editing).isNull()
    }

    /** D76: onto the day that is open; today when none is. */
    @Test
    fun `a workout is logged onto the open day, and onto today when none is open`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }

        model.toggle(20_698L)
        model.logWorkout()
        assertThat(model.state.first { it.sheet != null }.sheet!!.epochDay).isEqualTo(20_698L)

        model.closeSheet()
        model.toggle(20_698L)
        model.logWorkout()
        assertThat(model.state.first { it.sheet != null && it.openDay == null }.sheet!!.epochDay)
            .isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `with no profile the sheet opens with no weight`() = runTest {
        val model = viewModel(profiles = FakeProfileRepository(null))
        model.state.first { it.week != null }

        model.logWorkout()

        assertThat(model.state.first { it.sheet != null }.sheet!!.weightKg).isNull()
    }

    /** 45 minutes of moderate strength on 80 kg: (3.5 − 1) × 80 × 0.75 = 150. */
    @Test
    fun `saving logs a typed workout on that day with its estimate, and closes the sheet`() = runTest {
        val typed = FakeTypedWorkouts()
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        model.logWorkout()
        model.state.first { it.sheet != null }

        model.changeDraft(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"))
        model.saveWorkout()

        model.state.first { it.sheet == null }
        val saved = typed.logged.single()
        assertThat(saved.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(saved.source).isEqualTo(WorkoutSource.TYPED)
        assertThat(saved.energyKcal).isEqualTo(150)
        assertThat(saved.energySource).isEqualTo(EnergySource.MET_ESTIMATE)
        assertThat(Instant.ofEpochMilli(saved.startedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay())
            .isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `a draft that cannot be saved is not, and the sheet stays`() = runTest {
        val typed = FakeTypedWorkouts()
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        model.logWorkout()
        model.state.first { it.sheet != null }

        model.saveWorkout()
        advanceUntilIdle()

        assertThat(typed.logged).isEmpty()
        assertThat(model.state.first { it.sheet != null }.sheet!!.draft).isEqualTo(WorkoutDraft())
    }

    /** One hour of moderate strength on 80 kg: (3.5 − 1) × 80 × 1 = 200. */
    @Test
    fun `tapping a typed workout opens it filled, and saving changes it in place`() = runTest {
        val typed = FakeTypedWorkouts()
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        val workout = aTypedWorkout(id = 9, epochDay = 20_698, startedAtMillis = 1_000)

        model.openWorkout(workout)
        val sheet = model.state.first { it.sheet != null }.sheet!!
        assertThat(sheet.editing).isEqualTo(workout)
        assertThat(sheet.epochDay).isEqualTo(20_698L)
        assertThat(sheet.draft).isEqualTo(WorkoutDraft.from(workout))

        model.changeDraft(sheet.draft.copy(minutes = "60"))
        model.saveWorkout()

        model.state.first { it.sheet == null }
        val changed = typed.changed.single()
        assertThat(changed.id).isEqualTo(9L)
        assertThat(changed.epochDay).isEqualTo(20_698L)
        assertThat(changed.startedAtMillis).isEqualTo(1_000L)
        assertThat(changed.durationMinutes).isEqualTo(60)
        assertThat(changed.energyKcal).isEqualTo(200)
    }

    /** D76: a synced workout is not editable here. */
    @Test
    fun `a synced workout does not open the sheet`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }

        model.openWorkout(aTypedWorkout().copy(source = WorkoutSource.SYNCED))
        advanceUntilIdle()

        assertThat(model.state.first { it.week != null }.sheet).isNull()
    }

    @Test
    fun `deleting a typed workout deletes it and closes the sheet`() = runTest {
        val typed = FakeTypedWorkouts()
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        val workout = aTypedWorkout(id = 9)
        model.openWorkout(workout)
        model.state.first { it.sheet != null }

        model.deleteWorkout()

        model.state.first { it.sheet == null }
        assertThat(typed.deleted).containsExactly(workout)
    }

    /** D8: said on the sheet, logged, never thrown; what was typed is kept. */
    @Test
    fun `a save that fails is said on the sheet and logged, and the sheet stays open`() = runTest {
        val typed = FakeTypedWorkouts().apply { failing = IllegalStateException("disk full") }
        val problems = RecordingProblemLog()
        val model = viewModel(typed = typed, problems = problems)
        model.state.first { it.week != null }
        model.logWorkout()
        model.state.first { it.sheet != null }
        val draft = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45")

        model.changeDraft(draft)
        model.saveWorkout()

        val sheet = model.state.first { it.sheet?.failed == true }.sheet!!
        assertThat(sheet.saving).isFalse()
        assertThat(sheet.draft).isEqualTo(draft)
        assertThat(problems.recorded.single().kind).isEqualTo("movement")
    }
```

- [ ] **Step 2: Run to see it fail.**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.movement.MovementViewModelTest" > /tmp/ms-typed.log 2>&1; echo "exit $?"`
  Expected: exit non-zero; `Unresolved reference: logWorkout`, `sheet`, and too many arguments to
  `MovementViewModel`.

- [ ] **Step 3: Implement.** Replace `MovementUiState.kt` with:

```kotlin
package com.metaself.app.ui.screen.movement

import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutDraft

/**
 * @property week null until the first read has answered, and when it failed.
 * @property openDay the one open day (D73), which shows its detail beneath its summary; null when the
 *   owner has closed every day.
 * @property unreadable the record could not be read; the screen says so (D8).
 * @property sheet the log-a-workout sheet, when it is open (D76).
 */
data class MovementUiState(
    val week: MovementWeek? = null,
    val openDay: Long? = null,
    val unreadable: Boolean = false,
    val sheet: WorkoutSheetState? = null,
)

/**
 * The log-a-workout sheet (D76).
 *
 * @property epochDay the day the workout goes on: the open day when it was opened, or the typed
 *   workout's own day.
 * @property weightKg the profile's weight, which the estimate is priced on; null with no profile.
 * @property editing the typed workout being changed; null when logging a new one.
 * @property saving a write is under way; Save and Delete wait for it.
 * @property failed the last write failed; the sheet says so and keeps what was typed (D8).
 */
data class WorkoutSheetState(
    val draft: WorkoutDraft,
    val epochDay: Long,
    val weightKg: Double?,
    val editing: Workout? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
)
```

  In `MovementViewModel.kt`:

  (a) imports: `com.metaself.app.data.health.TypedWorkouts`,
  `com.metaself.app.data.profile.ProfileRepository`, `com.metaself.app.data.time.Now`,
  `com.metaself.app.domain.movement.Workout`, `com.metaself.app.domain.movement.WorkoutDraft`,
  `com.metaself.app.domain.movement.WorkoutSource`, `kotlinx.coroutines.CancellationException`,
  `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.launch`, `java.time.ZoneId`.

  (b) the constructor gains, after `private val problems: ProblemLog,`:

```kotlin
    private val typed: TypedWorkouts,
    private val profiles: ProfileRepository,
    private val now: Now,
```

  (c) the class KDoc gains a paragraph: `It also holds the log-a-workout sheet (D76): which workout it
  is for, what has been typed, and the write that saves it. A typed workout reaches the week through
  the record it is written to, like any other workout.`

  (d) after `private val openDay = …`, add:

```kotlin
    /** The log-a-workout sheet, when it is open (D76). */
    private val sheet = MutableStateFlow<WorkoutSheetState?>(null)
```

  (e) replace the `state` initialiser's `combine(week, openDay) { built, open -> … }` with:

```kotlin
    val state: StateFlow<MovementUiState> = combine(week, openDay, sheet) { built, open, sheetNow ->
        if (built == null) {
            MovementUiState(unreadable = true, openDay = open, sheet = sheetNow)
        } else {
            MovementUiState(week = built, openDay = open, sheet = sheetNow)
        }
    }
```

  keeping the `.stateIn(...)` as it is.

  (f) after `lookedAt()`, add:

```kotlin
    /** "Log a workout" (D76): an empty sheet, for the open day — today when none is open. */
    fun logWorkout() {
        val day = openDay.value ?: calendarToday.value
        viewModelScope.launch {
            sheet.value = WorkoutSheetState(draft = WorkoutDraft(), epochDay = day, weightKg = weight())
        }
    }

    /** A typed workout's line was tapped: the sheet, filled. A synced workout is not editable here (D76). */
    fun openWorkout(workout: Workout) {
        if (workout.source != WorkoutSource.TYPED) return
        viewModelScope.launch {
            sheet.value = WorkoutSheetState(
                draft = WorkoutDraft.from(workout),
                epochDay = workout.epochDay,
                weightKg = weight(),
                editing = workout,
            )
        }
    }

    fun changeDraft(draft: WorkoutDraft) {
        sheet.update { it?.copy(draft = draft, failed = false) }
    }

    fun closeSheet() {
        sheet.value = null
    }

    /**
     * Save: a new workout onto the sheet's day at the clock time now (D76), or the changed one in
     * place, keeping its day, start and hidden flag. Nothing happens while the draft cannot be saved.
     */
    fun saveWorkout() {
        val open = sheet.value ?: return
        if (open.saving) return
        val editing = open.editing
        val workout = if (editing == null) {
            open.draft.toWorkout(
                id = 0,
                epochDay = open.epochDay,
                startedAtMillis = WorkoutDraft.startOn(open.epochDay, now(), ZoneId.systemDefault()),
                weightKg = open.weightKg,
            )
        } else {
            open.draft.toWorkout(editing.id, editing.epochDay, editing.startedAtMillis, open.weightKg)
                ?.copy(hidden = editing.hidden)
        } ?: return
        write(open) { if (editing == null) typed.log(workout) else typed.change(workout) }
    }

    /** Delete, from the sheet of a typed workout being changed: at once, then Undo (plan question 8). */
    fun deleteWorkout() {
        val open = sheet.value ?: return
        val editing = open.editing ?: return
        if (open.saving) return
        write(open) { typed.delete(editing) }
    }

    /** One write from the sheet. The sheet closes when it lands, and says so when it does not (D8). */
    private fun write(open: WorkoutSheetState, action: suspend () -> Unit) {
        sheet.value = open.copy(saving = true, failed = false)
        viewModelScope.launch {
            try {
                action()
                sheet.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                problems.record(PROBLEM_KIND, "workout not saved: " + (failure.message ?: failure::class.java.simpleName))
                sheet.update { it?.copy(saving = false, failed = true) }
            }
        }
    }

    /** The profile's weight, which the MET estimate is priced on (D76); null with no profile. */
    private suspend fun weight(): Double? = profiles.profile.first()?.weightKg
```

- [ ] **Step 4: Run to see it pass.** Same command as Step 2. Expected: exit 0, every test in the class.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/screen/movement/MovementUiState.kt \
  app/src/main/java/com/metaself/app/ui/screen/movement/MovementViewModel.kt \
  app/src/test/java/com/metaself/app/ui/screen/movement/MovementViewModelTest.kt
git commit -m "feat: the Movement screen logs, changes and deletes a typed workout (D76)" \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The sheet

**Files:**
- Create: `app/src/main/java/com/metaself/app/ui/screen/movement/WorkoutSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/com/metaself/app/ui/ComposeRender.kt` (`isSelected`)
- Test: `app/src/test/java/com/metaself/app/ui/screen/movement/WorkoutSheetRenderTest.kt`

What a render can prove here: which words and fields are drawn, what is selected and enabled, what a
tap asks for, and that Save is placed above the fields inside a short box. What it cannot: the modal
chrome (a sheet draws in a window of its own, which `ComposeRender` does not walk — the reason
`MealNamingSheet` is split the same way), the keyboard, touch heights, wrapping. Those are phone checks.

- [ ] **Step 1: The render helper.** In `ComposeRender.kt`, after `stateOf`, add:

```kotlin
    /**
     * Whether the node matching [prefix] is told to a screen reader as selected — a chosen chip or
     * segment — or null when it says nothing about being selected.
     *
     * Reads the LAST render, so call [texts] first.
     */
    fun isSelected(prefix: String): Boolean? =
        nodeStartingWith(prefix).config.getOrNull(SemanticsProperties.Selected)
```

- [ ] **Step 2: Write the failing test.** Create
  `app/src/test/java/com/metaself/app/ui/screen/movement/WorkoutSheetRenderTest.kt`:

```kotlin
package com.metaself.app.ui.screen.movement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.Effort
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.aTypedWorkout
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4. The sheet's content, drawn on its own (see
 * `WorkoutSheet`). 80 kg is `aProfile()`'s; every figure is invented, and the kcal are worked in
 * `WorkoutDraftTest`.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutSheetRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `a new sheet offers six kinds, starts on Moderate, and cannot be saved yet`() {
        val texts = draw(WorkoutDraft())

        assertThat(texts).containsAtLeast(
            "Log a workout", "Save", "Run", "Walk", "Cycle", "Swim", "Strength", "Other",
            "Minutes", "Easy", "Moderate", "Hard", "set it yourself", "Note", "Not now",
        )
        assertThat(render.isSelected("Moderate")).isTrue()
        assertThat(render.isSelected("Easy")).isFalse()
        assertThat(render.isSelected("Run")).isFalse()
        assertThat(render.isEnabled("Save")).isFalse()
        assertThat(texts.none { it.startsWith("Distance") }).isTrue()
        assertThat(texts).doesNotContain("Delete this workout")
    }

    @Test
    fun `a run offers a distance and shows its pace and estimate live`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5"))

        assertThat(texts).contains("Distance (km)")
        assertThat(texts).contains("6:00 /km")
        assertThat(texts).contains("about 332 kcal, estimated from the pace")
        assertThat(render.isSelected("Run")).isTrue()
        assertThat(render.isEnabled("Save")).isTrue()
        assertThat(render.fieldTexts()).containsAtLeast("30", "5")
    }

    /** D78: pace for runs only. */
    @Test
    fun `a walk offers a distance but shows no pace`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.WALK, minutes = "50", distanceKm = "4"))

        assertThat(texts).contains("Distance (km)")
        assertThat(texts.none { it.endsWith("/km") }).isTrue()
    }

    @Test
    fun `strength has no distance, and its estimate is from the effort`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", distanceKm = "5"))

        assertThat(texts.none { it.startsWith("Distance") }).isTrue()
        assertThat(texts).contains("about 150 kcal, estimated from the effort")
    }

    @Test
    fun `choosing a kind asks for it`() {
        var asked: WorkoutDraft? = null
        draw(WorkoutDraft(), onDraft = { asked = it })

        render.click("Swim")

        assertThat(asked!!.kind).isEqualTo(WorkoutKind.SWIM)
    }

    /** Activity spec §8 item 4: effort can be changed, and choosing the chosen one keeps it. */
    @Test
    fun `effort can be changed but not cleared`() {
        var asked: WorkoutDraft? = null
        draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), onDraft = { asked = it })

        render.click("Hard")
        assertThat(asked!!.effort).isEqualTo(Effort.HARD)

        render.click("Moderate")
        assertThat(asked!!.effort).isEqualTo(Effort.MODERATE)
    }

    @Test
    fun `set it yourself asks for a figure of his own`() {
        var asked: WorkoutDraft? = null
        draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), onDraft = { asked = it })

        render.click("set it yourself")

        assertThat(asked!!.ownEnergy).isTrue()
    }

    @Test
    fun `with a figure of his own there is a field for it and no estimate`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45", ownEnergy = true, energyKcal = "300"))

        assertThat(texts).contains("Energy (kcal)")
        assertThat(texts).contains("use the estimate")
        assertThat(render.fieldTexts()).contains("300")
        assertThat(texts.none { it.startsWith("about ") }).isTrue()
    }

    @Test
    fun `with no weight it says why there is no estimate`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), weightKg = null)

        assertThat(texts).contains("No estimate: the profile has no weight")
    }

    @Test
    fun `a typed workout opened to change has Delete, and pressing it asks for it`() {
        var deleted = false
        val texts = draw(WorkoutDraft.from(aTypedWorkout()), editing = aTypedWorkout(), onDelete = { deleted = true })

        assertThat(texts).contains("Change this workout")
        render.click("Delete this workout")
        assertThat(deleted).isTrue()
    }

    @Test
    fun `a save that failed says so`() {
        val texts = draw(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"), failed = true)

        assertThat(texts).contains("Not saved; Recent problems says why.")
    }

    /**
     * D76: Save kept in view. It sits in the title row, above every field, so neither a long sheet nor
     * a keyboard rising from the bottom can cover it (plan design question 1). A short box stands in
     * for the phone; only relative geometry is asserted (CLAUDE.md). A node pushed out of the window
     * reads 0, so Save is asserted to be below the box's top as well as above the first field.
     */
    @Test
    @Config(qualifiers = "+h640dp")
    fun `Save stays in view above a long sheet`() {
        val longNote = List(40) { "line" }.joinToString("\n")
        render.texts {
            Box(Modifier.height(SHORT_SHEET_DP.dp)) {
                WorkoutSheetContent(
                    sheet = WorkoutSheetState(
                        draft = WorkoutDraft(kind = WorkoutKind.RUN, minutes = "30", distanceKm = "5", note = longNote),
                        epochDay = TEST_EPOCH_DAY,
                        weightKg = 80.0,
                    ),
                    onDraft = {}, onSave = {}, onDelete = {}, onCancel = {},
                )
            }
        }

        val save = render.topDp("Save")
        assertThat(save).isIn(Range.open(0, SHORT_SHEET_DP))
        assertThat(save).isLessThan(render.topDp("Minutes"))
    }

    private fun draw(
        draft: WorkoutDraft,
        weightKg: Double? = 80.0,
        editing: Workout? = null,
        failed: Boolean = false,
        onDraft: (WorkoutDraft) -> Unit = {},
        onDelete: () -> Unit = {},
    ): List<String> = render.texts {
        WorkoutSheetContent(
            sheet = WorkoutSheetState(
                draft = draft, epochDay = TEST_EPOCH_DAY, weightKg = weightKg, editing = editing, failed = failed,
            ),
            onDraft = onDraft,
            onSave = {},
            onDelete = onDelete,
            onCancel = {},
        )
    }

    private companion object {
        /** Shorter than a sheet with a forty-line note needs. */
        const val SHORT_SHEET_DP = 400
    }
}
```

- [ ] **Step 3: Run to see it fail.**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.movement.WorkoutSheetRenderTest" > /tmp/ms-typed.log 2>&1; echo "exit $?"`
  Expected: exit non-zero; `Unresolved reference: WorkoutSheetContent`.

- [ ] **Step 4: Implement.** In `strings.xml`, after `movement_unreadable`, add:

```xml
    <string name="movement_log_workout">Log a workout</string>
    <string name="movement_workout_change">change this workout</string>
    <string name="workout_sheet_new">Log a workout</string>
    <string name="workout_sheet_edit">Change this workout</string>
    <string name="workout_kind_run">Run</string>
    <string name="workout_kind_walk">Walk</string>
    <string name="workout_kind_cycle">Cycle</string>
    <string name="workout_kind_swim">Swim</string>
    <string name="workout_kind_strength">Strength</string>
    <string name="workout_kind_other">Other</string>
    <string name="workout_minutes">Minutes</string>
    <string name="workout_distance">Distance (km)</string>
    <string name="workout_effort_easy">Easy</string>
    <string name="workout_effort_moderate">Moderate</string>
    <string name="workout_effort_hard">Hard</string>
    <string name="workout_energy_own">set it yourself</string>
    <string name="workout_energy_estimate">use the estimate</string>
    <string name="workout_energy_label">Energy (kcal)</string>
    <string name="workout_note">Note</string>
    <string name="workout_save">Save</string>
    <string name="workout_delete">Delete this workout</string>
    <string name="workout_not_now">Not now</string>
    <string name="workout_not_saved">Not saved; Recent problems says why.</string>
```

  Create `app/src/main/java/com/metaself/app/ui/screen/movement/WorkoutSheet.kt`:

```kotlin
package com.metaself.app.ui.screen.movement

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.metaself.app.R
import com.metaself.app.domain.movement.Effort
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.ui.movement.WorkoutSheetWording
import com.metaself.app.ui.theme.Spacing

/**
 * The sheet itself: chrome around [WorkoutSheetContent]. Split in two for `MealNamingSheet`'s reason:
 * a modal sheet draws into a window of its own, which the render helper cannot see, so the content is
 * what a test draws and the chrome is a phone check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutSheet(
    sheet: WorkoutSheetState,
    onDraft: (WorkoutDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        WorkoutSheetContent(sheet, onDraft, onSave, onDelete, onCancel)
    }
}

/**
 * Typing in a workout (D76), top to bottom: kind; minutes; a distance for the kinds that have one,
 * with a run's pace live beneath it; the effort, which starts on Moderate and cannot be cleared; the
 * energy as a line — or the owner's own figure; a note. Only kind and minutes are required.
 *
 * **Save is in the title row.** The meal-naming sheet's lesson (0.52.1) was a button pushed out of
 * reach. Here the keyboard is what rises from the bottom, and Material 3 1.2's sheet does not lift its
 * content above it by default (its window insets are the system bars only), so a button at the foot
 * could be covered. One at the top cannot. The fields between scroll, and the whole is padded by the
 * keyboard's height so the lower fields can be scrolled clear of it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutSheetContent(
    sheet: WorkoutSheetState,
    onDraft: (WorkoutDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = sheet.draft
    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = Spacing.Screen, vertical = Spacing.Related),
        verticalArrangement = Arrangement.spacedBy(Spacing.Related),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (sheet.editing == null) R.string.workout_sheet_new else R.string.workout_sheet_edit),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSave, enabled = draft.canSave && !sheet.saving) {
                Text(stringResource(R.string.workout_save))
            }
        }

        if (sheet.failed) {
            Text(
                text = stringResource(R.string.workout_not_saved),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Related),
        ) {
            // Wrapping, as the weight chart's range chips do: six chips do not fit one line at 320 dp.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Related),
                verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
            ) {
                WorkoutDraft.KINDS.forEach { kind ->
                    FilterChip(
                        selected = draft.kind == kind,
                        onClick = { onDraft(draft.copy(kind = kind)) },
                        label = { Text(stringResource(kindLabel(kind))) },
                    )
                }
            }

            OutlinedTextField(
                value = draft.minutes,
                onValueChange = { onDraft(draft.copy(minutes = it)) },
                label = { Text(stringResource(R.string.workout_minutes)) },
                isError = draft.minutesProblem,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            if (draft.takesDistance) {
                OutlinedTextField(
                    value = draft.distanceKm,
                    onValueChange = { onDraft(draft.copy(distanceKm = it)) },
                    label = { Text(stringResource(R.string.workout_distance)) },
                    isError = draft.distanceProblem,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Live, so a typo is seen before it is saved (activity spec §4.3).
                WorkoutSheetWording.pace(draft)?.let { pace ->
                    Text(
                        text = pace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Effort.entries.forEachIndexed { index, effort ->
                    SegmentedButton(
                        selected = draft.effort == effort,
                        // Choosing the chosen one keeps it: effort cannot be cleared (activity spec §8 item 4).
                        onClick = { onDraft(draft.copy(effort = effort)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = Effort.entries.size),
                    ) {
                        Text(stringResource(effortLabel(effort)))
                    }
                }
            }

            if (draft.ownEnergy) {
                OutlinedTextField(
                    value = draft.energyKcal,
                    onValueChange = { onDraft(draft.copy(energyKcal = it)) },
                    label = { Text(stringResource(R.string.workout_energy_label)) },
                    isError = draft.energyProblem,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { onDraft(draft.copy(ownEnergy = false)) }) {
                    Text(stringResource(R.string.workout_energy_estimate))
                }
            } else {
                WorkoutSheetWording.estimate(draft, sheet.weightKg)?.let { line ->
                    Text(text = line, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { onDraft(draft.copy(ownEnergy = true)) }) {
                    Text(stringResource(R.string.workout_energy_own))
                }
            }

            OutlinedTextField(
                value = draft.note,
                onValueChange = { onDraft(draft.copy(note = it)) },
                label = { Text(stringResource(R.string.workout_note)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Plain, not red: D48 keeps red for a refusal and a bad field, and a delete he chose is neither.
            if (sheet.editing != null) {
                TextButton(onClick = onDelete, enabled = !sheet.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.workout_delete))
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_not_now))
            }
        }
    }
}

@StringRes
private fun kindLabel(kind: WorkoutKind): Int = when (kind) {
    WorkoutKind.RUN -> R.string.workout_kind_run
    WorkoutKind.WALK -> R.string.workout_kind_walk
    WorkoutKind.CYCLE -> R.string.workout_kind_cycle
    WorkoutKind.SWIM -> R.string.workout_kind_swim
    WorkoutKind.STRENGTH -> R.string.workout_kind_strength
    WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> R.string.workout_kind_other
}

@StringRes
private fun effortLabel(effort: Effort): Int = when (effort) {
    Effort.EASY -> R.string.workout_effort_easy
    Effort.MODERATE -> R.string.workout_effort_moderate
    Effort.HARD -> R.string.workout_effort_hard
}
```

- [ ] **Step 5: Run to see it pass.** Same command as Step 3. Expected: exit 0, twelve tests.
  **If only the `isSelected` assertions fail** because a chip or segment reports `null`: print what
  the node carries (`nodeStartingWith(prefix).config.toString()` in a scratch assertion message) and
  read selection from the property it does carry (Material 3 1.2 puts `selected` on a `FilterChip` and
  a `SegmentedButton` through `Surface(selected = …)`); do not delete the assertion.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/screen/movement/WorkoutSheet.kt \
  app/src/main/res/values/strings.xml \
  app/src/test/java/com/metaself/app/ui/ComposeRender.kt \
  app/src/test/java/com/metaself/app/ui/screen/movement/WorkoutSheetRenderTest.kt
git commit -m "feat: the log-a-workout sheet, with Save kept in view (D76)" \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Wiring on the Movement screen

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/screen/movement/MovementScreen.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt`
- Test: `app/src/test/java/com/metaself/app/ui/screen/movement/MovementScreenRenderTest.kt`

- [ ] **Step 1: Write the failing tests.** In `MovementScreenRenderTest.kt`: add imports
  `com.metaself.app.domain.movement.aTypedWorkout`; give `draw(...)` two more parameters after
  `onToggleDay: (Long) -> Unit = {},`:

```kotlin
        onLogWorkout: () -> Unit = {},
        onOpenWorkout: (Workout) -> Unit = {},
```

  and pass them: `MovementScreen(state = state, onToggleDay = onToggleDay, onBack = {},
  onLogWorkout = onLogWorkout, onOpenWorkout = onOpenWorkout)`.

  Replace the test `nothing is offered that does nothing yet, and nothing claims a burn` with:

```kotlin
    /** D63: no burn, no net. */
    @Test
    fun `nothing claims a burn`() {
        val texts = draw()

        assertThat(texts.none { it.contains("burn", ignoreCase = true) }).isTrue()
    }

    /** D76: the button at the foot, kept in view. */
    @Test
    fun `Log a workout is offered, and asks to log one`() {
        var asked = false
        val texts = draw(onLogWorkout = { asked = true })

        assertThat(texts).contains("Log a workout")
        render.clickDescribed("Log a workout")
        assertThat(asked).isTrue()
    }

    @Test
    fun `a record that could not be read offers no button`() {
        val texts = draw(state = MovementUiState(unreadable = true))

        assertThat(texts.none { it.contains("Log a workout") }).isTrue()
    }

    /** D76: a typed workout's line opens it; a synced one's does not (corrections are a later phase). */
    @Test
    fun `a typed workout in the open day is a door to change it, and a synced one is not`() {
        val typed = aTypedWorkout(id = 2, startedAtMillis = 1)
        val withTyped = MovementWeek.of(
            today = TEST_EPOCH_DAY,
            days = emptyList(),
            workouts = week.days.first().workouts + typed,
            mealsByDay = emptyMap(),
        )
        var opened: Workout? = null
        draw(state = MovementUiState(week = withTyped, openDay = TEST_EPOCH_DAY), onOpenWorkout = { opened = it })

        assertThat(render.roleOf("Weights · 45 min")).isEqualTo(Role.Button)
        assertThat(render.clickLabelOf("Weights · 45 min")).isEqualTo("change this workout")
        assertThat(render.clickLabelOf("Running · 6.2 km")).isNull()
        render.click("Weights · 45 min")
        assertThat(opened).isEqualTo(typed)
    }
```

- [ ] **Step 2: Run to see it fail.**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.movement.MovementScreenRenderTest" > /tmp/ms-typed.log 2>&1; echo "exit $?"`
  Expected: exit non-zero; `No parameter with name 'onLogWorkout'`.

- [ ] **Step 3: Implement.** In `MovementScreen.kt`:

  (a) imports: `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.Add`,
  `androidx.compose.material3.ExtendedFloatingActionButton`, `androidx.compose.material3.Icon`,
  `androidx.compose.ui.semantics.contentDescription`, `com.metaself.app.domain.movement.Workout`,
  `com.metaself.app.domain.movement.WorkoutDraft`, `com.metaself.app.domain.movement.WorkoutSource`.

  (b) the KDoc's last paragraph ("It LOOKS; it takes no input yet…") becomes: `"Log a workout" sits at
  the bottom edge, where it cannot scroll away (D75, D76); a typed workout's line in an open day opens
  it to be changed. A synced workout is not editable here.`

  (c) the signature and the scaffold call become:

```kotlin
@Composable
fun MovementScreen(
    state: MovementUiState,
    onToggleDay: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLogWorkout: () -> Unit = {},
    onOpenWorkout: (Workout) -> Unit = {},
    onDraft: (WorkoutDraft) -> Unit = {},
    onSaveWorkout: () -> Unit = {},
    onDeleteWorkout: () -> Unit = {},
    onCloseSheet: () -> Unit = {},
) {
    val readable = state.week != null
    MetaSelfScreen(
        title = stringResource(R.string.movement_title),
        modifier = modifier,
        onBack = onBack,
        hasFloatingButton = readable,
        floatingActionButton = { if (readable) LogWorkoutButton(onClick = onLogWorkout) },
    ) {
```

  (the body is unchanged except that `DayRow(...)` also receives `onOpenWorkout = onOpenWorkout`), and
  after the closing brace of `MetaSelfScreen(...) { … }`, still inside `MovementScreen`, add:

```kotlin
    state.sheet?.let { sheet ->
        WorkoutSheet(
            sheet = sheet,
            onDraft = onDraft,
            onSave = onSaveWorkout,
            onDelete = onDeleteWorkout,
            onCancel = onCloseSheet,
        )
    }
```

  (d) add, after `Headline`:

```kotlin
/**
 * "Log a workout" (D76), drawn as the screen's floating button so it never scrolls away. Named on the
 * button itself, as the day's `AddSomethingButton` is: the words drawn inside it never reached the
 * accessibility tree.
 */
@Composable
internal fun LogWorkoutButton(onClick: () -> Unit) {
    val said = stringResource(R.string.movement_log_workout)
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = said },
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(said) },
    )
}
```

  (e) `DayRow` takes `onOpenWorkout: (Workout) -> Unit` as its last parameter, reads
  `val change = stringResource(R.string.movement_workout_change)` beside `said` and `action`, and its
  open-day block becomes:

```kotlin
        if (open) {
            val details = MovementWeekWording.detailRows(day)
            if (details.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.Related),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Tight),
                ) {
                    details.forEach { line ->
                        // A typed workout's line is a door to change it (D76); a synced one's is not.
                        val typed = line.workout?.takeIf { it.source == WorkoutSource.TYPED }
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = if (typed == null) {
                                Modifier
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button, onClickLabel = change, onClick = { onOpenWorkout(typed) })
                                    // The usual 48 of touch; a render here cannot measure it (CLAUDE.md).
                                    .heightIn(min = 48.dp)
                            },
                        )
                    }
                }
            }
        }
```

  In `MetaSelfNavHost.kt`, the `MovementScreen(...)` call becomes:

```kotlin
            MovementScreen(
                state = movementState,
                onToggleDay = movementViewModel::toggle,
                onBack = { navController.popBackStack() },
                onLogWorkout = movementViewModel::logWorkout,
                onOpenWorkout = movementViewModel::openWorkout,
                onDraft = movementViewModel::changeDraft,
                onSaveWorkout = movementViewModel::saveWorkout,
                onDeleteWorkout = movementViewModel::deleteWorkout,
                onCloseSheet = movementViewModel::closeSheet,
            )
```

- [ ] **Step 4: Run to see it pass.**
  `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.ui.screen.movement.*" --tests "com.metaself.app.ui.nav.*" > /tmp/ms-typed.log 2>&1; echo "exit $?"`
  Expected: exit 0.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/metaself/app/ui/screen/movement/MovementScreen.kt \
  app/src/main/java/com/metaself/app/ui/nav/MetaSelfNavHost.kt \
  app/src/test/java/com/metaself/app/ui/screen/movement/MovementScreenRenderTest.kt
git commit -m "feat: Log a workout on the Movement screen, and a typed workout opens to be changed (D76)" \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Version, suite, lint, PR, CI, release, phone checks

- [ ] `app/build.gradle.kts`: `versionCode = 113` → `114`, `versionName = "0.58.0"` → `"0.59.0"`.
- [ ] Whole suite (`free -m` first):
  `~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-typed.log 2>&1; echo "exit $?"` — exit 0,
  0 failures; skipped exactly the ten SQLite classes (the eight `CLAUDE.md` names, `HealthRecordDaoTest`,
  `HealthRecordStoreTest`). Lint:
  `~/bin/gradlew-safe :app:lintDebug > /tmp/ms-typed-lint.log 2>&1; echo "exit $?"` — exit 0.
  `git status app/schemas` clean. **Anonymisation read** of every added line, test names included
  (they print in CI): no real figure, no frequency, no story about a session; notes in fixtures are
  "a note".
- [ ] Commit `app/build.gradle.kts` alone: `0.59.0: a workout can be typed in (D76–D78)` with the
  co-author line.
- [ ] Push; PR titled `0.59.0: a workout can be typed in (D76–D78)`. Body: what the owner can now do;
  that a typed workout is the third reading and never an addition (D77); the #58 fix, with
  `Fixes #58`; D78's three tidy-ups; no schema change, and one new DAO read (`byId`); the twenty
  design questions above, one line each; what the render tests cannot prove (the modal chrome, the keyboard,
  touch heights, the rule's fill and colour); what CI must show (0 skipped); the phone checks below.
  End with the "Generated with Claude Code" line. **No session link.**
- [ ] CI green with 0 skipped → squash-merge. `free -m` ≥ ~6000 MB and lock free → `~/bin/ms-release`;
  send the release-signed APK.
- [ ] **Phone checks** (for the owner, after installing over 0.58.0):
  1. The Movement screen has **Log a workout** at the bottom edge; it stays while the page scrolls.
  2. Tapping it opens a sheet titled "Log a workout" with Save at the top right, greyed until a kind
     and minutes are in; Moderate is already chosen.
  3. Choosing Run and typing minutes and a distance shows the pace beneath as it is typed; Strength
     has no distance field.
  4. With the keyboard up, Save is still visible, and the note field can be scrolled into view above
     the keyboard with no large empty gap under it (design question 1's padding).
  5. "set it yourself" swaps the estimate line for a kcal field; "use the estimate" swaps it back.
  6. Saved, the workout appears in the open day's detail with "about … kcal, estimated", in the day's
     one-line summary, and in the headline count.
  7. Tapping that line opens the sheet filled, titled "Change this workout", with "Delete this
     workout"; a band-recorded workout's line does nothing when tapped.
  8. A typed workout bigger than the day's movement moves the day screen's "+… kcal earned"; one
     smaller than the band's figure changes nothing (D77).
  9. On a day the band decided with few steps, the step line's rule is drawn against kcal, not steps
     (#58).
  10. A day with several walks reads "Walking ×n · …" in its summary; a walk's line has no pace; the
     headline says "… workouts · … walks · …" (D78).
  11. Deleting a typed workout closes the sheet and shows "Deleted · Undo" under the title bar; Undo
     puts it back on its day with its figures.

## Not built here, said in the PR

Editing or hiding a synced session and correcting a day (the corrections phase); sets × reps (D61);
any date picker; a typed workout for the babysitter's simulated
app (`sim/SimulatedApp.kt` — the activity spec's §6 wish; the simulated day screen still compiles
through `DayViewModel`'s default); merging a typed run with a band copy of it (activity spec §8
item 2, deferred until it happens).

## Self-review (2026-09-27)

**Spec coverage.** D76 button at the foot, kept in view → Task 8 (floating button) + render test. Logs
to the open day, today by default, at the current clock time, no date picker → Task 6
(`logWorkout`, `startOn`) + tests. Sheet order kind · minutes · distance (four kinds) · live run pace
· effort (Moderate, uncleared) · energy line with "set it yourself" → TYPED · note · Save in view →
Tasks 1 and 7. Nothing beyond kind and minutes required → `canSave`, tested. Stored as TYPED, origin
null, MET_ESTIMATE/TYPED → Tasks 1 and 3. Tap a typed workout → sheet filled with Delete; synced not
editable → Tasks 6 and 8, plus the store's own refusal (Task 3). D77 typed kcal the sum of visible
typed workouts, max never sum, `health_days` counts it → Tasks 3 (pins) and 4. #58 → Task 5. D78
combined same-kind summary with distance-or-time, pace for runs only, walks apart with either part
dropped at zero and all sessions' time → Task 2. Not built: listed above.

**Placeholder scan.** No TBD/TODO; every code step shows its code; every run step has its command and
its expected result. Task 7 Step 5's contingency names what to do, not "fix it".

**Type consistency.** `WorkoutDraft(kind, minutes, distanceKm, effort, ownEnergy, energyKcal, note)`,
`KINDS`, `MINUTES_IN_A_DAY`, `from`, `startOn`, `takesDistance`, `minutesValue`, `minutesProblem`,
`distanceM`, `distanceProblem`, `ownKcal`, `energyProblem`, `paceSecondsPerKm`, `pricedByPace`,
`estimateKcal`, `canSave`, `toWorkout(id, epochDay, startedAtMillis, weightKg)` — Tasks 1, 6, 7.
`MetEstimate.pricedByPace(kind, distanceM)` — Task 1. `WorkoutSheetWording.estimate/pace/NO_WEIGHT` —
Tasks 1, 7. `MovementWeek.walkCount` — Task 2. `DetailLine(text, workout)`,
`MovementWeekWording.detailRows/detailLines/kindName/name` — Tasks 2, 8. `TypedWorkouts.observe/log/
change/delete/NONE`, `RoomTypedWorkouts(workouts, transaction, store, now)`, `Workout.toTypedEntity()`,
`FakeTypedWorkouts(workouts, logged, changed, deleted, failing)` — Tasks 3, 4, 6. `TypedReading.kcalByDay/
merge` — Task 4. `WorkoutSheetState(draft, epochDay, weightKg, editing, saving, failed)`,
`MovementUiState.sheet`, `MovementViewModel(record, meals, today, problems, typed, profiles, now)` with
`logWorkout/openWorkout/changeDraft/closeSheet/saveWorkout/deleteWorkout` — Tasks 6, 8.
`WorkoutSheet/WorkoutSheetContent(sheet, onDraft, onSave, onDelete, onCancel)` — Tasks 7, 8.
`LogWorkoutButton`, `MovementScreen(..., onLogWorkout, onOpenWorkout, onDraft, onSaveWorkout,
onDeleteWorkout, onCloseSheet)` — Task 8. `ComposeRender.isSelected` — added in Task 7 before use.
String names `movement_log_workout`, `movement_workout_change`, `workout_*` match between Tasks 7 and 8.
`aTypedWorkout(...)` — created in Task 1, used from Task 2 on.
