package com.metaself.app.data.product

import com.metaself.app.domain.product.Product

/** An Open Food Facts account, as the write endpoint wants it. */
data class OffCredentials(val username: String, val password: String) {
    val isUsable: Boolean get() = username.isNotBlank() && password.isNotBlank()
}

/**
 * A product, as the fields Open Food Facts' write endpoint expects.
 *
 * Built as a pure map so that what is about to be sent can be asserted on without a network. The
 * privacy test over this map is the same shape as the one guarding what goes to the model, and for
 * the same reason: these are the only two things this app sends anywhere.
 *
 * Everything is per 100 g and says so — `nutrition_data_per` is not optional. Without it the server
 * would take the numbers as being per serving, and a database entry claiming 535 kcal in a serving
 * of unspecified size is worse than no entry at all.
 */
object ProductContribution {

    /** Their production server. Their own documentation demonstrates the TEST one; see below. */
    const val URL = "https://world.openfoodfacts.org/cgi/product_jqm2.pl"

    fun fieldsFor(product: Product, credentials: OffCredentials): Map<String, String> = buildMap {
        put("user_id", credentials.username)
        put("password", credentials.password)

        put("code", product.barcode)
        put("product_name", product.name)
        product.brand?.takeIf { it.isNotBlank() }?.let { put("brands", it) }
        product.servingSizeG?.takeIf { it > 0.0 }?.let { put("serving_size", "${trim(it)} g") }

        // Says what the numbers below are per. Not optional; see the note above.
        put("nutrition_data_per", "100g")

        put("nutriment_energy-kcal", trim(product.kcalPer100g))
        put("nutriment_energy-kcal_unit", "kcal")
        put("nutriment_proteins", trim(product.proteinPer100g))
        put("nutriment_proteins_unit", "g")
        put("nutriment_carbohydrates", trim(product.carbsPer100g))
        put("nutriment_carbohydrates_unit", "g")
        put("nutriment_fat", trim(product.fatPer100g))
        put("nutriment_fat_unit", "g")
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
