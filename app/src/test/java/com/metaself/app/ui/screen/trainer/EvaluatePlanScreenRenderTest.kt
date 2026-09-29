package com.metaself.app.ui.screen.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Evaluation
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
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4. Which words the evaluate page draws (D93, D94),
 * and that each door calls back. TEST_EPOCH_DAY is Thursday 3 September 2026; every word is invented.
 */
@RunWith(RobolectricTestRunner::class)
class EvaluatePlanScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the form has its two rows, the words, and a privacy line naming the last evaluation`() {
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY))

        assertThat(texts).containsAtLeast("HOW MANY WEEKS", "2 weeks", "4 weeks", "6 weeks", "SESSIONS A WEEK I CAN MANAGE", "2", "5").inOrder()
        assertThat(texts.any { it.contains("your last evaluation and how its plan went") }).isTrue()
        assertThat(render.isEnabled("Ask the trainer")).isFalse()
    }

    /** D4: the evaluation and the plan are labelled advice. */
    @Test
    fun `the answer shows where you stand, the plan with its dates, and Keep and Ask again`() {
        var kept = false
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, shown = SHOWN), onKeep = { kept = true })

        assertThat(texts).contains("From the AI trainer · advice, not a measurement")
        assertThat(texts).containsAtLeast("WHERE YOU STAND", "Invented headline.", "Going well.", "To work on.").inOrder()
        assertThat(texts).doesNotContain("Since last time.")
        assertThat(texts).containsAtLeast("THE PLAN · 2 WEEKS · 2 SESSIONS A WEEK", "Invented plan", "Starts Mon 31 Aug, ends Sun 13 Sep").inOrder()
        assertThat(texts).containsAtLeast("Week 1 · settle in", "Easy walk, 30 min", "Invented line").inOrder()
        assertThat(texts).contains("Keeping it replaces any plan you have now. The evaluation is kept either way.")
        render.click("Keep this plan")
        assertThat(kept).isTrue()
    }

    @Test
    fun `a kept answer says so, and the running plan shows its ticks`() {
        assertThat(draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, shown = SHOWN, kept = true)))
            .contains("Kept. It is on the Trainer screen.")

        val running = PlanCard.of(SHOWN.copy(startEpochDay = TEST_EPOCH_DAY - 3, status = ProgrammeStatus.RUNNING), emptyList(), TEST_EPOCH_DAY) as PlanCard.Running
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, onRunning = true, running = running, evaluation = SHOWN.evaluation))
        assertThat(texts).contains("Your plan")
        assertThat(texts).contains("To do: Easy walk, 30 min")
        assertThat(texts).doesNotContain("Keep this plan")
    }

    private fun draw(
        state: EvaluatePlanViewModel.State,
        onKeep: () -> Unit = {},
    ): List<String> = render.texts {
        EvaluatePlanScreen(state = state, onBack = {}, onChange = {}, onAsk = {}, onKeep = onKeep, onAskAgain = {})
    }

    private companion object {
        val WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val SHOWN = Programme(
            id = 1, createdAtMillis = 0, ask = ProgrammeAsk(2, 2),
            evaluation = Evaluation("Invented headline.", "Invented going well.", "Invented to work on.", ""),
            plan = WeeksPlan("Invented plan", listOf(PlanWeek("settle in", listOf(WALK, WALK)), PlanWeek("", listOf(WALK))), "Invented reason."),
            model = "a-model",
        )
    }
}
