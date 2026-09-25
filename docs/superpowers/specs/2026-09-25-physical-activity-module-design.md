# Physical activity and exercise — D59 to D64

> Written 2026-09-25 as an architectural proposal at the owner's request, and **approved by the
> owner the same day**, with the three questions in §8 settled as recorded there. It extends the movement decisions of milestone 1 (D12, D12a–d in
> `2026-09-02-milestone-1-design.md`) and the page decisions (D49, D50 in
> `2026-09-21-the-page-design.md`). Nothing here is built.
>
> **Every figure in a worked example is invented** and chosen to keep the arithmetic round. The
> standard fixture body (80 kg, `aProfile()`) is used wherever a body is needed.

---

## 0. What the brief got wrong, and why it matters

The brief describes the app's activity tracking as "a basic step-counter integration". It is not.
The app has had a Health Connect integration since 2026-09-05 (`data/movement/HealthConnectSteps.kt`)
that, on every app open and on pull-to-refresh:

- reads **daily step totals** and **daily active-calorie totals** through the aggregation API
  (deduplicated across apps by Health Connect's own priority list — a correctness requirement, D12c);
- reads **exercise sessions** by name and duration, and shows them on the day (D12a, D12b);
- works out the owner's **usual day** as a 30-day median (`NormalDay`) and credits only movement
  above it, at three quarters, capped at half a day's deficit (D12, `MovementCredit`, `MovementCap`);
- takes the day's movement energy as **the larger of steps and band calories, never their sum**
  (`ActivityEnergy`, D12b).

It stores none of it. Movement is read live and reduced to today's credit; nothing survives the
view model. That is the one structural fact this proposal changes, and everything else follows from
it.

What genuinely does not exist:

| Missing | Where the design already expects it |
|---|---|
| Any stored record of a workout | D12b: "further kinds of exercise are expected later, and should slot in beside this" |
| Logging a workout by hand | Nowhere — the brief's request |
| Distance, pace, weekly volume for runs | Nowhere — the brief's request |
| Strength sessions with any structure | Nowhere — the brief's request |
| Correcting a wrong day by hand | D12d, designed 2026-09-05, deferred as Step 22 |
| An activity screen | D50's pattern (the record got a screen of its own) |
| The known gap: a band-driven day explains itself in steps | §6 of milestone 1, "KNOWN GAP" |

So this is not a new module beside the app. It is the second half of a module whose first half
shipped three weeks ago, and it has to be built **on** D12, not around it.

---

## 1. Research

### 1.1 Health Connect — where it stands

Health Connect is the only Android health API worth building on. The Google Fit APIs shut down at
the end of 2026 and closed to new sign-ups in May 2024 (already recorded in `app/build.gradle.kts`).
On **Android 14 and later Health Connect is part of the platform**; on 9–13 it is a separate
Play Store app. The owner's phone is on 14 or newer, so the platform path is the one that matters,
but the app's `minSdk 26` means the code must keep tolerating absence — which it already does
(`StepAccess.UNAVAILABLE`).

**The library.** Measured on 2026-09-25 from the AAR metadata on Google's Maven, not from memory:

| `androidx.health.connect:connect-client` | `minCompileSdk` | `minAndroidGradlePluginVersion` |
|---|---|---|
| `1.1.0-alpha07` (the current pin) | 34 | none |
| `1.1.0-alpha10` | 35 | none |
| `1.1.0` (stable, 2025-10-08) | 36 | 8.9.1 |

This project compiles against SDK 34 with AGP 8.2.2 and Gradle 8.5. The stable library therefore
needs a whole toolchain upgrade (AGP 8.2.2 → 8.9.1+, Gradle 8.5 → 8.11.1+, compileSdk 34 → 36),
which the existing build comment rightly refuses to smuggle in under a feature.

**The pin does not block this proposal.** Feature by feature, everything the module needs was in the
library before `alpha07`:

| Needed | Present in `alpha07`? |
|---|---|
| `ExerciseSessionRecord` with `exerciseType`, `title`, `segments`, `laps` | yes (since 1.0) |
| `DistanceRecord` and its `DISTANCE_TOTAL` aggregate | yes |
| `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL` over a session's time range | yes (already used) |
| `HeartRateRecord.BPM_AVG`, `ElevationGainedRecord` (deferred here, §7) | yes |
| `aggregate` / `aggregateGroupByPeriod` with `dataOriginFilter` | yes |
| `getChangesToken` / `getChanges` | yes |
| `PermissionController.createRequestPermissionResultContract()` | yes (already used) |

What arrived later and is **not** needed: reads older than 30 days (`READ_HEALTH_DATA_HISTORY`,
alpha10 — unnecessary once sessions are mirrored locally, §3); background reads
(`READ_HEALTH_DATA_IN_BACKGROUND`, alpha09 — the app has no background machinery by design and
reads on open); the feature-availability API (alpha08); exercise-route reads (alpha03, present but
unused — no maps here).

**Decision D64 below: the pin stays.** The toolchain upgrade is worth doing on its own, in its own
branch, with nothing riding on it.

**Android 14 permission model**, as it applies here:

- Permissions are ordinary manifest `<uses-permission>` entries in the form
  `android.permission.health.READ_<TYPE>`; the app already declares `READ_STEPS`,
  `READ_ACTIVE_CALORIES_BURNED`, `READ_EXERCISE`. This module adds **`READ_DISTANCE`** and nothing
  else (heart rate and elevation are deferred, §7 — "asking for what cannot be used is how permission
  screens become things people dismiss").
- The request goes through `PermissionController.createRequestPermissionResultContract()`, which is
  already wired in `MetaSelfNavHost.kt`. Adding a permission to `HealthConnectSteps.PERMISSIONS`
  makes the next request ask for it; an already-connected phone will show the new one as ungranted
  until the owner taps the Settings button again. Settings must say so (§5.4).
- The rationale hook for Android 14+ is the `ViewPermissionUsageActivity` alias with
  `VIEW_PERMISSION_USAGE` / `HEALTH_PERMISSIONS` — present in the manifest. The Android-13 rationale
  action is present too. There is **no `<queries>` entry** for `com.google.android.apps.healthdata`;
  it is required below Android 14 for `getSdkStatus` to see the separate app, and harmless above.
  Add it (one line) in the sync phase.
- Grants are revocable at any time from the system's Health Connect settings; `getGrantedPermissions`
  before every read is already the pattern, and partial grants already degrade gracefully (steps
  alone are enough for `GRANTED`).
- On Android 14+ an app may read **its own** records without the 30-day limit and other apps'
  records only within 30 days. This app never writes to Health Connect (D12d, unchanged), so the
  limit applies to everything it reads. The local mirror (§3) is what turns 30 days into a year.
- Reads are quota-limited per app in the foreground. The sync design reads one page of sessions for
  the window and then **one aggregate per session that is new**, so a steady state costs one call.

### 1.2 External APIs — evaluated and refused

The brief asks about pulling run data (pace, distance) directly from a service. Checked
2026-09-25:

| Source | State | Verdict |
|---|---|---|
| **Strava API** | Since June 2026, any developer account needs an active Strava subscription (about $12/month) even for one athlete; data may be shown only to that athlete; rate-limited. | A monthly fee to read numbers the phone already holds. **No.** |
| **Garmin Connect Developer API** | Requires a legal entity, rejects personal use, and stopped taking new applications in spring 2026 pending a redesign. | Not available to an individual at all. **No.** |
| **Aggregators (Terra and similar)** | Hold their own vendor partnerships; commercial; route personal data through a third party. | Violates D16 (no third parties). **No.** |
| **Health Connect, fed by the vendor's own app** | Garmin Connect, Samsung Health, Fitbit/Pixel, Strava, Runna and others write sessions, distance and calories into Health Connect on the phone. Garmin's link arrived in 2025. | The owner's device already reaches the app this way. **The only door.** |

The consequence for the "30 km a week" style of question: **distance, duration and average pace per
run** are obtainable from Health Connect (`DistanceRecord` aggregated over the session's time range,
duration from the session itself). Per-kilometre splits and GPS routes are also technically readable
but are not built here — the app is a food and weight record, not a running log, and the vendor's
own app draws better maps than this one ever will.

### 1.3 What the Room schema has to be able to say

The provenance rule (D4) applies to every stored number: a workout's energy is a **measurement from
a device, an estimate from a formula, or the owner's own figure**, and the record says which. The
existing `Source`/`Confidence` pattern for foods (`domain/day/Source.kt`) is the shape to copy: a
string column, never an ordinal, unknown values read back as a named "unrecognised" state.

---

## 2. Decisions

**D59 — Workouts are stored; daily totals are not.** Sessions read from Health Connect are mirrored
into a local `workouts` table, keyed by the record's origin and id so that a re-read updates rather
than duplicates. Steps and active calories per day stay a **live aggregation read** exactly as now,
because the aggregation API is what deduplicates across apps (D12c) and a mirrored copy of a
deduplicated total buys nothing. The mirror exists for four reasons: a hand-logged workout needs a
home; a correction (D12d) needs a home; Health Connect gives 30 days and a running record needs a
year; and a table is a `Flow` the day and the activity screen can observe like everything else,
while a cross-process read is a one-shot.

**D60 — A hand-logged workout is a third reading into the maximum, never an addition to it.**
D12b takes a day's movement energy as the larger of the steps figure and the band's figure. A typed
workout's energy — a MET estimate, §3.3 — joins that comparison as a third candidate. A run logged by
hand on a day the phone also counted its steps is captured once, by whichever reading is larger. The
stingy direction is kept deliberately, for D12b's reason: eating back calories nobody burned is
invisible and stalls everything.

**D61 — A strength session is a session, not a lift log.** Chosen by the owner 2026-09-25 from
three options. What is recorded: what it was, how long, felt effort (easy / moderate / hard), a
note. Sets, reps and weights per exercise are **designed as a child table and not built** (§3.2),
so adding them later is a migration that adds a table, not one that reshapes `workouts`.

**D62 — Activity has a screen of its own, reached from the day; the day gets no dashboard.**
D49 says the day is a page that answers one question, and D50 moved the food record off it for
exactly the reason a dashboard would go back on: once a page is allowed to grow, it grows back into
fourteen things at one volume. The step line and the session lines on the day become **doors**
(D49 item 8's rule: every line is a door) into an activity screen, which is where the week, the
running distance, the log-a-workout action and the corrections live. Nothing new is drawn on the
day.

**D63 — There is no "net calories" number.** The brief asked for one. "Eaten minus burned" requires
a claim about total daily burn, and D25a is explicit that the app's measured figure is *not a claim
about his metabolism* and must never be worded as one. The activity screen shows the parts —
eaten, the allowance, what the day's movement was worth and which reading decided it — and lets the
arithmetic be visible (D9). Chosen by the owner 2026-09-25: keep D12, show the full picture beside
it as information.

**D64 — Health Connect is the only source, and the library pin stays.** Direct Strava, Garmin and
aggregator integrations are refused (§1.2). The `1.1.0-alpha07` pin is kept because it holds every
API this module uses (§1.1); the toolchain upgrade that the stable library needs is a separate piece
of work with its own branch and nothing riding on it.

**D12d becomes buildable.** Step 22 was deferred until a month of use showed what kind of wrong
day actually occurs. The pilot month ends around 2026-10-05; the correction table is designed here
(§3.2) and built in the last phase, against whatever the month showed.

---

## 3. Architecture and data model

### 3.1 Layers

```
data/movement/
  HealthConnectSteps        (exists)  daily step + active-kcal totals, live, aggregation API
  HealthConnectWorkouts     (new)     sessions + distance → WorkoutEntity, reconciled into Room
  WorkoutDao, WorkoutEntity, MovementCorrectionEntity   (new)
  WorkoutRepository         (new)     the one door the UI uses: observe, log, edit, hide, correct
domain/movement/
  MovementCredit, ActivityEnergy, NormalDay, MovementToday   (exist; ActivityEnergy gains a third reading)
  Workout, WorkoutKind, Effort, EnergySource, WorkoutSource  (new, pure)
  MetEstimate                                                (new, pure: kind + effort + pace → kcal)
  WeekOfActivity                                             (new, pure: 7 days → what the screen shows)
ui/screen/activity/
  ActivityScreen, ActivityViewModel, LogWorkoutSheet, WorkoutRow   (new)
ui/day/StepBar                                                    (exists; lines become doors)
```

The sync class, like `HealthConnectSteps`, **never throws upwards** (D8) — every failure is a
`ProblemLog` entry with kind `"workouts"` and an empty result.

### 3.2 Room — schema version 6

Two new tables. Column conventions follow the existing ones: `epochDay` as `Long`, moments as
`…Millis`, enumerations as strings.

```kotlin
package com.metaself.app.data.movement

/**
 * One workout, whether the band recorded it or the owner typed it.
 *
 * [origin] and [originId] are Health Connect's data origin (the writing app's package) and record
 * id; together they are unique, so a re-read of the same session updates this row rather than
 * adding a second. Both are null for a workout the owner logged himself.
 *
 * [energyKcal] carries where it came from in [energySource] (D4): the band's own figure, a MET
 * estimate from kind, effort and pace, the owner's typed number, or nothing at all.
 */
@Entity(
    tableName = "workouts",
    indices = [
        Index("epochDay"),
        Index(value = ["origin", "originId"], unique = true),
    ],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startedAtMillis: Long,
    val durationMinutes: Int,
    /** RUN, WALK, CYCLE, SWIM, STRENGTH, OTHER — a string, never an ordinal. */
    val kind: String,
    /** The band's title for it, or what the owner called it. Nullable: most sessions have none. */
    val title: String?,
    val distanceM: Int?,
    val energyKcal: Int?,
    /** BAND, MET_ESTIMATE, TYPED, NONE. */
    val energySource: String,
    /** EASY, MODERATE, HARD; only a typed workout has one. */
    val effort: String?,
    /** SYNCED or TYPED. */
    val source: String,
    val origin: String?,
    val originId: String?,
    /**
     * Set when the owner says this session is not real — a bus ride the band called cycling. The
     * row is kept, not deleted, so the next sync does not resurrect it (D12d: a correction is a
     * fact about that day).
     */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    val note: String?,
)

/**
 * The owner's own figure for a day's movement, replacing what Health Connect read (D12d).
 *
 * One row per day at most; either column may be null, meaning "leave that reading alone". Never
 * written back to Health Connect. Feeds the usual-day median like any other day.
 */
@Entity(tableName = "movement_corrections")
data class MovementCorrectionEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Int?,
    val activeKcal: Int?,
    val setAtMillis: Long,
    val note: String?,
)
```

**Designed, not built — the lift log (D61):**

```kotlin
// NOT in version 6. Recorded so that when sets arrive they are a table added, not a table reshaped.
@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"],
        childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutId")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exercise: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double?,
)
```

**Migration 5 → 6**, hand-written like the four before it, checked locally with a
`tools/check-migration-5-6.py` in the pattern of the 4→5 check, and validated in CI by
`MigrationTest`:

```sql
CREATE TABLE IF NOT EXISTS `workouts` (
  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `epochDay` INTEGER NOT NULL, `startedAtMillis` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL,
  `kind` TEXT NOT NULL, `title` TEXT, `distanceM` INTEGER, `energyKcal` INTEGER,
  `energySource` TEXT NOT NULL, `effort` TEXT, `source` TEXT NOT NULL,
  `origin` TEXT, `originId` TEXT, `hidden` INTEGER NOT NULL DEFAULT 0, `note` TEXT);
CREATE INDEX IF NOT EXISTS `index_workouts_epochDay` ON `workouts` (`epochDay`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_workouts_origin_originId` ON `workouts` (`origin`, `originId`);
CREATE TABLE IF NOT EXISTS `movement_corrections` (
  `epochDay` INTEGER PRIMARY KEY NOT NULL, `steps` INTEGER, `activeKcal` INTEGER,
  `setAtMillis` INTEGER NOT NULL, `note` TEXT);
```

The exported `app/schemas/…/6.json` is committed with it — a schema change with no new file there
means the version was not bumped. **Backup** (`BackupRepository`) gains both tables, replaced on
restore like meals and weights; `BackupRoundTripTest` covers them.

**DAO** — the queries the screens actually ask:

```kotlin
@Dao
interface WorkoutDao {

    @Query("SELECT * FROM workouts WHERE epochDay BETWEEN :from AND :to ORDER BY startedAtMillis")
    fun observeBetween(from: Long, to: Long): Flow<List<WorkoutEntity>>

    /**
     * Running distance per week, Monday-based. Epoch day 0 is a Thursday, so (epochDay + 3) / 7
     * is a whole number that changes on Mondays. Hidden sessions are excluded; a run with no
     * distance contributes nothing rather than zero-ing the week.
     */
    @Query(
        "SELECT (epochDay + 3) / 7 AS week, SUM(distanceM) AS metres, COUNT(*) AS runs " +
            "FROM workouts WHERE kind = 'RUN' AND hidden = 0 AND distanceM IS NOT NULL " +
            "GROUP BY week ORDER BY week DESC LIMIT :weeks",
    )
    fun observeWeeklyRunning(weeks: Int): Flow<List<WeekOfRunning>>

    @Query("SELECT originId FROM workouts WHERE source = 'SYNCED' AND epochDay BETWEEN :from AND :to")
    suspend fun syncedIdsBetween(from: Long, to: Long): List<String>

    @Upsert suspend fun upsert(workout: WorkoutEntity): Long

    @Query("DELETE FROM workouts WHERE source = 'SYNCED' AND epochDay BETWEEN :from AND :to " +
        "AND originId NOT IN (:keep) AND hidden = 0")
    suspend fun dropSyncedNotIn(from: Long, to: Long, keep: List<String>)

    @Query("UPDATE workouts SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("DELETE FROM workouts WHERE id = :id AND source = 'TYPED'")
    suspend fun deleteTyped(id: Long)
}

data class WeekOfRunning(val week: Long, val metres: Int, val runs: Int)
```

**Correlating with the food log.** The day's eaten total is already a domain calculation
(`DayTotals.of(meals)` over `MealDao.observeDay`), and the measured burn already has a per-day kcal
projection (`DayKcal`). The activity screen's week is therefore **a combination of flows in the view
model, not a SQL join**: seven `observeDay` flows, `observeBetween` for the workouts, the live
`StepSource.history` for the seven daily totals, and the corrections. This matches how the day screen
already assembles itself and keeps the arithmetic in pure, tested domain code:

```kotlin
/** One day of the activity screen's week. Every figure says where it came from. */
data class DayBalance(
    val epochDay: Long,
    val eatenKcal: Int,
    val allowanceKcal: Int,        // target + that day's credit, as the day screen showed it
    val movement: ActivityEnergy?, // the D12b/D60 maximum, with its source
    val workouts: List<Workout>,
    val corrected: Boolean,
)
```

### 3.3 Domain — the new pure pieces

```kotlin
enum class WorkoutKind { RUN, WALK, CYCLE, SWIM, STRENGTH, OTHER, UNRECOGNISED }
enum class Effort { EASY, MODERATE, HARD }
enum class EnergySource { BAND, MET_ESTIMATE, TYPED, NONE }
enum class WorkoutSource { SYNCED, TYPED }

data class Workout(
    val id: Long, val epochDay: Long, val startedAtMillis: Long, val durationMinutes: Int,
    val kind: WorkoutKind, val title: String?, val distanceM: Int?,
    val energyKcal: Int?, val energySource: EnergySource, val effort: Effort?,
    val source: WorkoutSource, val hidden: Boolean, val note: String?,
) {
    /** Minutes per kilometre, or null without a distance. Shown as "5:30 /km". */
    val paceSecondsPerKm: Int? get() = distanceM?.takeIf { it > 0 }
        ?.let { (durationMinutes * 60.0 / (it / 1000.0)).roundToInt() }
}
```

**`MetEstimate`** — what a typed workout cost, when the band did not say. Uses the Compendium of
Physical Activities (Ainsworth et al., 2011) MET table, **net of resting** (one MET subtracted, so the
figure is on the same footing as `KCAL_PER_STEP_PER_KG`, which is net):

```
netKcal = (MET − 1) × weightKg × hours
```

The MET for each `(kind, effort)` — and for a run, the MET for its **pace** when a distance was
typed — is transcribed from the published table when the code is written, with the Compendium code
beside each value. No value is written into this document from memory: the project's rule is that a
number is a measurement or is not written, and a MET copied wrong here would propagate into tests
unnoticed, as two invented figures once did.

Worked example on the standard 80 kg body, with a **placeholder MET of 3.5** for a moderate strength
session (to be confirmed against the table): 45 minutes → (3.5 − 1) × 80 × 0.75 = **150 kcal**,
stored as `energySource = MET_ESTIMATE` and shown as "about 150 kcal, estimated".

**`ActivityEnergy` gains a third reading (D60):**

```kotlin
// DayMovement gains `typedWorkoutsKcal: Int = 0` — the sum of that day's typed workouts' energy,
// zero until the store exists — and the comparison reads it from there:
fun of(day: DayMovement, weightKg: Double): ActivityEnergy =
    listOf(
        ActivityEnergy(stepsKcal(day, weightKg), MovementSource.STEPS),
        ActivityEnergy(day.activeKcal ?: 0, MovementSource.ACTIVE_CALORIES),
        ActivityEnergy(day.typedWorkoutsKcal, MovementSource.TYPED_WORKOUT),
    ).maxBy { it.kcal }   // ties resolve to the earlier, i.e. steps — the least-estimated reading
```

`MovementSource` grows `TYPED_WORKOUT`, and the day's "against usual" line (the KNOWN GAP) is fixed
at the same time to speak in whichever reading decided the credit.

**A correction (D12d)** is applied in `DayMovement` before anything else looks at it:
`steps = correction.steps ?: read.steps`, likewise for `activeKcal`, and `DayMovement` gains a
`corrected: Boolean` so the screen can say "you set this".

### 3.4 Sync — how a session becomes a row

On app open and pull-to-refresh, after the existing daily-totals read:

1. `readRecords(ExerciseSessionRecord, last 30 days)`, following page tokens (the lesson of the
   first steps read).
2. For every session whose `metadata.id` is **not already in the table**: one
   `aggregate(DISTANCE_TOTAL + ACTIVE_CALORIES_TOTAL, session.startTime..endTime)`. Both metrics are
   in Health Connect's "Activity" category and are deduplicated by the aggregation. `energySource`
   is `BAND` when a figure comes back, else `NONE`.
3. `upsert` each as `SYNCED` with `origin = metadata.dataOrigin.packageName`, `originId = metadata.id`.
   A row that already exists keeps its `hidden` and `note`.
4. `dropSyncedNotIn(window, seen ids)` — a session deleted in the vendor's app disappears here too.
   Hidden rows are kept (they were hidden on purpose).
5. Rows older than the window are never touched again: that is the mirror doing its job.

The two-apps-one-walk duplicate (D12c) is handled by the `(origin, originId)` key and by the
existing collapse-by-name-and-duration for display. The changes API (`getChangesToken`) would make
step 1 cheaper and is deliberately not used: a 30-day window is one page for any plausible band, the
reconcile is simpler to reason about, and a token that expires needs the reconcile as its fallback
anyway.

**Rate limits:** steady state is one `readRecords` per open; a new session costs one aggregate.

---

## 4. UI — Material 3, in the page's own vocabulary

The app has a typographic system (D48: Fraunces figures, an ink ladder, tabular numerals, hairline
rules) and a stated aversion to cards and dashboards. Every new surface below uses that system, not
Material's default card grammar.

### 4.1 The day screen — two doors, nothing new drawn

- The step line (`StepBar`) becomes tappable and opens the activity screen at today.
- Each session line ("Running · 32 min") becomes tappable and opens the activity screen at that
  session.
- A corrected day's line reads "9,000 steps · you set this" (D12d).
- **Nothing else changes.** No FAB, no chart, no second number.

### 4.2 The activity screen (D62) — one screen, reached from the day and from the top-right menu

Down the page, in D49's order of volume:

1. **Kicker:** "This week", `labelSmall`, with the Monday's date.
2. **The one number:** running distance this week, `displayLarge` — "18.4 km" (invented). Beneath
   it, `bodyMedium`: "of 20 this week · 3 runs" when a weekly target is set in Settings, else
   "3 runs". This is the module's answer to the brief's running-volume question and the only
   figure set large.
3. **One hairline rule** filled to the week's proportion of the target.
4. **Seven day rows**, today first, each a door:
   the weekday as a `titleSmall` kicker; the movement figure ("410 kcal · band" / "230 kcal · steps"
   / "150 kcal · estimated") and, when there was one, "+90 kcal earned"; then each workout as one
   `bodyMedium` line — "Running · 6.2 km · 32 min · 5:10 /km" or "Strength · 45 min · moderate".
   Eaten and allowance on the same row, in the ink-three grey: "1,840 of 2,090 eaten". That row is
   D63's full picture: every part visible, no synthetic net.
5. **A previous-weeks line** at the foot, `bodySmall`: "Last four weeks: 22 · 19 · 24 · 20 km"
   (invented). Text, not a chart; four numbers in a line are read faster than four bars.
6. **"Log a workout"** as a bottom-edge button, the same placement as the record screen's actions
   (D50), so it cannot scroll away.

Tapping a workout row expands it in place: the details, a note field, and the actions that apply —
**Edit** and **Delete** for a typed one; **Hide** ("not a real workout") and **Set the energy** for a
synced one. A synced session is never editable beyond that: its numbers are the band's, and the app
does not rewrite another app's record, only annotates its own copy.

### 4.3 Logging a workout by hand — one sheet

A `ModalBottomSheet` (the app's existing shape for a short form), top to bottom:

1. **Kind** as a `FilterChip` row: Run · Walk · Cycle · Swim · Strength · Other.
2. **Minutes** — one numeric `OutlinedTextField`.
3. **Distance (km)** — shown only for Run, Walk, Cycle, Swim; optional. A run with a distance
   shows its pace live beneath ("5:30 /km") so a typo is seen before it is saved.
4. **Effort** as a `SingleChoiceSegmentedButtonRow`: Easy · Moderate · Hard. Default Moderate.
5. **Energy:** a line, not a field — "about 150 kcal, estimated from the effort" — with a small
   "set it yourself" that turns it into a numeric field (`energySource = TYPED`).
6. **Note**, optional.
7. **Save**, kept in view above the keyboard (the lesson of the meal-naming sheet, 0.52.1).

It logs to the day on screen, at the current time; there is no date picker — a workout is logged
from its day, the way a meal is. The sheet is deliberately shorter than the food page, which the owner
found too complicated on 2026-09-25; every field beyond kind and minutes is optional or defaulted.

### 4.4 Syncing versus logging — how the difference shows

| | Synced | Typed |
|---|---|---|
| Arrives | on open / refresh, silently | from the sheet |
| Source line | "band" / the app's name from `origin` | "you logged this" |
| Energy | "410 kcal · band" or "no energy reported" | "about 150 kcal, estimated" or "150 kcal · you set this" |
| Editable | hide; set energy; note | everything; delete |
| Counts towards the credit | yes, via the band's daily total (D12b) | yes, as the third reading (D60) |

Settings' Steps section becomes **Movement**: the existing status text, the band-energy line, the
connect button (now asking for distance too, and saying "distance is new — allow it to see how far
you ran"), and one optional numeric field, **weekly running target (km)**, stored in the profile
DataStore as `running_weekly_target_km`.

---

## 5. Roadmap — one phase, one branch, one plan

Each phase ships on its own, is usable on its own, and leaves the app working if the next never
comes. Test first wherever the test is about behaviour (JUnit 5 + Truth for the pure parts;
Robolectric with `assumeSqliteRuntime()` for the DAO; `ComposeRender` for the sheet).

**Prerequisites:** none in the toolchain. Dependencies added: **none** — Room, Hilt, DataStore,
Compose Material 3 and the pinned Health Connect client are all already present. (Material 3's
`SingleChoiceSegmentedButtonRow` is in the BOM the project uses.)

| Phase | Branch does | Touches | Done when |
|---|---|---|---|
| **1. The model** | `Workout`, `WorkoutKind`, `Effort`, `EnergySource`, `MetEstimate` (with the Compendium values transcribed and cited), `ActivityEnergy` third reading, `MovementSource.TYPED_WORKOUT`, the KNOWN-GAP wording fix | `domain/movement/`, `ui/movement/MovementWording` | Pure tests pass; a band day's "against usual" line speaks in band energy |
| **2. The store** | Schema v6, the two entities, `WorkoutDao`, `MIGRATION_5_6`, `6.json`, `tools/check-migration-5-6.py`, backup round-trip | `data/movement/`, `MetaSelfDatabase`, `DataModule`, `BackupRepository` | Migration test and DAO tests skip locally and pass in CI; backup restores workouts |
| **3. The sync** | `HealthConnectWorkouts`, `READ_DISTANCE` permission, `<queries>` entry, reconcile into Room, Settings wording for the new permission | `data/movement/`, manifest, `SettingsViewModel`, nav host launcher | A run recorded by the band appears in the table with distance; a re-read does not duplicate it; deleting it in the vendor app removes it |
| **4. Logging by hand** | `LogWorkoutSheet`, `ActivityViewModel.log/edit/delete`, the day's two doors | `ui/screen/activity/`, `StepBar`, nav graph | A strength session logged in five taps shows on the day and moves the credit only when above usual |
| **5. The activity screen** | `ActivityScreen`, `WeekOfActivity`, weekly running line, previous weeks, the menu item, the weekly target setting | `ui/screen/activity/`, `DayPager` menu, Settings | The week reads at a glance; the running figure matches the sum of the runs |
| **6. Corrections (D12d)** | `MovementCorrectionEntity` in use, "set this day's steps / energy", hide a session, "you set this" everywhere | activity screen, `DayMovement`, `NormalDay` | A day that read 30,000 steps from a car journey can be set to what was walked, says so, and feeds the median |

Phase 6 waits for the pilot month's answer to "which kind of wrong day happens" (milestone 1 §6);
phases 1–5 do not. Phases 1 and 2 can be built in parallel by two agents; 3 needs 2; 4 and 5 need
1–3; 6 needs 5.

**Versions:** each phase is a minor version in the current series (0.53.0 onward), release-signed
via `~/bin/ms-release`, checked on the phone before the next starts.

---

## 6. Testing, specifically

- `MetEstimateTest`: every `(kind, effort)` has a value; the standard body's worked figures; a run's
  MET follows pace when distance is given.
- `ActivityEnergyTest`: three readings, maximum wins, ties fall to steps; a typed workout never
  adds to a band day.
- `WorkoutDaoTest` (Robolectric, CI): upsert by origin id updates in place; hidden rows survive
  `dropSyncedNotIn`; `observeWeeklyRunning` groups Mondays correctly across a month boundary —
  fixture days around `TEST_EPOCH_DAY` (2026-09-03, a Thursday).
- `MigrationTest` 5→6, plus the Python check.
- `HealthConnectClient` is an interface and could be faked, but `HealthConnectSteps` is not tested
  that way and this follows it: the reconcile logic lives in a pure `WorkoutReconcile` object that
  takes already-read sessions and existing rows and is tested there; `HealthConnectWorkouts` itself
  stays thin enough that a fake would test the fake.
- `LogWorkoutSheetTest` via `ComposeRender`: reads typed values through `EditableText`; distance
  field absent for Strength; pace line appears when both minutes and distance are present.
- `sim/SimulatedApp.kt` and `DayViewModelTest`'s inline `StepSource` fake gain a `WorkoutRepository`
  fake so the babysitter walks can log a workout.
- **Anonymisation check** before every merge: fixture workouts are named ("Running", "Strength"),
  never narrated; no real distance, pace, weekly volume or frequency anywhere, including test names.

---

## 7. Not built, and why

- **Sets × reps × weight** — D61; table designed, not created.
- **Heart rate, elevation, cadence, power** — no screen would show them; a permission unasked is a
  permission not dismissed.
- **Routes and maps** — the vendor's app draws them; this one records food.
- **Background sync (WorkManager + `READ_HEALTH_DATA_IN_BACKGROUND`)** — the app reads on open by
  design, and a workout synced at 3 a.m. buys nothing over one synced when the phone is next looked
  at.
- **Writing to Health Connect** — D12d, unchanged: another app's store is not this app's to edit.
- **Strava / Garmin / aggregators** — §1.2.
- **A net-calories figure** — D63.
- **Charts** — four numbers in a line, and one hairline rule; the page has no chart library and
  does not need one.
- **The toolchain upgrade** (AGP 8.9.1+, Gradle 8.11.1+, compileSdk 36, Health Connect 1.1.0) —
  worth doing, on its own, first verified by a build in its own branch; nothing here waits for it.

---

## 8. Questions settled with the owner, 2026-09-25

1. **Weekly running target is a setting** — optional, defaulting to none — because a number the
   owner cannot change is a number he will stop agreeing with.
2. **A typed run and a later-synced copy of the same run** count once towards the credit (the
   maximum handles it) but would double the weekly distance. Deferred until it first happens; until
   then the owner hides one. If it turns out to be common, the fix is an offer to merge when the sync
   arrives.
3. **The screen is called "Movement"**, the word Settings already uses.
