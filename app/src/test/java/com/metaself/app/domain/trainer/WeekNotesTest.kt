package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test

/** D116: which week_note is kept, and when it is shown. Every note and moment is invented. */
class WeekNotesTest {

    private val monday = TEST_EPOCH_DAY - 3

    @Test
    fun `a note replaces the kept one when it is about a later week, or as late a week written no earlier`() {
        val kept = note(weekMonday = monday, at = 2_000)

        assertThat(WeekNotes.replaces(null, kept)).isTrue()
        assertThat(WeekNotes.replaces(kept, note(weekMonday = monday, at = 3_000))).isTrue()
        assertThat(WeekNotes.replaces(kept, note(weekMonday = monday, at = 2_000))).isTrue()
        assertThat(WeekNotes.replaces(kept, note(weekMonday = monday, at = 1_000))).isFalse()
        assertThat(WeekNotes.replaces(kept, note(weekMonday = monday + 7, at = 1_000))).isTrue()
        // A letter written on Monday about the week before never displaces this week's note.
        assertThat(WeekNotes.replaces(kept, note(weekMonday = monday - 7, at = 9_000, source = WeekNoteSource.LETTER))).isFalse()
    }

    @Test
    fun `only this week's note is shown`() {
        val note = note(weekMonday = monday, at = 1_000)

        assertThat(WeekNotes.shown(note, monday)).isEqualTo(note)
        assertThat(WeekNotes.shown(note, monday + 7)).isNull()
        assertThat(WeekNotes.shown(null, monday)).isNull()
    }

    private fun note(weekMonday: Long, at: Long, source: WeekNoteSource = WeekNoteSource.TRAINER) =
        WeekNote("Invented week note.", at, weekMonday, source)
}
