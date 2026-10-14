package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 13: the conversation with the trainer (D148).
 *
 * **One table is created, and nothing else is read or written.** The statement is the exported schema's own,
 * verbatim (`13.json`). `tools/check-migration-12-13.py` proves it on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trainer_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`atMillis` INTEGER NOT NULL, `fromOwner` INTEGER NOT NULL, `text` TEXT NOT NULL, `model` TEXT, " +
                "`answered` INTEGER NOT NULL, `replyTo` INTEGER)",
        )
    }
}
