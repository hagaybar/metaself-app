package com.metaself.app.ui.screen.propose

import com.metaself.app.domain.ai.ProposedItem
import com.metaself.app.domain.amount.ItemToLog
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.ui.ActionRefused

/**
 * One row of a proposal: what the model said, and the item being logged from it (D53 §1).
 *
 * [estimate] is kept beside [item] until he saves, so that nothing done to the row loses the model's
 * answer. [item] is its own worth times its own amount; there is no scaling, and so no original to
 * scale from.
 */
data class ProposalRow(
    val estimate: ProposedItem,
    val item: ItemToLog,
) {
    /**
     * The row whose source the screen's origin line reads (D7a), or null.
     *
     * The row itself while it can be logged. While the amount box is blank or refused there is no
     * row, but the worth still has a source and the line still has something true to say — so it
     * is read at an amount of one, which changes the figures and never the source (D53 §3: the
     * amount has no source).
     */
    val sourceRow: FoodItem?
        get() = item.toFoodItem() ?: item.copy(amountText = "1").toFoodItem()
}

/** What the describe-a-meal screen is showing. */
sealed interface ProposalUiState {

    /**
     * Waiting for words. Also where a failure leaves the owner, with his words still in the box.
     *
     * [needsKey] is the one failure fixed somewhere else in the app, so the screen offers the way
     * there beside it (public issue #11).
     */
    data class Describing(
        val failure: String? = null,
        val needsKey: Boolean = false,
        /** Asking threw rather than answering. Drawn where [failure] is. */
        val refused: ActionRefused? = null,
    ) : ProposalUiState

    data object Waiting : ProposalUiState

    data class Proposed(
        val rows: List<ProposalRow>,
        val note: String?,
    ) : ProposalUiState {
        /** What the rows that can be logged add up to; a row with no usable amount adds nothing. */
        val totalKcal: Int get() = rows.sumOf { it.item.numbers?.kcal ?: 0 }

        /**
         * The first row that cannot be logged as it stands, or null when all of them can. Saving is
         * off while there is one, and the screen names it (D53 §6).
         */
        val blockedBy: Int?
            get() = rows.indexOfFirst { it.item.numbers == null }.takeIf { it >= 0 }
    }
}

/**
 * What the naming sheet drawn over the accept screen is showing (D46, issue #24).
 *
 * The rows are the ones just logged, read off the day — never the proposal, which holds no ids and
 * no stored figures. [isToday] and [refusal] are the day's own answers, because the sheet says
 * "Today will show these as one row called X" and shows why nothing was made in the place where he
 * is when it arrives.
 */
data class KeepingAsMeal(
    val rows: List<FoodItem>,
    val isToday: Boolean,
    val refusal: String?,
)

/**
 * The sheet to draw, or null when there is nothing to name yet.
 *
 * [taken] is the accept screen's own record that the offer was pressed — a `remember` on the screen,
 * the way the day remembers that naming has begun. [rows] are the day's chosen rows.
 *
 * **Both, or no sheet.** The rows are written after the tap and arrive a frame or more later, so a
 * sheet opened on the tap alone lists nothing and totals 0 kcal in front of him. And a choice may
 * already be standing on the day when he goes to describe something, so rows alone would open a
 * sheet he never asked for, over rows he did not just log.
 *
 * A decision rather than a condition inside a composable: this is the one rule of the new path that
 * nothing else can check, and a rule inside a modal sheet's `if` is a rule no test can see.
 */
fun keepingAsMeal(
    taken: Boolean,
    rows: List<FoodItem>,
    isToday: Boolean,
    refusal: String?,
): KeepingAsMeal? =
    if (taken && rows.isNotEmpty()) KeepingAsMeal(rows, isToday, refusal) else null

/** What the accept screen does next about the naming sheet. */
enum class NamingSheetStep {
    /** It is on screen; remember that it has been, so its going away means something. */
    OPEN,

    /** It has been open and there is nothing left to name: forget the name and go back to the day. */
    LEAVE,

    /** Nothing to name and nothing has been named: the screen has not been used for this at all. */
    WAIT,
}

/**
 * Open once, leave once, and leave only after it has been open (D46, issue #24).
 *
 * The sheet is not closed by the tap that asks for the meal — that would throw the typed name away
 * before anything knew whether it had worked, and a refusal is exactly the case where he needs the
 * name still in the field. It closes when the day says there is nothing left to name: the meal was
 * made, or the choice was given up on. Going back to the day belongs to that same moment.
 *
 * [hasBeenOpen] is what keeps the second rule: without it, an accept screen that was never used for
 * naming would "finish" on its first composition and pop itself off the stack.
 *
 * A function rather than a condition inside a `LaunchedEffect`, for the reason [keepingAsMeal] is
 * one: the sheet cannot be drawn in a render test, so a rule written inside the effect is a rule
 * nothing can check.
 */
fun namingSheetStep(hasSomethingToName: Boolean, hasBeenOpen: Boolean): NamingSheetStep = when {
    hasSomethingToName -> NamingSheetStep.OPEN
    hasBeenOpen -> NamingSheetStep.LEAVE
    else -> NamingSheetStep.WAIT
}
