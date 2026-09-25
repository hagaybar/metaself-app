package com.metaself.app.domain.food

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source

/**
 * A group he accepted from a review this editing session (D54 §5, as amended 2026-09-24).
 *
 * @property confidence the review's confidence for the group as it returned it.
 * @property keptFrom where the figures the review kept in this group came from — the group's source
 *   when he asked — or null when it kept none: it filled the group, or changed all four.
 */
data class AcceptedGroup(val confidence: Confidence, val keptFrom: Source? = null) {

    /**
     * What the group is stored as, with [setAtMillis] as when he stated it: **labelled by its
     * weakest member**. An estimate with the review's confidence — unless the figures it kept came
     * from something ranked below an estimate ([Source.REPEATED], [Source.UNRECOGNISED]), in which
     * case that source, with no confidence, since on a food only an estimate carries one. Downgrading
     * is always honest; upgrading never is, so a guess never lifts a figure copied off a past meal.
     * Kept figures ranked above an estimate (a label, his own) make no difference: the estimate is
     * already the weaker.
     */
    fun provenance(setAtMillis: Long): Provenance {
        val estimate = Provenance(Source.AI_ESTIMATE, confidence, setAtMillis)
        val kept = keptFrom ?: return estimate
        return if (Provenance.rankOf(kept) < estimate.rank) Provenance(kept, null, setAtMillis) else estimate
    }
}

/**
 * What one weighs, accepted from a review (D54 §12.7): stored as an estimate with [confidence].
 *
 * @property echoed the weight's figure is the one the form already held, kept under a unit the
 *   review named or renamed and he accepted: it now states what one of the model's unit weighs, so
 *   it is labelled by the weakest member — the estimate, or its own source where that ranks lower.
 */
data class WeightAccepted(val confidence: Confidence, val echoed: Boolean = false)
