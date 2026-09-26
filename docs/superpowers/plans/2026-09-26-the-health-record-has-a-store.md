# The health record has a store — Implementation Plan (health record, phase 1)

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans, task by task. Steps use checkbox syntax for tracking.

**Goal:** schema version 6 — every table the health record needs (readings, workouts, sleep with
stages, the daily summary, corrections, and the sync/archive bookkeeping), their DAOs, a hand-written
migration 5 → 6 checked locally in Python and in CI by `MigrationTest`, and backup format 3 carrying
the structured record. Nothing is copied from Health Connect yet (phase 2) and nothing new is drawn;
on a phone with no health data, every sentence the owner reads is unchanged.

**Architecture:** entities and DAOs live in a new package `data/health/`. The migration lives in
`data/day/` beside `MIGRATION_4_5`, where the database and its migrations are. The DAOs are
primitives only — the transactions that combine them (replace a record's rows, insert a night with
its stages) belong to phase 2's store class and to `BackupRepository`. The backup gains four blocks,
written verbatim, and restore REPLACES them inside the one existing transaction. Raw readings are
**not** in the daily file (D71: they go to Drive by month, phase 3).

**Decision:** the owner's, 2026-09-26 — D65 to D72 in
`docs/superpowers/specs/2026-09-26-health-record-design.md`; §2 is this phase. The `workouts` table
is the one in `2026-09-25-physical-activity-module-design.md` §3.2, extended by §2.2 of the new spec.

**Tech stack:** Kotlin 1.9.22, Room 2.6.1 over KSP, kotlinx.serialization, JUnit 5 + Truth (pure),
JUnit 4 + Robolectric (database, CI only). No new dependencies.

**Red lines (stop and report if crossed):**

- **Not one existing row is touched by the migration.** It creates tables and indices and does
  nothing else. The Python check asserts it.
- **The migration's SQL is copied from the generated `6.json`, never retyped from this plan.** This
  plan's SQL is what Room 2.6.1 is expected to generate; where it differs from the file, the file
  wins, and the difference goes in the report.
- **No `fallbackToDestructiveMigration`,** anywhere.
- **A file from a later version is still refused whole.** Every version-1 and version-2 file still
  restores.
- **Nothing the owner reads changes on a phone with no workouts and no health days.** Every existing
  `BackupWordingTest` assertion passes unchanged.
- **No Health Connect change, no manifest change, no permission, no screen.** Phase 2 onwards.
- **No `@Upsert` for a record read from Health Connect.** Room's upsert updates *by primary key*
  after a unique-index conflict; a freshly read row has id 0, so the update would match nothing and
  the re-read would be lost without a word.
- **Never `git add -A`.** Stage by path. Never bare `./gradlew`.
- **Anonymisation:** every fixture figure is invented, round, and says so. No real heart rate, step
  count, sleep length, distance, resting rate or frequency anywhere, test names included. Health data
  is the most personal thing this repository has been near; read every added line before the merge.

---

## The shared box — read before any build

