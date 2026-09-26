package com.metaself.app.data.health

import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.domain.health.HealthKind
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What Settings shows about the health record (D65, D66), read fresh each time the screen asks.
 *
 * With nothing granted at all, [HealthRecordState.notAllowed] is left empty: the existing "Off. Allow
 * MetaSelf to read your health data" line and the Connect button already say it, and naming all
 * thirteen kinds on top would be noise.
 */
@Singleton
class RoomHealthRecordStatus @Inject constructor(
    private val database: MetaSelfDatabase,
    private val source: HealthSource,
) : HealthRecordStatus {

    override suspend fun current(): HealthRecordState {
        val days = database.healthDayDao().observeCount().first()
        val earliest = database.healthDayDao().observeEarliest().first()
        val sync = database.healthBookkeepingDao().observeSync().first()
        val granted = source.grantedKinds()

        val lastCopiedMillis = sync.mapNotNull { it.tokenAtMillis }.maxOrNull()
        val syncByKind = sync.associateBy { HealthKind.parse(it.kind) }
        val catchingUp = granted.any { kind -> syncByKind[kind]?.catchUpDone != true }
        val notAllowed = if (granted.isEmpty()) emptySet() else HealthKind.entries.toSet() - granted

        return HealthRecordState(
            days = days,
            earliest = earliest,
            lastCopiedMillis = lastCopiedMillis,
            catchingUp = catchingUp,
            notAllowed = notAllowed,
        )
    }
}
