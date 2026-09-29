package com.metaself.app.data.letter

import com.metaself.app.domain.letter.WeeklyLetter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In memory; like the table, one letter a week — a second for the same week throws. */
class FakeLetterStore(vararg letters: WeeklyLetter) : LetterStore {
    val letters = MutableStateFlow(letters.toList())
    private var nextId = (letters.maxOfOrNull { it.id } ?: 0) + 1

    override fun observeAll(): Flow<List<WeeklyLetter>> = letters.map { all -> all.sortedByDescending { it.weekMonday } }

    override suspend fun of(weekMonday: Long): WeeklyLetter? = letters.value.firstOrNull { it.weekMonday == weekMonday }

    override suspend fun add(letter: WeeklyLetter): Long {
        check(letters.value.none { it.weekMonday == letter.weekMonday }) { "the week already has a letter" }
        val id = nextId++
        letters.value = letters.value + letter.copy(id = id)
        return id
    }

    override suspend fun markRead(weekMonday: Long, atMillis: Long) {
        letters.value = letters.value.map {
            if (it.weekMonday == weekMonday && it.readAtMillis == null) it.copy(readAtMillis = atMillis) else it
        }
    }
}
