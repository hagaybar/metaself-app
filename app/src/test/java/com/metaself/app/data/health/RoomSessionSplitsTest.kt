package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.time.Now
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.SessionSplit
import com.metaself.app.domain.movement.SessionWitnesses
import com.metaself.app.domain.movement.aSyncedWorkout
import com.metaself.app.domain.movement.aTypedWorkout
import com.metaself.app.domain.movement.at
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/** D92: "These are two sessions" stores the lead against each other witness. Every session is invented. */
class RoomSessionSplitsTest {

    private val dao = InMemorySplitDao()
    private val summarised = mutableListOf<Set<Long>>()
    private val log = mutableListOf<String>()
    private val transaction = object : DatabaseTransaction {
        override suspend fun run(block: suspend () -> Unit) {
            log += "begin"
            block()
            log += "commit"
        }
    }
    private val rows = mapOf(
        2L to aSyncedWorkout(id = 2).toTypedEntity().copy(epochDay = TEST_EPOCH_DAY - 1),
        5L to aSyncedWorkout(id = 5).toTypedEntity(),
    )
    private val splits = RoomSessionSplits(dao, transaction, summarisingStore(), Now { 1_000 }, workoutsById(rows))

    /** Records each summarise, and says whether it ran inside the transaction. */
    private fun summarisingStore(): HealthStore =
        Proxy.newProxyInstance(HealthStore::class.java.classLoader, arrayOf(HealthStore::class.java)) { _, method, args ->
            if (method.name != "summarise") throw UnsupportedOperationException("not in this test: ${method.name}")
            @Suppress("UNCHECKED_CAST")
            summarised += args[0] as Set<Long>
            log += "summarise"
            Unit
        } as HealthStore

    private fun workoutsById(rows: Map<Long, WorkoutEntity>): WorkoutDao =
        Proxy.newProxyInstance(WorkoutDao::class.java.classLoader, arrayOf(WorkoutDao::class.java)) { _, method, args ->
            if (method.name != "byId") throw UnsupportedOperationException("not in this test: ${method.name}")
            rows[args[0] as Long]
        } as WorkoutDao

    @Test
    fun `splitting a session stores the lead against each other witness, lower id first`() = runTest {
        val session = SessionWitnesses.combine(
            listOf(
                aSyncedWorkout(id = 5, from = 0, minutes = 60),
                aSyncedWorkout(id = 2, from = 0, minutes = 50),
                aTypedWorkout(id = 9, startedAtMillis = at(0), minutes = 40),
            ),
            emptySet(),
        ).single()

        val written = splits.split(session)

        assertThat(dao.rows.value).containsExactly(SessionSplitEntity(2, 5), SessionSplitEntity(5, 9))
        assertThat(written).containsExactly(SessionSplit(2, 5), SessionSplit(5, 9))
        // The day's stored workout count follows the sessions (D92), in the same transaction as the write.
        assertThat(summarised).containsExactly(setOf(TEST_EPOCH_DAY))
        assertThat(log).containsExactly("begin", "summarise", "commit").inOrder()
    }

    @Test
    fun `putting a session back together removes only those splits, and summarises their days`() = runTest {
        dao.insertAll(listOf(SessionSplitEntity(2, 5), SessionSplitEntity(5, 9)))

        splits.join(listOf(SessionSplit(2, 5)))

        assertThat(dao.rows.value).containsExactly(SessionSplitEntity(5, 9))
        assertThat(summarised).containsExactly(setOf(TEST_EPOCH_DAY - 1, TEST_EPOCH_DAY))
    }

    @Test
    fun `a session recorded once stores nothing`() = runTest {
        assertThat(splits.split(aSyncedWorkout(id = 1))).isEmpty()

        assertThat(dao.rows.value).isEmpty()
        assertThat(summarised).isEmpty()
    }

    @Test
    fun `a stored row that is not lower id first is read as nothing rather than breaking the read`() {
        assertThat(SessionSplitEntity(5, 2).toSplit()).isNull()
        assertThat(SessionSplitEntity(2, 5).toSplit()?.secondId).isEqualTo(5L)
    }
}

/** The splits table in memory; a pair already there is ignored, as the real insert does. */
class InMemorySplitDao(initial: List<SessionSplitEntity> = emptyList()) : SessionSplitDao {
    val rows = MutableStateFlow(initial)
    override fun observeAll(): Flow<List<SessionSplitEntity>> = rows
    override suspend fun all(): List<SessionSplitEntity> = rows.value
    override suspend fun insertAll(splits: List<SessionSplitEntity>) {
        rows.value = (rows.value + splits).distinct()
    }
    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
    override suspend fun delete(splits: List<SessionSplitEntity>) {
        rows.value = rows.value - splits.toSet()
    }
}
