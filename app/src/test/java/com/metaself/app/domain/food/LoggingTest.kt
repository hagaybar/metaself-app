package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The arithmetic of putting a food on the record.
 *
 * Two things are being protected here. One is that a number on a row says truthfully where it came
 * from, including when it was worked out from two facts that came from different places. The other
 * is that the one derivation the design forbids — inventing what one of something weighs — is not
 * reachable from anywhere, because a wrong weight propagates into every future gram-counted log of
 * that food, silently and for ever.
 */
class LoggingTest {

    private fun per100g(
        kcal: Double = 100.0,
        source: Source = Source.LABEL,
        confidence: Confidence? = null,
    ) = PerHundredGrams(
        nutrients = Nutrients(kcal, kcal / 10, kcal / 5, kcal / 25),
        provenance = Provenance(source, confidence, setAtMillis = 1_000),
    )

    private fun perUnit(
        unitName: String = "bar",
        kcal: Double = 190.0,
        source: Source = Source.TYPED,
        confidence: Confidence? = null,
    ) = PerUnit(
        unitName = unitName,
        nutrients = Nutrients(kcal, kcal / 10, kcal / 5, kcal / 25),
        provenance = Provenance(source, confidence, setAtMillis = 1_000),
    )

    private fun weighs(
        grams: Double = 45.0,
        source: Source = Source.TYPED,
        confidence: Confidence? = null,
    ) = GramsPerUnit(grams, Provenance(source, confidence, setAtMillis = 1_000))

    private fun numbers(result: LoggedFrom) = result as LoggedFrom.Numbers

    // --- Using what is known, as it stands ------------------------------------------------------

    @Test
    fun `weighing a food that knows what 100 grams are worth`() {
        val facts = FoodFacts(per100g = per100g(kcal = 422.0))

        val logged = numbers(Logging.log(facts, amount = 50.0, countedAs = CountedAs.GRAMS))

        assertThat(logged.kcal).isEqualTo(211)
        assertThat(logged.amount).isEqualTo(50.0)
        assertThat(logged.unit).isEqualTo("g")
        assertThat(logged.source).isEqualTo(Source.LABEL)
    }

    @Test
    fun `counting a food that knows what one of it is worth`() {
        val facts = FoodFacts(perUnit = perUnit(kcal = 190.0))

        val logged = numbers(Logging.log(facts, amount = 2.0, countedAs = CountedAs.UNITS))

        assertThat(logged.kcal).isEqualTo(380)
        assertThat(logged.amount).isEqualTo(2.0)
        assertThat(logged.unit).isEqualTo("bar")
        assertThat(logged.source).isEqualTo(Source.TYPED)
    }

    /**
     * A food may know all three facts and they may not agree. Nothing here reconciles them: the
     * stored number is used and the computed one is not reached for at all.
     */
    @Test
    fun `a stored number wins over one that could be computed`() {
        val facts = FoodFacts(
            // 422 per 100 g and 45 g per bar would compute to 190 per bar…
            per100g = per100g(kcal = 422.0),
            // …but the food says 200, and the food is what it says.
            perUnit = perUnit(kcal = 200.0),
            gramsPerUnit = weighs(grams = 45.0),
        )

        assertThat(numbers(Logging.log(facts, 1.0, CountedAs.UNITS)).kcal).isEqualTo(200)
    }

    // --- Filling a gap, which is the only time anything is computed ------------------------------

    @Test
    fun `weighing a food that knows only what one is worth and what one weighs`() {
        val facts = FoodFacts(perUnit = perUnit(kcal = 190.0), gramsPerUnit = weighs(grams = 45.0))

        // 90 g is two bars' worth.
        assertThat(numbers(Logging.log(facts, 90.0, CountedAs.GRAMS)).kcal).isEqualTo(380)
    }

