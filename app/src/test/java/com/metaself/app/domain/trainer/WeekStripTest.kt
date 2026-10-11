package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import org.junit.jupiter.api.Test

/**
 * D114: the week strip, counted from the ledger's own partition (D110). The week is Monday 31 August 2026 to
 * today, Thursday 3 September ([TEST_EPOCH_DAY]); every session and plan is invented, and round.
 */
class WeekStripTest {

    private val monday = TEST_EPOCH_DAY - 3
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
    private val run20 = PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Short run")

    @Test
    fun `each session is a block in its day's column, ticked or beyond as the ledger says`() {
        val sessions = listOf(
            session(1, monday), session(2, monday + 1), session(3, monday + 2),
            session(4, monday + 2, WorkoutKind.SWIM, minutes = 30, hour = 9),
        )

        val week = week(sessions, listOf(kept(plan(walk30, walk30))))

        assertThat(week.strip.days.map { day -> day.blocks.map { it.workoutId to it.ticked } }).containsExactly(
            listOf(1L to true), listOf(2L to true), listOf(3L to false, 4L to false),
            emptyList<Pair<Long, Boolean>>(), emptyList<Pair<Long, Boolean>>(),
            emptyList<Pair<Long, Boolean>>(), emptyList<Pair<Long, Boolean>>(),
        ).inOrder()
        assertThat(week.strip.days.map { it.ticked }).containsExactly(true, true, false, false, false, false, false).inOrder()
        // The same partition as the ledger's: two ticked, two beyond.
        val blocks = week.strip.days.flatMap { it.blocks }
        assertThat(blocks.count { it.ticked }).isEqualTo(week.ledger.practice!!.sessions.count { it.outcome != PlannedOutcome.NOT_DONE })
        assertThat(blocks.count { !it.ticked }).isEqualTo(week.ledger.beyond.kinds.sumOf { it.sessions })
    }

    @Test
    fun `a confirmed shorter session counts as ticked, an open one is beyond the plan`() {
        val short = session(1, monday, minutes = 20)
        val confirmed = week(listOf(short), listOf(kept(plan(walk30), counting = PlanCounting(1, 0, mapOf(1L to true)))))
        val open = week(listOf(short), listOf(kept(plan(walk30))))

        assertThat(confirmed.strip.days.first().blocks.single().ticked).isTrue()
        assertThat(open.strip.days.first().blocks.single().ticked).isFalse()
        assertThat(open.strip.toDo).containsExactly(30)
    }

    @Test
    fun `open planned sessions are in To do only, with their minutes`() {
        val week = week(listOf(session(1, monday)), listOf(kept(plan(run20, walk30))))

        assertThat(week.strip.toDo).containsExactly(20)
        assertThat(week.strip.withPlan).isTrue()
        assertThat(week.strip.days.flatMap { it.blocks }.map { it.minutes }).containsExactly(40)
    }

    @Test
    fun `with no plan there is no To do and nothing is ticked`() {
        val week = week(listOf(session(1, monday), session(2, monday + 1)), emptyList())

        assertThat(week.strip.toDo).isEmpty()
        assertThat(week.strip.withPlan).isFalse()
        assertThat(week.strip.days.flatMap { it.blocks }.none { it.ticked }).isTrue()
    }

    @Test
    fun `days after today are empty, and hidden or uncounted sessions are not drawn`() {
        val sessions = listOf(
            session(1, monday + 4), session(2, monday).copy(hidden = true), session(3, monday).copy(counted = false),
            session(4, monday - 1),
        )

        val strip = week(sessions, emptyList()).strip

        assertThat(strip.days.map { it.epochDay }).containsExactlyElementsIn((monday..monday + 6).toList()).inOrder()
        assertThat(strip.days.all { it.blocks.isEmpty() }).isTrue()
        assertThat(strip.isAfterToday(strip.days[3])).isFalse()
        assertThat(strip.isAfterToday(strip.days[4])).isTrue()
    }

