package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.MovementWeek
import kotlin.math.abs
import kotlin.math.round

/** D126: one goal the trainer suggests — a title, the goal, sessions a week and one sentence of why. Advice (D4). */
data class GoalSuggestion(val title: String, val goal: PlanGoal, val perWeek: Int, val why: String)

/** D126: the running plan's goal and outline, sent with a suggest-goals ask (D125); either may be null on an older plan. */
data class RunningGoal(val goal: PlanGoal?, val outline: Outline?)

/**
 * D126: what Plan ahead's form can hold, as a check on a goal the trainer gives — a dated goal's date is moved to the
 * Sunday of its week and must be [WEEKS_AHEAD] weeks after this one; km has at most one decimal (the form's typing);
 * sessions a week are [ProgrammeAsk.PER_WEEK]. The minutes' and km's own bounds are the goal's.
 */
object GoalBounds {

    /** D119, D127: a dated goal is 6 to 30 weeks ahead. */
    val WEEKS_AHEAD = 6..30

    /** How many suggestions are asked for, and the most kept. */
    const val SUGGESTIONS = 3

    /** The Sunday of [day]'s week. */
    fun sundayOf(day: Long): Long = MovementWeek.mondayOf(day) + 6

    /** Whole weeks from [today]'s week to [day]'s. */
    fun weeksAhead(today: Long, day: Long): Long = (MovementWeek.mondayOf(day) - MovementWeek.mondayOf(today)) / 7

    /** The Sunday of the week [weeks] after [today]'s. */
    fun byDay(today: Long, weeks: Int): Long = MovementWeek.mondayOf(today) + 7L * weeks + 6

    /** [goal] as the form would hold it — a dated goal's date on its Sunday — or null when the form could not. */
    fun checked(goal: PlanGoal, today: Long): PlanGoal? = when (goal) {
        PlanGoal.Steady -> goal
        is PlanGoal.Distance -> goal.copy(byEpochDay = sundayOf(goal.byEpochDay)).takeIf { oneDecimal(goal.km) && inWindow(goal, today) }
        is PlanGoal.WeeklyMinutes -> goal.copy(byEpochDay = sundayOf(goal.byEpochDay)).takeIf { inWindow(goal, today) }
    }

    /** [suggestion] with its goal [checked], or null when any part does not fit. */
    fun checked(suggestion: GoalSuggestion, today: Long): GoalSuggestion? {
        if (suggestion.title.isBlank() || suggestion.why.isBlank() || suggestion.perWeek !in ProgrammeAsk.PER_WEEK) return null
        return checked(suggestion.goal, today)?.let { suggestion.copy(goal = it) }
    }

    private fun inWindow(goal: PlanGoal.Dated, today: Long): Boolean = weeksAhead(today, goal.byEpochDay) in WEEKS_AHEAD.first.toLong()..WEEKS_AHEAD.last.toLong()

    private fun oneDecimal(km: Double): Boolean = abs(km * 10 - round(km * 10)) < 1e-6
}
