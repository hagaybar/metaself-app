package com.metaself.app.ui.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.BeyondPlan
import com.metaself.app.domain.trainer.FoodContext
import com.metaself.app.domain.trainer.KindLedger
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlanCounting
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PracticeLedger
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.Recovery
import com.metaself.app.domain.trainer.SessionOutcome
import com.metaself.app.domain.trainer.WeekLedger
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.trainer.TrainerWeekWording.Box
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * What the top of the Trainer page says (D114, D115). The week is Monday 31 August 2026 to today, Thursday
 * 3 September ([TEST_EPOCH_DAY]); every figure is invented, and round.
 */
class TrainerWeekWordingTest {

    private val monday = TEST_EPOCH_DAY - 3
    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")

    @Test
    fun `the header names the week, across a month or within one`() {
        assertThat(TrainerWeekWording.header(monday)).isEqualTo("THIS WEEK · MON 31 AUG – SUN 6 SEP")
        assertThat(TrainerWeekWording.header(LocalDate.of(2026, 9, 7).toEpochDay())).isEqualTo("THIS WEEK · MON 7 – SUN 13 SEP")
    }

    @Test
    fun `the verdict's first clause is the week's minutes`() {
        assertThat(TrainerWeekWording.verdict(ledger(beyond = listOf(walks(1, 40))))).isEqualTo("40 minutes moving this week.")
        assertThat(TrainerWeekWording.verdict(ledger(beyond = listOf(walks(1, 1))))).isEqualTo("1 minute moving this week.")
        assertThat(TrainerWeekWording.verdict(ledger())).isEqualTo("Nothing recorded yet this week.")
    }

    @Test
    fun `every row of D115's table`() {
        val done = SessionOutcome(walk30, PlannedOutcome.DONE, 40)
        val short = SessionOutcome(walk30, PlannedOutcome.DONE_SHORT, 20)
        fun open(kind: WorkoutKind) = SessionOutcome(walk30.copy(kind = kind), PlannedOutcome.NOT_DONE, null)
        fun verdict(vararg sessions: SessionOutcome) = TrainerWeekWording.verdict(ledger(practice = PracticeLedger(1, 2, sessions.toList())))

        assertThat(verdict(done, short)).isEqualTo("60 minutes moving this week. Every planned session done.")
        assertThat(verdict(done, open(WorkoutKind.WALK))).isEqualTo("40 minutes moving this week. One planned walk still to do.")
        assertThat(verdict(done, open(WorkoutKind.RUN))).endsWith("One planned run still to do.")
        assertThat(verdict(done, open(WorkoutKind.SWIM))).endsWith("One planned swim still to do.")
        assertThat(verdict(done, open(WorkoutKind.CYCLE))).endsWith("One planned ride still to do.")
        assertThat(verdict(done, open(WorkoutKind.STRENGTH))).endsWith("One planned session still to do.")
        assertThat(verdict(done, open(WorkoutKind.WALK), open(WorkoutKind.RUN))).endsWith(" Two planned sessions still to do.")
        assertThat(verdict(open(WorkoutKind.WALK))).isEqualTo("Nothing recorded yet this week. One planned walk still to do.")
        // No plan this week, or a week of the plan with nothing in it: no second clause.
        assertThat(TrainerWeekWording.verdict(ledger(beyond = listOf(walks(2, 80))))).isEqualTo("80 minutes moving this week.")
        assertThat(verdict()).isEqualTo("Nothing recorded yet this week.")
    }

    @Test
    fun `numbers are words to ten`() {
        assertThat(TrainerWeekWording.number(2)).isEqualTo("Two")
        assertThat(TrainerWeekWording.number(10)).isEqualTo("Ten")
        assertThat(TrainerWeekWording.number(11)).isEqualTo("11")
    }

    @Test
    fun `the main button names the open session's kind`() {
        assertThat(TrainerWeekWording.planOpen(WorkoutKind.WALK)).isEqualTo("Plan the open walk")
        assertThat(TrainerWeekWording.planOpen(WorkoutKind.OTHER)).isEqualTo("Plan the open session")
    }

    @Test
    fun `sleep's difference is rounded to ten minutes, half away from zero`() {
        assertThat(TrainerWeekWording.sleepDifference(410, 440)).isEqualTo(-30)
        assertThat(TrainerWeekWording.sleepDifference(415, 440)).isEqualTo(-30)
        assertThat(TrainerWeekWording.sleepDifference(445, 420)).isEqualTo(30)
        assertThat(TrainerWeekWording.sleepDifference(424, 420)).isEqualTo(0)
        assertThat(TrainerWeekWording.sleepDifference(415, 420)).isEqualTo(-10)
    }

