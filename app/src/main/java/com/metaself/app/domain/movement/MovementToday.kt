package com.metaself.app.domain.movement

/**
 * A day's walking, as the day screen shows it.
 *
 * Held separately from [MovementCredit] because the two answer different questions and only one of
 * them is about calories. The count is shown whenever it is known — an ordinary day included, and a
 * day before the app has learned a normal included — because activity is worth seeing for its own
 * sake (D12a). The credit is an extra line on the days that earn one.
 *
 * @property normalSteps null while there is not yet enough history to say what usual looks like.
 * @property credit null on an ordinary day, on any past day, and while still learning.
 */
data class MovementToday(
    val steps: Int,
    /**
     * False when Health Connect holds no record for this day yet.
     *
     * The first version had no such flag, so a day with nothing in it produced nothing at all and
     * the whole step display simply vanished — which early in the morning, before any walking at
     * all, is the app appearing to have lost a feature. A day with no record says so.
     */
    val recorded: Boolean = true,
    val normalSteps: Int?,
    val sessions: List<ExerciseSession> = emptyList(),
    val credit: MovementCredit? = null,
) {
    val extraSteps: Int? get() = normalSteps?.let { (steps - it).coerceAtLeast(0) }

    val aboveUsual: Boolean get() = normalSteps != null && steps > normalSteps

    /**
     * How full the bar is, against a usual day.
     *
     * Capped at one: a day that doubles his usual fills the bar and stops, because a bar that keeps
     * a huge day in scale makes every ordinary day look like nothing.
     */
    val fractionOfUsual: Float
        get() {
            val usual = normalSteps?.takeIf { it > 0 } ?: return 0f
            return (steps.toFloat() / usual).coerceIn(0f, 1f)
        }
}
