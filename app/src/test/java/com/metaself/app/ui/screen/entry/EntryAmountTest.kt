package com.metaself.app.ui.screen.entry

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

/**
 * Correcting how much of something was eaten, after it is already on the record.
 *
 * The editor had name and calories and nothing else, so a chicken breast logged yesterday could not
 * be corrected to today's smaller one without retyping every number — and doing that would have left
 * the portion still reading "180 g" beside figures that no longer matched it.
 */
class EntryAmountTest {

    private val chicken = FoodItem(
        id = 7,
        name = "Baked chicken",
        portion = "180 g",
        portionAmount = 180.0,
        portionUnit = "g",
        kcal = 300,
        proteinG = 54,
        carbsG = 0,
        fatG = 8,
        source = Source.AI_ESTIMATE,
        confidence = Confidence.MEDIUM,
    )

    @Test
    fun `an item with a portion opens with its amount ready to change`() {
        val form = EntryFormState.from(chicken)

        assertThat(form.hasAmount).isTrue()
        assertThat(form.amount).isEqualTo("180")
        assertThat(form.amountUnit).isEqualTo("g")
    }

    @Test
    fun `changing the amount carries the numbers with it`() {
        val smaller = EntryFormState.from(chicken).withAmount("120")

        assertThat(smaller.kcal).isEqualTo("200")
        assertThat(smaller.proteinG).isEqualTo("36")
        assertThat(smaller.portion).isEqualTo("120 g")
    }

    /** Scaled from the record every time, so going back arrives at the original, not near it. */
    @Test
    fun `changing it twice does not compound`() {
        val backAgain = EntryFormState.from(chicken).withAmount("120").withAmount("180")

        assertThat(backAgain.kcal).isEqualTo("300")
        assertThat(backAgain.proteinG).isEqualTo("54")
    }

    /** The amount says how much; he is entitled to disagree about what that much contained. */
    @Test
    fun `calories typed after scaling are his and stand`() {
        val corrected = EntryFormState.from(chicken).withAmount("120").copy(kcal = "250")

        assertThat(corrected.toItem()!!.kcal).isEqualTo(250)
        assertThat(corrected.toItem()!!.portion).isEqualTo("120 g")
    }

    @Test
    fun `the saved item keeps the portion numbers, so it can be adjusted again`() {
        val saved = EntryFormState.from(chicken).withAmount("120").toItem()!!

        assertThat(saved.portionAmount).isEqualTo(120.0)
        assertThat(saved.portionUnit).isEqualTo("g")
        assertThat(saved.id).isEqualTo(7)
    }

    @Test
    fun `an amount of nothing is refused`() {
        val none = EntryFormState.from(chicken).withAmount("0")

        assertThat(none.errors()).containsKey(EntryField.AMOUNT)
        assertThat(none.toItem()).isNull()
    }

