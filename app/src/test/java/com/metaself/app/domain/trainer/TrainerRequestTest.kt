package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.weight.WeightReading
import org.junit.jupiter.api.Test

/** D84: what one request holds. Every figure, date and word is invented. */
class TrainerRequestTest {

    private val walk = session(id = 1, day = 20_699, kind = WorkoutKind.WALK, minutes = 40).copy(
        distanceM = 3_000, energyKcal = 200, energySource = EnergySource.BAND,
        avgHeartRate = 110, maxHeartRate = 130, zoneSeconds = listOf(600, 1_200, 600, 0, 0), zoneMaxSource = "ESTIMATED",
    )
    private val strength = session(id = 2, day = 20_697, kind = WorkoutKind.STRENGTH, minutes = 45).copy(
        source = WorkoutSource.TYPED, energyKcal = 150, energySource = EnergySource.MET_ESTIMATE,
    )
    private val fromFile = session(id = 3, day = 20_658, kind = WorkoutKind.WALK, minutes = 40).copy(
        distanceM = 3_250, distanceSource = WorkoutFigureSource.FILE, steps = 4_000, stepsSource = WorkoutFigureSource.FILE,
    )
    private val tooEarly = session(id = 4, day = 20_657, kind = WorkoutKind.RUN, minutes = 30)
    private val hidden = session(id = 5, day = 20_698, kind = WorkoutKind.RUN, minutes = 30).copy(hidden = true)
    private val uncounted = session(id = 6, day = 20_698, kind = WorkoutKind.WALK, minutes = 30).copy(counted = false)
    private val all = listOf(walk, strength, fromFile, tooEarly, hidden, uncounted)

    private val plan = TrainerPlan(
        id = 9, createdAtMillis = 0,
        answers = PlanAnswers(PlanActivity.OUTDOOR_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.EASY),
        plan = SessionPlan("Easy loop", listOf(PlanStep(0, 45, "Walk", "easy pace")), "Invented."),
        model = "a-model", kept = false,
    )
    private val review = TrainerReview(id = 1, workoutId = 1, planId = 9, felt = Felt.RIGHT, words = "Invented words.")

