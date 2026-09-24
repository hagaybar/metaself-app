package com.metaself.app.data.product

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.product.Product
import org.junit.jupiter.api.Test

class ProductContributionTest {

    private val product = Product(
        barcode = "7290000066318",
        name = "במבה",
        brand = "דוגמה",
        kcalPer100g = 535.0,
        proteinPer100g = 17.0,
        carbsPer100g = 49.0,
        fatPer100g = 30.0,
        servingSizeG = 80.0,
    )

    private val credentials = OffCredentials(username = "someone", password = "a password")

    @Test
    fun `it carries the barcode, the name and the label's four numbers`() {
        val fields = ProductContribution.fieldsFor(product, credentials)

        assertThat(fields["code"]).isEqualTo("7290000066318")
        assertThat(fields["product_name"]).isEqualTo("במבה")
        assertThat(fields["brands"]).isEqualTo("דוגמה")
        assertThat(fields["nutriment_energy-kcal"]).isEqualTo("535")
        assertThat(fields["nutriment_proteins"]).isEqualTo("17")
        assertThat(fields["nutriment_carbohydrates"]).isEqualTo("49")
        assertThat(fields["nutriment_fat"]).isEqualTo("30")
    }

    /**
     * Without this the server reads the figures as being per serving, and an entry claiming 535 kcal
     * in a serving of unspecified size is worse than no entry at all.
     */
    @Test
    fun `it says the numbers are per hundred grams`() {
        assertThat(ProductContribution.fieldsFor(product, credentials)["nutrition_data_per"])
            .isEqualTo("100g")
    }

    @Test
    fun `every nutriment carries its unit`() {
        val fields = ProductContribution.fieldsFor(product, credentials)

        assertThat(fields["nutriment_energy-kcal_unit"]).isEqualTo("kcal")
        assertThat(fields["nutriment_proteins_unit"]).isEqualTo("g")
        assertThat(fields["nutriment_carbohydrates_unit"]).isEqualTo("g")
        assertThat(fields["nutriment_fat_unit"]).isEqualTo("g")
    }

    /**
     * D24: a product, and nothing about the person who scanned it. The same shape of test as the one
     * guarding what goes to the model — these are the only two things this app sends anywhere.
     */
    @Test
    fun `nothing about the owner goes with it beyond the username he is credited by`() {
        val everythingSent = ProductContribution.fieldsFor(product, credentials)
            .filterKeys { it != "user_id" && it != "password" }
            .values
            .joinToString(" ")
            .lowercase()

        listOf("weight", "kg", "height", "age", "sex", "goal", "target", "streak", "meal", "history")
            .forEach { forbidden ->
                assertThat(everythingSent).doesNotContain(forbidden)
            }
    }

    @Test
    fun `a product with no brand simply omits it`() {
        val fields = ProductContribution.fieldsFor(product.copy(brand = null), credentials)

        assertThat(fields).doesNotContainKey("brands")
    }

    /**
     * Their own tutorial demonstrates the write against world.openfoodfacts.NET, which is the test
     * instance. A contribution sent there succeeds and is discarded, and nothing in the reply would
     * tell the owner his products were going nowhere.
     */
    @Test
    fun `it posts to the real database and not to their test instance`() {
        assertThat(ProductContribution.URL).isEqualTo(
            "https://world.openfoodfacts.org/cgi/product_jqm2.pl",
        )
        assertThat(ProductContribution.URL).doesNotContain("openfoodfacts.net")
    }

    @Test
    fun `an account with nothing in it is not usable`() {
        assertThat(OffCredentials("", "").isUsable).isFalse()
        assertThat(OffCredentials("someone", "").isUsable).isFalse()
        assertThat(credentials.isUsable).isTrue()
    }
}
