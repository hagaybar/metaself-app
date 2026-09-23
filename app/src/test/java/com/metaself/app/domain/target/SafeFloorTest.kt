package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Sex
import org.junit.jupiter.api.Test

class SafeFloorTest {

    @Test
    fun `the floor is resting burn when resting burn is the higher of the two`() {
        assertThat(SafeFloor.kcal(Sex.MALE, restingBurnKcal = 1700.0)).isEqualTo(1700)
    }

    @Test
    fun `the floor is the conventional minimum for a man when resting burn falls below it`() {
        assertThat(SafeFloor.kcal(Sex.MALE, restingBurnKcal = 1320.0)).isEqualTo(1500)
    }

    @Test
    fun `the conventional minimum for a woman is lower`() {
        assertThat(SafeFloor.kcal(Sex.FEMALE, restingBurnKcal = 1100.0)).isEqualTo(1200)
    }

    @Test
    fun `a woman whose resting burn exceeds the conventional minimum gets her own number`() {
        assertThat(SafeFloor.kcal(Sex.FEMALE, restingBurnKcal = 1450.0)).isEqualTo(1450)
    }
}
