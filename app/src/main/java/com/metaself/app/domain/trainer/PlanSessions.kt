package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.MovementWeek

/**
 * D123: one card of the Session view — a planned session at its [place], the steps kept for it (D122), and, once
 * done, how ([state]) and the session that ticked it ([day], [minutes]).
 */
data class SessionCard(
    val place: PlanPlace,
    val planned: PlannedSession,
    val steps: TrainerPlan?,
    val state: TileState = TileState.OPEN,
    val day: Long? = null,
    val minutes: Int? = null,
)

/**
 * D122, D123: the Session view — this week's done sessions (folded above), this week's open ones in the plan's
 * order, next week's, and the Next session: the kept suggestion for no planned session. Pure.
 */
data class PlanSessions(
    val done: List<SessionCard>,
    val thisWeek: List<SessionCard>,
    val nextWeek: List<SessionCard>,
    val next: TrainerPlan?,
) {
    val empty: Boolean get() = done.isEmpty() && thisWeek.isEmpty() && nextWeek.isEmpty() && next == null

    companion object {
        /**
         * Cards only for a plan that runs ([PlanShown.running]); an ended, stopped or offered one has none. Next
         * week is the plan's week whose Monday is the Monday after [today]'s — week 1 of a plan not yet begun.
         */
        fun of(shown: PlanShown?, today: Long, next: TrainerPlan?): PlanSessions {
            if (shown?.running == null) return PlanSessions(emptyList(), emptyList(), emptyList(), next)
            val timeline = shown.timeline
            val nextMonday = MovementWeek.mondayOf(today) + 7
            val done = mutableListOf<SessionCard>()
            val open = mutableListOf<SessionCard>()
            val ahead = mutableListOf<SessionCard>()
            timeline.weeks.forEach { week ->
                val index = week.number - 1
                when (val place = week.place) {
                    is TimelinePlace.ThisWeek -> place.tiles.forEachIndexed { at, tile ->
                        val card = SessionCard(PlanPlace(index, at), tile.planned, shown.steps[PlanPlace(index, at)], tile.state, tile.day, tile.minutes)
                        if (tile.state == TileState.OPEN) open += card else done += card
                    }
                    is TimelinePlace.Ahead -> if (ProgrammeCalendar.monday(timeline.start, index) == nextMonday) {
                        place.sessions.forEachIndexed { at, planned -> ahead += SessionCard(PlanPlace(index, at), planned, shown.steps[PlanPlace(index, at)]) }
                    }
                    is TimelinePlace.Past -> Unit
                }
            }
            return PlanSessions(done, open, ahead, next)
        }
    }
}
