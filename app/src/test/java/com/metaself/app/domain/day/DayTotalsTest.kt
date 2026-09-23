package com.metaself.app.domain.day

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DayTotalsTest {

    @Test
    fun `a day with nothing logged has eaten nothing`() {
        assertThat(DayTotals.of(emptyList())).isEqualTo(DayTotals.NOTHING)
    }

    @Test
    fun `one meal of one item totals that item`() {
        val totals = DayTotals.of(listOf(aMeal(items = listOf(anItem(kcal = 600)))))
        assertThat(totals.kcal).isEqualTo(600)
    }

    @Test
    fun `items within a meal add up`() {
        val meal = aMeal(
            items = listOf(
                anItem(kcal = 600, proteinG = 40, carbsG = 50, fatG = 25),
                anItem(name = "Hummus", kcal = 180, proteinG = 6, carbsG = 12, fatG = 12),
            ),
        )
        val totals = DayTotals.of(listOf(meal))
        assertThat(totals.kcal).isEqualTo(780)
        assertThat(totals.proteinG).isEqualTo(46)
        assertThat(totals.carbsG).isEqualTo(62)
        assertThat(totals.fatG).isEqualTo(37)
    }

    @Test
    fun `meals across the day add up`() {
        val totals = DayTotals.of(
            listOf(
                aMeal(items = listOf(anItem(kcal = 600))),
                aMeal(items = listOf(anItem(name = "Yoghurt", kcal = 150))),
            ),
        )
        assertThat(totals.kcal).isEqualTo(750)
    }

    @Test
    fun `a meal must hold something, because a meal of nothing is not a record`() {
        try {
            aMeal(items = emptyList())
            throw AssertionError("expected an empty meal to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("empty")
        }
    }
}
