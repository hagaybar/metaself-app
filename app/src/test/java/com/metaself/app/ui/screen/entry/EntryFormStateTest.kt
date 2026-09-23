package com.metaself.app.ui.screen.entry

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.anItem
import org.junit.jupiter.api.Test

class EntryFormStateTest {

    private val complete = EntryFormState(
        name = "Chicken shawarma",
        kcal = "600",
        proteinG = "40",
        carbsG = "50",
        fatG = "25",
    )

    @Test
    fun `a complete form has no errors and becomes a typed item`() {
        assertThat(complete.errors()).isEmpty()
        val item = complete.toItem()
        assertThat(item?.name).isEqualTo("Chicken shawarma")
        assertThat(item?.kcal).isEqualTo(600)
        assertThat(item?.source).isEqualTo(Source.TYPED)
        assertThat(item?.confidence).isNull()
    }

    @Test
    fun `an empty form cannot become an item`() {
        assertThat(EntryFormState().toItem()).isNull()
    }

    @Test
    fun `a name is required`() {
        assertThat(complete.copy(name = "   ").errors()).containsKey(EntryField.NAME)
    }

    @Test
    fun `calories are required, and must be a number`() {
        assertThat(complete.copy(kcal = "").errors()).containsKey(EntryField.KCAL)
        assertThat(complete.copy(kcal = "lots").errors()).containsKey(EntryField.KCAL)
    }

    @Test
    fun `a missing macro counts as zero rather than blocking the save`() {
        val sparse = complete.copy(proteinG = "", carbsG = "", fatG = "")
        assertThat(sparse.errors()).isEmpty()
        assertThat(sparse.toItem()?.proteinG).isEqualTo(0)
    }

    @Test
    fun `a negative number is rejected`() {
        assertThat(complete.copy(kcal = "-5").errors()).containsKey(EntryField.KCAL)
        assertThat(complete.copy(proteinG = "-5").errors()).containsKey(EntryField.PROTEIN)
    }

    @Test
    fun `an implausibly large number is rejected as a slipped finger`() {
        assertThat(complete.copy(kcal = "60000").errors()).containsKey(EntryField.KCAL)
        assertThat(complete.copy(proteinG = "5000").errors()).containsKey(EntryField.PROTEIN)
    }

    @Test
    fun `a form can be built from an item, for the screens that edit one`() {
        val form = EntryFormState.from(anItem(name = "Hummus", kcal = 180, proteinG = 6))
        assertThat(form.name).isEqualTo("Hummus")
        assertThat(form.kcal).isEqualTo("180")
        assertThat(form.proteinG).isEqualTo("6")
    }

