package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.HealthRecordState
import com.metaself.app.data.movement.StepAccess
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Settings on older history (D72): a line and the Connect button when it is not allowed, nothing when
 * it is or when the phone cannot offer it. Every kind is allowed here, so the history is the only
 * reason for the button. JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsHistoryRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `older history not allowed is said, and Connect is offered`() {
        val texts = draw(HealthRecordState(historyAllowed = false))

        assertThat(texts).contains(LINE)
        assertThat(texts).contains(CONNECT)
    }

    @Test
    fun `older history allowed says nothing and offers nothing`() {
        val texts = draw(HealthRecordState(historyAllowed = true))

        assertThat(texts).doesNotContain(LINE)
        assertThat(texts).doesNotContain(CONNECT)
    }

    @Test
    fun `a phone that cannot offer older history says nothing and offers nothing`() {
        val texts = draw(HealthRecordState(historyAllowed = null))

        assertThat(texts).doesNotContain(LINE)
        assertThat(texts).doesNotContain(CONNECT)
    }

    @Test
    fun `a kind not allowed still offers Connect`() {
        val texts = draw(HealthRecordState(notAllowed = setOf(HealthKind.SLEEP), historyAllowed = true))

        assertThat(texts).contains(CONNECT)
    }

    private fun draw(record: HealthRecordState): List<String> = render.texts {
        MovementSettingsPage(
            state = SettingsUiState(stepAccess = StepAccess.GRANTED, healthRecord = record),
            onConnectSteps = {},
            onOpenBandReport = {},
            onBack = {},
        )
    }

    private companion object {
        const val LINE = "Reading history older than 30 days is not allowed — tap Connect to allow it."
        const val CONNECT = "Allow MetaSelf to read your health data"
    }
}
