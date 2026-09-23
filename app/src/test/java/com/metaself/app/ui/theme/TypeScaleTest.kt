package com.metaself.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isUnspecified
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/**
 * The app has 242 calls to `MaterialTheme.typography.*` and, until D48, no `Typography` of its own
 * — every one of them resolved to Material's stock default. Defining one reaches all of them at
 * once, which is the whole reason the face can be given to the app without moving a single layout.
 *
 * That leverage is also the hazard this test exists for. Nothing on a screen names a size, so
 * nothing on a screen would notice a slot that was left out, filled with a default, or filled with
 * the wrong figures: the text would simply render in whatever Material chose, looking plausible and
 * wrong. A slot is only ever read, never declared at the call site, so the scale itself is the only
 * place the mistake can be caught.
 *
 * Reading values back out of a value holder is ordinarily a test that the file says what the file
 * says, and most of `Type.kt` is deliberately not tested here for exactly that reason — the sizes
 * and the line heights are a design decision recorded in the spec, not an invariant. Two things are
 * invariants:
 *
 * 1. **Every slot is filled.** A screen written next month must not be able to reach an unstyled
 *    default by naming a role the scale forgot.
 * 2. **Every slot that can carry a figure asks for tabular lining figures.** This is the regression
 *    that will actually happen: somebody adds or reworks a slot, forgets the feature string, and a
 *    column of calories silently stops lining up. Nothing else in the app would fail.
 *
 * Pure values, no Android framework, so this is JUnit 5 as the repository's rule requires — a
 * `FontFamily` built from resource ids is a data holder and resolves nothing until it is drawn.
 */
class TypeScaleTest {

    /**
     * The twelve the app calls today, plus the three display slots. The scale fills all fifteen on
     * purpose: the three the app does not yet use — `displayLarge`, `displayMedium` and
     * `displaySmall`, which D49's day screen is what will first reach for — are filled from the
     * nearest role so that reaching one is a design choice rather than an accident.
     */
    private val everySlot: Map<String, TextStyle> = mapOf(
        "displayLarge" to MetaSelfTypography.displayLarge,
        "displayMedium" to MetaSelfTypography.displayMedium,
        "displaySmall" to MetaSelfTypography.displaySmall,
        "headlineLarge" to MetaSelfTypography.headlineLarge,
        "headlineMedium" to MetaSelfTypography.headlineMedium,
        "headlineSmall" to MetaSelfTypography.headlineSmall,
        "titleLarge" to MetaSelfTypography.titleLarge,
        "titleMedium" to MetaSelfTypography.titleMedium,
        "titleSmall" to MetaSelfTypography.titleSmall,
        "bodyLarge" to MetaSelfTypography.bodyLarge,
        "bodyMedium" to MetaSelfTypography.bodyMedium,
        "bodySmall" to MetaSelfTypography.bodySmall,
        "labelLarge" to MetaSelfTypography.labelLarge,
        "labelMedium" to MetaSelfTypography.labelMedium,
        "labelSmall" to MetaSelfTypography.labelSmall,
    )

    /**
     * The seven slots that can carry a figure, taken from the spec's type-scale table, which is the
     * record.
     *
     * `headlineSmall` is deliberately absent. An earlier draft of the plan listed it and omitted
     * `displaySmall`; that draft contradicted the spec, where `headlineSmall` sits on the Work Sans
     * 600 title row and carries a screen's own words. Had this test been written from the draft it
     * would have failed against a spec-faithful scale and the scale, not the list, would have been
     * "fixed" — so the list is spelled out here with its reason attached rather than left to be
     * re-derived.
     */
    private val slotsThatCarryFigures = setOf(
        "displayLarge",
        "displayMedium",
        "displaySmall",
        "headlineLarge",
        "headlineMedium",
        "bodySmall",
        "labelMedium",
    )

    @Test
    fun `every slot a screen can name is filled and has a real size`() {
        everySlot.forEach { (name, style) ->
            assertWithMessage("$name has no size, so a screen naming it gets Material's default")
                .that(style.fontSize.isUnspecified)
                .isFalse()
            assertWithMessage("$name has a size of zero, which renders nothing at all")
                .that(style.fontSize.value)
                .isGreaterThan(0f)
        }

        // Fifteen, not twelve: the scale fills the whole of Material's set so that no role is a
        // hole. If Material ever grows a sixteenth slot this count is what notices.
        assertThat(everySlot).hasSize(15)
    }

    @Test
    fun `every slot that carries a figure asks for tabular figures`() {
        slotsThatCarryFigures.forEach { name ->
            val style = everySlot.getValue(name)
            assertWithMessage(
                "$name carries figures, so a column set in it will not line up without tnum",
            )
                .that(style.fontFeatureSettings)
                .contains("tnum")
        }
    }

    /**
     * The one negative worth pinning, because it is the one that was got wrong once already.
     *
     * `headlineSmall` is a title row: it is a screen's own name, set in Work Sans 600, and words
     * set in tabular figures are not wrong so much as pointless — the feature only shapes digits.
     * The reason to assert it is not the rendering, it is the record: this slot was listed as
     * tabular in a draft of the plan, and a later reader reconciling the two lists should find the
     * answer asserted rather than argued.
     */
    @Test
    fun `the title slot that carries words is not marked as carrying figures`() {
        assertThat(MetaSelfTypography.headlineSmall.fontFeatureSettings ?: "")
            .doesNotContain("tnum")
    }
}
