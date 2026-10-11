package com.metaself.app.ui.about

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The lines of About this app, and its row on the Settings index (D134).
 *
 * The build number is said beside the version because two builds can share a version name, and that
 * is exactly when it matters which one is on the phone. Pure, so every line is pinned by a test.
 */
object AboutWording {

    /** "MetaSelf 0.81.0" */
    fun title(versionName: String): String = "MetaSelf $versionName"

    /** "Build 137 · installed version" */
    fun build(versionCode: Int): String = "Build $versionCode · installed version"

    /** "0.81.0 · 11 Oct" — the year is stored, not said: the log is weeks long, not years. */
    fun heading(entry: ChangeLogEntry): String = "${entry.version} · ${entry.date.format(DAY)}"

    /** The About row's status line on the Settings index: "Version 0.81.0 · what changed". */
    fun indexLine(versionName: String): String = "Version $versionName · what changed"

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)
}
