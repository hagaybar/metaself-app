package com.metaself.app.data.trainer

import com.metaself.app.data.ai.TrainerResponse
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The trainer's weekly plans (D98). Apart from [TrainerStore] on purpose: the two change for different
 * reasons, and the single-session plans' fakes need not grow.
 */
interface ProgrammeStore {
    /** The running plan, as stored; null when none runs, or when it cannot be read. */
    fun observeRunning(): Flow<Programme?>
    suspend fun running(): Programme?

    /** Every readable row, for D98's last evaluation and "See the plan"'s evaluation. */
    suspend fun all(): List<Programme>

    /** Stores a new answer, OFFERED; its id. */
    suspend fun add(programme: Programme): Long

    /** D94: [id] runs from [startEpochDay]; any other running plan stops [today], replaced. One transaction. */
    suspend fun keep(id: Long, startEpochDay: Long, today: Long)

    /**
     * D97: [oldId] stops [today], adjusted, and [newId] runs from its start. Refused (throws) when [oldId]
     * is no longer running. One transaction.
     */
    suspend fun keepAdjusted(newId: Long, oldId: Long, today: Long)

    /** D97: [id] stops [today]. */
    suspend fun stop(id: Long, today: Long)
}

class RoomProgrammeStore @Inject constructor(
    private val dao: TrainerDao,
    private val transaction: DatabaseTransaction,
) : ProgrammeStore {

    override fun observeRunning(): Flow<Programme?> = dao.observeRunning().map { it?.toProgramme() }
    override suspend fun running(): Programme? = dao.running()?.toProgramme()
    override suspend fun all(): List<Programme> = dao.allProgrammes().mapNotNull { it.toProgramme() }

    override suspend fun add(programme: Programme): Long = dao.insertProgramme(
        programme.copy(id = 0, status = ProgrammeStatus.OFFERED, startEpochDay = null, stoppedEpochDay = null).toEntity(),
    )

    override suspend fun keep(id: Long, startEpochDay: Long, today: Long) = transaction.run {
        dao.replaceRunning(id, today)
        dao.runProgramme(id, startEpochDay)
    }

    override suspend fun keepAdjusted(newId: Long, oldId: Long, today: Long) = transaction.run {
        val old = dao.programme(oldId)
        val start = old?.startEpochDay
        check(old != null && old.status == ProgrammeStatus.RUNNING.name && start != null) {
            "the plan being adjusted is no longer running"
        }
        dao.endProgramme(oldId, ProgrammeStatus.ADJUSTED.name, today)
        dao.runProgramme(newId, start)
    }

    override suspend fun stop(id: Long, today: Long) = dao.endProgramme(id, ProgrammeStatus.STOPPED.name, today)
}

/** Null when this version cannot read the row: such a plan is offered nowhere and sent nowhere. */
fun TrainerProgrammeEntity.toProgramme(): Programme? {
    val ask = runCatching { ProgrammeAsk(weeks, perWeek, words.orEmpty()) }.getOrNull() ?: return null
    val readPlan = TrainerResponse.readWeeksPlan(plan) ?: return null
    val readEvaluation = if (evaluation == null) null else TrainerResponse.readEvaluation(evaluation) ?: return null
    val readStatus = ProgrammeStatus.entries.firstOrNull { it.name == status } ?: return null
    return Programme(id, createdAtMillis, ask, readEvaluation, readPlan, model, startEpochDay, readStatus, stoppedEpochDay, replacesId)
}

fun Programme.toEntity(): TrainerProgrammeEntity = TrainerProgrammeEntity(
    id = id, createdAtMillis = createdAtMillis, weeks = ask.weeks, perWeek = ask.perWeek,
    words = ask.words.trim().ifEmpty { null }, evaluation = evaluation?.let(TrainerResponse::encodeEvaluation),
    plan = TrainerResponse.encodeWeeksPlan(plan), model = model, startEpochDay = startEpochDay,
    status = status.name, stoppedEpochDay = stoppedEpochDay, replacesId = replacesId,
)
