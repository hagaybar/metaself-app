package com.metaself.app.domain.food

import com.metaself.app.domain.portion.Portions
import java.math.BigDecimal
import java.math.MathContext

/**
 * A food counted in millilitres states its per-one worth **per 100 ml**, the way a carton prints it
 * (D56) — and keeps it stored **per one ml**, the shape D53 §3 already writes for a model's per-100 ml
 * worth. Nothing stored changes shape; only the form and the words scale, through here.
 *
 * Nothing here turns a millilitre into a gram (D4). The millilitre stays a unit name the app
 * understands, in the one unit slot a food has (decision 2): this object only moves a decimal point
 * between what is shown and what is stored.
 *
 * **Decimal shifts, never a float multiply.** 0.57 × 100 is 56.99999999999999 as a double and
 * 2.9 ÷ 100 is 0.028999999999999998; `BigDecimal.movePointRight/Left` on the double's shortest
 * decimal gives 57 and 0.029. What is shown is also rounded to [SHOWN_DIGITS] significant figures,
 * which drops only a double's last-digit noise (a stored 0.028999999999999998 shows as 2.9). A box
 * left as it opened is handed back as the stored figure itself (`FoodForm`'s rule, D54 §8.5), so
 * that rounding never reaches storage.
 */
object PerHundredMillilitres {

    /** What the per-one group is "per" for a food counted in millilitres. */
    const val PER = "100 ml"

    /** Past a double's noise and short of any figure a label or a person states. */
    private const val SHOWN_DIGITS = 15

    /** Whether a food whose unit box reads [unitName] is counted in millilitres. */
    fun applies(unitName: String?): Boolean = unitName != null && Portions.isMillilitres(unitName)

    /** A stored per-ml figure as it is shown and typed: per 100 ml. */
    fun shown(perMl: Double): Double =
        if (!perMl.isFinite()) {
            perMl * 100
        } else {
            BigDecimal.valueOf(perMl).movePointRight(2).round(MathContext(SHOWN_DIGITS)).toDouble()
        }

    /** A figure typed or shown per 100 ml, as it is stored: per one ml. */
    fun stored(per100Ml: Double): Double =
        if (!per100Ml.isFinite()) per100Ml / 100 else BigDecimal.valueOf(per100Ml).movePointLeft(2).toDouble()

    fun shown(perMl: Nutrients): Nutrients =
        Nutrients(shown(perMl.kcal), shown(perMl.proteinG), shown(perMl.carbsG), shown(perMl.fatG))

    fun stored(per100Ml: Nutrients): Nutrients = Nutrients(
        stored(per100Ml.kcal),
        stored(per100Ml.proteinG),
        stored(per100Ml.carbsG),
        stored(per100Ml.fatG),
    )

    /** What the per-one group of a food whose unit is [unitName] is said to be per: "100 ml", or the unit. */
    fun per(unitName: String): String = if (applies(unitName)) PER else unitName.trim()
}
