package com.metaself.app.data.trainer

import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [ProgrammeStore] in memory, with the Room store's rules. [failing] makes every write throw. */
class FakeProgrammeStore : ProgrammeStore {
    val rows = MutableStateFlow<List<Programme>>(emptyList())
    var failing = false
    private var nextId = 1L

    override fun observeRunning(): Flow<Programme?> = rows.map { all -> running(all) }
    override suspend fun running(): Programme? = running(rows.value)
    override suspend fun all(): List<Programme> = rows.value

    override suspend fun add(programme: Programme): Long {
        write()
        val id = nextId++
        rows.value = rows.value + programme.copy(id = id, status = ProgrammeStatus.OFFERED, startEpochDay = null, stoppedEpochDay = null)
        return id
    }

    override suspend fun keep(id: Long, startEpochDay: Long, today: Long) {
        write()
        check(rows.value.any { it.id == id && it.status == ProgrammeStatus.OFFERED }) { "only an offered plan can be kept" }
        rows.value = rows.value.map {
            when {
                it.id == id -> it.copy(status = ProgrammeStatus.RUNNING, startEpochDay = startEpochDay, stoppedEpochDay = null)
                it.status == ProgrammeStatus.RUNNING -> it.copy(status = ProgrammeStatus.REPLACED, stoppedEpochDay = today)
                else -> it
            }
        }
    }

    override suspend fun keepAdjusted(newId: Long, oldId: Long, today: Long) {
        write()
        val old = rows.value.firstOrNull { it.id == oldId }
        check(old != null && old.status == ProgrammeStatus.RUNNING && old.startEpochDay != null) {
            "the plan being adjusted is no longer running"
        }
        check(rows.value.any { it.id == newId && it.status == ProgrammeStatus.OFFERED && it.replacesId == oldId }) {
            "not an offered version of the plan being adjusted"
        }
        rows.value = rows.value.map {
            when (it.id) {
                oldId -> it.copy(status = ProgrammeStatus.ADJUSTED, stoppedEpochDay = today)
                newId -> it.copy(status = ProgrammeStatus.RUNNING, startEpochDay = old.startEpochDay)
                else -> it
            }
        }
    }

    override suspend fun stop(id: Long, today: Long) {
        write()
        check(rows.value.any { it.id == id && it.status == ProgrammeStatus.RUNNING }) { "the plan being stopped is no longer running" }
        rows.value = rows.value.map { if (it.id == id) it.copy(status = ProgrammeStatus.STOPPED, stoppedEpochDay = today) else it }
    }

    private fun running(all: List<Programme>) =
        all.filter { it.status == ProgrammeStatus.RUNNING }.maxByOrNull { it.createdAtMillis }

    private fun write() = check(!failing) { "disk full" }
}
