package com.metaself.app.data.trainer

import com.metaself.app.data.letter.LetterStore
import com.metaself.app.data.time.Now
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.Conversation
import com.metaself.app.domain.trainer.ConversationSummary
import com.metaself.app.domain.trainer.TalkPlan
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerMessage
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeekNotes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * The conversation with the trainer (D145–D147). The thread is built here from three places — the messages, each
 * review's feedback, each letter — and the last two are never copied (D146). A reply is one request, only when the
 * owner sends (D84): the fresh record as [AskTheTrainer.request] builds it for every path (where the sheet joins every
 * request in release 2, D151), with the conversation's window, the message answered, the running plan and the
 * coach's note in its question. A reply reads every conversation, the one being written in first (D159). Writes throw;
 * the screen catches them.
 */
class TalkToTheTrainer @Inject constructor(
    private val ask: AskTheTrainer,
    private val trainer: Trainer,
    private val messages: ConversationStore,
    private val store: TrainerStore,
    private val letters: LetterStore,
    private val weekNotes: WeekNoteStore,
    private val now: Now,
) {

    sealed interface Talked {
        /** The reply is stored, and the message marked answered. */
        data object Replied : Talked

        /** Nothing was stored: the message stays, not answered (D145). */
        data class Failed(val failure: EstimateResult) : Talked

        /** The message is gone, is not the owner's, or was answered already: nothing was asked. */
        data object NothingToAnswer : Talked
    }

    /** D146: every message, feedback and letter, in time order — read again whenever any of them changes. */
    fun observeThread(): Flow<List<ThreadItem>> = combine(
        messages.observe(),
        store.observeReviews(),
        store.observeReviewedWorkouts(),
        letters.observeAll(),
    ) { said, reviews, workouts, written -> thread(said, reviews, workouts, written) }

    /** D159: every conversation, the newest activity first. */
    fun observeConversations(): Flow<List<ConversationSummary>> = observeThread().map(Conversation::summaries)

    /**
     * D159: one conversation's items. A note's conversation with nothing stored yet shows the note while it is still
     * the one kept, by a row that is not stored (id 0): [say] stores it with the first message, since the note itself
     * is replaced (D116).
     */
    fun observeConversation(key: String): Flow<List<ThreadItem>> = combine(observeThread(), weekNotes.note) { thread, note ->
        val items = thread.filter { it.conversation == key }
        if (items.isEmpty() && note != null && key == Conversation.note(note.writtenAtMillis)) {
            listOf(ThreadItem.Said(TrainerMessage(0, note.writtenAtMillis, false, note.text, null, false, conversation = key)))
        } else {
            items
        }
    }

    /** D159: the words of a new conversation, begun now, from the box: its key and the message's id. */
    suspend fun begin(text: String): Pair<String, Long> {
        val key = Conversation.talk(now())
        return key to say(text, key)
    }

    /**
     * Stores the owner's words — trimmed, at most [Conversation.MAX_MESSAGE] chars — in [conversation], not yet
     * answered; the id. In a note's conversation the note goes first (D159).
     */
    suspend fun say(text: String, conversation: String): Long {
        val words = Conversation.cut(text.trim())
        require(words.isNotEmpty()) { "a message has words" }
        if (Conversation.isNote(conversation) && messages.observe().first().none { it.conversation == conversation }) {
            val note = weekNotes.note.first()
            if (note != null && Conversation.note(note.writtenAtMillis) == conversation) {
                messages.addNote(note.text, note.writtenAtMillis, conversation)
            }
        }
        return messages.addOwn(words, now(), conversation)
    }

    /** D145, D147: asks for a reply to the owner's message [id]; Try again is this, again. */
    suspend fun answer(id: Long): Talked {
        val message = messages.message(id)
        if (message == null || !message.fromOwner || message.answered) return Talked.NothingToAnswer
        return when (val reply = trainer.talk(ask.request(question(message)))) {
            is TrainerReply.Failed -> Talked.Failed(reply.failure)
            is TrainerReply.Answered -> {
                messages.addReply(id, reply.value, reply.model, now())
                Talked.Replied
            }
        }
    }

    /** D145: the owner's message [id] and the reply it got. */
    suspend fun delete(id: Long) = messages.deleteOwn(id)

    /** D159: the messages of [conversation]; a feedback or a letter stays where it is. */
    suspend fun deleteConversation(conversation: String) = messages.deleteConversation(conversation)

    /** D147, D159: the question a reply to [message] is asked with — the window at this moment. Reads only. */
    suspend fun question(message: TrainerMessage): TrainerQuestion.Talk {
        val zone = ZoneId.systemDefault()
        val current = message.conversation
        val window = Conversation.window(observeThread().first(), now(), current)
        val running = ask.running()
        val plan = running?.programme?.startEpochDay?.let { start -> TalkPlan(running.programme.ask, start, running.programme.plan, running.weekIndex) }
        val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate().toEpochDay()
        val note = runCatching { weekNotes.note.first() }.getOrNull()
        return TrainerQuestion.Talk(
            conversation = window.map { Conversation.line(it, zone, current) },
            replyTo = Conversation.line(ThreadItem.Said(message), zone, current),
            plan = plan,
            weekNote = WeekNotes.shown(note, MovementWeek.mondayOf(today))?.text,
        )
    }

    private fun thread(said: List<TrainerMessage>, reviews: List<TrainerReview>, workouts: List<Workout>, written: List<WeeklyLetter>): List<ThreadItem> {
        val byId = workouts.associateBy { it.id }
        val feedback = reviews.mapNotNull { review ->
            val text = review.feedback ?: return@mapNotNull null
            val at = review.feedbackAtMillis ?: return@mapNotNull null
            val workout = byId[review.workoutId]
            ThreadItem.AfterSession(review.workoutId, at, workout?.kind, workout?.durationMinutes, text)
        }
        return Conversation.thread(said, feedback, written.map { ThreadItem.Letter(it.weekMonday, it.createdAtMillis, it.texts) })
    }
}
