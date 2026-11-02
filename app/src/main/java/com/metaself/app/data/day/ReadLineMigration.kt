package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 18: a reply says what it read (D163).
 *
 * One nullable column is added to `trainer_messages`, as `18.json` declares it: every message written before this has
 * none, and shows no Read line. **No other table is touched and no message changes.**
 * `tools/check-migration-17-18.py` proves it on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `trainer_messages` ADD COLUMN `opened` TEXT")
    }
}
