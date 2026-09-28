package com.metaself.app.ui.screen.weight

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.weight.WeightTrend
import com.metaself.app.domain.weight.aFortnight
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phone turned sideways: the chart and the range chips, and nothing else.
 *
 * Sideways the height is what runs out. The title bar, the goal sentences, the captions and the
 * Close button each take a slice of it, and what is left for the plot is a strip. So both the
 * weight screen and the bigger view give the whole screen to the chart when the phone is on its
 * side, and turning it upright again brings back what was there.
 *
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "+land")
class WeightChartSidewaysRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `sideways the weight screen is only the chart and its chips`() {
        val texts = drawWeightScreen()

        assertThat(texts).containsAtLeast("1M", "3M", "6M", "1Y", "All")
        assertThat(texts).contains("Hold and drag to read a day")
        assertThat(texts).contains("80 kg")
        assertThat(texts).doesNotContain("Weight")
        assertThat(texts).doesNotContain("Log a weight")
        assertThat(texts).doesNotContain("Tap the chart for a bigger view")
        assertThat(texts).doesNotContain("The line is the trend; the dots are what the scale actually said.")
    }

    @Test
    fun `sideways the bigger view drops its title and its Close button`() {
        val texts = drawChartScreen()

        assertThat(texts).containsAtLeast("1M", "3M", "6M", "1Y", "All")
        assertThat(texts).contains("80 kg")
        assertThat(texts).doesNotContain("Weight chart")
        assertThat(texts).doesNotContain("Close")
    }

    @Test
    fun `a chip chosen sideways changes the range`() {
        var chosen: ChartRange? = null

        drawWeightScreen(onRange = { chosen = it })
        render.click("3M")

        assertThat(chosen).isEqualTo(ChartRange.Quarter)
    }

    @Test
    fun `sideways with nothing logged it still says so`() {
        val texts = render.texts {
            WeightChartScreen(
                state = WeightUiState(),
                todayEpochDay = TEST_EPOCH_DAY,
                onRange = {},
                onBack = {},
            )
        }

        assertThat(texts.any { it.startsWith("No weights logged yet") }).isTrue()
    }

    private fun fortnight(): WeightUiState {
        val readings = aFortnight()
        return WeightUiState(readings = readings, trend = WeightTrend.of(readings))
    }

    private fun drawWeightScreen(onRange: (ChartRange) -> Unit = {}): List<String> = render.texts {
        WeightScreen(
            state = fortnight(),
            todayEpochDay = TEST_EPOCH_DAY,
            justLogged = null,
            onAdd = {},
            onEdit = {},
            onDelete = {},
            onRange = onRange,
            onOpenChart = {},
            onChangeGoal = {},
            onBack = {},
        )
    }

    private fun drawChartScreen(): List<String> = render.texts {
        WeightChartScreen(
            state = fortnight(),
            todayEpochDay = TEST_EPOCH_DAY,
            onRange = {},
            onBack = {},
        )
    }
}
