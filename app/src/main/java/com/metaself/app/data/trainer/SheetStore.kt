package com.metaself.app.data.trainer

import com.metaself.app.data.ai.SheetResponse
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.SheetVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The sheet of what the trainer knows about the owner (D149–D151), one row per version — none is ever changed or
 * deleted but by a restore. Writes throw; the callers catch them.
 */
interface SheetStore {
    /** Every version this app can read, oldest first. */
    fun observe(): Flow<List<SheetVersion>>

    /** Stores a version; its id. */
    suspend fun add(atMillis: Long, facts: List<SheetFact>, madeBy: SheetMadeBy, model: String?, restoredFrom: Long? = null): Long

    companion object {
        /** No sheet, for tests that do not look at it. */
        val NONE: SheetStore = object : SheetStore {
            override fun observe(): Flow<List<SheetVersion>> = flowOf(emptyList())
            override suspend fun add(atMillis: Long, facts: List<SheetFact>, madeBy: SheetMadeBy, model: String?, restoredFrom: Long?): Long =
                error("no sheet store")
        }
    }
}

/** D151: the newest version, or null when none was made. */
suspend fun SheetStore.newest(): SheetVersion? = observe().first().maxByOrNull { it.id }

class RoomSheetStore @Inject constructor(private val dao: TrainerDao) : SheetStore {

    override fun observe(): Flow<List<SheetVersion>> = dao.observeSheets().map { rows -> rows.mapNotNull { it.toVersion() } }

    override suspend fun add(atMillis: Long, facts: List<SheetFact>, madeBy: SheetMadeBy, model: String?, restoredFrom: Long?): Long =
        dao.insertSheet(TrainerSheetEntity(0, atMillis, SheetResponse.encode(facts), madeBy.name, model, restoredFrom))
}

/** Null when the facts or who made it are not a shape this version reads: such a row is left out. */
fun TrainerSheetEntity.toVersion(): SheetVersion? {
    val read = SheetResponse.read(facts) ?: return null
    val by = SheetMadeBy.entries.firstOrNull { it.name == madeBy } ?: return null
    return SheetVersion(id, atMillis, read, by, model, restoredFrom)
}
