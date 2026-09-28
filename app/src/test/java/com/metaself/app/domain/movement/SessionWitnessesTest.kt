package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * D92: overlapping workouts are witnesses of one session. Every figure and time is invented; a time is
 * minutes after an invented base ([at]).
 */
class SessionWitnessesTest {

    private fun combine(vararg workouts: Workout, splits: Set<SessionSplit> = emptySet()) =
        SessionWitnesses.combine(workouts.toList(), splits)

    // --- Which copies are one session -------------------------------------------------------------

    @Test
    fun `a session wholly inside a longer one is one session with both as witnesses`() {
        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), aSyncedWorkout(id = 2, from = 10, minutes = 20))

        assertThat(sessions).hasSize(1)
        assertThat(sessions.single().witnessIds).containsExactly(1L, 2L).inOrder()
        assertThat(sessions.single().alsoRecordedBy).isEqualTo(1)
    }

    @Test
    fun `an overlap of exactly half the shorter session is one session`() {
        // 0–60 and 40–80: 20 minutes shared, the shorter is 40.
        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), aSyncedWorkout(id = 2, from = 40, minutes = 40))

        assertThat(sessions).hasSize(1)
    }

    @Test
    fun `an overlap just under half the shorter session leaves two sessions`() {
        // 0–60 and 41–81: 19 minutes shared, the shorter is 40.
        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), aSyncedWorkout(id = 2, from = 41, minutes = 40))

        assertThat(sessions.map { it.id }).containsExactly(1L, 2L).inOrder()
        assertThat(sessions.all { it.witnesses.isEmpty() }).isTrue()
    }

    @Test
    fun `sessions that only touch end to start are two`() {
        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 30), aSyncedWorkout(id = 2, from = 30, minutes = 30))

        assertThat(sessions).hasSize(2)
    }

    @Test
    fun `grouping is transitive, so a chain of overlaps is one session`() {
        // A 0–40 and C 60–100 do not overlap; B 20–80 overlaps each by half of itself or more of them.
        val sessions = combine(
            aSyncedWorkout(id = 1, from = 0, minutes = 40),
            aSyncedWorkout(id = 2, from = 20, minutes = 60),
            aSyncedWorkout(id = 3, from = 60, minutes = 40),
        )

        assertThat(sessions).hasSize(1)
        assertThat(sessions.single().witnessIds).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun `a pair the owner split stays two sessions whatever their overlap`() {
        val sessions = combine(
            aSyncedWorkout(id = 1, from = 0, minutes = 60),
            aSyncedWorkout(id = 2, from = 0, minutes = 60),
            splits = setOf(SessionSplit.of(2, 1)),
        )

        assertThat(sessions.map { it.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `a third witness overlapping both halves of a split never joins them again`() {
        val sessions = combine(
            aSyncedWorkout(id = 1, from = 0, minutes = 60),
            aSyncedWorkout(id = 2, from = 0, minutes = 60),
            aSyncedWorkout(id = 3, from = 10, minutes = 40),
            splits = setOf(SessionSplit.of(1, 2)),
        )

        assertThat(sessions).hasSize(2)
        // Pairs are joined in id order: 1 with 3 comes before 2 with 3.
        assertThat(sessions.first { 3L in it.witnessIds }.witnessIds).containsExactly(1L, 3L)
        assertThat(sessions.first { 2L in it.witnessIds }.witnesses).isEmpty()
    }

    @Test
    fun `a hidden workout is never a witness and passes through as it is`() {
        val hidden = aSyncedWorkout(id = 2, from = 0, minutes = 60, hidden = true)

        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), hidden)

        assertThat(sessions).hasSize(2)
        assertThat(sessions).contains(hidden)
    }

    @Test
    fun `a walk that does not count is never a witness and passes through as it is`() {
        val uncounted = aSyncedWorkout(id = 2, from = 0, minutes = 60, counted = false)

        val sessions = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), uncounted)

        assertThat(sessions).hasSize(2)
        assertThat(sessions).contains(uncounted)
    }

    @Test
    fun `a session recorded once comes back exactly as given`() {
        val alone = aSyncedWorkout(id = 1, from = 0, minutes = 60, distanceM = 5_000)

        assertThat(combine(alone)).containsExactly(alone)
        assertThat(alone.witnessIds).containsExactly(1L)
        assertThat(alone.alsoRecordedBy).isEqualTo(0)
        assertThat(alone.asStored).isSameInstanceAs(alone)
    }

    @Test
    fun `sessions come back in the order they started`() {
        val sessions = combine(
            aSyncedWorkout(id = 1, from = 200, minutes = 30),
            aSyncedWorkout(id = 2, from = 0, minutes = 30),
            aSyncedWorkout(id = 3, from = 100, minutes = 30),
        )

        assertThat(sessions.map { it.id }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun `a zero-minute session is a witness only when its moment lies inside the other`() {
        val inside = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), aSyncedWorkout(id = 2, from = 30, minutes = 0))
        val outside = combine(aSyncedWorkout(id = 1, from = 0, minutes = 60), aSyncedWorkout(id = 2, from = 60, minutes = 0))

        assertThat(inside).hasSize(1)
        assertThat(outside).hasSize(2)
    }

    @Test
    fun `a split is stored lower id first`() {
        assertThat(SessionSplit.of(9, 4)).isEqualTo(SessionSplit(4, 9))
    }
}
