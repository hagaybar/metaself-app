package com.metaself.app.data.profile

import com.metaself.app.domain.window.EatingWindow
import com.metaself.app.domain.window.MeasuredWindow
import com.metaself.app.domain.window.WindowRule

/**
 * Every window the owner has ever set, oldest first, of either kind.
 *
 * A LIST rather than one value, because a window applies from a day forwards and changing it must
 * leave the old one governing the days it governed. Storing only the current one would silently
 * re-score every past day the moment the rule changed, which is exactly what must not happen.
 *
 * "6-20@20699,16f@20707" — readable in a backup, like every other stored format here. The two
 * shapes are the two kinds of window (D29):
 *
 * - `6-20@20699` is the hours: from 6 until 20, governing from day 20699. This is the format the
 *   shipped version writes, it is read exactly as it always was, and if nothing about it is edited
 *   it is written back byte for byte — an owner who never uses a ratio never sees his stored value
 *   change.
 * - `16f@20707` is a ratio: sixteen hours fasting, governing from day 20707. `f` for fasting,
 *   which is also the half the number names.
 */
object WindowRuleCodec {

    /**
     * The key is the load-bearing part and does not change with the object's name: changing it
     * would silently lose every stored window and would look like the feature having been switched
     * off.
     */
    const val KEY = "eating_windows"

    fun encode(rules: List<WindowRule>): String = rules
        .sortedBy { it.fromEpochDay }
        .joinToString(",") { rule ->
            when (rule) {
                is WindowRule.Fixed ->
                    "${rule.window.startHour}-${rule.window.endHour}@${rule.fromEpochDay}"

                is WindowRule.Measured -> "${rule.window.fastingHours}f@${rule.fromEpochDay}"
            }
        }

    /** Anything unreadable is dropped; the rest survives. A bad entry costs one window, not all. */
    fun decode(value: String?): List<WindowRule> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").mapNotNull { entry ->
            // The kind is picked by shape INSIDE the catch, not before it. An entry that is neither
            // shape — "rubbish", with no "@" in it at all — is dropped because the destructuring
            // throws here, and an impossible ratio is dropped because `MeasuredWindow`'s own
            // `require` throws here too. Sniffing the kind outside this block would need a second
            // set of answers for both.
            runCatching {
                val (spec, from) = entry.split("@")
                val fromEpochDay = from.trim().toLong()
                val shape = spec.trim()

                if (shape.endsWith("f")) {
                    WindowRule.Measured(
                        window = MeasuredWindow(fastingHours = shape.dropLast(1).toInt()),
                        fromEpochDay = fromEpochDay,
                    )
                } else {
                    val (start, end) = shape.split("-")
                    WindowRule.Fixed(
                        EatingWindow(
                            startHour = start.trim().toInt(),
                            endHour = end.trim().toInt(),
                            fromEpochDay = fromEpochDay,
                        ),
                    )
                }
            }.getOrNull()
        }.sortedBy { it.fromEpochDay }
    }
}
