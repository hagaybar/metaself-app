package com.metaself.app.domain.milestone

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightReading
import org.junit.jupiter.api.Test

class MilestonesTest {

    /**
     * Every figure in this file is a rung on one invented ladder that starts at 82 kg and is going
     * to 70. Nothing here is a measurement: a milestone is a DISTANCE, so each weight below is
     * chosen for how far it stands from the 82 the ladder starts at, or from the 70 it ends at.
     */
    private val losing = Goal.lose(kgPerWeek = 0.5, targetKg = 70.0)

    @Test
    fun `the first kilogram, and nothing else yet`() {
        val reached = Milestones.reached(losing, trendOf(82.0, 80.8))

        assertThat(reached).containsExactly(Milestone.firstKg)
    }

    @Test
    fun `not quite a kilogram is not a milestone`() {
        assertThat(Milestones.reached(losing, trendOf(82.0, 81.2))).isEmpty()
    }

    @Test
    fun `five kilograms brings the first and the fifth`() {
        val reached = Milestones.reached(losing, trendOf(82.0, 76.6))

        assertThat(reached).containsExactly(Milestone.firstKg, Milestone.everyFifth(5))
    }

    @Test
    fun `every fifth accumulates`() {
        val reached = Milestones.reached(Goal.lose(0.5, targetKg = 52.0), trendOf(82.0, 70.0))

        assertThat(reached).contains(Milestone.everyFifth(5))
        assertThat(reached).contains(Milestone.everyFifth(10))
        assertThat(reached).doesNotContain(Milestone.everyFifth(15))
    }

    @Test
    fun `halfway is half the distance from where he started to where he is going`() {
        // 82 to 72 is ten kilograms; halfway is 77.
        val goal = Goal.lose(0.5, targetKg = 72.0)

        assertThat(Milestones.reached(goal, trendOf(82.0, 77.5))).doesNotContain(Milestone.halfway)
        assertThat(Milestones.reached(goal, trendOf(82.0, 77.0))).contains(Milestone.halfway)
    }

    @Test
    fun `the last kilogram`() {
        val reached = Milestones.reached(losing, trendOf(82.0, 70.9))

        assertThat(reached).contains(Milestone.lastKg)
    }

    /** Arriving has its own announcement; following it with "nearly there" would be absurd. */
    @Test
    fun `a goal already reached brings neither halfway nor the last kilogram`() {
        val reached = Milestones.reached(losing, trendOf(82.0, 69.5))

        assertThat(reached).doesNotContain(Milestone.lastKg)
        assertThat(reached).doesNotContain(Milestone.halfway)
    }

    /** D22: nothing is ever said about going the wrong way. */
    @Test
    fun `a weight that went up reaches nothing`() {
        assertThat(Milestones.reached(losing, trendOf(80.0, 83.0))).isEmpty()
    }

    @Test
    fun `holding weight has no milestones at all`() {
        assertThat(Milestones.reached(Goal.hold(), trendOf(82.0, 76.0))).isEmpty()
    }

    @Test
    fun `no readings, nothing reached`() {
        assertThat(Milestones.reached(losing, emptyList())).isEmpty()
    }

    /** A goal with no destination still counts distance covered — it just has nowhere to be. */
    @Test
    fun `a rate with no target still earns the first kilogram`() {
        val reached = Milestones.reached(Goal.lose(0.5), trendOf(82.0, 80.5))

        assertThat(reached).containsExactly(Milestone.firstKg)
    }

    @Test
    fun `four weeks each moving the right way`() {
        val weekly = weeklyTrend(82.0, 81.5, 81.0, 80.5, 80.0)

        assertThat(Milestones.reached(losing, weekly)).contains(Milestone.steady(4))
    }

    /**
     * Every week must move, not the average of them. Consistency is the only one of these that is
     * really praise, and praising it on the strength of one very good week would praise the wrong
     * thing.
     */
    @Test
    fun `a stalled week breaks the run and claims nothing`() {
        val weekly = weeklyTrend(82.0, 81.5, 81.6, 80.5, 80.0)

        assertThat(Milestones.reached(losing, weekly)).doesNotContain(Milestone.steady(4))
    }

    @Test
    fun `a fortnight of history cannot claim four weeks`() {
        val weekly = weeklyTrend(82.0, 81.5, 81.0)

        assertThat(Milestones.reached(losing, weekly)).doesNotContain(Milestone.steady(4))
    }

    /** Oldest first, one reading a week, most recent last. */
    private fun weeklyTrend(vararg kg: Double): List<TrendPoint> {
        val lastDay = 20_699L
        return kg.reversed().mapIndexed { index, value ->
            TrendPoint(WeightReading(lastDay - index * 7L, value), trendKg = value)
        }.reversed()
    }

    private fun trendOf(vararg kg: Double): List<TrendPoint> = kg.mapIndexed { index, value ->
        TrendPoint(WeightReading(20_000L + index, value), trendKg = value)
    }
}
