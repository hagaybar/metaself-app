package com.metaself.app.data.time

/**
 * The moment, in milliseconds, for the things that are stamped rather than dated.
 *
 * A `fun interface` for the same reason [Today] and [CurrentHour] are: Dagger cannot inject a value
 * type, and reading a clock inside something under test is what makes it untestable.
 *
 * Most of this app works in whole days and asks [Today]. This is for the handful of records that
 * need the moment instead — when a food was made, when one of its numbers came to be believed,
 * when it was last edited — where a date would be too coarse to order two edits made in the same
 * afternoon.
 *
 * Where a caller already has the moment to hand it passes it as an argument, as the export and the
 * barcode lookup do. This exists for the repository, whose every method needs one and which would
 * otherwise have to take it on all of them.
 */
fun interface Now {
    operator fun invoke(): Long
}
