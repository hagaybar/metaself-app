package com.metaself.app.ui.settings

import com.metaself.app.data.backup.RestoreResult

/**
 * What the app says about saving and restoring.
 *
 * Everything here is a count. "Restored successfully" is a claim the owner cannot check; "restored
 * 400 meals and 50 weights" is one he can, by opening the app and looking.
 */
object BackupWording {

    fun saved(result: RestoreResult): String = "Saved ${record(result)} to the file."

    fun restored(result: RestoreResult): String = "Restored ${record(result)}."

    /**
     * Asked before anything is destroyed, and it names what will go.
     *
     * "Are you sure?" is not information. A restore replaces, and the owner is entitled to know
     * what he is replacing before he agrees to it. The health record, the trainer's plans and the
     * session reviews (D88), the weekly plans (D98), the weekly letters (D104), the conversation with the trainer
     * (D148) and the versions of what the trainer knows (D150) are each named when either side holds any, so a phone with none reads exactly as it did before they
     * existed.
     */
    fun confirmReplacing(here: RestoreResult, incoming: RestoreResult): String = buildString {
        val withHealth = hasHealth(here) || hasHealth(incoming)
        val withTrainer = hasTrainer(here) || hasTrainer(incoming)
        val withWeeks = hasWeeks(here) || hasWeeks(incoming)
        val withLetters = hasLetters(here) || hasLetters(incoming)
        val withTalk = hasTalk(here) || hasTalk(incoming)
        val withSheets = hasSheets(here) || hasSheets(incoming)
        append("This will delete ")
        append(record(here, withHealth, withTrainer, withWeeks, withLetters, withTalk, withSheets))
        append(" already on this phone, and put back ")
        append(record(incoming, withHealth, withTrainer, withWeeks, withLetters, withTalk, withSheets))
        append(" from the file.")
        if (here.meals == 0 && here.weights == 0 && !hasHealth(here) && !hasTrainer(here) && !hasWeeks(here) && !hasLetters(here) && !hasTalk(here) &&
            !hasSheets(here)
        ) {
            append(" There is nothing here to lose.")
        }
    }

    const val UNREADABLE = "That file could not be read. Nothing on this phone has been changed."

    const val COULD_NOT_WRITE = "The file could not be written. Nothing has been saved."

    const val KEY_NOT_INCLUDED =
        "Your API key is deliberately not in the file. After restoring, paste it again."

    private fun meals(count: Int): String = if (count == 1) "1 meal" else "$count meals"

    private fun weights(count: Int): String = if (count == 1) "1 weight" else "$count weights"

    private fun hasHealth(result: RestoreResult): Boolean =
        result.workouts > 0 || result.healthDays > 0

    private fun hasTrainer(result: RestoreResult): Boolean =
        result.trainerPlans > 0 || result.trainerReviews > 0

    private fun hasWeeks(result: RestoreResult): Boolean = result.trainerProgrammes > 0

    private fun hasLetters(result: RestoreResult): Boolean = result.weeklyLetters > 0

    private fun hasTalk(result: RestoreResult): Boolean = result.trainerMessages > 0

    private fun hasSheets(result: RestoreResult): Boolean = result.trainerSheets > 0

    /**
     * "400 meals and 50 weights", or with the health record, "…, 12 workouts and 30 days of health
     * data", with the trainer's record, "…, 3 trainer plans and 2 session reviews", and with the weekly
     * plans, "…, 2 session reviews and 1 weekly plan", and with the weekly letters, "…, 1 weekly plan
     * and 2 weekly letters", with the conversation, "…, 2 weekly letters and 6 messages with the trainer", and with the
     * sheet, "…, 6 messages with the trainer and 3 versions of what the trainer knows" (every count invented).
     */
    private fun record(
        result: RestoreResult,
        withHealth: Boolean = hasHealth(result),
        withTrainer: Boolean = hasTrainer(result),
        withWeeks: Boolean = hasWeeks(result),
        withLetters: Boolean = hasLetters(result),
        withTalk: Boolean = hasTalk(result),
        withSheets: Boolean = hasSheets(result),
    ): String {
        val parts = listOf(meals(result.meals), weights(result.weights)) +
            (if (withHealth) listOf(workouts(result.workouts), healthDays(result.healthDays)) else emptyList()) +
            (if (withTrainer) listOf(trainerPlans(result.trainerPlans), sessionReviews(result.trainerReviews)) else emptyList()) +
            (if (withWeeks) listOf(weeklyPlans(result.trainerProgrammes)) else emptyList()) +
            (if (withLetters) listOf(weeklyLetters(result.weeklyLetters)) else emptyList()) +
            (if (withTalk) listOf(messages(result.trainerMessages)) else emptyList()) +
            if (withSheets) listOf(sheets(result.trainerSheets)) else emptyList()
        return parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    private fun trainerPlans(count: Int): String = if (count == 1) "1 trainer plan" else "$count trainer plans"

    private fun sessionReviews(count: Int): String = if (count == 1) "1 session review" else "$count session reviews"

    private fun weeklyPlans(count: Int): String = if (count == 1) "1 weekly plan" else "$count weekly plans"

    private fun weeklyLetters(count: Int): String = if (count == 1) "1 weekly letter" else "$count weekly letters"

    private fun messages(count: Int): String = if (count == 1) "1 message with the trainer" else "$count messages with the trainer"

    private fun sheets(count: Int): String =
        if (count == 1) "1 version of what the trainer knows" else "$count versions of what the trainer knows"

    private fun workouts(count: Int): String = if (count == 1) "1 workout" else "$count workouts"

    private fun healthDays(count: Int): String =
        if (count == 1) "1 day of health data" else "$count days of health data"
}
