package com.metaself.app.ui.screen.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.EditRefused
import com.metaself.app.data.food.EditResult
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.data.time.Now
import com.metaself.app.domain.ai.FoodReviewer
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodField
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.food.FoodWording
import com.metaself.app.ui.guarded
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * One food's own page (D55): correcting it, reviewing its figures, hiding it, deleting it, and
 * starting a join from it.
 *
 * **Nothing here can change what a past day was worth.** Correcting a food fixes the food from now
 * on; the days already logged at the old number stay at the old number. Renaming is the one thing
 * that does reach backwards, and it only moves labels.
 *
 * **Nothing is saved but by Save** (§2). Leaving the page — Leave it alone, the back arrow, the
 * system Back — discards what was typed, any review and any applied marks, and so does the process
 * ending (§8): the boxes and the review's acceptance are one thing, and keeping the boxes without it
 * would save a model's figure as one he typed (D4).
 *
 * **The page never navigates.** When it is finished it sets [FoodPageUiState.closing], once, and
 * the nav host leaves it and calls [closed]. Once a reason to close is set no second one is: a
 * delete that lands after he pressed Join does not also close the page as deleted (§7).
 *
 * **The food is watched by id.** Not stored when the page opens, or gone while it is open, the page
 * closes as [Closing.Gone] rather than draw a form for nothing and save into it (§7).
 *
 * **Nothing here takes the app down.** Every action goes through [guarded]; one that throws puts
 * [FoodPageUiState.failed] in the refusal slot and is written to the problem log. Each write is one
 * transaction, so the sentence can say nothing was changed; the reads that open a question say that
 * it could not be opened.
 */
