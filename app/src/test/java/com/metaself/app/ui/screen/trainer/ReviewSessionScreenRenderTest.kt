package com.metaself.app.ui.screen.trainer

import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.screen.trainer.TrainerScreens.FEEDBACK
import com.metaself.app.ui.screen.trainer.TrainerScreens.NOW
import com.metaself.app.ui.screen.trainer.TrainerScreens.storedPlan
import com.metaself.app.ui.screen.trainer.TrainerScreens.walk
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * Which words the review and the feedback draw (D87), and that each control calls back. Not provable
 * here (CLAUDE.md, Testing): wrapping, touch heights. Every figure, word and answer is invented.
 */
@RunWith(RobolectricTestRunner::class)
class ReviewSessionScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    private val session = walk(1).copy(energyKcal = 200, energySource = EnergySource.BAND, maxHeartRate = 130)
    private val form = ReviewSessionViewModel.State(workout = session, today = TEST_EPOCH_DAY)

    @Test
    fun `the form shows the session's figures with their sources`() {
        val texts = draw(form)

        assertThat(texts).contains("How did it go?")
        assertThat(texts.any { it.startsWith("Walking, today ") }).isTrue()
        assertThat(texts).containsAtLeast(
            "40 min", "3.0 km (phone and band)", "200 kcal (band)", "Heart 110 avg · 130 max (from the readings)",
        ).inOrder()
        assertThat(texts).containsAtLeast("HOW IT FELT", "Easy", "Right", "Hard").inOrder()
        assertThat(texts).contains("In your words")
        assertThat(texts).contains("Tap the microphone on your keyboard to speak instead of typing.")
        assertThat(texts).contains(
            "Sends this session, your words, your last six weeks of sessions and your weight trend to OpenAI with your key. " +
                "One of today's 30 AI requests.",
        )
    }

    @Test
    fun `a matched plan is named and can be refused`() {
        var refused = false
        draw(form.copy(plan = storedPlan(createdAt = NOW).copy(id = 1)), onNotThisPlan = { refused = true })

        assertThat(render.textsAgain()).contains("Planned: Steady walk")
        assertThat(render.roleOf("Not this plan")).isEqualTo(Role.Button)
        render.click("Not this plan")
        assertThat(refused).isTrue()
    }

    @Test
    fun `no plan, no planned line`() {
        assertThat(draw(form).none { it.startsWith("Planned:") }).isTrue()
    }

    @Test
    fun `the felt chips show the one picked and call back`() {
        var felt: Felt? = null
        draw(form.copy(felt = Felt.RIGHT), onFeel = { felt = it })

        assertThat(render.isSelected("Right")).isTrue()
        assertThat(render.isSelected("Hard")).isFalse()
        render.click("Hard")
        assertThat(felt).isEqualTo(Felt.HARD)
    }

    @Test
    fun `the two saves wait for a felt effort or words`() {
        draw(form)
        assertThat(render.isEnabled("Save and get feedback")).isFalse()
        assertThat(render.isEnabled("Just save")).isFalse()

        var asked = false
        var saved = false
        draw(form.copy(felt = Felt.EASY), onSaveAndAsk = { asked = true }, onJustSave = { saved = true })
        render.click("Save and get feedback")
        render.click("Just save")
        assertThat(asked).isTrue()
        assertThat(saved).isTrue()
    }

    @Test
    fun `working, saved and a failure after saving are said`() {
        assertThat(draw(form.copy(felt = Felt.EASY, working = true, askingTrainer = true))).contains("Saving and asking the trainer…")
        assertThat(draw(form.copy(felt = Felt.EASY, saved = true))).contains("Saved.")
        val failed = draw(form.copy(felt = Felt.EASY, saved = true, failure = EstimateResult.Unreachable()))
        assertThat(failed).contains("Your words are saved. Could not reach the model. Get feedback is on the session's row.")
        assertThat(failed).doesNotContain("Saved.")
    }

    /** Just save asks nobody, so it says only that it is saving. */
    @Test
    fun `just saving says Saving, not asking the trainer`() {
        val texts = draw(form.copy(felt = Felt.EASY, working = true, askingTrainer = false))

        assertThat(texts).contains("Saving…")
        assertThat(texts).doesNotContain("Saving and asking the trainer…")
    }

    /** What is saved is what is on screen: nothing can be changed while a save is under way. */
    @Test
    fun `while saving, the chips, the words and Not this plan are disabled`() {
        draw(form.copy(felt = Felt.EASY, working = true, plan = storedPlan(createdAt = NOW).copy(id = 1)))

        assertThat(render.isEnabled("Easy")).isFalse()
        assertThat(render.isEnabled("Hard")).isFalse()
        assertThat(render.isEnabled("In your words")).isFalse()
        assertThat(render.isEnabled("Not this plan")).isFalse()

        draw(form.copy(felt = Felt.EASY, plan = storedPlan(createdAt = NOW).copy(id = 1)))

        assertThat(render.isEnabled("Easy")).isTrue()
        assertThat(render.isEnabled("In your words")).isTrue()
        assertThat(render.isEnabled("Not this plan")).isTrue()
    }

    @Test
    fun `a session no longer in the record says so and nothing else`() {
        val texts = draw(ReviewSessionViewModel.State(gone = true, today = TEST_EPOCH_DAY))

        assertThat(texts).contains("This session is no longer in the record.")
        assertThat(texts).doesNotContain("HOW IT FELT")
        assertThat(texts).doesNotContain("Just save")
    }

    @Test
    fun `a session gone while writing says the words were saved`() {
        val texts = draw(form.copy(words = "Invented words.", gone = true, saved = true))

        assertThat(texts).contains(
            "Your words are saved, but this session is no longer in the record, so no feedback was asked for.",
        )
        assertThat(texts).doesNotContain("Just save")
    }

    @Test
    fun `a refused save is said`() {
        assertThat(draw(form.copy(words = "Invented words.", refused = ActionRefused.MAYBE_PARTIAL))).contains(
            "That didn't finish, and may have only partly happened. What went wrong is under Settings → Recent problems.",
        )
    }

    /** D4: feedback is labelled advice, never a measurement. */
    @Test
    fun `feedback shows its headline, its label and its four parts, then two ways on`() {
        var planNext = false
        var done = false
        val texts = draw(form.copy(felt = Felt.RIGHT, feedback = FEEDBACK), onPlanNext = { planNext = true }, onDone = { done = true })

        assertThat(texts).contains("Feedback")
        assertThat(texts).containsAtLeast(
            "Invented headline.",
            "From the AI trainer · advice, not a measurement",
            "AGAINST THE PLAN", "Invented plan part.",
            "WHAT THE NUMBERS SAY", "Invented numbers part.",
            "FOR NEXT TIME", "Invented next part.",
            "THIS WEEK", "Invented week part.",
        ).inOrder()
        assertThat(texts).doesNotContain("HOW IT FELT")
        render.click("Plan the next one")
        render.click("Done")
        assertThat(planNext).isTrue()
        assertThat(done).isTrue()
    }

    private fun draw(
        state: ReviewSessionViewModel.State,
        onFeel: (Felt) -> Unit = {},
        onNotThisPlan: () -> Unit = {},
        onSaveAndAsk: () -> Unit = {},
        onJustSave: () -> Unit = {},
        onPlanNext: () -> Unit = {},
        onDone: () -> Unit = {},
    ): List<String> = render.texts {
        ReviewSessionScreen(
            state = state, onBack = {}, onFeel = onFeel, onWords = {}, onNotThisPlan = onNotThisPlan,
            onSaveAndAsk = onSaveAndAsk, onJustSave = onJustSave, onPlanNext = onPlanNext, onDone = onDone,
        )
    }
}
