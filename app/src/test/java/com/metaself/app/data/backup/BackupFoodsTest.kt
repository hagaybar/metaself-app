package com.metaself.app.data.backup

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.backup.Backup
import com.metaself.app.domain.backup.BackupFood
import com.metaself.app.domain.backup.BackupNutrients
import com.metaself.app.domain.backup.BackupPerUnit
import com.metaself.app.domain.backup.BackupWeight2
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.MealComponent
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.food.SavedMeal
import org.junit.jupiter.api.Test

/**
 * Foods and meals, to the file and back.
 *
 * The file is the thing that survives losing the phone, so what matters here is that it can be read
 * by a person and restored without loss — and that the awkward cases fail visibly rather than
 * restoring something subtly wrong.
 */
class BackupFoodsTest {

    private val bar = Food(
        id = 1,
        name = "Protein bar",
        alsoKnownAs = listOf("חטיף חלבון"),
        brand = "Dairyco",
        barcode = "2000012345678",
        facts = FoodFacts(
            per100g = PerHundredGrams(
                Nutrients(422.0, 33.0, 38.0, 14.0),
                Provenance(Source.LABEL, null, 1_000),
            ),
            perUnit = PerUnit(
                "bar",
                Nutrients(190.0, 15.0, 17.0, 6.0),
                Provenance(Source.TYPED, null, 1_000),
            ),
            gramsPerUnit = GramsPerUnit(45.0, Provenance(Source.TYPED, null, 1_000)),
        ),
    )

    // --- How a food is referred to --------------------------------------------------------------

    /**
     * A key rather than a database id, because `"food": "yoghurt|na"` under an item named "Yoghurt"
     * explains itself to a person reading the file, and is stable across devices.
     */
    @Test
    fun `a food is referred to by its normalised name and brand`() {
        assertThat(BackupFoods.keyOf("Yoghurt", null)).isEqualTo("yoghurt|na")
        assertThat(BackupFoods.keyOf("Protein bar", "Dairyco")).isEqualTo("protein bar|dairyco")
    }

    @Test
    fun `spellings that normalise alike produce one key`() {
        assertThat(BackupFoods.keyOf("Low-fat yoghurt", null))
            .isEqualTo(BackupFoods.keyOf("LOW  FAT  YOGHURT", null))
    }

    /** An alias is a key too, so an item naming a name that was since merged still finds it. */
    @Test
    fun `every name a food answers to is a key`() {
        assertThat(BackupFoods.everyKeyOf(bar))
            .containsExactly("protein bar|dairyco", "חטיף חלבון|dairyco")
    }

    // --- Out and back ------------------------------------------------------------------------------

    @Test
    fun `a food survives the round trip whole`() {
        val again = BackupFoods.toDomain(BackupFoods.toBackup(bar), setAtMillis = 1_000)!!

        assertThat(again.name).isEqualTo("Protein bar")
        assertThat(again.brand).isEqualTo("Dairyco")
        assertThat(again.alsoKnownAs).containsExactly("חטיף חלבון")
        assertThat(again.barcode).isEqualTo("2000012345678")
        assertThat(again.facts.per100g!!.nutrients.kcal).isEqualTo(422.0)
        assertThat(again.facts.perUnit!!.unitName).isEqualTo("bar")
        assertThat(again.facts.gramsPerUnit!!.grams).isEqualTo(45.0)
    }

    /**
     * Each of the three facts carries its own source, which is what makes the file readable in the
     * sense that matters: a person can see the per-100-g figure came off a packet and the weight
     * came out of his own head.
     */
    @Test
    fun `each of the three facts keeps its own provenance`() {
        val written = BackupFoods.toBackup(bar)

        assertThat(written.per100g!!.source).isEqualTo("LABEL")
        assertThat(written.perUnit!!.source).isEqualTo("TYPED")
        assertThat(written.gramsPerUnit!!.source).isEqualTo("TYPED")

        val again = BackupFoods.toDomain(written, 1_000)!!
        assertThat(again.facts.per100g!!.provenance.source).isEqualTo(Source.LABEL)
        assertThat(again.facts.perUnit!!.provenance.source).isEqualTo(Source.TYPED)
    }

