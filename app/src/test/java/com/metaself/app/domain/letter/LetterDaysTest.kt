package com.metaself.app.domain.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.CountedPlan
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeeksPlan
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * D141: the letter's 35 day rows. The letter's week is the one holding [TEST_EPOCH_DAY]; every session, day
 * and figure is invented, and round.
 */
class LetterDaysTest {

    private val monday = MovementWeek.mondayOf(TEST_EPOCH_DAY)
    private val sunday = monday + 6
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")

    @Test
    fun `the rows are the 35 days to the letter's Sunday, oldest first`() {
        val rows = LetterDays.of(sunday, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())

        assertThat(rows).hasSize(35)
        assertThat(rows.first().epochDay).isEqualTo(monday - 28)
        assertThat(rows.last().epochDay).isEqualTo(sunday)
        assertThrows<IllegalArgumentException> { LetterDays.of(sunday - 1, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap()) }
    }

    /** One planned walk a week: the first walk of the week ticks it; the second, and the swim, are his own. */
    @Test
    fun `minutes are split into planned and own as the ledger splits them`() {
        val sessions = listOf(
            session(1, monday, minutes = 40), session(2, monday, WorkoutKind.SWIM, minutes = 30, hour = 18),
            session(3, monday + 2, minutes = 20), session(4, monday + 3, minutes = 50).copy(hidden = true),
        )

        val rows = LetterDays.of(sunday, sessions, listOf(kept(plan(walk30))), emptyList(), emptyList(), emptyMap())

        val first = rows.single { it.epochDay == monday }
        assertThat(first.plannedMinutes).isEqualTo(40)
        assertThat(first.ownMinutes).isEqualTo(30)
        assertThat(first.sessions.map { it.kind }).containsExactly(WorkoutKind.WALK, WorkoutKind.SWIM).inOrder()
        assertThat(rows.single { it.epochDay == monday + 2 }.ownMinutes).isEqualTo(20)
        // A hidden session is counted nowhere.
        assertThat(rows.single { it.epochDay == monday + 3 }.sessions).isEmpty()
    }

    @Test
    fun `without a plan every minute is his own`() {
        val rows = LetterDays.of(sunday, listOf(session(1, monday)), emptyList(), emptyList(), emptyList(), emptyMap())

        assertThat(rows.single { it.epochDay == monday }.let { it.plannedMinutes to it.ownMinutes }).isEqualTo(0 to 40)
    }

    @Test
    fun `a session carries how it felt, never the words`() {
        val review = TrainerReview(workoutId = 1, planId = null, felt = Felt.EASY, words = "Invented words.")

        val rows = LetterDays.of(sunday, listOf(session(1, monday)), emptyList(), listOf(review), emptyList(), emptyMap())

        assertThat(rows.single { it.epochDay == monday }.sessions).containsExactly(DaySession(WorkoutKind.WALK, 40, Felt.EASY))
    }

    @Test
    fun `steps and the night's sleep come from the day, and an unlogged day's food is null`() {
        val days = listOf(HealthDay(monday, steps = 8_000, sleepMinutes = 420))
        val food = mapOf(monday + 1 to DayTotals(2_000, 100, 200, 70))

        val rows = LetterDays.of(sunday, emptyList(), emptyList(), emptyList(), days, food)

        val first = rows.single { it.epochDay == monday }
        assertThat(first.steps).isEqualTo(8_000)
        assertThat(first.sleepMinutes).isEqualTo(420)
        assertThat(first.kcal).isNull()
        assertThat(first.proteinG).isNull()
        val second = rows.single { it.epochDay == monday + 1 }
        assertThat(second.kcal).isEqualTo(2_000)
        assertThat(second.proteinG).isEqualTo(100)
        assertThat(second.steps).isNull()
        assertThat(second.sleepMinutes).isNull()
    }

    private fun plan(vararg sessions: PlannedSession) =
        WeeksPlan("Invented plan", List(6) { PlanWeek("Invented focus", sessions.toList()) }, "Invented reason.")

    private fun kept(plan: WeeksPlan) = CountedPlan(
        Programme(1, 0, ProgrammeAsk(6, 2), null, plan, "a-model", monday - 28, ProgrammeStatus.RUNNING),
        PlanCounting(chainId = 1, fromMillis = 0, answers = emptyMap()),
    )

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
