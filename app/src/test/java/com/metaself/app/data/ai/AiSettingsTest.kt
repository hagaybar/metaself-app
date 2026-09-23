package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AiSettingsTest {

    @Test
    fun `a fresh day has the whole ceiling`() {
        assertThat(AiSettings(dailyCeiling = 30, usedToday = 0).remainingToday).isEqualTo(30)
    }

    @Test
    fun `each call spends one`() {
        assertThat(AiSettings(dailyCeiling = 30, usedToday = 7).remainingToday).isEqualTo(23)
    }

    @Test
    fun `a ceiling lowered below what is already spent leaves nothing, never a negative`() {
        assertThat(AiSettings(dailyCeiling = 5, usedToday = 9).remainingToday).isEqualTo(0)
    }

    @Test
    fun `the default is thirty a day and the default model is named in one place`() {
        assertThat(AiSettings().dailyCeiling).isEqualTo(30)
        assertThat(AiSettings().model).isEqualTo(EstimatePrompt.DEFAULT_MODEL)
    }
}
