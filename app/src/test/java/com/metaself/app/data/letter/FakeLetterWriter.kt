package com.metaself.app.data.letter

import com.metaself.app.domain.letter.LetterReply
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.LetterWriter

/** Hands back [replies] in turn (the last one again when they run out) and keeps every request asked. */
class FakeLetterWriter(vararg replies: LetterReply) : LetterWriter {
    private val scripted = replies.toMutableList()
    val asked = mutableListOf<LetterRequest>()

    override suspend fun write(request: LetterRequest): LetterReply {
        asked += request
        check(scripted.isNotEmpty()) { "no reply scripted" }
        return if (scripted.size > 1) scripted.removeAt(0) else scripted.single()
    }
}
