package com.metaself.app.ui.trainer

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.WorkoutKind
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
    const val AI = "Your trainer is an AI: its replies are advice, not measurements. It cannot change your plan from here; " +
        "it tells you what to change."
    const val EMPTY = "Nothing here yet. Write about a session, how your body feels, or what you want from your training."
    const val UNREADABLE = "The conversation could not be read; Recent problems says why."
    const val TAP_TO_READ = "tap to read"

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

    /** A failed reply: the reason, and that his message is kept for Try again (D145). */
    fun failure(result: EstimateResult): String =
        TrainerWording.failure(result).removeSuffix(" Your answers are still here.") + " Your message is kept; Try again sends it."
}
