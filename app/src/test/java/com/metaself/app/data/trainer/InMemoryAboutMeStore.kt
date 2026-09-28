package com.metaself.app.data.trainer

import kotlinx.coroutines.flow.MutableStateFlow

/** [AboutMeStore] in memory; [failing] makes the next saves throw, as a full disk would. */
class InMemoryAboutMeStore(initial: String = "") : AboutMeStore {
    override val note = MutableStateFlow(initial)
    var failing = false

    override suspend fun save(note: String) {
        if (failing) throw java.io.IOException("invented write failure")
        this.note.value = note.trim().take(AboutMeStore.MAX)
    }
}
