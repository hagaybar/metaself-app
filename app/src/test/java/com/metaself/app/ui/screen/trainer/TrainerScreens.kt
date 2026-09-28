package com.metaself.app.ui.screen.trainer

import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.Wish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * What the trainer screens' tests share. Today is TEST_EPOCH_DAY (Thursday 3 September 2026) and now
 * is 15:00 that day, UTC. Every figure, answer and word is invented.
 */
internal object TrainerScreens {
    const val HOUR = 3_600_000L
    const val DAY = 86_400_000L
    const val NOW = TEST_EPOCH_DAY * DAY + 15 * HOUR

    val ANSWERS = PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE, "Invented words.")

    val PLAN = SessionPlan(
        "Steady walk",
        listOf(PlanStep(0, 10, "Warm up", "easy pace"), PlanStep(10, 35, "Walk", "zone 2"), PlanStep(35, 45, "Cool down", "")),
        "Invented reason.",
    )

    val FEEDBACK = Feedback("Invented headline.", "Invented plan part.", "Invented numbers part.", "Invented next part.", "Invented week part.", PlanFollowed.YES)

    fun storedPlan(createdAt: Long, kept: Boolean = true) = TrainerPlan(0, createdAt, ANSWERS, PLAN, "a-model", kept)

    /** A synced forty-minute walk at 07:00 on [day], 3 km, heart 110 average. */
    fun walk(id: Long, day: Long = TEST_EPOCH_DAY) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 7 * HOUR, durationMinutes = 40,
        kind = WorkoutKind.WALK, title = null, distanceM = 3_000, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
        avgHeartRate = 110,
    )

    val today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }

    fun ask(record: FakeMovementRecord, store: FakeTrainerStore, trainer: Trainer) = AskTheTrainer(
        record, store, InMemoryWeightRepository(), FakeProfileRepository(aProfile()), trainer,
        today, Now { NOW }, CurrentYear { TEST_YEAR },
    )
}

/** AI settings in memory: the default ceiling unless a test sets another. */
internal class FakeAiSettings(initial: AiSettings = AiSettings()) : AiSettingsStore {
    val current = MutableStateFlow(initial)
    override val settings: Flow<AiSettings> = current
    override suspend fun setModel(model: String) = current.update { it.copy(model = model) }
    override suspend fun setDailyCeiling(ceiling: Int) = current.update { it.copy(dailyCeiling = ceiling) }
    override suspend fun recordCall() = current.update { it.copy(usedToday = it.usedToday + 1) }

    private fun MutableStateFlow<AiSettings>.update(change: (AiSettings) -> AiSettings) {
        value = change(value)
    }
}
