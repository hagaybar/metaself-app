package com.metaself.app.data.diagnostics

import java.io.File

/**
 * The problem log, as one small file.
 *
 * A plain file written synchronously rather than a database or a DataStore, for one reason: a crash
 * has to be recorded by a process that is about to die. Anything asynchronous loses the very event
 * that matters most.
 *
 * Capped at [LIMIT] entries. A log that grows for ever eventually costs something, and nothing here
 * is worth keeping for months.
 */
class FileProblemLog(private val file: File) : ProblemLog {

    override fun recent(): List<Problem> = runCatching {
        if (!file.exists()) return emptyList()
        file.readLines()
            .mapNotNull { line ->
                val parts = line.split(SEPARATOR, limit = 3)
                if (parts.size < 3) {
                    null
                } else {
                    parts[0].toLongOrNull()?.let { Problem(it, parts[1], parts[2]) }
                }
            }
            .asReversed()
    }.getOrDefault(emptyList())

    @Synchronized
    override fun record(kind: String, detail: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val entry = listOf(
                System.currentTimeMillis().toString(),
                kind.oneLine(),
                detail.oneLine().take(MAX_DETAIL),
            ).joinToString(SEPARATOR)

            val kept = (file.takeIf { it.exists() }?.readLines() ?: emptyList()) + entry
            file.writeText(kept.takeLast(LIMIT).joinToString("\n"))
        }
        // A failure to record a failure is not worth a failure. Swallowed deliberately.
    }

    override fun clear() {
        runCatching { file.delete() }
    }

    /** Newlines and separators would split one entry into several unreadable ones. */
    private fun String.oneLine(): String =
        replace(SEPARATOR, " ").replace(Regex("\\s+"), " ").trim()

    private companion object {
        /** A unit separator: it cannot appear in a stack trace or a provider's message. */
        const val SEPARATOR = "\u001F"
        const val LIMIT = 50
        const val MAX_DETAIL = 400
    }
}
