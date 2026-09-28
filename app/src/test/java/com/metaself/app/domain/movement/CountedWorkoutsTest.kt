package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** D81's one rule. `com.example.band` and `com.example.phone` are invented apps. */
class CountedWorkoutsTest {

    @Test
    fun `a walk from an app left out does not count`() {
        assertThat(CountedWorkouts.counts(WorkoutKind.WALK, BAND, setOf(BAND))).isFalse()
    }

    @Test
    fun `every other kind from that app still counts`() {
        WorkoutKind.entries.filter { it != WorkoutKind.WALK }.forEach { kind ->
            assertThat(CountedWorkouts.counts(kind, BAND, setOf(BAND))).isTrue()
        }
    }

    @Test
    fun `a walk from another app counts`() {
        assertThat(CountedWorkouts.counts(WorkoutKind.WALK, PHONE, setOf(BAND))).isTrue()
    }

    @Test
    fun `a typed walk has no writing app, so it always counts`() {
        assertThat(CountedWorkouts.counts(WorkoutKind.WALK, null, setOf(BAND))).isTrue()
    }

    @Test
    fun `with no app left out, every walk counts`() {
        assertThat(CountedWorkouts.counts(WorkoutKind.WALK, BAND, emptySet())).isTrue()
    }

    private companion object {
        const val BAND = "com.example.band"
        const val PHONE = "com.example.phone"
    }
}
