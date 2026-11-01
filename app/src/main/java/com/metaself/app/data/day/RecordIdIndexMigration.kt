package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 16: four indexes, two on `sleep_sessions` and two on `workouts`, and nothing else (D153).
 *
 * - `sleep_sessions.recordId` and `workouts.originId` alone: a record Health Connect reports deleted
 *   comes with its id and no origin, so `SleepDao.daysOf(recordId)`, `deleteByRecordId`,
 *   `WorkoutDao.daysOfSynced` and `deleteSynced` could use neither table's `(origin, id)` index and
 *   scanned the table — four scans per deleted id, of any kind, inside the copy's write transaction.
 * - `sleep_sessions.startMillis` and `workouts.startedAtMillis`: the window re-read's
 *   `SleepDao.daysBetween`, `deleteBetween` and `WorkoutDao.visibleSyncedBetween` select by start time
 *   and scanned the table once per re-read.
 *
 * Both tables grow by a row or so a day, so each scan grew slower every day, the way 0.86.1's
 * `health_readings` ones did. **No row is read or written.** The statements are the exported schema's
 * own, verbatim (`16.json`). `tools/check-migration-15-16.py` proves them, and that the queries now use
 * them, on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_originId` ON `workouts` (`originId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_startedAtMillis` ON `workouts` (`startedAtMillis`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_sessions_recordId` ON `sleep_sessions` (`recordId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_sessions_startMillis` ON `sleep_sessions` (`startMillis`)")
    }
}