    /** Correcting how much does not change where the numbers came from (D4). */
    @Test
    fun `the source and confidence survive being rescaled`() {
        val saved = EntryFormState.from(chicken).withAmount("90").toItem()!!

        assertThat(saved.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(saved.confidence).isEqualTo(Confidence.MEDIUM)
    }

    /** A typed entry has no amount to scale, and is offered none. */
    @Test
    fun `an item with no portion keeps the editor it always had`() {
        val typed = FoodItem(name = "Hummus", kcal = 180, proteinG = 6, carbsG = 12, fatG = 12, source = Source.TYPED)

        val form = EntryFormState.from(typed)

        assertThat(form.hasAmount).isFalse()
        assertThat(form.errors()).doesNotContainKey(EntryField.AMOUNT)
        assertThat(form.withAmount("50").kcal).isEqualTo("180")
    }

    // --- An amount has a ceiling, and an absurd one moves nothing (D42, issue #32) ---------------

    /**
     * "Infinity" pasted into the amount of a row with a zero figure crashed the editor while it was
     * drawing: the scaling runs on every keystroke, and 0 × ∞ is not a number that rounds. "1e300"
     * set every figure to 2,147,483,647. Now the box keeps what he pasted, the refusal names the
     * ceiling under it, the figures stay as logged, and nothing can be saved.
     */
    @Test
    fun `an amount past its ceiling is refused under the box and moves no figure`() {
        listOf("Infinity", "1e300", "5000.5").forEach { typed ->
            val form = EntryFormState.from(chicken).withAmount(typed)

            assertThat(form.amount).isEqualTo(typed)
            assertThat(form.kcal).isEqualTo("300")
            assertThat(form.proteinG).isEqualTo("54")
            assertThat(form.carbsG).isEqualTo("0")
            assertThat(form.fatG).isEqualTo("8")
            assertThat(form.portion).isEqualTo("180 g")
            assertThat(form.errors()[EntryField.AMOUNT])
                .isEqualTo("How much of it? A number greater than nothing (at most 5000 g).")
            assertThat(form.toItem()).isNull()
        }

        val atTheCeiling = EntryFormState.from(chicken).withAmount("5000")
        assertThat(atTheCeiling.errors()).doesNotContainKey(EntryField.AMOUNT)
        assertThat(atTheCeiling.kcal).isEqualTo("8333")
    }

    /**
     * Something counted is capped at a count — 100 slices, not 5000 — and the refusal names no unit,
     * since a count's unit takes its plural on the screen. The row it is counted in scales as before
     * up to the ceiling, and a weighed row still scales exactly as it did.
     */
    @Test
    fun `a counted amount is capped at 100`() {
        val toast = FoodItem(
            id = 8,
            name = "Toast",
            portion = "2 slices",
            portionAmount = 2.0,
            portionUnit = "slice",
            kcal = 160,
            proteinG = 6,
            carbsG = 30,
            fatG = 2,
            source = Source.TYPED,
        )

        val tooMany = EntryFormState.from(toast).withAmount("101")
        assertThat(tooMany.errors()[EntryField.AMOUNT])
            .isEqualTo("How much of it? A number greater than nothing (at most 100).")
        assertThat(tooMany.kcal).isEqualTo("160")
        assertThat(tooMany.toItem()).isNull()

        val hundred = EntryFormState.from(toast).withAmount("100")
        assertThat(hundred.errors()).doesNotContainKey(EntryField.AMOUNT)
        assertThat(hundred.kcal).isEqualTo("8000")

        val weighed = EntryFormState.from(chicken).withAmount("200")
        assertThat(weighed.kcal).isEqualTo("333")
        assertThat(weighed.proteinG).isEqualTo("60")
        assertThat(weighed.toItem()!!.portionAmount).isEqualTo(200.0)
    }

    /**
     * A mass the model wrote in its own word keeps the mass ceiling, and the refusal names that
     * unit as the box's label does — "How much (gram)" above, "at most 5000 gram" below — rather
     * than renaming it. The ceiling and the wording come from one answer to "is this a mass?", so
     * a mass unit can never be capped at 100 while its refusal says 5000, or the reverse.
     */
    @Test
    fun `a mass in the row's own word is capped at 5000 and named as the label names it`() {
        listOf("gram", "ml").forEach { unit ->
            val row = FoodItem(
                id = 9,
                name = "Yoghurt",
                portion = "200 $unit",
                portionAmount = 200.0,
                portionUnit = unit,
                kcal = 120,
                proteinG = 10,
                carbsG = 8,
                fatG = 5,
                source = Source.TYPED,
            )

            val tooMuch = EntryFormState.from(row).withAmount("5001")
            assertThat(tooMuch.errors()[EntryField.AMOUNT])
                .isEqualTo("How much of it? A number greater than nothing (at most 5000 $unit).")
            assertThat(tooMuch.toItem()).isNull()

            val atTheCeiling = EntryFormState.from(row).withAmount("5000")
            assertThat(atTheCeiling.errors()).doesNotContainKey(EntryField.AMOUNT)
        }
    }
}
