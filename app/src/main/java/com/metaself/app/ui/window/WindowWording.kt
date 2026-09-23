package com.metaself.app.ui.window

import com.metaself.app.domain.window.DayVerdict
import com.metaself.app.domain.window.DayWindow
import com.metaself.app.domain.window.EatingStretch
import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.StretchVerdict
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the app says about the eating window.
 *
 * Never a scold. The owner set the decision and wants to see whether it is being kept; an app that
 * tells somebody off for a late dinner is one they stop opening. Today may point out that a meal
 * fell outside; a past day states the same fact flatly and in no colour (D14).
 */
object WindowWording {

    fun hours(window: EatingWindow): String =
        "${clock(window.startHour)} to ${clock(window.endHour)}"

    /**
     * Said only when something fell outside.
     *
     * A day that kept the window used to get a sentence saying so; it was long and unnecessary —
     * the mark beside it already says the window is being kept, in the width of a word. Silence
     * for a kept day, a line for a missed one, nothing at all for a day no window governed.
     */
    fun outside(day: DayWindow?, isToday: Boolean): String? {
        if (day == null) return null

        return when {
            day.mealsOutside > 0 && isToday -> {
                val meals = if (day.mealsOutside == 1) "One meal" else "${day.mealsOutside} meals"
                "$meals outside your ${hours(day.window)} window."
            }

            day.mealsOutside > 0 -> {
                val meals = if (day.mealsOutside == 1) "one meal" else "${day.mealsOutside} meals"
                "Your window was ${hours(day.window)}; $meals fell outside it."
            }

            else -> null
        }
    }

    /**
     * Meals that could not be timed, said only when there are some.
     *
     * A meal written down on a different day has no trustworthy hour — the record keeps when it was
     * typed, not when it was eaten — and the owner is entitled to know a day was not fully judged
     * rather than to assume it passed.
     */
    fun untimed(day: DayVerdict?): String? {
        val count = day?.mealsUntimed ?: return null
        if (count == 0) return null
        // Says how to put it right, now that it can be (D33) — and says WHERE, which it has to now
        // that the day draws parts of the clock rather than loggings (D51). The time is set on one
        // logging's own row, and a part of the day covers several loggings with several times, so
        // there is no single time on the day to tap any more. The day's last row gathers exactly
        // these loggings and opens the record at them, which is where each one is a row again.
        return if (count == 1) {
            "One meal was written down on another day, so its time is not known. " +
                "Open the day's record to set it."
        } else {
            "$count meals were written down on other days, so their times are not known. " +
                "Open the day's record to set them."
        }
    }

    /** "Window kept on 10 of 14 days" — counted from the record, like the streak (D13). */
    fun kept(kept: Int, judged: Int): String? =
        if (judged == 0) null else "Window kept on $kept of $judged days"

    /**
     * Said when a window is set, because the owner's one firm requirement about this feature is
     * that it never reaches backwards.
     */
    const val FROM_TODAY =
        "It applies from today onwards. Days you have already logged are left exactly as they are."

    // --- the measured window (D29) ---------------------------------------------------------------

    /**
     * "16/8" — fasting first, the way the ratio is written everywhere else in the world.
     *
     * Never said on its own. Every place that shows this also shows [inWords] beside it.
     */
    fun ratio(window: MeasuredWindow): String = "${window.fastingHours}/${window.eatingHours}"

    /** The slash spelled out, so the meaning is never left to be inferred from it. */
    fun inWords(window: MeasuredWindow): String =
        "${window.fastingHours} hours fasting, ${window.eatingHours} hours eating"

    /**
     * The one sentence today says under a ratio: a TIME he can act on (D32).
     *
     * The shape required: on waking, good morning and — if the fast is being kept — that eating
     * can start at X; after the first meal, that if the window is being kept the last meal is by Y.
     *
     * **Every sentence about the future is a condition.** The app cannot know a meal is the last one
     * until the fast after it completes with nothing logged, and it takes nothing logged to mean
     * nothing eaten. "If you're keeping your fast" is the only honest phrasing; the
     * sentence this replaced went wrong exactly where it stopped being conditional, stating a
     * duration as a fact about a present the app cannot see.
     *
     * [latest] is the latest stretch under the ratio, OPEN OR CLOSED, whichever day it began on: a
     * closed one is what "your 14 hours were up" is said about. With `F` its first input, `L` its
     * last, the closing time is `F + eating hours` and the fast completes at `L + fasting hours`:
     *
     * | Situation | Said |
     * |---|---|
     * | no stretch under this ratio | nothing |
     * | the fast has completed, nothing since | "Your 14 hours were up at 12:00 — eat when you like." |
     * | open, before the closing time | "If you're keeping your window, last meal by 22:00." |
     * | open, past it, eating inside the hours | "If you're keeping your fast, next meal from 12:00." |
     * | open, eating over the hours | "You've eaten for 12h 0m — next meal from 12:00 if …" |
     *
     * The last row cannot come before the closing time: eating cannot exceed the eating hours while
     * fewer than the eating hours have passed since it began. It carries the over-run of D31's live
     * line — a ratio whose fast is never reached is one stretch that never closes, and without it
     * the app would say nothing to exactly the person failing to keep it — and, first, the time he
     * can act on. Over the hours is [EatingStretch.ranOver], the rule the verdict is judged by, so
     * this can never say mid-stretch that he ran over a ratio the verdict then says he kept.
     *
     * **"Good morning." before noon, and only then**, because the same sentence is said all evening.
     *
     * A time on the next calendar day says "tomorrow"; a moment already past, in the second row,
     * says "yesterday" or its date when it was not today. Clock times only, never time remaining:
     * a countdown is a nag (D15, D27), and nothing here notifies.
     *
     * Said on today only. A past day has nothing to act on, and keeps its own sentences about the
     * stretches it began: [span], [stillOpen] and [notJudged].
     */
    fun ratioNow(
        latest: EatingStretch?,
        window: MeasuredWindow,
        nowMillis: Long,
        zone: ZoneId,
        isToday: Boolean,
    ): String? {
        if (latest == null || !isToday) return null

        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val closesAt = latest.startedAtMillis + window.eatingHours * MILLIS_PER_HOUR
        val fastDoneAt = latest.lastAtMillis + window.fastingHours * MILLIS_PER_HOUR

        val sentence = when {
            latest.closed(window, nowMillis) ->
                "Your ${window.fastingHours} hours were up ${since(fastDoneAt, now)} — " +
                    "eat when you like."

            nowMillis < closesAt ->
                "If you're keeping your window, last meal by ${ahead(closesAt, now)}."

            !latest.ranOver(window) ->
                "If you're keeping your fast, next meal from ${ahead(fastDoneAt, now)}."

            else ->
                "You've eaten for ${duration(latest.spanMinutes)} — " +
                    "next meal from ${ahead(fastDoneAt, now)} if you're keeping your fast."
        }
        return if (now.hour < NOON) "Good morning. $sentence" else sentence
    }

