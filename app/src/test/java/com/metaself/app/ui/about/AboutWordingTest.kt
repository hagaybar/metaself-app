package com.metaself.app.ui.about

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** D134: the lines of About this app and its row on the Settings index. Version numbers invented. */
class AboutWordingTest {

    @Test
    fun `names the app and its version`() {
        assertThat(AboutWording.title("0.1.0")).isEqualTo("MetaSelf 0.1.0")
    }

    /** Two builds can share a version name; the build number is what tells them apart. */
    @Test
    fun `carries the build number, and says it is the one installed`() {
        assertThat(AboutWording.build(2)).isEqualTo("Build 2 · installed version")
    }

    @Test
    fun `an entry's heading is its version and the day, without the year`() {
        val entry = ChangeLogEntry("0.1.0", LocalDate.of(2026, 10, 11), "Something changed.")

        assertThat(AboutWording.heading(entry)).isEqualTo("0.1.0 · 11 Oct")
    }

    @Test
    fun `the index line is the version and what the page holds`() {
        assertThat(AboutWording.indexLine("0.1.0")).isEqualTo("Version 0.1.0 · what changed")
    }
}
