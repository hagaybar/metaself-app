package com.metaself.app.ui.screen.day

import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.movement.MovementToday
import com.metaself.app.domain.streak.Consistency
import com.metaself.app.domain.streak.ConsistencyFigure
import com.metaself.app.domain.streak.Streak
import com.metaself.app.domain.window.DayVerdict
import com.metaself.app.domain.day.Remaining
import com.metaself.app.domain.target.DailyTarget
import com.metaself.app.ui.ActionRefused

/** What the Today screen is showing. */
sealed interface DayUiState {

    /** Neither the profile nor the day has been read yet. */
    data object Loading : DayUiState

    /**
     * There is no profile, so there is no target, so there is nothing to be over or under.
     *
     * Reachable in practice only if the store is cleared behind the app's back; the root sends the
     * owner to setup before this screen exists. It is here because the alternative is a target of
     * zero, which would tell him he is 600 calories over after breakfast.
     */
    data object NeedsSetup : DayUiState

    data class Ready(
        val epochDay: Long,
        /**
         * Stored rather than worked out by the screen: the screen would have to read a clock to
         * decide, and reading a clock is what this project pushes to the edge.
         */
        val isToday: Boolean,
        val target: DailyTarget,
        val meals: List<Meal>,
        /**
         * Which meals he built are open, showing what is under them.
         *
         * Held here rather than inside the row, for the reason this project already applies to the
         * list of things eaten before: state a screen keeps to itself is state no test can put into
         * a known position, and a row that opens is exactly the thing worth a test.
         */
        val openMeals: Set<Long> = emptySet(),
        val remaining: Remaining,
        /** What the last weekly revision changed, if the owner has not dismissed it yet. */
        val targetChangeNotice: String? = null,
        /**
         * What the last logging action changed about a food's own figures (D45, issue #13).
         *
         * Held in memory and **outliving a day change**: a food is not a property of a day, so
         * swiping to yesterday must not take the sentence away before it has been read. Replaced
         * wholesale by the next logging action, including replaced by nothing — and by nothing
         * else: correcting a row without renaming it teaches no food, so it leaves this standing
         * rather than wiping it unread.
         */
        val foodRetaughtNotice: String? = null,
        /** What was logged a moment ago, so that saving is never silent. */
        val justLogged: String? = null,
        /**
         * The goal weight reached, on the day it was reached, and on no other day (D21).
         *
         * On the day screen rather than the weight screen because arriving switches the goal to
         * holding, which moves the daily calorie target by the whole size of the deficit — and this
         * is the screen where that number lives.
         */
        val goalReached: String? = null,
        /** Anything reached on the way to the goal today, said once and never again (D22). */
        val milestoneReached: String? = null,
        /**
         * The day's walking, shown whether or not it earned anything (D12a). Null when steps
         * cannot be read at all, or when the shown day has none.
         */
        val movement: MovementToday? = null,
        /**
         * How the day stood against the rule that governed it, or null if none did (D27, D29).
         *
         * Either shape: a count of meals inside and outside for the fixed hours, the eating
         * stretches that BEGAN on the day for a ratio. The screen branches on which; neither is
         * read as the other.
         */
        val verdict: DayVerdict? = null,
        /**
         * Whether the FIXED hours in force today are open right now, for the ring on the mark.
         *
         * A fact about NOW, derived from the rule in force today rather than from the day on
         * screen. It fills the ring in; it never decides WHICH mark the day gets, because the day
         * on screen may well be governed by the other kind. That choice is the [verdict]'s.
         *
         * Null when a ratio is in force, and when no rule is. An eating stretch does have an
         * open-or-shut state — it is open until the fast completes — and this briefly reported it,
         * which put a ring on a past FIXED day whose fill came from whether eating is going on
         * right now: a mark about a different thing from the one that day was judged by, drawn
         * beside that day's own tally. The measured kind has a mark of its own and never wanted
         * the ring (design §4, corrected 2026-09-17).
         */
        val windowOpenNow: Boolean? = null,
        val windowKept: Int = 0,
        val windowJudged: Int = 0,
        /**
         * Today's one sentence under a ratio — a time he can act on (D32): "If you're keeping your
         * fast, next meal from 12:00." Null on every other day, and when no ratio is in force.
         */
        val ratioNow: String? = null,
        /** "Kept 10 of 14 stretches since 3 Sep" — under a ratio, on every day (D32). */
        val ratioTally: String? = null,
        /** One encouraging line, at most one a day, or null. */
        val encouragement: String? = null,
        /** How consistently he has been logging, counted from the record (D13). */
        val streak: Streak = Streak(0, 0, 0),
        /**
         * Whether today itself holds something, so the run in [streak] ends today rather than
         * yesterday. A run counted back from yesterday still stands, but its milestone was
         * yesterday's (D52).
         */
        val loggedToday: Boolean = false,
        /**
         * The weekly congratulation fired today, dismissed or not. It has said the milestone, so the
         * figure does not say it again (D52).
         */
        val weeklyCongratulationToday: Boolean = false,
        /**
         * Which rows on the day are ticked, on the way to becoming a meal (design §3.5).
         *
         * Item ids, not meal ids: rows logged at different moments can be pulled together, so the
         * choice cuts across the meals they were logged in. Held here for the reason [openMeals] is:
         * state a screen keeps to itself is state no test can put into a known position.
         */
        val chosen: Set<Long> = emptySet(),
        /**
         * Why the last attempt to make a meal was not made — always naming the row that stood in the
         * way, so it is a next step rather than a dead end.
         */
        val refusal: String? = null,
        /**
         * An action that threw rather than finishing, drawn in [refusal]'s slot and never beside it:
         * there is nothing to act on, only the fact, and where it was written down.
         */
        val failed: ActionRefused? = null,
    ) : DayUiState {

        /**
         * Whether the day is in the middle of being picked from.
         *
         * Derived rather than stored: choosing IS having something chosen, and a second flag could
         * disagree with the set — leaving the day in a mode with nothing in it, where every tap
         * still ticked.
         */
        val choosing: Boolean get() = chosen.isNotEmpty()

        /**
         * The one consistency figure the day shows, or null (D52).
         *
         * Derived rather than stored, for [choosing]'s reason: a second field could disagree with
         * the [streak] it is chosen from. The milestone belongs to today's page only — on a past
         * day's page the same run is still printed, plainly, because it was not reached there.
         */
        val consistency: ConsistencyFigure?
            get() = Consistency.of(
                streak = streak,
                runEndsToday = isToday && loggedToday,
                milestoneAlreadySaid = weeklyCongratulationToday,
            )

        /**
         * Nothing at all was written down on this day.
         *
         * Every row on the day hangs off a meal, so an empty meal list is an empty day. It matters
         * only in the past, where it is the difference between "he ate nothing" and "no record of
         * what he ate" — see [com.metaself.app.ui.day.DayTotalsWording.headline].
         */
        val nothingLogged: Boolean get() = meals.isEmpty()
    }
}
