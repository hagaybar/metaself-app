package com.metaself.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.backup.BackupSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What an automatic backup did, so the screen can say something true rather than reassuring. */
sealed interface BackupOutcome {

    data class Written(val fileName: String, val deleted: Int) : BackupOutcome

    /** The folder is gone, renamed, or its permission was revoked. */
    data object FolderUnreachable : BackupOutcome

    data class Failed(val reason: String) : BackupOutcome
}

/**
 * Writing the backup into a folder the owner chose.
 *
 * Android's directory picker grants a permission that survives a reboot, which is the whole reason
 * this works with no Cloud project, no OAuth client and no secret in the app. If he points it at a
 * Drive folder, the copy lands in Drive; if he points it at a card, it lands on the card. The app
 * does not know or care which, and that is the feature.
 *
 * It only ever deletes files it named itself. The folder belongs to the owner, and a backup routine
 * that tidies up other people's files is a data-loss bug waiting for the wrong folder to be picked.
 */
@Singleton
class BackupFolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val problems: ProblemLog,
) {

    /**
     * Hold on to the folder across restarts.
     *
     * Without this the permission dies with the process and automatic backup works exactly once,
     * which is worse than not offering it.
     */
    fun remember(uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    }.onFailure { error ->
        problems.record(
            kind = "backup",
            detail = "could not keep the folder: ${error::class.java.simpleName} ${error.message}",
        )
    }.getOrDefault(false)

    suspend fun write(
        folderUri: Uri,
        fileName: String,
        contents: String,
    ): BackupOutcome = withContext(Dispatchers.IO) {
        val folder = runCatching { DocumentFile.fromTreeUri(context, folderUri) }.getOrNull()
        if (folder == null || !folder.isDirectory || !folder.canWrite()) {
            return@withContext BackupOutcome.FolderUnreachable
        }

        runCatching {
            // Replace today's file rather than accumulating "metaself-2026-09-04 (1).json".
            folder.findFile(fileName)?.delete()

            val file = folder.createFile(MIME_TYPE, fileName)
                ?: return@withContext BackupOutcome.Failed("the file could not be created")

            context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.write(contents.toByteArray())
            } ?: return@withContext BackupOutcome.Failed("the file could not be opened")

            BackupOutcome.Written(fileName, deleted = prune(folder))
        }.getOrElse { error ->
            problems.record(
                kind = "backup",
                detail = "automatic backup failed: ${error::class.java.simpleName} " +
                    "${error.message}",
            )
            BackupOutcome.Failed(error::class.java.simpleName)
        }
    }

    /** How many of the app's own old files were removed. */
    private fun prune(folder: DocumentFile): Int {
        val byName = folder.listFiles().mapNotNull { file -> file.name?.let { it to file } }.toMap()
        val doomed = BackupSchedule.filesToDelete(byName.keys.toList())
        return doomed.count { name -> byName[name]?.delete() == true }
    }

    private companion object {
        const val MIME_TYPE = "application/json"
    }
}
