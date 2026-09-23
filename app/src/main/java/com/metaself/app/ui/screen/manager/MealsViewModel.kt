package com.metaself.app.ui.screen.manager

import androidx.lifecycle.ViewModel
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.SavedMealRepository
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.guarded
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The list of meals the owner has built.
 *
 * **The order is the repository's.** Re-sorting here would make two screens disagree about what
 * "first" means, and neither of them would be wrong.
 *
 * **Only the meals the rest of the app is offered.** `observeOffered()` leaves out a meal put out of
 * sight, and there is deliberately no second query for the hidden ones: hiding a meal is not offered
 * anywhere in the app yet, so a "show hidden" toggle here would be a control with nothing to show.
 * When hiding arrives, this is where it is undone from.
 *
 * **A list that cannot be read says so** rather than taking the app down: the one thing this view
 * model starts is reading the list, so a failure there is [ActionRefused.COULD_NOT_OPEN], written to
 * the problem log. The list stays as it was last read, until he dismisses the sentence and the read
 * starts again.
 */
@HiltViewModel
class MealsViewModel @Inject constructor(
    private val savedMeals: SavedMealRepository,
    private val problems: ProblemLog,
) : ViewModel() {

    private val _state = MutableStateFlow(MealsUiState())
    val state: StateFlow<MealsUiState> = _state.asStateFlow()

    init {
        follow()
    }

    /**
     * He has read the failure; take it down, and start reading the list again.
     *
     * Again, because the read is a stream and a stream that threw has stopped: without this the tab
     * would go on showing the list as last read, however it changed, until the app was reopened.
     */
    fun dismissFailure() {
        val failed = _state.value.failed != null
        _state.value = _state.value.copy(failed = null)
        if (failed) follow()
    }

    private fun follow() {
        guarded(
            problems,
            onRefused = { _state.value = _state.value.copy(failed = ActionRefused.COULD_NOT_OPEN) },
        ) {
            savedMeals.observeOffered().collect { meals ->
                _state.value = MealsUiState(meals = meals)
            }
        }
    }
}
