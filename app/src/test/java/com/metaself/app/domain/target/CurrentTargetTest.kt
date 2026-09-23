package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import org.junit.jupiter.api.Test

class CurrentTargetTest {

    private val profile = aProfile(weightKg = 80.0)

    @Test
    fun `with no revision, the target is the one setup produced`() {
        val target = CurrentTarget.of(profile, revision = null, currentYear = TEST_YEAR)

        assertThat(target.kcal).isEqualTo(DailyTargetCalculator.of(profile, TEST_YEAR).kcal)
    }

    @Test
    fun `with a revision, the target is worked out from the revised weight`() {
        val revision = TargetRevision(
            epochDay = TEST_EPOCH_DAY,
            trendKg = 78.0,
            kcal = 0,
            previousKcal = null,
        )

        val target = CurrentTarget.of(profile, revision, TEST_YEAR)

        assertThat(target.kcal)
            .isEqualTo(DailyTargetCalculator.of(profile.copy(weightKg = 78.0), TEST_YEAR).kcal)
    }

    @Test
    fun `the weight the target used is reported, so two numbers cannot silently disagree`() {
        assertThat(CurrentTarget.weightUsedKg(profile, revision = null)).isEqualTo(80.0)

        val revision = TargetRevision(TEST_EPOCH_DAY, trendKg = 78.0, kcal = 0, previousKcal = null)
        assertThat(CurrentTarget.weightUsedKg(profile, revision)).isEqualTo(78.0)
    }

    @Test
    fun `the setup weight is never overwritten by a revision`() {
        val revision = TargetRevision(TEST_EPOCH_DAY, trendKg = 78.0, kcal = 0, previousKcal = null)

        CurrentTarget.of(profile, revision, TEST_YEAR)

        assertThat(profile.weightKg).isEqualTo(80.0)
    }
}
