package com.metaself.app.ui.settings

import com.metaself.app.data.backup.RestoreResult

/**
 * What the app says about saving and restoring.
 *
 * Everything here is a count. "Restored successfully" is a claim the owner cannot check; "restored
 * 400 meals and 50 weights" is one he can, by opening the app and looking.
 */
object BackupWording {

    fun saved(result: RestoreResult): String =
        "Saved ${meals(result.meals)} and ${weights(result.weights)} to the file."

    fun restored(result: RestoreResult): String =
        "Restored ${meals(result.meals)} and ${weights(result.weights)}."

    /**
     * Asked before anything is destroyed, and it names what will go.
     *
     * "Are you sure?" is not information. A restore replaces, and the owner is entitled to know
     * what he is replacing before he agrees to it.
     */
    fun confirmReplacing(here: RestoreResult, incoming: RestoreResult): String = buildString {
        append("This will delete ")
        append(meals(here.meals))
        append(" and ")
        append(weights(here.weights))
        append(" already on this phone, and put back ")
        append(meals(incoming.meals))
        append(" and ")
        append(weights(incoming.weights))
        append(" from the file.")
        if (here.meals == 0 && here.weights == 0) {
            append(" There is nothing here to lose.")
        }
    }

    const val UNREADABLE = "That file could not be read. Nothing on this phone has been changed."

    const val COULD_NOT_WRITE = "The file could not be written. Nothing has been saved."

    const val KEY_NOT_INCLUDED =
        "Your API key is deliberately not in the file. After restoring, paste it again."

    private fun meals(count: Int): String = if (count == 1) "1 meal" else "$count meals"

    private fun weights(count: Int): String = if (count == 1) "1 weight" else "$count weights"
}
