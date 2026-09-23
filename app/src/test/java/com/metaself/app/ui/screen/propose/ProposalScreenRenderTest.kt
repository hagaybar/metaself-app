package com.metaself.app.ui.screen.propose

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.PortionScale
import com.metaself.app.domain.ai.ProposedItem
import com.metaself.app.domain.ai.aProposedItem
import com.metaself.app.domain.day.Confidence
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The model's answer, drawn.
 *
 * Compose, so JUnit 4 — `org.junit.Test`, never `org.junit.jupiter.api.Test`. The two annotations
 * look identical at the call site and the wrong one produces a test that silently never runs.
 */
@RunWith(RobolectricTestRunner::class)
class ProposalScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    // --- "2 portion" on the proposal (D37, #27) -------------------------------------------------

    /**
     * The held words are what goes on the record, and they stay "2 portion" there; only the drawing
     * chooses the plural, and only of the app's own word in the app's own form.
     */
    @Test
    fun `the model's two portions read as portions`() {
        val texts = draw(proposed(anEstimate("Stew", "2 portion", 2.0, "portion")))

        assertThat(texts).contains("2 portions")
        assertThat(texts.none { it == "2 portion" }).isTrue()
    }

    @Test
    fun `the model's one portion reads as one portion`() {
        val texts = draw(proposed(anEstimate("Stew", "1 portion", 1.0, "portion")))

        assertThat(texts).contains("1 portion")
        assertThat(texts.none { it.contains("1 portions") }).isTrue()
    }

    /** A count rewrites the held words in the app's own form, so the new count reads the same way. */
    @Test
    fun `a count changed on the screen reads as portions`() {
        val asProposed = anEstimate("Stew", "2 portion", 2.0, "portion")
        val state = ProposalUiState.Proposed(
            rows = listOf(ProposalRow(asProposed, PortionScale.count(3, asProposed))),
            note = null,
        )

        val texts = draw(state)

        assertThat(texts).contains("3 portions")
    }

    /**
     * A unit the model named is not the app's to pluralise, and words that say more than the amount
     * and unit ("2 portion (large)") are what the model said — rebuilding them from the numbers
     * would silently drop the rest (D5).
     */
    @Test
    fun `a unit the model named, or words that say more, are drawn as written`() {
        val texts = draw(
            proposed(
                anEstimate("Bread", "2 slice", 2.0, "slice"),
                anEstimate("Stew", "2 portion (large)", 2.0, "portion"),
            ),
        )

        assertThat(texts).contains("2 slice")
        assertThat(texts).contains("2 portion (large)")
        assertThat(texts.none { it == "2 portions (large)" || it == "2 slices" }).isTrue()
    }

    // --- Keeping what was just described as a meal (D46, issue #24) -----------------------------

    /**
     * An answer of more than one row offers to keep those rows as a meal he names.
     *
     * The words are `propose_keep_as_meal` in `strings.xml`; written out here as the phone draws
     * them, the way every other render test in this project asserts on what it can read.
     */
    @Test
    fun `a two-item answer offers to keep them as a meal`() {
        val texts = draw(
            proposed(
                anEstimate("Milk", "120 ml", 120.0, "ml"),
                anEstimate("Espresso", "1 cup", 1.0, "cup"),
            ),
        )

        assertThat(texts).contains(KEEP_AS_MEAL)
        // The offer is an addition: accepting plainly is still the first thing on the screen.
        assertThat(texts).contains("Save this meal")
    }

    /** A meal of one is a food already, and this app has a way of keeping one of those. */
    @Test
    fun `a one-item answer does not offer it`() {
        val texts = draw(proposed(anEstimate("Espresso", "1 cup", 1.0, "cup")))

        assertThat(texts).contains("Save this meal")
        assertThat(texts).doesNotContain(KEEP_AS_MEAL)
    }

    /**
     * No answer, nothing to keep: the offer belongs to an answer and to nothing else.
     *
     * The screen has a third state, waiting for the model, and it is deliberately not drawn here:
     * its spinner animates for ever, and this renderer drains the main looper, so a test that drew
     * it would never come back. Both states that hold no answer are the same `when` branch as far as
     * the offer is concerned, and this is the one that can be looked at.
     */
    @Test
    fun `an answer that has not come back yet offers nothing`() {
        val texts = draw(ProposalUiState.Describing())

        assertThat(texts).doesNotContain(KEEP_AS_MEAL)
        assertThat(texts).doesNotContain("Save this meal")
    }

    private fun anEstimate(
        name: String,
        portion: String,
        amount: Double,
        unit: String,
    ): ProposedItem = aProposedItem(
        name = name,
        portion = portion,
        portionAmount = amount,
        portionUnit = unit,
        confidence = Confidence.MEDIUM,
    )

    private fun proposed(vararg items: ProposedItem) = ProposalUiState.Proposed(
        rows = items.map { ProposalRow(it, it) },
        note = null,
    )

    private fun draw(state: ProposalUiState): List<String> = render.texts {
        ProposalScreen(
            state = state,
            description = "",
            onDescribe = {},
            onScale = { _, _ -> },
            onCount = { _, _ -> },
            onRemove = {},
            onTellItMore = {},
            onSave = {},
            onTypeItMyself = {},
            onCancel = {},
            // The naming half, wired to nothing on purpose: the sheet draws into a window of its
            // own that this renderer cannot read, so what is asserted here is the offer that opens
            // it. `keepingAsMeal` and `namingSheetStep` carry the rules themselves, in
            // `KeepingAsMealTest`. These are named rather than defaulted because the screen has no
            // defaults: one place wires it, and it fails here rather than silently on the phone.
            onKeepAsMeal = {},
            onNameMeal = {},
            onGiveUpNaming = {},
            onKeepingDone = {},
            chosenRows = emptyList(),
            isToday = true,
            refusal = null,
        )
    }

    private companion object {
        /** `R.string.propose_keep_as_meal`, as the phone draws it. */
        const val KEEP_AS_MEAL = "Save, and keep these as a meal"
    }
}
