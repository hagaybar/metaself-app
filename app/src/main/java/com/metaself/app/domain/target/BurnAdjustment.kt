package com.metaself.app.domain.target

/**
 * The standing correction to what the formula thinks the owner burns.
 *
 * Kept as one number, moved a little at a time. It is added to the maintenance figure before the
 * goal's deficit is taken off, which is exactly the place the error it corrects came from: the
 * activity multiplier (D25).
 */
object BurnAdjustment {

    /**
     * The most it may move in one weekly revision.
     *
     * Under-logging looks precisely like a slow metabolism, and the correction it invites is to cut
     * the target, which invites more under-logging. Moving slowly is what makes that spiral
     * survivable: a single bad month costs a hundred calories, not six hundred.
     */
    const val MAX_STEP_KCAL = 100

    /**
     * The most it may ever be, in either direction.
     *
     * Roughly the distance between two activity buckets, twice over. A correction larger than that
     * is not a metabolism the formula failed to predict; it is a profile with something wrong in
     * it, or a log that bears no relation to the eating.
     */
    const val MAX_TOTAL_KCAL = 600

    /**
     * Where the adjustment should stand after seeing [measured].
     *
     * Returns the current value unchanged when there is nothing to go on, so a caller can compare
     * and know whether anything happened.
     */
    fun next(current: Int, measured: MeasuredBurn?): Int {
        if (measured == null) return current

        val wanted = current + measured.differenceKcal
        val stepped = wanted.coerceIn(current - MAX_STEP_KCAL, current + MAX_STEP_KCAL)
        return stepped.coerceIn(-MAX_TOTAL_KCAL, MAX_TOTAL_KCAL)
    }
}
