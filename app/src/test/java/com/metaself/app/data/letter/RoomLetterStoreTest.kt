package com.metaself.app.data.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.trainer.TrainerDao
import com.metaself.app.data.trainer.WeeklyLetterEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/**
 * How [RoomLetterStore] talks to its table, with no database. What the table then does (one letter a
 * week, read once) is `HealthRecordStoreTest`'s (CI only). Every figure and word is invented.
 */
class RoomLetterStoreTest {

    private val inserted = mutableListOf<WeeklyLetterEntity>()
    private var rows = listOf<WeeklyLetterEntity>()

    @Test
    fun `an added letter is inserted with no id of its own, and reads back equal`() = runTest {
        val letter = aWeeklyLetter().copy(id = 9, bandDataUntil = 2_000, readAtMillis = 3_000)

        RoomLetterStore(dao()).add(letter)

        assertThat(inserted.single().id).isEqualTo(0)
        rows = listOf(inserted.single().copy(id = 9))
        assertThat(RoomLetterStore(dao()).of(letter.weekMonday)).isEqualTo(letter)
    }

    @Test
    fun `a row whose figures or texts are unreadable is left out`() = runTest {
        val good = aWeeklyLetter().toEntity().copy(id = 1)
        rows = listOf(good, good.copy(id = 2, weekMonday = 1, figures = "{"), good.copy(id = 3, weekMonday = 2, letter = "{}"))

        assertThat(RoomLetterStore(dao()).observeAll().first().map { it.id }).containsExactly(1L)
        assertThat(RoomLetterStore(dao()).of(1)).isNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun dao(): TrainerDao =
        Proxy.newProxyInstance(TrainerDao::class.java.classLoader, arrayOf(TrainerDao::class.java)) { _, method, args ->
            when (method.name) {
                "toString" -> "TrainerDao"
                "hashCode" -> 0
                "equals" -> false
                "insertLetter" -> {
                    inserted += args[0] as WeeklyLetterEntity
                    1L
                }
                "letterOf" -> rows.firstOrNull { it.weekMonday == args[0] as Long }
                "observeLetters" -> flowOf(rows)
                else -> error("not expected: ${method.name}")
            }
        } as TrainerDao
}
