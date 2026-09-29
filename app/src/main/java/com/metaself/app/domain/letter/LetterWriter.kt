package com.metaself.app.domain.letter

import com.metaself.app.domain.ai.EstimateResult

/** Asking for a letter (D101): one ask; an implementation never retries a failed answer. */
fun interface LetterWriter {
    suspend fun write(request: LetterRequest): LetterReply
}

sealed interface LetterReply {
    data class Written(val texts: LetterTexts, val model: String) : LetterReply
    data class Failed(val failure: EstimateResult) : LetterReply
}
