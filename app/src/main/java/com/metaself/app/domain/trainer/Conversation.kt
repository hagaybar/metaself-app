package com.metaself.app.domain.trainer

import com.metaself.app.domain.letter.LetterTexts
import com.metaself.app.domain.movement.WorkoutKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One message of the conversation (D145, D148): the owner's ([fromOwner]) or the trainer's reply. [model] is null on
 * his; [answered] says whether his got a reply; [replyTo] is, on a reply, the id of the message it answers;
 * [conversation] is the conversation it belongs to (D159, [Conversation.talk] and its neighbours); [opened] is, on a
 * reply that read the file, what it read ([ReadLine], D163), "" when it opened nothing, null on anything older.
 */
data class TrainerMessage(
    val id: Long,
    val atMillis: Long,
    val fromOwner: Boolean,
    val text: String,
    val model: String?,
    val answered: Boolean,
    val replyTo: Long? = null,
    val conversation: String = "",
    val opened: String? = null,
)

/** What the thread shows (D146): the messages, and the feedback and letters in their place — never copied. */
sealed interface ThreadItem {
    val atMillis: Long

    /** D159: the conversation this item is in. */
    val conversation: String

    data class Said(val message: TrainerMessage) : ThreadItem {
        override val atMillis: Long get() = message.atMillis
        override val conversation: String get() = message.conversation
    }

    /** A session's feedback (D87), read from its review; [kind] and [minutes] are null when the session is gone. */
    data class AfterSession(
        val workoutId: Long,
        override val atMillis: Long,
        val kind: WorkoutKind?,
        val minutes: Int?,
        val feedback: Feedback,
    ) : ThreadItem {
        override val conversation: String get() = Conversation.session(workoutId)
    }

    /** A weekly letter (D101, D140), read from its row; it opens the letter page. */
    data class Letter(val weekMonday: Long, override val atMillis: Long, val texts: LetterTexts) : ThreadItem {
        override val conversation: String get() = Conversation.letter(weekMonday)
    }
}

enum class LineKind { MESSAGE, SESSION_FEEDBACK, LETTER }

/** One item of the conversation as a reply's request sends it (D147): local time, who, what, and its words. */
data class ConversationLine(
    val at: LocalDateTime,
    val fromOwner: Boolean,
    val kind: LineKind,
    val text: String,
    val sessionKind: WorkoutKind? = null,
    val sessionMinutes: Int? = null,
    val letterWeek: Long? = null,
    /** D159: it is in the conversation being written in now, not another the coach remembers. */
    val thisConversation: Boolean = false,
)

/** D147: the running weekly plan, the week the owner is in ([weekIndex], from 0) and its goal (on [ask]). */
data class TalkPlan(val ask: ProgrammeAsk, val startEpochDay: Long, val plan: WeeksPlan, val weekIndex: Int)

/**
 * D159: one conversation as a list shows it. [first] is the item it began with; [firstWords] the owner's first
 * message, when he has written one; [hasMessages] whether Delete this conversation has anything to remove.
 */
data class ConversationSummary(
    val key: String,
    val first: ThreadItem,
    val firstWords: String?,
    val lastAtMillis: Long,
    val lastWords: String,
    val hasMessages: Boolean,
    val notAnswered: Boolean,
)

/** D146, D147, D159: the thread's order, its conversations and the window a reply reads. Pure. */
object Conversation {

    /** D147: the days of conversation a reply reads, word for word. */
    const val WINDOW_DAYS = 14

    /** D147: the newest characters of those days that are sent — a chosen size, not a measured limit. */
    const val MAX_CHARS = 24_000

    /** Time order; two at the same moment keep their order of writing (a message's id). */
    private val ORDER = compareBy<ThreadItem> { it.atMillis }.thenBy { (it as? ThreadItem.Said)?.message?.id ?: 0L }

    /** The longest message the owner can send — a chosen size, so a fresh message always fits [MAX_CHARS]. */
    const val MAX_MESSAGE = 2_000

    private const val DAY_MILLIS = 86_400_000L

    /** D159: a conversation begun in the box, by the moment it began. */
    fun talk(startedAtMillis: Long): String = "talk:$startedAtMillis"

    /** D159: a session's feedback and what was said about it. */
    fun session(workoutId: Long): String = "session:$workoutId"

    /** D159: a letter and what was said about it. */
    fun letter(weekMonday: Long): String = "letter:$weekMonday"

    /** D159: the coach's note and what was said about it, by the moment the note was written. */
    fun note(writtenAtMillis: Long): String = "note:$writtenAtMillis"

    /** D159: a message from before conversations, by its local day. */
    fun day(date: LocalDate): String = "day:$date"