    @Test
    fun `a day's sessions are stacked oldest first`() {
        val strip = week(listOf(session(1, monday, hour = 18), session(2, monday, hour = 7)), emptyList()).strip

        assertThat(strip.days.first().blocks.map { it.workoutId }).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `the scale is the tallest column, To do included`() {
        val sessions = listOf(session(1, monday), session(2, monday + 2, minutes = 30), session(3, monday + 2, minutes = 30, hour = 9))

        assertThat(week(sessions, emptyList()).strip.scaleMinutes).isEqualTo(60)
        assertThat(week(sessions, listOf(kept(plan(run20, run20, run20, run20)))).strip.scaleMinutes).isEqualTo(80)
        assertThat(week(emptyList(), emptyList()).strip.scaleMinutes).isEqualTo(0)
    }

    @Test
    fun `a block's height is its share of the strip, never under the least`() {
        assertThat(WeekStrip.height(minutes = 60, scaleMinutes = 120, full = 100f, least = 8f)).isEqualTo(50f)
        assertThat(WeekStrip.height(minutes = 120, scaleMinutes = 120, full = 100f, least = 8f)).isEqualTo(100f)
        assertThat(WeekStrip.height(minutes = 5, scaleMinutes = 120, full = 100f, least = 8f)).isEqualTo(8f)
        assertThat(WeekStrip.height(minutes = 0, scaleMinutes = 0, full = 100f, least = 8f)).isEqualTo(8f)
    }

    @Test
    fun `the ledger's minutes and sessions are every counted session of the week`() {
        val sessions = listOf(session(1, monday), session(2, monday + 1, minutes = 20), session(3, monday + 2, WorkoutKind.SWIM, minutes = 30))

        val ledger = week(sessions, listOf(kept(plan(walk30)))).ledger

        assertThat(ledger.minutes).isEqualTo(90)
        assertThat(ledger.sessions).isEqualTo(3)
        assertThat(week(emptyList(), emptyList()).ledger.sessions).isEqualTo(0)
    }

    /**
     * D132: the strip card's movement — the ledger's sessions, this week's km to today, and the four weeks
     * before it as Movement's "Last four weeks" counts them. 3 + 4 km this week; 10 + 6 km in two of the four
     * weeks before; a day five weeks back is not among them. Invented.
     */
    @Test
    fun `the movement summary is the ledger's sessions, this week's km and the four weeks before`() {
        val days = listOf(
            HealthDay(monday, distanceM = 3_000), HealthDay(TEST_EPOCH_DAY, distanceM = 4_000),
            HealthDay(monday - 1, distanceM = 10_000), HealthDay(monday - 22, distanceM = 6_000),
            HealthDay(monday - 29, distanceM = 99_000),
        )
        val sessions = listOf(session(1, monday), session(2, monday + 1, WorkoutKind.SWIM, minutes = 30))

        val summary = TrainerWeek.of(monday, TEST_EPOCH_DAY, sessions, days, emptyMap(), targetKcal = null, plans = listOf(kept(plan(walk30)))).movement

        assertThat(summary).isEqualTo(MovementSummary(sessions = 2, distanceM = 7_000, fourWeeksM = 16_000))
        assertThat(week(emptyList(), emptyList()).movement).isEqualTo(MovementSummary(0, null, null))
    }

    private fun week(workouts: List<Workout>, plans: List<CountedPlan>) =
        TrainerWeek.of(monday, TEST_EPOCH_DAY, workouts, emptyList(), emptyMap(), targetKcal = null, plans = plans)

    private fun plan(vararg sessions: PlannedSession) =
        WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", sessions.toList()) }, "Invented reason.")

    private fun kept(plan: WeeksPlan, counting: PlanCounting = PlanCounting(1, 0, emptyMap())) =
        CountedPlan(Programme(1, 0, ProgrammeAsk(2, 2), null, plan, "a-model", monday, ProgrammeStatus.RUNNING), counting)

    private fun session(id: Long, day: Long, kind: WorkoutKind = WorkoutKind.WALK, minutes: Int = 40, hour: Int = 7) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + hour * HOUR, durationMinutes = minutes,
        kind = kind, title = null, distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
    }
}
