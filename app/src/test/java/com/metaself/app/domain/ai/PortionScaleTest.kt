package com.metaself.app.domain.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.portion.PortionControl
import org.junit.jupiter.api.Test

class PortionScaleTest {

    private val risotto = aProposedItem(
        name = "Risotto",
        portionAmount = 280.0,
        portionUnit = "g",
        kcal = 405,
        proteinG = 9,
        carbsG = 57,
        fatG = 16,
    )

    @Test
    fun `as described changes nothing at all`() {
        assertThat(PortionScale.AS_DESCRIBED.applyTo(risotto)).isEqualTo(risotto)
    }

    @Test
    fun `more is half as much again, and every number follows`() {
        val bigger = PortionScale.MORE.applyTo(risotto)

        assertThat(bigger.portionAmount).isWithin(0.01).of(420.0)
        assertThat(bigger.kcal).isEqualTo(608)
        assertThat(bigger.proteinG).isEqualTo(14)
        assertThat(bigger.carbsG).isEqualTo(86)
        assertThat(bigger.fatG).isEqualTo(24)
    }

    @Test
    fun `less is three quarters`() {
        val smaller = PortionScale.LESS.applyTo(risotto)

        assertThat(smaller.portionAmount).isWithin(0.01).of(210.0)
        assertThat(smaller.kcal).isEqualTo(304)
    }

    @Test
    fun `the portion sentence is rewritten, or it would contradict the numbers`() {
        // "~280 g" beside 608 kcal is a lie the owner would have no way to catch.
        assertThat(PortionScale.MORE.applyTo(risotto).portion).isEqualTo("420 g")
    }

    @Test
    fun `an exact amount can be given instead of a step`() {
        val exact = PortionScale.exactly(350.0, risotto)

        assertThat(exact.portionAmount).isWithin(0.01).of(350.0)
        assertThat(exact.kcal).isEqualTo(506)
        assertThat(exact.portion).isEqualTo("350 g")
    }

    @Test
    fun `scaling keeps how sure the model was, because scaling is the owner's doing`() {
        val item = aProposedItem(confidence = Confidence.LOW)

        assertThat(PortionScale.MORE.applyTo(item).confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `something measured in grams gets a scale`() {
        assertThat(PortionScale.controlFor(risotto)).isEqualTo(PortionControl.Scale)
    }

    @Test
    fun `something counted gets a count, starting at what the model assumed`() {
        // "Pizza — 1 slice". Nobody eats one and a half slices; they eat one, two or three.
        val pizza = aProposedItem(
            name = "Pizza",
            portion = "1 slice",
            portionAmount = 1.0,
            portionUnit = "slice",
            kcal = 285,
            proteinG = 12,
            carbsG = 36,
            fatG = 10,
        )

        assertThat(PortionScale.controlFor(pizza)).isEqualTo(PortionControl.Count(1))
    }

    @Test
    fun `three slices is three times one slice`() {
        val pizza = aProposedItem(
            name = "Pizza",
            portionAmount = 1.0,
            portionUnit = "slice",
            kcal = 285,
            proteinG = 12,
            carbsG = 36,
            fatG = 10,
        )

        val three = PortionScale.count(3, pizza)

        assertThat(three.portion).isEqualTo("3 slice")
        assertThat(three.kcal).isEqualTo(855)
        assertThat(three.proteinG).isEqualTo(36)
    }

    @Test
    fun `a count set against a model that already assumed two works from what it assumed`() {
        val twoEggs = aProposedItem(
            name = "Egg",
            portionAmount = 2.0,
            portionUnit = "eggs",
            kcal = 140,
            proteinG = 12,
            carbsG = 0,
            fatG = 10,
        )

        assertThat(PortionScale.count(3, twoEggs).kcal).isEqualTo(210)
    }

    @Test
    fun `a count is never less than one, because nought of something is a deletion`() {
        val pizza = aProposedItem(portionAmount = 1.0, portionUnit = "slice", kcal = 285)

        assertThat(PortionScale.count(0, pizza).kcal).isEqualTo(285)
    }

    @Test
    fun `an item the model could not measure gets no control rather than a lying one`() {
        val vague = aProposedItem(portion = "a handful", portionAmount = 0.0, portionUnit = "")

        assertThat(PortionScale.controlFor(vague)).isEqualTo(PortionControl.None)
    }

    @Test
    fun `an item with no numeric portion cannot be scaled, and says so`() {
        // "a handful" has no arithmetic in it. The buttons must be hidden rather than lie.
        val vague = aProposedItem(portion = "a handful", portionAmount = 0.0, portionUnit = "")

        assertThat(PortionScale.canScale(vague)).isFalse()
        assertThat(PortionScale.MORE.applyTo(vague)).isEqualTo(vague)
    }
}
