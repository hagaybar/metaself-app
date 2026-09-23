package com.metaself.app.ui.screen.manager

import com.metaself.app.domain.food.SavedMeal
import com.metaself.app.ui.ActionRefused

/**
 * The meals the owner has built, as the manager's second tab shows them.
 *
 * No total and no counting: a meal works out what it is worth from its own parts, and a number kept
 * here as well would be a second answer that could disagree with them.
 */
data class MealsUiState(
    val meals: List<SavedMeal> = emptyList(),
    /** The list could not be read. Said above whatever it last showed, until dismissed. */
    val failed: ActionRefused? = null,
) {

    /**
     * True when he has built none. The tab then says so and offers the builder. Never after a read
     * that failed: an empty list then says nothing about what he has built.
     */
    val nothingBuiltYet: Boolean get() = meals.isEmpty() && failed == null
}
