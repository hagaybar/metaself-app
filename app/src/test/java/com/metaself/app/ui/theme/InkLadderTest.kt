package com.metaself.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * D48 replaces the single grey the app used for everything with a ladder of four steps. **The
 * reason for the ladder is ranking, not contrast.** One grey was carrying brand names, aliases, row
 * figures and footnotes at once, so nothing on a screen outranked anything else and finding a food
 * meant reading rather than scanning. The small print was never hard to read: the caption colour
 * the ladder inherits measures 9.09:1 on the light page and 10.92:1 on the dark one, against a
 * 4.5:1 floor.
 *
 * This test exists because the first draft of the ladder claimed otherwise. It asserted, here and
 * in `Colours.kt`, that the old grey measured 3.9:1 and failed WCAG AA. Nothing in the palette ever
 * did. Acting on that invented number, the draft LIGHTENED every step — light captions 9.09 → 5.71,
 * dark 10.92 → 6.91 — and landed dark captions on a card at 3.47:1, a genuine AA failure the app
 * had not had before. The test in that draft did not catch it, because it measured only against
 * `background` and the failure was on `surfaceVariant`.
 *
 * So two invariants, and the second is the one that was missing:
 *
 * 1. Every text step clears 4.5:1 on the page, in both schemes.
 * 2. Every text step clears 4.5:1 on `surfaceVariant` too — a real card background in this app;
 *    the top bar is drawn on it (`MetaSelfScreen.kt`). Text lands on both, so both are measured.
 *
 * The formula is WCAG 2.1's, written out here in full on purpose. Pulling in Compose's own
 * `luminance()` would test the ladder against the same library the app draws with; writing the
 * eight lines means the number is arrived at independently of everything the app does.
 *
 * **Three steps are asserted, not four, and `outline` is not among them.** `Rule` is a hairline and
 * `outline` is a border; neither is ever text, so a 4.5:1 floor is meaningless for either — a
 * divider that passed text contrast would be a black line across the page.
 *
 * Where a step is wired to a Material slot it is read off the scheme, because the scheme is what
 * every screen actually renders from; reading the named constant instead would prove the constant
 * is legible and prove nothing about the app. `Ink two` is wired to no slot by design — it is the
 * step D49's running prose and margin notes take — so it is the one that has to be read by name.
 */
class InkLadderTest {

    /**
     * WCAG 2.1 relative luminance: undo the sRGB transfer curve on each channel, then weight the
     * three by how much the eye gets from each. The 0.03928 knee and the 2.4 exponent are the
     * specification's, not an approximation of it.
     */
    private fun luminance(colour: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(colour.red) +
            0.7152 * channel(colour.green) +
            0.0722 * channel(colour.blue)
    }

