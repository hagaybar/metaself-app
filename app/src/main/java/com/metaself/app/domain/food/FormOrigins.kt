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
 *   would store it) → the stored source and confidence, since Save would leave it alone;
 * - it was accepted from a review this session → an estimate with that review's confidence;
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
     * @param accepted the groups accepted from a review this session, with its confidence.
     */
    fun of(stored: FoodFacts?, form: FoodForm, accepted: Map<FactGroup, Confidence>): Origins {
        val per100g = form.per100gFigures()?.let { figures ->
            val held = stored?.per100g
            when {
                held != null && ReplacedFacts.sameFigures(held.nutrients, figures) ->
                    held.provenance.origin()
                else -> accepted.origin(FactGroup.PER_100G)
            }
        }

        val unitName = form.unitNameAsSaved()
        val perUnit = form.perUnitFigures()?.takeIf { unitName != null }?.let { figures ->
            val held = stored?.perUnit
            when {
                held != null && held.unitName == unitName &&
                    ReplacedFacts.sameFigures(held.nutrients, figures) -> held.provenance.origin()
                else -> accepted.origin(FactGroup.PER_UNIT)
            }
        }

        // Never accepted from anything: a review cannot answer it.
        val gramsPerUnit = form.weightFigure()?.let { grams ->
            val held = stored?.gramsPerUnit
            when {
                held != null && ReplacedFacts.sameFigure(held.grams, grams) -> held.provenance.origin()
                else -> Origin(Source.TYPED, null)
            }
        }

        return Origins(per100g, perUnit, gramsPerUnit)
    }

    private fun Provenance.origin() = Origin(source, confidence)

    private fun Map<FactGroup, Confidence>.origin(group: FactGroup): Origin =
        get(group)?.let { Origin(Source.AI_ESTIMATE, it) } ?: Origin(Source.TYPED, null)
}
