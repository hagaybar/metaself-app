package com.metaself.app.data.diagnostics.measure

/** D152: one [MeasureEvent] as one JSON line, and SQL text made safe to keep. */
object MeasureJson {

    /** The longest SQL text kept; the app's longest statements are well under it. */
    const val MAX_SQL = 600

    fun line(event: MeasureEvent): String = buildString {
        append("{\"type\":")
        string(event.type)
        append(",\"at\":").append(event.atMillis)
        val fields = event.fields
        var i = 0
        while (i < fields.size) {
            append(',')
            string(fields[i] as String)
            append(':')
            value(fields[i + 1])
            i += 2
        }
        append('}')
    }

    /** Any object as one JSON line: the diagnostics file's header ([DiagnosticsHeader]). */
    fun objectLine(fields: Map<String, Any?>): String = buildString { value(fields) }

    /**
     * The SQL as compiled, with every quoted literal that is not a constant name replaced by `?`.
     *
     * The app binds every value it reads or writes, so a statement's text holds table and column names
     * and the app's own constants (`'SYNCED'`, `'DISTANCE'`). This is the belt to that braces: a quoted
     * literal is kept only when it is an upper-case name of at most 32 letters and underscores; any
     * other — a word, a sentence, a number in quotes — becomes `?`, so no value from the record can
     * reach the file through a statement written with one inline. Whitespace is collapsed.
     */
    fun scrubSql(sql: String): String {
        val scrubbed = LITERAL.replace(sql) { match ->
            if (CONSTANT.matches(match.value)) match.value else "?"
        }
        val oneLine = WHITESPACE.replace(scrubbed, " ").trim()
        return if (oneLine.length > MAX_SQL) oneLine.take(MAX_SQL) + "…" else oneLine
    }

    private val LITERAL = Regex("'(?:[^']|'')*'")
    private val CONSTANT = Regex("'[A-Z_]{1,32}'")
    private val WHITESPACE = Regex("\\s+")

    private fun StringBuilder.value(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> string(value)
            is Boolean -> append(value)
            is Double -> append(if (value.isFinite()) value.toString() else "null")
            is Float -> append(if (value.isFinite()) value.toString() else "null")
            is Number -> append(value)
            is MeasureEvent.Millis -> millis(value.nanos)
            is MeasureEvent.Sql -> string(scrubSql(value.text))
            is MeasureEvent.Statement -> {
                append("{\"sql\":")
                string(scrubSql(value.sql))
                append(",\"count\":").append(value.count)
                append(",\"total_ms\":")
                millis(value.totalNanos)
                append(",\"longest_ms\":")
                millis(value.longestNanos)
                append('}')
            }
            is Map<*, *> -> {
                append('{')
                var first = true
                value.forEach { (k, v) ->
                    if (!first) append(',')
                    first = false
                    string(k.toString())
                    append(':')
                    value(v)
                }
                append('}')
            }
            is List<*> -> {
                append('[')
                value.forEachIndexed { index, v ->
                    if (index > 0) append(',')
                    value(v)
                }
                append(']')
            }
            else -> string(value.toString())
        }
    }

    private fun StringBuilder.millis(nanos: Long) {
        append(String.format(java.util.Locale.ROOT, "%.2f", nanos / 1_000_000.0))
    }

    private fun StringBuilder.string(text: String) {
        append('"')
        for (c in text) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append(String.format(java.util.Locale.ROOT, "\\u%04x", c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
