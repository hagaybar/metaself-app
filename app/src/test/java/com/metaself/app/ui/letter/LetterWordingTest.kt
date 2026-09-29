package com.metaself.app.ui.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.LETTER_MONDAY
import com.metaself.app.data.letter.aFullWeek
import com.metaself.app.data.letter.anEmptyWeek
import com.metaself.app.data.letter.someFigures
import com.metaself.app.domain.letter.FoodWeek
import com.metaself.app.domain.letter.LetterFigures
import com.metaself.app.ui.letter.LetterWording.Row
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * The weekly letter's sentences (D99, D102, D103). The week is the one holding TEST_EPOCH_DAY, Monday 31
 * August to Sunday 6 September 2026; every figure is invented and round.
 */
class LetterWordingTest {

    @Test
    fun `the page says first that the letter is advice and the figures are the phone's (D4)`() {
        assertThat(LetterWording.FROM_TRAINER)
            .isEqualTo("From the AI trainer · advice, not a measurement. The figures were counted on your phone.")
        assertThat(LetterWording.HEADINGS)
            .containsExactly("WHAT YOU PUT IN", "WHERE IT'S TAKING YOU", "ONE THING TO LOOK AT", "FOR NEXT WEEK").inOrder()
    }

    @Test
    fun `the week's label names its days, across a month and within one`() {
        assertThat(LetterWording.weekLabel(LETTER_MONDAY)).isEqualTo("WEEKLY LETTER · 31 AUG – 6 SEP")
        assertThat(LetterWording.weekLabel(LETTER_MONDAY + 7)).isEqualTo("WEEKLY LETTER · 7–13 SEP")
        assertThat(LetterWording.weekShort(LETTER_MONDAY)).isEqualTo("31 Aug – 6 Sep")
    }

    @Test
    fun `the day's note and the notifications`() {
        assertThat(LetterWording.dayNote("Invented headline")).isEqualTo("Your week is in: Invented headline")
        assertThat(LetterWording.READ_IT).isEqualTo("Read it")
        assertThat(LetterWording.CHANNEL).isEqualTo("Weekly letter")
        assertThat(LetterWording.ARRIVED_TITLE).isEqualTo("Your week is in")
        assertThat(LetterWording.FAILED_TITLE).isEqualTo("Your weekly letter couldn't be written")
        assertThat(LetterWording.FAILED_TEXT).isEqualTo("Tap to try again.")
    }

    @Test
    fun `a full week's box, this week against the four before`() {
        val rows = LetterWording.rows(someFigures(targetKcal = 2_100))

        assertThat(rows).containsExactly(
            Row("Calories a day", "2,000", "2,000"),
            Row("Target", "2,100", "2,100"),
            Row("Protein a day", "100 g", "100 g"),
            Row("Days logged", "5 of 7", "5"),
            Row("Weight trend", "−0.2 kg", "−0.2 kg"),
            Row("Sessions", "3", "3"),
            Row("Distance", "9 km", "9 km"),
            Row("Steps a day", "8,000", "8,000"),
            Row("Weekly plan", "2 of 3", "—"),
        ).inOrder()
    }

    @Test
    fun `a missing figure is a dash, the plan row is left out without a plan, and averages keep a half`() {
        val week = anEmptyWeek(LETTER_MONDAY).copy(weightChangeKg = 0.2)
        val earlier = listOf(
            aFullWeek(LETTER_MONDAY - 7).copy(food = FoodWeek(4, 2_000, 100, 200, 70)),
            anEmptyWeek(LETTER_MONDAY - 14),
            anEmptyWeek(LETTER_MONDAY - 21),
            aFullWeek(LETTER_MONDAY - 28).copy(food = FoodWeek(5, 2_000, 100, 200, 70)),
        )
        val rows = LetterWording.rows(LetterFigures(week, earlier, targetKcal = null)).associateBy { it.name }

        assertThat(rows.keys).doesNotContain("Weekly plan")
        assertThat(rows.getValue("Calories a day")).isEqualTo(Row("Calories a day", "—", "2,000"))
        assertThat(rows.getValue("Target")).isEqualTo(Row("Target", "—", "—"))
        assertThat(rows.getValue("Days logged")).isEqualTo(Row("Days logged", "0 of 7", "2.3"))
        assertThat(rows.getValue("Weight trend")).isEqualTo(Row("Weight trend", "+0.2 kg", "−0.2 kg"))
        assertThat(rows.getValue("Sessions")).isEqualTo(Row("Sessions", "0", "1.5"))
        assertThat(rows.getValue("Distance").thisWeek).isEqualTo("—")
        assertThat(rows.getValue("Steps a day").thisWeek).isEqualTo("—")
    }

