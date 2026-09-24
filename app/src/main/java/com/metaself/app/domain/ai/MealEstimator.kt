package com.metaself.app.domain.ai

/**
 * Turning a description of a meal into a proposal — decision D2's narrow interface.
 *
 * One function, and nothing in its vocabulary that names a vendor. The comparison that chose OpenAI
 * showed the models level on food; the choice rested on a spending cap, and a choice made on those
 * grounds should be cheap to revisit.
 */
interface MealEstimator {

    /**
     * @param description the owner's own words, in whatever language they were written in.
     * @param moreDetail an extra sentence he added after seeing a first answer, or null.
     */
    suspend fun estimate(description: String, moreDetail: String? = null): EstimateResult
}

/**
 * What came back — or what went wrong, in terms the screen can act on.
 *
 * Failures are a closed set rather than an exception, because every one of them has a different
 * sentence to show and the same destination: decision D8's manual path. A failure this app cannot
 * name is a failure it will handle badly.
 */
sealed interface EstimateResult {

    data class Proposed(val proposal: MealProposal) : EstimateResult

    /** No key entered yet. The only failure with a fix the owner can act on immediately. */
    data object NoKey : EstimateResult

    /** The daily ceiling has been reached. Local, self-inflicted, and resets tomorrow. */
    data object CeilingReached : EstimateResult

    /** No network, or it timed out. */
    data object Unreachable : EstimateResult

    /** The provider refused: a bad key, no credit, a rate limit. [detail] is theirs, not ours. */
    data class Refused(val detail: String) : EstimateResult

    /**
     * A reply arrived and could not be understood — including one that gave a bare total instead of
     * components, which is refused deliberately rather than accepted as one vague item.
     *
     * [why] is the app's own sentence and is what the problem log gets. [answer] is the model's
     * answer as it came, when one arrived, for *Show the model's answer* — shown only, never stored
     * or logged. [dropped] names the items when the answer was read and every one of them was
     * unusable, so the screen says that rather than that nothing could be understood (issue #1).
     */
    data class Unreadable(
        val why: String,
        val answer: String? = null,
        val dropped: List<String> = emptyList(),
    ) : EstimateResult

    /**
     * The reply left out how much of something there was, even when asked again (D34).
     *
     * Every item must come with a positive amount and a unit. One without is not logged: a row that
     * names a unit and no amount cannot join a meal, and read on the day exactly like one that could
     * (issue #23). [items] are the names that came back without one, so the owner can say them.
     */
    data class AmountMissing(val items: List<String>) : EstimateResult
}
