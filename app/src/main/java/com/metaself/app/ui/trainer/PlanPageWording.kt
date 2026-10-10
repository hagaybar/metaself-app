package com.metaself.app.ui.trainer

import com.metaself.app.domain.trainer.PlanTile
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.SessionCard
import com.metaself.app.domain.trainer.SessionSlot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the plan page's three views say, and Plan ahead (D122–D124). Every count is the phone's. */
object PlanPageWording {

    private val DAY_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

    /** D123: the switch under the title. */
    const val MONTH = "Month"
    const val WEEK = "Week"
    const val SESSION = "Session"

    /** D123: the Month view of a plan made before outlines existed — every plan, until release 2. */
    const val NO_OUTLINE = "This plan has no outline — plan ahead to set a goal."

    /** D124: was "Evaluate me and plan the weeks ahead". */
    const val PLAN_AHEAD = "Plan ahead"

    const val NEXT_SESSION = "NEXT SESSION"
    const val THIS_WEEK = "THIS WEEK"
    const val NEXT_WEEK = "NEXT WEEK"
    const val SHOW_STEPS = "Show the steps"
    const val HIDE_STEPS = "Hide the steps"
    const val GET_STEPS = "Get the steps"
    const val NOTHING_COMING = "Nothing planned is coming up."

    /** D124: the replace confirmation's two answers. */
    const val REPLACE = "Replace it"
    const val NOT_NOW = "Not now"

    /** "Easy walk, 30 min" — kind, effort, minutes (invented). */
    fun cardTitle(planned: PlannedSession): String = ProgrammeWording.plannedTitle(planned)

    /** "Done Mon", "Done Mon · 25 min" (invented) — as the tile says it. */
    fun doneLine(card: SessionCard): String = PlanTimelineWording.tileLine(PlanTile(card.planned, card.state, card.day, card.minutes))

    /** "Done this week · 2" (invented): the fold over this week's done sessions. */
    fun doneFold(count: Int): String = "Done this week · $count"

    /** D124: "This replaces your current plan from Mon 7 Sep." (invented). */
    fun replaces(start: Long): String = "This replaces your current plan from ${LocalDate.ofEpochDay(start).format(DAY_DATE)}."

    /** D122: where a kept suggestion went. */
    fun kept(slot: SessionSlot?): String =
        if (slot != null) "Kept. Its steps are on your plan, with that session." else "Kept. It is on your plan for seven days, under Session."

    /** D123: the form opened by Get the steps: "For week 2 of your plan: easy walk, 30 min" (invented). */
    fun forPlanned(tick: PlannedTick): String =
        "For week ${tick.week} of your plan: " + ProgrammeWording.plannedTitle(tick.session).replaceFirstChar { it.lowercase(Locale.US) }
}
