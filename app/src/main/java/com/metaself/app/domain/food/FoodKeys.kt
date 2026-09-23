package com.metaself.app.domain.food

import java.text.Normalizer
import java.util.Locale

/**
 * The rule that decides when two names are the same thing.
 *
 * Two foods are the same food when their [nameKey] and their [brandKey] are equal. Everything here
 * is what produces those two strings.
 *
 * **Computed in Kotlin and stored, never computed in SQL.** The SQLite that ships inside Android has
 * no ICU: its `LOWER()` and its `NOCASE` collation are ASCII-only, so a rule expressed in SQL would
 * fold `Yoghurt` and leave `Café` alone, and would do it differently on different Android versions.
 * An identity rule that behaves differently on a different phone is not an identity rule.
 *
 * **Pure, with no database and no Android in it**, because there are two places a food can come into
 * existence — being logged, and being made in the manager — plus the migration and the backup
 * restore, and all four have to normalise identically. One object, called from everywhere, is the
 * first of the three things that make that true; the repository's single creation method is the
 * second, and the unique index on (`nameKey`, `brandKey`) is the third. Convention, then one door,
 * then a lock.
 *
 * **The rule is effectively frozen once it ships.** Changing it later means a migration that
 * recomputes every stored key, and any two keys that newly collide have to be merged by hand or the
 * unique index refuses the migration.
 */
object FoodKeys {

    /** The brand of food that has no brand. Itself a brand, so the fallback is a value, not a null. */
    const val NO_BRAND = "NA"

    /** [NO_BRAND] as it keys. `NA`, `na` and `N/A` all arrive here. */
    const val NO_BRAND_KEY = "na"

    /**
     * Characters that modify the letter beside them rather than separating two words.
     *
     * In Hebrew a geresh marks a sound the alphabet has no letter for — `צ'יפס` is one word — and a
     * gershayim marks an abbreviation — `מ"ל`. Turning either into a space would split one food into
     * two. Removing them with no replacement also makes `צ'יפס` and `ציפס` one food, and `מ"ל` and
     * `מל` one unit, which is what the owner means in both cases.
     *
     * The two Latin apostrophes are here for the same reason and with the same effect: `Ben &
     * Jerry's` and `Ben & Jerrys` are one brand.
     *
     * **The plain ASCII quote is in this set, and the design that named the others did not list
     * it.** It has to be: a Hebrew keyboard has no gershayim key, so `מ"ל` typed on a phone is
     * U+0022 and not U+05F4, and the design's own worked example — `מ"ל` and `מל` are one unit —
     * would otherwise be false on every real device. This is the identical argument the
     * design already makes for listing the ASCII apostrophe beside the geresh, applied to the
     * character beside it on the same keyboard.
     *
     * The cost in English is small and checked: a quote only ever joins two words when nothing
     * separates them, so `"Greek" yoghurt` still keys as `greek yoghurt` and only `5"3` becomes
     * `53`.
     */
    private val APOSTROPHES = setOf('\'', '"', '’', '׳', '״')

    /**
     * What identity is: the normalised name.
     *
     * In this order, and the order matters — decomposing before dropping marks is what makes a
     * precomposed `é` and a typed `e` + accent the same food, and case-folding before stripping
     * punctuation means neither step has to know about the other.
     *
     * @throws IllegalArgumentException if nothing survives. A food must have a name, and a name that
     *   normalises to nothing cannot be looked up, cannot be told apart from the next such name, and
     *   would collide with every other one under the unique index.
     */
    fun nameKey(raw: String): String {
        val folded = fold(raw)
        require(folded.isNotEmpty()) { "a food must have a name: \"$raw\" normalises to nothing" }
        return folded
    }

    /**
     * The normalised brand, with no brand being the brand [NO_BRAND].
     *
     * Unlike a name, a brand that normalises away is not an error — it is the ordinary case. Almost
     * everything the owner describes in words has no brand, and it is precisely that shared `na`
     * which lets "yoghurt" typed today join "yoghurt" typed last week.
     *
     * Every way of writing it arrives at the same place. `N/A` folds to `n a`, because a slash is a
     * separator like any other punctuation — so the spaces are closed up before the comparison, and
     * `NA`, `na`, `N/A`, `N.A.` and `n a` are one brand. A brand genuinely called `N A` is that same
     * brand, which is correct rather than unfortunate: it is the one that means "none".
     */
    fun brandKey(raw: String?): String {
        if (raw.isNullOrBlank()) return NO_BRAND_KEY
        val folded = fold(raw)
        if (folded.isEmpty() || folded.filterNot { it == ' ' } == NO_BRAND_KEY) return NO_BRAND_KEY
        return folded
    }