    @Test
    fun `counting a food that knows only what 100 grams are worth and what one weighs`() {
        val facts = FoodFacts(per100g = per100g(kcal = 422.0), gramsPerUnit = weighs(grams = 45.0))

        // Two of them is 90 g, which is 380 kcal.
        assertThat(numbers(Logging.log(facts, 2.0, CountedAs.UNITS)).kcal).isEqualTo(380)
    }

    // --- What a computed number says about itself ------------------------------------------------

    /**
     * A number is only as good as its worst input. A packet's figure multiplied by a weight the
     * owner typed is his number, not the packet's, and the record says so.
     */
    @Test
    fun `a computed number carries the weaker of the two provenances`() {
        val facts = FoodFacts(
            per100g = per100g(source = Source.LABEL),
            gramsPerUnit = weighs(source = Source.TYPED),
        )

        assertThat(numbers(Logging.log(facts, 1.0, CountedAs.UNITS)).source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `a computed number carries an estimate's confidence when the estimate is the weaker`() {
        val facts = FoodFacts(
            per100g = per100g(source = Source.LABEL),
            gramsPerUnit = weighs(source = Source.AI_ESTIMATE, confidence = Confidence.LOW),
        )

        val logged = numbers(Logging.log(facts, 1.0, CountedAs.UNITS))

        assertThat(logged.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(logged.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `a computed number carries no confidence when neither input was a guess`() {
        val facts = FoodFacts(
            per100g = per100g(source = Source.LABEL),
            gramsPerUnit = weighs(source = Source.TYPED),
        )

        assertThat(numbers(Logging.log(facts, 1.0, CountedAs.UNITS)).confidence).isNull()
    }

    /** A food derived from a repeated row ranks below everything, and says so when it is logged. */
    @Test
    fun `a computed number from a repeated fact says it came from a repeat`() {
        val facts = FoodFacts(
            per100g = per100g(source = Source.REPEATED),
            gramsPerUnit = weighs(source = Source.TYPED),
        )

        assertThat(numbers(Logging.log(facts, 1.0, CountedAs.UNITS)).source)
            .isEqualTo(Source.REPEATED)
    }

    @Test
    fun `a number used as it stands carries exactly its own provenance`() {
        val facts = FoodFacts(
            per100g = per100g(source = Source.AI_ESTIMATE, confidence = Confidence.HIGH),
            gramsPerUnit = weighs(source = Source.LABEL),
        )

        val logged = numbers(Logging.log(facts, 100.0, CountedAs.GRAMS))

        assertThat(logged.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(logged.confidence).isEqualTo(Confidence.HIGH)
    }

    // --- What is not on offer, and why -----------------------------------------------------------

    /**
     * The whole reason the forbidden derivation matters. This food knows what one bar is worth and
     * what 100 g are worth, and those two would divide out to a weight — which is exactly the
     * arithmetic that must not happen, so the food simply cannot be weighed.
     */
    @Test
    fun `a food knowing both kinds of calories but no weight still cannot be weighed`() {
        val facts = FoodFacts(per100g = per100g(kcal = 422.0), perUnit = perUnit(kcal = 190.0))

        // Weighing works, because what 100 grams are worth is known outright.
        assertThat(Logging.log(facts, 90.0, CountedAs.GRAMS)).isInstanceOf(
            LoggedFrom.Numbers::class.java,
        )
        // And the food has acquired no weight along the way.
        assertThat(facts.gramsPerUnit).isNull()
    }

    @Test
    fun `a food that only knows what one is worth cannot be weighed, and says why`() {
        val facts = FoodFacts(perUnit = perUnit(unitName = "slice"))

        val result = Logging.log(facts, 90.0, CountedAs.GRAMS) as LoggedFrom.NotOnOffer

        assertThat(result.why).isEqualTo(CannotCount.NothingKnowsWhatOneWeighs("slice"))
    }

    @Test
    fun `a food that only knows what 100 grams are worth cannot be counted, and says why`() {
        val facts = FoodFacts(per100g = per100g())

        val result = Logging.log(facts, 2.0, CountedAs.UNITS) as LoggedFrom.NotOnOffer

        assertThat(result.why).isEqualTo(CannotCount.NothingSaysWhatOneIs)
    }

    // --- What each food can be offered ------------------------------------------------------------

    @Test
    fun `a food knowing only what 100 grams are worth is weighed and not counted`() {
        val facts = FoodFacts(per100g = per100g())

        assertThat(Logging.canWeigh(facts)).isNull()
        assertThat(Logging.canCount(facts)).isEqualTo(CannotCount.NothingSaysWhatOneIs)
    }

    @Test
    fun `a food knowing only what one is worth is counted and not weighed`() {
        val facts = FoodFacts(perUnit = perUnit(unitName = "egg"))

        assertThat(Logging.canCount(facts)).isNull()
        assertThat(Logging.canWeigh(facts))
            .isEqualTo(CannotCount.NothingKnowsWhatOneWeighs("egg"))
    }

    @Test
    fun `a food knowing both is offered both`() {
        val facts = FoodFacts(per100g = per100g(), perUnit = perUnit())

        assertThat(Logging.canWeigh(facts)).isNull()
        assertThat(Logging.canCount(facts)).isNull()
    }

    @Test
    fun `knowing what one weighs opens the other way of counting`() {
        val onlyMeasured = FoodFacts(per100g = per100g())
        val andWeighed = FoodFacts(per100g = per100g(), gramsPerUnit = weighs())

        assertThat(Logging.canCount(onlyMeasured)).isNotNull()
        assertThat(Logging.canCount(andWeighed)).isNull()
    }

    @Test
    fun `a food that only knows a portion is counted in portions and cannot be weighed`() {
        val facts = FoodFacts(perUnit = perUnit(unitName = FoodFacts.PORTION, kcal = 400.0))

        assertThat(Logging.canCount(facts)).isNull()
        assertThat(Logging.canWeigh(facts))
            .isEqualTo(CannotCount.NothingKnowsWhatOneWeighs("portion"))
        assertThat(numbers(Logging.log(facts, 1.0, CountedAs.UNITS)).unit).isEqualTo("portion")
    }

    // --- Arithmetic details worth pinning ---------------------------------------------------------

    @Test
    fun `the macros are scaled along with the calories`() {
        val facts = FoodFacts(
            per100g = PerHundredGrams(
                Nutrients(kcal = 400.0, proteinG = 20.0, carbsG = 40.0, fatG = 10.0),
                Provenance(Source.TYPED, null, 1_000),
            ),
        )

        val logged = numbers(Logging.log(facts, 50.0, CountedAs.GRAMS))

        assertThat(logged.kcal).isEqualTo(200)
        assertThat(logged.proteinG).isEqualTo(10)
        assertThat(logged.carbsG).isEqualTo(20)
        assertThat(logged.fatG).isEqualTo(5)
    }

    /**
     * The facts stay in fractions so that a 30 g slice does not accumulate error at every step; the
     * row gets whole numbers, once, at the end, exactly as a logged row always has.
     */
    @Test
    fun `rounding happens once, on the way onto the row`() {
        val facts = FoodFacts(per100g = per100g(kcal = 333.0))

        assertThat(numbers(Logging.log(facts, 30.0, CountedAs.GRAMS)).kcal).isEqualTo(100)
    }

    @Test
    fun `half a unit is a real amount`() {
        val facts = FoodFacts(perUnit = perUnit(kcal = 190.0))

        val logged = numbers(Logging.log(facts, 0.5, CountedAs.UNITS))

        assertThat(logged.kcal).isEqualTo(95)
        assertThat(logged.amount).isEqualTo(0.5)
    }

    @Test
    fun `eating none of it is not a thing that can be logged`() {
        val facts = FoodFacts(per100g = per100g())

        assertThrows<IllegalArgumentException> { Logging.log(facts, 0.0, CountedAs.GRAMS) }
        assertThrows<IllegalArgumentException> { Logging.log(facts, -5.0, CountedAs.GRAMS) }
    }
}
