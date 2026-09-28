package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.TrainerReview
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * How [RoomTrainerStore] talks to its table, with no database: what runs inside a transaction, and
 * how many ids one query is given. What the table then does is `HealthRecordStoreTest`'s (CI only).
 */
class RoomTrainerStoreWritesTest {

    private val log = mutableListOf<String>()
    private val planQueries = mutableListOf<List<Long>>()
    private var existing: TrainerReviewEntity? = null

    /** Read, then insert or update: two statements that must not have another write between them. */
    @Test
    fun `a review is read and written in one transaction`() = runTest {
        store().putReview(TrainerReview(workoutId = 4, planId = null, felt = Felt.RIGHT, words = "Invented."))

        assertThat(log).containsExactly("begin", "reviewOf", "insertReview", "commit").inOrder()
    }

    @Test
    fun `a review that is there is updated in the same transaction, keeping its id`() = runTest {
        existing = TrainerReviewEntity(9, 4, null, null, "Before.", null, null, null)

        val id = store().putReview(TrainerReview(workoutId = 4, planId = null, felt = null, words = "After."))

        assertThat(id).isEqualTo(9L)
        assertThat(log).containsExactly("begin", "reviewOf", "updateReview", "commit").inOrder()
    }

    /** An old SQLite allows 999 parameters in one statement; the ids go 500 at a time. */
    @Test
    fun `many plan ids are asked for a few hundred at a time`() = runTest {
        store().plans((1L..1_200L).toList() + 5L)

        assertThat(planQueries.map { it.size }).containsExactly(500, 500, 200).inOrder()
        assertThat(planQueries.flatten()).containsExactlyElementsIn(1L..1_200L)
    }

    private fun store() = RoomTrainerStore(dao(), Transaction())

    private inner class Transaction : DatabaseTransaction {
        override suspend fun run(block: suspend () -> Unit) {
            log += "begin"
            block()
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
                "plans" -> {
                    planQueries += args[0] as List<Long>
                    emptyList<TrainerPlanEntity>()
                }
                else -> {
                    log += method.name
                    when (method.name) {
                        "reviewOf" -> existing
                        "insertReview" -> 1L
                        else -> Unit
                    }
                }
            }
        } as TrainerDao
}
