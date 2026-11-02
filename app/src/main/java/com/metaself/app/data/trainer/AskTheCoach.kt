package com.metaself.app.data.trainer

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.CoachAnswer
import com.metaself.app.domain.trainer.CoachLooks
import com.metaself.app.domain.trainer.CoachModel
import com.metaself.app.domain.trainer.CoachRequest
import com.metaself.app.domain.trainer.ConversationLine
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.OpenedDrawer
import com.metaself.app.domain.trainer.RememberedFact
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerReply
import javax.inject.Inject

/**
 * D161: one reply, in at most [CoachLooks.MAX] requests: the model names what it needs, the phone fetches it, the model
 * asks again or answers. **Only reads and asks**: the caller stores what comes of it, so the workbench (D163) can try a
 * sentence with nothing stored. A look that opens something and also has words opens: it had not finished reading. On
 * the last look only words count, and none is a failure, as is a look with neither words nor a drawer.
 */
class AskTheCoach @Inject constructor(
    private val file: CoachFile,
    private val model: CoachModel,
) {

    /**
     * @param thread everything said, for the search; [conversation] is the window a reply reads of it.
     * @param exceptMessage the stored message being answered, left out of a search of [thread]; null when not stored.
     * @param instructions replaces the app's own (the workbench); null sends them.
     */
    suspend fun answer(
        replyTo: ConversationLine,
        conversation: List<ConversationLine>,
        thread: List<ThreadItem>,
        exceptMessage: Long?,
        instructions: String? = null,
    ): CoachAnswer {
        val page = file.frontPage(thread)
        val opened = mutableListOf<OpenedDrawer>()
        val seen = mutableSetOf<DrawerAsk>()
        val remember = linkedSetOf<RememberedFact>()
        var look = 1
        while (true) {
            val request = CoachRequest(replyTo, conversation, page, opened.toList(), look)
            when (val turn = model.look(request, instructions)) {
                is TrainerReply.Failed -> return CoachAnswer.Failed(turn.failure, request)
                is TrainerReply.Answered -> {
                    remember += turn.value.remember
                    val fresh = turn.value.open.distinct().filter { it !in seen }
                    if (!request.lastLook && fresh.isNotEmpty()) {
                        val answers = file.open(fresh, thread, exceptMessage, opened.sumOf { it.answer.length })
                        // Only what was read counts as open: a drawer refused for the cap may be asked for again.
                        seen += answers.filter { it.read != null }.map { it.ask }
                        opened += answers
                        look++
                    } else {
                        val words = turn.value.reply.trim()
                        if (words.isNotEmpty()) {
                            return CoachAnswer.Answered(words, turn.model, opened.mapNotNull { it.read }, remember.toList(), request)
                        }
                        if (request.lastLook || turn.value.open.isEmpty()) return CoachAnswer.Failed(NO_WORDS, request)
                        // It asked again for what it already has, with no words: the next look says so by what it holds.
                        look++
                    }
                }
            }
        }
    }

    private companion object {
        val NO_WORDS = EstimateResult.Unreadable("the reply was not in the shape this app asked for", "no words")
    }
}
