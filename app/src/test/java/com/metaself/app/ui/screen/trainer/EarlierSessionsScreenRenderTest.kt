package com.metaself.app.ui.screen.trainer

import androidx.compose.ui.semantics.Role
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.ReviewedSession
import com.metaself.app.domain.trainer.TrainerHome
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4.
 *
 * D114: Earlier sessions, the list that was on the Trainer page, on a page of its own. TEST_EPOCH_DAY is
 * Thursday 3 September 2026; every session and word is invented.
 */
@RunWith(RobolectricTestRunner::class)
class EarlierSessionsScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `earlier sessions show their name, date and line, and open`() {
        var reviewed: Long? = null
        val earlier = ReviewedSession(
            walk(2, TEST_EPOCH_DAY - 5),
            TrainerReview(workoutId = 2, planId = 1, felt = Felt.RIGHT, words = "Invented.", feedback = FEEDBACK),
        )
        val texts = draw(listOf(earlier), onReview = { reviewed = it })

        assertThat(texts).containsAtLeast("Earlier sessions", "Back")
        assertThat(texts).contains("Walking · Sat 29 Aug")
        assertThat(texts).contains("Felt right · feedback read · as planned")
        assertThat(render.roleOf("Walking · Sat 29 Aug")).isEqualTo(Role.Button)
        render.click("Walking · Sat 29 Aug")
        assertThat(reviewed).isEqualTo(2L)
    }

    @Test
    fun `with none, it says so`() {
        assertThat(draw(emptyList())).contains("No reviewed sessions yet.")
    }

    private fun draw(earlier: List<ReviewedSession>, onReview: (Long) -> Unit = {}): List<String> = render.texts {
        EarlierSessionsScreen(
            state = TrainerViewModel.State(home = TrainerHome(null, null, earlier), today = TEST_EPOCH_DAY),
            onBack = {},
            onReview = onReview,
        )
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L

        fun walk(id: Long, day: Long) = Workout(
            id = id, epochDay = day, startedAtMillis = day * DAY + 7 * HOUR, durationMinutes = 40,
            kind = WorkoutKind.WALK, title = null, distanceM = 3_000, energyKcal = null,
            energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
        )

        val FEEDBACK = Feedback("Invented headline.", "Invented.", "Invented.", "Invented.", "Invented.", PlanFollowed.YES)
    }
}
