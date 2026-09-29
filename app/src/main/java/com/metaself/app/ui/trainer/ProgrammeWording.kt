package com.metaself.app.ui.trainer

import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanProgress
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeCalendar
import com.metaself.app.domain.trainer.Tick
import com.metaself.app.domain.trainer.WeekProgress
import com.metaself.app.ui.movement.MovementWeekWording
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the weekly plan's screens say (D94–D97). Every count here is the phone's (D95). */
object ProgrammeWording {

    private const val SEP = " · "
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US)

    fun effort(effort: PlannedEffort): String = when (effort) {
        PlannedEffort.EASY -> "Easy"
        PlannedEffort.STEADY -> "Steady"
        PlannedEffort.PUSH -> "Push"
    }

    private fun noun(kind: WorkoutKind): String = when (kind) {
        WorkoutKind.WALK -> "walk"
        WorkoutKind.RUN -> "run"
        WorkoutKind.CYCLE -> "ride"
        WorkoutKind.SWIM -> "swim"
        WorkoutKind.STRENGTH -> "strength session"
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "session"
    }

    /** "Steady walk, 40 min" (invented). */
    fun plannedTitle(session: PlannedSession): String = "${effort(session.effort)} ${noun(session.kind)}, ${session.minutes} min"

    /** D96: "Next in your plan: steady walk, 40 min" (invented). */
    fun nextInPlan(tick: PlannedTick): String =
        "Next in your plan: " + plannedTitle(tick.session).replaceFirstChar { it.lowercase(Locale.US) }

    /** "YOUR 4-WEEK PLAN · WEEK 2 OF 4", or before week 1, "… · STARTS MON 7 SEP" (invented). */
    fun cardHeading(weeks: Int, weekIndex: Int, start: Long): String =
        "YOUR $weeks-WEEK PLAN$SEP" + if (weekIndex >= 0) {
            "WEEK ${weekIndex + 1} OF $weeks"
        } else {
            "STARTS " + date(start).uppercase(Locale.US)
        }

    fun weekDates(monday: Long): String = "This week, ${date(monday)} – ${date(monday + 6)}"

    fun span(start: Long, weeks: Int): String = "Starts ${date(start)}, ends ${date(ProgrammeCalendar.lastDay(start, weeks))}"

    fun planHeading(ask: ProgrammeAsk): String = "THE PLAN$SEP${ask.weeks} WEEKS$SEP${ask.perWeek} SESSIONS A WEEK"

    fun weekTitle(number: Int, focus: String): String = if (focus.isBlank()) "Week $number" else "Week $number$SEP${focus.trim()}"

    fun tickTitle(tick: Tick): String = (if (tick.by != null) "Done: " else "To do: ") + plannedTitle(tick.planned)

    /** Design question 8: "Done Mon · Walking, 32 min" (invented). */
    fun tickLine(workout: Workout): String =
        "Done ${LocalDate.ofEpochDay(workout.epochDay).format(WEEKDAY)}$SEP${MovementWeekWording.name(workout)}, ${workout.durationMinutes} min"

    fun pastWeek(week: WeekProgress): String = "Week ${week.index + 1}: ${week.done} of ${week.planned} done"

    fun thisWeekSoFar(week: WeekProgress): String = "Week ${week.index + 1} (this week): ${week.done} of ${week.planned} so far"

    fun endedHeading(weeks: Int): String = "YOUR $weeks-WEEK PLAN HAS ENDED"

    fun endedLine(progress: PlanProgress): String =
        "${progress.done} of ${progress.planned} planned sessions done$SEP" +
            "weeks " + progress.weeks.joinToString(", ") { "${it.done} of ${it.planned}" }

    /** Weeks [from] to [to], counted from 1. */
    fun rewritten(from: Int, to: Int): String = when (to - from) {
        0 -> "Week $from: to be rewritten"
        1 -> "Weeks $from and $to: to be rewritten"
        else -> "Weeks $from to $to: to be rewritten"
    }

    fun adjustRule(start: Long, weeks: Int): String =
        "The trainer rewrites this week's remaining sessions and the weeks after. Weeks already over stay as they were. " +
            "The end date stays ${date(ProgrammeCalendar.lastDay(start, weeks))}."

    private fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DAY)
}
