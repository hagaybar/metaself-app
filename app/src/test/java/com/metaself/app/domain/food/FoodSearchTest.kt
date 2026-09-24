package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.data.food.aFood
import org.junit.jupiter.api.Test

/**
 * Finding a food he already has, by the text printed on its row (D41, issue #14).
 *
 * Pure, so JUnit 5 — `org.junit.jupiter.api.Test`, never `org.junit.Test`. The two annotations look
 * identical at the call site and the wrong one produces a test that silently never runs.
 *
 * **The brand was the one piece of text on the row that could not be searched for.** A packet he
 * scanned says `Dairyco` under `Milk`, and typing `Dairyco` said nothing matched. The rule these tests
 * pin is that what is printed is what is searched — and only what is printed, so the brand of food
 * with no brand (`NA`) never is, or every unbranded food would match the letter `a`.
 *
 * **Brand and name typed together are words, each found somewhere on the row (issue #33).** He types
 * what he reads, `Dairyco milk`, and no one field holds that whole — so a food the whole search misses
 * is still found when every word of it is in its name, an alternative name or its real brand.
 */
class FoodSearchTest {

    private val cucumber = aFood("Cucumber")
    private val milk = aFood("Milk").copy(id = 5, brand = "Dairyco")

    @Test
    fun `a food is found by its brand, whatever the case`() {
        listOf("Dairyco", "dairyco", "DAIRYCO", " dair ").forEach { query ->
            assertWithMessage("searching \"$query\"")
                .that(FoodSearch.matching(listOf(cucumber, milk), query))
                .containsExactly(milk)
        }
    }

    /**
     * A brand match must never push down a food he found by its name: everything the search returned
     * before comes back, in the same relative order, ahead of anything the brand added. The matcher
     * does not rank, and this does not start ranking — it is a stable partition.
     */
    @Test
    fun `name matches keep their places and brand-only matches follow`() {
        val cottage = aFood("Dairyco cottage")
        val yoghurt = aFood("Yoghurt").copy(alsoKnownAs = listOf("Dairyco yoghurt"))
        val cheese = aFood("Cheese").copy(brand = "Dairyco")

        assertThat(FoodSearch.matching(listOf(milk, cottage, yoghurt, cheese), "dairyco"))
            .containsExactly(cottage, yoghurt, milk, cheese).inOrder()

        // Matching by name and by brand, it is listed once, among the name matches.
        val dairycoMilk = aFood("Dairyco milk").copy(brand = "Dairyco")
        assertThat(FoodSearch.matching(listOf(milk, dairycoMilk, cheese, cottage), "dairyco"))
            .containsExactly(dairycoMilk, cottage, milk, cheese).inOrder()
    }

    /**
     * Almost every food's brand is `NA`. Searching it would make the box stop narrowing at its first
     * English letter — `a`, `n` and `na` would list every unbranded food whatever its name.
     */
    @Test
    fun `the brand of food with no brand is never searched`() {
        val foods = listOf(aFood("Cucumber"), aFood("Egg").copy(brand = "N/A"))

        listOf("NA", "na", "N/A", "a").forEach { query ->
            assertWithMessage("searching \"$query\"")
                .that(FoodSearch.matching(foods, query))
                .isEmpty()
        }
    }

    /**
     * A new food's brand is stored as it arrived, spacing and all, where a name is always tidied
     * first; so the brand is searched as it reads on the row, not as it happens to be stored.
     */
    @Test
    fun `a brand kept with untidy spacing is found as it reads`() {
        val iceCream = aFood("Ice cream").copy(brand = "  Bob   &  Jan's ")

        assertThat(FoodSearch.matching(listOf(cucumber, iceCream), "bob & jan's"))
            .containsExactly(iceCream)
        assertThat(FoodSearch.matching(listOf(cucumber, iceCream), "Jan"))
            .containsExactly(iceCream)
    }

    /**
     * The tidying the search does to a brand throws on text that tidies to nothing, and the search
     * calls it without a guard — on the argument that such a brand keys as no brand and is never
     * reached. This pins that argument: a brand pasted as nothing but invisible characters is no
     * brand, is not printed, is not searched, and does not throw.
     */
    @Test
    fun `a brand of only invisible characters is no brand and is never searched`() {
        val pasted = aFood("Egg").copy(brand = "\u200B\u200E\u2066")

        assertThat(pasted.realBrand).isNull()
        listOf("\u200B", "a").forEach { query ->
            assertWithMessage("searching \"$query\"")
                .that(FoodSearch.matching(listOf(cucumber, pasted), query))
                .isEmpty()
        }
        // Still found by its name.
        assertThat(FoodSearch.matching(listOf(cucumber, pasted), "egg")).containsExactly(pasted)
    }

    @Test
    fun `names and alternative names are still found, and a blank search returns everything in order`() {
        val yoghurt = aFood("Yoghurt").copy(alsoKnownAs = listOf("יוגורט"))
        val foods = listOf(milk, cucumber, yoghurt)

        assertThat(FoodSearch.matching(foods, "cuc")).containsExactly(cucumber)
        assertThat(FoodSearch.matching(foods, "יוגורט")).containsExactly(yoghurt)
        assertThat(FoodSearch.matching(foods, "")).containsExactly(milk, cucumber, yoghurt).inOrder()
        assertThat(FoodSearch.matching(foods, "  ")).containsExactly(milk, cucumber, yoghurt).inOrder()
    }

    // --- Brand and name typed together (issue #33) ------------------------------------------------

