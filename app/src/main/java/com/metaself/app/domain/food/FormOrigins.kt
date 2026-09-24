package com.metaself.app.domain.food

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source

/**
 * Where each group on the food form came from, as a review is told it (D54 §2).
 *
 * "The figures" are the form as it stands when he asks, not the stored food — he may have typed since
 * opening it. Each group is worked out by the rule Save uses (§5), so what the model is told and what
 * is then stored cannot disagree:
 *
 * - its boxes equal the stored group ([ReplacedFacts.sameFigures]; the unit name exactly, as Save
 *   would store it) → the stored source and confidence, since Save would leave it alone. A box
 *   still showing a stored figure as it opened, rounded, reads as that figure, by the rule Save
 *   uses (`FoodForm.toFacts`'s `stored`, D54 §8.5);
 * - it was accepted from a review this session → an estimate with that review's confidence, or
 *   the source of the figures the review kept where that ranks lower ([AcceptedGroup.provenance]);
 * - anything else he typed → [Source.TYPED];
 * - its boxes are empty, half filled or refused → null, not known. Half-typing is not sent.
 *
 * [Source.UNRECOGNISED] is kept as it is here; the request sends it as `UNKNOWN`.
 */
object FormOrigins {

    data class Origin(val source: Source, val confidence: Confidence?)

    data class Origins(val per100g: Origin?, val perUnit: Origin?, val gramsPerUnit: Origin?)

    /**
     * @param stored the food as it is stored, or null for a food not yet made.
     * @param accepted the groups accepted from a review this session.
     */
    fun of(stored: FoodFacts?, form: FoodForm, accepted: Map<FactGroup, AcceptedGroup>): Origins {
        val per100g = form.per100gFigures(stored?.per100g?.nutrients)?.let { figures ->
            val held = stored?.per100g
            when {
                held != null && ReplacedFacts.sameFigures(held.nutrients, figures) ->
                    held.provenance.origin()
                else -> accepted.origin(FactGroup.PER_100G)
            }
        }

        val unitName = form.unitNameAsSaved()
        val perUnitFigures = form.perUnitFigures(stored?.perUnit?.nutrients)
        val perUnit = perUnitFigures?.takeIf { unitName != null }?.let { figures ->
            val held = stored?.perUnit
            when {
                held != null && held.unitName == unitName &&
                    ReplacedFacts.sameFigures(held.nutrients, figures) -> held.provenance.origin()
                else -> accepted.origin(FactGroup.PER_UNIT)
            }
        }

        // Never accepted from anything: a review cannot answer it.
        val gramsPerUnit = form.weightFigure(stored?.gramsPerUnit?.grams)?.let { grams ->
            val held = stored?.gramsPerUnit
            when {
                held != null && ReplacedFacts.sameFigure(held.grams, grams) -> held.provenance.origin()
                else -> Origin(Source.TYPED, null)
            }
        }

        return Origins(per100g, perUnit, gramsPerUnit)
    }

    private fun Provenance.origin() = Origin(source, confidence)

    private fun Map<FactGroup, AcceptedGroup>.origin(group: FactGroup): Origin =
        get(group)?.provenance(setAtMillis = 0)?.origin() ?: Origin(Source.TYPED, null)
}
