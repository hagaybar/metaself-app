package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * D149, D150: the sheet's rules — the cap enforced on the phone, what a rewrite's answer becomes, the owner's edits and
 * removals, the 28 days of No longer true, going back, and the span a rewrite reads. Today is [TEST_EPOCH_DAY]. Every
 * fact and word is invented.
 */
class SheetsTest {

    private val today = TEST_EPOCH_DAY

    @Test
    fun `a continued fact keeps its id and since, and a new one gets a fresh id`() {
        val previous = listOf(fact(1, "Invented fact one.", since = today - 9), fact(4, "Invented fact four."))

        val after = Sheets.afterRewrite(
            previous,
            listOf(answer(1, "Invented fact one, changed.", since = null), answer(null, "A new invented fact.", since = today - 2)),
            today,
        )

        assertThat(after.filter { it.current }.map { it.id to it.sinceEpochDay })
            .containsExactly(1L to today - 9, 5L to today - 2).inOrder()
        assertThat(after.single { it.id == 4L }.droppedEpochDay).isEqualTo(today)
        assertThat(after.single { it.id == 4L }.removedByOwner).isFalse()
    }

    @Test
    fun `an unknown or repeated id is a new fact, and a future since falls back`() {
        val previous = listOf(fact(1, "Invented fact one.", since = today - 3))

        val after = Sheets.afterRewrite(
            previous,
            listOf(
                answer(1, "Invented fact one.", since = today + 5),
                answer(1, "Invented fact two.", since = null),
                answer(99, "Invented fact three.", since = today + 1),
            ),
            today,
        )

        assertThat(after.map { Triple(it.id, it.text, it.sinceEpochDay) }).containsExactly(
            Triple(1L, "Invented fact one.", today - 3),
            Triple(2L, "Invented fact two.", today),
            Triple(3L, "Invented fact three.", today),
        ).inOrder()
    }

    @Test
    fun `an edited fact stays edited only while its text is unchanged`() {
        val previous = listOf(fact(1, "His own words.", edited = true), fact(2, "Also his words.", edited = true))

        val after = Sheets.afterRewrite(previous, listOf(answer(1, "His own words."), answer(2, "Reworded by the model.")), today)

        assertThat(after.map { it.edited }).containsExactly(true, false).inOrder()
    }

    @Test
    fun `blank facts and exact repeats are left out`() {
        val after = Sheets.afterRewrite(emptyList(), listOf(answer(null, "  "), answer(null, "Same words."), answer(null, "same words.")), today)

        assertThat(after.map { it.text }).containsExactly("Same words.")
    }

    @Test
    fun `the cap stops at 40 facts, in the model's order`() {
        val after = Sheets.afterRewrite(emptyList(), (1..45).map { answer(null, "Invented fact $it.") }, today)

        assertThat(after).hasSize(Sheets.MAX_FACTS)
        assertThat(after.last().text).isEqualTo("Invented fact 40.")
    }

    @Test
    fun `a fact that would cross 4,000 characters is left out, and a shorter one after it still fits`() {
        val after = Sheets.afterRewrite(
            emptyList(),
            listOf(answer(null, "a".repeat(3_000)), answer(null, "b".repeat(1_500)), answer(null, "c".repeat(1_000))),
            today,
        )

        assertThat(after.map { it.text.first() }).containsExactly('a', 'c').inOrder()
        assertThat(Sheets.chars(after)).isEqualTo(Sheets.MAX_CHARS)
    }

    @Test
    fun `over the cap the owner's edited facts are kept before the model's`() {
        val previous = listOf(fact(1, "e".repeat(2_000), edited = true))

        val after = Sheets.afterRewrite(previous, listOf(answer(null, "m".repeat(3_000)), answer(1, "e".repeat(2_000))), today)

        assertThat(after.filter { it.current }.map { it.id }).containsExactly(1L)
        assertThat(after.filter { it.current }.single().edited).isTrue()
    }

    @Test
    fun `a fact left out by the cap is dropped like any other`() {
        val previous = listOf(fact(1, "x".repeat(3_500)))

        val after = Sheets.afterRewrite(previous, listOf(answer(null, "y".repeat(1_000)), answer(1, "x".repeat(3_500))), today)

        assertThat(after.single { it.id == 1L }.droppedEpochDay).isEqualTo(today)
    }

    @Test
    fun `dropped facts are carried for 28 days, then go`() {
        val previous = listOf(
            fact(1, "Dropped 27 days ago.", dropped = today - 27),
            fact(2, "Dropped 28 days ago.", dropped = today - 28),
            fact(3, "Removed by him.", dropped = today - 1, removed = true),
        )

        val after = Sheets.afterRewrite(previous, emptyList(), today)

        assertThat(after.map { it.id }).containsExactly(1L, 3L)
        assertThat(after.single { it.id == 3L }.removedByOwner).isTrue()
        val version = SheetVersion(1, 0, previous, SheetMadeBy.REWRITE, "a-model")
        assertThat(version.dropped(today).map { it.id }).containsExactly(3L, 1L).inOrder()
    }

