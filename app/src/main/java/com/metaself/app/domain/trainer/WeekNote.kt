package com.metaself.app.domain.trainer

/** D116: what wrote a week_note — a trainer answer, or the weekly letter. */
enum class WeekNoteSource { TRAINER, LETTER }

/**
 * D116: a week_note — at most two sentences about a week as a whole, written by the AI beside an answer or a
 * letter — with the moment it was written, the Monday of the week it is about, and what wrote it. Advice,
 * never a measurement (D4).
 */
data class WeekNote(val text: String, val writtenAtMillis: Long, val weekMonday: Long, val source: WeekNoteSource)

/** D116's two rules for the one note kept. Pure. */
object WeekNotes {

    /**
     * Whether [incoming] replaces [kept]: it is about a later week, or about the same week and written no
     * earlier. So a letter written on a Monday about the week before never displaces this week's note.
     */
    fun replaces(kept: WeekNote?, incoming: WeekNote): Boolean =
        kept == null || compareValuesBy(incoming, kept, { it.weekMonday }, { it.writtenAtMillis }) >= 0

    /** A week's note is about its week: shown only during the week of [monday]. */
    fun shown(note: WeekNote?, monday: Long): WeekNote? = note?.takeIf { it.weekMonday == monday }
}
