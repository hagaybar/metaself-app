package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 8: the trainer's plans and the owner's reviews of his sessions (D88).
 *
 * **Two tables and one index are created, and nothing else is read or written.** No workout, meal,
 * weight or anything else is touched. Every statement is the exported schema's own, verbatim
 * (`8.json`): Room validates the finished database against that file. `tools/check-migration-7-8.py`
 * proves the statements and the finished shape on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trainer_plans` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                "`activity` TEXT NOT NULL, `minutes` INTEGER NOT NULL, `feeling` TEXT NOT NULL, " +
                "`wish` TEXT NOT NULL, `words` TEXT, `suggestion` TEXT NOT NULL, `model` TEXT NOT NULL, " +
                "`kept` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trainer_reviews` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workoutId` INTEGER NOT NULL, " +
                "`planId` INTEGER, `felt` TEXT, `words` TEXT, `feedback` TEXT, " +
                "`feedbackAtMillis` INTEGER, `model` TEXT)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_trainer_reviews_workoutId` " +
                "ON `trainer_reviews` (`workoutId`)",
        )
    }
}
