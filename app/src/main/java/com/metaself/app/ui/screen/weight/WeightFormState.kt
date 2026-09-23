package com.metaself.app.ui.screen.weight

import com.metaself.app.domain.weight.WeightReading

/**
 * What the weight editor holds, and whether it makes sense.
 *
 * Held as the string the owner typed, for the reason every other form in this app is: a field
 * mid-edit is a normal state, and "8" on the way to "80.5" is not an error worth shouting about.
 */
data class WeightFormState(
    val epochDay: Long,
    val kg: String = "",
) {

    /**
     * "NaN" parses, and every comparison with it is false, so the range alone let it through to
     * crash Save when the reading refused it (issue #32, D42). It is not a number, and is told so.
     * "Infinity" is a number past the range, and the range's message says so already.
     */
    fun error(): String? {
        val parsed = kg.trim().toDoubleOrNull()?.takeUnless { it.isNaN() }
            ?: return "A number, like 80.5"
        if (parsed <= WeightReading.MIN_KG || parsed >= WeightReading.MAX_KG) {
            return "A weight between ${WeightReading.MIN_KG.toInt()} and " +
                "${WeightReading.MAX_KG.toInt()} kg"
        }
        return null
    }

    fun toReading(): WeightReading? {
        if (error() != null) return null
        return WeightReading(epochDay = epochDay, kg = kg.trim().toDouble())
    }

    companion object {
        /** Pre-fill from a reading, for correcting one rather than adding it. */
        fun from(reading: WeightReading): WeightFormState = WeightFormState(
            epochDay = reading.epochDay,
            kg = reading.kg.toString(),
        )
    }
}
