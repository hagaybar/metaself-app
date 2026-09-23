package com.metaself.app.ui.screen.repeat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FoodSearch
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.SavedMeals
import com.metaself.app.domain.portion.Portions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import javax.inject.Inject

/**
 * What has been eaten before, ready to be eaten again — as it was, or in a different amount.
 *
 * Nothing here talks to the model, and nothing here touches the network. A repeat is a copy of rows
 * already on the phone, which is why it is instant, free, and works in a lift. Adjusting is
 * arithmetic on those same rows and is no different in that respect.
 *
 * More rows are read than are shown because deduplication happens after reading: thirty identical
 * breakfasts collapse to one, and a limit applied before that would return one meal thirty times.
 */
@HiltViewModel
class RepeatViewModel @Inject constructor(
    foods: FoodRepository,
    savedMeals: SavedMealRepository,
) : ViewModel() {

    private val _adjusting = MutableStateFlow<Adjusting?>(null)
    private val _choosing = MutableStateFlow<Choosing?>(null)

    /**
     * Which list is in front and what is typed into the search, as one value.
     *
     * One rather than two because `combine` takes five flows and there are six things to combine —
     * and because these two genuinely move together: every change to either closes whatever is open,
     * for the same reason.
     */
    private val _looking = MutableStateFlow(Looking())

    /**
     * The foods list is now the owner's foods, not a list derived from whatever happened to be
     * logged.
     *
     * A food logged again and again is one entry instead of thirty, and picking it asks how much
     * rather than offering a past amount. The meals list is still derived and stays so
     * until meals built by hand exist to replace it.
     */
    val state: StateFlow<RepeatUiState> = combine(
        foods.observeOffered().onEach { offered ->
            _choosing.update { open -> open?.let { refreshed(it, offered) } }
            _adjusting.update { open ->
                open?.adding?.let { open.copy(adding = refreshed(it, offered)) } ?: open
            }
        },
        savedMeals.observeOffered(),
        _adjusting,
        _choosing,
        _looking,
    ) { ownFoods, ownMeals, adjusting, choosing, looking ->
        val foods = FoodSearch.matching(ownFoods, looking.query)
        RepeatUiState(
            tab = looking.tab,
            query = looking.query,
            foods = foods,
            meals = ownMeals.filter { it.name.contains(looking.query.trim(), ignoreCase = true) },
            adjusting = adjusting?.let { offering(it, ownFoods) },
            choosing = choosing?.let { current(it, foods) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = RepeatUiState(),
    )

    /**
     * The question open, about the food as it is NOW rather than as it was when he picked it.
     *
     * "Give this a portion" leaves this screen for the food's editor with the question still open,
     * and he comes back to it. The foods are observed, so the list already shows the new portion; a
     * question still holding the food as picked would keep counting switched off beside a row that
     * says it can be counted — and, worse, would log from the old figures while the preview drew the
     * new ones. So the food is replaced in the question itself, where [countAs], [setAmount] and
     * [chosen] read it, not only in the copy the screen draws. Found by id, because a rename can
     * move it.
     *
     * Looked for among every food offered, not only those the search finds. One gone from that —
     * deleted, hidden, or joined into another in the editor — closes the question: logging it would
     * write a row about a food that no longer exists.
     */
    private fun refreshed(choosing: Choosing, offered: List<Food>): Choosing? {
        val food = offered.firstOrNull { it.id == choosing.food.id } ?: return null
        return choosing.copy(food = food)
    }

    /**
     * The question with the row it sits at in the list as filtered now — or at no row, -1, when a
     * rename has taken the food out of what the search finds. It is still his food, so the question
     * stays open, but the row it was at may now be a different food's.
     */
    private fun current(choosing: Choosing, foods: List<Food>): Choosing =
        choosing.copy(index = foods.indexOfFirst { it.id == choosing.food.id })

    /**
     * The adjustment with what its own search finds filled in: his foods matching it, less those
     * it already holds, which are named instead (D41) — the builder's rule, for the same reason.
     */
    private fun offering(adjusting: Adjusting, ownFoods: List<Food>): Adjusting {
        val finding = adjusting.finding ?: return adjusting
        val held = adjusting.rows.map { it.food }
        return adjusting.copy(
            offered = FoodSearch.matching(ownFoods, finding).filterNot { food -> held.any { it.id == food.id } },
            alreadyIn = if (finding.isBlank()) emptyList() else FoodSearch.matching(held, finding),
        )
    }

    fun showTab(tab: RepeatTab) {
        _looking.value = _looking.value.copy(tab = tab)
        closeWhateverIsOpen()
    }

    fun search(query: String) {
        _looking.value = _looking.value.copy(query = query)
        // The index a row sits at changes as the list filters, so anything left open would suddenly
        // be about a different food.
        closeWhateverIsOpen()
    }

    private fun closeWhateverIsOpen() {
        _adjusting.value = null
        _choosing.value = null
    }

    /**
     * Pick a food, and start deciding how much of it.
     *
     * Opened on whichever way of counting the food actually knows, preferring weighing when it knows
     * both — grams are the more exact of the two and the one a kitchen scale answers.
     */
    fun beginChoosing(index: Int) {
        val food = state.value.foods.getOrNull(index) ?: return
        val countedAs = if (food.facts.canBeWeighed) CountedAs.GRAMS else CountedAs.UNITS
        _choosing.value = Choosing(index = index, food = food, countedAs = countedAs)
    }

    fun countAs(countedAs: CountedAs) {
        val current = _choosing.value ?: return
        // The amount is kept rather than cleared: switching from grams to bars having typed 2 almost
        // always means he wants two bars, and clearing it would make him type it again.
        _choosing.value = current.copy(countedAs = countedAs)
    }

    fun setAmount(amount: String) {
        _choosing.value = _choosing.value?.copy(amount = amount)
    }

    fun cancelChoosing() {
        _choosing.value = null
    }

    /**
     * What would be logged, with the food already attached.
     *
     * The numbers are computed here, once, and frozen onto the row — nothing recomputes them
     * afterwards, which is why correcting the food later cannot change what this day was worth.
     */
    fun chosen(): FoodItem? {
        val choosing = _choosing.value ?: return null
        val numbers = choosing.preview ?: return null
        _choosing.value = null
        return FoodItem(
            name = choosing.food.name,
            portion = Portions.words(numbers.amount, numbers.unit),
            portionAmount = numbers.amount,
            portionUnit = numbers.unit,
            kcal = numbers.kcal,
            proteinG = numbers.proteinG,
            carbsG = numbers.carbsG,
            fatG = numbers.fatG,
            source = numbers.source,
            confidence = numbers.confidence,
            foodId = choosing.food.id,
        )
    }

    // --- A meal, for one day only ----------------------------------------------------------------

    /**
     * Open a meal he built, for this day only.
     *
     * Every row starts at exactly what the meal says. **Nothing he does here changes the meal**: he
     * can drop the oil, double the cucumber and add a slice of bread that is not in the salad at
     * all, and tomorrow's salad is still the salad.
     */
    fun beginAdjusting(mealIndex: Int) {
        val meal = state.value.meals.getOrNull(mealIndex) ?: return
        _adjusting.value = Adjusting(asDefined = meal, rows = meal.components)
    }

    fun cancelAdjusting() {
        _adjusting.value = null
    }

    /** Change how much of one of them there is, this once. */
    /**
     * How much of one of them there is, this once, as typed (D53 §6).
     *
     * The text is kept as typed, so "1." and "" are states the box can be in. The part's amount
     * follows only when the text is an amount it can be logged at — above nothing and within its
     * ceiling (D42); otherwise it keeps its last one and the adjustment is blocked
     * ([Adjusting.blockedBy]) until he types one.
     */
    fun setComponentAmount(componentId: Long, text: String) {
        val current = _adjusting.value ?: return
        val component = current.rows.firstOrNull { it.id == componentId } ?: return
        val amount = usableAmount(component, text)
        _adjusting.value = current.copy(
            typed = current.typed + (componentId to text),
            rows = if (amount == null) {
                current.rows
            } else {
                current.rows.map { if (it.id == componentId) it.copy(amount = amount) else it }
            },
        )
    }

    /**
     * − and + on a counted part: one of it more or fewer (D53 §6), as on the proposal screen.
     *
     * Only for a part counted in pieces; a weighed one has only its box. Never below one: a step
     * that would go there does nothing, so a typed 0.5 is kept, and nought of something is a part to
     * remove. A box that holds no number steps from nothing, so + gives 1. Decimal arithmetic, so
     * 0.1 and one make 1.1.
     */
    fun stepComponent(componentId: Long, by: Int) {
        val current = _adjusting.value ?: return
        val component = current.rows.firstOrNull { it.id == componentId } ?: return
        if (component.countedAs != CountedAs.UNITS) return
        val now = current.amountText(component).trim().replace(',', '.').ifEmpty { "0" }
            .toBigDecimalOrNull() ?: return
        val next = now + by.toBigDecimal()
        if (next < BigDecimal.ONE) return
        setComponentAmount(componentId, next.stripTrailingZeros().toPlainString())
    }

    /**
     * Drop something from it — the oil he did not use this time.
     *
     * Removing the last one closes the adjuster rather than leaving an empty meal to be logged: a
     * meal with nothing in it is not a thing that happened.
     */
    fun removeComponent(componentId: Long) {
        val current = _adjusting.value ?: return
        val kept = current.rows.filterNot { it.id == componentId }
        _adjusting.value = if (kept.isEmpty()) null else current.copy(rows = kept)
    }

    /**
     * Start looking for something to put in it, this once.
     *
     * The panel's own search, not the screen's: the screen's closes whatever is open, and typing
     * the name of the bread would throw away the salad it was to go with (issue #10).
     */
    fun beginAddingToMeal() {
        _adjusting.update { it?.copy(finding = "", adding = null) }
    }

    fun searchToAdd(query: String) {
        _adjusting.update { it?.copy(finding = query) }
    }

    /** Leave the step without putting anything in. The adjustment is as it was. */
    fun stopAddingToMeal() {
        _adjusting.update { it?.copy(finding = null, adding = null) }
    }

    /**
     * Pick one of the foods the panel's search offers, and start deciding how much of it — opened
     * on the same way of counting the foods tab would open it on.
     */
    fun pickToAdd(foodId: Long) {
        val food = state.value.adjusting?.offered?.firstOrNull { it.id == foodId } ?: return
        val countedAs = if (food.facts.canBeWeighed) CountedAs.GRAMS else CountedAs.UNITS
        _adjusting.update { it?.copy(adding = Choosing(index = -1, food = food, countedAs = countedAs)) }
    }

    fun countAddedAs(countedAs: CountedAs) {
        // The amount is kept, as it is on the foods tab: 2 typed as grams almost always means two.
        _adjusting.update { open -> open?.copy(adding = open.adding?.copy(countedAs = countedAs)) }
    }

    fun setAddedAmount(amount: String) {
        _adjusting.update { open -> open?.copy(adding = open.adding?.copy(amount = amount)) }
    }

    /** Not this one: back to the search, with the words still typed. */
    fun dropPicked() {
        _adjusting.update { it?.copy(adding = null) }
    }

    /**
     * Put the food picked into today's meal, in the amount typed, and close the step.
     *
     * Nothing goes in until the amount makes sense and the food can be costed that way — the same
     * terms on which the foods tab's Log it is enabled. Nothing is written anywhere either: the row
     * joins the adjustment, and reaches the record only when he logs it.
     */
    fun putItIn() {
        val adding = _adjusting.value?.adding ?: return
        if (adding.preview == null) return
        val amount = adding.amountOrNull ?: return
        if (addToAdjustment(adding.food, amount, adding.countedAs)) stopAddingToMeal()
    }

    /**
     * Add something that is not in the meal at all, this once. True when it went in.
     *
     * The adjustment may ADD as well as remove and rescale — a piece of bread with the salad is a
     * thing that happens, and an adjuster that could only take away would send him back to describing
     * it from scratch.
     *
     * A food it already holds is refused: a meal holds a food once (D41). The panel's search never
     * offers one, and names it instead, so this is a backstop rather than the way he is told.
     */
    fun addToAdjustment(food: Food, amount: Double, countedAs: CountedAs): Boolean {
        val current = _adjusting.value ?: return false
        if (amount <= 0.0) return false
        if (current.rows.any { it.food.id == food.id }) return false
        _adjusting.value = current.copy(
            rows = current.rows + MealComponent(
                // Negative, so it cannot collide with a real component of the meal and so that
                // nothing downstream can mistake it for part of the definition — and one below the
                // lowest in use, never derived from the count: after a removal the count hands out
                // an id a row still holds, and remove or rescale then acts on both (issue #10).
                id = minOf(0L, current.rows.minOfOrNull { it.id } ?: 0L) - 1,
                food = food,
                amount = amount,
                countedAs = countedAs,
                // Last on the record, where the panel draws it. The record is ordered by position,
                // and a count can fall below a position still in use after a removal, or below the
                // meal's own when the builder left gaps in them (issue #10).
                position = (current.rows.maxOfOrNull { it.position } ?: -1) + 1,
            ),
        )
        return true
    }

    /**
     * What would be logged if he saved it now, and whether it differs from the meal as it stands.
     *
     * The numbers are computed here, once, and frozen onto the rows. Whether it was adjusted is
     * answered against the definition AT THIS MOMENT and written once — the definition may be
     * edited afterwards, and the flag must never be counted or turned into an offer to change the
     * meal, because nothing learns from what he does.
     */
    fun adjusted(): LoggedMeal? {
        val current = _adjusting.value ?: return null
        // A part whose box holds no usable amount is not logged at its last one behind his back.
        if (current.blockedBy != null) return null
        val items = SavedMeals.toLoggableItems(current.asDefined.copy(components = current.rows))
        if (items.isEmpty()) return null
        _adjusting.value = null
        return LoggedMeal(
            items = items,
            savedMealId = current.asDefined.id,
            adjusted = current.adjusted,
        )
    }

    /** Which list is in front, and what is typed into the search. */
    private data class Looking(
        val tab: RepeatTab = RepeatTab.FOODS,
        val query: String = "",
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
