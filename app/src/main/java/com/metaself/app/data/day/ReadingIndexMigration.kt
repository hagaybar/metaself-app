package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 15: two indexes on `health_readings`, and nothing else (0.86.1).
 *
 * - `recordId` alone: a record Health Connect reports deleted comes with its id and no origin, so
 *   `HealthReadingDao.daysOf(recordId)` and `deleteByRecordId` could use neither existing index and
 *   scanned the whole table, once each per deleted id, inside the copy's write transaction.
 * - `epochDay` alone: `HealthReadingDao.onDay`, read once per day summarised, walked the whole
 *   `(kind, epochDay, startMillis)` index, as `kind` is not constrained.
 *
 * The table grows every day, so both grew slower every day, and every other write — and every
 * `@Transaction` read — waited behind them. **No row is read or written.** The statements are the
 * exported schema's own, verbatim (`15.json`). `tools/check-migration-14-15.py` proves them, and that
 * the queries now use them, on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_readings_recordId` ON `health_readings` (`recordId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_readings_epochDay` ON `health_readings` (`epochDay`)")
    }
}
