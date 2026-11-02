package com.metaself.app.ui.trainer

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.ConversationSummary
import com.metaself.app.domain.trainer.Proposal
import com.metaself.app.domain.trainer.ProposalKind
import com.metaself.app.domain.trainer.ProposalState
import com.metaself.app.domain.trainer.ThreadItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What Your trainer, the conversation (D145, D146), says. Every reply is advice (D4), said once at the top. */
object TalkWording {

    const val TITLE = "Your trainer"
    const val TALK = "Talk to your trainer"
    const val REPLY = "Reply ›"
    const val PLACEHOLDER = "Write to your trainer…"
    const val SEND = "Send"
    const val WRITING = "Your trainer is writing…"
    const val NOT_ANSWERED = "Not answered"
    const val TRY_AGAIN = "Try again"
    const val DELETE = "Delete"
    const val CONFIRM_DELETE = "Delete your message? The trainer's reply to it goes too."
    const val KEEP = "Keep it"
    const val AI = "Your trainer is an AI: its replies are advice, not measurements. It can propose a change to your plan or " +
        "to what it knows about you; nothing changes until you press Confirm."
    const val EMPTY = "Nothing here yet. Write about a session, how your body feels, or what you want from your training."
    const val UNREADABLE = "The conversation could not be read; Recent problems says why."
    const val TAP_TO_READ = "tap to read"
    const val DELETE_CONVERSATION = "Delete this conversation"
    const val CONFIRM_DELETE_CONVERSATION = "Delete this conversation? Your messages and the trainer's replies go. " +
        "Feedback and letters stay, and so does anything already in What I know about you."
    const val BOX_PLACEHOLDER = "Ask your coach anything…"
    const val ASK_ABOUT_THE_WEEK = "How am I doing this week?"
    const val SEE_LETTER = "See your weekly letter"
    const val SENT_TO_OPENAI = "Sent to OpenAI with your key · What's sent ›"
    const val WHATS_SENT = "What's sent"
    const val CLOSE = "Close"
    const val COUNT_SESSION = "A shorter session may count for your plan"
    const val ALL_CONVERSATIONS = "All conversations"
    const val NO_CONVERSATIONS = "Nothing here yet."
    const val EVERYTHING_ELSE = "Everything else"

    const val CONFIRM = "Confirm"
    const val NOT_NOW = "Not now"
    const val SEE_WEEKS = "See the weeks"
    const val PREPARING = "Preparing it…"
    const val NOT_PREPARED = "This was not prepared. Nothing changed."
    const val DONE = "Done."
    const val LEFT_AS_IT_IS = "Left as it is."
    const val OUT_OF_DATE = "This no longer applies, so nothing changed."
    const val CANNOT_READ = "Your coach named a change this app could not read. Nothing changed."
    const val FILL_LINE = "Your coach writes those sessions when you confirm, and they go straight into your plan. One AI request."

    /** D164: what a proposal would change, in a line. */
    fun proposalTitle(proposal: Proposal): String = when (proposal.kind) {
        ProposalKind.CHANGE_PLAN -> "Change your plan" + titled(proposal)
        ProposalKind.NEW_PLAN -> "A new plan" + titled(proposal)
        ProposalKind.STOP_PLAN -> "Stop your plan" + titled(proposal)
        ProposalKind.FILL_WEEK -> "Fill in the next week of your plan"
        ProposalKind.NEXT_BLOCK -> "Plan the next block of your plan"
        ProposalKind.ADD_FACT -> "Add to What I know about you: ${proposal.text}"
        ProposalKind.CHANGE_FACT -> "In What I know about you, change \"${proposal.fact}\" to \"${proposal.text}\""
        ProposalKind.REMOVE_FACT -> "Remove from What I know about you: ${proposal.fact}"
        ProposalKind.UNREADABLE -> CANNOT_READ
    }

    private fun titled(proposal: Proposal): String = if (proposal.title.isBlank()) "" else ": ${proposal.title}"

