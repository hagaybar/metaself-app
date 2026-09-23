package com.metaself.app.data.day

import androidx.room.withTransaction
import javax.inject.Inject

/**
 * One database transaction around [run]'s block: a throw out of it rolls back every write inside.
 *
 * An interface so the ORDER of what a caller writes inside and outside it can be tested on a machine
 * with no database; what rolling back undoes is only provable against the real one.
 *
 * **The block must not catch a Room failure.** Every repository here opens its own
 * `withTransaction`, which joins this one; a write that fails in one of those ends it unmarked, and
 * SQLite then rolls back the WHOLE transaction when it closes, however the exception was handled —
 * so a caught failure turns into a commit reported for nothing stored.
 */
interface DatabaseTransaction {
    suspend fun run(block: suspend () -> Unit)
}

class RoomDatabaseTransaction @Inject constructor(
    private val database: MetaSelfDatabase,
) : DatabaseTransaction {
    override suspend fun run(block: suspend () -> Unit) {
        database.withTransaction { block() }
    }
}
