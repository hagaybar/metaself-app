package com.metaself.app.domain.profile

/**
 * How much a normal day moves, expressed as a multiplier on resting burn.
 *
 * These are the standard Mifflin-St Jeor activity multipliers. They are coarse by nature — the gap
 * between two of them is several hundred calories — which is why the screen shows the multiplier it
 * used rather than folding it invisibly into a total.
 */
enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9),
}
