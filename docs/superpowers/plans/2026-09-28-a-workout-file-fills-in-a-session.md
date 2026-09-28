# A workout file fills in a session — Implementation Plan (D82)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** D82. A TCX file — shared to MetaSelf from another app, or picked with "Import a workout
file" on the Movement screen — fills in what Health Connect did not carry on the matching synced
session (distance, steps, calories), never replacing a figure already there, each filled figure
stored with its source `FILE`. No match offers "Add it as a workout"; several matches are listed to
choose from. One confirmation line says what happened. Importing the same file twice changes nothing.

**Decision:** the owner's, 2026-09-28 — `docs/superpowers/specs/2026-09-28-a-workout-file-fills-in-a-session-design.md`.

**Architecture:**

```
domain/movement/WorkoutFile.kt        pure: FileWorkout (what a file says), WorkoutFileRefusal,
                                      ImportOutcome, AddedFigures                          compiles only
domain/movement/TcxReader.kt          pure: TCX text → FileWorkout or a refusal            JUnit 5
domain/movement/WorkoutFileMatch.kt   pure: which stored workouts a file matches;
                                      what filling one would change; the workout "Add" makes JUnit 5
data/health/WorkoutFiles.kt           WorkoutFileStore (Room: candidates, fill in one
                                      transaction, add via TypedWorkouts) + ImportWorkoutFile
                                      (reads, matches, fills or offers)                     JUnit 5 (fakes)
data/health/WorkoutFileSource.kt      the file's text from a content Uri, size-capped       compiles only
data/health/SharedWorkoutFiles.kt     the one pending shared file (a holder)               JUnit 5
data/health/HealthEntities.kt         WorkoutEntity + distanceSource, steps, stepsSource   schema v7
data/day/WorkoutFileMigration.kt      MIGRATION_6_7, verbatim from 7.json                  tools/check-migration-6-7.py + MigrationTest (CI)
data/health/RoomHealthStore.kt        a re-read session keeps the file's figures           HealthRecordStoreTest (CI)
data/health/MovementRecord.kt         mapping of the three fields both ways                JUnit 5
data/health/TypedWorkouts.kt          a changed typed workout keeps unchanged file figures JUnit 5
domain/backup/Backup.kt + repository  three fields; version 4                              JUnit 5 (codec)
ui/movement/WorkoutFileWording.kt     the confirmation line, refusals, choice rows         JUnit 5
ui/movement/MovementWeekWording.kt    "3.25 km (from file)"; "… kcal, from the file"       JUnit 5
ui/screen/movement/*                  the import entry, the line, Add, the choices         JUnit 5 VM + Robolectric render
MainActivity.kt, AndroidManifest.xml  the share target (ACTION_SEND, the spec's MIME types)
ui/nav/MetaSelfNavHost.kt             a pending shared file opens Movement; the picker
```

**Tech Stack:** Kotlin, `javax.xml.parsers` (DOM; on the JVM for tests and on Android), Compose
(Material 3) with `ActivityResultContracts.OpenDocument`, Hilt, Room (schema v7, hand-written
migration), kotlinx.serialization (backup), JUnit 5 + Truth, Robolectric (JUnit 4) for render and
Room tests. No new dependency.

**Red lines (stop and report if crossed):**

- **A measurement is never replaced (D4).** A figure already on the session stays; the file's is
  shown beside it only in the confirmation line. A filled figure is stored with source `FILE`.
- **The file's average heart rate is not stored** (D70: worked out from the readings).
- **No real file.** Every TCX in a test is built in the test from invented, round figures, labelled
  as invented. No app is named in code; no real record appears anywhere.
- **The migration is the exported schema's own SQL**, character for character, checked by
  `tools/check-migration-6-7.py` here and `MigrationTest` in CI. `app/schemas` gains `7.json` only.
- **D8:** a file that cannot be opened, or a write that fails, is logged (kind "workout file") and
  said; nothing throws upwards. A file refused for its content is said, not logged.
- **CI-only database tests go inside `HealthRecordStoreTest` or `MigrationTest`** so the local skip
  list stays at exactly ten classes.
- **Never `git add -A`; never stage `app/.settings/*` or `tools/__pycache__`; never bare `./gradlew`;
  never pipe a build whose result is reported.** No version bump, no push, no release.

## Design questions the spec does not answer — settled here

1. **Parser.** `javax.xml.parsers.DocumentBuilderFactory`, namespace-aware, elements matched by
   local name so any prefix or namespace is accepted; DOCTYPE refused (no external entities). A
   byte-order mark and leading whitespace are stripped. The first `Activity` is read; others ignored.
