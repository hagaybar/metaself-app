package com.metaself.app.data.time

import java.time.LocalTime

/**
 * The hour of the day, 0 to 23.
 *
 * A `fun interface` for the same reason [Today] and [CurrentYear] are: Dagger cannot inject a value
 * type, and reading a clock inside a calculation is what makes the calculation untestable.
 *
 * Only one thing needs this — deciding whether the eating window is open, and whether it has opened
 * yet today. Everything else in this app works in whole days.
 */
fun interface CurrentHour {
    operator fun invoke(): Int
}

/** The real one. Local, like every other time in this app. */
fun systemHour(): CurrentHour = CurrentHour { LocalTime.now().hour }
