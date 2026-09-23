package com.metaself.app.ui.screen.foods

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.EditRefused
import com.metaself.app.data.food.EditResult
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.time.Now
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.FoodSearch
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.food.FoodWording
import com.metaself.app.ui.guarded
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Maintaining the list of foods.
 *
 * Renaming, correcting, deleting, hiding and joining two duplicates into one — in one place, because
 * they are one job: the list has a duplicate in it, or a wrong number, or a name he has changed his
 * mind about, and he is sitting down to put it right.
 *
 * **Nothing here can change what a past day was worth.** Correcting a food fixes the food from now
 * on; the days already logged at the old number stay at the old number. Renaming is the
 * one thing that does reach backwards, and it only moves labels: every day that food was eaten shows
 * the new name, and not one stored number moves for it.
 *
 * **Nothing whole goes, and no two foods become one, without being asked first** (D36). Neither can
 * be put back, so a delete asks "Delete it?" and a join asks which food stays — on both ways into a
 * join — and only the answer does it.
 *
 * **Nothing here takes the app down.** Every action goes through [guarded]; one that throws puts
 * [FoodsUiState.failed] in the refusal slot and is written to the problem log. Each write below is
 * one transaction, so the sentence can say nothing was changed; the reads that open a question or
 * an editor say that it could not be opened.
 */
