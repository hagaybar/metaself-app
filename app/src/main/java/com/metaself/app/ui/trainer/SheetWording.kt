package com.metaself.app.ui.trainer

import com.metaself.app.data.trainer.TrainerSheets
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.Sheets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the page What I know about you (D149, D150) says, in the trainer's voice. Each fact says where it came from —
 * "you said" or "I noticed" (D4).
 */
object SheetWording {

    const val TITLE = "What I know about you"

    /** The Trainer page's row, under its name. */
    const val ROW_LINE = "Kept from our talks · updated Sundays"

    const val HEADER = "I rewrite this every Sunday from the week's conversation. I read it every time I write to you — " +
        "notes, plans, letters and replies. Change or remove anything that's wrong."

    /** What the weekly rewrite sends (D150), said on the page. */
    const val PRIVACY = "Each Sunday, after the weekly letter, the rewrite sends to OpenAI, with your key: this page as it " +
        "stands, your note about yourself, and our conversation since the last rewrite, word for word, my feedback and " +
        "letters in it included. Nothing else from your record. One AI request; none in a week you wrote nothing."

    /** Before the first rewrite: what will appear, and when. */
    const val EMPTY = "Nothing here yet. Every Sunday, after your weekly letter, I read what we said that week and write " +
        "down what I learn about you: aches and injuries, how you like to train, your goals in your own words, and what in " +
        "your life affects your training. The first facts appear the Sunday after you first talk with me. The rewrite " +
        "runs with the weekly letter, so it needs that switched on in Settings."

    /** A sheet with every fact removed or dropped. */
    const val NONE_NOW = "Nothing is kept right now. Next Sunday's rewrite adds what you tell me."

    const val NO_LONGER_TRUE = "NO LONGER TRUE"
    const val EDIT = "Edit"
    const val REMOVE = "Remove"
    const val SAVE = "Save"
    const val CANCEL = "Cancel"
    const val KEEP = "Keep it"
    const val EDIT_TITLE = "Change this fact"
    const val CONFIRM_REMOVE = "Remove this? I stop reading it, and it shows under No longer true for 28 days."
    const val GO_BACK = "Go back to the previous version"
    const val CONFIRM_GO_BACK = "Go back to the sheet as it was before the last rewrite? That rewrite's changes are undone, " +
        "and next Sunday I read its conversation again."
    const val GO_BACK_YES = "Go back"
    const val FULL_CONVERSATION = "Full conversation ›"
    const val UNREADABLE = "This page could not be read; Recent problems says why."

    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    /** The group's eyebrow. */
    fun group(group: FactGroup): String = when (group) {
        FactGroup.BODY -> "BODY"
        FactGroup.TRAINING -> "HOW YOU LIKE TO TRAIN"
        FactGroup.GOALS -> "GOALS IN YOUR WORDS"
        FactGroup.LIFE -> "LIFE"
    }

    /** "Since Mon 12 Oct · you said" (invented). */
    fun factLine(fact: SheetFact): String = "Since " + DAY.format(LocalDate.ofEpochDay(fact.sinceEpochDay)) + " · " + source(fact.source)

    private fun source(source: FactSource): String = when (source) {
        FactSource.SAID -> "you said"
        FactSource.NOTICED -> "I noticed"
    }

    /** "6 Oct – 11 Oct · dropped Sun 11 Oct", or "· you removed it Tue 13 Oct" (invented). */
    fun droppedLine(fact: SheetFact): String {
        val dropped = LocalDate.ofEpochDay(fact.droppedEpochDay ?: fact.sinceEpochDay)
        val span = DAY_MONTH.format(LocalDate.ofEpochDay(fact.sinceEpochDay)) + " – " + DAY_MONTH.format(dropped)
        return span + " · " + (if (fact.removedByOwner) "you removed it " else "dropped ") + DAY.format(dropped)
    }

    /** "Last rewritten Sun 11 Oct, 20:00" (invented). */
    fun lastRewritten(atMillis: Long, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(atMillis).atZone(zone)
        return "Last rewritten " + DAY.format(at) + ", " + TIME.format(at)
    }

    /** The edit's count: "120 / 300". */
    fun count(text: String): String = "${text.trim().length} / ${Sheets.MAX_FACT}"

    /** Why an edit was not saved; null when it was. */
    fun refused(edit: TrainerSheets.Edit): String? = when (edit) {
        TrainerSheets.Edit.DONE -> null
        TrainerSheets.Edit.EMPTY -> "A fact needs words. To take it away, use Remove."
        TrainerSheets.Edit.TOO_LONG -> "A fact is at most ${Sheets.MAX_FACT} characters."
        TrainerSheets.Edit.SHEET_FULL ->
            "The sheet holds at most ${String.format(Locale.US, "%,d", Sheets.MAX_CHARS)} characters; shorten this or remove another fact."
        TrainerSheets.Edit.GONE -> "That fact has changed since this page opened; nothing was saved."
    }
}
