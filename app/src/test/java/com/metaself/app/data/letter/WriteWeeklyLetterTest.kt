package com.metaself.app.data.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.BackgroundHealthCopy
import com.metaself.app.data.health.BackgroundHealthRead
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.health.HealthRecordState
import com.metaself.app.data.health.HealthRecordStatus
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.trainer.FakeProgrammeStore
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.data.trainer.InMemoryAboutMeStore
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.letter.LetterReply
import com.metaself.app.domain.letter.LetterTexts
import com.metaself.app.domain.letter.PlanWeekFigures
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanConfirmation
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.domain.weight.WeightReading
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The weekly letter's job (D99–D102), with fakes all round. The week is the one holding `TEST_EPOCH_DAY`
 * ([LETTER_MONDAY]); every figure, word and time here is invented and round.
 */
class WriteWeeklyLetterTest {

    private val food = FakeFoodTotals()
    private val weights = InMemoryWeightRepository()
    private val record = FakeMovementRecord()
    private val trainer = FakeTrainerStore()
    private val programmes = FakeProgrammeStore()
    private val profiles = FakeProfileRepository(aProfile())
    private val aboutMe = InMemoryAboutMeStore()
    private val letters = FakeLetterStore()
    private val events = mutableListOf<String>()
    private var told = 0
    private var granted = false
    private var copySucceeds = true
    private var onCopy: suspend () -> Unit = {}
    private var writer = FakeLetterWriter(LetterReply.Written(TEXTS, "a-model"))

    private fun job() = WriteWeeklyLetter(
        food = food,
        weights = weights,
        record = record,
        reviews = trainer,
        programmes = programmes,
        profiles = profiles,
        aboutMe = aboutMe,
        writer = { request -> events += "ask"; writer.write(request) },
        letters = letters,
        backgroundRead = object : BackgroundHealthRead {
            override suspend fun offered() = true
            override suspend fun granted() = granted
        },
        backgroundCopy = BackgroundHealthCopy { events += "copy"; onCopy(); copySucceeds },
        status = object : HealthRecordStatus {
            override suspend fun current() = HealthRecordState(lastCopiedMillis = LAST_COPIED)
        },
        now = { NOW },
        year = { 2026 },
        notifier = { told++ },
    )

