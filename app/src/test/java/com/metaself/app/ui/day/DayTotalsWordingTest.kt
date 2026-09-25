package com.metaself.app.ui.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.Remaining
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.day.correctionOf
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.DailyTargetCalculator
import org.junit.jupiter.api.Test

class DayTotalsWordingTest {

    private val target = DailyTargetCalculator.of(aProfile(), TEST_YEAR)

    @Test
    fun `with nothing eaten, the whole target is left`() {
        val line = DayTotalsWording.headline(Remaining.of(target, DayTotals.NOTHING), isToday = true)
        assertThat(line).isEqualTo("2,090 kcal left")
    }

    @Test
    fun `past the target, it says how far over rather than a negative number`() {
        val line = DayTotalsWording.headline(Remaining.of(target, DayTotals(2300, 0, 0, 0)), isToday = true)
        assertThat(line).isEqualTo("210 kcal over")
    }

    @Test
    fun `a past day says what it came to, not what is left of it`() {
        val remaining = Remaining.of(target, DayTotals(1500, 0, 0, 0))
        assertThat(DayTotalsWording.headline(remaining, isToday = false))
            .isEqualTo("590 kcal under")
    }

    @Test
    fun `a past day over its target says so in the past tense too`() {
        val remaining = Remaining.of(target, DayTotals(2300, 0, 0, 0))
        assertThat(DayTotalsWording.headline(remaining, isToday = false))
            .isEqualTo("210 kcal over")
    }

    @Test
    fun `a past day with nothing on it says so rather than measuring the fast it never saw`() {
        val remaining = Remaining.of(target, DayTotals.NOTHING)
        assertThat(DayTotalsWording.headline(remaining, isToday = false, nothingLogged = true))
            .isEqualTo("Nothing logged")
    }

    @Test
    fun `today with nothing on it still has its whole target left`() {
        val remaining = Remaining.of(target, DayTotals.NOTHING)
        assertThat(DayTotalsWording.headline(remaining, isToday = true, nothingLogged = true))
            .isEqualTo("2,090 kcal left")
    }

    // ---------------------------------------------------------------------------------------
    // The page needs the same answer in pieces (D49).
    //
    // `headline` returns "1,240 kcal left" as one string, which is the right shape for a ring that
    // draws one line of text in one face. A page is not that: the figure is set in the display face
    // at 72 sp and the words beside it in the text face at 14 sp, and two different faces at two
    // different sizes cannot come out of one string. So `amount` and `unit` return the two pieces
    // separately, and `headline` stays exactly as it is for the callers that still want the sentence
    // whole.
    //
    // These cases deliberately mirror the `headline` cases above one for one — today, over target, a
    // past day under, and a past day with nothing on it. Any case the sentence handles and the
    // pieces do not is a case where the page would say something the ring did not.
    // ---------------------------------------------------------------------------------------

    @Test
    fun `the figure alone is the grouped number, with no unit attached to it`() {
        val remaining = Remaining.of(target, DayTotals.NOTHING)
        assertThat(DayTotalsWording.amount(remaining, isToday = true)).isEqualTo("2,090")
    }

    /**
     * The same rule the sentence follows: how far over, not a negative number. The minus sign is the
     * truth of the arithmetic and "210 over" is the truth of the day, and the page prints the second
     * one — set 72 sp tall, a leading minus is also a typographic mess that shifts the whole figure.
     */
    @Test
    fun `past the target the figure is how far over, not a negative`() {
        val remaining = Remaining.of(target, DayTotals(2300, 0, 0, 0))

        assertThat(DayTotalsWording.amount(remaining, isToday = true)).isEqualTo("210")
        assertThat(DayTotalsWording.amount(remaining, isToday = false)).isEqualTo("210")
    }

    @Test
    fun `a past day under its target shows what it came to`() {
        val remaining = Remaining.of(target, DayTotals(1500, 0, 0, 0))
        assertThat(DayTotalsWording.amount(remaining, isToday = false)).isEqualTo("590")
    }

