package com.metaself.app.data.trainer

import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.SheetVersion
import com.metaself.app.domain.trainer.Sheets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The owner's changes to the sheet (D149, D150), each a new version made by him: an edit (his words, "you said"), a
 * removal (to No longer true), and Go back to the version before the newest rewrite. Writes throw; the page catches
 * them (`guarded`) and says so.
 */
class TrainerSheets @Inject constructor(
    private val sheets: SheetStore,
    private val today: Today,
    private val now: Now,
) {
    enum class Edit { DONE, EMPTY, TOO_LONG, SHEET_FULL, GONE }

    /** Every version, oldest first. */
    fun observe(): Flow<List<SheetVersion>> = sheets.observe()

    /** The current fact [id] in [text]; refused, and nothing stored, when blank, too long, over the cap or gone. */
    suspend fun edit(id: Long, text: String): Edit {
        val words = text.trim()
        if (words.isEmpty()) return Edit.EMPTY
        if (words.length > Sheets.MAX_FACT) return Edit.TOO_LONG
        val newest = sheets.newest() ?: return Edit.GONE
        val fact = newest.current.firstOrNull { it.id == id } ?: return Edit.GONE
        if (Sheets.chars(newest.facts) - fact.text.length + words.length > Sheets.MAX_CHARS) return Edit.SHEET_FULL
        if (words == fact.text) return Edit.DONE
        sheets.add(now(), Sheets.edit(newest.facts, id, words, day()), SheetMadeBy.OWNER, null)
        return Edit.DONE
    }

    /** The current fact [id] to No longer true, marked his; false when it is gone already. */
    suspend fun remove(id: Long): Boolean {
        val newest = sheets.newest() ?: return false
        if (newest.current.none { it.id == id }) return false
        sheets.add(now(), Sheets.remove(newest.facts, id, day()), SheetMadeBy.OWNER, null)
        return true
    }

    /** D150: the facts of the version before the newest rewrite, as a new version; false when not offered. */
    suspend fun goBack(): Boolean {
        val to = Sheets.goBackTo(sheets.observe().first()) ?: return false
        val day = day()
        val facts = to.facts.filter { it.current || Sheets.stillShown(it.droppedEpochDay!!, day) }
        sheets.add(now(), facts, SheetMadeBy.OWNER, null, restoredFrom = to.id)
        return true
    }

    private fun day(): Long = today().toEpochDay()
}
