package com.metaself.app.domain.food

/**
 * Finding a food the owner already has.
 *
 * A plain case-insensitive contains, on purpose, and the same rule the list it replaces used. Food
 * names may be in Hebrew or in English, and anything cleverer would need a stemmer for both and
 * would fail in ways nobody could predict.
 *
 * **Every name a food answers to is searched, not only the one shown.** That is what merging two
 * duplicates buys: after joining `יוגורט` to `Yoghurt`, typing either finds the one food. Without
 * searching the aliases, the merge would keep the second name and then never use it.
 *
 * **The brand is searched too, exactly when it is printed (D41).** It was the one piece of text on a
 * food's row that typing could not find: a packet he scanned says `Dairyco` under `Milk`, and `Dairyco`
 * said nothing matched. A scan is the main way a real brand arrives, so this matters more as scanning
 * grows. `NA` — the brand of food with no brand, and almost every food's — is never searched, or the
 * box would stop narrowing at its first `a`. The brand gets the rule a name gets and no more: the
 * same plain contains, whitespace tidied as a name's already is.
 *
 * **Brand and name typed together find the food (D41, issue #33).** The row prints the brand beside
 * the name and he types what he reads, in either order — `Dairyco milk` — which no one field holds
 * whole. So a food the whole search is not in is still found when every word of the search is in
 * one of its fields: its name, an alternative name or its real brand, each word by the same plain
 * contains. Every word, not any: with any, `Dairyco bread` would list his milk and his bread, and Add
 * something would stop falling through to describing a food he does not have (D28). Decided food by
 * food, never only when the whole list missed, so what else is in the list — another food that
 * happens to match whole, a filter ticked on My foods — never changes whether a food is found.
 *
 * Words split on whitespace as [Char.isWhitespace] defines it, the test `trim` and a stored name's
 * tidying already use, and not on a regex's `\s`, which is ASCII-only and would leave a
 * non-breaking space — some keyboards type one — inside a word. The whole search is the words put
 * back together with single spaces, so `Dairyco  milk` typed with two spaces finds a food named
 * `Dairyco milk` as a whole, in the first part rather than the word part. It found nothing before:
 * every stored name is tidied to single spaces, so the doubled space was in no name. A one-word
 * search is its own whole, so the word part is skipped for it and it lists exactly what it did.
 *
 * **Found by name first.** The matcher does not rank, and neither the brand nor the words start it
 * ranking: every food a name or an alias matched comes back in the caller's order, then every food
 * only its brand matched, then every food found only word by word, each in the caller's order. So a
 * brand never pushes down a food he found by name, a word never pushes down anything found whole,
 * and every result the search gave before is still there, in the same place relative to the others.
 */
object FoodSearch {

    fun matching(foods: List<Food>, query: String): List<Food> {
        val words = wordsOf(query)
        if (words.isEmpty()) return foods
        val whole = words.joinToString(" ")
        val (byName, rest) = foods.partition { food ->
            food.everyName.any { it.contains(whole, ignoreCase = true) }
        }
        val (byBrand, unmatched) = rest.partition { food ->
            food.searchableBrand?.contains(whole, ignoreCase = true) == true
        }
        if (words.size < 2) return byName + byBrand
        return byName + byBrand + unmatched.filter { food ->
            val fields = food.everyName + listOfNotNull(food.searchableBrand)
            words.all { word -> fields.any { it.contains(word, ignoreCase = true) } }
        }
    }

    /**
     * The query's words: every run of characters that are not [Char.isWhitespace], so no word is
     * ever empty. A loop rather than `split`, which takes either a fixed list of delimiters or a
     * regex, and a regex's `\s` misses the non-breaking space. The same walk as the tidying every
     * stored name has been through, so a word never holds a space no stored name could.
     */
    private fun wordsOf(query: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        for (char in query) {
            if (char.isWhitespace()) {
                if (word.isNotEmpty()) {
                    words += word.toString()
                    word.clear()
                }
            } else {
                word.append(char)
            }
        }
        if (word.isNotEmpty()) words += word.toString()
        return words
    }

    /**
     * The same brand, searched as it reads.
     *
     * Names are always stored tidied, but a brand is not stored the same way on every path — creating
     * a food keeps it as it arrived, doubled spaces and all — so it is tidied here, at read time,
     * where every path meets.
     *
     * [FoodKeys.displayName] throws on text that tidies to nothing, and cannot here: [Food.realBrand]
     * is null unless the brand keys to something other than `na`, and a character that survives
     * keying — not whitespace, not invisible — survives tidying too. A brand of only invisible
     * characters therefore keys as no brand and is never searched, which `FoodSearchTest` pins.
     */
    private val Food.searchableBrand: String?
        get() = realBrand?.let(FoodKeys::displayName)
}
