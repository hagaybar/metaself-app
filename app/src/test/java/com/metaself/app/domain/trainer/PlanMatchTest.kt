package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/** D86's "older than seven days stops being offered"; D87's "kept within seven days before the session started". */
class PlanMatchTest {

    private val start = TEST_EPOCH_DAY * DAY + 7 * HOUR

    @Test
    fun `a kept plan is offered for seven days, then not`() {
        val plan = aPlan(createdAt = start)

        assertThat(PlanMatch.offered(plan, start + HOUR)).isEqualTo(plan)
        assertThat(PlanMatch.offered(plan, start + 7 * DAY)).isEqualTo(plan)
        assertThat(PlanMatch.offered(plan, start + 7 * DAY + 1)).isNull()
        assertThat(PlanMatch.offered(plan.copy(kept = false), start + HOUR)).isNull()
        assertThat(PlanMatch.offered(null, start)).isNull()
    }

    @Test
    fun `a session matches the kept plan made up to seven days before it started`() {
        val session = aSession(startedAt = start)

        assertThat(PlanMatch.forSession(aPlan(createdAt = start - HOUR), session)).isNotNull()
        assertThat(PlanMatch.forSession(aPlan(createdAt = start - 7 * DAY), session)).isNotNull()
        assertThat(PlanMatch.forSession(aPlan(createdAt = start - 7 * DAY - 1), session)).isNull()
        assertThat(PlanMatch.forSession(aPlan(createdAt = start + 1), session)).isNull()
        assertThat(PlanMatch.forSession(aPlan(createdAt = start - HOUR).copy(kept = false), session)).isNull()
    }

    private fun aPlan(createdAt: Long) = TrainerPlan(
        id = 1, createdAtMillis = createdAt,
        answers = PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE),
        plan = SessionPlan("Steady walk", listOf(PlanStep(0, 45, "Walk", "")), "Invented."),
        model = "a-model", kept = true,
    )

    private fun aSession(startedAt: Long) = Workout(
        id = 7, epochDay = TEST_EPOCH_DAY, startedAtMillis = startedAt, durationMinutes = 40,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
    }
}
