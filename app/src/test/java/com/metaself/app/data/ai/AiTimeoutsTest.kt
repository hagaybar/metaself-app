package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.di.AiModule
import org.junit.jupiter.api.Test

class AiTimeoutsTest {

    /** The read timeout was left at OkHttp's ten seconds under a longer call timeout. */
    @Test
    fun `the client the app uses waits a minute to read, not OkHttp's ten seconds`() {
        val client = AiModule.provideHttpClient()

        assertThat(client.readTimeoutMillis).isEqualTo(60_000)
        assertThat(client.writeTimeoutMillis).isEqualTo(60_000)
        assertThat(client.callTimeoutMillis).isEqualTo(60_000)
        assertThat(client.connectTimeoutMillis).isEqualTo(15_000)
    }

    @Test
    fun `a final analysis waits two minutes to read and in all`() {
        val client = AiTimeouts.deep(AiModule.provideHttpClient().newBuilder()).build()

        assertThat(client.readTimeoutMillis).isEqualTo(120_000)
        assertThat(client.callTimeoutMillis).isEqualTo(120_000)
    }
}