2. **What is read, per lap, summed:** `TotalTimeSeconds`, `DistanceMeters`, `Calories`, `Steps`
   (anywhere under the lap, extensions included). Heart rate: `AverageHeartRateBpm/Value`, or a
   plain number inside `HeartRateBpm` directly under the lap; averaged by lap time; read, not stored.
   Trackpoints are never read. A lap's figure that does not parse is left out of the sum (tolerant);
   a file with no lap time at all is refused.
3. **Start:** the `Activity`'s `Id`, else the first lap's `StartTime`. Written with an offset or `Z`,
   it gives both the wall clock as written and a true instant; written with none, only the wall clock.
4. **Refusals, each said in plain words:** not XML (or has a DOCTYPE); no activity; no start; no
   duration; nothing to add (no distance, steps or calories); larger than 5 MB (a choice — a summary
   is a few kB); could not be opened (logged).
5. **Matching** (`WorkoutFileMatch`): candidates are the visible synced workouts, and the typed
   workouts a file made (so a second import of an added workout finds it), on the local day(s) of the
   file's start. A candidate matches when its start is within **±10 minutes** (a choice) of the file's
   wall-clock time taken in the phone's zone, or of the file's true instant; its stored day is the
   local day of that time; and the file's duration is within **±10 %** (a choice) of its stored
   minutes, never less than ±1 minute (stored minutes are rounded).
6. **"Add it as a workout":** a TYPED workout at the wall-clock start in the phone's zone; minutes
   rounded, at least 1; kind from `Sport` (running → RUN, biking/cycling → CYCLE, walking → WALK,
   swimming → SWIM, else OTHER); no effort (nothing was felt, so none is claimed); distance and steps
   from the file with source `FILE`.
7. **Calories from a file are `EnergySource.FILE`, in both paths.** The spec says TYPED for an added
   workout "labelled from the file"; a TYPED source would say the owner typed it (D4) and leaves
   nothing to label from, so the new enum value carries it. The open day says "250 kcal, from the
   file" (invented). The sheet treats FILE energy as the workout's own figure, so opening and saving
   an added workout keeps it; a figure the owner then changes becomes TYPED.
8. **A changed typed workout keeps each file figure it did not change** (`RoomTypedWorkouts.change`):
   steps always; distance with `FILE` while the metres are the same, else `TYPED`; calories `FILE`
   while the same.