    /** D159: whether [key] is a note's conversation. */
    fun isNote(key: String): Boolean = key.startsWith("note:")

    /**
     * D159: the conversation of each message of a file from before they had one — its local day; a reply goes with the
     * message it answers, whichever day it arrived. By id, for every message in [messages].
     */
    fun byDay(messages: List<TrainerMessage>, zone: ZoneId): Map<Long, String> {
        val own = messages.associate { it.id to day(Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate()) }
        return messages.associate { message -> message.id to (own[message.replyTo] ?: own.getValue(message.id)) }
    }

    /** D159: every conversation in [thread] (in time order), the newest activity first. */
    fun summaries(thread: List<ThreadItem>): List<ConversationSummary> =
        thread.groupBy { it.conversation }.map { (key, items) ->
            val said = items.filterIsInstance<ThreadItem.Said>().map { it.message }
            val last = items.last()
            ConversationSummary(
                key = key,
                first = items.first(),
                firstWords = said.firstOrNull { it.fromOwner }?.text,
                lastAtMillis = last.atMillis,
                lastWords = text(last),
                hasMessages = said.isNotEmpty(),
                notAnswered = said.any { it.fromOwner && !it.answered },
            )
        }.sortedByDescending { it.lastAtMillis }

    /** Every item in time order; two at the same moment keep their order of writing (a message's id). */
    fun thread(
        messages: List<TrainerMessage>,
        feedback: List<ThreadItem.AfterSession>,
        letters: List<ThreadItem.Letter>,
    ): List<ThreadItem> =
        (messages.map { ThreadItem.Said(it) } + feedback + letters)
            .sortedWith(ORDER)

    /** The words an item is sent with, and counted by against [MAX_CHARS]. */
    fun text(item: ThreadItem): String = when (item) {
        is ThreadItem.Said -> item.message.text
        is ThreadItem.AfterSession -> with(item.feedback) {
            val note = note
            (listOf(headline) + if (note != null) listOf(note) else listOf(againstPlan, numbers, nextTime, thisWeek))
                .filter { it.isNotBlank() }.joinToString("\n\n")
        }
        is ThreadItem.Letter -> when (val texts = item.texts) {
            is LetterTexts.Coach -> listOf(texts.headline, texts.recognition, texts.payingOff, texts.lookAfter, texts.restsOn, texts.nextWeek, texts.close)
            is LetterTexts.SixParts -> listOf(texts.headline, texts.effort, texts.progress, texts.lookAt, texts.nextWeek, texts.close)
        }.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    /**
     * D147, D159: what a reply reads, oldest first, within [MAX_CHARS] characters of words. First [current], the
     * conversation being written in, whatever its age; then the rest of [thread] (in time order) newer than
     * [WINDOW_DAYS] days before [nowMillis]. Each is the newest run that fits: the first item that would cross the
     * cap is left out whole, and so is everything older in that part.
     */
    fun window(thread: List<ThreadItem>, nowMillis: Long, current: String? = null): List<ThreadItem> {
        val from = nowMillis - WINDOW_DAYS * DAY_MILLIS
        var chars = 0
        fun newestThatFit(items: List<ThreadItem>): List<ThreadItem> {
            val kept = mutableListOf<ThreadItem>()
            for (item in items.asReversed()) {
                val length = text(item).length
                if (chars + length > MAX_CHARS) break
                chars += length
                kept += item
            }
            return kept
        }
        val (here, others) = thread.partition { it.conversation == current }
        val kept = newestThatFit(here) + newestThatFit(others.filter { it.atMillis > from })
        return kept.sortedWith(ORDER)
    }

    /** [item] as a request sends it, its time in [zone]; [current] is the conversation being written in (D159). */
    fun line(item: ThreadItem, zone: ZoneId, current: String? = null): ConversationLine {
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(item.atMillis), zone)
        val here = item.conversation == current
        return when (item) {
            is ThreadItem.Said -> ConversationLine(at, item.message.fromOwner, LineKind.MESSAGE, text(item), thisConversation = here)
            is ThreadItem.AfterSession -> ConversationLine(at, false, LineKind.SESSION_FEEDBACK, text(item), item.kind, item.minutes, thisConversation = here)
            is ThreadItem.Letter -> ConversationLine(at, false, LineKind.LETTER, text(item), letterWeek = item.weekMonday, thisConversation = here)
        }
    }

    /** [text] cut to at most [MAX_MESSAGE] chars, never through a character outside the basic plane (an emoji). */
    fun cut(text: String): String {
        if (text.length <= MAX_MESSAGE) return text
        val end = if (Character.isHighSurrogate(text[MAX_MESSAGE - 1])) MAX_MESSAGE - 1 else MAX_MESSAGE
        return text.substring(0, end)
    }
}
