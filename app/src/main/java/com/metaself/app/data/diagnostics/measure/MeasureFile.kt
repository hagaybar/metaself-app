package com.metaself.app.data.diagnostics.measure

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Where the measuring lines go (D152). Called from the recorder's own thread only. */
interface MeasureSink {
    fun append(lines: List<String>)
    fun copyTo(out: OutputStream)

    companion object {
        val NONE: MeasureSink = object : MeasureSink {
            override fun append(lines: List<String>) = Unit
            override fun copyTo(out: OutputStream) = Unit
        }
    }
}

/**
 * The measuring file, as two halves in [dir] — the app's private files, excluded from Android's backup
 * and read by nothing but Download diagnostics (D152). Together they hold at most [capBytes]: when the
 * newer half would pass half of it, it becomes the older half and the old older half is deleted, so
 * the oldest lines go first and a line is never cut.
 */
class MeasureFile(private val dir: File, private val capBytes: Long = CAP_BYTES) : MeasureSink {

    private val newer get() = File(dir, NEWER)
    private val older get() = File(dir, OLDER)

    override fun append(lines: List<String>) {
        if (lines.isEmpty()) return
        dir.mkdirs()
        val bytes = lines.joinToString(separator = "\n", postfix = "\n").toByteArray()
        if (newer.length() + bytes.size > capBytes / 2) {
            older.delete()
            newer.renameTo(older)
        }
        // A batch larger than a half on its own is kept to its newest lines.
        val kept = if (bytes.size > capBytes / 2) newestWithin(lines, capBytes / 2) else bytes
        FileOutputStream(newer, true).use { it.write(kept) }
    }

    /** Older half first, so the lines come out oldest first. */
    override fun copyTo(out: OutputStream) {
        listOf(older, newer).filter { it.exists() }.forEach { file -> file.inputStream().use { it.copyTo(out) } }
    }

    /** Both halves' size together: what the cap is held to. */
    fun size(): Long = older.length() + newer.length()

    private fun newestWithin(lines: List<String>, limit: Long): ByteArray {
        val kept = ArrayDeque<String>()
        var size = 0L
        for (line in lines.asReversed()) {
            val next = line.toByteArray().size + 1
            if (size + next > limit) break
            kept.addFirst(line)
            size += next
        }
        return kept.joinToString(separator = "\n", postfix = "\n").toByteArray()
    }

    companion object {
        /** D152: 5 MB, the two halves together. */
        const val CAP_BYTES = 5L * 1024 * 1024
        const val NEWER = "measure.jsonl"
        const val OLDER = "measure.older.jsonl"
        /** The directory under the app's files, as the backup rules name it. */
        const val DIRECTORY = "measure"
    }
}