    /**
     * The name as it is stored to be shown.
     *
     * Cleaning up is not keying. Capitals, accents, punctuation and Hebrew vowel points all carry
     * meaning on screen, so this does only the four steps that remove things nobody can see or
     * chose: compatibility-normalise, strip the invisibles, collapse whitespace, recompose.
     *
     * So `  Café  au   lait ` is stored as `Café au lait` and keys as `cafe au lait`.
     *
     * @throws IllegalArgumentException if nothing survives, for the same reason [nameKey] refuses.
     */
    fun displayName(raw: String): String {
        val cleaned = Normalizer.normalize(raw, Normalizer.Form.NFKD)
            .filterNot { it.isFormat() }
            .let(::collapseWhitespace)
            .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
        require(cleaned.isNotEmpty()) { "a food must have a name: \"$raw\" cleans up to nothing" }
        return cleaned
    }

    /**
     * The eight steps, shared by a name and a brand because the two are normalised identically.
     *
     * Returns the empty string rather than throwing: whether nothing surviving is an error is the
     * caller's question, and it is answered differently for a name than for a brand.
     */
    private fun fold(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFKD)
        // Nonspacing marks. The Hebrew-critical step: niqqud and cantillation go, so `יוֹגוּרט` and
        // `יוגורט` are one food. It folds Latin accents too, so `café` and `cafe` are one food.
        // Both are wanted.
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        // Format characters: the direction marks, the bidi isolates, the zero-width joiners, the
        // soft hyphen, the byte-order mark. Invisible, arriving by copy-and-paste, and the worst
        // possible kind of duplicate to debug.
        .filterNot { it.isFormat() }
        // Root and not the default locale: the Turkish dotless-i rule would otherwise make a food's
        // identity depend on the phone's language setting. Hebrew is caseless, so this does nothing
        // there.
        .lowercase(Locale.ROOT)
        .filterNot { it in APOSTROPHES }
        .map { if (it.isPunctuationOrSymbol()) ' ' else it }
        .joinToString("")
        .let(::collapseWhitespace)
        .let { Normalizer.normalize(it, Normalizer.Form.NFC) }

    /**
     * Every run of whitespace to one ordinary space, then trimmed.
     *
     * [Char.isWhitespace] rather than a literal space, so the non-breaking space, the en quad and
     * the rest of the Unicode spaces are whitespace too. They look like a space and are typed as
     * one.
     */
    private fun collapseWhitespace(text: String): String = buildString {
        var pendingSpace = false
        for (character in text) {
            if (character.isWhitespace()) {
                pendingSpace = isNotEmpty()
            } else {
                if (pendingSpace) append(' ')
                pendingSpace = false
                append(character)
            }
        }
    }

    private fun Char.isFormat(): Boolean = Character.getType(this) == Character.FORMAT.toInt()

    /**
     * Punctuation and symbols become separators, so `low-fat` and `low fat` are one food and
     * `yoghurt (plain)` and `yoghurt plain` are one food.
     *
     * Digits are deliberately not here: 5% yoghurt is not 3% yoghurt, and the number is the whole
     * difference.
     */
    private fun Char.isPunctuationOrSymbol(): Boolean = when (Character.getType(this).toByte()) {
        Character.CONNECTOR_PUNCTUATION,
        Character.DASH_PUNCTUATION,
        Character.START_PUNCTUATION,
        Character.END_PUNCTUATION,
        Character.INITIAL_QUOTE_PUNCTUATION,
        Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION,
        Character.MATH_SYMBOL,
        Character.CURRENCY_SYMBOL,
        Character.MODIFIER_SYMBOL,
        Character.OTHER_SYMBOL,
        -> true
        else -> false
    }
}
