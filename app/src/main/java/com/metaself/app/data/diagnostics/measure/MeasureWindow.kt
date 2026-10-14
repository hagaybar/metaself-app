package com.metaself.app.data.diagnostics.measure

/**
 * D152: when measuring is on, from the two stored times — when it was switched on, and when it ends
 * (72 hours later, or earlier if switched off). Pure.
 */
object MeasureWindow {

    /** D152: three days. */
    const val LENGTH_MILLIS = 72L * 60 * 60 * 1000

    /** The end measuring switched on at [startedAt] gets. */
    fun endOf(startedAt: Long): Long = startedAt + LENGTH_MILLIS

    /** On at [now]: started, and its end not reached. */
    fun isOn(startedAt: Long?, until: Long?, now: Long): Boolean =
        startedAt != null && until != null && now >= startedAt && now < until

    /** Hours measured by [now]: from the start to the end or now, whichever came first; null if never started. */
    fun hoursMeasured(startedAt: Long?, until: Long?, now: Long): Double? {
        if (startedAt == null || until == null) return null
        val end = minOf(until, now)
        return (end - startedAt).coerceAtLeast(0) / 3_600_000.0
    }
}
