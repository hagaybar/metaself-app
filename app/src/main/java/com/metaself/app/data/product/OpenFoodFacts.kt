package com.metaself.app.data.product

import com.metaself.app.domain.product.Product
import com.metaself.app.domain.product.ProductField
import com.metaself.app.domain.product.ProductForm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turning Open Food Facts' reply into a product, purely.
 *
 * Kept apart from the request so that the interesting half — a reply with no nutrition, a product
 * that does not exist, a number stored as a string — is testable against the real thing without a
 * network. The fixture in the test resources IS the real thing: fetched from the live database on
 * 2026-09-04, not written by hand to match the parser.
 *
 * Everything the database holds is per 100 g. A figure it does not hold — calories or any macro —
 * is never replaced by a zero: a silent zero is a meal that appears to cost nothing, and since #28
 * it would reach the food as a label fact (D4). Such a reply is [Reply.Incomplete], the label form
 * filled with what the database does have, the missing box empty for him to copy off the packet
 * (D40, issue #30). A figure it does hold that is no quantity of food — infinite, negative, "NaN",
 * or past its per-100 g ceiling (D42, issue #32) — makes the reply [Reply.Unusable] rather than be
 * carried to the screen (D39, issue #31).
 */
object OpenFoodFacts {

    /** What a reply turned out to be. */
    sealed interface Reply {

        /** Every figure stated, and every one a quantity of food. */
        data class Usable(val product: Product) : Reply

        /**
         * The database has the packet but not every figure: the form, filled with what it has.
         * [missing] is in the form's order, never empty, and never the name or the serving.
         */
        data class Incomplete(val form: ProductForm, val missing: List<ProductField>) : Reply

        /** Never heard of, no name, not a reply at all — or a figure that is no quantity of food. */
        data object Unusable : Reply
    }

    /** The whole path an app of this size needs. One number goes out and nothing else (D23a). */
    fun urlFor(barcode: String): String =
        "https://world.openfoodfacts.org/api/v2/product/$barcode" +
            "?fields=product_name,brands,quantity,serving_size,nutriments"

    private val json = Json { ignoreUnknownKeys = true }

    private val SERVING_GRAMS = Regex("""(\d+(?:\.\d+)?)\s*(?:g|gr|גרם|ג)\b""", RegexOption.IGNORE_CASE)

    /** The four figures, in the form's order, under the keys the database files them by. */
    private val FIGURES = listOf(
        ProductField.KCAL to "energy-kcal_100g",
        ProductField.PROTEIN to "proteins_100g",
        ProductField.CARBS to "carbohydrates_100g",
        ProductField.FAT to "fat_100g",
    )

    /**
     * A usable product, or nothing. No production code asks this any more — the scan needs [read]'s
     * third answer, the half-filled form (D40) — so it is kept only for the tests whose question is
     * whether a reply is a product at all, where [Reply.Incomplete] and [Reply.Unusable] are alike.
     */
    fun parse(barcode: String, body: String): Product? =
        (read(barcode, body) as? Reply.Usable)?.product

    fun read(barcode: String, body: String): Reply {
        val root = objectOrNull { json.parseToJsonElement(body) } ?: return Reply.Unusable

        // status 0 is how the database says it has never heard of this barcode.
        val found = root.text("status")
        if (found == "0") return Reply.Unusable

        // An entry that is there but not an object is as unreadable as one that is not there.
        val product = objectOrNull { root["product"] } ?: return Reply.Unusable
        val name = product.text("product_name")?.takeIf { it.isNotBlank() } ?: return Reply.Unusable

        // No nutriments at all is a named packet with all four figures missing, not an unknown one:
        // the name and serving are still worth keeping (D40). One that is not an object is broken.
        val nutriments = when (product["nutriments"]) {
            null, JsonNull -> JsonObject(emptyMap())
            else -> objectOrNull { product["nutriments"] } ?: return Reply.Unusable
        }

        // Missing is exactly what reads as no number — the key absent, a JSON null, blank, a word;
        // a stated 0 reads as 0.0 and is a figure like any other.
        val figures = FIGURES.associate { (field, key) -> field to nutriments.number(key) }

        // D39 before D40: a reply holding an impossible or unbelievable figure — past what a label for
        // 100 g of anything prints (D42), such as kilojoules filed under the kcal key — is not offered
        // half-filled, with the app's own refusal sitting in one box while he types into another.
        val noFood = figures.any { (field, figure) ->
            figure != null && !Product.isQuantityOfFood(field, figure)
        }
        if (noFood) return Reply.Unusable

        val brand = product.text("brands")?.takeIf { it.isNotBlank() }
        val serving = gramsIn(product.text("serving_size")) ?: gramsIn(product.text("quantity"))

        val missing = figures.filterValues { it == null }.keys.toList()
        if (missing.isNotEmpty()) {
            return Reply.Incomplete(
                form = ProductForm.prefilled(
                    barcode = barcode,
                    name = name,
                    brand = brand,
                    kcalPer100g = figures[ProductField.KCAL],
                    proteinPer100g = figures[ProductField.PROTEIN],
                    carbsPer100g = figures[ProductField.CARBS],
                    fatPer100g = figures[ProductField.FAT],
                    servingSizeG = serving,
                ),
                missing = missing,
            )
        }

        return Reply.Usable(
            Product(
                barcode = barcode,
                name = name,
                brand = brand,
                kcalPer100g = figures.getValue(ProductField.KCAL)!!,
                proteinPer100g = figures.getValue(ProductField.PROTEIN)!!,
                carbsPer100g = figures.getValue(ProductField.CARBS)!!,
                fatPer100g = figures.getValue(ProductField.FAT)!!,
                servingSizeG = serving,
            ),
        )
    }

    /** "1 serving (80 g)" and "80 ג" both mean eighty grams. Anything else means no suggestion. */
    private fun gramsIn(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        return SERVING_GRAMS.find(text)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    /** An element that is a JSON object, or null for anything else — never a throw. */
    private inline fun objectOrNull(
        element: () -> JsonElement?,
    ): JsonObject? = runCatching { element()?.jsonObject }.getOrNull()

    /**
     * A JSON null is absent, not the word: its content is the string "null", which would otherwise
     * become a packet named "null" or a brand box reading "null" — saved as his typed product and,
     * with an account, sent to Open Food Facts (D40 made both reachable).
     */
    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

    /** Some entries hold their numbers as strings. Both are accepted; neither is invented. */
    private fun JsonObject.number(key: String): Double? =
        runCatching { this[key]?.jsonPrimitive?.content?.toDoubleOrNull() }.getOrNull()
}
