package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 9: the pairs the owner split with "These are two sessions" (D92).
 *
 * **One table is created, and nothing else is read or written.** No workout, review or anything else is
 * touched: overlapping workouts are combined as the record is read, never in storage. The statement is
 * the exported schema's own, verbatim (`9.json`): Room validates the finished database against that
 * file. `tools/check-migration-8-9.py` proves the statement and the finished shape on this machine;
 * `MigrationTest` validates in CI.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_splits` (" +
                "`firstWorkoutId` INTEGER NOT NULL, `secondWorkoutId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`firstWorkoutId`, `secondWorkoutId`))",
        )
    }
}
