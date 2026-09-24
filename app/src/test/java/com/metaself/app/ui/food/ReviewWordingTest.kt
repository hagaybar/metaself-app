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

    /** Figures are written as the editor's boxes write them: up to two decimals (D54 §8.5). */
    @Test
    fun `a changed group is one line of what would change, old to new`() {
        val line = ReviewWording.changes(
            "100 g",
            Suggestion(
                nutrients = Nutrients(100.0, 8.0, 4.0, 5.0),
                confidence = Confidence.MEDIUM,
                filled = false,
                changes = listOf(
                    FigureChange(Figure.PROTEIN, 6.25, 8.0, "Why."),
                    FigureChange(Figure.CARBS, 4.5, 4.0, "Why."),
                    FigureChange(Figure.FAT, 5.75, 5.0, "Why."),
                ),
                reason = null,
            ),
        )

        assertThat(line).isEqualTo("Per 100 g: Protein 6.25 → 8 · Carbs 4.5 → 4 · Fat 5.75 → 5")
    }

    @Test
    fun `calories are named, and a filled group is its four figures`() {
        assertThat(
            ReviewWording.changes(
                "biscuit",
                Suggestion(Nutrients(95.0, 1.0, 12.0, 1.0), Confidence.LOW, false,
                    listOf(FigureChange(Figure.KCAL, 90.0, 95.0, "Why.")), null),
            ),
        ).isEqualTo("Per biscuit: Calories 90 → 95")
        assertThat(
            ReviewWording.changes(
                "bowl",
                Suggestion(Nutrients(60.0, 4.0, 9.0, 1.0), Confidence.LOW, true, emptyList(), "Why."),
            ),
        ).isEqualTo("Per bowl: Filled: 60 kcal · P 4 · C 9 · F 1")
    }

    /** One small line per group, each reason once, in the order the figures came. */
    @Test
    fun `a group's reasons are one line, each said once`() {
        val changed = Suggestion(
            nutrients = Nutrients(100.0, 8.0, 4.0, 5.0),
            confidence = Confidence.MEDIUM,
            filled = false,
            changes = listOf(
                FigureChange(Figure.PROTEIN, 7.0, 10.0, "The label was misread."),
                FigureChange(Figure.CARBS, 6.0, 5.0, "The label was misread."),
                FigureChange(Figure.FAT, 8.0, 7.0, "Rounded."),
            ),
            reason = null,
        )
        val filled = Suggestion(Nutrients(60.0, 4.0, 9.0, 1.0), Confidence.LOW, true, emptyList(), "A thick soup.")

        assertThat(ReviewWording.reasons(changed)).isEqualTo("The label was misread. Rounded.")
        assertThat(ReviewWording.reasons(filled)).isEqualTo("A thick soup.")
    }

    @Test
    fun `the model's answer is shown pretty-printed when it is JSON, and as it came otherwise`() {
        assertThat(ReviewWording.modelAnswer("""{"per_100g":null,"note":"Consistent."}"""))
            .isEqualTo("{\n    \"per_100g\": null,\n    \"note\": \"Consistent.\"\n}")
        assertThat(ReviewWording.modelAnswer("not json")).isEqualTo("not json")
    }
}
