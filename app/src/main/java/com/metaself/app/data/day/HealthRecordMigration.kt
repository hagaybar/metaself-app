package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 6: the health record (D65, D68).
 *
 * **Eight tables and their indices are created, and nothing else is read or written.** No meal,
 * item, food, weight or packet is touched; every day the owner has logged is worth after this exactly
 * what it is worth now. `tools/check-migration-5-6.py` proves that on this machine and
 * `MigrationTest` validates the finished tables against the exported schema in CI.
 *
 * Every statement is the exported schema's own, verbatim: Room validates the finished database
 * against that file, and a hand-typed difference in a default or a null fails validation for a reason
 * that takes an afternoon to find.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_readings` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                "`startMillis` INTEGER NOT NULL, `endMillis` INTEGER, `value` REAL NOT NULL, " +
                "`unit` TEXT NOT NULL, `origin` TEXT NOT NULL, `recordId` TEXT NOT NULL, " +
                "`sampleIndex` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_health_readings_kind_epochDay_startMillis` " +
                "ON `health_readings` (`kind`, `epochDay`, `startMillis`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_health_readings_origin_recordId_sampleIndex` " +
                "ON `health_readings` (`origin`, `recordId`, `sampleIndex`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `workouts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                "`startedAtMillis` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `title` TEXT, `distanceM` INTEGER, `energyKcal` INTEGER, " +
                "`energySource` TEXT NOT NULL, `effort` TEXT, `source` TEXT NOT NULL, " +
                "`origin` TEXT, `originId` TEXT, `hidden` INTEGER NOT NULL DEFAULT 0, `note` TEXT, " +
                "`avgHeartRate` INTEGER, `maxHeartRate` INTEGER, `zoneSeconds` TEXT, " +
                "`zoneMaxSource` TEXT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_workouts_epochDay` ON `workouts` (`epochDay`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_workouts_origin_originId` " +
                "ON `workouts` (`origin`, `originId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sleep_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, " +
                "`startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, " +
                "`origin` TEXT NOT NULL, `recordId` TEXT NOT NULL, `title` TEXT)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sleep_sessions_epochDay` ON `sleep_sessions` (`epochDay`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_sessions_origin_recordId` " +
                "ON `sleep_sessions` (`origin`, `recordId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sleep_stages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                "`stage` TEXT NOT NULL, `startMillis` INTEGER NOT NULL, `endMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sleep_sessions`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sleep_stages_sessionId` ON `sleep_stages` (`sessionId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_days` (" +
                "`epochDay` INTEGER NOT NULL, `computedAtMillis` INTEGER NOT NULL, " +
                "`steps` INTEGER, `stepsSource` TEXT, `distanceM` INTEGER, `distanceSource` TEXT, " +
                "`activeKcal` INTEGER, `activeKcalSource` TEXT, `totalKcal` INTEGER, " +
                "`totalKcalSource` TEXT, `restingHeartRate` INTEGER, `restingHeartRateSource` TEXT, " +
                "`avgHeartRate` INTEGER, `avgHeartRateSource` TEXT, `hrvMs` REAL, `hrvSource` TEXT, " +
                "`oxygenPct` REAL, `oxygenSource` TEXT, `respiratoryRate` REAL, " +
                "`respiratoryRateSource` TEXT, `sleepMinutes` INTEGER, `deepMinutes` INTEGER, " +
                "`lightMinutes` INTEGER, `remMinutes` INTEGER, `awakeMinutes` INTEGER, " +
                "`sleepSource` TEXT, `workoutCount` INTEGER, `workoutMinutes` INTEGER, " +
                "`workoutSource` TEXT, PRIMARY KEY(`epochDay`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `movement_corrections` (" +
                "`epochDay` INTEGER NOT NULL, `steps` INTEGER, `activeKcal` INTEGER, " +
                "`setAtMillis` INTEGER NOT NULL, `note` TEXT, PRIMARY KEY(`epochDay`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_sync` (" +
                "`kind` TEXT NOT NULL, `changesToken` TEXT, `tokenAtMillis` INTEGER, " +
                "`catchUpCursorMillis` INTEGER, `catchUpDone` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`kind`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `archive_months` (" +
                "`month` TEXT NOT NULL, `changedAtMillis` INTEGER NOT NULL, " +
                "`writtenAtMillis` INTEGER, PRIMARY KEY(`month`))",
        )
    }
}
