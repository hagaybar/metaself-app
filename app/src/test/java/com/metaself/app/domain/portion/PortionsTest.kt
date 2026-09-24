package com.metaself.app.domain.portion

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Reading a portion that was only ever written down as words.
 *
 * This exists for one reason: rows logged before the portion's numbers were kept. Every parse here
 * is a best effort at recovering something the app should not have thrown away, and the honest
 * answer for anything it cannot read is nothing at all.
 */
class PortionsTest {

    @Test
    fun `a count and its unit`() {
        assertThat(Portions.parse("2 slice")).isEqualTo(ParsedPortion(2.0, "slice"))
    }

    @Test
    fun `an approximate mass`() {
        assertThat(Portions.parse("~280 g")).isEqualTo(ParsedPortion(280.0, "g"))
    }

    @Test
    fun `no space between the number and its unit`() {
        assertThat(Portions.parse("280g")).isEqualTo(ParsedPortion(280.0, "g"))
    }

    /**
     * The model writes "1 ball, ~100 g" and means one ball. Taking the trailing gram count would
     * turn a thing the owner can count into a mass he cannot, and offering him "less / more" for
     * a mozzarella ball is the exact confusion the count control exists to avoid.
     */
    @Test
    fun `the leading figure wins when a portion states two`() {
        assertThat(Portions.parse("1 ball, ~100 g")).isEqualTo(ParsedPortion(1.0, "ball"))
    }

    @Test
    fun `a fraction`() {
        assertThat(Portions.parse("0.5 dish")).isEqualTo(ParsedPortion(0.5, "dish"))
    }

    @Test
    fun `hebrew`() {
        assertThat(Portions.parse("2 פרוסות")).isEqualTo(ParsedPortion(2.0, "פרוסות"))
    }

    @Test
    fun `words with no number in them are not a portion`() {
        assertThat(Portions.parse("a handful")).isNull()
    }

    @Test
    fun `a number with nothing after it is not a portion`() {
        assertThat(Portions.parse("2")).isNull()
    }

    @Test
    fun `nothing at all`() {
        assertThat(Portions.parse(null)).isNull()
        assertThat(Portions.parse("")).isNull()
        assertThat(Portions.parse("   ")).isNull()
    }

    @Test
    fun `whole numbers lose their decimal point`() {
        assertThat(Portions.words(2.0, "slice")).isEqualTo("2 slice")
        assertThat(Portions.words(1.5, "dish")).isEqualTo("1.5 dish")
    }

    /**
     * Every spelling of the gram is the gram, whatever case it was typed in.
     *
     * Anything doing arithmetic with a logged amount has to know whether the number IS a number of
     * grams, which is a narrower question than whether a substance was measured out.
     */
    @Test
    fun `the gram is the gram, however it is written`() {
        listOf("g", "G", "gram", "grams", " g ", "\u05d2\u05e8\u05dd").forEach {
            assertThat(Portions.isGrams(it)).isTrue()
        }
    }

    /**
     * And every other mass unit is NOT, because turning one into grams needs a factor this app does
     * not have anywhere — which is why a meal made out of a day refuses a row logged in them.
     */
    @Test
    fun `the other mass units are measured out but are not grams`() {
        listOf("kg", "ml", "l", "cl", "oz", "lb").forEach {
            assertThat(Portions.isMass(it)).isTrue()
            assertThat(Portions.isGrams(it)).isFalse()
        }
    }

    /** A per-100 ml worth is only ever multiplied by an amount in millilitres (D53 §1). */
    @Test
    fun `millilitres are recognised however spelled`() {
        listOf(
            "ml", "ML", " ml ", "millilitre", "millilitres", "milliliter", "milliliters",
            "מ\"ל", "מל",
        ).forEach {
            assertThat(Portions.isMillilitres(it)).isTrue()
            assertThat(Portions.isMass(it)).isTrue()
        }
    }

    /**
     * The model answers in the language it was asked in, so a gram comes back as a Hebrew word,
     * plural or abbreviated, its abbreviation mark typed as an apostrophe or as the Hebrew geresh.
     * Each is the gram with no factor at all (issue #1).
     */
    @Test
    fun `the gram written in Hebrew, plural or abbreviated, is the gram`() {
        listOf(
            "גרם", "גרמים", "גר", "גר'", "גר׳", "ג'", "ג׳", "gr", "gr.", "g.", "גרם.",
        ).forEach {
            assertThat(Portions.isGrams(it)).isTrue()
            assertThat(Portions.isMass(it)).isTrue()
        }
    }

    /** The same for the millilitre, its double mark typed as a quote, the gershayim or two apostrophes. */
    @Test
    fun `the millilitre written in Hebrew, however it is marked, is the millilitre`() {
        listOf(
            "מ\"ל", "מ״ל", "מ''ל", "מ׳׳ל", "מל", "מיליליטר", "מיליליטרים", "mL", "ml.",
        ).forEach {
            assertThat(Portions.isMillilitres(it)).isTrue()
            assertThat(Portions.isMass(it)).isTrue()
        }
    }

    /**
     * Kilograms and litres in Hebrew are measured out, and are NOT grams or millilitres: nothing
     * here multiplies by 1000 (D4), so a per-100 worth cannot be costed against them.
     */
    @Test
    fun `kilograms and litres in Hebrew are measured, but are neither grams nor millilitres`() {
        listOf(
            "קילו", "קילוגרם", "קילוגרמים", "ק\"ג", "ק״ג", "קג", "kilo", "kilogram", "kilograms",
            "ליטר", "ליטרים", "ל'", "ל׳", "litre", "liter", "litres", "liters",
        ).forEach {
            assertThat(Portions.isMass(it)).isTrue()
            assertThat(Portions.isGrams(it)).isFalse()
            assertThat(Portions.isMillilitres(it)).isFalse()
        }
    }

    /** A cup is counted, in either language: a per-100 worth of one means nothing (D53 §2). */
    @Test
    fun `a cup is counted, in either language`() {
        listOf("cup", "כוס", "כוסות", "יחידה").forEach {
            assertThat(Portions.isMass(it)).isFalse()
        }
    }

    @Test
    fun `millilitres are not grams`() {
        assertThat(Portions.isGrams("ml")).isFalse()
        assertThat(Portions.isMillilitres("g")).isFalse()
        assertThat(Portions.isMillilitres("l")).isFalse()
        assertThat(Portions.isMillilitres("slice")).isFalse()
    }

    @Test
    fun `a counted unit is neither`() {
        assertThat(Portions.isMass("slice")).isFalse()
        assertThat(Portions.isGrams("slice")).isFalse()
    }
}
