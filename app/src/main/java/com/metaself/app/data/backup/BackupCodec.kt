package com.metaself.app.data.backup

import com.metaself.app.domain.backup.Backup
import kotlinx.serialization.json.Json

/**
 * The backup file's format, as a pure function both ways.
 *
 * Indented on purpose. This is the one file the owner is expected to be able to open and read, and
 * a single line of dense JSON is not that.
 */
object BackupCodec {

    private val json = Json {
        prettyPrint = true
        // A file written by an OLDER version will be missing fields this one knows about; they take
        // their defaults. A file with fields this one does NOT know is a file from the future, and
        // is caught by the version check rather than by silently dropping what it cannot place.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    /**
     * Null for anything that cannot be read whole — rubbish, a truncated file, or one written by a
     * later version of the app. No partial restore: a half-restored record is worse than a failed
     * one, because a failure is visible and a half is not.
     */
    fun decode(text: String): Backup? {
        val backup = runCatching { json.decodeFromString(Backup.serializer(), text) }.getOrNull()
            ?: return null
        return backup.takeIf { it.version in 1..Backup.CURRENT_VERSION }
    }
}
