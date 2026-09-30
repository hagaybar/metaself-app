package com.metaself.app.data.trainer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

    /** Read, capped at [MAX_BYTES]; a bigger file reads as null rather than being loaded whole. */
    override suspend fun read(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                val out = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = stream.read(chunk)
                    if (n == -1) break
                    total += n
                    if (total > MAX_BYTES) return@use null
                    out.write(chunk, 0, n)
                }
                out.toByteArray().decodeToString()
            }
        }.getOrNull()
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

    private companion object {
        const val MAX_BYTES = 1_048_576
    }
}