    @Test
    fun `an estimate keeps how sure it was`() {
        val guessed = bar.copy(
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(422.0, 33.0, 38.0, 14.0),
                    Provenance(Source.AI_ESTIMATE, Confidence.LOW, 1_000),
                ),
            ),
        )

        val again = BackupFoods.toDomain(BackupFoods.toBackup(guessed), 1_000)!!

        assertThat(again.facts.per100g!!.provenance.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `a food that knows only one way keeps knowing only that`() {
        val yoghurt = Food(
            name = "Yoghurt",
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(72.0, 4.0, 6.0, 2.0),
                    Provenance(Source.TYPED, null, 1_000),
                ),
            ),
        )

        val again = BackupFoods.toDomain(BackupFoods.toBackup(yoghurt), 1_000)!!

        assertThat(again.facts.per100g).isNotNull()
        assertThat(again.facts.perUnit).isNull()
        assertThat(again.facts.gramsPerUnit).isNull()
    }

    // --- What the file is not allowed to contain ---------------------------------------------------

    /**
     * A food knowing neither way could not be logged, could not be costed inside a meal, and would
     * be a name with nothing behind it. Rejected rather than restored into that state.
     */
    @Test
    fun `a food with neither number group is refused`() {
        val nothing = BackupFood(key = "tahini|na", name = "Tahini", brand = "NA")

        assertThat(BackupFoods.toDomain(nothing, 1_000)).isNull()
    }

    @Test
    fun `numbers per unit with no unit named are refused`() {
        val noUnit = BackupFood(
            key = "bar|na",
            name = "Bar",
            brand = "NA",
            perUnit = BackupPerUnit("", 190.0, 15.0, 17.0, 6.0, "TYPED"),
        )

        assertThat(BackupFoods.toDomain(noUnit, 1_000)).isNull()
    }

    @Test
    fun `nothing weighs nothing, so a weight of zero is simply absent`() {
        val zero = BackupFood(
            key = "bar|na",
            name = "Bar",
            brand = "NA",
            per100g = BackupNutrients(422.0, 33.0, 38.0, 14.0, "LABEL"),
            gramsPerUnit = BackupWeight2(0.0, "TYPED"),
        )

        assertThat(BackupFoods.toDomain(zero, 1_000)!!.facts.gramsPerUnit).isNull()
    }

    /**
     * A source this version does not understand keeps its numbers and stops claiming where they came
     * from — the same forgiveness a stored row already gets, and for the same reason.
     */
    @Test
    fun `a source from a version this one does not understand is read honestly`() {
        val odd = BackupFood(
            key = "bar|na",
            name = "Bar",
            brand = "NA",
            per100g = BackupNutrients(422.0, 33.0, 38.0, 14.0, "SOMETHING_NEW"),
        )

        val again = BackupFoods.toDomain(odd, 1_000)!!

        assertThat(again.facts.per100g!!.nutrients.kcal).isEqualTo(422.0)
        assertThat(again.facts.per100g!!.provenance.source).isEqualTo(Source.UNRECOGNISED)
    }

    @Test
    fun `an estimate that did not say how sure it was is read honestly`() {
        val odd = BackupFood(
            key = "bar|na",
            name = "Bar",
            brand = "NA",
            per100g = BackupNutrients(422.0, 33.0, 38.0, 14.0, "AI_ESTIMATE", confidence = null),
        )

        assertThat(BackupFoods.toDomain(odd, 1_000)!!.facts.per100g!!.provenance.source)
            .isEqualTo(Source.UNRECOGNISED)
    }

    // --- Meals ---------------------------------------------------------------------------------------

    @Test
    fun `a meal is written as its name and what is in it, by key`() {
        val cucumber = Food(
            id = 1,
            name = "Cucumber",
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(16.0, 0.7, 3.6, 0.1),
                    Provenance(Source.TYPED, null, 0),
                ),
            ),
        )
        val salad = SavedMeal(
            id = 1,
            name = "Vegetable salad",
            components = listOf(MealComponent(1, cucumber, 100.0, CountedAs.GRAMS, position = 0)),
        )

        val written = BackupFoods.toBackup(salad)

        assertThat(written.name).isEqualTo("Vegetable salad")
        assertThat(written.components.single().food).isEqualTo("cucumber|na")
        assertThat(written.components.single().amount).isEqualTo(100.0)
        assertThat(written.components.single().countedAs).isEqualTo("GRAMS")
    }

    /**
     * A meal has no total of its own in the file, for the same reason it has none in the store: a
     * total would be a second answer that could disagree with its own parts.
     */
    @Test
    fun `a meal in the file has no total of its own`() {
        val written = BackupFoods.toBackup(SavedMeal(id = 1, name = "Salad"))

        assertThat(written.components).isEmpty()
        assertThat(written.name).isEqualTo("Salad")
    }

    // --- Stored numbers are read as they are (D42, issue #32) ---------------------------------------

    /**
     * The ceilings every box has since D42 are for typing, never for reading what is stored: a food
     * past every one of them — 5000 kcal and 500 g of fat per 100 g, 9 kg for one of it — and a
     * meal holding a tonne of it come back from the file exactly as they went in. The local stand-in
     * for the database round trip, which runs only in CI.
     */
    @Test
    fun `a food past every ceiling still restores`() {
        val lard = Food(
            id = 1,
            name = "Lard",
            facts = FoodFacts(
                per100g = PerHundredGrams(
                    Nutrients(5_000.0, 0.0, 0.0, 500.0),
                    Provenance(Source.TYPED, null, 1_000),
                ),
                perUnit = PerUnit(
                    "tub",
                    Nutrients(450_000.0, 0.0, 0.0, 45_000.0),
                    Provenance(Source.TYPED, null, 1_000),
                ),
                gramsPerUnit = GramsPerUnit(9_000.0, Provenance(Source.TYPED, null, 1_000)),
            ),
        )

        val file = BackupCodec.decode(
            BackupCodec.encode(Backup(foods = listOf(BackupFoods.toBackup(lard)))),
        )!!
        val again = BackupFoods.toDomain(file.foods.single(), setAtMillis = 1_000)!!

        assertThat(again.facts.per100g!!.nutrients).isEqualTo(Nutrients(5_000.0, 0.0, 0.0, 500.0))
        assertThat(again.facts.perUnit!!.nutrients)
            .isEqualTo(Nutrients(450_000.0, 0.0, 0.0, 45_000.0))
        assertThat(again.facts.gramsPerUnit!!.grams).isEqualTo(9_000.0)

        val feast = SavedMeal(
            id = 1,
            name = "Feast",
            components = listOf(MealComponent(1, lard, 1e6, CountedAs.GRAMS, position = 0)),
        )
        val written = BackupCodec.decode(
            BackupCodec.encode(Backup(savedMeals = listOf(BackupFoods.toBackup(feast)))),
        )!!.savedMeals.single().components.single()

        assertThat(written.amount).isEqualTo(1e6)
        assertThat(BackupFoods.countedAs(written.countedAs)).isEqualTo(CountedAs.GRAMS)
    }
}