    @Test
    fun `editing an estimate keeps it an estimate, with its confidence`() {
        val estimate = anItem(source = Source.AI_ESTIMATE, confidence = Confidence.LOW)
        val edited = EntryFormState.from(estimate).copy(kcal = "650").toItem()
        assertThat(edited?.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(edited?.confidence).isEqualTo(Confidence.LOW)
        assertThat(edited?.kcal).isEqualTo(650)
    }

    /**
     * Correcting a logged row keeps the food it is (issue #22).
     *
     * The form rebuilt the row from scratch and never carried the link, so every correction — a
     * calorie figure, a macro — silently detached the row from its food. It then looked exactly as
     * before, could never join a meal, and nothing could reattach it — it surfaced as a row that
     * could not join a meal, with no visible reason why.
     */
    @Test
    fun `correcting a logged row keeps the food it is`() {
        val logged = anItem(id = 5, name = "Cucumber", kcal = 15).copy(foodId = 42)

        val corrected = EntryFormState.from(logged).copy(kcal = "18").toItem()!!

        assertThat(corrected.kcal).isEqualTo(18)
        assertThat(corrected.foodId).isEqualTo(42)
    }

    /** A new entry has no food yet: logging finds or makes it, as it always has. */
    @Test
    fun `a new entry carries no food of its own`() {
        val typed = EntryFormState(name = "Cucumber", kcal = "15").toItem()!!

        assertThat(typed.foodId).isNull()
    }

    /**
     * An amount typed into a row that had none is recorded, the words follow it, and the calories
     * stay (issue #23).
     *
     * The way to repair a row the model left without an amount — one that could not join a meal.
     * The calories were stated for whatever amount that was, so they are kept rather than scaled
     * from nothing; the words on the day stop saying "amount not stated".
     */
    @Test
    fun `an amount typed into a row that had none is recorded, words and all, calories kept`() {
        val amountless = anItem(id = 5, name = "Stew", kcal = 400)
            .copy(portion = "amount not stated", portionAmount = 0.0, portionUnit = "g", foodId = 9)

        val repaired = EntryFormState.from(amountless).withAmount("350").toItem()!!

        assertThat(repaired.portionAmount).isEqualTo(350.0)
        assertThat(repaired.portionUnit).isEqualTo("g")
        assertThat(repaired.portion).isEqualTo("350 g")
        assertThat(repaired.kcal).isEqualTo(400)
        assertThat(repaired.foodId).isEqualTo(9)
    }

    // --- Whole numbers, and the reason given when they are not (issue #18, D38) -----------------

    /**
     * The walk's own figures, off a packet: 0.7 g protein, 3.6 g carbohydrate, 0.1 g fat.
     *
     * A day's row keeps whole grams, so these are refused — and refused with the words the form
     * says up front, not a generic "whole units" that tells him nothing about grams. Rounding them
     * to 1, 4 and 0 would store altered numbers as his own (D4), so the state must still hold
     * exactly what he typed and nothing must be saved. "0,7" and "1.0" are refused too: the rule is
     * about the form of what he typed, not whether its value happens to be whole.
     */
    @Test
    fun `a decimal macro is refused with the reason, and nothing is rounded`() {
        val typed = complete.copy(proteinG = "0.7", carbsG = "3.6", fatG = "0.1")

        val errors = typed.errors()
        assertThat(errors[EntryField.PROTEIN]).isEqualTo(WHOLE_GRAMS)
        assertThat(errors[EntryField.CARBS]).isEqualTo(WHOLE_GRAMS)
        assertThat(errors[EntryField.FAT]).isEqualTo(WHOLE_GRAMS)
        assertThat(typed.toItem()).isNull()
        assertThat(typed.proteinG).isEqualTo("0.7")
        assertThat(typed.carbsG).isEqualTo("3.6")
        assertThat(typed.fatG).isEqualTo("0.1")

        assertThat(complete.copy(proteinG = "0,7").errors()[EntryField.PROTEIN])
            .isEqualTo(WHOLE_GRAMS)
        assertThat(complete.copy(proteinG = "1.0").errors()[EntryField.PROTEIN])
            .isEqualTo(WHOLE_GRAMS)
    }

    /**
     * A comma-decimal keyboard types "1,0", not "1.0". The value is whole, but it is refused all the
     * same and in the same words — which is why the refusal names the comma as well as the point:
     * a reason that spoke only of a decimal point would not describe what he typed.
     */
    @Test
    fun `a decimal comma in a macro is refused in the same words`() {
        val typed = complete.copy(proteinG = "1,0")

        assertThat(typed.errors()[EntryField.PROTEIN]).isEqualTo(WHOLE_GRAMS)
        assertThat(typed.toItem()).isNull()
        assertThat(typed.proteinG).isEqualTo("1,0")
    }

    /**
     * Calories are not grams, so they get their own wording. It is also what an empty calorie box
     * says, and it reads correctly there: it names what is wanted.
     */
    @Test
    fun `calories that are missing or not whole are refused with their own reason`() {
        for (kcal in listOf("", "600.5", "lots")) {
            val typed = complete.copy(kcal = kcal)
            assertThat(typed.errors()[EntryField.KCAL]).isEqualTo(WHOLE_CALORIES)
            assertThat(typed.toItem()).isNull()
        }
    }

    /** The guard on the other side: refusing decimals must not disturb what was always accepted. */
    @Test
    fun `whole numbers save exactly as typed`() {
        val item = complete.toItem()!!
        assertThat(item.kcal).isEqualTo(600)
        assertThat(item.proteinG).isEqualTo(40)
        assertThat(item.carbsG).isEqualTo(50)
        assertThat(item.fatG).isEqualTo(25)

        assertThat(complete.copy(fatG = "0").toItem()!!.fatG).isEqualTo(0)
        assertThat(complete.copy(proteinG = " 7 ").toItem()!!.proteinG).isEqualTo(7)
    }

    // --- The entry ceilings, now D42's (issue #32) -------------------------------------------------

    /**
     * Mostly a pin. "Infinity" and "1e999" never were whole numbers, so they are refused in the
     * box's own words, as before; the slipped-finger ceilings were already D42's numbers and now
     * come from the one place every box reads them from. Exactly at the ceiling is accepted.
     */
    @Test
    fun `calories and grams refuse Infinity and 1e999 as not whole numbers, and are capped at the entry ceiling`() {
        for (kcal in listOf("Infinity", "1e999")) {
            val typed = complete.copy(kcal = kcal)
            assertThat(typed.errors()[EntryField.KCAL]).isEqualTo(WHOLE_CALORIES)
            assertThat(typed.toItem()).isNull()
        }
        assertThat(complete.copy(proteinG = "Infinity").errors()[EntryField.PROTEIN])
            .isEqualTo(WHOLE_GRAMS)

        assertThat(complete.copy(kcal = "10001").errors()[EntryField.KCAL])
            .isEqualTo("That looks like a slipped finger — the most is 10000.")
        assertThat(complete.copy(kcal = "10000").errors()).isEmpty()
        assertThat(complete.copy(kcal = "10000").toItem()!!.kcal).isEqualTo(10_000)

        assertThat(complete.copy(proteinG = "1001").errors()[EntryField.PROTEIN])
            .isEqualTo("That looks like a slipped finger — the most is 1000.")
        assertThat(complete.copy(proteinG = "1000").errors()).isEmpty()
        assertThat(complete.copy(proteinG = "1000").toItem()!!.proteinG).isEqualTo(1_000)
    }

    // --- D44 (issue #35): the editor is not where a correction's source is decided ----------------

    /** 30 g off a packet, as a scan logs it (D38): whole grams, fat rounded to nothing, LABEL. */
    private val scanned = anItem(
        id = 5,
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

    /**
     * **Pins today's behaviour, deliberately, and it must keep passing.**
     *
     * The form carries a row's source through untouched — that is what keeps a corrected estimate an
     * estimate (D4). Whether a correction changes what the row claims is therefore decided once on
     * the save path (D44, issue #35), where both the row as it stood and the row being saved are in
     * hand, so every route into *Correct this item* behaves the same. If this ever starts failing,
     * the rule has been put in the form, where the nav host and the simulated app each build their
     * own copy of it.
     */
    @Test
    fun `the editor carries a scanned row's source through untouched`() {
        val edited = EntryFormState.from(scanned).copy(kcal = "140").toItem()!!

        assertThat(edited.source).isEqualTo(Source.LABEL)
        assertThat(edited.confidence).isNull()
        assertThat(edited.kcal).isEqualTo(140)
    }

    /**
     * **Regression.** No re-formatting of a figure can ever reach the save path's comparison,
     * because a decimal never becomes a row at all: it is refused under its box rather than rounded
     * into a "correction" of a packet's reading (D4, D38).
     */
    @Test
    fun `a decimal typed over a scanned row's calories is refused, not rounded`() {
        val retyped = EntryFormState.from(scanned).copy(kcal = "116.0")

        assertThat(retyped.errors()[EntryField.KCAL]).isEqualTo(WHOLE_CALORIES)
        assertThat(retyped.toItem()).isNull()
    }

    private companion object {
        /** The refusal under a macro box, in the words the form's guidance uses. */
        const val WHOLE_GRAMS = "Whole grams — no decimal point or comma."

        /** The refusal under the calorie box: calories are not grams. */
        const val WHOLE_CALORIES = "A whole number of calories."
    }
}
