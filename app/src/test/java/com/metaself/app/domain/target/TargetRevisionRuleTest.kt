package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.Sex
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend
import com.metaself.app.domain.weight.aReading
import org.junit.jupiter.api.Test
import kotlin.math.abs

class TargetRevisionRuleTest {

    private val profile = aProfile()

    @Test
    fun `with no weights logged, nothing is ever due`() {
        val revision = TargetRevisionRule.revise(
            profile = profile,
            trend = emptyList(),
            last = null,
            today = TEST_EPOCH_DAY,
            currentYear = TEST_YEAR,
        )

        assertThat(revision).isNull()
    }

    @Test
    fun `one reading is not enough, however different from the setup weight`() {
        // D11: never from a single day's reading. The first revision waits a week.
        val trend = WeightTrend.of(listOf(aReading(epochDay = TEST_EPOCH_DAY, kg = 74.0)))

        val revision = TargetRevisionRule.revise(
            profile = profile,
            trend = trend,
            last = null,
            today = TEST_EPOCH_DAY,
            currentYear = TEST_YEAR,
        )

        assertThat(revision).isNull()
    }

    @Test
    fun `a week after the first reading, the first revision is due`() {
        val trend = WeightTrend.of(dailyReadings(from = TEST_EPOCH_DAY - 7, days = 8, startKg = 79.0))

        val revision = TargetRevisionRule.revise(
            profile = profile,
            trend = trend,
            last = null,
            today = TEST_EPOCH_DAY,
            currentYear = TEST_YEAR,
        )!!

        assertThat(revision.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(revision.trendKg).isWithin(0.01).of(trend.last().trendKg)
        assertThat(revision.previousKcal).isNull()
    }

    @Test
    fun `six days after a revision, nothing is due`() {
        val trend = WeightTrend.of(dailyReadings(from = TEST_EPOCH_DAY - 20, days = 21, startKg = 79.0))
        val last = TargetRevision(TEST_EPOCH_DAY - 6, trendKg = 79.0, kcal = 2090, previousKcal = null)

        val revision = TargetRevisionRule.revise(profile, trend, last, TEST_EPOCH_DAY, TEST_YEAR)

        assertThat(revision).isNull()
    }

    @Test
    fun `seven days after a revision, another is due`() {
        val trend = WeightTrend.of(dailyReadings(from = TEST_EPOCH_DAY - 20, days = 21, startKg = 79.0))
        val last = TargetRevision(TEST_EPOCH_DAY - 7, trendKg = 79.0, kcal = 2090, previousKcal = null)

        val revision = TargetRevisionRule.revise(profile, trend, last, TEST_EPOCH_DAY, TEST_YEAR)!!

        assertThat(revision.previousKcal).isEqualTo(2090)
    }

    @Test
    fun `the target is worked out from the trend, not from the last reading`() {
        // The last reading is 2 kg heavy; the trend is not. A target built on the reading would be
        // visibly larger, which is the failure D11 exists to prevent.
        val readings = dailyReadings(from = TEST_EPOCH_DAY - 10, days = 10, startKg = 79.0) +
            aReading(epochDay = TEST_EPOCH_DAY, kg = 81.0)
        val trend = WeightTrend.of(readings)

        val revision = TargetRevisionRule.revise(profile, trend, null, TEST_EPOCH_DAY, TEST_YEAR)!!

        val fromTrend =
            DailyTargetCalculator.of(profile.copy(weightKg = trend.last().trendKg), TEST_YEAR).kcal
        val fromReading = DailyTargetCalculator.of(profile.copy(weightKg = 81.0), TEST_YEAR).kcal

        assertThat(revision.kcal).isEqualTo(fromTrend)
        assertThat(revision.kcal).isNotEqualTo(fromReading)
    }

    @Test
    fun `the safe floor is applied again at every revision`() {
        val small = aProfile(
            sex = Sex.FEMALE,
            weightKg = 65.0,
            heightCm = 165,
            birthYear = 1986,
            activity = ActivityLevel.SEDENTARY,
            goal = Goal.lose(1.0),
        )
        val trend = WeightTrend.of(dailyReadings(from = TEST_EPOCH_DAY - 7, days = 8, startKg = 64.0))

        val revision = TargetRevisionRule.revise(small, trend, null, TEST_EPOCH_DAY, TEST_YEAR)!!

        val expected =
            DailyTargetCalculator.of(small.copy(weightKg = trend.last().trendKg), TEST_YEAR)
        assertThat(expected.floorApplied).isTrue()
        assertThat(revision.kcal).isEqualTo(expected.kcal)
    }

    @Test
    fun `a simulated month of loss moves the target once a week, and each move is explained`() {
        // THE COMPLETION CRITERION for this step, from the design.
        //
        // Twenty-nine days, not twenty-eight: with the first revision a week after the first
        // reading, four revisions need twenty-nine days of readings. Simulated and counted, not
        // reasoned about.
        //
        // Losing 150 g a day — about a kilo a week. At the fixture's own goal rate of half a kilo a
        // week the weekly change is around 8 kcal, which rounds to nothing at all half the time;
        // see `at an ordinary rate of loss the weekly change is very small`, which is the more
        // interesting finding.
        val readings = dailyReadings(
            from = TEST_EPOCH_DAY - 28,
            days = 29,
            startKg = 80.0,
            dailyChangeKg = -0.15,
        )

        var last: TargetRevision? = null
        val revisions = mutableListOf<TargetRevision>()

        for (day in (TEST_EPOCH_DAY - 28)..TEST_EPOCH_DAY) {
            val soFar = readings.filter { it.epochDay <= day }
            val revision = TargetRevisionRule.revise(
                profile = profile,
                trend = WeightTrend.of(soFar),
                last = last,
                today = day,
                currentYear = TEST_YEAR,
            )
            if (revision != null) {
                revisions += revision
                last = revision
            }
        }

        assertThat(revisions).hasSize(4)
        assertThat(revisions.map { it.epochDay })
            .containsExactly(
                TEST_EPOCH_DAY - 21,
                TEST_EPOCH_DAY - 14,
                TEST_EPOCH_DAY - 7,
                TEST_EPOCH_DAY,
            ).inOrder()

        // Every revision after the first knows what it changed from, which is what "explained"
        // requires. The first has nothing before it and is not announced.
        assertThat(revisions.first().previousKcal).isNull()
        assertThat(revisions.drop(1).all { it.previousKcal != null }).isTrue()

        // Losing weight lowers the target, gradually rather than in one lurch.
        // Simulated: changes of -10, -20 and -10 kcal.
        assertThat(revisions.last().kcal).isLessThan(revisions.first().kcal)
        assertThat(revisions.drop(1).all { it.changeKcal < 0 }).isTrue()
    }

    @Test
    fun `at an ordinary rate of loss the weekly change is very small`() {
        // Not a defect — an honest measurement of what this feature does, recorded so that the
        // decision about whether to announce a change that small is made deliberately. Losing half
        // a kilo a week moves the target by around 8 kcal, which rounds to 10 or to nothing.
        val readings = dailyReadings(
            from = TEST_EPOCH_DAY - 14,
            days = 15,
            startKg = 80.0,
            dailyChangeKg = -0.07,
        )
        val first = TargetRevisionRule.revise(
            profile = profile,
            trend = WeightTrend.of(readings.filter { it.epochDay <= TEST_EPOCH_DAY - 7 }),
            last = null,
            today = TEST_EPOCH_DAY - 7,
            currentYear = TEST_YEAR,
        )!!
        val second = TargetRevisionRule.revise(
            profile = profile,
            trend = WeightTrend.of(readings),
            last = first,
            today = TEST_EPOCH_DAY,
            currentYear = TEST_YEAR,
        )!!

        assertThat(abs(second.changeKcal)).isAtMost(20)
    }

    private fun dailyReadings(
        from: Long,
        days: Int,
        startKg: Double,
        dailyChangeKg: Double = -0.05,
    ): List<WeightReading> = (0 until days).map { n ->
        WeightReading(epochDay = from + n, kg = startKg + dailyChangeKg * n)
    }
}