Another project on this machine runs Gradle builds too. `~/bin/gradlew-safe` and `~/bin/ms-release`
take one shared lock, so builds queue — but a finished build's idle daemon stays resident for up to
ten minutes. **Before every Gradle command, run `free -m`;** if "available" is under about 4000 MB
(about 6000 MB before `ms-release`), wait a few minutes and check again rather than start. Never run
`gradlew --stop` (it can stop the other project's daemon). Never start a second build of your own
while one is running, and never from two subagents at once.

Test command shape (through the lock; never pipe a build whose result is reported):

```
export ANDROID_HOME=/home/ubuntu/android-sdk
free -m
~/bin/gradlew-safe :app:testDebugUnitTest --tests "<pattern>" > /tmp/ms-health1.log 2>&1; echo "exit $?"
```

Read `/tmp/ms-health1.log` for failures. A SQLite test class **skips** on this box (aarch64); say
"skipped", never "passed", in every report.

---

## File structure

| File | Responsibility |
|---|---|
| Modify `docs/superpowers/specs/2026-09-26-health-record-design.md` | §2.4: sleep and workout figures share one source per group |
| Create `app/src/main/java/com/metaself/app/data/health/HealthEntities.kt` | all eight entities, `WeekOfRunning` |
| Create `app/src/main/java/com/metaself/app/data/health/WorkoutDao.kt` | workouts |
| Create `app/src/main/java/com/metaself/app/data/health/HealthReadingDao.kt` | raw readings |
| Create `app/src/main/java/com/metaself/app/data/health/SleepDao.kt` | nights and stages |
| Create `app/src/main/java/com/metaself/app/data/health/HealthDayDao.kt` | daily summaries |
| Create `app/src/main/java/com/metaself/app/data/health/MovementCorrectionDao.kt` | corrections |
| Create `app/src/main/java/com/metaself/app/data/health/HealthBookkeepingDao.kt` | sync tokens, archive months |
| Create `app/src/main/java/com/metaself/app/data/day/HealthRecordMigration.kt` | `MIGRATION_5_6` |
| Modify `app/src/main/java/com/metaself/app/data/day/MetaSelfDatabase.kt` | entities, version 6, DAO accessors |
| Create (generated) `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/6.json` | committed |
| Modify `app/src/main/java/com/metaself/app/di/DataModule.kt` | DAO providers, the migration |
| Create `tools/check-migration-5-6.py` | the local check |
| Modify `app/src/main/java/com/metaself/app/domain/backup/Backup.kt` | version 3, four blocks |
| Modify `app/src/main/java/com/metaself/app/data/backup/BackupRepository.kt` | export, restore, count |
| Modify `app/src/main/java/com/metaself/app/ui/settings/BackupWording.kt` | name workouts and health days when present |
| Modify `app/src/main/java/com/metaself/app/ui/screen/settings/SettingsViewModel.kt` (~572, ~606) | pass the counts |
| Modify `app/build.gradle.kts:19,30` | 108 / 0.54.0 |
| Modify `CLAUDE.md` | nine classes skip locally |
| Test `app/src/test/java/com/metaself/app/data/health/HealthRecordDaoTest.kt` | new, CI only |
| Test `app/src/test/java/com/metaself/app/data/MigrationTest.kt` | 5 → 6, CI only |
| Test `app/src/test/java/com/metaself/app/domain/backup/BackupCodecTest.kt` | new blocks, version-2 file |
| Test `app/src/test/java/com/metaself/app/data/backup/BackupRestoreOrderTest.kt` | order, anywhere |
| Test `app/src/test/java/com/metaself/app/data/backup/BackupRoundTripTest.kt` | CI only |
| Test `app/src/test/java/com/metaself/app/domain/backup/BackupWordingTest.kt` | wording |
| Test `app/src/test/java/com/metaself/app/ui/screen/settings/SettingsViewModelTest.kt` | constructor, proxy |

Every DAO is tested in one class, `HealthRecordDaoTest`, so the list of classes that skip locally
grows by exactly one.

Delete `docs/superpowers/plans/2026-09-26-workouts-are-kept-on-the-phone.md` in Task 1: it was never
committed and this plan replaces it.

---

### Task 1: Record the as-built choices, clear the superseded plan

**Files:**
- Modify: `docs/superpowers/specs/2026-09-26-health-record-design.md`
- Delete (untracked): `docs/superpowers/plans/2026-09-26-workouts-are-kept-on-the-phone.md`

- [ ] **Step 0: Be on the branch.** `git branch --show-current` must print
  `the-app-keeps-a-health-record` (it holds the spec commit). If not: `git checkout the-app-keeps-a-health-record`.

- [ ] **Step 1: In the spec, §2.4**, replace the sentence
  `One row per day. Every figure is nullable (no data is not zero) and has a source column beside it holding \`TOTAL\`, \`READ\`, \`COMPUTED\` or \`CORRECTED\` (D69).`
  with:

```markdown
One row per day. Every figure is nullable (no data is not zero) and has a source column beside it
holding `TOTAL`, `READ`, `COMPUTED` or `CORRECTED` (D69) — except the five sleep figures, which share
`sleepSource`, and the two workout figures, which share `workoutSource`, because each group is
computed together from one source and cannot differ within it.
```

- [ ] **Step 2: Delete the superseded plan:** `rm docs/superpowers/plans/2026-09-26-workouts-are-kept-on-the-phone.md`
  (untracked; `git status` must no longer list it).

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-09-26-health-record-design.md \
  docs/superpowers/plans/2026-09-26-the-health-record-has-a-store.md
git commit -m "docs: the health record's phase 1 plan; sleep and workout figures share a source (D69)"
```

---

### Task 2: The entities, the DAOs, schema 6 and its generated file

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/health/HealthEntities.kt`
- Create: the six DAO files listed above
- Modify: `app/src/main/java/com/metaself/app/data/day/MetaSelfDatabase.kt`
- Modify: `app/src/main/java/com/metaself/app/di/DataModule.kt`
- Create (generated): `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/6.json`
- Test: `app/src/test/java/com/metaself/app/data/health/HealthRecordDaoTest.kt`

**Install and release nothing built at the end of this task.** Version 6 exists here without its
migration (Task 3); a phone upgraded from this build would stop at start-up.

- [ ] **Step 1: Write the failing DAO test** (JUnit 4 — Robolectric's runner is JUnit 4; skips on
  aarch64, runs in CI):

```kotlin
package com.metaself.app.data.health

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.assumeSqliteRuntime
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every DAO of the health record, in one class so that one class skips locally.
 *
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4. Skipped on aarch64, run in CI. Days sit
 * around [TEST_EPOCH_DAY] (2026-09-03, a Thursday). Every figure is invented and round.
 */
@RunWith(RobolectricTestRunner::class)
class HealthRecordDaoTest {

    private lateinit var db: MetaSelfDatabase

    @Before
    fun setUp() {
        assumeSqliteRuntime()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MetaSelfDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        if (this::db.isInitialized) db.close()
    }

    // --- Workouts ----------------------------------------------------------------------------------

    @Test
    fun `a day's workouts come back in the order they started`() = runTest {
        val dao = db.workoutDao()
        dao.insert(typed(startedAt = 2_000))
        dao.insert(typed(startedAt = 1_000))
        dao.insert(typed(day = TEST_EPOCH_DAY + 1, startedAt = 500))

        val day = dao.observeBetween(TEST_EPOCH_DAY, TEST_EPOCH_DAY).first()

        assertThat(day.map { it.startedAtMillis }).containsExactly(1_000L, 2_000L).inOrder()
    }

    @Test
    fun `a synced session is found again by where it came from`() = runTest {
        val dao = db.workoutDao()
        val id = dao.insert(synced("abc-1"))

        assertThat(dao.synced(ORIGIN, "abc-1")?.id).isEqualTo(id)
        assertThat(dao.synced(ORIGIN, "abc-2")).isNull()
    }

    @Test
    fun `the same session cannot be stored twice`() = runTest {
        val dao = db.workoutDao()
        dao.insert(synced("abc-1"))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(synced("abc-1")) }
        }
    }

    /** SQLite lets any number of rows share a null in a unique index. */
    @Test
    fun `any number of typed workouts can be stored, having no origin`() = runTest {
        db.workoutDao().insert(typed())
        db.workoutDao().insert(typed())

        assertThat(db.workoutDao().all()).hasSize(2)
    }

    @Test
    fun `an update keeps the row's id and replaces its figures`() = runTest {
        val dao = db.workoutDao()
        val id = dao.insert(synced("abc-1", distanceM = 5_000))

        dao.update(dao.synced(ORIGIN, "abc-1")!!.copy(distanceM = 6_000, avgHeartRate = 140))

        val row = dao.all().single()
        assertThat(row.id).isEqualTo(id)
        assertThat(row.distanceM).isEqualTo(6_000)
        assertThat(row.avgHeartRate).isEqualTo(140)
    }

    @Test
    fun `the synced ids in a window are listed, and typed rows are not`() = runTest {
        val dao = db.workoutDao()
        dao.insert(synced("abc-1"))
        dao.insert(synced("abc-2", day = TEST_EPOCH_DAY - 40))
        dao.insert(typed())

        assertThat(dao.syncedIdsBetween(TEST_EPOCH_DAY - 29, TEST_EPOCH_DAY)).containsExactly("abc-1")
    }

    /** A session deleted in the band's app disappears here; one hidden on purpose stays hidden. */
    @Test
    fun `sessions no longer read are dropped, but hidden and typed ones are kept`() = runTest {
        val dao = db.workoutDao()
        dao.insert(synced("kept"))
        dao.insert(synced("gone"))
        dao.insert(synced("hidden").copy(hidden = true))
        dao.insert(typed())

        dao.dropSyncedNotIn(TEST_EPOCH_DAY - 29, TEST_EPOCH_DAY, keep = listOf("kept"))

        assertThat(dao.all().map { it.originId }).containsExactly("kept", "hidden", null)
    }

    @Test
    fun `when nothing is read any more, every visible synced session in the window goes`() = runTest {
        val dao = db.workoutDao()
        dao.insert(synced("gone"))
        dao.insert(synced("older", day = TEST_EPOCH_DAY - 40))

        dao.dropSyncedNotIn(TEST_EPOCH_DAY - 29, TEST_EPOCH_DAY, keep = emptyList())

        assertThat(dao.all().map { it.originId }).containsExactly("older")
    }

    @Test
    fun `hiding a session keeps the row, and only a typed workout can be deleted`() = runTest {
        val dao = db.workoutDao()
        val band = dao.insert(synced("abc-1"))
        val mine = dao.insert(typed())

        dao.setHidden(band, true)
        dao.deleteTyped(band)
        dao.deleteTyped(mine)

        assertThat(dao.all().single().hidden).isTrue()
    }

    /**
     * Sunday 2026-08-30 closes one week; Monday 31 August and Tuesday 1 September open the next,
     * across the month boundary.
     */
    @Test
    fun `running distance is grouped into weeks that start on Monday`() = runTest {
        val dao = db.workoutDao()
        dao.insert(run(day = TEST_EPOCH_DAY - 4, metres = 4_000)) // Sunday 30 August
        dao.insert(run(day = TEST_EPOCH_DAY - 3, metres = 5_000)) // Monday 31 August
        dao.insert(run(day = TEST_EPOCH_DAY - 2, metres = 6_000)) // Tuesday 1 September

        val weeks = dao.observeWeeklyRunning(weeks = 4).first()

        assertThat(weeks).containsExactly(
            WeekOfRunning(week = (TEST_EPOCH_DAY + 3) / 7, metres = 11_000, runs = 2),
            WeekOfRunning(week = (TEST_EPOCH_DAY + 3) / 7 - 1, metres = 4_000, runs = 1),
        ).inOrder()
    }

    @Test
    fun `a hidden run, a run without a distance and a walk add nothing to the week`() = runTest {
        val dao = db.workoutDao()
        dao.insert(run(metres = 5_000))
        dao.insert(run(metres = 5_000).copy(hidden = true))
        dao.insert(run(metres = 5_000).copy(distanceM = null))
        dao.insert(run(metres = 5_000).copy(kind = "WALK"))

        val week = dao.observeWeeklyRunning(weeks = 4).first().single()

        assertThat(week.metres).isEqualTo(5_000)
        assertThat(week.runs).isEqualTo(1)
    }

    // --- Readings ----------------------------------------------------------------------------------

    /** A heart-rate record is one record of many samples: one row each, under one record id. */
    @Test
    fun `a record's samples are stored as rows and removed together`() = runTest {
        val dao = db.healthReadingDao()
        dao.insertAll(listOf(beat("hr-1", 0, 60.0), beat("hr-1", 1, 62.0), beat("hr-2", 0, 70.0)))

        dao.deleteRecord(ORIGIN, "hr-1")

        assertThat(dao.onDay(TEST_EPOCH_DAY).map { it.recordId }).containsExactly("hr-2")
    }

    @Test
    fun `the same sample cannot be stored twice`() = runTest {
        val dao = db.healthReadingDao()
        dao.insertAll(listOf(beat("hr-1", 0, 60.0)))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertAll(listOf(beat("hr-1", 0, 61.0))) }
        }
    }

    @Test
    fun `a day's readings of one kind come back in time order`() = runTest {
        val dao = db.healthReadingDao()
        dao.insertAll(
            listOf(
                beat("hr-1", 1, 62.0, at = 2_000),
                beat("hr-1", 0, 60.0, at = 1_000),
                beat("hr-9", 0, 70.0, day = TEST_EPOCH_DAY + 1),
            ),
        )

        val day = dao.ofKindOnDay("HEART_RATE", TEST_EPOCH_DAY)

        assertThat(day.map { it.value }).containsExactly(60.0, 62.0).inOrder()
    }

    // --- Sleep -------------------------------------------------------------------------------------

    @Test
    fun `a night comes back with its stages, and deleting it takes them too`() = runTest {
        val dao = db.sleepDao()
        val id = dao.insertSession(night("s-1"))
        dao.insertStages(
            listOf(
                SleepStageEntity(sessionId = id, stage = "LIGHT", startMillis = 1_000, endMillis = 2_000),
                SleepStageEntity(sessionId = id, stage = "DEEP", startMillis = 2_000, endMillis = 3_000),
            ),
        )

        assertThat(dao.stagesOf(id).map { it.stage }).containsExactly("LIGHT", "DEEP").inOrder()

        dao.deleteRecord(ORIGIN, "s-1")

        assertThat(dao.allSessions()).isEmpty()
        assertThat(dao.allStages()).isEmpty()
    }

    @Test
    fun `the same night cannot be stored twice`() = runTest {
        db.sleepDao().insertSession(night("s-1"))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.sleepDao().insertSession(night("s-1")) }
        }
    }

    @Test
    fun `nights are found by the day he woke up`() = runTest {
        val dao = db.sleepDao()
        dao.insertSession(night("s-1"))
        dao.insertSession(night("s-2").copy(epochDay = TEST_EPOCH_DAY + 1))

        assertThat(dao.sessionsBetween(TEST_EPOCH_DAY, TEST_EPOCH_DAY).map { it.recordId })
            .containsExactly("s-1")
    }

    // --- Daily summary, corrections, bookkeeping --------------------------------------------------

    @Test
    fun `a day's summary is replaced whole when it is recomputed`() = runTest {
        val dao = db.healthDayDao()
        dao.put(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 1_000, steps = 9_000, stepsSource = "TOTAL"))
        dao.put(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 2_000, steps = 8_000, stepsSource = "CORRECTED"))

        val day = dao.day(TEST_EPOCH_DAY)!!
        assertThat(day.steps).isEqualTo(8_000)
        assertThat(day.stepsSource).isEqualTo("CORRECTED")
        assertThat(dao.observeBetween(TEST_EPOCH_DAY - 6, TEST_EPOCH_DAY).first()).hasSize(1)
    }

    /** No data is null, not zero. */
    @Test
    fun `a figure never read stays null`() = runTest {
        db.healthDayDao().put(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 1_000))

        val day = db.healthDayDao().day(TEST_EPOCH_DAY)!!
        assertThat(day.steps).isNull()
        assertThat(day.sleepMinutes).isNull()
    }

    @Test
    fun `a correction is one row per day, the later one replacing it`() = runTest {
        val dao = db.movementCorrectionDao()
        dao.insertAll(listOf(MovementCorrectionEntity(TEST_EPOCH_DAY, 9_000, null, 1_000, null)))
        dao.insertAll(listOf(MovementCorrectionEntity(TEST_EPOCH_DAY, 8_000, null, 2_000, null)))

        assertThat(dao.all().single().steps).isEqualTo(8_000)
    }

    @Test
    fun `where copying stands is kept per kind`() = runTest {
        val dao = db.healthBookkeepingDao()
        dao.putSync(HealthSyncEntity(kind = "STEPS", changesToken = "t-1", tokenAtMillis = 1_000))
        dao.putSync(HealthSyncEntity(kind = "STEPS", changesToken = "t-2", tokenAtMillis = 2_000))

        assertThat(dao.sync("STEPS")?.changesToken).isEqualTo("t-2")
        assertThat(dao.sync("SLEEP")).isNull()
    }

    @Test
    fun `a month is out of date until it is written after its last change`() = runTest {
        val dao = db.healthBookkeepingDao()
        dao.putMonth(ArchiveMonthEntity(month = "2026-08", changedAtMillis = 1_000, writtenAtMillis = 2_000))
        dao.putMonth(ArchiveMonthEntity(month = "2026-09", changedAtMillis = 3_000, writtenAtMillis = 2_000))
        dao.putMonth(ArchiveMonthEntity(month = "2026-10", changedAtMillis = 3_000, writtenAtMillis = null))

        assertThat(dao.monthsOutOfDate().map { it.month }).containsExactly("2026-09", "2026-10").inOrder()
    }

    @Test
    fun `deleting all empties every table a backup replaces`() = runTest {
        db.workoutDao().insertAll(listOf(typed(), synced("abc-1")))
        db.sleepDao().insertSession(night("s-1"))
        db.healthDayDao().insertAll(listOf(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 1)))
        db.movementCorrectionDao().insertAll(listOf(MovementCorrectionEntity(TEST_EPOCH_DAY, 1, null, 1, null)))

        db.workoutDao().deleteAll()
        db.sleepDao().deleteAll()
        db.healthDayDao().deleteAll()
        db.movementCorrectionDao().deleteAll()

        assertThat(db.workoutDao().all()).isEmpty()
        assertThat(db.sleepDao().allSessions()).isEmpty()
        assertThat(db.healthDayDao().all()).isEmpty()
        assertThat(db.movementCorrectionDao().all()).isEmpty()
    }

    // --- Fixtures ----------------------------------------------------------------------------------

    private fun typed(day: Long = TEST_EPOCH_DAY, startedAt: Long = 1_000) = WorkoutEntity(
        epochDay = day,
        startedAtMillis = startedAt,
        durationMinutes = 45,
        kind = "STRENGTH",
        title = "Strength",
        distanceM = null,
        energyKcal = 150,
        energySource = "MET_ESTIMATE",
        effort = "MODERATE",
        source = "TYPED",
        origin = null,
        originId = null,
        note = null,
    )

    private fun synced(id: String, day: Long = TEST_EPOCH_DAY, distanceM: Int? = null) =
        WorkoutEntity(
            epochDay = day,
            startedAtMillis = 1_000,
            durationMinutes = 30,
            kind = "RUN",
            title = "Running",
            distanceM = distanceM,
            energyKcal = null,
            energySource = "NONE",
            effort = null,
            source = "SYNCED",
            origin = ORIGIN,
            originId = id,
            note = null,
        )

    private fun run(day: Long = TEST_EPOCH_DAY, metres: Int) =
        typed(day = day).copy(kind = "RUN", title = "Running", distanceM = metres)

    private fun beat(
        record: String,
        index: Int,
        bpm: Double,
        at: Long = 1_000,
        day: Long = TEST_EPOCH_DAY,
    ) = HealthReadingEntity(
        kind = "HEART_RATE",
        startMillis = at,
        endMillis = null,
        value = bpm,
        unit = "bpm",
        origin = ORIGIN,
        recordId = record,
        sampleIndex = index,
        epochDay = day,
    )

    private fun night(record: String) = SleepSessionEntity(
        epochDay = TEST_EPOCH_DAY,
        startMillis = 1_000,
        endMillis = 3_000,
        origin = ORIGIN,
        recordId = record,
        title = null,
    )

    private companion object {
        const val ORIGIN = "com.example.band"
    }
}
```

- [ ] **Step 2: Write `HealthEntities.kt`:**

```kotlin
package com.metaself.app.data.health

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One simple reading as Health Connect held it (D68): a value at a moment, or over a span.
 *
 * A series record — heart rate — is one Health Connect record of many samples, stored as one row per
 * sample under the same [recordId], told apart by [sampleIndex]. `(origin, recordId, sampleIndex)`
 * is unique, so the same sample can never be stored twice. Two apps recording the same walk both
 * keep their rows: that is what was recorded. De-duplication is the daily summary's job (D69).
 *
 * @property kind STEPS, DISTANCE, ACTIVE_KCAL, TOTAL_KCAL, HEART_RATE, RESTING_HEART_RATE,
 *   HRV_RMSSD, OXYGEN_SATURATION, RESPIRATORY_RATE, WEIGHT, BODY_FAT — a name, never an ordinal.
 * @property endMillis null for an instant (a heart-rate sample, a weight); the span's end otherwise.
 * @property unit "count", "m", "kcal", "bpm", "ms", "%", "breaths/min", "kg".
 * @property epochDay the local day of [startMillis], stored so that a day is one indexed range.
 */