    @Test
    fun `an edit is his words, said and edited, and is refused when too long or over the cap`() {
        val previous = listOf(fact(1, "Invented.", source = FactSource.NOTICED), fact(2, "z".repeat(3_900)))

        val edited = Sheets.edit(previous, 1, "  His invented words.  ", today)

        assertThat(edited.first { it.id == 1L }).isEqualTo(
            SheetFact(1, FactGroup.BODY, "His invented words.", today - 1, FactSource.SAID, edited = true),
        )
        assertThrows<IllegalArgumentException> { Sheets.edit(previous, 1, "w".repeat(Sheets.MAX_FACT + 1), today) }
        assertThrows<IllegalArgumentException> { Sheets.edit(previous, 1, "w".repeat(200), today) }
        assertThrows<IllegalArgumentException> { Sheets.edit(previous, 1, " ", today) }
        assertThrows<IllegalArgumentException> { Sheets.edit(previous, 7, "Words.", today) }
    }

    @Test
    fun `removing moves the fact to no longer true, marked his`() {
        val previous = listOf(fact(1, "Invented."), fact(2, "Another."))

        val after = Sheets.remove(previous, 2, today)

        assertThat(after.filter { it.current }.map { it.id }).containsExactly(1L)
        assertThat(after.single { it.id == 2L }.removedByOwner).isTrue()
        assertThat(after.single { it.id == 2L }.droppedEpochDay).isEqualTo(today)
        assertThrows<IllegalArgumentException> { Sheets.remove(after, 2, today) }
    }

    @Test
    fun `go back is offered only while the newest version is a rewrite with one before it`() {
        val first = version(1, SheetMadeBy.REWRITE)
        val second = version(2, SheetMadeBy.REWRITE)
        val edit = version(3, SheetMadeBy.OWNER)

        assertThat(Sheets.canGoBack(listOf(first))).isFalse()
        assertThat(Sheets.goBackTo(listOf(second, first))).isEqualTo(first)
        assertThat(Sheets.canGoBack(listOf(first, second, edit))).isFalse()
    }

    @Test
    fun `a rewrite undone by go back does not count as the last rewrite`() {
        val first = version(1, SheetMadeBy.REWRITE, at = 100)
        val edit = version(2, SheetMadeBy.OWNER, at = 150)
        val bad = version(3, SheetMadeBy.REWRITE, at = 200)
        val back = version(4, SheetMadeBy.OWNER, at = 250, restoredFrom = 2)

        assertThat(Sheets.lastRewriteAt(listOf(first, edit, bad))).isEqualTo(200)
        assertThat(Sheets.lastRewriteAt(listOf(first, edit, bad, back))).isEqualTo(100)
        assertThat(Sheets.newestRewriteAt(listOf(first, edit, bad, back))).isEqualTo(200)
        assertThat(Sheets.lastRewriteAt(listOf(edit))).isNull()
    }

    @Test
    fun `the span is everything after the last rewrite, cut to the newest 48,000 characters`() {
        val before = said(1, at = 1_000, "Before the rewrite.")
        val long = said(2, at = 3_000, "l".repeat(30_000))
        val middle = said(3, at = 4_000, "m".repeat(20_000))
        val newest = said(4, at = 5_000, "n".repeat(20_000))

        assertThat(Sheets.span(listOf(before, said(5, 2_000, "After.")), afterMillis = 1_000).map { (it as ThreadItem.Said).message.id })
            .containsExactly(5L)
        assertThat(Sheets.span(listOf(before, long, middle, newest), afterMillis = null)).containsExactly(middle, newest).inOrder()
    }

    private fun fact(
        id: Long,
        text: String,
        since: Long = today - 1,
        source: FactSource = FactSource.SAID,
        edited: Boolean = false,
        dropped: Long? = null,
        removed: Boolean = false,
    ) = SheetFact(id, FactGroup.BODY, text, since, source, edited, dropped, removed)

    private fun answer(id: Long?, text: String, since: Long? = today - 1) = RewrittenFact(id, FactGroup.BODY, text, since, FactSource.SAID)

    private fun version(id: Long, madeBy: SheetMadeBy, at: Long = id * 100, restoredFrom: Long? = null) =
        SheetVersion(id, at, emptyList(), madeBy, if (madeBy == SheetMadeBy.REWRITE) "a-model" else null, restoredFrom)

    private fun said(id: Long, at: Long, text: String) = ThreadItem.Said(TrainerMessage(id, at, true, text, null, answered = false))
}
