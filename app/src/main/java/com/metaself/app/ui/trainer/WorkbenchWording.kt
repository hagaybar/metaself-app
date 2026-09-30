package com.metaself.app.ui.trainer

import com.metaself.app.domain.trainer.TrainerPath

/** Every sentence on "Test the trainer's instructions" (D106). */
object WorkbenchWording {
    const val INTRO = "A testing tool: try other instructions on your real record. Nothing here is stored, " +
        "and the trainer's own screens are unchanged."
    const val APP_OWN = "the app's own instructions"
    const val LOAD = "Load instructions from a file"
    const val USE_APP_OWN = "Use the app's own instructions"
    const val SAVE_APP_OWN = "Save the app's instructions to a file"
    const val SEND = "Send"
    const val SENDING = "Sending…"
    const val PRIVACY = "On Send, what the chosen path sends goes to OpenAI with your key, under the instructions " +
        "named above. It counts as one of today's AI requests. Nothing is kept."
    const val COPY_REPLY = "Copy reply"
    const val COPY_SENT = "Copy what was sent"
    const val NO_PLAN = "No plan is running."
    const val NO_SESSIONS = "No sessions in the last 42 days."
    const val SESSION_GONE = "That session is no longer in the record."
    const val RECORD_UNREADABLE = "The record could not be read. Nothing was sent."

    /** On opening: the sessions or whether a plan runs could not be read, so the list and the plan line may be wrong. */
    const val RECORD_NOT_READ = "The record could not be read, so the sessions and the plan shown here may be incomplete."
    const val COULD_NOT_READ = "The file could not be read, or is empty. Nothing changed."
    const val UNNAMED = "the file you picked"
    const val SAVED = "Saved."
    const val COULD_NOT_WRITE = "The file could not be written."

    fun path(path: TrainerPath): String = when (path) {
        TrainerPath.FEEDBACK -> "Feedback on a session"
        TrainerPath.PLAN -> "Plan my next session"
        TrainerPath.EVALUATE -> "Review and plan the weeks ahead"
        TrainerPath.ADJUST -> "Change the rest of my plan"
    }

    /** The line that always says which instructions Send will use. */
    fun willSend(fileName: String?): String = "Will send: ${fileName ?: APP_OWN}"

    /** The suggested name when saving the app's instructions for [path]. */
    fun fileName(path: TrainerPath): String = "trainer-instructions-${path.name.lowercase()}.txt"
}
