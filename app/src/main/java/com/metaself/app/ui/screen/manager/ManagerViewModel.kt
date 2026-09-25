package com.metaself.app.ui.screen.manager

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which of the manager's two lists is in front.
 *
 * Held here rather than inside the composable for the reason the day's open meals are: state a
 * composable keeps to itself is state no test can put in a known position.
 *
 * Nothing else lives here. The two lists have a view model each and share nothing, which is what
 * keeps them independent — if one tab ever needs to know what the other holds, that is a reason to
 * reconsider the shape rather than to add a third thing they both talk to.
 */
@HiltViewModel
class ManagerViewModel @Inject constructor(
    savedState: SavedStateHandle,
) : ViewModel() {

    /** Opened on the meals list when the route asks, as after keeping a described meal (D58 §5.3). */
    private val _tab = MutableStateFlow(
        if (savedState.get<String>(TAB) == MEALS) ManagerTab.MEALS else ManagerTab.FOODS,
    )
    val tab: StateFlow<ManagerTab> = _tab.asStateFlow()

    fun showTab(tab: ManagerTab) {
        _tab.value = tab
    }

    companion object {
        /** The route argument naming the list in front. */
        const val TAB = "tab"

        /** Its value for the meals list. */
        const val MEALS = "meals"
    }
}
