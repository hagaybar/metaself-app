package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Locale

/**
 * The rule that decides when two names are the same thing.
 *
 * Every one of these is a duplicate the owner would otherwise have to merge by hand, or a
 * distinction he would lose without being asked. The rule is deliberately dumb — no stemming, no
 * plurals, no transliteration, no fuzzy matching of any kind — so what it DOES fold has to be
 * exactly right, and what it refuses to fold has to stay refused.
 *
 * The rule is effectively frozen once it ships: changing it later means recomputing every stored
 * key, and any two keys that newly collide must be merged by hand before the migration will run.
 * That is why these tests are specific about cases nobody has hit yet.
 */
class FoodKeysTest {

    // --- Hebrew, which is the reason most of this rule exists -----------------------------------

    /**
     * Niqqud are nonspacing marks. The same word typed with and without vowel points is one word to
     * a reader and would otherwise be two foods that look identical in the list.
     */
    @Test
    fun `vowel points make no difference`() {
        assertThat(FoodKeys.nameKey("יוֹגוּרט")).isEqualTo(FoodKeys.nameKey("יוגורט"))
    }

    @Test
    fun `cantillation marks make no difference`() {
        assertThat(FoodKeys.nameKey("לֶ֖חֶם")).isEqualTo(FoodKeys.nameKey("לחם"))
    }

    /**
     * In Hebrew a geresh modifies the letter before it rather than separating two words, so
     * `צ'יפס` is one word. Replacing it with a space would split the food in half.
     */
    @Test
    fun `a geresh is removed rather than replaced by a space`() {
        assertThat(FoodKeys.nameKey("צ'יפס")).isEqualTo(FoodKeys.nameKey("ציפס"))
    }

    @Test
    fun `a gershayim is removed rather than replaced by a space`() {
        assertThat(FoodKeys.nameKey("מ״ל")).isEqualTo(FoodKeys.nameKey("מל"))
    }

    /**
     * A Hebrew keyboard has no gershayim key, so the character that arrives is the plain ASCII
     * quote. Treating it as ordinary punctuation would turn `מ"ל` into two words and make it
     * a different food from `מל`, which is the case the rule exists to fold.
     */
    @Test
    fun `an ASCII quote is the gershayim a Hebrew keyboard types`() {
        assertThat(FoodKeys.nameKey("מ\"ל")).isEqualTo(FoodKeys.nameKey("מל"))
        assertThat(FoodKeys.nameKey("מ\"ל")).isEqualTo(FoodKeys.nameKey("מ״ל"))
    }

    /**
     * What removing the quote costs in English, checked rather than assumed. A quote only ever
     * joins two words when nothing already separates them.
     */
    @Test
    fun `removing the quote does not join two English words`() {
        assertThat(FoodKeys.nameKey("\"Greek\" yoghurt")).isEqualTo("greek yoghurt")
    }

    @Test
    fun `a typographic apostrophe folds with a plain one`() {
        assertThat(FoodKeys.nameKey("צ’יפס")).isEqualTo(FoodKeys.nameKey("צ'יפס"))
    }

    /** A Hebrew presentation form is a compatibility spelling of an ordinary letter. */
    @Test
    fun `a Hebrew presentation form folds onto the ordinary letters`() {
        assertThat(FoodKeys.nameKey("שׁלום")).isEqualTo(FoodKeys.nameKey("שׁלום"))
    }

    /**
     * Direction marks arrive by copy-and-paste and are invisible on screen. A food that differs
     * from another only by a character nobody can see is the worst possible duplicate to debug.
     */
    @Test
    fun `an invisible direction mark makes no difference`() {
        assertThat(FoodKeys.nameKey("‏יוגורט‎")).isEqualTo(FoodKeys.nameKey("יוגורט"))
    }

    @Test
    fun `a zero-width joiner makes no difference`() {
        assertThat(FoodKeys.nameKey("yog‍urt")).isEqualTo(FoodKeys.nameKey("yogurt"))
    }

    @Test
    fun `a soft hyphen makes no difference`() {
        assertThat(FoodKeys.nameKey("yog­urt")).isEqualTo(FoodKeys.nameKey("yogurt"))
    }

    @Test
    fun `a byte order mark makes no difference`() {
        assertThat(FoodKeys.nameKey("﻿yogurt")).isEqualTo(FoodKeys.nameKey("yogurt"))
    }

