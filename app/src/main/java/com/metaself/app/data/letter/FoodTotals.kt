package com.metaself.app.data.letter

import com.metaself.app.data.day.MealDao
import com.metaself.app.domain.day.DayTotals
import javax.inject.Inject

/** Each day's food totals over a span (D100) — the only thing the letter reads of what was eaten. */
fun interface FoodTotals {
    suspend fun byDay(from: Long, to: Long): Map<Long, DayTotals>
}

class RoomFoodTotals @Inject constructor(private val meals: MealDao) : FoodTotals {
    override suspend fun byDay(from: Long, to: Long): Map<Long, DayTotals> =
        meals.totalsByDayBetween(from, to).associate { it.epochDay to DayTotals(it.kcal, it.proteinG, it.carbsG, it.fatG) }
}
