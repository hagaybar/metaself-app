package com.metaself.app.data.letter

import androidx.work.WorkInfo
import java.time.LocalDate

/** The Sunday run's names and when a queued one is left alone (design question 4). Pure. */
object LetterWork {

    val NAMES = listOf("weekly-letter-even", "weekly-letter-odd")

    /** The input a run carries: the Monday of the week it writes. */
    const val WEEK = "week_monday"

    /**
     * The name of the run for [sunday], by that Sunday's week parity: consecutive Sundays alternate, so a
     * run queueing its successor never replaces itself (`REPLACE` would cancel it, running or not).
     */
    fun nameFor(sunday: LocalDate): String = NAMES[Math.floorMod(Math.floorDiv(sunday.toEpochDay(), 7L), 2L).toInt()]

    /**
     * Whether a run already under that name is in progress — running, or waiting to retry — and must be
     * kept rather than replaced: the hour changed under a Sunday run that is still trying, say.
     */
    fun inProgress(state: WorkInfo.State, runAttemptCount: Int): Boolean =
        state == WorkInfo.State.RUNNING || (state == WorkInfo.State.ENQUEUED && runAttemptCount > 0)
}
