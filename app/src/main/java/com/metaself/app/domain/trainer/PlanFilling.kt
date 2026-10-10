package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.MovementWeek

/** D121: what a running plan with an outline offers to fill in. */
sealed interface PlanOffer {
    /** This week has begun with no sessions: "Plan this week". [week] from 0. */
    data class PlanThisWeek(val week: Int) : PlanOffer

    /** From Friday, next week has none: "Fill in next week". [week] from 0. */
    data class FillNextWeek(val week: Int) : PlanOffer

    /** A steady plan's last week: "Plan the next block". */
    data object NextBlock : PlanOffer
}

/**
 * D121: weeks are filled in as they come — only when tapped, never in the background (D84). Only a running plan with
 * an outline is filled in, and not one finishing its week before a plan that replaces it. Pure.
 */
object PlanFilling {

    /** Days after Monday: Friday is 4, Sunday 6. */
    private const val FRIDAY = 4
    private const val SUNDAY = 6

    fun offer(card: PlanCard.Running, today: Long): PlanOffer? {
        if (card.replacedFrom != null) return null
        val programme = card.programme
        if (programme.plan.outline == null) return null
        val weeks = programme.plan.weeks
        val index = card.weekIndex
        if (index in weeks.indices && weeks[index].sessions.isEmpty()) return PlanOffer.PlanThisWeek(index)
        val next = index + 1
        if (next in weeks.indices && weeks[next].sessions.isEmpty() && today - MovementWeek.mondayOf(today) >= FRIDAY) {
            return PlanOffer.FillNextWeek(next)
        }
        if (programme.ask.goal == PlanGoal.Steady && index == weeks.lastIndex) return PlanOffer.NextBlock
        return null
    }

    /** D121: the Trainer page's main button offers next week only on Sunday. */
    fun sunday(today: Long): Boolean = today - MovementWeek.mondayOf(today) == SUNDAY.toLong()
}