@HiltViewModel
class FoodPageViewModel @Inject constructor(
    private val foods: FoodRepository,
    private val now: Now,
    private val problems: ProblemLog,
    private val reviewer: FoodReviewer,
    savedState: SavedStateHandle,
) : ViewModel() {

    /** The route's food. A page of no particular food is not a thing that can be drawn (§2). */
    private val foodId: Long = requireNotNull(savedState.get<Long>(FOOD_ID)) {
        "a food's page is opened for one food"
    }

    private val _editing = MutableStateFlow<Editing?>(null)
    private val _deleting = MutableStateFlow<Deleting?>(null)
    private val _refusal = MutableStateFlow<String?>(null)
    private val _failed = MutableStateFlow<ActionRefused?>(null)
    private val _closing = MutableStateFlow<Closing?>(null)

    /** True once the form has been filled from the stored food, so it is never filled again. */
    private var opened = false

    /** True once a reason to close has been set — kept after [closed], so there is only ever one. */
    private var finished = false

    /** How many reviews have been asked for, so only the latest one's answer is ever shown. */
    private var reviewsAsked = 0

    private val food = foods.observeAll()
        // Every food, hidden ones included: a hidden food's page is where it is shown again.
        .map { all -> all.firstOrNull { it.id == foodId } }
        .distinctUntilChanged()
        .onEach(::arrived)

    val state: StateFlow<FoodPageUiState> = combine(
        food,
        foods.observeUse(foodId),
        _editing,
        _deleting,
        combine(_refusal, _failed, _closing) { refusal, failed, closing -> Aside(refusal, failed, closing) },
    ) { food, use, editing, deleting, aside ->
        FoodPageUiState(
            food = food,
            use = use,
            editing = editing,
            deleting = deleting,
            refusal = aside.refusal,
            failed = aside.failed,
            closing = aside.closing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = FoodPageUiState(),
    )

    /**
     * The food as stored, each time it changes. The first time it is there, the boxes are filled
     * from it — once: what he types afterwards is his, and a later change to the stored food (a
     * touch, a join into it) must not retype it. The first time it is not there, the page closes.
     */
    private fun arrived(food: Food?) {
        when {
            food == null -> close(Closing.Gone)
            !opened -> {
                opened = true
                _editing.value = Editing(foodId = food.id, form = FoodForm.of(food))
            }
        }
    }

    /**
     * He is typing. Any answer about deleting is let go of here: he has gone back to editing, and a
     * refusal left standing directly above Save reads, after his next Save, as the reason that Save
     * was refused.
     */
    fun setForm(form: FoodForm) {
        _editing.value = _editing.value?.let { editing ->
            // Typing in a group withdraws its suggestion, arrived or still out (D54 §4).
            editing.copy(form = form, reviewing = editing.reviewing.typed(editing.form, form))
        }
        _deleting.value = null
    }

    /**
     * **Review the figures** (D54): this one food, as the form stands, is sent to the model — its
     * stored facts tell each group's origin, and nothing else of it is within reach of the request.
     *
     * Offered only for a name the form would take, and once at a time. **Nothing is written** — not
     * to the boxes, not to the food — until he accepts a group and saves. A failure is said under the
     * button, in the estimator's own words, and leaves the form as it was (D8, D54 §9.4). A review
     * writes nothing, so one that throws says it could not open.
     *
     * **An answer that lands after the page began closing is dropped** (§8): it has no page to land
     * on. Each request is numbered, and only the latest one, for a page still waiting on it, is
     * shown. That holds for one that throws too.
     */
    fun review() {
        val editing = _editing.value ?: return
        if (editing.reviewing.asking || FoodField.NAME in editing.errors) return
        dismissRefusal()
        val asked = ++reviewsAsked
        _editing.value = editing.copy(reviewing = editing.reviewing.asked())
        fun stillWaiting() = asked == reviewsAsked && !finished &&
            _editing.value?.reviewing?.asking == true
        // A throw is logged by the guard and said under the button by `finally` (D54 §9.4).
        guarded(problems, onRefused = {}) {
            try {
                val stored = foods.byId(editing.foodId) ?: return@guarded
                // The form as it stood when he pressed the button.
                val request = ReviewRequest.of(
                    ReviewProcess.EXISTING_FOOD,
                    editing.form,
                    stored.facts,
                    editing.reviewing.accepted,
                )
                val result = reviewer.review(request)
                if (!stillWaiting()) return@guarded
                val open = _editing.value ?: return@guarded
                when (result) {
                    is ReviewResult.Proposed ->
                        _editing.value = open.copy(
                            reviewing = open.reviewing.answered(result.review, result.raw),
                        )
                    // Arrived, and nothing in it could be used: said as that, not as a failure.
                    is ReviewResult.Unusable ->
                        _editing.value = open.copy(
                            reviewing = open.reviewing.unusable(result.review, result.raw),
                        )
                    // Said under the button it answers, not in the slot above Save (§9.4).
                    is ReviewResult.Failed ->
                        _editing.value = open.copy(
                            reviewing = open.reviewing.failed(result.failure, result.raw),
                        )
                }
            } finally {
                // Thrown, or the food gone: the button must not be left reading Reviewing….
                if (stillWaiting()) {
                    _editing.value = _editing.value?.let { it.copy(reviewing = it.reviewing.failed(null)) }
                }
            }
        }
    }

    /**
     * **Apply these changes** (D54 §11): every suggested group into its boxes, accepted as an
     * estimate, and the boxes it changed marked until he saves, undoes or types in them.
     */
    fun applyReview() {
        val editing = _editing.value ?: return
        val (form, reviewing) = editing.reviewing.apply(editing.form)
        _editing.value = editing.copy(form = form, reviewing = reviewing)
    }

    /** **Undo**: the boxes and the review go back to how they stood before Apply these changes. */
    fun undoReview() {
        val editing = _editing.value ?: return
        val (form, reviewing) = editing.reviewing.undo(editing.form) ?: return
        _editing.value = editing.copy(form = form, reviewing = reviewing)
    }

    /** **Dismiss**, or **Keep mine**: what is left of the review goes; what he accepted stays accepted. */
    fun dismissReview() {
        _editing.value = _editing.value?.let { it.copy(reviewing = it.reviewing.dismissed()) }
    }

    /**
     * Save what he has typed, and close the page (§2).
     *
     * The name, the brand and the numbers are three separate things that can each be refused for
     * their own reason, but they are saved as one change ([FoodRepository.saveForm]): the first
     * refusal undoes the rest, so renaming onto a name another food holds cannot half-apply — and a
     * Save that throws has changed nothing either. A refused Save leaves the page, its boxes and its
     * marks exactly as they were.
     *
     * An earlier refusal to delete is let go of first, for the reason [setForm] lets go of it: left
     * above Save, it would read as this Save's refusal, beside this Save's own answer.
     */
    fun save() {
        val editing = _editing.value ?: return
        if (finished) return
        _deleting.value = null
        if (editing.errors.isNotEmpty()) {
            _editing.value = editing.copy(showErrors = true)
            return
        }
        act(ActionRefused.NOTHING_CHANGED) {
            val form = editing.form
            // A group accepted from a review goes as an estimate, or weaker, even if he then changed
            // a figure in it (D54 §5); the repository leaves any group whose figures did not change alone.
            // A box still showing a stored figure as it opened, rounded, saves the stored figure,
            // so an untouched group is found unchanged and kept, source and all (D54 §8.5).
            val stored = foods.byId(editing.foodId)?.facts
            val facts = form.toFacts(now(), estimated = editing.reviewing.accepted, stored = stored)
                ?: return@act
            val brand = form.brand.takeIf { it.isNotBlank() }
            when (val saved = foods.saveForm(editing.foodId, form.name, brand, facts)) {
                is EditResult.Refused -> refuse(saved.why)
                EditResult.Done -> close(Closing.Saved)
            }
        }
    }

    /**
     * Out of every picker, with its history still attached and its name still shown on every day;
     * and the page closes, so the list can say it happened and offer it back (§6). The name the list
     * says is the stored one, read here, not what is half-typed in the box.
     */
    fun hide() {
        if (finished) return
        act(ActionRefused.NOTHING_CHANGED) {
            val food = foods.byId(foodId) ?: return@act
            foods.hide(foodId)
            close(Closing.Hidden(foodId, food.name))
        }
    }

    /**
     * **Show again**, on a hidden food's page. The page stays: bringing a food back is usually the
     * first step of doing something with it (§6).
     */
    fun unhide() {
        if (finished) return
        act(ActionRefused.NOTHING_CHANGED) { foods.unhide(foodId) }
    }

    /**
     * He pressed Delete: ask first, or say why it cannot go (D36).
     *
     * **The refusal is checked before anything is asked.** A food a saved meal uses cannot be
     * deleted, and asking "Delete it?" only to refuse the answer would be a question with no real
     * answer. So the meals are read first, and a used food gets [Deleting.Refused] instead of the
     * question — drawn directly above the buttons, where he pressed Delete.
     *
     * **An answer that arrives after the page began closing is dropped**: it resolves after two
     * reads, and a question landed on a page that is leaving would answer nothing.
     */
    fun askToDelete() {
        dismissRefusal()
        act(ActionRefused.COULD_NOT_OPEN) {
            val food = foods.byId(foodId) ?: return@act
            val using = foods.savedMealsUsing(foodId)
            if (finished) return@act
            _deleting.value = if (using.isEmpty()) {
                Deleting.Asking(food)
            } else {
                Deleting.Refused(food, FoodWording.refusal(EditRefused.UsedBySavedMeals(using)))
            }
        }
    }

    /**
     * He answered Delete: delete it outright, and close the page (§6).
     *
     * Never deletes a day's food: every row it was on keeps its own name, portion, numbers, source
     * and confidence and simply stops pointing anywhere. What it does lose is the labels — a deleted
     * food's days fall back to whatever was typed on the day — which is why hiding is usually the
     * better answer for a food with history, and why this is asked first.
     *
     * The question is let go of at once, before the delete lands, so a second press of Delete or a
     * late Keep it has nothing left to answer. A refusal that only arises here — a meal began using
     * the food after he was asked — is shown where the early one would have been, and the page stays.
     * If the page began closing meanwhile the food still goes, because he did say Delete, but the
     * page is not closed a second time.
     */
    fun confirmDeleting() {
        val asking = _deleting.value as? Deleting.Asking ?: return
        _deleting.value = null
        act(ActionRefused.NOTHING_CHANGED) {
            val result = foods.delete(asking.food.id)
            if (finished) return@act
            when (result) {
                is EditResult.Refused ->
                    _deleting.value = Deleting.Refused(asking.food, FoodWording.refusal(result.why))
                EditResult.Done -> close(Closing.Deleted)
            }
        }
    }

    /** He answered Keep it. Nothing else moves: the page stays, with what he typed in it. */
    fun cancelDeleting() {
        _deleting.value = null
    }

    /**
     * **Join with a duplicate**: the page closes and the list picks the duplicate (§5), because
     * choosing it is the job the list is for — its search, Show hidden, and every row's brand and
     * figures side by side. Nothing is joined here and nothing typed is saved: the join question
     * says the food kept keeps its numbers, the stored ones.
     */
    fun beginJoining() {
        close(Closing.Join(foodId))
    }

    /** Take down whichever sentence is in the refusal slot — a refusal, or an action that failed. */
    fun dismissRefusal() {
        _refusal.value = null
        _failed.value = null
    }

    /** The nav host has left the page. The reason goes; no second reason can be set after it. */
    fun closed() {
        _closing.value = null
    }

    private fun close(why: Closing) {
        if (finished) return
        finished = true
        _deleting.value = null
        _closing.value = why
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

    /** The three things that are not the food or the form, as one value, because `combine` takes five. */
    private data class Aside(
        val refusal: String?,
        val failed: ActionRefused?,
        val closing: Closing?,
    )

    companion object {
        /** The route's argument: which food this page is. */
        const val FOOD_ID = "foodId"

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
