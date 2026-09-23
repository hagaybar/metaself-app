package com.metaself.app.ui.screen.propose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.domain.ai.PortionScale
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.ui.propose.ProposalWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Describing a meal, correcting what came back, and accepting it.
 *
 * One call to the model per press. No conversation: the rows ARE the clarification, and they cost
 * nothing and require no reply. "Tell it more" is the exception, and it is the owner's to press.
 */
@HiltViewModel
class ProposalViewModel @Inject constructor(
    private val estimator: MealEstimator,
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
     * This is what handles what scaling cannot: the same bowl cooked richer. One extra call, only
     * when the owner decides the first answer was not close enough.
     */
    fun tellItMore(extra: String) {
        if (description.isBlank() || extra.isBlank()) return
        ask(description, moreDetail = extra)
    }

    private fun ask(text: String, moreDetail: String?) {
        viewModelScope.launch {
            _state.value = ProposalUiState.Waiting
            _state.value = when (val result = estimator.estimate(text, moreDetail)) {
                is EstimateResult.Proposed -> ProposalUiState.Proposed(
                    rows = result.proposal.items.map { ProposalRow(it, it) },
                    note = result.proposal.note,
                )

                else -> ProposalUiState.Describing(
                    failure = ProposalWording.failure(result),
                    needsKey = result is EstimateResult.NoKey,
                )
            }
        }
    }

    fun scale(index: Int, scale: PortionScale) {
        updateRow(index) { row -> row.copy(current = scale.applyTo(row.asProposed)) }
    }

    fun setCount(index: Int, howMany: Int) {
        updateRow(index) { row ->
            row.copy(current = PortionScale.count(howMany, row.asProposed))
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

    /** What would be logged if the owner accepted it now. */
    fun accepted(): List<FoodItem> =
        (_state.value as? ProposalUiState.Proposed)?.rows?.map { it.current.toFoodItem() }
            ?: emptyList()

    /**
     * He is going to settings to add the key the last answer said was missing (issue #11).
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
