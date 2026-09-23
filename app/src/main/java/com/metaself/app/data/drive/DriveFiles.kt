package com.metaself.app.data.drive

import com.metaself.app.domain.backup.BackupSchedule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One file this app has put in the owner's Drive. */
data class DriveFile(val id: String, val name: String)

/**
 * Talking to Drive's REST API, as pure text in and out.
 *
 * Kept apart from the HTTP so that the interesting half — what a reply means, what gets sent, which
 * files should go — is testable without a network or a Google account, neither of which exists on
 * the build machine.
 */
object DriveFiles {

    const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
    const val FILES_URL = "https://www.googleapis.com/drive/v3/files"

    /**
     * The scope asked for, and the narrowest one that works.
     *
     * `drive.file` grants access ONLY to files this app itself created. It cannot see, list, open or
     * modify anything else in the owner's Drive — and the listing below is therefore of this app's
     * own backups and nothing else, which is what makes pruning them safe.
     */
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    private val json = Json { ignoreUnknownKeys = true }

    /** Search for this app's own backups. With `drive.file` there is nothing else to find. */
    fun listQuery(): String = "name contains 'metaself-' and trashed = false"

    /** The metadata half of the multipart upload: a name, and nothing else. */
    fun metadataFor(fileName: String): String = """{"name":"$fileName"}"""

    /** File ids and names out of a listing, or an empty list for anything unreadable. */
    fun readListing(body: String): List<DriveFile> = runCatching {
        json.parseToJsonElement(body).jsonObject["files"]?.jsonArray.orEmpty().mapNotNull { entry ->
            val file = entry.jsonObject
            val id = file["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = file["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            DriveFile(id, name)
        }
    }.getOrDefault(emptyList())

    /** The id of a file already holding today's name, so a second copy is replaced not duplicated. */
    fun existing(files: List<DriveFile>, fileName: String): String? =
        files.firstOrNull { it.name == fileName }?.id

    /**
     * Which of this app's own files should go, oldest first.
     *
     * The same rule the folder backup uses, and the same guarantee: **only files this app named.**
     * `drive.file` means nothing else is even visible, but the name check stays anyway — a backup
     * routine that deletes what it did not write is a data-loss bug waiting for a wrong assumption.
     */
    fun toDelete(files: List<DriveFile>): List<DriveFile> = files
        .filter { BackupSchedule.isBackupFile(it.name) }
        .sortedBy { it.name }
        .dropLast(BackupSchedule.KEEP_FILES)
}
