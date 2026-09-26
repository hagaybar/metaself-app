package com.metaself.app.data.health

/**
 * One day's four totals from Health Connect's aggregation API, which removes the overlap between
 * apps (D12c, D69). A null is a total Health Connect did not give — no data, or no permission.
 */
data class DayTotals(
    val steps: Int? = null,
    val distanceM: Int? = null,
    val activeKcal: Int? = null,
    val totalKcal: Int? = null,
)
