package com.metaself.app.ui.screen.day

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.Remaining
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.DailyTargetCalculator
import com.metaself.app.ui.theme.LocalMoves
import com.metaself.app.ui.theme.MetaSelfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Today moves, the past is still (public issue #16): the day's figure rolls to a new value on
 * today, and simply changes on a past day or when the system has removed animations.
 *
 * **What a render here can see.** The clock is held, and the frame just after the change is
 * inspected: mid-roll, the old figure is on its way out while the new one comes in, so both are in
 * the tree; a figure that swaps leaves only the new one. That is all this can prove. How the roll
 * looks, how long it feels, and the hairline filling under it — a width Robolectric cannot measure
 * here (see CLAUDE.md) — are checks for the phone.
 *
 * JUnit 4 because Compose's rule demands it. `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class DayMotionRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val target = DailyTargetCalculator.of(aProfile(), TEST_YEAR)

    private var meals by mutableStateOf(listOf(aMeal(items = listOf(anItem(kcal = 90)))))

    /** 2,090 − 90 before, 2,090 − 90 − 600 after. */
    private val before = "2,000"
    private val after = "1,400"

    private fun day(isToday: Boolean, systemAllows: Boolean) {
        compose.setContent {
            MetaSelfTheme {
                CompositionLocalProvider(LocalMoves provides systemAllows) {
                    DayScreenContent(
                        state = DayUiState.Ready(
                            epochDay = 20_699L,
                            isToday = isToday,
                            target = target,
                            meals = meals,
                            remaining = Remaining.of(target, DayTotals.of(meals)),
                        ),
                        onAdd = {},
                        onDescribe = {},
                        onScan = {},
                        onOpenSettings = {},
                        onDismissTargetChange = {},
                        onDismissJustLogged = {},
                        onDismissEncouragement = {},
                        onDismissRefusal = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        assertThat(drawn(before)).isEqualTo(1)
    }

    /** Something eaten, and the first frames after it — the clock held so the roll cannot finish. */
    private fun eatAndLookMidway() {
        compose.mainClock.autoAdvance = false
        meals = meals + listOf<Meal>(aMeal(id = 2, items = listOf(anItem(id = 2, kcal = 600))))
        // With the clock held nothing else tells the composition the state changed.
        Snapshot.sendApplyNotifications()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun drawn(figure: String): Int =
        compose.onAllNodesWithText(figure, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `on today the figure rolls from the old value to the new`() {
        day(isToday = true, systemAllows = true)

        eatAndLookMidway()

        assertThat(drawn(before)).isEqualTo(1)
        assertThat(drawn(after)).isEqualTo(1)

        // And it arrives: once the roll is over, only the new figure is left.
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(drawn(before)).isEqualTo(0)
        assertThat(drawn(after)).isEqualTo(1)
    }

    @Test
    fun `on a past day the figure changes without moving`() {
        day(isToday = false, systemAllows = true)

        eatAndLookMidway()

        assertThat(drawn(before)).isEqualTo(0)
        assertThat(drawn(after)).isEqualTo(1)
    }

    @Test
    fun `with animations removed, even today's figure changes without moving`() {
        day(isToday = true, systemAllows = false)

        eatAndLookMidway()

        assertThat(drawn(before)).isEqualTo(0)
        assertThat(drawn(after)).isEqualTo(1)
    }
}
