package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/**
 * D110: one week's ledger. The week is Monday 31 August 2026 to today, Thursday 3 September
 * ([TEST_EPOCH_DAY]); every session, day and figure is invented, and round.
 */
class WeekLedgersTest {

    private val monday = TEST_EPOCH_DAY - 3
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
    private val run20 = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Short run")

    @Test
    fun `every counted session lands in exactly one ledger`() {
        val sessions = listOf(
            session(1, monday), session(2, monday + 1), session(3, monday + 2),
            session(4, monday + 2, WorkoutKind.SWIM, minutes = 30, hour = 9), session(5, monday + 3, minutes = 10),
            session(6, monday, minutes = 50).copy(hidden = true), session(7, monday + 1, minutes = 50).copy(counted = false),
        )

        val ledger = ledger(sessions, listOf(kept(plan(walk30, walk30))))

        assertThat(ledger.practice).isEqualTo(
            PracticeLedger(1, 2, listOf(SessionOutcome(walk30, PlannedOutcome.DONE, 40), SessionOutcome(walk30, PlannedOutcome.DONE, 40))),
        )
        // Nothing of their kind left to tick: the third walk and the ten-minute one; and the swim.
        assertThat(ledger.beyond.kinds).containsExactly(
            KindLedger(WorkoutKind.WALK, 2, 50, null, null, emptyList()),
            KindLedger(WorkoutKind.SWIM, 1, 30, null, null, emptyList()),
        ).inOrder()
        // Five visible, counted sessions: two ticked, three beyond — none twice, none dropped.
        val placed = ledger.practice!!.sessions.count { it.outcome != PlannedOutcome.NOT_DONE } +
            ledger.beyond.kinds.sumOf { it.sessions }
        assertThat(placed).isEqualTo(5)
    }

    @Test
    fun `a planned session not done stays not done, however much else the week holds`() {
        val walks = (1L..6L).map { session(it, monday + (it - 1) / 2, hour = 7 + it.toInt()) }

        val ledger = ledger(walks, listOf(kept(plan(run20, walk30))))

        assertThat(ledger.practice!!.sessions).containsExactly(
            SessionOutcome(run20, PlannedOutcome.NOT_DONE, null), SessionOutcome(walk30, PlannedOutcome.DONE, 40),
        ).inOrder()
        assertThat(ledger.beyond.kinds).containsExactly(KindLedger(WorkoutKind.WALK, 5, 200, null, null, emptyList()))
    }

    /**
     * D105: ten minutes against thirty is under half — an attempt; twenty, unanswered, is a candidate. Both
     * are beyond the plan, each marked with the planned minutes it attempted; the place stays not done.
     */
    @Test
    fun `an attempt keeps its mark, beside its kind`() {
        val sessions = listOf(session(1, monday, minutes = 10), session(2, monday + 1), session(3, monday + 2, minutes = 20))

        val ledger = ledger(sessions, listOf(kept(plan(walk30, walk30))))

        assertThat(ledger.practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.NOT_DONE).inOrder()
        assertThat(ledger.beyond.kinds).containsExactly(
            KindLedger(
                WorkoutKind.WALK, 2, 30, null, null,
                listOf(AttemptFacts(WorkoutKind.WALK, 10, 30), AttemptFacts(WorkoutKind.WALK, 20, 30)),
            ),
        )
    }

    @Test
    fun `a week no plan covers has no practice ledger, and every session is beyond it`() {
        val sessions = listOf(session(1, monday), session(2, monday + 1, minutes = 20))

        // No plan at all; and a plan kept on a Friday, which starts next Monday.
        listOf(emptyList<CountedPlan>(), listOf(kept(plan(walk30), start = monday + 7))).forEach { plans ->
            val ledger = ledger(sessions, plans)

            assertThat(ledger.practice).isNull()
            assertThat(ledger.beyond.kinds).containsExactly(KindLedger(WorkoutKind.WALK, 2, 60, null, null, emptyList()))
        }
    }

    @Test
    fun `a session before the plan was kept is beyond it`() {
        val sessions = listOf(session(1, monday), session(2, monday + 1))
        val keptTuesday = PlanCounting(chainId = 1, fromMillis = (monday + 1) * DAY, answers = emptyMap())

        val ledger = ledger(sessions, listOf(kept(plan(walk30, walk30), counting = keptTuesday)))

        assertThat(ledger.practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.NOT_DONE).inOrder()
        assertThat(ledger.beyond.kinds).containsExactly(KindLedger(WorkoutKind.WALK, 1, 40, null, null, emptyList()))
    }

