package com.metaself.app.ui.screen.record

import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.streak.Streak
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.screen.day.DayUiState

/**
 * What the day's record is showing (D50).
 *
 * **Deliberately NOT [DayUiState.Ready].** The day answers *how am I doing* and this screen answers
 * *what exactly did I write down, and is it right* — two different jobs, which is the whole reason
 * the record was given a screen of its own. So the target, what is left, the macros, the streak, the
 * window and every notice are absent from this state rather than present and ignored: handed the
 * day's state whole, the temptation to draw progress here would be built in, and the first small
 * addition would put the day back on this page a piece at a time.
 *
 * What is here is the record and the things that can be done to it: the loggings, which meals he
 * built are open, which rows are ticked, and why the last attempt to make a meal was refused.
 *
 * **The one exception is the [streak]**, since D52: the day now prints one consistency figure, and
 * all three counts moved here, to a "Your record" section at the foot of the list. They are facts
 * about how much of the record exists, not about how the day is going, which is why they belong on
 * the screen where the record is read.
 *
 * **[epochDay] is the day this screen was reached from, and there is no way to change it.** See
 * [RecordScreen] for why that is a correctness requirement rather than a simplification.
 */
data class RecordUiState(
    val epochDay: Long,
    /**
     * The calendar day now, so the screen can say "Today" or "Yesterday" rather than only a date.
     *
     * Passed rather than read from a clock, which is what this project does everywhere: reading the
     * clock is pushed to the edge, and a screen that read its own would be a second answer to which
     * day it is.
     */
    val todayEpochDay: Long,
    val isToday: Boolean,
    val meals: List<Meal>,
    /** Which meals he built are open, showing what is under them. Held outside the row, as D49's. */
    val openMeals: Set<Long> = emptySet(),
    /** Which rows are ticked, on the way to becoming a meal or to being deleted together. */
    val chosen: Set<Long> = emptySet(),
    /** Why the last attempt to make a meal was not made — always naming the row that stood in it. */
    val refusal: String? = null,
    /** An action on the record that threw rather than finishing, in [refusal]'s slot. */
    val failed: ActionRefused? = null,
    /** How consistently he has been logging, for "Your record" at the foot of the list (D52). */
    val streak: Streak = Streak(0, 0, 0),
) {

    /** Every row of the record, across the loggings they were written in. */
    val items: List<FoodItem> get() = meals.flatMap { it.items }

    /**
     * Whether the record is in the middle of being picked from.
     *
     * Derived rather than stored, for [DayUiState.Ready]'s reason: choosing IS having something
     * chosen, and a second flag could disagree with the set.
     */
    val choosing: Boolean get() = chosen.isNotEmpty()

    /** Nothing at all was written down on this day. Every row hangs off a logging. */
    val nothingLogged: Boolean get() = meals.isEmpty()

    /** The ticked rows in the record's own order, which is the order a meal made from them keeps. */
    val chosenRows: List<FoodItem> get() = items.filter { it.id in chosen }

    companion object {
        /**
         * The record's own state, narrowed from the day's.
         *
         * One view model serves both screens (see [RecordScreen]), so this is where the narrowing
         * happens: the record takes the eight things it draws and leaves the rest of the day behind.
         */
        fun of(day: DayUiState.Ready, todayEpochDay: Long): RecordUiState = RecordUiState(
            epochDay = day.epochDay,
            todayEpochDay = todayEpochDay,
            isToday = day.isToday,
            meals = day.meals,
            openMeals = day.openMeals,
            chosen = day.chosen,
            refusal = day.refusal,
            failed = day.failed,
            streak = day.streak,
        )
    }
}
