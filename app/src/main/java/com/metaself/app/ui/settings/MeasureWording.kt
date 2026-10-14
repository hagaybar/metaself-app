package com.metaself.app.ui.settings

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** D152 (temporary): the words of Measure the app for 3 days, on Recent problems. Pure. */
object MeasureWording {

    const val TITLE = "Measure the app for 3 days"

    const val WHAT = "While this is on, the app writes down how long its own work takes — reading and saving " +
        "its records, copying from Health Connect, opening the day, Add something and the Trainer page, " +
        "and each Log — as timings and counts only. No food name, weight, heart rate, message or any other " +
        "value from your records is written. It stays on this phone, in a file of at most 5 MB, until you " +
        "download it, and is never sent anywhere. It turns itself off after 3 days."

    const val DOWNLOAD = "Download diagnostics"

    const val DOWNLOAD_NOTE = "Saves the measurements where you choose, with the phone's model, its Android " +
        "version, how many hours were measured and how many rows each of the app's tables holds — never what is in them."

    const val SAVED = "Saved."
    const val NOT_SAVED = "Could not save the file. Nothing was changed."

    private val UNTIL = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

    /** "On until …" while on; "Off." otherwise. */
    fun status(untilMillis: Long?, zone: ZoneId): String =
        if (untilMillis == null) {
            "Off."
        } else {
            "On until ${UNTIL.format(Instant.ofEpochMilli(untilMillis).atZone(zone))}; it turns itself off then."
        }
}
