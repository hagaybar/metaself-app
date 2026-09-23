package com.metaself.app.domain.product

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.domain.amount.BelievableAmount
import org.junit.jupiter.api.Test

class ProductFormTest {

    private val complete = ProductForm(
        barcode = "7290000066318",
        name = "במבה",
        brand = "אסם",
        kcalPer100g = "535",
        proteinPer100g = "17",
        carbsPer100g = "49",
        fatPer100g = "30",
        servingSizeG = "80",
    )

    @Test
    fun `a filled form becomes a product`() {
        val product = complete.toProduct()!!

        assertThat(product.barcode).isEqualTo("7290000066318")
        assertThat(product.name).isEqualTo("במבה")
        assertThat(product.brand).isEqualTo("אסם")
        assertThat(product.kcalPer100g).isEqualTo(535.0)
        assertThat(product.servingSizeG).isEqualTo(80.0)
    }

    @Test
    fun `a product needs a name`() {
        assertThat(complete.copy(name = "  ").errors()).containsKey(ProductField.NAME)
        assertThat(complete.copy(name = "").toProduct()).isNull()
    }

    @Test
    fun `every one of the four numbers is required`() {
        assertThat(complete.copy(kcalPer100g = "").errors()).containsKey(ProductField.KCAL)
        assertThat(complete.copy(proteinPer100g = "").errors()).containsKey(ProductField.PROTEIN)
        assertThat(complete.copy(carbsPer100g = "").errors()).containsKey(ProductField.CARBS)
        assertThat(complete.copy(fatPer100g = "").errors()).containsKey(ProductField.FAT)
    }

    @Test
    fun `a number that is not a number is refused`() {
        assertThat(complete.copy(kcalPer100g = "about 500").errors()).containsKey(ProductField.KCAL)
    }

    /** Negative is not a quantity of food. */
    @Test
    fun `a negative amount is refused`() {
        assertThat(complete.copy(fatPer100g = "-3").errors()).containsKey(ProductField.FAT)
    }

    @Test
    fun `zero is a perfectly good amount of fat`() {
        assertThat(complete.copy(fatPer100g = "0").errors()).isEmpty()
        assertThat(complete.copy(fatPer100g = "0").toProduct()!!.fatPer100g).isEqualTo(0.0)
    }

    @Test
    fun `the brand and the serving size are optional`() {
        val minimal = complete.copy(brand = "", servingSizeG = "")

        assertThat(minimal.errors()).isEmpty()
        val product = minimal.toProduct()!!
        assertThat(product.brand).isNull()
        assertThat(product.servingSizeG).isNull()
    }

    @Test
    fun `a serving size that is nonsense is refused rather than ignored`() {
        assertThat(complete.copy(servingSizeG = "a handful").errors())
            .containsKey(ProductField.SERVING)
    }

    @Test
    fun `a mid-edit field is not yet an error worth shouting about`() {
        // The screen only asks for errors once he presses Save, which is what makes this true.
        val halfTyped = complete.copy(kcalPer100g = "5")

        assertThat(halfTyped.errors()).isEmpty()
    }

    /**
     * The guarantee behind the sentence the label form now shows before anything is typed — "0.5 g
     * is kept as 0.5 g for this packet" (issue #18, D38). The guarantee is the product record's:
     * the packet's own row keeps the label's figures and, since #28, a food learned from it keeps
     * them too. The sentence says "for this packet" and not "on this food" because what is typed
     * there reaches a food only when the packet is logged.
     */
    @Test
    fun `a label's decimal figures are kept exactly`() {
        val product = complete.copy(proteinPer100g = "0.7", carbsPer100g = "3.6", fatPer100g = "0.1")
            .toProduct()!!

        assertThat(product.proteinPer100g).isEqualTo(0.7)
        assertThat(product.carbsPer100g).isEqualTo(3.6)
        assertThat(product.fatPer100g).isEqualTo(0.1)
    }

    /**
     * Kotlin reads "NaN", "Infinity", "-Infinity" and "1e999" as numbers, so a word check alone let
     * the infinite ones through (issue #31). Each is refused under the fat box alone, with the reason
     * that box already gives a negative — no new sentence for something only a paste can type — and
     * the form makes no product, so nothing is saved (D39). Since D42 (issue #32) that reason names
     * the box's ceiling.
     */
    @Test
    fun `a figure that is not a quantity is refused under its own box, and saves nothing`() {
        listOf("NaN", "Infinity", "-Infinity", "1e999", "-3").forEach { typed ->
            val form = complete.copy(fatPer100g = typed)

            assertWithMessage("fat typed as \"$typed\"").that(form.errors())
                .isEqualTo(mapOf(ProductField.FAT to fatRefused))
            assertWithMessage("fat typed as \"$typed\"").that(form.toProduct()).isNull()
        }
    }

