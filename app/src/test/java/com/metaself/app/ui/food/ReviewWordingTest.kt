package com.metaself.app.ui.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.food.Nutrients
import org.junit.jupiter.api.Test

/** The lines a review's suggestion is drawn as, under its group (D54 §4). Figures invented. */
class ReviewWordingTest {

    @Test
    fun `a changed figure reads from, to, and why`() {
        val line = ReviewWording.change(
            FigureChange(Figure.FAT, 1.0, 4.0, "18 g of a food with 22 g of fat per 100 g holds about 4 g."),
        )

        assertThat(line).isEqualTo("Fat 1 → 4 g — 18 g of a food with 22 g of fat per 100 g holds about 4 g.")
    }

    @Test
    fun `calories are in kcal and a decimal keeps one place`() {
        assertThat(ReviewWording.change(FigureChange(Figure.KCAL, 120.0, 370.0, "Why.")))
            .isEqualTo("Calories 120 → 370 kcal — Why.")
        assertThat(ReviewWording.change(FigureChange(Figure.CARBS, 12.0, 12.5, "Why.")))
            .isEqualTo("Carbs 12 → 12.5 g — Why.")
        assertThat(ReviewWording.change(FigureChange(Figure.PROTEIN, 0.0, 2.0, "Why.")))
            .isEqualTo("Protein 0 → 2 g — Why.")
    }

    @Test
    fun `a filled group is one line of its four figures and its reason`() {
        val lines = ReviewWording.lines(
            Suggestion(
                nutrients = Nutrients(60.0, 4.0, 9.0, 1.0),
                confidence = Confidence.LOW,
                filled = true,
                changes = emptyList(),
                reason = "A typical lentil soup.",
            ),
        )

        assertThat(lines).containsExactly("Suggested: 60 kcal · P 4 · C 9 · F 1 — A typical lentil soup.")
    }

    @Test
    fun `a changed group is one line per change`() {
        val lines = ReviewWording.lines(
            Suggestion(
                nutrients = Nutrients(370.0, 30.0, 40.0, 10.0),
                confidence = Confidence.MEDIUM,
                filled = false,
                changes = listOf(
                    FigureChange(Figure.KCAL, 120.0, 370.0, "The macros alone give about 370."),
                    FigureChange(Figure.FAT, 9.0, 10.0, "Rounded."),
                ),
                reason = null,
            ),
        )

        assertThat(lines).containsExactly(
            "Calories 120 → 370 kcal — The macros alone give about 370.",
            "Fat 9 → 10 g — Rounded.",
        ).inOrder()
    }

    @Test
    fun `the model's answer is shown pretty-printed when it is JSON, and as it came otherwise`() {
        assertThat(ReviewWording.modelAnswer("""{"per_100g":null,"note":"Consistent."}"""))
            .isEqualTo("{\n    \"per_100g\": null,\n    \"note\": \"Consistent.\"\n}")
        assertThat(ReviewWording.modelAnswer("not json")).isEqualTo("not json")
    }
}
