package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.WeeksPlan
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.Proxy

/**
 * How [RoomProgrammeStore] talks to its table, with no database: which writes share a transaction, and
 * how a stored row reads back. What the table then does is `HealthRecordStoreTest`'s (CI only). Every
 * figure and word is invented.
 */
class RoomProgrammeStoreWritesTest {

    private val log = mutableListOf<String>()
    private val stored = mutableMapOf<Long, TrainerProgrammeEntity>()

    /** What the guarded updates (`runProgramme`, `stopRunning`) report as changed. */
    private var changed = 1

    @Test
    fun `keeping a plan replaces the running one and starts this one, in one transaction`() = runTest {
        store().keep(id = 4, startEpochDay = 20_696, today = 20_699)

        assertThat(log).containsExactly("begin", "replaceRunning(4, 20699)", "runProgramme(4, 20696)", "commit").inOrder()
    }

    @Test
    fun `keeping a row that is not an offered answer is refused, and the replacement rolls back`() = runTest {
        changed = 0

        assertThrows<IllegalStateException> { store().keep(id = 4, startEpochDay = 20_696, today = 20_699) }
        assertThat(log).containsExactly("begin", "replaceRunning(4, 20699)", "runProgramme(4, 20696)", "rollback").inOrder()
    }

    @Test
    fun `keeping an adjusted version ends the old one as adjusted and runs the new one from the old start`() = runTest {
        running(3)
        version(5, replacesId = 3)

        store().keepAdjusted(newId = 5, oldId = 3, today = 20_699)

        assertThat(log).containsExactly(
            "begin", "programme(3)", "programme(5)", "endProgramme(3, ADJUSTED, 20699)", "runProgramme(5, 20696)", "commit",
        ).inOrder()
    }

    @Test
    fun `an adjusted version of a plan no longer running is refused, and nothing is written`() = runTest {
        stored[3] = PROGRAMME.toEntity().copy(id = 3, status = "STOPPED", startEpochDay = 20_696)
        version(5, replacesId = 3)

        assertThrows<IllegalStateException> { store().keepAdjusted(newId = 5, oldId = 3, today = 20_699) }
        assertThat(writes()).isEmpty()
    }

    @Test
    fun `an adjusted version made from another plan, already kept, or missing is refused, and nothing is written`() = runTest {
        running(3)
        version(5, replacesId = 2)
        stored[6] = PROGRAMME.toEntity().copy(id = 6, status = "REPLACED", startEpochDay = 20_696, replacesId = 3)

        listOf(5L, 6L, 9L).forEach { newId ->
            assertThrows<IllegalStateException> { store().keepAdjusted(newId = newId, oldId = 3, today = 20_699) }
        }
        assertThat(writes()).isEmpty()
    }

    @Test
    fun `stopping is one guarded write with today's date`() = runTest {
        store().stop(id = 4, today = 20_699)

        assertThat(log).containsExactly("stopRunning(4, 20699)")
    }

    @Test
    fun `stopping a plan no longer running is refused`() = runTest {
        changed = 0

        assertThrows<IllegalStateException> { store().stop(id = 4, today = 20_699) }
    }

    @Test
    fun `a row reads back as it was written, and an unreadable one as nothing`() {
        val row = PROGRAMME.copy(id = 7, status = ProgrammeStatus.RUNNING, startEpochDay = 20_696).toEntity()

        assertThat(row.toProgramme()).isEqualTo(PROGRAMME.copy(id = 7, status = ProgrammeStatus.RUNNING, startEpochDay = 20_696))
        assertThat(row.copy(plan = "not json").toProgramme()).isNull()
        assertThat(row.copy(status = "SOMETHING_NEW").toProgramme()).isNull()
        assertThat(row.copy(weeks = 3).toProgramme()).isNull()
    }

    private fun store() = RoomProgrammeStore(dao(), Transaction())

    private fun running(id: Long) {
        stored[id] = PROGRAMME.toEntity().copy(id = id, status = "RUNNING", startEpochDay = 20_696)
    }

    private fun version(id: Long, replacesId: Long) {
        stored[id] = PROGRAMME.toEntity().copy(id = id, evaluation = null, replacesId = replacesId)
    }

    private fun writes() = log.filter { it.startsWith("endProgramme") || it.startsWith("runProgramme") }

    private inner class Transaction : DatabaseTransaction {
        override suspend fun run(block: suspend () -> Unit) {
            log += "begin"
            try {
                block()
            } catch (e: Throwable) {
                log += "rollback"
                throw e
            }
            log += "commit"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun dao(): TrainerDao =
        Proxy.newProxyInstance(TrainerDao::class.java.classLoader, arrayOf(TrainerDao::class.java)) { _, method, args ->
            when (method.name) {
                "toString" -> "TrainerDao"
                "hashCode" -> 0
                "equals" -> false
                "programme" -> {
                    log += "programme(${args[0]})"
                    stored[args[0] as Long]
                }
                else -> {
                    // A suspending call's last argument is its continuation.
                    log += method.name + "(" + args.dropLast(1).joinToString() + ")"
                    if (method.name == "runProgramme" || method.name == "stopRunning") changed else Unit
                }
            }
        } as TrainerDao

    private companion object {
        val PROGRAMME = Programme(
            id = 0, createdAtMillis = 1_000, ask = ProgrammeAsk(2, 2, "Invented."),
            evaluation = com.metaself.app.domain.trainer.Evaluation("Invented.", "Invented.", "Invented.", ""),
            plan = WeeksPlan(
                "Invented",
                List(2) { PlanWeek("w", listOf(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk"))) },
                "Invented.",
            ),
            model = "a-model",
        )
    }
}
