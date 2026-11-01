package com.metaself.app.data.day

import com.metaself.app.domain.food.LastAmount
import com.metaself.app.domain.food.LoggedRow
import kotlinx.coroutines.flow.Flow

/** What the record says has been logged, for working out what to offer on Add something (D154, D155). */
interface LoggingHistory {

    /** Every row on a day from [fromEpochDay] on, each with the two facts about its meal. */
    fun observeRowsSince(fromEpochDay: Long): Flow<List<LoggedRow>>

    /** The last time each food was logged on its own: the only row of a meal not from a built meal. */
    fun observeLastAmounts(): Flow<List<LastAmount>>
}
