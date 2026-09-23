package com.metaself.app.ui.screen.propose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.portion.Portions
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import com.metaself.app.ui.propose.ProposalWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Describing a meal, correcting what came back, and accepting it.
 *
 * One call to the model per press. No conversation: the rows ARE the clarification, and they cost
 * nothing and require no reply. "Tell it more" is the exception, and it is the owner's to press.
 *
 * **Nothing here takes the app down.** The estimator reports its own failures as results; anything
 * it throws instead is caught by [guarded], written to the problem log, and leaves the owner where
 * any other failure does — describing, with his words kept. Asking stores nothing, so the sentence
 * says nothing was changed.
 */
@HiltViewModel
class ProposalViewModel @Inject constructor(
    private val estimator: MealEstimator,
    private val problems: ProblemLog,
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _state = MutableStateFlow<ProposalUiState>(ProposalUiState.Describing())
    val state: StateFlow<ProposalUiState> = _state.asStateFlow()

    /**
     * The owner's words, kept so a failure never loses them (D8).
     *
     * Seeded here, at construction, from the words the food search carried in when nothing there
     * matched. It has to be here and not in a later call: this is a plain `var`, not something the
     * screen observes, and the text field reads it once when it is first composed. Filled in
     * afterwards it would change nothing on screen, and the owner would arrive at an empty box
     * having been told his words came with him.
     *
     * Carrying words in is not the same as asking. Nothing here calls the model — the owner presses
     * the button (D8).
     */
    var description: String = savedState.get<String>("text").orEmpty()
        private set

    fun describe(text: String) {
        description = text
        if (text.isBlank()) return
        ask(text, moreDetail = null)
    }

    /**
     * Ask again with a sentence added.
     *
     * This is what handles what a typed amount cannot: the same bowl cooked richer. One extra call,
     * only when the owner decides the first answer was not close enough. It replaces every row.
     */
    fun tellItMore(extra: String) {
        if (description.isBlank() || extra.isBlank()) return
        ask(description, moreDetail = extra)
    }

    private fun ask(text: String, moreDetail: String?) {
        guarded(
            problems,
            onRefused = {
                _state.value = ProposalUiState.Describing(refused = ActionRefused.NOTHING_CHANGED)
            },
        ) {
            _state.value = ProposalUiState.Waiting
            _state.value = when (val result = estimator.estimate(text, moreDetail)) {
                is EstimateResult.Proposed -> ProposalUiState.Proposed(
                    rows = result.proposal.items.map { ProposalRow(it, it.toItemToLog()) },
                    note = result.proposal.note,
                )

                else -> ProposalUiState.Describing(
                    failure = ProposalWording.failure(result),
                    needsKey = result is EstimateResult.NoKey,
                )
            }
        }
    }

    /**
     * How much of it there was, as typed (D53 §1). Only the amount changes: the worth stays what it
     * was, and the total follows.
     */
    fun setAmount(index: Int, text: String) {
        updateRow(index) { row -> row.copy(item = row.item.copy(amountText = text)) }
    }

    /**
     * − and + on a counted row: one piece more or fewer (D53 §6).
     *
     * Only for a piece — a measured unit (grams, millilitres and the rest `Portions.isMass` names)
     * has only its box. Never below one: a step that would go there does nothing, so a typed 0.5 is
     * kept rather than rounded, and nought of something is an item to remove, not a count. A box
     * that holds no number steps from nothing, so + gives 1. The arithmetic is decimal, so 0.1 and
     * one make 1.1 and not 1.1000000000000001.
     */
    fun step(index: Int, by: Int) {
        updateRow(index) { row ->
            val item = row.item
            if (Portions.isMass(item.unit)) return@updateRow row
            val now = item.amountText.trim().replace(',', '.').ifEmpty { "0" }
                .toBigDecimalOrNull() ?: return@updateRow row
            val next = now + by.toBigDecimal()
            if (next < BigDecimal.ONE) return@updateRow row
            row.copy(item = item.copy(amountText = next.stripTrailingZeros().toPlainString()))
        }
    }

    /** *Change* under the worth line: the four boxes open, holding the worth (D53 §6). */
    fun openWorth(index: Int) {
        updateRow(index) { row ->
            if (row.editingWorth != null) return@updateRow row
            WorthBoxes.of(row.item)?.let { row.copy(editingWorth = it) } ?: row
        }
    }

    /**
     * One worth box typed into (D53 §1, §3). Only the worth changes, never the amount. While the
     * four make a worth the row takes it — his, once any figure differs from what the box opened
     * with; while one is blank or refused the row keeps its last worth and cannot be logged.
     * The food it is attached to, if any, stays: typing over his food's worth changes only this
     * entry.
     */
    fun setWorthBox(index: Int, figure: WorthFigure, text: String) {
        updateRow(index) { row ->
            val boxes = row.editingWorth?.with(figure, text) ?: return@updateRow row
            val worth = boxes.worth()
            row.copy(
                item = if (worth == null) row.item else row.item.copy(worth = worth),
                editingWorth = boxes,
            )
        }
    }

    /**
     * The boxes close on what was typed. Not while one is refused: closing would hide the one
     * sentence saying why the row cannot be saved.
     */
    fun closeWorth(index: Int) {
        updateRow(index) { row ->
            if (row.editingWorth?.refused == true) row else row.copy(editingWorth = null)
        }
    }

    /** Remove a row the model invented, or one the owner did not eat. */
    fun remove(index: Int) {
        val current = _state.value as? ProposalUiState.Proposed ?: return
        val kept = current.rows.filterIndexed { at, _ -> at != index }
        _state.value = if (kept.isEmpty()) {
            ProposalUiState.Describing()
        } else {
            current.copy(rows = kept)
        }
    }

    /**
     * What would be logged if the owner accepted it now — nothing at all while any row cannot be
     * logged, so that saving never quietly leaves one of the rows behind (D53 §6).
     */
    fun accepted(): List<FoodItem> {
        val proposed = _state.value as? ProposalUiState.Proposed ?: return emptyList()
        if (proposed.blockedBy != null) return emptyList()
        return proposed.rows.mapNotNull { it.item.toFoodItem() }
    }

    /**
     * He is going to settings to add the key the last answer said was missing (public issue #11).
     *
     * The complaint is taken down now rather than on his return, because nothing here can tell that
     * he saved one; left up, it would contradict settings the moment he had. The words stay — in
     * [description], and in the field, which keeps whatever he typed since — so coming back is one
     * press of "Work it out", and without a key that press simply says so again.
     */
    fun leaveToAddKey() {
        _state.value = ProposalUiState.Describing()
    }

    fun startOver() {
        description = ""
        _state.value = ProposalUiState.Describing()
    }

    private fun updateRow(index: Int, change: (ProposalRow) -> ProposalRow) {
        val current = _state.value as? ProposalUiState.Proposed ?: return
        if (index !in current.rows.indices) return
        _state.value = current.copy(
            rows = current.rows.mapIndexed { at, row -> if (at == index) change(row) else row },
        )
    }
}
