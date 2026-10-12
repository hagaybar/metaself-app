package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.TrainerMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [ConversationStore] in memory, numbered from 1, with [RoomConversationStore]'s rules. [failWrites] makes every write throw. */
class InMemoryConversationStore : ConversationStore {
    val messages = MutableStateFlow<List<TrainerMessage>>(emptyList())
    var failWrites = false
    private var next = 1L

    override fun observe(): Flow<List<TrainerMessage>> = messages.map { all -> all.sortedWith(compareBy({ it.atMillis }, { it.id })) }

    override suspend fun message(id: Long): TrainerMessage? = messages.value.firstOrNull { it.id == id }

    override suspend fun addOwn(text: String, atMillis: Long): Long {
        check(!failWrites) { "invented write failure" }
        val id = next++
        messages.value = messages.value + TrainerMessage(id, atMillis, true, text, null, answered = false)
        return id
    }

    override suspend fun addReply(to: Long, text: String, model: String, atMillis: Long): Long {
        check(!failWrites) { "invented write failure" }
        check(messages.value.any { it.id == to && it.fromOwner }) { "the message answered is not the owner's, or is gone" }
        val id = next++
        messages.value = messages.value.map { if (it.id == to) it.copy(answered = true) else it } +
            TrainerMessage(id, atMillis, false, text, model, answered = false, replyTo = to)
        return id
    }

    override suspend fun deleteOwn(id: Long) {
        check(!failWrites) { "invented write failure" }
        messages.value = messages.value.filterNot { (it.id == id && it.fromOwner) || it.replyTo == id }
    }
}
