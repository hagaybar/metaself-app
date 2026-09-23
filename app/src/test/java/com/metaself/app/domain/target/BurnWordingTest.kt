package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.target.BurnWording
import org.junit.jupiter.api.Test

class BurnWordingTest {

    private val losing = MeasuredBurn(
        days = 28,
        daysLogged = 26,
        averageLoggedKcal = 2_150,
        trendChangeKg = -1.2,
        fromStoresKcalPerDay = 330,
        measuredKcal = 2_480,
        formulaKcal = 2_640,
    )

    /** D9: he is being asked to accept a number that moved, so he sees what moved it. */
    @Test
    fun `it shows the sum rather than the conclusion`() {
        val text = BurnWording.arithmetic(losing)

        assertThat(text).contains("2150 kcal a day")
        assertThat(text).contains("26 of them")
        assertThat(text).contains("1.2 kg")
        assertThat(text).contains("330 kcal a day")
        assertThat(text).contains("about 2480 kcal")
    }

    @Test
    fun `a generous formula is named as generous`() {
        val text = BurnWording.againstTheFormula(losing)

        assertThat(text).contains("2640")
        assertThat(text).contains("160 kcal generous")
        assertThat(text).contains("coming down")
    }

    @Test
    fun `a short formula is named as short`() {
        val text = BurnWording.againstTheFormula(losing.copy(measuredKcal = 2_800))

        assertThat(text).contains("160 kcal short")
        assertThat(text).contains("going up")
    }

    @Test
    fun `a small difference is left alone rather than fussed over`() {
        val text = BurnWording.againstTheFormula(losing.copy(measuredKcal = 2_650))

        assertThat(text).contains("close enough to leave alone")
    }

    @Test
    fun `a rising trend reads as going into store, not out of it`() {
        val gaining = losing.copy(trendChangeKg = 0.4, fromStoresKcalPerDay = -110)

        val text = BurnWording.arithmetic(gaining)
        assertThat(text).contains("went up 0.4 kg")
        assertThat(text).contains("into store")
    }

    /**
     * The one assumption the arithmetic cannot check, and the one that breaks it. It is stated every
     * time and it says what actually matters — consistency, not accuracy (D25a).
     */
    @Test
    fun `the assumption is stated plainly and asks for consistency, not accuracy`() {
        // Checked on the line the screen actually shows, measured or not yet: it is said every time.
        listOf(
            BurnWording.lines(measured = null, standingKcal = 0, daysLoggedRecently = 3),
            BurnWording.lines(measured = losing, standingKcal = -160, daysLoggedRecently = 28),
        ).forEach { lines ->
            val assumption = lines.last()
            assertThat(assumption.heading).contains("what you logged is what you ate")
            assertThat(assumption.detail).contains("does not have to be exact")
            assertThat(assumption.detail).contains("consistent")
        }
    }

    /** It must never be worded as a fact about his body. */
    @Test
    fun `nothing claims to know his metabolism`() {
        val everything = listOf(
            BurnWording.arithmetic(losing),
            BurnWording.againstTheFormula(losing),
            BurnWording.lines(measured = losing, standingKcal = -160, daysLoggedRecently = 28)
                .joinToString(" ") { it.heading + " " + it.detail },
            BurnWording.standing(-160).orEmpty(),
        ).joinToString(" ").lowercase()

        listOf("metabolism", "metabolic", "tdee", "burn rate", "your body burns")
            .forEach { assertThat(everything).doesNotContain(it) }
    }

    @Test
    fun `with no correction there is nothing to report`() {
        assertThat(BurnWording.standing(0)).isNull()
        assertThat(BurnWording.standing(-160)).contains("down by 160")
        assertThat(BurnWording.standing(75)).contains("up by 75")
    }

    @Test
    fun `before there is enough it says how much is missing`() {
        val text = BurnWording.notYet(daysLogged = 14, daysNeeded = 22)

        assertThat(text).contains("14 of the last 28")
        assertThat(text).contains("22 are needed")
    }
}