    /** "12:00", or "12:00 tomorrow" for a moment on the next calendar day. */
    private fun ahead(millis: Long, now: ZonedDateTime): String {
        val at = Instant.ofEpochMilli(millis).atZone(now.zone)
        val tomorrow = if (at.toLocalDate() != now.toLocalDate()) " tomorrow" else ""
        return clock(at.hour, at.minute) + tomorrow
    }

    /** "at 12:00", "yesterday at 12:00", or "on 18 Sep at 12:00". */
    private fun since(millis: Long, now: ZonedDateTime): String {
        val at = Instant.ofEpochMilli(millis).atZone(now.zone)
        val time = clock(at.hour, at.minute)
        return when (at.toLocalDate()) {
            now.toLocalDate() -> "at $time"
            now.toLocalDate().minusDays(1) -> "yesterday at $time"
            else -> "on ${at.format(DAY_AND_MONTH)} at $time"
        }
    }

    /**
     * Said only when a closed stretch ran past the ratio.
     *
     * Silence for a kept stretch, exactly as the fixed window is silent for a kept day: the mark
     * beside it already says so. A broken one states the fact flatly, with no reproach in it (D14),
     * and says the same thing whether it ended an hour ago or last month — the two inputs are
     * already that far apart either way, which is why this one takes no `isToday` where its
     * siblings do.
     *
     * The ratio comes in beside the stretch because a stretch is a record of what happened and
     * holds no opinion about what was allowed; the rule in force says that, and it is the day's
     * verdict that carries it.
     */
    fun span(stretch: StretchVerdict?, window: MeasuredWindow): String? {
        if (stretch == null || !stretch.judged || stretch.kept) return null
        return "You ate over ${duration(stretch.stretch.spanMinutes)}; " +
            "your ratio allows ${window.eatingHours}h."
    }

    /**
     * Why a past day's stretch has no verdict yet: the fast after it has not completed.
     *
     * New on screen with D31, and the honest consequence of counting hours instead of days: a day was
     * over at midnight and could always be judged, but a stretch is not over until the fast
     * completes, so last night's eating may have no verdict until this morning. The app says that
     * rather than guessing at one.
     *
     * **A past day's sentence only.** On today [ratioNow] says the one thing there is to say about
     * an open stretch, as a time he can act on (D32); a second sentence explaining that nothing has
     * been judged yet would be one too many.
     */
    fun stillOpen(
        stretch: StretchVerdict?,
        window: MeasuredWindow,
        isToday: Boolean,
    ): String? {
        if (stretch == null || stretch.closed || isToday) return null
        return "Still open — nothing is judged until you have fasted ${window.fastingHours} hours."
    }

    /**
     * Said when a closed stretch held one input and so was not judged either way.
     *
     * A span of nothing would keep any ratio, which is a compliment for having logged almost
     * nothing (design §2.1). Carried over from the day rule word for word except for the unit.
     *
     * No `isToday` here, where the day rule needed one. A stretch of one input that is still going
     * is the closing-time case above, and this sentence waits for the fast to shut it — so "still
     * eating" and "that was all there was" can no longer be confused for one another, whichever day
     * is on screen.
     */
    fun notJudged(stretch: StretchVerdict?): String? {
        if (stretch == null || !stretch.closed || stretch.stretch.inputs != 1) return null
        return "Only one thing was logged, so this stretch is not judged either way."
    }

    /**
     * "Kept 10 of 14 stretches since 3 Sep" — the tally, in the unit a ratio judges in (§3.1),
     * counted from the day the ratio was set (D32) rather than over the last fortnight.
     */
    fun keptStretches(kept: Int, judged: Int, since: LocalDate): String? =
        if (judged == 0) {
            null
        } else {
            "Kept $kept of $judged stretches since ${since.format(DAY_AND_MONTH)}"
        }

    private fun duration(minutes: Long): String = "${minutes / 60}h ${minutes % 60}m"

    private const val MILLIS_PER_HOUR = 60L * 60L * 1000L

    private const val NOON = 12

    /** "18 Sep" — in English, like every other word here, whatever the phone's language. */
    private val DAY_AND_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.US)


    private fun clock(hour: Int): String = String.format(Locale.US, "%02d:00", hour)

    private fun clock(hour: Int, minute: Int): String =
        String.format(Locale.US, "%02d:%02d", hour, minute)
}