    /** The ratio of the lighter to the darker, each lifted by 0.05 so that pure black is finite. */
    private fun contrast(one: Color, other: Color): Double {
        val a = luminance(one)
        val b = luminance(other)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /**
     * AA for body text. The app's small print is 11 sp at its smallest, which is nowhere near the
     * 18 pt that would let a step qualify under the large-text floor of 3:1, so every text step in
     * this app is held to 4.5 regardless of which slot it lands in.
     */
    private val floorForText = 4.5

    /**
     * The three steps that carry text in the light scheme, read off the scheme wherever a slot
     * claims one. `Ink two` is wired to no slot by design and so is read by name.
     */
    private val lightTextSteps = mapOf(
        // The answer: names, figures, headings. Wired to onBackground and onSurface.
        "Ink" to MetaSelfLightColours.onBackground,
        // Prose that is read but not scanned. No Material slot claims it, by design.
        "Ink two" to MetaSelfLightInkTwo,
        // Captions, origins, the colophon.
        "Ink three" to MetaSelfLightColours.onSurfaceVariant,
    )

    /** The same three in the dark scheme. */
    private val darkTextSteps = mapOf(
        "Ink" to MetaSelfDarkColours.onBackground,
        "Ink two" to MetaSelfDarkInkTwo,
        "Ink three" to MetaSelfDarkColours.onSurfaceVariant,
    )

    private fun assertLegible(steps: Map<String, Color>, behind: Color, where: String) {
        steps.forEach { (name, ink) ->
            val ratio = contrast(ink, behind)
            assertWithMessage("$name on the $where is %s:1", ratio)
                .that(ratio)
                .isAtLeast(floorForText)
        }
    }

    @Test
    fun `every ink step that carries text is legible on the light page`() {
        assertLegible(lightTextSteps, MetaSelfLightColours.background, "light page")
    }

    @Test
    fun `every ink step that carries text is legible on the dark page`() {
        assertLegible(darkTextSteps, MetaSelfDarkColours.background, "dark page")
    }

    /**
     * The page is not the only thing text lands on. `surfaceVariant` is a card background in this
     * app — the top bar of every screen is filled with it and its title and icons are drawn on top
     * — and it is lighter than the page in the dark scheme, so it is the harder of the two there.
     *
     * This is the assertion whose absence let a 3.47:1 dark caption through: the ladder's draft
     * values passed on the page and failed here, and nothing looked.
     */
    @Test
    fun `every ink step that carries text is legible on a light card`() {
        assertLegible(lightTextSteps, MetaSelfLightColours.surfaceVariant, "light card")
    }

    @Test
    fun `every ink step that carries text is legible on a dark card`() {
        assertLegible(darkTextSteps, MetaSelfDarkColours.surfaceVariant, "dark card")
    }

    /**
     * The ladder is only worth anything if the screens are drawn from it, and a named value that
     * nothing is wired to is a note, not a decision. This says the two steps that do map to a slot
     * are the same colour as the step they are named after — so a later edit that retunes `Ink`
     * without retuning `onBackground`, or the reverse, stops being silent.
     *
     * `onSurface` is checked against `onBackground` rather than against `Ink` a second time for the
     * same reason: the day's page and the cards on it must not drift apart into two blacks.
     */
    @Test
    fun `the steps that map to a Material slot are wired to it in both schemes`() {
        assertWithMessage("light Ink is not what a screen's text actually reads")
            .that(MetaSelfLightColours.onBackground)
            .isEqualTo(MetaSelfLightInk)
        assertWithMessage("light Ink three is not what a caption actually reads")
            .that(MetaSelfLightColours.onSurfaceVariant)
            .isEqualTo(MetaSelfLightInkThree)
        assertWithMessage("the light page and the light card disagree about black")
            .that(MetaSelfLightColours.onSurface)
            .isEqualTo(MetaSelfLightColours.onBackground)

        assertWithMessage("dark Ink is not what a screen's text actually reads")
            .that(MetaSelfDarkColours.onBackground)
            .isEqualTo(MetaSelfDarkInk)
        assertWithMessage("dark Ink three is not what a caption actually reads")
            .that(MetaSelfDarkColours.onSurfaceVariant)
            .isEqualTo(MetaSelfDarkInkThree)
        assertWithMessage("the dark page and the dark card disagree about black")
            .that(MetaSelfDarkColours.onSurface)
            .isEqualTo(MetaSelfDarkColours.onBackground)
    }

    /**
     * A box the review changed and he has not saved (D54 §11) is drawn with a `tertiaryContainer`
     * fill, a `tertiary` border and a `tertiary` label, and its figure in the body's ink. The label
     * sits in the border's notch, so it lands on the fill and on the page (or a card) behind it;
     * the figure lands on the fill. Every one of those is text, held to 4.5:1 in both schemes.
     */
    @Test
    fun `a box changed by the review is legible in both schemes`() {
        listOf(MetaSelfLightColours to "light", MetaSelfDarkColours to "dark").forEach { (scheme, name) ->
            assertLegible(
                mapOf("the teal label" to scheme.tertiary, "the figure" to scheme.onSurface),
                scheme.tertiaryContainer,
                "$name teal fill",
            )
            assertLegible(mapOf("the teal label" to scheme.tertiary), scheme.background, "$name page")
            assertLegible(mapOf("the teal label" to scheme.tertiary), scheme.surfaceVariant, "$name card")
        }
    }
}
