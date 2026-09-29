package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/** What the Trainer screen shows (D85, design questions 9 and 10). Every session and plan is invented. */
class TrainerHomeTest {

    @Test
    fun `the waiting card is the newest unreviewed session of the last three days`() {
        val today = walk(1, TEST_EPOCH_DAY, hour = 7)
        val yesterday = walk(2, TEST_EPOCH_DAY - 1, hour = 18)

        assertThat(home(recent = listOf(yesterday, today)).waiting).isEqualTo(today)
        assertThat(home(recent = listOf(yesterday, today), reviews = listOf(reviewOf(1))).waiting).isEqualTo(yesterday)
        assertThat(home(recent = listOf(walk(3, TEST_EPOCH_DAY - 2, hour = 7))).waiting).isNotNull()
        assertThat(home(recent = listOf(walk(3, TEST_EPOCH_DAY - 3, hour = 7))).waiting).isNull()
        assertThat(home(recent = listOf(today.copy(hidden = true))).waiting).isNull()
        assertThat(home(recent = listOf(today.copy(counted = false))).waiting).isNull()
    }

    @Test
    fun `the kept plan is offered for seven days only`() {
        val kept = aPlan(createdAt = NOW - 6 * DAY)

        assertThat(home(kept = kept).keptPlan).isEqualTo(kept)
        assertThat(home(kept = aPlan(createdAt = NOW - 8 * DAY)).keptPlan).isNull()
        assertThat(home(kept = null).keptPlan).isNull()
    }

    @Test
    fun `earlier sessions are the reviewed ones, newest first`() {
        val older = walk(1, TEST_EPOCH_DAY - 10, hour = 7)
        val newer = walk(2, TEST_EPOCH_DAY - 2, hour = 7)
        val hidden = walk(3, TEST_EPOCH_DAY - 1, hour = 7).copy(hidden = true)

        val earlier = home(reviewed = listOf(older, newer, hidden), reviews = listOf(reviewOf(1), reviewOf(2), reviewOf(3))).earlier

        assertThat(earlier.map { it.workout.id }).containsExactly(2L, 1L).inOrder()
        assertThat(earlier.first().review.workoutId).isEqualTo(2L)
    }

    /** A review whose session left the record sits under a negative workout id (D88) and is shown nowhere. */
    @Test
    fun `a review without its session is shown nowhere`() {
        val shown = home(reviewed = listOf(walk(1, TEST_EPOCH_DAY - 5, hour = 7)), reviews = listOf(reviewOf(1), reviewOf(-1)))

        assertThat(shown.earlier.map { it.review.workoutId }).containsExactly(1L)
    }

    /** D92: a session whose other witness has a review is not waiting for words. */
    @Test
    fun `a combined session reviewed on its other witness is not waiting`() {
        val first = walk(1, TEST_EPOCH_DAY, hour = 7)
        val combined = com.metaself.app.domain.movement.SessionWitnesses.combine(listOf(first, first.copy(id = 2, durationMinutes = 30)), emptySet())

        assertThat(home(recent = combined).waiting?.id).isEqualTo(1L)
        assertThat(home(recent = combined, reviews = listOf(reviewOf(2))).waiting).isNull()
    }

    // --- D95, D97. The plan starts Monday 31 August 2026 (TEST_EPOCH_DAY - 3). --------------------

    private val start = TEST_EPOCH_DAY - 3
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
    private val walk40 = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Steady walk")
    private val running = Programme(
        id = 1, createdAtMillis = 0, ask = ProgrammeAsk(2, 2), evaluation = null,
        plan = WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(walk30, walk40)) }, "Invented."),
        model = "a-model", startEpochDay = start, status = ProgrammeStatus.RUNNING,
    )

    @Test
    fun `a running plan shows its week, its ticks and the next planned session`() {
        val card = PlanCard.of(running, listOf(planSession(1, start + 1)), today = TEST_EPOCH_DAY) as PlanCard.Running

        assertThat(card.weekIndex).isEqualTo(0)
        assertThat(card.progress.weeks.first().done).isEqualTo(1)
        assertThat(card.next).isEqualTo(PlannedTick(1, walk40))
    }

    @Test
    fun `before week 1 there is no next session, and after the last day the plan has ended for fourteen days`() {
        val later = running.copy(startEpochDay = start + 7)
        val before = PlanCard.of(later, emptyList(), today = TEST_EPOCH_DAY) as PlanCard.Running
        assertThat(before.weekIndex).isEqualTo(-1)
        assertThat(before.next).isNull()

        val last = start + 13
        assertThat(PlanCard.of(running, emptyList(), today = last + 1)).isInstanceOf(PlanCard.Ended::class.java)
        assertThat(PlanCard.of(running, emptyList(), today = last + 14)).isInstanceOf(PlanCard.Ended::class.java)
        assertThat(PlanCard.of(running, emptyList(), today = last + 15)).isEqualTo(PlanCard.None)
        assertThat(PlanCard.of(null, emptyList(), today = TEST_EPOCH_DAY)).isEqualTo(PlanCard.None)
    }

    @Test
    fun `the home carries the card and the next session`() {
        val home = TrainerHome.of(
            today = TEST_EPOCH_DAY, nowMillis = 0, recent = emptyList(), reviewed = emptyList(), reviews = emptyList(),
            kept = null, running = running, planWorkouts = emptyList(),
        )

        assertThat(home.plan).isInstanceOf(PlanCard.Running::class.java)
        assertThat(home.next).isEqualTo(PlannedTick(1, walk30))
    }

    /** A synced thirty-minute walk at 07:00 on [day]. Invented. */
    private fun planSession(id: Long, day: Long) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 7 * HOUR, durationMinutes = 30,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private fun home(
        recent: List<Workout> = emptyList(),
        reviewed: List<Workout> = emptyList(),
        reviews: List<TrainerReview> = emptyList(),
        kept: TrainerPlan? = null,
    ) = TrainerHome.of(TEST_EPOCH_DAY, NOW, recent, reviewed, reviews, kept)

    private fun reviewOf(workoutId: Long) = TrainerReview(workoutId = workoutId, planId = null, felt = Felt.RIGHT, words = "Invented.")

    /** A synced forty-minute walk at [hour]:00 on [day]. Invented. */
    private fun walk(id: Long, day: Long, hour: Int) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + hour * HOUR, durationMinutes = 40,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private fun aPlan(createdAt: Long) = TrainerPlan(
        id = 1, createdAtMillis = createdAt,
        answers = PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE),
        plan = SessionPlan("Steady walk", listOf(PlanStep(0, 45, "Walk", "")), "Invented."),
        model = "a-model", kept = true,
    )

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        const val NOW = TEST_EPOCH_DAY * DAY + 15 * HOUR
    }
}
