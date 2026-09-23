package com.metaself.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class VersionMarkerTest {

    @Test
    fun `states the version name and the build number`() {
        assertThat(VersionMarker.line("0.1.0-rc1", 1)).isEqualTo("v0.1.0-rc1 (1)")
    }

    @Test
    fun `carries the build number because two builds can share a version name`() {
        assertThat(VersionMarker.line("0.1.0-rc1", 2)).isEqualTo("v0.1.0-rc1 (2)")
    }
}
