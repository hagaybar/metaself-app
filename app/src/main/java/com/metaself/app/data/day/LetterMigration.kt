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
