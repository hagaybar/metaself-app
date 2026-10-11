package com.metaself.app.ui.screen.movement

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.ui.theme.MetaSelfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * D135: a sideways swipe on the page does what the arrows do, and only when the arrow is drawn; a vertical one does
 * neither. JUnit 4 because Compose's rule demands it. An earlier week, all seven days of it, invented.
 */
@RunWith(RobolectricTestRunner::class)
class MovementSwipeTest {

    @get:Rule
    val compose = createComposeRule()

    private var earlier = 0
    private var later = 0

    private val pastWeek = MovementWeek.of(
        today = TEST_EPOCH_DAY,
        days = (20_689L..20_695L).map { HealthDay(epochDay = it, steps = 5_000, activeKcal = 200) },
        workouts = emptyList(),
        mealsByDay = emptyMap(),
        monday = 20_689,
    )

    private fun start(canGoEarlier: Boolean, canGoLater: Boolean) {
        compose.setContent {
            MetaSelfTheme {
                MovementScreen(
                    state = MovementUiState(week = pastWeek, canGoEarlier = canGoEarlier, canGoLater = canGoLater),
                    onToggleDay = {},
                    onBack = {},
                    onEarlierWeek = { earlier++ },
                    onLaterWeek = { later++ },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a swipe right goes back a span and a swipe left forward`() {
        start(canGoEarlier = true, canGoLater = true)

        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertThat(earlier).isEqualTo(1)
        assertThat(later).isEqualTo(0)

        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(later).isEqualTo(1)
    }

    @Test
    fun `a swipe does nothing where no arrow is drawn`() {
        start(canGoEarlier = false, canGoLater = false)

        compose.onRoot().performTouchInput { swipeRight() }
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertThat(earlier).isEqualTo(0)
        assertThat(later).isEqualTo(0)
    }

    @Test
    fun `a vertical swipe moves no span`() {
        start(canGoEarlier = true, canGoLater = true)

        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitForIdle()

        assertThat(earlier).isEqualTo(0)
        assertThat(later).isEqualTo(0)
    }
}
