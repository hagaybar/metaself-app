package com.metaself.app.ui.trainer

import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PracticeLedger
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.WeekLedger
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** What the top of the Trainer page says (D114, D115). Every figure is the week's ledger's (D110). */
object TrainerWeekWording {

    private const val SEP = " · "
    private val DAY_MONTH = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val DAY_ONLY = DateTimeFormatter.ofPattern("EEE d", Locale.US)
    private val WRITTEN = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.US)
    private val NUMBERS = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

    /** The eighth column's heading, there only while planned sessions are open (D114). */
    const val TO_DO = "To do"

    /** The strip's description for a screen reader, and what its tap does. */
    const val STRIP = "This week's sessions, day by day"
    const val OPEN_MOVEMENT = "open Movement"

    const val LETTERS = "Weekly letters"
    const val EARLIER = "Earlier sessions"
    const val ABOUT_ME = "About me"
    const val NO_EARLIER = "No reviewed sessions yet."

    /** "THIS WEEK · MON 5 – SUN 11 OCT"; across two months, "THIS WEEK · MON 28 SEP – SUN 4 OCT" (invented). */
    fun header(monday: Long): String {
        val from = LocalDate.ofEpochDay(monday)
        val to = from.plusDays(6)
        val first = if (from.month == to.month) from.format(DAY_ONLY) else from.format(DAY_MONTH)
        return "THIS WEEK$SEP$first – ${to.format(DAY_MONTH)}".uppercase(Locale.US)
    }

    /** D115: one sentence, two clauses at most, counted from the week's ledger; never the AI's. */
    fun verdict(ledger: WeekLedger): String = listOfNotNull(minutesClause(ledger), planClause(ledger.practice)).joinToString(" ")

    private fun minutesClause(ledger: WeekLedger): String = when {
        ledger.sessions == 0 -> "Nothing recorded yet this week."
        ledger.minutes == 1 -> "1 minute moving this week."
        else -> "${ledger.minutes} minutes moving this week."
    }

    /** No plan this week, or a week of it holding no session: no clause. */
    private fun planClause(practice: PracticeLedger?): String? {
        if (practice == null || practice.sessions.isEmpty()) return null
        val open = practice.sessions.filter { it.outcome == PlannedOutcome.NOT_DONE }
        return when (open.size) {
            0 -> "Every planned session done."
            1 -> "One planned ${noun(open.single().planned.kind)} still to do."
            else -> "${number(open.size)} planned sessions still to do."
        }
    }

    /** Words to ten, digits above. */
    fun number(n: Int): String = NUMBERS.getOrNull(n - 1) ?: n.toString()

    /** D115's kinds: walk, run, swim, ride, session. */
    fun noun(kind: WorkoutKind): String = when (kind) {
        WorkoutKind.WALK -> "walk"
        WorkoutKind.RUN -> "run"
        WorkoutKind.SWIM -> "swim"
        WorkoutKind.CYCLE -> "ride"
        WorkoutKind.STRENGTH, WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "session"
    }

    /** D114: the main button while a planned session is open: "Plan the open walk". */
    fun planOpen(kind: WorkoutKind): String = "Plan the open ${noun(kind)}"

    /** D114: one of the three boxes. [under] tints it amber: sleep under its usual. */
    data class Box(val label: String, val figure: String, val line: String, val extra: String? = null, val under: Boolean = false)

    /** Steps, sleep and food, in that order, each absent when its figure is (a null is never zero, D110). */
    fun boxes(ledger: WeekLedger): List<Box> = listOfNotNull(
        ledger.beyond.stepsADay?.let { Box("STEPS", thousands(it), "a day") },
        ledger.recovery.sleepMinutes?.let { minutes ->
            val difference = ledger.recovery.usualSleepMinutes?.let { sleepDifference(minutes, it) }
            val line = when {
                difference == null -> "a night"
                difference == 0 -> "as usual"
                difference < 0 -> "${-difference} min under usual"
                else -> "$difference min over usual"
            }
            Box("SLEEP", sleep(minutes), line, under = difference != null && difference < 0)
        },
        ledger.food.kcalADay?.let { kcal ->
            val days = ledger.food.daysLogged
            Box(
                "FOOD",
                thousands(kcal),
                "kcal" + (ledger.food.targetKcal?.let { "${SEP}target ${thousands(it)}" } ?: ""),
                extra = if (days < 7) "over $days logged day" + (if (days == 1) "" else "s") else null,
            )
        },
    )

    /** The difference from the usual, rounded to ten minutes, half away from zero. */
    fun sleepDifference(minutes: Int, usual: Int): Int {
        val difference = minutes - usual
        val rounded = (abs(difference) + 5) / 10 * 10
        return if (difference < 0) -rounded else rounded
    }

    /** "6 h 50", "7 h", "7 h 05", "45 min" (invented). */
    fun sleep(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h " + "%02d".format(Locale.US, minutes % 60)
    }

    /** With no plan the strip is all one green, and its one entry names it. */
    fun legend(withPlan: Boolean): List<String> =
        if (withPlan) listOf("Planned, done", "Planned, open", "Beyond the plan") else listOf("Sessions")

    /**
     * "Your plan · week 2 of 2"; before week 1, "Your plan · starts Mon 7 Sep"; ended, "Your plan · ended Sun 20 Sep"
     * (invented) — the plan screen draws an ended plan too (D117). "Your plan" without one: the plan screen is
     * where Plan ahead and the Next session are (D122, D124).
     */
    fun planRow(card: PlanCard): String = when (card) {
        is PlanCard.Running -> "Your plan$SEP" + if (card.weekIndex >= 0) {
            "week ${card.weekIndex + 1} of ${card.programme.ask.weeks}"
        } else {
            "starts " + LocalDate.ofEpochDay(card.programme.startEpochDay ?: 0).format(DAY_MONTH)
        }
        is PlanCard.Ended -> {
            val programme = card.programme
            "Your plan${SEP}ended " + LocalDate.ofEpochDay(ProgrammeCalendar.lastDay(programme.startEpochDay ?: 0, programme.ask.weeks)).format(DAY_MONTH)
        }
        PlanCard.None -> "Your plan"
    }

    /** D116: "Written Sat 09:12", or "From Sunday's letter" — the weekday it was written (invented). */
    fun noteWhen(note: WeekNote, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(note.writtenAtMillis).atZone(zone)
        return when (note.source) {
            WeekNoteSource.TRAINER -> "Written " + at.format(WRITTEN)
            WeekNoteSource.LETTER -> "From " + at.format(WEEKDAY) + "'s letter"
        }
    }

    /** "M", "T", "W" … — a column's day. */
    fun dayLetter(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).dayOfWeek.name.take(1)

    /** An open planned session's label in "To do": "40′". */
    fun openMinutes(minutes: Int): String = "$minutes′"

    private fun thousands(value: Int): String = "%,d".format(Locale.US, value)
}
