package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.Sheets
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** D149, D150: the owner's changes to the sheet, each a new version made by him. Every fact is invented. */
class TrainerSheetsTest {

    private val sheets = InMemorySheetStore()
    private val owner = TrainerSheets(sheets, Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, Now { NOW })

    @Test
    fun `an edit is a new version of his, the fact in his words`() = runTest {
        sheets.add(NOW - 1, listOf(NOTICED), SheetMadeBy.REWRITE, "a-model")

        assertThat(owner.edit(1, "  His invented words.  ")).isEqualTo(TrainerSheets.Edit.DONE)

        val version = sheets.versions.value.last()
        assertThat(version.madeBy).isEqualTo(SheetMadeBy.OWNER)
        assertThat(version.model).isNull()
        assertThat(version.facts.single()).isEqualTo(NOTICED.copy(text = "His invented words.", source = FactSource.SAID, edited = true))
    }

    @Test
    fun `an edit that is blank, too long, over the cap, unchanged or of a gone fact stores nothing`() = runTest {
        sheets.add(NOW - 1, listOf(NOTICED, NOTICED.copy(id = 2, text = "x".repeat(3_950))), SheetMadeBy.REWRITE, "a-model")

        assertThat(owner.edit(1, " ")).isEqualTo(TrainerSheets.Edit.EMPTY)
        assertThat(owner.edit(1, "y".repeat(Sheets.MAX_FACT + 1))).isEqualTo(TrainerSheets.Edit.TOO_LONG)
        assertThat(owner.edit(1, "y".repeat(100))).isEqualTo(TrainerSheets.Edit.SHEET_FULL)
        assertThat(owner.edit(9, "Words.")).isEqualTo(TrainerSheets.Edit.GONE)
        assertThat(owner.edit(1, NOTICED.text)).isEqualTo(TrainerSheets.Edit.DONE)
        assertThat(sheets.versions.value).hasSize(1)
    }

    @Test
    fun `removing moves the fact to no longer true, as his`() = runTest {
        sheets.add(NOW - 1, listOf(NOTICED), SheetMadeBy.REWRITE, "a-model")

        assertThat(owner.remove(1)).isTrue()
        assertThat(owner.remove(1)).isFalse()

        val fact = sheets.versions.value.last().facts.single()
        assertThat(fact.droppedEpochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(fact.removedByOwner).isTrue()
        assertThat(sheets.versions.value).hasSize(2)
    }

    @Test
    fun `go back brings back the version before the newest rewrite, once`() = runTest {
        val before = sheets.add(NOW - 3, listOf(NOTICED), SheetMadeBy.OWNER, null)
        sheets.add(NOW - 2, listOf(NOTICED.copy(droppedEpochDay = TEST_EPOCH_DAY), NOTICED.copy(id = 2, text = "Wrong.")), SheetMadeBy.REWRITE, "a-model")

        assertThat(owner.goBack()).isTrue()
        assertThat(owner.goBack()).isFalse()

        val restored = sheets.versions.value.last()
        assertThat(restored.facts).containsExactly(NOTICED)
        assertThat(restored.restoredFrom).isEqualTo(before)
        assertThat(restored.madeBy).isEqualTo(SheetMadeBy.OWNER)
    }

    private companion object {
        const val NOW = TEST_EPOCH_DAY * 86_400_000L + 12 * 3_600_000L
        val NOTICED = SheetFact(1, FactGroup.TRAINING, "Invented.", TEST_EPOCH_DAY - 5, FactSource.NOTICED)
    }
}
