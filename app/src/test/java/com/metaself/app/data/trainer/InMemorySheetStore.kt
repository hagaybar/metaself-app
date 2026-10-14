package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.SheetVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [SheetStore] in memory, numbered from 1, oldest first. [failWrites] makes every write throw. */
class InMemorySheetStore : SheetStore {
    val versions = MutableStateFlow<List<SheetVersion>>(emptyList())
    var failWrites = false
    private var next = 1L

    override fun observe(): Flow<List<SheetVersion>> = versions

    override suspend fun add(atMillis: Long, facts: List<SheetFact>, madeBy: SheetMadeBy, model: String?, restoredFrom: Long?): Long {
        check(!failWrites) { "invented write failure" }
        val id = next++
        versions.value = versions.value + SheetVersion(id, atMillis, facts, madeBy, model, restoredFrom)
        return id
    }
}
