package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/** D95, D96, D98. The plan starts Monday 31 August 2026; every session and figure is invented. */
class PlanProgressTest {

    private val start = TEST_EPOCH_DAY - 3
    private val easyWalk = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
    private val steadyWalk = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Steady walk")
    private val run = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Short run")
    private val plan = WeeksPlan(
        "Invented title",
        listOf(PlanWeek("first", listOf(easyWalk, steadyWalk, run)), PlanWeek("second", listOf(easyWalk, run))),
        "Invented reason.",
    )

    @Test
    fun `each session ticks the first unticked planned session of its kind, in start order`() {
        val progress = PlanProgress.of(plan, start, listOf(session(2, start + 2, WorkoutKind.WALK), session(1, start, WorkoutKind.WALK)), until = start + 6)

        val week = progress.weeks.first()
        assertThat(week.ticks.map { it.by?.id }).containsExactly(1L, 2L, null).inOrder()
        assertThat(week.done).isEqualTo(2)
        assertThat(week.next).isEqualTo(run)
    }

    @Test
    fun `a session with nothing of its kind left in the plan ticks nothing`() {
        // No WorkoutKind.UNRECOGNISED session here: since no PlannedSession is ever of that kind
        // either (its own init forbids it), such a workout could never match a slot whether or not
        // `of()`'s explicit UNRECOGNISED filter ran — a case here would look like coverage without
        // being any.
        val sessions = listOf(
            session(1, start, WorkoutKind.RUN), session(2, start + 1, WorkoutKind.RUN),
            session(3, start + 2, WorkoutKind.CYCLE),
        )

        val week = PlanProgress.of(plan, start, sessions, until = start + 6).weeks.first()

        assertThat(week.ticks.map { it.by?.id }).containsExactly(null, null, 1L).inOrder()
    }

    @Test
    fun `hidden and uncounted sessions, and days after until, count for nothing`() {
        val sessions = listOf(
            session(1, start, WorkoutKind.WALK).copy(hidden = true),
            session(2, start, WorkoutKind.WALK).copy(counted = false),
            session(3, start + 8, WorkoutKind.WALK),
        )

        val progress = PlanProgress.of(plan, start, sessions, until = start + 7)

        assertThat(progress.done).isEqualTo(0)
        assertThat(progress.planned).isEqualTo(5)
    }

    @Test
    fun `sessions tick their own week only`() {
        val progress = PlanProgress.of(plan, start, listOf(session(1, start + 7, WorkoutKind.WALK)), until = start + 13)

        assertThat(progress.weeks.map { it.done }).containsExactly(0, 1).inOrder()
        assertThat(progress.tickOf(1)).isEqualTo(PlannedTick(2, easyWalk))
        assertThat(progress.tickOf(9)).isNull()
    }

    @Test
    fun `the last evaluation is the newest kept one, with the newest kept version of its plan`() {
        val eval = Evaluation("h", "g", "t", "")
        val first = programme(1, createdAt = 100, evaluation = eval, status = ProgrammeStatus.ADJUSTED)
        val adjusted = programme(2, createdAt = 200, evaluation = null, status = ProgrammeStatus.RUNNING, replacesId = 1)
        val notKept = programme(3, createdAt = 300, evaluation = null, status = ProgrammeStatus.OFFERED, replacesId = 2)
        val offeredEval = programme(4, createdAt = 400, evaluation = eval, status = ProgrammeStatus.OFFERED)

        val (evaluated, latest) = Programmes.lastEvaluated(listOf(first, adjusted, notKept, offeredEval))!!

        assertThat(evaluated.id).isEqualTo(1)
        assertThat(latest.id).isEqualTo(2)
        assertThat(Programmes.evaluationOf(adjusted, listOf(first, adjusted))).isEqualTo(eval)
        assertThat(Programmes.lastEvaluated(listOf(offeredEval))).isNull()
    }

    @Test
    fun `a row that replaces itself does not loop forever`() {
        val eval = Evaluation("h", "g", "t", "")
        val selfReplacing = programme(5, createdAt = 500, evaluation = eval, status = ProgrammeStatus.RUNNING, replacesId = 5)

        val (evaluated, latest) = Programmes.lastEvaluated(listOf(selfReplacing))!!

        assertThat(evaluated.id).isEqualTo(5)
        assertThat(latest.id).isEqualTo(5)
        assertThat(Programmes.evaluationOf(selfReplacing, listOf(selfReplacing))).isEqualTo(eval)
    }

    @Test
    fun `a version counts until the day it stopped, never past its last day`() {
        val running = programme(1, createdAt = 0, evaluation = null, status = ProgrammeStatus.RUNNING).copy(startEpochDay = start)
        assertThat(Programmes.countedUntil(running, today = start + 100)).isEqualTo(start + 13)
        assertThat(Programmes.countedUntil(running.copy(stoppedEpochDay = start + 5), today = start + 100)).isEqualTo(start + 5)
        assertThat(Programmes.countedUntil(running, today = start + 3)).isEqualTo(start + 3)
    }

    @Test
    fun `the form is pre-filled with the shortest time that fits and the effort's wish`() {
        assertThat(NextInPlan.time(20)).isEqualTo(TimeAvailable.MIN_20)
        assertThat(NextInPlan.time(25)).isEqualTo(TimeAvailable.MIN_30)
        assertThat(NextInPlan.time(45)).isEqualTo(TimeAvailable.MIN_45)
        assertThat(NextInPlan.time(60)).isEqualTo(TimeAvailable.MIN_60_OR_MORE)
        assertThat(NextInPlan.time(90)).isEqualTo(TimeAvailable.MIN_60_OR_MORE)
        assertThat(NextInPlan.wish(PlannedEffort.EASY)).isEqualTo(Wish.EASY)
        assertThat(NextInPlan.wish(PlannedEffort.PUSH)).isEqualTo(Wish.PUSH)
        assertThat(NextInPlan.wish(PlannedEffort.STEADY)).isEqualTo(Wish.NOT_SURE)
    }

    private fun programme(id: Long, createdAt: Long, evaluation: Evaluation?, status: ProgrammeStatus, replacesId: Long? = null) =
        Programme(id, createdAt, ProgrammeAsk(2, 3), evaluation, plan, "a-model", start, status, null, replacesId)

    private fun session(id: Long, day: Long, kind: WorkoutKind) = Workout(
        id = id, epochDay = day, startedAtMillis = day * 86_400_000L + 7 * 3_600_000L, durationMinutes = 30,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )
}
