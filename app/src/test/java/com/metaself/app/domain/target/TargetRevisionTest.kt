package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test

class TargetRevisionTest {

    @Test
    fun `a revision records when it happened, from what weight, and to what number`() {
        val revision = TargetRevision(
            epochDay = TEST_EPOCH_DAY,
            trendKg = 79.2,
            kcal = 2050,
            previousKcal = 2090,
        )

        assertThat(revision.changeKcal).isEqualTo(-40)
        assertThat(revision.isChange).isTrue()
    }

    @Test
    fun `a revision that changed nothing says so, because it is not worth announcing`() {
        val revision = TargetRevision(
            epochDay = TEST_EPOCH_DAY,
            trendKg = 80.0,
            kcal = 2090,
            previousKcal = 2090,
        )

        assertThat(revision.changeKcal).isEqualTo(0)
        assertThat(revision.isChange).isFalse()
    }

    @Test
    fun `the first revision has no previous number and is therefore not a change to announce`() {
        val first = TargetRevision(
            epochDay = TEST_EPOCH_DAY,
            trendKg = 79.2,
            kcal = 2050,
            previousKcal = null,
        )

        assertThat(first.isChange).isFalse()
        assertThat(first.changeKcal).isEqualTo(0)
    }
}
