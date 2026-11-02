package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout

/** One of the coach screen's suggestions (D158), worked out on the phone. */
sealed interface CoachSuggestion {
    /** A session waiting for words (D85): opens its review. */
    data class Review(val session: Workout) : CoachSuggestion

    /** A shorter session waiting for "count it for this?" (D105): opens Everything else, where the question is. */
    data object CountSession : CoachSuggestion

    /** The next thing to do (D114, D124). */
    data class Next(val action: MainAction) : CoachSuggestion

    /** Fills the box and sends nothing: it shows what the box is for. */
    data object AskAboutTheWeek : CoachSuggestion
}

/** D158. Pure. */
object CoachSuggestions {

    const val MOST = 3

    /** In D158's order, at most [MOST]. */
    fun of(home: TrainerHome): List<CoachSuggestion> {
        val running = home.plan as? PlanCard.Running
        val toCount = running?.progress?.weeks?.getOrNull(running.weekIndex)?.ticks.orEmpty().any { it.candidate != null }
        return buildList {
            home.waiting?.let { add(CoachSuggestion.Review(it)) }
            if (toCount) add(CoachSuggestion.CountSession)
            add(CoachSuggestion.Next(home.main))
            add(CoachSuggestion.AskAboutTheWeek)
        }.take(MOST)
    }
}
