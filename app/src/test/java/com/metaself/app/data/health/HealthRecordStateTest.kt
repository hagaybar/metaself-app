package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.health.HealthKind
import org.junit.jupiter.api.Test

/** The pure rule behind Settings' health-record line (D65, D66). Every figure is invented. */
class HealthRecordStateTest {

    private fun row(kind: HealthKind, tokenAtMillis: Long?, catchUpDone: Boolean) = HealthSyncEntity(
        kind = kind.name, changesToken = "t", tokenAtMillis = tokenAtMillis,
        catchUpCursorMillis = 0, catchUpDone = catchUpDone,
    )

    @Test
    fun `nothing granted leaves notAllowed empty`() {
        val state = HealthRecordState.from(days = 0, earliest = null, syncRows = emptyList(), granted = emptySet())

        assertThat(state.notAllowed).isEmpty()
    }

    @Test
    fun `some granted lists the rest`() {
        val state = HealthRecordState.from(
            days = 3, earliest = 20_699,
            syncRows = listOf(row(HealthKind.STEPS, 1_000, catchUpDone = true)),
            granted = setOf(HealthKind.STEPS),
        )

        assertThat(state.notAllowed).isEqualTo(HealthKind.entries.toSet() - HealthKind.STEPS)
    }

    @Test
    fun `a granted kind with no row is still catching up`() {
        val state = HealthRecordState.from(
            days = 0, earliest = null, syncRows = emptyList(), granted = setOf(HealthKind.STEPS),
        )

        assertThat(state.catchingUp).isTrue()
    }

    @Test
    fun `every granted kind done means not catching up`() {
        val state = HealthRecordState.from(
            days = 5, earliest = 20_699,
            syncRows = listOf(
                row(HealthKind.STEPS, 1_000, catchUpDone = true),
                row(HealthKind.HEART_RATE, 2_000, catchUpDone = true),
            ),
            granted = setOf(HealthKind.STEPS, HealthKind.HEART_RATE),
        )

        assertThat(state.catchingUp).isFalse()
    }

    @Test
    fun `last copied is the newest of the tokens taken`() {
        val state = HealthRecordState.from(
            days = 5, earliest = 20_699,
            syncRows = listOf(
                row(HealthKind.STEPS, 1_000, catchUpDone = true),
                row(HealthKind.HEART_RATE, 3_000, catchUpDone = true),
            ),
            granted = setOf(HealthKind.STEPS, HealthKind.HEART_RATE),
        )

        assertThat(state.lastCopiedMillis).isEqualTo(3_000)
    }
}
