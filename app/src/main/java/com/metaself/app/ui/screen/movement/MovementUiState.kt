package com.metaself.app.ui.screen.movement

import com.metaself.app.domain.movement.MovementWeek

/**
 * @property week null until the first read has answered, and when it failed.
 * @property openDay the one open day (D73), which shows its detail beneath its summary; null when the
 *   owner has closed every day.
 * @property unreadable the record could not be read; the screen says so (D8).
 */
data class MovementUiState(
    val week: MovementWeek? = null,
    val openDay: Long? = null,
    val unreadable: Boolean = false,
)