9. **A session re-read by the sync keeps the file's figures:** steps and their source always; a file
   distance or file calories while Health Connect still gives none. If Health Connect later gives
   one, its figure is taken (the session's own source, as before D82).
10. **Distance format:** a figure from a file is shown to two decimals — "3.25 km" (invented) — in the
    confirmation line and the open day; every other distance keeps the app's one decimal.
11. **Confirmation lines** (every figure invented):
    - filled: "Added 3.25 km and 4,000 steps to Walking, Thu 3 Sep 10:00."
    - filled, distance kept: "Added 4,000 steps to Walking, Thu 3 Sep 10:00. It already had 3.2 km;
      the file says 3.25 km."
    - nothing new: "This session already had 3.2 km; nothing changed." (or, with no distance on either,
      "This session already had everything in the file; nothing changed.")
    - none: "No workout matches this file (Thu 3 Sep 10:00)." and the button "Add it as a workout".
    - several: "Several workouts match this file; choose one." then one row per workout
      ("Walking · 10:00 · 40 min").
    - added: "Added Walking, Thu 3 Sep 10:00, from the file."
    - a failed write: "The file could not be added; Recent problems says why."
12. **Share target:** `MainActivity` takes `ACTION_SEND` with `EXTRA_STREAM` for the spec's four MIME
    types; a share with no stream is ignored. The Uri goes to a singleton holder; the nav host opens
    Movement; the Movement screen takes it, only while its navigation entry is RESUMED (so an entry
    under another screen, or in a stopped activity, never takes it), and hands it to the view model to
    import. `MainActivity` reads the share in `onCreate` only: with the default launch mode each share
    starts a new activity, so there is no `onNewIntent` path; a relaunch from Recents
    (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) is not a new share. The picker (`OpenDocument`) offers the
    same four types. Content decides, never the type.
13. **The steps are stored, not shown** on the open day in this version (the spec asks only for the
    distance line); the confirmation line says them.

## The shared box — read before any build

Another project builds on this machine. `~/bin/gradlew-safe` and `~/bin/ms-release` share one lock; a
finished build's daemon lingers up to ten minutes. **Before every Gradle command run `free -m`;** under
about 4000 MB available (6000 MB before `ms-release`), wait and check again. Never `gradlew --stop`.
One build at a time.

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-d82.log 2>&1; echo "exit $?"
```

SQLite classes skip locally (ten, listed in `CLAUDE.md`); report them as skipped.

(For this plan the log file is `/tmp/ms-d82.log`. The ten skipping classes are the eight
`CLAUDE.md` names plus `HealthRecordDaoTest` and `HealthRecordStoreTest`.)

---

### Task 1: Reading a TCX file

**Files:** Create `domain/movement/WorkoutFile.kt`, `domain/movement/TcxReader.kt`; test
`domain/movement/TcxReaderTest.kt` (JUnit 5). Fixtures are built by a `tcx(...)` helper in the test.

```kotlin
data class FileWorkout(
    val writtenAt: LocalDateTime,   // the wall clock as written
    val instant: Instant?,          // only when the file gave an offset or Z
    val seconds: Int,
    val distanceM: Double?,
    val kcal: Int?,
    val steps: Int?,
    val avgHeartRate: Int?,         // read, never stored (D70)
    val sport: String?,
)
enum class WorkoutFileRefusal { NOT_XML, NO_WORKOUT, NO_START, NO_DURATION, NOTHING_TO_ADD, TOO_LARGE, UNREADABLE }
sealed interface TcxRead { data class Read(val workout: FileWorkout) : TcxRead; data class Refused(val reason: WorkoutFileRefusal) : TcxRead }
object TcxReader { fun read(text: String): TcxRead }
```

- [ ] Tests first: a standard file (namespaced) reads start, time, distance, calories and heart rate;
  the band-style file (plain number in `HeartRateBpm`, a `Steps` element, `Z` on a wall-clock time)
  reads steps and heart rate; two laps are summed; a prefixed namespace reads the same; a time with
  an offset gives its instant, one with none gives no instant; trackpoints are ignored; each refusal
  (not XML, DOCTYPE, no activity, no start, no laps, nothing to add); a BOM is tolerated.
- [ ] Run, fail; implement; run, pass; commit `feat: read a TCX workout file (D82)`.

### Task 2: Matching, filling, adding — pure

**Files:** Create `domain/movement/WorkoutFileMatch.kt`; test `WorkoutFileMatchTest.kt`.

```kotlin
object WorkoutFileMatch {
    const val START_MINUTES = 10L        // a choice
    const val DURATION_SHARE = 0.10      // a choice
    fun days(file: FileWorkout, zone: ZoneId): Set<Long>
    fun candidates(stored: List<Workout>): List<Workout>          // visible synced + typed from a file
    fun matches(file: FileWorkout, stored: List<Workout>, zone: ZoneId): List<Workout>
    fun fill(workout: Workout, file: FileWorkout): Workout          // only what is missing, source FILE
    fun added(before: Workout, after: Workout): AddedFigures
    fun asWorkout(file: FileWorkout, zone: ZoneId): Workout         // TYPED, id 0
}
```

- [ ] Tests first: wall-clock match with the zone's offset; true-UTC match; 11 minutes off no match;
  duration 11 % off no match, 1 minute off on a 5-minute session matches; a hidden or plain typed
  workout is never a candidate, a typed-from-file one is; another day never matches; fill keeps an
  existing distance and band calories, fills missing ones with FILE; fill twice = fill once;
  `asWorkout` kind mapping, minutes, FILE sources, no effort.
- [ ] Commit `feat: match a workout file to a session (D82)`.

### Task 3: Schema v7 and its migration

**Files:** `HealthEntities.kt` (three nullable columns), `MetaSelfDatabase.kt` (version 7),
create `data/day/WorkoutFileMigration.kt` (`MIGRATION_6_7`), `DataModule.kt` (add it),
`app/schemas/.../7.json` (generated), create `tools/check-migration-6-7.py`, `tools/README.md` (a line),
`MigrationTest` (6 → 7, CI).

```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `workouts` ADD COLUMN `distanceSource` TEXT")
        db.execSQL("ALTER TABLE `workouts` ADD COLUMN `steps` INTEGER")
        db.execSQL("ALTER TABLE `workouts` ADD COLUMN `stepsSource` TEXT")
    }
}
```

- [ ] Bump the version, build once to generate `7.json`, write the migration from its column
  definitions, write the Python check (every statement parses; `workouts` after the migration has the
  shape of a fresh `workouts` from 7.json; every other table's CREATE is the same in 6.json and
  7.json; every row of every table is kept, the new columns null). Run it: `OK`.
- [ ] MigrationTest: a version 6 database with one synced workout migrates, validates, keeps it, and
  its three new columns are null.
- [ ] Commit `feat: workouts can hold a file's distance and steps (D82, schema 7)`.

### Task 4: The domain workout, the mapping, the typed store, the sync

**Files:** `Workout.kt` (+ `WorkoutFigureSource { FILE, TYPED }`, `EnergySource.FILE`,
`distanceSource`, `steps`, `stepsSource`, `fromFile`), `MovementRecord.kt`, `TypedWorkouts.kt`,
`WorkoutDraft.kt` (FILE energy is own), `RoomHealthStore.kt`; tests `MovementRecordTest`,
`RoomTypedWorkoutsTest`, `WorkoutDraftTest`, `HealthRecordStoreTest` (CI).

