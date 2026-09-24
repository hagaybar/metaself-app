package com.metaself.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.SavedStateHandle
import com.metaself.app.ui.screen.food.Closing
import com.metaself.app.ui.screen.foods.FoodsViewModel

/**
 * Where a food's page goes when it closes (D55 §5, §6), decided apart from the nav controller so it
 * can be tested without one. The nav host acts on it, and so does the walk harness.
 *
 * **The list beneath, when there is one, is told two things**: a food to join from, and a food just
 * hidden. Anything else closes onto whatever is beneath with nothing said — Save, Delete, and a food
 * gone. From *Give this a portion* there is no list beneath: a join replaces the page with the list,
 * picking; a hide says nothing, because *Add something* closes its own question for a hidden food.
 */
sealed interface FoodPageExit {

    /** Go back, leaving [result] for the list beneath first when there is one. */
    data class Back(val result: ListResult? = null) : FoodPageExit

    /** Replace the page with the list, picking a duplicate for [joinFrom]. */
    data class ToList(val joinFrom: Long) : FoodPageExit {
        val route: String get() = Destination.Foods.joiningFrom(joinFrom)
    }

    companion object {
        fun of(closing: Closing, listBelow: Boolean): FoodPageExit = when (closing) {
            Closing.Saved, Closing.Deleted, Closing.Gone -> Back()
            is Closing.Hidden ->
                Back(if (listBelow) ListResult(FoodsViewModel.HIDDEN, closing.foodId) else null)
            is Closing.Join ->
                if (listBelow) {
                    Back(ListResult(FoodsViewModel.JOIN_FROM, closing.foodId))
                } else {
                    ToList(closing.foodId)
                }
        }
    }
}

/**
 * What a food's page leaves for the list beneath it, in that list's back stack entry's saved state.
 *
 * **Not the list view model's own saved state.** Navigation 2.7's `NavBackStackEntry.savedStateHandle`
 * is a handle of its own, held by a view model of Navigation's, with none of the route's arguments
 * in it; a Hilt view model on the same entry is given a different handle. So a result written to
 * the entry is not seen by `FoodsViewModel`, and [TakeFromFoodPage] carries it across.
 */
data class ListResult(val key: String, val foodId: Long) {
    fun leaveIn(results: SavedStateHandle) {
        results[key] = foodId
    }
}

/**
 * Hands the list whatever a food's page left for it in [results], once: each is taken out as it is
 * read, so the list coming back again — or restored after the process ended — does not act on it a
 * second time (D55 §5, §8).
 */
@Composable
fun TakeFromFoodPage(
    results: SavedStateHandle,
    onJoinFrom: (Long) -> Unit,
    onHidden: (Long) -> Unit,
) {
    LaunchedEffect(results) {
        results.getStateFlow<Long?>(FoodsViewModel.JOIN_FROM, null).collect { foodId ->
            if (foodId != null) {
                results[FoodsViewModel.JOIN_FROM] = null
                onJoinFrom(foodId)
            }
        }
    }
    LaunchedEffect(results) {
        results.getStateFlow<Long?>(FoodsViewModel.HIDDEN, null).collect { foodId ->
            if (foodId != null) {
                results[FoodsViewModel.HIDDEN] = null
                onHidden(foodId)
            }
        }
    }
}
