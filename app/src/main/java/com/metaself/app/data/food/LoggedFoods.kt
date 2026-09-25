package com.metaself.app.data.food

import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.DerivedFoods
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.FoodRetaught
import com.metaself.app.domain.food.LoggedFoodRow
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.food.ReplacedFacts
import com.metaself.app.domain.food.RetaughtRow
import javax.inject.Inject

/**
 * Attaching what is about to be logged to the food it is.
 *
 * Every way a thing gets logged — described to the model, scanned off a packet, picked out of the
 * owner's own list, typed by hand, repeated from a past day — arrives here, because all of them go
 * through the same one place before reaching the record. One food for the yoghurt, however it was
 * arrived at.
 *
 * **The facts a row implies are worked out by the same function the conversion of the whole record
 * uses.** One row through it produces exactly the food the migration would have produced from that
 * row, which is what stops a yoghurt logged tomorrow being subtly different from the same yoghurt
 * logged last year. The one exception is a scan's per-100 g, which is the packet's own figure: the
 * row it was logged as is rounded to whole grams (D38), and working the label back out of a rounded
 * row would file a different number under the label's name (D4, #28). Every other figure worked
 * back from a label row — a scanned row renamed in *Correct this item*, arriving here with no
 * packet — is filed as copied from a past meal, never as the label's, and so ranks below anything
 * he typed (D43, #29). Those facts are then OFFERED to the food, through the guarded statements, so
 * a scan improves what the food knows and a guess cannot damage it.
 *
 * **A scan adds a fact and changes nothing about how the owner counts the food.** Open Food Facts
 * is always per 100 g, so those are the only numbers a packet has any claim to; the guarded
 * statements see to it that what one bar is worth, and what one bar weighs, are left exactly as they
 * were. That is the whole of the failure mode the earlier design had, where re-scanning a food
 * counted in bars flipped it to grams and yesterday's "2" silently meant two grams today.
 */