    private fun request(
        question: TrainerQuestion = TrainerQuestion.Plan(
            PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_30, Feeling.FRESH, Wish.PUSH),
        ),
        readings: List<WeightReading> = listOf(WeightReading(20_671, 80.0), WeightReading(20_685, 80.0), WeightReading(20_699, 80.0)),
        profile: com.metaself.app.domain.profile.Profile? = aProfile(),
        feedback: List<Feedback> = emptyList(),
        workouts: List<Workout> = all,
        earliest: Long? = null,
        aboutMe: String? = null,
    ) = TrainerRequest.of(
        question = question, today = TEST_EPOCH_DAY, workouts = workouts, reviews = listOf(review),
        plans = mapOf(9L to plan),
        days = listOf(HealthDay(20_699, distanceM = 4_000, activeKcal = 300), HealthDay(20_698, distanceM = 2_000, activeKcal = 100),
            HealthDay(20_690, distanceM = 5_000)),
        readings = readings, profile = profile, currentYear = TEST_YEAR, earlierFeedback = feedback,
        earliestDay = earliest, aboutMe = aboutMe,
    )

    @Test
    fun `the sessions are the counted ones of the last 42 days, oldest first`() {
        assertThat(request().sessions.map { it.epochDay }).containsExactly(20_658L, 20_697L, 20_699L).inOrder()
    }

    @Test
    fun `a session carries each figure with where it came from`() {
        val sessions = request().sessions

        val sent = sessions.last()
        assertThat(sent.kind).isEqualTo(WorkoutKind.WALK)
        assertThat(sent.minutes).isEqualTo(40)
        assertThat(sent.distanceM).isEqualTo(3_000)
        assertThat(sent.distanceFrom).isEqualTo(Origin.SYNCED)
        assertThat(sent.energyKcal).isEqualTo(200)
        assertThat(sent.energyFrom).isEqualTo(EnergySource.BAND)
        assertThat(sent.avgHeartRate).isEqualTo(110)
        assertThat(sent.maxHeartRate).isEqualTo(130)
        assertThat(sent.zoneMinutes).containsExactly(10, 20, 10, 0, 0).inOrder()
        assertThat(sent.zoneMaxEstimated).isTrue()

        val typed = sessions[1]
        assertThat(typed.energyFrom).isEqualTo(EnergySource.MET_ESTIMATE)
        assertThat(typed.distanceM).isNull()
        assertThat(typed.distanceFrom).isNull()

        val filed = sessions.first()
        assertThat(filed.distanceFrom).isEqualTo(Origin.FILE)
        assertThat(filed.steps).isEqualTo(4_000)
        assertThat(filed.stepsFrom).isEqualTo(Origin.FILE)
    }

    @Test
    fun `a reviewed session carries how it felt, the words and its plan`() {
        val sent = request().sessions.last()

        assertThat(sent.felt).isEqualTo(Felt.RIGHT)
        assertThat(sent.words).isEqualTo("Invented words.")
        assertThat(sent.plan).isEqualTo(plan.plan)
        assertThat(request().sessions.first().felt).isNull()
    }

    /** D74's figures, per Monday-based week, newest first; no meals. */
    @Test
    fun `six weeks of totals, this one first and so far`() {
        val weeks = request().weeks

        assertThat(weeks.map { it.monday }).containsExactly(20_696L, 20_689L, 20_682L, 20_675L, 20_668L, 20_661L).inOrder()
        assertThat(weeks.first().current).isTrue()
        assertThat(weeks.drop(1).none { it.current }).isTrue()
        assertThat(weeks[0].distanceM).isEqualTo(6_000)
        assertThat(weeks[0].averageActiveKcal).isEqualTo(200)
        assertThat(weeks[0].sessions).isEqualTo(2) // the walk and the strength session; hidden and uncounted left out
        assertThat(weeks[1].distanceM).isEqualTo(5_000)
        assertThat(weeks[1].sessions).isEqualTo(0)
    }

    /**
     * The weight screen's figures: the smoothed line now, and its measured weekly change. The readings
     * differ from each other and from the line, so sending any one of them would show. Invented:
     * 80.4, 79.2 and 78.6 kg a fortnight apart put the line at 78.80006 kg (a tenth's pull a day), and
     * the 28 days from the first reading at 1.59994 kg down, 0.39998 kg a week.
     */
    @Test
    fun `the weight is the trend and its measured rate, never a weigh-in`() {
        val readings = listOf(WeightReading(20_671, 80.4), WeightReading(20_685, 79.2), WeightReading(20_699, 78.6))
        val request = request(readings = readings)
        val weight = request.weight!!

        assertThat(weight.trendKg).isWithin(1e-4).of(78.80006)
        assertThat(weight.asOfEpochDay).isEqualTo(20_699L)
        assertThat(weight.kgPerWeek!!).isWithin(1e-4).of(-0.39998)
        assertThat(weight.overDays).isEqualTo(28)
        readings.forEach { reading ->
            assertThat(weight.trendKg).isNotEqualTo(reading.kg)
            assertThat(request.toString()).doesNotContain(reading.kg.toString())
        }
        assertThat(request(readings = emptyList()).weight).isNull()
    }

    @Test
    fun `the goal is direction and rate, the body is age, sex and height`() {
        val request = request()

        assertThat(request.goal).isEqualTo(GoalFacts(GoalDirection.LOSE, 0.5))
        assertThat(request.body).isEqualTo(BodyFacts(ageYears = 46, sex = Sex.MALE, heightCm = 180))
        assertThat(request(profile = null).goal).isNull()
        assertThat(request(profile = null).body).isNull()
    }

    /** The rhythm is counted here, never by the model (D87). A Thursday: Friday to Sunday are left, today not counted. */
    @Test
    fun `this week's sessions so far and the days left are counted on the phone`() {
        assertThat(request().thisWeek).isEqualTo(Rhythm(sessionsSoFar = 2, daysLeft = 3))
    }

    @Test
    fun `at most three earlier feedback texts go`() {
        val four = (1..4).map { Feedback("Headline $it", "", "", "", "", PlanFollowed.NO_PLAN) }

        assertThat(request(feedback = four).earlierFeedback.map { it.headline })
            .containsExactly("Headline 1", "Headline 2", "Headline 3").inOrder()
    }

    @Test
    fun `a review's question is its session with the words being asked about`() {
        val question = TrainerRequest.reviewQuestion(walk, review, plan)

        assertThat(question.session.felt).isEqualTo(Felt.RIGHT)
        assertThat(question.session.plan).isEqualTo(plan.plan)
    }

    /** D89: June 2026's walk goes in June's line; the record begins on 1 June, so May and before have none. */
    @Test
    fun `the months come from the record, oldest first`() {
        val june = session(id = 7, day = JUNE_1 + 9, kind = WorkoutKind.WALK, minutes = 50).copy(distanceM = 4_000)

        val months = request(workouts = all + june, earliest = JUNE_1).months

        assertThat(months.map { it.firstDay }).containsExactly(JUNE_1, JUNE_1 + 30).inOrder()
        assertThat(months.first().kinds).containsExactly(KindFacts(WorkoutKind.WALK, 1, 4_000))
        assertThat(months.first().minutes).isEqualTo(50)
        assertThat(request(earliest = null).months).isEmpty()
    }

    /** D90: the note goes as written, trimmed; a blank one is no note. */
    @Test
    fun `the note is sent as written, and a blank one is none`() {
        assertThat(request(aboutMe = "  Invented note.\nSecond line. ").aboutMe).isEqualTo("Invented note.\nSecond line.")
        assertThat(request(aboutMe = "   ").aboutMe).isNull()
        assertThat(request().aboutMe).isNull()
    }

    /** D89: a month's weight is the trend's change; no weigh-in of the month is in its line. Invented kg. */
    @Test
    fun `a month's line holds no weigh-in`() {
        val readings = listOf(
            WeightReading(JUNE_1 - 1, 81.4), WeightReading(JUNE_1 + 14, 80.6), WeightReading(JUNE_1 + 29, 80.2),
            WeightReading(20_699, 79.8),
        )

        val months = request(readings = readings, earliest = JUNE_1).months

        assertThat(months.first().weightChangeKg).isNotNull()
        readings.forEach { assertThat(months.toString()).doesNotContain(it.kg.toString()) }
    }

    /**
     * D84's "never sent", as a shape: there is no field for a meal, sleep, a raw reading, a weigh-in, a
     * target, a name, a title or an origin. D89 adds the monthly lines and D90 the owner's note. A new field fails here before it can leave the phone.
     */
    @Test
    fun `the request has room for exactly what D84 lists`() {
        assertThat(fieldsOf(TrainerRequest::class.java)).containsExactly(
            "question", "today", "aboutMe", "sessions", "weeks", "months", "weight", "goal", "body", "thisWeek",
            "earlierFeedback",
        )
        assertThat(fieldsOf(SessionFacts::class.java)).containsExactly(
            "epochDay", "kind", "minutes", "distanceM", "distanceFrom", "energyKcal", "energyFrom",
            "avgHeartRate", "maxHeartRate", "zoneMinutes", "zoneMaxEstimated", "steps", "stepsFrom",
            "felt", "words", "plan",
        )
        assertThat(fieldsOf(WeekFacts::class.java)).containsExactly("monday", "distanceM", "averageActiveKcal", "sessions", "current")
        assertThat(fieldsOf(WeightFacts::class.java)).containsExactly("trendKg", "asOfEpochDay", "kgPerWeek", "overDays")
        assertThat(fieldsOf(MonthFacts::class.java)).containsExactly(
            "firstDay", "lastDay", "part", "kinds", "minutes", "longestMinutes", "bestWeekMonday", "bestWeekM",
            "avgHeartRate", "felt", "stepsADay", "weightChangeKg",
        )
        assertThat(fieldsOf(KindFacts::class.java)).containsExactly("kind", "sessions", "distanceM")
        assertThat(fieldsOf(FeltCounts::class.java)).containsExactly("easy", "right", "hard")
        assertThat(fieldsOf(GoalFacts::class.java)).containsExactly("direction", "kgPerWeek")
        assertThat(fieldsOf(BodyFacts::class.java)).containsExactly("ageYears", "sex", "heightCm")
    }

    private fun fieldsOf(type: Class<*>): List<String> =
        type.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }

    private fun session(id: Long, day: Long, kind: WorkoutKind, minutes: Int) = Workout(
        id = id, epochDay = day, startedAtMillis = day * 86_400_000L + 7 * 3_600_000L, durationMinutes = minutes,
        kind = kind, title = "Invented title", distanceM = null, energyKcal = null, energySource = EnergySource.NONE,
        effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        val JUNE_1 = java.time.LocalDate.of(2026, 6, 1).toEpochDay()
    }
}
