package com.metaself.app.ui.screen.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.LETTER_MONDAY
import com.metaself.app.data.letter.aWeeklyLetter
import com.metaself.app.data.letter.anEmptyWeek
import com.metaself.app.data.letter.someFigures
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.letter.LetterWording
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * What the letter's page draws, and in what order (D4, D103). Not provable here (CLAUDE.md, Testing):
 * widths, wrapping. The week is the one holding TEST_EPOCH_DAY; every figure and word is invented.
 */
@RunWith(RobolectricTestRunner::class)
class LetterScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    private fun draw(letter: WeeklyLetter): List<String> = render.texts {
        LetterScreen(LetterViewModel.State.Shown(letter), onBack = {}, zone = ZoneOffset.UTC)
    }

    @Test
    fun `the advice line comes before anything the model wrote but the headline, and the parts follow in order`() {
        val texts = draw(aWeeklyLetter().copy(texts = TEXTS))

        assertThat(texts).containsAtLeast(
            "WEEKLY LETTER · 31 AUG – 6 SEP", "Invented headline", LetterWording.FROM_TRAINER,
            "WHAT YOU PUT IN", "Invented effort.", "WHERE IT'S TAKING YOU", "Invented progress.",
            "ONE THING TO LOOK AT", "Invented look.", "FOR NEXT WEEK", "Invented next.", "Invented close.",
        ).inOrder()
        assertThat(render.isDrawnBefore(LetterWording.FROM_TRAINER, "WHAT YOU PUT IN")).isTrue()
        assertThat(render.isDrawnBefore("Invented close.", "THE WEEK")).isTrue()
    }

    @Test
    fun `the box shows this week against the four weeks, and a dash where a figure is missing`() {
        val week = anEmptyWeek(LETTER_MONDAY).copy(food = anEmptyWeek(LETTER_MONDAY).food.copy(daysLogged = 5, kcal = 2_000))
        val figures = someFigures().copy(week = week)
        val texts = draw(aWeeklyLetter().copy(figures = figures))

        assertThat(texts).containsAtLeast("THE WEEK", "THIS WEEK", "4-WEEK AVG").inOrder()
        assertThat(texts).containsAtLeast("Days logged", "5 of 7", "5").inOrder()
        assertThat(texts).containsAtLeast("Steps a day", "—", "8,000").inOrder()
        assertThat(texts).doesNotContain("Weekly plan")
    }

    @Test
    fun `the band's line shows only when the band's data may be behind`() {
        val sundayTwoPm = ((LETTER_MONDAY + 6) * 24 + 14) * 3_600_000L
        val behind = draw(aWeeklyLetter().copy(bandDataUntil = sundayTwoPm))
        assertThat(behind).contains("Band data up to Sun 14:00.")
        assertThat(behind).contains("Calories and protein are averages over the 5 days you logged.")

        val current = draw(aWeeklyLetter())
        assertThat(current.none { it.startsWith("Band data up to") }).isTrue()
    }

    @Test
    fun `a letter that cannot be read says so`() {
        val texts = render.texts { LetterScreen(LetterViewModel.State.Unreadable, onBack = {}) }

        assertThat(texts).contains(LetterWording.UNREADABLE)
    }

    private companion object {
        val TEXTS = com.metaself.app.domain.letter.LetterTexts(
            "Invented headline", "Invented effort.", "Invented progress.", "Invented look.", "Invented next.", "Invented close.",
        )
    }
}