    /**
     * Null, not a dash, and the choice is deliberate.
     *
     * A past day with no rows has no number — that is `headline`'s whole reason for saying "Nothing
     * logged" rather than presenting the untouched target as a measurement of a fast nobody recorded
     * (D4). The page's number slot is the display face at 72 sp; putting an em dash in it would set
     * a punctuation mark in the position the screen reserves for its one answer, which reads as a
     * figure that failed to load rather than as a day with no record.
     *
     * Null lets the caller draw nothing at all and give the whole slot to `unit`'s "Nothing logged",
     * which is the sentence that actually says what happened. It also matches what this object
     * already does with `origin` and `mealTitle`: null is this file's established way of saying
     * there is nothing here, rather than inventing a placeholder for a screen to interpret.
     */
    @Test
    fun `a past day with nothing on it has no figure at all`() {
        val remaining = Remaining.of(target, DayTotals.NOTHING)
        assertThat(DayTotalsWording.amount(remaining, isToday = false, nothingLogged = true)).isNull()
    }

    /** Today is unaffected by the empty-day rule: a day still running has its whole target left. */
    @Test
    fun `today with nothing on it still shows the whole target as its figure`() {
        val remaining = Remaining.of(target, DayTotals.NOTHING)
        assertThat(DayTotalsWording.amount(remaining, isToday = true, nothingLogged = true))
            .isEqualTo("2,090")
    }

    @Test
    fun `the unit carries the meaning the figure cannot`() {
        val nothingEaten = Remaining.of(target, DayTotals.NOTHING)
        val over = Remaining.of(target, DayTotals(2300, 0, 0, 0))
        val under = Remaining.of(target, DayTotals(1500, 0, 0, 0))

        assertThat(DayTotalsWording.unit(nothingEaten, isToday = true)).isEqualTo("kcal left")
        assertThat(DayTotalsWording.unit(over, isToday = true)).isEqualTo("kcal over")
        assertThat(DayTotalsWording.unit(over, isToday = false)).isEqualTo("kcal over")
        assertThat(DayTotalsWording.unit(under, isToday = false)).isEqualTo("kcal under")
    }

    /**
     * The one case with no figure beside it, so the unit stops being a unit and becomes the whole
     * sentence. There is nothing for "kcal" to be the unit OF.
     */
    @Test
    fun `a past day with nothing on it says so where the unit would be`() {
        val remaining = Remaining.of(target, DayTotals.NOTHING)

        assertThat(DayTotalsWording.unit(remaining, isToday = false, nothingLogged = true))
            .isEqualTo("Nothing logged")
        assertThat(DayTotalsWording.unit(remaining, isToday = true, nothingLogged = true))
            .isEqualTo("kcal left")
    }

    /**
     * The denominator, without a second "kcal". Untested until now, which is how it kept one.
     */
    @Test
    fun `the target is named as a bare figure, because the unit has already been said`() {
        assertThat(DayTotalsWording.ofTarget(target)).isEqualTo("of 2,090")
    }

