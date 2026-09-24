package com.metaself.app.ui.nav

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.screen.food.Closing
import com.metaself.app.ui.screen.foods.FoodsViewModel
import org.junit.jupiter.api.Test

/**
 * Where a food's page goes when it closes (D55 §5, §6), decided without a nav controller.
 *
 * The nav host and the walk harness both act on this; only the acting is untested here. JUnit 5:
 * nothing in it touches Android.
 */
class FoodPageExitTest {

    @Test
    fun `saved, deleted and gone go back and leave the list nothing`() {
        listOf(Closing.Saved, Closing.Deleted, Closing.Gone).forEach { closing ->
            assertThat(FoodPageExit.of(closing, listBelow = true)).isEqualTo(FoodPageExit.Back())
            assertThat(FoodPageExit.of(closing, listBelow = false)).isEqualTo(FoodPageExit.Back())
        }
    }

    /** The list beneath is handed the food to join from, with its search and scroll as they were. */
    @Test
    fun `joining with the list below goes back to it with the food to join from`() {
        assertThat(FoodPageExit.of(Closing.Join(7), listBelow = true))
            .isEqualTo(FoodPageExit.Back(ListResult(FoodsViewModel.JOIN_FROM, 7)))
    }

    /** From "Give this a portion" there is no list beneath: the page is replaced by one. */
    @Test
    fun `joining with anything else below replaces the page with the list, picking`() {
        val exit = FoodPageExit.of(Closing.Join(7), listBelow = false)

        assertThat(exit).isEqualTo(FoodPageExit.ToList(joinFrom = 7))
        assertThat((exit as FoodPageExit.ToList).route).isEqualTo("foods?joinFrom=7")
    }

    @Test
    fun `hiding with the list below tells it which food was hidden`() {
        assertThat(FoodPageExit.of(Closing.Hidden(7, "Greek yoghurt"), listBelow = true))
            .isEqualTo(FoodPageExit.Back(ListResult(FoodsViewModel.HIDDEN, 7)))
    }

    /** Add something closes its own question for a hidden food; there is no list to tell. */
    @Test
    fun `hiding with anything else below just goes back`() {
        assertThat(FoodPageExit.of(Closing.Hidden(7, "Greek yoghurt"), listBelow = false))
            .isEqualTo(FoodPageExit.Back())
    }

    /** The list is recognised by the pattern the host registers it under, arguments and all. */
    @Test
    fun `only the list's registered route counts as the list`() {
        assertThat(Destination.Foods.isTheList("foods?joinFrom={joinFrom}")).isTrue()
        assertThat(Destination.Foods.isTheList("meal/repeat")).isFalse()
        assertThat(Destination.Foods.isTheList("food/{foodId}")).isFalse()
        assertThat(Destination.Foods.isTheList(null)).isFalse()
    }
}
