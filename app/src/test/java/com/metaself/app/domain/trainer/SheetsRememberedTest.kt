package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test

/** D161: facts said in a conversation join the sheet, under its caps, and change nothing there. Every word is invented. */
class SheetsRememberedTest {

    private val edited = SheetFact(3, FactGroup.BODY, "Your invented edited fact.", TEST_EPOCH_DAY - 9, FactSource.SAID, edited = true)

    @Test
    fun `a new fact is said, from today, with a fresh id, and what is there is untouched`() {
        val after = Sheets.remembered(listOf(edited), listOf(FactGroup.EATING to "  A new invented fact.  "), TEST_EPOCH_DAY)

        assertThat(after.first()).isEqualTo(edited)
        val added = after.last()
        assertThat(added.id).isEqualTo(4)
        assertThat(added.text).isEqualTo("A new invented fact.")
        assertThat(added.group).isEqualTo(FactGroup.EATING)
        assertThat(added.source).isEqualTo(FactSource.SAID)
        assertThat(added.sinceEpochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(added.edited).isFalse()
    }

    @Test
    fun `a repeat, a blank and a fact too long add nothing, and the answer is the same list`() {
        val previous = listOf(edited)

        val after = Sheets.remembered(
            previous,
            listOf(FactGroup.BODY to "your INVENTED edited fact.", FactGroup.LIFE to "  ", FactGroup.LIFE to "x".repeat(Sheets.MAX_FACT + 1)),
            TEST_EPOCH_DAY,
        )

        assertThat(after).isSameInstanceAs(previous)
    }

    /** D150: a fact he removed does not come back by being said to have been said. */
    @Test
    fun `a fact the owner removed is not brought back`() {
        val removed = edited.copy(droppedEpochDay = TEST_EPOCH_DAY - 1, removedByOwner = true)

        assertThat(Sheets.remembered(listOf(removed), listOf(FactGroup.BODY to edited.text), TEST_EPOCH_DAY)).containsExactly(removed)
    }

    @Test
    fun `the count and character caps drop what crosses them, and a later fact that fits is still tried`() {
        val full = List(Sheets.MAX_FACTS) { SheetFact(it.toLong(), FactGroup.LIFE, "Invented fact $it.", TEST_EPOCH_DAY, FactSource.NOTICED) }
        assertThat(Sheets.remembered(full, listOf(FactGroup.LIFE to "One too many."), TEST_EPOCH_DAY)).isSameInstanceAs(full)

        val nearly = listOf(SheetFact(1, FactGroup.LIFE, "x".repeat(Sheets.MAX_CHARS - 10), TEST_EPOCH_DAY, FactSource.NOTICED))
        val after = Sheets.remembered(nearly, listOf(FactGroup.LIFE to "y".repeat(11), FactGroup.LIFE to "y".repeat(10)), TEST_EPOCH_DAY)
        assertThat(after.map { it.text }).containsExactly("x".repeat(Sheets.MAX_CHARS - 10), "y".repeat(10)).inOrder()
    }

    @Test
    fun `a fact dropped long ago goes, and one still shown stays, when a version is made`() {
        val old = edited.copy(id = 1, droppedEpochDay = TEST_EPOCH_DAY - Sheets.DROPPED_DAYS)
        val recent = edited.copy(id = 2, text = "Invented, dropped lately.", droppedEpochDay = TEST_EPOCH_DAY - 1)

        val after = Sheets.remembered(listOf(old, recent), listOf(FactGroup.LIFE to "A new invented fact."), TEST_EPOCH_DAY)

        assertThat(after.map { it.id }).containsExactly(3L, 2L).inOrder()
    }
}
