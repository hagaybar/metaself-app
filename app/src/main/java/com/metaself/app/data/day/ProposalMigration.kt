package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 19: a reply can name a change, waiting for Confirm (D164).
 *
 * One nullable column is added to `trainer_messages`, as `19.json` declares it: every message written before this has
 * none, and shows no card. **No other table is touched and no message changes.**
 * `tools/check-migration-18-19.py` proves it on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `trainer_messages` ADD COLUMN `proposal` TEXT")
    }
}