@Entity(
    tableName = "health_readings",
    indices = [
        Index(value = ["kind", "epochDay", "startMillis"]),
        Index(value = ["origin", "recordId", "sampleIndex"], unique = true),
    ],
)
data class HealthReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val startMillis: Long,
    val endMillis: Long?,
    val value: Double,
    val unit: String,
    val origin: String,
    val recordId: String,
    val sampleIndex: Int = 0,
    val epochDay: Long,
)

/**
 * One workout, whether the band recorded it or the owner typed it (D59, extended by D65).
 *
 * [origin] and [originId] are Health Connect's data origin and record id; together unique, so a
 * re-read finds the row rather than adding a second. Both null for a typed workout — SQLite lets any
 * number of rows share a null in a unique index.
 *
 * [energyKcal] carries where it came from in [energySource] (D4). The heart-rate figures are worked
 * out by this app from the readings inside the session (phase 2); [zoneSeconds] is five
 * comma-separated totals, zone 1 to 5, and [zoneMaxSource] says what maximum they were measured
 * against — ESTIMATED until the trainer offers an observed one (D70). [note] is where the trainer's
 * notebook will keep the owner's words.
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
    /** RUN, WALK, CYCLE, SWIM, STRENGTH, OTHER. */
    val kind: String,
    val title: String?,
    val distanceM: Int?,
    val energyKcal: Int?,
    /** BAND, MET_ESTIMATE, TYPED, NONE. */
    val energySource: String,
    /** EASY, MODERATE, HARD. A typed workout always has one; a synced one never. */
    val effort: String?,
    /** SYNCED or TYPED. */
    val source: String,
    val origin: String?,
    val originId: String?,
    /** Set when the owner says a session is not real; kept, so the next sync cannot bring it back. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    val note: String?,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val zoneSeconds: String? = null,
    val zoneMaxSource: String? = null,
)

/** One night (D68): it belongs to [epochDay], the day he woke up. */
@Entity(
    tableName = "sleep_sessions",
    indices = [
        Index("epochDay"),
        Index(value = ["origin", "recordId"], unique = true),
    ],
)
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val startMillis: Long,
    val endMillis: Long,
    val origin: String,
    val recordId: String,
    val title: String?,
)

/** A stretch of one night. Deleting the night deletes its stages. */
@Entity(
    tableName = "sleep_stages",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SleepStageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** AWAKE, LIGHT, DEEP, REM, SLEEPING, OUT_OF_BED, AWAKE_IN_BED, UNKNOWN. */
    val stage: String,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * One day of the health record, summarised (D69).
 *
 * Every figure is nullable: no data is not zero. Each figure's source is TOTAL (Health Connect's
 * de-duplicated aggregate), READ (one record), COMPUTED (worked out here) or CORRECTED (the owner's
 * figure, D12d). The five sleep figures share [sleepSource] and the two workout figures share
 * [workoutSource], because each group is computed together.
 */
@Entity(tableName = "health_days")
data class HealthDayEntity(
    @PrimaryKey val epochDay: Long,
    val computedAtMillis: Long,
    val steps: Int? = null,
    val stepsSource: String? = null,
    val distanceM: Int? = null,
    val distanceSource: String? = null,
    val activeKcal: Int? = null,
    val activeKcalSource: String? = null,
    val totalKcal: Int? = null,
    val totalKcalSource: String? = null,
    val restingHeartRate: Int? = null,
    val restingHeartRateSource: String? = null,
    val avgHeartRate: Int? = null,
    val avgHeartRateSource: String? = null,
    val hrvMs: Double? = null,
    val hrvSource: String? = null,
    val oxygenPct: Double? = null,
    val oxygenSource: String? = null,
    val respiratoryRate: Double? = null,
    val respiratoryRateSource: String? = null,
    val sleepMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val remMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val sleepSource: String? = null,
    val workoutCount: Int? = null,
    val workoutMinutes: Int? = null,
    val workoutSource: String? = null,
)

/**
 * The owner's own figure for a day's movement, replacing what Health Connect read (D12d).
 * One row per day; a null column leaves that reading alone. Never written back to Health Connect.
 */
@Entity(tableName = "movement_corrections")
data class MovementCorrectionEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Int?,
    val activeKcal: Int?,
    val setAtMillis: Long,
    val note: String?,
)

/**
 * Where copying one kind stands (D67). Belongs to this phone's Health Connect, so it is never
 * backed up: a restored phone re-reads its window.
 */
@Entity(tableName = "health_sync")
data class HealthSyncEntity(
    @PrimaryKey val kind: String,
    val changesToken: String? = null,
    val tokenAtMillis: Long? = null,
    /** How far back the first copy has reached; it runs newest first. */
    val catchUpCursorMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val catchUpDone: Boolean = false,
)

/** Which Drive month files are out of date (D71). Never backed up. `month` is "2026-09". */
@Entity(tableName = "archive_months")
data class ArchiveMonthEntity(
    @PrimaryKey val month: String,
    val changedAtMillis: Long,
    val writtenAtMillis: Long? = null,
)

/**
 * One Monday-to-Sunday week of running. [week] is `(epochDay + 3) / 7` — epoch day 0 was a
 * Thursday, so this whole number changes on Mondays.
 */
data class WeekOfRunning(val week: Long, val metres: Int, val runs: Int)
```

- [ ] **Step 3: Write the six DAOs.**

`WorkoutDao.kt`:

```kotlin
package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Workouts. **No `@Upsert`:** Room's upsert updates by primary key after a unique-index conflict, and
 * a session freshly read from Health Connect has id 0, so a re-read would be dropped without a word.
 * The sync finds the row with [synced] and then calls [insert] or [update].
 */
@Dao
interface WorkoutDao {

    @Query("SELECT * FROM workouts WHERE epochDay BETWEEN :from AND :to ORDER BY startedAtMillis")
    fun observeBetween(from: Long, to: Long): Flow<List<WorkoutEntity>>

    /**
     * Running distance per week, Monday-based, newest first. A hidden session is left out; a run with
     * no distance adds nothing rather than zeroing the week. A week with no runs has no row.
     */
    @Query(
        "SELECT (epochDay + 3) / 7 AS week, SUM(distanceM) AS metres, COUNT(*) AS runs " +
            "FROM workouts WHERE kind = 'RUN' AND hidden = 0 AND distanceM IS NOT NULL " +
            "GROUP BY week ORDER BY week DESC LIMIT :weeks",
    )
    fun observeWeeklyRunning(weeks: Int): Flow<List<WeekOfRunning>>

    @Query("SELECT * FROM workouts WHERE origin = :origin AND originId = :originId")
    suspend fun synced(origin: String, originId: String): WorkoutEntity?

