package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 7: a workout can hold what a workout file adds (D82) — where its distance came from, its
 * steps, and where those came from.
 *
 * **Three nullable columns are added to `workouts`, and nothing else is read or written.** Every
 * existing workout keeps every figure; the new columns are null, which for `distanceSource` means
 * what it always meant: Health Connect's total over the session. No other table is touched.
 *
 * Each column definition is the exported schema's own, verbatim (`7.json`'s `createSql` for
 * `workouts`): Room validates the finished table against that file. `tools/check-migration-6-7.py`
 * proves the statements and the finished shape on this machine; `MigrationTest` validates in CI.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `workouts` ADD COLUMN `distanceSource` TEXT")
        db.execSQL("ALTER TABLE `workouts` ADD COLUMN `steps` INTEGER")
        db.execSQL("ALTER TABLE `workouts` ADD COLUMN `stepsSource` TEXT")
    }
}
