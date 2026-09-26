package com.metaself.app.data.health

import com.metaself.app.data.day.MetaSelfDatabase
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What Settings shows about the health record (D65, D66), read fresh each time the screen asks.
 *
 * With nothing granted at all, [HealthRecordState.notAllowed] is left empty: the existing "Off. Allow
 * MetaSelf to read your steps and a long walk will add to that day's allowance" line and the Connect
 * button already say it, and naming all thirteen kinds on top would be noise. The rule itself is pure
 * — [HealthRecordState.from] — so it is tested without Room; this class only reads the rows.
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

        return HealthRecordState.from(days, earliest, sync, granted)
    }
}
