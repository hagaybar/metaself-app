package com.metaself.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.food.aFood
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.ui.food.ReviewActions
import com.metaself.app.ui.screen.day.DayMeals
import com.metaself.app.ui.screen.foods.FoodsScreen
import com.metaself.app.ui.screen.foods.FoodsUiState
import com.metaself.app.ui.theme.MetaSelfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The hand feels a choice begin and a row tick (public issue #16).
 *
 * **What this proves, and what it cannot.** A recording [HapticFeedback] stands in for the phone's,
 * so these tests prove the app ASKS for the right feel at the right moment — a firm one when a long
 * press starts choosing, a light one when a tap ticks or unticks a row, none on an ordinary tap.
 * Whether the phone then vibrates, and how it feels, is a check for the phone itself.
 *
 * JUnit 4 because Compose's rule demands it. `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class TouchFeelRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val haptics = RecordingHaptics()

    private var chosen by mutableStateOf(emptySet<Long>())

    private fun recordRows() {
        compose.setContent {
            MetaSelfTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    DayMeals(
                        meals = listOf(aMeal(id = 1, items = listOf(anItem(id = 7, name = "Hummus")))),
                        onEditItem = {},
                        onDeleteItem = {},
                        chosen = chosen,
                        onBeginChoosing = { chosen = chosen + it },
                        onToggleChosen = { chosen = if (it in chosen) chosen - it else chosen + it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun builtMeal() {
        compose.setContent {
            MetaSelfTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    DayMeals(
                        meals = listOf(
                            aMeal(
                                id = 1,
                                items = listOf(anItem(id = 7, name = "Hummus")),
                                savedMealId = 3,
                                savedMealName = "Lunch plate",
                            ),
                        ),
                        onEditItem = {},
                        onDeleteItem = {},
                        chosen = chosen,
                        onChooseMeal = { meal ->
                            val ids = meal.items.map { it.id }.toSet()
                            chosen = if (chosen.containsAll(ids)) chosen - ids else chosen + ids
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun foodRows() {
        compose.setContent {
            MetaSelfTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    FoodsScreen(
                        state = FoodsUiState(foods = listOf(aFood(name = "Oat milk")), chosen = chosen),
                        onSearch = {},
                        onShowOnlyPortions = {},
                        onShowHidden = {},
                        onEdit = {},
                        onSetForm = {},
                        onSave = {},
                        onCancelEditing = {},
                        onHide = {},
                        onUnhide = {},
                        onDelete = {},
                        onConfirmDeleting = {},
                        onCancelDeleting = {},
                        onBeginMerging = {},
                        onMergeInto = {},
                        onConfirmMerging = {},
                        onCancelMerging = {},
                        onDismissRefusal = {},
                        review = ReviewActions.NONE,
                        onBeginChoosing = { chosen = chosen + it },
                        onToggleChosen = { chosen = if (it in chosen) chosen - it else chosen + it },
                        onClearChoosing = {},
                        onMakeMeal = {},
                        onJoinChosen = {},
                        onBack = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun hold(text: String) {
        compose.onNodeWithText(text, substring = true).performTouchInput { longClick() }
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text, substring = true).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `holding a record row to start choosing is felt as a firm press`() {
        recordRows()

        hold("Hummus")

        assertThat(chosen).containsExactly(7L)
        assertThat(haptics.asked).containsExactly(HapticFeedbackType.LongPress)
    }

    @Test
    fun `ticking and unticking a record row is felt as a light tick each time`() {
        chosen = setOf(99L)
        recordRows()

        tap("Hummus")
        tap("Hummus")

        assertThat(chosen).containsExactly(99L)
        assertThat(haptics.asked)
            .containsExactly(HapticFeedbackType.TextHandleMove, HapticFeedbackType.TextHandleMove)
    }

    /** An ordinary tap opens the row to be corrected; it is not a choice, and is not felt as one. */
    @Test
    fun `an ordinary tap on a record row asks for no feel of its own`() {
        recordRows()

        tap("Hummus")

        assertThat(haptics.asked).isEmpty()
    }

    @Test
    fun `holding a built meal to take all of it is felt as a firm press`() {
        builtMeal()

        hold("Lunch plate")

        assertThat(chosen).containsExactly(7L)
        assertThat(haptics.asked).containsExactly(HapticFeedbackType.LongPress)
    }

    @Test
    fun `ticking a built meal while choosing is felt as a light tick`() {
        chosen = setOf(99L)
        builtMeal()

        tap("Lunch plate")

        assertThat(chosen).containsExactly(99L, 7L)
        assertThat(haptics.asked).containsExactly(HapticFeedbackType.TextHandleMove)
    }

    @Test
    fun `holding a food to start choosing is felt as a firm press`() {
        foodRows()

        hold("Oat milk")

        assertThat(chosen).containsExactly(0L)
        assertThat(haptics.asked).containsExactly(HapticFeedbackType.LongPress)
    }

    @Test
    fun `ticking a food while choosing is felt as a light tick`() {
        chosen = setOf(99L)
        foodRows()

        tap("Oat milk")

        assertThat(chosen).containsExactly(99L, 0L)
        assertThat(haptics.asked).containsExactly(HapticFeedbackType.TextHandleMove)
    }

    @Test
    fun `an ordinary tap on a food asks for no feel of its own`() {
        foodRows()

        tap("Oat milk")

        assertThat(haptics.asked).isEmpty()
    }

    private class RecordingHaptics : HapticFeedback {
        val asked = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            asked += hapticFeedbackType
        }
    }
}
