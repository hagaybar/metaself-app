package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test

/** D126: a suggested goal checked as the form would hold it. Today is Thursday 3 September 2026; every figure is invented. */
class GoalBoundsTest {

    private fun suggestion(goal: PlanGoal, perWeek: Int = 3) = GoalSuggestion("Invented title", goal, perWeek, "Invented why.")

    @Test
    fun `a dated goal's date moves to its Sunday`() {
        val wednesday = GOAL_15K.byEpochDay - 4
        assertThat(GoalBounds.checked(GOAL_15K.copy(byEpochDay = wednesday), TEST_EPOCH_DAY)).isEqualTo(GOAL_15K)
        assertThat(GoalBounds.checked(GOAL_150_MIN.copy(byEpochDay = wednesday), TEST_EPOCH_DAY)).isEqualTo(GOAL_150_MIN)
        assertThat(GoalBounds.checked(PlanGoal.Steady, TEST_EPOCH_DAY)).isEqualTo(PlanGoal.Steady)
    }

    @Test
    fun `a date 6 to 30 weeks ahead fits, and nothing nearer or further`() {
        assertThat(GoalBounds.checked(GOAL_150_MIN.copy(byEpochDay = GoalBounds.byDay(TEST_EPOCH_DAY, 6)), TEST_EPOCH_DAY)).isNotNull()
        assertThat(GoalBounds.checked(GOAL_150_MIN.copy(byEpochDay = GoalBounds.byDay(TEST_EPOCH_DAY, 30)), TEST_EPOCH_DAY)).isNotNull()
        assertThat(GoalBounds.checked(GOAL_150_MIN.copy(byEpochDay = GoalBounds.byDay(TEST_EPOCH_DAY, 5)), TEST_EPOCH_DAY)).isNull()
        assertThat(GoalBounds.checked(GOAL_15K.copy(byEpochDay = GoalBounds.byDay(TEST_EPOCH_DAY, 31)), TEST_EPOCH_DAY)).isNull()
    }

    @Test
    fun `km with more than one decimal, sessions a week out of 2 to 5, or no title or why do not fit`() {
        assertThat(GoalBounds.checked(GOAL_15K.copy(km = 21.1), TEST_EPOCH_DAY)).isNotNull()
        assertThat(GoalBounds.checked(GOAL_15K.copy(km = 12.25), TEST_EPOCH_DAY)).isNull()
        assertThat(GoalBounds.checked(suggestion(GOAL_15K), TEST_EPOCH_DAY)).isEqualTo(suggestion(GOAL_15K))
        assertThat(GoalBounds.checked(suggestion(GOAL_15K, perWeek = 1), TEST_EPOCH_DAY)).isNull()
        assertThat(GoalBounds.checked(suggestion(GOAL_15K, perWeek = 6), TEST_EPOCH_DAY)).isNull()
        assertThat(GoalBounds.checked(suggestion(GOAL_15K).copy(title = " "), TEST_EPOCH_DAY)).isNull()
        assertThat(GoalBounds.checked(suggestion(GOAL_15K).copy(why = ""), TEST_EPOCH_DAY)).isNull()
    }
}
