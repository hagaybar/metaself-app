package com.metaself.app.domain.trainer

import java.time.LocalDateTime

/** D149: the sheet's four groups, in the order the page shows them. Stored by name, never by ordinal. */
enum class FactGroup { BODY, TRAINING, GOALS, LIFE }

/** D149, D4: where a fact came from — the owner said it ("you said"), or the trainer inferred it ("I noticed"). */
enum class FactSource { SAID, NOTICED }

/**
 * One fact of the sheet (D149). [id] is numbered on the phone, so a rewrite can say which fact it continues;
 * [sinceEpochDay] is the day it became true. [edited]: the owner changed its text (D150, his edits win). Once dropped,
 * [droppedEpochDay] is the day, and [removedByOwner] says he removed it rather than a rewrite; a dropped fact is shown
 * under No longer true for [Sheets.DROPPED_DAYS] days, then goes.
 */
data class SheetFact(
    val id: Long,
    val group: FactGroup,
    val text: String,
    val sinceEpochDay: Long,
    val source: FactSource,
    val edited: Boolean = false,
    val droppedEpochDay: Long? = null,
    val removedByOwner: Boolean = false,
) {
    val current: Boolean get() = droppedEpochDay == null
}

/** D151: who made a version — the weekly rewrite, or the owner (an edit, a removal, going back). */
enum class SheetMadeBy { REWRITE, OWNER }

/**
 * One version of the sheet (D150): every rewrite and every change the owner makes is a row, so a bad rewrite can be
 * undone. [model] is the rewrite's, null on his. [restoredFrom]: on a version made by Go back, the version whose facts
 * it brings back.
 */
data class SheetVersion(
    val id: Long,
    val atMillis: Long,
    val facts: List<SheetFact>,
    val madeBy: SheetMadeBy,
    val model: String?,
    val restoredFrom: Long? = null,
) {
    /** The facts that are true now — what every request reads (D151). */
    val current: List<SheetFact> get() = facts.filter { it.current }

    /** No longer true, as of [today]: dropped within the last [Sheets.DROPPED_DAYS] days, newest first. */
    fun dropped(today: Long): List<SheetFact> =
        facts.filter { it.droppedEpochDay != null && Sheets.stillShown(it.droppedEpochDay, today) }
            .sortedByDescending { it.droppedEpochDay }
}

/** One fact of a rewrite's answer (D150): [id] the fact it continues, or null for a new one; [sinceEpochDay] null when unreadable. */
data class RewrittenFact(
    val id: Long?,
    val group: FactGroup,
    val text: String,
    val sinceEpochDay: Long?,
    val source: FactSource,
)

/**
 * D150: what the weekly rewrite sends — today, the current facts (with their ids and whether the owner edited them)
 * and the facts he removed that are still shown ([facts]), his note (D90), the moment the span starts (null: the
 * conversation's start), and the span itself as the conversation's lines, oldest first. Never the activity record.
 */
data class SheetRewriteRequest(
    val today: Long,
    val facts: List<SheetFact>,
    val aboutMe: String?,
    val since: LocalDateTime?,
    val conversation: List<ConversationLine>,
)

/** D150: asking for the rewritten sheet — one ask; an implementation never retries a failed answer. */
fun interface SheetWriter {
    suspend fun rewrite(request: SheetRewriteRequest): TrainerReply<List<RewrittenFact>>
}

/** D149, D150: the sheet's rules. Pure. */
object Sheets {

    /** D149: the cap, enforced here — never trusted to the model. Chosen sizes. */
    const val MAX_FACTS = 40
    const val MAX_CHARS = 4_000

    /** The longest fact the owner can write in an edit — a chosen size. */
    const val MAX_FACT = 300

    /** D149: a dropped fact is shown struck through for this many days, then goes. */
    const val DROPPED_DAYS = 28

    /** The newest characters of the span one rewrite reads — a chosen size, twice the reply's window (D147). */
    const val REWRITE_CHARS = 48_000

    fun stillShown(droppedEpochDay: Long, today: Long): Boolean = today - droppedEpochDay < DROPPED_DAYS

    /** The characters the cap counts: the current facts' words. */
    fun chars(facts: List<SheetFact>): Int = facts.filter { it.current }.sumOf { it.text.length }

