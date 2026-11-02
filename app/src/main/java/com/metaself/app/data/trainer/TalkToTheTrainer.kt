package com.metaself.app.data.trainer

import com.metaself.app.data.ai.SheetPrompt
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.letter.LetterStore
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.CoachAnswer
import com.metaself.app.domain.trainer.Conversation
import com.metaself.app.domain.trainer.ConversationSummary
import com.metaself.app.domain.trainer.ReadLine
import com.metaself.app.domain.trainer.RememberedFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.Sheets
import com.metaself.app.domain.trainer.ThreadItem
import com.metaself.app.domain.trainer.TrainerMessage
import com.metaself.app.domain.trainer.TrainerReview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject

/**
 * The conversation with the trainer (D145–D147): the thread is built here from three places — the messages, each
 * review's feedback, each letter — and the last two are never copied (D146). A reply is asked for only when the owner
 * sends (D84), and opens his file as it needs ([AskTheCoach], D161): at most three requests, each counted. It reads
 * every conversation, the one being written in first (D159). What the owner said goes on the sheet with the reply. Writes
 * throw, but for that fact; the screen catches them.
 */
class TalkToTheTrainer @Inject constructor(
    private val coach: AskTheCoach,
    private val messages: ConversationStore,
    private val store: TrainerStore,
    private val letters: LetterStore,
    private val weekNotes: WeekNoteStore,
    private val sheets: SheetStore,
    private val problems: ProblemLog,
    private val today: Today,
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

    /** D145, D161: asks for a reply to the owner's message [id]; Try again is this, again. */
    suspend fun answer(id: Long): Talked {
        val message = messages.message(id)
        if (message == null || !message.fromOwner || message.answered) return Talked.NothingToAnswer
        val thread = observeThread().first()
        return when (val reply = ask(message, thread, exceptMessage = id, instructions = null)) {
            is CoachAnswer.Failed -> Talked.Failed(reply.failure)
            is CoachAnswer.Answered -> {
                messages.addReply(id, reply.text, reply.model, now(), ReadLine.of(reply.opened, reply.foodUnchecked), reply.proposal)
                remember(reply.remember)
                Talked.Replied
            }
        }
    }

    /**
     * D163: the workbench: [words] asked about exactly as a message would be, under [instructions] (null: the app's
     * own). **Nothing is stored**: not the words, not the reply, and nothing goes on the sheet.
     */
    suspend fun tryOut(words: String, instructions: String?): CoachAnswer {
        val message = TrainerMessage(0, now(), true, Conversation.cut(words.trim()), null, false, conversation = TRIED_OUT)
        val thread = observeThread().first() + ThreadItem.Said(message)
        return ask(message, thread, exceptMessage = 0, instructions = instructions)
    }

    /** D147, D159: the window at this moment, [message] the one answered. */
    private suspend fun ask(message: TrainerMessage, thread: List<ThreadItem>, exceptMessage: Long, instructions: String?): CoachAnswer {
        val zone = ZoneId.systemDefault()
        val current = message.conversation
        val window = Conversation.window(thread, now(), current)
        return coach.answer(
            replyTo = Conversation.line(ThreadItem.Said(message), zone, current),
            conversation = window.map { Conversation.line(it, zone, current) },
            thread = thread,
            exceptMessage = exceptMessage,
            instructions = instructions,
        )
    }

    /**
     * D161: what the owner said, on the sheet at once, as a version of its own, under the sheet's caps (D149). A group
     * this app does not know and anything over a cap is dropped. Never worth failing a stored reply for: a failed
     * write is logged by kind and dropped; the weekly rewrite can still pick the fact up.
     */
    private suspend fun remember(facts: List<RememberedFact>) {
        val said = facts.mapNotNull { fact -> SheetPrompt.groupOf(fact.group)?.let { it to fact.text } }
        if (said.isEmpty()) return
        try {
            val previous = sheets.newest()?.facts.orEmpty()
            val after = Sheets.remembered(previous, said, today().toEpochDay())
            if (after !== previous) sheets.add(now(), after, SheetMadeBy.CONVERSATION, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unwritten: Exception) {
            problems.record("coach", "a fact was not kept: ${unwritten::class.java.name}")
        }
    }

    /** D145: the owner's message [id] and the reply it got. */
    suspend fun delete(id: Long) = messages.deleteOwn(id)

    /** D159: the messages of [conversation]; a feedback or a letter stays where it is. */
    suspend fun deleteConversation(conversation: String) = messages.deleteConversation(conversation)

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

    private companion object {
        /** The conversation key of a sentence on the workbench, which no stored item has. */
        const val TRIED_OUT = "workbench"
    }
}
