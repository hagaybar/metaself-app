package com.metaself.app.ui.screen.trainer

import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.CountedPlan
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.WeekTarget
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.Phase
import com.metaself.app.domain.trainer.Outline
import com.metaself.app.domain.trainer.Block
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.TrainerHome
import com.metaself.app.domain.trainer.TrainerWeek
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.trainer.TrainerWeekWording
import com.metaself.app.ui.trainer.TrainerWording
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * Which words the Trainer page draws (D85, D114), in which order, and that each door calls back. Not
 * provable here (CLAUDE.md, Testing): a block's height, a colour, a row's touch height, wrapping. The week is
 * Monday 31 August 2026 to today, Thursday 3 September ([TEST_EPOCH_DAY]); every session, plan, figure and
 * word is invented.
 */
@RunWith(RobolectricTestRunner::class)
class TrainerScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the screen is called Trainer and has a way back`() {
        val texts = draw(page())

        assertThat(texts).contains("Trainer")
        assertThat(texts).contains("Back")
    }

    /** D114: header and verdict, the strip with its question, the boxes, the note, the main button, the rest. */
    @Test
    fun `the page runs in D114's order`() {
        // The forty-minute walk ticks the first planned walk; the shorter one, a day later, is asked about.
        val texts = draw(
            page(
                workouts = listOf(walk(1, MONDAY), walk(6, MONDAY + 1, minutes = 20)), running = RUNNING, waiting = walk(5, TEST_EPOCH_DAY),
                days = sleepAndSteps(), food = mapOf(MONDAY to DayTotals(1_300, 80, 150, 40)), note = NOTE,
            ),
        )

        val order = listOf(
            "THIS WEEK · MON 31 AUG – SUN 6 SEP", "60 minutes moving this week.", "Planned, done", "Walking, 20 min — count it for this?",
            "STEPS", "SLEEP", "FOOD", "Invented week note.", "Plan the open walk", "WAITING FOR YOUR WORDS",
            "Your plan · week 1 of 2", "Weekly letters", "Earlier sessions", "About me",
        )
        order.zipWithNext().forEach { (first, second) ->
            assertThat(render.isDrawnBefore(first, second)).isTrue()
        }
        assertThat(texts).containsNoneOf("ABOUT ME", "EARLIER SESSIONS", "Adjust the plan", "See the plan", "YOUR 2-WEEK PLAN · WEEK 1 OF 2")
        // D122: the kept single session lives on the plan page now.
        assertThat(texts).doesNotContain("YOUR KEPT PLAN")
    }

    @Test
    fun `the verdict is the phone's, counted from the week`() {
        val texts = draw(page(workouts = listOf(walk(1, MONDAY), walk(2, MONDAY + 1)), running = RUNNING))

        assertThat(texts).contains("80 minutes moving this week. Every planned session done.")
        assertThat(draw(page())).contains("Nothing recorded yet this week.")
    }

    /** D114 item 5, D124: a planned session open, nothing open, or no plan — each its own main button. */
    @Test
    fun `the main button's three cases`() {
        var planned = 0
        var evaluated = 0

        val open = draw(page(running = RUNNING), onPlan = { planned++ })
        assertThat(open).contains("Plan the open walk")
        assertThat(open).contains("Next in your plan: easy walk, 30 min")
        assertThat(open).doesNotContain("Plan ahead")
        render.click("Plan the open walk")
        assertThat(planned).isEqualTo(1)

        val done = draw(page(workouts = listOf(walk(1, MONDAY), walk(2, MONDAY + 1)), running = RUNNING), onPlan = { planned++ })
        assertThat(done).contains("Plan my next session")
        assertThat(done).doesNotContain("Plan ahead")
        render.click("Plan my next session")
        assertThat(planned).isEqualTo(2)

        val none = draw(page(), onPlan = { planned++ }, onPlanAhead = { evaluated++ })
        assertThat(none).containsAtLeast("Plan ahead", "Plan my next session").inOrder()
        assertThat(render.roleOf("Plan ahead")).isEqualTo(Role.Button)
        render.click("Plan ahead")
        render.click("Plan my next session")
        assertThat(evaluated).isEqualTo(1)
        assertThat(planned).isEqualTo(3)

        // D124: in the plan's last week, Plan ahead — and the open session's action under it.
        val last = draw(page(running = RUNNING.copy(startEpochDay = MONDAY - 7)), onPlan = { planned++ }, onPlanAhead = { evaluated++ })
        assertThat(last).containsAtLeast("Plan ahead", "Plan the open walk").inOrder()
        render.click("Plan the open walk")
        assertThat(planned).isEqualTo(4)
    }

    /** D105: a twenty-minute walk under a thirty-minute place is still asked about, now under the strip. Invented. */
    /** D121: an outlined plan whose week has begun empty — the main button opens the plan page, where it is planned. */
    @Test
    fun `an empty week's main button is Plan this week, which opens the plan page`() {
        var opened = 0
        var planned = 0
        val outline = Outline(listOf(Block(Phase.STEADY, "Invented", List(2) { WeekTarget(100, 30, false) })))
        val empty = RUNNING.copy(
            ask = ProgrammeAsk(2, 2, goal = PlanGoal.Steady),
            plan = WeeksPlan("Invented", List(2) { PlanWeek("", emptyList()) }, "Invented.", outline),
        )

        val texts = draw(page(running = empty), onSeePlan = { opened++ }, onPlan = { planned++ })

        assertThat(texts).containsAtLeast("Plan this week", "Plan my next session").inOrder()
        render.click("Plan this week")
        render.click("Plan my next session")
        assertThat(opened).isEqualTo(1)
        assertThat(planned).isEqualTo(1)
    }

    @Test
    fun `the shorter-session question is still answerable, under the strip`() {
        var answered: Pair<Long, Boolean>? = null
        val texts = draw(page(workouts = listOf(walk(5, MONDAY, minutes = 20)), running = RUNNING), onAnswer = { id, yes -> answered = id to yes })

        assertThat(texts).containsAtLeast("Easy walk, 30 min", "Walking, 20 min — count it for this?", "Yes", "No").inOrder()
        assertThat(render.isDrawnBefore("Planned, done", "Walking, 20 min — count it for this?")).isTrue()
        render.click("Yes")
        assertThat(answered).isEqualTo(5L to true)
    }

    @Test
    fun `while its answer is being written, Yes and No cannot be pressed`() {
        draw(page(workouts = listOf(walk(5, MONDAY, minutes = 20)), running = RUNNING, answering = setOf(5L)))

        assertThat(render.isEnabled("Yes")).isFalse()
        assertThat(render.isEnabled("No")).isFalse()
    }

    @Test
    fun `the strip opens Movement`() {
        var opened = false
        draw(page(workouts = listOf(walk(1, MONDAY))), onMovement = { opened = true })

        render.clickDescribed(TrainerWeekWording.STRIP)
        assertThat(opened).isTrue()
    }

    /** D114: the day letters always; "To do" and its minutes only with planned sessions open; the legend follows the plan. */
    @Test
    fun `the legend and To do follow the plan`() {
        val planned = draw(page(workouts = listOf(walk(1, MONDAY)), running = RUNNING))
        assertThat(planned).containsAtLeast("M", "T", "W", "T", "F", "S", "S", "To do").inOrder()
        assertThat(planned).containsAtLeast("Planned, done", "Planned, open", "Beyond the plan").inOrder()
        assertThat(planned).contains("30′")

        val unplanned = draw(page(workouts = listOf(walk(1, MONDAY))))
        assertThat(unplanned).contains("Sessions")
        assertThat(unplanned).containsNoneOf("To do", "Planned, done", "Beyond the plan")
    }

    @Test
    fun `the boxes, and an absent one`() {
        val texts = draw(page(days = sleepAndSteps(), food = mapOf(MONDAY to DayTotals(1_300, 80, 150, 40))))

        assertThat(texts).containsAtLeast("STEPS", "8,000", "a day").inOrder()
        assertThat(texts).containsAtLeast("SLEEP", "6 h 50", "30 min under usual").inOrder()
        assertThat(texts).containsAtLeast("FOOD", "1,300", "kcal · target 1,650", "over 1 logged day").inOrder()

        val noSleep = draw(page(food = mapOf(MONDAY to DayTotals(1_300, 80, 150, 40))))
        assertThat(noSleep).contains("FOOD")
        assertThat(noSleep).containsNoneOf("SLEEP", "STEPS")
    }

    /** D116: the coach's note with when it was written, under the trainer's label (D4); none, nothing. */
    @Test
    fun `the coach's note with when it was written, and none without one`() {
        val texts = draw(page(note = NOTE))

        assertThat(texts).contains("Invented week note.")
        assertThat(texts.any { it.startsWith("Written ") }).isTrue()
        assertThat(texts).contains(TrainerWording.FROM_TRAINER)

        val none = draw(page())
        assertThat(none.none { it.startsWith("Written ") }).isTrue()
        assertThat(none).doesNotContain(TrainerWording.FROM_TRAINER)
    }

    @Test
    fun `each row opens what it names`() {
        val opened = mutableListOf<String>()
        draw(
            page(running = RUNNING),
            onSeePlan = { opened += "plan" }, onLetters = { opened += "letters" }, onEarlier = { opened += "earlier" },
            onAboutMe = { opened += "about" },
        )

        listOf("Your plan · week 1 of 2", "Weekly letters", "Earlier sessions", "About me").forEach {
            assertThat(render.roleOf(it)).isEqualTo(Role.Button)
            render.click(it)
        }
        assertThat(opened).containsExactly("plan", "letters", "earlier", "about").inOrder()

        // D124: with no plan the row is still there — the plan screen holds Plan ahead and the Next session.
        assertThat(draw(page())).contains("Your plan")
    }

    @Test
    fun `a waiting session shows its title, its line and How did it go`() {
        var reviewed: Long? = null
        val texts = draw(page(waiting = walk(5, TEST_EPOCH_DAY)), onReview = { reviewed = it })

        assertThat(texts).contains("WAITING FOR YOUR WORDS")
        assertThat(texts.any { it.startsWith("Walking, today ") }).isTrue()
        assertThat(texts).contains("40 min · 3.0 km · heart 110 average")
        render.click("How did it go?")
        assertThat(reviewed).isEqualTo(5L)
    }

    @Test
    fun `an unreadable record says so`() {
        val texts = render.texts {
            TrainerScreen(TrainerViewModel.State(today = TEST_EPOCH_DAY, unreadable = true), {}, {}, {}, {}, {}, {}, {}, {}, {})
        }

        assertThat(texts).contains("The trainer's record could not be read; Recent problems says why.")
        assertThat(texts).doesNotContain("Plan my next session")
    }

    private fun draw(
        state: TrainerViewModel.State,
        onReview: (Long) -> Unit = {},
        onPlan: () -> Unit = {},
        onAboutMe: () -> Unit = {},
        onPlanAhead: () -> Unit = {},
        onSeePlan: () -> Unit = {},
        onLetters: () -> Unit = {},
        onEarlier: () -> Unit = {},
        onMovement: () -> Unit = {},
        onAnswer: (Long, Boolean) -> Unit = { _, _ -> },
    ): List<String> = render.texts {
        TrainerScreen(
            state = state,
            onBack = {},
            onReview = onReview,
            onPlan = onPlan,
            onAboutMe = onAboutMe,
            onPlanAhead = onPlanAhead,
            onSeePlan = onSeePlan,
            onLetters = onLetters,
            onEarlier = onEarlier,
            onMovement = onMovement,
            onAnswer = onAnswer,
        )
    }

    /** The page's state as the view model builds it: the home, and this week counted from the same sessions. */
    private fun page(
        workouts: List<Workout> = emptyList(),
        running: Programme? = null,
        waiting: Workout? = null,
        days: List<HealthDay> = emptyList(),
        food: Map<Long, DayTotals> = emptyMap(),
        note: WeekNote? = null,
        answering: Set<Long> = emptySet(),
    ): TrainerViewModel.State {
        val counting = PlanCounting(1, 0, emptyMap())
        val card = running?.let { PlanCard.of(it, workouts, TEST_EPOCH_DAY, counting) } ?: PlanCard.None
        val home = TrainerHome(waiting, emptyList(), card, (card as? PlanCard.Running)?.next, TEST_EPOCH_DAY)
        val plans = listOfNotNull(running?.let { CountedPlan(it, counting) })
        val week = TrainerWeek.of(MONDAY, TEST_EPOCH_DAY, workouts, days, food, 1_650, plans)
        return TrainerViewModel.State(home = home, today = TEST_EPOCH_DAY, week = week, note = note, answering = answering)
    }

    /**
     * Eight thousand steps on each day so far, and sleep of 410 minutes on each this week against 440 on each
     * of the 28 days before: "30 min under usual". Invented.
     */
    private fun sleepAndSteps(): List<HealthDay> =
        (MONDAY - 28 until MONDAY).map { HealthDay(it, sleepMinutes = 440) } +
            (MONDAY..TEST_EPOCH_DAY).map { HealthDay(it, steps = 8_000, sleepMinutes = 410) }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        val MONDAY = TEST_EPOCH_DAY - 3

        /** A synced walk at 07:00 on [day], 3 km, heart 110 average; forty minutes unless said. */
        fun walk(id: Long, day: Long, minutes: Int = 40) = Workout(
            id = id, epochDay = day, startedAtMillis = day * DAY + 7 * HOUR, durationMinutes = minutes,
            kind = WorkoutKind.WALK, title = null, distanceM = 3_000, energyKcal = null,
            energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
            avgHeartRate = 110,
        )

        val EASY_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val RUNNING = Programme(
            1, 0, ProgrammeAsk(2, 2), null, WeeksPlan("Invented plan", List(2) { PlanWeek("w", listOf(EASY_30, EASY_30)) }, "Invented."),
            "a-model", MONDAY, ProgrammeStatus.RUNNING,
        )

        val NOTE = WeekNote("Invented week note.", TEST_EPOCH_DAY * DAY + 9 * HOUR, MONDAY, WeekNoteSource.TRAINER)
    }
}
