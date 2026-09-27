package com.metaself.app.ui.screen.movement

import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutDraft

/**
 * @property week null until the first read has answered, and when it failed.
 * @property openDay the one open day (D73), which shows its detail beneath its summary; null when the
 *   owner has closed every day.
 * @property unreadable the record could not be read; the screen says so (D8).
 * @property sheet the log-a-workout sheet, when it is open (D76).
 * @property canUndo a typed workout deleted here can still be put back — what the Undo line is drawn
 *   from, as on the weight and record screens. False whenever [unreadable] is true, whatever a delete
 *   left pending: the week cannot be shown, so nothing offers Undo on it, even though the deleted
 *   workout is still there to put back once the record reads again.
 * @property undoFailed the last Undo did not put the workout back; the screen says so, and Undo is
 *   still offered (D8).
 */
data class MovementUiState(
    val week: MovementWeek? = null,
    val openDay: Long? = null,
    val unreadable: Boolean = false,
    val sheet: WorkoutSheetState? = null,
    val canUndo: Boolean = false,
    val undoFailed: Boolean = false,
)

/**
 * The log-a-workout sheet (D76).
 *
 * @property epochDay the day the workout goes on: the open day when it was opened, or the typed
 *   workout's own day.
 * @property weightKg the profile's weight, which the estimate is priced on; null with no profile.
 * @property editing the typed workout being changed; null when logging a new one.
 * @property saving a write is under way; Save and Delete wait for it.
 * @property failure which write failed last, if one did; the sheet says so and keeps what was typed
 *   (D8).
 * @property token which sheet this is, distinct from every other one the view model has opened —
 *   never shown, never compared for anything but identity. A write started against one sheet only
 *   ever touches the state of that same sheet when it lands, so a slow write cannot mark, clear or
 *   fail a sheet opened after it, and a write whose sheet was since closed lands on nothing.
 */
data class WorkoutSheetState(
    val draft: WorkoutDraft,
    val epochDay: Long,
    val weightKg: Double?,
    val editing: Workout? = null,
    val saving: Boolean = false,
    val failure: WriteFailure? = null,
    val token: Long = 0,
) {
    val failed: Boolean get() = failure != null
}

/** Which of the sheet's writes failed: each is said in its own words ("Not saved", "Not deleted"). */
enum class WriteFailure { SAVE, DELETE }
