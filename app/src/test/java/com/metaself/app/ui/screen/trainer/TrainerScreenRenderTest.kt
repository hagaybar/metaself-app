package com.metaself.app.ui.screen.trainer

import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.ReviewedSession
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerHome
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * Which words the Trainer screen draws (D85), and that each door calls back. Not provable here
 * (CLAUDE.md, Testing): a row's touch height, wrapping. TEST_EPOCH_DAY is Thursday 3 September 2026;
 * every session, plan and word is invented.
 */
@RunWith(RobolectricTestRunner::class)
class TrainerScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the screen is called Trainer and has a way back`() {
        val texts = draw(TrainerHome(null, null, emptyList()))

        assertThat(texts).contains("Trainer")
        assertThat(texts).contains("Back")
    }

    @Test
    fun `a waiting session shows its title, its line and How did it go`() {
        var reviewed: Long? = null
        val texts = draw(TrainerHome(WAITING, null, emptyList()), onReview = { reviewed = it })

        assertThat(texts).contains("WAITING FOR YOUR WORDS")
        assertThat(texts.any { it.startsWith("Walking, today ") }).isTrue()
        assertThat(texts).contains("40 min · 3.0 km · heart 110 average")
        render.click("How did it go?")
        assertThat(reviewed).isEqualTo(5L)
    }

    @Test
    fun `plan my next session is a button`() {
        var planned = false
        draw(TrainerHome(null, null, emptyList()), onPlan = { planned = true })

        assertThat(render.roleOf("Plan my next session")).isEqualTo(Role.Button)
        render.click("Plan my next session")
        assertThat(planned).isTrue()
    }

    @Test
    fun `the kept plan shows its title and opens`() {
        var opened = false
        val texts = draw(TrainerHome(null, KEPT, emptyList()), onOpenKept = { opened = true })

        assertThat(texts).contains("YOUR KEPT PLAN")
        assertThat(texts).contains("Steady walk")
        render.click("Open")
        assertThat(opened).isTrue()
    }

    @Test
    fun `earlier sessions show their name, date and line, and open`() {
        var reviewed: Long? = null
        val earlier = ReviewedSession(
            WAITING.copy(id = 2, epochDay = TEST_EPOCH_DAY - 5, startedAtMillis = (TEST_EPOCH_DAY - 5) * DAY + 7 * HOUR),
            TrainerReview(workoutId = 2, planId = 1, felt = Felt.RIGHT, words = "Invented.", feedback = FEEDBACK),
        )
        val texts = draw(TrainerHome(null, null, listOf(earlier)), onReview = { reviewed = it })

        assertThat(texts).contains("EARLIER SESSIONS")
        assertThat(texts).contains("Walking · Sat 29 Aug")
        assertThat(texts).contains("Felt right · feedback read · as planned")
        assertThat(render.roleOf("Walking · Sat 29 Aug")).isEqualTo(Role.Button)
        render.click("Walking · Sat 29 Aug")
        assertThat(reviewed).isEqualTo(2L)
    }

    @Test
    fun `with nothing to show, only Plan my next session`() {
        val texts = draw(TrainerHome(null, null, emptyList()))

        assertThat(texts).contains("Plan my next session")
        assertThat(texts).containsNoneOf("WAITING FOR YOUR WORDS", "YOUR KEPT PLAN", "EARLIER SESSIONS")
    }

    @Test
    fun `an unreadable record says so`() {
        val texts = render.texts {
            TrainerScreen(TrainerViewModel.State(today = TEST_EPOCH_DAY, unreadable = true), {}, {}, {}, {})
        }

        assertThat(texts).contains("The trainer's record could not be read; Recent problems says why.")
        assertThat(texts).doesNotContain("Plan my next session")
    }

    private fun draw(
        home: TrainerHome,
        onReview: (Long) -> Unit = {},
        onPlan: () -> Unit = {},
        onOpenKept: () -> Unit = {},
    ): List<String> = render.texts {
        TrainerScreen(
            state = TrainerViewModel.State(home = home, today = TEST_EPOCH_DAY),
            onBack = {},
            onReview = onReview,
            onPlan = onPlan,
            onOpenKept = onOpenKept,
        )
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L

        val WAITING = Workout(
            id = 5, epochDay = TEST_EPOCH_DAY, startedAtMillis = TEST_EPOCH_DAY * DAY + 7 * HOUR, durationMinutes = 40,
            kind = WorkoutKind.WALK, title = null, distanceM = 3_000, energyKcal = null,
            energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
            avgHeartRate = 110,
        )

        val KEPT = TrainerPlan(
            id = 1, createdAtMillis = TEST_EPOCH_DAY * DAY,
            answers = PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE),
            plan = SessionPlan("Steady walk", listOf(PlanStep(0, 45, "Walk", "")), "Invented."),
            model = "a-model", kept = true,
        )

        val FEEDBACK = Feedback("Invented headline.", "Invented.", "Invented.", "Invented.", "Invented.", PlanFollowed.YES)
    }
}
