package com.metaself.app.data.day

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 17: each message says which conversation it is in (D159).
 *
 * One column is added to `trainer_messages`, as `17.json` declares it. Each message written before this goes in its
 * local day's conversation, and a reply goes wherever the message it answers went, whichever day it arrived; a reply
 * whose message is gone goes in its own day's. `Conversation.byDay` does the same for an older backup file. **No other
 * table is touched and no message's words change.** `tools/check-migration-16-17.py` proves it on this machine;
 * `MigrationTest` validates in CI.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `trainer_messages` ADD COLUMN `conversation` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "UPDATE `trainer_messages` SET `conversation` = 'day:' || date(`atMillis` / 1000, 'unixepoch', 'localtime')",
        )
        db.execSQL(
            "UPDATE `trainer_messages` SET `conversation` = (SELECT `answered_message`.`conversation` FROM `trainer_messages` AS `answered_message` " +
                "WHERE `answered_message`.`id` = `trainer_messages`.`replyTo`) " +
                "WHERE `replyTo` IN (SELECT `id` FROM `trainer_messages`)",
        )
    }
}
