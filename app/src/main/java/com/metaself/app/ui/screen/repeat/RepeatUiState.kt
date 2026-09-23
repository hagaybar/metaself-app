package com.metaself.app.ui.screen.repeat

import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.food.CannotCount
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.LoggedFrom
import com.metaself.app.domain.food.Logging
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeals
import com.metaself.app.domain.food.SavedMeal

/**
 * A meal the owner built, opened for one day only.
 *
 * **This changes nothing about the meal.** He can drop the oil, double the cucumber, and add a piece
 * of bread that is not in the salad at all; tomorrow's salad still has oil in it, one cucumber's
 * worth, and no bread. The app will never offer to update the meal because he has dropped the oil
 * four times running — if the meal is to change, he changes it.
 *
 * @property asDefined the meal as it stands right now, kept so that "whether this was adjusted" is
 *   answered against the definition AT THIS MOMENT. The definition may be edited afterwards, so
 *   comparing a past day against a later one would be comparing it against something that did not
 *   exist yet.
 */
data class Adjusting(
    val asDefined: SavedMeal,
    val rows: List<MealComponent>,
) {
    val totalKcal: Int
        get() = rows.sumOf { (it.worth as? LoggedFrom.Numbers)?.kcal ?: 0 }

    /** Whether what is about to be logged differs from the meal as it stands. Written once. */
    val adjusted: Boolean get() = SavedMeals.wasAdjusted(asDefined, rows)

    val isEmpty: Boolean get() = rows.isEmpty()
}

/** Which of the two lists is in front. */
enum class RepeatTab { FOODS, MEALS }

/**
 * A food picked, and how much of it is about to be logged.
 *
 * Opened in place rather than on a screen of its own, so that what he is deciding about stays next
 * to the other foods he might have picked instead.
 *
 * **He chooses how he is counting, and only the ways the food actually knows are on offer.** The
 * other is not hidden: it is shown with the reason it is unavailable, because a field that is simply
 * absent looks like a fault in the app, and the same field with a sentence beside it is an
 * invitation to supply what is missing.
 *
 * @property amount what he has typed, as he typed it. A string rather than a number because a
 *   half-typed "1." is a real state and turning it into 1.0 under his fingers moves the cursor.
 */
data class Choosing(
    val index: Int,
    val food: Food,
    val countedAs: CountedAs,
    val amount: String = "",
) {
    /** The ceiling on how much of it, by how he is counting: 5000 g, or 100 of them (D42). */
    val most: Double get() = BelievableAmount.amountEaten(countedAs)

    /**
     * The amount to log: above nothing and not past [most] (D42, issue #32). Past it there is no
     * preview — which is what stops both the 2,147,483,647-kcal row and the throw while drawing
     * that "Infinity" caused on a food with a zero figure (0 × ∞ is no number).
     */
    val amountOrNull: Double?
        get() = typedAmount?.takeIf { it > 0.0 && BelievableAmount.isBelievable(it, most) }

    /**
     * True only for a number past [most], which is what the box says out loud. A blank, a zero, a
     * word or a half-typed "1." just leave Log it off, as they always have.
     */
    val amountTooMuch: Boolean
        get() = typedAmount?.let { BelievableAmount.isTooMuch(it, most) } == true

    private val typedAmount: Double? get() = amount.trim().replace(',', '.').toDoubleOrNull()

    /** Why weighing is not on offer, or null when it is. */
    val cannotWeigh: CannotCount? get() = Logging.canWeigh(food.facts)

    /** Why counting is not on offer, or null when it is. */
    val cannotCount: CannotCount? get() = Logging.canCount(food.facts)

    /**
     * What would be logged if he pressed the button now — so he sees the number before it lands on
     * the record rather than after.
     */
    val preview: LoggedFrom.Numbers?
        get() = amountOrNull?.let { Logging.log(food.facts, it, countedAs) as? LoggedFrom.Numbers }

    val canLog: Boolean get() = preview != null
}

/**
 * What the eat-it-again screen is showing.
 *
 * Two lists rather than one merged one: a food and a meal are different things to want, and
 * interleaving them makes both harder to scan. One search box serves whichever is showing.
 *
 * **The meals list is now the meals he built and named.** Nothing is promoted from what he happens
 * to have logged together, so it is empty until he builds one — which is why the builder had to
 * ship in the same release as the change that emptied it.
 */
data class RepeatUiState(
    val tab: RepeatTab = RepeatTab.FOODS,
    val query: String = "",
    val foods: List<Food> = emptyList(),
    val meals: List<SavedMeal> = emptyList(),
    val adjusting: Adjusting? = null,
    val choosing: Choosing? = null,
) {
    /** True when he has searched and nothing came back, which needs saying rather than showing. */
    val searchedAndFoundNothing: Boolean
        get() = query.isNotBlank() &&
            when (tab) {
                RepeatTab.FOODS -> foods.isEmpty()
                RepeatTab.MEALS -> meals.isEmpty()
            }

    /**
     * True when the search missed BOTH lists, which is the only case where describing is the right
     * next step. [searchedAndFoundNothing] is per-tab on purpose — the sentence about this list is
     * about this list — but offering to describe something the other tab already holds would
     * manufacture the duplicate the search exists to prevent.
     */
    val nothingMatchedEither: Boolean
        get() = query.isNotBlank() && foods.isEmpty() && meals.isEmpty()

    /**
     * The other list, when the search missed the one in front and the other holds a match; null
     * otherwise. Both lists are already filtered by the same words, so this is known without looking
     * again — and saying nothing about it left the owner to guess that the other tab was worth a look.
     */
    val matchesOnOtherTab: RepeatTab?
        get() = when {
            !searchedAndFoundNothing -> null
            tab == RepeatTab.MEALS && foods.isNotEmpty() -> RepeatTab.FOODS
            tab == RepeatTab.FOODS && meals.isNotEmpty() -> RepeatTab.MEALS
            else -> null
        }

    /**
     * True when the list in front is empty with nothing searched for, and the other list is not —
     * so [nothingEverLogged] is false and [searchedAndFoundNothing] is false, and the screen would
     * otherwise draw tabs and a search box over an empty space with nothing offered.
     *
     * Deliberately without an `&& !nothingEverLogged` guard: the screen returns on that first, and
     * the guard would make this property's truth depend on the order of two `if`s somewhere else.
     */
    val thisTabIsEmpty: Boolean
        get() = query.isBlank() &&
            when (tab) {
                RepeatTab.FOODS -> foods.isEmpty()
                RepeatTab.MEALS -> meals.isEmpty()
            }

    /**
     * True before anything has ever been logged, which is a different thing to say.
     *
     * Also the hand-off point to describing: this screen is the way in now, so on a fresh install
     * this branch is the first thing the owner sees and must offer a way onward. The four
     * properties here say four different things and none of them collapses into another.
     */
    val nothingEverLogged: Boolean get() = query.isBlank() && foods.isEmpty() && meals.isEmpty()
}

/**
 * A meal about to be written to the record: its rows, which built meal it came from, and whether
 * what is being logged differs from that meal as it stood at this moment.
 *
 * The three travel together because they are written together, once, and because separating them
 * is how a day's row ends up making a claim nothing on the record can check.
 */
data class LoggedMeal(
    val items: List<FoodItem>,
    val savedMealId: Long?,
    val adjusted: Boolean,
)
