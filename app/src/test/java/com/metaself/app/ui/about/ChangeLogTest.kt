package com.metaself.app.ui.about

import com.google.common.truth.Truth.assertThat
import com.metaself.app.BuildConfig
import org.junit.jupiter.api.Test

/** D134: the change log the About page draws, and the rule that a version bump cannot ship without its entry. */
class ChangeLogTest {

    /** Fails on a bump that did not add its entry at the top — which is the point. */
    @Test
    fun `the newest entry is the version this build is`() {
        assertThat(ChangeLog.entries.first().version).isEqualTo(BuildConfig.VERSION_NAME)
    }

    @Test
    fun `entries run newest first, each version once`() {
        val versions = ChangeLog.entries.map { it.version }

        versions.zipWithNext().forEach { (newer, older) ->
            assertThat(ChangeLog.compare(newer, older)).isGreaterThan(0)
        }
    }

    @Test
    fun `dates never run forwards down the log`() {
        ChangeLog.entries.zipWithNext().forEach { (newer, older) ->
            assertThat(newer.date).isAtLeast(older.date)
        }
    }

    @Test
    fun `the log goes back to 0_67_0`() {
        assertThat(ChangeLog.entries.last().version).isEqualTo("0.67.0")
    }

    /** D134: an entry says what changed on screen, never a decision number, an issue number or a code name. */
    @Test
    fun `no entry names a decision or an issue`() {
        ChangeLog.entries.forEach { entry ->
            assertThat(entry.sentence).doesNotContainMatch("\\bD\\d+")
            assertThat(entry.sentence).doesNotContain("#")
            assertThat(entry.sentence).endsWith(".")
        }
    }

    @Test
    fun `versions compare by number, not by spelling`() {
        assertThat(ChangeLog.compare("0.100.0", "0.99.0")).isGreaterThan(0)
        assertThat(ChangeLog.compare("1.0.0", "0.81.0")).isGreaterThan(0)
        assertThat(ChangeLog.compare("0.81.0", "0.81.0")).isEqualTo(0)
    }
}
