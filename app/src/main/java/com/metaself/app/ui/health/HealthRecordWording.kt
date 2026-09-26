package com.metaself.app.ui.health

import com.metaself.app.domain.health.HealthKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What Settings says about the health record (D65–D71). Counts and dates, never reassurance. */
object HealthRecordWording {

    const val NOT_BACKED_UP =
        "Detailed readings are kept on this phone only; the daily backup has the summaries."

    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Locale.UK)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)

    /**
     * @param zone the phone's own zone, to decide "today" for the year comparison below; tests pass a
     *   fixed zone so the figure stays reproducible.
     */
    fun status(
        days: Int,
        earliest: LocalDate?,
        lastCopiedMillis: Long?,
        nowMillis: Long,
        catchingUp: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (days == 0 || earliest == null) return "Health record: nothing copied yet."
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val format = if (earliest.year != today.year) DAY_MONTH_YEAR else DAY_MONTH
        return buildString {
            append("Health record: ")
            append(if (days == 1) "1 day" else "$days days")
            append(", from ").append(earliest.format(format))
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

    /**
     * A choice: more names than this reads as a list, not a sentence, so the count is given instead.
     */
    private const val MOST_NAMED = 3

    /**
     * Phrased as "Reading X is not allowed", not "X is/are not allowed", so one wording serves a
     * single name and several without asking each [HealthKind] whether its display name is singular
     * or plural.
     */
    fun notAllowed(kinds: Set<HealthKind>): String? {
        if (kinds.isEmpty()) return null
        if (kinds.size > MOST_NAMED) return "${kinds.size} kinds of health data are not allowed — tap Connect to allow them."
        val names = kinds.sortedBy { it.ordinal }.map { it.displayName.replaceFirstChar { c -> c.lowercase() } }
        val list = if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
        val pronoun = if (names.size == 1) "it" else "them"
        return "Reading $list is not allowed — tap Connect to allow $pronoun."
    }

    private fun plural(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"
}
