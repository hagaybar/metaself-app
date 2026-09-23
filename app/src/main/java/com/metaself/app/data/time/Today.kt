package com.metaself.app.data.time

import java.time.LocalDate

/**
 * The one place in the app that asks what today's date is.
 *
 * A `fun interface` for the same reason [CurrentYear] is one: Dagger cannot inject a value type,
 * and reading a clock inside a calculation is what makes the calculation untestable.
 *
 * The date is the LOCAL calendar date, with no small-hours grace period: something logged at 1am
 * belongs to the new day. A "your day ends at 4am" rule is a real convenience for some people and a
 * quiet confusion for everyone else; if it is wanted it should be added deliberately, not inherited.
 */
fun interface Today {
    operator fun invoke(): LocalDate
}

/** The year, from the same reading, so the app cannot hold two opinions about the date. */
fun Today.asCurrentYear(): CurrentYear = CurrentYear { this().year }
