package com.metaself.app.domain.window

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.anItem
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EatingWindowTest {

    private val zone: ZoneId = ZoneId.of("UTC+01:00")
    private val today = LocalDate.of(2026, 9, 5).toEpochDay()

    /** An ordinary fixed window: eat between six in the morning and eight in the evening. */
    private val untilEight = EatingWindow(startHour = 6, endHour = 20, fromEpochDay = today)

    private fun mealAt(hour: Int, onDay: Long = today, loggedOn: Long = onDay): Meal {
        val at = LocalDateTime.of(LocalDate.ofEpochDay(loggedOn), java.time.LocalTime.of(hour, 30))
        return Meal(
            epochDay = onDay,
            loggedAtMillis = at.atZone(zone).toInstant().toEpochMilli(),
            note = null,
            items = listOf(anItem()),
        )
    }

    @Test
    fun `an hour inside the window is inside`() {
        assertThat(untilEight.contains(13)).isTrue()
        assertThat(untilEight.contains(19)).isTrue()
        assertThat(untilEight.contains(6)).isTrue()
    }

    /**
     * The end is a DEADLINE, not a last permitted hour. The mark once still showed open at 20:45 on
     * a window ending at 20:00: reading the end inclusively turned "until 20:00" into "until
     * 20:59".
     */
    @Test
    fun `the window is shut at the hour it ends`() {
        assertThat(untilEight.contains(20)).isFalse()
        assertThat(untilEight.contains(21)).isFalse()
    }

    @Test
    fun `and open right up to it`() {
        assertThat(untilEight.contains(19)).isTrue()
    }

    @Test
    fun `an hour outside it is outside`() {
        assertThat(untilEight.contains(22)).isFalse()
        assertThat(untilEight.contains(3)).isFalse()
    }

    /** Eating between two in the afternoon and one in the morning is an ordinary fast. */
    @Test
    fun `a window can run past midnight`() {
        val overnight = EatingWindow(startHour = 14, endHour = 1, fromEpochDay = today)

        assertThat(overnight.contains(15)).isTrue()
        assertThat(overnight.contains(0)).isTrue()
        // Shut at the hour it ends, the same rule either side of midnight.
        assertThat(overnight.contains(1)).isFalse()
        assertThat(overnight.contains(13)).isFalse()
        assertThat(overnight.contains(2)).isFalse()
    }

    @Test
    fun `a day inside the window is kept`() {
        val verdict = EatingWindows.judge(
            listOf(untilEight),
            listOf(mealAt(8), mealAt(13), mealAt(19)),
            today,
            zone,
        )!!

        assertThat(verdict.kept).isTrue()
        assertThat(verdict.mealsInside).isEqualTo(3)
    }

    @Test
    fun `a late meal is counted, and the day is not kept`() {
        val verdict = EatingWindows.judge(
            listOf(untilEight),
            listOf(mealAt(13), mealAt(22)),
            today,
            zone,
        )!!

        assertThat(verdict.kept).isFalse()
        assertThat(verdict.mealsOutside).isEqualTo(1)
    }

    /**
     * The owner's constraint, and the whole design. A rule invented today does not get to judge
     * last Tuesday.
     */
    @Test
    fun `a day before the window began is not judged at all`() {
        val yesterday = today - 1

        assertThat(
            EatingWindows.judge(listOf(untilEight), listOf(mealAt(23, onDay = yesterday)), yesterday, zone),
        ).isNull()
    }

    /** Changing it leaves the old one governing the days it actually governed. */
    @Test
    fun `an older window still governs its own days`() {
        val older = EatingWindow(startHour = 6, endHour = 22, fromEpochDay = today - 30)
        val windows = listOf(older, untilEight)

        assertThat(EatingWindows.inForceOn(windows, today - 10)).isEqualTo(older)
        assertThat(EatingWindows.inForceOn(windows, today)).isEqualTo(untilEight)
        assertThat(EatingWindows.inForceOn(windows, today - 40)).isNull()
    }

    /**
     * A meal written down on a different day has no trustworthy hour: the record keeps when it was
     * typed, not when it was eaten. Filling in Tuesday on Thursday must not accuse him of anything.
     */
    @Test
    fun `a meal backfilled on another day is not judged`() {
        val verdict = EatingWindows.judge(
            listOf(untilEight),
            listOf(mealAt(hour = 23, onDay = today, loggedOn = today + 1)),
            today,
            zone,
        )!!

        assertThat(verdict.mealsUntimed).isEqualTo(1)
        assertThat(verdict.mealsOutside).isEqualTo(0)
        assertThat(verdict.kept).isTrue()
        assertThat(verdict.judged).isFalse()
    }

    @Test
    fun `days kept are counted from the record`() {
        val windows = listOf(untilEight.copy(fromEpochDay = today - 5))
        val meals = mapOf(
            (today - 5) to listOf(mealAt(13, today - 5)),
            (today - 4) to listOf(mealAt(23, today - 4)),
            (today - 3) to listOf(mealAt(12, today - 3)),
            (today - 2) to emptyList(),
        )

        val (kept, judged) = EatingWindows.daysKept(windows, meals, today - 5, today, zone)

        assertThat(judged).isEqualTo(3)
        assertThat(kept).isEqualTo(2)
    }

    /** A day nothing governed was not a day he was keeping to anything. */
    @Test
    fun `days before the window count neither way`() {
        val meals = mapOf((today - 10) to listOf(mealAt(23, today - 10)))

        val (kept, judged) = EatingWindows.daysKept(listOf(untilEight), meals, today - 10, today, zone)

        assertThat(judged).isEqualTo(0)
        assertThat(kept).isEqualTo(0)
    }

    @Test
    fun `an impossible hour is refused`() {
        try {
            EatingWindow(startHour = 25, endHour = 20, fromEpochDay = today)
            throw AssertionError("expected an hour of 25 to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("hour")
        }
    }
}
