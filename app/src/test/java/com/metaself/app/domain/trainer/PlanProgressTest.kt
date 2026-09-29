package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/** D95, D96, D98, D105. The plan starts Monday 31 August 2026; every session and figure is invented. */
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
        val progress = PlanProgress.of(plan, start, listOf(session(2, start + 2, WorkoutKind.WALK), session(1, start, WorkoutKind.WALK)), until = start + 6, counting = ALL)

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

        val week = PlanProgress.of(plan, start, sessions, until = start + 6, counting = ALL).weeks.first()

        assertThat(week.ticks.map { it.by?.id }).containsExactly(null, null, 1L).inOrder()
    }

    @Test
    fun `hidden and uncounted sessions, and days after until, count for nothing`() {
        val sessions = listOf(
            session(1, start, WorkoutKind.WALK).copy(hidden = true),
            session(2, start, WorkoutKind.WALK).copy(counted = false),
            session(3, start + 8, WorkoutKind.WALK),
        )

        val progress = PlanProgress.of(plan, start, sessions, until = start + 7, counting = ALL)

        assertThat(progress.done).isEqualTo(0)
        assertThat(progress.planned).isEqualTo(5)
    }

    @Test
    fun `sessions tick their own week only`() {
        val progress = PlanProgress.of(plan, start, listOf(session(1, start + 7, WorkoutKind.WALK)), until = start + 13, counting = ALL)

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

    // --- D105 -------------------------------------------------------------------------------------

    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk 30")
    private val walk40 = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Walk 40")
    private val oneWeek = WeeksPlan("Invented", listOf(PlanWeek("w", listOf(walk30, walk40))), "Invented.")
    private val longOnly = WeeksPlan("Invented", listOf(PlanWeek("w", listOf(walk40))), "Invented.")

    private fun week(p: WeeksPlan, vararg sessions: Workout, answers: Map<Long, Boolean> = emptyMap(), from: Long = 0) =
        PlanProgress.of(p, start, sessions.toList(), until = start + 6, counting = PlanCounting(7, from, answers)).weeks.first()

    @Test
    fun `a session that starts before the plan was kept ticks nothing and is no attempt`() {
        val kept = (start + 1) * DAY + 12 * HOUR
        val w = week(oneWeek, session(1, start, WorkoutKind.WALK), session(2, start + 1, WorkoutKind.WALK, hour = 8), from = kept)

        assertThat(w.ticks.map { it.by?.id }).containsExactly(null, null).inOrder()
        assertThat(w.attempts).isEmpty()
        assertThat(week(oneWeek, session(3, start + 1, WorkoutKind.WALK, hour = 13), from = kept).done).isEqualTo(1)
    }

    @Test
    fun `a session as long as planned ticks the first place it fills in full, in plan order`() {
        val w = week(oneWeek, session(1, start, WorkoutKind.WALK, minutes = 30), session(2, start + 1, WorkoutKind.WALK, minutes = 40))

        assertThat(w.ticks.map { it.by?.id }).containsExactly(1L, 2L).inOrder()
        assertThat(w.ticks.none { it.short }).isTrue()
    }

    @Test
    fun `a session shorter than one place but as long as a later one ticks the later one`() {
        val plan = WeeksPlan("Invented", listOf(PlanWeek("w", listOf(walk40, walk30))), "Invented.")

        val w = week(plan, session(1, start, WorkoutKind.WALK, minutes = 30))

        assertThat(w.ticks.map { it.by?.id }).containsExactly(null, 1L).inOrder()
        assertThat(w.ticks.first().candidate).isNull()
    }

    @Test
    fun `at least half as long is a candidate until answered, and not a tick`() {
        val w = week(longOnly, session(1, start, WorkoutKind.WALK, minutes = 20))

        assertThat(w.ticks.single().by).isNull()
        assertThat(w.ticks.single().candidate?.id).isEqualTo(1L)
        assertThat(w.done).isEqualTo(0)
        assertThat(w.next).isEqualTo(walk40)
        assertThat(w.attempts).isEmpty()
    }

    @Test
    fun `a candidate answered yes ticks, marked short, and one answered no is an attempt`() {
        val yes = week(longOnly, session(1, start, WorkoutKind.WALK, minutes = 20), answers = mapOf(1L to true))
        val no = week(longOnly, session(1, start, WorkoutKind.WALK, minutes = 20), answers = mapOf(1L to false))

        assertThat(yes.ticks.single().by?.id).isEqualTo(1L)
        assertThat(yes.ticks.single().short).isTrue()
        assertThat(yes.done).isEqualTo(1)
        assertThat(no.ticks.single().by).isNull()
        assertThat(no.ticks.single().candidate).isNull()
        assertThat(no.attempts.map { it.workout.id to it.against }).containsExactly(1L to walk40)
    }

    @Test
    fun `a session said yes to stays on the place it was confirmed for, even with a shorter place after it`() {
        val walk20 = PlannedSession(WorkoutKind.WALK, 20, PlannedEffort.EASY, "Walk 20")
        val plan = WeeksPlan("Invented", listOf(PlanWeek("w", listOf(walk30, walk20))), "Invented.")

        val w = week(plan, session(1, start, WorkoutKind.WALK, minutes = 20), answers = mapOf(1L to true))

        assertThat(w.ticks.map { it.by?.id }).containsExactly(1L, null).inOrder()
        assertThat(w.ticks.first().short).isTrue()
    }

    @Test
    fun `a session said yes to pushes out an earlier open candidate`() {
        val w = week(
            longOnly, session(1, start, WorkoutKind.WALK, minutes = 20), session(2, start + 1, WorkoutKind.WALK, minutes = 25),
            answers = mapOf(2L to true),
        )

        assertThat(w.ticks.single().by?.id).isEqualTo(2L)
        assertThat(w.ticks.single().candidate).isNull()
        assertThat(w.attempts.map { it.workout.id }).containsExactly(1L)
    }

    @Test
    fun `under half is an attempt against the nearest place`() {
        val w = week(oneWeek, session(1, start, WorkoutKind.WALK, minutes = 10))

        assertThat(w.done).isEqualTo(0)
        assertThat(w.ticks.all { it.candidate == null }).isTrue()
        assertThat(w.attempts.map { it.workout.id to it.against }).containsExactly(1L to walk30)
    }

    @Test
    fun `an open candidate gives way to a later full session, and becomes an attempt`() {
        val w = week(longOnly, session(1, start, WorkoutKind.WALK, minutes = 20), session(2, start + 2, WorkoutKind.WALK, minutes = 40))

        assertThat(w.ticks.single().by?.id).isEqualTo(2L)
        assertThat(w.ticks.single().candidate).isNull()
        assertThat(w.attempts.map { it.workout.id }).containsExactly(1L)
    }

    @Test
    fun `a candidate answered yes holds its place against a later full session`() {
        val w = week(
            longOnly, session(1, start, WorkoutKind.WALK, minutes = 20), session(2, start + 2, WorkoutKind.WALK, minutes = 40),
            answers = mapOf(1L to true),
        )

        assertThat(w.ticks.single().by?.id).isEqualTo(1L)
        assertThat(w.attempts).isEmpty()
    }

    @Test
    fun `a second short session passes over a place holding an open candidate`() {
        val twoLong = WeeksPlan("Invented", listOf(PlanWeek("w", listOf(walk40, walk40))), "Invented.")
        val first = session(1, start, WorkoutKind.WALK, minutes = 20)
        val second = session(2, start + 1, WorkoutKind.WALK, minutes = 30)

        assertThat(week(twoLong, first, second).ticks.map { it.candidate?.id }).containsExactly(1L, 2L).inOrder()

        val one = week(longOnly, first, second)
        assertThat(one.ticks.single().candidate?.id).isEqualTo(1L)
        assertThat(one.attempts.map { it.workout.id }).containsExactly(2L)

        val afterNo = week(longOnly, first, second, answers = mapOf(1L to false))
        assertThat(afterNo.ticks.single().candidate?.id).isEqualTo(2L)
        assertThat(afterNo.attempts.map { it.workout.id }).containsExactly(1L)
    }

    @Test
    fun `a walk after every walk place is ticked is neither a tick nor an attempt`() {
        val w = week(longOnly, session(1, start, WorkoutKind.WALK, minutes = 40), session(2, start + 1, WorkoutKind.WALK, minutes = 10))

        assertThat(w.done).isEqualTo(1)
        assertThat(w.attempts).isEmpty()
    }

    @Test
    fun `the outcomes name done, done short and not done, with the attempts and the open candidates`() {
        val plan = WeeksPlan("Invented", listOf(PlanWeek("w", listOf(walk30, walk40, walk40, run))), "Invented.")
        val w = week(
            plan,
            session(1, start, WorkoutKind.WALK, minutes = 30),
            session(2, start + 1, WorkoutKind.WALK, minutes = 20),
            session(3, start + 2, WorkoutKind.WALK, minutes = 30),
            session(4, start + 3, WorkoutKind.RUN, minutes = 5),
            answers = mapOf(2L to true),
        )

        val outcome = w.outcome()

        assertThat(outcome.week).isEqualTo(1)
        assertThat(outcome.sessions.map { it.outcome }).containsExactly(
            PlannedOutcome.DONE, PlannedOutcome.DONE_SHORT, PlannedOutcome.NOT_DONE, PlannedOutcome.NOT_DONE,
        ).inOrder()
        assertThat(outcome.sessions.map { it.minutesDone }).containsExactly(30, 20, null, null).inOrder()
        assertThat(outcome.attempts).containsExactly(
            AttemptFacts(WorkoutKind.WALK, 30, 40),
            AttemptFacts(WorkoutKind.RUN, 5, 20),
        ).inOrder()
    }

    @Test
    fun `the chain's first version is found by following what each replaces`() {
        val first = programme(1, createdAt = 100, evaluation = null, status = ProgrammeStatus.ADJUSTED)
        val second = programme(2, createdAt = 200, evaluation = null, status = ProgrammeStatus.ADJUSTED, replacesId = 1)
        val third = programme(3, createdAt = 300, evaluation = null, status = ProgrammeStatus.RUNNING, replacesId = 2)
        val loop = programme(5, createdAt = 500, evaluation = null, status = ProgrammeStatus.RUNNING, replacesId = 5)

        assertThat(Programmes.rootOf(third, listOf(first, second, third)).id).isEqualTo(1)
        assertThat(Programmes.rootOf(first, listOf(first)).id).isEqualTo(1)
        assertThat(Programmes.rootOf(loop, listOf(loop)).id).isEqualTo(5)
    }

    private fun programme(id: Long, createdAt: Long, evaluation: Evaluation?, status: ProgrammeStatus, replacesId: Long? = null) =
        Programme(id, createdAt, ProgrammeAsk(2, 3), evaluation, plan, "a-model", start, status, null, replacesId)

    /** Forty minutes at 07:00 unless said: as long as every planned session above. */
    private fun session(id: Long, day: Long, kind: WorkoutKind, minutes: Int = 40, hour: Int = 7) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + hour * HOUR, durationMinutes = minutes,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L

        /** Every session counts: kept before any of them started, nothing answered. */
        val ALL = PlanCounting(chainId = 1, fromMillis = 0, answers = emptyMap())
    }
}
