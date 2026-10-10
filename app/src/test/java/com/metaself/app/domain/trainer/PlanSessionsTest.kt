package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/**
 * D123: the Session view — this week's open sessions, then next week's, this week's done ones folded above, and
 * the Next session (D122). A four-week plan from Monday 24 August 2026; today, Thursday 3 September
 * ([TEST_EPOCH_DAY]), is in week 2. Every session, plan and figure is invented, and round.
 */
class PlanSessionsTest {

    private val monday = TEST_EPOCH_DAY - 3
    private val start = monday - 7
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
    private val walk45 = PlannedSession(WorkoutKind.WALK, 45, PlannedEffort.STEADY, "Invented line")
    private val run20 = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Invented line")
    private val plan = WeeksPlan(
        "Invented plan",
        listOf(
            PlanWeek("Settle in", listOf(walk30, walk30)),
            PlanWeek("Add a run", listOf(walk30, walk45, run20)),
            PlanWeek("Hold it", listOf(walk45, run20)),
            PlanWeek("Take stock", listOf(walk30)),
        ),
        "Invented reason.",
    )
    private val kept = Programme(1, 0, ProgrammeAsk(4, 3), null, plan, "a-model", start, ProgrammeStatus.RUNNING)
    private val steps = TrainerPlan(
        5, 0, PlanAnswers(PlanActivity.OUTDOOR_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.EASY),
        SessionPlan("Invented steps", listOf(PlanStep(0, 45, "Walk", "")), "Invented."), "a-model", kept = true, slot = SessionSlot(1, 1, 1),
    )
    private val next = steps.copy(id = 6, slot = null)

    @Test
    fun `this week's open sessions, then next week's, with this week's done ones apart`() {
        val sessions = PlanSessions.of(shown(listOf(walk(1, monday))), TEST_EPOCH_DAY, null)

        assertThat(sessions.done).containsExactly(SessionCard(PlanPlace(1, 0), walk30, null, TileState.DONE, monday, 30))
        assertThat(sessions.thisWeek).containsExactly(
            SessionCard(PlanPlace(1, 1), walk45, steps), SessionCard(PlanPlace(1, 2), run20, null),
        ).inOrder()
        assertThat(sessions.nextWeek).containsExactly(SessionCard(PlanPlace(2, 0), walk45, null), SessionCard(PlanPlace(2, 1), run20, null)).inOrder()
        assertThat(sessions.next).isNull()
        assertThat(sessions.empty).isFalse()
    }

    @Test
    fun `before its first week, a plan starting next Monday has week 1 as next week`() {
        val later = kept.copy(startEpochDay = monday + 7)
        val timeline = PlanTimeline.of(later, monday + 7, TEST_EPOCH_DAY, null, emptyMap())
        val card = PlanCard.of(later, emptyList(), TEST_EPOCH_DAY, COUNTING) as PlanCard.Running

        val sessions = PlanSessions.of(PlanShown(timeline, card, null), TEST_EPOCH_DAY, next)

        assertThat(sessions.thisWeek).isEmpty()
        assertThat(sessions.nextWeek.map { it.place }).containsExactly(PlanPlace(0, 0), PlanPlace(0, 1)).inOrder()
        assertThat(sessions.next).isEqualTo(next)
    }

    @Test
    fun `a plan ended, stopped or only offered has no cards, only the next session`() {
        val ended = PlanSessions.of(PlanShown(PlanTimeline.of(kept, start, start + 40, null, emptyMap()), null, null), start + 40, next)
        val none = PlanSessions.of(null, TEST_EPOCH_DAY, null)

        assertThat(ended).isEqualTo(PlanSessions(emptyList(), emptyList(), emptyList(), next))
        assertThat(none.empty).isTrue()
    }

    private fun shown(sessions: List<Workout>): PlanShown {
        val progress = PlanProgress.of(plan, start, sessions, TEST_EPOCH_DAY, COUNTING)
        val card = PlanCard.of(kept, sessions, TEST_EPOCH_DAY, COUNTING) as PlanCard.Running
        val stepsByPlace = mapOf(PlanPlace(1, 1) to steps)
        return PlanShown(PlanTimeline.of(kept, start, TEST_EPOCH_DAY, progress, emptyMap()).withSteps(stepsByPlace.keys), card, null, stepsByPlace)
    }

    private fun walk(id: Long, day: Long) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 7 * HOUR, durationMinutes = 30,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        val COUNTING = PlanCounting(1, 0, emptyMap())
    }
}
