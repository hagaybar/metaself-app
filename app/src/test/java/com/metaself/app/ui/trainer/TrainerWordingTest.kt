package com.metaself.app.ui.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.Wish
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * What the trainer's three screens say (D85–D87). TEST_EPOCH_DAY is Thursday 3 September 2026; every
 * figure, word and answer here is invented.
 */
class TrainerWordingTest {

    @Test
    fun `the four rows and the felt effort say the design's words`() {
        assertThat(PlanActivity.entries.map(TrainerWording::activity)).containsExactly("Treadmill walk", "Outdoor walk", "Run", "Something else").inOrder()
        assertThat(TimeAvailable.entries.map(TrainerWording::time)).containsExactly("20 min", "30 min", "45 min", "60 min or more").inOrder()
        assertThat(Feeling.entries.map(TrainerWording::feeling)).containsExactly("Fresh", "Normal", "Tired").inOrder()
        assertThat(Wish.entries.map(TrainerWording::wish)).containsExactly("Easy", "A push", "Not sure").inOrder()
        assertThat(Felt.entries.map(TrainerWording::felt)).containsExactly("Easy", "Right", "Hard").inOrder()
    }

    @Test
    fun `a session's title says its kind, its day and its time`() {
        assertThat(TrainerWording.sessionTitle(walkAt(TEST_EPOCH_DAY, 7, 40), TEST_EPOCH_DAY, UTC)).isEqualTo("Walking, today 07:40")
        assertThat(TrainerWording.sessionTitle(walkAt(TEST_EPOCH_DAY - 1, 18, 10), TEST_EPOCH_DAY, UTC)).isEqualTo("Walking, yesterday 18:10")
        assertThat(TrainerWording.sessionTitle(walkAt(TEST_EPOCH_DAY - 2, 7, 0), TEST_EPOCH_DAY, UTC)).isEqualTo("Walking, Tue 1 Sep 07:00")
    }

    /** D85's card line; D87's figures with their sources (D4). */
    @Test
    fun `a session's figures say where each came from`() {
        val walk = walkAt(TEST_EPOCH_DAY, 7, 40).copy(
            distanceM = 3_000, energyKcal = 200, energySource = EnergySource.BAND, avgHeartRate = 110, maxHeartRate = 130,
        )

        assertThat(TrainerWording.sessionLine(walk)).isEqualTo("40 min · 3.0 km · 200 kcal · heart 110 average")
        assertThat(TrainerWording.figures(walk)).containsExactly(
            "40 min", "3.0 km (phone and band)", "200 kcal (band)", "Heart 110 avg · 130 max (from the readings)",
        ).inOrder()
        val filed = walk.copy(
            distanceM = 3_250, distanceSource = WorkoutFigureSource.FILE, steps = 4_000,
            stepsSource = WorkoutFigureSource.FILE, energySource = EnergySource.MET_ESTIMATE,
        )
        assertThat(TrainerWording.figures(filed)).containsAtLeast("3.25 km (from file)", "about 200 kcal, estimated", "4,000 steps (from file)")
    }

    /** D4: an estimated energy figure is never shown bare, even on the card's short line. */
    @Test
    fun `the card line says an estimated energy is about`() {
        val estimated = walkAt(TEST_EPOCH_DAY, 7, 40).copy(energyKcal = 200, energySource = EnergySource.MET_ESTIMATE)

        assertThat(TrainerWording.sessionLine(estimated)).isEqualTo("40 min · about 200 kcal")
    }

    @Test
    fun `an earlier session's line says how it felt, whether feedback was read, and the plan as judged`() {
        val read = TrainerReview(workoutId = 1, planId = 3, felt = Felt.RIGHT, words = "Invented.", feedback = aFeedback(PlanFollowed.YES))
        assertThat(TrainerWording.earlierLine(read)).isEqualTo("Felt right · feedback read · as planned")
        assertThat(TrainerWording.earlierLine(read.copy(feedback = aFeedback(PlanFollowed.PARTLY)))).isEqualTo("Felt right · feedback read · partly as planned")
        assertThat(TrainerWording.earlierLine(read.copy(feedback = aFeedback(PlanFollowed.NO)))).isEqualTo("Felt right · feedback read · not as planned")
        assertThat(TrainerWording.earlierLine(read.copy(feedback = null, words = null))).isEqualTo("Felt right · no words added · no feedback yet")
        assertThat(TrainerWording.earlierLine(read.copy(felt = null, feedback = aFeedback(PlanFollowed.NO_PLAN)))).isEqualTo("feedback read")
    }

