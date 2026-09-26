package com.metaself.app.ui.health

import com.metaself.app.domain.health.HealthKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What Settings says about the health record (D65–D71). Counts and dates, never reassurance. */
object HealthRecordWording {

    const val NOT_BACKED_UP =
        "Detailed readings are kept on this phone only; the daily backup has the summaries."

    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Locale.UK)

    fun status(days: Int, earliest: LocalDate?, lastCopiedMillis: Long?, nowMillis: Long, catchingUp: Boolean): String {
        if (days == 0 || earliest == null) return "Health record: nothing copied yet."
        return buildString {
            append("Health record: ")
            append(if (days == 1) "1 day" else "$days days")
            append(", from ").append(earliest.format(DAY_MONTH))
            lastCopiedMillis?.let { append(" · last copied ").append(ago(it, nowMillis)) }
            if (catchingUp) append(" · still catching up")
        }
    }

    fun ago(thenMillis: Long, nowMillis: Long): String {
        val minutes = (nowMillis - thenMillis) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> plural(minutes, "minute") + " ago"
            minutes < 24 * 60 -> plural(minutes / 60, "hour") + " ago"
            else -> plural(minutes / (24 * 60), "day") + " ago"
        }
    }

    fun notAllowed(kinds: Set<HealthKind>): String? {
        if (kinds.isEmpty()) return null
        if (kinds.size == 1) return "${kinds.single().displayName} is not allowed — tap Connect to allow it."
        if (kinds.size > 3) return "${kinds.size} kinds of health data are not allowed — tap Connect to allow them."
        val names = kinds.sortedBy { it.ordinal }.map { it.displayName }
        val listed = names.dropLast(1).joinToString(", ") + " and " + names.last().replaceFirstChar { it.lowercase() }
        return "$listed are not allowed — tap Connect to allow them."
    }

    private fun plural(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"
}
