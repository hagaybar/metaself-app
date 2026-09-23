package com.metaself.app.ui.screen.foods

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * What "chosen" means in the food list.
 *
 * Pure, so JUnit 5 — `org.junit.jupiter.api.Test`, never `org.junit.Test`. The two annotations look
 * identical at the call site and the wrong one produces a test that silently never runs.
 *
 * The three properties below are derived rather than stored for the reason the manager's other
 * counts are: a flag written down beside the set can disagree with the set, and then two parts of
 * one screen offer different things at the same moment.
 */
class FoodsUiStateTest {

    @Test
    fun `nothing is being chosen until something is`() {
        assertThat(FoodsUiState().choosing).isFalse()
    }

    /** Merging is pairwise and stays pairwise: two is the only size it is offered at. */
    @Test
    fun `two chosen can also be joined into one food`() {
        assertThat(FoodsUiState(chosen = setOf(1L, 2L)).canJoin).isTrue()
    }

    @Test
    fun `three chosen is a meal and not a join`() {
        val state = FoodsUiState(chosen = setOf(1L, 2L, 3L))
        assertThat(state.canJoin).isFalse()
        assertThat(state.canMakeAMeal).isTrue()
    }

    @Test
    fun `one chosen is neither yet`() {
        val state = FoodsUiState(chosen = setOf(1L))
        assertThat(state.canJoin).isFalse()
        assertThat(state.canMakeAMeal).isFalse()
    }

    /**
     * Hiding every food used to take the Show-hidden chip down with the list, leaving no control
     * anywhere that could bring them back. An empty list is only "nothing yet" when there is also
     * nothing hidden behind it.
     */
    @Test
    fun `a list emptied by hiding is not an empty list`() {
        val state = FoodsUiState(hiddenCount = 3)
        assertThat(state.nothingAtAll).isFalse()
        assertThat(state.everythingHidden).isTrue()
    }

    @Test
    fun `with nothing hidden an empty list really is empty`() {
        val state = FoodsUiState()
        assertThat(state.nothingAtAll).isTrue()
        assertThat(state.everythingHidden).isFalse()
    }

    @Test
    fun `showing the hidden ones stops the screen saying they are all hidden`() {
        val state = FoodsUiState(hiddenCount = 3, showHidden = true)
        assertThat(state.everythingHidden).isFalse()
    }
}
