package com.metaself.app.ui.trainer

import com.metaself.app.domain.trainer.PlanStanding
import com.metaself.app.domain.trainer.PlanTile
import com.metaself.app.domain.trainer.PlanTimeline
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.TileState
import com.metaself.app.domain.trainer.TimelinePlace
import com.metaself.app.domain.trainer.TimelineWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the plan screen says (D117, D118). Every count is the phone's (D95, D110). */
object PlanTimelineWording {

    private const val SEP = " · "
    private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val DAY_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US)

    /** After this week's heading, in the lighter ink. */
    const val THIS_WEEK = " — this week"

    const val READ_FULL = "Read the trainer's full evaluation"
    const val HIDE_FULL = "Hide the trainer's full evaluation"

    /** "4 weeks · 28 Sep – 25 Oct · you're in week 2"; "starts Mon 12 Oct", "ended Sun 25 Oct", "stopped Wed 14 Oct" (invented). */
    fun subtitle(timeline: PlanTimeline): String {
        val where = when (val standing = timeline.standing) {
            is PlanStanding.Before -> "starts " + dayDate(standing.start)
            is PlanStanding.InWeek -> "you're in week ${standing.number}"
            is PlanStanding.Ended -> "ended " + dayDate(standing.lastDay)
            is PlanStanding.Stopped -> "stopped " + dayDate(standing.day)
        }
        return "${timeline.weeks.size} weeks$SEP${date(timeline.start)} – ${date(timeline.lastDay)}$SEP$where"
    }

    /** "Week 2 · Add a run" (invented); a week with no focus is "Week 2". */
    fun weekHeading(week: TimelineWeek): String = ProgrammeWording.weekTitle(week.number, week.focus)

    /** "2 of 2 planned", and "· plus 4 of your own" when the week held sessions beyond the plan (invented). */
    fun pastLine(past: TimelinePlace.Past): String =
        "${past.done} of ${past.planned} planned" + if (past.own > 0) "${SEP}plus ${past.own} of your own" else ""

    /** "Easy 30′ · Steady 45′ · Push 20′" (invented). */
    fun aheadLine(sessions: List<PlannedSession>): String = sessions.joinToString(SEP) { tileTitle(it) }

    /** "Easy 30′". */
    fun tileTitle(session: PlannedSession): String = "${ProgrammeWording.effort(session.effort)} ${session.minutes}′"

    /** D122: an open session whose steps are kept. */
    const val STEPS_READY = "Steps ready"

    /** "Done Mon", "Done Mon · 25 min", "Open", or "Steps ready" (invented). */
    fun tileLine(tile: PlanTile): String {
        val day = tile.day?.let { LocalDate.ofEpochDay(it).format(WEEKDAY) }
        return when (tile.state) {
            TileState.DONE -> "Done $day"
            TileState.DONE_SHORT -> "Done $day$SEP${tile.minutes} min"
            TileState.OPEN -> if (tile.stepsReady) STEPS_READY else "Open"
        }
    }

    /** "WHERE YOU STOOD · SUN 27 SEP" — the day the evaluation was made (invented). */
    fun stood(epochDay: Long): String = "WHERE YOU STOOD$SEP${dayDate(epochDay)}".uppercase(Locale.US)

    private fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DATE)

    private fun dayDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DAY_DATE)
}
