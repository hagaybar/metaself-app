package com.metaself.app.ui.food

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** The words a review's answer is drawn in (D54 §9.4, §12). Figures invented. */
class ReviewWordingTest {

    @Test
    fun `the line a review ends in carries the note, or ends at a full stop`() {
        assertThat(ReviewWording.outcome("Reviewed: no changes suggested", "Consistent."))
            .isEqualTo("Reviewed: no changes suggested — Consistent.")
        assertThat(ReviewWording.outcome("Reviewed: no changes suggested", " ")).isEqualTo("Reviewed: no changes suggested.")
    }

    @Test
    fun `the page says why it clears a weight held per millilitre`() {
        assertThat(ReviewWording.weightPerMillilitre(" glass "))
            .isEqualTo("A weight per millilitre is not a weight per glass.")
    }

    @Test
    fun `the model's answer is shown pretty-printed when it is JSON, and as it came otherwise`() {
        assertThat(ReviewWording.modelAnswer("""{"per_100g":null,"note":"Consistent."}"""))
            .isEqualTo("{\n    \"per_100g\": null,\n    \"note\": \"Consistent.\"\n}")
        assertThat(ReviewWording.modelAnswer("not json")).isEqualTo("not json")
    }
}
