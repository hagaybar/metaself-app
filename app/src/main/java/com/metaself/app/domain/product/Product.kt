package com.metaself.app.domain.product

import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.portion.Portions
import kotlin.math.roundToInt

/**
 * A packaged food, as its label declares it.
 *
 * Everything is per 100 g, because that is how Open Food Facts holds it and how European labels are
 * printed. How much was eaten therefore has to be stated, and [servingSizeG] is the package's own
 * answer to that question where it gives one — a default worth offering, never a number to assume.
 *
 * @property barcode the identity, and the key of the local table.
 * @property name and [brand] as the database holds them, which is whatever language the label is
 *   printed in — often not Latin script, and sometimes right-to-left.
 */
data class Product(
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingSizeG: Double? = null,
) {
    init {
        require(barcode.isNotBlank()) { "a product is identified by its barcode" }
        require(name.isNotBlank()) { "a product without a name is not usable" }
    }

    /**
     * What to put in the amount box when the screen opens: the package's serving, else 100 g. A
     * serving the grams box would refuse is no serving to offer: a packet typed before D39 (issue
     * #31) may hold "Infinity", and a pack's net weight of 6 kg is past the 5000 g ceiling (D42,
     * issue #32) — offering either would open the screen with its own refusal already showing.
     */
    val suggestedGrams: Double
        get() = servingSizeG
            ?.takeIf { it > 0.0 && BelievableAmount.isBelievable(it, BelievableAmount.GRAMS) }
            ?: DEFAULT_GRAMS

    /**
     * Whether all four per-100 g figures are quantities of food ([isQuantityOfFood]), each judged
     * against its own ceiling. A product failing this is refused at every door it could enter by
     * (D39, issue #31; the ceilings since D42, issue #32).
     */
    val figuresAreFood: Boolean
        get() = isQuantityOfFood(ProductField.KCAL, kcalPer100g) &&
            isQuantityOfFood(ProductField.PROTEIN, proteinPer100g) &&
            isQuantityOfFood(ProductField.CARBS, carbsPer100g) &&
            isQuantityOfFood(ProductField.FAT, fatPer100g)

    /** "במבה — אסם", or just the name when the database has no brand for it. */
    val label: String get() = brand?.takeIf { it.isNotBlank() }?.let { "$name — $it" } ?: name

    /**
     * What was eaten, as a record entry.
     *
     * Marked [Source.LABEL] and carrying no confidence: nothing guessed. The portion keeps its
     * numbers as well as its words, so a barcode entry can be adjusted afterwards exactly like a
     * meal repeated or a model's proposal.
     *
     * Null for no amount, and — as a last line, not the fix — for a figure or an amount that is no
     * quantity of food, or past its ceiling (D42, issue #32). The doors refuse such a packet and
     * such an amount, so nothing the app builds today reaches this with one; a caller holding one
     * some other way must log nothing rather than crash on the arithmetic below, or — since
     * rounding saturates rather than throws — log a 2,147,483,647-kcal row.
     */
    fun toFoodItem(grams: Double): FoodItem? {
        if (grams <= 0.0 || !BelievableAmount.isBelievable(grams, BelievableAmount.GRAMS)) {
            return null
        }
        if (!figuresAreFood) return null
        val share = grams / 100.0
        return FoodItem(
            name = name,
            portion = Portions.words(grams, "g"),
            portionAmount = grams,
            portionUnit = "g",
            kcal = (kcalPer100g * share).roundToInt(),
            proteinG = (proteinPer100g * share).roundToInt(),
            carbsG = (carbsPer100g * share).roundToInt(),
            fatG = (fatPer100g * share).roundToInt(),
            source = Source.LABEL,
            confidence = null,
        )
    }

    /**
     * The label's own figures per 100 g, as printed — what a food learns from a scan, rather than
     * the whole-gram row it was logged as, which cannot be worked back to them (D38, issue #28).
     *
     * Copied, not computed. Null when any figure is negative, not finite or past its ceiling: that
     * is no quantity of food, and a zero put in its place would be a number the label never stated
     * (D4). Since D39 (issue #31) every door refuses such a product, so the null here is a last
     * line.
     */
    fun nutrientsPer100g(): Nutrients? {
        if (!figuresAreFood) return null
        return Nutrients(
            kcal = kcalPer100g,
            proteinG = proteinPer100g,
            carbsG = carbsPer100g,
            fatG = fatPer100g,
        )
    }

    companion object {
        const val DEFAULT_GRAMS = 100.0

        /**
         * The one rule for a label figure (D39, issue #31): a number, not below zero, and — since
         * D42 (issue #32) — not above what a label for 100 g of anything prints: 1000 kcal, or
         * 110 g of any one macro (a little past the chemistry, because a rounded label goes past
         * it and is still what the packet says). Applied to the value, not the spelling — "NaN",
         * "Infinity" and "1e999" all parse, and all fail here; so do 1e12 kcal and 3700 "kcal"
         * that are really kilojoules.
         * Zero is a quantity, so a zero the database states passes; one it leaves out is never
         * read as zero at all (D40, issue #30).
         *
         * The figure is judged with its field, so nothing can check a packet figure without its
         * ceiling. A serving is an amount in grams and takes that ceiling, never a macro's; a
         * name is no figure at all, and asking is a caller's mistake rather than a silent answer.
         */
        fun isQuantityOfFood(field: ProductField, figure: Double): Boolean {
            val most = when (field) {
                ProductField.KCAL -> BelievableAmount.KCAL_PER_100G
                ProductField.PROTEIN, ProductField.CARBS, ProductField.FAT ->
                    BelievableAmount.MACRO_PER_100G
                ProductField.SERVING -> BelievableAmount.GRAMS
                ProductField.NAME -> throw IllegalArgumentException("a product's name is no figure")
            }
            return BelievableAmount.isBelievable(figure, most)
        }
    }
}
