package com.metaself.app.domain.weight

import com.metaself.app.domain.day.TEST_EPOCH_DAY

fun aReading(
    epochDay: Long = TEST_EPOCH_DAY,
    kg: Double = 80.0,
): WeightReading = WeightReading(epochDay = epochDay, kg = kg)

/**
 * A fortnight of daily readings drifting down by 100 g a day, with [spikeOnDay] days from the start
 * carrying [spikeKg] extra — a salty dinner, and the case the whole step exists to survive.
 */
fun aFortnight(
    startDay: Long = TEST_EPOCH_DAY - 13,
    startKg: Double = 80.0,
    dailyChangeKg: Double = -0.1,
    spikeOnDay: Int? = null,
    spikeKg: Double = 2.0,
): List<WeightReading> = (0..13).map { n ->
    WeightReading(
        epochDay = startDay + n,
        kg = startKg + dailyChangeKg * n + if (n == spikeOnDay) spikeKg else 0.0,
    )
}

/**
 * A month of daily readings drifting down by 100 g a day — 28 days apart at the ends, so that a
 * rate can be measured from it. [aFortnight] spans 13 and deliberately cannot be.
 */
fun aMonth(
    startDay: Long = TEST_EPOCH_DAY - 28,
    startKg: Double = 80.0,
    dailyChangeKg: Double = -0.1,
): List<WeightReading> = (0..28).map { n ->
    WeightReading(epochDay = startDay + n, kg = startKg + dailyChangeKg * n)
}