    @Test
    fun `infinite calories are refused under the calories box`() {
        val form = complete.copy(kcalPer100g = "Infinity")

        assertThat(form.errors())
            .isEqualTo(mapOf(ProductField.KCAL to kcalRefused))
        assertThat(form.toProduct()).isNull()
    }

    /** It would otherwise open the amount box at infinity grams. */
    @Test
    fun `an infinite serving size is refused rather than suggested`() {
        val form = complete.copy(servingSizeG = "Infinity")

        assertThat(form.errors())
            .isEqualTo(mapOf(ProductField.SERVING to servingRefused))
        assertThat(form.toProduct()).isNull()
    }

    // --- Every box has a ceiling, and says so (D42, issue #32) ---

    private val kcalRefused = "Calories per 100 g, from the label (at most 1000)."
    private val fatRefused = "Fat per 100 g, in grams (at most 110)."
    private val servingRefused = "A serving size in grams (at most 5000), or leave it empty."

    /**
     * "1000.5", "110.5" and "5000.5" were saved before D42: finite and not negative. Each is refused
     * now under its own box alone, with that box's one sentence naming its ceiling, as "Infinity"
     * and "1e999" are — one message per box for every kind of wrong, so the same paste reads the
     * same as it did. Nothing is saved.
     */
    @Test
    fun `each figure box refuses Infinity, 1e999 and just past its ceiling, naming the ceiling`() {
        listOf("Infinity", "1e999", "1000.5").forEach { typed ->
            val form = complete.copy(kcalPer100g = typed)

            assertWithMessage("kcal typed as \"$typed\"").that(form.errors())
                .isEqualTo(mapOf(ProductField.KCAL to kcalRefused))
            assertWithMessage("kcal typed as \"$typed\"").that(form.toProduct()).isNull()
        }
        listOf("Infinity", "1e999", "110.5").forEach { typed ->
            val form = complete.copy(fatPer100g = typed)

            assertWithMessage("fat typed as \"$typed\"").that(form.errors())
                .isEqualTo(mapOf(ProductField.FAT to fatRefused))
            assertWithMessage("fat typed as \"$typed\"").that(form.toProduct()).isNull()
        }
        assertThat(complete.copy(proteinPer100g = "110.5").errors()).isEqualTo(
            mapOf(ProductField.PROTEIN to "Protein per 100 g, in grams (at most 110)."),
        )
        assertThat(complete.copy(carbsPer100g = "110.5").errors()).isEqualTo(
            mapOf(ProductField.CARBS to "Carbohydrate per 100 g, in grams (at most 110)."),
        )
        listOf("Infinity", "5000.5").forEach { typed ->
            val form = complete.copy(servingSizeG = typed)

            assertWithMessage("serving typed as \"$typed\"").that(form.errors())
                .isEqualTo(mapOf(ProductField.SERVING to servingRefused))
            assertWithMessage("serving typed as \"$typed\"").that(form.toProduct()).isNull()
        }
    }

    /** Exactly at every ceiling is a label, and it is kept exactly. */
    @Test
    fun `a label at every ceiling saves exactly`() {
        val atTheCeilings = complete.copy(
            kcalPer100g = "1000",
            proteinPer100g = "110",
            carbsPer100g = "110",
            fatPer100g = "110",
            servingSizeG = "5000",
        )

        assertThat(atTheCeilings.errors()).isEmpty()
        assertThat(atTheCeilings.toProduct()).isEqualTo(
            Product(
                barcode = "7290000066318",
                name = "במבה",
                brand = "אסם",
                kcalPer100g = BelievableAmount.KCAL_PER_100G,
                proteinPer100g = BelievableAmount.MACRO_PER_100G,
                carbsPer100g = BelievableAmount.MACRO_PER_100G,
                fatPer100g = BelievableAmount.MACRO_PER_100G,
                servingSizeG = BelievableAmount.GRAMS,
            ),
        )
    }

    /**
     * An oil label rounded per spoon prints about 923 kcal and 103 g of fat per 100 g — past the
     * chemistry, but what the packet says. The form takes it as printed, so he is never left typing
     * a number the label does not state and having it stored as the label's (D4, D42).
     */
    @Test
    fun `a rounded oil label is taken as printed`() {
        val oil = complete.copy(kcalPer100g = "923", fatPer100g = "103")

        assertThat(oil.errors()).isEmpty()
        assertThat(oil.toProduct()!!.kcalPer100g).isEqualTo(923.0)
        assertThat(oil.toProduct()!!.fatPer100g).isEqualTo(103.0)
    }

    /**
     * A pack's net weight of 6 kg is no serving the form would take: pre-filled, it would open with
     * a refusal waiting in a box he never typed in. Left empty instead; a believable one is kept.
     */
    @Test
    fun `prefilled leaves out a serving it would refuse`() {
        fun withServing(serving: Double) = ProductForm.prefilled(
            barcode = "1", name = "X", brand = null,
            kcalPer100g = 100.0, proteinPer100g = 1.0, carbsPer100g = 1.0, fatPer100g = null,
            servingSizeG = serving,
        )

        assertThat(withServing(6_000.0).servingSizeG).isEqualTo("")
        assertThat(withServing(80.0).servingSizeG).isEqualTo("80")
    }

