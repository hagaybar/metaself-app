package com.metaself.app.domain.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.movement.SessionWitnesses
import com.metaself.app.domain.movement.aSyncedWorkout
import com.metaself.app.domain.movement.aTypedWorkout
import com.metaself.app.domain.movement.at
import org.junit.jupiter.api.Test

/** D92: a session's review may sit on any of its witnesses. Every session and review is invented. */
class SessionReviewsTest {

    private val lead = aSyncedWorkout(id = 1, from = 0, minutes = 60)
    private val other = aSyncedWorkout(id = 2, from = 0, minutes = 50)
    private val typed = aTypedWorkout(id = 3, startedAtMillis = at(0), minutes = 40)
    private val session = SessionWitnesses.combine(listOf(lead, other, typed), emptySet()).single()

    private fun review(workoutId: Long, words: String) =
        TrainerReview(workoutId = workoutId, planId = null, felt = Felt.RIGHT, words = words)

    @Test
    fun `the lead's review is the session's`() {
        val byLead = review(1, "on the lead")

        val keyed = SessionReviews.bySession(listOf(session), listOf(review(2, "on another"), byLead))

        assertThat(keyed[1L]).isEqualTo(byLead)
    }

    @Test
    fun `with none on the lead it is the first witness's in lead order`() {
        val onOther = review(2, "on another")
        val onTyped = review(3, "on the typed one")

        val keyed = SessionReviews.bySession(listOf(session), listOf(onTyped, onOther))

        assertThat(keyed[1L]).isEqualTo(onOther)
    }

    @Test
    fun `every review stays under its own workout too`() {
        val onTyped = review(3, "on the typed one")

        val keyed = SessionReviews.bySession(listOf(session), listOf(onTyped, review(-1, "session gone")))

        assertThat(keyed.keys).containsExactly(1L, 3L, -1L)
        assertThat(keyed[3L]).isEqualTo(onTyped)
    }

    @Test
    fun `a session with no review anywhere has none`() {
        assertThat(SessionReviews.bySession(listOf(session), emptyList())).isEmpty()
    }
}
