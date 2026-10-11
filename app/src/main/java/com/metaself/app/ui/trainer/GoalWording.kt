package com.metaself.app.ui.trainer

import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.trainer.Block
import com.metaself.app.domain.trainer.GoalSport
import com.metaself.app.domain.trainer.MonthWeek
import com.metaself.app.domain.trainer.Phase
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.WeekTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the goal, the outline and the Month view say (D119, D120, D123), and Plan ahead's form. Every count is the phone's. */
object GoalWording {

    private const val SEP = " · "
    private val DAY_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

    const val STEADY = "Keep a steady rhythm"
    const val DISTANCE = "A distance by a date"
    const val MINUTES = "More time each week"
    const val HERE = "you are here"

    /** Plan ahead's form (D124). */
    const val ROW_GOAL = "GOAL"
    const val ROW_SPORT = "WHAT"
    const val ROW_KM = "DISTANCE (KM)"
    const val ROW_BY = "BY"
    const val ROW_MINUTES = "MINUTES A WEEK"
    const val FEWER = "10 min less"
    const val MORE = "10 min more"
    const val EARLIER = "A week earlier"
    const val LATER = "A week later"

    fun sport(sport: GoalSport): String = when (sport) {
        GoalSport.RUN -> "Run"
        GoalSport.WALK -> "Walk"
        GoalSport.SWIM -> "Swim"
        GoalSport.RIDE -> "Ride"
    }

    fun phase(phase: Phase): String = when (phase) {
        Phase.BASE -> "Base"
        Phase.BUILD -> "Build"
        Phase.PEAK -> "Peak"
        Phase.TAPER -> "Taper"
        Phase.STEADY -> "Steady"
    }

    /**
     * D119, D127: "15 km run · by Sun 13 Dec · 14 weeks to go", "150 min a week · by Sun 13 Dec · 14 weeks to go", or
     * "Keep a steady rhythm" (invented); null without a goal.
     */
    fun goalLine(goal: PlanGoal?, today: Long): String? = when (goal) {
        null -> null
        PlanGoal.Steady -> STEADY
        is PlanGoal.Distance -> "${km(goal.km)} km ${sport(goal.sport).lowercase(Locale.US)}" + dated(goal, today)
        is PlanGoal.WeeklyMinutes -> minutesLine(goal.minutes) + dated(goal, today)
    }

    /** " · by Sun 13 Dec · 14 weeks to go", "· 1 week to go", "· goal week", "· goal date passed" (invented). */
    private fun dated(goal: PlanGoal.Dated, today: Long): String = "${SEP}by ${dayDate(goal.byEpochDay)}$SEP" +
        when (val weeks = weeksBetween(today, goal.byEpochDay)) {
            0L -> "goal week"
            1L -> "1 week to go"
            in 2..Long.MAX_VALUE -> "$weeks weeks to go"
            else -> "goal date passed"
        }

    /** D127: "150 min a week" (invented). */
    fun minutesLine(minutes: Int): String = "$minutes min a week"

    /** "15" for 15.0, "42.5" for 42.5. */
    fun km(km: Double): String = if (km % 1.0 == 0.0) km.toLong().toString() else km.toString()

    /** D123: "about 150 min · longest 60′ · easier week" (invented). */
    fun targetLine(target: WeekTarget): String =
        "about ${target.minutes} min${SEP}longest ${target.longest}′" + if (target.easier) "${SEP}easier week" else ""

    /** D123: a block's row title: "Base · Easy rhythm" (invented). */
    fun blockTitle(block: Block): String = blockTitle(block.phase, block.focus)

    fun blockTitle(phase: Phase, focus: String): String = phase(phase) + if (focus.isBlank()) "" else SEP + focus.trim()

    /**
     * D123: one bar in words, under its row — so no figure is only a height or a colour: "Wk 3 · 150 min, 120 done",
     * "Wk 4 · 100 min, easier", "Wk 6 · 150 min, you are here" (invented).
     */
    fun barWords(week: MonthWeek): String {
        val notes = listOfNotNull(
            week.done?.let { "$it done" },
            "easier".takeIf { week.target.easier },
            HERE.takeIf { week.here },
        )
        return "Wk ${week.number}$SEP${week.target.minutes} min" + if (notes.isEmpty()) "" else ", " + notes.joinToString(", ")
    }

    /** The form's date: "Sun 13 Dec · 14 weeks" (invented). */
    fun byLine(byEpochDay: Long, today: Long): String = "${dayDate(byEpochDay)}$SEP${weeksBetween(today, byEpochDay)} weeks"

    /** The Sunday of the week [weeks] after [today]'s. */
    fun byDay(today: Long, weeks: Int): Long = MovementWeek.mondayOf(today) + 7L * weeks + 6

    private fun weeksBetween(today: Long, day: Long): Long = (MovementWeek.mondayOf(day) - MovementWeek.mondayOf(today)) / 7

    private fun dayDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DAY_DATE)
}
