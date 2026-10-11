package com.metaself.app.domain.trainer

import com.metaself.app.domain.day.TEST_EPOCH_DAY

/**
 * The goal fixture (CLAUDE.md): **15 km run by Sunday 13 December 2026** ([TEST_EPOCH_DAY] + 101), 14 weeks after
 * [TEST_EPOCH_DAY]'s week — a 15-week plan from Monday 31 August in blocks of 4 (base), 4 (build), 5 (peak) and 2
 * (taper) weeks. Every figure is invented and chosen round.
 */
val GOAL_15K = PlanGoal.Distance(GoalSport.RUN, 15.0, TEST_EPOCH_DAY + 101L)

/** D127: the time-goal fixture — **150 min a week by the same Sunday**, 13 December 2026. Invented and round. */
val GOAL_150_MIN = PlanGoal.WeeklyMinutes(150, TEST_EPOCH_DAY + 101L)

/** One week's target; every fourth week of a block easier. */
fun target(minutes: Int, longest: Int, easier: Boolean = false) = WeekTarget(minutes, longest, easier)

val OUTLINE_15 = Outline(
    listOf(
        Block(Phase.BASE, "Easy rhythm", listOf(target(120, 40), target(130, 40), target(140, 50), target(100, 30, easier = true))),
        Block(Phase.BUILD, "Longer runs", listOf(target(140, 50), target(150, 60), target(160, 60), target(120, 40, easier = true))),
        Block(Phase.PEAK, "Race distance", List(4) { target(170, 70) } + target(130, 50, easier = true)),
        Block(Phase.TAPER, "Fresh legs", listOf(target(120, 50), target(90, 40, easier = true))),
    ),
)

/** A steady rhythm's one block of four weeks. */
val STEADY_OUTLINE = Outline(
    listOf(Block(Phase.STEADY, "Keep it going", listOf(target(120, 40), target(130, 40), target(140, 50), target(100, 30, easier = true)))),
)
