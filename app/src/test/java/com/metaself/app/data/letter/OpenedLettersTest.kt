package com.metaself.app.data.letter

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** What a weekly letter notification's tap asks to open (D103, design question 16). The week is invented. */
class OpenedLettersTest {

    @Test
    fun `an open is handed out once, and a later tap replaces an untaken one`() {
        val opened = OpenedLetters()
        assertThat(opened.take()).isNull()

        opened.offer(LetterOpen.List)
        opened.offer(LetterOpen.Letter(LETTER_MONDAY))

        assertThat(opened.pending.value).isEqualTo(LetterOpen.Letter(LETTER_MONDAY))
        assertThat(opened.take()).isEqualTo(LetterOpen.Letter(LETTER_MONDAY))
        assertThat(opened.take()).isNull()
        assertThat(opened.pending.value).isNull()
    }

    @Test
    fun `a launch carrying a week opens that letter`() {
        assertThat(letterOpen(weekMonday = LETTER_MONDAY, list = false)).isEqualTo(LetterOpen.Letter(LETTER_MONDAY))
    }

    @Test
    fun `the failure notification's launch opens the list`() {
        assertThat(letterOpen(weekMonday = null, list = true)).isEqualTo(LetterOpen.List)
    }

    @Test
    fun `any other launch opens nothing`() {
        assertThat(letterOpen(weekMonday = null, list = false)).isNull()
    }
}
