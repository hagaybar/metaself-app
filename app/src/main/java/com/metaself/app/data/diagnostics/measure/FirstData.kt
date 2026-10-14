package com.metaself.app.data.diagnostics.measure

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * D152: time from a screen's view model being made to its first data — one `screen` line, once.
 * After the first, each emission costs one read of a volatile field.
 */
class FirstData(private val recorder: MeasureRecorder, private val screen: String) {

    private val madeAt = recorder.clock.nanos()

    @Volatile
    private var seen = false

    /** [flow], noting its first value [isData] accepts. */
    fun <T> of(flow: Flow<T>, isData: (T) -> Boolean = { true }): Flow<T> = flow.onEach { value ->
        if (!seen && isData(value)) {
            seen = true
            val took = recorder.clock.nanos() - madeAt
            recorder.record("screen") {
                put("screen", screen)
                putMillis("first_data_ms", took)
            }
        }
    }
}
