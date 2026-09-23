package com.metaself.app.data.movement

import com.metaself.app.domain.movement.DayMovement
import java.time.LocalDate

/** Whether steps can be read at all, and why not when they cannot. */
enum class StepAccess {

    /** Readable. */
    GRANTED,

    /** Health Connect is there but has not been given permission. */
    NOT_PERMITTED,

    /** No Health Connect on this device, or a version too old to talk to. */
    UNAVAILABLE,
}

/**
 * Where a day's steps come from.
 *
 * An interface so that everything deciding what a step is WORTH can be tested on a machine with no
 * phone, no Health Connect and no band. The implementation behind it is deliberately thin.
 */
interface StepSource {

    suspend fun access(): StepAccess

    /**
     * One entry per day that has a record, oldest first. Days with no record are ABSENT rather than
     * zero, which is the distinction the normal-day rule depends on.
     */
    suspend fun history(from: LocalDate, to: LocalDate): List<DayMovement>
}
