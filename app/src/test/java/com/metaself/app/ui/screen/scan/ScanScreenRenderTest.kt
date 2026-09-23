package com.metaself.app.ui.screen.scan

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.product.Product
import com.metaself.app.domain.product.ProductField
import com.metaself.app.domain.product.ProductForm
import com.metaself.app.ui.scan.ContributeWording
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JUnit 4 by necessity — Robolectric's runner is JUnit 4. */
@RunWith(RobolectricTestRunner::class)
class ScanScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    /**
     * Nothing about this behaviour changes as the day's ways in are rearranged. The test exists so
     * that the claim "the barcode path still falls through to describing" is checked rather than
     * asserted. The screen is stateless and the camera is behind `hasCamera`, so drawing it here
     * costs nothing.
     *
     * An entry the database holds but whose figures are no quantity of food is treated as not found
     * (D39, issue #31) — the same state as a barcode it does not know — so this screen is where he
     * lands, and it must also offer the way out that fits: type it from the packet. So must the
     * offline screen, which is where a refused packet already on the phone lands with no network.
     */
    @Test
    fun `a barcode the database does not know still offers to describe it`() {
        val texts = draw(ScanUiState.NotFound(barcode = "7290000066318", couldNotAsk = false))

        assertThat(texts).contains("Describe it in words instead")
        assertThat(texts).contains("Add it yourself")

        val offline = draw(ScanUiState.NotFound(barcode = "7290000066318", couldNotAsk = true))

        assertThat(offline).contains("Add it yourself")
    }

    /**
     * "Infinity" pasted into the fat box is refused under that box with its own reason, and the box
     * still shows what he pasted, so he can see what to fix (D39, issue #31). Once only: the reason
     * belongs to the fat box and no other. Since D42 (issue #32) the reason names its ceiling,
     * and a figure just past it is refused in the same words.
     */
    @Test
    fun `a figure that is no quantity is refused under its box, and the box keeps it`() {
        val form = ProductForm(
            barcode = "7290000066318",
            name = "במבה",
            brand = "אסם",
            kcalPer100g = "535",
            proteinPer100g = "17",
            carbsPer100g = "49",
            fatPer100g = "Infinity",
            servingSizeG = "80",
        )

        val texts = draw(ScanUiState.Adding(form, showErrors = true))

        assertThat(texts).contains("Fat per 100 g")
        assertThat(texts.filter { it == FAT_REFUSED }).hasSize(1)
        assertThat(texts.indexOf(FAT_REFUSED)).isGreaterThan(texts.indexOf("Fat per 100 g"))
        assertThat(texts.indexOf(FAT_REFUSED))
            .isLessThan(texts.indexOf("Serving size in grams (optional)"))
        assertThat(texts).contains("Infinity")
    }

    /**
     * What is typed on this form is kept exactly as printed on the packet's own record (the
     * barcode's product row, which stores decimals). What reaches the day is a logged row in whole
     * grams, and what is typed here reaches a food only when the packet is logged (since issue #28
     * the food then takes the packet's figures as printed), so the shared "…on this food." sentence
     * the food editor and the builder carry would be false here (issue #18, D38). This form
     * therefore has its own sentence, pinned word for word so it cannot drift back to the shared
     * one, and never "whole grams" on its own as a rule for what he types, which would have him
     * round 0.7 g off the label by hand. Its only mention of whole grams is about what goes on the
     * day, which is true.
     */
    @Test
    fun `typing in a packet says decimals are kept, before anything is typed`() {
        val texts = draw(ScanUiState.Adding(ProductForm(barcode = "7290000066318")))

        assertThat(texts).contains(PACKET_FIGURES_KEPT)
        assertThat(texts.indexOf(PACKET_FIGURES_KEPT)).isLessThan(texts.indexOf("Calories per 100 g"))
        assertThat(texts.indexOf(PACKET_FIGURES_KEPT)).isLessThan(texts.indexOf("Protein per 100 g"))
        // The shared sentence claims the figures are kept "on this food"; this form has no food.
        assertThat(texts).doesNotContain(DECIMALS_KEPT_ON_THIS_FOOD)
        assertThat(texts.none { it.contains("on this food") }).isTrue()
        // Nothing on the form tells him to type whole grams; the one mention is about the day.
        assertThat(texts.filter { it.contains("whole grams") }).containsExactly(PACKET_FIGURES_KEPT)
    }

    // --- A packet the database has without every figure (D40, issue #30) ---

    private val bambaWithoutFat = ProductForm.prefilled(
        barcode = "7290000066318",
        name = "במבה",
        brand = "אסם",
        kcalPer100g = 535.0,
        proteinPer100g = 17.0,
        carbsPer100g = 49.0,
        fatPer100g = null,
        servingSizeG = 80.0,
    )

    /**
     * The sentence comes first, under the title: it is why he is on this form rather than the
     * found screen. The figures the database had are in their boxes (read as the fields' contents,
     * which is what `EditableText` carries), and the fat box — the one it lacked — is empty, not 0.
     * A merged field node yields its label and then its value, so the entry after a label is what
     * that box holds.
     */
    @Test
    fun `a packet missing its fat says so, above a form filled with the rest`() {
        val texts = draw(
            ScanUiState.Adding(bambaWithoutFat, databaseLacks = listOf(ProductField.FAT)),
        )

        assertThat(texts).contains(LACKS_FAT)
        assertThat(texts.indexOf(LACKS_FAT)).isLessThan(texts.indexOf(PACKET_FIGURES_KEPT))
        assertThat(texts.indexOf(LACKS_FAT)).isLessThan(texts.indexOf("Calories per 100 g"))
        assertThat(texts).containsAtLeast("במבה", "אסם", "535", "17", "49", "80")
        assertThat(texts[texts.indexOf("Fat per 100 g") + 1]).isEqualTo("")
        assertThat(texts[texts.indexOf("Protein per 100 g") + 1]).isEqualTo("17")
        assertThat(texts[texts.indexOf("Calories per 100 g") + 1]).isEqualTo("535")
    }

    /** Named in the form's order and joined as a sentence; plural once there are two. */
    @Test
    fun `two missing figures are named together`() {
        val two = draw(
            ScanUiState.Adding(
                bambaWithoutFat.copy(proteinPer100g = ""),
                databaseLacks = listOf(ProductField.PROTEIN, ProductField.FAT),
            ),
        )
        assertThat(two).contains(LACKS_PROTEIN_AND_FAT)

        val four = draw(
            ScanUiState.Adding(
                ProductForm(barcode = "7290000066318", name = "במבה"),
                databaseLacks = listOf(
                    ProductField.KCAL, ProductField.PROTEIN, ProductField.CARBS, ProductField.FAT,
                ),
            ),
        )
        assertThat(four).contains(LACKS_ALL_FOUR)
    }

    /**
     * Saving with the fat box still empty is refused where every empty required box is refused —
     * under that box, in its existing words — and the sentence saying why it is empty stays.
     */
    @Test
    fun `saving without the missing figure is refused under its box`() {
        val texts = draw(
            ScanUiState.Adding(
                bambaWithoutFat,
                showErrors = true,
                databaseLacks = listOf(ProductField.FAT),
            ),
        )

        assertThat(texts.filter { it == FAT_REFUSED }).hasSize(1)
        assertThat(texts.indexOf(FAT_REFUSED)).isGreaterThan(texts.indexOf("Fat per 100 g"))
        assertThat(texts.indexOf(FAT_REFUSED))
            .isLessThan(texts.indexOf("Serving size in grams (optional)"))
        assertThat(texts).contains(LACKS_FAT)
    }

    /**
     * Add it yourself opens a blank form; the database has nothing to be said about there, and
     * "copy these from the package itself" is the whole instruction — every box is his to copy.
     */
    @Test
    fun `a form he opens himself says nothing about the database`() {
        val texts = draw(ScanUiState.Adding(ProductForm(barcode = "7290000066318")))

        assertThat(texts.none { it.contains("has this packet") }).isTrue()
        assertThat(texts).contains(ContributeWording.ONLY_THE_LABEL)
        assertThat(texts.indexOf(ContributeWording.ONLY_THE_LABEL))
            .isLessThan(texts.indexOf("Calories per 100 g"))
    }

    /**
     * On the pre-filled form the sentence about the database already says what to copy and that
     * the rest came from the database; "copy these from the package itself" under it would tell
     * him to copy boxes that are already filled. One instruction, not two that pull apart (D40).
     */
    @Test
    fun `a pre-filled form does not also tell him to copy every box`() {
        val texts = draw(
            ScanUiState.Adding(bambaWithoutFat, databaseLacks = listOf(ProductField.FAT)),
        )

        assertThat(texts).contains(LACKS_FAT)
        assertThat(texts).doesNotContain(ContributeWording.ONLY_THE_LABEL)
        assertThat(texts.none { it.contains("from the package itself") }).isTrue()
    }

    /** A figure just past its ceiling is refused exactly as "Infinity" is: once, under its box. */
    @Test
    fun `a fat just past its ceiling is refused once, under its box`() {
        val form = ProductForm(
            barcode = "7290000066318",
            name = "במבה",
            brand = "אסם",
            kcalPer100g = "535",
            proteinPer100g = "17",
            carbsPer100g = "49",
            fatPer100g = "110.5",
            servingSizeG = "80",
        )

        val texts = draw(ScanUiState.Adding(form, showErrors = true))

        assertThat(texts.filter { it == FAT_REFUSED }).hasSize(1)
        assertThat(texts.indexOf(FAT_REFUSED)).isGreaterThan(texts.indexOf("Fat per 100 g"))
        assertThat(texts.indexOf(FAT_REFUSED))
            .isLessThan(texts.indexOf("Serving size in grams (optional)"))
        assertThat(texts).contains("110.5")
    }

    // --- The grams box has a ceiling, and Log it never closes silently (D42, issue #32) ---

    /** The real figures from Bamba's entry in Open Food Facts, checked on 2026-09-04. */
    private val bamba = Product(
        barcode = "7290000066318",
        name = "במבה",
        brand = "אסם",
        kcalPer100g = 535.0,
        proteinPer100g = 17.0,
        carbsPer100g = 49.0,
        fatPer100g = 30.0,
        servingSizeG = 80.0,
    )

    /** The worked-out total for an amount, as against the label's own line, which is per 100 g. */
    private fun List<String>.totalLines() =
        filter { it.contains(" kcal · ") && !it.endsWith("per 100 g") }

    /**
     * "Infinity" in the grams box used to leave Log it on and close the screen with nothing logged.
     * The box keeps what he pasted, the ceiling is named under it, there is no total to agree to,
     * and a believable amount says nothing of the sort.
     */
    @Test
    fun `an amount past 5000 g is refused under the box`() {
        val texts = draw(ScanUiState.Found(bamba, fromThisPhone = false, grams = "Infinity"))

        assertThat(texts).contains("Infinity")
        assertThat(texts.filter { it == GRAMS_TOO_MUCH }).hasSize(1)
        assertThat(texts.indexOf(GRAMS_TOO_MUCH)).isGreaterThan(texts.indexOf("How much, in grams"))
        assertThat(texts.indexOf(GRAMS_TOO_MUCH)).isLessThan(texts.indexOf("Log it"))
        assertThat(texts.totalLines()).isEmpty()

        val usual = draw(ScanUiState.Found(bamba, fromThisPhone = false, grams = "80"))
        assertThat(usual).doesNotContain(GRAMS_TOO_MUCH)
        assertThat(usual).contains("428 kcal · P 14 · C 39 · F 24")
        assertThat(usual).doesNotContain(NOTHING_LOGGED_AMOUNT)
        assertThat(usual).doesNotContain(NOTHING_LOGGED_PACKET)
    }

    /**
     * Log it pressed with nothing it could log: the screen stays, and says why under the button,
     * in words that fit what was wrong — the amount, or a packet whose figures no row can be made
     * from.
     */
    @Test
    fun `Log it that logged nothing says why, on the same screen`() {
        val packet = draw(
            ScanUiState.Found(
                bamba.copy(kcalPer100g = 1e12),
                fromThisPhone = true,
                grams = "80",
                nothingLogged = NothingLogged.PACKET,
            ),
        )
        assertThat(packet.filter { it == NOTHING_LOGGED_PACKET }).hasSize(1)
        assertThat(packet.indexOf(NOTHING_LOGGED_PACKET)).isGreaterThan(packet.indexOf("Log it"))
        assertThat(packet.indexOf(NOTHING_LOGGED_PACKET)).isLessThan(packet.indexOf("Scan another"))
        assertThat(packet).doesNotContain(NOTHING_LOGGED_AMOUNT)

        val amount = draw(
            ScanUiState.Found(
                bamba,
                fromThisPhone = false,
                grams = "Infinity",
                nothingLogged = NothingLogged.AMOUNT,
            ),
        )
        assertThat(amount.filter { it == NOTHING_LOGGED_AMOUNT }).hasSize(1)
        assertThat(amount.indexOf(NOTHING_LOGGED_AMOUNT)).isGreaterThan(amount.indexOf("Log it"))
        assertThat(amount).doesNotContain(NOTHING_LOGGED_PACKET)
    }

    private fun draw(state: ScanUiState): List<String> = render.texts {
        ScanScreen(
            state = state,
            hasCamera = false,
            onBarcode = {},
            onSetGrams = {},
            onAddByHand = {},
            onSetForm = {},
            onSaveTyped = {},
            onContribute = {},
            onSave = {},
            onScanAgain = {},
            onDescribeInstead = {},
            onBack = {},
        )
    }

    private companion object {
        /** The fat box's own reason, the one a negative already got, naming its ceiling (D42). */
        const val FAT_REFUSED = "Fat per 100 g, in grams (at most 110)."

        /** Under the grams box, only when what he typed is a number past 5000 g (D42). */
        const val GRAMS_TOO_MUCH = "At most 5000 g at a time."

        /** Under Log it, when it was pressed and nothing could be logged (D42, issue #32). */
        const val NOTHING_LOGGED_AMOUNT =
            "Nothing was logged — the amount above is not one this can count."

        const val NOTHING_LOGGED_PACKET = "Nothing was logged — this packet's figures are not " +
            "ones this app can count. Scan again to look it up afresh."

        /**
         * This form's own sentence: what is kept exactly is the packet, and what goes on the day is
         * whole grams — both true of a scan, unlike "on this food".
         */
        const val PACKET_FIGURES_KEPT = "Type the numbers as printed — 0.5 g is kept as 0.5 g for " +
            "this packet. What goes on the day is counted in whole grams."

        /** The sentence My foods' editor and the builder's new-food form share; not this form's. */
        const val DECIMALS_KEPT_ON_THIS_FOOD =
            "Numbers here can have a decimal point: 0.5 g is kept as 0.5 g on this food."

        /**
         * Why a scanned packet opened the label form (D40): the database has it, lacks these
         * figures, and filled the boxes it could. Pinned word for word, one figure and several.
         */
        const val LACKS_FAT = "The food database has this packet but no fat figure for it. " +
            "Copy it from the packet — the figures it has are filled in from the database."

        const val LACKS_PROTEIN_AND_FAT = "The food database has this packet but no protein or " +
            "fat figures for it. Copy them from the packet — the figures it has are filled in " +
            "from the database."

        const val LACKS_ALL_FOUR = "The food database has this packet but no calorie, protein, " +
            "carbohydrate or fat figures for it. Copy them from the packet — the figures it has " +
            "are filled in from the database."
    }
}
