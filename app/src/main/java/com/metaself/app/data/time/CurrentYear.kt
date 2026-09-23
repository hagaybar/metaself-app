package com.metaself.app.data.time

/**
 * The one place in the app that asks what year it is.
 *
 * The arithmetic needs an age, an age needs the current year, and reading a clock inside a
 * calculation is what makes a calculation untestable. This is that reading, pushed to the edge and
 * injected, so a test can pin the year without pinning a clock.
 *
 * A `fun interface` rather than an `Int` because Dagger cannot inject a bare `Int` and will not
 * honour a Kotlin default argument on an injected constructor.
 */
fun interface CurrentYear {
    operator fun invoke(): Int
}
