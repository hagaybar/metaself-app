package com.metaself.app.ui.screen.trainer

import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.DatedEvaluation
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.PlanShown
import com.metaself.app.domain.trainer.PlanTimeline
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.Programmes
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4. Which words the evaluate page and the plan screen draw
 * (D93, D94, D117, D118), and that each door calls back. TEST_EPOCH_DAY is Thursday 3 September 2026; the plan
 * is kept from Monday 24 August, so today is in its week 2; every word is invented.
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
        assertThat(texts.any { it.contains("your last evaluation and how its plan went") && it.endsWith("Never meals.") }).isTrue()
    }

    /** As the approved mock-up: 4 weeks and 3 sessions a week are chosen, so Ask is ready at once. */
    @Test
    fun `the form opens on 4 weeks and 3 sessions a week, ready to ask`() {
        draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY))

        assertThat(render.isSelected("4 weeks")).isTrue()
        assertThat(render.isSelected("2 weeks")).isFalse()
        assertThat(render.isSelected("3")).isTrue()
        assertThat(render.isEnabled("Ask the trainer")).isTrue()

        draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, form = EvaluatePlanViewModel.Form(weeks = 4)))
        assertThat(render.isEnabled("Ask the trainer")).isFalse()
    }

    @Test
    fun `the running plan is drawn as its weeks, in D117's order`() {
        val texts = draw(running(workouts = listOf(TrainerScreens.walk(1, MONDAY))))

        assertThat(texts.count { it == "From the AI trainer · advice, not a measurement" }).isEqualTo(1)
        assertThat(texts).containsAtLeast(
            "From the AI trainer · advice, not a measurement",
            "Invented plan", "4 weeks · 24 Aug – 20 Sep · you're in week 2",
            "Week 1 · settle in", "0 of 2 planned",
            "Week 2 · add a walk — this week", "Easy 30′", "Done Mon", "Steady 45′", "Open",
            "Week 3 · hold it", "Easy 30′ · Steady 45′",
            "Week 4", "Easy 30′",
            "WHERE YOU STOOD · SUN 30 AUG", "Invented headline.", "Read the trainer's full evaluation",
            "Adjust the plan", "Plan the open walk",
        ).inOrder()
        assertThat(texts).doesNotContain("Going well.")
        assertThat(texts).doesNotContain("Keep this plan")
    }

    @Test
    fun `the full evaluation opens from its line, with the plan's why`() {
        draw(running())
        render.click("Read the trainer's full evaluation")

        assertThat(render.textsAgain()).containsAtLeast(
            "Invented headline.", "Hide the trainer's full evaluation", "Going well.", "Invented going well.",
            "To work on.", "Invented to work on.", "Why.", "Invented reason.",
        ).inOrder()
        assertThat(render.textsAgain()).doesNotContain("Since last time.")
    }

    @Test
    fun `a plan with no evaluation shows neither its headline nor the full evaluation`() {
        val texts = draw(running(evaluation = null))

        assertThat(texts.none { it.startsWith("WHERE YOU STOOD") }).isTrue()
        assertThat(texts).doesNotContain("Read the trainer's full evaluation")
    }

    @Test
    fun `while it runs, Adjust the plan and the main action, and with nothing open Plan my next session`() {
        val opened = mutableListOf<String>()
        val done = listOf(TrainerScreens.walk(1, MONDAY), TrainerScreens.walk(2, MONDAY + 1).copy(durationMinutes = 45))
        draw(running(workouts = done), onAdjust = { opened += "adjust" }, onPlan = { opened += "plan" })

        assertThat(render.roleOf("Adjust the plan")).isEqualTo(Role.Button)
        render.click("Adjust the plan")
        render.click("Plan my next session")
        assertThat(opened).containsExactly("adjust", "plan").inOrder()
        assertThat(render.textsAgain()).doesNotContain("Evaluate me and plan again")
    }

    @Test
    fun `ended or stopped, one button — Evaluate me and plan again`() {
        var evaluated = 0
        val ended = draw(running(today = START + 28), onEvaluate = { evaluated++ })
        assertThat(ended).contains("4 weeks · 24 Aug – 20 Sep · ended Sun 20 Sep")
        assertThat(ended).doesNotContain("Adjust the plan")
        render.click("Evaluate me and plan again")

        val stopped = draw(running(stoppedOn = MONDAY + 1), onEvaluate = { evaluated++ })
        assertThat(stopped).contains("4 weeks · 24 Aug – 20 Sep · stopped Tue 1 Sep")
        assertThat(stopped).containsAtLeast("Week 2 · add a walk", "0 of 2 planned", "Week 3 · hold it", "Easy 30′ · Steady 45′").inOrder()
        assertThat(stopped).doesNotContain("Plan the open walk")
        render.click("Evaluate me and plan again")
        assertThat(evaluated).isEqualTo(2)
    }

    @Test
    fun `with no plan to show it says so`() {
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, onRunning = true))

        assertThat(texts).contains("No plan is running.")
        assertThat(texts).doesNotContain("Adjust the plan")
    }

    /** D118: the answer is drawn as the timeline it would be, kept today — week 1 is this week, its sessions open. */
    @Test
    fun `an answer is drawn as its weeks, with Keep this plan and Ask again`() {
        var kept = false
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, shown = SHOWN), onKeep = { kept = true })

        assertThat(texts.count { it == "From the AI trainer · advice, not a measurement" }).isEqualTo(1)
        assertThat(texts).containsAtLeast(
            "From the AI trainer · advice, not a measurement", "Invented plan", "4 weeks · 31 Aug – 27 Sep · you're in week 1",
            "Week 1 · settle in — this week", "Easy 30′", "Open", "Week 2 · add a walk",
            "WHERE YOU STOOD · THU 3 SEP", "Invented headline.", "Read the trainer's full evaluation",
            "Keep this plan", "Ask again", "Keeping it replaces any plan you have now. The evaluation is kept either way.",
        ).inOrder()
        assertThat(texts).doesNotContain("Adjust the plan")
        render.click("Keep this plan")
        assertThat(kept).isTrue()
    }

    @Test
    fun `a kept answer says so`() {
        assertThat(draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, shown = SHOWN, kept = true)))
            .contains("Kept. It is on the Trainer screen.")
    }

    /** A tap on Keep is not taken twice: while it writes, Keep and Ask again cannot be pressed. */
    @Test
    fun `while Keep is writing, Keep and Ask again cannot be pressed`() {
        draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, shown = SHOWN, writing = true))

        assertThat(render.isEnabled("Keep this plan")).isFalse()
        assertThat(render.isEnabled("Ask again")).isFalse()
    }

    private fun draw(
        state: EvaluatePlanViewModel.State,
        onKeep: () -> Unit = {},
        onAdjust: () -> Unit = {},
        onPlan: () -> Unit = {},
        onEvaluate: () -> Unit = {},
    ): List<String> = render.texts {
        EvaluatePlanScreen(
            state = state, onBack = {}, onChange = {}, onAsk = {}, onKeep = onKeep, onAskAgain = {},
            onAdjust = onAdjust, onPlan = onPlan, onEvaluate = onEvaluate,
        )
    }

    /** The plan kept from [START], as the screen reads it on [today]; [stoppedOn] stops it. */
    private fun running(
        today: Long = TEST_EPOCH_DAY,
        workouts: List<Workout> = emptyList(),
        evaluation: DatedEvaluation? = DatedEvaluation(SHOWN.evaluation!!, MONDAY - 1),
        stoppedOn: Long? = null,
    ): EvaluatePlanViewModel.State {
        val kept = SHOWN.copy(startEpochDay = START, status = ProgrammeStatus.RUNNING)
        val programme = if (stoppedOn == null) kept else kept.copy(status = ProgrammeStatus.STOPPED, stoppedEpochDay = stoppedOn)
        val counting = PlanCounting(1, 0, emptyMap())
        val progress = PlanProgress.of(programme.plan, START, workouts, Programmes.countedUntil(programme, today), counting)
        val card = if (stoppedOn == null) PlanCard.of(kept, workouts, today, counting) as? PlanCard.Running else null
        val shown = PlanShown(PlanTimeline.of(programme, START, today, progress, emptyMap()), card, evaluation)
        return EvaluatePlanViewModel.State(today = today, onRunning = true, planShown = shown)
    }

    private companion object {
        val MONDAY = TEST_EPOCH_DAY - 3
        val START = MONDAY - 7
        val WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val WALK_45 = PlannedSession(WorkoutKind.WALK, 45, PlannedEffort.STEADY, "Invented line")
        val SHOWN = Programme(
            id = 1, createdAtMillis = 0, ask = ProgrammeAsk(4, 2),
            evaluation = Evaluation("Invented headline.", "Invented going well.", "Invented to work on.", ""),
            plan = WeeksPlan(
                "Invented plan",
                listOf(
                    PlanWeek("settle in", listOf(WALK, WALK)), PlanWeek("add a walk", listOf(WALK, WALK_45)),
                    PlanWeek("hold it", listOf(WALK, WALK_45)), PlanWeek("", listOf(WALK)),
                ),
                "Invented reason.",
            ),
            model = "a-model",
        )
    }
}
