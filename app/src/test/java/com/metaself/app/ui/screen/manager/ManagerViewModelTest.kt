package com.metaself.app.ui.screen.manager

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Which of the manager's two lists is in front.
 *
 * Pure state, so JUnit 5 — `org.junit.jupiter.api.Test`, never `org.junit.Test`. The two
 * annotations look identical at the call site and the wrong one produces a test that never runs.
 *
 * The tab is held in a view model rather than inside the composable for the reason the day's open
 * meals are: state a composable keeps to itself is state no test can put in a known position.
 */
class ManagerViewModelTest {

    @Test
    fun `it opens on the foods list`() {
        assertThat(ManagerViewModel(SavedStateHandle()).tab.value).isEqualTo(ManagerTab.FOODS)
    }

    /** After keeping a described meal, he lands on the meals list (D58 §5.3). */
    @Test
    fun `asked by the route, it opens on the meals list`() {
        val model = ManagerViewModel(SavedStateHandle(mapOf(ManagerViewModel.TAB to ManagerViewModel.MEALS)))

        assertThat(model.tab.value).isEqualTo(ManagerTab.MEALS)
    }

    @Test
    fun `the other list is one tap away`() {
        val model = ManagerViewModel(SavedStateHandle())
        model.showTab(ManagerTab.MEALS)
        assertThat(model.tab.value).isEqualTo(ManagerTab.MEALS)
    }
}
