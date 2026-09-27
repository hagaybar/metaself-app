package com.metaself.app.ui.screen.settings

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.Arrival
import com.metaself.app.domain.health.ArrivedWorkout
import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.ui.ComposeRender
import com.metaself.app.ui.health.BandReportWording
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * "What the band sends" drawn (D80): what is on it and in which order, and that Copy as text calls
 * back. Nothing about size or wrapping (`CLAUDE.md`). Every count is invented; the app is invented.
 * JUnit 4 because Robolectric's runner is.
 */
@RunWith(RobolectricTestRunner::class)
class BandReportRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    private val today = TEST_EPOCH_DAY
    private val report = BandReport.of(
        BandReport.fromDayFor(today), today,
        arrivals = listOf(Arrival(HealthKind.STEPS, BAND, today, 300)),
        workouts = listOf(ArrivedWorkout(today, WorkoutKind.WALK, typed = false, origin = BAND, true, false, true, false)),
        days = emptyList(),
    )

    @Test
    fun `every kind is listed in order, each with what arrived`() {
        val texts = page(BandReportUiState(report = report, labels = mapOf(BAND to "Example Band"), today = today), setOf(HealthKind.WEIGHT))

        assertThat(texts).contains("What the band sends")
        val names = HealthKind.entries.map { it.displayName }
        assertThat(texts).containsAtLeastElementsIn(names).inOrder()
        assertThat(texts).contains("300 readings · 1 day · from Example Band")
        assertThat(texts).contains("first today · last today")
        assertThat(texts).contains("nothing arrived")
        assertThat(texts).contains("not allowed")
    }

    @Test
    fun `workouts, the daily summary, the line on what cannot arrive, and Copy follow the kinds`() {
        val texts = page(BandReportUiState(report = report, today = today))

        assertThat(texts).containsAtLeast(
            "Body fat", "1 workout", "Walking 1", "copied 1 · typed 0",
            "steps 0 of 30 days", "workouts 0 of 30 days", BandReportWording.NOT_SHARED, "Copy as text",
        ).inOrder()
    }

    @Test
    fun `Copy as text calls back`() {
        var copied = 0
        render.texts(heightPx = TALL) {
            BandReportPage(state = BandReportUiState(report = report, today = today), notAllowed = emptySet(), onCopy = { copied++ }, onBack = {})
        }

        render.click("Copy as text")

        assertThat(copied).isEqualTo(1)
    }

    @Test
    fun `a read that failed is said, and nothing is offered to copy`() {
        val texts = page(BandReportUiState(unreadable = true, today = today))

        assertThat(texts).contains("The health record could not be read; Recent problems says why.")
        assertThat(texts).doesNotContain("Copy as text")
    }

    @Test
    fun `while reading, only the title is there`() {
        val texts = page(BandReportUiState())

        assertThat(texts).contains("What the band sends")
        assertThat(texts).doesNotContain("Copy as text")
        assertThat(texts).doesNotContain("Steps")
    }

    private fun page(state: BandReportUiState, notAllowed: Set<HealthKind> = emptySet()): List<String> =
        render.texts(heightPx = TALL) {
            BandReportPage(state = state, notAllowed = notAllowed, onCopy = {}, onBack = {})
        }

    private companion object {
        const val TALL = 20_000
        const val BAND = "com.example.band"
    }
}
