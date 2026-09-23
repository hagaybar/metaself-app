package com.metaself.app.ui.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.anItem
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * When a meal was eaten, as the day shows it (D33).
 *
 * The time belongs to the meal: a meal logged on its own day shows the time it was logged, and one
 * written down on a later day — whose stored moment is the moment of writing, on another date —
 * shows that its time is not known, which is the one the owner most needs to set.
 */
class EatenAtWordingTest {

    private val zone: ZoneId = ZoneId.of("UTC+01:00")
    private val day = LocalDate.of(2026, 9, 17)

    private fun at(date: LocalDate, hour: Int, minute: Int) =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant()
            .toEpochMilli()

    private fun mealAt(millis: Long) = Meal(
        id = 7,
        epochDay = day.toEpochDay(),
        loggedAtMillis = millis,
        items = listOf(anItem(name = "Omelette")),
    )

    @Test
    fun `a meal logged on its own day shows the time it was eaten`() {
        assertThat(DayTotalsWording.eatenAt(mealAt(at(day, 9, 30)), zone)).isEqualTo("09:30")
    }

    /**
     * Written down the next morning onto yesterday, its stored moment is the next morning: another
     * date. That is not a time on this day, and showing it would state a time he did not eat at.
     */
    @Test
    fun `a meal written down on a later day has no time to show`() {
        assertThat(DayTotalsWording.eatenAt(mealAt(at(day.plusDays(1), 8, 0)), zone)).isNull()
    }

    @Test
    fun `just before midnight is still the day's own time`() {
        assertThat(DayTotalsWording.eatenAt(mealAt(at(day, 23, 59)), zone)).isEqualTo("23:59")
    }

    /**
     * What a screen reader hears, which is also how any test or walk tells two times apart: each
     * names its meal, so two meals at 12:00 are not two controls called "12:00".
     */
    @Test
    fun `the time names its meal, so two meals at one time are still two different controls`() {
        val meal = mealAt(at(day, 12, 0))

        assertThat(DayTotalsWording.eatenAtDescription(meal, zone))
            .isEqualTo("Eaten at 12:00: Omelette. Tap to change the time.")
        assertThat(DayTotalsWording.eatenAtDescription(mealAt(at(day.plusDays(1), 8, 0)), zone))
            .isEqualTo("Time not known: Omelette. Tap to set it.")
    }

    @Test
    fun `a time later than now is refused in words that say why`() {
        assertThat(DayTotalsWording.laterThanNow(hour = 21, minute = 30))
            .isEqualTo("21:30 has not happened yet. A meal can only be put at a time already past.")
    }
}
