package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 11: the owner's answers to "count it for this?" about a shorter session and a weekly plan (D105).
 *
 * **One table is created, and nothing else is read or written.** No plan, workout or review is touched.
 * The statement is the exported schema's own, verbatim (`11.json`): Room validates the finished database
 * against that file. `tools/check-migration-10-11.py` proves the statement and the finished shape on this
 * machine; `MigrationTest` validates in CI.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `plan_confirmations` (" +
                "`programmeId` INTEGER NOT NULL, `workoutId` INTEGER NOT NULL, `confirmed` INTEGER NOT NULL, " +
                "`answeredAtMillis` INTEGER NOT NULL, PRIMARY KEY(`programmeId`, `workoutId`))",
        )
    }
}
