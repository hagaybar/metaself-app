package com.metaself.app.ui.screen.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4. Which words the adjust page draws (D97), and
 * that each door calls back. The Stop question is drawn in a window of its own, which this helper
 * does not read; AdjustPlanStopSessionTest presses through it. TEST_EPOCH_DAY is Thursday 3 September 2026; the plan runs from Monday
 * 31 August; every word is invented.
 */
@RunWith(RobolectricTestRunner::class)
class AdjustPlanScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()
    @Test
    fun `the page says what is counted, what is rewritten, and when the plan ends`() {
        val texts = draw(AdjustPlanViewModel.State(loading = false, running = RUNNING, today = TEST_EPOCH_DAY))

        assertThat(texts).containsAtLeast("SO FAR, COUNTED ON THE PHONE", "Week 1 (this week): 0 of 2 so far", "Week 2: to be rewritten").inOrder()
        assertThat(texts.any { it.startsWith("The trainer rewrites this week's remaining sessions") && it.endsWith("The end date stays Sun 13 Sep.") }).isTrue()
        assertThat(texts.any { it.startsWith("Sends to OpenAI, with your key: your plan, how it has gone, and your words;") }).isTrue()
        assertThat(texts).contains("Stop this plan")
    }

    @Test
    fun `a new version shows with Keep this version and Keep the old one`() {
        var keptNew = false
        val texts = draw(
            AdjustPlanViewModel.State(loading = false, running = RUNNING, shown = RUNNING.programme.copy(id = 2, replacesId = 1), today = TEST_EPOCH_DAY),
            onKeepNew = { keptNew = true },
        )

        assertThat(texts.count { it == "From the AI trainer · advice, not a measurement" }).isEqualTo(1)
        assertThat(render.isDrawnBefore("From the AI trainer", "THE PLAN")).isTrue()
        assertThat(texts).contains("Keep the old one")
        render.click("Keep this version")
        assertThat(keptNew).isTrue()
    }

    /** In the plan's last week there is no week after it: the page names this week's rest. */
    @Test
    fun `in the last week the page says this week's rest is rewritten`() {
        val last = PlanCard.of(RUNNING.programme.copy(startEpochDay = TEST_EPOCH_DAY - 10), emptyList(), TEST_EPOCH_DAY) as PlanCard.Running
        val texts = draw(AdjustPlanViewModel.State(loading = false, running = last, today = TEST_EPOCH_DAY))

        assertThat(texts).containsAtLeast("Week 1: 0 of 2 done", "Week 2 (this week): 0 of 2 so far", "Week 2: the rest to be rewritten").inOrder()
    }

    /** While an adjustment is asked for, or a write is under way, Stop cannot be pressed; nor can the keeps while writing. */
    @Test
    fun `stop and the keeps cannot be pressed while asking or writing`() {
        draw(AdjustPlanViewModel.State(loading = false, running = RUNNING, asking = true, today = TEST_EPOCH_DAY))
        assertThat(render.isEnabled("Stop this plan")).isFalse()

        draw(AdjustPlanViewModel.State(loading = false, running = RUNNING, today = TEST_EPOCH_DAY))
        assertThat(render.isEnabled("Stop this plan")).isTrue()

        draw(
            AdjustPlanViewModel.State(
                loading = false, running = RUNNING, shown = RUNNING.programme.copy(id = 2, replacesId = 1), writing = true, today = TEST_EPOCH_DAY,
            ),
        )
        assertThat(render.isEnabled("Keep this version")).isFalse()
        assertThat(render.isEnabled("Keep the old one")).isFalse()
    }

    private fun draw(
        state: AdjustPlanViewModel.State,
        onKeepNew: () -> Unit = {},
    ): List<String> = render.texts {
        AdjustPlanScreen(
            state = state, onBack = {}, onWords = {}, onAdjust = {}, onKeepNew = onKeepNew, onKeepOld = {},
            onAskStop = {}, onCancelStop = {}, onConfirmStop = {}, onFinished = {},
        )
    }

    private companion object {
        val WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val RUNNING = PlanCard.of(
            Programme(
                1, 0, ProgrammeAsk(2, 2), null, WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK, WALK)) }, "Invented."),
                "a-model", TEST_EPOCH_DAY - 3, ProgrammeStatus.RUNNING,
            ),
            emptyList(),
            TEST_EPOCH_DAY,
        ) as PlanCard.Running
    }
}
