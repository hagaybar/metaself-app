package com.metaself.app.domain.letter

import com.metaself.app.domain.ai.EstimateResult

/** Asking for a letter (D101): one ask; an implementation never retries a failed answer. */
fun interface LetterWriter {
    suspend fun write(request: LetterRequest): LetterReply
}

sealed interface LetterReply {
    /** [weekNote]: the letter's week_note (D116), null when the model left it out. */
    data class Written(val texts: LetterTexts, val model: String, val weekNote: String? = null) : LetterReply
    /**
     * @property status the provider's HTTP status when it answered with a refusal, else null — so the weekly
     *   job can tell a provider error worth retrying (5xx, 429) from a refusal no retry mends (D99).
     */
    data class Failed(val failure: EstimateResult, val status: Int? = null) : LetterReply
}
