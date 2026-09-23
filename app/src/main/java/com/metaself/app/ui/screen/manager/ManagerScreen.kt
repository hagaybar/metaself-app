package com.metaself.app.ui.screen.manager

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metaself.app.R
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.screen.foods.FoodsContent
import com.metaself.app.ui.screen.foods.FoodsUiState

/**
 * The one screen the food list and the meals he has built live on, a tab each.
 *
 * Looking after these two lists used to be reachable only by starting to log something, which made
 * maintenance read as part of eating. This is the front door; the ways in from the logging screen
 * stay exactly where they are, because that is where a duplicate is noticed.
 *
 * **Nothing here logs anything.** The meals tab is where a meal is looked after, not where one is
 * eaten. Back goes wherever he came from — the day when he came from the menu, the logging screen
 * when he stepped out of it to fix a duplicate — which is the plain back contract and keeps a
 * half-finished log where he left it.
 *
 * The two tabs share nothing but this frame: the food list is [FoodsContent], unchanged from the
 * screen it is also drawn on, and the meals list has a view model of its own.
 */
@Composable
fun ManagerScreen(
    tab: ManagerTab,
    onShowTab: (ManagerTab) -> Unit,
    foods: FoodsUiState,
    onSearch: (String) -> Unit,
    onShowOnlyPortions: (Boolean) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onEdit: (Long) -> Unit,
    onSetForm: (FoodForm) -> Unit,
    onSave: () -> Unit,
    onCancelEditing: () -> Unit,
    onHide: (Long) -> Unit,
    onUnhide: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onConfirmDeleting: () -> Unit,
    onCancelDeleting: () -> Unit,
    onBeginMerging: (Long) -> Unit,
    onMergeInto: (Long) -> Unit,
    onConfirmMerging: () -> Unit,
    onCancelMerging: () -> Unit,
    onDismissRefusal: () -> Unit,
    onBeginChoosing: (Long) -> Unit,
    onToggleChosen: (Long) -> Unit,
    onClearChoosing: () -> Unit,
    onMakeMeal: () -> Unit,
    onJoinChosen: () -> Unit,
    meals: MealsUiState,
    onBuildMeal: () -> Unit,
    onEditMeal: (Long) -> Unit,
    onDismissMealsFailure: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MetaSelfScreen(
        title = stringResource(R.string.manager_title),
        modifier = modifier,
        onBack = onBack,
        // Each tab keeps its own place; one shared scroller lost it in both at once.
        scrollKey = tab,
        // The strip is chrome, not content: it goes in the frame's own slot, edge to edge and
        // directly under the title bar. Emitted with the list instead, it was indented by the
        // screen's margins, pushed down from the bar, and scrolled away with the foods.
        belowBar = {
            // A food and a meal are different things to look after, so they are two lists rather
            // than one interleaved one — the same reasoning, and the same pair of names, the
            // logging screen already uses.
            TabRow(selectedTabIndex = if (tab == ManagerTab.FOODS) 0 else 1) {
                Tab(
                    selected = tab == ManagerTab.FOODS,
                    onClick = { onShowTab(ManagerTab.FOODS) },
                    text = { Text(stringResource(R.string.manager_tab_foods)) },
                )
                Tab(
                    selected = tab == ManagerTab.MEALS,
                    onClick = { onShowTab(ManagerTab.MEALS) },
                    text = { Text(stringResource(R.string.manager_tab_meals)) },
                )
            }
        },
    ) {
        when (tab) {
            ManagerTab.FOODS -> FoodsContent(
                state = foods,
                onSearch = onSearch,
                onShowOnlyPortions = onShowOnlyPortions,
                onShowHidden = onShowHidden,
                onEdit = onEdit,
                onSetForm = onSetForm,
                onSave = onSave,
                onCancelEditing = onCancelEditing,
                onHide = onHide,
                onUnhide = onUnhide,
                onDelete = onDelete,
                onConfirmDeleting = onConfirmDeleting,
                onCancelDeleting = onCancelDeleting,
                onBeginMerging = onBeginMerging,
                onMergeInto = onMergeInto,
                onConfirmMerging = onConfirmMerging,
                onCancelMerging = onCancelMerging,
                onDismissRefusal = onDismissRefusal,
                onBeginChoosing = onBeginChoosing,
                onToggleChosen = onToggleChosen,
                onClearChoosing = onClearChoosing,
                onMakeMeal = onMakeMeal,
                onJoinChosen = onJoinChosen,
            )

            ManagerTab.MEALS -> MealsContent(
                state = meals,
                onBuildMeal = onBuildMeal,
                onEditMeal = onEditMeal,
                onDismissFailure = onDismissMealsFailure,
            )
        }
    }
}