- [ ] Tests first: mapping round-trips the three fields and FILE energy; a changed typed workout keeps
  steps, keeps FILE distance while unchanged and turns it TYPED when changed; the sheet opens FILE
  energy as own. CI: a re-read session keeps file steps and a file distance while Health Connect has
  none, and takes Health Connect's when it has one.
- [ ] Commit `feat: a file's figures survive a re-read and an edit (D82)`.

### Task 5: The backup

**Files:** `Backup.kt` (`distance_source`, `steps`, `steps_source`, all defaulted; `CURRENT_VERSION = 4`
with its KDoc line), `BackupRepository.kt` (both mappings); test `BackupCodecTest`.

- [ ] Tests first: the format is version 4; a workout's three fields survive encode/decode; a
  version 3 workout without them reads with nulls.
- [ ] Commit `feat: the backup keeps a file's figures (D82, backup 4)`.

### Task 6: The import, over fakes

**Files:** create `data/health/WorkoutFiles.kt`, `WorkoutFileSource.kt`, `SharedWorkoutFiles.kt`;
`DataModule.kt`; tests `ImportWorkoutFileTest.kt`, `SharedWorkoutFilesTest.kt`.

```kotlin
sealed interface ImportOutcome {
    data class Refused(val reason: WorkoutFileRefusal) : ImportOutcome
    data class Filled(val workout: Workout, val added: AddedFigures, val fileDistanceM: Int?) : ImportOutcome
    data class Unchanged(val workout: Workout, val fileDistanceM: Int?) : ImportOutcome
    data class NoMatch(val file: FileWorkout) : ImportOutcome
    data class Several(val file: FileWorkout, val choices: List<Workout>) : ImportOutcome
    data class AddedWorkout(val workout: Workout) : ImportOutcome
    data object Failed : ImportOutcome
}
interface WorkoutFileStore { suspend fun on(days: Set<Long>): List<Workout>; suspend fun fill(id: Long, file: FileWorkout): Pair<Workout, Workout>?; suspend fun add(workout: Workout): Long }
class ImportWorkoutFile(source, store, problems, zone) { suspend fun import(uri: String): ImportOutcome; suspend fun choose(file, id): ImportOutcome; suspend fun add(file): ImportOutcome }
```

- [ ] Tests first: one match is filled and the outcome says what was added; the same file again is
  Unchanged; none → NoMatch, then `add` stores a typed workout, and the file again finds it and is
  Unchanged; several → Several, `choose` fills that one; an unopenable file is Refused(UNREADABLE)
  and logged; a failing write is Failed and logged; the holder hands a shared file out once.
- [ ] Commit `feat: import a workout file (D82)`.

### Task 7: Wording

**Files:** create `ui/movement/WorkoutFileWording.kt`; `MovementWeekWording.kt`; tests.

- [ ] Tests first: every line of design question 11, and each refusal; the open day's "3.25 km (from
  file)" and "250 kcal, from the file" (both invented).
- [ ] Commit `feat: say what a workout file changed (D82)`.

### Task 8: The screen, the picker, the share target

**Files:** `MovementUiState.kt`, `MovementViewModel.kt`, `MovementScreen.kt`, `MetaSelfNavHost.kt`,
`MainActivity.kt`, `AndroidManifest.xml`, `strings.xml`; tests `MovementViewModelTest`,
`MovementScreenRenderTest`.

- [ ] Tests first: the view model imports a pending shared file once; `importFile(uri)` puts the
  outcome in the state; `addFromFile()` and `chooseForFile(id)` act on the held file; dismiss clears.
  Render: "Import a workout file" is drawn after the week; the outcome line is drawn; with no match
  "Add it as a workout" is a button that calls back; several matches are buttons that call back with
  the id. (Not provable here: the picker opening, the share list — phone checks.)
- [ ] Commit `feat: import a workout file from Movement or the share list (D82)`.

### Task 9: Suite, lint, build

- [ ] `free -m`; whole suite to `/tmp/ms-d82.log`; exit 0; from the XML: 0 failures, skipped exactly
  the ten SQLite classes. `:app:lintDebug` exit 0; `:app:assembleDebug` exit 0;
  `python3 tools/check-migration-6-7.py` prints OK; `git status app/schemas` shows only `7.json` new.
  No version bump, no push.

**Phone checks owed (not provable here):** MetaSelf in the share list of the band's app's TCX export;
the picker opening at Downloads; the matched session's open day showing the file's distance; the
migration on the real database (CI's MigrationTest first).
