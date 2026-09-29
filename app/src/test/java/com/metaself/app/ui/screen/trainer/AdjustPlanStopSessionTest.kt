package com.metaself.app.ui.screen.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.ComposeSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * D97: Stop this plan asks first, tapped through. The question is an AlertDialog, drawn in a window of
 * its own that ComposeRender does not read; [ComposeSession] reads every window. The page's state is
 * held here the way AdjustPlanViewModel changes it. TEST_EPOCH_DAY is Thursday 3 September 2026; every
 * word is invented.
 *
 * JUnit 4, because Compose's rule demands it: `org.junit.Test`, never `org.junit.jupiter.api.Test`.
 */
@RunWith(RobolectricTestRunner::class)
class AdjustPlanStopSessionTest {

    @get:Rule
    val compose = createComposeRule()

    private val session by lazy { ComposeSession(compose) }

    @Test
    fun `stopping asks first, Keep going leaves it, and Stop it stops`() {
        var stopped = 0
        session.start {
            var state by remember { mutableStateOf(AdjustPlanViewModel.State(loading = false, running = RUNNING, today = TEST_EPOCH_DAY)) }
            AdjustPlanScreen(
                state = state, onBack = {}, onWords = {}, onAdjust = {}, onKeepNew = {}, onKeepOld = {},
                onAskStop = { state = state.copy(confirmStop = true) },
                onCancelStop = { state = state.copy(confirmStop = false) },
                onConfirmStop = {
                    stopped++
                    state = state.copy(confirmStop = false)
                },
                onFinished = {},
            )
        }

        val asked = session.press("Stop this plan")
        assertThat(asked).contains("Stop this plan? It is kept on record.")
        assertThat(asked).containsAtLeast("Stop it", "Keep going")

        val kept = session.press("Keep going")
        assertThat(kept).doesNotContain("Stop this plan? It is kept on record.")
        assertThat(stopped).isEqualTo(0)

        session.press("Stop this plan")
        session.press("Stop it")
        assertThat(stopped).isEqualTo(1)
    }

    private companion object {
        val WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val RUNNING = PlanCard.of(
            Programme(
                1, 0, ProgrammeAsk(2, 2), null, WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK, WALK)) }, "Invented."),
                "a-model", TEST_EPOCH_DAY - 3, ProgrammeStatus.RUNNING,
            ),
            emptyList(),
            TEST_EPOCH_DAY,
        ) as PlanCard.Running
    }
}
