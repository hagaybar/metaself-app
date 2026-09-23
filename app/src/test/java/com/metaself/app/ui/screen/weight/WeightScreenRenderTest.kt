package com.metaself.app.ui.screen.weight

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.goal.GoalProgress
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.weight.WeightTrend
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.weight.aFortnight
import com.metaself.app.domain.weight.aReading
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JUnit 4 by necessity — Robolectric's runner is JUnit 4. */
@RunWith(RobolectricTestRunner::class)
class WeightScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `with nothing logged it says so and offers to start`() {
        val texts = draw(WeightUiState())
        assertThat(texts.any { it.startsWith("No weights logged yet") }).isTrue()
        assertThat(texts).contains("Log a weight")
    }

    @Test
    fun `a fortnight of readings shows the trend and how far it moved`() {
        val texts = drawFortnight()

        assertThat(texts.any { it.endsWith(" kg") }).isTrue()
        assertThat(texts.any { it.startsWith("Down ") && it.endsWith("over 13 days") }).isTrue()
    }

    @Test
    fun `it no longer claims the calorie target ignores his weight, because it does not`() {
        // True until the weekly recalculation shipped (D11), untrue afterwards, and the day screen
        // says the opposite. Two screens of one app must not disagree about what the app does.
        assertThat(drawFortnight().any { it.contains("does not follow this yet") }).isFalse()
    }

    @Test
    fun `it says what the dots are, because a line alone asks to be trusted`() {
        assertThat(drawFortnight().any { it.contains("what the scale actually said") }).isTrue()
    }

    @Test
    fun `every reading is listed, newest first, with the day it was taken`() {
        val texts = drawFortnight()

        assertThat(texts).contains("Every reading")
        // aFortnight ends on TEST_EPOCH_DAY, which the screen is told is today.
        assertThat(texts.any { it.startsWith("Today") }).isTrue()
        assertThat(texts.any { it.startsWith("Yesterday") }).isTrue()
    }

    @Test
    fun `a single reading is still listed, so saving one visibly does something`() {
        val readings = listOf(aReading(kg = 80.5))
        val texts = draw(WeightUiState(readings = readings, trend = WeightTrend.of(readings)))

        assertThat(texts).contains("Every reading")
        assertThat(texts.any { it.startsWith("Today") && it.contains("80.5 kg") }).isTrue()
    }

    @Test
    fun `each reading can be deleted`() {
        assertThat(drawFortnight()).contains("Delete")
    }

    @Test
    fun `the big number says what it is, and the scale's own number is beside it`() {
        // The largest figure on the screen was the trend, unlabelled, which reads as the app
        // getting the weight wrong rather than smoothing it.
        val readings = listOf(
            aReading(epochDay = TEST_EPOCH_DAY - 8, kg = 81.0),
            aReading(epochDay = TEST_EPOCH_DAY, kg = 80.5),
        )
        val texts = draw(WeightUiState(readings = readings, trend = WeightTrend.of(readings)))

        assertThat(texts).contains("Your trend")
        // The trend, recomputed with the readings: eight days pull it 1 - 0.9^8 = 0.5695 of the
        // way across the 0.5 kg gap, so 81.0 - 0.285 = 80.715, shown to one decimal as 80.7.
        assertThat(texts).contains("80.7 kg")
        assertThat(texts).contains("Last weighed 80.5 kg, today")
        assertThat(texts.any { it.contains("not the same as this morning") }).isTrue()
    }

    @Test
    fun `the chart says what its axis covers`() {
        // They were computed, tested and never drawn. The unit test passed and proved nothing about
        // the screen; this one looks at the screen.
        //
        // Both figures are COMPUTED, not chosen: aFortnight starts at 80.0 and loses 100 g a day,
        // so it spans 1.3 kg, the axis widens to its two-kilogram minimum, and the ends of that
        // window are 80.4 and 78.4.
        val texts = drawFortnight()

        assertThat(texts.any { it == "80.4 kg" }).isTrue()
        assertThat(texts.any { it == "78.4 kg" }).isTrue()
    }

    @Test
    fun `each reading can be corrected`() {
        assertThat(drawFortnight()).contains("Edit")
    }

    @Test
    fun `this screen looks, it does not take input`() {
        // The form is for logging, and after logging the owner should not still be on it. So there
        // is no weight field here at all — logging opens an editor and comes back.
        val texts = draw(WeightUiState())
        assertThat(texts).doesNotContain("Weight in kilograms")
        assertThat(texts).contains("Log a weight")
    }

    @Test
    fun `it confirms what was just logged, so a save is not silent`() {
        val readings = listOf(aReading(kg = 80.5))
        val texts = render.texts {
            WeightScreen(
                state = WeightUiState(readings = readings, trend = WeightTrend.of(readings)),
                todayEpochDay = TEST_EPOCH_DAY,
                justLogged = "Logged 80.5 kg for Today.",
                onAdd = {},
                onEdit = {},
                onDelete = {},
                onRange = {},
                onOpenChart = {},
                onChangeGoal = {},
                onBack = {},
            )
        }

        assertThat(texts).contains("Logged 80.5 kg for Today.")
    }

    @Test
    fun `nothing is confirmed before anything has been logged`() {
        assertThat(draw(WeightUiState()).none { it.startsWith("Logged ") }).isTrue()
    }

    @Test
    fun `it says how far there is to go and how long that would take`() {
        val readings = aFortnight()
        val trend = WeightTrend.of(readings)
        val texts = draw(
            WeightUiState(
                readings = readings,
                trend = trend,
                progress = GoalProgress.of(Goal.lose(0.5, targetKg = 75.0), trend),
            ),
        )

        assertThat(texts.any { it.contains("kg to go, to 75 kg") }).isTrue()
        // The rate must be in the same sentence as the weeks: it is a division, not a promise.
        assertThat(texts.any { it.contains("weeks at 0.5 kg a week") }).isTrue()
    }

    /**
     * Public issue #11: the screen names the goal weight and the weekly rate, so it offers the way
     * to change them — the profile editor, where both are set.
     */
    @Test
    fun `the goal it names can be changed from here`() {
        val readings = aFortnight()
        val trend = WeightTrend.of(readings)
        var changing = false
        draw(
            WeightUiState(
                readings = readings,
                trend = trend,
                progress = GoalProgress.of(Goal.lose(0.5, targetKg = 75.0), trend),
            ),
            onChangeGoal = { changing = true },
        )

        render.click(CHANGE_GOAL)

        assertThat(changing).isTrue()
    }

    /** With no goal weight there is nothing to change, but the same editor is where one is set. */
    @Test
    fun `with no goal weight the way to set one is still there`() {
        assertThat(drawFortnight()).contains(CHANGE_GOAL)
    }

    @Test
    fun `with no goal weight it says nothing about one`() {
        val texts = drawFortnight()

        assertThat(texts.any { it.contains("to go") }).isFalse()
    }

    @Test
    fun `the chart offers the preset ranges`() {
        val texts = drawFortnight()

        assertThat(texts).contains("1M")
        assertThat(texts).contains("3M")
        assertThat(texts).contains("6M")
        assertThat(texts).contains("1Y")
        assertThat(texts).contains("All")
    }

    @Test
    fun `the chart says the dates at both ends`() {
        // The geometry test proves the two dates are computed. This one proves they are DRAWN —
        // the same gap `the chart says what its axis covers` was written to close, and the reason
        // they have to be Text in the column rather than paint inside the canvas.
        val texts = drawFortnight()

        assertThat(texts).contains("21 Aug")
        assertThat(texts).contains("3 Sep")
    }

    @Test
    fun `choosing a range asks for it to be remembered`() {
        var chosen: ChartRange? = null
        val readings = aFortnight()

        // texts { } first, always: click reads the nodes of the LAST render, and nothing else
        // fills them.
        draw(
            WeightUiState(readings = readings, trend = WeightTrend.of(readings)),
            onRange = { chosen = it },
        )
        render.click("3M")

        assertThat(chosen).isEqualTo(ChartRange.Quarter)
    }

    @Test
    fun `a range with nothing in it says so instead of drawing an empty box`() {
        // A fortnight of readings that ended ninety days ago, with the month chosen.
        val readings = aFortnight(startDay = TEST_EPOCH_DAY - 103)
        val texts = draw(
            WeightUiState(
                readings = readings,
                trend = WeightTrend.of(readings),
                range = ChartRange.Month,
            ),
        )

        assertThat(texts).contains("Nothing weighed in the last month.")
        // And no chart: the two end dates are the chart's own, and neither is drawn.
        assertThat(texts).doesNotContain("23 May")
        assertThat(texts).doesNotContain("5 Jun")
    }

    @Test
    fun `the empty sentence names the range that is empty`() {
        // One key cannot say four different things: the sentence is formatted with the chosen
        // range's own span phrase, so a six-month view can never claim to be a month.
        val readings = aFortnight(startDay = TEST_EPOCH_DAY - 213)
        val texts = draw(
            WeightUiState(
                readings = readings,
                trend = WeightTrend.of(readings),
                range = ChartRange.Quarter,
            ),
        )

        assertThat(texts).contains("Nothing weighed in the last three months.")
    }

    @Test
    fun `it says when the goal weight is off this view`() {
        // A fortnight that moved 400 g, inside the month, with the goal ten kilograms away: the
        // goal line is dropped so the readings get the height back, and the screen says so rather
        // than leaving a dashed line to be looked for.
        val readings = aFortnight(startKg = 90.0, dailyChangeKg = -0.03)
        val trend = WeightTrend.of(readings)
        val texts = draw(
            WeightUiState(
                readings = readings,
                trend = trend,
                progress = GoalProgress.of(Goal.lose(0.5, targetKg = 80.0), trend),
                range = ChartRange.Month,
            ),
        )

        assertThat(texts.any { it.startsWith("Your goal weight is off this view") }).isTrue()
    }

    @Test
    fun `tapping the chart opens the bigger view`() {
        var opened = false
        val readings = aFortnight()

        draw(
            WeightUiState(readings = readings, trend = WeightTrend.of(readings)),
            onOpenChart = { opened = true },
        )
        render.click("Tap the chart")

        assertThat(opened).isTrue()
    }

    private fun drawFortnight(): List<String> {
        val readings = aFortnight()
        return draw(WeightUiState(readings = readings, trend = WeightTrend.of(readings)))
    }

    private fun draw(
        state: WeightUiState,
        onRange: (ChartRange) -> Unit = {},
        onOpenChart: () -> Unit = {},
        onChangeGoal: () -> Unit = {},
    ): List<String> = render.texts {
        WeightScreen(
            state = state,
            todayEpochDay = TEST_EPOCH_DAY,
            justLogged = null,
            onAdd = {},
            onEdit = {},
            onDelete = {},
            onRange = onRange,
            onOpenChart = onOpenChart,
            onChangeGoal = onChangeGoal,
            onBack = {},
        )
    }

    private companion object {
        /** `R.string.weight_change_goal`, as the phone draws it. */
        const val CHANGE_GOAL = "Change your goal"
    }
}
