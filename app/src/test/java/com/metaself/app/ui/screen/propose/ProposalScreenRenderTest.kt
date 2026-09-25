package com.metaself.app.ui.screen.propose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.ProposedItem
import com.metaself.app.domain.ai.aBun
import com.metaself.app.domain.ai.aModelPita
import com.metaself.app.domain.ai.aProposedItem
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import com.metaself.app.ui.ActionRefused
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

    // --- The typed amount and the worth line (D53 §6) ---------------------------------------------

    @Test
    fun `the amount is a box holding the model's amount, with its unit beside it`() {
        val texts = draw(proposed(aProposedItem()))

        assertThat(texts).contains("g")
        // In a text field, not drawn as a line: the amount is typed, not chosen from proportions.
        assertThat(render.fieldTexts()).contains("200")
    }

    @Test
    fun `the worth line says what it is worth per 100 g`() {
        val texts = draw(proposed(aProposedItem()))

        assertThat(texts).contains("per 100 g: 250 kcal · P 18 · C 0 · F 20")
        // The total, and D7's line saying how sure the estimate was.
        assertThat(texts).contains("500 kcal · P 36 · C 0 · F 40")
        assertThat(texts).contains("Estimated — moderate confidence")
    }

    @Test
    fun `a piece's worth line is per that piece, and its detail is said`() {
        val texts = draw(proposed(aBun()))

        assertThat(texts).contains("per bun: 150 kcal · P 5 · C 28 · F 2")
        assertThat(texts).contains("sesame, toasted")
    }

    /** "Per 100 ml" is a per-100 worth whose unit is the millilitre: worded from the row's unit. */
    @Test
    fun `a drink measured in millilitres is worth so much per 100 ml`() {
        val juice = aProposedItem(name = "Orange juice", amount = 330.0, unit = "ml")

        assertThat(draw(proposed(juice))).contains("per 100 ml: 250 kcal · P 18 · C 0 · F 20")
    }

    @Test
    fun `a counted row has minus and plus, a weighed one has not`() {
        assertThat(draw(proposed(aBun()))).containsAtLeast(MINUS, PLUS)
        assertThat(draw(proposed(aProposedItem()))).containsNoneOf(MINUS, PLUS)
    }

    @Test
    fun `a blank amount turns saving off and names the row`() {
        val burger = aProposedItem()
        val state = ProposalUiState.Proposed(
            rows = listOf(ProposalRow(burger, burger.toItemToLog().copy(amountText = ""))),
            note = null,
        )

        val texts = draw(state)

        assertThat(texts).contains("Say how much Beef burger was to save this.")
        assertThat(render.isEnabled("Save this meal")).isFalse()
    }

    /** The model's 6000 g arrives in the box, refused as if he had typed it (D53 §2, D42). */
    @Test
    fun `an amount past the ceiling says the ceiling under the box`() {
        val texts = draw(proposed(aProposedItem(amount = 6000.0)))

        assertThat(texts).contains("At most 5000 g at a time.")
        assertThat(render.isEnabled("Save this meal")).isFalse()
    }

    /** Millilitres are not grams: the ceiling is said in ml, never as "g" (D56). */
    @Test
    fun `an amount of millilitres past the ceiling does not call them grams`() {
        val texts = draw(proposed(aProposedItem(name = "Juice", amount = 6000.0, unit = "ml")))

        assertThat(texts).contains("At most 5000 ml at a time.")
        assertThat(texts).doesNotContain("At most 5000 g at a time.")
    }

    /** A row that is not there is said above the rows, or it is never noticed (D53 §2). */
    @Test
    fun `items dropped from the answer are named above the rows`() {
        val one = proposed(aProposedItem()).copy(dropped = listOf("Sauce"))
        assertThat(draw(one)).contains("1 item in the answer couldn't be used: Sauce")

        val two = proposed(aProposedItem()).copy(dropped = listOf("Sauce", "Pickles"))
        assertThat(draw(two)).contains("2 items in the answer couldn't be used: Sauce, Pickles")
    }

    /**
     * With an item dropped, the answer can be read (issue #1): the button, then the answer
     * pretty-printed and Copy. Shown only — the screen is handed it and keeps nothing.
     */
    @Test
    fun `an answer with a dropped item can be shown`() {
        val state = proposed(aProposedItem())
            .copy(dropped = listOf("Sauce"), answer = """{"items":[{"name":"Sauce"}]}""")

        assertThat(draw(state)).contains(SHOW_ANSWER)
        render.click(SHOW_ANSWER)

        val shown = render.textsAgain()
        assertThat(shown.joinToString("\n")).contains("\"name\": \"Sauce\"")
        assertThat(shown).contains("Copy")
    }

    @Test
    fun `an answer used whole offers no answer to show`() {
        assertThat(draw(proposed(aProposedItem()))).doesNotContain(SHOW_ANSWER)
    }

    /** An answer that failed as unreadable can be read too, under the sentence saying so. */
    @Test
    fun `an unreadable answer can be shown under its failure`() {
        val state = ProposalUiState.Describing(failure = "Could not be understood.", answer = "not JSON")

        assertThat(draw(state)).contains(SHOW_ANSWER)
        render.click(SHOW_ANSWER)
        assertThat(render.textsAgain()).contains("not JSON")
    }

    @Test
    fun `a failure with no answer offers none`() {
        assertThat(draw(ProposalUiState.Describing(failure = "Could not reach the model.")))
            .doesNotContain(SHOW_ANSWER)
    }

    @Test
    fun `nothing dropped says nothing`() {
        assertThat(draw(proposed(aProposedItem())).joinToString()).doesNotContain("couldn't be used")
    }

    // --- The worth, typed over (D53 §1, §3, §6) --------------------------------------------------

    @Test
    fun `the worth line offers to change it`() {
        assertThat(draw(proposed(aProposedItem()))).contains(CHANGE)
    }

    @Test
    fun `Change opens four boxes holding the worth`() {
        val burger = aProposedItem()
        val item = burger.toItemToLog()
        val state = ProposalUiState.Proposed(
            rows = listOf(ProposalRow(burger, item, editingWorth = WorthBoxes.of(item))),
            note = null,
        )

        val texts = draw(state)

        assertThat(texts).containsAtLeast("Calories", "Protein (g)", "Carbs (g)", "Fat (g)")
        assertThat(texts).containsAtLeast("250", "18", "0", "20")
        assertThat(texts).doesNotContain(CHANGE)
    }

    /**
     * Every row draws the same Remove, the same amount box, and — opened — the same four worth
     * boxes, so a screen reader heard two rows' controls by one set of names (public issue #3).
     * Each now says which row it is for; the words on screen are unchanged.
     */
    @Test
    fun `each row's controls say which row they are for`() {
        val burger = aProposedItem()
        val bun = aBun()
        val state = ProposalUiState.Proposed(
            rows = listOf(burger, bun).map { proposed ->
                val item = proposed.toItemToLog()
                ProposalRow(proposed, item, editingWorth = WorthBoxes.of(item))
            },
            note = null,
        )

        val texts = draw(state)

        assertThat(texts.count { it == "Remove" }).isEqualTo(2)
        assertThat(texts.count { it == "Calories" }).isEqualTo(2)
        for (name in listOf("Beef burger", "Hamburger bun")) {
            assertThat(render.describedCount("Remove $name")).isEqualTo(1)
            assertThat(render.describedCount("How much of $name")).isEqualTo(1)
            assertThat(render.describedIsField("How much of $name")).isTrue()
            assertThat(render.describedCount("Done, for $name")).isEqualTo(1)
        }
        assertThat(render.describedCount("Calories per 100 g, for Beef burger")).isEqualTo(1)
        assertThat(render.describedIsField("Calories per 100 g, for Beef burger")).isTrue()
        assertThat(render.describedCount("Fat (g) per bun, for Hamburger bun")).isEqualTo(1)
        // − and + only on the counted row.
        assertThat(render.describedCount("One less of Hamburger bun")).isEqualTo(1)
        assertThat(render.describedCount("One more of Hamburger bun")).isEqualTo(1)
    }

    @Test
    fun `change on each row says which row it opens`() {
        draw(proposed(aProposedItem(), aBun()))

        assertThat(render.describedCount("Change, for Beef burger")).isEqualTo(1)
        assertThat(render.describedCount("Change, for Hamburger bun")).isEqualTo(1)
    }

    /** A typed worth is his, and a typed row says nothing about where it came from (D7a). */
    @Test
    fun `after a change the estimate's origin line is gone`() {
        val burger = aProposedItem()
        val opened = WorthBoxes.of(burger.toItemToLog())!!.with(WorthFigure.KCAL, "240")
        val item = burger.toItemToLog().copy(worth = opened.worth()!!)
        val state = ProposalUiState.Proposed(
            rows = listOf(ProposalRow(burger, item, editingWorth = null)),
            note = null,
        )

        val texts = draw(state)

        assertThat(texts).contains("per 100 g: 240 kcal · P 18 · C 0 · F 20")
        assertThat(texts).contains("480 kcal · P 36 · C 0 · F 40")
        assertThat(texts).doesNotContain("Estimated — moderate confidence")
    }

    /**
     * A row held back by a refused worth box names the worth, not the amount: "say how much" over a
     * row whose amount is fine sends him to the wrong box.
     */
    @Test
    fun `a row held back by its worth says so, not how much`() {
        val burger = aProposedItem()
        val item = burger.toItemToLog()
        val boxes = WorthBoxes.of(item)!!.with(WorthFigure.FAT, "")
        val state = ProposalUiState.Proposed(
            rows = listOf(ProposalRow(burger, item, editingWorth = boxes)),
            note = null,
        )

        val texts = draw(state)

        assertThat(texts).contains("Say what Beef burger is worth to save this.")
        assertThat(texts).doesNotContain("Say how much Beef burger was to save this.")
    }

    @Test
    fun `a worth past its ceiling says the ceiling and turns saving off`() {
        val burger = aProposedItem()
        val item = burger.toItemToLog()
        val boxes = WorthBoxes.of(item)!!.with(WorthFigure.KCAL, "1001")
        val state = ProposalUiState.Proposed(
            rows = listOf(ProposalRow(burger, item, editingWorth = boxes)),
            note = null,
        )

        val texts = draw(state)

        assertThat(texts).contains(
            "All four per 100 g (at most 1000 kcal, and 110 g of protein, carbohydrate or fat).",
        )
        assertThat(render.isEnabled("Save this meal")).isFalse()
    }

    // --- His own foods (D53 §4, §5) --------------------------------------------------------------

    @Test
    fun `his own food's row says so and offers the estimate`() {
        val row = ProposalRow.of(aModelPita(), listOf(hisPita()))

        val texts = draw(ProposalUiState.Proposed(rows = listOf(row), note = null))

        assertThat(texts).contains("From your foods")
        assertThat(texts).contains("Use the estimate (165 kcal)")
        assertThat(texts).contains("250 kcal · P 8 · C 50 · F 1")
        // His Pita's figure is typed, and a typed figure says nothing about where it came from.
        assertThat(texts).doesNotContain("Estimated — moderate confidence")
    }

    /** "From your foods" is wording above the origin line, never in place of it (D53 §3). */
    @Test
    fun `his food's own origin is still said beneath it`() {
        val estimated = hisPita().copy(
            facts = FoodFacts(
                perUnit = PerUnit(
                    "pita",
                    Nutrients(250.0, 8.0, 50.0, 1.0),
                    Provenance(Source.AI_ESTIMATE, Confidence.LOW, setAtMillis = 0),
                ),
            ),
        )
        val row = ProposalRow.of(aModelPita(), listOf(estimated))

        val texts = draw(ProposalUiState.Proposed(rows = listOf(row), note = null))

        assertThat(texts).contains("From your foods")
        assertThat(texts).contains("Estimated — low confidence")
        assertThat(render.isDrawnBefore("From your foods", "Estimated — low confidence")).isTrue()
    }

    @Test
    fun `his food counted in grams says so, and offers to count it in grams`() {
        val row = ProposalRow.of(aBun(), listOf(hisBun()))

        val texts = draw(ProposalUiState.Proposed(rows = listOf(row), note = null))

        assertThat(texts).contains(
            "Your Hamburger bun is counted in grams. Say how many grams to use its figures.",
        )
        assertThat(texts).contains("Count it in grams")
        assertThat(texts).doesNotContain("From your foods")
        assertThat(texts).contains("Estimated — moderate confidence")
    }

    @Test
    fun `a close match is one question with his food's name in it`() {
        val yoghurt = aProposedItem(name = "Yoghurt", amount = 150.0, unit = "g")
        val greek = Food(id = 9, name = "Greek yoghurt", facts = hisBun().facts)
        val row = ProposalRow.of(yoghurt, listOf(greek))

        val texts = draw(ProposalUiState.Proposed(rows = listOf(row), note = null))

        assertThat(texts).contains("Use your Greek yoghurt?")
        assertThat(texts).doesNotContain("From your foods")
    }

    @Test
    fun `no match draws no line about his foods`() {
        val texts = draw(proposed(aProposedItem()))

        assertThat(texts.none { it.startsWith("Use your") || it.startsWith("Use the estimate") })
            .isTrue()
        assertThat(texts).doesNotContain("From your foods")
    }

    // --- "2 portion" on the proposal (D37, #27) -------------------------------------------------

    /** The app's own "portion" beside the box takes its plural from the number in the box. */
    @Test
    fun `the model's two portions read as portions`() {
        val texts = draw(proposed(aProposedItem(name = "Stew", amount = 2.0, unit = "portion")))

        assertThat(texts).contains("portions")
        assertThat(texts).doesNotContain("portion")
    }

    @Test
    fun `the model's one portion reads as one portion`() {
        val texts = draw(proposed(aProposedItem(name = "Stew", amount = 1.0, unit = "portion")))

        assertThat(texts).contains("portion")
        assertThat(texts).doesNotContain("portions")
    }

    /** A unit the model named is not the app's to pluralise (D37). */
    @Test
    fun `a unit the model named is drawn as written`() {
        val texts = draw(proposed(aBun(amount = 2.0, unit = "slice")))

        assertThat(texts).contains("slice")
        assertThat(texts).doesNotContain("slices")
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
                aProposedItem(name = "Milk", amount = 120.0, unit = "ml"),
                aProposedItem(name = "Espresso", amount = 1.0, unit = "cup"),
            ),
        )

        assertThat(texts).contains(KEEP_AS_MEAL)
        // The offer is an addition: accepting plainly is still the first thing on the screen.
        assertThat(texts).contains("Save this meal")
    }

    /** A meal of one is a food already, and this app has a way of keeping one of those. */
    @Test
    fun `a one-item answer does not offer it`() {
        val texts = draw(proposed(aProposedItem(name = "Espresso", amount = 1.0, unit = "cup")))

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

    /** Asking that threw is said where any other failure to answer is. */
    @Test
    fun `an ask that threw says nothing was changed`() {
        val texts = draw(ProposalUiState.Describing(refused = ActionRefused.NOTHING_CHANGED))

        assertThat(texts).contains(
            "That didn't work, and nothing was changed. " +
                "What went wrong is under Settings → Recent problems.",
        )
    }

    /**
     * Keeping the answer as a meal writes it first, and a write that fails never opens the naming
     * sheet — so the sentence has to be on the accept screen itself, beside the answer that is still
     * there to be tried again.
     */
    @Test
    fun `a keep that failed says so on the answer, outside the naming sheet`() {
        val answer = proposed(
            aProposedItem(name = "Milk", amount = 120.0, unit = "ml"),
            aProposedItem(name = "Espresso", amount = 1.0, unit = "cup"),
        )
        // The day's answer, arriving after the tap the way the real write's does.
        var failure by mutableStateOf<String?>(null)
        render.texts {
            ProposalScreen(
                state = answer,
                description = "",
                onDescribe = {},
                onSetAmount = { _, _ -> },
                onStep = { _, _ -> },
                onOpenWorth = {},
                onSetWorthBox = { _, _, _ -> },
                onCloseWorth = {},
                onUseYourFood = {},
                onUseEstimate = {},
                onCountInFoodUnit = {},
                onRemove = {},
                onTellItMore = {},
            conversation = NO_CONVERSATION,
                onSave = {},
                onTypeItMyself = {},
                onAddKey = {},
                onCancel = {},
                onKeepAsMeal = { failure = MAYBE_PARTIAL },
                onNameMeal = {},
                onGiveUpNaming = {},
                onKeepingDone = {},
                chosenRows = emptyList(),
                isToday = true,
                refusal = failure,
            )
        }

        render.click(KEEP_AS_MEAL)
        val texts = render.textsAgain()

        assertThat(texts).contains(MAYBE_PARTIAL)
        // The answer is still there to try again, and the offer can be pressed again.
        assertThat(texts).contains(KEEP_AS_MEAL)
        assertThat(render.isEnabled(KEEP_AS_MEAL)).isTrue()
    }

    /**
     * The answer now stays on screen until the day has written it, so while the write is out
     * neither button may be pressed: a second tap would log the same meal twice.
     */
    @Test
    fun `while a keep is being written the answer cannot be accepted again`() {
        draw(
            proposed(
                aProposedItem(name = "Milk", amount = 120.0, unit = "ml"),
                aProposedItem(name = "Espresso", amount = 1.0, unit = "cup"),
            ),
        )

        render.click(KEEP_AS_MEAL)
        render.textsAgain()

        assertThat(render.isEnabled(KEEP_AS_MEAL)).isFalse()
        assertThat(render.isEnabled("Save this meal")).isFalse()
    }

    private val typed = Provenance(Source.TYPED, null, setAtMillis = 0)

    /** The spec's invented Pita: per pita, 250 kcal · P 8 · C 50 · F 1, typed. */
    private fun hisPita() = Food(
        id = 7,
        name = "Pita",
        facts = FoodFacts(perUnit = PerUnit("pita", Nutrients(250.0, 8.0, 50.0, 1.0), typed)),
    )

    /** The spec's invented Hamburger bun: per 100 g only, 270 kcal · P 9 · C 50 · F 4, typed. */
    private fun hisBun() = Food(
        id = 8,
        name = "Hamburger bun",
        facts = FoodFacts(per100g = PerHundredGrams(Nutrients(270.0, 9.0, 50.0, 4.0), typed)),
    )

    private fun proposed(vararg items: ProposedItem) = ProposalUiState.Proposed(
        rows = items.map { ProposalRow(it, it.toItemToLog()) },
        note = null,
    )

    private fun draw(state: ProposalUiState): List<String> = render.texts {
        ProposalScreen(
            state = state,
            description = "",
            onDescribe = {},
            onSetAmount = { _, _ -> },
            onStep = { _, _ -> },
            onOpenWorth = {},
            onSetWorthBox = { _, _, _ -> },
            onCloseWorth = {},
            onUseYourFood = {},
            onUseEstimate = {},
            onCountInFoodUnit = {},
            onRemove = {},
            onTellItMore = {},
            conversation = NO_CONVERSATION,
            onSave = {},
            onTypeItMyself = {},
            onAddKey = {},
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

        /** `R.string.propose_count_fewer` and `propose_count_more`, as the phone draws them. */
        const val MINUS = "−"
        const val PLUS = "+"

        /** `R.string.propose_change_worth`, as the phone draws it. */
        const val CHANGE = "Change"

        const val SHOW_ANSWER = "Show the model's answer"

        /** `R.string.action_refused_maybe_partial`, as the phone draws it. */
        const val MAYBE_PARTIAL = "That didn't finish, and may have only partly happened. " +
            "What went wrong is under Settings → Recent problems."
    }
}