    @Test
    fun `after a plan stops, the rest of its week is beyond it`() {
        val sessions = listOf(session(1, monday), session(2, monday + 2))
        val stopped = kept(plan(walk30, walk30), status = ProgrammeStatus.STOPPED, stopped = monday + 1)

        val ledger = ledger(sessions, listOf(stopped))

        assertThat(ledger.practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.NOT_DONE).inOrder()
        assertThat(ledger.beyond.kinds).containsExactly(KindLedger(WorkoutKind.WALK, 1, 40, null, null, emptyList()))
    }

    /**
     * D97: an adjusted version keeps its plan's start. Under the old one (a 60-minute walk) the forty-minute
     * walk would be an unanswered candidate; under the adjusted one (30 minutes) it ticks. The week counts
     * under the version made later, whatever order the versions are read in.
     */
    @Test
    fun `of two versions with one start, the one made later counts the week`() {
        val longWalk = PlannedSession(WorkoutKind.WALK, 60, PlannedEffort.STEADY, "Long walk")
        val old = kept(plan(longWalk), id = 1, createdAtMillis = 0, status = ProgrammeStatus.ADJUSTED, stopped = monday + 2)
        val adjusted = kept(plan(walk30), id = 2, createdAtMillis = (monday + 2) * DAY)

        listOf(listOf(old, adjusted), listOf(adjusted, old)).forEach { plans ->
            val ledger = ledger(listOf(session(1, monday)), plans)

            assertThat(ledger.practice!!.sessions).containsExactly(SessionOutcome(walk30, PlannedOutcome.DONE, 40))
        }
    }

    /**
     * D70: zones 2 to 5 are brisk. The first walk has 1,200 + 600 + 600 + 0 seconds there: 40 minutes. The
     * second has zones, all in zone 1: it adds nothing. The third has none. A cycle whose zones are all zero
     * was measured: 0. A run with no zones at all has no brisk figure.
     */
    @Test
    fun `brisk minutes are zones 2 to 5 over the sessions with zones, and absent, not zero, for a kind with none`() {
        val sessions = listOf(
            session(1, monday).copy(zoneSeconds = listOf(600, 1_200, 600, 600, 0)),
            session(2, monday + 1).copy(zoneSeconds = listOf(300, 0, 0, 0, 0)),
            session(3, monday + 2),
            session(4, monday + 2, WorkoutKind.RUN, minutes = 20, hour = 9),
            session(5, monday + 3, WorkoutKind.CYCLE, minutes = 30).copy(zoneSeconds = listOf(0, 0, 0, 0, 0)),
        )

        val kinds = ledger(sessions).beyond.kinds.associateBy { it.kind }

        assertThat(kinds.getValue(WorkoutKind.WALK).briskMinutes).isEqualTo(40)
        assertThat(kinds.getValue(WorkoutKind.RUN).briskMinutes).isNull()
        assertThat(kinds.getValue(WorkoutKind.CYCLE).briskMinutes).isEqualTo(0)
    }

    @Test
    fun `a kind's distance sums the sessions that recorded one`() {
        val sessions = listOf(session(1, monday).copy(distanceM = 3_000), session(2, monday + 1).copy(distanceM = 4_000), session(3, monday + 2))

        assertThat(ledger(sessions).beyond.kinds.single().distanceM).isEqualTo(7_000)
        assertThat(ledger(listOf(session(1, monday))).beyond.kinds.single().distanceM).isNull()
    }

    @Test
    fun `an unrecognised session is beyond the plan, as other`() {
        val sessions = listOf(session(1, monday, WorkoutKind.UNRECOGNISED), session(2, monday + 1, WorkoutKind.OTHER))

        val ledger = ledger(sessions, listOf(kept(plan(walk30))))

        assertThat(ledger.beyond.kinds).containsExactly(KindLedger(WorkoutKind.OTHER, 2, 80, null, null, emptyList()))
    }

    /** Never split into "in sessions" and "outside them" (D110): the day's figures, averaged, and nothing else. */
    @Test
    fun `everyday movement is steps and distance a day, each over the days that record it`() {
        val days = listOf(
            HealthDay(monday, steps = 6_000, distanceM = 5_000),
            HealthDay(monday + 1, steps = 8_000),
            HealthDay(monday + 2, distanceM = 3_000),
            HealthDay(monday - 1, steps = 20_000, distanceM = 15_000),
        )

        val beyond = ledger(days = days).beyond

        assertThat(beyond.stepsADay).isEqualTo(7_000)
        assertThat(beyond.distanceMADay).isEqualTo(4_000)
        assertThat(ledger().beyond.stepsADay).isNull()
        assertThat(ledger().beyond.distanceMADay).isNull()
    }

