package com.metaself.app.domain.portion

import java.util.Locale
import kotlin.math.roundToInt

/** A portion recovered from text: how much, and of what. */
data class ParsedPortion(val amount: Double, val unit: String)

/**
 * The rules about portions that do not care what is being portioned.
 *
 * A proposal from the model and an item already on the record are the same problem — how much was
 * there, is it counted or measured, and what does it say when it changes — and answering it twice
 * would guarantee the two answers drift.
 */
object Portions {

    /**
     * The ways the gram itself is written.
     *
     * Its own set, and folded back into [MASS_UNITS] so the two cannot drift apart. The distinction
     * matters exactly once, and it is not a fussy one: "grams" and "g" are one unit written two
     * ways, and recognising that introduces no factor at all, whereas turning a kilogram, an ounce
     * or a millilitre into grams introduces one this app does not have anywhere and is not allowed
     * to invent (D4). A row logged through this app's own arithmetic always says "g"; a row
     * described in words keeps whatever the model answered, which is why the rest are here.
     */
    private val GRAM_SPELLINGS = setOf("g", "gram", "grams", "גרם")

    /**
     * Units that are an amount of a substance rather than a number of things.
     *
     * Everything else is counted. Getting this list wrong in the countable direction is the safe
     * error: offering "2" for something measured in grams is odd but harmless, whereas offering
     * "1.5" for a pizza slice is the thing being fixed.
     */
    private val MASS_UNITS = GRAM_SPELLINGS + setOf(
        "kg", "ml", "l", "cl", "oz", "lb",
        "מ\"ל", "ליטר", "קג",
    )

    /**
     * A number, then the first word after it.
     *
     * Deliberately the FIRST pair in the string. The model writes "1 ball, ~100 g" and means one
     * ball; the gram count is its working, not its portion.
     */
    private val NUMBER_THEN_WORD = Regex("""(\d+(?:\.\d+)?)\s*([\p{L}"']+)""")

    /**
     * The proportions offered for something measured rather than counted, shared by the model's
     * proposals and by meals repeated from the record so that the two cannot offer different ones.
     */
    const val LESS = 0.75
    const val AS_IT_WAS = 1.0
    const val MORE = 1.5

    fun canScale(amount: Double, unit: String): Boolean = amount > 0.0 && unit.isNotBlank()

    /**
     * Whether this unit measures a substance rather than counting things.
     *
     * Public because the migration that derives a food from every row ever logged has to ask the
     * same question — a row measured in grams is what fills a food's per-100-g figure, and a row
     * counted in slices is what fills its per-slice one. Asking it twice would guarantee that the
     * list of what counts as a gram drifts between the screen and the conversion.
     */
    fun isMass(unit: String): Boolean = unit.trim().lowercase() in MASS_UNITS

    /**
     * Whether this unit IS the gram, however it was spelled.
     *
     * Narrower than [isMass] and answering a different question: [isMass] asks whether a substance
     * was measured out, which is what decides whether a portion can be scaled. This asks whether the
     * number on the row can be used as a number of grams, which is what anything doing arithmetic
     * with it has to know. Everything else [isMass] accepts needs a conversion, and there is none.
     */
    fun isGrams(unit: String): Boolean = unit.trim().lowercase() in GRAM_SPELLINGS

    /** What to offer for this portion: a scale, a count, or nothing. */
    fun controlFor(amount: Double, unit: String): PortionControl = when {
        !canScale(amount, unit) -> PortionControl.None
        isMass(unit) -> PortionControl.Scale
        else -> PortionControl.Count(amount.roundToInt().coerceAtLeast(1))
    }

    fun words(amount: Double, unit: String): String = "${format(amount)} $unit"

    fun format(amount: Double): String =
        if (amount % 1.0 == 0.0) {
            amount.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.1f", amount)
        }

    /**
     * Read a portion out of the words it was written as.
     *
     * Only ever used to recover what earlier versions of this app failed to keep. Anything it
     * cannot read returns null, which downstream means no control rather than a wrong one.
     */
    fun parse(text: String?): ParsedPortion? {
        if (text.isNullOrBlank()) return null
        val match = NUMBER_THEN_WORD.find(text) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        return if (amount > 0.0 && unit.isNotBlank()) ParsedPortion(amount, unit) else null
    }
}
