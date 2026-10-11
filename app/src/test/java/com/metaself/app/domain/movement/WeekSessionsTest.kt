package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test

/**
 * D115, D131: the one rule for a week's counted sessions and their minutes, which the Trainer's verdict and
 * Movement's headline both count by. TEST_EPOCH_DAY is Thursday 3 September 2026; its Monday is 31 August.
 * Every minute is invented.
 */
class WeekSessionsTest {

    private val monday = TEST_EPOCH_DAY - 3

    @Test
    fun `this week's sessions are the visible, counted ones from Monday to today, in start order`() {
        val workouts = listOf(
            aTypedWorkout(id = 1, epochDay = monday + 1, minutes = 30, startedAtMillis = 2_000),
            aTypedWorkout(id = 2, epochDay = monday, minutes = 20, startedAtMillis = 1_000),
            aTypedWorkout(id = 3, epochDay = monday, minutes = 50, hidden = true),
            aSyncedWorkout(id = 4, minutes = 40, counted = false),
            aTypedWorkout(id = 5, epochDay = monday - 1, minutes = 60), // Sunday before: last week's
            aTypedWorkout(id = 6, epochDay = TEST_EPOCH_DAY + 1, minutes = 70), // tomorrow: not yet counted
        )

        val sessions = WeekSessions.of(workouts, monday, TEST_EPOCH_DAY)

        assertThat(sessions.map { it.id }).containsExactly(2L, 1L).inOrder()
        assertThat(WeekSessions.minutes(sessions)).isEqualTo(50)
    }

    @Test
    fun `an earlier week runs to its Sunday`() {
        val earlier = monday - 7
        val workouts = listOf(
            aTypedWorkout(id = 1, epochDay = earlier + 6, minutes = 25),
            aTypedWorkout(id = 2, epochDay = monday, minutes = 35),
        )

        val sessions = WeekSessions.of(workouts, earlier, TEST_EPOCH_DAY)

        assertThat(sessions.map { it.id }).containsExactly(1L)
        assertThat(WeekSessions.minutes(sessions)).isEqualTo(25)
    }

    @Test
    fun `a week with no session has none, and no minutes`() {
        assertThat(WeekSessions.of(emptyList(), monday, TEST_EPOCH_DAY)).isEmpty()
        assertThat(WeekSessions.minutes(emptyList())).isEqualTo(0)
    }
}
