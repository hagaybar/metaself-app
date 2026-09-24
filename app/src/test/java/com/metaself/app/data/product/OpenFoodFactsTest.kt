package com.metaself.app.data.product

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.data.product.OpenFoodFacts.Reply
import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.product.ProductField
import com.metaself.app.domain.product.ProductForm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class OpenFoodFactsTest {

    /** The real reply, fetched from the live database on 2026-09-04 and committed unchanged. */
    private val bambaReply: String =
        javaClass.classLoader!!.getResourceAsStream("openfoodfacts-bamba.json")!!
            .readBytes()
            .decodeToString()

    @Test
    fun `the real reply reads into a product`() {
        val product = OpenFoodFacts.parse("7290000066318", bambaReply)!!

        assertThat(product.name).isEqualTo("במבה")
        assertThat(product.brand).isEqualTo("דוגמה")
        assertThat(product.kcalPer100g).isEqualTo(535.0)
        assertThat(product.proteinPer100g).isEqualTo(17.0)
        assertThat(product.carbsPer100g).isEqualTo(49.0)
        assertThat(product.fatPer100g).isEqualTo(30.0)
    }

    /** "1 serving (80 g)" is the package's own answer to "how much is a portion". */
    @Test
    fun `the serving size comes through in grams`() {
        assertThat(OpenFoodFacts.parse("7290000066318", bambaReply)!!.servingSizeG).isEqualTo(80.0)
    }

    @Test
    fun `a barcode the database has never seen is nothing, not an empty product`() {
        val reply = """{"code":"1234567890123","status":0,"status_verbose":"product not found"}"""

        assertThat(OpenFoodFacts.parse("1234567890123", reply)).isNull()
    }

    /**
     * An entry with a name and no nutrition at all. No product is built from it, so no meal can
     * appear to have cost nothing; since D40 (issue #30) the scan opens the label form instead, the
     * name kept and all four boxes empty, rather than a blank not-found.
     */
    @Test
    fun `a named product with no nutrition is not a product but a form with all four empty`() {
        val reply = """{"code":"1","product":{"product_name":"Something","nutriments":{}}}"""

        assertThat(OpenFoodFacts.parse("1", reply)).isNull()
        val read = OpenFoodFacts.read("1", reply) as Reply.Incomplete
        assertThat(read.missing).containsExactly(
            ProductField.KCAL, ProductField.PROTEIN, ProductField.CARBS, ProductField.FAT,
        ).inOrder()
        assertThat(read.form.name).isEqualTo("Something")
    }

    @Test
    fun `a product with no name is not usable either`() {
        val reply = """{"code":"1","product":{"product_name":"","nutriments":{"energy-kcal_100g":100}}}"""

        assertThat(OpenFoodFacts.parse("1", reply)).isNull()
    }

    /**
     * Pinned the opposite until D40 (issue #30): an omitted macro used to become 0.0 and reach the
     * food as a label fact. A figure the database does not hold is now never a zero — no product is
     * built, and the label form opens with those boxes empty and the rest as the database had it.
     */
    @Test
    fun `a macro the database leaves out is not read as zero`() {
        val reply = """{"code":"1","product":{"product_name":"Water","nutriments":{"energy-kcal_100g":0.5}}}"""

        assertThat(OpenFoodFacts.parse("1", reply)).isNull()
        val read = OpenFoodFacts.read("1", reply) as Reply.Incomplete
        assertThat(read.missing)
            .containsExactly(ProductField.PROTEIN, ProductField.CARBS, ProductField.FAT).inOrder()
        assertThat(read.form.kcalPer100g).isEqualTo("0.5")
        assertThat(read.form.proteinPer100g).isEqualTo("")
        assertThat(read.form.servingSizeG).isEqualTo("")
    }

    /** Some entries hold their numbers as strings. Accepted; never invented. */
    @Test
    fun `a number stored as a string is still a number`() {
        // All four stated, so the only thing under test is the strings (D40: a partial entry is
        // not a product at all).
        val reply = """{"code":"1","product":{"product_name":"X","nutriments":""" +
            """{"energy-kcal_100g":"250","proteins_100g":"1","carbohydrates_100g":"2","fat_100g":"3"}}}"""

        val product = OpenFoodFacts.parse("1", reply)!!
        assertThat(product.kcalPer100g).isEqualTo(250.0)
        assertThat(product.proteinPer100g).isEqualTo(1.0)
        assertThat(product.carbsPer100g).isEqualTo(2.0)
        assertThat(product.fatPer100g).isEqualTo(3.0)
    }

    @Test
    fun `rubbish is nothing, and does not throw`() {
        assertThat(OpenFoodFacts.parse("1", "not json at all")).isNull()
        assertThat(OpenFoodFacts.parse("1", "")).isNull()
    }

    /**
     * D23a: exactly one number leaves the phone. The same shape of test as the one guarding what
     * goes to the model, and for the same reason — this is the only other thing that goes out.
     */
    @Test
    fun `the request carries the barcode and nothing else`() {
        val url = OpenFoodFacts.urlFor("7290000066318")

        assertThat(url).contains("7290000066318")
        listOf("weight", "kg", "age", "sex", "goal", "target", "history", "user", "token")
            .forEach { forbidden ->
                assertThat(url.lowercase()).doesNotContain(forbidden)
            }
    }

    @Test
    fun `a hebrew quantity is read when there is no serving size`() {
        val reply = """{"code":"1","product":{"product_name":"X","quantity":"80 ג",""" +
            """"nutriments":{"energy-kcal_100g":100,"proteins_100g":1,"carbohydrates_100g":1,""" +
            """"fat_100g":1}}}"""

        assertThat(OpenFoodFacts.parse("1", reply)!!.servingSizeG).isEqualTo(80.0)
    }

    /**
     * A complete entry — name and all four figures — with [figures] replacing the defaults, so the
     * only reason to refuse it is the figure under test. Complete on purpose: since D40 (issue #30)
     * an entry with a figure left out is an incomplete reply rather than a product, and a D39 test
     * over a partial entry could pass for that reason instead of the one it names. Each value is
     * the raw JSON token, so a string is written with its quotes. Written by hand, like the other
     * small replies here.
     */
    private fun replyWith(vararg figures: Pair<String, String>): String {
        val nutriments = linkedMapOf(
            "energy-kcal_100g" to "100",
            "proteins_100g" to "1",
            "carbohydrates_100g" to "1",
            "fat_100g" to "1",
        ).apply { putAll(figures) }
            .entries.joinToString(",") { (key, value) -> "\"$key\":$value" }
        return """{"code":"1","product":{"product_name":"X","nutriments":{$nutriments}}}"""
    }

    /**
     * Rewritten for D40 (issue #30): this pinned "a macro the database leaves out is still read as
     * zero" until then. What remains true is the other half — a zero the database STATES is a
     * quantity of food, so the rule that refuses impossible figures (D39) does not refuse it.
     */
    @Test
    fun `a zero the database states is not refused`() {
        val reply = replyWith(
            "energy-kcal_100g" to "0",
            "proteins_100g" to "0",
            "carbohydrates_100g" to "0",
            "fat_100g" to "0",
        )

        val product = (OpenFoodFacts.read("1", reply) as Reply.Usable).product
        assertThat(product.kcalPer100g).isEqualTo(0.0)
        assertThat(product.proteinPer100g).isEqualTo(0.0)
        assertThat(product.carbsPer100g).isEqualTo(0.0)
        assertThat(product.fatPer100g).isEqualTo(0.0)
    }

    /**
     * "NaN" reads as a number in Kotlin, and a not-a-number figure crashed the scan (issue #31).
     * Not found sends him to type it from the packet, so the only number is one he read (D4, D39).
     * Unusable, not an incomplete form: the figure is present, and it is impossible.
     */
    @Test
    fun `a figure that is not a number makes the product not found`() {
        val reply = replyWith("fat_100g" to "\"NaN\"")

        assertThat(OpenFoodFacts.parse("1", reply)).isNull()
        assertThat(OpenFoodFacts.read("1", reply)).isEqualTo(Reply.Unusable)
    }

    /** 1e999 is a valid JSON number that overflows to infinity; the strings read as infinities too. */
    @Test
    fun `an infinite figure makes the product not found`() {
        val overflowing = replyWith("energy-kcal_100g" to "1e999")
        val infinite = replyWith("proteins_100g" to "\"Infinity\"")
        val minusInfinite = replyWith("carbohydrates_100g" to "\"-Infinity\"")

        listOf(overflowing, infinite, minusInfinite).forEach { reply ->
            assertWithMessage(reply).that(OpenFoodFacts.parse("1", reply)).isNull()
            assertWithMessage(reply).that(OpenFoodFacts.read("1", reply)).isEqualTo(Reply.Unusable)
        }
    }

    /** Small or large, below zero is no quantity of food — not "only what would round below zero". */
    @Test
    fun `a negative figure makes the product not found`() {
        val large = replyWith("fat_100g" to "-5")
        val small = replyWith("fat_100g" to "-0.3")

        listOf(large, small).forEach { reply ->
            assertWithMessage(reply).that(OpenFoodFacts.parse("1", reply)).isNull()
            assertWithMessage(reply).that(OpenFoodFacts.read("1", reply)).isEqualTo(Reply.Unusable)
        }
    }

    /**
     * A bare NaN token is not JSON, but kotlinx's reader takes an unquoted token as a literal
     * rather than refusing the body, so the fat reads as "NaN" and it is the figure rule (D39), not
     * the reader, that refuses the product. Both halves are pinned: that the reader keeps the body,
     * and that the same body with a real fat is a product. Were a later reader to refuse the body,
     * the first assertion would fail rather than let this test pass with the rule removed.
     */
    @Test
    fun `a bare NaN in the reply is read as a figure, and refused as one`() {
        val reply = replyWith("fat_100g" to "NaN")
        val nutriments = Json.parseToJsonElement(reply)
            .jsonObject["product"]!!.jsonObject["nutriments"]!!.jsonObject
        val fat = nutriments["fat_100g"]!!.jsonPrimitive

        assertThat(fat.isString).isFalse()
        assertThat(fat.content).isEqualTo("NaN")
        assertThat(OpenFoodFacts.parse("1", reply.replace("NaN", "5"))).isNotNull()
        assertThat(OpenFoodFacts.parse("1", reply)).isNull()
        assertThat(OpenFoodFacts.read("1", reply)).isEqualTo(Reply.Unusable)
    }

    // --- A figure the database leaves out is typed from the packet, never invented (D40, #30) ---

    /** Bamba as the database might hold it: every figure but [without], which is simply absent. */
    private fun bambaReplyWithout(vararg without: String): String {
        val nutriments = linkedMapOf(
            "energy-kcal_100g" to "535",
            "proteins_100g" to "17",
            "carbohydrates_100g" to "49",
            "fat_100g" to "30",
        ).apply { without.forEach { remove(it) } }
            .entries.joinToString(",") { (key, value) -> "\"$key\":$value" }
        return """{"code":"7290000066318","product":{"product_name":"במבה","brands":"דוגמה",""" +
            """"serving_size":"1 serving (80 g)","nutriments":{$nutriments}}}"""
    }

    /**
     * The case the issue names. Before D40 this was a product with fat 0.0 — saved, and since #28
     * carried to the food as a label fact that outranks what he typed. Now it is no product at all:
     * the form opens with what the database does have and the fat box empty.
     */
    @Test
    fun `a fat the database leaves out is not a product but a form with fat empty`() {
        val reply = bambaReplyWithout("fat_100g")

        assertThat(OpenFoodFacts.parse("7290000066318", reply)).isNull()
        val read = OpenFoodFacts.read("7290000066318", reply) as Reply.Incomplete
        assertThat(read.missing).containsExactly(ProductField.FAT)
        assertThat(read.form).isEqualTo(
            ProductForm.prefilled(
                barcode = "7290000066318",
                name = "במבה",
                brand = "דוגמה",
                kcalPer100g = 535.0,
                proteinPer100g = 17.0,
                carbsPer100g = 49.0,
                fatPer100g = null,
                servingSizeG = 80.0,
            ),
        )
        assertThat(read.form.fatPer100g).isEqualTo("")
        assertThat(read.form.kcalPer100g).isEqualTo("535")
        assertThat(read.form.servingSizeG).isEqualTo("80")
        assertThat(read.form.brand).isEqualTo("דוגמה")
    }

    /** Only a figure the database does not hold is missing; one it states as 0 is a real 0. */
    @Test
    fun `a zero the database states is a real zero`() {
        listOf("0", "\"0\"").forEach { zero ->
            val reply = replyWith("fat_100g" to zero)

            val read = OpenFoodFacts.read("1", reply)
            assertWithMessage("fat stated as $zero").that(read).isInstanceOf(Reply.Usable::class.java)
            assertWithMessage("fat stated as $zero")
                .that((read as Reply.Usable).product.fatPer100g).isEqualTo(0.0)
        }
    }

    /**
     * Calories follow the same rule as the other three. They used to make the packet "not found",
     * which sent him to a blank form and threw away the name and figures the database did have.
     */
    @Test
    fun `missing calories open the same form, calories empty`() {
        val reply = bambaReplyWithout("energy-kcal_100g")

        assertThat(OpenFoodFacts.parse("7290000066318", reply)).isNull()
        val read = OpenFoodFacts.read("7290000066318", reply) as Reply.Incomplete
        assertThat(read.missing).containsExactly(ProductField.KCAL)
        assertThat(read.form.kcalPer100g).isEqualTo("")
        assertThat(read.form.proteinPer100g).isEqualTo("17")
        assertThat(read.form.carbsPer100g).isEqualTo("49")
        assertThat(read.form.fatPer100g).isEqualTo("30")
        assertThat(read.form.name).isEqualTo("במבה")
    }

    /** In the form's own order, never the name or serving, which are not figures it can lack. */
    @Test
    fun `every figure the database leaves out is named, in order`() {
        val onlyCalories = bambaReplyWithout("proteins_100g", "carbohydrates_100g", "fat_100g")
        assertThat((OpenFoodFacts.read("1", onlyCalories) as Reply.Incomplete).missing)
            .containsExactly(ProductField.PROTEIN, ProductField.CARBS, ProductField.FAT).inOrder()

        val emptyNutriments =
            """{"code":"1","product":{"product_name":"Something","nutriments":{}}}"""
        val noNutriments = """{"code":"1","product":{"product_name":"Something"}}"""

        listOf(emptyNutriments, noNutriments).forEach { reply ->
            val read = OpenFoodFacts.read("1", reply) as Reply.Incomplete
            assertWithMessage(reply).that(read.missing).containsExactly(
                ProductField.KCAL, ProductField.PROTEIN, ProductField.CARBS, ProductField.FAT,
            ).inOrder()
            assertWithMessage(reply).that(read.form.name).isEqualTo("Something")
            assertWithMessage(reply).that(OpenFoodFacts.parse("1", reply)).isNull()
        }
    }

    /**
     * What the old code defaulted to 0.0 is exactly what is missing now: a JSON null, a blank, a
     * word. None of them is a figure the label states. ("NaN" is different — it reads as a number,
     * and D39 refuses it.)
     */
    @Test
    fun `a null, a blank or a word is missing, not zero`() {
        listOf("null", "\"\"", "\"unknown\"").forEach { fat ->
            val read = OpenFoodFacts.read("1", replyWith("fat_100g" to fat))

            assertWithMessage("fat as $fat").that(read).isInstanceOf(Reply.Incomplete::class.java)
            assertWithMessage("fat as $fat").that((read as Reply.Incomplete).missing)
                .containsExactly(ProductField.FAT)
            assertWithMessage("fat as $fat").that(read.form.fatPer100g).isEqualTo("")
        }
    }

    /**
     * D40's "also closed on the way", pinned. A product or nutrition entry that is present but not
     * a JSON object used to throw out of the parser — `.jsonObject` on a null, a string or an
     * array — and so out of the lookup, uncaught; it is now not found. A nutrition entry of `null` is the
     * other case: not broken, just no nutrition, so the named packet opens the form with all four
     * empty and its name, brand and serving kept.
     */
    @Test
    fun `a product or nutrition entry that is not an object is not found, and does not throw`() {
        listOf("null", "\"x\"", "[]", "1").forEach { product ->
            val reply = """{"code":"1","status":1,"product":$product}"""

            assertWithMessage("product as $product")
                .that(OpenFoodFacts.read("1", reply)).isEqualTo(Reply.Unusable)
        }
        listOf("\"x\"", "[]", "1").forEach { nutriments ->
            val reply = """{"code":"1","product":{"product_name":"X","nutriments":$nutriments}}"""

            assertWithMessage("nutriments as $nutriments")
                .that(OpenFoodFacts.read("1", reply)).isEqualTo(Reply.Unusable)
        }
    }

    @Test
    fun `a nutrition entry of null is no nutrition, the rest kept`() {
        val reply = """{"code":"1","product":{"product_name":"Something","brands":"Acme",""" +
            """"serving_size":"30 g","nutriments":null}}"""

        val read = OpenFoodFacts.read("1", reply) as Reply.Incomplete
        assertThat(read.missing).containsExactly(
            ProductField.KCAL, ProductField.PROTEIN, ProductField.CARBS, ProductField.FAT,
        ).inOrder()
        assertThat(read.form.name).isEqualTo("Something")
        assertThat(read.form.brand).isEqualTo("Acme")
        assertThat(read.form.servingSizeG).isEqualTo("30")
    }

    /**
     * A JSON null is absent, not the word "null". Before D40 a brand of null only showed on the
     * found screen; now it would pre-fill the brand box and could be saved as his typed packet and
     * sent up to Open Food Facts. A name of null is no name, so the entry is not found.
     */
    @Test
    fun `a brand or name of null never becomes the word null`() {
        val nullBrand = """{"code":"1","product":{"product_name":"X","brands":null,""" +
            """"nutriments":{"energy-kcal_100g":100}}}"""
        val form = (OpenFoodFacts.read("1", nullBrand) as Reply.Incomplete).form
        assertThat(form.brand).isEqualTo("")

        val complete = replyWith()
            .replace(""""product_name":"X"""", """"product_name":"X","brands":null""")
        assertThat(OpenFoodFacts.parse("1", complete)!!.brand).isNull()

        val nullName = """{"code":"1","product":{"product_name":null,"nutriments":{}}}"""
        assertThat(OpenFoodFacts.read("1", nullName)).isEqualTo(Reply.Unusable)
    }

    /**
     * D39 is checked before anything is called missing: a reply whose fat is impossible is not
     * offered half-filled with the protein box empty, because the app has just called one of its
     * figures no quantity of food.
     */
    @Test
    fun `a figure that is no quantity of food still makes the product not found, even with another missing`() {
        val reply = """{"code":"1","product":{"product_name":"X","nutriments":""" +
            """{"energy-kcal_100g":100,"carbohydrates_100g":1,"fat_100g":"NaN"}}}"""

        assertThat(OpenFoodFacts.read("1", reply)).isEqualTo(Reply.Unusable)
        assertThat(OpenFoodFacts.parse("1", reply)).isNull()
    }

    // --- A figure past its per-100 g ceiling is no food either (D42, issue #32) ---

    /**
     * A label for 100 g of anything prints at most 1000 kcal and 110 g of any one macro, so a figure
     * past that is as impossible as a negative one: not found, and he types it from the packet
     * (D39's path). The 3700 is the real case — kilojoules filed under the kcal key. Checked before anything is called
     * missing, as D39's rule is: a reply is not offered half-filled around a figure the app has just
     * called no food. At the ceiling it is food, and so is a rounded oil label — 923 kcal and 103 g
     * of fat per 100 g, past the chemistry but what the packet says.
     */
    @Test
    fun `a figure past its ceiling makes the product not found`() {
        val absurd = replyWith("energy-kcal_100g" to "1e12")
        val kilojoules = replyWith("energy-kcal_100g" to "3700")
        val fatOver = replyWith("fat_100g" to "110.5")

        listOf(absurd, kilojoules, fatOver).forEach { reply ->
            assertWithMessage(reply).that(OpenFoodFacts.parse("1", reply)).isNull()
            assertWithMessage(reply).that(OpenFoodFacts.read("1", reply)).isEqualTo(Reply.Unusable)
        }

        val absurdWithAMacroMissing = """{"code":"1","product":{"product_name":"X","nutriments":""" +
            """{"energy-kcal_100g":1e12,"carbohydrates_100g":1,"fat_100g":1}}}"""
        assertThat(OpenFoodFacts.read("1", absurdWithAMacroMissing)).isEqualTo(Reply.Unusable)

        val atTheCeiling = replyWith("energy-kcal_100g" to "1000", "fat_100g" to "110")
        val product = (OpenFoodFacts.read("1", atTheCeiling) as Reply.Usable).product
        assertThat(product.kcalPer100g).isEqualTo(BelievableAmount.KCAL_PER_100G)
        assertThat(product.fatPer100g).isEqualTo(BelievableAmount.MACRO_PER_100G)

        val roundedOil = replyWith("energy-kcal_100g" to "923", "fat_100g" to "103")
        val oil = (OpenFoodFacts.read("1", roundedOil) as Reply.Usable).product
        assertThat(oil.kcalPer100g).isEqualTo(923.0)
        assertThat(oil.fatPer100g).isEqualTo(103.0)
    }

    /**
     * A pack's net weight of 6 kg is no serving the label form would take, so it is not pre-filled:
     * the form would otherwise open with its own refusal waiting in a box he never typed in. A
     * believable serving is pre-filled as before.
     */
    @Test
    fun `a pre-filled serving the form would refuse is left out`() {
        fun fatMissingWithServing(serving: String) =
            """{"code":"1","product":{"product_name":"X","serving_size":"$serving",""" +
                """"nutriments":{"energy-kcal_100g":100,"proteins_100g":1,"carbohydrates_100g":1}}}"""

        val tooBig = OpenFoodFacts.read("1", fatMissingWithServing("6000 g")) as Reply.Incomplete
        assertThat(tooBig.missing).containsExactly(ProductField.FAT)
        assertThat(tooBig.form.servingSizeG).isEqualTo("")

        val believable = OpenFoodFacts.read("1", fatMissingWithServing("500 g")) as Reply.Incomplete
        assertThat(believable.form.servingSizeG).isEqualTo("500")
    }
}
