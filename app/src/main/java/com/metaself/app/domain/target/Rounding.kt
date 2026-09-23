package com.metaself.app.domain.target

import kotlin.math.roundToInt

/**
 * Round to the nearest multiple of [step].
 *
 * Every number this app puts in front of the owner is rounded, and all of it goes through here so
 * that "2,150" on one screen cannot become "2,147" on another. Calorie totals use a step of 10 and
 * macro grams a step of 5: finer than that is precision the underlying estimate does not have.
 *
 * Half-way values round upwards, because [roundToInt] delegates to Math.round. No quantity in this
 * app lands on an exact half after division, so the asymmetry is invisible; if one ever does,
 * decide deliberately which way it should go rather than inheriting this.
 */
fun roundToNearest(value: Double, step: Int): Int {
    require(step > 0) { "step must be positive" }
    return (value / step).roundToInt() * step
}
