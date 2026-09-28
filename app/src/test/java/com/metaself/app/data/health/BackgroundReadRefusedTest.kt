package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Which failures are Health Connect refusing a read from the background. The messages are invented. */
class BackgroundReadRefusedTest {

    @Test
    fun `a security exception naming the foreground is a background refusal`() {
        val failure = SecurityException("Caller must be in the Foreground to read")

        val refused = BackgroundReadRefused.from(failure)

        assertThat(refused).isNotNull()
        assertThat(refused!!.cause).isSameInstanceAs(failure)
    }

    @Test
    fun `a security exception naming the background-read permission is one too`() {
        assertThat(BackgroundReadRefused.from(SecurityException("missing android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND")))
            .isNotNull()
    }

    @Test
    fun `any other failure is not`() {
        assertThat(BackgroundReadRefused.from(SecurityException("Caller doesn't have READ_STEPS"))).isNull()
        assertThat(BackgroundReadRefused.from(SecurityException())).isNull()
        assertThat(BackgroundReadRefused.from(IllegalStateException("foreground"))).isNull()
    }

    @Test
    fun `one already made is passed on as it is`() {
        val refused = BackgroundReadRefused(SecurityException("foreground"))

        assertThat(BackgroundReadRefused.from(refused)).isSameInstanceAs(refused)
    }
}
