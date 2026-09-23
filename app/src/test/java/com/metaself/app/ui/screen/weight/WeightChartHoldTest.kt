package com.metaself.app.ui.screen.weight

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightTrend
import com.metaself.app.domain.weight.aFortnight
import com.metaself.app.domain.weight.aReading
import com.metaself.app.ui.theme.Feel
import com.metaself.app.ui.theme.MetaSelfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Hold and drag to read a day off the weight chart (public issue #15).
 *
 * Driven through Compose's own test rule, because a hold and a drag are pointer events and only the
 * rule can inject them. A recording [HapticFeedback] stands in for the phone's, so these prove the
 * chart ASKS for a tick at the right moments; whether the phone then vibrates is a check for the
 * phone. Robolectric has no real font, so where the finger lands is chosen at the ends of the chart
 * (which a finger past either end reads) and at its middle, never at a measured pixel.
 *
 * JUnit 4 because Compose's rule demands it. `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class WeightChartHoldTest {

    @get:Rule
    val compose = createComposeRule()

    private val haptics = RecordingHaptics()

    @Test
    fun `holding the newest day reads its trend and what the scale said`() {
        chart(WeightTrend.of(aFortnight()))

        hold { centerRight - Offset(1f, 0f) }

        // aFortnight's last reading is 78.7 on Thursday 3 Sep; its trend, smoothed a tenth a day
        // from 80.0 down 100 g a day, is 79.371 there.
        compose.onNodeWithText("Thu 3 Sep · Trend 79.4 · Weighed 78.7").assertExists()
    }

    @Test
    fun `a day nobody weighed reads the trend and nothing weighed`() {
        chart(twoReadingsTenDaysApart())

        hold { center }

        val readout = compose.onNodeWithText("· Trend", substring = true)
            .fetchSemanticsNode().config
            .let { it[SemanticsProperties.Text].single().text }
        assertThat(readout).doesNotContain("Weighed")
    }

    @Test
    fun `letting go clears the reading and brings the hint back`() {
        chart(WeightTrend.of(aFortnight()))

        hold { centerRight - Offset(1f, 0f) }
        compose.onNodeWithContentDescription("Weight chart", substring = true)
            .performTouchInput { up() }
        compose.waitForIdle()

        compose.onNodeWithText("Hold and drag to read a day").assertExists()
        compose.onNodeWithText("Trend", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the finger feels a tick at each reading and nothing between them`() {
        chart(twoReadingsTenDaysApart())

        // Lands on the older reading: a tick. Across the empty middle: nothing. Onto the newer
        // reading: a second tick.
        hold { centerLeft + Offset(1f, 0f) }
        assertThat(haptics.asked).containsExactly(Feel.Tick)

        drag { center }
        assertThat(haptics.asked).containsExactly(Feel.Tick)

        drag { centerRight - Offset(1f, 0f) }
        assertThat(haptics.asked).containsExactly(Feel.Tick, Feel.Tick)
    }

    @Test
    fun `on the weight screen a hold reads the chart and does not open the bigger view`() {
        var opened = false
        chart(WeightTrend.of(aFortnight()), onOpen = { opened = true })

        hold { center }
        compose.onNodeWithContentDescription("Weight chart", substring = true)
            .performTouchInput { up() }
        compose.waitForIdle()

        assertThat(opened).isFalse()
    }

    @Test
    fun `on the weight screen a tap still opens the bigger view`() {
        var opened = false
        chart(WeightTrend.of(aFortnight()), onOpen = { opened = true })

        compose.onNodeWithContentDescription("Weight chart", substring = true).performClick()
        compose.waitForIdle()

        assertThat(opened).isTrue()
    }

    // ---- Driving ----

    private fun chart(trend: List<TrendPoint>, onOpen: (() -> Unit)? = null) {
        compose.setContent {
            MetaSelfTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    WeightChartBlock(
                        trend = trend,
                        range = ChartRange.All,
                        todayEpochDay = TEST_EPOCH_DAY,
                        onRange = {},
                        onOpen = onOpen,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /** Puts a finger down at [at] and keeps it there past the long-press timeout. */
    private fun hold(at: TouchInjectionScope.() -> Offset) {
        compose.onNodeWithContentDescription("Weight chart", substring = true).performTouchInput {
            down(at())
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            // A move after the timeout is what lets the held gesture see that time has passed.
            moveBy(Offset(0f, 0f))
        }
        compose.waitForIdle()
    }

    /** Moves the finger that is already down to [to], without lifting it. */
    private fun drag(to: TouchInjectionScope.() -> Offset) {
        compose.onNodeWithContentDescription("Weight chart", substring = true).performTouchInput {
            moveTo(to())
        }
        compose.waitForIdle()
    }

    /** Two readings, 81.0 then 80.0, ten days apart: nine days between them with nothing weighed. */
    private fun twoReadingsTenDaysApart(): List<TrendPoint> = WeightTrend.of(
        listOf(
            aReading(epochDay = TEST_EPOCH_DAY - 10, kg = 81.0),
            aReading(epochDay = TEST_EPOCH_DAY, kg = 80.0),
        ),
    )
}

private class RecordingHaptics : HapticFeedback {
    val asked = mutableListOf<HapticFeedbackType>()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        asked += hapticFeedbackType
    }
}