    /**
     * The issue itself: the row prints `Dairyco` beside `Milk`, and typing the two together found
     * nothing. A non-breaking space, which some keyboards put between words, has to split like any
     * other space — Java's ASCII `\s` would leave it one word, so it is written here as an escape.
     */
    @Test
    fun `brand and name typed together find the food, in either order, any case and any spacing`() {
        listOf(
            "Dairyco milk",
            "milk dairyco",
            "DAIRYCO  Milk",
            " dairyco\tmilk ",
            "dairyco\u00A0milk",
        ).forEach { query ->
            assertWithMessage("searching \"$query\"")
                .that(FoodSearch.matching(listOf(cucumber, milk), query))
                .containsExactly(milk)
        }
    }

    /**
     * Any word would do the opposite of what the search is for: `Dairyco bread` would list the milk and
     * a bread, and Add something would stop offering to describe the food he does not have (D28).
     */
    @Test
    fun `every word has to be found, not just one`() {
        val foods = listOf(cucumber, milk, aFood("Bread"))

        listOf("Dairyco bread", "milk bread").forEach { query ->
            assertWithMessage("searching \"$query\"")
                .that(FoodSearch.matching(foods, query))
                .isEmpty()
        }
    }

    /** A word is found in whichever field holds it — an alias, the name, a brand kept untidy. */
    @Test
    fun `each word may be found in a different name, alias or brand`() {
        val yoghurt = aFood("Yoghurt").copy(alsoKnownAs = listOf("יוגורט"), brand = "Dairyco")
        val iceCream = aFood("Ice cream").copy(brand = "  Bob   &  Jan's ")

        assertThat(FoodSearch.matching(listOf(cucumber, yoghurt), "יוגורט dairyco"))
            .containsExactly(yoghurt)
        assertThat(FoodSearch.matching(listOf(cucumber, iceCream), "jan's ice cream"))
            .containsExactly(iceCream)
    }

    /**
     * Words get the brand the whole search gets — the real one, never `NA` — or `milk na` would list
     * every unbranded milk, and a word of one letter would match nearly every food's brand.
     */
    @Test
    fun `a word is never matched against the brand of food with no brand`() {
        val foods = listOf(aFood("Milk"), aFood("Egg").copy(brand = "N/A"))

        listOf("milk na", "egg n/a", "egg na", "NA milk").forEach { query ->
            assertWithMessage("searching \"$query\"")
                .that(FoodSearch.matching(foods, query))
                .isEmpty()
        }
    }

    /**
     * The case he will actually meet: an unbranded milk beside the Dairyco one. Only a real brand is a
     * field, so the two are told apart by it — `na` finds neither, and `dairyco` leaves the unbranded
     * milk out rather than listing it on the strength of its name alone.
     */
    @Test
    fun `an unbranded food and the same food with a brand are told apart by the brand`() {
        val unbranded = aFood("Milk")
        val foods = listOf(unbranded, milk)

        assertThat(FoodSearch.matching(foods, "na milk")).isEmpty()
        assertThat(FoodSearch.matching(foods, "dairyco milk")).containsExactly(milk)
    }

    /**
     * A zero-width space — what a paste can leave between two words — is not whitespace, so it
     * neither splits the words nor is tidied away, and the search misses as it always did. Pinned
     * because D41 records it as a cost, not a defect to fix quietly.
     */
    @Test
    fun `a zero-width character between words does not split them`() {
        assertThat(FoodSearch.matching(listOf(cucumber, milk), "dairyco​milk")).isEmpty()
    }

    /**
     * Nothing found before moves: name matches, then brand matches, then foods found only word by
     * word — each part in the caller's order, each food once. The query is put back together from
     * its words for the whole search, so a doubled or non-breaking space typed inside a name finds
     * that name as a whole, ahead of a brand match, rather than falling to the word part.
     */
    @Test
    fun `word matches come after every whole-search match, in the caller's order`() {
        val cream = aFood("Cream").copy(brand = "Dairyco Milk")
        val named = aFood("Dairyco milk")
        val cheese = aFood("Cheese").copy(brand = "Dairyco", alsoKnownAs = listOf("Milk cheese"))
        val foods = listOf(milk, cream, named, cheese)

        listOf("dairyco milk", "dairyco  milk", "dairyco\u00A0milk").forEach { query ->
            assertWithMessage("searching \"$query\"")
                .that(FoodSearch.matching(foods, query))
                .containsExactly(named, cream, milk, cheese).inOrder()
        }
    }

    /**
     * Decided food by food, not only when the whole search found nothing in the list: owning a food
     * named `Dairyco milk powder` must not make `Dairyco milk` stop finding the milk branded Dairyco.
     */
    @Test
    fun `a food is found by its own words whatever else the search finds`() {
        val powder = aFood("Dairyco milk powder")

        assertThat(FoodSearch.matching(listOf(milk, powder), "dairyco milk"))
            .containsExactly(powder, milk).inOrder()
    }

    /** One word is the whole search, so the word part adds nothing and nothing moves. */
    @Test
    fun `a search of one word lists exactly what it did before`() {
        val named = aFood("Dairyco milk")
        val cream = aFood("Cream").copy(brand = "Dairyco Milk")
        val foods = listOf(milk, named, cream)

        listOf("milk", " milk ").forEach { query ->
            assertWithMessage("searching \"$query\"")
                .that(FoodSearch.matching(foods, query))
                .containsExactly(milk, named, cream).inOrder()
        }
    }
}