@HiltViewModel
class FoodsViewModel @Inject constructor(
    private val foods: FoodRepository,
    private val now: Now,
    private val problems: ProblemLog,
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _looking = MutableStateFlow(Looking())
    private val _editing = MutableStateFlow<Editing?>(null)
    private val _merging = MutableStateFlow<Merging?>(null)
    private val _refusal = MutableStateFlow<String?>(null)
    private val _chosen = MutableStateFlow<Set<Long>>(emptySet())
    private val _deleting = MutableStateFlow<Deleting?>(null)
    private val _failed = MutableStateFlow<ActionRefused?>(null)

    val state: StateFlow<FoodsUiState> = combine(
        // Every food, hidden ones included: this is the screen where hiding is undone, and a food
        // that cannot be found here cannot be brought back.
        foods.observeAll(),
        foods.observeOnlyAPortionCount(),
        _looking,
        _editing,
        combine(_merging, _refusal, _chosen, _deleting, _failed) { merging, refusal, chosen, deleting,
            failed ->
            Aside(merging, refusal, chosen, deleting, failed)
        },
    ) { all, portionCount, looking, editing, aside ->
        FoodsUiState(
            query = looking.query,
            onlyPortions = looking.onlyPortions,
            showHidden = looking.showHidden,
            foods = visible(all, looking),
            onlyAPortionCount = portionCount,
            // Counted from every food rather than from what is on screen: this is what tells an
            // empty list apart from a list emptied by hiding, and the hidden ones are by definition
            // not in the second.
            hiddenCount = all.count { it.hidden },
            editing = editing,
            merging = aside.merging,
            refusal = aside.refusal,
            chosen = aside.chosen,
            deleting = aside.deleting,
            failed = aside.failed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = FoodsUiState(),
    )

    init {
        // Opened from "Give this a portion" on the logging screen, for one food: its editor is
        // opened, and the list searched down to it, so the editor is on screen rather than below
        // however many foods come before it. Read before the first frame for the reason the meal
        // builder reads its chosen foods then. A food that has gone meanwhile opens nothing.
        //
        // Once only: the route's arguments outlive the process, so without the mark a manager
        // recreated after the system ended the app would reopen an editor he had closed.
        val openFor = savedState.get<String>("food")?.toLongOrNull()
            ?.takeUnless { savedState.get<Boolean>(FOOD_OPENED) == true }
        savedState[FOOD_OPENED] = true
        openFor?.let { foodId ->
            act(ActionRefused.COULD_NOT_OPEN) {
                val food = foods.byId(foodId) ?: return@act
                _looking.value = _looking.value.copy(query = food.name)
                _editing.value = Editing(foodId = food.id, form = FoodForm.of(food))
            }
        }
    }

    private fun visible(all: List<Food>, looking: Looking): List<Food> {
        val shown = all.filter { looking.showHidden || !it.hidden }
        val filtered = if (looking.onlyPortions) shown.filter { it.facts.onlyAPortion } else shown
        return FoodSearch.matching(filtered, looking.query)
    }

    /**
     * Searching, and the two filters below, close an open food but leave a join alone, for the
     * reason [clearChoosing] gives about what is chosen: while a join waits for its duplicate, the
     * search is how he finds it, and Show hidden is the only way to reach a hidden one. Looking that
     * ended the join would cancel the one act that cannot be undone, silently, the moment it was used.
     */
    fun search(query: String) {
        _looking.value = _looking.value.copy(query = query)
        stopEditing()
    }

    /** The foods the conversion could say least about, so he can go through them in one sitting. */
    fun showOnlyPortions(only: Boolean) {
        _looking.value = _looking.value.copy(onlyPortions = only)
        stopEditing()
    }

    fun showHidden(show: Boolean) {
        _looking.value = _looking.value.copy(showHidden = show)
        stopEditing()
    }

    fun edit(foodId: Long) {
        val food = state.value.foods.firstOrNull { it.id == foodId } ?: return
        dismissRefusal()
        _merging.value = null
        _deleting.value = null
        _editing.value = Editing(foodId = food.id, form = FoodForm.of(food))
    }

    /**
     * He is typing. Any answer about deleting is let go of here: he has gone back to editing, and a
     * refusal left standing directly above Save reads, after his next Save, as the reason that Save
     * was refused.
     */
    fun setForm(form: FoodForm) {
        _editing.value = _editing.value?.copy(form = form)
        _deleting.value = null
    }

    fun cancelEditing() {
        closeEditor()
    }

    /**
     * Save what he has typed.
     *
     * The name, the brand and the numbers are three separate things that can each be refused for
     * their own reason, but they are saved as one change ([FoodRepository.saveForm]): the first
     * refusal undoes the rest, so renaming onto a name another food holds cannot half-apply, leaving
     * the numbers changed and the name not — and a Save that throws has changed nothing either.
     *
     * An earlier refusal to delete is let go of first, for the reason [setForm] lets go of it: left
     * above Save, it would read as this Save's refusal, beside this Save's own answer.
     */
    fun save() {
        val editing = _editing.value ?: return
        _deleting.value = null
        if (editing.errors.isNotEmpty()) {
            _editing.value = editing.copy(showErrors = true)
            return
        }
        act(ActionRefused.NOTHING_CHANGED) {
            val form = editing.form
            val facts = form.toFacts(now()) ?: return@act
            val brand = form.brand.takeIf { it.isNotBlank() }
            when (val saved = foods.saveForm(editing.foodId, form.name, brand, facts)) {
                is EditResult.Refused -> refuse(saved.why)
                EditResult.Done -> closeEditor()
            }
        }
    }

    /** Out of every picker, with its history still attached and its name still shown on every day. */
    fun hide(foodId: Long) {
        act(ActionRefused.NOTHING_CHANGED) {
            foods.hide(foodId)
            closeEditor()
        }
    }

    fun unhide(foodId: Long) {
        act(ActionRefused.NOTHING_CHANGED) { foods.unhide(foodId) }
    }

    /**
     * He pressed Delete: ask first, or say why it cannot go (D36).
     *
     * **The refusal is checked before anything is asked.** A food a saved meal uses cannot be
     * deleted, and asking "Delete it?" only to refuse the answer would be a question with no real
     * answer. So the meals are read first, and a used food gets [Deleting.Refused] instead of the
     * question — drawn in its editor, where he pressed Delete, never in the screen-wide refusal at
     * the top of the list, which is off screen from the foot of an open editor.
     *
     * **An answer that arrives after he has moved on is dropped.** This resolves after two reads; if
     * he has opened another food, searched or started a join meanwhile, landing it would put a
     * question back onto a screen he has left.
     */
    fun askToDelete(foodId: Long) {
        dismissRefusal()
        act(ActionRefused.COULD_NOT_OPEN) {
            val food = foods.byId(foodId) ?: return@act
            val using = foods.savedMealsUsing(foodId)
            if (_editing.value?.foodId != foodId) return@act
            _deleting.value = if (using.isEmpty()) {
                Deleting.Asking(food)
            } else {
                Deleting.Refused(food, FoodWording.refusal(EditRefused.UsedBySavedMeals(using)))
            }
        }
    }

    /**
     * He answered Delete: delete it outright.
     *
     * Never deletes a day's food: every row it was on keeps its own name, portion, numbers, source
     * and confidence and simply stops pointing anywhere. What it does lose is the labels — a deleted
     * food's days fall back to whatever was typed on the day — which is why hiding is usually the
     * better answer for a food with history, and why this is asked first.
     *
     * The question is let go of at once, before the delete lands, so a second press of Delete or a
     * late Keep it has nothing left to answer: once he has said Delete, the food is going. A refusal
     * that only arises here — a meal began using the food after he was asked — is shown where the
     * early one would have been. Either answer is dropped if he has moved to another food meanwhile:
     * the food still goes, because he did say Delete, but another food's open editor is not closed by
     * it.
     */
    fun confirmDeleting() {
        val asking = _deleting.value as? Deleting.Asking ?: return
        _deleting.value = null
        act(ActionRefused.NOTHING_CHANGED) {
            val result = foods.delete(asking.food.id)
            if (_editing.value?.foodId != asking.food.id) return@act
            when (result) {
                is EditResult.Refused ->
                    _deleting.value = Deleting.Refused(asking.food, FoodWording.refusal(result.why))
                EditResult.Done -> closeEditor()
            }
        }
    }

    /** He answered Keep it. Nothing else moves: the editor stays open, with what he typed in it. */
    fun cancelDeleting() {
        _deleting.value = null
    }

    /** Begin joining this food to a duplicate. This is the one that survives. */
    fun beginMerging(foodId: Long) {
        val food = state.value.foods.firstOrNull { it.id == foodId } ?: return
        _editing.value = null
        _deleting.value = null
        dismissRefusal()
        _merging.value = Merging(keeping = food)
    }

    /**
     * Back out of a join — while picking, or in answer to the question.
     *
     * Lets go of the choice with it once the pair is settled: when he ticked the two, the choice
     * existed to ask this question, and leaving it ticked after he has said no leaves the list in a
     * mode he did not ask to stay in. When the pair was settled by picking from a food's own editor
     * nothing is ticked — the editor cannot be open while choosing — so the same clear is harmless
     * there, and one rule serves both ways in.
     */
    fun cancelMerging() {
        if (_merging.value?.bothChosen == true) _chosen.value = emptySet()
        _merging.value = null
    }

    /**
     * He has picked the duplicate of the food he opened. This settles the pair and asks; nothing is
     * joined here.
     *
     * A join cannot be undone, and ticking two foods already asks before it joins them. Picking from
     * a food's own editor is the same irreversible act, so it now asks the same question in the same
     * words, and [confirmJoining] answers for both ways in (D36).
     *
     * **A pick whose read lands after he has backed out is dropped.** The picked food is looked up
     * first; if meanwhile he pressed Not now, held a row or opened a food, the join he was in has
     * ended, and writing the pair now would bring back a question he had walked away from.
     * Only the same join, still waiting for its pick, takes it.
     */
    fun mergeInto(loserId: Long) {
        val keeping = _merging.value?.keeping ?: return
        if (keeping.id == loserId) return
        act(ActionRefused.COULD_NOT_OPEN) {
            // Gone only in a race — the row he tapped comes from the observed list and leaves it with
            // the food — and the join before D36 said nothing here either: there is nothing to join.
            val losing = foods.byId(loserId) ?: return@act
            if (_merging.value != Merging(keeping)) return@act
            _merging.value = Merging(keeping, losing)
        }
    }

    // --- Choosing several foods at once --------------------------------------------------------

    /**
     * Join the two foods he has ticked in the list.
     *
     * **Both of them, by id.** Looked up in the repository rather than in the list on screen,
     * because what is chosen deliberately outlives a search — he may have ticked the English yoghurt
     * under one search and the Hebrew one under another, and the first is then not on the list at
     * all. Resolved from the list, that tap did nothing and threw the choice away as it went.
     *
     * **The survivor is named before anything happens**, which is the existing merge flow's rule and
     * the reason it has one: a merge cannot be undone. The older of the two survives — the one that
     * has been in the list longer is the one his history is most likely to hang off — and the other
     * one's names become its aliases.
     *
     * Nothing is merged here. This only asks; [confirmJoining] answers, and until one or the other
     * happens the choice stays exactly as he made it.
     */
    fun joinChosen() {
        // Merging is pairwise and stays pairwise, so this is a no-op at any other size — and the
        // screen only offers it at two.
        val both = _chosen.value.sorted().takeIf { it.size == 2 } ?: return
        act(ActionRefused.COULD_NOT_OPEN) {
            val keeping = foods.byId(both[0]) ?: return@act
            val losing = foods.byId(both[1]) ?: return@act
            _editing.value = null
            _deleting.value = null
            dismissRefusal()
            _merging.value = Merging(keeping = keeping, losing = losing)
        }
    }

    /**
     * Join the settled pair, now that he has seen which one survives.
     *
     * The one place a join happens, for both ways in — ticked in the list or picked from a food's
     * own editor — so there is one question and one answer to it, and no second copy to drift.
     * The picked one's names become aliases of the survivor, which is the step that makes this worth
     * having: without it, joining the Hebrew yoghurt to the English one today means there are two
     * again the next time it is logged in Hebrew.
     */
    fun confirmJoining() {
        val merging = _merging.value ?: return
        val losing = merging.losing ?: return
        if (merging.keeping.id == losing.id) return
        act(ActionRefused.NOTHING_CHANGED) {
            when (val result = foods.merge(winnerId = merging.keeping.id, loserId = losing.id)) {
                // Left standing, choice and all, so the refusal names something he can still act on.
                is EditResult.Refused -> refuse(result.why)
                EditResult.Done -> {
                    _merging.value = null
                    _chosen.value = emptySet()
                }
            }
        }
    }

    /**
     * Holding a food starts choosing, and chooses that one.
     *
     * Holding rather than tapping, because a tap already means "open this to fix it": choosing that
     * began on a tap would turn every attempt to correct a wrong number into the start of a meal.
     * What is chosen deliberately outlives a search — see [clearChoosing].
     */
    fun beginChoosing(foodId: Long) {
        _editing.value = null
        _deleting.value = null
        _merging.value = null
        _chosen.value = _chosen.value + foodId
    }

    /** Tapping while choosing adds a food or takes it out again; the last one out ends choosing. */
    fun toggleChosen(foodId: Long) {
        val chosen = _chosen.value
        _chosen.value = if (foodId in chosen) chosen - foodId else chosen + foodId
    }

    /**
     * End choosing outright.
     *
     * The only thing that does. Searching, filtering and hiding all leave the choice alone on
     * purpose: he may be collecting the parts of a salad from three different searches, and a
     * search that emptied the choice would make that impossible, and would do it silently.
     */
    fun clearChoosing() {
        _chosen.value = emptySet()
    }

    /** Take down whichever sentence is in the refusal slot — a refusal, or an action that failed. */
    fun dismissRefusal() {
        _refusal.value = null
        _failed.value = null
    }

    private fun refuse(why: EditRefused) {
        _failed.value = null
        _refusal.value = FoodWording.refusal(why)
    }

    /**
     * Run an action under the guard. If it throws, [how] goes in the refusal slot in place of
     * whatever was there — one sentence at a time, the latest.
     */
    private fun act(how: ActionRefused, block: suspend () -> Unit) =
        guarded(problems, onRefused = {
            _refusal.value = null
            _failed.value = how
        }) { block() }

    private fun closeEditor() {
        stopEditing()
        _merging.value = null
    }

    /** Close an open food and any question about deleting it, and nothing else. */
    private fun stopEditing() {
        _editing.value = null
        _deleting.value = null
    }

    /** The five things that are not about looking, as one value, because `combine` takes five. */
    private data class Aside(
        val merging: Merging?,
        val refusal: String?,
        val chosen: Set<Long>,
        val deleting: Deleting?,
        val failed: ActionRefused?,
    )

    /** What is being looked for, as one value, so five things can be combined rather than seven. */
    private data class Looking(
        val query: String = "",
        val onlyPortions: Boolean = false,
        val showHidden: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** Saved once the route's food has been acted on, so a recreation does not act on it again. */
        const val FOOD_OPENED = "foodOpened"
    }
}
