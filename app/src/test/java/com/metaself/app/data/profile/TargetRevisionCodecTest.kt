package com.metaself.app.data.profile

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.target.TargetRevision
import org.junit.jupiter.api.Test

class TargetRevisionCodecTest {

    private val revision = TargetRevision(
        epochDay = TEST_EPOCH_DAY,
        trendKg = 79.2,
        kcal = 2050,
        previousKcal = 2090,
    )

    @Test
    fun `a revision survives a round trip`() {
        assertThat(TargetRevisionCodec.decode(TargetRevisionCodec.encode(revision)))
            .isEqualTo(revision)
    }

    @Test
    fun `a first revision, with nothing before it, survives too`() {
        val first = revision.copy(previousKcal = null)
        assertThat(TargetRevisionCodec.decode(TargetRevisionCodec.encode(first))).isEqualTo(first)
    }

    @Test
    fun `nothing stored decodes to no revision`() {
        assertThat(TargetRevisionCodec.decode(emptyMap())).isNull()
    }

    @Test
    fun `a half-written record decodes to no revision rather than a wrong one`() {
        val partial = TargetRevisionCodec.encode(revision) - TargetRevisionCodec.KEY_KCAL
        assertThat(TargetRevisionCodec.decode(partial)).isNull()
    }
}
