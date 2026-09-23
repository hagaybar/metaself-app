package com.metaself.app.ui.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.PartOfTheClock
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import org.junit.jupiter.api.Test

/**
 * The words on a part-of-the-clock row (D51).
 *
 * Pure, so it is tested here and not through a render: what the row SAYS is decided by these four
 * answers, and a render test can only check that they reached the screen.
 */
class DayPartWordingTest {

    /**
     * The band ends at the last minute of the part, never at the first minute of the next.
     *
     * "12:00–18:00" is ambiguous at exactly the boundary the row exists to make unambiguous: 18:00
     * is Evening. A range printed to make a rule followable must not need a footnote, and this is
     * the assertion that keeps it that way.
     */
    @Test
    fun `a part's hours end at its last minute`() {
        assertThat(DayPartWording.hours(PartOfTheClock.NIGHT)).isEqualTo("00:00–05:59")
        assertThat(DayPartWording.hours(PartOfTheClock.MORNING)).isEqualTo("06:00–11:59")
        assertThat(DayPartWording.hours(PartOfTheClock.MIDDAY)).isEqualTo("12:00–17:59")
        assertThat(DayPartWording.hours(PartOfTheClock.EVENING)).isEqualTo("18:00–23:59")
    }

    /** The name and the hours together, because the rule belongs on the screen it governs. */
    @Test
    fun `the heading is the part's name and its hours`() {
        assertThat(DayPartWording.heading("Midday", PartOfTheClock.MIDDAY))
            .isEqualTo("Midday · 12:00–17:59")
    }

    /**
     * The untimed row gets NO hours, because it has none.
     *
     * Its loggings were written down on another day, so the moment stored against them says when
     * they were typed and nothing about when they were eaten (D33). Printing a band of hours beside
     * that sentence would be the invention this whole decision refuses.
     */
    @Test
    fun `the untimed row's heading is its own words alone`() {
        assertThat(DayPartWording.heading("Time not known", null)).isEqualTo("Time not known")
    }

    /** A meal he built keeps the name he gave it. */
    @Test
    fun `a meal he built lends the row its name`() {
        val salad = aMeal(
            items = listOf(anItem(name = "Cucumber"), anItem(name = "Olive oil")),
            savedMealId = 1,
            savedMealName = "Vegetable salad",
        )

        assertThat(DayPartWording.what(listOf(salad))).isEqualTo("Vegetable salad")
    }

    /**
     * Everything with no name of its own — the ordinary case — lends its first food's
     * name instead.
     *
     * The same fallback the time's spoken description and the time picker already use, so a row
     * never has to invent a label for itself. An earlier draft of this row said "the time and the
     * total alone" when there was no name, which is broken in fact: the time is unknown for
     * anything written down on another day, so that row would have read "--:--  180 kcal" with
     * nothing identifying it — on exactly the entries the colophon tells him to go and fix.
     */
    @Test
    fun `a logging with no name of its own lends its first food's name`() {
        val typed = aMeal(items = listOf(anItem(name = "Porridge"), anItem(name = "Coffee")))

        assertThat(DayPartWording.what(listOf(typed))).isEqualTo("Porridge")
    }

    /** The FIRST logging in the row names it; the grouping has already put them in time order. */
    @Test
    fun `the row is named after the first logging in it`() {
        val first = aMeal(id = 1, items = listOf(anItem(name = "Shawarma")))
        val second = aMeal(id = 2, items = listOf(anItem(name = "Hummus")))

        assertThat(DayPartWording.what(listOf(first, second))).isEqualTo("Shawarma")
    }

    /**
     * How many things are behind the row: a meal he built is ONE, and everything else counts its
     * foods.
     *
     * That is the count of what the record would show him, which is what the number is for — how
     * much is behind the door, not how many times he happened to press save.
     */
    @Test
    fun `a meal he built counts as one thing and everything else counts its foods`() {
        val salad = aMeal(
            id = 1,
            items = listOf(anItem(name = "Cucumber"), anItem(name = "Olive oil")),
            savedMealId = 1,
            savedMealName = "Vegetable salad",
        )
        val typed = aMeal(id = 2, items = listOf(anItem(name = "Porridge"), anItem(name = "Coffee")))

        assertThat(DayPartWording.size(listOf(salad))).isEqualTo(1)
        assertThat(DayPartWording.size(listOf(typed))).isEqualTo(2)
        assertThat(DayPartWording.size(listOf(salad, typed))).isEqualTo(3)
    }

    /** One thing is no count at all — the name has already said what the only thing is. */
    @Test
    fun `one thing is counted as one, and the screen draws no count for it`() {
        assertThat(DayPartWording.size(listOf(aMeal(items = listOf(anItem()))))).isEqualTo(1)
    }

    /**
     * The total is summed from the loggings' own rows, across every logging in the part.
     *
     * **There is no `Meal.kcal`**, and a meal definition's stored total is the wrong number for
     * anything logged adjusted: a row reading that would disagree with the figure at the top of the
     * day on exactly the days he changed something.
     */
    @Test
    fun `the total sums every logging in the part`() {
        val first = aMeal(id = 1, items = listOf(anItem(name = "Porridge", kcal = 180)))
        val second = aMeal(
            id = 2,
            items = listOf(anItem(name = "Coffee", kcal = 20), anItem(name = "Milk", kcal = 30)),
        )

        assertThat(DayPartWording.total(listOf(first, second))).isEqualTo("230 kcal")
    }

    /** Grouped, like every other figure in this app: four digits are read, not counted. */
    @Test
    fun `a four-figure total is grouped`() {
        val big = aMeal(items = listOf(anItem(kcal = 1_240)))

        assertThat(DayPartWording.total(listOf(big))).isEqualTo("1,240 kcal")
    }
}
