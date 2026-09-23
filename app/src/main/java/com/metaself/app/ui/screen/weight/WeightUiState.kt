package com.metaself.app.ui.screen.weight

import com.metaself.app.domain.goal.GoalForecast
import com.metaself.app.domain.goal.GoalProgress
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightReading

/**
 * What the weight screen is showing.
 *
 * Not a sealed hierarchy: there is no meaningful "loading" here, because an empty list and a list
 * not yet read look the same on screen and the difference would only ever produce a flicker.
 */
data class WeightUiState(
    val readings: List<WeightReading> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    /** How far there is to go, or null when the goal has no destination (D21). */
    val progress: GoalProgress? = null,
    /** Both finish lines, and the rate the trend has actually been moving at (D47). */
    val forecast: GoalForecast? = null,
    /** How much history the chart is showing, remembered between visits. */
    val range: ChartRange = ChartRange.All,
)
