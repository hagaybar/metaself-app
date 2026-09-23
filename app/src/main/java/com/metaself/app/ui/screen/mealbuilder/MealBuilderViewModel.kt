package com.metaself.app.ui.screen.mealbuilder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.MealResult
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.data.time.Now
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.FoodSearch
import com.metaself.app.domain.food.ReplacedFacts
import com.metaself.app.ui.food.MealWording
import com.metaself.app.ui.food.RetaughtBecause
import com.metaself.app.ui.food.RetaughtWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Building a meal: name it, put foods in it with amounts, take them out, come back later.
 *
 * **Resumable because what is in the meal is already saved.** A half-built salad is a meal with
 * fewer things in it: its components, their order and removals are written as he makes each change,
 * and its name when he presses Start it or Rename it, so leaving loses none of those. What leaving
 * does lose is what lives only here: a food picked but not yet put in — one opened for adding
 * ([_adding]: from the search, or a food just made here, which itself stays in his list), or one
 * chosen in the food list and still waiting for an amount ([_pending], below) — and a name or
 * rename typed but not yet confirmed. The screen's heading says so for the parts (D37): a food
 * with no amount is not yet anything the record could hold.
 *
 * **A food missing from his list can be made here**, through the same one door everything else goes
 * through, so a food made mid-salad is indistinguishable afterwards from one made by eating it.
 * Sending him away to the foods manager to make the tahini would lose the salad he is halfway
 * through, which is the reason this is worth the extra screenful.
 *
 * **Foods chosen in the food list arrive waiting for an amount**, read out of the route the way the
 * description screen reads the words already typed. Those waiting rows are one of the things here
 * that are NOT saved as he goes: leaving the builder keeps every component already added and
 * forgets them, because a food with no amount is not yet anything the record could hold — and
 * nothing fills an amount in for him (D4). Each of them joins the meal when he says so, not while
 * he is still typing the number. What the meal already holds is filtered out of them, because the
 * route is read afresh every time this screen is built — including after Android has killed the
 * process and restored it.
 */
