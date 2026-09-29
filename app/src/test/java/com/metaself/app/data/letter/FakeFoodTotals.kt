package com.metaself.app.data.letter

import com.metaself.app.domain.day.DayTotals

/** Each day's totals as given; only the asked span is handed back, as the query does. */
class FakeFoodTotals(var days: Map<Long, DayTotals> = emptyMap()) : FoodTotals {
    override suspend fun byDay(from: Long, to: Long): Map<Long, DayTotals> = days.filterKeys { it in from..to }
}