    @Test
    fun `food is the days logged, the averages over them, and the target`() {
        val food = mapOf(
            monday to DayTotals(2_000, 100, 200, 70),
            monday + 2 to DayTotals(2_400, 140, 260, 80),
            monday - 1 to DayTotals(3_000, 50, 300, 90),
        )

        assertThat(ledger(food = food).food).isEqualTo(FoodContext(2, 2_200, 120, 2_100))
        assertThat(ledger().food).isEqualTo(FoodContext(0, null, null, 2_100))
    }

    /** Each figure over the days that record it; each usual over the 28 days before Monday. */
    @Test
    fun `recovery is each average over the days that record it, beside its usual`() {
        val week = listOf(
            HealthDay(monday, sleepMinutes = 420, restingHeartRate = 60, hrvMs = 40.0),
            HealthDay(monday + 1, sleepMinutes = 480),
            HealthDay(monday + 2, restingHeartRate = 62, hrvMs = 50.0),
        )
        // Seven days in the 28 before Monday, the earliest on its first day; one more just outside, which does not count.
        val before = (1L..7L).map { HealthDay(monday - it * 4, sleepMinutes = 400, restingHeartRate = 58, hrvMs = 30.0) } +
            HealthDay(monday - 29, sleepMinutes = 600, restingHeartRate = 90, hrvMs = 90.0)

        assertThat(ledger(days = week + before).recovery).isEqualTo(Recovery(450, 400, 61, 58, 45, 30))
    }

    /** One recorded night is never sent as the week's average: that average IS the night (D110). */
    @Test
    fun `a recovery figure recorded on one day of the week has no weekly average`() {
        val one = listOf(HealthDay(monday, sleepMinutes = 420, restingHeartRate = 60, hrvMs = 40.0))

        assertThat(ledger(days = one).recovery).isEqualTo(Recovery(null, null, null, null, null, null))
    }

    /** A usual of six nights is not a usual (D110). */
    @Test
    fun `there is no usual under seven recorded days`() {
        val six = (1L..6L).map { HealthDay(monday - it, sleepMinutes = 400, restingHeartRate = 58, hrvMs = 30.0) }
        val seven = six + HealthDay(monday - 7, sleepMinutes = 400, restingHeartRate = 58, hrvMs = 30.0)

        assertThat(ledger(days = six).recovery).isEqualTo(Recovery(null, null, null, null, null, null))
        assertThat(ledger(days = seven).recovery).isEqualTo(Recovery(null, 400, null, 58, null, 30))
    }

    @Test
    fun `this week is counted to today and is current, and an earlier week is all seven days`() {
        val sessions = listOf(session(1, monday - 1), session(2, monday - 7), session(3, monday), session(4, monday + 4))

        val earlier = ledger(sessions, week = monday - 7)
        val thisWeek = ledger(sessions)

        assertThat(earlier.current).isFalse()
        assertThat(earlier.beyond.kinds.single().sessions).isEqualTo(2)
        assertThat(thisWeek.current).isTrue()
        assertThat(thisWeek.beyond.kinds.single().sessions).isEqualTo(1)
    }

    /** Two weeks, each of [sessions]. */
    private fun plan(vararg sessions: PlannedSession) =
        WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", sessions.toList()) }, "Invented reason.")

    private fun kept(
        plan: WeeksPlan,
        start: Long = monday,
        id: Long = 1,
        createdAtMillis: Long = 0,
        status: ProgrammeStatus = ProgrammeStatus.RUNNING,
        stopped: Long? = null,
        counting: PlanCounting = ALL,
    ) = CountedPlan(Programme(id, createdAtMillis, ProgrammeAsk(2, 2), null, plan, "a-model", start, status, stopped), counting)

    private fun ledger(
        workouts: List<Workout> = emptyList(),
        plans: List<CountedPlan> = emptyList(),
        days: List<HealthDay> = emptyList(),
        food: Map<Long, DayTotals> = emptyMap(),
        week: Long = monday,
    ) = WeekLedgers.of(week, TEST_EPOCH_DAY, workouts, days, food, targetKcal = 2_100, plans = plans)

    private fun session(id: Long, day: Long, kind: WorkoutKind = WorkoutKind.WALK, minutes: Int = 40, hour: Int = 7) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + hour * HOUR, durationMinutes = minutes,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        val ALL = PlanCounting(chainId = 1, fromMillis = 0, answers = emptyMap())
    }
}
