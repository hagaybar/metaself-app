# One session, several witnesses (D92) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overlapping stored workouts are read as witnesses of one session: one line on Movement, one
count everywhere sessions are counted, each figure from the witness that knows it best with its source,
a disagreeing distance shown, "also recorded by N more", and "These are two sessions" stored as splits
that survive a sync and go into the backup.

**Architecture:** One pure combiner, `SessionWitnesses.combine(workouts, splits)` in
`domain/movement`, applied once where the record is read (`RoomMovementRecord.observeWorkouts`). Every
reader of sessions already goes through `MovementRecord` or is moved onto it (typed reading, file fill,
the review screen's session), so each gets combined sessions without its own copy of the rule. A
combined session is an ordinary `Workout` carrying the lead's id, start and minutes, the chosen figures,
and `witnesses` (every witness as stored, lead first) — so no consumer's type changes. Reviews stay
keyed by workout id; `SessionReviews.bySession` re-keys a witness's review onto its session where
reviews are read beside sessions. Splits live in a new Room table (version 9) and the backup (format 7).

**Tech Stack:** Kotlin 1.9.22, Compose (compiler 1.5.9), Room 2.6 with hand-written migrations, Hilt,
kotlinx.serialization, JUnit 5 + Truth (JUnit 4 only for Robolectric/Compose render tests).

**Spec:** `docs/superpowers/specs/2026-09-28-one-session-several-witnesses-design.md`.

---

## Ground rules for every task

- `export ANDROID_HOME=/home/ubuntu/android-sdk`; build only with `~/bin/gradlew-safe`; `free -m` first
  (under 4000 MB available: wait 60 s and recheck, up to ~20 min). Never pipe a build whose result is
  reported: redirect it to a log file and check `$?`.
- A targeted run: `~/bin/gradlew-safe :app:testDebugUnitTest --tests '<fqcn>' > $LOG 2>&1; echo $?`.
- Stage explicit paths only. Commit messages end with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and carry no session link.
- Public repository: every figure in a test or comment is invented and round; test names are published
  prose; no device or app brand in copy.
- Compose: never an early `return` out of an inline composable lambda (compiler 1.5.9 miscompiles);
  branch instead. `InlineComposableReturnGuardTest` must pass.

## Times used by the tests

`TEST_EPOCH_DAY = 20_699` (3 September 2026). Session times are written as minutes after an invented
base instant, `at(m) = BASE + m * 60_000` with `BASE = 1_000_000_000`, so "A 0–60, B 30–60" reads as the
overlap it is. Ids are small and chosen per test so the tie rules can be seen.

## File map

| File | Change |
|---|---|
| `domain/movement/Workout.kt` | `witnesses`, `otherDistance`, `ownDistance` fields; `alsoRecordedBy`, `witnessIds`, `asStored` |
| `domain/movement/SessionWitnesses.kt` | **new**: `SessionSplit`, `OtherDistance`, `DistanceWitness`, `SessionWitnesses` (grouping, lead, figures, splits, hide) |
| `domain/trainer/SessionReviews.kt` | **new**: a witness's review re-keyed onto its session |
| `domain/trainer/TrainerRequest.kt`, `MonthlyLines.kt`, `TrainerHome.kt` | reviews through `SessionReviews.bySession` |
| `data/health/SessionSplits.kt` | **new**: `SessionSplitEntity`, `SessionSplitDao`, `SessionSplits` + `RoomSessionSplits` |
| `data/day/MetaSelfDatabase.kt`, `data/day/SessionSplitMigration.kt` (**new**), `di/DataModule.kt` | version 9, `MIGRATION_8_9`, DAO + store providers |
| `app/schemas/.../9.json` | exported by the build, committed |
| `tools/check-migration-8-9.py` | **new**, in the pattern of 7-8 |
| `data/health/MovementRecord.kt` | combine on read; `sessionOf(id)` |
| `data/health/TypedWorkouts.kt` | `observe` = the typed-led combined sessions |
| `data/health/WorkoutFiles.kt` | `on(days)` = combined sessions |
| `data/trainer/TrainerStore.kt` | `workout(id)` = the session that workout is a witness of |
| `ui/movement/MovementWeekWording.kt` | "also recorded by N more"; the disagreeing distance |
| `ui/screen/movement/MovementViewModel.kt`, `MovementScreen.kt`, `ui/nav/MetaSelfNavHost.kt`, `res/values/strings.xml` | "These are two sessions"; reviews re-keyed; a review button opens the witness's own review |
| `domain/backup/Backup.kt`, `data/backup/BackupSplits.kt` (**new**), `data/backup/BackupRepository.kt` | format 7, `session_splits` by file position |
| `app/build.gradle.kts` | 0.66.0, versionCode 122 |

---

### Task 1: Grouping witnesses into sessions (pure)

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/movement/Workout.kt`
- Create: `app/src/main/java/com/metaself/app/domain/movement/SessionWitnesses.kt`
- Modify: `app/src/test/java/com/metaself/app/domain/movement/Workouts.kt` (a synced-workout fixture)
- Test: `app/src/test/java/com/metaself/app/domain/movement/SessionWitnessesTest.kt`

- [ ] **Step 1: Failing tests** — grouping only: a session wholly inside another is one session; overlap
  of exactly half the shorter is one session; just under half is two; A–B and B–C with A, C apart are
  one (transitive); a split pair stays two; a split pair is not rejoined through a third witness that
  overlaps both (the third joins the first one it meets in id order, never both); a hidden workout
  and an uncounted walk (D81) are never witnesses and pass through unchanged; a workout recorded once
  comes back exactly as given (no `witnesses`); the output is ordered by start.
- [ ] **Step 2: Run** `SessionWitnessesTest` → FAIL (no `SessionWitnesses`).
- [ ] **Step 3: Implement.** New fields on `Workout` (defaulted, so nothing else changes):

```kotlin
/** D92: every witness of this session as stored, lead first; empty for a session recorded once. */
val witnesses: List<Workout> = emptyList(),
/** D92: another witness's distance, when it differs from [distanceM] by more than 15 %. */
val otherDistance: OtherDistance? = null,
/** D81: its own app wrote a distance reading during it. Worked out as it is read, never stored. */
val ownDistance: Boolean = false,
```
with `alsoRecordedBy = (witnesses.size - 1).coerceAtLeast(0)`, `witnessIds = witnesses.map { it.id }
.ifEmpty { listOf(id) }`, `asStored = witnesses.firstOrNull() ?: this`.

`SessionWitnesses.combine(workouts, splits)`: candidates are `!hidden && counted`; sort by
(start, id); pairs found by a sweep that stops once the next start is past the current end;
`overlaps(a, b)`: overlap × 2 ≥ the shorter's length (lengths in whole minutes, as stored; a
zero-minute session is a witness only when its instant lies inside the other). Edges are taken in
(lower id, higher id) order and joined unless the two groups hold a split pair between them. Groups of
one pass through untouched; larger groups become one `Workout` (Task 2 fills the figures; here the lead
alone with `witnesses`).

`SessionSplit(firstId, secondId)` with `require(firstId < secondId)` and `of(a, b)` ordering them.

- [ ] **Step 4: Run** → PASS. **Step 5: Commit** `feat: overlapping workouts are grouped as one session (D92)`.

### Task 2: The lead, and each figure from the best witness (pure)

**Files:** `SessionWitnesses.kt`, `SessionWitnessesTest.kt`.

- [ ] **Step 1: Failing tests:**
  - lead order: synced with heart rate > synced > added from a file > typed; ties → longer, then lower id;
    the session has the lead's id, day, start and minutes;
  - kind: the lead's; when the lead's is OTHER (or one this version does not know) the first witness in
    lead order with a specific kind, and its title with it;
  - distance: typed by the owner > a file's > a witness whose own app recorded distance (D81) > Health
    Connect's total; its source kept (`distanceSource` FILE / TYPED / null);
  - steps: a file's first, else any witness's;
  - heart rate (average, highest, zones, what they were measured against): the lead's, else the first
    witness with one, all four from that one witness;
  - calories: band > file > typed > estimate > none, with the source;
  - disagreement: another witness's distance more than 15 % apart (of the larger) is carried as
    `otherDistance` with who said it (another app / a file / typed); exactly 15 % is not; the widest
    one when several;
  - a typed witness under a synced lead: `asStored` is the lead as stored, `witnesses` holds the typed
    one untouched;
  - `splitsOf(session)`: the lead against each other witness, lower id first;
  - `toHide(session)`: every synced witness's id, no typed one.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** in `SessionWitnesses.session(group)`.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit** `feat: a combined session takes each figure from its best witness (D92)`.

### Task 3: Reviews follow the session (pure)

**Files:**
- Create: `app/src/main/java/com/metaself/app/domain/trainer/SessionReviews.kt`
- Modify: `TrainerRequest.kt`, `MonthlyLines.kt`, `TrainerHome.kt`
- Test: `app/src/test/java/com/metaself/app/domain/trainer/SessionReviewsTest.kt`, plus one case each in
  `TrainerRequestTest`, `MonthlyLinesTest`, `TrainerHomeTest`

- [ ] **Step 1: Failing tests:** the lead's review is the session's; with none on the lead, the first
  witness's in lead order; every review stays under its own workout id too. `TrainerRequest.of` with a
  combined session sends one session carrying the witness's felt effort; `MonthlyLines` counts it once
  with that felt; `TrainerHome` does not offer as "waiting" a session whose other witness has a review.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement**:

```kotlin
object SessionReviews {
    fun bySession(sessions: List<Workout>, reviews: List<TrainerReview>): Map<Long, TrainerReview> {
        val byWorkout = reviews.associateBy { it.workoutId }
        val out = byWorkout.toMutableMap()
        sessions.forEach { s ->
            if (s.id !in byWorkout) s.witnesses.firstNotNullOfOrNull { byWorkout[it.id] }?.let { out[s.id] = it }
        }
        return out
    }
}
```
and use it for `byWorkout` in `TrainerRequest.of`, `feltBy` in `MonthlyLines.of`, `byWorkout` in
`TrainerHome.of`.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit** `feat: a session's review may be on any of its witnesses (D92)`.

### Task 4: The splits table, Room version 9

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/SessionSplits.kt` (entity, DAO, store)
- Create: `app/src/main/java/com/metaself/app/data/day/SessionSplitMigration.kt`
- Modify: `MetaSelfDatabase.kt` (entity, `sessionSplitDao()`, version 9), `di/DataModule.kt`
- Create: `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/9.json` (exported by the build)
- Create: `tools/check-migration-8-9.py`
- Test: `MigrationTest` (8 → 9, CI only), `RoomSessionSplitsTest` (the store over a fake DAO)

```kotlin
@Entity(tableName = "session_splits", primaryKeys = ["firstWorkoutId", "secondWorkoutId"])
data class SessionSplitEntity(val firstWorkoutId: Long, val secondWorkoutId: Long)
```
No foreign key: a typed witness deleted and put back by Undo comes back under its own id and is still
split; a split naming a workout that is gone groups nothing.

- [ ] Step 1: failing `RoomSessionSplitsTest` (split writes lead-vs-each-other pairs; a session recorded
  once writes nothing) and the 8 → 9 `MigrationTest` case.
- [ ] Step 2: run → FAIL. Step 3: entity + DAO + store + database version 9; build once to export
  `9.json`; copy its `createSql` verbatim into `MIGRATION_8_9`; register it in `DataModule`.
- [ ] Step 4: `python3 tools/check-migration-8-9.py` → OK; the store test → PASS (MigrationTest skips
  here, runs in CI).
- [ ] Step 5: commit `feat: session splits are stored, Room version 9 (D92)`.

### Task 5: Combined where the record is read

**Files:** `MovementRecord.kt`, `TypedWorkouts.kt`, `WorkoutFiles.kt`, `TrainerStore.kt`,
`FakeMovementRecord.kt`, `MovementRecordTest.kt`, `RoomTypedWorkoutsTest.kt`,
`RoomTrainerStoreWritesTest.kt`, `DataModule.kt` if a constructor changes.

- [ ] Step 1: failing tests: `RoomMovementRecord.observeWorkouts` over two overlapping rows gives one
  session, and a split row keeps them two, re-read when the splits change; the own-distance query is
  asked only when there is more than one row; `sessionOf(id)` of a non-lead witness is the combined
  session. `RoomTypedWorkouts.observe` leaves out a typed workout that is a witness of a synced session.
- [ ] Step 2: run → FAIL. Step 3: implement:
  - `RoomMovementRecord(days, workouts, walks, splits: SessionSplitDao)`; `observeWorkouts` = combine of
    rows, uncounted, splits → `SessionWitnesses.combine`; `sessionOf(id)` reads the row's day ±1.
  - `RoomTypedWorkouts.observe` = `record.observeWorkouts(from, to)` filtered to `source == TYPED` (the
    lead's), so D77 counts combined sessions.
  - `RoomWorkoutFileStore.on(days)` = the combined sessions on those days.
  - `RoomTrainerStore.workout(id)` = `record.sessionOf(id)`.
- [ ] Step 4: run → PASS. Step 5: commit `feat: every reader of the record sees combined sessions (D92)`.

### Task 6: What Movement shows, and "These are two sessions"

**Files:** `MovementWeekWording.kt`, `MovementViewModel.kt`, `MovementScreen.kt`, `MetaSelfNavHost.kt`,
`strings.xml`; tests `MovementWordingTest`/`MovementWeekWordingTest`, `MovementViewModelTest`,
`MovementScreenRenderTest`.

- [ ] Step 1: failing tests: a combined session's line says its distance with its source and, when
  they disagree, "another app said 3.1 km" (invented); "also recorded by 2 more" under it; the view
  model's `split(session)` writes the splits and logs a failure (D8); the reviews map carries a
  witness's review under the session's id; the render shows the button and the line.
- [ ] Step 2: run → FAIL. Step 3: implement; the review button opens `review.workoutId` when the review
  is a witness's; a typed-led session opens its sheet on `asStored`.
- [ ] Step 4: run → PASS (and `InlineComposableReturnGuardTest`). Step 5: commit
  `feat: Movement shows one line per session, and splits one in two (D92)`.

### Task 7: The backup carries the splits, format 7

**Files:** `Backup.kt`, `data/backup/BackupSplits.kt` (**new**, pure), `BackupRepository.kt`, and the
three tests that build a `BackupRepository` (`BackupRoundTripTest`, `BackupRestoreOrderTest`,
`SettingsViewModelTest`); `BackupCodecTest`; `BackupSplitsTest` (**new**).

- [ ] Step 1: failing tests: `BackupSplits.toFile` writes each split as the two workouts' positions in
  the file (from 1) and drops one whose workout is gone; `rows` maps positions to the ids the restore
  gives (after the file's own duplicates are dropped) and drops a split naming a dropped or missing
  workout; the format is 7; a version 6 file reads with no splits; a restore empties and refills the
  table inside the transaction.
- [ ] Step 2: run → FAIL. Step 3: implement. Step 4: run → PASS. Step 5: commit
  `feat: the backup carries session splits, format 7 (D92)`.

### Task 8: 0.66.0 and the full checks

- [ ] `versionCode = 122`, `versionName = "0.66.0"`.
- [ ] `free -m`; `:app:testDebugUnitTest` (only the ten Room classes may skip); `:app:lintDebug`;
  `:app:assembleDebug` — each separately, each redirected, each exit code checked.
- [ ] Commit `0.66.0: one session, several witnesses (D92)`.

---

## Design questions settled here

1. **Which workouts may be witnesses.** Visible (not hidden) and counted: a walk from an app whose walks
   are switched off (D81) is counted nowhere, so it witnesses nothing either and stays on its own line.
2. **Transitive grouping with a split.** Pairs are joined in (lower id, higher id) order; a join that
   would put a split pair into one session is skipped. So a third workout overlapping both halves of a
   split joins whichever it meets first and never reunites them. Deterministic, and never undoes a split.
3. **"These are two sessions" with three or more witnesses** splits the lead from each other witness,
   as the spec says. Two non-lead witnesses that overlap each other stay one session between them — the
   button is on that session too if the owner wants it apart.
4. **A split can be undone** (decided after review). Right after "These are two sessions" the screen
   offers Undo, in the line pinned under the title bar that the typed-workout delete already uses on
   this screen — the app has no snackbar, and the bottom edge is "Log a workout"'s. Undo removes exactly
   the pairs just written. Later, a session parted from a partner it still overlaps shows **Put back
   together**, which removes those splits; a split whose workouts no longer overlap offers nothing.
5. **A split, Undo or Put back together that fails** is logged (D8) and the sessions stay as they were;
   no separate error line was added.
6. **Kind "other"** includes a kind this version does not know (UNRECOGNISED): neither says what was
   done. The session's title comes with the kind it took, so a named "Running" does not sit on a walk.
7. **Distance disagreement** is measured against the larger of the two figures (symmetric); exactly 15 %
   is not shown; with several disagreeing witnesses the widest gap is shown, one line only.
8. **"Typed by the owner" distance** is a typed workout's own distance (stored with no source) or one
   marked TYPED over a file's. On a combined session it is marked TYPED. On a session a band or a file
   leads it says "(you typed)" (D4), beside any disagreeing distance too; on a typed session it reads
   as a typed distance does today.
8a. **Pace** is worked from the distance and the minutes of the same witness: a distance lent by another
   witness is paced over that witness's minutes (`Workout.distanceMinutes`), never the lead's.
9. **The D77 reading.** It counts typed-led sessions (typed or added from a file, with no synced
   witness), with the session's calories. A typed workout that is a witness of a synced session is the
   band's session now, and is no longer a reading of its own: its time is already in the band's day.
10. **File fill and "Is it one of these?"** see combined sessions: one choice per session, named by
    its lead, and a fill goes to the lead's row — the session's identity (spec: a file fill attaches to
    the lead). What the lead already has is never replaced (D82 unchanged).
11. **A review on a non-lead witness.** It is shown and sent as the session's while the session is
    combined (lead's first). Its button opens that witness's own review. After a split it stays with
    its own workout — which is then a session of its own — and the lead has none.
12. **The review screen's session** is the combined session containing the reviewed workout, so the
    figures shown and sent to the trainer are the combined ones.
13. **Hide.** No Hide button exists yet (planned in the activity module's corrections phase). The rule
    is written as `SessionWitnesses.toHide(session)` — every synced witness, no typed one — for that
    phase to call. Nothing on screen changes here.
14. **Not combined:** the band report (it counts what arrived, per app, on purpose); the day screen's
    session names, read straight from Health Connect and never counted; "Earlier sessions" on the
    trainer screen, which lists each review beside its own workout.
14a. **The stored daily summary's workout count and minutes** (`health_days`) follow combined sessions
    (decided after review): they are in the backup, so they must not count one session twice. They
    are worked out with the splits, and a split, Undo or Put back together summarises its days again in
    the same transaction. They are not in the Drive archive, which holds raw readings only.
15. **Backup positions.** A split is written as the two workouts' positions in the file's `workouts`
    list, counted from 1, as a person reading the file would count them; the restore gives the workouts
    ids 1…n in order, so positions become ids after the file's own duplicates are dropped.
