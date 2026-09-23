package com.metaself.app.ui.screen.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.day.correctionOf
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.theme.MetaSelfTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JUnit 4 by necessity — Robolectric's runner is JUnit 4. */
@RunWith(RobolectricTestRunner::class)
class EntryEditorScreenRenderTest {

    private val render = ComposeRender()

    /**
     * Only the typing test uses this. [ComposeRender] draws a state once and cannot drive a field's
     * `onValueChange`; typing needs a live composition, which only Compose's own rule provides
     * (see [com.metaself.app.ui.ComposeSession] for why a hand-driven one fails silently).
     */
    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `asks for a name, the calories and the three macros`() {
        val texts = draw(EntryFormState(), showErrors = false)
        assertThat(texts).contains("What was it?")
        assertThat(texts).contains("Calories")
        assertThat(texts).contains("Protein (g)")
        assertThat(texts).contains("Carbohydrate (g)")
        assertThat(texts).contains("Fat (g)")
    }

    @Test
    fun `says nothing is wrong before the owner has tried to save`() {
        val texts = draw(EntryFormState(), showErrors = false)
        assertThat(texts).doesNotContain(WHOLE_GRAMS)
        assertThat(texts).doesNotContain(WHOLE_CALORIES)
        assertThat(texts).doesNotContain(OLD_REFUSAL)
    }

    /**
     * An empty form: the calories are required, so their refusal shows; blank macros are allowed
     * (they count as zero), so the macro refusal must not. The old shared "whole units" wording is
     * gone from every box (issue #18).
     */
    @Test
    fun `shows what is wrong once the owner has tried to save`() {
        val texts = draw(EntryFormState(), showErrors = true)
        assertThat(texts).contains(WHOLE_CALORIES)
        assertThat(texts).doesNotContain(WHOLE_GRAMS)
        assertThat(texts).doesNotContain(OLD_REFUSAL)
    }

    // --- Said before he types, not after the refusal (issue #18, D38) ----------------------------

    /**
     * The walk typed a packet's 0.7 g and learned only on pressing Add it that the form wants whole
     * numbers. The rule is said above the first box it governs — the calories — on the empty form,
     * and on Correct this item, which is the same form.
     */
    @Test
    fun `says whole numbers and whole grams on the empty form, before anything is typed`() {
        val texts = draw(EntryFormState(), showErrors = false)

        assertThat(texts).contains(GUIDANCE)
        assertThat(texts.indexOf(GUIDANCE)).isLessThan(texts.indexOf("Calories"))
        assertThat(texts.indexOf(GUIDANCE)).isLessThan(texts.indexOf("Protein (g)"))
        assertThat(texts).contains("Leave a macro blank if you do not know it — it counts as zero.")

        val correcting = draw(EntryFormState(), showErrors = false, isEdit = true)
        assertThat(correcting).contains(GUIDANCE)
    }

    /**
     * The amount box takes decimals ("How much" is 180 g or 0.5 slice), so the sentence sits below
     * it, where it cannot be read as governing it.
     */
    @Test
    fun `on an item with an amount, it is said below the amount and above the calories`() {
        val texts = draw(
            EntryFormState.from(anItem(portion = "180 g", portionAmount = 180.0, portionUnit = "g")),
            showErrors = false,
        )

        assertThat(texts).contains(GUIDANCE)
        assertThat(texts).contains("How much (g)")
        assertThat(texts.indexOf("How much (g)")).isLessThan(texts.indexOf(GUIDANCE))
        assertThat(texts.indexOf(GUIDANCE)).isLessThan(texts.indexOf("Calories"))
    }

    /**
     * Refused under the box, with the reason, and the box still reads what he typed: nothing is
     * rounded to 1 for him. No other text on this form is exactly "1" (the calories are 72), so its
     * absence means the fat box was not rewritten.
     */
    @Test
    fun `a decimal macro is refused under its box and the box still reads what was typed`() {
        val texts = draw(
            EntryFormState(name = "Yoghurt", kcal = "72", fatG = "0.7"),
            showErrors = true,
        )

        assertThat(texts).contains(WHOLE_GRAMS)
        assertThat(texts).contains("0.7")
        assertThat(texts.none { it == "1" }).isTrue()
    }

    /**
     * The test above draws a state already holding "0.7"; it would still pass if the fat box
     * dropped the decimal point AS HE TYPES — a "helpful" digits-only filter, which is rounding by
     * another name and exactly what issue #18 rules out. So this one types, a keystroke at a time,
     * into the real field, and reads what the screen reported back through `onChange`: that is the
     * state the save would check. "0", ".", "7" rather than "0.7" at once, because a filter that
     * refuses a change ending in "." would let a whole-string paste through and still eat the point
     * from a person typing.
     */
    @Test
    fun `typing a decimal into the fat box reaches the form as typed, point included`() {
        var received: EntryFormState? = null
        compose.setContent {
            MetaSelfTheme {
                var state by remember { mutableStateOf(EntryFormState(name = "Yoghurt", kcal = "72")) }
                EntryEditorScreen(
                    state = state,
                    showErrors = true,
                    isEdit = false,
                    onChange = {
                        state = it
                        received = it
                    },
                    onSave = {},
                    onCancel = {},
                )
            }
        }

        val fat = compose.onNodeWithText("Fat (g)")
        fat.performScrollTo()
        fat.performTextInput("0")
        fat.performTextInput(".")
        fat.performTextInput("7")
        compose.waitForIdle()

        assertThat(received?.fatG).isEqualTo("0.7")
        // And it is refused for what it is, under its box, rather than quietly becoming 1.
        compose.onNodeWithText(WHOLE_GRAMS).assertExists()
        compose.onNodeWithText("0.7").assertExists()
    }

