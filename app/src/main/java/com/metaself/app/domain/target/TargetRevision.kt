package com.metaself.app.domain.target

/**
 * One weekly recalculation of the daily target.
 *
 * Everything needed to explain the change is kept, because decision D11's requirement is not that
 * the target follows the trend but that it says so when it does. A stored figure with no account of
 * where it came from is exactly the number D11 says people stop trusting.
 *
 * @property epochDay the day the revision was made, which is what the weekly rhythm counts from.
 * @property trendKg the smoothed weight it was worked out from — not a reading.
 * @property previousKcal what the target was before, or null for the first revision.
 */
data class TargetRevision(
    val epochDay: Long,
    val trendKg: Double,
    val kcal: Int,
    val previousKcal: Int?,
) {
    /** How far the target moved. Zero when there was nothing before it to move from. */
    val changeKcal: Int get() = if (previousKcal == null) 0 else kcal - previousKcal

    /** Whether there is anything worth telling the owner about. */
    val isChange: Boolean get() = changeKcal != 0
}
