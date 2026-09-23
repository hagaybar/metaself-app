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

/**
 * The chart on its own, full screen.
 *
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 */
@RunWith(RobolectricTestRunner::class)
class WeightChartScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the full-screen chart shows the same chips`() {
        val texts = drawFortnight()

        assertThat(texts).contains("1M")
        assertThat(texts).contains("3M")
        assertThat(texts).contains("6M")
        assertThat(texts).contains("1Y")
        assertThat(texts).contains("All")
    }

    @Test
    fun `the full-screen chart shows the axis and the dates`() {
        val texts = drawFortnight()

        // The same four labels the weight screen draws: what the axis covers, and the two ends of
        // what is shown. A bigger chart that lost them would be a bigger picture of less.
        //
        // The two kilogram figures are COMPUTED from the fixture: aFortnight starts at 80.0 and
        // loses 100 g a day, so the axis widens to its two-kilogram minimum around that data and
        // its ends are 80.4 and 78.4.
        assertThat(texts).contains("80.4 kg")
        assertThat(texts).contains("78.4 kg")
        assertThat(texts).contains("21 Aug")
        assertThat(texts).contains("3 Sep")
    }

    @Test
    fun `it can be closed`() {
        // This screen's OWN Close button, not the shared top bar's arrow. That arrow carries only a
        // content description, and click matches on text alone — so click("Back") could never find
        // it, and giving the shared bar a visible word would change the title bar of every screen
        // in the app. A second way out of a full-screen view is a real affordance anyway.
        var closed = false

        drawFortnight(onBack = { closed = true })
        render.click("Close")

        assertThat(closed).isTrue()
    }

    @Test
    fun `with nothing logged it says so rather than showing an empty frame`() {
        val texts = draw(WeightUiState())

        assertThat(texts.any { it.startsWith("No weights logged yet") }).isTrue()
    }

    private fun drawFortnight(onBack: () -> Unit = {}): List<String> {
        val readings = aFortnight()
        return draw(
            WeightUiState(readings = readings, trend = WeightTrend.of(readings)),
            onBack = onBack,
        )
    }

    private fun draw(
        state: WeightUiState,
        onRange: (ChartRange) -> Unit = {},
        onBack: () -> Unit = {},
    ): List<String> = render.texts {
        WeightChartScreen(
            state = state,
            todayEpochDay = TEST_EPOCH_DAY,
            onRange = onRange,
            onBack = onBack,
        )
    }
}
