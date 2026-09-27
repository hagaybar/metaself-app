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

    // --- Older history (D72) --------------------------------------------------------------------

    /** The copying's older-history marker shares the table; it is not a kind and never counted as one. */
    @Test
    fun `the older-history marker is neither a kind nor a copy`() {
        val state = HealthRecordState.from(
            days = 5, earliest = 20_699,
            syncRows = listOf(
                row(HealthKind.STEPS, 1_000, catchUpDone = true),
                HealthSyncEntity(kind = HealthStore.HISTORY_MARKER, tokenAtMillis = 9_000, catchUpDone = false),
            ),
            granted = setOf(HealthKind.STEPS),
        )

        assertThat(state.catchingUp).isFalse()
        assertThat(state.lastCopiedMillis).isEqualTo(1_000)
        assertThat(state.notAllowed).isEqualTo(HealthKind.entries.toSet() - HealthKind.STEPS)
    }

    @Test
    fun `older history not allowed asks for Connect`() {
        val state = HealthRecordState.from(
            days = 5, earliest = 20_699, syncRows = emptyList(), granted = HealthKind.entries.toSet(), history = false,
        )

        assertThat(state.historyAllowed).isFalse()
        assertThat(state.notAllowed).isEmpty()
        assertThat(state.asksToConnect).isTrue()
    }

    @Test
    fun `older history allowed, or not offered by the phone, asks for nothing`() {
        val allowed = HealthRecordState.from(
            days = 5, earliest = 20_699, syncRows = emptyList(), granted = HealthKind.entries.toSet(), history = true,
        )
        val notOffered = HealthRecordState.from(
            days = 5, earliest = 20_699, syncRows = emptyList(), granted = HealthKind.entries.toSet(), history = null,
        )

        assertThat(allowed.historyAllowed).isTrue()
        assertThat(allowed.asksToConnect).isFalse()
        assertThat(notOffered.historyAllowed).isNull()
        assertThat(notOffered.asksToConnect).isFalse()
    }

    /** As with [HealthRecordState.notAllowed]: with nothing granted, the "Off" line already says it. */
    @Test
    fun `with nothing granted, older history is not mentioned`() {
        val state = HealthRecordState.from(
            days = 0, earliest = null, syncRows = emptyList(), granted = emptySet(), history = false,
        )

        assertThat(state.historyAllowed).isNull()
    }

    /** [HealthRecordState.historyOffered] is never folded into null: the Connect button needs it even
     * with nothing granted yet, unlike [HealthRecordState.historyAllowed]. */
    @Test
    fun `history offered is kept even with nothing granted`() {
        val state = HealthRecordState.from(
            days = 0, earliest = null, syncRows = emptyList(), granted = emptySet(), historyOffered = true,
        )

        assertThat(state.historyOffered).isTrue()
        assertThat(state.historyAllowed).isNull()
    }

    @Test
    fun `a kind not allowed still asks for Connect`() {
        val state = HealthRecordState.from(
            days = 5, earliest = 20_699, syncRows = emptyList(), granted = setOf(HealthKind.STEPS), history = true,
        )

        assertThat(state.asksToConnect).isTrue()
    }
}