    // --- Filled from the food database, a figure it left out left empty (D40, issue #30) ---

    /** What Open Food Facts gave for Bamba, less the fat — the case issue #30 names. */
    private fun bambaWithoutFat(): ProductForm = ProductForm.prefilled(
        barcode = "7290000066318",
        name = "במבה",
        brand = "אסם",
        kcalPer100g = 535.0,
        proteinPer100g = 17.0,
        carbsPer100g = 49.0,
        fatPer100g = null,
        servingSizeG = 80.0,
    )

    /**
     * A figure the database left out is not a zero; the box is empty, so the form's existing rule
     * refuses the save under that box and nowhere else until he copies it off the packet (D4, D40).
     */
    @Test
    fun `a form filled from the database leaves a missing figure empty`() {
        val form = bambaWithoutFat()

        assertThat(form.fatPer100g).isEqualTo("")
        assertThat(form.barcode).isEqualTo("7290000066318")
        assertThat(form.name).isEqualTo("במבה")
        assertThat(form.brand).isEqualTo("אסם")
        assertThat(form.kcalPer100g).isEqualTo("535")
        assertThat(form.proteinPer100g).isEqualTo("17")
        assertThat(form.carbsPer100g).isEqualTo("49")
        assertThat(form.servingSizeG).isEqualTo("80")
        assertThat(form.errors()).isEqualTo(mapOf(ProductField.FAT to fatRefused))
        assertThat(form.toProduct()).isNull()
    }

    /**
     * The boxes show what a person would have typed off the label: "17", not "17.0", and never an
     * exponent. A stated zero shows as "0" on the phone and here alike, however the platform's
     * decimal formatting happens to write a zero.
     */
    @Test
    fun `figures are filled as a person would type them`() {
        mapOf(17.0 to "17", 0.7 to "0.7", 0.0 to "0", 100.0 to "100", 1.0E-4 to "0.0001")
            .forEach { (figure, typed) ->
                val form = ProductForm.prefilled(
                    barcode = "1",
                    name = "X",
                    brand = null,
                    kcalPer100g = figure,
                    proteinPer100g = figure,
                    carbsPer100g = figure,
                    fatPer100g = figure,
                    servingSizeG = null,
                )

                assertWithMessage("kcal $figure").that(form.kcalPer100g).isEqualTo(typed)
                assertWithMessage("protein $figure").that(form.proteinPer100g).isEqualTo(typed)
                assertWithMessage("carbs $figure").that(form.carbsPer100g).isEqualTo(typed)
                assertWithMessage("fat $figure").that(form.fatPer100g).isEqualTo(typed)
                assertWithMessage("no brand").that(form.brand).isEqualTo("")
                assertWithMessage("no serving").that(form.servingSizeG).isEqualTo("")
            }

        val serving = ProductForm.prefilled(
            barcode = "1", name = "X", brand = null,
            kcalPer100g = null, proteinPer100g = null, carbsPer100g = null, fatPer100g = null,
            servingSizeG = 12.5,
        )
        assertThat(serving.servingSizeG).isEqualTo("12.5")
    }

    /**
     * The figures he did not touch are the database's label figures, exactly as a complete scan
     * would have stored them — the form's text round-trips to the same doubles.
     */
    @Test
    fun `a filled form saved as it came gives back the database's figures exactly`() {
        val form = ProductForm.prefilled(
            barcode = "1",
            name = "Milk",
            brand = null,
            kcalPer100g = 535.0,
            proteinPer100g = 0.7,
            carbsPer100g = 3.6,
            fatPer100g = 0.0,
            servingSizeG = 12.5,
        )

        assertThat(form.toProduct()).isEqualTo(
            Product(
                barcode = "1",
                name = "Milk",
                brand = null,
                kcalPer100g = 535.0,
                proteinPer100g = 0.7,
                carbsPer100g = 3.6,
                fatPer100g = 0.0,
                servingSizeG = 12.5,
            ),
        )
    }

    /**
     * Once he types the missing figure the form is indistinguishable from one he typed in full on
     * Add it yourself, so it goes through the same save and the same logging — nothing about a
     * pre-filled form is special after that.
     */
    @Test
    fun `filling the missing box makes it the same form as one typed by hand`() {
        val filled = bambaWithoutFat().copy(fatPer100g = "30")

        assertThat(filled).isEqualTo(complete)
        assertThat(filled.toProduct()).isEqualTo(
            Product(
                barcode = "7290000066318",
                name = "במבה",
                brand = "אסם",
                kcalPer100g = 535.0,
                proteinPer100g = 17.0,
                carbsPer100g = 49.0,
                fatPer100g = 30.0,
                servingSizeG = 80.0,
            ),
        )
    }
}
