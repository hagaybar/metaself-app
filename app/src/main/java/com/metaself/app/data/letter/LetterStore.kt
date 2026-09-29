package com.metaself.app.data.letter

import com.metaself.app.data.ai.LetterResponse
import com.metaself.app.data.trainer.TrainerDao
import com.metaself.app.data.trainer.WeeklyLetterEntity
import com.metaself.app.domain.letter.WeeklyLetter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The weekly letters (D104). */
interface LetterStore {
    /** Newest first; a row this version cannot read is left out. */
    fun observeAll(): Flow<List<WeeklyLetter>>
    suspend fun of(weekMonday: Long): WeeklyLetter?

    /** Throws if the week already has one (one letter a week). */
    suspend fun add(letter: WeeklyLetter): Long

    /** Sets the time the letter was first read; a later call leaves it. */
    suspend fun markRead(weekMonday: Long, atMillis: Long)
}

class RoomLetterStore @Inject constructor(private val dao: TrainerDao) : LetterStore {
    override fun observeAll(): Flow<List<WeeklyLetter>> = dao.observeLetters().map { rows -> rows.mapNotNull { it.toLetter() } }
    override suspend fun of(weekMonday: Long): WeeklyLetter? = dao.letterOf(weekMonday)?.toLetter()
    override suspend fun add(letter: WeeklyLetter): Long = dao.insertLetter(letter.toEntity())
    override suspend fun markRead(weekMonday: Long, atMillis: Long) = dao.markLetterRead(weekMonday, atMillis)
}

/** Null when the figures or the texts are not a shape this version reads. */
fun WeeklyLetterEntity.toLetter(): WeeklyLetter? {
    val figures = LetterCodec.read(figures) ?: return null
    val texts = LetterResponse.read(letter) ?: return null
    return WeeklyLetter(id, weekMonday, createdAtMillis, figures, texts, model, bandDataUntil, readAtMillis)
}

/** Always id 0: the table numbers it. */
fun WeeklyLetter.toEntity() = WeeklyLetterEntity(
    id = 0, weekMonday = weekMonday, createdAtMillis = createdAtMillis, figures = LetterCodec.encode(figures),
    letter = LetterResponse.encode(texts), model = model, bandDataUntil = bandDataUntil, readAtMillis = readAtMillis,
)