    @Query(
        "SELECT originId FROM workouts WHERE source = 'SYNCED' AND originId IS NOT NULL " +
            "AND epochDay BETWEEN :from AND :to",
    )
    suspend fun syncedIdsBetween(from: Long, to: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    /**
     * Synced sessions in the window the last read did not return — deleted in the writing app. A
     * hidden one is kept: it was hidden on purpose, and deleting it would let the next read bring
     * it back.
     */
    @Query(
        "DELETE FROM workouts WHERE source = 'SYNCED' AND epochDay BETWEEN :from AND :to " +
            "AND originId NOT IN (:keep) AND hidden = 0",
    )
    suspend fun dropSyncedNotIn(from: Long, to: Long, keep: List<String>)

    @Query("UPDATE workouts SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    /** A synced session is never deleted by the owner, only hidden: its numbers are the band's. */
    @Query("DELETE FROM workouts WHERE id = :id AND source = 'TYPED'")
    suspend fun deleteTyped(id: Long)

    @Query("SELECT * FROM workouts ORDER BY epochDay, startedAtMillis, id")
    suspend fun all(): List<WorkoutEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(workouts: List<WorkoutEntity>)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()
}
```

`HealthReadingDao.kt`:

```kotlin
package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Raw readings. Primitives only: replacing a record's rows is a delete and an insert inside one
 * transaction, which the store (phase 2) owns.
 */
@Dao
interface HealthReadingDao {

    /** Refuses a sample already stored: `(origin, recordId, sampleIndex)` is unique. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(readings: List<HealthReadingEntity>)

    @Query("DELETE FROM health_readings WHERE origin = :origin AND recordId = :recordId")
    suspend fun deleteRecord(origin: String, recordId: String)

    @Query("SELECT * FROM health_readings WHERE epochDay = :epochDay ORDER BY kind, startMillis, sampleIndex")
    suspend fun onDay(epochDay: Long): List<HealthReadingEntity>

    @Query(
        "SELECT * FROM health_readings WHERE kind = :kind AND epochDay = :epochDay " +
            "ORDER BY startMillis, sampleIndex",
    )
    suspend fun ofKindOnDay(kind: String, epochDay: Long): List<HealthReadingEntity>
}
```

`SleepDao.kt`:

```kotlin
package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Nights and their stages. A night and its stages are written together inside a transaction the
 * caller owns; deleting a night deletes its stages (the foreign key cascades).
 */
@Dao
interface SleepDao {

    /** Refuses a night already stored: `(origin, recordId)` is unique. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: SleepSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStages(stages: List<SleepStageEntity>)

    @Query("SELECT * FROM sleep_sessions WHERE epochDay BETWEEN :from AND :to ORDER BY startMillis")
    suspend fun sessionsBetween(from: Long, to: Long): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_stages WHERE sessionId = :sessionId ORDER BY startMillis")
    suspend fun stagesOf(sessionId: Long): List<SleepStageEntity>

    @Query("DELETE FROM sleep_sessions WHERE origin = :origin AND recordId = :recordId")
    suspend fun deleteRecord(origin: String, recordId: String)

    @Query("SELECT * FROM sleep_sessions ORDER BY startMillis, id")
    suspend fun allSessions(): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_stages ORDER BY sessionId, startMillis, id")
    suspend fun allStages(): List<SleepStageEntity>

    @Query("DELETE FROM sleep_sessions")
    suspend fun deleteAll()
}
```

`HealthDayDao.kt`:

```kotlin
package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Daily summaries. A day is recomputed whole, so writing one replaces it (the day is the key). */
@Dao
interface HealthDayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(day: HealthDayEntity)

    @Query("SELECT * FROM health_days WHERE epochDay = :epochDay")
    suspend fun day(epochDay: Long): HealthDayEntity?

    @Query("SELECT * FROM health_days WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun observeBetween(from: Long, to: Long): Flow<List<HealthDayEntity>>

    @Query("SELECT * FROM health_days ORDER BY epochDay")
    suspend fun all(): List<HealthDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<HealthDayEntity>)

    @Query("DELETE FROM health_days")
    suspend fun deleteAll()
}
```

`MovementCorrectionDao.kt`:

```kotlin
package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** The backup's three calls. Reading a correction for a day arrives with the corrections phase. */
@Dao
interface MovementCorrectionDao {

    @Query("SELECT * FROM movement_corrections ORDER BY epochDay")
    suspend fun all(): List<MovementCorrectionEntity>

    /** One per day: a second for the same day replaces the first. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(corrections: List<MovementCorrectionEntity>)

    @Query("DELETE FROM movement_corrections")
    suspend fun deleteAll()
}
```

`HealthBookkeepingDao.kt`:

```kotlin
package com.metaself.app.data.health

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Where copying stands, and which Drive month files are out of date. Never backed up. */
@Dao
interface HealthBookkeepingDao {

    @Query("SELECT * FROM health_sync WHERE kind = :kind")
    suspend fun sync(kind: String): HealthSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSync(sync: HealthSyncEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMonth(month: ArchiveMonthEntity)

    /** Never written, or changed since it was. Oldest first. */
    @Query(
        "SELECT * FROM archive_months WHERE writtenAtMillis IS NULL " +
            "OR writtenAtMillis < changedAtMillis ORDER BY month",
    )
    suspend fun monthsOutOfDate(): List<ArchiveMonthEntity>
}
```

- [ ] **Step 4: Register them in `MetaSelfDatabase.kt`.** Add imports for the eight entities and six
  DAOs from `com.metaself.app.data.health`; append to `entities`:

```kotlin
        HealthReadingEntity::class,
        WorkoutEntity::class,
        SleepSessionEntity::class,
        SleepStageEntity::class,
        HealthDayEntity::class,
        MovementCorrectionEntity::class,
        HealthSyncEntity::class,
        ArchiveMonthEntity::class,
```

change `version = 5` to `version = 6`, and add after `abstract fun savedMealDao(): SavedMealDao`:

```kotlin
    abstract fun workoutDao(): WorkoutDao

    abstract fun healthReadingDao(): HealthReadingDao

    abstract fun sleepDao(): SleepDao

    abstract fun healthDayDao(): HealthDayDao

    abstract fun movementCorrectionDao(): MovementCorrectionDao

    abstract fun healthBookkeepingDao(): HealthBookkeepingDao
```

- [ ] **Step 5: Provide them in `DataModule.kt`**, beside `provideWeightDao`, with the six imports:

```kotlin
    @Provides
    fun provideWorkoutDao(database: MetaSelfDatabase): WorkoutDao = database.workoutDao()

    @Provides
    fun provideHealthReadingDao(database: MetaSelfDatabase): HealthReadingDao =
        database.healthReadingDao()

    @Provides
    fun provideSleepDao(database: MetaSelfDatabase): SleepDao = database.sleepDao()

    @Provides
    fun provideHealthDayDao(database: MetaSelfDatabase): HealthDayDao = database.healthDayDao()

    @Provides
    fun provideMovementCorrectionDao(database: MetaSelfDatabase): MovementCorrectionDao =
        database.movementCorrectionDao()

    @Provides
    fun provideHealthBookkeepingDao(database: MetaSelfDatabase): HealthBookkeepingDao =
        database.healthBookkeepingDao()
```

- [ ] **Step 6: Generate the schema file and compile the test** (check `free -m` first)

Run: `~/bin/gradlew-safe :app:compileDebugUnitTestKotlin > /tmp/ms-health1.log 2>&1; echo "exit $?"`
Expected: `exit 0`, and `app/schemas/com.metaself.app.data.day.MetaSelfDatabase/6.json` exists.
`git status --short app/schemas` shows `?? …/6.json` and nothing else.

- [ ] **Step 7: Run the DAO test**

Run: `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.health.*" > /tmp/ms-health1.log 2>&1; echo "exit $?"`
Expected on this box: `exit 0`, every `HealthRecordDaoTest` test **skipped** (aarch64). CI runs them
in Task 7.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/metaself/app/data/health/ \
  app/src/main/java/com/metaself/app/data/day/MetaSelfDatabase.kt \
  app/src/main/java/com/metaself/app/di/DataModule.kt \
  app/schemas/com.metaself.app.data.day.MetaSelfDatabase/6.json \
  app/src/test/java/com/metaself/app/data/health/HealthRecordDaoTest.kt
git commit -m "feat: schema 6 has somewhere to keep the health record (D65, D68)"
```

---

### Task 3: Migration 5 → 6, checked here and in CI

**Files:**
- Create: `app/src/main/java/com/metaself/app/data/day/HealthRecordMigration.kt`
- Modify: `app/src/main/java/com/metaself/app/di/DataModule.kt` (`addMigrations`)
- Create: `tools/check-migration-5-6.py`
- Test: `app/src/test/java/com/metaself/app/data/MigrationTest.kt`

- [ ] **Step 1: Write the failing migration test.** Append to `MigrationTest`, importing
  `com.metaself.app.data.day.MIGRATION_5_6`:

```kotlin
    /**
     * Version 6 adds the health record's eight tables, empty. A meal and a weight already there
     * come through as they were. Invented figures.
     */
    @Test
    fun `a version 5 database migrates to version 6, keeps its record, and has room for health data`() {
        assumeSqliteRuntime()

        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL("INSERT INTO meals (id, epochDay, loggedAtMillis, note) VALUES (1, 20699, 1000, NULL)")
            db.execSQL("INSERT INTO weights (epochDay, kg) VALUES (20699, 80.0)")
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)

        migrated.query("SELECT COUNT(*) FROM meals").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }
        migrated.query("SELECT kg FROM weights").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getDouble(0)).isEqualTo(80.0)
        }
        listOf(
            "health_readings", "workouts", "sleep_sessions", "sleep_stages", "health_days",
            "movement_corrections", "health_sync", "archive_months",
        ).forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
        migrated.close()
    }
```

`runMigrationsAndValidate(…, true, …)` is the proof that matters here: it compares every new table
with `6.json`. Only CI can run it.

- [ ] **Step 2: Write `HealthRecordMigration.kt`.** Open `6.json`; for each of the eight new
  entities, copy its `createSql` and each index's `createSql` **verbatim**, replacing `${TABLE_NAME}`
  with the table name, one `db.execSQL` per statement, tables in the order below and each table's
  indices straight after it. What follows is what Room 2.6.1 is expected to generate; where the file
  differs in any character, the file is right.

```kotlin
package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 6: the health record (D65, D68).
 *
 * **Eight tables and their indices are created, and nothing else is read or written.** No meal,
 * item, food, weight or packet is touched; every day the owner has logged is worth after this exactly
 * what it is worth now. `tools/check-migration-5-6.py` proves that on this machine and
 * `MigrationTest` validates the finished tables against the exported schema in CI.
 *
 * Every statement is the exported schema's own, verbatim: Room validates the finished database
 * against that file, and a hand-typed difference in a default or a null fails validation for a reason
 * that takes an afternoon to find.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_readings` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                "`startMillis` INTEGER NOT NULL, `endMillis` INTEGER, `value` REAL NOT NULL, " +
                "`unit` TEXT NOT NULL, `origin` TEXT NOT NULL, `recordId` TEXT NOT NULL, " +
                "`sampleIndex` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_health_readings_kind_epochDay_startMillis` " +
                "ON `health_readings` (`kind`, `epochDay`, `startMillis`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_health_readings_origin_recordId_sampleIndex` " +
                "ON `health_readings` (`origin`, `recordId`, `sampleIndex`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workouts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                "`startedAtMillis` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `title` TEXT, `distanceM` INTEGER, `energyKcal` INTEGER, " +
                "`energySource` TEXT NOT NULL, `effort` TEXT, `source` TEXT NOT NULL, " +
                "`origin` TEXT, `originId` TEXT, `hidden` INTEGER NOT NULL DEFAULT 0, `note` TEXT, " +
                "`avgHeartRate` INTEGER, `maxHeartRate` INTEGER, `zoneSeconds` TEXT, " +
                "`zoneMaxSource` TEXT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_epochDay` ON `workouts` (`epochDay`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workouts_origin_originId` " +
                "ON `workouts` (`origin`, `originId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sleep_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                "`startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, " +
                "`origin` TEXT NOT NULL, `recordId` TEXT NOT NULL, `title` TEXT)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sleep_sessions_epochDay` ON `sleep_sessions` (`epochDay`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_sessions_origin_recordId` " +
                "ON `sleep_sessions` (`origin`, `recordId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sleep_stages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                "`stage` TEXT NOT NULL, `startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sleep_sessions`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sleep_stages_sessionId` ON `sleep_stages` (`sessionId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_days` (" +
                "`epochDay` INTEGER NOT NULL, `computedAtMillis` INTEGER NOT NULL, " +
                "`steps` INTEGER, `stepsSource` TEXT, `distanceM` INTEGER, `distanceSource` TEXT, " +
                "`activeKcal` INTEGER, `activeKcalSource` TEXT, `totalKcal` INTEGER, " +
                "`totalKcalSource` TEXT, `restingHeartRate` INTEGER, `restingHeartRateSource` TEXT, " +
                "`avgHeartRate` INTEGER, `avgHeartRateSource` TEXT, `hrvMs` REAL, `hrvSource` TEXT, " +
                "`oxygenPct` REAL, `oxygenSource` TEXT, `respiratoryRate` REAL, " +
                "`respiratoryRateSource` TEXT, `sleepMinutes` INTEGER, `deepMinutes` INTEGER, " +
                "`lightMinutes` INTEGER, `remMinutes` INTEGER, `awakeMinutes` INTEGER, " +
                "`sleepSource` TEXT, `workoutCount` INTEGER, `workoutMinutes` INTEGER, " +
                "`workoutSource` TEXT, PRIMARY KEY(`epochDay`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `movement_corrections` (" +
                "`epochDay` INTEGER NOT NULL, `steps` INTEGER, `activeKcal` INTEGER, " +
                "`setAtMillis` INTEGER NOT NULL, `note` TEXT, PRIMARY KEY(`epochDay`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_sync` (" +
                "`kind` TEXT NOT NULL, `changesToken` TEXT, `tokenAtMillis` INTEGER, " +
                "`catchUpCursorMillis` INTEGER, `catchUpDone` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`kind`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `archive_months` (" +
                "`month` TEXT NOT NULL, `changedAtMillis` INTEGER NOT NULL, " +
                "`writtenAtMillis` INTEGER, PRIMARY KEY(`month`))",
        )
    }
}
```

- [ ] **Step 3: Register it.** In `DataModule.provideDatabase`, add `MIGRATION_5_6,` after
  `MIGRATION_4_5,` in `addMigrations(...)`, importing `com.metaself.app.data.day.MIGRATION_5_6`.

- [ ] **Step 4: Write `tools/check-migration-5-6.py`:**

```python
#!/usr/bin/env python3
"""Run the version 5 -> 6 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside here and runs in CI.
In the pattern of `check-migration-4-5.py`, this extracts every statement from
`HealthRecordMigration.kt` (rather than retyping them, which would test a copy), builds a version 5
database from the committed `5.json`, puts one row in every table, and runs the migration.

WHAT IT PROVES
  - every statement parses and executes;
  - the statements are, character for character, the ones `6.json` declares for the new tables;
  - each new table has the same columns, types, nullability, defaults, keys, indices and foreign
    keys as a database built fresh from `6.json`;
  - every table that existed has exactly the rows it had before;
  - the unique indices refuse a second copy of a sample, a session and a night, and allow any number
    of typed workouts with no origin;
  - deleting a night deletes its stages once foreign keys are on, as Room turns them on.

WHAT IT DOES NOT PROVE
  - Room's own validation (`runMigrationsAndValidate`) — CI only.

Usage:  python3 tools/check-migration-5-6.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/HealthRecordMigration.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
NEW_TABLES = (
    "health_readings", "workouts", "sleep_sessions", "sleep_stages", "health_days",
    "movement_corrections", "health_sync", "archive_months",
)


def statements():
    """Every SQL string the migration executes, in source order."""
    raw = MIGRATION.read_text()
    src = "\n".join(l for l in raw.splitlines() if not l.strip().startswith("//"))
    found = []
    for match in re.finditer(r'db\.execSQL\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', src):
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1))
        found.append("".join(parts).replace('\\"', '"'))
    return found


def declared(version, tables=None):
    """Every CREATE statement the exported schema declares, tables first within each entity."""
    schema = json.loads((SCHEMAS / f"{version}.json").read_text())
    out = []
    for entity in schema["database"]["entities"]:
        table = entity["tableName"]
        if tables and table not in tables:
            continue
        out.append(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            out.append(index["createSql"].replace("${TABLE_NAME}", table))
    return out


def build(version):
    db = sqlite3.connect(":memory:")
    for sql in declared(version):
        db.execute(sql)
    return db


def shape(db, table):
    """Columns, indices (without the creation-order number) and foreign keys."""
    return (
        db.execute(f"PRAGMA table_info(`{table}`)").fetchall(),
        sorted(row[1:] for row in db.execute(f"PRAGMA index_list(`{table}`)").fetchall()),
        db.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(),
    )


def everything(db, tables):
    return {t: sorted(db.execute(f"SELECT * FROM `{t}`").fetchall(), key=repr) for t in tables}


def refused(db, sql):
    try:
        db.execute(sql)
        return False
    except sqlite3.IntegrityError:
        return True


def main():
    failures = []

    migration = statements()
    expected = declared(6, NEW_TABLES)
    if sorted(migration) != sorted(expected):
        missing = sorted(set(expected) - set(migration))
        extra = sorted(set(migration) - set(expected))
        failures.append(
            "the migration's statements are not the ones 6.json declares:\n"
            + "".join(f"  only in 6.json:    {s}\n" for s in missing)
            + "".join(f"  only in migration: {s}\n" for s in extra)
        )

    db = build(5)
    old_tables = [t for (t,) in db.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")]
    # Invented values: one row in every version-5 table, so "untouched" means something for each.
    # Foreign keys are off (sqlite3's default, and the state a Room migration runs in).
    for table in old_tables:
        columns = db.execute(f"PRAGMA table_info(`{table}`)").fetchall()
        values = [1 if kind.upper() in ("INTEGER", "REAL") else f"{table}.{name}"
                  for _, name, kind, _, _, _ in columns]
        db.execute(f"INSERT INTO `{table}` VALUES ({', '.join('?' for _ in values)})", values)
    before = everything(db, old_tables)

    for sql in migration:
        db.execute(sql)

    if everything(db, old_tables) != before:
        failures.append("a table that existed before the migration changed")

    fresh = build(6)
    for table in NEW_TABLES:
        if shape(db, table) != shape(fresh, table):
            failures.append(f"`{table}` after the migration differs from 6.json:\n"
                            f"  migrated: {shape(db, table)}\n  6.json:   {shape(fresh, table)}")

    sample = ("INSERT INTO health_readings (kind, startMillis, value, unit, origin, recordId, "
              "sampleIndex, epochDay) VALUES ('HEART_RATE', 1000, 60, 'bpm', 'o', 'hr-1', 0, 20699)")
    db.execute(sample)
    if not refused(db, sample):
        failures.append("a second copy of the same sample was accepted")

    typed = ("INSERT INTO workouts (epochDay, startedAtMillis, durationMinutes, kind, energySource, "
             "source) VALUES (20699, 1000, 45, 'STRENGTH', 'NONE', 'TYPED')")
    db.execute(typed)
    db.execute(typed)
    synced = ("INSERT INTO workouts (epochDay, startedAtMillis, durationMinutes, kind, energySource, "
              "source, origin, originId) VALUES (20699, 1000, 30, 'RUN', 'NONE', 'SYNCED', 'o', 'w-1')")
    db.execute(synced)
    if not refused(db, synced):
        failures.append("a second copy of the same synced session was accepted")

    db.execute("PRAGMA foreign_keys = ON")
    night = ("INSERT INTO sleep_sessions (id, epochDay, startMillis, endMillis, origin, recordId) "
             "VALUES (1, 20699, 1000, 3000, 'o', 's-1')")
    db.execute(night)
    if not refused(db, night.replace("(1,", "(2,")):
        failures.append("a second copy of the same night was accepted")
    db.execute("INSERT INTO sleep_stages (sessionId, stage, startMillis, endMillis) "
               "VALUES (1, 'DEEP', 1000, 2000)")
    db.execute("DELETE FROM sleep_sessions WHERE id = 1")
    if db.execute("SELECT COUNT(*) FROM sleep_stages").fetchone() != (0,):
        failures.append("deleting a night left its stages behind")

    if failures:
        print("FAIL")
        for f in failures:
            print(" -", f)
        sys.exit(1)
    print(f"OK: {len(migration)} statements, {len(old_tables)} existing tables untouched, "
          f"{len(NEW_TABLES)} new tables match 6.json")


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: Run the check**

Run: `python3 tools/check-migration-5-6.py; echo "exit $?"`
Expected: `OK: 15 statements, … existing tables untouched, 8 new tables match 6.json`, `exit 0`.
If statements differ, fix `HealthRecordMigration.kt` to match `6.json` — never the other way round.
If a version-5 table's generic row is refused by a constraint, give that table a hand-written row in
the script and say so in the report.

- [ ] **Step 6: Compile and run the migration test** (skips here)

Run: `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.MigrationTest" > /tmp/ms-health1.log 2>&1; echo "exit $?"`
Expected: `exit 0`, every `MigrationTest` test skipped on aarch64.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/metaself/app/data/day/HealthRecordMigration.kt \
  app/src/main/java/com/metaself/app/di/DataModule.kt \
  app/src/test/java/com/metaself/app/data/MigrationTest.kt \
  tools/check-migration-5-6.py
git commit -m "feat: migration 5 to 6 adds the health record and touches nothing else (D65)"
```

---

### Task 4: Backup format 3 carries the structured record

**Files:**
- Modify: `app/src/main/java/com/metaself/app/domain/backup/Backup.kt`
- Test: `app/src/test/java/com/metaself/app/domain/backup/BackupCodecTest.kt`

A version-2 app refuses a version-3 file whole, which is the point: without the bump it would read
the file, ignore the unknown blocks, and restore everything else — a half-restore that looks whole.

- [ ] **Step 1: Write the failing tests.** In `BackupCodecTest`, add to the `full` fixture:

```kotlin
        workouts = listOf(
            BackupWorkout(
                epochDay = 20_699,
                startedAtMillis = 1_000,
                durationMinutes = 30,
                kind = "RUN",
                title = "Running",
                distanceM = 5_000,
                energyKcal = 300,
                energySource = "BAND",
                source = "SYNCED",
                origin = "com.example.band",
                originId = "abc-1",
                avgHeartRate = 140,
                maxHeartRate = 160,
                zoneSeconds = "0,300,900,600,0",
                zoneMaxSource = "ESTIMATED",
            ),
        ),
        sleep = listOf(
            BackupSleep(
                epochDay = 20_699,
                startMillis = 1_000,
                endMillis = 3_000,
                origin = "com.example.band",
                recordId = "s-1",
                stages = listOf(BackupSleepStage("DEEP", 1_000, 2_000)),
            ),
        ),
        healthDays = listOf(
            BackupHealthDay(epochDay = 20_699, computedAtMillis = 5_000, steps = 9_000, stepsSource = "TOTAL"),
        ),
        movementCorrections = listOf(
            BackupMovementCorrection(epochDay = 20_699, steps = 9_000, setAtMillis = 2_000),
        ),
```

and these tests:

```kotlin
    @Test
    fun `the health record is written in words a person can check`() {
        val text = BackupCodec.encode(full)

        assertThat(text).contains("\"workouts\"")
        assertThat(text).contains("\"energy_source\": \"BAND\"")
        assertThat(text).contains("\"sleep\"")
        assertThat(text).contains("\"health_days\"")
        assertThat(text).contains("\"steps_source\": \"TOTAL\"")
        assertThat(text).contains("\"movement_corrections\"")
    }

    /** D71: the raw readings go to Drive by month, never into the daily file. */
    @Test
    fun `the daily file has no raw readings`() {
        assertThat(BackupCodec.encode(full)).doesNotContain("readings")
    }

    /** Every file written before the health record existed stays restorable, and brings none. */
    @Test
    fun `a version 2 file still reads, with no health record`() {
        val version2 = """{"version": 2, "exported_at": 1000, "meals": [], "weights": []}"""

        val read = BackupCodec.decode(version2)!!

        assertThat(read.workouts).isEmpty()
        assertThat(read.sleep).isEmpty()
        assertThat(read.healthDays).isEmpty()
        assertThat(read.movementCorrections).isEmpty()
    }

    @Test
    fun `the format is version 3`() {
        assertThat(Backup.CURRENT_VERSION).isEqualTo(3)
    }
```

Before relying on `the daily file has no raw readings`, check that the existing `full` fixture has no
field or value containing the word "readings"; if it does, assert `doesNotContain("\"readings\"")`
instead and say so.

- [ ] **Step 2: Run them to see them fail**

Run: `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.backup.BackupCodecTest" > /tmp/ms-health1.log 2>&1; echo "exit $?"`
Expected: compilation fails on `BackupWorkout`.

- [ ] **Step 3: Change `Backup.kt`.** Add at the end of `Backup`'s constructor:

```kotlin
    val workouts: List<BackupWorkout> = emptyList(),
    val sleep: List<BackupSleep> = emptyList(),
    @SerialName("health_days") val healthDays: List<BackupHealthDay> = emptyList(),
    @SerialName("movement_corrections")
    val movementCorrections: List<BackupMovementCorrection> = emptyList(),
```

Set `const val CURRENT_VERSION = 3` and add to its KDoc:

```kotlin
         * Version 3 adds the health record's structured part — workouts, nights of sleep with their
         * stages, daily summaries and the owner's corrections (D71). The raw readings are not here:
         * they go to Drive by month. A version 1 or 2 file has none of it, and restoring one leaves
         * the phone with none — a restore replaces, and the confirmation says what it will delete.
```

Add after `BackupWeight`:

```kotlin
/**
 * One workout, written verbatim — the band's or the owner's. Every enumeration is written as the
 * name the database holds, so a file from a later version restores rows this one cannot yet read
 * rather than dropping them.
 */
@Serializable
data class BackupWorkout(
    @SerialName("epoch_day") val epochDay: Long,
    @SerialName("started_at") val startedAtMillis: Long,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val kind: String,
    val title: String? = null,
    @SerialName("distance_m") val distanceM: Int? = null,
    @SerialName("energy_kcal") val energyKcal: Int? = null,
    @SerialName("energy_source") val energySource: String,
    val effort: String? = null,
    val source: String,
    val origin: String? = null,
    @SerialName("origin_id") val originId: String? = null,
    val hidden: Boolean = false,
    val note: String? = null,
    @SerialName("avg_heart_rate") val avgHeartRate: Int? = null,
    @SerialName("max_heart_rate") val maxHeartRate: Int? = null,
    @SerialName("zone_seconds") val zoneSeconds: String? = null,
    @SerialName("zone_max_source") val zoneMaxSource: String? = null,
)

/** One night, under the day he woke up, with its stages inside it. */
@Serializable
data class BackupSleep(
    @SerialName("epoch_day") val epochDay: Long,
    @SerialName("start") val startMillis: Long,
    @SerialName("end") val endMillis: Long,
    val origin: String,
    @SerialName("record_id") val recordId: String,
    val title: String? = null,
    val stages: List<BackupSleepStage> = emptyList(),
)

@Serializable
data class BackupSleepStage(
    val stage: String,
    @SerialName("start") val startMillis: Long,
    @SerialName("end") val endMillis: Long,
)

/** One day of the health record, summarised; each figure beside where it came from (D69). */
@Serializable
data class BackupHealthDay(
    @SerialName("epoch_day") val epochDay: Long,
    @SerialName("computed_at") val computedAtMillis: Long,
    val steps: Int? = null,
    @SerialName("steps_source") val stepsSource: String? = null,
    @SerialName("distance_m") val distanceM: Int? = null,
    @SerialName("distance_source") val distanceSource: String? = null,
    @SerialName("active_kcal") val activeKcal: Int? = null,
    @SerialName("active_kcal_source") val activeKcalSource: String? = null,
    @SerialName("total_kcal") val totalKcal: Int? = null,
    @SerialName("total_kcal_source") val totalKcalSource: String? = null,
    @SerialName("resting_heart_rate") val restingHeartRate: Int? = null,
    @SerialName("resting_heart_rate_source") val restingHeartRateSource: String? = null,
    @SerialName("avg_heart_rate") val avgHeartRate: Int? = null,
    @SerialName("avg_heart_rate_source") val avgHeartRateSource: String? = null,
    @SerialName("hrv_ms") val hrvMs: Double? = null,
    @SerialName("hrv_source") val hrvSource: String? = null,
    @SerialName("oxygen_pct") val oxygenPct: Double? = null,
    @SerialName("oxygen_source") val oxygenSource: String? = null,
    @SerialName("respiratory_rate") val respiratoryRate: Double? = null,
    @SerialName("respiratory_rate_source") val respiratoryRateSource: String? = null,
    @SerialName("sleep_minutes") val sleepMinutes: Int? = null,
    @SerialName("deep_minutes") val deepMinutes: Int? = null,
    @SerialName("light_minutes") val lightMinutes: Int? = null,
    @SerialName("rem_minutes") val remMinutes: Int? = null,
    @SerialName("awake_minutes") val awakeMinutes: Int? = null,
    @SerialName("sleep_source") val sleepSource: String? = null,
    @SerialName("workout_count") val workoutCount: Int? = null,
    @SerialName("workout_minutes") val workoutMinutes: Int? = null,
    @SerialName("workout_source") val workoutSource: String? = null,
)

/** The owner's own figure for a day's movement (D12d). A null reading was left alone. */
@Serializable
data class BackupMovementCorrection(
    @SerialName("epoch_day") val epochDay: Long,
    val steps: Int? = null,
    @SerialName("active_kcal") val activeKcal: Int? = null,
    @SerialName("set_at") val setAtMillis: Long,
    val note: String? = null,
)
```

- [ ] **Step 4: Run the backup domain tests**

Run: `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.backup.*" > /tmp/ms-health1.log 2>&1; echo "exit $?"`
Expected: `exit 0`. `a file from a later version is refused whole` still passes (it substitutes the
current version, whatever it is).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/metaself/app/domain/backup/Backup.kt \
  app/src/test/java/com/metaself/app/domain/backup/BackupCodecTest.kt
git commit -m "feat: backup format 3 carries workouts, sleep and daily summaries (D71)"
```

---

### Task 5: Export and restore the record, inside the one transaction

**Files:**
- Modify: `app/src/main/java/com/metaself/app/data/backup/BackupRepository.kt`
- Test: `app/src/test/java/com/metaself/app/data/backup/BackupRestoreOrderTest.kt`
- Test: `app/src/test/java/com/metaself/app/data/backup/BackupRoundTripTest.kt`
- Modify (construction only): `app/src/test/java/com/metaself/app/ui/screen/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Make the order test say where the new writes go.** In `BackupRestoreOrderTest`:

In the `dao(...)` proxy helper, make every read answer an empty list and a night's insert answer an
id. Change the line `"allMeals", "all" -> emptyList<Any>()` to be handled by a guard before the
`when`, and add `"insertSession" -> 1L` beside `"insertMeal" -> 1L`:

```kotlin
    private inline fun <reified T> dao(prefix: String = "", failingOn: String? = null): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            when {
                method.name == "toString" -> T::class.java.simpleName
                method.name == "hashCode" -> 0
                method.name == "equals" -> false
                method.name.startsWith("all") -> emptyList<Any>()
                else -> {
                    log += prefix + method.name
                    if (method.name == failingOn) throw IllegalStateException("disk full")
                    when (method.name) {
                        "insertMeal", "insertSession" -> 1L
                        "insertItems" -> listOf(1L)
                        else -> Unit
                    }
                }
            }
        } as T
```

In `restorer(...)`, add to `BackupRepository(...)`:

```kotlin
        workouts = dao(prefix = "workouts."),
        sleep = dao(prefix = "sleep."),
        days = dao(prefix = "days."),
        corrections = dao(prefix = "corrections."),
```

In `aFile()`, add (imports `BackupWorkout`, `BackupSleep`, `BackupSleepStage`, `BackupHealthDay`,
`BackupMovementCorrection` from `com.metaself.app.domain.backup`):

```kotlin
        workouts = listOf(
            BackupWorkout(
                epochDay = TEST_EPOCH_DAY,
                startedAtMillis = 1_000,
                durationMinutes = 45,
                kind = "STRENGTH",
                energyKcal = 150,
                energySource = "MET_ESTIMATE",
                effort = "MODERATE",
                source = "TYPED",
            ),
        ),
        sleep = listOf(
            BackupSleep(
                epochDay = TEST_EPOCH_DAY,
                startMillis = 1_000,
                endMillis = 3_000,
                origin = "com.example.band",
                recordId = "s-1",
                stages = listOf(BackupSleepStage("DEEP", 1_000, 2_000)),
            ),
        ),
        healthDays = listOf(BackupHealthDay(epochDay = TEST_EPOCH_DAY, computedAtMillis = 1_000)),
        movementCorrections = listOf(
            BackupMovementCorrection(epochDay = TEST_EPOCH_DAY, steps = 9_000, setAtMillis = 1_000),
        ),
```

Replace the expected log of `the settings are written inside the transaction, …` with:

```kotlin
            assertThat(log).containsExactly(
                "snapshot",
                "begin",
                "deleteAllMeals",
                "weights.deleteAll",
                "workouts.deleteAll",
                "sleep.deleteAll",
                "days.deleteAll",
                "corrections.deleteAll",
                "findOrCreate Yoghurt",
                "create Breakfast",
                "put",
                "insertMeal",
                "insertItems",
                "weights.upsert",
                "workouts.insertAll",
                "sleep.insertSession",
                "sleep.insertStages",
                "days.insertAll",
                "corrections.insertAll",
                "profile.save",
                "saveRevision",
                "saveArrival",
                "ai.setModel",
                "ai.setDailyCeiling",
                "reminders.save",
                "commit",
                "alarm.schedule",
            ).inOrder()
```

Replace the expected value in `a restore that commits says what it restored` with
`RestoreResult(meals = 1, weights = 1, hasProfile = true, workouts = 1, healthDays = 1)`, and add:

```kotlin
    @Test
    fun `a health-record write that throws rolls back and puts the settings back`() = runTest {
        val failure = thrownBy<NothingRestored> {
            BackupRepository(
                meals = dao(),
                weights = dao(prefix = "weights."),
                workouts = dao(prefix = "workouts."),
                sleep = dao(prefix = "sleep.", failingOn = "insertStages"),
                days = dao(prefix = "days."),
                corrections = dao(prefix = "corrections."),
                profiles = Profiles(),
                reminders = Reminders(),
                scheduler = Scheduler(),
                ai = Ai(),
                foods = Foods(),
                savedMeals = SavedMeals(),
                transaction = Transaction(),
                snapshot = Snapshot(),
            ).restore(aFile())
        }

        assertThat(failure.cause).hasMessageThat().isEqualTo("disk full")
        assertThat(log).containsAtLeast("sleep.insertStages", "rollback", "put back").inOrder()
        assertThat(log).doesNotContain("profile.save")
    }
```

Check the helper class names (`Profiles`, `Reminders`, `Scheduler`, `Ai`, `Foods`, `SavedMeals`,
`Transaction`, `Snapshot`) against the file; they are the ones `restorer(...)` already uses.

- [ ] **Step 2: Run it to see it fail**

Run: `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.backup.BackupRestoreOrderTest" > /tmp/ms-health1.log 2>&1; echo "exit $?"`
Expected: compilation fails — `BackupRepository` has no `workouts` parameter.

- [ ] **Step 3: Change `BackupRepository`.**

Imports: `HealthDayDao`, `HealthDayEntity`, `MovementCorrectionDao`, `MovementCorrectionEntity`,
`SleepDao`, `SleepSessionEntity`, `SleepStageEntity`, `WorkoutDao`, `WorkoutEntity` from
`com.metaself.app.data.health`; `BackupHealthDay`, `BackupMovementCorrection`, `BackupSleep`,
`BackupSleepStage`, `BackupWorkout` from `com.metaself.app.domain.backup`.

`RestoreResult` becomes:

```kotlin
/**
 * What a restore did, so the owner is told in numbers rather than reassured in adjectives.
 *
 * The two counts of the health record come last and defaulted, so every existing positional
 * construction still means what it did.
 */
data class RestoreResult(
    val meals: Int,
    val weights: Int,
    val hasProfile: Boolean,
    val workouts: Int = 0,
    val healthDays: Int = 0,
)
```

Constructor, after `private val weights: WeightDao,`:

```kotlin
    private val workouts: WorkoutDao,
    private val sleep: SleepDao,
    private val days: HealthDayDao,
    private val corrections: MovementCorrectionDao,
```

In `export`, after the `weights = …` line:

```kotlin
            workouts = workouts.all().map { it.toBackup() },
            sleep = sleep.allStages().groupBy { it.sessionId }.let { stagesBySession ->
                sleep.allSessions().map { night ->
                    night.toBackup(stagesBySession[night.id].orEmpty())
                }
            },
            healthDays = days.all().map { it.toBackup() },
            movementCorrections = corrections.all().map {
                BackupMovementCorrection(it.epochDay, it.steps, it.activeKcal, it.setAtMillis, it.note)
            },
```

Change the KDoc sentence **"The meals and the weights are replaced; the foods are merged into."** to
**"The meals, the weights and the health record's structured part are replaced; the foods are
merged into."** and add, at the end of that KDoc, before the closing `*/`:

```kotlin
     *
     * **The raw readings are not touched by a restore.** They are not in the file (D71); they stay
     * on the phone, and the daily summaries can be recomputed from them.
```

In `restore`, inside `transaction.run { … }`:

```kotlin
                meals.deleteAllMeals()
                weights.deleteAll()
                workouts.deleteAll()
                sleep.deleteAll()
                days.deleteAll()
                corrections.deleteAll()
                // The foods first, so every row restored after them has something to point at.
                val restoredFoods = restoreFoods(prepared)
                val savedMealIdByName = restoreSavedMeals(prepared, restoredFoods.byKey)
                restoreMeals(prepared, restoredFoods, savedMealIdByName)
                prepared.weights.forEach { weights.upsert(it) }
                workouts.insertAll(prepared.workouts)
                prepared.nights.forEach { (night, stages) ->
                    val id = sleep.insertSession(night)
                    sleep.insertStages(stages.map { it.copy(sessionId = id) })
                }
                days.insertAll(prepared.days)
                corrections.insertAll(prepared.corrections)
                // Last, and inside: a throw here is still a throw out of the transaction.
                restoreSettings(prepared)
```

The returned result:

```kotlin
        return RestoreResult(
            meals = backup.meals.size,
            weights = backup.weights.size,
            hasProfile = backup.profile != null,
            workouts = backup.workouts.size,
            healthDays = backup.healthDays.size,
        )
```

In `prepare`, add to `Prepared(...)`:

```kotlin
            workouts = backup.workouts.map { it.toEntity() },
            nights = backup.sleep.map { night ->
                night.toEntity() to night.stages.map {
                    SleepStageEntity(sessionId = 0, stage = it.stage, startMillis = it.startMillis, endMillis = it.endMillis)
                }
            },
            days = backup.healthDays.map { it.toEntity() },
            corrections = backup.movementCorrections.map {
                MovementCorrectionEntity(it.epochDay, it.steps, it.activeKcal, it.setAtMillis, it.note)
            },
```

and to `class Prepared`:

```kotlin
        val workouts: List<WorkoutEntity>,
        /** Each night with its stages; the stages learn their night's id when it is inserted. */
        val nights: List<Pair<SleepSessionEntity, List<SleepStageEntity>>>,
        val days: List<HealthDayEntity>,
        val corrections: List<MovementCorrectionEntity>,
```

`whatIsHere` becomes:

```kotlin
    suspend fun whatIsHere(): RestoreResult = RestoreResult(
        meals = meals.allMeals().size,
        weights = weights.all().size,
        hasProfile = profiles.profile.first() != null,
        workouts = workouts.all().size,
        healthDays = days.all().size,
    )
```

And the mappers at the bottom of the file, beside `FoodItem.toBackup`:

```kotlin
/** Verbatim: the file keeps what the table holds, including names this version cannot read. */
private fun WorkoutEntity.toBackup() = BackupWorkout(
    epochDay = epochDay,
    startedAtMillis = startedAtMillis,
    durationMinutes = durationMinutes,
    kind = kind,
    title = title,
    distanceM = distanceM,
    energyKcal = energyKcal,
    energySource = energySource,
    effort = effort,
    source = source,
    origin = origin,
    originId = originId,
    hidden = hidden,
    note = note,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    zoneSeconds = zoneSeconds,
    zoneMaxSource = zoneMaxSource,
)

/** Id 0, so the table numbers the rows afresh, as a restored meal's are. */
private fun BackupWorkout.toEntity() = WorkoutEntity(
    epochDay = epochDay,
    startedAtMillis = startedAtMillis,
    durationMinutes = durationMinutes,
    kind = kind,
    title = title,
    distanceM = distanceM,
    energyKcal = energyKcal,
    energySource = energySource,
    effort = effort,
    source = source,
    origin = origin,
    originId = originId,
    hidden = hidden,
    note = note,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    zoneSeconds = zoneSeconds,
    zoneMaxSource = zoneMaxSource,
)

private fun SleepSessionEntity.toBackup(stages: List<SleepStageEntity>) = BackupSleep(
    epochDay = epochDay,
    startMillis = startMillis,
    endMillis = endMillis,
    origin = origin,
    recordId = recordId,
    title = title,
    stages = stages.map { BackupSleepStage(it.stage, it.startMillis, it.endMillis) },
)

private fun BackupSleep.toEntity() = SleepSessionEntity(
    epochDay = epochDay,
    startMillis = startMillis,
    endMillis = endMillis,
    origin = origin,
    recordId = recordId,
    title = title,
)

private fun HealthDayEntity.toBackup() = BackupHealthDay(
    epochDay = epochDay,
    computedAtMillis = computedAtMillis,
    steps = steps,
    stepsSource = stepsSource,
    distanceM = distanceM,
    distanceSource = distanceSource,
    activeKcal = activeKcal,
    activeKcalSource = activeKcalSource,
    totalKcal = totalKcal,
    totalKcalSource = totalKcalSource,
    restingHeartRate = restingHeartRate,
    restingHeartRateSource = restingHeartRateSource,
    avgHeartRate = avgHeartRate,
    avgHeartRateSource = avgHeartRateSource,
    hrvMs = hrvMs,
    hrvSource = hrvSource,
    oxygenPct = oxygenPct,
    oxygenSource = oxygenSource,
    respiratoryRate = respiratoryRate,
    respiratoryRateSource = respiratoryRateSource,
    sleepMinutes = sleepMinutes,
    deepMinutes = deepMinutes,
    lightMinutes = lightMinutes,
    remMinutes = remMinutes,
    awakeMinutes = awakeMinutes,
    sleepSource = sleepSource,
    workoutCount = workoutCount,
    workoutMinutes = workoutMinutes,
    workoutSource = workoutSource,
)

private fun BackupHealthDay.toEntity() = HealthDayEntity(
    epochDay = epochDay,
    computedAtMillis = computedAtMillis,
    steps = steps,
    stepsSource = stepsSource,
    distanceM = distanceM,
    distanceSource = distanceSource,
    activeKcal = activeKcal,
    activeKcalSource = activeKcalSource,
    totalKcal = totalKcal,
    totalKcalSource = totalKcalSource,
    restingHeartRate = restingHeartRate,
    restingHeartRateSource = restingHeartRateSource,
    avgHeartRate = avgHeartRate,
    avgHeartRateSource = avgHeartRateSource,
    hrvMs = hrvMs,
    hrvSource = hrvSource,
    oxygenPct = oxygenPct,
    oxygenSource = oxygenSource,
    respiratoryRate = respiratoryRate,
    respiratoryRateSource = respiratoryRateSource,
    sleepMinutes = sleepMinutes,
    deepMinutes = deepMinutes,
    lightMinutes = lightMinutes,
    remMinutes = remMinutes,
    awakeMinutes = awakeMinutes,
    sleepSource = sleepSource,
    workoutCount = workoutCount,
    workoutMinutes = workoutMinutes,
    workoutSource = workoutSource,
)
```

- [ ] **Step 4: Give the other two constructions the new arguments.**

`BackupRoundTripTest.repository(...)`:

```kotlin
        workouts = db.workoutDao(),
        sleep = db.sleepDao(),
        days = db.healthDayDao(),
        corrections = db.movementCorrectionDao(),
```

`SettingsViewModelTest`, in `class Daos` (imports from `com.metaself.app.data.health`):

```kotlin
        val workouts: WorkoutDao = table(failing)
        val sleep: SleepDao = table(failing)
        val days: HealthDayDao = table(failing)
        val corrections: MovementCorrectionDao = table(failing)
```

In its `table(...)` proxy, replace `method.name == "allMeals" || method.name == "all" -> emptyList<Any>()`
with `method.name.startsWith("all") -> emptyList<Any>()`, and add `method.name == "insertSession" -> 1L`
before the final `else`. In `backups(...)`:

```kotlin
            workouts = daos.workouts,
            sleep = daos.sleep,
            days = daos.days,
            corrections = daos.corrections,
```

- [ ] **Step 5: Add the round trip over a real database** (CI only) to `BackupRoundTripTest`,
  importing the health entities:

```kotlin
    /** Invented figures. A band's run, a typed session, a night, a day and a correction survive a wipe. */
    @Test
    fun `the health record comes back from a wiped store as it was`() = runTest {
        val band = WorkoutEntity(
            epochDay = 20_699, startedAtMillis = 1_000, durationMinutes = 30, kind = "RUN",
            title = "Running", distanceM = 5_000, energyKcal = 300, energySource = "BAND",
            effort = null, source = "SYNCED", origin = "com.example.band", originId = "abc-1",
            hidden = true, note = "not a run", avgHeartRate = 140, maxHeartRate = 160,
            zoneSeconds = "0,300,900,600,0", zoneMaxSource = "ESTIMATED",
        )
        val typed = band.copy(
            kind = "STRENGTH", title = "Strength", distanceM = null, energyKcal = 150,
            energySource = "MET_ESTIMATE", effort = "MODERATE", source = "TYPED",
            origin = null, originId = null, hidden = false, note = null,
            avgHeartRate = null, maxHeartRate = null, zoneSeconds = null, zoneMaxSource = null,
        )
        db.workoutDao().insertAll(listOf(band, typed))
        val nightId = db.sleepDao().insertSession(
            SleepSessionEntity(epochDay = 20_699, startMillis = 1_000, endMillis = 3_000,
                origin = "com.example.band", recordId = "s-1", title = null),
        )
        db.sleepDao().insertStages(
            listOf(
                SleepStageEntity(sessionId = nightId, stage = "LIGHT", startMillis = 1_000, endMillis = 2_000),
                SleepStageEntity(sessionId = nightId, stage = "DEEP", startMillis = 2_000, endMillis = 3_000),
            ),
        )
        val day = HealthDayEntity(epochDay = 20_699, computedAtMillis = 5_000, steps = 9_000,
            stepsSource = "TOTAL", sleepMinutes = 30, sleepSource = "COMPUTED")
        db.healthDayDao().put(day)
        db.movementCorrectionDao().insertAll(listOf(MovementCorrectionEntity(20_699, 9_000, null, 2_000, null)))

        val file = BackupCodec.decode(BackupCodec.encode(repository().export(nowMillis = 5_000)))!!
        db.workoutDao().deleteAll()
        db.sleepDao().deleteAll()
        db.healthDayDao().deleteAll()
        db.movementCorrectionDao().deleteAll()
        val result = repository().restore(file)

        assertThat(result.workouts).isEqualTo(2)
        assertThat(result.healthDays).isEqualTo(1)
        assertThat(db.workoutDao().all().map { it.copy(id = 0) }).containsExactly(band, typed)
        val night = db.sleepDao().allSessions().single()
        assertThat(night.recordId).isEqualTo("s-1")
        assertThat(db.sleepDao().stagesOf(night.id).map { it.stage }).containsExactly("LIGHT", "DEEP").inOrder()
        assertThat(db.healthDayDao().day(20_699)).isEqualTo(day)
        assertThat(db.movementCorrectionDao().all().single().steps).isEqualTo(9_000)
    }
```

- [ ] **Step 6: Run the backup and settings tests**

Run: `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.data.backup.*" --tests "com.metaself.app.ui.screen.settings.*" > /tmp/ms-health1.log 2>&1; echo "exit $?"`
Expected: `exit 0`. `BackupRestoreOrderTest` and `SettingsViewModelTest` pass; `BackupRoundTripTest`
skips on aarch64.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/metaself/app/data/backup/BackupRepository.kt \
  app/src/test/java/com/metaself/app/data/backup/BackupRestoreOrderTest.kt \
  app/src/test/java/com/metaself/app/data/backup/BackupRoundTripTest.kt \
  app/src/test/java/com/metaself/app/ui/screen/settings/SettingsViewModelTest.kt
git commit -m "feat: a backup exports and restores the health record inside the one transaction (D71)"
```

---

### Task 6: The restore question names the health record, when there is one

**Files:**
- Modify: `app/src/main/java/com/metaself/app/ui/settings/BackupWording.kt`
- Modify: `app/src/main/java/com/metaself/app/ui/screen/settings/SettingsViewModel.kt` (~572, ~606)
- Test: `app/src/test/java/com/metaself/app/domain/backup/BackupWordingTest.kt`

- [ ] **Step 1: Write the failing tests** in `BackupWordingTest` (invented counts):

```kotlin
    /** A restore deletes the health record too; the question says so when there is one. */
    @Test
    fun `the confirmation names workouts and health days when either side has some`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 400, weights = 60, hasProfile = true, workouts = 12, healthDays = 30),
            incoming = RestoreResult(meals = 400, weights = 50, hasProfile = true),
        )

        assertThat(text).contains("delete 400 meals, 60 weights, 12 workouts and 30 days of health data")
        assertThat(text).contains("put back 400 meals, 50 weights, 0 workouts and 0 days of health data")
    }

    @Test
    fun `a phone holding only health data still has something to lose`() {
        val text = BackupWording.confirmReplacing(
            here = RestoreResult(meals = 0, weights = 0, hasProfile = false, healthDays = 1),
            incoming = RestoreResult(meals = 400, weights = 50, hasProfile = true),
        )

        assertThat(text).contains("1 day of health data")
        assertThat(text).doesNotContain("nothing here to lose")
    }

    @Test
    fun `saving and restoring name the health record only when there is one`() {
        assertThat(BackupWording.restored(RestoreResult(1, 1, true, workouts = 1, healthDays = 3)))
            .isEqualTo("Restored 1 meal, 1 weight, 1 workout and 3 days of health data.")
        assertThat(BackupWording.saved(RestoreResult(1, 1, true)))
            .isEqualTo("Saved 1 meal and 1 weight to the file.")
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.backup.BackupWordingTest" > /tmp/ms-health1.log 2>&1; echo "exit $?"`
Expected: 3 failures; the existing tests pass.

- [ ] **Step 3: Change `BackupWording`:**

```kotlin
    fun saved(result: RestoreResult): String = "Saved ${record(result)} to the file."

    fun restored(result: RestoreResult): String = "Restored ${record(result)}."

    /**
     * Asked before anything is destroyed, and it names what will go.
     *
     * "Are you sure?" is not information. A restore replaces, and the owner is entitled to know
     * what he is replacing before he agrees to it. The health record is named when either side holds
     * any, so a phone with none reads exactly as it did before the record existed.
     */
    fun confirmReplacing(here: RestoreResult, incoming: RestoreResult): String = buildString {
        val withHealth = hasHealth(here) || hasHealth(incoming)
        append("This will delete ")
        append(record(here, withHealth))
        append(" already on this phone, and put back ")
        append(record(incoming, withHealth))
        append(" from the file.")
        if (here.meals == 0 && here.weights == 0 && !hasHealth(here)) {
            append(" There is nothing here to lose.")
        }
    }
```

and beside the other private helpers:

```kotlin
    private fun hasHealth(result: RestoreResult): Boolean =
        result.workouts > 0 || result.healthDays > 0

    /** "400 meals and 50 weights", or with the health record, "…, 12 workouts and 30 days of health data". */
    private fun record(result: RestoreResult, withHealth: Boolean = hasHealth(result)): String {
        val parts = listOf(meals(result.meals), weights(result.weights)) +
            if (withHealth) listOf(workouts(result.workouts), healthDays(result.healthDays)) else emptyList()
        return parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    private fun workouts(count: Int): String = if (count == 1) "1 workout" else "$count workouts"

    private fun healthDays(count: Int): String =
        if (count == 1) "1 day of health data" else "$count days of health data"
```

- [ ] **Step 4: Pass the file's counts in `SettingsViewModel`.** Both constructions from a `backup`
  (in `exportTo` and the `incoming` of `offerRestoreFrom`) become:

```kotlin
                    RestoreResult(
                        backup.meals.size,
                        backup.weights.size,
                        backup.profile != null,
                        workouts = backup.workouts.size,
                        healthDays = backup.healthDays.size,
                    ),
```

- [ ] **Step 5: Run the wording and settings tests**

Run: `~/bin/gradlew-safe :app:testDebugUnitTest --tests "com.metaself.app.domain.backup.*" --tests "com.metaself.app.ui.screen.settings.*" > /tmp/ms-health1.log 2>&1; echo "exit $?"`
Expected: `exit 0`; every earlier `BackupWordingTest` assertion unchanged and passing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/metaself/app/ui/settings/BackupWording.kt \
  app/src/main/java/com/metaself/app/ui/screen/settings/SettingsViewModel.kt \
  app/src/test/java/com/metaself/app/domain/backup/BackupWordingTest.kt
git commit -m "feat: the restore question names the health record when there is one (D71)"
```

---

### Task 7: Version, the whole suite, CI, and the release build

**Files:**
- Modify: `app/build.gradle.kts:19,30`
- Modify: `CLAUDE.md` (Testing — the classes that skip locally)

- [ ] **Step 1: Bump the version.** `versionCode = 107` → `108`; `versionName = "0.53.0"` → `"0.54.0"`.

- [ ] **Step 2: `CLAUDE.md`.** "expect exactly these eight classes to skip" → "these nine classes",
  and add `HealthRecordDaoTest` to the list after `RoomWeightRepositoryTest`.

- [ ] **Step 3: The whole suite** (check `free -m` first)

Run: `~/bin/gradlew-safe :app:testDebugUnitTest > /tmp/ms-health1.log 2>&1; echo "exit $?"`
Expected: `exit 0`, 0 failures, and the skipped classes exactly the nine. Count them from
`app/build/test-results/testDebugUnitTest/*.xml` (`skipped=` non-zero), not by eye.

- [ ] **Step 4: Lint and the migration check**

Run: `~/bin/gradlew-safe :app:lintDebug > /tmp/ms-health1-lint.log 2>&1; echo "exit $?"` → `exit 0`,
no new issue.
Run: `python3 tools/check-migration-5-6.py; echo "exit $?"` → `OK …`, `exit 0`.

- [ ] **Step 5: The anonymisation read.** Read every added line (`git diff main --stat`, then the
  diff) as prose: comments, KDoc, test names, fixtures. Fixtures are "Running", "Strength", a night
  with invented times; nothing says how often anything happens, how long anyone sleeps, or what
  anyone's heart does. Fix and re-read if anything reads as a record of a person.

- [ ] **Step 6: Commit, push, open the pull request**

```bash
git add app/build.gradle.kts CLAUDE.md
git commit -m "0.54.0: the health record has a store (D65–D72, phase 1)"
git push -u origin the-app-keeps-a-health-record
```

The PR body says: what the store holds; the migration touches nothing else (with the Python check's
output); the backup format is now 3, a version-2 app refuses such a file, and raw readings are not in
it; no `@Upsert` and why; and **what CI must show** — `MigrationTest`, `HealthRecordDaoTest` and
`BackupRoundTripTest` actually ran and passed, and "No test skipped" is green. No session link.

- [ ] **Step 7: Wait for CI; it is the only proof of the database tests.** Do not merge on a local
  green. A database test failing in CI is fixed on the branch and pushed again.

- [ ] **Step 8: The release build** — check `free -m` shows about 6000 MB available, then
  `~/bin/ms-release` (never `assembleRelease`), and send the APK to the owner. **What there is to
  check on the phone:** the app opens after the upgrade with every meal, weight and food where it was;
  Settings → export a backup, and the file says `"version": 3` and has empty `"workouts": []`,
  `"sleep": []` and `"health_days": []`. Nothing else is visible until phase 2.
