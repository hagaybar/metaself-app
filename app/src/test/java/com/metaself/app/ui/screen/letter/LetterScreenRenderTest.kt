package com.metaself.app.ui.screen.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.LETTER_MONDAY
import com.metaself.app.data.letter.aWeeklyLetter
import com.metaself.app.data.letter.anEmptyWeek
import com.metaself.app.data.letter.someFigures
import com.metaself.app.domain.letter.LetterTexts
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
 * What the letter's page draws, and in what order (D4, D103, D140). Not provable here (CLAUDE.md, Testing):
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

    /** D140: the coach's parts in order under their headings, rests_on under look_after, the figures closed. */
    @Test
    fun `a coach's letter draws its parts in order, with the figures closed`() {
        val texts = draw(aWeeklyLetter().copy(texts = COACH))

        assertThat(texts).containsAtLeast(
            "WEEKLY LETTER · 31 AUG – 6 SEP", "Invented headline", LetterWording.FROM_COACH,
            "WHAT YOU DID THIS WEEK", "Invented first paragraph.", "Invented second paragraph.",
            "Invented lead. Invented rest.",
            "ONE THING I WANT TO LOOK AFTER", "Invented look.", "Rests on invented days",
            "FOR NEXT WEEK", "Invented next.", "Invented close.", LetterWording.FIGURES,
        ).inOrder()
        assertThat(texts).doesNotContain("THE WEEK")
        assertThat(texts).doesNotContain("WHAT YOU PUT IN")
        assertThat(texts).doesNotContain(LetterWording.FROM_TRAINER)
    }

    @Test
    fun `the week's figures open in place, with their notes`() {
        val sundayTwoPm = ((LETTER_MONDAY + 6) * 24 + 14) * 3_600_000L
        draw(aWeeklyLetter().copy(texts = COACH, bandDataUntil = sundayTwoPm))

        render.click(LetterWording.FIGURES)
        val opened = render.textsAgain()

        assertThat(opened).containsAtLeast(LetterWording.FIGURES, "THE WEEK", "THIS WEEK", "4-WEEK AVG").inOrder()
        assertThat(opened).contains("Band data up to Sun 14:00.")
        assertThat(opened).contains("Calories and protein are averages over the 5 days you logged.")
    }

    /** D140: a letter stored before it keeps D102's shape — its headings, its advice line, and the box open. */
    @Test
    fun `an old six-part letter is drawn as before`() {
        val texts = draw(aWeeklyLetter(texts = SIX_PARTS))

        assertThat(texts).containsAtLeast(
            "WEEKLY LETTER · 31 AUG – 6 SEP", "Invented headline", LetterWording.FROM_TRAINER,
            "WHAT YOU PUT IN", "Invented effort.", "WHERE IT'S TAKING YOU", "Invented progress.",
            "ONE THING TO LOOK AT", "Invented look.", "FOR NEXT WEEK", "Invented next.", "Invented close.",
        ).inOrder()
        assertThat(render.isDrawnBefore(LetterWording.FROM_TRAINER, "WHAT YOU PUT IN")).isTrue()
        assertThat(render.isDrawnBefore("Invented close.", "THE WEEK")).isTrue()
        assertThat(texts).doesNotContain(LetterWording.FIGURES)
    }

    @Test
    fun `the box shows this week against the four weeks, and a dash where a figure is missing`() {
        val week = anEmptyWeek(LETTER_MONDAY).copy(food = anEmptyWeek(LETTER_MONDAY).food.copy(daysLogged = 5, kcal = 2_000))
        val figures = someFigures().copy(week = week)
        val texts = draw(aWeeklyLetter(texts = SIX_PARTS).copy(figures = figures))

        assertThat(texts).containsAtLeast("THE WEEK", "THIS WEEK", "4-WEEK AVG").inOrder()
        assertThat(texts).containsAtLeast("Days logged", "5 of 7", "5").inOrder()
        assertThat(texts).containsAtLeast("Steps a day", "—", "8,000").inOrder()
        assertThat(texts).doesNotContain("Weekly plan")
    }

    @Test
    fun `the band's line shows only when the band's data may be behind`() {
        val sundayTwoPm = ((LETTER_MONDAY + 6) * 24 + 14) * 3_600_000L
        val behind = draw(aWeeklyLetter(texts = SIX_PARTS).copy(bandDataUntil = sundayTwoPm))
        assertThat(behind).contains("Band data up to Sun 14:00.")
        assertThat(behind).contains("Calories and protein are averages over the 5 days you logged.")

        val current = draw(aWeeklyLetter(texts = SIX_PARTS))
        assertThat(current.none { it.startsWith("Band data up to") }).isTrue()
    }

    @Test
    fun `a letter that cannot be read says so`() {
        val texts = render.texts { LetterScreen(LetterViewModel.State.Unreadable, onBack = {}) }

        assertThat(texts).contains(LetterWording.UNREADABLE)
    }

    private companion object {
        val SIX_PARTS = LetterTexts.SixParts(
            "Invented headline", "Invented effort.", "Invented progress.", "Invented look.", "Invented next.", "Invented close.",
        )
        val COACH = LetterTexts.Coach(
            "Invented headline", "Invented first paragraph.\n\nInvented second paragraph.", "Invented lead. Invented rest.",
            "Invented look.", "invented days", "Invented next.", "Invented close.",
        )
    }
}
