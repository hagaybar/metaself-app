package com.metaself.app.ui

import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog

/** A problem log that keeps what it is told, so a test can see that a failure was written down. */
class RecordingProblemLog : ProblemLog {

    val recorded = mutableListOf<Problem>()

    override fun recent(): List<Problem> = recorded.reversed()

    override fun record(kind: String, detail: String) {
        recorded += Problem(whenMillis = 0, kind = kind, detail = detail)
    }

    override fun clear() = recorded.clear()
}
