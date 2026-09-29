package com.metaself.app.ui.letter

import com.metaself.app.domain.letter.LetterFigures
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Every sentence of the weekly letter (D99, D102, D103). Numbers are grouped the US way, as the trainer's
 * are. Every figure in the box is the phone's own count (D4): nothing here reads the model's words.
 */
object LetterWording {
    const val CHANNEL = "Weekly letter"
    const val ARRIVED_TITLE = "Your week is in"
    const val FAILED_TITLE = "Your weekly letter couldn't be written"
    const val FAILED_TEXT = "Tap to try again."

    /** D4: first under the headline, before anything the model wrote. */
    const val FROM_TRAINER = "From the AI trainer · advice, not a measurement. The figures were counted on your phone."

    const val PAGE_TITLE = "Weekly letter"
    const val EFFORT = "WHAT YOU PUT IN"
    const val PROGRESS = "WHERE IT'S TAKING YOU"
    const val LOOK_AT = "ONE THING TO LOOK AT"
    const val NEXT_WEEK = "FOR NEXT WEEK"
    val HEADINGS = listOf(EFFORT, PROGRESS, LOOK_AT, NEXT_WEEK)

    const val BOX_TITLE = "THE WEEK"
    const val BOX_THIS_WEEK = "THIS WEEK"
    const val BOX_AVERAGE = "4-WEEK AVG"

    const val UNREADABLE = "This letter could not be read; Recent problems says why."
    const val MISSING = "This letter could not be opened."

    const val READ_IT = "Read it"
    const val DISMISS = "Got it"

    const val SETTING_TITLE = "Weekly letter"
    const val SETTING_HOUR = "Sunday at"
    const val ASK_BACKGROUND = "Allow Sunday's band data"
    const val BACKGROUND_REASON = "So Sunday's letter includes sessions from today."

    const val LIST_TITLE = "Weekly letters"
    const val WRITE_NOW = "Write it now"
    const val WRITING = "Writing…"
    const val EMPTY = "Your first letter comes on Sunday evening."
    const val QUIET = "Nothing was logged that week, so there is nothing to write."
    const val NEW = "New"

    private const val DASH = "—"
    private const val MINUS = "−"
    private const val METRES_PER_KM = 1_000.0
    private const val WHOLE_KM_FROM = 10_000

    /** "WEEKLY LETTER · 31 AUG – 6 SEP"; within one month "WEEKLY LETTER · 7–13 SEP". */
    fun weekLabel(monday: Long): String {
        val start = LocalDate.ofEpochDay(monday)
        val end = start.plusDays(6)
        val span = if (start.month == end.month) "${start.dayOfMonth}–${end.dayOfMonth} ${MONTH.format(end)}" else weekShort(monday)
        return "WEEKLY LETTER · " + span.uppercase(Locale.US)
    }

    /** "31 Aug – 6 Sep": the week in the list of letters. */
    fun weekShort(monday: Long): String {
        val start = LocalDate.ofEpochDay(monday)
        return "${DAY_MONTH.format(start)} – ${DAY_MONTH.format(start.plusDays(6))}"
    }

    fun dayNote(headline: String): String = "Your week is in: $headline"

    /** One row of the box: its name, this week's figure and the four weeks' average. */
    data class Row(val name: String, val thisWeek: String, val average: String)

    /** D100, D103: the box, in the spec's order; the Weekly plan row only when a plan ran this week. */
    fun rows(figures: LetterFigures): List<Row> {
        val week = figures.week
        val avg = figures.average
        val target = figures.targetKcal?.let(::number) ?: DASH
        return listOfNotNull(
            Row("Calories a day", week.food.kcal.or(::number), avg.kcal.or(::number)),
            Row("Target", target, target),
            Row("Protein a day", week.food.proteinG.or(::grams), avg.proteinG.or(::grams)),
            Row("Days logged", "${week.food.daysLogged} of 7", decimal(avg.daysLogged)),
            Row("Weight trend", weightChange(week.weightChangeKg), weightChange(avg.weightChangeKg)),
            Row("Sessions", week.movement.sessions.toString(), decimal(avg.sessions)),
            Row("Distance", distance(week.movement.distanceM), distance(avg.distanceM)),
            Row("Steps a day", week.movement.stepsADay.or(::number), avg.stepsADay.or(::number)),
            week.plan?.let { Row("Weekly plan", "${it.done} of ${it.planned}", DASH) },
        )
    }

    /** "−0.3 kg", "+0.2 kg", "0.0 kg" when it rounds to nothing; "—" for none. The minus is U+2212. */
    fun weightChange(kg: Double?): String {
        kg ?: return DASH
        val tenths = (kg * 10).roundToInt()
        val size = String.format(Locale.US, "%.1f kg", abs(tenths) / 10.0)
        return when {
            tenths < 0 -> MINUS + size
            tenths > 0 -> "+$size"
            else -> size
        }
    }

    /** "12 km" from ten kilometres; "4.5 km" under, "4 km" when whole; "—" for none. */
    fun distance(metres: Int?): String {
        metres ?: return DASH
        return if (metres >= WHOLE_KM_FROM) "${(metres / METRES_PER_KM).roundToInt()} km" else "${decimal(metres / METRES_PER_KM)} km"
    }

    /** "Calories and protein are averages over the 5 days you logged."; null with none logged. */
    fun foodNote(daysLogged: Int): String? = when (daysLogged) {
        0 -> null
        1 -> "Calories and protein are averages over the 1 day you logged."
        else -> "Calories and protein are averages over the $daysLogged days you logged."
    }

    /** D99: "Band data up to Sun 14:00." — the band's data may be behind what the letter says. */
    fun bandNote(untilMillis: Long, zone: ZoneId): String =
        "Band data up to ${DAY_TIME.format(Instant.ofEpochMilli(untilMillis).atZone(zone))}."

    const val SET_UP = "Set up"

    /**
     * The one-time note that sets the letter up (D99, D103): what it still needs — notifications, so it is
     * seen; the band's background read, with the setting's reason — or null when it needs nothing.
     */
    fun setupNote(hour: Int, notifications: Boolean, background: Boolean): String? {
        val arrives = "Your weekly letter arrives Sundays at ${hour(hour)}."
        val band = "allow Sunday's band data — so Sunday's letter includes sessions from today."
        return when {
            notifications && background -> "$arrives Allow notifications so you see it. Then $band"
            notifications -> "$arrives Allow notifications so you see it."
            background -> "$arrives " + band.replaceFirstChar { it.uppercase() }
            else -> null
        }
    }

    fun settingLine(on: Boolean, hour: Int): String = if (on) "Sundays at ${hour(hour)}" else "Off"

    fun hour(hour: Int): String = String.format(Locale.US, "%02d:00", hour)

    /** What Write it now sends, said beside it (D16, D101). */
    fun privacyLetter(ceiling: Int): String =
        "Sends to the AI provider, with your key: your week's food as averages a day, your weight trend, your " +
            "sessions and steps, your note about yourself, your goal, age, sex and height. Never a meal or a " +
            "food. One of today's $ceiling AI requests."

    private fun Int?.or(format: (Int) -> String): String = this?.let(format) ?: DASH

    private fun number(value: Int): String = String.format(Locale.US, "%,d", value)

    private fun grams(value: Int): String = "${number(value)} g"

    /** One decimal, dropped when whole: "4.5", "6". */
    private fun decimal(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else String.format(Locale.US, "%.1f", tenths / 10.0)
    }

    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val MONTH = DateTimeFormatter.ofPattern("MMM", Locale.US)
    private val DAY_TIME = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)
}
