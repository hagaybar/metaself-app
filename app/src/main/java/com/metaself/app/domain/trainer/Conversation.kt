package com.metaself.app.domain.trainer

import com.metaself.app.domain.letter.LetterTexts
import com.metaself.app.domain.movement.WorkoutKind
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One message of the conversation (D145, D148): the owner's ([fromOwner]) or the trainer's reply. [model] is null on
 * his; [answered] says whether his got a reply; [replyTo] is, on a reply, the id of the message it answers.
 */
data class TrainerMessage(
    val id: Long,
    val atMillis: Long,
    val fromOwner: Boolean,
    val text: String,
    val model: String?,
    val answered: Boolean,
    val replyTo: Long? = null,
)

/** What the thread shows (D146): the messages, and the feedback and letters in their place — never copied. */
sealed interface ThreadItem {
    val atMillis: Long

    data class Said(val message: TrainerMessage) : ThreadItem {
        override val atMillis: Long get() = message.atMillis
    }

    /** A session's feedback (D87), read from its review; [kind] and [minutes] are null when the session is gone. */
    data class AfterSession(
        val workoutId: Long,
        override val atMillis: Long,
        val kind: WorkoutKind?,
        val minutes: Int?,
        val feedback: Feedback,
    ) : ThreadItem

    /** A weekly letter (D101, D140), read from its row; it opens the letter page. */
    data class Letter(val weekMonday: Long, override val atMillis: Long, val texts: LetterTexts) : ThreadItem
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
)

/** D147: the running weekly plan, the week the owner is in ([weekIndex], from 0) and its goal (on [ask]). */
data class TalkPlan(val ask: ProgrammeAsk, val startEpochDay: Long, val plan: WeeksPlan, val weekIndex: Int)

/** D146, D147: the thread's order and the window a reply reads. Pure. */
object Conversation {

    /** D147: the days of conversation a reply reads, word for word. */
    const val WINDOW_DAYS = 14

    /** D147: the newest characters of those days that are sent — a chosen size, not a measured limit. */
    const val MAX_CHARS = 24_000

    /** The longest message the owner can send — a chosen size, so a fresh message always fits [MAX_CHARS]. */
    const val MAX_MESSAGE = 2_000

    private const val DAY_MILLIS = 86_400_000L

    /** Every item in time order; two at the same moment keep their order of writing (a message's id). */
    fun thread(
        messages: List<TrainerMessage>,
        feedback: List<ThreadItem.AfterSession>,
        letters: List<ThreadItem.Letter>,
    ): List<ThreadItem> =
        (messages.map { ThreadItem.Said(it) } + feedback + letters)
            .sortedWith(compareBy<ThreadItem> { it.atMillis }.thenBy { (it as? ThreadItem.Said)?.message?.id ?: 0L })

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
     * D147: the items of [thread] (in time order) newer than [WINDOW_DAYS] days before [nowMillis], cut to the newest
     * [MAX_CHARS] characters of their words — the newest run that fits: the first item that would cross the cap is
     * left out whole, and so is everything older. Oldest first.
     */
    fun window(thread: List<ThreadItem>, nowMillis: Long): List<ThreadItem> {
        val from = nowMillis - WINDOW_DAYS * DAY_MILLIS
        val kept = ArrayDeque<ThreadItem>()
        var chars = 0
        for (item in thread.asReversed()) {
            if (item.atMillis <= from) break
            val length = text(item).length
            if (chars + length > MAX_CHARS) break
            chars += length
            kept.addFirst(item)
        }
        return kept.toList()
    }

    /** [item] as a request sends it, its time in [zone]. */
    fun line(item: ThreadItem, zone: ZoneId): ConversationLine {
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(item.atMillis), zone)
        return when (item) {
            is ThreadItem.Said -> ConversationLine(at, item.message.fromOwner, LineKind.MESSAGE, text(item))
            is ThreadItem.AfterSession -> ConversationLine(at, false, LineKind.SESSION_FEEDBACK, text(item), item.kind, item.minutes)
            is ThreadItem.Letter -> ConversationLine(at, false, LineKind.LETTER, text(item), letterWeek = item.weekMonday)
        }
    }

    /** [text] cut to at most [MAX_MESSAGE] chars, never through a character outside the basic plane (an emoji). */
    fun cut(text: String): String {
        if (text.length <= MAX_MESSAGE) return text
        val end = if (Character.isHighSurrogate(text[MAX_MESSAGE - 1])) MAX_MESSAGE - 1 else MAX_MESSAGE
        return text.substring(0, end)
    }
}