    private fun walk(id: Long, day: Long, minutes: Int = 40) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 36_000_000, durationMinutes = minutes,
        kind = WorkoutKind.WALK, title = null, distanceM = 3_000, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
        hidden = false, note = null,
    )

    private fun someFood() {
        food.days = mapOf(LETTER_MONDAY to DayTotals(2_000, 100, 200, 70))
    }

    /** D103: a letter stored after all takes down "couldn't be written"; a failure or a quiet week leaves it. */
    @Test
    fun `a stored letter tells the notifications, and nothing else does`() = runTest {
        someFood()
        job()(LETTER_MONDAY)
        assertThat(told).isEqualTo(1)

        letters.letters.value = emptyList()
        writer = FakeLetterWriter(LetterReply.Failed(EstimateResult.Unreachable(), null))
        job()(LETTER_MONDAY)
        assertThat(told).isEqualTo(1)

        food.days = emptyMap()
        assertThat(job()(LETTER_MONDAY)).isEqualTo(WriteWeeklyLetter.Outcome.Quiet)
        assertThat(told).isEqualTo(1)
    }

    @Test
    fun `a quiet week asks nothing and stores nothing`() = runTest {
        food.days = mapOf(LETTER_MONDAY - 7 to DayTotals(2_000, 100, 200, 70))

        val outcome = job()(LETTER_MONDAY)

        assertThat(outcome).isEqualTo(WriteWeeklyLetter.Outcome.Quiet)
        assertThat(writer.asked).isEmpty()
        assertThat(letters.letters.value).isEmpty()
    }

    @Test
    fun `a week that already has a letter is never asked again, and nothing is copied`() = runTest {
        someFood()
        granted = true
        letters.add(aWeeklyLetter(LETTER_MONDAY))

        val outcome = job()(LETTER_MONDAY)

        assertThat(outcome).isEqualTo(WriteWeeklyLetter.Outcome.AlreadyWritten)
        assertThat(events).isEmpty()
        assertThat(letters.letters.value).hasSize(1)
    }

    @Test
    fun `a letter stored while the band's data was copied is not asked for again`() = runTest {
        someFood()
        granted = true
        onCopy = { letters.add(aWeeklyLetter(LETTER_MONDAY)) }

        val outcome = job()(LETTER_MONDAY)

        assertThat(outcome).isEqualTo(WriteWeeklyLetter.Outcome.AlreadyWritten)
        assertThat(events).containsExactly("copy")
    }

    @Test
    fun `with the permission the band's data is copied before the ask, and nothing is noted as behind`() = runTest {
        someFood()
        granted = true

        val outcome = job()(LETTER_MONDAY)

        assertThat(events).containsExactly("copy", "ask").inOrder()
        assertThat((outcome as WriteWeeklyLetter.Outcome.Written).letter.bandDataUntil).isNull()
    }

    @Test
    fun `a session the background copy brings counts, so the week is not quiet`() = runTest {
        granted = true
        onCopy = { record.workouts.value = listOf(walk(1, LETTER_MONDAY + 6)) }

        val outcome = job()(LETTER_MONDAY)

        assertThat(outcome).isInstanceOf(WriteWeeklyLetter.Outcome.Written::class.java)
        assertThat((outcome as WriteWeeklyLetter.Outcome.Written).letter.figures.week.movement.sessions).isEqualTo(1)
    }

    @Test
    fun `without the permission nothing is copied, and the letter says how far the band's data goes`() = runTest {
        someFood()

        val outcome = job()(LETTER_MONDAY)

        assertThat(events).containsExactly("ask")
        assertThat((outcome as WriteWeeklyLetter.Outcome.Written).letter.bandDataUntil).isEqualTo(LAST_COPIED)
    }

    @Test
    fun `a copy that does not finish leaves the letter saying how far the band's data goes`() = runTest {
        someFood()
        granted = true
        copySucceeds = false

        val outcome = job()(LETTER_MONDAY)

        assertThat(events).containsExactly("copy", "ask").inOrder()
        assertThat((outcome as WriteWeeklyLetter.Outcome.Written).letter.bandDataUntil).isEqualTo(LAST_COPIED)
    }

    @Test
    fun `Write it now never copies`() = runTest {
        someFood()
        granted = true

        job()(LETTER_MONDAY, copy = false)

        assertThat(events).containsExactly("ask")
    }

    @Test
    fun `a written letter is stored with this week, the four before it, and today's target`() = runTest {
        food.days = mapOf(
            LETTER_MONDAY to DayTotals(2_000, 100, 200, 70),
            LETTER_MONDAY + 1 to DayTotals(2_200, 120, 220, 90),
            LETTER_MONDAY - 14 to DayTotals(1_800, 90, 180, 60),
        )

        val outcome = job()(LETTER_MONDAY)

        val letter = (outcome as WriteWeeklyLetter.Outcome.Written).letter
        assertThat(letters.letters.value.single().copy(id = 0)).isEqualTo(letter)
        assertThat(letter.weekMonday).isEqualTo(LETTER_MONDAY)
        assertThat(letter.createdAtMillis).isEqualTo(NOW)
        assertThat(letter.texts).isEqualTo(TEXTS)
        assertThat(letter.model).isEqualTo("a-model")
        assertThat(letter.figures.week.food.kcal).isEqualTo(2_100)
        assertThat(letter.figures.week.food.daysLogged).isEqualTo(2)
        assertThat(letter.figures.earlier.map { it.monday })
            .containsExactly(LETTER_MONDAY - 7, LETTER_MONDAY - 14, LETTER_MONDAY - 21, LETTER_MONDAY - 28).inOrder()
        assertThat(letter.figures.earlier[1].food.kcal).isEqualTo(1_800)
        assertThat(letter.figures.targetKcal).isEqualTo(CurrentTarget.of(aProfile(), null, 2026, 0).kcal)
    }

    @Test
    fun `the request carries no words on a session, last week's line for next week, and the note trimmed`() = runTest {
        record.workouts.value = listOf(walk(1, LETTER_MONDAY + 2))
        trainer.reviews.value = listOf(TrainerReview(workoutId = 1, planId = null, felt = Felt.HARD, words = "Invented words"))
        aboutMe.note.value = "  Invented note.  "
        letters.add(aWeeklyLetter(LETTER_MONDAY - 7).let { it.copy(texts = it.texts.copy(nextWeek = "Invented line.")) })

        job()(LETTER_MONDAY)

        val request = writer.asked.single()
        assertThat(request.sessions).hasSize(1)
        assertThat(request.sessions.single().words).isNull()
        assertThat(request.sessions.single().felt).isNull()
        assertThat(request.toString()).doesNotContain("Invented words")
        assertThat(request.lastNextWeek).isEqualTo("Invented line.")
        assertThat(request.aboutMe).isEqualTo("Invented note.")
        assertThat(request.figures.week.movement.hard).isEqualTo(1)
    }

    @Test
    fun `a blank note is sent as none`() = runTest {
        someFood()
        aboutMe.note.value = "   "

        job()(LETTER_MONDAY)

        assertThat(writer.asked.single().aboutMe).isNull()
        assertThat(writer.asked.single().lastNextWeek).isNull()
    }

    @Test
    fun `the plan's week counts only ticked sessions, never a candidate waiting for an answer`() = runTest {
        val planned = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Invented walk")
        val plan = WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", listOf(planned, planned)) }, "Invented.")
        programmes.rows.value = listOf(
            Programme(1, 0, ProgrammeAsk(2, 2), null, plan, "a-model", LETTER_MONDAY - 7, ProgrammeStatus.RUNNING),
        )
        // One as long as planned; one over half of it, never answered — a candidate (D105).
        record.workouts.value = listOf(walk(1, LETTER_MONDAY), walk(2, LETTER_MONDAY + 2, minutes = 25))

        job()(LETTER_MONDAY)

        val request = writer.asked.single()
        assertThat(request.figures.week.plan).isEqualTo(PlanWeekFigures("Invented plan", planned = 2, done = 1, ended = false))
        assertThat(request.planTitle).isEqualTo("Invented plan")
        assertThat(request.planWeek).isEqualTo(plan.weeks[1])
        assertThat(request.figures.earlier[0].plan).isEqualTo(PlanWeekFigures("Invented plan", planned = 2, done = 0, ended = false))
        assertThat(request.figures.earlier[1].plan).isNull()
    }

    @Test
    fun `a candidate the owner confirmed counts as done`() = runTest {
        val planned = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Invented walk")
        val plan = WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", listOf(planned, planned)) }, "Invented.")
        programmes.rows.value = listOf(
            Programme(1, 0, ProgrammeAsk(2, 2), null, plan, "a-model", LETTER_MONDAY - 7, ProgrammeStatus.RUNNING),
        )
        programmes.confirm(PlanConfirmation(programmeId = 1, workoutId = 2, confirmed = true, answeredAtMillis = 0))
        record.workouts.value = listOf(walk(1, LETTER_MONDAY), walk(2, LETTER_MONDAY + 2, minutes = 25))

        job()(LETTER_MONDAY)

        assertThat(writer.asked.single().figures.week.plan!!.done).isEqualTo(2)
    }

    @Test
    fun `no key, a refusal or the day's ceiling gives up at once, and nothing is stored`() = runTest {
        someFood()
        for (failure in listOf(EstimateResult.NoKey, EstimateResult.Refused("Invented."), EstimateResult.CeilingReached)) {
            writer = FakeLetterWriter(LetterReply.Failed(failure))

            assertThat(job()(LETTER_MONDAY)).isEqualTo(WriteWeeklyLetter.Outcome.GiveUp(failure))
        }
        assertThat(letters.letters.value).isEmpty()
    }

    @Test
    fun `a provider error, a 5xx or too many requests, is worth trying again, and any other refusal is not`() = runTest {
        someFood()
        val refused = EstimateResult.Refused("Invented.")
        for (status in listOf(500, 503, 429)) {
            writer = FakeLetterWriter(LetterReply.Failed(refused, status))
            assertThat(job()(LETTER_MONDAY)).isEqualTo(WriteWeeklyLetter.Outcome.Retry(refused))
        }
        for (status in listOf(400, 401, 403)) {
            writer = FakeLetterWriter(LetterReply.Failed(refused, status))
            assertThat(job()(LETTER_MONDAY)).isEqualTo(WriteWeeklyLetter.Outcome.GiveUp(refused))
        }
        assertThat(letters.letters.value).isEmpty()
    }

    @Test
    fun `no network or an unreadable answer is worth trying again, and nothing is stored`() = runTest {
        someFood()
        for (failure in listOf(EstimateResult.Unreachable(), EstimateResult.Unreadable("Invented why.", answer = "Invented answer."))) {
            writer = FakeLetterWriter(LetterReply.Failed(failure))

            assertThat(job()(LETTER_MONDAY)).isEqualTo(WriteWeeklyLetter.Outcome.Retry(failure))
        }
        assertThat(letters.letters.value).isEmpty()
    }

    @Test
    fun `a weigh-in alone makes the week worth a letter`() = runTest {
        weights.log(WeightReading(LETTER_MONDAY + 3, 80.0))

        assertThat(job()(LETTER_MONDAY)).isInstanceOf(WriteWeeklyLetter.Outcome.Written::class.java)
    }

    @Test
    fun `a week wants a letter when it has none and is not quiet, and asking that sends and copies nothing`() = runTest {
        granted = true
        assertThat(job().wanted(LETTER_MONDAY)).isFalse()

        someFood()
        assertThat(job().wanted(LETTER_MONDAY)).isTrue()

        letters.add(aWeeklyLetter(LETTER_MONDAY))
        assertThat(job().wanted(LETTER_MONDAY)).isFalse()
        assertThat(events).isEmpty()
    }

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 1_000_000L
        const val LAST_COPIED = 500_000L
        val TEXTS = LetterTexts("Invented headline.", "Invented a.", "Invented b.", "Invented c.", "Invented d.", "Invented e.")
    }
}
