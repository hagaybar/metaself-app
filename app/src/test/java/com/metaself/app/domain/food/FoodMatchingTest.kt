package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

/**
 * Finding his own food for an item the model named (D53 §4), and whether that food can cost the
 * described amount with no conversion (§5).
 *
 * Every food and figure here is invented.
 */
class FoodMatchingTest {

    private val typed = Provenance(Source.TYPED, null, setAtMillis = 0)
    private val figures = Nutrients(100.0, 5.0, 10.0, 2.0)

    private fun per100g() = FoodFacts(per100g = PerHundredGrams(figures, typed))

    private fun food(
        name: String,
        id: Long = name.hashCode().toLong(),
        alsoKnownAs: List<String> = emptyList(),
        brand: String = FoodKeys.NO_BRAND,
        facts: FoodFacts = per100g(),
    ) = Food(id = id, name = name, alsoKnownAs = alsoKnownAs, brand = brand, facts = facts)

    // --- Exact --------------------------------------------------------------------------------

    @Test
    fun `the same name is an exact match`() {
        val pita = food("Pita")

        assertThat(FoodMatching.match("Pita", listOf(food("Bread"), pita)))
            .isEqualTo(FoodMatch.Exact(pita))
    }

    @Test
    fun `an alternative name is an exact match`() {
        val pita = food("Pita", alsoKnownAs = listOf("Pitta"))

        assertThat(FoodMatching.match("pitta", listOf(pita))).isEqualTo(FoodMatch.Exact(pita))
    }

    @Test
    fun `case, spacing and accents do not stop an exact match`() {
        val pita = food("Pita")
        val cafe = food("Café au lait")

        assertThat(FoodMatching.match("  PITA ", listOf(pita))).isEqualTo(FoodMatch.Exact(pita))
        assertThat(FoodMatching.match("cafe  au lait", listOf(cafe))).isEqualTo(FoodMatch.Exact(cafe))
    }

    /** Identity is name and brand, and the model names no brand: a packet is offered, not assumed. */
    @Test
    fun `a branded food is never an exact match`() {
        val milk = food("Milk", brand = "Dairyco")

        assertThat(FoodMatching.match("Milk", listOf(milk))).isEqualTo(FoodMatch.Close(milk))
    }

    /** A hidden food is not among the foods on offer, and nothing else is searched. */
    @Test
    fun `only the foods given are searched`() {
        assertThat(FoodMatching.match("Pita", listOf(food("Bread")))).isEqualTo(FoodMatch.None)
        assertThat(FoodMatching.match("Pita", emptyList())).isEqualTo(FoodMatch.None)
    }

    // --- Close --------------------------------------------------------------------------------

    @Test
    fun `with no exact match, the first food the search lists is the close one`() {
        val greek = food("Greek yoghurt")
        val drink = food("Yoghurt drink")

        assertThat(FoodMatching.match("Yoghurt", listOf(greek, drink)))
            .isEqualTo(FoodMatch.Close(greek))
    }

    @Test
    fun `an exact match is never shadowed by a close one`() {
        val greek = food("Greek yoghurt")
        val yoghurt = food("Yoghurt")

        assertThat(FoodMatching.match("Yoghurt", listOf(greek, yoghurt)))
            .isEqualTo(FoodMatch.Exact(yoghurt))
    }

    /** The search looks for the typed words inside a food's names, never the other way round. */
    @Test
    fun `a longer model name does not find a shorter food`() {
        assertThat(FoodMatching.match("Hamburger bun", listOf(food("Bun"))))
            .isEqualTo(FoodMatch.None)
    }

    @Test
    fun `nothing matches`() {
        assertThat(FoodMatching.match("Lentil soup", listOf(food("Pita"), food("Yoghurt"))))
            .isEqualTo(FoodMatch.None)
    }

    /** A blank search lists every food; a name that keys to nothing must not borrow that. */
    @Test
    fun `a name that is only punctuation or blank matches nothing`() {
        val foods = listOf(food("Pita"))

        assertThat(FoodMatching.match("!!!", foods)).isEqualTo(FoodMatch.None)
        assertThat(FoodMatching.match("   ", foods)).isEqualTo(FoodMatch.None)
    }

    // --- countedAsFor -------------------------------------------------------------------------

    private fun perSlice(weighs: Double? = null) = FoodFacts(
        perUnit = PerUnit("slice", figures, typed),
        gramsPerUnit = weighs?.let { GramsPerUnit(it, typed) },
    )

    @Test
    fun `grams are costed from a per-100 g figure`() {
        assertThat(FoodMatching.countedAsFor(food("Bun"), "g")).isEqualTo(CountedAs.GRAMS)
    }

    @Test
    fun `grams are costed from one of it and what one weighs`() {
        assertThat(FoodMatching.countedAsFor(food("Bread", facts = perSlice(weighs = 30.0)), "grams"))
            .isEqualTo(CountedAs.GRAMS)
    }

    @Test
    fun `grams are not costed from one of it alone`() {
        assertThat(FoodMatching.countedAsFor(food("Bread", facts = perSlice()), "g")).isNull()
    }

    @Test
    fun `the same unit, case and spacing aside, is counted`() {
        assertThat(FoodMatching.countedAsFor(food("Bread", facts = perSlice()), " Slice "))
            .isEqualTo(CountedAs.UNITS)
    }

    /** The spec's accepted cost: nothing singularises, in either language. */
    @Test
    fun `a plural is a different unit`() {
        assertThat(FoodMatching.countedAsFor(food("Bread", facts = perSlice()), "slices")).isNull()
    }

    @Test
    fun `a food naming no unit is counted in portions`() {
        val weighed = food(
            "Rice",
            facts = FoodFacts(
                per100g = PerHundredGrams(figures, typed),
                gramsPerUnit = GramsPerUnit(150.0, typed),
            ),
        )

        assertThat(FoodMatching.countedAsFor(weighed, "portion")).isEqualTo(CountedAs.UNITS)
        assertThat(FoodMatching.countedAsFor(weighed, "bowl")).isNull()
    }

    @Test
    fun `a piece is not costed from a per-100 g figure alone`() {
        assertThat(FoodMatching.countedAsFor(food("Bun"), "bun")).isNull()
    }

    /** Nothing turns millilitres or kilograms into grams. */
    @Test
    fun `no other measured unit is converted to grams`() {
        assertThat(FoodMatching.countedAsFor(food("Juice"), "ml")).isNull()
        assertThat(FoodMatching.countedAsFor(food("Potatoes"), "kg")).isNull()
    }

    @Test
    fun `a unit that keys to nothing is never counted`() {
        assertThat(FoodMatching.countedAsFor(food("Bread", facts = perSlice()), "\"")).isNull()
    }
}
