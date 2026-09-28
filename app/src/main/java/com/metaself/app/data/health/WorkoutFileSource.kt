package com.metaself.app.data.health

import android.content.Context
import androidx.core.net.toUri
import com.metaself.app.domain.movement.WorkoutFileRefusal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject

/** A workout file's text, or why it was not read. */
sealed interface FileText {
    data class Text(val text: String) : FileText
    data class Refused(val reason: WorkoutFileRefusal) : FileText
}

/** Where a workout file's text comes from (D82). Throws when the file cannot be opened. */
fun interface WorkoutFileSource {
    suspend fun read(uri: String): FileText
}

/**
 * Marks text shared inline (a share with no attached stream, `Intent.EXTRA_TEXT` instead) so it can
 * travel the same String "uri" the rest of the D82 pipeline passes around, and
 * [ContentWorkoutFileSource] reads it back as text in hand rather than opening it through the content
 * resolver. Never a real content Uri.
 */
private const val INLINE_TEXT_PREFIX = "metaself-inline-workout-text:"

/** Wraps [text] shared inline so it reads as itself, not as a Uri to open. */
fun inlineWorkoutText(text: String): String = INLINE_TEXT_PREFIX + text

/**
 * A shared or picked file's text through the content resolver, as UTF-8 — or, for text shared inline
 * ([inlineWorkoutText]), the text itself. Whatever type the file arrived as, only its content decides
 * (`TcxReader`). Either way, anything over [MAX_BYTES] is refused without reading the rest.
 */
class ContentWorkoutFileSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkoutFileSource {

    override suspend fun read(uri: String): FileText = withContext(Dispatchers.IO) {
        if (uri.startsWith(INLINE_TEXT_PREFIX)) return@withContext inlineText(uri.removePrefix(INLINE_TEXT_PREFIX))
        val stream = context.contentResolver.openInputStream(uri.toUri())
            ?: throw IOException("no stream for the file")
        stream.use { input ->
            // Read by hand: InputStream.readNBytes is API 33, above this app's minimum.
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (out.size() <= MAX_BYTES) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            if (out.size() > MAX_BYTES) FileText.Refused(WorkoutFileRefusal.TOO_LARGE)
            else FileText.Text(out.toString(Charsets.UTF_8.name()))
        }
    }

    private fun inlineText(text: String): FileText =
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) FileText.Refused(WorkoutFileRefusal.TOO_LARGE)
        else FileText.Text(text)

    companion object {
        /**
         * A choice, not a measurement: 5 MB. A session-totals file is a few kilobytes; even a file with
         * second-by-second points for a long session is far below this.
         */
        const val MAX_BYTES = 5 * 1024 * 1024

        private const val BUFFER = 8 * 1024
    }
}
