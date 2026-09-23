package com.metaself.app.ui.screen.weight

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.weight.aReading
import com.metaself.app.ui.ComposeRender
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** JUnit 4 by necessity — Robolectric's runner is JUnit 4. */
@RunWith(RobolectricTestRunner::class)
class WeightEditorScreenRenderTest {

    private val render = ComposeRender()

    @After
    fun tearDown() = render.dispose()

    @Test
    fun `logging a new weight says so, and says which day`() {
        val texts = draw(WeightFormState(epochDay = TEST_EPOCH_DAY), isEdit = false)

        assertThat(texts).contains("Log a weight")
        assertThat(texts.any { it.startsWith("Logging against Today") }).isTrue()
        assertThat(texts).contains("Weight in kilograms")
    }

    @Test
    fun `correcting a reading says that instead, and starts from what was there`() {
        val texts = draw(WeightFormState.from(aReading(kg = 80.5)), isEdit = true)

        assertThat(texts).contains("Change this reading")
        assertThat(texts).contains("80.5")
    }

    @Test
    fun `it warns before replacing a reading a day already has`() {
        val texts = draw(
            WeightFormState(epochDay = TEST_EPOCH_DAY),
            isEdit = false,
            existingDays = setOf(TEST_EPOCH_DAY),
        )

        assertThat(texts.any { it.contains("Saving replaces it") }).isTrue()
    }

    @Test
    fun `correcting a reading does not warn about replacing it, which is the point`() {
        val texts = draw(
            WeightFormState.from(aReading(kg = 80.5)),
            isEdit = true,
            existingDays = setOf(TEST_EPOCH_DAY),
        )

        assertThat(texts.none { it.contains("Saving replaces it") }).isTrue()
    }

    private fun draw(
        initial: WeightFormState,
        isEdit: Boolean,
        existingDays: Set<Long> = emptySet(),
    ): List<String> = render.texts {
        WeightEditorScreen(
            initial = initial,
            todayEpochDay = TEST_EPOCH_DAY,
            isEdit = isEdit,
            existingDays = existingDays,
            onSave = {},
            onCancel = {},
        )
    }
}
