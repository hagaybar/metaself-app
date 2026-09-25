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
