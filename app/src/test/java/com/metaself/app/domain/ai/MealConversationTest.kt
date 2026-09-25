package com.metaself.app.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The rules of a conversation (D58 §2, §7, §12). Every meal and answer here is invented. */
class MealConversationTest {

    private val q1 = Question("How big was the container?", listOf("Small box", "Standard box", "Not sure"))
    private val q2 = Question("What was the sauce like?", listOf("Thin", "Creamy", "Not sure"))
    private val q3 = Question("How much cheese?", listOf("A sprinkle", "A layer", "Not sure"))

    private fun opened(planned: Int = 3, remaining: Int = 20): Chat =
        MealConversation.open("pasta from a counter", q1, planned, remaining)!!

    /** Three questions asked and answered, the third on screen with its answer. */
    private fun throughThree(): Chat {
        var chat = opened()
        chat = (MealConversation.answer(chat, "Standard box", 20) as Next.Step).chat
        chat = MealConversation.arrived(chat, q2, planned = 3)
        chat = (MealConversation.answer(chat, "Creamy", 20) as Next.Step).chat
        chat = MealConversation.arrived(chat, q3, planned = 3)
        return chat
    }

    @Test
    fun `the offer is the smallest of the plan, five, and what the day allows`() {
        assertThat(MealConversation.open("x", q1, planned = 3, remaining = 20)!!.cap).isEqualTo(3)
        assertThat(MealConversation.open("x", q1, planned = 9, remaining = 20)!!.cap).isEqualTo(5)
        assertThat(MealConversation.open("x", q1, planned = 4, remaining = 2)!!.cap).isEqualTo(2)
        assertThat(MealConversation.open("x", q1, planned = 0, remaining = 20)!!.cap).isEqualTo(1)
    }

    @Test
    fun `with nothing left today, no offer is made`() {
        assertThat(MealConversation.open("x", q1, planned = 3, remaining = 0)).isNull()
    }

    @Test
    fun `answering below the offer asks for the next question, with every answer so far`() {
        val next = MealConversation.answer(opened(), " Standard box ", remaining = 20) as Next.Step

        assertThat(next.asked).containsExactly(Asked(q1.text, "Standard box"))
    }

    @Test
    fun `answering the last offered question goes to the final analysis, with more thinking`() {
        val next = MealConversation.answer(throughThree(), "A layer", remaining = 20) as Next.Final

        assertThat(next.asked.map { it.answer }).containsExactly("Standard box", "Creamy", "A layer").inOrder()
        assertThat(next.deep).isTrue()
    }

    @Test
    fun `that's enough goes to the final analysis with what was said`() {
        val chat = throughThree()

        val next = MealConversation.enough(chat, remaining = 20) as Next.Final

        assertThat(next.asked.map { it.answer }).containsExactly("Standard box", "Creamy").inOrder()
    }

    @Test
    fun `back shows the earlier question from memory, with its answer`() {
        val back = MealConversation.back(throughThree())!!

        assertThat(back.shown).isEqualTo(q2)
        assertThat(back.chosen).isEqualTo("Creamy")
        assertThat(back.questions).hasSize(3)
    }

    @Test
    fun `the same answer again moves forward with nothing sent, ignoring case and spaces`() {
        val back = MealConversation.back(throughThree())!!

        val next = MealConversation.answer(back, " creamy", remaining = 20) as Next.Show

        assertThat(next.chat.shown).isEqualTo(q3)
    }

    @Test
    fun `a different answer drops every later question and asks again`() {
        val back = MealConversation.back(throughThree())!!

        val next = MealConversation.answer(back, "Thin", remaining = 20) as Next.Step

        assertThat(next.chat.questions).containsExactly(q1, q2).inOrder()
        assertThat(next.asked.map { it.answer }).containsExactly("Standard box", "Thin").inOrder()
    }

    @Test
    fun `that's enough after going back keeps the shown question's answer and drops the later`() {
        val answeredAll = throughThree().let { it.copy(answers = it.answers + "A layer") }
        val back = MealConversation.back(answeredAll)!!

        val next = MealConversation.enough(back, remaining = 20) as Next.Final

        assertThat(next.asked.map { it.answer }).containsExactly("Standard box", "Creamy").inOrder()
    }

    @Test
    fun `back from the first question is back to the offer`() {
        assertThat(MealConversation.back(opened())).isNull()
    }

    @Test
    fun `best guess from the offer sends no answers`() {
        assertThat((MealConversation.bestGuess(remaining = 20) as Next.Final).asked).isEmpty()
    }

    @Test
    fun `with one request left, no question is asked and the result is worked out as an everyday request`() {
        val next = MealConversation.answer(opened(), "Standard box", remaining = 1) as Next.Final

        assertThat(next.allowanceOnly).isTrue()
        assertThat(next.deep).isFalse()
        assertThat(next.asked).hasSize(1)
    }

    @Test
    fun `the final analysis on the day's last request asks for no more thinking`() {
        assertThat((MealConversation.bestGuess(remaining = 1) as Next.Final).deep).isFalse()
    }

    @Test
    fun `with nothing left, the ceiling`() {
        assertThat(MealConversation.answer(opened(), "Standard box", remaining = 0)).isEqualTo(Next.Ceiling)
        assertThat(MealConversation.bestGuess(remaining = 0)).isEqualTo(Next.Ceiling)
    }

    @Test
    fun `best guess after a failed step uses what was said so far`() {
        val asked = listOf(Asked(q1.text, "Standard box"))

        assertThat((MealConversation.bestGuessSoFar(asked, remaining = 5) as Next.Final).asked).isEqualTo(asked)
    }

    @Test
    fun `the heading follows the latest plan, never above the offer nor below the question`() {
        val chat = opened(planned = 3)
        assertThat(chat.ofUpTo).isEqualTo(3)
        assertThat(chat.copy(planned = 5).ofUpTo).isEqualTo(3)
        val third = throughThree().copy(planned = 1)
        assertThat(third.ofUpTo).isEqualTo(3)
    }

    @Test
    fun `a new question replaces the plan`() {
        val chat = MealConversation.arrived(opened(planned = 3), q2, planned = 2)

        assertThat(chat.shown).isEqualTo(q2)
        assertThat(chat.planned).isEqualTo(2)
    }
}
