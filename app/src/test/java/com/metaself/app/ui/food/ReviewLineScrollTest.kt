package com.metaself.app.ui.food

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.ui.MetaSelfScreen
import com.metaself.app.ui.theme.MetaSelfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * D54 §9.4: when a review's answer arrives, its line is brought into view, so an answer is never
 * drawn where he cannot see it.
 *
 * Only the scroll position is asserted, read from the scroller's own semantics, never a size — see
 * CLAUDE.md on what a Robolectric render can and cannot measure here.
 *
 * JUnit 4 because Compose's rule demands it. `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class ReviewLineScrollTest {

    @get:Rule
    val compose = createComposeRule()

    private var reviewing by mutableStateOf(FormReview().asked(FoodForm(name = "Oat biscuit")))

    private val scroller = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun position(): Float = compose.onNode(scroller).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    /** The review sits below a screenful of other things, as a long editor's can. */
    private fun start() {
        compose.setContent {
            MetaSelfTheme {
                MetaSelfScreen(title = "Editor") {
                    repeat(40) { row ->
                        Text("Row $row")
                        Spacer(Modifier.height(40.dp))
                    }
                    ReviewTheFigures(
                        reviewing = reviewing,
                        offered = true,
                        unitName = "",
                        actions = ReviewActions.NONE,
                    )
                }
            }
        }
        compose.waitForIdle()
        assertThat(position()).isEqualTo(0f)
    }

    @Test
    fun `an answer that arrives is brought into view`() {
        start()

        reviewing = reviewing.answered(FoodReview(null, null, "Consistent.", emptyList()), null, FoodForm(name = "Oat biscuit")).second
        compose.waitForIdle()

        assertThat(position()).isGreaterThan(0f)
    }

    @Test
    fun `a failure that arrives is brought into view`() {
        start()

        reviewing = reviewing.failed(EstimateResult.Unreachable())
        compose.waitForIdle()

        assertThat(position()).isGreaterThan(0f)
    }
}