    /** D164: when a new plan would start, and that it replaces the running one. */
    fun startsLine(proposal: Proposal): String? = proposal.startEpochDay?.let { start ->
        val day = DAY.format(LocalDate.ofEpochDay(start))
        when {
            proposal.state == ProposalState.CONFIRMED -> "It starts on $day."
            proposal.replaces -> "It starts on $day and replaces your running plan."
            else -> "It starts on $day."
        }
    }

    /** D164: why a job did not answer; the card stays as it was. */
    fun proposalFailure(result: EstimateResult): String =
        TrainerWording.failure(result).removeSuffix(" Your answers are still here.") + " Nothing changed."

    private const val READ = "Read: "
    private const val READ_NOTHING = "the front page only"

    /** D163: the line under a reply, from what the phone stored of what it read; null on a reply from before the file. */
    fun read(opened: String?): String? = opened?.let { READ + it.ifEmpty { READ_NOTHING } }

    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    /** The day a run of the thread was written: "SUN 11 OCT" (invented). */
    fun day(epochDay: Long): String = DAY.format(LocalDate.ofEpochDay(epochDay)).uppercase(Locale.US)

    /** [atMillis]'s day in [zone]. */
    fun epochDay(atMillis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate().toEpochDay()

    /** D146: the label on a session's feedback — "AFTER YOUR RUN · 60 MIN"; "AFTER A SESSION" when the session is gone. */
    fun afterSession(kind: WorkoutKind?, minutes: Int?): String {
        if (kind == null) return "AFTER A SESSION"
        val what = when (kind) {
            WorkoutKind.RUN -> "YOUR RUN"
            WorkoutKind.WALK -> "YOUR WALK"
            WorkoutKind.CYCLE -> "YOUR RIDE"
            WorkoutKind.SWIM -> "YOUR SWIM"
            WorkoutKind.STRENGTH -> "YOUR STRENGTH SESSION"
            WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "YOUR SESSION"
        }
        return "AFTER $what" + (minutes?.let { " · $it MIN" } ?: "")
    }

    /** D146: a letter's card — "Your letter · week of 5 Oct" (invented). */
    fun letterTitle(weekMonday: Long): String = "Your letter · week of " + DAY_MONTH.format(LocalDate.ofEpochDay(weekMonday))

    /** D146: "Arrived 20:00 · tap to read" (invented). */
    fun letterLine(createdAtMillis: Long, zone: ZoneId): String =
        "Arrived " + TIME.format(Instant.ofEpochMilli(createdAtMillis).atZone(zone)) + " · " + TAP_TO_READ

    /** D159: a conversation's name — a session's or a letter's, else the first words of the owner's first message. */
    fun name(summary: ConversationSummary): String {
        val first = summary.first
        return when {
            first is ThreadItem.AfterSession -> sessionName(first.kind, first.minutes)
            first is ThreadItem.Letter -> letterTitle(first.weekMonday)
            summary.firstWords != null -> firstWords(summary.firstWords)
            else -> firstWords(summary.lastWords)
        }
    }

    /** D159: "After your run · 60 min" (invented), "After a session" when the session is gone. */
    private fun sessionName(kind: WorkoutKind?, minutes: Int?): String =
        afterSession(kind, minutes).lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }

    /** D159: at most [NAME_CHARS] of [text] on its first line, cut at a word when there is one. */
    fun firstWords(text: String): String {
        val line = text.trim().lineSequence().first().trim()
        if (line.length <= NAME_CHARS) return line
        val cut = line.substring(0, NAME_CHARS).let { if (Character.isHighSurrogate(it.last())) it.dropLast(1) else it }
        val space = cut.lastIndexOf(' ')
        return (if (space >= NAME_CHARS / 2) cut.substring(0, space) else cut).trimEnd() + "…"
    }

    /** D160: when a conversation last moved: its time today, else its day ("Sun 11 Oct", invented). */
    fun lastMoved(atMillis: Long, today: Long, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(atMillis).atZone(zone)
        return if (at.toLocalDate().toEpochDay() == today) TIME.format(at) else DAY.format(at)
    }

    private const val NAME_CHARS = 48

    /** A failed reply: the reason, and that his message is kept for Try again (D145). */
    fun failure(result: EstimateResult): String =
        TrainerWording.failure(result).removeSuffix(" Your answers are still here.") + " Your message is kept; Try again sends it."
}