    /**
     * D150: the facts after a rewrite answered [answer], from [previous] (the newest version's facts), on [today].
     *
     * An id naming a current fact continues it, once; any other is a new fact with a fresh id. A continued fact keeps
     * "edited" only with its text unchanged; a `since` after today or missing falls back to the continued fact's own,
     * else today. The cap: the owner's edited facts are considered first, then the rest, each in the model's order; a
     * fact is kept while the sheet stays within [MAX_FACTS] and [MAX_CHARS], and one that would cross either is left
     * out (later ones are still tried). An exact repeat (ignoring case) and a blank fact are left out. Every current
     * fact not kept is dropped today; dropped facts are carried while still shown.
     */
    fun afterRewrite(previous: List<SheetFact>, answer: List<RewrittenFact>, today: Long): List<SheetFact> {
        val currentById = previous.filter { it.current }.associateBy { it.id }
        var nextId = (previous.maxOfOrNull { it.id } ?: 0L) + 1
        val used = mutableSetOf<Long>()
        val seen = mutableSetOf<String>()
        val candidates = answer.mapNotNull { fact ->
            val text = fact.text.trim()
            if (text.isEmpty() || !seen.add(text.lowercase())) return@mapNotNull null
            val continued = fact.id?.let(currentById::get)?.takeIf { used.add(it.id) }
            val since = fact.sinceEpochDay?.takeIf { it <= today } ?: continued?.sinceEpochDay ?: today
            if (continued != null) {
                SheetFact(continued.id, fact.group, text, since, fact.source, edited = continued.edited && continued.text == text)
            } else {
                SheetFact(nextId++, fact.group, text, since, fact.source)
            }
        }
        val keptIds = mutableSetOf<Long>()
        var chars = 0
        for (fact in candidates.filter { it.edited } + candidates.filterNot { it.edited }) {
            if (keptIds.size >= MAX_FACTS) break
            if (chars + fact.text.length > MAX_CHARS) continue
            chars += fact.text.length
            keptIds += fact.id
        }
        val kept = candidates.filter { it.id in keptIds }
        val newlyDropped = previous.filter { it.current && it.id !in keptIds }.map { it.copy(droppedEpochDay = today, removedByOwner = false) }
        return kept + newlyDropped + carried(previous, today)
    }

    /** The owner's [text] for the current fact [id]: his words, "you said", edited. Throws when refused. */
    fun edit(previous: List<SheetFact>, id: Long, text: String, today: Long): List<SheetFact> {
        val words = text.trim()
        require(words.isNotEmpty()) { "a fact has words" }
        require(words.length <= MAX_FACT) { "a fact is at most $MAX_FACT characters" }
        val fact = previous.firstOrNull { it.id == id && it.current } ?: throw IllegalArgumentException("no such fact")
        val changed = previous.map { if (it === fact) it.copy(text = words, source = FactSource.SAID, edited = true) else it }
        require(chars(changed) <= MAX_CHARS) { "the sheet is at most $MAX_CHARS characters" }
        return changed.filter { it.current } + carried(changed, today)
    }

    /** The owner removes the current fact [id]: it moves to No longer true, marked his. Throws when there is none. */
    fun remove(previous: List<SheetFact>, id: Long, today: Long): List<SheetFact> {
        val fact = previous.firstOrNull { it.id == id && it.current } ?: throw IllegalArgumentException("no such fact")
        val changed = previous.map { if (it === fact) it.copy(droppedEpochDay = today, removedByOwner = true) else it }
        return changed.filter { it.current } + carried(changed, today)
    }

    /** The dropped facts of [facts] still shown on [today]. */
    private fun carried(facts: List<SheetFact>, today: Long): List<SheetFact> =
        facts.filter { it.droppedEpochDay != null && stillShown(it.droppedEpochDay, today) }

    /** [versions] in the order they were made (by id). */
    private fun ordered(versions: List<SheetVersion>) = versions.sortedBy { it.id }

    /** D150: Go back is offered while the newest version is a rewrite with a version before it. */
    fun canGoBack(versions: List<SheetVersion>): Boolean = goBackTo(versions) != null

    /** The version Go back restores: the one before the newest version, when that newest is a rewrite; else null. */
    fun goBackTo(versions: List<SheetVersion>): SheetVersion? {
        val all = ordered(versions)
        val newest = all.lastOrNull() ?: return null
        if (newest.madeBy != SheetMadeBy.REWRITE) return null
        return all.getOrNull(all.size - 2)
    }

    /**
     * D150: when the last successful rewrite was made — the newest rewrite not undone by Go back (a version restoring
     * an older one undoes every rewrite made between them) — or null when there has been none, so the next rewrite
     * reads from the start.
     */
    fun lastRewriteAt(versions: List<SheetVersion>): Long? {
        val all = ordered(versions)
        val undone = all.filter { it.restoredFrom != null }.flatMap { back ->
            all.filter { it.madeBy == SheetMadeBy.REWRITE && it.id > back.restoredFrom!! && it.id < back.id }.map { it.id }
        }.toSet()
        return all.filter { it.madeBy == SheetMadeBy.REWRITE && it.id !in undone }.maxOfOrNull { it.atMillis }
    }

    /** When the newest rewrite was made, undone or not — so one Sunday is never rewritten twice. */
    fun newestRewriteAt(versions: List<SheetVersion>): Long? =
        versions.filter { it.madeBy == SheetMadeBy.REWRITE }.maxOfOrNull { it.atMillis }

    /**
     * D150: the items of [thread] (in time order) after [afterMillis] (all of them when null), cut to the newest
     * [REWRITE_CHARS] characters of their words — the newest run that fits, as [Conversation.window] cuts. Oldest first.
     */
    fun span(thread: List<ThreadItem>, afterMillis: Long?): List<ThreadItem> {
        val kept = ArrayDeque<ThreadItem>()
        var chars = 0
        for (item in thread.asReversed()) {
            if (afterMillis != null && item.atMillis <= afterMillis) break
            val length = Conversation.text(item).length
            if (chars + length > REWRITE_CHARS) break
            chars += length
            kept.addFirst(item)
        }
        return kept.toList()
    }
}
