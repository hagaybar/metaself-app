package com.metaself.app.ui.food

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.food.Food

// Shared by the meal builder and the one-day adjuster on Add something: both name the foods their
// search will not offer because the meal already holds them (D41), in the same words.

/**
 * Any number of names as one phrase — "Cucumber", "Cucumber and Olive oil", "Cucumber, Olive oil
 * and Tomato" — in the order given. That is the search's order, not always the order on screen: the
 * foods found by a name come first and those found only by their brand after them (D41), each in
 * the order they are drawn.
 *
 * Folded rather than one resource per count because a meal can hold more foods than any fixed set
 * of resources would cover. The joiners are resources so a translation can reorder them.
 */
@Composable
fun namesTogether(names: List<String>): String {
    if (names.size <= 1) return names.firstOrNull().orEmpty()
    var together = names.first()
    names.drop(1).dropLast(1).forEach { name ->
        together = stringResource(R.string.builder_names_more, together, name)
    }
    return stringResource(R.string.builder_names_last, together, names.last())
}

/**
 * A food as the sentence about it names it: `Milk (Dairyco)` when it has a real brand, else `Milk`.
 *
 * The search finds a food by its brand (D41), and two foods may share a name and differ only in
 * brand — so a bare name could read "Milk and Milk are already in this meal", or name a Milk while
 * offering a different one below. The brand is the one thing that tells them apart on screen. A
 * resource rather than concatenation, so a translation can place it.
 */
@Composable
fun named(food: Food): String = FoodWording.brand(food)
    ?.let { brand -> stringResource(R.string.builder_food_with_brand, food.name, brand) }
    ?: food.name
