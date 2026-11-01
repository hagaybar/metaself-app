# The app measures itself (D152, D153) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 0.87.0 — a script that measures every DAO query against a year-sized invented store and the fixes
it finds (D153), and a temporary, off-by-default switch that records timings and counts on the phone into a
private file the owner can download (D152).

**Architecture:** D153 is `tools/measure-queries.py` (python3 -I, sqlite3, schema from `app/schemas`), plus
one Room migration (15 → 16) for the index findings and one query change for the N+1-shaped finding. D152 is
a `MeasureRecorder` (volatile switch, in-memory queue, one daemon thread writing JSON lines to
`filesDir/measure/`, 5 MB in two rotating halves), fed by a delegating `SupportSQLiteOpenHelper.Factory`
(statements and transactions), the copy (`HealthRecordSync`), three view models and the day screen; a
`MeasureSwitch` over the existing preferences DataStore turns it on for 72 hours; the Recent problems page
gets the switch and a Download diagnostics button (Storage Access Framework).

**Tech Stack:** Kotlin, Room 2.6.1, androidx.sqlite, Hilt, DataStore, Compose; Python 3 sqlite3. JUnit 5 +
Truth for pure code; JUnit 4 + Robolectric only for the migration test (CI) and Room-backed tests.

The spec is `docs/superpowers/specs/2026-10-14-the-app-measures-itself-design.md` and is authoritative.

---

## File map

