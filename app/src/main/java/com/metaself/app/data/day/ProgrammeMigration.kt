package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 10: the trainer's weekly plans, each with its evaluation (D98).
 *
 * **One table is created, and nothing else is read or written.** No workout, review, plan or anything
 * else is touched. The statement is the exported schema's own, verbatim (`10.json`): Room validates the
 * finished database against that file. `tools/check-migration-9-10.py` proves the statement and the
 * finished shape on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trainer_programmes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                "`weeks` INTEGER NOT NULL, `perWeek` INTEGER NOT NULL, `words` TEXT, `evaluation` TEXT, " +
                "`plan` TEXT NOT NULL, `model` TEXT NOT NULL, `startEpochDay` INTEGER, `status` TEXT NOT NULL, " +
                "`stoppedEpochDay` INTEGER, `replacesId` INTEGER)",
        )
    }
}
