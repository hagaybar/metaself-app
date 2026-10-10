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
import com.metaself.app.domain.trainer.PlanPlace
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.PlanShown
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.PlanTimeline
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.Programmes
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TrainerPlan
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

        assertThat(texts).contains("Plan ahead")
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
            "Adjust the plan", "Plan the open walk", "Plan ahead",
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
    }

    /** D124: Plan ahead is on the plan page always — beside Adjust while it runs, alone once it is over. */
    @Test
    fun `ended or stopped, one button — Plan ahead`() {
        var planned = 0
        val ended = draw(running(today = START + 28), onPlanAhead = { planned++ })
        assertThat(ended).contains("4 weeks · 24 Aug – 20 Sep · ended Sun 20 Sep")
        assertThat(ended).doesNotContain("Adjust the plan")
        render.click("Plan ahead")

        val stopped = draw(running(stoppedOn = MONDAY + 1), onPlanAhead = { planned++ })
        assertThat(stopped).contains("4 weeks · 24 Aug – 20 Sep · stopped Tue 1 Sep")
        assertThat(stopped).containsAtLeast("Week 2 · add a walk", "0 of 2 planned", "Week 3 · hold it", "Easy 30′ · Steady 45′").inOrder()
        assertThat(stopped).doesNotContain("Plan the open walk")
        render.click("Plan ahead")

        draw(running(), onPlanAhead = { planned++ })
        render.click("Plan ahead")
        assertThat(planned).isEqualTo(3)
    }

    @Test
    fun `with no plan to show it says so, and offers Plan ahead and the next session`() {
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, onRunning = true, nextSession = NEXT))

        assertThat(texts).containsAtLeast("No plan is running.", "NEXT SESSION", "Steady walk", "Plan ahead").inOrder()
        assertThat(texts).doesNotContain("Adjust the plan")
    }

    /** D123: Month · Week · Session under the title, Week chosen first; a chip changes the view. */
    @Test
    fun `the switch has three views, Week chosen`() {
        val chosen = mutableListOf<EvaluatePlanViewModel.PlanView>()
        val texts = draw(running(), onChoose = { chosen += it })

        assertThat(texts).contains("Your plan")
        assertThat(texts).containsAtLeast("Month", "Week", "Session", "Invented plan").inOrder()
        assertThat(render.isSelected("Week")).isTrue()
        assertThat(render.isSelected("Month")).isFalse()
        render.click("Session")
        render.click("Month")
        assertThat(chosen).containsExactly(EvaluatePlanViewModel.PlanView.SESSION, EvaluatePlanViewModel.PlanView.MONTH).inOrder()
    }

    /** D123, release 1: no plan has an outline yet. */
    @Test
    fun `Month says the plan has no outline`() {
        val texts = draw(running().copy(view = EvaluatePlanViewModel.PlanView.MONTH))

        assertThat(texts).containsAtLeast("This plan has no outline — plan ahead to set a goal.", "Adjust the plan", "Plan ahead").inOrder()
        assertThat(texts).doesNotContain("Week 1 · settle in")
        assertThat(draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, onRunning = true, view = EvaluatePlanViewModel.PlanView.MONTH)))
            .contains("No plan is running.")
    }

    /** D122: an open session with steps reads Steps ready on Week. */
    @Test
    fun `on Week an open session with steps is ready`() {
        val texts = draw(running(workouts = listOf(TrainerScreens.walk(1, MONDAY)), steps = mapOf(PlanPlace(1, 1) to STEPS)))

        assertThat(texts).containsAtLeast("Week 2 · add a walk — this week", "Easy 30′", "Done Mon", "Steady 45′", "Steps ready").inOrder()
        assertThat(texts).doesNotContain("Open")
    }

    /**
     * D122, D123: Session — the Next session, this week's done ones folded, this week's open one with its steps,
     * next week's with Get the steps.
     */
    @Test
    fun `Session lists the sessions coming up, each opening to its steps or offering Get the steps`() {
        val asked = mutableListOf<PlanPlace>()
        val state = running(workouts = listOf(TrainerScreens.walk(1, MONDAY)), steps = mapOf(PlanPlace(1, 1) to STEPS))
            .copy(view = EvaluatePlanViewModel.PlanView.SESSION, nextSession = NEXT)
        val texts = draw(state, onGetSteps = { asked += it })

        assertThat(texts).containsAtLeast(
            "NEXT SESSION", "Steady walk", "Show the steps",
            "Done this week · 1",
            "THIS WEEK", "Steady walk, 45 min", "Steps ready", "Show the steps",
            "NEXT WEEK", "Easy walk, 30 min", "Get the steps", "Steady walk, 45 min", "Get the steps",
            "Adjust the plan", "Plan the open walk", "Plan ahead",
        ).inOrder()
        assertThat(texts).containsNoneOf("Done Mon", "Invented steps", "Week 1 · settle in")

        render.click("Done this week · 1")
        assertThat(render.textsAgain()).containsAtLeast("Easy walk, 30 min", "Done Mon").inOrder()
        render.click("Get the steps")
        assertThat(asked).containsExactly(PlanPlace(2, 0))
    }

    @Test
    fun `a session's steps open from its card`() {
        val state = running(steps = mapOf(PlanPlace(1, 1) to STEPS)).copy(view = EvaluatePlanViewModel.PlanView.SESSION)
        draw(state)

        render.click("Show the steps")

        assertThat(render.textsAgain()).containsAtLeast("Hide the steps", "Invented steps", "0–20", "Walk", "Invented reason for the steps.").inOrder()
    }

    @Test
    fun `with nothing coming and no next session, Session says so`() {
        val texts = draw(EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, onRunning = true, view = EvaluatePlanViewModel.PlanView.SESSION))

        assertThat(texts).containsAtLeast("Nothing planned is coming up.", "Plan ahead").inOrder()
    }

    /** D124: while a plan runs, Keep this plan first says what it replaces, and from when. */
    @Test
    fun `keeping while a plan runs asks first, naming next Monday`() {
        var kept = 0
        var cancelled = 0
        val asking = EvaluatePlanViewModel.State(today = TEST_EPOCH_DAY, shown = SHOWN, replacingFrom = MONDAY + 7)
        assertThat(draw(asking)).containsAtLeast("4 weeks · 7 Sep – 4 Oct · starts Mon 7 Sep", "Keep this plan").inOrder()

        val texts = draw(asking.copy(confirming = true), onKeep = { kept++ }, onCancelKeep = { cancelled++ })

        assertThat(texts).containsAtLeast("This replaces your current plan from Mon 7 Sep.", "Replace it", "Not now").inOrder()
        assertThat(texts).doesNotContain("Keep this plan")
        render.click("Replace it")
        render.click("Not now")
        assertThat(kept to cancelled).isEqualTo(1 to 1)
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
        onPlanAhead: () -> Unit = {},
        onChoose: (EvaluatePlanViewModel.PlanView) -> Unit = {},
        onGetSteps: (PlanPlace) -> Unit = {},
        onCancelKeep: () -> Unit = {},
    ): List<String> = render.texts {
        EvaluatePlanScreen(
            state = state, onBack = {}, onChange = {}, onAsk = {}, onKeep = onKeep, onAskAgain = {},
            onAdjust = onAdjust, onPlan = onPlan, onPlanAhead = onPlanAhead, onChoose = onChoose, onGetSteps = onGetSteps,
            onCancelKeep = onCancelKeep,
        )
    }

    /** The plan kept from [START], as the screen reads it on [today]; [stoppedOn] stops it. */
    private fun running(
        today: Long = TEST_EPOCH_DAY,
        workouts: List<Workout> = emptyList(),
        evaluation: DatedEvaluation? = DatedEvaluation(SHOWN.evaluation!!, MONDAY - 1),
        stoppedOn: Long? = null,
        steps: Map<PlanPlace, TrainerPlan> = emptyMap(),
    ): EvaluatePlanViewModel.State {
        val kept = SHOWN.copy(startEpochDay = START, status = ProgrammeStatus.RUNNING)
        val programme = if (stoppedOn == null) kept else kept.copy(status = ProgrammeStatus.STOPPED, stoppedEpochDay = stoppedOn)
        val counting = PlanCounting(1, 0, emptyMap())
        val progress = PlanProgress.of(programme.plan, START, workouts, Programmes.countedUntil(programme, today), counting)
        val card = if (stoppedOn == null) PlanCard.of(kept, workouts, today, counting) as? PlanCard.Running else null
        val shown = PlanShown(PlanTimeline.of(programme, START, today, progress, emptyMap()).withSteps(steps.keys), card, evaluation, steps)
        return EvaluatePlanViewModel.State(today = today, onRunning = true, planShown = shown)
    }

    private companion object {
        val MONDAY = TEST_EPOCH_DAY - 3

        /** A kept suggestion for no planned session: the Next session (D122). */
        val NEXT = TrainerScreens.storedPlan(TrainerScreens.NOW).copy(id = 8)

        /** Steps kept for a planned session (D122). */
        val STEPS = TrainerScreens.storedPlan(TrainerScreens.NOW).copy(
            id = 9,
            plan = SessionPlan(
                "Invented steps",
                listOf(PlanStep(0, 20, "Walk", "")),
                "Invented reason for the steps.",
            ),
        )
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
