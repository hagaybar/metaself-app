package com.metaself.app.domain.ai

/**
 * A conversation about one meal, so far (D58 §2, §12.2): every question the model has asked, every
 * answer the owner gave, and which question is on screen.
 *
 * Questions and answers past [at] are kept after *Back*, so that answering the same way again can
 * move forward without a request; a different answer drops them.
 *
 * @property cap how many questions were offered — never more are asked.
 * @property planned the model's latest `total_planned`.
 */
data class Chat(
    val description: String,
    val cap: Int,
    val planned: Int,
    val questions: List<Question>,
    val answers: List<String>,
    val at: Int,
) {
    init {
        require(at in questions.indices) { "the question shown is one that was asked" }
        require(answers.size <= questions.size) { "no answer without its question" }
    }

    /** The question on screen. */
    val shown: Question get() = questions[at]

    /** His remembered answer to the question on screen, after *Back*; null when not yet answered. */
    val chosen: String? get() = answers.getOrNull(at)

    /** The question's own number, from one. */
    val number: Int get() = at + 1

    /** The heading's *of up to N*: the latest plan, never above what was offered nor below this question. */
    val ofUpTo: Int get() = planned.coerceAtMost(cap).coerceAtLeast(number)

    /** The first [count] questions with their answers, as they are sent. */
    fun asked(count: Int): List<Asked> = (0 until count).map { Asked(questions[it].text, answers[it]) }
}

/** What to do next, as the conversation's rules decide it (D58 §2, §7, §12). */
sealed interface Next {

    /** Show [chat]; nothing is sent. */
    data class Show(val chat: Chat) : Next

    /** Ask the model for the next question. */
    data class Step(val chat: Chat, val asked: List<Asked>) : Next

    /**
     * Ask for the final analysis.
     *
     * @property deep whether it may ask for more thinking (D58 §8) — not on the day's last request
     *   (§12.4).
     * @property allowanceOnly true when questions were cut short because only one request remained,
     *   which the screen says (§7).
     */
    data class Final(val asked: List<Asked>, val deep: Boolean, val allowanceOnly: Boolean = false) : Next

    /** Nothing can be sent today: the ceiling's sentence, his words kept. */
    data object Ceiling : Next
}

/**
 * The rules of a conversation, pure: they decide and never send (D58 §2, §7, §12). The view model
 * sends what [Next] says and hands back what came.
 *
 * **One request is always kept for the result** (§7): a question is asked only with two or more of
 * the day's requests left, and the last one goes to the final analysis, sent as an everyday request
 * (§12.4).
 */
object MealConversation {

    /** The most questions ever offered (D58 §2.3). */
    const val MOST_QUESTIONS = 5

    /**
     * The conversation the first reply opens, or null when nothing more can be sent today.
     *
     * Question one is in hand, so N questions need N − 1 steps and the final analysis — N requests —
     * and the offer is the smallest of the model's plan, five, and what [remaining] allows.
     */
    fun open(description: String, first: Question, planned: Int, remaining: Int): Chat? {
        val cap = minOf(planned.coerceIn(1, MOST_QUESTIONS), remaining)
        if (cap < 1) return null
        return Chat(description, cap, planned, listOf(first), emptyList(), at = 0)
    }

    /**
     * He answered the question on screen with [text].
     *
     * The same answer as the remembered one (trimmed, ignoring case) moves forward to the kept next
     * question, with nothing sent. Any other answer drops every later question and answer, and asks
     * again — or goes to the final analysis once the offered number have been answered.
     */
    fun answer(chat: Chat, text: String, remaining: Int): Next {
        val answer = text.trim()
        val same = chat.chosen?.trim()?.equals(answer, ignoreCase = true) == true
        if (same && chat.at + 1 < chat.questions.size) return Next.Show(chat.copy(at = chat.at + 1))

        val kept = chat.copy(
            questions = chat.questions.take(chat.at + 1),
            answers = chat.answers.take(chat.at) + answer,
        )
        val asked = kept.asked(kept.answers.size)
        return if (asked.size >= chat.cap) beforeFinal(asked, remaining) else beforeStep(kept, asked, remaining)
    }

    /** The next question arrived: it is shown, and the plan it carried replaces the last. */
    fun arrived(chat: Chat, question: Question, planned: Int): Chat = chat.copy(
        questions = chat.questions + question,
        at = chat.questions.size,
        planned = planned,
    )

    /**
     * *That's enough, go ahead*: the answers up to and including the question shown, when it has a
     * remembered one; later ones are dropped (§12.2).
     */
    fun enough(chat: Chat, remaining: Int): Next {
        val count = if (chat.chosen != null) chat.at + 1 else chat.at
        return beforeFinal(chat.asked(count), remaining)
    }

    /** *Use your best guess* from the offer: no answers, even remembered ones (§12.2). */
    fun bestGuess(remaining: Int): Next = beforeFinal(emptyList(), remaining)

    /** *Use your best guess with what you've said so far*, after a step failed (§9). */
    fun bestGuessSoFar(asked: List<Asked>, remaining: Int): Next = beforeFinal(asked, remaining)

    /** Back one question, or null for back to the offer. Nothing is sent and nothing forgotten. */
    fun back(chat: Chat): Chat? = if (chat.at > 0) chat.copy(at = chat.at - 1) else null

    /** A step, when two or more requests remain; the final analysis on the last one; else the ceiling. */
    fun beforeStep(chat: Chat, asked: List<Asked>, remaining: Int): Next = when {
        remaining >= 2 -> Next.Step(chat, asked)
        remaining == 1 -> Next.Final(asked, deep = false, allowanceOnly = true)
        else -> Next.Ceiling
    }

    /** The final analysis — asked to think harder unless it is the day's last request (§12.4). */
    fun beforeFinal(asked: List<Asked>, remaining: Int): Next = when {
        remaining >= 2 -> Next.Final(asked, deep = true)
        remaining == 1 -> Next.Final(asked, deep = false)
        else -> Next.Ceiling
    }
}
