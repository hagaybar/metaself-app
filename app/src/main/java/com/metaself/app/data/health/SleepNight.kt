package com.metaself.app.data.health

import androidx.room.Embedded
import androidx.room.Relation

/** A night with its stages, read in one query so the two can never disagree. */
data class SleepNight(
    @Embedded val session: SleepSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val stages: List<SleepStageEntity>,
)
