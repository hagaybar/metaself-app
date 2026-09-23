package com.metaself.app.ui.screen.propose

import com.metaself.app.data.food.ToLog
import com.metaself.app.domain.ai.ProposedItem
import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.amount.ItemToLog
import com.metaself.app.domain.amount.Per
import com.metaself.app.domain.amount.Rate
import com.metaself.app.domain.amount.Worth
import com.metaself.app.domain.amount.teaches
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.food.CountedIn
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.FoodMatch
import com.metaself.app.domain.food.FoodMatching
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
 *
 * [match] is what his own foods said about the model's plain name when the answer arrived (§4). The
 * row is on his food while [item] carries its id — his food's own worth, or his typing over it —
 * and on the estimate otherwise; one tap moves it either way, and nothing is lost.
 */
data class ProposalRow(
    val estimate: ProposedItem,
    val item: ItemToLog,
    /** The four worth boxes, while they are open under the worth line; null while closed. */
    val editingWorth: WorthBoxes? = null,
    val match: FoodMatch = FoodMatch.None,
) {
    /** True while the row is his food's — its worth, or his typing over it. */
    val onYourFood: Boolean get() = item.foodId != null

    private val candidate: Food?
        get() = when (val match = match) {
            is FoodMatch.Exact -> match.food
            is FoodMatch.Close -> match.food
            FoodMatch.None -> null
        }

    /**
     * The way across when the row is on the estimate and the food it is exactly cannot cost the
     * described amount (§5) — the sentence and *Count it in …* are drawn from it. Null otherwise.
     */
    val switchOffered: Pair<Food, CountedIn>?
        get() {
            if (onYourFood) return null
            val food = (match as? FoodMatch.Exact)?.food ?: return null
            return FoodMatching.countedInstead(food, item.unit)?.let { food to it }
        }

    /**
     * The food *Use your …?* names, or null when it is not offered: a close match not yet taken,
     * or an exact one he has set aside for the estimate. An exact match that cannot cost the amount
     * offers [switchOffered] instead.
     */
    val yourFoodOffered: Food?
        get() {
            if (onYourFood) return null
            return when (val match = match) {
                is FoodMatch.Close -> match.food
                is FoodMatch.Exact ->
                    match.food.takeIf { FoodMatching.countedAsFor(it, item.unit) != null }
                FoodMatch.None -> null
            }
        }

    /**
     * The brand the row is saved under, or null for none.
     *
     * A branded food is only ever a close match — identity is name and brand, and the model names
     * no brand (§4). Taken, it is his answer that the item is that food; when the food cannot cost
     * the amount the row stays the estimate under the food's name (§5), unattached, and is found
     * again by name and brand when it is saved. Without the brand it would land on — or make — an
     * unbranded food of the same name beside his. So the brand goes with the row whenever the row
     * is on the estimate under that food's name, exactly as an unbranded exact match's row lands
     * on his food by name and teaches it the estimate. Under the model's own name it carries none.
     */
    val brand: String?
        get() {
            if (onYourFood) return null
            val food = (match as? FoodMatch.Exact)?.food ?: return null
            val sameName = runCatching {
                FoodKeys.nameKey(item.name) == FoodKeys.nameKey(food.name)
            }.getOrDefault(false)
            if (!sameName || FoodKeys.brandKey(food.brand) == FoodKeys.NO_BRAND_KEY) return null
            return food.brand
        }

    /**
     * What saving hands over for this row, or null while it cannot be logged: the row, the worth its
     * food is to be taught (D53 §3), and [brand].
     */
    fun toLog(): ToLog? =
        item.toFoodItem()?.let { ToLog(it, taught = item.teaches(), brand = brand) }

    /** What *Use the estimate* would log, for its label — null while that is not yet a number. */
    val estimateKcal: Int? get() = usingEstimate().numbers?.kcal

    /**
     * His food, in place of the estimate (§4). A close match taken is his answer that it is this
     * food, so from then on it is treated as the exact one — its name, and the unit rule of §5: the
     * amount described is kept when the food can cost it, and otherwise the row stays the estimate
     * and offers the switch. Whatever amount he has typed is his, and stays.
     */
    fun usingYourFood(): ProposalRow {
        val food = candidate ?: return this
        val taken = copy(match = FoodMatch.Exact(food), editingWorth = null)
        val countedAs = FoodMatching.countedAsFor(food, item.unit)
            ?: return taken.copy(item = item.copy(name = food.name))
        return taken.copy(
            item = item.copy(
                name = food.name,
                worth = Worth.YourFood(food, countedAs),
                foodId = food.id,
            ),
        )
    }

    /**
     * The model's item again (§4, §5): its name, its worth, its source. The amount stays when the
     * unit is still the model's; after a switch of unit the model's own amount and unit return.
     */
    fun usingEstimate(): ProposalRow {
        val model = estimate.toItemToLog()
        val amount = if (item.unit == model.unit) item.amountText else model.amountText
        return copy(item = model.copy(amountText = amount), editingWorth = null)
    }

    /**
     * *Count it in …* (§5): the food's own way of counting and its worth, with the amount box
     * EMPTY — a number put there would look like his own (D4, D30). The row cannot be saved until
     * he types one.
     */
    fun countedInFoodUnit(): ProposalRow {
        val (food, way) = switchOffered ?: return this
        return copy(
            item = ItemToLog(
                name = food.name,
                detail = item.detail,
                amountText = "",
                unit = way.unit,
                worth = Worth.YourFood(food, way.countedAs),
                foodId = food.id,
            ),
            editingWorth = null,
        )
    }

    companion object {
        /**
         * A row for the model's [estimate], looked up among the foods [offered] (§4): on his food
         * from the start when it is exactly one of them and can cost the amount, on the estimate
         * otherwise.
         */
        fun of(estimate: ProposedItem, offered: List<Food>): ProposalRow {
            val row = ProposalRow(
                estimate = estimate,
                item = estimate.toItemToLog(),
                match = FoodMatching.match(estimate.name, offered),
            )
            if (row.match !is FoodMatch.Exact) return row
            return if (row.yourFoodOffered != null) row.usingYourFood() else row
        }
    }

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
