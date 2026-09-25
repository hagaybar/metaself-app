package com.metaself.app.ui.screen.propose

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.Asked
import com.metaself.app.domain.ai.Chat
import com.metaself.app.domain.ai.Next
import com.metaself.app.domain.ai.Question
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A conversation's stages, drawn (D58 §2, §9). Compose, so JUnit 4 — `org.junit.Test`. Every meal,
 * question and answer here is invented.
 */
@RunWith(RobolectricTestRunner::class)
class ConversationScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    private val q1 = Question("How big was the container?", listOf("Small box", "Standard box", "Not sure"))
    private val chat = Chat(MEAL, cap = 3, planned = 3, questions = listOf(q1), answers = emptyList(), at = 0)

    @Test
    fun `the offer says how many questions, with OK and the best guess`() {
        val texts = draw(ProposalUiState.Offer(chat))

        assertThat(texts).contains("I'd like to ask up to 3 questions to get this right.")
        assertThat(texts).contains("OK")
        assertThat(texts).contains("Use your best guess")
    }

    @Test
    fun `an offer of one question says one`() {
        val texts = draw(ProposalUiState.Offer(chat.copy(cap = 1)))

        assertThat(texts).contains("I'd like to ask one question to get this right.")
    }

    @Test
    fun `a question draws its answers, other, enough and back`() {
        val texts = draw(ProposalUiState.Asking(chat))

        assertThat(texts).contains("Question 1 of up to 3")
        assertThat(texts).contains("How big was the container?")
        assertThat(texts).containsAtLeast("Small box", "Standard box", "Not sure").inOrder()
        assertThat(texts).contains("Other")
        assertThat(texts).contains("That's enough, go ahead")
        assertThat(texts).contains("Back")
    }

    @Test
    fun `tapping an answer answers with its words`() {
        val answered = mutableListOf<String>()
        draw(ProposalUiState.Asking(chat), actions = actions(onAnswer = { answered += it }))

        render.click("Standard box")

        assertThat(answered).containsExactly("Standard box")
    }

    @Test
    fun `other cannot be sent empty`() {
        draw(ProposalUiState.Asking(chat))

        assertThat(render.isEnabled("Answer")).isFalse()
    }

    @Test
    fun `a question come back to says its answer, and his own words are back in other`() {
        val tapped = draw(ProposalUiState.Asking(chat.copy(answers = listOf("Standard box"))))
        assertThat(tapped).contains("Your answer: Standard box")

        val own = draw(ProposalUiState.Asking(chat.copy(answers = listOf("two boxes, shared"))))
        assertThat(own).contains("Your answer: two boxes, shared")
        assertThat(render.fieldTexts()).contains("two boxes, shared")
    }

    @Test
    fun `back on a question steps back`() {
        var backs = 0
        draw(ProposalUiState.Asking(chat), actions = actions(onStepBack = { backs++; true }))

        render.click("Back")

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun `a failed step offers to try again, to guess with what was said, or to type`() {
        val asked = listOf(Asked(q1.text, "Standard box"))
        val texts = draw(
            ProposalUiState.ConversationFailed(
                failure = "The answer could not be understood. Type the numbers instead.",
                asked = asked,
                answer = "not the shape",
                retry = Next.Step(chat, asked),
                bestGuessWith = asked,
            ),
        )

        assertThat(texts).contains("Try again")
        assertThat(texts).contains("Use your best guess with what you've said so far")
        assertThat(texts).contains("Type the numbers myself")
        assertThat(texts).contains("Show the model's answer")
        // His words and answers, kept: nothing is stored (§6).
        assertThat(texts).contains(MEAL)
        assertThat(texts).contains("How big was the container? — Standard box")
    }

    @Test
    fun `with nothing left today, only typing is offered`() {
        val texts = draw(
            ProposalUiState.ConversationFailed(
                failure = "You have used today's estimates. Type the numbers, or raise the daily limit in settings.",
                asked = emptyList(),
            ),
        )

        assertThat(texts).doesNotContain("Try again")
        assertThat(texts).contains("Type the numbers myself")
    }

    // Waiting is not drawn here: its spinner is an endless animation, and a render that idles the
    // main looper never finishes. What it says is decided in the view model (`Waiting.kind`,
    // `allowanceOnly`) and asserted there.

    /** Long answers wrap inside their own buttons, one above the next, never beside or over. */
    @Test
    @Config(qualifiers = "+w150dp")
    fun `long answers stack, one under the next`() {
        val long = Question(
            "How much dressing was on it?",
            listOf("A light drizzle over the top only", "Tossed through all of it, glossy", "Not sure"),
        )
        draw(ProposalUiState.Asking(chat.copy(questions = listOf(long))))

        assertThat(render.topDp("Tossed through")).isGreaterThan(render.topDp("A light drizzle"))
        assertThat(render.topDp("Not sure")).isGreaterThan(render.topDp("Tossed through"))
        assertThat(render.isDrawnBefore("A light drizzle", "Tossed through")).isTrue()
    }

    private fun actions(
        onAnswer: (String) -> Unit = {},
        onStepBack: () -> Boolean = { false },
    ) = NO_CONVERSATION.copy(onAnswer = onAnswer, onStepBack = onStepBack)

    private fun draw(
        state: ProposalUiState,
        actions: ConversationActions = NO_CONVERSATION,
        fromMyMeals: Boolean = false,
    ): List<String> =
        render.texts {
            ProposalScreen(
                state = state,
                description = MEAL,
                onDescribe = {},
                onSetAmount = { _, _ -> },
                onStep = { _, _ -> },
                onOpenWorth = {},
                onSetWorthBox = { _, _, _ -> },
                onCloseWorth = {},
                onUseYourFood = {},
                onUseEstimate = {},
                onCountInFoodUnit = {},
                onRemove = {},
                onTellItMore = {},
                conversation = actions,
                fromMyMeals = fromMyMeals,
                keepOnly = NO_KEEP_ONLY,
                onSave = {},
                onTypeItMyself = {},
                onAddKey = {},
                onCancel = {},
                onKeepAsMeal = {},
                onNameMeal = {},
                onGiveUpNaming = {},
                onKeepingDone = {},
                chosenRows = emptyList(),
                isToday = true,
                refusal = null,
            )
        }

    private companion object {
        const val MEAL = "pasta with a mushroom sauce from a takeaway counter"
    }
}
