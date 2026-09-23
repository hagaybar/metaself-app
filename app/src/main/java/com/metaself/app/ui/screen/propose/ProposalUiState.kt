package com.metaself.app.ui.screen.propose

import com.metaself.app.domain.ai.ProposedItem
import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.amount.ItemToLog
import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.amount.Worth
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.food.LoggedFrom
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.portion.Portions
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
    /** The four worth boxes, while they are open under the worth line; null while closed. */
    val editingWorth: WorthBoxes? = null,
) {
    /**
     * What the row will log — or null while its amount is not usable, or while a worth box is
     * blank or refused, which blocks the row exactly as a blank amount does (D53 §6).
     */
    val numbers: LoggedFrom.Numbers?
        get() = if (editingWorth?.refused == true) null else item.numbers

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

/** One of the four worth boxes, in the order they are drawn. */
enum class WorthFigure { KCAL, PROTEIN, CARBS, FAT }

/**
 * The worth, as four boxes being typed into (D53 §1, §3, §6).
 *
 * They open holding the worth as a person would type it, and are judged as typed: a comma is a
 * decimal point, and each figure has D42's ceiling for the basis — a food's per-100 g ceilings for
 * per 100 g or ml, its per-unit ones for per one piece. A blank box is refused as a figure past its
 * ceiling is, since a row has to be worth something to be logged.
 *
 * **A figure is changed only when it is a different number from the one it opened with** —
 * "250.0" for "250" is no change. With none changed the row keeps the worth it had, source and
 * all; with one changed, all four are his ([Worth.Typed]: the source belongs to the row, D44's
 * cost). A box left alone keeps its figure at full precision, not the one decimal it was shown with.
 *
 * @property opened the worth's figures when the boxes opened, at full precision.
 * @property openedWith the worth the row had, to return to while nothing differs from it.
 * @property typed the four boxes' text, in [WorthFigure] order.
 */
data class WorthBoxes(
    val opened: Rate,
    val openedWith: Worth,
    val typed: List<String>,
) {
    val per: Per get() = opened.per

    private val openedFigures: List<Double>
        get() = with(opened.nutrients) { listOf(kcal, proteinG, carbsG, fatG) }

    private val judged: List<Double?>
        get() = typed.mapIndexed { at, text ->
            val most = if (at == 0) per.kcalMost else per.macroMost
            text.trim().replace(',', '.').toDoubleOrNull()
                ?.takeIf { BelievableAmount.isBelievable(it, most) }
        }

    /** True while any box is blank, not a number, negative or past its ceiling. */
    val refused: Boolean get() = judged.any { it == null }

    fun with(figure: WorthFigure, text: String): WorthBoxes =
        copy(typed = typed.mapIndexed { at, old -> if (at == figure.ordinal) text else old })

    /** The worth the boxes now say, or null while [refused]. */
    fun worth(): Worth? {
        val figures = judged
        if (figures.any { it == null }) return null
        val shown = openedFigures.map { Portions.format(it).toDouble() }
        val changed = figures.indices.filter { figures[it] != shown[it] }
        if (changed.isEmpty()) return openedWith
        val kept = figures.indices.map { if (it in changed) figures[it]!! else openedFigures[it] }
        return Worth.Typed(Rate(Nutrients(kept[0], kept[1], kept[2], kept[3]), per))
    }

    companion object {
        /** The boxes opened on [item]'s worth, or null when it has no worth line to open. */
        fun of(item: ItemToLog): WorthBoxes? {
            val rate = item.rateLine ?: return null
            val figures = with(rate.nutrients) { listOf(kcal, proteinG, carbsG, fatG) }
            return WorthBoxes(rate, item.worth, figures.map(Portions::format))
        }
    }
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
        val totalKcal: Int get() = rows.sumOf { it.numbers?.kcal ?: 0 }

        /**
         * The first row that cannot be logged as it stands, or null when all of them can. Saving is
         * off while there is one, and the screen names it (D53 §6).
         */
        val blockedBy: Int?
            get() = rows.indexOfFirst { it.numbers == null }.takeIf { it >= 0 }
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
