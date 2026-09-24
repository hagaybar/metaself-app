package com.metaself.app.ui.screen.foods

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.EditRefused
import com.metaself.app.data.food.EditResult
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.domain.food.Food
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
 * Maintaining the list of foods: finding them, choosing several, and joining two duplicates into one
 * (D55 §1).
 *
 * **Everything about one food is its page's** (`FoodPageViewModel`): correcting it, reviewing its
 * figures, hiding it and deleting it. The list keeps what is about more than one food. A page hands
 * the list two things when it closes onto it — a food to join from ([beginJoiningFrom]) and a food it
 * has just hidden ([sayHidden]) — and nothing else passes between them.
 *
 * **No two foods become one without being asked first** (D36). A join cannot be put back, so it asks
 * which food stays — on both ways in — and only the answer does it.
 *
 * **Nothing here takes the app down.** Every action goes through [guarded]; one that throws puts
 * [FoodsUiState.failed] in the refusal slot and is written to the problem log. Each write below is
 * one transaction, so the sentence can say nothing was changed; the reads that open a question say
 * that it could not be opened.
 */
@HiltViewModel
class FoodsViewModel @Inject constructor(
    private val foods: FoodRepository,
    private val problems: ProblemLog,
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _looking = MutableStateFlow(Looking())
    private val _merging = MutableStateFlow<Merging?>(null)
    private val _refusal = MutableStateFlow<String?>(null)
    private val _chosen = MutableStateFlow<Set<Long>>(emptySet())
    private val _failed = MutableStateFlow<ActionRefused?>(null)

    /** The food a page just hid, by id: the line names it as it is called now (§6). */
    private val _hid = MutableStateFlow<Long?>(null)

    val state: StateFlow<FoodsUiState> = combine(
        // Every food, hidden ones included: this is the screen where hiding is undone, and a food
        // that cannot be found here cannot be brought back.
        foods.observeAll(),
        foods.observeOnlyAPortionCount(),
        _looking,
        combine(_merging, _refusal, _chosen, _failed, _hid) { merging, refusal, chosen, failed, hid ->
            Aside(merging, refusal, chosen, failed, hid)
        },
    ) { all, portionCount, looking, aside ->
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
            merging = aside.merging,
            refusal = aside.refusal,
            chosen = aside.chosen,
            failed = aside.failed,
            // Gone meanwhile — joined away, or deleted — and there is nothing to say it about.
            hid = aside.hid?.let { id -> all.firstOrNull { it.id == id } },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = FoodsUiState(),
    )

    init {
        // Opened by route from a page with no list beneath it: "Join with a duplicate" (§5). Spent
        // as it is read, so a list restored after the process ended does not begin again a join he
        // has since finished or walked away from (§8).
        savedState.get<String>(JOIN_FROM)?.let { joinFrom ->
            savedState[JOIN_FROM] = null
            joinFrom.toLongOrNull()?.let(::beginJoiningFrom)
        }
    }

    private fun visible(all: List<Food>, looking: Looking): List<Food> {
        val shown = all.filter { looking.showHidden || !it.hidden }
        val filtered = if (looking.onlyPortions) shown.filter { it.facts.onlyAPortion } else shown
        return FoodSearch.matching(filtered, looking.query)
    }

    /**
     * Searching, and the two filters below, take down the hidden line but leave a join alone, for the
     * reason [clearChoosing] gives about what is chosen: while a join waits for its duplicate, the
     * search is how he finds it, and Show hidden is the only way to reach a hidden one. Looking that
     * ended the join would cancel the one act that cannot be undone, silently, the moment it was used.
     */
    fun search(query: String) {
        _looking.value = _looking.value.copy(query = query)
        _hid.value = null
    }

    /** The foods the conversion could say least about, so he can go through them in one sitting. */
    fun showOnlyPortions(only: Boolean) {
        _looking.value = _looking.value.copy(onlyPortions = only)
        _hid.value = null
    }

    fun showHidden(show: Boolean) {
        _looking.value = _looking.value.copy(showHidden = show)
        _hid.value = null
    }

    /**
     * A food's page is opening from the list (§1). The hidden line goes, as it does on any looking,
     * and so does a join whose question is up: tapping a row while it was asked about has always
     * ended it, and a question left behind a page would be waiting, unexplained, on the way back.
     */
    fun openingAFood() {
        _hid.value = null
        _merging.value = null
        dismissRefusal()
    }

    /**
     * *Join with a duplicate*, pressed on this food's page (§5): the list picks the duplicate. This
     * food is the one that survives.
     *
     * **Looked up by id, not out of the list on screen**: the list comes back with the search and
     * filters he left it with, because the duplicate is probably what that search was for, and they
     * may not hold this food — a hidden one, with Show hidden off, never is. A food gone meanwhile
     * begins nothing and says nothing: there is nothing to join.
     *
     * A join and a choice are not both under way, so the choice is let go of.
     */
    fun beginJoiningFrom(foodId: Long) {
        act(ActionRefused.COULD_NOT_OPEN) {
            val keeping = foods.byId(foodId) ?: return@act
            _chosen.value = emptySet()
            _hid.value = null
            dismissRefusal()
            _merging.value = Merging(keeping = keeping)
        }
    }

    /**
     * A food's page hid [foodId] and closed onto the list (§6). Said in the list's sentence slot with
     * a way back: hiding costs nothing to reverse, and a food that silently leaves a list it was just
     * in reads as deleted.
     */
    fun sayHidden(foodId: Long) {
        _hid.value = foodId
    }

    /** *Show again*, on the hidden line: the food comes back, and the line goes. */
    fun showAgain() {
        val foodId = _hid.value ?: return
        act(ActionRefused.NOTHING_CHANGED) {
            foods.unhide(foodId)
            _hid.value = null
        }
    }

    /** *All right*, on the hidden line: the line goes, and the food stays hidden. */
    fun dismissHidden() {
        _hid.value = null
    }

    /**
     * Back out of a join — while picking, or in answer to the question.
     *
     * Lets go of the choice with it once the pair is settled: when he ticked the two, the choice
     * existed to ask this question, and leaving it ticked after he has said no leaves the list in a
     * mode he did not ask to stay in. When the pair was settled by picking for a food's page nothing
     * is ticked — beginning that join let go of the choice — so the same clear is harmless there, and
     * one rule serves both ways in.
     */
    fun cancelMerging() {
        if (_merging.value?.bothChosen == true) _chosen.value = emptySet()
        _merging.value = null
    }

    /**
     * He has picked the duplicate of the food whose page he joined from. This settles the pair and
     * asks; nothing is joined here.
     *
     * A join cannot be undone, and ticking two foods already asks before it joins them. Picking for
     * a food's page is the same irreversible act, so it asks the same question in the same words,
     * and [confirmJoining] answers for both ways in (D36).
     *
     * **A pick whose read lands after he has backed out is dropped.** The picked food is looked up
     * first; if meanwhile he pressed Not now or held a row, the join he was in has ended, and writing
     * the pair now would bring back a question he had walked away from.
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
            _hid.value = null
            dismissRefusal()
            _merging.value = Merging(keeping = keeping, losing = losing)
        }
    }

    /**
     * Join the settled pair, now that he has seen which one survives.
     *
     * The one place a join happens, for both ways in — ticked in the list or picked for a food's
     * page — so there is one question and one answer to it, and no second copy to drift.
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
     * Holding rather than tapping, because a tap already means "open this food's page": choosing
     * that began on a tap would turn every attempt to correct a wrong number into the start of a
     * meal. What is chosen deliberately outlives a search — see [clearChoosing].
     */
    fun beginChoosing(foodId: Long) {
        _hid.value = null
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

    /** The five things that are not about looking, as one value, because `combine` takes five. */
    private data class Aside(
        val merging: Merging?,
        val refusal: String?,
        val chosen: Set<Long>,
        val failed: ActionRefused?,
        val hid: Long?,
    )

    /** What is being looked for, as one value, so four things can be combined rather than six. */
    private data class Looking(
        val query: String = "",
        val onlyPortions: Boolean = false,
        val showHidden: Boolean = false,
    )

    companion object {
        /**
         * The food a page's *Join with a duplicate* hands the list (§5): a route argument when the
         * list is opened for it, a result left in the list's back stack entry when it is beneath.
         */
        const val JOIN_FROM = "joinFrom"

        /** The food a page hid, left for the list beneath it (§6). */
        const val HIDDEN = "hidden"

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
