package com.metaself.app.ui.screen.letter

import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.LETTER_MONDAY
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.letter.LetterWording
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * Weekly letters (D103): Write it now with what it sends, the rows, New, and the empty list. Not provable
 * here (CLAUDE.md, Testing): a row's true touch height. Every headline is invented.
 */
@RunWith(RobolectricTestRunner::class)
class LettersScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `Write it now is offered with what it sends, and taps through`() {
        var tapped = false
        val texts = render.texts {
            LettersScreen(State(writeWeek = LETTER_MONDAY), onBack = {}, onOpen = {}, onWriteNow = { tapped = true })
        }

        assertThat(texts).contains("Write it now")
        assertThat(texts).contains(LetterWording.privacyLetter(20))
        render.click("Write it now")
        assertThat(tapped).isTrue()
    }

    @Test
    fun `without a week to write there is no Write it now`() {
        val texts = render.texts { LettersScreen(State(), onBack = {}, onOpen = {}, onWriteNow = {}) }

        assertThat(texts).doesNotContain("Write it now")
        assertThat(texts).contains(LetterWording.EMPTY)
    }

    @Test
    fun `an unread letter shows New, a read one does not, and a row opens its letter`() {
        var opened: Long? = null
        val rows = listOf(
            LettersViewModel.Row(LETTER_MONDAY, "31 Aug – 6 Sep", "Invented new headline", new = true),
            LettersViewModel.Row(LETTER_MONDAY - 7, "24 Aug – 30 Aug", "Invented old headline", new = false),
        )
        val texts = render.texts { LettersScreen(State(letters = rows), onBack = {}, onOpen = { opened = it }, onWriteNow = {}) }

        assertThat(texts).containsAtLeast("31 Aug – 6 Sep", "Invented new headline", "New", "24 Aug – 30 Aug", "Invented old headline").inOrder()
        assertThat(texts.count { it == "New" }).isEqualTo(1)
        assertThat(render.roleOf("31 Aug – 6 Sep")).isEqualTo(Role.Button)
        render.click("24 Aug – 30 Aug")
        assertThat(opened).isEqualTo(LETTER_MONDAY - 7)
    }

    @Test
    fun `a quiet week's answer is said`() {
        val texts = render.texts { LettersScreen(State(said = LetterWording.QUIET), onBack = {}, onOpen = {}, onWriteNow = {}) }

        assertThat(texts).contains(LetterWording.QUIET)
    }

    private fun State(
        writeWeek: Long? = null,
        letters: List<LettersViewModel.Row> = emptyList(),
        said: String? = null,
    ) = LettersViewModel.State(loading = false, letters = letters, writeWeek = writeWeek, said = said, ceiling = 20)
}
