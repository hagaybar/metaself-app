package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNotes
import kotlinx.coroutines.flow.MutableStateFlow

/** [WeekNoteStore] in memory, keeping by the same rule; [failing] makes the next keeps throw, as a full disk would. */
class InMemoryWeekNoteStore(initial: WeekNote? = null) : WeekNoteStore {
    override val note = MutableStateFlow(initial)
    var failing = false

    override suspend fun keep(note: WeekNote) {
        if (failing) throw java.io.IOException("invented write failure")
        if (WeekNotes.replaces(this.note.value, note)) this.note.value = note
    }
}