class LoggedFoods @Inject constructor(
    private val foods: FoodRepository,
) {

    /**
     * The rows, with the foods they are, and what each row saw those foods holding.
     *
     * A carrier rather than a bare list so a caller cannot drop the report by forgetting a line: a
     * caller that wants only the rows has to write [items] or [item] (issue #13).
     */
    data class Attached(val items: List<FoodItem>, val rows: List<RetaughtRow>) {

        /** The one row, for the callers that attach exactly one. */
        val item: FoodItem get() = items.single()

        /** One line per food this action really changed — collapsed, so a food is named once. */
        val retaught: List<FoodRetaught> get() = ReplacedFacts.collapse(rows)
    }

    /**
     * The rows of one meal, each with what its food is to be taught ([ToLog]).
     *
     * [strict] lets a storage failure out instead of leaving the row unattached — for keeping a meal
     * without logging (D58 §12.7), where the attaching runs inside one transaction and a failure
     * swallowed there would roll it all back while reporting success. Logging keeps swallowing it,
     * so a row is still logged.
     */
    @JvmName("attachToLog")
    suspend fun attach(rows: List<ToLog>, strict: Boolean = false): Attached {
        val each = rows.map { attach(it.item, brand = it.brand, taught = it.taught, strict = strict) }
        return Attached(each.flatMap { it.items }, each.flatMap { it.rows })
    }

    suspend fun attach(items: List<FoodItem>): Attached {
        val each = items.map { attach(it) }
        return Attached(each.flatMap { it.items }, each.flatMap { it.rows })
    }

    /**
     * The row, with the food it is.
     *
     * [labelPer100g] is the packet's own figures, from a scan; when given, the food is offered them
     * as its per-100 g instead of the figures worked back from the row. [taught] is a described
     * item's worth (D53 §3); when given, the food is offered it in place of everything worked back
     * from the row, in the one offer, for the same reason. A scan that brings a
     * [barcode] but no such figures — its label is no quantity of food — teaches its food nothing:
     * see [attachUntaught].
     *
     * Returns the row unattached if its name is not a name — punctuation, or nothing that survives
     * normalising. That cannot arrive from a screen, which requires a name, and leaving such a row
     * pointing at nothing is right anyway: it would otherwise join every other unkeyable row under
     * one food that is true of none of them.
     */
    suspend fun attach(
        item: FoodItem,
        barcode: String? = null,
        brand: String? = null,
        labelPer100g: Nutrients? = null,
        taught: FoodFacts? = null,
        strict: Boolean = false,
    ): Attached {
        // A row already attached keeps its food. A repeated meal arrives carrying rows that were
        // resolved when they were first logged, and resolving them again would be asking the same
        // question twice and risking a different answer.
        if (item.foodId != null) return Attached(listOf(item), emptyList())

        val derived = runCatching {
            DerivedFoods.from(listOf(item.asRow())).foods.singleOrNull()
        }.getOrNull() ?: return Attached(listOf(item), emptyList())
        if (derived.displayName == DerivedFoods.UNNAMED) return Attached(listOf(item), emptyList())

        if (barcode != null && labelPer100g == null) return attachUntaught(item, barcode, brand)

        // A scan brings the packet and the brand with it, which is what makes the same bar
        // described last week and scanned today one food rather than two. Everything else brings
        // neither, and lands on the brand that means none.
        //
        // A scan also brings the label, and its per-100 g replaces the one worked out from the row:
        // the row is rounded to whole grams (D38), so 0.5 g of fat logged as 30 g is a row of 0 g,
        // and working back from it would file 0.0 under the label's name (D4, #28). Replaced in the
        // one offer, not offered afterwards, so the food never holds the worked-back number even
        // for a moment. Everything else the row implies is left as derived — for a row in grams,
        // nothing about what one of it is worth or weighs.
        val finding: suspend () -> FoundOrCreated = {
            foods.findOrCreate(
                name = item.name,
                brand = brand,
                facts = labelPer100g?.let { label ->
                    derived.facts.copy(
                        per100g = PerHundredGrams(
                            nutrients = label,
                            provenance = Provenance(Source.LABEL, confidence = null, setAtMillis = 0),
                        ),
                    )
                } ?: taught ?: derived.facts,
                barcode = barcode,
            )
        }
        val found = if (strict) finding() else runCatching { finding() }.getOrNull()
            ?: return Attached(listOf(item), emptyList())

        // The two snapshots this row saw, reported and not judged: what the food held immediately
        // before it was offered these facts, and what it holds now. Whether that is a replacement
        // worth telling the owner about is decided once, over a whole action, by `ReplacedFacts`
        // (D45, issue #13). The name is the food's own stored one, never what he typed.
        return Attached(
            listOf(item.copy(foodId = found.food.id)),
            listOf(
                RetaughtRow(
                    foodId = found.food.id,
                    foodName = found.food.name,
                    before = found.before,
                    after = found.food.facts,
                ),
            ),
        )
    }

    /**
     * A scanned row whose packet has a figure that is no quantity of food (negative, infinite or
     * not a number). No path in the app reaches this today: since D39 (issue #31) every door
     * refuses such a packet, and `Product.toFoodItem` refuses one too, so no such row is made to
     * log. It is reached only by a caller that builds the logged row some other way with a product
     * whose `Product.nutrientsPer100g` is null — kept as the last line because that food should
     * not be taught a figure worked back from the row.
     *
     * The row is whole grams (D38), so the only per-100 g it could offer is one worked back from a
     * rounded row. Before D43 (#29) that figure was filed under the label's name — the #28 defect by
     * another door (D4); since, it would be filed as copied instead. Either way a packet whose
     * figures are no quantity of food is no evidence about this food at all, so it offers nothing.
     * It still lands on the food it already is — the one holding this barcode, else the one of this
     * name and brand, as [FoodRepository.findOrCreate] would find it — because that link costs no
     * number. It is not taught the barcode either: the only door that sets one also offers facts,
     * and a later scan with real figures will find the food by name and teach it.
     *
     * With no such food the row is left unattached rather than a food made: a food must know what
     * some amount of it is worth, and nothing here says so honestly. The next scan of this packet with
     * figures that are a quantity makes the food, and the repair for detached rows (#22) can then put
     * this row under it. That repair goes by name alone and ignores brand ([DetachedRows]): a branded
     * row left unattached here because the only food of its name is unbranded is put under that
     * unbranded food the next time the app opens. Only the link moves, never a number (D4).
     */
    private suspend fun attachUntaught(item: FoodItem, barcode: String, brand: String?): Attached {
        val foodId = runCatching {
            foods.byBarcode(barcode)?.id ?: run {
                val brandKey = FoodKeys.brandKey(brand)
                foods.foodIdsNamed(item.name).singleOrNull { id ->
                    foods.byId(id)?.let { FoodKeys.brandKey(it.brand) == brandKey } == true
                }
            }
        }.getOrNull() ?: return Attached(listOf(item), emptyList())
        // It teaches its food nothing, so there is never anything to report from here.
        return Attached(listOf(item.copy(foodId = foodId)), emptyList())
    }

    /** The row as the shared conversion needs to see it. The ref is unused: there is only one. */
    private fun FoodItem.asRow() = LoggedFoodRow(
        ref = 0,
        name = name,
        portionAmount = portionAmount,
        portionUnit = portionUnit,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        source = source,
        confidence = confidence,
        loggedAtMillis = 0,
    )
}

/**
 * A row about to be logged as part of a meal, with what its food is to be taught when that is not
 * what the row itself implies.
 *
 * @property taught a described item's worth, unrounded ([LoggedFoods.attach]); null to teach from
 *   the row, as every other way in does.
 * @property brand the brand of the food the row was taken as, so it lands on that food rather than
 *   an unbranded one of the same name (identity is name and brand); null for none.
 */
data class ToLog(val item: FoodItem, val taught: FoodFacts? = null, val brand: String? = null)
