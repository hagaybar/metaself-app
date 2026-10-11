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
import com.metaself.app.data.trainer.InMemoryWeekNoteStore
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.letter.LetterPatterns
import com.metaself.app.domain.letter.LetterReply
import com.metaself.app.domain.letter.LetterTexts
import com.metaself.app.domain.letter.PastLetter
import com.metaself.app.domain.letter.PlanWeekFigures
import com.metaself.app.domain.letter.SessionWords
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.trainer.AttemptFacts
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.FoodContext
import com.metaself.app.domain.trainer.GoalVerdict
import com.metaself.app.domain.trainer.GoalView
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.KindLedger
import com.metaself.app.domain.trainer.PlanConfirmation
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PracticeLedger
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.SessionOutcome
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
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
    private val weekNotes = InMemoryWeekNoteStore()
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
        weekNotes = weekNotes,
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

    /** D116: the letter's week_note is kept with the letter's week and the moment it came; a letter without one keeps none. */
    @Test
    fun `the letter's week_note is kept with its week`() = runTest {
        someFood()
        writer = FakeLetterWriter(LetterReply.Written(TEXTS, "a-model"))
        job()(LETTER_MONDAY)
        assertThat(weekNotes.note.value).isNull()

        letters.letters.value = emptyList()
        writer = FakeLetterWriter(LetterReply.Written(TEXTS, "a-model", "Invented week note."))
        job()(LETTER_MONDAY)
        assertThat(weekNotes.note.value).isEqualTo(WeekNote("Invented week note.", NOW, LETTER_MONDAY, WeekNoteSource.LETTER))
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
    fun `the request carries no words on a session, the week's answers apart, last week's letter, and the note trimmed`() = runTest {
        record.workouts.value = listOf(walk(1, LETTER_MONDAY + 2), walk(2, LETTER_MONDAY - 3))
        trainer.reviews.value = listOf(
            TrainerReview(workoutId = 1, planId = null, felt = Felt.HARD, words = " Invented words "),
            TrainerReview(workoutId = 2, planId = null, felt = Felt.EASY, words = "Invented earlier words"),
        )
        aboutMe.note.value = "  Invented note.  "
        letters.add(aWeeklyLetter(LETTER_MONDAY - 7, aCoachLetter(lookAfter = "Invented look.", nextWeek = "Invented line.")))

        job()(LETTER_MONDAY)

        val request = writer.asked.single()
        assertThat(request.sessions).hasSize(1)
        assertThat(request.sessions.single().words).isNull()
        assertThat(request.sessions.single().felt).isNull()
        // D141: this week's answers to "How did it go?" go apart from the sessions; an earlier week's do not.
        assertThat(request.sessionWords).containsExactly(SessionWords(LETTER_MONDAY + 2, WorkoutKind.WALK, Felt.HARD, "Invented words"))
        assertThat(request.toString()).doesNotContain("Invented earlier words")
        assertThat(request.lastLetters).containsExactly(PastLetter(LETTER_MONDAY - 7, "Invented look.", "Invented line."))
        assertThat(request.aboutMe).isEqualTo("Invented note.")
        assertThat(request.figures.week.movement.hard).isEqualTo(1)
    }

    /** D141: the last three letters before this week, newest first — an old six-part letter by its look_at. */
    @Test
    fun `the last three letters go, the old shape by its look_at`() = runTest {
        someFood()
        val old = LetterTexts.SixParts("Invented.", "Invented.", "Invented.", "Invented old look.", "Invented old line.", "Invented.")
        letters.add(aWeeklyLetter(LETTER_MONDAY - 35, old))
        letters.add(aWeeklyLetter(LETTER_MONDAY - 28, old))
        letters.add(aWeeklyLetter(LETTER_MONDAY - 21, aCoachLetter(nextWeek = "Invented a.")))
        // A quiet week had no letter; the run skips it.
        letters.add(aWeeklyLetter(LETTER_MONDAY - 7, aCoachLetter(nextWeek = "Invented b.")))

        job()(LETTER_MONDAY)

        assertThat(writer.asked.single().lastLetters).containsExactly(
            PastLetter(LETTER_MONDAY - 7, "Invented.", "Invented b."),
            PastLetter(LETTER_MONDAY - 21, "Invented.", "Invented a."),
            PastLetter(LETTER_MONDAY - 28, "Invented old look.", "Invented old line."),
        ).inOrder()
    }

    @Test
    fun `the request carries the 35 day rows and their patterns`() = runTest {
        someFood()
        record.workouts.value = listOf(walk(1, LETTER_MONDAY))
        record.days.value = listOf(HealthDay(LETTER_MONDAY, steps = 8_000, sleepMinutes = 420))

        job()(LETTER_MONDAY)

        val request = writer.asked.single()
        assertThat(request.days).hasSize(35)
        assertThat(request.days.first().epochDay).isEqualTo(LETTER_MONDAY - 28)
        val monday = request.days.single { it.epochDay == LETTER_MONDAY }
        assertThat(monday.ownMinutes).isEqualTo(40)
        assertThat(monday.steps).isEqualTo(8_000)
        assertThat(monday.sleepMinutes).isEqualTo(420)
        assertThat(monday.kcal).isEqualTo(2_000)
        assertThat(request.days.single { it.epochDay == LETTER_MONDAY + 1 }.kcal).isNull()
        assertThat(request.patterns).isEqualTo(LetterPatterns.of(request.days, LETTER_MONDAY))
    }

    /** D141: the running plan's goal, the view stored with its evaluation, and the words of the last five weeks' requests. */
    @Test
    fun `the request carries the plan's goal, the trainer's view of it and the plan-request words`() = runTest {
        someFood()
        val planned = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Invented walk")
        val plan = WeeksPlan("Invented plan", List(4) { PlanWeek("Invented focus", listOf(planned)) }, "Invented.")
        val goal = PlanGoal.WeeklyMinutes(150, LETTER_MONDAY + 13)
        val view = GoalView(GoalVerdict.STRETCH, "Invented view.")
        val made = (LETTER_MONDAY - 14) * DAY + 43_200_000
        programmes.rows.value = listOf(
            // Asked long before the five weeks: its words stay home.
            Programme(1, (LETTER_MONDAY - 60) * DAY + 43_200_000, ProgrammeAsk(2, 2, "Invented old words"), null, plan.copy(weeks = plan.weeks.take(2)), "a-model", LETTER_MONDAY - 63, ProgrammeStatus.REPLACED),
            Programme(2, made, ProgrammeAsk(4, 2, " Invented plan words ", goal), null, plan, "a-model", LETTER_MONDAY - 14, ProgrammeStatus.ADJUSTED, goalView = view),
            Programme(3, made + DAY, ProgrammeAsk(4, 2, "Invented adjust words", goal), null, plan, "a-model", LETTER_MONDAY - 14, ProgrammeStatus.RUNNING, replacesId = 2),
        )

        job()(LETTER_MONDAY)

        val request = writer.asked.single()
        assertThat(request.planGoal).isEqualTo(goal)
        assertThat(request.goalView).isEqualTo(view)
        assertThat(request.planWords.map { it.words to it.adjustment }).containsExactly(
            "Invented plan words" to false, "Invented adjust words" to true,
        ).inOrder()
        assertThat(request.toString()).doesNotContain("Invented old words")
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

    /**
     * D110, D111: the letter carries its week's ledger. The plan's second week holds two 40-minute walks:
     * one is done; a 25-minute walk, never answered, is a candidate — beyond the plan, marked as an attempt.
     * Seven nights of 400 minutes before the week; two in it, of 400 and 440. Invented.
     */
    @Test
    fun `the request carries the week's ledger`() = runTest {
        val planned = PlannedSession(WorkoutKind.WALK, 40, PlannedEffort.STEADY, "Invented walk")
        val plan = WeeksPlan("Invented plan", List(2) { PlanWeek("Invented focus", listOf(planned, planned)) }, "Invented.")
        programmes.rows.value = listOf(
            Programme(1, 0, ProgrammeAsk(2, 2), null, plan, "a-model", LETTER_MONDAY - 7, ProgrammeStatus.RUNNING),
        )
        record.workouts.value = listOf(walk(1, LETTER_MONDAY), walk(2, LETTER_MONDAY + 2, minutes = 25))
        record.days.value = (1L..7L).map { HealthDay(LETTER_MONDAY - it, sleepMinutes = 400) } +
            listOf(HealthDay(LETTER_MONDAY, sleepMinutes = 400), HealthDay(LETTER_MONDAY + 1, sleepMinutes = 440))
        someFood()

        job()(LETTER_MONDAY)

        val ledger = writer.asked.single().ledger
        assertThat(ledger.monday).isEqualTo(LETTER_MONDAY)
        assertThat(ledger.practice).isEqualTo(
            PracticeLedger(2, 2, listOf(SessionOutcome(planned, PlannedOutcome.DONE, 40), SessionOutcome(planned, PlannedOutcome.NOT_DONE, null))),
        )
        assertThat(ledger.beyond.kinds).containsExactly(
            KindLedger(WorkoutKind.WALK, 1, 25, 3_000, null, listOf(AttemptFacts(WorkoutKind.WALK, 25, 40))),
        )
        assertThat(ledger.food).isEqualTo(FoodContext(1, 2_000, 100, CurrentTarget.of(aProfile(), null, 2026, 0).kcal))
        assertThat(ledger.recovery.sleepMinutes).isEqualTo(420)
        assertThat(ledger.recovery.usualSleepMinutes).isEqualTo(400)
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
        val TEXTS = LetterTexts.Coach("Invented headline.", "Invented a.", "Invented b.", "Invented c.", "Invented f.", "Invented d.", "Invented e.")
    }
}
