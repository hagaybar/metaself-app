package com.metaself.app.domain.ai

/**
 * One question the model asked about a meal, with the ready-made answers it offered (D58 §2.3).
 *
 * The phone adds *Other* and *That's enough* itself; they are never part of [options].
 */
data class Question(val text: String, val options: List<String>) {
    init {
        require(text.isNotBlank()) { "a question has words" }
        require(options.size in MIN_OPTIONS..MAX_OPTIONS) { "a question offers 2 to 6 answers" }
    }

    companion object {
        const val MIN_OPTIONS = 2

        /** Five answers and *Not sure* (D58 §4.1). */
        const val MAX_OPTIONS = 6
    }
}

/**
 * A question and the owner's answer to it, as both are sent back to the model (D58 §3): the
 * question in the model's own words, the answer as the button he tapped or the words he typed.
 */
data class Asked(val question: String, val answer: String)

/**
 * What one request of a conversation came to (D58 §4.1).
 *
 * [Failed] carries one of [EstimateResult]'s failures — no key, the ceiling, no network, a refusal,
 * or an unreadable reply — so the screen says it in the sentences it already has.
 */
sealed interface StepResult {

    /** The first request needed no question: the meal, estimated as today's describe does. */
    data class Estimate(val result: EstimateResult) : StepResult

    /** A question, and how many the model now plans in all, already clamped to 1..5. */
    data class Ask(val question: Question, val planned: Int) : StepResult

    /** A step answered that no more questions are needed. */
    data object Enough : StepResult

    data class Failed(val failure: EstimateResult) : StepResult
}

/**
 * Asks the model the requests a conversation is made of (D58 §3). Each call is one request (with
 * D57's learning retries, and D34's second ask where it applies), counted against the day's
 * ceiling; which one to make is [MealConversation]'s decision, never this.
 */
interface MealConversationAsker {

    /** The first request: an estimate, or the first question. */
    suspend fun open(description: String): StepResult

    /** A step: the next question, or none. [cap] is the most questions offered in all. */
    suspend fun next(description: String, asked: List<Asked>, cap: Int): StepResult

    /**
     * The final analysis. [deep] asks for more thinking where the model takes it (D58 §8), false on
     * the day's last request (§12.4); [moreDetail] is his *Ask again* sentence (§5.1).
     */
    suspend fun finish(
        description: String,
        asked: List<Asked>,
        moreDetail: String? = null,
        deep: Boolean = true,
    ): EstimateResult

    /** How many requests the day's ceiling still allows. */
    suspend fun remainingToday(): Int
}
