package com.metaself.app.ui.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.PlanPlace
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.SessionCard
import com.metaself.app.domain.trainer.SessionSlot
import com.metaself.app.domain.trainer.TileState
import org.junit.jupiter.api.Test

/** What the plan page's views say (D122–D124). Today is Thursday 3 September 2026 ([TEST_EPOCH_DAY]); every figure is invented. */
class PlanPageWordingTest {

    private val walk30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")

    @Test
    fun `the replace sentence names the Monday`() {
        assertThat(PlanPageWording.replaces(TEST_EPOCH_DAY + 4)).isEqualTo("This replaces your current plan from Mon 7 Sep.")
    }

    @Test
    fun `a session card's title and done line, and the fold`() {
        val monday = TEST_EPOCH_DAY - 3
        assertThat(PlanPageWording.cardTitle(walk30)).isEqualTo("Easy walk, 30 min")
        assertThat(PlanPageWording.doneLine(SessionCard(PlanPlace(0, 0), walk30, null, TileState.DONE, monday, 30))).isEqualTo("Done Mon")
        assertThat(PlanPageWording.doneLine(SessionCard(PlanPlace(0, 0), walk30, null, TileState.DONE_SHORT, monday, 25))).isEqualTo("Done Mon · 25 min")
        assertThat(PlanPageWording.doneFold(2)).isEqualTo("Done this week · 2")
    }

    @Test
    fun `the kept line says where the steps went`() {
        assertThat(PlanPageWording.kept(SessionSlot(1, 0, 0))).isEqualTo("Kept. Its steps are on your plan, with that session.")
        assertThat(PlanPageWording.kept(null)).isEqualTo("Kept. It is on your plan for seven days, under Session.")
    }

    @Test
    fun `the form names the session it was opened for`() {
        assertThat(PlanPageWording.forPlanned(PlannedTick(2, 4, walk30))).isEqualTo("For week 2 of your plan: easy walk, 30 min")
    }
}
