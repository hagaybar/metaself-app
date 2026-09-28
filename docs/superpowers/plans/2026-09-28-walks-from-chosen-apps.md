# Walks from chosen apps — Implementation Plan (D81)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D81. On "What the band sends", each app that has written workouts gets a switch, **"Count
its walks as workouts"**, on by default. Switched off, that app's WALK sessions stay stored but are
counted nowhere a workout is counted (the Movement screen's rows and headline, `health_days`'
workout count and minutes, the workouts' heart-rate figures), and are shown on the band page marked
"not counted". Plus the spec's investigation of why a synced workout may carry no distance: the part
that is a flaw in this code is fixed, and the page says, per app, when the app wrote no distance
during its workouts at all.

**Decision:** the owner's, 2026-09-28 — `docs/superpowers/specs/2026-09-28-which-apps-count-as-workouts-design.md`.

**Architecture:**

```
domain/movement/CountedWorkouts.kt     pure: does this workout count? (D81's one rule)   JUnit 5
data/health/WalkChoices.kt             the stored set of apps whose walks do not count  JUnit 5 (@TempDir DataStore)
                                       + WalkSwitch: store the choice, re-summarise
                                         the days of that app's walks                   JUnit 5 (fakes)
data/health/DaySummary.kt              leaves uncounted walks out of workoutCount/Min.  JUnit 5
data/health/RoomHealthStore.kt         reads the set when summarising; an uncounted
                                       walk keeps no heart-rate figures                 Robolectric, CI only
data/health/WorkoutDao.kt              + syncedWalkDays, + idsWithOwnDistance,
                                       + missingTotalsOn (no schema change)             HealthRecordDaoTest, CI only
domain/movement/Workout.kt             + counted (defaulted true)                       compiles only
data/health/MovementRecord.kt          toWorkout(uncounted); Room adapter combines      JUnit 5 (mapping)
domain/movement/MovementWeek.kt        leaves uncounted workouts out, as hidden ones    JUnit 5
domain/health/BandReport.kt            not-counted count; per-app lines                 JUnit 5
data/health/BandRecord.kt              reads the set and each workout's own distance    JUnit 5 (mapping)
ui/health/BandReportWording.kt         the per-app sentences                            JUnit 5
ui/screen/settings/BandReport*         the switches; the write; its failure said (D8)   JUnit 5 + Robolectric
data/health/HealthConnectReader.kt     sessionTotals(start, end, …) shared with session()
data/health/HealthPorts.kt             HealthSource.sessionTotals; HealthStore gaps     
data/health/HealthRecordSync.kt        after phase A, re-asks a session's missing
                                       distance / calories on the days just touched     JUnit 5 (fakes)
```

**Tech Stack:** Kotlin, Compose (Material 3), Hilt, Room (queries only), Preferences DataStore,
kotlinx-coroutines-test, JUnit 5 + Truth, Robolectric (JUnit 4) for render and Room tests. No new
dependency.

**Red lines (stop and report if crossed):**

- **No schema change.** `app/schemas` untouched; no entity, column or migration. New DAO queries are
  fine. The choice lives in the profile DataStore under one new key.
- **No app is named in code, tests or docs** — the stored set is keyed by package; fixtures use
  `com.example.band` and `com.example.phone`. Every figure in a test is invented and says so.
- **Nothing is deleted.** A walk left out stays in `workouts`; the band page still counts it.
- **D4:** a filled-in distance or calories is still Health Connect's aggregate over the session's
  time — the same provenance as a figure read with the session. Nothing is estimated.
- **D8:** a failed write of the choice, a failed re-summarise, a failed re-ask of a session's totals
  are each logged and said (or, in the copying, logged); nothing throws upwards.
- **CI-only database tests go inside `HealthRecordDaoTest` or `HealthRecordStoreTest`** so the local
  skip list stays at exactly ten classes.
- **Never `git add -A`; never stage `app/.settings/*` or `tools/__pycache__`; never bare `./gradlew`;
  never pipe a build whose result is reported.** No version bump, no push, no release.

## Design questions the spec does not answer — settled here

1. **"Walk" is `WorkoutKind.WALK`** — the kind `WorkoutKinds.of` maps Health Connect's walking types
   to. Hiking, if it maps elsewhere, is not a walk here.
2. **A typed workout always counts.** It has no writing app; the switch is keyed by package.
3. **An app switched off but with no workouts in the 30 days is still listed**, with its switch, so a
   choice can always be undone from the page that made it.
4. **Switching re-summarises at once**, without Health Connect: the days holding that app's synced
   walks (all of them, not only the 30 days) are summarised again with every total "failed", which
   keeps each stored total (`RoomHealthStore.keepingStored`). So `health_days` agrees with the choice
   before the next open.
5. **An uncounted walk keeps no heart-rate figures** (average, maximum, zones), as a typed workout
   keeps none: the spec lists heart-rate figures among the places a workout is counted. Its readings
   are untouched, so switching back on works them out again at the re-summarise.
6. **The Movement screen leaves an uncounted walk out of its rows too**, as it does a hidden one (the
   spec: "the Movement screen's rows and headline").
7. **The switch's state is held by the page while the write runs** and the report is read again after
   it, so the switch moves at once and the counts follow.
8. **The choice is not in the JSON backup.** A backup restored on a new phone counts every app's walks
   until switched off again. Stated in the PR as not built.
9. **Band page per app, under Workouts:** the app's name, then "6 workouts · 4 walks" (invented) (", not
   counted" after the walks when switched off), then "distance on 2 of 6" (invented), then — only when the app
   wrote no distance reading of its own during any of those workouts — "distance not shared for
   workouts by this app", then the switch. Above them, after "copied · typed", a line "4 not counted" (invented)
   when any are.

## The distance investigation — what the code shows

Recorded here so the plan's Task 8 has its reasons; the report restates it.

- `ExerciseSessionRecord` in connect-client 1.1.0 carries **no distance of its own** (checked with
  `javap` on the pinned jar: start/end, zone offsets, metadata, exerciseType, title, notes, segments,
  laps, exerciseRouteResult, plannedExerciseSessionId). A lap has an optional `length`; a route is
  GPS points behind its own permission. So a session's distance can only come from `DistanceRecord`s
  over its time — which is what `HealthConnectReader.session()` asks for.
- The ask is `aggregate(DISTANCE_TOTAL)` over `[start, end)` with **no data-origin filter**: every
  app's distance, de-duplicated by Health Connect's priority list — the same aggregation the daily
  total uses, and that one arrives. Filtering to the session's own app could only find the same or
  less, so it is not a fix.
- **The flaw:** the ask is made **once**, at the moment the session is read (a change, or a catch-up
  week), and never again unless the session record itself changes. A writing app that stores the
  session before the distance of that stretch (a band's app syncing its interval data in a later
  batch; a phone app writing step-derived distance in buckets) leaves a session read in between with
  no distance forever — and the same for its calories.
- **What cannot be told from here:** whether the owner's apps write distance during workouts at all.
  The stored `DISTANCE` readings can: the page counts, per app, the workouts during which that app
  wrote a distance reading of its own, and says "distance not shared for workouts by this app" when
  that is none.

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` and `~/bin/ms-release` share one lock; a
finished build's daemon lingers up to ten minutes. **Before every Gradle command run `free -m`;** under
about 4000 MB available (6000 MB before `ms-release`), wait and check again. Never `gradlew --stop`.
One build at a time.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-d81.log 2>&1; echo "exit $?"
```

SQLite classes skip locally (ten: the eight `CLAUDE.md` names plus `HealthRecordDaoTest` and
`HealthRecordStoreTest`); report them as skipped.

---

### Task 1: The rule — does this workout count?

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/movement/CountedWorkouts.kt`
- Test: `app/src/test/java/com/metaself/app/domain/movement/CountedWorkoutsTest.kt`

- [ ] **Step 1: failing tests** (JUnit 5): a walk from an app in the set does not count; a walk from
  another app counts; a run from an app in the set counts; a typed walk (origin null) counts; an
  empty set counts everything.

```kotlin
@Test
fun `a walk from an app left out does not count, its run does`() {
    val out = setOf(BAND)
    assertThat(CountedWorkouts.counts(WorkoutKind.WALK, BAND, out)).isFalse()
    assertThat(CountedWorkouts.counts(WorkoutKind.RUN, BAND, out)).isTrue()
    assertThat(CountedWorkouts.counts(WorkoutKind.WALK, PHONE, out)).isTrue()
    assertThat(CountedWorkouts.counts(WorkoutKind.WALK, null, out)).isTrue()
}
```

- [ ] **Step 2:** run, see it fail to compile.
- [ ] **Step 3: implement**

```kotlin
/** D81: the one rule for whether a workout counts as one, wherever workouts are counted. Pure. */
object CountedWorkouts {
    /**
     * False only for a WALK written by an app in [uncountedWalkApps]. A typed workout (no [origin])
     * always counts; every other kind from that app still counts. Hidden is a separate rule.
     */
    fun counts(kind: WorkoutKind, origin: String?, uncountedWalkApps: Set<String>): Boolean =
        kind != WorkoutKind.WALK || origin == null || origin !in uncountedWalkApps
}
```

- [ ] **Step 4:** run, pass. **Step 5:** commit `feat: which workouts count (D81)`.

### Task 2: The stored choice, and the switch that applies it

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/WalkChoices.kt`
- Modify: `app/src/main/java/com/metaself/app/data/health/WorkoutDao.kt` (+ `syncedWalkDays`)
- Modify: `app/src/main/java/com/metaself/app/di/DataModule.kt` (bind both)
- Test: `app/src/test/java/com/metaself/app/data/health/WalkChoicesTest.kt` (JUnit 5, `@TempDir` DataStore,
  and the switch over fakes); DAO query in `HealthRecordDaoTest` (CI).

```kotlin
interface WalkChoices {
    /** The packages whose walks do not count (D81). Empty until one is switched off. */
    val uncounted: Flow<Set<String>>
    suspend fun setCounted(origin: String, counted: Boolean)
}

class DataStoreWalkChoices @Inject constructor(private val store: DataStore<Preferences>) : WalkChoices {
    override val uncounted = store.data.map { it[KEY] ?: emptySet() }
    override suspend fun setCounted(origin: String, counted: Boolean) {
        store.edit { p -> val now = p[KEY] ?: emptySet(); p[KEY] = if (counted) now - origin else now + origin }
    }
    private companion object { val KEY = stringSetPreferencesKey("walks_not_counted") }
}

/** Stores the choice, then re-summarises every day holding that app's synced walks. */
fun interface WalkSwitch { suspend fun set(origin: String, counted: Boolean) }

class RecountingWalkSwitch @Inject constructor(
    private val choices: WalkChoices, private val workouts: WorkoutDao,
    private val store: HealthStore, private val now: Now,
) : WalkSwitch {
    override suspend fun set(origin: String, counted: Boolean) {
        choices.setCounted(origin, counted)
        val days = workouts.syncedWalkDays(origin).toSet()
        if (days.isNotEmpty()) store.summarise(days, TotalsResult.ALL_FAILED, now())
    }
}
```

DAO: `@Query("SELECT DISTINCT epochDay FROM workouts WHERE source = 'SYNCED' AND kind = 'WALK' AND origin = :origin") suspend fun syncedWalkDays(origin: String): List<Long>`

- [ ] Tests first: a fresh store holds no choice; off then on round-trips; two apps are kept apart;
  switching off one app leaves the other's choice; the switch stores and then summarises exactly the
  days its DAO returns with `ALL_FAILED` (a fake `WorkoutDao` is too wide, so `RecountingWalkSwitch`
  takes a `suspend (String) -> List<Long>` for the days in its primary constructor and the `@Inject`
  one passes `workouts::syncedWalkDays`). DAO test (CI): only that app's synced walks' days.
- [ ] Run, fail; implement; run, pass; commit `feat: the stored choice of whose walks count (D81)`.

### Task 3: The daily summary and the heart-rate figures

**Files:** `DaySummary.kt`, `RoomHealthStore.kt`; tests `DaySummaryTest.kt` (JUnit 5),
`HealthRecordStoreTest.kt` (CI); every `RoomHealthStore(...)` in tests gains a `WalkChoices`.

- `DaySummary.of(..., nowMillis, uncountedWalkApps: Set<String> = emptySet())`:
  `val counted = workouts.filter { !it.hidden && CountedWorkouts.counts(WorkoutKind.parse(it.kind), it.origin, uncountedWalkApps) }`
  used for `workoutCount/Minutes/Source` and the "nothing" test.
- `RoomHealthStore` takes `walks: WalkChoices`; `summarise` reads `walks.uncounted.first()` once and
  passes it on; `withHeartRate` clears the four figures of an uncounted walk exactly as of a typed one.
- [ ] Tests first: an uncounted walk is not in the count or minutes; its run is; a day holding only an
  uncounted walk and nothing else has no summary. CI: a walk from an app switched off keeps no
  heart-rate figures after a summarise, and gets them back once switched on and summarised again;
  summarising with `ALL_FAILED` keeps the stored totals and drops the walk from the count.
- [ ] Commit `feat: walks left out are not in the day's workouts (D81)`.

### Task 4: The Movement screen

**Files:** `Workout.kt` (+ `val counted: Boolean = true`), `MovementRecord.kt`
(`toWorkout(uncountedWalkApps = emptySet())`; `RoomMovementRecord` takes `WalkChoices` and combines),
`MovementWeek.kt` (`filter { !it.hidden && it.counted && ... }`); tests `MovementRecordTest`,
`MovementWeekTest`.

- [ ] Tests first: a stored walk from an app switched off maps with `counted = false`, its run with
  `true`; the week leaves an uncounted walk out of its rows, workout count, walk count and minutes.
- [ ] Commit `feat: the Movement screen leaves out walks that do not count (D81)`.

### Task 5: The band report counts them, app by app

**Files:** `BandReport.kt`, `BandRecord.kt`, `WorkoutDao.kt` (+ `idsWithOwnDistance`);
tests `BandReportTest`, `BandRecordTest`, DAO in `HealthRecordDaoTest` (CI).

- `ArrivedWorkout` + `id: Long = 0`, `ownDistance: Boolean = false` (the writing app itself wrote a
  distance reading during it).
- `BandReport` + `uncountedWalkApps: Set<String>`; `WorkoutArrivals` + `notCounted: Int`,
  `apps: List<WorkoutApp>`:

```kotlin
data class WorkoutApp(
    val origin: String, val workouts: Int, val walks: Int,
    val withDistance: Int, val withOwnDistance: Int, val walksCounted: Boolean,
)
```

  Apps: every origin of a copied workout in the window, plus every app in the set; most workouts
  first, a tie by name.
- DAO:

```sql
SELECT w.id FROM workouts w WHERE w.source = 'SYNCED' AND w.epochDay BETWEEN :from AND :to AND EXISTS (
  SELECT 1 FROM health_readings r WHERE r.kind = 'DISTANCE' AND r.origin = w.origin
  AND r.epochDay BETWEEN w.epochDay - 1 AND w.epochDay + 1
  AND r.startMillis < w.startedAtMillis + w.durationMinutes * 60000
  AND COALESCE(r.endMillis, r.startMillis) > w.startedAtMillis)
```

- [ ] Tests first: not-counted count; per-app counts; an app switched off with nothing in the window is
  listed; `toArrived(ownDistance = true)` carries it. CI: a same-app distance reading inside the
  workout marks it; another app's reading, or one outside its time, does not.
- [ ] Commit `feat: the band report counts each app's workouts and walks (D81)`.

### Task 6: What the page says, and the switches

**Files:** `BandReportWording.kt`, `BandReportPage.kt`, `BandReportViewModel.kt`,
`SettingsDestination.kt`, `strings.xml`, `DataModule.kt`; tests `BandReportWordingTest`,
`BandReportViewModelTest`, `BandReportRenderTest`.

- Wording: `notCountedLine(w)` → "4 not counted" (invented) or null; `appLines(app)` → ["6 workouts · 4 walks" (invented),
  "distance on 2 of 6" (invented), "distance not shared for workouts by this app"?]; ", not counted" after the
  walks when off; "no workouts in these 30 days" for a listed app with none. `asText` includes them
  and "walks counted: yes/no".
- View model: `uncounted: Set<String>` in the state (from the report, then updated at once on a
  switch); `setWalksCounted(origin, counted)` → `WalkSwitch.set`, then read again; a failure is logged
  (kind "band report") and the page says "The choice could not be saved; Recent problems says why."
- Page: under Workouts, per app a name line, its lines and a `Switch` row labelled "Count its walks
  as workouts".
- [ ] Tests first (wording, view model with a fake switch, render: the switch label follows the app's
  lines and a click calls back with the origin and the new value).
- [ ] Commit `feat: switch an app's walks off on the band page (D81)`.

### Task 7: Distance and calories asked again when missing

**Files:** `HealthPorts.kt`, `HealthConnectReader.kt`, `HealthRecordSync.kt`, `RoomHealthStore.kt`,
`WorkoutDao.kt` (+ `missingTotalsOn`); tests `HealthRecordSyncTest` (JUnit 5 fakes; the two other
`HealthStore` fakes gain the two methods), `HealthRecordStoreTest` and `HealthRecordDaoTest` (CI).

```kotlin
data class SessionTotals(val distanceM: Int? = null, val energyKcal: Int? = null)
data class SessionGap(val id: Long, val startMillis: Long, val endMillis: Long, val needsDistance: Boolean, val needsEnergy: Boolean)

// HealthSource
suspend fun sessionTotals(startMillis: Long, endMillis: Long, distance: Boolean, energy: Boolean): SessionTotals
// HealthStore
suspend fun sessionGaps(days: Set<Long>): List<SessionGap>
suspend fun fillSessionTotals(id: Long, totals: SessionTotals)
```

- Sync, after phase A and before its summarise, when EXERCISE is granted: for each gap on the days
  phase A touched, ask only for what is missing AND granted; store what came back. One failure is
  logged ("workout totals: …") and the rest go on. Phase B's days are not re-asked (just read).
- Store: fills a null distance; fills calories only when null with source NONE (then BAND); never
  replaces a figure; a typed or hidden row is not a gap. DAO:
  `SELECT * FROM workouts WHERE source = 'SYNCED' AND hidden = 0 AND epochDay IN (:days) AND (distanceM IS NULL OR (energyKcal IS NULL AND energySource = 'NONE'))`
- [ ] Tests first: a session with no distance on a day phase A touched is asked for distance only,
  and what comes back is stored; nothing is asked when nothing is missing; distance not granted →
  not asked; a failing ask is logged and the next gap is still asked; gaps on days only phase B
  touched are not asked. CI: a gap is found and filled; a figure already there is never replaced.
- [ ] Commit `fix: a workout read before its distance arrived asks again (D81)`.

### Task 8: Suite, lint, build

- [ ] `free -m`; whole suite to `/tmp/ms-d81.log`; exit 0; from the XML: 0 failures, skipped exactly
  the ten SQLite classes.
- [ ] `:app:lintDebug` exit 0; `:app:assembleDebug` exit 0. No version bump, no push.
