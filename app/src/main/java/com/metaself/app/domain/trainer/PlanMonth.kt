package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind

/**
 * D123: one week's bar on the Month view — its [number] from 1, its [target], the minutes done when the week is past
 * ([done], every visible, counted session of the week: the target is the week's total), and whether today is in it.
 */
data class MonthWeek(val number: Int, val target: WeekTarget, val done: Int?, val here: Boolean)

/** D123: one block's row. */
data class MonthBlock(val phase: Phase, val focus: String, val weeks: List<MonthWeek>)

/** D123: the road to the goal — the goal line, then each block as a row of its weeks. Pure. */
data class PlanMonth(val goal: PlanGoal?, val blocks: List<MonthBlock>) {

    /** The tallest bar's minutes — a target or a past week's done — which every bar is drawn against. */
    val scale: Int get() = blocks.flatMap { it.weeks }.maxOfOrNull { maxOf(it.target.minutes, it.done ?: 0) } ?: 0

    companion object {
        /** [programme]'s outline from [start]; null when it has none (a plan made before outlines). */
        fun of(programme: Programme, start: Long, today: Long, workouts: List<Workout>): PlanMonth? {
            val outline = programme.plan.outline ?: return null
            val counted = workouts.filter { !it.hidden && it.counted && it.kind != WorkoutKind.UNRECOGNISED }
            var index = 0
            val blocks = outline.blocks.map { block ->
                MonthBlock(
                    block.phase,
                    block.focus,
                    block.weeks.map { target ->
                        val monday = ProgrammeCalendar.monday(start, index)
                        val week = monday..monday + 6
                        val done = if (monday + 6 < today) counted.filter { it.epochDay in week }.sumOf { it.durationMinutes } else null
                        MonthWeek(++index, target, done, today in week)
                    },
                )
            }
            return PlanMonth(programme.ask.goal, blocks)
        }
    }
}
