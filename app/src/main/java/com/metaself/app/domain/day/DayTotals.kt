package com.metaself.app.domain.day

/**
 * What a day's meals add up to.
 *
 * Integers throughout, matching what is stored. There is no rounding here to disagree with the
 * rounding anywhere else, because nothing is divided.
 */
data class DayTotals(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
) {
    companion object {

        val NOTHING = DayTotals(kcal = 0, proteinG = 0, carbsG = 0, fatG = 0)

        fun of(meals: List<Meal>): DayTotals {
            val items = meals.flatMap { it.items }
            return DayTotals(
                kcal = items.sumOf { it.kcal },
                proteinG = items.sumOf { it.proteinG },
                carbsG = items.sumOf { it.carbsG },
                fatG = items.sumOf { it.fatG },
            )
        }
    }
}
