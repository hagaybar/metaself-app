package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/**
 * D117, D118: a weekly plan drawn as its weeks. The plan is four weeks from Monday 24 August 2026, so today,
 * Thursday 3 September ([TEST_EPOCH_DAY]), is in week 2; every session, plan and figure is invented, and round.
 */
class PlanTimelineTest {

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
            PlanWeek("Hold it", listOf(walk30, walk45, run20)),
            PlanWeek("Take stock", listOf(walk30, walk45)),
        ),
        "Invented reason.",
    )
    private val kept = Programme(1, 0, ProgrammeAsk(4, 3), null, plan, "a-model", start, ProgrammeStatus.RUNNING)

    @Test
    fun `a running plan has its past weeks, this week and the weeks ahead`() {
        val timeline = timeline()

        assertThat(timeline.standing).isEqualTo(PlanStanding.InWeek(2))
        assertThat(timeline.weeks.map { it.place::class }).containsExactly(
            TimelinePlace.Past::class, TimelinePlace.ThisWeek::class, TimelinePlace.Ahead::class, TimelinePlace.Ahead::class,
        ).inOrder()
        assertThat(timeline.weeks.map { it.number to it.focus }).containsExactly(
            1 to "Settle in", 2 to "Add a run", 3 to "Hold it", 4 to "Take stock",
        ).inOrder()
        assertThat(timeline.weeks[2].place).isEqualTo(TimelinePlace.Ahead(listOf(walk30, walk45, run20)))
        assertThat(timeline.title).isEqualTo("Invented plan")
        assertThat(timeline.why).isEqualTo("Invented reason.")
        assertThat(timeline.lastDay).isEqualTo(start + 27)
        assertThat(timeline.over).isFalse()
    }

    @Test
    fun `before its first week every week is ahead`() {
        val later = kept.copy(startEpochDay = monday + 7)

        val timeline = PlanTimeline.of(later, monday + 7, TEST_EPOCH_DAY, progress(later, emptyList()), emptyMap())

        assertThat(timeline.standing).isEqualTo(PlanStanding.Before(monday + 7))
        assertThat(timeline.weeks.all { it.place is TimelinePlace.Ahead }).isTrue()
    }

    @Test
    fun `after its last day every week is past`() {
        val timeline = timeline(today = start + 28)

        assertThat(timeline.standing).isEqualTo(PlanStanding.Ended(start + 27))
        assertThat(timeline.weeks.all { it.place is TimelinePlace.Past }).isTrue()
        assertThat(timeline.over).isTrue()
    }

    @Test
    fun `stopped mid-way, the weeks it reached are past and the rest are drawn ahead`() {
        val stopped = kept.copy(status = ProgrammeStatus.STOPPED, stoppedEpochDay = monday + 1)

        val timeline = PlanTimeline.of(stopped, start, TEST_EPOCH_DAY, progress(stopped, emptyList()), emptyMap())

        assertThat(timeline.standing).isEqualTo(PlanStanding.Stopped(monday + 1))
        assertThat(timeline.weeks.map { it.place::class }).containsExactly(
            TimelinePlace.Past::class, TimelinePlace.Past::class, TimelinePlace.Ahead::class, TimelinePlace.Ahead::class,
        ).inOrder()
        assertThat(timeline.over).isTrue()
        // A version replaced or adjusted also has a stop day, but a later version runs: it is not "stopped".
        val replaced = kept.copy(status = ProgrammeStatus.REPLACED, stoppedEpochDay = monday + 1)
        assertThat(PlanTimeline.of(replaced, start, TEST_EPOCH_DAY, null, emptyMap()).standing).isEqualTo(PlanStanding.InWeek(2))
    }

    @Test
    fun `a past week counts its planned sessions done and, from its ledger, the sessions of its own`() {
        val sessions = listOf(
            session(1, start), session(2, start + 1), session(3, start + 2, WorkoutKind.SWIM), session(4, start + 3),
        )
        val ledger = WeekLedgers.of(start, TEST_EPOCH_DAY, sessions, emptyList(), emptyMap(), null, listOf(CountedPlan(kept, COUNTING)))

        val withLedger = PlanTimeline.of(kept, start, TEST_EPOCH_DAY, progress(kept, sessions), mapOf(start to ledger))
        val without = PlanTimeline.of(kept, start, TEST_EPOCH_DAY, progress(kept, sessions), emptyMap())

        assertThat(withLedger.weeks[0].place).isEqualTo(TimelinePlace.Past(done = 2, planned = 2, own = 2))
        assertThat(without.weeks[0].place).isEqualTo(TimelinePlace.Past(done = 2, planned = 2, own = 0))
        assertThat(timeline().weeks[0].place).isEqualTo(TimelinePlace.Past(done = 0, planned = 2, own = 0))
    }

    @Test
    fun `this week's tiles are done, done short or open, with the day of the session that ticked each`() {
        val short = session(5, monday, minutes = 20)
        val full = session(6, monday + 2, minutes = 45)
        val counting = PlanCounting(1, 0, mapOf(5L to true))
        val progress = PlanProgress.of(plan, start, listOf(short, full), TEST_EPOCH_DAY, counting)

        val timeline = PlanTimeline.of(kept, start, TEST_EPOCH_DAY, progress, emptyMap())

        assertThat(timeline.weeks[1].place).isEqualTo(
            TimelinePlace.ThisWeek(
                listOf(
                    PlanTile(walk30, TileState.DONE_SHORT, day = monday, minutes = 20),
                    PlanTile(walk45, TileState.DONE, day = monday + 2, minutes = 45),
                    PlanTile(run20, TileState.OPEN),
                ),
            ),
        )
    }

    /** D118: an answer not yet kept, which would start this Monday — nothing is counted for it. */
    @Test
    fun `an answer never kept has every session of this week open`() {
        val offered = Programme(2, 0, ProgrammeAsk(4, 3), null, plan, "a-model")

        val timeline = PlanTimeline.of(offered, monday, TEST_EPOCH_DAY, null, emptyMap())

        assertThat(timeline.standing).isEqualTo(PlanStanding.InWeek(1))
        assertThat(timeline.weeks[0].place).isEqualTo(TimelinePlace.ThisWeek(listOf(PlanTile(walk30, TileState.OPEN), PlanTile(walk30, TileState.OPEN))))
        assertThat(timeline.weeks.drop(1).all { it.place is TimelinePlace.Ahead }).isTrue()
    }

    /** D122: an open tile of this week with steps reads "Steps ready"; a week ahead is unchanged. */
    @Test
    fun `an open tile with steps is ready, and only that one`() {
        val marked = timeline().withSteps(setOf(PlanPlace(1, 1), PlanPlace(2, 0)))

        val tiles = (marked.weeks[1].place as TimelinePlace.ThisWeek).tiles
        assertThat(tiles.map { it.stepsReady }).containsExactly(false, true, false).inOrder()
        assertThat(marked.weeks[2]).isEqualTo(timeline().weeks[2])
        assertThat(marked.weeks[0]).isEqualTo(timeline().weeks[0])
    }

    private fun timeline(today: Long = TEST_EPOCH_DAY) = PlanTimeline.of(kept, start, today, progress(kept, emptyList(), today), emptyMap())

    private fun progress(programme: Programme, sessions: List<Workout>, today: Long = TEST_EPOCH_DAY) =
        PlanProgress.of(programme.plan, programme.startEpochDay!!, sessions, Programmes.countedUntil(programme, today), COUNTING)

    private fun session(id: Long, day: Long, kind: WorkoutKind = WorkoutKind.WALK, minutes: Int = 30) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 7 * HOUR, durationMinutes = minutes,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        val COUNTING = PlanCounting(1, 0, emptyMap())
    }
}
