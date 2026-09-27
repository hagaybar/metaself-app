package com.metaself.app.ui.health

import com.metaself.app.data.health.ArchiveRestore
import com.metaself.app.data.health.ArchiveWrite
import com.metaself.app.domain.health.HealthKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What Settings says about the health record (D65–D71). Counts and dates, never reassurance. */
object HealthRecordWording {

    /** Where the raw readings are kept (D71): Drive, a file a month, when Drive backup is on. */
    fun detailedBackup(driveOn: Boolean): String = if (driveOn) {
        "Detailed readings are copied to your Drive, one file a month."
    } else {
        "Detailed readings are kept on this phone only while Drive backup is off; the daily backup has the summaries."
    }

    /**
     * The sentence "Copy to Drive now" adds about the month files (D71). Anything that left a month
     * unsent points to the problem log, where [ArchiveWrite] always leaves the reason.
     */
    fun archiveSent(write: ArchiveWrite): String = when {
        write is ArchiveWrite.Sent && write.failed == 0 && write.written > 0 ->
            "Also sent ${plural(write.written.toLong(), "month")} of detailed readings."
        write == ArchiveWrite.NothingDue || write == ArchiveWrite.Sent(written = 0, failed = 0) ->
            "The detailed readings in Drive were already up to date."
        write is ArchiveWrite.Sent && write.failed > 0 ->
            "Sent ${plural(write.written.toLong(), "month")} of detailed readings; " +
                "${plural(write.failed.toLong(), "month")} could not be sent — Recent problems says why."
        write == ArchiveWrite.Busy -> "The detailed readings are already being sent."
        else -> "The detailed readings could not be sent this time; Recent problems says why."
    }

    /** Bringing the months back could not start: Drive did not answer. */
    const val NOT_BROUGHT_BACK = "Drive could not be reached; the detailed readings were not brought back."

    /** Asked from the Drive controls, and Drive answered with no months. */
    const val NONE_IN_DRIVE = "No detailed readings were found in Drive."

    /** The question after a restore, or asked from the Drive controls, when Drive holds month files. */
    fun offerMonths(months: Int): String =
        "Also bring back ${plural(months.toLong(), "month")} of detailed readings from Drive?"

    /**
     * What bringing the months back did. A file that could not be downloaded is said apart from one
     * that could not be read: the first may come another time, the second will not.
     */
    fun broughtBack(result: ArchiveRestore): String = buildString {
        append("Brought back ").append(plural(result.months.toLong(), "month")).append(": ")
        if (result.readings == 0) {
            append("nothing new — the phone already had every reading.")
        } else {
            append(if (result.readings == 1) "1 reading" else "${number(result.readings)} readings").append(".")
        }
        if (result.unreachable > 0) append(" ").append(files(result.unreachable)).append(" could not be downloaded.")
        if (result.unreadable > 0) append(" ").append(files(result.unreadable)).append(" could not be read.")
    }

    private fun files(n: Int) = if (n == 1) "1 file" else "$n files"

    private fun number(value: Int): String = String.format(Locale.US, "%,d", value)

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

    /** D72: only when the phone could allow it and has not; null when allowed or not offered. */
    fun historyNotAllowed(allowed: Boolean?): String? =
        if (allowed == false) "Reading history older than 30 days is not allowed — tap Connect to allow it." else null

    private fun plural(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"
}