    @Test
    fun `draws the values it was handed, for the screens that edit an item`() {
        val texts = draw(EntryFormState.from(anItem(name = "Hummus", kcal = 180)), showErrors = false)
        assertThat(texts).contains("Hummus")
        assertThat(texts).contains("180")
    }

    @Test
    fun `adding by hand is headed Type the numbers`() {
        val texts = draw(EntryFormState(), showErrors = false, isEdit = false)

        assertThat(texts).contains("Type the numbers")
    }

    /**
     * The day's edit tap opens this same screen. In this app's vocabulary "Type the numbers" is the
     * manual, no-network path specifically (D8), so heading a correction with it would tell the
     * owner he is starting from nothing when he is fixing an estimate.
     */
    @Test
    fun `correcting an item already logged keeps its own heading`() {
        val texts = draw(EntryFormState(), showErrors = false, isEdit = true)

        assertThat(texts).contains("Correct this item")
        assertThat(texts).doesNotContain("Type the numbers")
    }

    /**
     * **A labelled regression, not a discriminating test: it passes before and after D44 (issue
     * #35).** It is here because the brief asks for the correction to be visible on a screen, and
     * this is the only screen that shows any of it.
     *
     * Where a corrected row's source actually surfaces is the export and, when the correction also
     * renames the row, *My foods* — the day's list prints no per-row origin line. What IS on screen
     * is this: reopen the row and the boxes hold the figures he typed, read through `EditableText`,
     * since Compose exposes a field's contents that way and a helper reading only `Text` would see
     * the labels and nothing typed into them.
     */
    @Test
    fun `reopening a corrected scanned row shows the numbers he typed`() {
        val scanned = FoodItem(
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
        val corrected = scanned.copy(kcal = 140).correctionOf(scanned)

        val texts = draw(EntryFormState.from(corrected), showErrors = false, isEdit = true)

        assertThat(texts).contains("Rice cakes")
        assertThat(texts).contains("140")
        assertThat(texts).doesNotContain("116")
    }

    /**
     * "Infinity" pasted into Correct this item's amount crashed the editor on a row with a zero
     * figure — the scaling runs as he types. Past its ceiling (D42, issue #32) the figures do not
     * move, live, as he types; once he presses Save the refusal naming the ceiling shows once,
     * under the amount box and above the calories, and the box still reads what he pasted.
     */
    @Test
    fun `an amount past its ceiling is refused once under the amount box, and the box keeps it`() {
        val chicken = FoodItem(
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
        val pasted = EntryFormState.from(chicken).withAmount("Infinity")

        val typing = draw(pasted, showErrors = false, isEdit = true)
        assertThat(typing).contains("Infinity")
        assertThat(typing).containsAtLeast("300", "54", "0", "8")
        assertThat(typing).doesNotContain(AMOUNT_TOO_MUCH)

        val saved = draw(pasted, showErrors = true, isEdit = true)
        assertThat(saved).contains("Infinity")
        assertThat(saved.filter { it == AMOUNT_TOO_MUCH }).hasSize(1)
        assertThat(saved.indexOf(AMOUNT_TOO_MUCH)).isGreaterThan(saved.indexOf("How much (g)"))
        assertThat(saved.indexOf(AMOUNT_TOO_MUCH)).isLessThan(saved.indexOf("Calories"))
        assertThat(saved).containsAtLeast("300", "54", "8")
    }

    private fun draw(
        state: EntryFormState,
        showErrors: Boolean,
        isEdit: Boolean = false,
    ): List<String> = render.texts {
        EntryEditorScreen(
            state = state,
            showErrors = showErrors,
            isEdit = isEdit,
            onChange = {},
            onSave = {},
            onCancel = {},
        )
    }

    private companion object {
        /** The guidance above the calories, said before anything is typed. */
        const val GUIDANCE = "Whole numbers only: calories, and protein, carbohydrate and fat in " +
            "whole grams. A number with a decimal point or comma is refused, not rounded."

        /** The refusal under a macro box, in the guidance's words. */
        const val WHOLE_GRAMS = "Whole grams — no decimal point or comma."

        /** The refusal under the calorie box: calories are not grams. */
        const val WHOLE_CALORIES = "A whole number of calories."

        /** The amount box's refusal, naming its ceiling in the row's own unit (D42). */
        const val AMOUNT_TOO_MUCH = "How much of it? A number greater than nothing (at most 5000 g)."

        /** The shared message every box used to give, which named neither grams nor calories. */
        const val OLD_REFUSAL = "A number, in whole units."
    }
}
