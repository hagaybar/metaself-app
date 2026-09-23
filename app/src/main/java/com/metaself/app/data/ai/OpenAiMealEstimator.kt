package com.metaself.app.data.ai

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * The one implementation behind [MealEstimator].
 *
 * Everything interesting happens somewhere else: what is sent is [EstimatePrompt], what comes back
 * is [EstimateResponse], and both are pure. This class is the network and nothing else, which is
 * why it is short and why swapping the provider (D2) is an afternoon.
 *
 * The base URL is a parameter so a test can point it at a local server. **No test in this project
 * makes a real network call.**
 */
class OpenAiMealEstimator(
    private val keys: ApiKeyStore,
    private val settings: AiSettingsStore,
    private val client: OkHttpClient,
    private val problems: ProblemLog = ProblemLog.NONE,
    private val baseUrl: String = OPENAI_URL,
) : MealEstimator {

    override suspend fun estimate(description: String, moreDetail: String?): EstimateResult =
        withContext(Dispatchers.IO) { estimating(description, moreDetail).alsoRecorded() }

    /**
     * Every failure is written to the on-device log, and the meal description never is.
     *
     * The log exists to be copied and sent to somebody; what the owner ate is not that person's
     * business, and a log that is not safe to share is a log nobody shares.
     */
    private fun EstimateResult.alsoRecorded(): EstimateResult = also { result ->
        when (result) {
            is EstimateResult.Proposed -> Unit
            is EstimateResult.Refused -> problems.record("estimate refused", result.detail)
            is EstimateResult.Unreadable -> problems.record("estimate unreadable", result.why)
            // The item names are the model's words for what he ate, and never go in the log.
            is EstimateResult.AmountMissing ->
                problems.record("estimate without amounts", "${result.items.size} item(s), twice")
            is EstimateResult.Unreachable -> problems.record("estimate unreachable", "no answer")
            is EstimateResult.NoKey -> Unit
            is EstimateResult.CeilingReached -> Unit
        }
    }

    /**
     * Ask, and if the answer left amounts out, ask once more naming them (D34).
     *
     * Once, not until it complies: each ask is a paid call counted against the day's ceiling, and a
     * model that leaves an amount out twice will not be talked round by a third try. The second ask
     * is not made at all if the first used the last of the day's allowance.
     */
    private suspend fun estimating(description: String, moreDetail: String?): EstimateResult {
        val first = asking(description, moreDetail, missingAmounts = emptyList())
        if (first !is EstimateResult.AmountMissing) return first
        if (settings.settings.first().remainingToday <= 0) return first
        return asking(description, moreDetail, missingAmounts = first.items)
    }

    private suspend fun asking(
        description: String,
        moreDetail: String?,
        missingAmounts: List<String>,
    ): EstimateResult =
        withContext(Dispatchers.IO) {
            val key = keys.key.first()
            if (key.isNullOrBlank()) return@withContext EstimateResult.NoKey

            val current = settings.settings.first()
            if (current.remainingToday <= 0) return@withContext EstimateResult.CeilingReached

            val body = EstimatePrompt.requestBody(
                current.model,
                description,
                moreDetail,
                missingAmounts = missingAmounts,
            )
            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer $key")
                .post(body.toRequestBody(JSON))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    // Counted only when a call actually happened — a refusal still cost money and
                    // still spends allowance; an unreachable server did neither.
                    settings.recordCall()

                    if (!response.isSuccessful) {
                        EstimateResult.Refused(refusalOf(response.code, text))
                    } else {
                        EstimateResponse.parse(text)
                    }
                }
            } catch (expected: IOException) {
                EstimateResult.Unreachable
            } catch (unexpected: Exception) {
                // Decision D8: the failure mode of a habit app is the day it refuses to work — and
                // a crash is the loudest possible refusal. Nothing may escape this seam.
                //
                // The first Test button press crashed on a SecurityException, because the app had
                // no INTERNET permission and a SecurityException is not an IOException. The
                // permission is the fix for that particular bug; this is the fix for the class of
                // bug, and it is the one that matters.
                EstimateResult.Refused(
                    unexpected.message ?: unexpected::class.simpleName ?: "something went wrong",
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
