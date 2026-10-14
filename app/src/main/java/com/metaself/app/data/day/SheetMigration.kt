package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 14: the sheet of what the trainer knows about the owner, every version of it (D150, D151).
 *
 * **One table is created, and nothing else is read or written.** The statement is the exported schema's own,
 * verbatim (`14.json`). `tools/check-migration-13-14.py` proves it on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trainer_sheets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`atMillis` INTEGER NOT NULL, `facts` TEXT NOT NULL, `madeBy` TEXT NOT NULL, `model` TEXT, " +
                "`restoredFrom` INTEGER)",
        )
    }
}
