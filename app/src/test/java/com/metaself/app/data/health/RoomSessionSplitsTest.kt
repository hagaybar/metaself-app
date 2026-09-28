package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.movement.SessionWitnesses
import com.metaself.app.domain.movement.aSyncedWorkout
import com.metaself.app.domain.movement.aTypedWorkout
import com.metaself.app.domain.movement.at
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** D92: "These are two sessions" stores the lead against each other witness. Every session is invented. */
class RoomSessionSplitsTest {

    private val dao = InMemorySplitDao()
    private val splits = RoomSessionSplits(dao)

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

        splits.split(session)

        assertThat(dao.rows.value).containsExactly(SessionSplitEntity(2, 5), SessionSplitEntity(5, 9))
    }

    @Test
    fun `a session recorded once stores nothing`() = runTest {
        splits.split(aSyncedWorkout(id = 1))

        assertThat(dao.rows.value).isEmpty()
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
}