@HiltViewModel
class MealBuilderViewModel @Inject constructor(
    private val meals: SavedMealRepository,
    private val foods: FoodRepository,
    private val now: Now,
    savedState: SavedStateHandle,
) : ViewModel() {

    /** Zero means a meal that does not exist yet: he is starting one. */
    private val openedMealId: Long = savedState.get<String>("mealId")?.toLongOrNull() ?: 0L

    /**
     * The foods he chose in the list, read at construction.
     *
     * Read here rather than in the screen for the reason the description screen reads its carried
     * words here: the first composition has to be able to draw them, and anything read later arrives
     * after the list has already been drawn empty.
     */
    private val chosenFoodIds: List<Long> = savedState.get<String>("foods")
        .orEmpty()
        .split(',')
        .mapNotNull { it.trim().toLongOrNull() }

    private val _mealId = MutableStateFlow(openedMealId)
    private val _typedName = MutableStateFlow("")
    private val _query = MutableStateFlow("")
    private val _adding = MutableStateFlow<Adding?>(null)
    private val _creating = MutableStateFlow(false)
    private val _refusal = MutableStateFlow<String?>(null)
    private val _meal = MutableStateFlow<com.metaself.app.domain.food.SavedMeal?>(null)
    private val _pending = MutableStateFlow<List<Pending>>(emptyList())

    val state: StateFlow<MealBuilderUiState> = combine(
        _meal,
        foods.observeOffered(),
        _query,
        _adding,
        combine(_typedName, _creating, _refusal, _pending) { name, creating, refusal, pending ->
            Aside(name, creating, refusal, pending)
        },
    ) { meal, ownFoods, query, adding, aside ->
        val inMeal = meal?.components.orEmpty().map { it.food }
        val waiting = aside.pending.map { it.food }
        MealBuilderUiState(
            meal = meal,
            typedName = aside.typedName,
            query = query,
            // What is already in the meal is not offered again: the same food twice in one meal is
            // an editing accident rather than something he meant. Nor is one already waiting for an
            // amount, for the same reason. What is not offered is named instead (D41), or the
            // search would say nothing matched a food drawn a few lines up.
            candidates = FoodSearch.matching(ownFoods, query)
                .filterNot { food -> inMeal.any { it.id == food.id } }
                .filterNot { food -> waiting.any { it.id == food.id } },
            // Searched over what is on screen, not over the foods on offer: a food hidden since it
            // went in is still in the meal and still drawn. An empty search matches everything, so
            // without the guard the sentence would list the whole meal before he typed a letter.
            alreadyIn = if (query.isBlank()) emptyList() else FoodSearch.matching(inMeal, query),
            alreadyWaiting = if (query.isBlank()) emptyList() else FoodSearch.matching(waiting, query),
            adding = adding,
            pending = aside.pending,
            creating = aside.creating,
            refusal = aside.refusal,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = MealBuilderUiState(),
    )

    init {
        viewModelScope.launch {
            if (openedMealId != 0L) reload()
            // What the meal already holds is never offered again, here for the reason it is not
            // offered in the search results either — and because the route is re-read every time
            // this screen is rebuilt. Android may kill the process at any moment and hand the same
            // route back, and without this a food already put in would return as an empty waiting
            // row, whose confirmation would write over the amount he had entered.
            val alreadyIn = _meal.value?.components.orEmpty().map { it.food.id }.toSet()
            // In the order he chose them, and with nothing filled in: a food knows what it is, not
            // how much of it goes in a salad.
            _pending.value = chosenFoodIds
                .filterNot { it in alreadyIn }
                .mapNotNull { foods.byId(it) }
                .map { food -> Pending(food = food, countedAs = defaultFor(food)) }
        }
    }

    fun setName(name: String) {
        _typedName.value = name
    }

    /**
     * Name it, which is the one thing that has to happen before anything else can.
     *
     * A meal is only something he built AND named: the app never invents a name, which is the whole
     * reason the derived list this replaces refused to label anything it offered.
     */
    fun name() {
        val typed = _typedName.value.trim()
        if (typed.isBlank()) return
        viewModelScope.launch {
            when (val result = meals.create(typed)) {
                is MealResult.Built -> {
                    _mealId.value = result.mealId
                    reload()
                }
                is MealResult.NameTaken ->
                    _refusal.value = MealWording.nameTaken(typed)
                MealResult.Done -> Unit
            }
        }
    }

    fun rename(name: String) {
        val id = _mealId.value.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            when (meals.rename(id, name)) {
                is MealResult.NameTaken ->
                    _refusal.value = MealWording.nameTaken(name)
                else -> reload()
            }
        }
    }

    fun search(query: String) {
        _query.value = query
    }

    /** Pick a food to put in, and start saying how much. */
    fun beginAdding(foodId: Long) {
        val food = state.value.candidates.firstOrNull { it.id == foodId } ?: return
        _adding.value = Adding(food = food, countedAs = defaultFor(food))
    }

    fun countAs(countedAs: CountedAs) {
        _adding.value = _adding.value?.copy(countedAs = countedAs)
    }

    fun setAmount(amount: String) {
        _adding.value = _adding.value?.copy(amount = amount)
    }

    fun cancelAdding() {
        _adding.value = null
    }

    fun confirmAdding() {
        val adding = _adding.value ?: return
        val amount = adding.amountOrNull ?: return
        val id = _mealId.value.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            meals.put(id, adding.food.id, amount, adding.countedAs)
            _adding.value = null
            reload()
        }
    }

    // --- The foods chosen in the list, waiting for an amount -------------------------------------

    /**
     * What he has typed against a waiting food, so far.
     *
     * **Typing alone never puts anything in the meal.** The box is filled a character at a time, so
     * "100" arrives as "1", then "10", then "100" — and a row that went in the instant what was
     * typed could be costed would have gone in at one gram, taken its own box off the screen, and
     * left the "00" nowhere to land, with no way back: a component's amount cannot be edited once it
     * is in. So the row stays, showing what the amount so far comes to, until [confirmPending].
     */
    fun setPendingAmount(foodId: Long, text: String) {
        changePending(foodId) { it.copy(amount = text) }
    }

    /**
     * Put a waiting food into the meal at the amount he typed.
     *
     * The same shape as [confirmAdding], and offered on the same terms: only once the amount is one
     * this food can actually be costed at. A half-typed number, a zero, or an amount counted a way
     * this food does not support leaves the row exactly where it is, with the reason on screen.
     */
    fun confirmPending(foodId: Long) {
        val ready = _pending.value.firstOrNull { it.food.id == foodId }?.takeIf { it.canAdd } ?: return
        val amount = ready.amountOrNull ?: return
        // Until it has a name there is no meal to put anything in, so it goes on waiting. The name
        // is the one gate in this screen and this does not become a second way around it.
        val mealId = _mealId.value.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            meals.put(mealId, ready.food.id, amount, ready.countedAs)
            _pending.value = _pending.value.filterNot { it.food.id == foodId }
            reload()
        }
    }

    /** Grams or whole ones — only the ways this food knows are on offer, and the other says why. */
    fun countPendingAs(foodId: Long, countedAs: CountedAs) {
        changePending(foodId) { it.copy(countedAs = countedAs) }
    }

    /**
     * Take a waiting food out again.
     *
     * It touches the meal not at all: nothing was put in it, so there is nothing to take out. What is
     * already in stays exactly as it was.
     */
    fun dropPending(foodId: Long) {
        _pending.value = _pending.value.filterNot { it.food.id == foodId }
    }

    private fun changePending(foodId: Long, change: (Pending) -> Pending) {
        _pending.value = _pending.value.map { if (it.food.id == foodId) change(it) else it }
    }

    fun remove(componentId: Long) {
        viewModelScope.launch {
            meals.remove(componentId)
            reload()
        }
    }

    /** The order is his: identity no longer depends on it, and the display does. */
    fun move(componentId: Long, by: Int) {
        val meal = _meal.value ?: return
        val order = meal.components.map { it.id }.toMutableList()
        val at = order.indexOf(componentId)
        val to = at + by
        if (at < 0 || to !in order.indices) return
        order[at] = order[to].also { order[to] = order[at] }
        viewModelScope.launch {
            meals.reorder(meal.id, order)
            reload()
        }
    }

    // --- Making a food on the spot -------------------------------------------------------------

    fun beginCreatingFood() {
        _creating.value = true
    }

    fun cancelCreatingFood() {
        _creating.value = false
    }

    /**
     * Make a food that is not in his list, without leaving the meal.
     *
     * The same one door everything else goes through, so a food made mid-salad is indistinguishable
     * afterwards from one made by eating it — and the identity rule catches it if the thing he is
     * making already exists under another spelling.
     */
    fun createFood(form: FoodForm) {
        val facts = form.toFacts(now()) ?: return
        viewModelScope.launch {
            val made = foods.findOrCreate(
                name = form.name,
                brand = form.brand.takeIf { it.isNotBlank() },
                facts = facts,
            )
            _creating.value = false
            _adding.value = Adding(
                food = made.food,
                countedAs = defaultFor(made.food),
                // Typing figures into a name he has used before replaces what that food held, in
                // exactly the way logging does — so it says so, worded for typing rather than for
                // logging, in the only surface this screen has (D45, issue #13). Null when the
                // food was made by this call or nothing of it was replaced.
                retaughtNotice = RetaughtWording.notice(
                    ReplacedFacts.one(made.food.name, made.before, made.food.facts),
                    RetaughtBecause.JUST_TYPED,
                ),
            )
        }
    }

    /**
     * Which way of counting a food opens on: the one it knows best.
     *
     * Not a guess about the amount — there is none — but about the question asked, which the food's
     * own facts answer: a food that knows what 100 g of it are worth is weighed, anything else is
     * counted out.
     */
    private fun defaultFor(food: Food): CountedAs =
        if (food.facts.canBeWeighed) CountedAs.GRAMS else CountedAs.UNITS

    /**
     * Delete the meal he built.
     *
     * It takes its parts list and nothing else. No food is touched — the schema refuses that — and
     * no past day is touched either: a day logged from this meal loses its title and shows its
     * items, which is what every day looked like before meals he built existed.
     */
    fun delete() {
        val id = _mealId.value.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            meals.delete(id)
            _mealId.value = 0L
            _meal.value = null
        }
    }

    fun dismissRefusal() {
        _refusal.value = null
    }

    /**
     * Read the meal back from the store after every change.
     *
     * Rather than keeping a copy here and hoping the two agree: what is in the meal is the store's
     * answer, and a builder holding its own idea of it is how a half-built salad ends up disagreeing
     * with itself after a rotation.
     */
    private suspend fun reload() {
        _meal.value = _mealId.value.takeIf { it != 0L }?.let { meals.byId(it) }
    }

    /** The four things that are not the meal itself, as one value, because `combine` takes five. */
    private data class Aside(
        val typedName: String,
        val creating: Boolean,
        val refusal: String?,
        val pending: List<Pending>,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
