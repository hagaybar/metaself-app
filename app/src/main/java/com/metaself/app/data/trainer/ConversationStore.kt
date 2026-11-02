package com.metaself.app.data.trainer

import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.domain.trainer.TrainerMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The conversation's messages (D145, D148): the owner's and the trainer's replies. The feedback and letters the
 * thread shows are not here (D146). Writes throw; the screen catches them (`guarded`) and says so.
 */
interface ConversationStore {
    /** Every message, oldest first. */
    fun observe(): Flow<List<TrainerMessage>>

    suspend fun message(id: Long): TrainerMessage?

    /** Stores the owner's message in [conversation], not answered; its id. */
    suspend fun addOwn(text: String, atMillis: Long, conversation: String): Long

    /** D159: stores the coach's note in its [conversation], as the trainer's words that answer nothing; its id. */
    suspend fun addNote(text: String, atMillis: Long, conversation: String): Long

    /**
     * Stores the trainer's reply to the owner's message [to], in that message's conversation, and marks that message
     * answered — one transaction, so a reply is never stored beside a message still marked not answered. Its id.
     */
    suspend fun addReply(to: Long, text: String, model: String, atMillis: Long): Long

    /** D145: the owner's message [id] and the reply it got; a reply's id deletes nothing. */
    suspend fun deleteOwn(id: Long)

    /** D159: every message of [conversation]. Feedback and letters are not messages, and stay. */
    suspend fun deleteConversation(conversation: String)
}

class RoomConversationStore @Inject constructor(
    private val dao: TrainerDao,
    private val transaction: DatabaseTransaction,
) : ConversationStore {

    override fun observe(): Flow<List<TrainerMessage>> = dao.observeMessages().map { rows -> rows.map { it.toMessage() } }

    override suspend fun message(id: Long): TrainerMessage? = dao.message(id)?.toMessage()

    override suspend fun addOwn(text: String, atMillis: Long, conversation: String): Long =
        dao.insertMessage(TrainerMessageEntity(0, atMillis, fromOwner = true, text = text, model = null, answered = false, replyTo = null, conversation = conversation))

    override suspend fun addNote(text: String, atMillis: Long, conversation: String): Long =
        dao.insertMessage(TrainerMessageEntity(0, atMillis, fromOwner = false, text = text, model = null, answered = false, replyTo = null, conversation = conversation))

    override suspend fun addReply(to: Long, text: String, model: String, atMillis: Long): Long {
        var id = 0L
        transaction.run {
            check(dao.markAnswered(to) == 1) { "the message answered is not the owner's, or is gone" }
            val conversation = checkNotNull(dao.message(to)) { "the message answered is gone" }.conversation
            id = dao.insertMessage(TrainerMessageEntity(0, atMillis, fromOwner = false, text = text, model = model, answered = false, replyTo = to, conversation = conversation))
        }
        return id
    }

    override suspend fun deleteOwn(id: Long) {
        dao.deleteOwnMessage(id)
    }

    override suspend fun deleteConversation(conversation: String) {
        dao.deleteConversation(conversation)
    }
}

fun TrainerMessageEntity.toMessage(): TrainerMessage = TrainerMessage(id, atMillis, fromOwner, text, model, answered, replyTo, conversation)