    /**
     * The defect D49 exists to fix, and the only assertion that can catch it coming back.
     *
     * The line reads "1,240 kcal left of 2,090 kcal" today. On a phone that wraps, and it wraps
     * because it says the unit twice — once where it belongs, once where it is redundant. Dropping
     * the second one is not a saving of five characters; it is what lets the line stay on one line.
     *
     * Asserted on the two pieces COMBINED rather than on `ofTarget` alone, because that is the thing
     * the owner reads: either piece on its own can be correct while the pair is wrong. The
     * nothing-logged case has no unit anywhere, which is right — there is no figure for a unit to
     * belong to — so it is checked for at most one rather than exactly one.
     */
    @Test
    fun `the number and its denominator never say kcal twice`() {
        val cases = listOf(
            DayTotalsWording.unit(Remaining.of(target, DayTotals.NOTHING), isToday = true),
            DayTotalsWording.unit(Remaining.of(target, DayTotals(2300, 0, 0, 0)), isToday = true),
            DayTotalsWording.unit(Remaining.of(target, DayTotals(1500, 0, 0, 0)), isToday = false),
        )

        cases.forEach { unit ->
            val line = "$unit ${DayTotalsWording.ofTarget(target)}"
            assertThat(Regex("kcal").findAll(line).count()).isEqualTo(1)
        }

        val empty = "${DayTotalsWording.unit(
            Remaining.of(target, DayTotals.NOTHING),
            isToday = false,
            nothingLogged = true,
        )} ${DayTotalsWording.ofTarget(target)}"
        assertThat(Regex("kcal").findAll(empty).count()).isAtMost(1)
    }

    @Test
    fun `a macro line names what is left of each`() {
        val lines = DayTotalsWording.macros(Remaining.of(target, DayTotals(0, 40, 50, 25)))
        assertThat(lines[0]).isEqualTo("Protein 105 g left")
        assertThat(lines[1]).isEqualTo("Carbs 180 g left")
        assertThat(lines[2]).isEqualTo("Fat 40 g left")
    }

    @Test
    fun `a typed item says nothing about where it came from, because it is simply his number`() {
        assertThat(DayTotalsWording.origin(anItem(source = Source.TYPED))).isNull()
    }

    @Test
    fun `an estimate says so, and how sure it was`() {
        val item = anItem(source = Source.AI_ESTIMATE, confidence = Confidence.LOW)
        assertThat(DayTotalsWording.origin(item)).isEqualTo("Estimated — low confidence")
    }

    /**
     * Nothing shows this on the day's list any more (D7a). Where it can still be shown, a repeat
     * says what its numbers ARE rather than that he ate them before: that he has had something
     * twice is a fact about his habits, not about the figure.
     */
    @Test
    fun `a repeated estimate is still an estimate, and says how sure`() {
        val repeated = anItem(source = Source.REPEATED, confidence = Confidence.MEDIUM)

        assertThat(DayTotalsWording.origin(repeated)).isEqualTo("Estimated — moderate confidence")
    }

    @Test
    fun `a repeated typed number says nothing, as a typed number does`() {
        assertThat(DayTotalsWording.origin(anItem(source = Source.REPEATED))).isNull()
    }

    /**
     * A figure he changed in *Correct this item* stops saying the packet declared it (D44, issue
     * #35), and an untouched scanned row goes on saying so.
     *
     * Built through the rule itself rather than by handing in a TYPED row, so it is the real
     * decision being read and not a fixture agreeing with itself. Nothing in the wording changes:
     * the corrected row simply arrives at the arm a typed number has always arrived at, which says
     * nothing at all — a number of his own needs no badge.
     *
     * This is the only honest place to test it. The day's item list has printed no per-row origin
     * line since it was removed on purpose, and `origin`'s one caller on a screen draws the model's
     * proposals, not stored rows — so a render test claiming to read this line would be asserting
     * against a fixture rather than against the app.
     */
    @Test
    fun `a corrected scanned row says nothing about where its figures came from`() {
        val scanned = anItem(
            name = "Rice cakes",
            portion = "30 g",
            portionAmount = 30.0,
            portionUnit = "g",
            kcal = 116,
            proteinG = 2,
            carbsG = 24,
            fatG = 0,
            source = Source.LABEL,
        )

        assertThat(DayTotalsWording.origin(scanned)).isEqualTo("From the package label")
        assertThat(DayTotalsWording.origin(scanned.copy(kcal = 140).correctionOf(scanned))).isNull()
    }

    @Test
    fun `an unreadable item admits that its origin is unknown`() {
        assertThat(DayTotalsWording.origin(anItem(source = Source.UNRECOGNISED)))
            .isEqualTo("Origin not recognised by this version")
    }

    @Test
    fun `an item states what it was and what it held`() {
        val item = anItem(name = "Hummus", kcal = 180, proteinG = 6, carbsG = 12, fatG = 12)

        assertThat(DayTotalsWording.itemName(item)).isEqualTo("Hummus")
        assertThat(DayTotalsWording.itemNumbers(item, item.portion))
            .isEqualTo("180 kcal · P 6 · C 12 · F 12")
    }

    @Test
    fun `the portion joins the numbers rather than costing a line of its own`() {
        val item = anItem(name = "Pizza", portion = "2 slice", kcal = 570)

        assertThat(DayTotalsWording.itemNumbers(item, item.portion)).endsWith("· 2 slice")
    }

    /**
     * The words for the portion are chosen where resources are, on the screen, and handed in; this
     * stays pure. Handing in nothing means the figures alone, whatever words the row stored.
     */
    @Test
    fun `the numbers take the portion words they are given`() {
        val item = anItem(portion = "2 portion", portionAmount = 2.0, portionUnit = "portion")

        assertThat(DayTotalsWording.itemNumbers(item, "2 portions"))
            .isEqualTo("600 kcal · P 40 · C 50 · F 25 · 2 portions")
        assertThat(DayTotalsWording.itemNumbers(item, null))
            .isEqualTo("600 kcal · P 40 · C 50 · F 25")
    }

    /** D7a as amended by D58: a model's estimate reads as one on the day, before its amount. */
    @Test
    fun `an estimated row's amount is marked as about`() {
        val item = anItem(
            name = "Quinoa", portion = "70 g", portionAmount = 70.0, portionUnit = "g", kcal = 84,
            proteinG = 3, carbsG = 15, fatG = 1, source = Source.AI_ESTIMATE, confidence = Confidence.MEDIUM,
        )

        assertThat(DayTotalsWording.itemNumbers(item, item.portion)).isEqualTo("84 kcal · P 3 · C 15 · F 1 · ≈70 g")
        assertThat(DayTotalsWording.itemNumbersSpoken(item, item.portion))
            .isEqualTo("84 kcal · P 3 · C 15 · F 1 · about 70 g")
    }

    /** Typed, scanned, or repeated from his own foods: the amount is his, and carries no mark. */
    @Test
    fun `a row that is not a model's estimate carries no mark`() {
        listOf(Source.TYPED, Source.LABEL, Source.REPEATED).forEach { source ->
            val item = anItem(portion = "70 g", portionAmount = 70.0, portionUnit = "g", source = source)

            assertThat(DayTotalsWording.itemNumbers(item, item.portion)).endsWith("· 70 g")
            assertThat(DayTotalsWording.itemNumbersSpoken(item, item.portion)).endsWith("· 70 g")
        }
    }

    /** The mark is for an amount; words that do not start with one are left as written. */
    @Test
    fun `an estimate with no amount in its words, or no words, carries no mark`() {
        val worded = anItem(portion = "large", source = Source.AI_ESTIMATE, confidence = Confidence.LOW)
        val bare = anItem(source = Source.AI_ESTIMATE, confidence = Confidence.LOW)

        assertThat(DayTotalsWording.itemNumbers(worded, worded.portion)).endsWith("· large")
        assertThat(DayTotalsWording.itemNumbers(bare, null)).doesNotContain("≈")
    }

    /**
     * The reason the name is separate at all. A Hebrew name and a Latin figure in one string are two
     * runs of opposite direction, and the bidirectional algorithm is entitled to reorder them:
     * joined into one string, a row's calorie count can be dragged in front of the food it belongs
     * to. Nothing can interleave two separate pieces of text, so they are never joined.
     */
    @Test
    fun `a hebrew name is never mixed into the same string as its numbers`() {
        val item = anItem(name = "לחם", kcal = 100, proteinG = 5, carbsG = 20, fatG = 0)

        assertThat(DayTotalsWording.itemName(item)).isEqualTo("לחם")
        assertThat(DayTotalsWording.itemNumbers(item, item.portion)).doesNotContain("לחם")
    }

    @Test
    fun `the macro bars are labelled short enough to sit side by side`() {
        val target = DailyTargetCalculator.of(aProfile(), TEST_YEAR)
        val bars = DayTotalsWording.macroBars(
            target,
            Remaining.of(target, DayTotals.of(emptyList())),
        )

        // Short enough for a third of the screen's width. "Protein 149 g left" wrapped to three
        // lines there and cost more room than the sentence it replaced.
        assertThat(bars.map { it.label }).hasSize(3)
        bars.forEach { assertThat(it.label.length).isAtMost(12) }
        assertThat(bars.first().label).startsWith("P ")
    }
}
