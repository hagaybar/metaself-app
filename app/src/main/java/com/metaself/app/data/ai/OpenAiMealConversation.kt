package com.metaself.app.data.ai

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.Asked
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealConversationAsker
import com.metaself.app.domain.ai.StepResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The requests of a conversation about one meal, over [OpenAiCall] (D58). What is sent is
 * [ConversationPrompt] and what it means is [ConversationResponse], both pure; this is the counting,
 * D57's remembering, D34's second ask, and the problem log.
 *
 * **The problem log never gets the description, a question, an answer or an option** — a question
 * quotes the meal (§9). Only the kind of failure goes in it, and a refusal's provider words, as the
 * estimator already logs them.
 */
class OpenAiMealConversation(
    keys: ApiKeyStore,
    private val settings: AiSettingsStore,
    client: OkHttpClient,
    profiles: RequestProfileStore,
    private val problems: ProblemLog = ProblemLog.NONE,
    baseUrl: String = OpenAiCall.OPENAI_URL,
) : MealConversationAsker {

    private val call = OpenAiCall(keys, settings, client, profiles, baseUrl)

    override suspend fun open(description: String): StepResult = withContext(Dispatchers.IO) {
        val (outcome, text) = stepping { model, profile -> ConversationPrompt.opening(model, description, profile) }
        val result = text?.let(ConversationResponse::opening)
            ?: StepResult.Failed((outcome as OpenAiCall.Outcome.Failed).failure)
        rememberIfRead(outcome, result)
        // D34, for the meal that needed no question: asked again once, as today's describe asks.
        val estimate = (result as? StepResult.Estimate)?.result
        val settled = if (estimate is EstimateResult.AmountMissing && remaining() > 0) {
            StepResult.Estimate(everydayAgain(description, estimate.items))
        } else {
            result
        }
        settled.also { recorded("opening", it) }
    }

    override suspend fun next(description: String, asked: List<Asked>, cap: Int): StepResult =
        withContext(Dispatchers.IO) {
            val (outcome, text) = stepping { model, profile ->
                ConversationPrompt.step(model, description, asked, cap, profile)
            }
            val result = text?.let(ConversationResponse::step)
                ?: StepResult.Failed((outcome as OpenAiCall.Outcome.Failed).failure)
            rememberIfRead(outcome, result)
            result.also { recorded("step", it) }
        }

    override suspend fun finish(
        description: String,
        asked: List<Asked>,
        moreDetail: String?,
        deep: Boolean,
    ): EstimateResult = withContext(Dispatchers.IO) {
        val effort = if (deep) OpenAiCall.Effort.DEEP else OpenAiCall.Effort.EVERYDAY
        val first = finishing(effort, description, asked, moreDetail, emptyList())
        // D34: asked once more, naming the items, and only with allowance left — the second ask is
        // not part of the reserve (§12.4).
        val result = if (first is EstimateResult.AmountMissing && remaining() > 0) {
            finishing(effort, description, asked, moreDetail, first.items)
        } else {
            first
        }
        result.also { recordedFinal(it) }
    }

    override suspend fun remainingToday(): Int = remaining()

    private suspend fun remaining(): Int = settings.settings.first().remainingToday

    /** One request: the outcome, and its text when an answer came. */
    private suspend fun stepping(
        build: (String, RequestProfile) -> String,
    ): Pair<OpenAiCall.Outcome, String?> {
        val outcome = call.send(OpenAiCall.Effort.EVERYDAY, build)
        return outcome to (outcome as? OpenAiCall.Outcome.Body)?.text
    }

    /** D58 §12.5: every readable step teaches the everyday profile. */
    private suspend fun rememberIfRead(outcome: OpenAiCall.Outcome, result: StepResult) {
        val body = outcome as? OpenAiCall.Outcome.Body ?: return
        val read = when (result) {
            is StepResult.Ask, StepResult.Enough -> true
            is StepResult.Estimate ->
                result.result is EstimateResult.Proposed || result.result is EstimateResult.AmountMissing
            is StepResult.Failed -> false
        }
        if (read) call.remember(body)
    }

    private suspend fun finishing(
        effort: OpenAiCall.Effort,
        description: String,
        asked: List<Asked>,
        moreDetail: String?,
        missingAmounts: List<String>,
    ): EstimateResult = when (
        val outcome = call.send(effort) { model, profile ->
            ConversationPrompt.final(model, description, asked, moreDetail, missingAmounts, profile)
        }
    ) {
        is OpenAiCall.Outcome.Failed -> outcome.failure
        is OpenAiCall.Outcome.Body -> ConversationResponse.final(outcome.text).also { read ->
            if (read is EstimateResult.Proposed || read is EstimateResult.AmountMissing) call.remember(outcome)
        }
    }

    /** Today's everyday second ask, naming the items that came back without an amount (D34). */
    private suspend fun everydayAgain(description: String, missing: List<String>): EstimateResult =
        when (
            val outcome = call.send { model, profile ->
                EstimatePrompt.requestBody(model, description, missingAmounts = missing, profile = profile)
            }
        ) {
            is OpenAiCall.Outcome.Failed -> outcome.failure
            is OpenAiCall.Outcome.Body -> EstimateResponse.parse(outcome.text).also { read ->
                if (read is EstimateResult.Proposed || read is EstimateResult.AmountMissing) call.remember(outcome)
            }
        }

    private fun recorded(stage: String, result: StepResult) {
        when (result) {
            is StepResult.Failed -> failed("conversation $stage", result.failure)
            is StepResult.Estimate -> failed("conversation $stage", result.result)
            else -> Unit
        }
    }

    private fun recordedFinal(result: EstimateResult) = failed("final analysis", result)

    /** The kind, and nothing the owner said or the model asked (§9). */
    private fun failed(kind: String, result: EstimateResult) {
        when (result) {
            is EstimateResult.Refused -> problems.record("$kind refused", result.detail)
            is EstimateResult.Unreadable -> problems.record("$kind unreadable", result.why)
            is EstimateResult.AmountMissing ->
                problems.record("$kind without amounts", "${result.items.size} item(s)")
            is EstimateResult.Unreachable -> problems.record("$kind unreachable", "no answer")
            is EstimateResult.Proposed, is EstimateResult.NoKey, is EstimateResult.CeilingReached -> Unit
        }
    }
}