    @Test
    fun `distance is whole from ten kilometres, one decimal under unless it is whole`() {
        assertThat(LetterWording.distance(12_000)).isEqualTo("12 km")
        assertThat(LetterWording.distance(4_500)).isEqualTo("4.5 km")
        assertThat(LetterWording.distance(4_000)).isEqualTo("4 km")
        assertThat(LetterWording.distance(null)).isEqualTo("—")
    }

    @Test
    fun `a weight change too small to show is not given a sign`() {
        assertThat(LetterWording.weightChange(-0.01)).isEqualTo("0.0 kg")
        assertThat(LetterWording.weightChange(-0.3)).isEqualTo("−0.3 kg")
        assertThat(LetterWording.weightChange(null)).isEqualTo("—")
    }

    @Test
    fun `the notes under the box`() {
        assertThat(LetterWording.foodNote(5)).isEqualTo("Calories and protein are averages over the 5 days you logged.")
        assertThat(LetterWording.foodNote(1)).isEqualTo("Calories and protein are averages over the 1 day you logged.")
        assertThat(LetterWording.foodNote(0)).isNull()
        val sundayTwoPm = (LETTER_MONDAY + 6) * DAY + 14 * HOUR
        assertThat(LetterWording.bandNote(sundayTwoPm, ZoneOffset.UTC)).isEqualTo("Band data up to Sun 14:00.")
    }

    @Test
    fun `the setting and its ask`() {
        assertThat(LetterWording.SETTING_TITLE).isEqualTo("Weekly letter")
        assertThat(LetterWording.settingLine(on = true, hour = 20)).isEqualTo("Sundays at 20:00")
        assertThat(LetterWording.settingLine(on = false, hour = 20)).isEqualTo("Off")
        assertThat(LetterWording.hour(18)).isEqualTo("18:00")
        assertThat(LetterWording.ASK_BACKGROUND).isEqualTo("Allow Sunday's band data")
        assertThat(LetterWording.BACKGROUND_REASON).isEqualTo("So Sunday's letter includes sessions from today.")
    }

    @Test
    fun `the setup note asks for what is still wanted`() {
        assertThat(LetterWording.setupNote(20, notifications = true, background = false))
            .isEqualTo("Your weekly letter arrives Sundays at 20:00. Allow notifications so you see it.")
        assertThat(LetterWording.setupNote(20, notifications = true, background = true)).isEqualTo(
            "Your weekly letter arrives Sundays at 20:00. Allow notifications so you see it. Then allow Sunday's band " +
                "data — so Sunday's letter includes sessions from today.",
        )
        assertThat(LetterWording.setupNote(18, notifications = false, background = true))
            .isEqualTo("Your weekly letter arrives Sundays at 18:00. Allow Sunday's band data — so Sunday's letter includes sessions from today.")
        assertThat(LetterWording.setupNote(20, notifications = false, background = false)).isNull()
        assertThat(LetterWording.SET_UP).isEqualTo("Set up")
    }

    @Test
    fun `the list, Write it now and what it sends`() {
        assertThat(LetterWording.LIST_TITLE).isEqualTo("Weekly letters")
        assertThat(LetterWording.WRITE_NOW).isEqualTo("Write it now")
        assertThat(LetterWording.EMPTY).isEqualTo("Your first letter comes on Sunday evening.")
        assertThat(LetterWording.QUIET).isEqualTo("Nothing was logged that week, so there is nothing to write.")
        assertThat(LetterWording.privacyLetter(20)).isEqualTo(
            "Sends to the AI provider, with your key: your week's food as averages a day, your weight trend, your " +
                "sessions and steps, your note about yourself, your goal, age, sex and height. Never a meal or a " +
                "food. One of today's 20 AI requests.",
        )
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
    }
}
