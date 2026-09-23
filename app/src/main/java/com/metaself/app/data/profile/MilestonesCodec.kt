package com.metaself.app.data.profile

import com.metaself.app.domain.milestone.Milestone

/**
 * What has already been celebrated, and when.
 *
 * One preference string of `NAME:epochDay` pairs. Readable by a person looking at a backup, for the
 * same reason a food item's source is stored as a word rather than a number: a file full of ordinals
 * is unreadable by anybody trying to work out what went wrong.
 *
 * This set is the thing that makes "only once" true. It is never derived and never rebuilt from the
 * weight history — a weight that crossed a threshold, drifted back and crossed again would rebuild
 * as though nothing had been said.
 */
object MilestonesCodec {

    const val KEY = "milestones_reached"

    private const val PAIR = ":"
    private const val SEPARATOR = ","

    fun encode(reached: Map<Milestone, Long>): String = reached.entries
        .sortedBy { it.value }
        .joinToString(SEPARATOR) { (milestone, epochDay) -> "${milestone.name}$PAIR$epochDay" }

    /**
     * Anything unreadable is dropped rather than throwing. A half-written entry costs one repeated
     * celebration; refusing to decode the string would cost every one of them at once.
     */
    fun decode(value: String?): Map<Milestone, Long> {
        if (value.isNullOrBlank()) return emptyMap()
        return value.split(SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(PAIR)
            if (parts.size != 2) return@mapNotNull null
            val name = parts[0].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val epochDay = parts[1].trim().toLongOrNull() ?: return@mapNotNull null
            Milestone(name) to epochDay
        }.toMap()
    }
}
