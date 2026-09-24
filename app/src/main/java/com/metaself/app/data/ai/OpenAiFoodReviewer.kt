package com.metaself.app.data.ai

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.FoodReviewer
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import okhttp3.OkHttpClient

/**
 * The one implementation behind [FoodReviewer] (D54).
 *
 * What is sent is [ReviewPrompt] and what comes back is [ReviewResponse], both pure; the call is
 * [OpenAiCall], shared with the meal estimator, so the key, the day's ceiling, the timeout, the
 * counting and the failures are one code path (§6). One call, no retry: D34's second ask is for
 * missing amounts, and a review has none.
 *
 * The base URL is a parameter so a test can point it at a local server. **No test in this project
 * makes a real network call.**
 */
class OpenAiFoodReviewer(
    keys: ApiKeyStore,
    settings: AiSettingsStore,
    client: OkHttpClient,
    private val problems: ProblemLog = ProblemLog.NONE,
    baseUrl: String = OpenAiCall.OPENAI_URL,
) : FoodReviewer {

    private val call = OpenAiCall(keys, settings, client, baseUrl)

    override suspend fun review(request: ReviewRequest): ReviewResult =
        when (val outcome = call.send { model -> ReviewPrompt.requestBody(model, request) }) {
            is OpenAiCall.Outcome.Body -> ReviewResponse.parse(outcome.text, request).alsoRecorded(null)
            is OpenAiCall.Outcome.Failed -> ReviewResult.Failed(outcome.failure).alsoRecorded(outcome.status)
        }

    /**
     * Every failure is written to the on-device log, and the food's name never is (D54 §6).
     *
     * The log exists to be copied and sent to somebody; a food's name is what he eats, which is not
     * that person's business — the meal estimator's rule for a description.
     *
     * **A refusal is logged by its kind and [status] only, never in the provider's words**: those
     * can quote back what was sent, name included. The words are still shown on screen, where he
     * reads them himself. A refusal with no status is one this app raised in making the call.
     */
    private fun ReviewResult.alsoRecorded(status: Int?): ReviewResult = also { result ->
        if (result !is ReviewResult.Failed) return@also
        when (val failure = result.failure) {
            is EstimateResult.Refused -> problems.record(
                "review refused",
                if (status != null) "the provider answered $status" else "the call could not be made",
            )
            is EstimateResult.Unreadable -> problems.record("review unreadable", failure.why)
            is EstimateResult.Unreachable -> problems.record("review unreachable", "no answer")
            is EstimateResult.NoKey,
            is EstimateResult.CeilingReached,
            is EstimateResult.Proposed,
            is EstimateResult.AmountMissing,
            -> Unit
        }
    }
}
