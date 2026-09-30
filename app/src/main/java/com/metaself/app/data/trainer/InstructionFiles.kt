package com.metaself.app.data.trainer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * D106: the instructions file picked with Android's document picker, read or written as plain text — so a
 * file on Drive works, and the app needs no storage permission. Uris are strings so the page's view model
 * is tested without Android. Nothing here keeps a copy.
 */
interface InstructionFiles {
    suspend fun read(uri: String): String?
    suspend fun write(uri: String, text: String): Boolean
    suspend fun nameOf(uri: String): String?
}

@Singleton
class ContentResolverInstructionFiles @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstructionFiles {

    override suspend fun read(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes().decodeToString() } }.getOrNull()
    }

    /** Truncated first ("wt"), as the backup export is, so a shorter text leaves no tail behind. */
    override suspend fun write(uri: String, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { it.write(text.toByteArray()) } != null
        }.getOrDefault(false)
    }

    override suspend fun nameOf(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }
}
