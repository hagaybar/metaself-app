package com.metaself.app.ui.trainer

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
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
import com.metaself.app.ui.movement.MovementWeekWording
import com.metaself.app.ui.movement.WorkoutFileWording
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the trainer's three screens say (D85–D87). Every AI answer is under [SUGGESTED] or
 * [FROM_TRAINER] (D4); every figure of a session says where it came from.
 */
object TrainerWording {

    const val SUGGESTED = "Suggested by the AI trainer · advice, not a measurement"
    const val FROM_TRAINER = "From the AI trainer · advice, not a measurement"

    private const val SEP = " · "
    private const val STILL_HERE = " Your answers are still here."
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

    fun activity(activity: PlanActivity): String = when (activity) {
        PlanActivity.TREADMILL_WALK -> "Treadmill walk"
        PlanActivity.OUTDOOR_WALK -> "Outdoor walk"
        PlanActivity.RUN -> "Run"
        PlanActivity.SOMETHING_ELSE -> "Something else"
    }

    fun time(time: TimeAvailable): String = if (time.orMore) "${time.minutes} min or more" else "${time.minutes} min"

    fun feeling(feeling: Feeling): String = when (feeling) {
        Feeling.FRESH -> "Fresh"
        Feeling.NORMAL -> "Normal"
        Feeling.TIRED -> "Tired"
    }

    fun wish(wish: Wish): String = when (wish) {
        Wish.EASY -> "Easy"
        Wish.PUSH -> "A push"
        Wish.NOT_SURE -> "Not sure"
    }

    fun felt(felt: Felt): String = when (felt) {
        Felt.EASY -> "Easy"
        Felt.RIGHT -> "Right"
        Felt.HARD -> "Hard"
    }

    /** "Walking, today 07:40"; "…, yesterday 18:10"; "…, Tue 1 Sep 07:00". */
    fun sessionTitle(workout: Workout, today: Long, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone)
        val day = when (workout.epochDay) {
            today -> "today"
            today - 1 -> "yesterday"
            else -> LocalDate.ofEpochDay(workout.epochDay).format(DAY)
        }
        return "${MovementWeekWording.name(workout)}, $day ${at.format(TIME)}"
    }

    /**
     * The waiting card's line (D85): "40 min · 3.0 km · 200 kcal · heart 110 average". Short, so the
     * sources wait for the review screen; but an estimated energy still says "about" (D4).
     */
    fun sessionLine(workout: Workout): String = listOfNotNull(
        MovementWeekWording.duration(workout.durationMinutes),
        workout.distanceM?.let(MovementWeekWording::km),
        workout.energyKcal?.let { kcal ->
            when (workout.energySource) {
                EnergySource.NONE -> null
                EnergySource.MET_ESTIMATE -> "about ${number(kcal)} kcal"
                else -> "${number(kcal)} kcal"
            }
        },
        workout.avgHeartRate?.let { "heart $it average" },
    ).joinToString(SEP)

    /** D87: the session's figures, each with where it came from (D4, D69, D82). */
    fun figures(workout: Workout): List<String> = listOfNotNull(
        MovementWeekWording.duration(workout.durationMinutes),
        workout.distanceM?.let { metres ->
            when {
                workout.distanceSource == WorkoutFigureSource.FILE -> WorkoutFileWording.fileKm(metres) + " (from file)"
                workout.distanceSource == WorkoutFigureSource.TYPED || workout.source == WorkoutSource.TYPED ->
                    MovementWeekWording.km(metres) + " (you typed it)"
                else -> MovementWeekWording.km(metres) + " (phone and band)"
            }
        },
        workout.energyKcal?.let { kcal ->
            when (workout.energySource) {
                EnergySource.BAND -> "${number(kcal)} kcal (band)"
                EnergySource.MET_ESTIMATE -> "about ${number(kcal)} kcal, estimated"
                EnergySource.TYPED -> "${number(kcal)} kcal, you set this"
                EnergySource.FILE -> "${number(kcal)} kcal, from the file"
                EnergySource.NONE -> null
            }
        },
        heart(workout),
        workout.steps?.let { "${number(it)} steps (from file)" },
    )

    private fun heart(workout: Workout): String? {
        val parts = listOfNotNull(workout.avgHeartRate?.let { "$it avg" }, workout.maxHeartRate?.let { "$it max" })
        return if (parts.isEmpty()) null else "Heart " + parts.joinToString(SEP) + " (from the readings)"
    }

    fun planned(title: String): String = "Planned: $title"

    fun minutes(step: PlanStep): String = "${step.fromMinute}–${step.toMinute}"

    /**
     * Design question 9. Whether it followed its plan is the trainer's judgement, said only when the
     * review has a plan: with none there was nothing to follow.
     */
    fun earlierLine(review: TrainerReview): String = listOfNotNull(
        review.felt?.let { "Felt " + felt(it).lowercase(Locale.US) },
        "no words added".takeIf { review.words.isNullOrBlank() && review.felt != null },
        if (review.feedback != null) "feedback read" else "no feedback yet",
        review.planId?.let {
            when (review.feedback?.followed) {
                PlanFollowed.YES -> "as planned"
                PlanFollowed.PARTLY -> "partly as planned"
                PlanFollowed.NO -> "not as planned"
                PlanFollowed.NO_PLAN, null -> null
            }
        },
    ).joinToString(SEP)

    /** Design question 7. */
    fun rowAction(review: TrainerReview?): String = when {
        review == null -> "How did it go?"
        review.feedback == null -> "Get feedback"
        else -> "See feedback"
    }

    /** The four headed parts, in the design's order; a blank part is left out. */
    fun parts(feedback: Feedback): List<Pair<String, String>> = listOf(
        "AGAINST THE PLAN" to feedback.againstPlan,
        "WHAT THE NUMBERS SAY" to feedback.numbers,
        "FOR NEXT TIME" to feedback.nextTime,
        "THIS WEEK" to feedback.thisWeek,
    ).filter { it.second.isNotBlank() }

    fun privacyPlan(ceiling: Int): String =
        "Sends these answers, your last six weeks of sessions and your weight trend to OpenAI with your key. " +
            "One of today's $ceiling AI requests."

    fun privacyReview(ceiling: Int): String =
        "Sends this session, your words, your last six weeks of sessions and your weight trend to OpenAI with your key. " +
            "One of today's $ceiling AI requests."

    /** Design question 16. */
    fun failure(result: EstimateResult): String = when (result) {
        is EstimateResult.NoKey -> "No API key yet. Add one in settings."
        is EstimateResult.CeilingReached -> "You have used today's AI requests. Raise the daily limit in settings, or try tomorrow."
        is EstimateResult.Unreachable -> if (result.afterRefusal != null) {
            "Could not reach the model. Before that, the provider refused: ${result.afterRefusal}$STILL_HERE"
        } else {
            "Could not reach the model.$STILL_HERE"
        }
        is EstimateResult.Refused -> "The provider refused: ${result.detail}"
        is EstimateResult.Unreadable -> "The answer could not be understood."
        // Never a trainer call's (TrainerReply.Failed refuses both); worded rather than thrown, so a
        // screen can never crash on it.
        is EstimateResult.Proposed, is EstimateResult.AmountMissing -> "The answer could not be understood."
    }

    fun savedWithoutFeedback(result: EstimateResult): String =
        "Your words are saved. " + failure(result).removeSuffix(STILL_HERE) + " Get feedback is on the session's row."

    private fun number(value: Int): String = String.format(Locale.US, "%,d", value)
}
