package com.metaself.app.ui.goal

import com.metaself.app.domain.milestone.Milestone

/**
 * One sentence per milestone.
 *
 * Plain, and short. These are shown for a single day and are meant to be pleasant to meet and easy
 * to forget; anything longer becomes something to scroll past, and something to scroll past is the
 * first step towards a screen the owner stops reading.
 */
object MilestoneWording {

    private val EVERY_FIFTH = Regex("""^EVERY_5_KG_(\d+)$""")
    private val STEADY = Regex("""^STEADY_(\d+)W$""")

    /** Null for a milestone this version does not recognise — a stored name from a later build. */
    fun of(milestone: Milestone): String? {
        EVERY_FIFTH.find(milestone.name)?.let { match ->
            return "${match.groupValues[1]} kg down since you started."
        }
        STEADY.find(milestone.name)?.let { match ->
            return "${match.groupValues[1]} weeks running, every one of them going the right way."
        }
        return when (milestone.name) {
            Milestone.FIRST_KG -> "That is your first kilogram."
            Milestone.HALFWAY -> "You are halfway to your goal weight."
            Milestone.LAST_KG -> "One kilogram left."
            else -> null
        }
    }

    /**
     * Everything reached today, as one message.
     *
     * More than one can land on the same day — the fifth kilogram is also halfway, for somebody
     * whose goal is ten. Joined rather than stacked, because two celebration cards in a column look
     * like a bug.
     */
    fun today(milestones: Collection<Milestone>): String? = milestones
        .mapNotNull(::of)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")
}
