package com.metaself.app.domain.encourage

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.encourage.EncouragementWording
import org.junit.jupiter.api.Test

class EncouragementsTest {

    private fun pick(
        somethingRarer: Boolean = false,
        alreadySaidToday: Boolean = false,
        daysAway: Int = 0,
        windowKeptYesterday: Boolean = false,
        windowKeptLastSeven: Boolean = false,
        trendAtNewLow: Boolean = false,
        streakDays: Int = 3,
        stepsToday: Int = 4_000,
        usualSteps: Int? = 5_200,
    ) = Encouragements.pick(
        somethingRarer, alreadySaidToday, daysAway, windowKeptYesterday, windowKeptLastSeven,
        trendAtNewLow, streakDays, stepsToday, usualSteps,
    )

    @Test
    fun `an ordinary day says nothing`() {
        assertThat(pick()).isNull()
    }

    /**
     * Coming back after a gap is the moment a habit is actually saved, and it is exactly when most
     * apps choose to show a broken streak instead.
     */
    @Test
    fun `coming back after a gap beats everything else`() {
        val occasion = pick(
            daysAway = 5,
            windowKeptYesterday = true,
            trendAtNewLow = true,
            streakDays = 7,
        )

        assertThat(occasion).isEqualTo(Occasion.WELCOME_BACK)
    }

    @Test
    fun `a day or two away is not a gap`() {
        assertThat(pick(daysAway = 2)).isNull()
    }

    @Test
    fun `yesterday inside the window is worth saying`() {
        assertThat(pick(windowKeptYesterday = true)).isEqualTo(Occasion.WINDOW_YESTERDAY)
    }

    @Test
    fun `a new low then a kept week, once no streak milestone is due`() {
        assertThat(pick(trendAtNewLow = true, windowKeptLastSeven = true, streakDays = 9))
            .isEqualTo(Occasion.NEW_LOW)
        assertThat(pick(windowKeptLastSeven = true, streakDays = 9))
            .isEqualTo(Occasion.WINDOW_WEEK)
        assertThat(pick(streakDays = 14)).isEqualTo(Occasion.LOGGING_WEEK)
    }

    /**
     * The whole of issue #59. A streak milestone comes round one day in seven; everything below
     * WELCOME_BACK can be true on any day, keeping the eating window included. Fifth in the
     * queue, the streak could lose its turn indefinitely — and because it only recurs weekly, losing
     * the slot lost the occasion rather than deferring it. It now goes second.
     */
    @Test
    fun `a streak milestone beats everything a common day can offer`() {
        val occasion = pick(
            streakDays = 21,
            windowKeptYesterday = true,
            windowKeptLastSeven = true,
            trendAtNewLow = true,
            stepsToday = 30_000,
        )

        assertThat(occasion).isEqualTo(Occasion.LOGGING_WEEK)
    }

    /** Coming back after a gap still wins, which is the one thing rarer than a weekly milestone. */
    @Test
    fun `coming back still beats a streak milestone`() {
        assertThat(pick(daysAway = 5, streakDays = 21)).isEqualTo(Occasion.WELCOME_BACK)
    }

    /** The budget is untouched: a milestone day that has already had its remark stays quiet. */
    @Test
    fun `a streak milestone does not buy a second remark`() {
        assertThat(pick(alreadySaidToday = true, streakDays = 21)).isNull()
        assertThat(pick(somethingRarer = true, streakDays = 21)).isNull()
    }

    @Test
    fun `a streak that is not a multiple of seven is not a week`() {
        assertThat(pick(streakDays = 9)).isNull()
        assertThat(pick(streakDays = 0)).isNull()
    }

    @Test
    fun `a walk has to be well past usual to be remarkable`() {
        assertThat(pick(stepsToday = 7_000, usualSteps = 5_200)).isNull()
        assertThat(pick(stepsToday = 9_000, usualSteps = 5_200)).isEqualTo(Occasion.BIG_WALK)
    }

    @Test
    fun `with no usual day yet, no walk is remarkable`() {
        assertThat(pick(stepsToday = 30_000, usualSteps = null)).isNull()
    }

    /** One a day, whatever else is true. Praise that arrives every time stops being praise. */
    @Test
    fun `nothing more is said once something has been`() {
        assertThat(pick(alreadySaidToday = true, daysAway = 9, windowKeptYesterday = true)).isNull()
    }

    /** A milestone or an arrival is rare by design and always takes the day's slot. */
    @Test
    fun `something rarer wins the day`() {
        assertThat(pick(somethingRarer = true, windowKeptYesterday = true)).isNull()
    }

    /**
     * There is no occasion for a window missed, a streak broken or a target overshot. Silence is
     * the whole of what the app says about a bad day (D14).
     */
    @Test
    fun `nothing here is about a failure`() {
        val everything = Occasion.entries.joinToString(" ") {
            EncouragementWording.of(it, streakDays = 14)
        }.lowercase()

        // Whole words. "unbroken" contains "broke", and a substring check would have failed on a
        // sentence that says the opposite of what it was looking for — the same trap the privacy
        // test hit when "age" turned out to live inside "language".
        listOf("missed", "broke", "broken", "failed", "should", "only", "lapse")
            .forEach { word ->
                assertThat(Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(everything))
                    .isFalse()
            }
    }

    @Test
    fun `a week of logging is a week, and a fortnight is counted`() {
        assertThat(EncouragementWording.of(Occasion.LOGGING_WEEK, streakDays = 7))
            .isEqualTo("A week of logging, unbroken.")
        assertThat(EncouragementWording.of(Occasion.LOGGING_WEEK, streakDays = 14))
            .isEqualTo("14 days of logging, unbroken.")
    }

    /** Warm, and entirely without reference to what was missed. */
    @Test
    fun `coming back is not made conditional on an apology`() {
        val text = EncouragementWording.of(Occasion.WELCOME_BACK).lowercase()

        assertThat(text).isEqualTo("good to see you back.")
        assertThat(text).doesNotContain("away")
        assertThat(text).doesNotContain("days")
    }
}
