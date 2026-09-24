package com.metaself.app.ui.screen.mealbuilder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.food.MealResult
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.data.time.Now
import com.metaself.app.domain.ai.FoodReviewer
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodField
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.FoodSearch
import com.metaself.app.domain.food.ReplacedFacts
import com.metaself.app.domain.portion.Portions
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.food.MealWording
import com.metaself.app.ui.food.RetaughtBecause
import com.metaself.app.ui.food.RetaughtWording
import com.metaself.app.ui.guarded
import com.metaself.app.ui.propose.ProposalWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
 *
 * **Nothing here takes the app down.** Every action goes through [guarded]; one that throws puts
 * [MealBuilderUiState.failed] in the refusal slot and is written to the problem log. Each write is
 * one transaction, and most are followed by reading the meal back — so a failure in the write
 * changed nothing, and one in the read after it could not open what was written.
 */
@HiltViewModel
class MealBuilderViewModel @Inject constructor(
    private val meals: SavedMealRepository,
    private val foods: FoodRepository,
    private val now: Now,
    private val problems: ProblemLog,
    private val reviewer: FoodReviewer,
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
    private val _making = MutableStateFlow<MakingFood?>(null)
    private val _refusal = MutableStateFlow<String?>(null)
    private val _meal = MutableStateFlow<com.metaself.app.domain.food.SavedMeal?>(null)
    private val _pending = MutableStateFlow<List<Pending>>(emptyList())
    private val _failed = MutableStateFlow<ActionRefused?>(null)

    /** How many reviews have been asked for, so only the latest one's answer is ever shown. */
    private var reviewsAsked = 0

    val state: StateFlow<MealBuilderUiState> = combine(
        _meal,
        foods.observeOffered(),
        _query,
        _adding,
        combine(_typedName, _making, _refusal, _pending, _failed) { name, making, refusal,
            pending, failed ->
            Aside(name, making, refusal, pending, failed)
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
            making = aside.making,
            refusal = aside.refusal,
            failed = aside.failed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = MealBuilderUiState(),
    )

    init {
        act({ ActionRefused.COULD_NOT_OPEN }) {
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
        writeThenReload(
            write = {
                when (val result = meals.create(typed)) {
                    is MealResult.Built -> _mealId.value = result.mealId
                    is MealResult.NameTaken -> _refusal.value = MealWording.nameTaken(typed)
                    MealResult.Done -> Unit
                }
            },
        )
    }

    fun rename(name: String) {
        val id = _mealId.value.takeIf { it != 0L } ?: return
        var refused = false
        writeThenReload(
            write = {
                if (meals.rename(id, name) is MealResult.NameTaken) {
                    refused = true
                    _refusal.value = MealWording.nameTaken(name)
                }
            },
            reloadIf = { !refused },
        )
    }

    fun search(query: String) {
        _query.value = query
    }

    /** Pick a food to put in, and start saying how much. */
    fun beginAdding(foodId: Long) {
        val food = state.value.candidates.firstOrNull { it.id == foodId } ?: return
        _adding.value = Adding(food = food, countedAs = defaultFor(food))
    }

    /**
     * Tap a part already in the meal: the same panel, holding that part's own amount and way of
     * counting (D53 §7, #4) — his stored numbers, not a default (D30). *Change it* writes through
     * [confirmAdding], which changes the part in place.
     */
    fun beginChanging(componentId: Long) {
        val part = _meal.value?.components?.firstOrNull { it.id == componentId } ?: return
        _adding.value = Adding(
            food = part.food,
            countedAs = part.countedAs,
            amount = Portions.inBox(part.amount),
            changing = part.id,
        )
    }

    /**
     * A food the search found already in the meal (D41): named, not offered — and the name now
     * opens its part, so taking it out and putting it back is never the only way to change it.
     */
    fun beginChangingFood(foodId: Long) {
        val part = _meal.value?.components?.firstOrNull { it.food.id == foodId } ?: return
        beginChanging(part.id)
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

    /** Put it in — or, for a part already there, change it in place: `put` does either (#4). */
    fun confirmAdding() {
        // Only on the terms the button is enabled on: an amount this food can be costed at, counted
        // a way it knows. The screen's button already says so; this is where it is enforced.
        val adding = _adding.value?.takeIf { it.canAdd } ?: return
        val amount = adding.amountOrNull ?: return
        val id = _mealId.value.takeIf { it != 0L } ?: return
        writeThenReload(
            write = { meals.put(id, adding.food.id, amount, adding.countedAs) },
            afterWrite = { _adding.value = null },
        )
    }

    // --- The foods chosen in the list, waiting for an amount -------------------------------------

    /**
     * What he has typed against a waiting food, so far.
     *
     * **Typing alone never puts anything in the meal.** The box is filled a character at a time, so
     * "100" arrives as "1", then "10", then "100" — and a row that went in the instant what was
     * typed could be costed would have gone in at one gram, taken its own box off the screen, and
     * left the "00" nowhere to land — at the time with no way back, since a part's amount could not be
     * changed until D53 §7. So the row stays, showing what the amount so far comes to, until [confirmPending].
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
        writeThenReload(
            write = { meals.put(mealId, ready.food.id, amount, ready.countedAs) },
            afterWrite = { _pending.value = _pending.value.filterNot { it.food.id == foodId } },
        )
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
        writeThenReload(write = { meals.remove(componentId) })
    }

    /** The order is his: identity no longer depends on it, and the display does. */
    fun move(componentId: Long, by: Int) {
        val meal = _meal.value ?: return
        val order = meal.components.map { it.id }.toMutableList()
        val at = order.indexOf(componentId)
        val to = at + by
        if (at < 0 || to !in order.indices) return
        order[at] = order[to].also { order[to] = order[at] }
        writeThenReload(write = { meals.reorder(meal.id, order) })
    }

    // --- Making a food on the spot -------------------------------------------------------------

    fun beginCreatingFood() {
        _making.value = MakingFood()
    }

    /** Cancel: the panel goes, and with it anything typed, reviewed or accepted in it. */
    fun cancelCreatingFood() {
        _making.value = null
    }

    /** He is typing in *Make a food*. Typing in a group withdraws its suggestion (D54 §4). */
    fun setNewFoodForm(form: FoodForm) {
        _making.value = _making.value?.let { making ->
            making.copy(form = form, reviewing = making.reviewing.typed(making.form, form))
        }
    }

    /**
     * **Review the figures** for a food not yet made (D54): the panel's form as it stands, as a new
     * food — there is no stored food, so every group he typed is sent as typed, and a group he left
     * empty is sent as unknown.
     *
     * The same rules as My foods' review: offered only for a name the form would take, once at a
     * time; nothing goes into the boxes until he accepts; a failure is said in the screen's sentence
     * slot in the estimator's own words, the form untouched; an answer for a panel he has closed —
     * or closed and opened again — is dropped; a throw says it could not open, since nothing was
     * written.
     */
    fun reviewNewFood() {
        val making = _making.value ?: return
        if (making.reviewing.asking || FoodField.NAME in making.errors) return
        dismissRefusal()
        val asked = ++reviewsAsked
        val waiting = making.copy(reviewing = making.reviewing.asked())
        _making.value = waiting
        fun stillWaiting() = asked == reviewsAsked && _making.value?.reviewing?.asking == true
        // Set in `finally`, before the guard's handler runs, so a throw is said only while this
        // panel is still waiting on it — never over the builder once the panel has gone.
        var thrownHere = false

        _failed.value = null
        guarded(problems, onRefused = {
            if (thrownHere) {
                _refusal.value = null
                _failed.value = ActionRefused.COULD_NOT_OPEN
            }
        }) {
            try {
                val request = ReviewRequest.of(
                    ReviewProcess.NEW_FOOD,
                    making.form,
                    stored = null,
                    accepted = making.reviewing.accepted,
                )
                val result = reviewer.review(request)
                if (!stillWaiting()) return@guarded
                val open = _making.value ?: return@guarded
                when (result) {
                    is ReviewResult.Proposed ->
                        _making.value = open.copy(reviewing = open.reviewing.answered(result.review))
                    // Arrived, and nothing in it could be used: said as that, not as a failure.
                    is ReviewResult.Unusable ->
                        _making.value = open.copy(reviewing = open.reviewing.unusable(result.review))
                    is ReviewResult.Failed -> {
                        _making.value = open.copy(reviewing = open.reviewing.failed())
                        _refusal.value = ProposalWording.failure(result.failure)
                    }
                }
            } finally {
                // Thrown: the button must not be left reading Reviewing….
                if (stillWaiting()) {
                    thrownHere = true
                    _making.value = _making.value?.let { it.copy(reviewing = it.reviewing.failed()) }
                }
            }
        }
    }

    /** **Use these** in *Make a food*: the group's suggested figures into its boxes, accepted. */
    fun acceptNewFoodGroup(group: FactGroup) {
        val making = _making.value ?: return
        val (form, reviewing) = making.reviewing.accept(group, making.form) ?: return
        _making.value = making.copy(form = form, reviewing = reviewing)
    }

    /** **Use all** in *Make a food*. */
    fun acceptAllForNewFood() {
        val making = _making.value ?: return
        val (form, reviewing) = making.reviewing.acceptAll(making.form)
        _making.value = making.copy(form = form, reviewing = reviewing)
    }

    /** **Dismiss** in *Make a food*: what is left goes; what he accepted stays accepted. */
    fun dismissNewFoodReview() {
        _making.value = _making.value?.let { it.copy(reviewing = it.reviewing.dismissed()) }
    }

    /**
     * Make a food that is not in his list, without leaving the meal.
     *
     * The same one door everything else goes through, so a food made mid-salad is indistinguishable
     * afterwards from one made by eating it — and the identity rule catches it if the thing he is
     * making already exists under another spelling.
     *
     * A group accepted from a review goes as an estimate, or weaker (D54 §5). If the name is one he already
     * has, those estimates are only offered to it: he never saw that food's figures here, so an
     * estimate does not replace one it holds better, and D45's line says only what was replaced.
     */
    fun createFood() {
        val making = _making.value ?: return
        if (making.errors.isNotEmpty()) {
            _making.value = making.copy(showErrors = true)
            return
        }
        val form = making.form
        val facts = form.toFacts(now(), estimated = making.reviewing.accepted) ?: return
        // One transaction, and nothing read after it: a failure made no food.
        act({ ActionRefused.NOTHING_CHANGED }) {
            val made = foods.findOrCreate(
                name = form.name,
                brand = form.brand.takeIf { it.isNotBlank() },
                facts = facts,
            )
            _making.value = null
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
     *
     * [onDeleted] runs once it has gone — which is when the screen may leave. Leaving on the tap
     * would take a failure off the screen before it could be said.
     */
    fun delete(onDeleted: () -> Unit = {}) {
        val id = _mealId.value.takeIf { it != 0L } ?: return
        act({ ActionRefused.NOTHING_CHANGED }) {
            meals.delete(id)
            _mealId.value = 0L
            _meal.value = null
            onDeleted()
        }
    }

    /** Take down whichever sentence is in the refusal slot — a refusal, or an action that failed. */
    fun dismissRefusal() {
        _refusal.value = null
        _failed.value = null
    }

    /**
     * One write, then the meal read back — under the guard, and saying which of the two failed.
     *
     * The write is one transaction, so a failure there changed nothing. A failure reading back
     * comes after a write that happened, so "nothing was changed" would be untrue; what did not
     * happen is the meal being opened again, and that is what it says.
     */
    private fun writeThenReload(
        write: suspend () -> Unit,
        afterWrite: () -> Unit = {},
        reloadIf: () -> Boolean = { true },
    ) {
        var written = false
        act({ if (written) ActionRefused.COULD_NOT_OPEN else ActionRefused.NOTHING_CHANGED }) {
            write()
            written = true
            afterWrite()
            if (reloadIf()) reload()
        }
    }

    /**
     * Run one action under the guard. The last failure is let go of as the next action starts, and
     * a failure takes the refusal's place: one sentence at a time, the latest. [how] is asked only
     * once the action has failed, so it can say how far it got.
     */
    private fun act(how: () -> ActionRefused, block: suspend () -> Unit) {
        _failed.value = null
        guarded(problems, onRefused = {
            _refusal.value = null
            _failed.value = how()
        }) { block() }
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

    /** The five things that are not the meal itself, as one value, because `combine` takes five. */
    private data class Aside(
        val typedName: String,
        val making: MakingFood?,
        val refusal: String?,
        val pending: List<Pending>,
        val failed: ActionRefused?,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
