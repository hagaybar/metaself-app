package com.metaself.app.ui.screen.trainer

import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.screen.trainer.TrainerScreens.NOW
import com.metaself.app.ui.screen.trainer.TrainerScreens.PLAN
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * Which words the plan form and the suggestion draw (D86), and that each control calls back. Not
 * provable here (CLAUDE.md, Testing): chip wrapping, touch heights. Every answer and word is invented.
 */
@RunWith(RobolectricTestRunner::class)
class PlanSessionScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `the form shows its four rows and every choice`() {
        val texts = draw(PlanSessionViewModel.State())

        assertThat(texts).contains("Plan my next session")
        assertThat(texts).containsAtLeast("WHAT", "TIME I HAVE", "HOW I FEEL", "TODAY I WANT").inOrder()
        assertThat(texts).containsAtLeast("Treadmill walk", "Outdoor walk", "Run", "Something else").inOrder()
        assertThat(texts).containsAtLeast("20 min", "30 min", "45 min", "60 min or more").inOrder()
        assertThat(texts).containsAtLeast("Fresh", "Normal", "Tired").inOrder()
        assertThat(texts).containsAtLeast("Easy", "A push", "Not sure").inOrder()
        assertThat(texts).contains("Anything else? (optional)")
    }

    @Test
    fun `the next planned session is named above the rows, and in the privacy line`() {
        val next = PlannedTick(1, PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Invented line"))
        val texts = draw(PlanSessionViewModel.State(next = next))

        assertThat(texts).containsAtLeast("Next in your plan: steady walk, 40 min", "WHAT").inOrder()
        assertThat(texts.any { it.contains("these answers and the next session in your weekly plan") }).isTrue()
    }

    @Test
    fun `a chip changes the form`() {
        var changed: PlanSessionViewModel.Form? = null
        draw(PlanSessionViewModel.State(form = PlanSessionViewModel.Form(activity = PlanActivity.RUN)), onChange = { changed = it })

        assertThat(render.isSelected("Run")).isTrue()
        render.click("45 min")

        assertThat(changed).isEqualTo(PlanSessionViewModel.Form(activity = PlanActivity.RUN, time = TimeAvailable.MIN_45))
    }

    @Test
    fun `ask the trainer waits for all four rows, and says what it sends`() {
        draw(PlanSessionViewModel.State(ceiling = 20))

        assertThat(render.isEnabled("Ask the trainer")).isFalse()
        assertThat(render.textsAgain()).contains(
            "Sends to OpenAI, with your key: these answers; " +
                "your note about yourself; your sessions of the last six weeks, with your words on them; weekly totals; " +
                "a line for each month of the year before; your weight trend and goal rate; your age, sex and height; and the trainer's last three feedbacks. " +
                "One of today's 20 AI requests.",
        )

        var asked = false
        draw(PlanSessionViewModel.State(form = FILLED), onAsk = { asked = true })
        assertThat(render.isEnabled("Ask the trainer")).isTrue()
        render.click("Ask the trainer")
        assertThat(asked).isTrue()
    }

    @Test
    fun `asking says so, and a failure says why`() {
        assertThat(draw(PlanSessionViewModel.State(form = FILLED, asking = true))).contains("Asking the trainer…")
        assertThat(draw(PlanSessionViewModel.State(form = FILLED, failure = EstimateResult.NoKey)))
            .contains("No API key yet. Add one in settings.")
    }

    /** D4: the suggestion is labelled advice, never a measurement. */
    @Test
    fun `the suggestion shows its steps, why, and Keep and Ask again`() {
        var kept = false
        var again = false
        val texts = draw(PlanSessionViewModel.State(shown = SHOWN), onKeep = { kept = true }, onAskAgain = { again = true })

        assertThat(texts).contains("Your next session")
        assertThat(texts).contains("Steady walk")
        assertThat(texts).contains("Suggested by the AI trainer · advice, not a measurement")
        assertThat(texts).containsAtLeast("0–10", "Warm up", "easy pace", "10–35", "Walk", "zone 2", "35–45", "Cool down").inOrder()
        assertThat(texts).containsAtLeast("WHY THIS ONE", "Invented reason.").inOrder()
        assertThat(texts).doesNotContain("WHAT")
        assertThat(render.roleOf("Keep this plan")).isEqualTo(Role.Button)
        render.click("Keep this plan")
        render.click("Ask again")
        assertThat(kept).isTrue()
        assertThat(again).isTrue()
    }

    @Test
    fun `a kept plan says so and offers no Keep`() {
        val texts = draw(PlanSessionViewModel.State(shown = SHOWN.copy(kept = true), kept = true))

        assertThat(texts).contains("Kept. It is on the Trainer screen for seven days.")
        assertThat(texts).doesNotContain("Keep this plan")
        assertThat(texts).contains("Ask again")
    }

    @Test
    fun `a refused write is said`() {
        val texts = draw(PlanSessionViewModel.State(shown = SHOWN, refused = ActionRefused.NOTHING_CHANGED))

        assertThat(texts).contains(
            "That didn't work, and nothing was changed. What went wrong is under Settings → Recent problems.",
        )
    }

    /** What the trainer is asked is what is on screen: nothing can be changed while it is asked. */
    @Test
    fun `while asking, the chips and the words are disabled`() {
        draw(PlanSessionViewModel.State(form = FILLED, asking = true))

        assertThat(render.isEnabled("Run")).isFalse()
        assertThat(render.isEnabled("45 min")).isFalse()
        assertThat(render.isEnabled("Anything else?")).isFalse()

        draw(PlanSessionViewModel.State(form = FILLED))

        assertThat(render.isEnabled("Run")).isTrue()
        assertThat(render.isEnabled("Anything else?")).isTrue()
    }

    /** Opened for the kept plan, the form is not drawn while the plan is read. */
    @Test
    fun `loading draws neither the form nor a plan`() {
        val texts = draw(PlanSessionViewModel.State(loading = true))

        assertThat(texts).doesNotContain("WHAT")
        assertThat(texts).doesNotContain("Ask the trainer")
    }

    private fun draw(
        state: PlanSessionViewModel.State,
        onChange: (PlanSessionViewModel.Form) -> Unit = {},
        onAsk: () -> Unit = {},
        onKeep: () -> Unit = {},
        onAskAgain: () -> Unit = {},
    ): List<String> = render.texts {
        PlanSessionScreen(state = state, onBack = {}, onChange = onChange, onAsk = onAsk, onKeep = onKeep, onAskAgain = onAskAgain)
    }

    private companion object {
        val FILLED = PlanSessionViewModel.Form(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE)
        val SHOWN = TrainerScreens.storedPlan(createdAt = NOW, kept = false).copy(id = 1, plan = PLAN)
    }
}
