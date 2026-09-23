package com.metaself.app.ui.scan

import com.metaself.app.domain.product.Product
import java.util.Locale
import kotlin.math.roundToInt

/** What the scanning screen says. */
object ScanWording {

    /** "535 kcal · P 17 · C 49 · F 30 per 100 g" — the label, stated as the label states it. */
    fun per100g(product: Product): String =
        "${round(product.kcalPer100g)} kcal · P ${round(product.proteinPer100g)} · " +
            "C ${round(product.carbsPer100g)} · F ${round(product.fatPer100g)} per 100 g"

    /**
     * What this will actually put on the record, worked out in front of him.
     *
     * The same principle as decision D9's visible arithmetic: he is agreeing to a number, so he
     * should be able to see where it came from.
     */
    fun forAmount(product: Product, grams: Double?): String? {
        val item = grams?.let { product.toFoodItem(it) } ?: return null
        return "${item.kcal} kcal · P ${item.proteinG} · C ${item.carbsG} · F ${item.fatG}"
    }

    fun origin(fromThisPhone: Boolean): String = if (fromThisPhone) {
        "From the label, remembered on this phone. Nothing was sent."
    } else {
        "From the label, via Open Food Facts."
    }

    fun notFound(couldNotAsk: Boolean): String = if (couldNotAsk) {
        "Could not reach the food database just now. You can still describe it in words."
    } else {
        "That barcode is not in the food database. You can describe it in words instead."
    }

    private fun round(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
}
