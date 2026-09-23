package com.metaself.app.ui.target

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.target.TargetRevision
import org.junit.jupiter.api.Test

class RevisionWordingTest {

    @Test
    fun `a target that fell says by how much, and from what weight`() {
        val revision =
            TargetRevision(TEST_EPOCH_DAY, trendKg = 79.2, kcal = 2050, previousKcal = 2090)

        val notice = RevisionWording.notice(revision)!!

        assertThat(notice).contains("2,090")
        assertThat(notice).contains("2,050")
        assertThat(notice).contains("79.2 kg")
    }

    @Test
    fun `a target that rose says so in the same shape`() {
        val revision =
            TargetRevision(TEST_EPOCH_DAY, trendKg = 81.0, kcal = 2140, previousKcal = 2090)

        assertThat(RevisionWording.notice(revision)!!).contains("2,140")
    }

    @Test
    fun `the first revision announces nothing, because nothing changed`() {
        val first = TargetRevision(TEST_EPOCH_DAY, trendKg = 79.2, kcal = 2050, previousKcal = null)

        assertThat(RevisionWording.notice(first)).isNull()
    }

    @Test
    fun `a revision that landed on the same number announces nothing`() {
        val same = TargetRevision(TEST_EPOCH_DAY, trendKg = 79.9, kcal = 2090, previousKcal = 2090)

        assertThat(RevisionWording.notice(same)).isNull()
    }

    @Test
    fun `the weight the target now follows is stated for the profile screen`() {
        assertThat(RevisionWording.weightUsed(79.24, fromTrend = true))
            .isEqualTo("Worked out from your weight trend, 79.2 kg")
        assertThat(RevisionWording.weightUsed(80.0, fromTrend = false))
            .isEqualTo("Worked out from the weight you entered, 80.0 kg")
    }
}
