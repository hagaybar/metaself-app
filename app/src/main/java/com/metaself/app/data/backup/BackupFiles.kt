package com.metaself.app.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reading and writing the file the owner chose, behind an interface so the view model can be tested.
 *
 * Android's own document picker hands back a URI it has already granted access to, which is why
 * this app asks for no storage permission at all and why the owner puts the file wherever he already
 * keeps things — a folder, Drive, a memory card — rather than wherever the app decided.
 */
interface BackupFiles {

    suspend fun read(uri: Uri): String?

    suspend fun write(uri: Uri, text: String): Boolean
}

@Singleton
class ContentResolverBackupFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupFiles {

    override suspend fun read(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    /**
     * Truncated before writing.
     *
     * Without "wt" the picker reuses an existing file's length, so exporting a smaller record over
     * a larger one leaves the tail of the old file behind and produces something that is not valid
     * JSON and not obviously broken either.
     */
    override suspend fun write(uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
        true
    }.getOrDefault(false)
}
