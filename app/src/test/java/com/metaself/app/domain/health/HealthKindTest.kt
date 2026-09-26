package com.metaself.app.domain.health

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HealthKindTest {

    @Test
    fun `there are thirteen kinds, eleven of them simple readings`() {
        assertThat(HealthKind.entries).hasSize(13)
        assertThat(HealthKind.entries.filter { it.isReading }).hasSize(11)
        assertThat(HealthKind.SLEEP.isReading).isFalse()
        assertThat(HealthKind.EXERCISE.isReading).isFalse()
    }

    @Test
    fun `every reading has a unit, and nothing else does`() {
        HealthKind.entries.forEach { kind ->
            assertThat(kind.unit != null).isEqualTo(kind.isReading)
        }
    }

    @Test
    fun `a stored name reads back, and an unknown one does not`() {
        assertThat(HealthKind.parse("HEART_RATE")).isEqualTo(HealthKind.HEART_RATE)
        assertThat(HealthKind.parse("STRESS")).isNull()
    }
}