    @Test
    fun `the boxes`() {
        assertThat(TrainerWeekWording.boxes(ledger(steps = 8_900))).containsExactly(Box("STEPS", "8,900", "a day"))
        assertThat(TrainerWeekWording.boxes(ledger(sleep = 410, usualSleep = 440)))
            .containsExactly(Box("SLEEP", "6 h 50", "30 min under usual", under = true))
        assertThat(TrainerWeekWording.boxes(ledger(sleep = 450, usualSleep = 430)))
            .containsExactly(Box("SLEEP", "7 h 30", "20 min over usual"))
        assertThat(TrainerWeekWording.boxes(ledger(sleep = 422, usualSleep = 420))).containsExactly(Box("SLEEP", "7 h 02", "as usual"))
        assertThat(TrainerWeekWording.boxes(ledger(sleep = 420))).containsExactly(Box("SLEEP", "7 h", "a night"))
        assertThat(TrainerWeekWording.boxes(ledger(food = FoodContext(3, 1_300, 80, 1_650))))
            .containsExactly(Box("FOOD", "1,300", "kcal · target 1,650", "over 3 logged days"))
        assertThat(TrainerWeekWording.boxes(ledger(food = FoodContext(7, 1_300, 80, 1_650)))).containsExactly(Box("FOOD", "1,300", "kcal · target 1,650"))
        assertThat(TrainerWeekWording.boxes(ledger(food = FoodContext(1, 1_300, 80, null)))).containsExactly(Box("FOOD", "1,300", "kcal", "over 1 logged day"))
    }

    @Test
    fun `absent boxes leave the others`() {
        assertThat(TrainerWeekWording.boxes(ledger())).isEmpty()
        assertThat(TrainerWeekWording.boxes(ledger(food = FoodContext(2, 1_300, 80, 1_650))).map { it.label }).containsExactly("FOOD")
        assertThat(TrainerWeekWording.boxes(ledger(steps = 8_000, food = FoodContext(2, 1_300, 80, 1_650))).map { it.label })
            .containsExactly("STEPS", "FOOD").inOrder()
        // An average with no usual is the average alone; no average, no box — a usual alone is not drawn.
        assertThat(TrainerWeekWording.boxes(ledger(usualSleep = 440))).isEmpty()
    }

    @Test
    fun `a night's sleep is hours and minutes`() {
        assertThat(TrainerWeekWording.sleep(410)).isEqualTo("6 h 50")
        assertThat(TrainerWeekWording.sleep(420)).isEqualTo("7 h")
        assertThat(TrainerWeekWording.sleep(425)).isEqualTo("7 h 05")
        assertThat(TrainerWeekWording.sleep(45)).isEqualTo("45 min")
    }

    @Test
    fun `the legend`() {
        assertThat(TrainerWeekWording.legend(withPlan = true)).containsExactly("Planned, done", "Planned, open", "Beyond the plan").inOrder()
        assertThat(TrainerWeekWording.legend(withPlan = false)).containsExactly("Sessions")
    }

    @Test
    fun `the plan's row`() {
        val counting = PlanCounting(1, 0, emptyMap())
        val running = Programme(
            1, 0, ProgrammeAsk(2, 2), null, WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(walk30)) }, "Invented."),
            "a-model", monday - 7, ProgrammeStatus.RUNNING,
        )

        assertThat(TrainerWeekWording.planRow(PlanCard.of(running, emptyList(), TEST_EPOCH_DAY, counting))).isEqualTo("Your plan · week 2 of 2")
        val later = running.copy(startEpochDay = monday + 7)
        assertThat(TrainerWeekWording.planRow(PlanCard.of(later, emptyList(), TEST_EPOCH_DAY, counting))).isEqualTo("Your plan · starts Mon 7 Sep")
        assertThat(TrainerWeekWording.planRow(PlanCard.of(running, emptyList(), TEST_EPOCH_DAY + 7, counting))).isNull()
        assertThat(TrainerWeekWording.planRow(PlanCard.None)).isNull()
    }

    @Test
    fun `the day letters, and the open minutes`() {
        assertThat((0..6).map { TrainerWeekWording.dayLetter(monday + it) }).containsExactly("M", "T", "W", "T", "F", "S", "S").inOrder()
        assertThat(TrainerWeekWording.openMinutes(40)).isEqualTo("40′")
    }

    private fun walks(sessions: Int, minutes: Int) = KindLedger(WorkoutKind.WALK, sessions, minutes, null, null, emptyList())

    private fun ledger(
        practice: PracticeLedger? = null,
        beyond: List<KindLedger> = emptyList(),
        steps: Int? = null,
        sleep: Int? = null,
        usualSleep: Int? = null,
        food: FoodContext = FoodContext(0, null, null, 1_650),
    ) = WeekLedger(
        monday, current = true, practice = practice, beyond = BeyondPlan(beyond, steps, null), food = food,
        recovery = Recovery(sleep, usualSleep, null, null, null, null),
    )
}
