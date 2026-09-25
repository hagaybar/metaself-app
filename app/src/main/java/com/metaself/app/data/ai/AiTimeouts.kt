package com.metaself.app.data.ai

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * How long a request to the model may take, in one place.
 *
 * Every limit is set, not only the call's: OkHttp's defaults (ten seconds each to connect, write and
 * read) otherwise stay in force underneath a longer call timeout, and the read timeout fires first
 * on a model that thinks before it answers.
 */
object AiTimeouts {

    /** Every everyday request: the estimate, a conversation's questions, a review, *Test it*. */
    const val EVERYDAY_SECONDS = 60L

    /** A conversation's final analysis, asked to think harder (D58 §8.6). */
    const val DEEP_SECONDS = 120L

    /** Connecting is not where a model spends its time; a network that cannot connect in this, cannot. */
    const val CONNECT_SECONDS = 15L

    fun everyday(builder: OkHttpClient.Builder): OkHttpClient.Builder = within(builder, EVERYDAY_SECONDS)

    fun deep(builder: OkHttpClient.Builder): OkHttpClient.Builder = within(builder, DEEP_SECONDS)

    private fun within(builder: OkHttpClient.Builder, seconds: Long): OkHttpClient.Builder = builder
        .connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(seconds, TimeUnit.SECONDS)
        .readTimeout(seconds, TimeUnit.SECONDS)
        .callTimeout(seconds, TimeUnit.SECONDS)
}
