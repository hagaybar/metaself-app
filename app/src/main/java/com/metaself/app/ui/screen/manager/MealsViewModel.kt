package com.metaself.app.ui.screen.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metaself.app.data.food.SavedMealRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 */
@HiltViewModel
class MealsViewModel @Inject constructor(
    private val savedMeals: SavedMealRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MealsUiState())
    val state: StateFlow<MealsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            savedMeals.observeOffered().collect { meals ->
                _state.value = MealsUiState(meals = meals)
            }
        }
    }
}
