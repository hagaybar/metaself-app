package com.metaself.app.ui.screen.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.metaself.app.ui.food.ReviewActions

/**
 * One food's page wired to a live view model, every callback bound as the nav host binds it — and
 * left, drawing nothing, once it says it is closing, as the nav host leaves it. For session tests,
 * which press through the page rather than look at a drawing of it.
 */
@Composable
fun LiveFoodPage(viewModel: FoodPageViewModel) {
    val state by viewModel.state.collectAsState()
    var left by remember { mutableStateOf(false) }
    LaunchedEffect(state.closing) {
        if (state.closing != null) {
            left = true
            viewModel.closed()
        }
    }
    if (left) return

    FoodPageScreen(
        state = state,
        onSetForm = viewModel::setForm,
        onSave = viewModel::save,
        onHide = viewModel::hide,
        onUnhide = viewModel::unhide,
        onDelete = viewModel::askToDelete,
        onConfirmDeleting = viewModel::confirmDeleting,
        onCancelDeleting = viewModel::cancelDeleting,
        onBeginJoining = viewModel::beginJoining,
        onDismissRefusal = viewModel::dismissRefusal,
        review = ReviewActions(
            onReview = viewModel::review,
            onPutBack = viewModel::putBack,
            onAcceptAndSave = viewModel::acceptAndSave,
            onCancel = viewModel::cancelReview,
            onDismiss = viewModel::dismissReview,
        ),
        onBack = {},
    )
}
