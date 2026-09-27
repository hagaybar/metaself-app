package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** What the Connect button asks for (D66, D72). */
class HealthPermissionsTest {

    @Test
    fun `Connect also asks for history older than thirty days`() {
        assertThat(HealthPermissions.ALL).contains("android.permission.health.READ_HEALTH_DATA_HISTORY")
    }

    @Test
    fun `Connect still asks for every kind`() {
        assertThat(HealthPermissions.ALL).containsAtLeastElementsIn(
            com.metaself.app.domain.health.HealthKind.entries.map(HealthPermissions::of),
        )
    }

    @Test
    fun `without history drops just the history permission`() {
        assertThat(HealthPermissions.withoutHistory)
            .doesNotContain("android.permission.health.READ_HEALTH_DATA_HISTORY")
        assertThat(HealthPermissions.withoutHistory).containsAtLeastElementsIn(
            com.metaself.app.domain.health.HealthKind.entries.map(HealthPermissions::of),
        )
    }
}
