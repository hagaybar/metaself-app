package com.metaself.app.data.ai

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The one implementation behind [MealEstimator].
 *
 * Everything interesting happens somewhere else: what is sent is [EstimatePrompt], what comes back
 * is [EstimateResponse], and both are pure; the call itself is [OpenAiCall], shared with a food's
 * review. This class is D34's second ask and the problem log, which is why it is short and why
 * swapping the provider (D2) is an afternoon.
 *
 * The base URL is a parameter so a test can point it at a local server. **No test in this project
 * makes a real network call.**
 */
class OpenAiMealEstimator(
    keys: ApiKeyStore,
    private val settings: AiSettingsStore,
    client: OkHttpClient,
    profiles: RequestProfileStore,
    private val problems: ProblemLog = ProblemLog.NONE,
    baseUrl: String = OPENAI_URL,
) : MealEstimator {

    /** The key, the ceiling, the POST, the counting and the failures — shared with a review (D54). */
    private val call = OpenAiCall(keys, settings, client, profiles, baseUrl)

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
            // The app's own sentence only: the answer and the names in it are what he ate.
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
        when (
            val outcome = call.send { model, profile ->
                EstimatePrompt.requestBody(model, description, moreDetail, missingAmounts, profile)
            }
        ) {
            is OpenAiCall.Outcome.Body -> EstimateResponse.parse(outcome.text)
            is OpenAiCall.Outcome.Failed -> outcome.failure
        }

    companion object {
        const val OPENAI_URL = OpenAiCall.OPENAI_URL
    }
}