    // --- Case, and the one locale that would have broken it ------------------------------------

    @Test
    fun `capitals make no difference`() {
        assertThat(FoodKeys.nameKey("Greek Yoghurt")).isEqualTo(FoodKeys.nameKey("greek yoghurt"))
    }

    /**
     * Turkish lowercases `I` to a dotless `ı`. Case-folding at the default locale would make a
     * food's identity depend on the phone's language setting, so that the same yoghurt typed on a
     * phone set to Turkish would be a different food from the one typed the day before.
     */
    @Test
    fun `identity does not depend on the phone's language`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val inTurkish = FoodKeys.nameKey("PITA")
            Locale.setDefault(Locale.ENGLISH)
            val inEnglish = FoodKeys.nameKey("PITA")
            assertThat(inTurkish).isEqualTo(inEnglish)
            assertThat(inTurkish).isEqualTo("pita")
        } finally {
            Locale.setDefault(original)
        }
    }

    // --- Accents, punctuation, whitespace ------------------------------------------------------

    @Test
    fun `Latin accents fold`() {
        assertThat(FoodKeys.nameKey("café")).isEqualTo(FoodKeys.nameKey("cafe"))
    }

    @Test
    fun `a precomposed accent and a combining one are the same name`() {
        assertThat(FoodKeys.nameKey("café")).isEqualTo(FoodKeys.nameKey("café"))
    }

    @Test
    fun `a hyphen and a space are the same separator`() {
        assertThat(FoodKeys.nameKey("low-fat")).isEqualTo(FoodKeys.nameKey("low fat"))
    }

    @Test
    fun `brackets are separators too`() {
        assertThat(FoodKeys.nameKey("yoghurt (plain)")).isEqualTo(FoodKeys.nameKey("yoghurt plain"))
    }

    @Test
    fun `a percent sign is a symbol and separates`() {
        assertThat(FoodKeys.nameKey("yoghurt 5%")).isEqualTo(FoodKeys.nameKey("yoghurt 5"))
    }

    @Test
    fun `runs of whitespace collapse and the ends are trimmed`() {
        assertThat(FoodKeys.nameKey("  greek   yoghurt  ")).isEqualTo("greek yoghurt")
    }

    @Test
    fun `a non-breaking space is whitespace`() {
        assertThat(FoodKeys.nameKey("greek yoghurt")).isEqualTo("greek yoghurt")
    }

    @Test
    fun `a tab is whitespace`() {
        assertThat(FoodKeys.nameKey("greek\tyoghurt")).isEqualTo("greek yoghurt")
    }

    @Test
    fun `full-width Latin folds onto the ordinary letters`() {
        assertThat(FoodKeys.nameKey("ｙｏｇｕｒｔ")).isEqualTo("yogurt")
    }

    @Test
    fun `a ligature folds onto its letters`() {
        assertThat(FoodKeys.nameKey("waﬄe")).isEqualTo(FoodKeys.nameKey("waffle"))
    }

    @Test
    fun `a superscript digit folds onto the ordinary one`() {
        assertThat(FoodKeys.nameKey("omega³")).isEqualTo(FoodKeys.nameKey("omega3"))
    }

    // --- What the rule deliberately refuses to do ----------------------------------------------

    /**
     * No stemming and no plural folding. Two foods, and the merge feature is how they become one.
     * Guessing here would be worse: `crisps` and `crisp` are arguably the same, `oats` and `oat`
     * are, and `chip` and `chips` need not be — and nothing in the data can tell them apart.
     */
    @Test
    fun `a plural is a different food`() {
        assertThat(FoodKeys.nameKey("egg")).isNotEqualTo(FoodKeys.nameKey("eggs"))
    }

    @Test
    fun `a spelling variant is a different food`() {
        assertThat(FoodKeys.nameKey("yogurt")).isNotEqualTo(FoodKeys.nameKey("yoghurt"))
    }

    @Test
    fun `two languages are two foods`() {
        assertThat(FoodKeys.nameKey("יוגורט")).isNotEqualTo(FoodKeys.nameKey("yoghurt"))
    }

    /** Digits are not punctuation and carry meaning: 5% yoghurt is not 3% yoghurt. */
    @Test
    fun `digits survive`() {
        assertThat(FoodKeys.nameKey("yoghurt 5")).isNotEqualTo(FoodKeys.nameKey("yoghurt 3"))
    }

    // --- A name has to be a name ----------------------------------------------------------------

    @Test
    fun `a name that is only punctuation is refused`() {
        assertThrows<IllegalArgumentException> { FoodKeys.nameKey("---") }
    }

    @Test
    fun `a blank name is refused`() {
        assertThrows<IllegalArgumentException> { FoodKeys.nameKey("   ") }
    }

    @Test
    fun `a name of nothing but invisibles is refused`() {
        assertThrows<IllegalArgumentException> { FoodKeys.nameKey("‎‏") }
    }

    // --- Brands ---------------------------------------------------------------------------------

    /**
     * `NA` is itself a brand — the brand of food that has no brand — which is what lets "yoghurt"
     * typed today join "yoghurt" typed last week. So the fallback is a value, never a null.
     */
    @Test
    fun `no brand is the brand NA`() {
        assertThat(FoodKeys.brandKey(null)).isEqualTo("na")
        assertThat(FoodKeys.brandKey("")).isEqualTo("na")
        assertThat(FoodKeys.brandKey("   ")).isEqualTo("na")
    }

    /**
     * `N/A` folds to `n a`, because a slash separates like any other punctuation. Closing the spaces
     * up before deciding is what makes every way of writing "no brand" arrive at the same brand —
     * which matters, because this is the key almost every food the owner describes will carry.
     */
    @Test
    fun `every spelling of NA is the same brand`() {
        assertThat(FoodKeys.brandKey("NA")).isEqualTo("na")
        assertThat(FoodKeys.brandKey("na")).isEqualTo("na")
        assertThat(FoodKeys.brandKey("N/A")).isEqualTo("na")
        assertThat(FoodKeys.brandKey("N.A.")).isEqualTo("na")
        assertThat(FoodKeys.brandKey("n a")).isEqualTo("na")
    }

    /** A brand whose name merely starts with those letters is a real brand and stays one. */
    @Test
    fun `a real brand beginning with those letters is not the brand NA`() {
        assertThat(FoodKeys.brandKey("Nature Valley")).isEqualTo("nature valley")
    }

    /** A brand that normalises away entirely is no brand at all, not a refusal. */
    @Test
    fun `a brand of nothing but punctuation is the brand NA`() {
        assertThat(FoodKeys.brandKey("--")).isEqualTo("na")
    }

    @Test
    fun `a real brand keys by the same rule as a name`() {
        assertThat(FoodKeys.brandKey("Dairyco")).isEqualTo("dairyco")
        assertThat(FoodKeys.brandKey("Ben & Jerry's")).isEqualTo("ben jerrys")
    }

    // --- The name as stored to be shown ---------------------------------------------------------

    /**
     * The stored name is the cleaned-up form, not whichever variant was logged last, and cleaning
     * up is not the same as keying: capitals, accents and punctuation carry meaning on screen.
     */
    @Test
    fun `the displayed name keeps its capitals, accents and punctuation`() {
        assertThat(FoodKeys.displayName("  Café  au   lait ")).isEqualTo("Café au lait")
        assertThat(FoodKeys.nameKey("  Café  au   lait ")).isEqualTo("cafe au lait")
    }

    @Test
    fun `the displayed name keeps a hyphen`() {
        assertThat(FoodKeys.displayName("Low-fat yoghurt")).isEqualTo("Low-fat yoghurt")
    }

    @Test
    fun `the displayed name drops the invisibles`() {
        assertThat(FoodKeys.displayName("‏יוגורט‎")).isEqualTo("יוגורט")
    }

    /** Compatibility spellings are folded even for display: they are the same letters. */
    @Test
    fun `the displayed name normalises compatibility spellings`() {
        assertThat(FoodKeys.displayName("waﬄe")).isEqualTo("waffle")
    }

    /** Vowel points are meaning on screen even though they are not identity. */
    @Test
    fun `the displayed name keeps vowel points`() {
        assertThat(FoodKeys.displayName("יוֹגוּרט")).isEqualTo("יוֹגוּרט")
    }

    @Test
    fun `a name that cannot be displayed is refused`() {
        assertThrows<IllegalArgumentException> { FoodKeys.displayName("   ") }
    }

    /**
     * Composed form out, always. Two names that a reader cannot tell apart must not be two
     * different strings in the store, or a lookup by display name would miss.
     */
    @Test
    fun `the displayed name comes out composed`() {
        assertThat(FoodKeys.displayName("café")).isEqualTo("café")
    }
}
