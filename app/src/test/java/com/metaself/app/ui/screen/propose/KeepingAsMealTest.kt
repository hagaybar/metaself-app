package com.metaself.app.ui.screen.propose

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.anItem
import org.junit.jupiter.api.Test

/**
 * When the accept screen has a meal to name, and when it has not (D46, issue #24).
 *
 * Pure, so JUnit 5 — `org.junit.jupiter.api.Test`. It is here rather than inside a render test for
 * the reason the rule exists at all: a modal bottom sheet draws into a window of its own that the
 * render helper cannot see, so a rule written as an `if` around the sheet is a rule nothing can
 * check. Written as a function, both ways of getting it wrong are one assertion each.
 */
class KeepingAsMealTest {

    private val rows = listOf(
        anItem(id = 1, name = "Milk", portion = "120 ml", portionAmount = 120.0, portionUnit = "ml"),
        anItem(id = 2, name = "Espresso", portion = "1 cup", portionAmount = 1.0, portionUnit = "cup"),
    )

    /**
     * The tap comes first and the rows arrive after it: the write is a coroutine and the day's
     * state is a combined flow, so there is a frame, and usually more than one, in between.
     *
     * A sheet opened in that frame lists nothing and totals 0 kcal — in front of a Confirm that
     * would make nothing, because naming an empty choice makes nothing.
     */
    @Test
    fun `the sheet does not open before the rows have been logged`() {
        val keeping = keepingAsMeal(taken = true, rows = emptyList(), isToday = true, refusal = null)

        assertThat(keeping).isNull()
    }

    /** Once they are on the day, the sheet names exactly them, and says so about the right day. */
    @Test
    fun `once the rows are logged the sheet holds exactly them`() {
        val keeping = keepingAsMeal(taken = true, rows = rows, isToday = false, refusal = null)

        assertThat(keeping).isNotNull()
        assertThat(keeping!!.rows).isEqualTo(rows)
        assertThat(keeping.isToday).isFalse()
    }

    /**
     * A choice may already be standing when he goes to describe something — *Describe a meal* is on
     * the day, and ticking rows there does not take it away. Rows alone would open a sheet he never
     * asked for, over rows he did not just log.
     */
    @Test
    fun `rows chosen without the offer being taken draw no sheet`() {
        val keeping = keepingAsMeal(taken = false, rows = rows, isToday = true, refusal = null)

        assertThat(keeping).isNull()
    }

    /**
     * A refusal is shown where he is when it arrives, with the name he typed still in the field.
     *
     * The choice stands through a refusal, which is exactly what keeps the sheet open to show it.
     */
    @Test
    fun `a refusal is carried into the sheet while the rows stand`() {
        val keeping = keepingAsMeal(
            taken = true,
            rows = rows,
            isToday = true,
            refusal = "You already have a meal called “Cappuccino”.",
        )

        assertThat(keeping?.refusal).isEqualTo("You already have a meal called “Cappuccino”.")
    }

    // --- Opening once, leaving once, and never before it has opened ----------------------------

    /**
     * The accept screen is reached to describe a meal, and most times nothing is ever named on it.
     *
     * If "nothing to name" meant "finished", the screen would take him back to the day on its own
     * first composition, before he had typed a word.
     */
    @Test
    fun `a screen that has never named anything does not take him back`() {
        val step = namingSheetStep(hasSomethingToName = false, hasBeenOpen = false)

        assertThat(step).isEqualTo(NamingSheetStep.WAIT)
    }

    /** The rows have landed: the sheet is on screen, and that it has been is worth remembering. */
    @Test
    fun `rows to name open the sheet`() {
        val step = namingSheetStep(hasSomethingToName = true, hasBeenOpen = false)

        assertThat(step).isEqualTo(NamingSheetStep.OPEN)
    }

    /**
     * A refusal leaves the choice standing, so there is still something to name.
     *
     * The sheet must stay open over it, with the typed name still in the field — closing on the tap
     * that asked for the meal would throw the name away before anything knew whether it had worked.
     */
    @Test
    fun `an open sheet with rows still to name stays open`() {
        val step = namingSheetStep(hasSomethingToName = true, hasBeenOpen = true)

        assertThat(step).isEqualTo(NamingSheetStep.OPEN)
    }

    /** Made, or given up on: the day has nothing left to name, so this screen is done with. */
    @Test
    fun `a sheet that has been open and has nothing left to name goes back to the day`() {
        val step = namingSheetStep(hasSomethingToName = false, hasBeenOpen = true)

        assertThat(step).isEqualTo(NamingSheetStep.LEAVE)
    }
}
