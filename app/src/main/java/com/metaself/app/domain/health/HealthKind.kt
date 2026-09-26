package com.metaself.app.domain.health

/**
 * Every kind of health data the record copies (D66). Stored by name, never by ordinal.
 *
 * Eleven are simple readings — a value at a moment or over a span, kept in the shared table (D68) —
 * and carry the [unit] their values are in. Sleep and workouts have structure and tables of their own.
 */
enum class HealthKind(val unit: String?, val displayName: String) {
    STEPS("count", "Steps"),
    DISTANCE("m", "Distance"),
    ACTIVE_KCAL("kcal", "Active calories"),
    TOTAL_KCAL("kcal", "Total calories"),
    HEART_RATE("bpm", "Heart rate"),
    RESTING_HEART_RATE("bpm", "Resting heart rate"),
    HRV_RMSSD("ms", "Heart-rate variability"),
    OXYGEN_SATURATION("%", "Blood oxygen"),
    RESPIRATORY_RATE("breaths/min", "Breathing rate"),
    WEIGHT("kg", "Weight"),
    BODY_FAT("%", "Body fat"),
    SLEEP(null, "Sleep"),
    EXERCISE(null, "Workouts");

    val isReading: Boolean get() = unit != null

    companion object {
        fun parse(stored: String?): HealthKind? = entries.firstOrNull { it.name == stored }
    }
}
