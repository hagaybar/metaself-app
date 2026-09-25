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
import kotlin.coroutines.cancellation.CancellationException

/**
 * One call to the model, and everything about it that is not what is asked or what the answer means:
 * the key, the day's ceiling, the POST, the counting, the closed set of failures, and learning what
 * the model accepts (D57).
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
    private val profiles: RequestProfileStore,
    private val baseUrl: String = OPENAI_URL,
) {

    /** What one call came to: the answer's text, or one of the call's own failures. */
    sealed interface Outcome {

        /**
         * The answer's text, and how the request that got it was sent (D57).
         *
         * @property model the model's name as settings held it for this call.
         * @property profile what the answered request was sent with — the one to report and, once
         *   the caller has read the answer, to [remember].
         * @property alreadyRemembered whether [profile] is what was remembered for [model] already.
         */
        data class Body(
            val text: String,
            val model: String,
            val profile: RequestProfile,
            val alreadyRemembered: Boolean,
        ) : Outcome

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
     * Check the key and the ceiling, then send the body [build] makes for the model in settings and
     * the profile it is sent with (D57).
     *
     * The profile is the one remembered for the model, or the first guess. A 400 refusal that a
     * different profile can answer ([RequestFix]) is answered by sending again with it — at most
     * [RequestFix.MAX_RETRIES] times, never the same profile twice in one call, and never with the
     * day's allowance spent. **Every request that reaches the provider is counted**, a refused one
     * included. The answer comes back with the profile that got it; the caller [remember]s it once
     * the answer has been read — an answer in the wrong shape teaches nothing. A refusal nothing can
     * answer is handed back as it always was.
     *
     * Nothing is built or sent without a key or with the day's allowance spent. Never throws.
     */
    suspend fun send(build: (model: String, profile: RequestProfile) -> String): Outcome =
        withContext(Dispatchers.IO) {
            val key = keys.key.first()
            if (key.isNullOrBlank()) return@withContext Outcome.Failed(EstimateResult.NoKey)

            val current = settings.settings.first()
            if (current.remainingToday <= 0) {
                return@withContext Outcome.Failed(EstimateResult.CeilingReached)
            }

            try {
                learning(key, current.model, build)
            } catch (expected: IOException) {
                Outcome.Failed(EstimateResult.Unreachable())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unexpected: Exception) {
                // Decision D8: the failure mode of a habit app is the day it refuses to work — and a
                // crash is the loudest possible refusal. Nothing may escape this seam.
                //
                // The first Test button press crashed on a SecurityException, because the app had no
                // INTERNET permission and a SecurityException is not an IOException. The permission
                // is the fix for that particular bug; this is the fix for the class of bug, and it is
                // the one that matters.
                Outcome.Failed(
                    EstimateResult.Refused(
                        unexpected.message ?: unexpected::class.simpleName ?: "something went wrong",
                    ),
                )
            }
        }

    /** Send, and on a refusal a profile can answer, send again with it (D57 §3, §4, §5). */
    private suspend fun learning(
        key: String,
        model: String,
        build: (model: String, profile: RequestProfile) -> String,
    ): Outcome {
        val remembered = rememberedFor(model)
        var profile = remembered ?: RequestProfile.guess(model)
        val tried = mutableSetOf(profile)
        var retries = 0
        var refusedFirst: String? = null
        while (true) {
            val answer = try {
                post(key, build(model, profile))
            } catch (lost: IOException) {
                // A retry that could not be sent still says why it was being sent.
                return Outcome.Failed(EstimateResult.Unreachable(afterRefusal = refusedFirst))
            }
            if (answer.code in 200..299) {
                return Outcome.Body(answer.text, model, profile, alreadyRemembered = profile == remembered)
            }
            val words = refusalOf(answer.code, answer.text)
            val refused = Outcome.Failed(EstimateResult.Refused(words), answer.code)
            if (retries >= RequestFix.MAX_RETRIES) return refused
            val next = nextProfile(answer, profile, tried) ?: return refused
            if (settings.settings.first().remainingToday <= 0) return refused
            tried += next
            profile = next
            retries++
            refusedFirst = words
        }
    }

    /** One HTTP status and body. */
    private class Answer(val code: Int, val text: String)

    /** One request, counted once it reached the provider — a refusal still cost a request. */
    private suspend fun post(key: String, body: String): Answer {
        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $key")
            .post(body.toRequestBody(JSON))
            .build()
        val answer = client.newCall(request).execute().use { response ->
            Answer(response.code, response.body?.string().orEmpty())
        }
        // Counted only when a call actually happened — a refusal still cost money and still spends
        // allowance; an unreachable server did neither.
        settings.recordCall()
        return answer
    }

    /** The first profile not yet tried that answers a 400 refusal, or null (D57 §3). */
    private fun nextProfile(answer: Answer, sent: RequestProfile, tried: Set<RequestProfile>): RequestProfile? {
        if (answer.code != 400) return null
        val refusal = ProviderRefusal.parse(answer.text) ?: return null
        return RequestFix.candidates(sent, refusal).firstOrNull { it !in tried }
    }

    /**
     * Remember how [answer] was asked for, once the caller has read it (D57 §5). Nothing is written
     * when it is what was remembered already. Never throws: an answer that came is not lost because
     * remembering how it was asked for failed — the next call learns again.
     */
    suspend fun remember(answer: Outcome.Body) {
        if (answer.alreadyRemembered) return
        try {
            profiles.remember(answer.model, answer.profile)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ignored: Exception) {
            // Nothing remembered: the next call learns again, from the guess.
        }
    }

    /** What is remembered for [model]; a store that cannot be read is nothing remembered. */
    private suspend fun rememberedFor(model: String): RequestProfile? = try {
        profiles.profileFor(model).first()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unreadable: Exception) {
        null
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