    /** Design question 9: with no plan matched there was nothing to follow, whatever the stored judgement says. */
    @Test
    fun `a review with no plan never says as planned`() {
        val unplanned = TrainerReview(workoutId = 1, planId = null, felt = Felt.EASY, words = "Invented.", feedback = aFeedback(PlanFollowed.YES))

        assertThat(TrainerWording.earlierLine(unplanned)).isEqualTo("Felt easy · feedback read")
    }

    @Test
    fun `a session row offers the next step`() {
        assertThat(TrainerWording.rowAction(null)).isEqualTo("How did it go?")
        assertThat(TrainerWording.rowAction(REVIEW.copy(feedback = null))).isEqualTo("Get feedback")
        assertThat(TrainerWording.rowAction(REVIEW.copy(feedback = aFeedback(PlanFollowed.YES)))).isEqualTo("See feedback")
    }

    @Test
    fun `a step's minutes, the advice lines and the privacy lines`() {
        assertThat(TrainerWording.minutes(PlanStep(0, 8, "Warm up", ""))).isEqualTo("0–8")
        assertThat(TrainerWording.SUGGESTED).isEqualTo("Suggested by the AI trainer · advice, not a measurement")
        assertThat(TrainerWording.FROM_TRAINER).isEqualTo("From the AI trainer · advice, not a measurement")
        assertThat(TrainerWording.privacyPlan(30)).isEqualTo(
            "Sends these answers, your last six weeks of sessions and your weight trend to OpenAI with your key. " +
                "One of today's 30 AI requests.",
        )
        assertThat(TrainerWording.privacyReview(30)).isEqualTo(
            "Sends this session, your words, your last six weeks of sessions and your weight trend to OpenAI with your key. " +
                "One of today's 30 AI requests.",
        )
        assertThat(TrainerWording.planned("Steady walk")).isEqualTo("Planned: Steady walk")
    }

    /** Design question 16: the meal estimator's shapes, without "type the numbers". */
    @Test
    fun `failures are said as the meal estimator says them`() {
        assertThat(TrainerWording.failure(EstimateResult.NoKey)).isEqualTo("No API key yet. Add one in settings.")
        assertThat(TrainerWording.failure(EstimateResult.CeilingReached))
            .isEqualTo("You have used today's AI requests. Raise the daily limit in settings, or try tomorrow.")
        assertThat(TrainerWording.failure(EstimateResult.Unreachable())).isEqualTo("Could not reach the model. Your answers are still here.")
        assertThat(TrainerWording.failure(EstimateResult.Refused("invented"))).isEqualTo("The provider refused: invented")
        assertThat(TrainerWording.failure(EstimateResult.Unreadable("x"))).isEqualTo("The answer could not be understood.")
        assertThat(TrainerWording.savedWithoutFeedback(EstimateResult.Unreachable()))
            .isEqualTo("Your words are saved. Could not reach the model. Get feedback is on the session's row.")
    }

    @Test
    fun `feedback's four parts are headed as the design heads them`() {
        assertThat(TrainerWording.parts(aFeedback(PlanFollowed.YES)).map { it.first })
            .containsExactly("AGAINST THE PLAN", "WHAT THE NUMBERS SAY", "FOR NEXT TIME", "THIS WEEK").inOrder()
        assertThat(TrainerWording.parts(aFeedback(PlanFollowed.NO_PLAN).copy(againstPlan = "")).map { it.first })
            .doesNotContain("AGAINST THE PLAN")
    }

    /** A synced forty-minute walk starting at [hour]:[minute] UTC on [day]. Invented. */
    private fun walkAt(day: Long, hour: Int, minute: Int) = Workout(
        id = 1, epochDay = day, startedAtMillis = day * DAY + hour * HOUR + minute * MINUTE, durationMinutes = 40,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private fun aFeedback(followed: PlanFollowed) =
        Feedback("Invented headline.", "Invented.", "Invented.", "Invented.", "Invented.", followed)

    private companion object {
        val UTC: ZoneOffset = ZoneOffset.UTC
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        val REVIEW = TrainerReview(workoutId = 1, planId = null, felt = Felt.RIGHT, words = "Invented.")
    }
}
