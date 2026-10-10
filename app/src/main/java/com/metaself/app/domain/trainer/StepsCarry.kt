package com.metaself.app.domain.trainer

/**
 * Release 1's limit 2 (D122): kept steps belong to a planned session, which a new version of the plan may move —
 * Adjust puts this week's done sessions first (D97). Every session of an old week is matched to one of the new
 * version's same week, in order: identical (kind, minutes and effort), then the same kind and effort, then the same
 * kind; each new session is matched once. Pure.
 */
object StepsCarry {

    /** Where each of [places] (in [old]) is in [new]; null when its session has no match there. */
    fun moves(old: WeeksPlan, new: WeeksPlan, places: Collection<PlanPlace>): Map<PlanPlace, PlanPlace?> {
        val weeks = places.map { it.week }.distinct().associateWith { week ->
            matches(old.weeks.getOrNull(week)?.sessions.orEmpty(), new.weeks.getOrNull(week)?.sessions.orEmpty())
        }
        return places.associateWith { place -> weeks.getValue(place.week)[place.session]?.let { PlanPlace(place.week, it) } }
    }

    private val RULES: List<(PlannedSession, PlannedSession) -> Boolean> = listOf(
        { a, b -> a.kind == b.kind && a.minutes == b.minutes && a.effort == b.effort },
        { a, b -> a.kind == b.kind && a.effort == b.effort },
        { a, b -> a.kind == b.kind },
    )

    /** Old index to new index, for the sessions that have a match. */
    private fun matches(old: List<PlannedSession>, new: List<PlannedSession>): Map<Int, Int> {
        val matched = mutableMapOf<Int, Int>()
        val taken = mutableSetOf<Int>()
        RULES.forEach { rule ->
            old.forEachIndexed { at, session ->
                if (at in matched) return@forEachIndexed
                val to = new.indices.firstOrNull { it !in taken && rule(session, new[it]) } ?: return@forEachIndexed
                matched[at] = to
                taken += to
            }
        }
        return matched
    }
}
