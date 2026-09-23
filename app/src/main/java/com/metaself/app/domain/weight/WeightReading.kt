package com.metaself.app.domain.weight

/**
 * One weighing: which day, and what the scale said.
 *
 * A date rather than a timestamp, for the reason a meal carries one — "what did I weigh on Tuesday"
 * is a question about the calendar. One reading per day is the rule the store enforces; weighing
 * again replaces, because two readings on one morning are the same uncertainty and not two facts.
 *
 * The bounds are wide enough to catch a slipped finger and to hold no opinion about anybody's body,
 * exactly as the setup form's are.
 */
data class WeightReading(
    val epochDay: Long,
    val kg: Double,
) {
    init {
        require(kg > MIN_KG && kg < MAX_KG) {
            "a weight of $kg kg is outside anything this can be: " +
                "expected between $MIN_KG and $MAX_KG"
        }
    }

    companion object {
        const val MIN_KG = 20.0
        const val MAX_KG = 400.0
    }
}