| File | Responsibility |
| --- | --- |
| `tools/measure-queries.py` (new) | D153: build the year-sized store, run every `@Query` + Room's `@Relation` child reads, print plan and time, exit 1 on an unaccepted scan of a growing table or a query over 50 ms |
| `app/src/main/java/com/metaself/app/data/day/RecordIdIndexMigration.kt` (new) | `MIGRATION_15_16`: the index statements `16.json` adds |
| `app/src/main/java/com/metaself/app/data/health/HealthEntities.kt` | the new `@Index`es on `workouts` and `sleep_sessions` |
| `app/schemas/.../16.json` (generated) | exported schema 16 |
| `tools/check-migration-15-16.py` (new) | migration SQL against sqlite3, every row kept, plans before/after |
| `app/src/test/java/com/metaself/app/data/MigrationTest.kt` | 15 → 16 under Room's validation (CI) |
| `WorkoutDao.kt`, `MovementRecord.kt`, `SessionWitnesses.kt` | own-distance asked only for synced workouts that overlap another |
| `data/diagnostics/measure/MeasureRecorder.kt` (new) | switch, queue, writer thread, event API |
| `data/diagnostics/measure/MeasureFile.kt` (new) | two rotating JSON-lines files, 5 MB together |
| `data/diagnostics/measure/MeasureJson.kt` (new) | JSON line encoding; SQL text scrubbing |
| `data/diagnostics/measure/MeasuringOpenHelperFactory.kt` (new) | delegating factory/helper/database/statement |
| `data/diagnostics/measure/MeasureSwitch.kt` (new) | DataStore key, 72 h auto-off, restore on open |
| `data/diagnostics/measure/Diagnostics.kt` (new) | header assembly (pure), download (row counts, file size, the lines) |
| `di/DataModule.kt` | `openHelperFactory(MeasuringOpenHelperFactory(...))`, `MIGRATION_15_16` |
| `data/health/HealthRecordSync.kt` | copy phases per kind/page |
| `ui/screen/day/DayViewModel.kt`, `repeat/RepeatViewModel.kt`, `trainer/TrainerViewModel.kt` | first data on screen; tap → written |
| `MainActivity.kt` | restore the switch on open |
| `ui/screen/settings/ProblemsSettingsPage.kt`, `SettingsViewModel.kt`, `SettingsUiState.kt`, `ui/nav/SettingsDestination.kt`, `res/values/strings.xml` | the switch and the download |
| `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | exclude `measure/` |
| `privacy.html` | one line: the diagnostics file leaves only when downloaded |
| `app/build.gradle.kts`, `ui/about/ChangeLog.kt` | 0.87.0 |

## Task 1: D153 script (done first, it decides Tasks 2–3)

- [ ] Write `tools/measure-queries.py`: sizes stated in its docstring (invented): HEART_RATE a sample a
  minute in hourly records, STEPS/DISTANCE/ACTIVE_KCAL/TOTAL_KCAL per 15 min, smaller kinds, a second app's
  steps; ~690k readings; 261 workouts; 366 nights with 28 stages; a year of food (4 meals × 3 items a day);
  trainer tables. SQL extracted from the Kotlin `@Query` strings (constants resolved), Room's four
  `@Relation` child reads checked against generated code when present.
- [ ] Run `python3 -I tools/measure-queries.py`; record every flagged query.
- [ ] Read the callers of each flagged query (and audit hot paths for N+1 / whole-table reads).
- [ ] Commit the script: `tools: measure every DAO query against a year-sized made-up store (D153)`.

## Task 2: per-deleted-id and window lookups stop scanning (Room 15 → 16)

Finding: `SleepDao.daysOf(recordId)`, `deleteByRecordId`, `WorkoutDao.daysOfSynced(originId)`,
`deleteSynced` scan their tables once per deleted id inside the copy's write transaction;
`SleepDao.daysBetween`, `deleteBetween`, `WorkoutDao.visibleSyncedBetween` scan once per window re-read.

- [ ] Add `Index("recordId")`, `Index("startMillis")` to `SleepSessionEntity`; `Index("originId")`,
  `Index("startedAtMillis")` to `WorkoutEntity`; version 16; build to export `16.json`.
- [ ] `MIGRATION_15_16` with `16.json`'s four `CREATE INDEX` statements verbatim; register in `DataModule`.
- [ ] `tools/check-migration-15-16.py` (pattern of 14-15): statements == indexes 16.json adds; every row kept;
  the seven plans go from `SCAN` to `SEARCH ... USING INDEX`. Run it.
- [ ] `MigrationTest`: 15 → 16 keeps a row in each table and has the four indexes (CI).
- [ ] Re-run the script: those seven no longer flagged. Commit with before/after plans and timings.

## Task 3: own-distance asked only where it decides something

Finding: `WorkoutDao.idsWithOwnDistance(from, to)` (from `RoomMovementRecord.observeWorkouts`, a Flow
re-run on every `workouts` write) probes 3 days of DISTANCE rows per synced workout in the range — Year and
every trainer request cover ~13 months. `ownDistance` is read only in `SessionWitnesses.session(group)` for a
synced witness with a distance in a group of two or more.

- [ ] Test (JUnit 5): `SessionWitnesses.ownDistanceDecides(workouts)` returns exactly the ids of visible,
  counted, synced workouts with a distance that overlap another visible counted workout; empty for one
  workout or none overlapping.
- [ ] Implement it; add `WorkoutDao.idsWithOwnDistanceAmong(ids)` (same EXISTS, `w.id IN (:ids)`);
  `observeWorkouts` asks only for those ids (none → no query). Room test of the new query (CI, skips here).
- [ ] Script measures the new query; commit with before/after.

## Task 4: the recorder (pure, test-first)

- [ ] Tests (JUnit 5, temp dir, fake clock, direct executor where needed): off → `record` builds nothing,
  no file, no thread; on → lines appear only after the writer runs, on the writer thread; cap: halves
  rotate, total ≤ 5 MB, oldest dropped; auto-off when the clock passes the end; SQL scrubbing keeps
  placeholders and CONSTANT literals, drops other literals.
- [ ] Implement `MeasureRecorder`, `MeasureFile`, `MeasureJson`.

## Task 5: SQL timing

- [ ] Test (JUnit 5, fake `SupportSQLiteDatabase`/statement via simple delegating fakes): a statement bound
  to a sentinel string records its SQL with `?` and the sentinel never reaches the file; a transaction
  records wait, held, and the statements inside; off → the delegate's own cursor/statement path, nothing
  recorded.
- [ ] Implement `MeasuringOpenHelperFactory`; wire in `DataModule.provideDatabase`.

## Task 6: switch, download, settings page, copy phases, screens

- [ ] Test `Diagnostics.header(...)` (pure): app version, model, Android version, hours measured, row counts,
  database bytes, as one JSON line.
- [ ] `MeasureSwitch` over DataStore (`measure_started_at_millis`); restore on open in `MainActivity`.
- [ ] Problems page: switch, plain explanation, Download diagnostics (`CreateDocument("application/jsonl")`,
  default `metaself-diagnostics-<date>.jsonl`).
- [ ] `HealthRecordSync`: per kind and page — read, stored, deleted, HC read ms, store ms; per summarise run.
- [ ] Day / Repeat / Trainer first data; every Log tap → written; process start → day drawn.
- [ ] Backup exclusions; `privacy.html` line.

## Task 7: version, verification, PR

- [ ] versionCode 145, versionName 0.87.0, ChangeLog entry.
- [ ] `~/bin/gradlew-safe :app:testDebugUnitTest` (only the ten Room classes skip), `:app:lintDebug`,
  `~/bin/ms-release`; push; `gh pr create --base main`.
