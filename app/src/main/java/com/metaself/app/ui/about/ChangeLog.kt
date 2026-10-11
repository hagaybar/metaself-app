package com.metaself.app.ui.about

import java.time.LocalDate

/** One version in the change log: its number, the day it was merged, and what it did, in one sentence. */
data class ChangeLogEntry(
    val version: String,
    val date: LocalDate,
    val sentence: String,
)

/**
 * What each version changed, newest first (D134) — drawn by About this app.
 *
 * **Every version bump adds its entry at the top, in the words its release was announced in.**
 * `ChangeLogTest` fails while the newest entry is not the app's own versionName, so a release cannot
 * ship without one. An entry says what changed on screen or in behaviour: never a decision number, an
 * issue number or a code name. The log starts at 0.67.0.
 */
object ChangeLog {

    val entries: List<ChangeLogEntry> = listOf(
        entry("0.84.0", 10, 11, "The weekly letter is written like a coach: what you did and what it took first, then one thing to look after."),
        entry("0.83.1", 10, 11, "The 12-week average and comparison leave out the week still under way."),
        entry("0.83.0", 10, 11, "A workout file shared before its session reaches the phone now waits and fills it in later; Recent problems lists only real problems."),
        entry("0.82.0", 10, 11, "Your movement shows Week, 12 weeks and Year, as charts you can swipe through."),
        entry("0.81.0", 10, 11, "Your numbers moved into Settings, and this page says what each version changed."),
        entry("0.80.0", 10, 11, "Movement moved under the Trainer, with Log a workout on the Trainer page."),
        entry("0.79.0", 10, 11, "Suggested goals, a goal of more time each week, and the trainer says when a goal is too big."),
        entry("0.78.0", 10, 11, "Goals, four-week blocks and the Month view; weeks are filled in as they come."),
        entry("0.77.0", 10, 10, "Every planned session in one place, with its steps."),
        entry("0.76.0", 10, 10, "The plan is drawn as a timeline of weeks."),
        entry("0.75.0", 10, 10, "The Trainer page shows the week: a chart of sessions, steps, sleep, food and the coach's note."),
        entry("0.74.0", 10, 10, "The trainer sees everything you did, not only planned sessions, and your sleep and food."),
        entry("0.73.0", 10, 10, "A fast that fell short says so, and when to finish eating."),
        entry("0.72.0", 9, 30, "Feedback after a session reads as a coach's note."),
        entry("0.71.0", 9, 30, "A page in Settings to test the trainer's instructions."),
        entry("0.70.0", 9, 29, "The weekly letter."),
        entry("0.69.0", 9, 29, "A plan counts from the day it is kept."),
        entry("0.68.0", 9, 29, "The trainer evaluates and plans the weeks ahead."),
        entry("0.67.0", 9, 29, "The weight chart fills the screen sideways."),
    )

    /** Positive when [a] is the later version: "0.100.0" is after "0.99.0", which a string comparison gets wrong. */
    fun compare(a: String, b: String): Int {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    private fun parts(version: String): List<Int> = version.split('.').map { it.toInt() }

    private fun entry(version: String, month: Int, day: Int, sentence: String) =
        ChangeLogEntry(version, LocalDate.of(2026, month, day), sentence)
}
