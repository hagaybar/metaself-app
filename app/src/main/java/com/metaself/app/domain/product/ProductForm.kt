package com.metaself.app.domain.product

import com.metaself.app.domain.amount.BelievableAmount
import java.math.BigDecimal

/**
 * What the owner types off a package that the database has never heard of — or has, without every
 * figure, in which case it opens filled with what the database holds ([prefilled], D40).
 *
 * Held as the strings he typed rather than as parsed numbers: a field being mid-edit is a normal
 * state, and "5" on the way to "535" is not an error worth shouting about. The same shape as the
 * setup form, for the same reason.
 *
 * **Everything here comes off the label.** There is no path from an AI estimate into this form and
 * no button offering one. D4 says an estimate is never presented as a measurement, and publishing a
 * guess into a database strangers rely on is that sin committed against people who cannot see where
 * the number came from.
 */
enum class ProductField { NAME, KCAL, PROTEIN, CARBS, FAT, SERVING }

data class ProductForm(
    val barcode: String,
    val name: String = "",
    val brand: String = "",
    val kcalPer100g: String = "",
    val proteinPer100g: String = "",
    val carbsPer100g: String = "",
    val fatPer100g: String = "",
    val servingSizeG: String = "",
) {

    /**
     * One sentence per box for every kind of wrong — a word, a negative, "NaN", "Infinity", or
     * since D42 (issue #32) a figure past its ceiling — and the sentence names that ceiling, so a
     * pasted "Infinity" reads the same as it did and the reason for "950" is on the screen. The
     * ceilings are interpolated from [BelievableAmount], never written a second time.
     */
    fun errors(): Map<ProductField, String> = buildMap {
        if (name.isBlank()) put(ProductField.NAME, "What is it called? Copy the package.")

        val kcal = BelievableAmount.words(BelievableAmount.KCAL_PER_100G)
        val macro = BelievableAmount.words(BelievableAmount.MACRO_PER_100G)
        required(
            ProductField.KCAL,
            kcalPer100g,
            "Calories per 100 g, from the label (at most $kcal).",
        )
        required(
            ProductField.PROTEIN,
            proteinPer100g,
            "Protein per 100 g, in grams (at most $macro).",
        )
        required(
            ProductField.CARBS,
            carbsPer100g,
            "Carbohydrate per 100 g, in grams (at most $macro).",
        )
        required(ProductField.FAT, fatPer100g, "Fat per 100 g, in grams (at most $macro).")

        // Optional, but not allowed to be nonsense when it is given.
        if (servingSizeG.isNotBlank() && number(servingSizeG, ProductField.SERVING) == null) {
            val grams = BelievableAmount.words(BelievableAmount.GRAMS)
            put(
                ProductField.SERVING,
                "A serving size in grams (at most $grams), or leave it empty.",
            )
        }
    }

    fun toProduct(): Product? {
        if (errors().isNotEmpty()) return null
        return runCatching {
            Product(
                barcode = barcode,
                name = name.trim(),
                brand = brand.trim().takeIf { it.isNotBlank() },
                kcalPer100g = number(kcalPer100g, ProductField.KCAL)!!,
                proteinPer100g = number(proteinPer100g, ProductField.PROTEIN)!!,
                carbsPer100g = number(carbsPer100g, ProductField.CARBS)!!,
                fatPer100g = number(fatPer100g, ProductField.FAT)!!,
                servingSizeG = number(servingSizeG, ProductField.SERVING),
            )
        }.getOrNull()
    }

    private fun MutableMap<ProductField, String>.required(
        field: ProductField,
        typed: String,
        message: String,
    ) {
        if (number(typed, field) == null) put(field, message)
    }

    /**
     * Negative is not a quantity of food, and neither is a word — nor "NaN", "Infinity" or "1e999",
     * which Kotlin reads as numbers (D39, issue #31), nor a figure past what its box can hold (D42,
     * issue #32). The rule is [Product.isQuantityOfFood], judged with the box's own field, so the
     * serving takes the grams ceiling and each figure its per-100 g one. No comma: this form never
     * took one, and the parse is left exactly as it was.
     */
    private fun number(typed: String, field: ProductField): Double? =
        typed.trim().toDoubleOrNull()?.takeIf { Product.isQuantityOfFood(field, it) }

    companion object {

        /**
         * The form as the food database left it: what it holds already in the boxes, and a figure
         * it does not hold left empty for him to copy off the packet (D40, issue #30).
         *
         * A form rather than a partial [Product] because this is the one door a figure he types
         * may enter by — D39's rules live in [number] — and because an empty box has to be able to
         * say "nothing typed yet", which a product of four non-null figures cannot. Once the gap
         * is filled the form is the same as one typed in full, so it saves and logs as one.
         *
         * A serving the form would refuse — a pack's net weight of 6 kg — is left out rather than
         * pre-filled: it would open with a refusal waiting in a box he never typed in (D42).
         */
        fun prefilled(
            barcode: String,
            name: String,
            brand: String?,
            kcalPer100g: Double?,
            proteinPer100g: Double?,
            carbsPer100g: Double?,
            fatPer100g: Double?,
            servingSizeG: Double?,
        ): ProductForm = ProductForm(
            barcode = barcode,
            name = name,
            brand = brand.orEmpty(),
            kcalPer100g = typed(kcalPer100g),
            proteinPer100g = typed(proteinPer100g),
            carbsPer100g = typed(carbsPer100g),
            fatPer100g = typed(fatPer100g),
            servingSizeG = typed(
                servingSizeG?.takeIf { Product.isQuantityOfFood(ProductField.SERVING, it) },
            ),
        )

        /**
         * A figure as a person would type it off the label: "17", not "17.0", and never an
         * exponent. `BigDecimal.valueOf` goes through the shortest decimal that reads back to the
         * same double, so a form saved as it came gives back exactly the figures the database
         * stated. Zero is written out by hand: how a zero BigDecimal strips its trailing zeros has
         * differed between Java versions ("0.0" on some), and the phone and the tests must agree.
         */
        private fun typed(figure: Double?): String = when {
            figure == null -> ""
            figure == 0.0 -> "0"
            else -> BigDecimal.valueOf(figure).stripTrailingZeros().toPlainString()
        }
    }
}
