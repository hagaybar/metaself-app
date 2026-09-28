package com.metaself.app.data.ai

import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import okhttp3.OkHttpClient

/**
 * The one implementation behind [Trainer] (D84). What is sent is [TrainerPrompt] and what comes back is
 * [TrainerResponse], both pure; the call is [OpenAiCall], shared with the meal estimator and the food
 * review — the key, the day's ceiling, the counting and the failures are one code path. One call, no
 * retry.
 *
 * The base URL is a parameter so a test can point it at a local server. **No test in this project
 * makes a real network call.**
 */
class OpenAiTrainer(
    keys: ApiKeyStore,
    settings: AiSettingsStore,
    client: OkHttpClient,
    profiles: RequestProfileStore,
    private val problems: ProblemLog = ProblemLog.NONE,
    baseUrl: String = OpenAiCall.OPENAI_URL,
) : Trainer {

    private val call = OpenAiCall(keys, settings, client, profiles, baseUrl)

    override suspend fun suggest(request: TrainerRequest): TrainerReply<SessionPlan> =
        ask({ model, profile -> TrainerPrompt.planBody(model, request, profile) }, TrainerResponse::parsePlan)

    override suspend fun feedback(request: TrainerRequest): TrainerReply<Feedback> =
        ask({ model, profile -> TrainerPrompt.feedbackBody(model, request, profile) }, TrainerResponse::parseFeedback)

    private suspend fun <T> ask(
        build: (String, RequestProfile) -> String,
        parse: (String, String) -> TrainerReply<T>,
    ): TrainerReply<T> = when (val outcome = call.send(build = build)) {
        is OpenAiCall.Outcome.Body -> parse(outcome.text, outcome.model).also { reply ->
            if (reply is TrainerReply.Answered) call.remember(outcome) else recorded(reply as TrainerReply.Failed, null)
        }
        is OpenAiCall.Outcome.Failed -> TrainerReply.Failed(outcome.failure).also { recorded(it, outcome.status) }
    }

    /**
     * Every failure is logged by its kind, and never in words: the request holds the owner's words and
     * the answer may quote them back; a refusal's own words may too. The screen shows the words.
     */
    private fun recorded(failed: TrainerReply.Failed, status: Int?) {
        when (failed.failure) {
            is EstimateResult.Refused -> problems.record(
                "trainer refused",
                if (status != null) "the provider answered $status" else "the call could not be made",
            )
            is EstimateResult.Unreadable -> problems.record("trainer unreadable", "the reply was not in the shape this app asked for")
            is EstimateResult.Unreachable -> problems.record("trainer unreachable", "no answer")
            else -> Unit
        }
    }
}
