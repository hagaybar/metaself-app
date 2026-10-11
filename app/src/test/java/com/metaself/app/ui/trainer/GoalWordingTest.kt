package com.metaself.app.ui.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.GOAL_15K
import com.metaself.app.domain.trainer.GoalSport
import com.metaself.app.domain.trainer.MonthWeek
import com.metaself.app.domain.trainer.Phase
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.OUTLINE_15
import com.metaself.app.domain.trainer.target
import org.junit.jupiter.api.Test

/** D119, D120, D123: what the goal, the outline and the Month view say. Today is Thursday 3 September 2026; every figure is invented. */
class GoalWordingTest {

    @Test
    fun `the goal line says the distance, the date and the weeks to go`() {
        assertThat(GoalWording.goalLine(GOAL_15K, TEST_EPOCH_DAY)).isEqualTo("15 km run · by Sun 13 Dec · 14 weeks to go")
        assertThat(GoalWording.goalLine(GOAL_15K, TEST_EPOCH_DAY + 7 * 13)).isEqualTo("15 km run · by Sun 13 Dec · 1 week to go")
        assertThat(GoalWording.goalLine(GOAL_15K, TEST_EPOCH_DAY + 7 * 14)).isEqualTo("15 km run · by Sun 13 Dec · goal week")
        assertThat(GoalWording.goalLine(GOAL_15K, TEST_EPOCH_DAY + 7 * 15)).isEqualTo("15 km run · by Sun 13 Dec · goal date passed")
        assertThat(GoalWording.goalLine(PlanGoal.Distance(GoalSport.RIDE, 42.5, GOAL_15K.byEpochDay), TEST_EPOCH_DAY))
            .isEqualTo("42.5 km ride · by Sun 13 Dec · 14 weeks to go")
        assertThat(GoalWording.goalLine(PlanGoal.Steady, TEST_EPOCH_DAY)).isEqualTo("Keep a steady rhythm")
    }

    @Test
    fun `a target, a block and a bar in words`() {
        assertThat(GoalWording.targetLine(target(150, 60, easier = true))).isEqualTo("about 150 min · longest 60′ · easier week")
        assertThat(GoalWording.targetLine(target(150, 60))).isEqualTo("about 150 min · longest 60′")
        assertThat(GoalWording.blockTitle(OUTLINE_15.blocks.first())).isEqualTo("Base · Easy rhythm")
        assertThat(GoalWording.phase(Phase.TAPER)).isEqualTo("Taper")
        assertThat(GoalWording.barWords(MonthWeek(3, target(150, 60), done = 120, here = false))).isEqualTo("Wk 3 · 150 min, 120 done")
        assertThat(GoalWording.barWords(MonthWeek(4, target(100, 30, easier = true), done = null, here = false))).isEqualTo("Wk 4 · 100 min, easier")
        assertThat(GoalWording.barWords(MonthWeek(6, target(150, 60), done = null, here = true))).isEqualTo("Wk 6 · 150 min, you are here")
    }

    @Test
    fun `the form's date says the Sunday and the weeks`() {
        assertThat(GoalWording.byLine(GOAL_15K.byEpochDay, TEST_EPOCH_DAY)).isEqualTo("Sun 13 Dec · 14 weeks")
        assertThat(GoalWording.byDay(TEST_EPOCH_DAY, 14)).isEqualTo(GOAL_15K.byEpochDay)
    }
}
