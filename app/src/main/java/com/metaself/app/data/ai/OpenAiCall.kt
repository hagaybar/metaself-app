package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * One call to the model, and everything about it that is not what is asked or what the answer means:
 * the key, the day's ceiling, the POST, the counting and the closed set of failures.
 *
 * Shared by everything that asks the model something (D54 §6) — the meal estimator, and a food's
 * review — so the two cannot drift in how they count or how they fail. What is sent and what the
 * answer means stay with each caller, pure.
 *
 * The base URL is a parameter so a test can point it at a local server. **No test in this project
 * makes a real network call.**
 */
class OpenAiCall(
    private val keys: ApiKeyStore,
    private val settings: AiSettingsStore,
    private val client: OkHttpClient,
    private val baseUrl: String = OPENAI_URL,
) {

    /** What one call came to: the answer's text, or one of the call's own failures. */
    sealed interface Outcome {

        data class Body(val text: String) : Outcome

        /**
         * [failure] is [EstimateResult.NoKey], [EstimateResult.CeilingReached],
         * [EstimateResult.Unreachable] or [EstimateResult.Refused] — the call's failures, so a
         * caller's existing sentences for them are reused unchanged. Whether an answer is readable
         * is the caller's to judge.
         *
         * @property status the provider's HTTP status when it answered with a refusal, else null —
         *   something a caller can log without logging the provider's words, which may quote back
         *   what was sent.
         */
        data class Failed(val failure: EstimateResult, val status: Int? = null) : Outcome {
            init {
                require(
                    failure is EstimateResult.NoKey || failure is EstimateResult.CeilingReached ||
                        failure is EstimateResult.Unreachable || failure is EstimateResult.Refused,
                ) { "a call fails for want of a key, allowance, network or permission; not $failure" }
            }
        }
    }

    /**
     * Check the key and the ceiling, then send the body [build] makes for the model in settings.
     *
     * Nothing is built or sent without a key or with the day's allowance spent. Never throws.
     */
    suspend fun send(build: (model: String) -> String): Outcome = withContext(Dispatchers.IO) {
        val key = keys.key.first()
        if (key.isNullOrBlank()) return@withContext Outcome.Failed(EstimateResult.NoKey)

        val current = settings.settings.first()
        if (current.remainingToday <= 0) {
            return@withContext Outcome.Failed(EstimateResult.CeilingReached)
        }

        try {
            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer $key")
                .post(build(current.model).toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                // Counted only when a call actually happened — a refusal still cost money and
                // still spends allowance; an unreachable server did neither.
                settings.recordCall()

                if (!response.isSuccessful) {
                    Outcome.Failed(EstimateResult.Refused(refusalOf(response.code, text)), response.code)
                } else {
                    Outcome.Body(text)
                }
            }
        } catch (expected: IOException) {
            Outcome.Failed(EstimateResult.Unreachable)
        } catch (unexpected: Exception) {
            // Decision D8: the failure mode of a habit app is the day it refuses to work — and a
            // crash is the loudest possible refusal. Nothing may escape this seam.
            //
            // The first Test button press crashed on a SecurityException, because the app had no
            // INTERNET permission and a SecurityException is not an IOException. The permission is
            // the fix for that particular bug; this is the fix for the class of bug, and it is the
            // one that matters.
            Outcome.Failed(
                EstimateResult.Refused(
                    unexpected.message ?: unexpected::class.simpleName ?: "something went wrong",
                ),
            )
        }
    }

    /**
     * The provider's own words where they are usable, and the status where they are not.
     *
     * Their message is more useful than anything this app could invent — "incorrect API key" tells
     * the owner exactly what to fix — but it is their text, shown as theirs.
     */
    private fun refusalOf(code: Int, body: String): String =
        Regex(""""message"\s*:\s*"([^"]{0,200})"""").find(body)?.groupValues?.get(1)
            ?: "the provider answered $code"

    companion object {
        const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
